package com.mikuna.backend.restaurant.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Restaurante de una cuenta de rol RESTAURANT. El estado se guarda como texto plano (sin CHECK de
 * Hibernate); las transiciones de estado se hacen con actualizaciones condicionales del repositorio.
 */
@Entity
@Table(name = "restaurantes",
        uniqueConstraints = @UniqueConstraint(name = "uk_restaurantes_owner", columnNames = "owner_id"))
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(length = 300)
    private String description;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false, length = 100)
    private String zone;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "rejection_reason", length = 300)
    private String rejectionReason;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(nullable = false)
    private boolean available;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "logo_public_id", length = 200)
    private String logoPublicId;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(name = "cover_public_id", length = 200)
    private String coverPublicId;

    @Version
    private long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "horarios_restaurante",
            joinColumns = @JoinColumn(name = "restaurante_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_horarios_dia",
                    columnNames = {"restaurante_id", "day_of_week"}))
    private List<OpeningHour> schedule = new ArrayList<>();

    protected Restaurant() {
        // JPA
    }

    private Restaurant(UUID ownerId, String name, String category, String description, String address,
            String zone, String phone, Instant now) {
        this.ownerId = ownerId;
        this.status = RestaurantStatus.PENDING.name();
        this.submittedAt = now;
        this.available = false;
        updateGeneralData(name, category, description, address, zone, phone);
    }

    /** Nueva solicitud: siempre pendiente y no disponible. */
    public static Restaurant request(UUID ownerId, String name, String category, String description,
            String address, String zone, String phone, Instant now) {
        return new Restaurant(ownerId, name, category, description, address, zone, phone, now);
    }

    public void updateGeneralData(String name, String category, String description, String address,
            String zone, String phone) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.address = address;
        this.zone = zone;
        this.phone = phone;
    }

    public void replaceSchedule(List<OpeningHour> days) {
        this.schedule.clear();
        this.schedule.addAll(days);
    }

    public void changeLogo(String url, String publicId) {
        this.logoUrl = url;
        this.logoPublicId = publicId;
    }

    public void changeCover(String url, String publicId) {
        this.coverUrl = url;
        this.coverPublicId = publicId;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getAddress() { return address; }
    public String getZone() { return zone; }
    public String getPhone() { return phone; }
    public RestaurantStatus getStatus() { return RestaurantStatus.valueOf(status); }
    public Instant getSubmittedAt() { return submittedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public UUID getResolvedBy() { return resolvedBy; }
    public Instant getResolvedAt() { return resolvedAt; }
    public boolean isAvailable() { return available; }
    public String getLogoUrl() { return logoUrl; }
    public String getLogoPublicId() { return logoPublicId; }
    public String getCoverUrl() { return coverUrl; }
    public String getCoverPublicId() { return coverPublicId; }
    public List<OpeningHour> getSchedule() { return List.copyOf(schedule); }
}
