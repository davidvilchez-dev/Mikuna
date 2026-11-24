package com.mikuna.backend.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import com.mikuna.backend.assistant.dto.DishSuggestion;
import com.mikuna.backend.assistant.dto.OpenRestaurant;
import com.mikuna.backend.assistant.service.AssistantCatalogTools;
import com.mikuna.backend.assistant.service.AssistantModel.ToolCall;
import com.mikuna.backend.catalog.model.Product;
import com.mikuna.backend.catalog.repository.ProductRepository;
import com.mikuna.backend.restaurant.model.Restaurant;
import com.mikuna.backend.restaurant.repository.RestaurantRepository;
import com.mikuna.backend.restaurant.service.OrderEligibility;
import com.mikuna.backend.support.TestRestaurants;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** US2/US3: las herramientas solo entregan datos reales y públicos, con reloj fijo (lunes 15:00 en Lima). */
@ExtendWith(MockitoExtension.class)
class AssistantCatalogToolsTest {

    @Mock RestaurantRepository restaurants;
    @Mock ProductRepository products;

    private AssistantCatalogTools tools;
    private final List<Restaurant> visible = new ArrayList<>();
    private final List<Product> dishes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-16T20:00:00Z"), ZoneOffset.UTC);
        tools = new AssistantCatalogTools(restaurants, products, new OrderEligibility(clock, "America/Lima"), clock,
                "America/Lima");
        lenient().when(restaurants.findVisible()).thenReturn(visible);
        lenient().when(products.findByRestaurantIdInAndActiveTrueAndAvailableTrue(any())).thenReturn(dishes);
    }

    private Restaurant restaurant(String name, String category, boolean available) {
        Restaurant restaurant = TestRestaurants.approvedOpenOnMonday(UUID.randomUUID(), available);
        ReflectionTestUtils.setField(restaurant, "name", name);
        ReflectionTestUtils.setField(restaurant, "category", category);
        visible.add(restaurant);
        return restaurant;
    }

    private Product dish(Restaurant restaurant, String name, String description, String category, String price) {
        Product product = Product.create(restaurant.getId(), name, description, category, new BigDecimal(price),
                "https://img.test/x.jpg", "x", Instant.parse("2026-11-16T10:00:00Z"));
        dishes.add(product);
        return product;
    }

    @Test
    void respetaPrecioMaximoInclusivoYOrdenaPorPrecio() {
        Restaurant r = restaurant("Verde", "Saludable", true);
        dish(r, "Ensalada vegetariana", "Con quinua", "Ensaladas", "18.00");
        dish(r, "Hamburguesa vegetariana", "De lentejas", "Fondos", "20.00");
        dish(r, "Pizza vegetariana", "De verduras", "Pizzas", "28.00");

        List<DishSuggestion> result = tools.searchDishes("vegetariana", "", new BigDecimal("20"), "", 10);

        assertThat(result).extracting(DishSuggestion::name)
                .containsExactly("Ensalada vegetariana", "Hamburguesa vegetariana");
        assertThat(result.get(0).restaurant()).isEqualTo("Verde");
        assertThat(result.get(0).open()).isTrue();
    }

    @Test
    void busquedaSinTildesNiMayusculasEnNombreDescripcionYCategoria() {
        Restaurant r = restaurant("Verde", "Saludable", true);
        dish(r, "Ají de gallina", "Cremoso", "Fondos", "15.00");
        dish(r, "Torta", "Con MANÍ", "Postres", "9.00");

        assertThat(tools.searchDishes("aji", "", null, "", 10)).extracting(DishSuggestion::name)
                .containsExactly("Ají de gallina");
        assertThat(tools.searchDishes("mani", "", null, "", 10)).extracting(DishSuggestion::name)
                .containsExactly("Torta");
        assertThat(tools.searchDishes("", "postres", null, "", 10)).extracting(DishSuggestion::name)
                .containsExactly("Torta");
    }

    @Test
    void sinRestaurantesVisiblesNoConsultaPlatosYDevuelveVacio() {
        assertThat(tools.searchDishes("", "", null, "", 10)).isEmpty();
    }

    @Test
    void filtraPorRestauranteYLimitaA10() {
        Restaurant a = restaurant("Casa Norte", "Criolla", true);
        Restaurant b = restaurant("Casa Sur", "Criolla", true);
        for (int i = 0; i < 15; i++) {
            dish(a, "Plato " + i, "d", "Fondos", "10." + String.format("%02d", i));
        }
        dish(b, "Plato sur", "d", "Fondos", "5.00");

        assertThat(tools.searchDishes("", "", null, "", 10)).hasSize(10);
        assertThat(tools.searchDishes("", "", null, "casa sur", 10)).extracting(DishSuggestion::name)
                .containsExactly("Plato sur");
    }

    @Test
    void indicaSiElRestauranteEstaAbierto() {
        Restaurant pausado = restaurant("Pausa", "Criolla", false);
        dish(pausado, "Lomo", "d", "Fondos", "20.00");

        assertThat(tools.searchDishes("lomo", "", null, "", 10)).singleElement()
                .satisfies(d -> assertThat(d.open()).isFalse());
    }

    @Test
    void recortaLaDescripcionA200() {
        Restaurant r = restaurant("Verde", "Saludable", true);
        dish(r, "Plato largo", "x".repeat(500), "Fondos", "10.00");

        assertThat(tools.searchDishes("", "", null, "", 10).get(0).description()).hasSize(200);
    }

    @Test
    void soloListaRestaurantesAbiertosYSuHorarioDeHoy() {
        restaurant("Abierto", "Pollería", true);
        restaurant("Pausado", "Pollería", false);

        List<OpenRestaurant> open = tools.openRestaurants("");

        assertThat(open).singleElement().satisfies(r -> {
            assertThat(r.name()).isEqualTo("Abierto");
            assertThat(r.todaySchedule()).isEqualTo("09:00-21:00");
        });
        assertThat(tools.openRestaurants("polleria")).hasSize(1);
        assertThat(tools.openRestaurants("pizza")).isEmpty();
    }

    @Test
    void sinRestaurantesAbiertosDevuelveListaVacia() {
        restaurant("Pausado", "Pollería", false);

        assertThat(tools.openRestaurants("")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeInterpretaArgumentosYNuncaLanzaConArgumentosRaros() {
        Restaurant r = restaurant("Verde", "Saludable", true);
        dish(r, "Ensalada", "d", "Ensaladas", "12.00");

        Map<String, Object> ok = tools.execute(new ToolCall("buscar_platos",
                Map.of("texto", "ensalada", "precio_maximo", 15, "limite", 3), null));
        Map<String, Object> raros = tools.execute(new ToolCall("buscar_platos",
                Map.of("texto", 42, "precio_maximo", "caro", "limite", -4), null));

        assertThat((List<DishSuggestion>) ok.get("platos")).hasSize(1);
        assertThat((List<DishSuggestion>) raros.get("platos")).hasSize(1);
        assertThat(ok).containsKey("nota");
    }

    @Test
    void executeRestaurantesCategoriasYHerramientaDesconocida() {
        Restaurant r = restaurant("Verde", "Saludable", true);
        dish(r, "Ensalada", "d", "Ensaladas", "12.00");

        assertThat(tools.execute(new ToolCall("restaurantes_abiertos", null, null))).containsKey("restaurantes");
        Map<String, Object> categories = tools.execute(new ToolCall("listar_categorias", Map.of(), null));
        assertThat(categories.get("categorias_de_platos")).isEqualTo(List.of("Ensaladas"));
        assertThat(categories.get("categorias_de_restaurantes")).isEqualTo(List.of("Saludable"));
        assertThat(tools.execute(new ToolCall("borrar_todo", Map.of(), null))).containsKey("error");
        assertThat(tools.execute(new ToolCall(null, Map.of(), null))).containsKey("error");
    }

    @Test
    void sinRestaurantesListarCategoriasDevuelveListasVacias() {
        Map<String, Object> categories = tools.execute(new ToolCall("listar_categorias", Map.of(), null));

        assertThat(categories.get("categorias_de_platos")).isEqualTo(List.of());
    }

    @Test
    void declaraTresHerramientasDeSoloLectura() {
        assertThat(tools.declarations()).extracting(d -> d.name())
                .containsExactly("buscar_platos", "restaurantes_abiertos", "listar_categorias");
    }
}
