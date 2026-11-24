package com.mikuna.backend.order.model;

import com.mikuna.backend.common.exception.InvalidOrderTransitionException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Pedido confirmado (RF-14): nace {@code PENDING} con precios, importes y dirección fijados al confirmar.
 * El identificador lo genera el servidor antes de autorizar el pago; una confirmación lógica se identifica
 * por {@code (customer_id, confirmation_key)}, única, para que un reintento no duplique el pedido.
 */
@Entity
@Table(name = "pedidos",
        uniqueConstraints = @UniqueConstraint(name = Order.UK_CONFIRMATION,
                columnNames = {"customer_id", "confirmation_key"}),
        indexes = {@Index(name = "ix_pedidos_cliente_fecha", columnList = "customer_id, created_at"),
                @Index(name = "ix_pedidos_restaurante_fecha", columnList = "restaurant_id, created_at"),
                @Index(name = "ix_pedidos_estado_fecha", columnList = "status, created_at"),
                @Index(name = "ix_pedidos_estado_vence", columnList = "status, expires_at")})
public class Order implements Persistable<UUID> {

    public static final String UK_CONFIRMATION = "uk_pedidos_cliente_clave";

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    @Column(name = "confirmation_key", nullable = false, updatable = false, length = 64)
    private String confirmationKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, updatable = false, length = 16)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal subtotal;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal deliveryFee;

    @Column(nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal commission;

    @Column(nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal total;

    @Column(name = "restaurant_name", nullable = false, updatable = false)
    private String restaurantName;

    @Column(name = "delivery_label", updatable = false)
    private String deliveryLabel;

    @Column(name = "delivery_street", nullable = false, updatable = false)
    private String deliveryStreet;

    @Column(name = "delivery_district", nullable = false, updatable = false)
    private String deliveryDistrict;

    @Column(name = "delivery_reference", updatable = false)
    private String deliveryReference;

    @Column(name = "delivery_phone", updatable = false)
    private String deliveryPhone;

    /** Punto exacto de entrega copiado de la dirección al confirmar; los pedidos anteriores no lo tienen. */
    @Column(name = "delivery_latitude", updatable = false)
    private Double deliveryLatitude;

    @Column(name = "delivery_longitude", updatable = false)
    private Double deliveryLongitude;

    /** Nota del cliente para el restaurante. */
    @Column(name = "store_note", updatable = false, length = 300)
    private String storeNote;

    /** Hora de entrega elegida; {@code null} es entrega inmediata. */
    @Column(name = "scheduled_for", updatable = false)
    private Instant scheduledFor;

    /** Quien recibe, si no es el cliente; {@code null} es "yo mismo". */
    @Column(name = "receiver_name", updatable = false, length = 80)
    private String receiverName;

    @Column(name = "receiver_phone", updatable = false, length = 20)
    private String receiverPhone;

    @Column(name = "receiver_note", updatable = false, length = 300)
    private String receiverNote;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("productName ASC, id ASC")
    private List<OrderLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Momento en que vence si sigue Pendiente (spec 012): se fija al crear como creación + plazo vigente y no
     * cambia. Nullable en BD solo por los pedidos anteriores, que se rellenan al arrancar.
     */
    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    /** El identificador se asigna en el servidor: se indica a Spring Data que la entidad es nueva. */
    @Transient
    private boolean isNew = true;

    protected Order() {
        // JPA
    }

    /** Datos de entrega copiados de la dirección al confirmar. */
    public record Delivery(String label, String street, String district, String reference, String phone,
            Double latitude, Double longitude) {

        public Delivery(String label, String street, String district, String reference, String phone) {
            this(label, street, district, reference, phone, null, null);
        }
    }

    /** Importes ya calculados y redondeados en el servidor. */
    public record Amounts(BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal commission, BigDecimal total) {
    }

    public Order(UUID id, UUID customerId, UUID restaurantId, String restaurantName, String confirmationKey,
            PaymentMethod paymentMethod, Amounts amounts, Delivery delivery, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.restaurantId = restaurantId;
        this.restaurantName = restaurantName;
        this.confirmationKey = confirmationKey;
        this.paymentMethod = paymentMethod;
        this.status = OrderStatus.PENDING;
        this.subtotal = amounts.subtotal();
        this.deliveryFee = amounts.deliveryFee();
        this.commission = amounts.commission();
        this.total = amounts.total();
        this.deliveryLabel = delivery.label();
        this.deliveryStreet = delivery.street();
        this.deliveryDistrict = delivery.district();
        this.deliveryReference = delivery.reference();
        this.deliveryPhone = delivery.phone();
        this.deliveryLatitude = delivery.latitude();
        this.deliveryLongitude = delivery.longitude();
        this.createdAt = createdAt;
    }

    /** Opciones elegidas en la confirmación (nota, horario, receptor); se fijan una sola vez. */
    public void applyOptions(String storeNote, Instant scheduledFor, String receiverName, String receiverPhone,
            String receiverNote) {
        this.storeNote = storeNote;
        this.scheduledFor = scheduledFor;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.receiverNote = receiverNote;
    }

    public void addLine(UUID productId, String productName, BigDecimal unitPrice, int quantity,
            BigDecimal lineTotal) {
        lines.add(new OrderLine(this, productId, productName, unitPrice, quantity, lineTotal));
    }

    /** Fija el vencimiento una sola vez, al crear el pedido. */
    public void setExpiresAt(Instant expiresAt) {
        if (this.expiresAt == null) {
            this.expiresAt = expiresAt;
        }
    }

    /** Sigue Pendiente y su plazo ya terminó. */
    public boolean isExpiredAt(Instant now) {
        return status == OrderStatus.PENDING && expiresAt != null && !now.isBefore(expiresAt);
    }

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        TRANSITIONS.put(OrderStatus.PENDING,
                EnumSet.of(OrderStatus.ACCEPTED, OrderStatus.REJECTED, OrderStatus.CANCELLED));
        TRANSITIONS.put(OrderStatus.ACCEPTED, EnumSet.of(OrderStatus.PREPARING));
        TRANSITIONS.put(OrderStatus.PREPARING, EnumSet.of(OrderStatus.READY));
        TRANSITIONS.put(OrderStatus.READY, EnumSet.of(OrderStatus.ASSIGNED));
        // ASSIGNED a READY solo lo origina el rechazo del repartidor o la liberación del administrador (spec 008).
        TRANSITIONS.put(OrderStatus.ASSIGNED, EnumSet.of(OrderStatus.PICKED_UP, OrderStatus.READY));
        TRANSITIONS.put(OrderStatus.PICKED_UP, EnumSet.of(OrderStatus.DELIVERED));
    }

    /**
     * Único punto de cambio de estado (FR-007): solo Pendiente a Aceptado/Rechazado/Cancelado, Aceptado a En
     * preparación, En preparación a Listo para recoger y, desde la spec 008, Listo a Asignado, Asignado a Recogido o
     * de vuelta a Listo (rechazo), y Recogido a Entregado. Rechazado, Cancelado y Entregado son finales.
     */
    public void transitionTo(OrderStatus target) {
        if (!TRANSITIONS.getOrDefault(status, Set.of()).contains(target)) {
            throw new InvalidOrderTransitionException();
        }
        this.status = target;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public UUID getRestaurantId() { return restaurantId; }
    public OrderStatus getStatus() { return status; }
    public String getConfirmationKey() { return confirmationKey; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public BigDecimal getCommission() { return commission; }
    public BigDecimal getTotal() { return total; }
    public String getRestaurantName() { return restaurantName; }
    public String getDeliveryLabel() { return deliveryLabel; }
    public String getDeliveryStreet() { return deliveryStreet; }
    public String getDeliveryDistrict() { return deliveryDistrict; }
    public String getDeliveryReference() { return deliveryReference; }
    public String getDeliveryPhone() { return deliveryPhone; }
    public Double getDeliveryLatitude() { return deliveryLatitude; }
    public Double getDeliveryLongitude() { return deliveryLongitude; }
    public String getStoreNote() { return storeNote; }
    public Instant getScheduledFor() { return scheduledFor; }
    public String getReceiverName() { return receiverName; }
    public String getReceiverPhone() { return receiverPhone; }
    public String getReceiverNote() { return receiverNote; }
    public List<OrderLine> getLines() { return List.copyOf(lines); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
