package com.mikuna.backend.address;

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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** RF-12 y RF-04: libreta de direcciones del cliente, dentro de la zona y solo las propias. */
class AddressIntegrationTest extends IntegrationTestSupport {

    private static final String URL = "/api/customer/addresses";

    private Account customer;
    private String token;

    @BeforeEach
    void prepare() throws Exception {
        customer = customer("cliente@mail.com");
        token = loginAndGetToken("cliente@mail.com");
    }

    private static String json(String street, String district) {
        return """
                {"label":"Casa","street":"%s","district":"%s","reference":"Frente al parque","contactPhone":"987654321"}"""
                .formatted(street, district);
    }

    private ResultActions create(String bearer, String body) throws Exception {
        return mvc.perform(post(URL).header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void registraUnaDireccionDentroDeLaZona() throws Exception {
        create(token, json("Jr. Lima 123", "Ayacucho")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.street").value("Jr. Lima 123"))
                .andExpect(jsonPath("$.district").value("Ayacucho"))
                .andExpect(jsonPath("$.customerId").doesNotExist());

        assertThat(jdbc.queryForObject("select count(*) from direcciones where customer_id = ?", Integer.class,
                customer.getId())).isEqualTo(1);
    }

    @Test
    void guardaYDevuelveElPuntoMarcadoEnElMapa() throws Exception {
        String body = """
                {"label":"Casa","street":"Psje. Tarapaca","district":"Ayacucho","reference":"","contactPhone":"",
                 "latitude":-13.158812,"longitude":-74.223310}""";
        create(token, body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.latitude").value(-13.158812))
                .andExpect(jsonPath("$.longitude").value(-74.22331));

        mvc.perform(get(URL).header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].latitude").value(-13.158812));
    }

    @Test
    void aceptaCoordenadasNulasYRechazaUnPuntoIncompletoOFueraDeRango() throws Exception {
        create(token, """
                {"street":"Jr. Lima 123","district":"Ayacucho","latitude":null,"longitude":null}""")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.latitude").doesNotExist());
        create(token, """
                {"street":"Jr. Lima 123","district":"Ayacucho","latitude":-13.15}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.locationComplete").exists());
        create(token, """
                {"street":"Jr. Lima 123","district":"Ayacucho","latitude":123,"longitude":-74.2}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.latitude").exists());
    }

    @Test
    void elDistritoSeComparaSinTildesNiMayusculas() throws Exception {
        create(token, json("Jr. Lima 123", "san juan BAUTISTA")).andExpect(status().isCreated());
    }

    @Test
    void rechazaUnDistritoFueraDeLaZonaSinGuardar() throws Exception {
        create(token, json("Jr. Lima 123", "Huanta")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.district").value("Esa dirección está fuera de la zona de reparto"));

        assertThat(jdbc.queryForObject("select count(*) from direcciones", Integer.class)).isZero();
    }

    @Test
    void rechazaDatosInvalidosExplicandoCadaError() throws Exception {
        create(token, """
                {"street":"abc","district":"","contactPhone":"12ab"}""").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.street").exists())
                .andExpect(jsonPath("$.errors.district").exists())
                .andExpect(jsonPath("$.errors.contactPhone").exists());
        create(token, json("x".repeat(151), "Ayacucho")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.street").exists());

        assertThat(jdbc.queryForObject("select count(*) from direcciones", Integer.class)).isZero();
    }

    @Test
    void rechazaCamposDesconocidosComoElClienteOElPropietario() throws Exception {
        create(token, """
                {"street":"Jr. Lima 123","district":"Ayacucho","customerId":"%s"}""".formatted(UUID.randomUUID()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listaSoloLasDireccionesPropias() throws Exception {
        Account other = customer("otro@mail.com");
        TestCarts.address(jdbc, customer.getId(), "Jr. Lima 123", "Ayacucho");
        TestCarts.address(jdbc, other.getId(), "Av. Ajena 9", "Ayacucho");

        String body = mvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Jr. Lima 123").doesNotContain("Av. Ajena 9");
    }

    @Test
    void editaYEliminaUnaDireccionPropia() throws Exception {
        UUID id = TestCarts.address(jdbc, customer.getId(), "Jr. Lima 123", "Ayacucho");

        mvc.perform(put(URL + "/" + id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json("Av. Nueva 55", "Ayacucho")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.street").value("Av. Nueva 55"));
        mvc.perform(put(URL + "/" + id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json("Av. Nueva 55", "Huanta")))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select street from direcciones where id = ?", String.class, id))
                .isEqualTo("Av. Nueva 55");

        mvc.perform(delete(URL + "/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from direcciones", Integer.class)).isZero();
    }

    @Test
    void unaDireccionAjenaSeResponde404SinCambios() throws Exception {
        Account other = customer("otro@mail.com");
        UUID foreign = TestCarts.address(jdbc, other.getId(), "Av. Ajena 9", "Ayacucho");

        mvc.perform(put(URL + "/" + foreign).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json("Hackeada 123", "Ayacucho")))
                .andExpect(status().isNotFound());
        mvc.perform(delete(URL + "/" + foreign).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("select street from direcciones where id = ?", String.class, foreign))
                .isEqualTo("Av. Ajena 9");
    }

    @Test
    void laUndecimaDireccionSeRechazaConCodigo() throws Exception {
        for (int i = 0; i < 10; i++) {
            TestCarts.address(jdbc, customer.getId(), "Jr. Lima " + (100 + i), "Ayacucho");
        }

        create(token, json("Jr. Once 111", "Ayacucho")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADDRESS_LIMIT"));
    }

    @Test
    void sinSesionResponde401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json("Jr. Lima 123", "Ayacucho")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void otrosRolesResponden403() throws Exception {
        restaurantOwner("local@mail.com");
        registrationService.registerPublic("Repartidor", "rep@mail.com", PASSWORD, PASSWORD, Role.COURIER);
        admin("admin@mail.com");

        for (String email : new String[] {"local@mail.com", "rep@mail.com", "admin@mail.com"}) {
            String other = loginAndGetToken(email);
            mvc.perform(get(URL).header("Authorization", "Bearer " + other)).andExpect(status().isForbidden());
            create(other, json("Jr. Lima 123", "Ayacucho")).andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("select count(*) from direcciones", Integer.class)).isZero();
    }

    @Test
    void unaCuentaInhabilitadaResponde403() throws Exception {
        disable(customer.getId());

        mvc.perform(get(URL).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        create(token, json("Jr. Lima 123", "Ayacucho")).andExpect(status().isForbidden());
    }

    @Test
    void elIdDeLaRespuestaPermiteConsultarla() throws Exception {
        String body = create(token, json("Jr. Lima 123", "Ayacucho")).andReturn().getResponse().getContentAsString();

        assertThat(UUID.fromString(JsonPath.read(body, "$.id"))).isNotNull();
    }
}
