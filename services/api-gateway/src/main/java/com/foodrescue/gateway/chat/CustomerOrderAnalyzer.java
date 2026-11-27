package com.foodrescue.gateway.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure Java aggregator so order totals and counts are exact instead of relying on LLM arithmetic. */
public final class CustomerOrderAnalyzer {
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private CustomerOrderAnalyzer() {}

    public static ObjectNode analyze(List<JsonNode> orders, Integer month, Integer year,
                                     String statusFilter, Long storeIdFilter,
                                     Map<Long, String> storeNames, ObjectMapper mapper) {
        List<JsonNode> filtered = new ArrayList<>();
        for (JsonNode order : orders) {
            if (!matchesTime(order.path("createdAt").asText(null), month, year)) continue;
            if (statusFilter != null && !statusFilter.isBlank()
                    && !statusFilter.trim().equalsIgnoreCase(order.path("status").asText(""))) continue;
            if (storeIdFilter != null && order.path("storeId").asLong(-1) != storeIdFilter) continue;
            filtered.add(order);
        }

        int completed = 0;
        int confirmed = 0;
        int pendingPayment = 0;
        int cancelled = 0;
        int noShow = 0;
        int totalItems = 0;
        BigDecimal totalPaidVnd = BigDecimal.ZERO;
        BigDecimal totalCompletedVnd = BigDecimal.ZERO;
        BigDecimal totalRefundOrPendingVnd = BigDecimal.ZERO;
        Map<Long, StoreStats> byStore = new LinkedHashMap<>();

        for (JsonNode order : filtered) {
            String status = order.path("status").asText("");
            String paymentStatus = order.path("paymentStatus").asText("");
            BigDecimal amount = decimal(order.get("totalAmount"));
            int items = order.path("itemCount").asInt(0);
            long storeId = order.path("storeId").asLong(-1);

            switch (status) {
                case "COMPLETED" -> completed++;
                case "CONFIRMED" -> confirmed++;
                case "PENDING_PAYMENT" -> pendingPayment++;
                case "CANCELLED" -> cancelled++;
                case "NO_SHOW" -> noShow++;
                default -> {}
            }

            if (!"CANCELLED".equals(status)) {
                totalItems += items;
            }
            if ("PAID".equals(paymentStatus)) {
                totalPaidVnd = totalPaidVnd.add(amount);
            }
            if ("COMPLETED".equals(status)) {
                totalCompletedVnd = totalCompletedVnd.add(amount);
            }
            if ("REFUNDED".equals(paymentStatus) || "REFUND_PENDING".equals(paymentStatus)) {
                totalRefundOrPendingVnd = totalRefundOrPendingVnd.add(amount);
            }
            if (storeId > 0) {
                StoreStats stats = byStore.computeIfAbsent(storeId,
                        id -> new StoreStats(id, storeNames.getOrDefault(id, "Cửa hàng #" + id)));
                stats.orders++;
                if ("PAID".equals(paymentStatus)) {
                    stats.paidVnd = stats.paidVnd.add(amount);
                }
            }
        }

        ObjectNode result = mapper.createObjectNode();
        if (month != null) result.put("filterMonth", month);
        if (year != null) result.put("filterYear", year);
        if (statusFilter != null && !statusFilter.isBlank()) result.put("filterStatus", statusFilter.trim());
        if (storeIdFilter != null) result.put("filterStoreId", storeIdFilter);

        result.put("totalOrders", filtered.size());
        result.put("completedOrders", completed);
        result.put("confirmedOrders", confirmed);
        result.put("pendingPaymentOrders", pendingPayment);
        result.put("cancelledOrders", cancelled);
        result.put("noShowOrders", noShow);
        result.put("totalItemsNonCancelled", totalItems);
        result.put("totalSpentPaidVnd", totalPaidVnd.longValue());
        result.put("totalCompletedVnd", totalCompletedVnd.longValue());
        result.put("totalRefundOrPendingVnd", totalRefundOrPendingVnd.longValue());

        ArrayNode storesNode = result.putArray("byStore");
        byStore.values().stream()
                .sorted(Comparator.comparingInt((StoreStats s) -> s.orders).reversed())
                .limit(10)
                .forEach(s -> {
                    ObjectNode node = storesNode.addObject();
                    node.put("storeId", s.storeId);
                    node.put("storeName", s.storeName);
                    node.put("orders", s.orders);
                    node.put("paidVnd", s.paidVnd.longValue());
                });

        ArrayNode ordersNode = result.putArray("orders");
        filtered.stream().limit(25).forEach(o -> {
            ObjectNode row = ordersNode.addObject();
            long sid = o.path("storeId").asLong(-1);
            row.put("id", o.path("id").asLong());
            row.put("orderCode", o.path("orderCode").asText(""));
            row.put("storeId", sid);
            if (sid > 0) row.put("storeName", storeNames.getOrDefault(sid, "Cửa hàng #" + sid));
            row.put("itemCount", o.path("itemCount").asInt(0));
            row.put("totalAmount", decimal(o.get("totalAmount")).longValue());
            row.put("status", o.path("status").asText(""));
            row.put("paymentStatus", o.path("paymentStatus").asText(""));
            if (!o.path("pickupCode").isNull() && !o.path("pickupCode").asText("").isBlank()) {
                row.put("pickupCode", o.path("pickupCode").asText());
            }
            row.put("createdAt", o.path("createdAt").asText(""));
            if (!o.path("completedAt").isNull()) {
                row.put("completedAt", o.path("completedAt").asText(""));
            }
        });
        return result;
    }

    private static boolean matchesTime(String rawCreatedAt, Integer month, Integer year) {
        if (month == null && year == null) return true;
        if (rawCreatedAt == null || rawCreatedAt.isBlank()) return false;
        try {
            LocalDateTime created = LocalDateTime.parse(rawCreatedAt);
            if (year != null && created.getYear() != year) return false;
            return month == null || created.getMonthValue() == month;
        } catch (DateTimeParseException ex) {
            try {
                LocalDateTime created = java.time.OffsetDateTime.parse(rawCreatedAt)
                        .atZoneSameInstant(VIETNAM_ZONE).toLocalDateTime();
                if (year != null && created.getYear() != year) return false;
                return month == null || created.getMonthValue() == month;
            } catch (DateTimeParseException ignored) {
                return false;
            }
        }
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) return BigDecimal.ZERO;
        if (node.isNumber()) return node.decimalValue();
        try { return new BigDecimal(node.asText("0")); }
        catch (NumberFormatException ex) { return BigDecimal.ZERO; }
    }

    private static final class StoreStats {
        final long storeId;
        final String storeName;
        int orders;
        BigDecimal paidVnd = BigDecimal.ZERO;

        StoreStats(long storeId, String storeName) {
            this.storeId = storeId;
            this.storeName = storeName;
        }
    }
}
