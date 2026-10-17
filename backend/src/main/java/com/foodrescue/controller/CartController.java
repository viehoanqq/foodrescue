package com.foodrescue.controller;

import com.foodrescue.dto.request.AddToCartRequest;
import com.foodrescue.dto.request.UpdateCartItemRequest;
import com.foodrescue.dto.response.CartResponse;
import com.foodrescue.security.CurrentUser;
import com.foodrescue.service.CartService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getCart() {
        return cartService.getCart(CurrentUser.id());
    }

    @PostMapping
    public CartResponse addToCart(@Valid @RequestBody AddToCartRequest req) {
        return cartService.addToCart(CurrentUser.id(), req.batchId(), req.getResolvedQuantity());
    }

    @PutMapping("/{batchId}")
    public CartResponse updateQuantity(
            @PathVariable Long batchId,
            @Valid @RequestBody UpdateCartItemRequest req) {
        return cartService.updateQuantity(CurrentUser.id(), batchId, req.quantity());
    }

    @DeleteMapping("/{batchId}")
    public void removeItem(@PathVariable Long batchId) {
        cartService.removeItem(CurrentUser.id(), batchId);
    }

    @DeleteMapping
    public void clearCart() {
        cartService.clearCart(CurrentUser.id());
    }
}
