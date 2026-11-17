package com.mikuna.backend.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mikuna.backend.catalog.service.CloudinaryImageStorage.StoredImage;
import com.mikuna.backend.common.exception.ImageUnavailableException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Cliente de Cloudinary con un servidor simulado: sin red ni credenciales reales. */
class CloudinaryImageStorageTest {

    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC);
    private static final CloudinaryProperties CONFIGURED =
            new CloudinaryProperties("demo", "key123", "secret456", "mikuna/products");

    private static String sha1(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static CloudinaryImageStorage storage(CloudinaryProperties properties, MockRestServiceServer[] holder) {
        RestClient.Builder builder = RestClient.builder();
        holder[0] = MockRestServiceServer.bindTo(builder).build();
        return new CloudinaryImageStorage(builder, properties, CLOCK);
    }

    @Test
    void subeLaImagenConFirmaSha1YDevuelveUrlSeguraYPublicId() throws Exception {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        CloudinaryImageStorage storage = storage(CONFIGURED, server);
        String expectedSignature = sha1("folder=mikuna/products&timestamp=1700000000secret456");
        server[0].expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/upload"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(expectedSignature)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("key123")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret456"))))
                .andRespond(withSuccess("{\"secure_url\":\"https://res.cloudinary.com/demo/p.jpg\",\"public_id\":\"mikuna/products/p\"}",
                        MediaType.APPLICATION_JSON));

        StoredImage stored = storage.upload(new byte[] {1, 2, 3}, "p.jpg");

        assertThat(stored).isEqualTo(new StoredImage("https://res.cloudinary.com/demo/p.jpg", "mikuna/products/p"));
        server[0].verify();
    }

    @Test
    void laFirmaOrdenaLosParametrosYAnadeElSecreto() throws Exception {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        CloudinaryImageStorage storage = storage(CONFIGURED, server);
        assertThat(storage.sign(new java.util.TreeMap<>(java.util.Map.of("timestamp", "5", "folder", "f"))))
                .isEqualTo(sha1("folder=f&timestamp=5secret456"));
    }

    @Test
    void respuestaDeErrorOSinUrlSePropagaComoServicioNoDisponible() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        CloudinaryImageStorage storage = storage(CONFIGURED, server);
        server[0].expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/upload")).andRespond(withServerError());
        assertThatThrownBy(() -> storage.upload(new byte[] {1}, "p.jpg")).isInstanceOf(ImageUnavailableException.class);

        server[0].reset();
        server[0].expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/upload"))
                .andRespond(withSuccess("{\"otro\":\"dato\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> storage.upload(new byte[] {1}, "p.jpg")).isInstanceOf(ImageUnavailableException.class);
    }

    @Test
    void sinCredencialesNoSubeNiLlamaALaRed() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        CloudinaryImageStorage storage = storage(new CloudinaryProperties(null, "", " ", "f"), server);
        assertThatThrownBy(() -> storage.upload(new byte[] {1}, "p.jpg")).isInstanceOf(ImageUnavailableException.class);
        assertThatCode(() -> storage.delete("mikuna/products/p")).doesNotThrowAnyException();
        server[0].verify();
    }

    @Test
    void borraConPublicIdFirmadoYUnFalloNoSePropaga() throws Exception {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        CloudinaryImageStorage storage = storage(CONFIGURED, server);
        String expectedSignature = sha1("public_id=mikuna/products/p&timestamp=1700000000secret456");
        server[0].expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/destroy"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(expectedSignature)))
                .andRespond(withServerError());

        assertThatCode(() -> storage.delete("mikuna/products/p")).doesNotThrowAnyException();
        server[0].verify();
        assertThatCode(() -> storage.delete(null)).doesNotThrowAnyException();
    }
}
