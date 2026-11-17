package com.mikuna.backend.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.address.model.Address;
import com.mikuna.backend.address.repository.AddressRepository;
import com.mikuna.backend.address.service.AddressDeletedEvent;
import com.mikuna.backend.address.service.DeliveryZone;
import com.mikuna.backend.cart.dto.AddLineRequest;
import com.mikuna.backend.cart.dto.CartResponse;
import com.mikuna.backend.cart.model.Cart;
import com.mikuna.backend.cart.repository.CartRepository;
import com.mikuna.backend.catalog.model.Product;
import com.mikuna.backend.catalog.repository.ProductRepository;
import com.mikuna.backend.common.config.DeliveryProperties;
import com.mikuna.backend.common.exception.AddressNotFoundException;
import com.mikuna.backend.common.exception.CartConflictException;
import com.mikuna.backend.common.exception.CartLineNotFoundException;
import com.mikuna.backend.common.exception.OutsideDeliveryZoneException;
import com.mikuna.backend.common.exception.ProductNotFoundException;
import com.mikuna.backend.restaurant.model.AttentionStatus;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import com.mikuna.backend.restaurant.service.OrderEligibility;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** RF-11, RF-12 y RF-13: reglas del carrito con repositorios simulados y reloj fijo. */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-17T15:00:00Z");

    @Mock CartRepository carts;
    @Mock ProductRepository products;
    @Mock RestaurantRepository restaurants;
    @Mock AddressRepository addresses;
    @Mock OrderEligibility eligibility;

    private CartService service;
    private Cart cart;
    private Restaurant restaurant;
    private UUID restaurantId;
    private final Map<UUID, Product> catalog = new HashMap<>();

    @BeforeEach
    void setUp() {
        DeliveryProperties props = new DeliveryProperties(List.of("Ayacucho"), new BigDecimal("5.00"),
                new BigDecimal("5"));
        service = new CartService(carts, products, restaurants, addresses, new DeliveryZone(props), eligibility,
                new CartPricing(props), Clock.fixed(NOW, ZoneOffset.UTC));
        cart = Cart.create(CUSTOMER, NOW);
        restaurantId = UUID.randomUUID();
        restaurant = mock(Restaurant.class);
        lenient().when(restaurant.getId()).thenReturn(restaurantId);
        lenient().when(restaurant.getName()).thenReturn("La Picantería");
        lenient().when(carts.lockByCustomerId(CUSTOMER)).thenReturn(Optional.of(cart));
        lenient().when(carts.findByCustomerId(CUSTOMER)).thenReturn(Optional.of(cart));
        lenient().when(products.findById(any())).thenAnswer(i -> Optional.ofNullable(catalog.get(i.<UUID>getArgument(0))));
        lenient().when(products.findAllById(any())).thenAnswer(i -> {
            List<Product> found = new ArrayList<>();
            for (UUID id : i.<Iterable<UUID>>getArgument(0)) {
                found.add(catalog.get(id));
            }
            return found;
        });
        lenient().when(carts.save(any(Cart.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(restaurants.findVisibleById(restaurantId)).thenReturn(Optional.of(restaurant));
        lenient().when(eligibility.canReceiveOrders(restaurant, true)).thenReturn(true);
        lenient().when(eligibility.attentionStatus(restaurant)).thenReturn(AttentionStatus.OPEN);
    }

    private Product product(UUID owner, String name, String price, boolean available, boolean active) {
        Product product = Product.create(owner, name, null, "Fondos", new BigDecimal(price),
                "https://img.test/p.jpg", "p", NOW);
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        product.setAvailable(available);
        if (!active) {
            product.withdraw(NOW);
        }
        catalog.put(product.getId(), product);
        return product;
    }

    private CartResponse add(Product product, int quantity) {
        CartResponse response = service.addLine(CUSTOMER, new AddLineRequest(product.getId(), quantity));
        // El identificador de las líneas lo genera JPA; en memoria se asigna para poder direccionarlas.
        cart.getLines().stream().filter(l -> l.getId() == null)
                .forEach(l -> ReflectionTestUtils.setField(l, "id", UUID.randomUUID()));
        return response;
    }

    @Test
    void agregarCreaLaLineaYFijaElRestauranteDelCarrito() {
        Product lomo = product(restaurantId, "Lomo", "22.50", true, true);

        CartResponse response = add(lomo, 2);

        assertThat(cart.getRestaurantId()).isEqualTo(restaurantId);
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().getFirst().quantity()).isEqualTo(2);
        assertThat(response.lines().getFirst().lineTotal()).isEqualByComparingTo("45.00");
        assertThat(response.restaurant().name()).isEqualTo("La Picantería");
        assertThat(response.totals().subtotal()).isEqualByComparingTo("45.00");
    }

    @Test
    void agregarUnProductoYaPresenteSumaSuCantidad() {
        Product lomo = product(restaurantId, "Lomo", "22.50", true, true);
        add(lomo, 2);

        CartResponse response = add(lomo, 3);

        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().getFirst().quantity()).isEqualTo(5);
    }

    @Test
    void sumarMasDelMaximoPorProductoSeRechazaSinModificar() {
        Product lomo = product(restaurantId, "Lomo", "22.50", true, true);
        add(lomo, 18);

        assertThatThrownBy(() -> add(lomo, 3)).isInstanceOfSatisfying(CartConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo(CartConflictException.LIMIT));
        assertThat(cart.getLines().getFirst().getQuantity()).isEqualTo(18);
    }

    @Test
    void laLineaTreintaYUnoSeRechaza() {
        for (int i = 0; i < Cart.MAX_LINES; i++) {
            add(product(restaurantId, "P" + i, "1.00", true, true), 1);
        }

        assertThatThrownBy(() -> add(product(restaurantId, "Extra", "1.00", true, true), 1))
                .isInstanceOfSatisfying(CartConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo(CartConflictException.LIMIT));
        assertThat(cart.getLines()).hasSize(Cart.MAX_LINES);
    }

    @Test
    void unProductoDeOtroRestauranteSeRechazaSinMezclar() {
        add(product(restaurantId, "Lomo", "22.50", true, true), 1);
        UUID otherId = UUID.randomUUID();
        Restaurant other = mock(Restaurant.class);
        lenient().when(other.getId()).thenReturn(otherId);
        when(restaurants.findVisibleById(otherId)).thenReturn(Optional.of(other));
        when(eligibility.canReceiveOrders(other, true)).thenReturn(true);
        Product foreign = product(otherId, "Pizza", "30.00", true, true);

        assertThatThrownBy(() -> add(foreign, 1)).isInstanceOfSatisfying(CartConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo(CartConflictException.OTHER_RESTAURANT));
        assertThat(cart.getLines()).hasSize(1);
        assertThat(cart.getRestaurantId()).isEqualTo(restaurantId);
    }

    @Test
    void productosNoElegiblesSeRechazanSinModificarElCarrito() {
        Product unavailable = product(restaurantId, "Agotado", "10.00", false, true);
        Product withdrawn = product(restaurantId, "Retirado", "10.00", true, false);

        assertThatThrownBy(() -> add(unavailable, 1)).isInstanceOfSatisfying(CartConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo(CartConflictException.NOT_ORDERABLE));
        assertThatThrownBy(() -> add(withdrawn, 1)).isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> service.addLine(CUSTOMER, new AddLineRequest(UUID.randomUUID(), 1)))
                .isInstanceOf(ProductNotFoundException.class);
        assertThat(cart.getLines()).isEmpty();
        assertThat(cart.getRestaurantId()).isNull();
    }

    @Test
    void unRestauranteNoVisibleOQueNoRecibePedidosSeRechaza() {
        UUID hiddenId = UUID.randomUUID();
        when(restaurants.findVisibleById(hiddenId)).thenReturn(Optional.empty());
        Product hidden = product(hiddenId, "Oculto", "10.00", true, true);
        when(eligibility.canReceiveOrders(restaurant, true)).thenReturn(false);
        Product closed = product(restaurantId, "Cerrado", "10.00", true, true);

        assertThatThrownBy(() -> add(hidden, 1)).isInstanceOfSatisfying(CartConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo(CartConflictException.NOT_ORDERABLE));
        assertThatThrownBy(() -> add(closed, 1)).isInstanceOfSatisfying(CartConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo(CartConflictException.NOT_ORDERABLE));
        assertThat(cart.getLines()).isEmpty();
    }

    @Test
    void cambiarLaCantidadActualizaLaLineaYSusImportes() {
        add(product(restaurantId, "Lomo", "22.50", true, true), 1);
        UUID lineId = cart.getLines().getFirst().getId();

        CartResponse response = service.changeQuantity(CUSTOMER, lineId, 4);

        assertThat(response.lines().getFirst().quantity()).isEqualTo(4);
        assertThat(response.totals().subtotal()).isEqualByComparingTo("90.00");
    }

    @Test
    void cambiarOEliminarUnaLineaInexistenteEsNoEncontrada() {
        assertThatThrownBy(() -> service.changeQuantity(CUSTOMER, UUID.randomUUID(), 2))
                .isInstanceOf(CartLineNotFoundException.class);
        assertThatThrownBy(() -> service.removeLine(CUSTOMER, UUID.randomUUID()))
                .isInstanceOf(CartLineNotFoundException.class);
    }

    @Test
    void eliminarLaUltimaLineaDejaElCarritoSinRestaurante() {
        add(product(restaurantId, "Lomo", "22.50", true, true), 1);
        UUID lineId = cart.getLines().getFirst().getId();

        CartResponse response = service.removeLine(CUSTOMER, lineId);

        assertThat(response.lines()).isEmpty();
        assertThat(response.restaurant()).isNull();
        assertThat(cart.getRestaurantId()).isNull();
        assertThat(response.totals().pending()).isEqualTo("EMPTY");
    }

    @Test
    void vaciarConservaLaDireccionSeleccionada() {
        add(product(restaurantId, "Lomo", "22.50", true, true), 1);
        UUID addressId = UUID.randomUUID();
        cart.selectAddress(addressId);
        Address address = Address.create(CUSTOMER, null, "Jr. Lima 123", "Ayacucho", null, null, NOW);
        ReflectionTestUtils.setField(address, "id", addressId);
        when(addresses.findByIdAndCustomerId(addressId, CUSTOMER)).thenReturn(Optional.of(address));

        CartResponse response = service.clear(CUSTOMER);

        assertThat(response.lines()).isEmpty();
        assertThat(cart.getRestaurantId()).isNull();
        assertThat(cart.getSelectedAddressId()).isEqualTo(addressId);
        assertThat(response.address().id()).isEqualTo(addressId);
    }

    @Test
    void seleccionarUnaDireccionPropiaEnZonaCalculaEnvioComisionYTotal() {
        add(product(restaurantId, "Lomo", "20.00", true, true), 2);
        UUID addressId = UUID.randomUUID();
        Address address = Address.create(CUSTOMER, null, "Jr. Lima 123", "Ayacucho", null, null, NOW);
        ReflectionTestUtils.setField(address, "id", addressId);
        when(addresses.findByIdAndCustomerId(addressId, CUSTOMER)).thenReturn(Optional.of(address));

        CartResponse response = service.selectAddress(CUSTOMER, addressId);

        assertThat(response.address().district()).isEqualTo("Ayacucho");
        assertThat(response.totals().subtotal()).isEqualByComparingTo("40.00");
        assertThat(response.totals().deliveryFee()).isEqualByComparingTo("5.00");
        assertThat(response.totals().commission()).isEqualByComparingTo("2.00");
        assertThat(response.totals().total()).isEqualByComparingTo("47.00");
    }

    @Test
    void seleccionarUnaDireccionAjenaOFueraDeZonaSeRechazaSinCambios() {
        UUID foreign = UUID.randomUUID();
        when(addresses.findByIdAndCustomerId(foreign, CUSTOMER)).thenReturn(Optional.empty());
        UUID farId = UUID.randomUUID();
        Address far = Address.create(CUSTOMER, null, "Jr. Lejos 1", "Huanta", null, null, NOW);
        ReflectionTestUtils.setField(far, "id", farId);
        when(addresses.findByIdAndCustomerId(farId, CUSTOMER)).thenReturn(Optional.of(far));

        assertThatThrownBy(() -> service.selectAddress(CUSTOMER, foreign)).isInstanceOf(AddressNotFoundException.class);
        assertThatThrownBy(() -> service.selectAddress(CUSTOMER, farId)).isInstanceOf(OutsideDeliveryZoneException.class);
        assertThat(cart.getSelectedAddressId()).isNull();
    }

    @Test
    void unaDireccionSeleccionadaQueSalioDeLaZonaSeRetiraConAviso() {
        add(product(restaurantId, "Lomo", "20.00", true, true), 1);
        UUID addressId = UUID.randomUUID();
        cart.selectAddress(addressId);
        Address moved = Address.create(CUSTOMER, null, "Jr. Lejos 1", "Huanta", null, null, NOW);
        ReflectionTestUtils.setField(moved, "id", addressId);
        when(addresses.findByIdAndCustomerId(addressId, CUSTOMER)).thenReturn(Optional.of(moved));

        CartResponse response = service.get(CUSTOMER);

        assertThat(response.address()).isNull();
        assertThat(cart.getSelectedAddressId()).isNull();
        assertThat(response.totals().pending()).isEqualTo("ADDRESS");
        assertThat(response.notices()).anyMatch(n -> n.contains("zona de reparto"));
    }

    @Test
    void lineasNoElegiblesSeMarcanYNoSumanAlSubtotal() {
        Product lomo = product(restaurantId, "Lomo", "20.00", true, true);
        Product cebiche = product(restaurantId, "Cebiche", "25.00", true, true);
        add(lomo, 1);
        add(cebiche, 1);
        cebiche.setAvailable(false);

        CartResponse response = service.get(CUSTOMER);

        assertThat(response.lines()).extracting(CartResponse.Line::available).containsExactly(true, false);
        assertThat(response.lines().get(1).reason()).isEqualTo("PRODUCT_UNAVAILABLE");
        assertThat(response.lines().get(1).lineTotal()).isNull();
        assertThat(response.totals().subtotal()).isEqualByComparingTo("20.00");
        assertThat(response.notices()).anyMatch(n -> n.contains("Cebiche"));
    }

    @Test
    void productoRetiradoYRestauranteNoElegibleTienenSuPropioMotivo() {
        Product lomo = product(restaurantId, "Lomo", "20.00", true, true);
        add(lomo, 1);
        lomo.withdraw(NOW);

        assertThat(service.get(CUSTOMER).lines().getFirst().reason()).isEqualTo("PRODUCT_WITHDRAWN");

        Product pizza = product(restaurantId, "Pizza", "20.00", true, true);
        cart.clear();
        add(pizza, 1);
        when(eligibility.canReceiveOrders(restaurant, true)).thenReturn(false);

        CartResponse response = service.get(CUSTOMER);
        assertThat(response.lines().getFirst().reason()).isEqualTo("RESTAURANT_UNAVAILABLE");
        assertThat(response.totals().pending()).isEqualTo("EMPTY");
    }

    @Test
    void unClienteSinCarritoVeUnoVacio() {
        UUID newcomer = UUID.randomUUID();
        when(carts.findByCustomerId(newcomer)).thenReturn(Optional.empty());

        CartResponse response = service.get(newcomer);

        assertThat(response.lines()).isEmpty();
        assertThat(response.restaurant()).isNull();
        assertThat(response.totals().total()).isEqualByComparingTo("0.00");
    }

    @Test
    void alEliminarUnaDireccionElCarritoDejaDeSeleccionarla() {
        UUID addressId = UUID.randomUUID();

        service.onAddressDeleted(new AddressDeletedEvent(CUSTOMER, addressId));

        verify(carts).clearSelection(CUSTOMER, addressId);
    }

    @Test
    void sinLineasNoHayInformacionDeRestauranteAunqueElCarritoExista() {
        CartResponse response = service.get(CUSTOMER);

        assertThat(response.restaurant()).isNull();
        assertThat(response.lines()).isEmpty();
    }

    @Test
    void elRestauranteNoVisibleDelCarritoSeMuestraSinSituacionDeAtencion() {
        Product lomo = product(restaurantId, "Lomo", "20.00", true, true);
        add(lomo, 1);
        when(restaurants.findVisibleById(restaurantId)).thenReturn(Optional.empty());
        when(restaurants.findById(restaurantId)).thenReturn(Optional.of(restaurant));

        CartResponse response = service.get(CUSTOMER);

        assertThat(response.restaurant().name()).isEqualTo("La Picantería");
        assertThat(response.restaurant().attention()).isNull();
        assertThat(response.lines().getFirst().reason()).isEqualTo("RESTAURANT_UNAVAILABLE");
    }
}
