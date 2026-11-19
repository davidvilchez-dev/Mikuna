package com.mikuna.backend.address.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Dirección de entrega de un cliente (RF-12). El propietario sale siempre de la sesión. */
@Entity
@Table(name = "direcciones", indexes = @Index(name = "idx_direcciones_cliente", columnList = "customer_id"))
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(length = 50)
    private String label;

    @Column(nullable = false, length = 150)
    private String street;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(length = 200)
    private String reference;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    /** Punto exacto de entrega marcado en el mapa; ambos o ninguno (las direcciones anteriores no lo tienen). */
    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Address() {
        // JPA
    }

    public static Address create(UUID customerId, String label, String street, String district, String reference,
            String contactPhone, Instant now) {
        return create(customerId, label, street, district, reference, contactPhone, null, null, now);
    }

    public static Address create(UUID customerId, String label, String street, String district, String reference,
            String contactPhone, Double latitude, Double longitude, Instant now) {
        Address address = new Address();
        address.customerId = customerId;
        address.createdAt = now;
        address.updatedAt = now;
        address.update(label, street, district, reference, contactPhone, latitude, longitude);
        return address;
    }

    public void update(String label, String street, String district, String reference, String contactPhone) {
        update(label, street, district, reference, contactPhone, latitude, longitude);
    }

    public void update(String label, String street, String district, String reference, String contactPhone,
            Double latitude, Double longitude) {
        this.label = blankToNull(label);
        this.street = street.trim();
        this.district = district.trim();
        this.reference = blankToNull(reference);
        this.contactPhone = blankToNull(contactPhone);
        this.latitude = latitude;
        this.longitude = longitude;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
    public String getLabel() { return label; }
    public String getStreet() { return street; }
    public String getDistrict() { return district; }
    public String getReference() { return reference; }
    public String getContactPhone() { return contactPhone; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
