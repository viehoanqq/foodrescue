package com.foodrescue.catalog.api;

import com.foodrescue.buildingblocks.security.AuthenticatedUser;
import com.foodrescue.catalog.application.usecase.BatchManagementUseCase;
import com.foodrescue.catalog.domain.model.Actor;
import com.foodrescue.catalog.domain.model.BatchDraft;
import com.foodrescue.catalog.domain.model.RescueBatch;
import com.foodrescue.catalog.domain.model.StockMovement;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@Tag(name = "Túi giải cứu và tồn kho")
public class BatchManagementController {
    private final BatchManagementUseCase batches;

    public BatchManagementController(BatchManagementUseCase batches) {
        this.batches = batches;
    }

    @GetMapping("/api/store/batches")
    @SecurityRequirement(name = "bearerAuth")
    public List<BatchResponse> listMine(@AuthenticationPrincipal AuthenticatedUser user) {
        return batches.listMine(actor(user)).stream().map(BatchResponse::from).toList();
    }

    @PostMapping("/api/store/batches")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    public BatchResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                @Valid @RequestBody BatchRequest request) {
        return BatchResponse.from(batches.create(actor(user), request.toDraft()));
    }

    @PutMapping("/api/store/batches/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public BatchResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                @Valid @RequestBody BatchRequest request) {
        return BatchResponse.from(batches.update(actor(user), id, request.toDraft()));
    }

    @PatchMapping("/api/store/batches/{id}/quantity")
    @SecurityRequirement(name = "bearerAuth")
    public BatchResponse adjustStock(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                     @Valid @RequestBody StockAdjustmentRequest request) {
        return BatchResponse.from(batches.adjustStock(actor(user), id, request.changeQty(),
                request.reason(), request.note()));
    }

    @GetMapping("/api/store/batches/{id}/stock-movements")
    @SecurityRequirement(name = "bearerAuth")
    public PageResponse<StockMovementResponse> movements(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return PageResponse.from(batches.movements(actor(user), id, PageRequest.of(page, size))
                .map(StockMovementResponse::from));
    }

    @PostMapping("/api/store/batches/{id}/cancel")
    @SecurityRequirement(name = "bearerAuth")
    public BatchResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                @Valid @RequestBody CancelBatchRequest request) {
        return BatchResponse.from(batches.cancel(actor(user), id, request.reason(), false));
    }

    @GetMapping("/api/admin/batches")
    @SecurityRequirement(name = "bearerAuth")
    public PageResponse<BatchResponse> listAdmin(@RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return PageResponse.from(batches.listAdmin(storeId, PageRequest.of(page, size))
                .map(BatchResponse::from));
    }

    @PostMapping("/api/admin/batches/{id}/cancel")
    @SecurityRequirement(name = "bearerAuth")
    public BatchResponse cancelAdmin(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                     @Valid @RequestBody CancelBatchRequest request) {
        return BatchResponse.from(batches.cancel(actor(user), id, request.reason(), true));
    }

    private static Actor actor(AuthenticatedUser user) {
        return new Actor(user.id(), user.role(), user.storeId());
    }

    public record BatchRequest(@NotNull @Min(1) Long categoryId, @NotBlank @Size(max = 150) String title,
            @Size(max = 2000) String description, @Size(max = 255) String imageUrl,
            @NotNull BigDecimal originalPrice, @NotNull BigDecimal rescuePrice,
            @NotNull @Min(1) @Max(100000) Integer quantity,
            @NotNull LocalDateTime pickupStart, @NotNull LocalDateTime pickupEnd) {
        BatchDraft toDraft() {
            return new BatchDraft(categoryId, title, description, imageUrl, originalPrice,
                    rescuePrice, quantity, pickupStart, pickupEnd);
        }
    }

    public record StockAdjustmentRequest(@NotNull @Min(1) @Max(100000) Integer changeQty,
            @NotBlank String reason, @Size(max = 255) String note) {}

    public record CancelBatchRequest(@NotBlank @Size(max = 255) String reason) {}

    public record BatchResponse(Long id, Long storeId, Long categoryId, String title, String description,
            String imageUrl, BigDecimal originalPrice, BigDecimal rescuePrice, int quantity,
            int remainingQuantity, LocalDateTime pickupStart, LocalDateTime pickupEnd, String status,
            LocalDateTime createdAt, long aggregateVersion, boolean deleted) {
        static BatchResponse from(RescueBatch batch) {
            return new BatchResponse(batch.id(), batch.storeId(), batch.categoryId(), batch.title(),
                    batch.description(), batch.imageUrl(), batch.originalPrice(), batch.rescuePrice(),
                    batch.quantity(), batch.remainingQuantity(), batch.pickupStart(), batch.pickupEnd(),
                    batch.status(), batch.createdAt(), batch.version(), batch.deleted());
        }
    }

    public record StockMovementResponse(Long id, Long batchId, int changeQty, String reason,
            Long orderId, Long actorId, int remainingAfter, String note, LocalDateTime createdAt) {
        static StockMovementResponse from(StockMovement movement) {
            return new StockMovementResponse(movement.id(), movement.batchId(), movement.changeQty(),
                    movement.reason(), movement.orderId(), movement.actorId(), movement.remainingAfter(),
                    movement.note(), movement.createdAt());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        static <T> PageResponse<T> from(Page<T> result) {
            return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                    result.getTotalElements(), result.getTotalPages());
        }
    }
}
