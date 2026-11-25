package com.foodrescue.order.api;

import com.foodrescue.buildingblocks.security.AuthenticatedUser;
import com.foodrescue.order.api.dto.AddToCartRequest;
import com.foodrescue.order.api.dto.CartResponse;
import com.foodrescue.order.api.dto.UpdateCartItemRequest;
import com.foodrescue.order.application.usecase.CartUseCase;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@SecurityRequirement(name = "bearerAuth")
public class CartController {
    private final CartUseCase cartUseCase;

    public CartController(CartUseCase cartUseCase) {
        this.cartUseCase = cartUseCase;
    }

    @GetMapping
    public CartResponse getCart(@AuthenticationPrincipal AuthenticatedUser user) {
        return cartUseCase.getCart(user.id());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse addToCart(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AddToCartRequest req) {
        return cartUseCase.addToCart(user.id(), req);
    }

    @PutMapping("/items/{batchId}")
    public CartResponse updateItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long batchId,
            @Valid @RequestBody UpdateCartItemRequest req) {
        return cartUseCase.updateItem(user.id(), batchId, req);
    }

    @DeleteMapping("/items/{batchId}")
    public CartResponse removeItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long batchId) {
        return cartUseCase.removeItem(user.id(), batchId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearCart(@AuthenticationPrincipal AuthenticatedUser user) {
        cartUseCase.clearCart(user.id());
    }
}
