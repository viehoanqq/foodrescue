package com.foodrescue.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Kết quả huỷ túi (BR-24): đơn đã trả tiền thì tạo hoàn tiền, đơn chưa trả thì huỷ UNPAID, cả hai đều trả kho. */
public record CancelBatchResponse(
        StoreBatchResponse batch,
        @Schema(description = "Số đơn CONFIRMED đã chuyển CANCELLED + REFUND_PENDING (lý do BATCH_CANCELLED)") int refundRequestedOrders,
        @Schema(description = "Số đơn PENDING_PAYMENT đã chuyển CANCELLED + UNPAID") int cancelledUnpaidOrders) {
}
