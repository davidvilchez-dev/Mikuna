package com.mikuna.backend.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mikuna.backend.catalog.dto.AvailabilityRequest;
import com.mikuna.backend.catalog.dto.ProductRequest;
import com.mikuna.backend.catalog.dto.ProductResponse;
import com.mikuna.backend.catalog.model.Product;
import com.mikuna.backend.catalog.repository.ProductRepository;
import com.mikuna.backend.catalog.service.CloudinaryImageStorage.StoredImage;
import com.mikuna.backend.common.exception.ImageUnavailableException;
import com.mikuna.backend.common.exception.ProductConflictException;
import com.mikuna.backend.common.exception.ProductNotFoundException;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.common.exception.RestaurantNotFoundException;
import com.mikuna.backend.common.exception.RestaurantStateException;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.model.RestaurantStatus;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import com.mikuna.backend.support.TestProducts;
import com.mikuna.backend.support.TestRestaurants;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

/** RF-08: reglas de producto, pertenencia, versión, imagen y duplicados (colaboradores simulados). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T15:00:00Z");
    private static final UUID OWNER = UUID.randomUUID();
    private static final StoredImage STORED = new StoredImage("https://img.test/new.jpg", "mikuna/products/new");

    @Mock ProductRepository products;
    @Mock RestaurantRepository restaurants;
    @Mock ImageValidator imageValidator;
    @Mock CloudinaryImageStorage storage;

    private ProductService service;
    private Restaurant restaurant;
    private final MockMultipartFile image = new MockMultipartFile("image", "p.jpg", "image/jpeg", TestProducts.JPEG);

    @BeforeEach
    void setUp() {
        service = new ProductService(products, restaurants, imageValidator, storage,
                Clock.fixed(NOW, ZoneOffset.UTC));
        restaurant = TestRestaurants.restaurant(OWNER, RestaurantStatus.APPROVED);
        when(restaurants.findByOwnerId(OWNER)).thenReturn(Optional.of(restaurant));
        when(imageValidator.validate(any())).thenReturn(TestProducts.JPEG);
        when(storage.upload(any(), anyString())).thenReturn(STORED);
        when(products.saveAndFlush(any(Product.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static ProductRequest request(String name, String category, String description, String price, Long version) {
        return new ProductRequest(name, category, description, price == null ? null : new BigDecimal(price), version);
    }

    private static ProductRequest valid() {
        return request("  Ají de gallina ", " Platos de fondo ", " Con arroz ", "18.5", null);
    }

    private Product existing(long version) {
        Product product = Product.create(restaurant.getId(), "Lomo saltado", null, "Fondos", new BigDecimal("20.00"),
                "https://img.test/old.jpg", "mikuna/products/old", NOW);
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(product, "version", version);
        when(products.findByIdAndRestaurantIdAndActiveTrue(product.getId(), restaurant.getId()))
                .thenReturn(Optional.of(product));
        return product;
    }

    @Test
    void creaUnProductoDisponibleConDatosRecortadosYLaImagenSubida() {
        ProductResponse response = service.create(OWNER, valid(), image);

        assertThat(response.name()).isEqualTo("Ají de gallina");
        assertThat(response.category()).isEqualTo("Platos de fondo");
        assertThat(response.description()).isEqualTo("Con arroz");
        assertThat(response.price()).isEqualByComparingTo("18.50");
        assertThat(response.available()).isTrue();
        assertThat(response.imageUrl()).isEqualTo(STORED.secureUrl());
        verify(products).saveAndFlush(any(Product.class));
    }

    @Test
    void elProductoNuevoSeAsociaAlRestauranteDelTokenYLasClavesIgnoranTildesYMayusculas() {
        service.create(OWNER, valid(), image);
        org.mockito.ArgumentCaptor<Product> saved = org.mockito.ArgumentCaptor.forClass(Product.class);
        verify(products).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRestaurantId()).isEqualTo(restaurant.getId());
        assertThat(Product.key("  ÁJÍ De Gallina ")).isEqualTo("aji de gallina");
        assertThat(saved.getValue().isActive()).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "name|X|Fondos|NULL|10.00",
            "name|NULL|Fondos|NULL|10.00",
            "category|Lomo|F|NULL|10.00",
            "category|Lomo|NULL|NULL|10.00",
            "price|Lomo|Fondos|NULL|0",
            "price|Lomo|Fondos|NULL|-5",
            "price|Lomo|Fondos|NULL|10000",
            "price|Lomo|Fondos|NULL|1.234",
            "price|Lomo|Fondos|NULL|NULL"})
    void rechazaDatosInvalidosConElCampoCorrespondiente(String field, String name, String category,
            String description, String price) {
        assertThatThrownBy(() -> service.create(OWNER, request(name, category, description, price, null), image))
                .isInstanceOfSatisfying(RegistrationValidationException.class,
                        ex -> assertThat(ex.getField()).isEqualTo(field));
        verifyNoInteractions(storage);
        verify(products, never()).saveAndFlush(any());
    }

    @Test
    void validaLosLimitesExactosDeNombreCategoriaDescripcionYPrecio() {
        assertThat(service.create(OWNER, request("ab", "ab", "d".repeat(300), "0.01", null), image).price())
                .isEqualByComparingTo("0.01");
        assertThat(service.create(OWNER, request("n".repeat(100), "c".repeat(50), null, "9999.99", null), image).price())
                .isEqualByComparingTo("9999.99");
        assertThat(service.create(OWNER, request("Lomo", "Fondos", null, "10.500", null), image).price())
                .isEqualByComparingTo("10.50");
        assertThatThrownBy(() -> service.create(OWNER, request("n".repeat(101), "Fondos", null, "5", null), image))
                .isInstanceOf(RegistrationValidationException.class);
        assertThatThrownBy(() -> service.create(OWNER, request("Lomo", "c".repeat(51), null, "5", null), image))
                .isInstanceOf(RegistrationValidationException.class);
        assertThatThrownBy(() -> service.create(OWNER, request("Lomo", "Fondos", "d".repeat(301), "5", null), image))
                .isInstanceOfSatisfying(RegistrationValidationException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("description"));
        assertThatThrownBy(() -> service.create(OWNER, null, image)).isInstanceOf(RegistrationValidationException.class);
    }

    @Test
    void alCrearNoSeAdmiteLaVersion() {
        assertThatThrownBy(() -> service.create(OWNER, request("Lomo", "Fondos", null, "5", 0L), image))
                .isInstanceOfSatisfying(RegistrationValidationException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("version"));
    }

    @Test
    void sinRestauranteDa404YPendienteORechazadoDa403() {
        when(restaurants.findByOwnerId(OWNER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(OWNER, valid(), image)).isInstanceOf(RestaurantNotFoundException.class);

        for (RestaurantStatus status : new RestaurantStatus[] {RestaurantStatus.PENDING, RestaurantStatus.REJECTED}) {
            when(restaurants.findByOwnerId(OWNER)).thenReturn(Optional.of(TestRestaurants.restaurant(OWNER, status)));
            assertThatThrownBy(() -> service.create(OWNER, valid(), image)).isInstanceOf(RestaurantStateException.class);
            assertThatThrownBy(() -> service.list(OWNER, 0, 20)).isInstanceOf(RestaurantStateException.class);
        }
        verifyNoInteractions(storage);
    }

    @Test
    void duplicadoAlGuardarDa409YBorraLaImagenRecienSubida() {
        when(products.saveAndFlush(any(Product.class))).thenThrow(new DataIntegrityViolationException("uk"));

        assertThatThrownBy(() -> service.create(OWNER, valid(), image))
                .isInstanceOfSatisfying(ProductConflictException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ProductConflictException.DUPLICATE));
        verify(storage).delete(STORED.publicId());
    }

    @Test
    void otroFalloAlGuardarTambienBorraLaImagenYSePropaga() {
        when(products.saveAndFlush(any(Product.class))).thenThrow(new IllegalStateException("boom"));
        assertThatThrownBy(() -> service.create(OWNER, valid(), image)).isInstanceOf(IllegalStateException.class);
        verify(storage).delete(STORED.publicId());
    }

    @Test
    void siFallaLaSubidaNoSeGuardaNada() {
        when(storage.upload(any(), anyString())).thenThrow(new ImageUnavailableException());
        assertThatThrownBy(() -> service.create(OWNER, valid(), image)).isInstanceOf(ImageUnavailableException.class);
        verify(products, never()).saveAndFlush(any());
    }

    @Test
    void editarConVersionDesfasadaDa409SinTocarNada() {
        Product product = existing(3);
        assertThatThrownBy(() -> service.update(OWNER, product.getId(), request("Lomo", "Fondos", null, "21", 2L), null))
                .isInstanceOfSatisfying(ProductConflictException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ProductConflictException.STALE));
        assertThatThrownBy(() -> service.update(OWNER, product.getId(), request("Lomo", "Fondos", null, "21", null), null))
                .isInstanceOfSatisfying(RegistrationValidationException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("version"));
        verify(products, never()).saveAndFlush(any());
    }

    @Test
    void editarSinImagenConservaLaAnteriorYConImagenNuevaBorraLaVieja() {
        Product product = existing(1);
        ProductResponse kept = service.update(OWNER, product.getId(), request("Lomo", "Fondos", null, "21", 1L), null);
        assertThat(kept.imageUrl()).isEqualTo("https://img.test/old.jpg");
        verifyNoInteractions(storage);

        ProductResponse replaced = service.update(OWNER, product.getId(), request("Lomo", "Fondos", null, "22", 1L), image);
        assertThat(replaced.imageUrl()).isEqualTo(STORED.secureUrl());
        verify(storage).delete("mikuna/products/old");
        verify(storage, never()).delete(STORED.publicId());
    }

    @Test
    void editarConChoqueDeVersionOCopiaDuplicadaBorraLaImagenNuevaYDa409() {
        Product product = existing(1);
        when(products.saveAndFlush(any(Product.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Product.class, product.getId()));
        assertThatThrownBy(() -> service.update(OWNER, product.getId(), request("Lomo", "Fondos", null, "21", 1L), image))
                .isInstanceOfSatisfying(ProductConflictException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ProductConflictException.STALE));

        when(products.saveAndFlush(any(Product.class))).thenThrow(new DataIntegrityViolationException("uk"));
        assertThatThrownBy(() -> service.update(OWNER, product.getId(), request("Lomo", "Fondos", null, "21", 1L), image))
                .isInstanceOfSatisfying(ProductConflictException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ProductConflictException.DUPLICATE));

        when(products.saveAndFlush(any(Product.class))).thenThrow(new IllegalStateException("boom"));
        assertThatThrownBy(() -> service.update(OWNER, product.getId(), request("Lomo", "Fondos", null, "21", 1L), image))
                .isInstanceOf(IllegalStateException.class);
        verify(storage, org.mockito.Mockito.times(3)).delete(STORED.publicId());
        verify(storage, never()).delete("mikuna/products/old");
    }

    @Test
    void unProductoAjenoRetiradoOInexistenteEs404() {
        UUID other = UUID.randomUUID();
        when(products.findByIdAndRestaurantIdAndActiveTrue(other, restaurant.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(OWNER, other)).isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> service.update(OWNER, other, valid(), null)).isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> service.setAvailability(OWNER, other, new AvailabilityRequest(true, 0L)))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> service.withdraw(OWNER, other, 0L)).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void disponibilidadExigeLaVersionVigenteYElValor() {
        Product product = existing(2);
        assertThat(service.setAvailability(OWNER, product.getId(), new AvailabilityRequest(false, 2L)).available()).isFalse();
        assertThat(service.setAvailability(OWNER, product.getId(), new AvailabilityRequest(true, 2L)).available()).isTrue();
        assertThatThrownBy(() -> service.setAvailability(OWNER, product.getId(), new AvailabilityRequest(false, 1L)))
                .isInstanceOf(ProductConflictException.class);
        assertThatThrownBy(() -> service.setAvailability(OWNER, product.getId(), new AvailabilityRequest(null, 2L)))
                .isInstanceOf(RegistrationValidationException.class);
        assertThatThrownBy(() -> service.setAvailability(OWNER, product.getId(), null))
                .isInstanceOf(RegistrationValidationException.class);
    }

    @Test
    void retirarMarcaElProductoInactivoYNoBorraLaImagen() {
        Product product = existing(0);
        service.withdraw(OWNER, product.getId(), 0L);
        assertThat(product.isActive()).isFalse();
        assertThat(product.getWithdrawnAt()).isEqualTo(NOW);
        verify(storage, never()).delete(anyString());

        assertThatThrownBy(() -> service.withdraw(OWNER, product.getId(), 9L)).isInstanceOf(ProductConflictException.class);
        assertThatThrownBy(() -> service.withdraw(OWNER, product.getId(), null))
                .isInstanceOf(RegistrationValidationException.class);
    }

    @Test
    void siElGuardadoDetectaVersionDesfasadaRetirarYDisponibilidadDan409() {
        Product product = existing(0);
        when(products.saveAndFlush(any(Product.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Product.class, product.getId()));
        assertThatThrownBy(() -> service.withdraw(OWNER, product.getId(), 0L)).isInstanceOf(ProductConflictException.class);
        assertThatThrownBy(() -> service.setAvailability(OWNER, product.getId(), new AvailabilityRequest(false, 0L)))
                .isInstanceOf(ProductConflictException.class);
    }

    @Test
    void listaValidaLaPaginaYSoloConsultaElRestauranteDelToken() {
        assertThatThrownBy(() -> service.list(OWNER, -1, 20)).isInstanceOf(RegistrationValidationException.class);
        assertThatThrownBy(() -> service.list(OWNER, 0, 0)).isInstanceOf(RegistrationValidationException.class);
        assertThatThrownBy(() -> service.list(OWNER, 0, 51)).isInstanceOf(RegistrationValidationException.class);

        Product one = existing(0);
        when(products.findByRestaurantIdAndActiveTrue(any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(one)));
        assertThat(service.list(OWNER, 0, 20).items()).hasSize(1);
        verify(products).findByRestaurantIdAndActiveTrue(any(), any());
    }
}
