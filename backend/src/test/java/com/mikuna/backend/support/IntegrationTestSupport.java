package com.mikuna.backend.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.jayway.jsonpath.JsonPath;
import com.mikuna.backend.catalog.service.CloudinaryImageStorage;
import com.mikuna.backend.payment.service.StripeGateway;
import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.account.repository.AccountRepository;
import com.mikuna.backend.account.service.AccountRegistrationService;
import com.mikuna.backend.auth.service.LoginAttemptLimiter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Base de las pruebas de integración contra mikuna_test_db (PostgreSQL local). */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    protected static final String PASSWORD = "contraseña-segura";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected AccountRepository accounts;

    @Autowired
    protected AccountRegistrationService registrationService;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private LoginAttemptLimiter loginAttempts;

    /** Cloudinary nunca se llama en las pruebas; por defecto devuelve una imagen fija. */
    @MockitoBean
    protected CloudinaryImageStorage imageStorage;

    /** Stripe nunca se llama en las pruebas (spec 006); por defecto autoriza con un intento fijo. */
    @MockitoBean
    protected StripeGateway stripeGateway;

    @BeforeEach
    void cleanDatabase() {
        when(stripeGateway.authorize(anyLong(), any(), any(), any(), any()))
                .thenReturn(new StripeGateway.Authorization(StripeGateway.Outcome.REQUIRES_CAPTURE, "pi_test_1",
                        null, null));
        when(imageStorage.upload(any(), any()))
                .thenReturn(new CloudinaryImageStorage.StoredImage("https://img.test/p.jpg", "mikuna/products/p"));
        loginAttempts.reset();
        jdbc.update("delete from acciones_admin_cuenta");
        jdbc.update("delete from calificaciones");
        jdbc.update("delete from avisos");
        jdbc.update("delete from hitos_entrega");
        jdbc.update("delete from ubicaciones_repartidor");
        jdbc.update("delete from rechazos_entrega");
        jdbc.update("delete from entregas");
        jdbc.update("delete from repartidores_disponibilidad");
        jdbc.update("delete from cambios_estado_pedido");
        jdbc.update("delete from transacciones_pago");
        jdbc.update("delete from pagos");
        jdbc.update("delete from lineas_pedido");
        jdbc.update("delete from pedidos");
        jdbc.update("delete from lineas_carrito");
        jdbc.update("delete from carritos");
        jdbc.update("delete from direcciones");
        jdbc.update("delete from productos");
        jdbc.update("delete from horarios_restaurante");
        jdbc.update("delete from restaurantes");
        accounts.deleteAll();
    }

    protected String loginAndGetToken(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    protected Account admin(String email) {
        return registrationService.createAdmin("Responsable", email, PASSWORD);
    }

    protected Account customer(String email) {
        return registrationService.registerPublic("Cliente", email, PASSWORD, PASSWORD, Role.CUSTOMER);
    }

    protected Account restaurantOwner(String email) {
        return registrationService.registerPublic("Dueño", email, PASSWORD, PASSWORD, Role.RESTAURANT);
    }

    protected static String validRestaurantJson() {
        return """
                {"name":"La Picantería","category":"Criolla","description":"Comida casera",
                 "address":"Jr. Lima 123","zone":"Ayacucho","phone":"+51 966 123 456"}""";
    }

    protected void disable(UUID accountId) {
        jdbc.update("update cuentas set status = 'DISABLED' where id = ?", accountId);
    }
}
