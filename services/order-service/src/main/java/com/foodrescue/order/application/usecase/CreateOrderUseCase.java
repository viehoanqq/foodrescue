package com.foodrescue.order.application.usecase;

import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.order.api.dto.CreateOrderItemRequest;
import com.foodrescue.order.api.dto.CreateOrderRequest;
import com.foodrescue.order.api.dto.OrderItemResponse;
import com.foodrescue.order.api.dto.OrderResponse;
import com.foodrescue.order.application.port.out.IInventoryPort;
import com.foodrescue.order.application.port.out.IOutboxPort;
import com.foodrescue.order.application.port.out.IStorePolicyPort;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public CreateOrderUseCase(
            SpringDataOrderRepository orderRepository,
            SpringDataOrderItemRepository orderItemRepository,
            SpringDataCartItemRepository cartRepository,
            IInventoryPort inventoryPort,
            IStorePolicyPort storePolicyPort,
            IOutboxPort outboxPort) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartRepository = cartRepository;
        this.inventoryPort = inventoryPort;
        this.storePolicyPort = storePolicyPort;
        this.outboxPort = outboxPort;
    }

    @Transactional
    public OrderResponse createOrder(Long customerId, CreateOrderRequest request, UUID correlationId) {
        if (correlationId == null) {
            correlationId = UUID.randomUUID();
        }

        List<CreateOrderItemRequest> requestItems = new ArrayList<>();
        if (request != null && request.items() != null && !request.items().isEmpty()) {
            requestItems.addAll(request.items());
        } else {
            List<CartItemEntity> cartItems = cartRepository.findByCustomerId(customerId);
            if (cartItems.isEmpty()) {
                throw OrderException.cartEmpty();
            }
            for (CartItemEntity ci : cartItems) {
                requestItems.add(new CreateOrderItemRequest(ci.getBatchId(), ci.getQuantity()));
            }
        }

        if (requestItems.isEmpty()) {
            throw OrderException.cartEmpty();
        }

        for (CreateOrderItemRequest it : requestItems) {
            if (it.quantity() == null || it.quantity() < 1 || it.quantity() > 5) {
                throw OrderException.validation("Số lượng mỗi túi phải từ 1 đến 5");
            }
        }

        Long storeId = (request != null && request.storeId() != null) ? request.storeId() : 1L;

        // Kiểm tra Store Policy
        IStorePolicyPort.StoreCheckoutPolicy policy = storePolicyPort.getPolicy(storeId);
        if (policy != null && !"APPROVED".equalsIgnoreCase(policy.status())) {
            throw OrderException.conflict("STORE_NOT_APPROVED", "Cửa hàng chưa được duyệt hoặc đang bị khóa");
        }
        BigDecimal commissionRate = (policy != null && policy.commissionRate() != null)
                ? policy.commissionRate() : new BigDecimal("15.00");

        // Chuẩn bị Reservation sang Catalog
        UUID reservationId = UUID.randomUUID();
        List<StockReservationCommand.Item> commandItems = requestItems.stream()
                .map(it -> new StockReservationCommand.Item(it.batchId(), it.quantity()))
                .toList();

        StockReservation reservation;
        try {
            reservation = inventoryPort.reserve(
                    new StockReservationCommand(reservationId, 1L, storeId, customerId, commandItems),
                    correlationId);
        } catch (Exception ex) {
            log.error("Stock reservation failed for customerId={}: {}", customerId, ex.getMessage());
            throw OrderException.conflict("STOCK_NOT_ENOUGH", "Không thể giữ kho cho túi cứu trợ: " + ex.getMessage());
        }

        try {
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
            for (CreateOrderItemRequest reqItem : requestItems) {
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
                    "order.state-changed.v1",
                    "Order",
                    savedOrder.getId().toString(),
                    1L,
                    correlationId,
                    null,
                    customerId,
                    payload);
            outboxPort.append(envelope);

            return toResponse(savedOrder, itemResponses);
        } catch (Exception ex) {
            log.error("Failed to commit order after reservation, compensating release: {}", ex.getMessage());
            try {
                inventoryPort.release(reservationId, correlationId);
            } catch (Exception releaseEx) {
                log.error("Failed to release reservation {}: {}", reservationId, releaseEx.getMessage());
            }
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
                o.getPickupCode(),
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
