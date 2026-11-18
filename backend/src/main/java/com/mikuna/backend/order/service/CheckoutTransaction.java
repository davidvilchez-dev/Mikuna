package com.mikuna.backend.order.service;

import com.mikuna.backend.address.model.Address;
import com.mikuna.backend.address.repository.AddressRepository;
import com.mikuna.backend.cart.dto.CartResponse;
import com.mikuna.backend.cart.dto.TotalsResponse;
import com.mikuna.backend.cart.model.Cart;
import com.mikuna.backend.cart.service.CartService;
import com.mikuna.backend.common.exception.AddressNotFoundException;
import com.mikuna.backend.common.exception.OrderConflictException;
import com.mikuna.backend.common.exception.OrderNotFoundException;
import com.mikuna.backend.order.dto.CheckoutResponse;
import com.mikuna.backend.order.dto.ConfirmOrderRequest;
import com.mikuna.backend.order.dto.OrderResponse;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.repository.OrderRepository;
import com.mikuna.backend.payment.model.Payment;
import com.mikuna.backend.payment.model.PaymentStatus;
import com.mikuna.backend.payment.repository.PaymentRepository;
import com.mikuna.backend.payment.service.PaymentService;
import com.mikuna.backend.payment.service.StripeProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tramo transaccional de la confirmación (RF-14, RF-16), separado de {@link CheckoutService} para que el
 * duplicado de una clave se resuelva fuera de la transacción abortada (research §5 y §6). El orden es fijo:
 * bloquear el carrito, buscar el pedido por clave, validar, recalcular, autorizar el pago y guardar todo
 * junto. Si algo falla tras autorizar, se libera la reserva.
 */
@Component
public class CheckoutTransaction {

    /** Resultado de confirmar: el pedido y si se creó ahora ({@code false} es una repetición de la clave). */
    public record Confirmation(OrderResponse order, boolean created) {
    }

    private final CartService carts;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final AddressRepository addresses;
    private final PaymentService paymentService;
    private final StripeProperties stripe;
    private final Clock clock;

    public CheckoutTransaction(CartService carts, OrderRepository orders, PaymentRepository payments,
            AddressRepository addresses, PaymentService paymentService, StripeProperties stripe, Clock clock) {
        this.carts = carts;
        this.orders = orders;
        this.payments = payments;
        this.addresses = addresses;
        this.paymentService = paymentService;
        this.stripe = stripe;
        this.clock = clock;
    }

    @Transactional
    public Confirmation confirm(UUID customerId, String confirmationKey, ConfirmOrderRequest request) {
        Cart cart = carts.lockForCheckout(customerId);

        Optional<Order> existing = orders.findByCustomerIdAndConfirmationKey(customerId, confirmationKey);
        if (existing.isPresent()) {
            return replay(existing.get(), request);
        }

        CartResponse view = carts.checkoutView(cart);
        validate(cart, view, request);

        TotalsResponse totals = view.totals();
        UUID orderId = UUID.randomUUID();
        String intentId = paymentService.authorize(customerId, orderId, totals.total(), request.paymentMethodId(),
                confirmationKey);
        try {
            Address address = addresses.findByIdAndCustomerId(cart.getSelectedAddressId(), customerId)
                    .orElseThrow(AddressNotFoundException::new);
            Order order = new Order(orderId, customerId, view.restaurant().id(), view.restaurant().name(),
                    confirmationKey, request.paymentMethod(),
                    new Order.Amounts(totals.subtotal(), totals.deliveryFee(), totals.commission(), totals.total()),
                    new Order.Delivery(address.getLabel(), address.getStreet(), address.getDistrict(),
                            address.getReference(), address.getContactPhone()),
                    Instant.now(clock));
            for (CartResponse.Line line : view.lines()) {
                order.addLine(line.productId(), line.name(), line.unitPrice(), line.quantity(), line.lineTotal());
            }
            orders.saveAndFlush(order);
            paymentService.recordAuthorized(customerId, orderId, confirmationKey, totals.total(), intentId);
            carts.clearAfterCheckout(cart);
            return new Confirmation(OrderResponse.of(order, PaymentStatus.AUTHORIZED), true);
        } catch (RuntimeException e) {
            // Una clave duplicada comparte la misma reserva con el pedido ya registrado: no se libera.
            if (!isDuplicateConfirmation(e)) {
                paymentService.cancelQuietly(customerId, confirmationKey, totals.total(), intentId);
            }
            throw e;
        }
    }

    /** Devuelve el pedido ya registrado con esa clave; se usa tras un duplicado fuera de la transacción abortada. */
    @Transactional(readOnly = true)
    public Optional<Confirmation> findByKey(UUID customerId, String confirmationKey, ConfirmOrderRequest request) {
        return orders.findByCustomerIdAndConfirmationKey(customerId, confirmationKey)
                .map(order -> replay(order, request));
    }

    @Transactional(readOnly = true)
    public OrderResponse find(UUID customerId, UUID orderId) {
        Order order = orders.findByIdAndCustomerId(orderId, customerId).orElseThrow(OrderNotFoundException::new);
        return OrderResponse.of(order, paymentStatus(order));
    }

    @Transactional(readOnly = true)
    public CheckoutResponse summary(UUID customerId) {
        return CheckoutResponse.of(carts.checkoutView(customerId), stripe.publishableKey());
    }

    /** Una clave ya usada devuelve el pedido existente, salvo que el total esperado difiera del pedido. */
    private Confirmation replay(Order order, ConfirmOrderRequest request) {
        if (request.expectedTotal().compareTo(order.getTotal()) != 0) {
            throw new OrderConflictException(OrderConflictException.AMOUNTS_CHANGED,
                    "Esta confirmación ya se procesó con otro total");
        }
        return new Confirmation(OrderResponse.of(order, paymentStatus(order)), false);
    }

    private PaymentStatus paymentStatus(Order order) {
        return payments.findByOrderId(order.getId()).map(Payment::getStatus).orElse(PaymentStatus.AUTHORIZED);
    }

    private void validate(Cart cart, CartResponse view, ConfirmOrderRequest request) {
        if (view.lines().isEmpty()) {
            throw conflict(OrderConflictException.CART_EMPTY, "Tu carrito está vacío");
        }
        if (cart.getSelectedAddressId() == null) {
            throw conflict(OrderConflictException.NO_ADDRESS, "Selecciona una dirección de entrega");
        }
        if (view.address() == null) {
            throw conflict(OrderConflictException.NOT_ORDERABLE,
                    "Tu dirección ya no está en la zona de reparto; elige otra");
        }
        if (view.lines().stream().anyMatch(line -> CartService.REASON_RESTAURANT.equals(line.reason()))) {
            throw conflict(OrderConflictException.NOT_ORDERABLE, "Este restaurante no está recibiendo pedidos ahora");
        }
        List<String> unavailable = view.lines().stream().filter(line -> !line.available())
                .map(CartResponse.Line::name).toList();
        if (!unavailable.isEmpty()) {
            throw conflict(OrderConflictException.NOT_ORDERABLE,
                    "Estos productos ya no están disponibles: " + String.join(", ", unavailable));
        }
        if (view.lines().stream().anyMatch(line -> line.quantity() < 1 || line.quantity() > Cart.MAX_QUANTITY)) {
            throw conflict(OrderConflictException.NOT_ORDERABLE, "Hay productos con una cantidad no permitida");
        }
        if (request.expectedTotal().compareTo(view.totals().total()) != 0) {
            throw new OrderConflictException(OrderConflictException.AMOUNTS_CHANGED,
                    "Los importes cambiaron; revisa el resumen y confirma de nuevo",
                    CheckoutResponse.of(view, stripe.publishableKey()));
        }
    }

    private static OrderConflictException conflict(String code, String message) {
        return new OrderConflictException(code, message);
    }

    /**
     * {@code true} si la causa es la restricción única de la clave de confirmación del cliente. Se reconoce por
     * el nombre de la restricción, que PostgreSQL incluye en su mensaje sea cual sea el idioma del servidor.
     */
    static boolean isDuplicateConfirmation(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException violation && violation.getConstraintName() != null
                    && violation.getConstraintName().toLowerCase().contains(Order.UK_CONFIRMATION)) {
                return true;
            }
            if (t.getMessage() != null && t.getMessage().toLowerCase().contains(Order.UK_CONFIRMATION)) {
                return true;
            }
        }
        return false;
    }
}
