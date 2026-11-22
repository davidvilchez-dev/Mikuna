package com.mikuna.backend.cart.service;

import com.mikuna.backend.address.model.Address;
import com.mikuna.backend.address.repository.AddressRepository;
import com.mikuna.backend.address.service.AddressDeletedEvent;
import com.mikuna.backend.address.service.DeliveryZone;
import com.mikuna.backend.cart.dto.AddLineRequest;
import com.mikuna.backend.cart.dto.CartResponse;
import com.mikuna.backend.cart.dto.TotalsResponse;
import com.mikuna.backend.cart.model.Cart;
import com.mikuna.backend.cart.model.CartLine;
import com.mikuna.backend.cart.repository.CartRepository;
import com.mikuna.backend.catalog.model.Product;
import com.mikuna.backend.catalog.repository.ProductRepository;
import com.mikuna.backend.common.exception.AddressNotFoundException;
import com.mikuna.backend.common.exception.CartConflictException;
import com.mikuna.backend.common.exception.CartLineNotFoundException;
import com.mikuna.backend.common.exception.OutsideDeliveryZoneException;
import com.mikuna.backend.common.exception.ProductNotFoundException;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import com.mikuna.backend.restaurant.service.OrderEligibility;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carrito del cliente (RF-11, RF-12, RF-13). Toda mutación bloquea la fila del carrito del cliente, de modo
 * que solicitudes simultáneas no pierden ni duplican líneas ni mezclan restaurantes. El propietario sale
 * siempre del token; los precios, del catálogo vigente.
 */
@Service
@Transactional
public class CartService {

    public static final String REASON_WITHDRAWN = "PRODUCT_WITHDRAWN";
    public static final String REASON_UNAVAILABLE = "PRODUCT_UNAVAILABLE";
    public static final String REASON_RESTAURANT = "RESTAURANT_UNAVAILABLE";

    private final CartRepository carts;
    private final ProductRepository products;
    private final RestaurantRepository restaurants;
    private final AddressRepository addresses;
    private final DeliveryZone zone;
    private final OrderEligibility eligibility;
    private final CartPricing pricing;
    private final Clock clock;

    public CartService(CartRepository carts, ProductRepository products, RestaurantRepository restaurants,
            AddressRepository addresses, DeliveryZone zone, OrderEligibility eligibility, CartPricing pricing,
            Clock clock) {
        this.carts = carts;
        this.products = products;
        this.restaurants = restaurants;
        this.addresses = addresses;
        this.zone = zone;
        this.eligibility = eligibility;
        this.pricing = pricing;
        this.clock = clock;
    }

    /** Consulta el carrito y recalcula los importes; un cliente sin carrito ve uno vacío. */
    public CartResponse get(UUID customerId) {
        Optional<Cart> cart = carts.findByCustomerId(customerId);
        return cart.map(this::view).orElseGet(this::emptyView);
    }

    public CartResponse addLine(UUID customerId, AddLineRequest request) {
        Cart cart = loadOrCreateLocked(customerId);
        int quantity = request.quantity();

        Product product = products.findById(request.productId()).filter(Product::isActive)
                .orElseThrow(ProductNotFoundException::new);
        Restaurant restaurant = restaurants.findVisibleById(product.getRestaurantId()).orElseThrow(() ->
                notOrderable("Este restaurante no está disponible para pedidos"));
        if (!product.isAvailable()) {
            throw notOrderable("Este producto no está disponible");
        }
        if (!eligibility.canReceiveOrders(restaurant, true)) {
            throw notOrderable("Este restaurante no está recibiendo pedidos ahora");
        }
        if (cart.getRestaurantId() != null && !cart.getRestaurantId().equals(restaurant.getId())
                && !cart.getLines().isEmpty()) {
            throw new CartConflictException(CartConflictException.OTHER_RESTAURANT,
                    "Tu carrito es de otro restaurante. Vacíalo para empezar un pedido con este");
        }
        Optional<CartLine> existing = cart.findLineByProduct(product.getId());
        int resulting = existing.map(CartLine::getQuantity).orElse(0) + quantity;
        if (resulting > Cart.MAX_QUANTITY) {
            throw new CartConflictException(CartConflictException.LIMIT,
                    "Puedes pedir hasta " + Cart.MAX_QUANTITY + " unidades de cada producto");
        }
        if (existing.isEmpty() && cart.getLines().size() >= Cart.MAX_LINES) {
            throw new CartConflictException(CartConflictException.LIMIT,
                    "Tu carrito admite hasta " + Cart.MAX_LINES + " productos distintos");
        }
        cart.addOrIncrease(restaurant.getId(), product.getId(), quantity, Instant.now(clock));
        return view(carts.save(cart));
    }

    public CartResponse changeQuantity(UUID customerId, UUID lineId, int quantity) {
        Cart cart = loadOrCreateLocked(customerId);
        CartLine line = cart.findLine(lineId).orElseThrow(CartLineNotFoundException::new);
        line.setQuantity(quantity);
        return view(carts.save(cart));
    }

    public CartResponse removeLine(UUID customerId, UUID lineId) {
        Cart cart = loadOrCreateLocked(customerId);
        CartLine line = cart.findLine(lineId).orElseThrow(CartLineNotFoundException::new);
        cart.removeLine(line);
        return view(carts.save(cart));
    }

    /** Vacía el carrito y conserva la dirección seleccionada. */
    public CartResponse clear(UUID customerId) {
        Cart cart = loadOrCreateLocked(customerId);
        cart.clear();
        return view(carts.save(cart));
    }

    public CartResponse selectAddress(UUID customerId, UUID addressId) {
        Cart cart = loadOrCreateLocked(customerId);
        Address address = addresses.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(AddressNotFoundException::new);
        if (!zone.covers(address.getDistrict())) {
            throw new OutsideDeliveryZoneException();
        }
        cart.selectAddress(address.getId());
        return view(carts.save(cart));
    }

    /** Al eliminar una dirección, el carrito deja de seleccionarla (misma transacción que la baja). */
    @EventListener
    void onAddressDeleted(AddressDeletedEvent event) {
        carts.clearSelection(event.customerId(), event.addressId());
    }

    private Cart loadOrCreateLocked(UUID customerId) {
        carts.insertIfAbsent(UUID.randomUUID(), customerId, Instant.now(clock));
        return carts.lockByCustomerId(customerId).orElseThrow();
    }

    private static CartConflictException notOrderable(String message) {
        return new CartConflictException(CartConflictException.NOT_ORDERABLE, message);
    }

    /** Bloquea y devuelve el carrito del cliente para confirmarlo (spec 006); crea uno vacío si no existe. */
    public Cart lockForCheckout(UUID customerId) {
        return loadOrCreateLocked(customerId);
    }

    /**
     * Vista vigente del carrito para el checkout: igual que {@link #get} pero sin retirar la dirección fuera de
     * zona (la confirmación debe rechazarla, no limpiarla en silencio). No modifica el carrito.
     */
    public CartResponse checkoutView(Cart cart) {
        return view(cart, false);
    }

    /** Resumen del checkout de un cliente sin bloquear su carrito (consulta informativa). */
    public CartResponse checkoutView(UUID customerId) {
        return carts.findByCustomerId(customerId).map(this::checkoutView).orElseGet(this::emptyView);
    }

    /** Vacía el carrito tras confirmar el pedido y conserva la dirección seleccionada. */
    public void clearAfterCheckout(Cart cart) {
        cart.clear();
        carts.save(cart);
    }

    private CartResponse emptyView() {
        return new CartResponse(null, List.of(), null, pricing.compute(List.of(), false), List.of());
    }

    /** Construye la vista con los datos vigentes: elegibilidad por línea, dirección revalidada e importes. */
    private CartResponse view(Cart cart) {
        return view(cart, true);
    }

    private CartResponse view(Cart cart, boolean dropInvalidAddress) {
        List<String> notices = new ArrayList<>();
        List<CartLine> lines = cart.getLines();

        Map<UUID, Product> catalog = products.findAllById(lines.stream().map(CartLine::getProductId).toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        Optional<Restaurant> visible = cart.getRestaurantId() == null ? Optional.empty()
                : restaurants.findVisibleById(cart.getRestaurantId());
        boolean restaurantOk = visible.map(r -> eligibility.canReceiveOrders(r, true)).orElse(false);

        List<CartResponse.Line> responseLines = new ArrayList<>();
        List<BigDecimal> eligibleTotals = new ArrayList<>();
        for (CartLine line : lines) {
            Product product = catalog.get(line.getProductId());
            String reason = reasonOf(product, restaurantOk);
            BigDecimal lineTotal = null;
            if (reason == null) {
                lineTotal = CartPricing.lineTotal(product.getPrice(), line.getQuantity());
                eligibleTotals.add(lineTotal);
            } else {
                notices.add("«" + product.getName() + "» no está disponible y no se incluye en el total");
            }
            responseLines.add(new CartResponse.Line(line.getId(), line.getProductId(), product.getName(),
                    product.getImageUrl(), product.getPrice(), line.getQuantity(), lineTotal, reason == null,
                    reason));
        }

        CartResponse.AddressInfo address = resolveAddress(cart, notices, dropInvalidAddress);
        TotalsResponse totals = pricing.compute(eligibleTotals, address != null);
        return new CartResponse(restaurantInfo(cart, visible), responseLines, address, totals, notices);
    }

    private static String reasonOf(Product product, boolean restaurantOk) {
        if (!product.isActive()) {
            return REASON_WITHDRAWN;
        }
        if (!product.isAvailable()) {
            return REASON_UNAVAILABLE;
        }
        return restaurantOk ? null : REASON_RESTAURANT;
    }

    private CartResponse.RestaurantInfo restaurantInfo(Cart cart, Optional<Restaurant> visible) {
        if (cart.getRestaurantId() == null) {
            return null;
        }
        if (visible.isPresent()) {
            Restaurant r = visible.get();
            return new CartResponse.RestaurantInfo(r.getId(), r.getName(), eligibility.attentionStatus(r));
        }
        return restaurants.findById(cart.getRestaurantId())
                .map(r -> new CartResponse.RestaurantInfo(r.getId(), r.getName(), null)).orElse(null);
    }

    /** Dirección seleccionada vigente; si ya no existe o salió de la zona, se retira de la selección. */
    private CartResponse.AddressInfo resolveAddress(Cart cart, List<String> notices, boolean dropInvalid) {
        UUID selected = cart.getSelectedAddressId();
        if (selected == null) {
            return null;
        }
        Optional<Address> address = addresses.findByIdAndCustomerId(selected, cart.getCustomerId());
        if (address.isPresent() && zone.covers(address.get().getDistrict())) {
            return new CartResponse.AddressInfo(address.get().getId(), address.get().getStreet(),
                    address.get().getDistrict(), address.get().getLatitude(), address.get().getLongitude());
        }
        if (dropInvalid) {
            cart.clearAddress();
            carts.save(cart);
        }
        notices.add("Tu dirección seleccionada ya no está en la zona de reparto; elige otra");
        return null;
    }
}
