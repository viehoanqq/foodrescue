package com.foodrescue.order;

import com.foodrescue.cart.CartService;
import com.foodrescue.common.entity.*;
import com.foodrescue.common.enums.*;
import com.foodrescue.common.exception.BusinessException;
import com.foodrescue.common.exception.ErrorCode;
import com.foodrescue.common.repository.*;
import com.foodrescue.common.service.AuditService;
import com.foodrescue.common.service.StockService;
import com.foodrescue.common.util.Money;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final String CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final RescueBatchRepository rescueBatchRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final StockService stockService;
    private final AuditService auditService;

    @Value("${app.order.payment-link-minutes:10}")
    private int paymentLinkMinutes;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            RescueBatchRepository rescueBatchRepository,
            CartItemRepository cartItemRepository,
            UserRepository userRepository,
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            StockService stockService,
            AuditService auditService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.rescueBatchRepository = rescueBatchRepository;
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.stockService = stockService;
        this.auditService = auditService;
    }

    @Transactional
    public OrderResponse createOrder(Long customerId, CreateOrderRequest req) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        Map<Long, Integer> itemQuantities = new LinkedHashMap<>();
        if (req != null && req.items() != null && !req.items().isEmpty()) {
            for (CreateOrderItemRequest item : req.items()) {
                if (item.batchId() == null || item.quantity() == null) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR);
                }
                itemQuantities.merge(item.batchId(), item.quantity(), Integer::sum);
            }
        } else {
            List<CartItem> cartItems = cartItemRepository.findByCustomerId(customerId);
            if (cartItems.isEmpty()) {
                throw new BusinessException(ErrorCode.CART_EMPTY);
            }
            for (CartItem ci : cartItems) {
                itemQuantities.put(ci.getBatchId(), ci.getQuantity());
            }
        }

        if (itemQuantities.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        for (int qty : itemQuantities.values()) {
            if (qty < 1 || qty > 5) {
                throw new BusinessException(ErrorCode.CART_ITEM_LIMIT);
            }
        }

        LocalDateTime now = LocalDateTime.now();

        // Chống bán vượt: khoá các dòng rescue_batches theo thứ tự ID tăng dần để tránh deadlock
        List<Long> sortedBatchIds = new ArrayList<>(itemQuantities.keySet());
        Collections.sort(sortedBatchIds);

        Map<Long, RescueBatch> lockedBatches = new LinkedHashMap<>();
        Store store = null;
        LocalDateTime maxPickupStart = null;
        LocalDateTime minPickupEnd = null;

        for (Long batchId : sortedBatchIds) {
            RescueBatch batch = rescueBatchRepository.findByIdForUpdate(batchId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

            if (batch.getStatus() != BatchStatus.AVAILABLE || batch.getPickupEnd().isBefore(now)) {
                throw new BusinessException(ErrorCode.BATCH_NOT_AVAILABLE);
            }

            int reqQty = itemQuantities.get(batchId);
            if (batch.getRemainingQuantity() < reqQty) {
                if (batch.getRemainingQuantity() == 0) {
                    throw new BusinessException(ErrorCode.BATCH_SOLD_OUT);
                } else {
                    throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
                }
            }

            if (store == null) {
                store = batch.getStore();
            } else if (!store.getId().equals(batch.getStore().getId())) {
                throw new BusinessException(ErrorCode.CART_OTHER_STORE);
            }

            if (maxPickupStart == null || batch.getPickupStart().isAfter(maxPickupStart)) {
                maxPickupStart = batch.getPickupStart();
            }
            if (minPickupEnd == null || batch.getPickupEnd().isBefore(minPickupEnd)) {
                minPickupEnd = batch.getPickupEnd();
            }

            lockedBatches.put(batchId, batch);
        }

        if (maxPickupStart == null || minPickupEnd == null || !minPickupEnd.isAfter(maxPickupStart)) {
            throw new BusinessException(ErrorCode.PICKUP_WINDOW_CONFLICT);
        }

        // Tính tiền & số lượng
        int totalItemCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : itemQuantities.entrySet()) {
            RescueBatch b = lockedBatches.get(entry.getKey());
            int q = entry.getValue();
            totalItemCount += q;
            totalAmount = totalAmount.add(Money.subtotal(b.getRescuePrice(), q));
        }

        BigDecimal commissionRate = store.getCommissionRate();
        BigDecimal commissionAmount = Money.commission(totalAmount, commissionRate);
        BigDecimal storeEarning = Money.storeEarning(totalAmount, commissionRate);

        String orderCode = generateOrderCode(now);
        String pickupCode = generatePickupCode();
        LocalDateTime paymentExpiresAt = now.plusMinutes(paymentLinkMinutes);

        Order order = new Order();
        order.setOrderCode(orderCode);
        order.setCustomer(customer);
        order.setStore(store);
        order.setItemCount(totalItemCount);
        order.setTotalAmount(totalAmount);
        order.setCommissionRate(commissionRate);
        order.setCommissionAmount(commissionAmount);
        order.setStoreEarning(storeEarning);
        order.setPickupStart(maxPickupStart);
        order.setPickupEnd(minPickupEnd);
        order.setPickupCode(pickupCode);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setPaymentExpiresAt(paymentExpiresAt);

        Order savedOrder = orderRepository.save(order);

        List<OrderItemResponse> itemResponses = new ArrayList<>();

        // Trừ tồn kho và ghi sổ kho
        for (Map.Entry<Long, Integer> entry : itemQuantities.entrySet()) {
            Long bId = entry.getKey();
            int qty = entry.getValue();
            RescueBatch batch = lockedBatches.get(bId);

            int remainingAfter = batch.getRemainingQuantity() - qty;
            batch.setRemainingQuantity(remainingAfter);
            if (remainingAfter == 0) {
                batch.setStatus(BatchStatus.SOLD_OUT);
            }
            rescueBatchRepository.save(batch);

            BigDecimal subtotal = Money.subtotal(batch.getRescuePrice(), qty);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(savedOrder);
            orderItem.setBatch(batch);
            orderItem.setQuantity(qty);
            orderItem.setUnitPrice(batch.getRescuePrice());
            orderItem.setSubtotal(subtotal);
            OrderItem savedItem = orderItemRepository.save(orderItem);

            // Ghi sổ kho ORDERED
            stockService.recordMovement(
                    batch,
                    -qty,
                    StockReason.ORDERED,
                    savedOrder,
                    customer,
                    "Đơn " + savedOrder.getOrderCode());

            itemResponses.add(new OrderItemResponse(
                    savedItem.getId(),
                    batch.getId(),
                    batch.getTitle(),
                    batch.getImageUrl(),
                    qty,
                    batch.getRescuePrice(),
                    subtotal));
        }

        // Xoá các mặt hàng đã đặt khỏi giỏ
        for (Long bId : itemQuantities.keySet()) {
            cartItemRepository.deleteById(new CartItemId(customerId, bId));
        }

        return toOrderResponse(savedOrder, itemResponses);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getCustomerOrders(Long customerId, OrderStatus status) {
        List<Order> orders = status != null
                ? orderRepository.findByCustomerIdAndStatusOrderByCreatedAtDesc(customerId, status)
                : orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);

        return orders.stream().map(this::toOrderResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getCustomerOrderDetail(Long customerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!order.getCustomer().getId().equals(customerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return toOrderResponse(order);
    }

    @Transactional
    public OrderResponse cancelCustomerOrder(Long customerId, Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!order.getCustomer().getId().equals(customerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // Khách chỉ được huỷ đơn khi chưa thanh toán (PENDING_PAYMENT + UNPAID)
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT || order.getPaymentStatus() != PaymentStatus.UNPAID) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELLABLE);
        }

        User customer = order.getCustomer();
        LocalDateTime now = LocalDateTime.now();

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledBy(customer);
        order.setCancelledAt(now);
        order.setCancelReason(reason != null && !reason.isBlank() ? reason : "Khách hàng huỷ đơn");
        orderRepository.save(order);

        restoreStock(order, customer, "Huỷ đơn " + order.getOrderCode());

        auditService.log(
                customerId,
                AuditAction.ORDER_CANCELLED,
                EntityType.ORDER,
                order.getId(),
                order.getTotalAmount(),
                order.getCancelReason());

        return toOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getStoreOrders(Long storeId, OrderStatus status) {
        List<Order> orders = status != null
                ? orderRepository.findByStoreIdAndStatusOrderByCreatedAtDesc(storeId, status)
                : orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId);

        return orders.stream().map(this::toOrderResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getStoreOrderDetail(Long storeId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!order.getStore().getId().equals(storeId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return toOrderResponse(order);
    }

    @Transactional
    public OrderResponse cancelStoreOrder(Long actorId, Long storeId, Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!order.getStore().getId().equals(storeId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // Cửa hàng được huỷ khi đơn ở trạng thái CONFIRMED hoặc PENDING_PAYMENT
        if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELLABLE);
        }

        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        LocalDateTime now = LocalDateTime.now();

        boolean wasPaid = order.getPaymentStatus() == PaymentStatus.PAID;

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledBy(actor);
        order.setCancelledAt(now);
        order.setCancelReason(reason);

        if (wasPaid) {
            order.setPaymentStatus(PaymentStatus.REFUND_PENDING);

            Payment payment = paymentRepository
                    .findFirstByOrderIdAndStatusOrderByCreatedAtDesc(order.getId(), PaymentTxnStatus.SUCCESS)
                    .orElseThrow(() -> new BusinessException(ErrorCode.DATA_CONSTRAINT_VIOLATION));

            Refund refund = new Refund();
            refund.setRefundCode("RF-" + order.getOrderCode());
            refund.setOrder(order);
            refund.setPayment(payment);
            refund.setAmount(order.getTotalAmount());
            refund.setReason(RefundReason.STORE_CANCELLED);
            refund.setStatus(RefundStatus.PENDING);
            refund.setRequestedBy(actor);
            refund.setRequestedAt(now);
            refund.setNote(reason);
            Refund savedRefund = refundRepository.save(refund);

            auditService.log(
                    actorId,
                    AuditAction.REFUND_REQUESTED,
                    EntityType.REFUND,
                    savedRefund.getId(),
                    savedRefund.getAmount(),
                    savedRefund.getRefundCode() + ", lý do STORE_CANCELLED");
        }

        orderRepository.save(order);

        restoreStock(order, actor, "Cửa hàng huỷ đơn " + order.getOrderCode());

        auditService.log(
                actorId,
                AuditAction.ORDER_CANCELLED,
                EntityType.ORDER,
                order.getId(),
                order.getTotalAmount(),
                reason);

        return toOrderResponse(order);
    }

    private void restoreStock(Order order, User actor, String note) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        List<OrderItem> sortedItems = new ArrayList<>(items);
        sortedItems.sort(Comparator.comparing(i -> i.getBatch().getId()));

        LocalDateTime now = LocalDateTime.now();

        for (OrderItem item : sortedItems) {
            Long bId = item.getBatch().getId();
            RescueBatch batch = rescueBatchRepository.findByIdForUpdate(bId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

            int remainingAfter = batch.getRemainingQuantity() + item.getQuantity();
            batch.setRemainingQuantity(remainingAfter);

            if (batch.getStatus() == BatchStatus.SOLD_OUT && batch.getPickupEnd().isAfter(now)) {
                batch.setStatus(BatchStatus.AVAILABLE);
            }
            rescueBatchRepository.save(batch);

            stockService.recordMovement(
                    batch,
                    item.getQuantity(),
                    StockReason.RETURNED,
                    order,
                    actor,
                    note);
        }
    }

    private String generateOrderCode(LocalDateTime now) {
        String datePrefix = "FR" + DateTimeFormatter.ofPattern("yyMMdd").format(now) + "-";
        for (int i = 0; i < 20; i++) {
            int seq = RANDOM.nextInt(9000) + 1000;
            String code = datePrefix + String.format("%04d", seq);
            if (orderRepository.findByOrderCode(code).isEmpty()) {
                return code;
            }
        }
        return datePrefix + System.currentTimeMillis() % 10000;
    }

    private String generatePickupCode() {
        for (int attempt = 0; attempt < 50; attempt++) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (orderRepository.findByPickupCode(code).isEmpty()) {
                return code;
            }
        }
        return "PK" + (RANDOM.nextInt(9000) + 1000);
    }

    public OrderResponse toOrderResponse(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        List<OrderItemResponse> itemResponses = items.stream().map(i -> new OrderItemResponse(
                i.getId(),
                i.getBatch().getId(),
                i.getBatch().getTitle(),
                i.getBatch().getImageUrl(),
                i.getQuantity(),
                i.getUnitPrice(),
                i.getSubtotal())).toList();
        return toOrderResponse(order, itemResponses);
    }

    private OrderResponse toOrderResponse(Order order, List<OrderItemResponse> items) {
        boolean canCancel = order.getStatus() == OrderStatus.PENDING_PAYMENT
                && order.getPaymentStatus() == PaymentStatus.UNPAID;

        return new OrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getStore().getId(),
                order.getStore().getName(),
                order.getStore().getAddress(),
                order.getStore().getPhone(),
                order.getCustomer().getFullName(),
                order.getCustomer().getPhone(),
                order.getItemCount(),
                order.getTotalAmount(),
                order.getCommissionRate(),
                order.getCommissionAmount(),
                order.getStoreEarning(),
                order.getPickupStart(),
                order.getPickupEnd(),
                order.getPickupCode(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getPaymentExpiresAt(),
                order.getCompletedAt(),
                order.getCancelReason(),
                order.getCancelledAt(),
                order.getCreatedAt(),
                items,
                canCancel);
    }
}
