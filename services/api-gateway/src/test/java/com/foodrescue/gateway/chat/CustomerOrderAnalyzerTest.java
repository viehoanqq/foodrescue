package com.foodrescue.gateway.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomerOrderAnalyzerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void filtersByMonthAndYearComputesExactTotalsAndExcludesInternalFields() throws Exception {
        List<JsonNode> orders = List.of(
                mapper.readTree("""
                        {"id":1,"orderCode":"FR-001","storeId":10,"itemCount":2,"totalAmount":60000,
                         "commissionRate":15,"storeEarning":51000,"status":"COMPLETED","paymentStatus":"PAID",
                         "pickupCode":"ABC123","createdAt":"2026-11-10T18:30:00","completedAt":"2026-11-10T19:15:00"}"""),
                mapper.readTree("""
                        {"id":2,"orderCode":"FR-002","storeId":10,"itemCount":1,"totalAmount":35000,
                         "commissionRate":15,"storeEarning":29750,"status":"CONFIRMED","paymentStatus":"PAID",
                         "pickupCode":"XYZ789","createdAt":"2026-11-25T17:00:00","completedAt":null}"""),
                mapper.readTree("""
                        {"id":3,"orderCode":"FR-003","storeId":20,"itemCount":1,"totalAmount":50000,
                         "status":"CANCELLED","paymentStatus":"REFUND_PENDING",
                         "createdAt":"2026-11-26T12:00:00"}"""),
                mapper.readTree("""
                        {"id":4,"orderCode":"FR-004","storeId":10,"itemCount":3,"totalAmount":90000,
                         "status":"COMPLETED","paymentStatus":"PAID",
                         "createdAt":"2026-10-05T19:00:00"}""")
        );

        ObjectNode summary = CustomerOrderAnalyzer.analyze(
                orders, 11, 2026, null, null, Map.of(10L, "Bánh mì An Phát", 20L, "Sweet Paris"), mapper);

        assertEquals(3, summary.get("totalOrders").asInt());
        assertEquals(1, summary.get("completedOrders").asInt());
        assertEquals(1, summary.get("confirmedOrders").asInt());
        assertEquals(1, summary.get("cancelledOrders").asInt());
        assertEquals(3, summary.get("totalItemsNonCancelled").asInt());
        assertEquals(95000L, summary.get("totalSpentPaidVnd").asLong());
        assertEquals(60000L, summary.get("totalCompletedVnd").asLong());
        assertEquals(50000L, summary.get("totalRefundOrPendingVnd").asLong());

        JsonNode firstOrder = summary.get("orders").get(0);
        assertEquals("Bánh mì An Phát", firstOrder.get("storeName").asText());
        assertFalse(firstOrder.has("commissionRate"));
        assertFalse(firstOrder.has("storeEarning"));
    }
}
