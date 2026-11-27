package com.foodrescue.order.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.order.api.dto.OrderResponse;
import com.foodrescue.order.application.port.out.IInventoryPort;
import com.foodrescue.order.application.port.out.IStorePolicyPort;
import com.foodrescue.order.domain.exception.OrderException;
import com.foodrescue.order.domain.model.StockReservation;
import com.foodrescue.order.domain.model.StockReservationCommand;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataCartItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataIdempotencyKeyRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.CartItemEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.IdempotencyKeyEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderItemEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CreateOrderUseCase {
    private static final Logger log = LoggerFactory.getLogger(CreateOrderUseCase.class);
    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SpringDataOrderRepository orderRepository;
    private final SpringDataOrderItemRepository orderItemRepository;
    private final SpringDataCartItemRepository cartRepository;
    private final SpringDataIdempotencyKeyRepository idempotencyKeyRepository;
    private final IInventoryPort inventoryPort;
    private final IStorePolicyPort storePolicyPort;
    private final InventoryReleaseService inventoryReleaseService;
    private final OrderSupport orderSupport;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public CreateOrderUseCase(
            SpringDataOrderRepository orderRepository,
            SpringDataOrderItemRepository orderItemRepository,
            SpringDataCartItemRepository cartRepository,
            SpringDataIdempotencyKeyRepository idempotencyKeyRepository,
            IInventoryPort inventoryPort,
            IStorePolicyPort storePolicyPort,
            InventoryReleaseService inventoryReleaseService,
            OrderSupport orderSupport,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartRepository = cartRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.inventoryPort = inventoryPort;
        this.storePolicyPort = storePolicyPort;
        this.inventoryReleaseService = inventoryReleaseService;
        this.orderSupport = orderSupport;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Đặt đơn hàng mới từ giỏ hàng DB:
     * 1. Kiểm tra Idempotency-Key trước (nếu có).
     * 2. Chỉ đọc các túi từ cart_items trong DB, không nhận items/storeId từ browser.
     * 3. Gọi HTTP ngoài DB transaction: lấy storeId từ Catalog, kiểm tra Store APPROVED, giữ kho (có lookup khi timeout).
     * 4. Mở DB transaction ngắn để lưu Order, OrderItems, xoá giỏ, ghi Outbox và lưu Idempotency-Key.
     * 5. Nếu lưu DB thất bại sau khi giữ kho, trả kho ngay và xếp hàng retry nếu lần đầu lỗi.
     */
    public OrderResponse createOrder(Long customerId, String idempotencyKey, UUID correlationId) {
        UUID corrId = (correlationId != null) ? correlationId : UUID.randomUUID();
        String cleanKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey.trim() : null;

        // 1. Đọc giỏ hàng từ DB (không tin items từ browser)
        List<CartItemEntity> cartItems = cartRepository.findByCustomerId(customerId);
        String requestHash = computeCartHash(customerId, cartItems);

        // 2. Kiểm tra Idempotency-Key trước khi gọi HTTP hay giữ kho
        if (cleanKey != null) {
            Optional<IdempotencyKeyEntity> existingOpt = idempotencyKeyRepository.findById(cleanKey);
            if (existingOpt.isPresent()) {
                IdempotencyKeyEntity existing = existingOpt.get();
                if (!Objects.equals(existing.getCustomerId(), customerId)
                        || (existing.getRequestHash() != null && !existing.getRequestHash().equals(requestHash))) {
                    throw OrderException.conflict("IDEMPOTENCY_CONFLICT",
                            "Idempotency-Key đã được sử dụng cho một yêu cầu khác");
                }
                if (existing.getResponseBody() != null) {
                    try {
                        return objectMapper.readValue(existing.getResponseBody(), OrderResponse.class);
                    } catch (Exception e) {
                        log.warn("Failed to deserialize cached idempotency response for key={}", cleanKey);
                    }
                }
            }
        }

        if (cartItems.isEmpty()) {
            throw OrderException.cartEmpty();
        }

        for (CartItemEntity ci : cartItems) {
            if (ci.getQuantity() == null || ci.getQuantity() < 1 || ci.getQuantity() > 5) {
                throw OrderException.validation("Số lượng mỗi túi phải từ 1 đến 5");
            }
        }

        // 3. Xác định đúng storeId từ các túi trong giỏ qua Catalog (không tin storeId từ browser)
        Long resolvedStoreId = null;
        for (CartItemEntity ci : cartItems) {
            IInventoryPort.BatchInfo batchInfo;
            try {
                batchInfo = inventoryPort.getBatch(ci.getBatchId(), corrId);
            } catch (Exception ex) {
                log.error("Failed to fetch batch info for batchId={}: {}", ci.getBatchId(), ex.getMessage());
                throw OrderException.serviceUnavailable("Không thể kiểm tra thông tin túi cứu trợ từ Catalog");
            }
            if (batchInfo == null || batchInfo.storeId() == null) {
                throw OrderException.conflict("BATCH_NOT_AVAILABLE", "Túi cứu trợ #" + ci.getBatchId() + " không tồn tại");
            }
            if (resolvedStoreId == null) {
                resolvedStoreId = batchInfo.storeId();
            } else if (!resolvedStoreId.equals(batchInfo.storeId())) {
                throw OrderException.multiStoreCheckout();
            }
        }

        final Long storeId = resolvedStoreId;

        // 4. Kiểm tra Store Policy (ngoài DB transaction)
        IStorePolicyPort.StoreCheckoutPolicy policy = storePolicyPort.getPolicy(storeId);
        if (policy != null && !"APPROVED".equalsIgnoreCase(policy.status())) {
            throw OrderException.conflict("STORE_NOT_APPROVED", "Cửa hàng chưa được duyệt hoặc đang bị khóa");
        }
        BigDecimal commissionRate = (policy != null && policy.commissionRate() != null)
                ? policy.commissionRate() : new BigDecimal("15.00");

        // 5. Giữ kho qua Catalog (ngoài DB transaction), có tra cứu lại reservationId nếu bị timeout
        UUID reservationId = UUID.randomUUID();
        List<StockReservationCommand.Item> commandItems = cartItems.stream()
                .map(ci -> new StockReservationCommand.Item(ci.getBatchId(), ci.getQuantity()))
                .toList();

        StockReservation reservation = reserveWithTimeoutLookup(
                new StockReservationCommand(reservationId, 1L, storeId, customerId, commandItems),
                corrId);

        // 6. Ghi Order + OrderItems + Outbox + Idempotency-Key trong DB transaction ngắn
        try {
            OrderResponse response = transactionTemplate.execute(status -> {
                LocalDateTime now = LocalDateTime.now();
                BigDecimal totalAmount = BigDecimal.ZERO;
                int totalItems = 0;

                List<OrderItemEntity> orderItems = new ArrayList<>();
                for (StockReservation.Item resItem : reservation.items()) {
                    totalItems += resItem.quantity();
                    BigDecimal unitPrice = resItem.unitPrice() != null ? resItem.unitPrice() : BigDecimal.valueOf(30000);
                    BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(resItem.quantity()));
                    totalAmount = totalAmount.add(subtotal);

                    OrderItemEntity itemEntity = new OrderItemEntity();
                    itemEntity.setBatchId(resItem.batchId());
                    itemEntity.setQuantity(resItem.quantity());
                    itemEntity.setUnitPrice(unitPrice);
                    itemEntity.setSubtotal(subtotal);
                    orderItems.add(itemEntity);
                }

                BigDecimal commissionAmount = totalAmount.multiply(commissionRate)
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
                BigDecimal storeEarning = totalAmount.subtract(commissionAmount);

                String orderCode = "FR-" + now.format(DateTimeFormatter.ofPattern("yyMMdd")) + "-" + (1000 + RANDOM.nextInt(9000));
                String pickupCode = generatePickupCode();

                OrderEntity order = new OrderEntity();
                order.setOrderCode(orderCode);
                order.setCustomerId(customerId);
                order.setStoreId(storeId);
                order.setItemCount(totalItems);
                order.setTotalAmount(totalAmount);
                order.setCommissionRate(commissionRate);
                order.setCommissionAmount(commissionAmount);
                order.setStoreEarning(storeEarning);
                order.setPickupStart(reservation.pickupStart() != null ? reservation.pickupStart() : now.plusHours(1));
                order.setPickupEnd(reservation.pickupEnd() != null ? reservation.pickupEnd() : now.plusHours(3));
                order.setPickupCode(pickupCode);
                order.setStatus("PENDING_PAYMENT");
                order.setPaymentStatus("UNPAID");
                order.setPaymentExpiresAt(now.plusMinutes(15));
                order.setCreatedAt(now);
                order.setReservationId(reservationId);
                order.setAggregateVersion(1L);
                order.setRefundRequested(false);

                OrderEntity savedOrder = orderRepository.save(order);

                for (OrderItemEntity itemEntity : orderItems) {
                    itemEntity.setOrderId(savedOrder.getId());
                }
                List<OrderItemEntity> savedItems = orderItemRepository.saveAll(orderItems);

                // Xoá giỏ hàng
                cartRepository.deleteByCustomerId(customerId);

                // Ghi Outbox: order.state-changed.v1
                orderSupport.emitOrderStateChanged(savedOrder, savedItems, corrId, customerId);

                OrderResponse orderResponse = orderSupport.toResponse(savedOrder, savedItems);

                // Lưu Idempotency-Key nếu có
                if (cleanKey != null) {
                    try {
                        IdempotencyKeyEntity idem = new IdempotencyKeyEntity();
                        idem.setKey(cleanKey);
                        idem.setTargetType("ORDER");
                        idem.setTargetId(savedOrder.getId().toString());
                        idem.setCustomerId(customerId);
                        idem.setRequestHash(requestHash);
                        idem.setResponseBody(objectMapper.writeValueAsString(orderResponse));
                        idem.setCreatedAt(now);
                        idempotencyKeyRepository.save(idem);
                    } catch (Exception jsonEx) {
                        log.warn("Could not serialize idempotency response for key={}", cleanKey);
                    }
                }

                return orderResponse;
            });

            if (response == null) {
                throw new IllegalStateException("Order transaction returned null");
            }
            return response;

        } catch (Exception ex) {
            log.error("Failed to persist order after reservationId={}, triggering compensation release: {}",
                    reservationId, ex.getMessage());
            inventoryReleaseService.releaseOrEnqueue(reservationId, "ORDER_PERSIST_FAILED", corrId);
            if (ex instanceof OrderException oe) {
                throw oe;
            }
            throw new IllegalStateException("Không thể tạo đơn hàng, đã kích hoạt trả kho: " + ex.getMessage(), ex);
        }
    }

    private StockReservation reserveWithTimeoutLookup(StockReservationCommand command, UUID correlationId) {
        try {
            return inventoryPort.reserve(command, correlationId);
        } catch (Exception reserveEx) {
            log.warn("Reserve call failed/timed out for reservationId={}, checking status via find(): {}",
                    command.reservationId(), reserveEx.getMessage());
            try {
                StockReservation existing = inventoryPort.find(command.reservationId(), correlationId);
                if (existing != null && "HELD".equalsIgnoreCase(existing.status())
                        && existing.items() != null && !existing.items().isEmpty()) {
                    log.info("Recovered HELD reservationId={} after timeout", command.reservationId());
                    return existing;
                }
            } catch (Exception findEx) {
                log.debug("Lookup after failed reserve did not find active reservationId={}: {}",
                        command.reservationId(), findEx.getMessage());
            }
            throw OrderException.conflict("STOCK_NOT_ENOUGH",
                    "Không thể giữ kho cho túi cứu trợ: " + reserveEx.getMessage());
        }
    }

    private String computeCartHash(Long customerId, List<CartItemEntity> cartItems) {
        List<String> parts = cartItems.stream()
                .sorted(Comparator.comparing(CartItemEntity::getBatchId))
                .map(ci -> ci.getBatchId() + ":" + ci.getQuantity())
                .toList();
        String raw = customerId + "|" + String.join(",", parts);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            return raw;
        }
    }

    private String generatePickupCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
