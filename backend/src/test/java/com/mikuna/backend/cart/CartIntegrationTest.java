package com.mikuna.backend.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.support.IntegrationTestSupport;
import com.mikuna.backend.support.TestCarts;
import com.mikuna.backend.support.TestProducts;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** RF-11, RF-12 y RF-04: carrito de un solo restaurante, elegibilidad y propiedad por cliente. */
class CartIntegrationTest extends IntegrationTestSupport {

    private static final String CART = "/api/customer/cart";

    private Account customer;
    private String token;
    private UUID restaurantId;
    private UUID lomo;
    private UUID cebiche;

    @BeforeEach
    void prepare() throws Exception {
        customer = customer("cliente@mail.com");
        token = loginAndGetToken("cliente@mail.com");
        restaurantId = TestProducts.approvedRestaurant(jdbc, restaurantOwner("local@mail.com").getId(),
                "La Picantería", "Criolla", "Ayacucho");
        lomo = TestProducts.product(jdbc, restaurantId, "Lomo saltado", "Fondos", "22.50", true, true);
        cebiche = TestProducts.product(jdbc, restaurantId, "Cebiche", "Entradas", "25.00", true, true);
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            String bearer, String body) throws Exception {
        builder = builder.header("Authorization", "Bearer " + bearer);
        return mvc.perform(body == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions add(String bearer, UUID product, int quantity) throws Exception {
        return send(post(CART + "/lines"), bearer,
                "{\"productId\":\"%s\",\"quantity\":%d}".formatted(product, quantity));
    }

    private String lineId(String body, int index) {
        return JsonPath.read(body, "$.lines[" + index + "].id");
    }

    private int lineCount() {
        return jdbc.queryForObject("select count(*) from lineas_carrito", Integer.class);
    }

    @Test
    void agregarCreaElCarritoConElRestauranteYPersisteLaLinea() throws Exception {
        add(token, lomo, 2).andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurant.name").value("La Picantería"))
                .andExpect(jsonPath("$.restaurant.attention").value("OPEN"))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].name").value("Lomo saltado"))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(45.0))
                .andExpect(jsonPath("$.lines[0].available").value(true));

        assertThat(jdbc.queryForObject("select restaurant_id from carritos where customer_id = ?", UUID.class,
                customer.getId())).isEqualTo(restaurantId);
        assertThat(jdbc.queryForObject("select quantity from lineas_carrito", Integer.class)).isEqualTo(2);
    }

    @Test
    void agregarElMismoProductoSumaLaCantidad() throws Exception {
        add(token, lomo, 2).andExpect(status().isOk());
        add(token, lomo, 3).andExpect(status().isOk()).andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].quantity").value(5));

        assertThat(lineCount()).isEqualTo(1);
    }

    @Test
    void cambiarCantidadEliminarYVaciar() throws Exception {
        String body = add(token, lomo, 1).andReturn().getResponse().getContentAsString();
        body = add(token, cebiche, 1).andReturn().getResponse().getContentAsString();
        String first = lineId(body, 0);
        String second = lineId(body, 1);

        send(put(CART + "/lines/" + first), token, "{\"quantity\":3}").andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].quantity").value(3));
        send(delete(CART + "/lines/" + second), token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(1));
        send(delete(CART), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.lines.length()").value(0))
                .andExpect(jsonPath("$.restaurant").doesNotExist());

        assertThat(lineCount()).isZero();
        assertThat(jdbc.queryForObject("select restaurant_id from carritos", UUID.class)).isNull();
    }

    @Test
    void cantidadesInvalidasSeRechazanSinModificarElCarrito() throws Exception {
        String body = add(token, lomo, 2).andReturn().getResponse().getContentAsString();
        String line = lineId(body, 0);

        for (String quantity : new String[] {"0", "-1", "21", "1.5", "\"dos\"", "null"}) {
            send(put(CART + "/lines/" + line), token, "{\"quantity\":" + quantity + "}")
                    .andExpect(status().isBadRequest());
        }
        add(token, lomo, 21).andExpect(status().isBadRequest());
        add(token, lomo, 0).andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select quantity from lineas_carrito", Integer.class)).isEqualTo(2);
    }

    @Test
    void sumarMasDeVeinteUnidadesDeUnProductoEsConflicto() throws Exception {
        add(token, lomo, 18).andExpect(status().isOk());

        add(token, lomo, 3).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CART_LIMIT"));

        assertThat(jdbc.queryForObject("select quantity from lineas_carrito", Integer.class)).isEqualTo(18);
    }

    @Test
    void elCarritoEsDeUnSoloRestaurante() throws Exception {
        UUID otherRestaurant = TestProducts.approvedRestaurant(jdbc, restaurantOwner("otro@mail.com").getId(),
                "Pizzería", "Pizza", "Ayacucho");
        UUID pizza = TestProducts.product(jdbc, otherRestaurant, "Pizza", "Pizzas", "30.00", true, true);
        add(token, lomo, 1).andExpect(status().isOk());

        add(token, pizza, 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_OTHER_RESTAURANT"));
        assertThat(lineCount()).isEqualTo(1);

        send(delete(CART), token, null).andExpect(status().isOk());
        add(token, pizza, 1).andExpect(status().isOk()).andExpect(jsonPath("$.restaurant.name").value("Pizzería"));
    }

    @Test
    void productosNoDisponiblesORetiradosOInexistentesSeRechazan() throws Exception {
        UUID unavailable = TestProducts.product(jdbc, restaurantId, "Agotado", "Fondos", "10.00", false, true);
        UUID withdrawn = TestProducts.product(jdbc, restaurantId, "Retirado", "Fondos", "10.00", true, false);

        add(token, unavailable, 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_ORDERABLE"));
        add(token, withdrawn, 1).andExpect(status().isNotFound());
        add(token, UUID.randomUUID(), 1).andExpect(status().isNotFound());

        assertThat(lineCount()).isZero();
    }

    @Test
    void restaurantesNoElegiblesNoPermitenAgregar() throws Exception {
        jdbc.update("update restaurantes set available = false where id = ?", restaurantId);
        add(token, lomo, 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_ORDERABLE"));

        jdbc.update("update restaurantes set available = true where id = ?", restaurantId);
        TestProducts.closeAllDays(jdbc, restaurantId);
        add(token, lomo, 1).andExpect(status().isConflict());

        TestProducts.approvedRestaurant(jdbc, restaurantOwner("otro@mail.com").getId(), "Pendiente", "X", "Ayacucho");
        UUID pending = jdbc.queryForObject("select id from restaurantes where name = 'Pendiente'", UUID.class);
        jdbc.update("update restaurantes set status = 'PENDING' where id = ?", pending);
        UUID pendingProduct = TestProducts.product(jdbc, pending, "Plato", "Fondos", "10.00", true, true);
        add(token, pendingProduct, 1).andExpect(status().isConflict());

        UUID disabledOwnerRestaurant = TestProducts.approvedRestaurant(jdbc,
                restaurantOwner("inhab@mail.com").getId(), "Inhabilitado", "X", "Ayacucho");
        jdbc.update("update cuentas set status = 'DISABLED' where email = 'inhab@mail.com'");
        UUID plate = TestProducts.product(jdbc, disabledOwnerRestaurant, "Plato 2", "Fondos", "10.00", true, true);
        add(token, plate, 1).andExpect(status().isConflict());

        assertThat(lineCount()).isZero();
    }

    @Test
    void lineasDeOtroClienteSeResponden404SinCambios() throws Exception {
        String body = add(token, lomo, 2).andReturn().getResponse().getContentAsString();
        String line = lineId(body, 0);
        customer("otro@mail.com");
        String other = loginAndGetToken("otro@mail.com");

        send(put(CART + "/lines/" + line), other, "{\"quantity\":5}").andExpect(status().isNotFound());
        send(delete(CART + "/lines/" + line), other, null).andExpect(status().isNotFound());
        send(get(CART), other, null).andExpect(status().isOk()).andExpect(jsonPath("$.lines.length()").value(0));

        assertThat(jdbc.queryForObject("select quantity from lineas_carrito", Integer.class)).isEqualTo(2);
    }

    @Test
    void camposQueElClienteNoPuedeEnviarSeRechazan() throws Exception {
        for (String extra : new String[] {"\"customerId\":\"" + UUID.randomUUID() + "\"",
                "\"restaurantId\":\"" + restaurantId + "\"", "\"price\":0.01", "\"unitPrice\":0.01",
                "\"total\":0.01", "\"subtotal\":0.01"}) {
            send(post(CART + "/lines"), token,
                    "{\"productId\":\"%s\",\"quantity\":1,%s}".formatted(lomo, extra))
                    .andExpect(status().isBadRequest());
        }
        assertThat(lineCount()).isZero();
    }

    @Test
    void seleccionarUnaDireccionPropiaEnZona() throws Exception {
        UUID address = TestCarts.address(jdbc, customer.getId(), "Jr. Lima 123", "Ayacucho");

        send(put(CART + "/address"), token, "{\"addressId\":\"%s\"}".formatted(address)).andExpect(status().isOk())
                .andExpect(jsonPath("$.address.id").value(address.toString()))
                .andExpect(jsonPath("$.address.district").value("Ayacucho"));

        assertThat(jdbc.queryForObject("select selected_address_id from carritos", UUID.class)).isEqualTo(address);
    }

    @Test
    void seleccionarUnaDireccionAjenaOFueraDeZonaSeRechaza() throws Exception {
        Account other = customer("otro@mail.com");
        UUID foreign = TestCarts.address(jdbc, other.getId(), "Av. Ajena 9", "Ayacucho");
        UUID far = TestCarts.address(jdbc, customer.getId(), "Jr. Lejos 1", "Huanta");

        send(put(CART + "/address"), token, "{\"addressId\":\"%s\"}".formatted(foreign))
                .andExpect(status().isNotFound());
        send(put(CART + "/address"), token, "{\"addressId\":\"%s\"}".formatted(far))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.district").exists());
        send(put(CART + "/address"), token, "{}").andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select count(*) from carritos where selected_address_id is not null",
                Integer.class)).isZero();
    }

    @Test
    void eliminarLaDireccionSeleccionadaDejaElCarritoSinDireccion() throws Exception {
        UUID address = TestCarts.address(jdbc, customer.getId(), "Jr. Lima 123", "Ayacucho");
        send(put(CART + "/address"), token, "{\"addressId\":\"%s\"}".formatted(address)).andExpect(status().isOk());

        send(delete("/api/customer/addresses/" + address), token, null).andExpect(status().isNoContent());

        send(get(CART), token, null).andExpect(status().isOk()).andExpect(jsonPath("$.address").doesNotExist());
        assertThat(jdbc.queryForObject("select selected_address_id from carritos", UUID.class)).isNull();
    }

    @Test
    void vaciarElCarritoConservaLaDireccionSeleccionada() throws Exception {
        UUID address = TestCarts.address(jdbc, customer.getId(), "Jr. Lima 123", "Ayacucho");
        add(token, lomo, 1).andExpect(status().isOk());
        send(put(CART + "/address"), token, "{\"addressId\":\"%s\"}".formatted(address)).andExpect(status().isOk());

        send(delete(CART), token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.address.id").value(address.toString()));
    }

    @Test
    void sinSesionResponde401() throws Exception {
        mvc.perform(get(CART)).andExpect(status().isUnauthorized());
        mvc.perform(post(CART + "/lines").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"%s\",\"quantity\":1}".formatted(lomo))).andExpect(status().isUnauthorized());
        mvc.perform(delete(CART)).andExpect(status().isUnauthorized());
        mvc.perform(put(CART + "/address").contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":\"%s\"}".formatted(UUID.randomUUID()))).andExpect(status().isUnauthorized());
    }

    @Test
    void otrosRolesResponden403() throws Exception {
        registrationService.registerPublic("Repartidor", "rep@mail.com", PASSWORD, PASSWORD, Role.COURIER);
        admin("admin@mail.com");

        for (String email : new String[] {"local@mail.com", "rep@mail.com", "admin@mail.com"}) {
            String other = loginAndGetToken(email);
            send(get(CART), other, null).andExpect(status().isForbidden());
            add(other, lomo, 1).andExpect(status().isForbidden());
            send(delete(CART), other, null).andExpect(status().isForbidden());
        }
        assertThat(lineCount()).isZero();
    }

    @Test
    void unaCuentaInhabilitadaResponde403() throws Exception {
        disable(customer.getId());

        send(get(CART), token, null).andExpect(status().isForbidden());
        add(token, lomo, 1).andExpect(status().isForbidden());
        assertThat(lineCount()).isZero();
    }
}
