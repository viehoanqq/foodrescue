package com.foodrescue.order.application.usecase;

import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.buildingblocks.events.MessagingNames;
import com.foodrescue.order.api.dto.OrderItemResponse;
import com.foodrescue.order.api.dto.OrderResponse;
import com.foodrescue.order.application.port.out.IInventoryPort;
import com.foodrescue.order.application.port.out.ICheckoutIdempotencyPort;
import com.foodrescue.order.application.port.out.IOutboxPort;
import com.foodrescue.order.application.port.out.IStorePolicyPort;
import com.foodrescue.order.application.port.out.IReservationRecoveryPort;
import com.foodrescue.order.domain.exception.OrderException;
import com.foodrescue.order.domain.model.StockReservation;
import com.foodrescue.order.domain.model.StockReservationCommand;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataCartItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.CartItemEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderItemEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CreateOrderUseCase {
    private static final Logger log = LoggerFactory.getLogger(CreateOrderUseCase.class);
    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SpringDataOrderRepository orderRepository;
    private final SpringDataOrderItemRepository orderItemRepository;
    private final SpringDataCartItemRepository cartRepository;
    private final IInventoryPort inventoryPort;
    private final IStorePolicyPort storePolicyPort;
    private final IOutboxPort outboxPort;
    private final ICheckoutIdempotencyPort idempotencyPort;
    private final IReservationRecoveryPort recoveryPort;
    private final ReservationRecoveryWorker recoveryWorker;
    private final TransactionTemplate transactions;

    public CreateOrderUseCase(
            SpringDataOrderRepository orderRepository,
            SpringDataOrderItemRepository orderItemRepository,
            SpringDataCartItemRepository cartRepository,
            IInventoryPort inventoryPort,
            IStorePolicyPort storePolicyPort,
            IOutboxPort outboxPort,
            ICheckoutIdempotencyPort idempotencyPort,
            IReservationRecoveryPort recoveryPort,
            ReservationRecoveryWorker recoveryWorker,
            TransactionTemplate transactions) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartRepository = cartRepository;
        this.inventoryPort = inventoryPort;
        this.storePolicyPort = storePolicyPort;
        this.outboxPort = outboxPort;
        this.idempotencyPort = idempotencyPort;
        this.recoveryPort = recoveryPort;
        this.recoveryWorker = recoveryWorker;
        this.transactions = transactions;
    }

    public OrderResponse createOrder(Long customerId, String idempotencyKey, UUID correlationId) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw OrderException.validation("Idempotency-Key must contain 1 to 128 characters");
        }
        String key = idempotencyKey.trim();
        UUID reservationId = UUID.nameUUIDFromBytes((customerId + ":" + key).getBytes(StandardCharsets.UTF_8));
        UUID traceId = correlationId == null ? UUID.randomUUID() : correlationId;
        try {
            return transactions.execute(status -> createOrderInTransaction(customerId, key, traceId, reservationId));
        } catch (RuntimeException ex) {
            try {
                recoveryPort.requestRelease(reservationId);
                recoveryWorker.recoverOne(reservationId);
            } catch (RuntimeException recoveryError) {
                ex.addSuppressed(recoveryError);
                log.error("Recovery scheduling failed for reservation {}: {}", reservationId, recoveryError.getMessage());
            }
            throw ex;
        }
    }

    private OrderResponse createOrderInTransaction(Long customerId, String idempotencyKey,
                                                   UUID correlationId, UUID reservationId) {
        Long existingId = idempotencyPort.claim(customerId, idempotencyKey);
        if (existingId != null) {
            OrderEntity existing = orderRepository.findById(existingId)
                    .orElseThrow(() -> new IllegalStateException("Idempotent order no longer exists"));
            if (!customerId.equals(existing.getCustomerId())) {
                throw OrderException.forbidden("Idempotency key belongs to another customer");
            }
            return toResponse(existing, orderItemRepository.findByOrderId(existingId).stream()
                    .map(i -> new OrderItemResponse(i.getId(), i.getBatchId(), i.getQuantity(), i.getUnitPrice(), i.getSubtotal()))
                    .toList());
        }
        List<CartItemEntity> cartItems = cartRepository.findByCustomerId(customerId);
        if (cartItems.isEmpty()) throw OrderException.cartEmpty();
        List<StockReservationCommand.Item> requestItems = cartItems.stream()
                .map(ci -> new StockReservationCommand.Item(ci.getBatchId(), ci.getQuantity()))
                .toList();
        Long storeId;
        try {
            storeId = inventoryPort.requireSingleStore(requestItems.stream()
                    .map(StockReservationCommand.Item::batchId).toList());
        } catch (com.foodrescue.buildingblocks.http.InternalServiceException ex) {
            if (ex.status() == 409) throw OrderException.multiStoreCheckout();
            throw ex;
        }

        IStorePolicyPort.StoreCheckoutPolicy policy = storePolicyPort.getPolicy(storeId);
        if (policy == null || !"APPROVED".equalsIgnoreCase(policy.status())) {
            throw OrderException.conflict("STORE_NOT_APPROVED", "Cửa hàng chưa được duyệt hoặc đang bị khóa");
        }
        if (policy.commissionRate() == null) {
            throw OrderException.conflict("STORE_POLICY_INVALID", "Store commission rate is missing");
        }
        BigDecimal commissionRate = policy.commissionRate();

        // Chuẩn bị Reservation sang Catalog
        recoveryPort.ensurePending(reservationId);
        recoveryPort.lockPending(reservationId);
        List<StockReservationCommand.Item> commandItems = requestItems;

        StockReservation reservation;
        try {
            reservation = inventoryPort.reserve(
                    new StockReservationCommand(reservationId, 1L, storeId, customerId, commandItems),
                    correlationId);
        } catch (RuntimeException ex) {
            if (ex instanceof com.foodrescue.buildingblocks.http.InternalServiceException serviceError
                    && serviceError.status() < 500) {
                throw OrderException.conflict("STOCK_NOT_ENOUGH", "Catalog rejected the stock reservation");
            }
            try {
                reservation = inventoryPort.find(reservationId, correlationId);
            } catch (RuntimeException lookupError) {
                ex.addSuppressed(lookupError);
                throw new OrderException("RESERVATION_UNCERTAIN",
                        "Catalog did not confirm the reservation; recovery will check and release it", 503);
            }
        }

        if (reservation == null || !reservationId.equals(reservation.reservationId())
                || !"HELD".equals(reservation.status())) {
            throw OrderException.conflict("RESERVATION_NOT_HELD", "Inventory reservation is not active");
        }
        Map<Long, Integer> requested = new HashMap<>();
        for (StockReservationCommand.Item item : commandItems) requested.put(item.batchId(), item.quantity());
        Map<Long, Integer> reserved = new HashMap<>();
        if (reservation.items() != null) {
            for (StockReservation.Item item : reservation.items()) reserved.put(item.batchId(), item.quantity());
        }
        if (!requested.equals(reserved)) {
            throw OrderException.conflict("RESERVATION_MISMATCH", "Catalog reservation does not match the cart");
        }

        try {
            LocalDateTime now = LocalDateTime.now();
            BigDecimal totalAmount = BigDecimal.ZERO;
            int totalItems = 0;

            List<OrderItemEntity> orderItems = new ArrayList<>();
            for (StockReservation.Item resItem : reservation.items()) {
                totalItems += resItem.quantity();
                if (resItem.unitPrice() == null) {
                    throw OrderException.conflict("PRICE_MISSING", "Catalog did not return a batch price");
                }
                BigDecimal unitPrice = resItem.unitPrice();
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

            OrderEntity savedOrder = orderRepository.save(order);

            List<OrderItemResponse> itemResponses = new ArrayList<>();
            for (OrderItemEntity oi : orderItems) {
                oi.setOrderId(savedOrder.getId());
                OrderItemEntity savedItem = orderItemRepository.save(oi);
                itemResponses.add(new OrderItemResponse(
                        savedItem.getId(), savedItem.getBatchId(), savedItem.getQuantity(),
                        savedItem.getUnitPrice(), savedItem.getSubtotal()));
            }

            // Xóa các mặt hàng khỏi giỏ
            for (StockReservationCommand.Item reqItem : requestItems) {
                cartRepository.deleteByCustomerIdAndBatchId(customerId, reqItem.batchId());
            }

            // Ghi Outbox event: order.state-changed.v1
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderId", savedOrder.getId());
            payload.put("storeId", savedOrder.getStoreId());
            payload.put("customerId", savedOrder.getCustomerId());
            payload.put("status", savedOrder.getStatus());
            payload.put("paymentStatus", savedOrder.getPaymentStatus());
            payload.put("totalAmount", savedOrder.getTotalAmount().longValue());
            payload.put("commissionAmount", savedOrder.getCommissionAmount().longValue());
            payload.put("storeEarning", savedOrder.getStoreEarning().longValue());
            payload.put("completedAt", null);
            payload.put("reservationId", savedOrder.getReservationId().toString());
            payload.put("items", requestItems.stream()
                    .map(it -> Map.of("batchId", it.batchId(), "quantity", it.quantity()))
                    .toList());

            EventEnvelope<Map<String, Object>> envelope = EventEnvelope.create(
                    MessagingNames.ORDER_STATE_CHANGED_EVENT,
                    "Order",
                    savedOrder.getId().toString(),
                    1L,
                    correlationId,
                    null,
                    customerId,
                    payload);
            outboxPort.append(envelope);
            idempotencyPort.complete(customerId, idempotencyKey, savedOrder.getId());
            recoveryPort.markOrdered(reservationId, savedOrder.getId());

            return toResponse(savedOrder, itemResponses);
        } catch (RuntimeException ex) {
            log.error("Order persistence failed after stock reservation {}: {}", reservationId, ex.getMessage());
            throw ex;
        }
    }

    private String generatePickupCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    public OrderResponse toResponse(OrderEntity o, List<OrderItemResponse> items) {
        return new OrderResponse(
                o.getId(),
                o.getOrderCode(),
                o.getCustomerId(),
                o.getStoreId(),
                o.getItemCount(),
                o.getTotalAmount(),
                o.getCommissionRate(),
                o.getCommissionAmount(),
                o.getStoreEarning(),
                o.getPickupStart(),
                o.getPickupEnd(),
                "PAID".equals(o.getPaymentStatus()) ? o.getPickupCode() : null,
                o.getStatus(),
                o.getPaymentStatus(),
                o.getCancelReason(),
                o.getPaymentExpiresAt(),
                o.getCompletedAt(),
                o.getCreatedAt(),
                o.getReservationId(),
                o.getAggregateVersion(),
                items);
    }
}
