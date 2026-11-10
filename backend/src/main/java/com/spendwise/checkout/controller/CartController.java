package com.spendwise.checkout.controller;

import com.spendwise.checkout.dto.CheckoutDetails;
import com.spendwise.checkout.service.CartService;
import com.spendwise.checkout.service.CartService.CartItem;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping
    public List<CartItem> items(@AuthenticationPrincipal(expression = "name") String ownerId) {
        return cart.items(ownerId);
    }

    @PutMapping("/items/{candidateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@AuthenticationPrincipal(expression = "name") String ownerId, @PathVariable UUID candidateId) {
        cart.add(ownerId, candidateId);
    }

    @DeleteMapping("/items/{candidateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal(expression = "name") String ownerId, @PathVariable UUID candidateId) {
        cart.remove(ownerId, candidateId);
    }

    @GetMapping("/details")
    public CheckoutDetails details(@AuthenticationPrincipal(expression = "name") String ownerId) {
        return cart.details(ownerId);
    }

    @PutMapping("/details")
    public CheckoutDetails saveDetails(@AuthenticationPrincipal(expression = "name") String ownerId,
            @Valid @RequestBody CheckoutDetails details) {
        return cart.saveDetails(ownerId, details);
    }
}
