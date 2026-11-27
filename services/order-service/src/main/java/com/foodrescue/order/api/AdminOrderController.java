package com.foodrescue.order.api;

import com.foodrescue.buildingblocks.security.AuthenticatedUser;
import com.foodrescue.order.api.dto.OrderResponse;
import com.foodrescue.order.api.dto.PageResponse;
import com.foodrescue.order.application.usecase.CustomerOrderUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Orders", description = "Quản trị viên tra cứu và huỷ đơn hàng")
public class AdminOrderController {

    private final CustomerOrderUseCase customerOrderUseCase;

    public AdminOrderController(CustomerOrderUseCase customerOrderUseCase) {
        this.customerOrderUseCase = customerOrderUseCase;
    }

    @GetMapping
    @Operation(summary = "Xem danh sách toàn bộ đơn hàng trong hệ thống (có phân trang và lọc)")
    public ResponseEntity<PageResponse<OrderResponse>> getAllOrders(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String code,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(customerOrderUseCase.getAllOrdersForAdmin(storeId, status, date, code, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết một đơn hàng bất kỳ")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(customerOrderUseCase.getOrderByIdForAdmin(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Quản trị viên huỷ đơn hàng (kèm lý do và ghi audit)")
    public ResponseEntity<OrderResponse> cancelOrder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        Long adminId = (user != null && user.id() != null) ? user.id() : 1L;
        String reason = (body != null && body.containsKey("reason")) ? body.get("reason") : "Quản trị viên huỷ đơn";
        return ResponseEntity.ok(customerOrderUseCase.adminCancelOrder(adminId, id, reason));
    }
}
