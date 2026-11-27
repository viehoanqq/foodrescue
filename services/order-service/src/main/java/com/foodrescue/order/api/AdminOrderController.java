package com.foodrescue.order.api;

import com.foodrescue.order.api.dto.OrderResponse;
import com.foodrescue.order.application.usecase.CustomerOrderUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders")
@Tag(name = "Admin Orders", description = "Quản trị viên tra cứu và huỷ đơn hàng")
public class AdminOrderController {

    private final CustomerOrderUseCase customerOrderUseCase;

    public AdminOrderController(CustomerOrderUseCase customerOrderUseCase) {
        this.customerOrderUseCase = customerOrderUseCase;
    }

    @GetMapping
    @Operation(summary = "Xem danh sách toàn bộ đơn hàng trong hệ thống")
    public ResponseEntity<List<OrderResponse>> getAllOrders(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(customerOrderUseCase.getAllOrdersForAdmin(status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết một đơn hàng bất kỳ")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(customerOrderUseCase.getOrderByIdForAdmin(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Quản trị viên huỷ đơn hàng (kèm lý do và ghi audit)")
    public ResponseEntity<OrderResponse> cancelOrder(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        Long adminId = (Long) request.getAttribute("userId");
        if (adminId == null) {
            adminId = 1L;
        }
        String reason = (body != null && body.containsKey("reason")) ? body.get("reason") : "Quản trị viên huỷ đơn";
        return ResponseEntity.ok(customerOrderUseCase.adminCancelOrder(adminId, id, reason));
    }
}
