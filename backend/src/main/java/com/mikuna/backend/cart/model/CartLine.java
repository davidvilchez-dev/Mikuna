package com.mikuna.backend.cart.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/** Línea del carrito: producto y cantidad. El precio no se guarda; se lee del producto vigente. */
@Entity
@Table(name = "lineas_carrito",
        uniqueConstraints = @UniqueConstraint(name = "uk_lineas_carrito_producto",
                columnNames = {"cart_id", "product_id"}))
public class CartLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected CartLine() {
        // JPA
    }

    CartLine(Cart cart, UUID productId, int quantity, Instant addedAt) {
        this.cart = cart;
        this.productId = productId;
        this.quantity = quantity;
        this.addedAt = addedAt;
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public Instant getAddedAt() { return addedAt; }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
