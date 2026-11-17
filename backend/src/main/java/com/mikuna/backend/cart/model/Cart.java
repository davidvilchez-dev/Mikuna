package com.mikuna.backend.cart.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Carrito de un cliente (RF-11): uno por cliente y, mientras tenga líneas, de un único restaurante. No guarda
 * precios ni importes: se calculan al consultar con los datos vigentes.
 */
@Entity
@Table(name = "carritos",
        uniqueConstraints = @UniqueConstraint(name = "uk_carritos_cliente", columnNames = "customer_id"))
public class Cart {

    public static final int MAX_QUANTITY = 20;
    public static final int MAX_LINES = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "restaurant_id")
    private UUID restaurantId;

    @Column(name = "selected_address_id")
    private UUID selectedAddressId;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("addedAt ASC, id ASC")
    private List<CartLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Cart() {
        // JPA
    }

    public static Cart create(UUID customerId, Instant now) {
        Cart cart = new Cart();
        cart.customerId = customerId;
        cart.createdAt = now;
        cart.updatedAt = now;
        return cart;
    }

    public Optional<CartLine> findLine(UUID lineId) {
        return lines.stream().filter(line -> line.getId() != null && line.getId().equals(lineId)).findFirst();
    }

    public Optional<CartLine> findLineByProduct(UUID productId) {
        return lines.stream().filter(line -> line.getProductId().equals(productId)).findFirst();
    }

    /** Suma a la línea del producto o crea una nueva; fija el restaurante del carrito. */
    public void addOrIncrease(UUID restaurant, UUID productId, int quantity, Instant now) {
        Optional<CartLine> existing = findLineByProduct(productId);
        if (existing.isPresent()) {
            existing.get().setQuantity(existing.get().getQuantity() + quantity);
        } else {
            lines.add(new CartLine(this, productId, quantity, now));
        }
        this.restaurantId = restaurant;
    }

    public void removeLine(CartLine line) {
        lines.remove(line);
        if (lines.isEmpty()) {
            restaurantId = null;
        }
    }

    /** Vacía las líneas y suelta el restaurante; la dirección seleccionada se conserva. */
    public void clear() {
        lines.clear();
        restaurantId = null;
    }

    public void selectAddress(UUID addressId) {
        this.selectedAddressId = addressId;
    }

    public void clearAddress() {
        this.selectedAddressId = null;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getSelectedAddressId() { return selectedAddressId; }
    public List<CartLine> getLines() { return List.copyOf(lines); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
