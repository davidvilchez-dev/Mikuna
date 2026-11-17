package com.mikuna.backend.catalog.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Producto del catálogo de un restaurante (RF-08). El retiro es lógico y definitivo: {@code active}
 * pasa a {@code null}, de modo que la restricción única solo afecta a los productos activos
 * (PostgreSQL trata los NULL como distintos).
 */
@Entity
@Table(name = "productos",
        uniqueConstraints = @UniqueConstraint(name = "uk_productos_activo",
                columnNames = {"restaurant_id", "name_key", "category_key", "active"}),
        indexes = @Index(name = "idx_productos_restaurante", columnList = "restaurant_id, active, available"))
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "name_key", nullable = false, length = 100)
    private String nameKey;

    @Column(length = 300)
    private String description;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "category_key", nullable = false, length = 50)
    private String categoryKey;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "image_public_id", nullable = false, length = 200)
    private String imagePublicId;

    @Column(nullable = false)
    private boolean available;

    @Column
    private Boolean active;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Product() {
        // JPA
    }

    private Product(UUID restaurantId, Instant now) {
        this.restaurantId = restaurantId;
        this.available = true;
        this.active = Boolean.TRUE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Producto nuevo: siempre activo y disponible. */
    public static Product create(UUID restaurantId, String name, String description, String category,
            BigDecimal price, String imageUrl, String imagePublicId, Instant now) {
        Product product = new Product(restaurantId, now);
        product.update(name, description, category, price);
        product.changeImage(imageUrl, imagePublicId);
        return product;
    }

    public void update(String name, String description, String category, BigDecimal price) {
        this.name = name;
        this.nameKey = key(name);
        this.description = description;
        this.category = category;
        this.categoryKey = key(category);
        this.price = price;
    }

    public void changeImage(String url, String publicId) {
        this.imageUrl = url;
        this.imagePublicId = publicId;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    /** Retiro lógico y definitivo; libera el nombre dentro de su categoría. */
    public void withdraw(Instant now) {
        this.active = null;
        this.withdrawnAt = now;
    }

    /** Minúsculas y sin tildes: base de la unicidad de nombre y categoría. */
    public static String key(String text) {
        String decomposed = Normalizer.normalize(text.trim(), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public String getImageUrl() { return imageUrl; }
    public String getImagePublicId() { return imagePublicId; }
    public boolean isAvailable() { return available; }
    public boolean isActive() { return Boolean.TRUE.equals(active); }
    public Instant getWithdrawnAt() { return withdrawnAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
