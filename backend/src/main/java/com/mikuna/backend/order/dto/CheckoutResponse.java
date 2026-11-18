package com.mikuna.backend.order.dto;

import com.mikuna.backend.cart.dto.CartResponse;
import com.mikuna.backend.cart.service.CartService;
import com.mikuna.backend.order.model.PaymentMethod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Resumen de confirmación: el carrito vigente, si se puede confirmar ({@code ready}) y por qué no
 * ({@code blockers}), los métodos de pago ofrecidos y la clave publicable de Stripe (nunca la secreta). Es
 * informativo: el servidor vuelve a validar todo al confirmar.
 */
public record CheckoutResponse(CartResponse cart, boolean ready, List<String> blockers,
        List<MethodInfo> paymentMethods, String publishableKey) {

    public static final String CART_EMPTY = "CART_EMPTY";
    public static final String NO_ADDRESS = "NO_ADDRESS";
    public static final String RESTAURANT_UNAVAILABLE = "RESTAURANT_UNAVAILABLE";
    public static final String LINES_UNAVAILABLE = "LINES_UNAVAILABLE";

    public record MethodInfo(String code, String label) {
    }

    public static CheckoutResponse of(CartResponse cart, String publishableKey) {
        List<String> blockers = new ArrayList<>();
        if (cart.lines().isEmpty()) {
            blockers.add(CART_EMPTY);
        } else {
            boolean restaurantDown = cart.lines().stream()
                    .anyMatch(line -> CartService.REASON_RESTAURANT.equals(line.reason()));
            boolean linesDown = cart.lines().stream()
                    .anyMatch(line -> !line.available() && !CartService.REASON_RESTAURANT.equals(line.reason()));
            if (restaurantDown) {
                blockers.add(RESTAURANT_UNAVAILABLE);
            }
            if (linesDown) {
                blockers.add(LINES_UNAVAILABLE);
            }
            if (cart.address() == null) {
                blockers.add(NO_ADDRESS);
            }
        }
        List<MethodInfo> methods = Arrays.stream(PaymentMethod.values())
                .map(method -> new MethodInfo(method.name(), method.label())).toList();
        return new CheckoutResponse(cart, blockers.isEmpty(), blockers, methods, publishableKey);
    }
}
