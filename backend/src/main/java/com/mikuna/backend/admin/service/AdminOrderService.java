package com.mikuna.backend.admin.service;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.repository.AccountRepository;
import com.mikuna.backend.admin.dto.AdminDeliveryDetail;
import com.mikuna.backend.admin.dto.AdminDeliverySummary;
import com.mikuna.backend.admin.dto.AdminOrderDetail;
import com.mikuna.backend.admin.dto.AdminOrderSummary;
import com.mikuna.backend.admin.dto.AdminOrderSummary.Party;
import com.mikuna.backend.admin.dto.StatusChangeEntry;
import com.mikuna.backend.admin.repository.AdminOrderQueryRepository;
import com.mikuna.backend.catalog.dto.PageResponse;
import com.mikuna.backend.common.exception.OrderNotFoundException;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.delivery.model.Delivery;
import com.mikuna.backend.delivery.repository.DeliveryRepository;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.model.OrderStatusChange;
import com.mikuna.backend.order.repository.OrderStatusChangeRepository;
import com.mikuna.backend.payment.model.Payment;
import com.mikuna.backend.payment.repository.PaymentRepository;
import com.mikuna.backend.payment.repository.PaymentTransactionRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta administrativa de pedidos y entregas (RF-33), solo lectura (FR-018): listados filtrados por estado,
 * paginados y del más reciente al más antiguo, y detalle con estado de pago y cambios de estado.
 */
@Service
@Transactional(readOnly = true)
public class AdminOrderService {

    /** Estados en los que existe una entrega: nace al quedar el pedido Listo para recoger (spec 008). */
    static final Set<OrderStatus> DELIVERY_STATUSES =
            EnumSet.of(OrderStatus.READY, OrderStatus.ASSIGNED, OrderStatus.PICKED_UP, OrderStatus.DELIVERED);

    private final AdminOrderQueryRepository queries;
    private final AccountRepository accounts;
    private final PaymentRepository payments;
    private final DeliveryRepository deliveries;
    private final OrderStatusChangeRepository changes;
    private final PaymentTransactionRepository transactions;

    public AdminOrderService(AdminOrderQueryRepository queries, AccountRepository accounts,
            PaymentRepository payments, DeliveryRepository deliveries, OrderStatusChangeRepository changes,
            PaymentTransactionRepository transactions) {
        this.transactions = transactions;
        this.queries = queries;
        this.accounts = accounts;
        this.payments = payments;
        this.deliveries = deliveries;
        this.changes = changes;
    }

    public PageResponse<AdminOrderSummary> listOrders(String status, Integer page, Integer size) {
        Pageable pageable = AdminPaging.pageable(page, size);
        OrderStatus filter = AdminPaging.enumFilter("status", status, OrderStatus.class);
        Collection<OrderStatus> statuses = filter == null ? EnumSet.allOf(OrderStatus.class) : List.of(filter);
        Page<Order> result = queries.listOrders(statuses, pageable);
        Map<UUID, Account> people = accountsById(result.getContent().stream().map(Order::getCustomerId).toList());
        Map<UUID, Payment> paid = paymentsByOrder(result.getContent().stream().map(Order::getId).toList());
        return PageResponse.from(result, order -> summary(order, people.get(order.getCustomerId()),
                paid.get(order.getId())));
    }

    public PageResponse<AdminDeliverySummary> listDeliveries(String status, Integer page, Integer size) {
        Pageable pageable = AdminPaging.pageable(page, size);
        OrderStatus filter = AdminPaging.enumFilter("status", status, OrderStatus.class);
        if (filter != null && !DELIVERY_STATUSES.contains(filter)) {
            throw new RegistrationValidationException("status", "Estado no válido para una entrega");
        }
        Collection<OrderStatus> statuses = filter == null ? DELIVERY_STATUSES : List.of(filter);
        Page<Delivery> result = queries.listDeliveries(statuses, pageable);
        List<UUID> orderIds = result.getContent().stream().map(Delivery::getOrderId).toList();
        Map<UUID, Order> orders = queries.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        Map<UUID, Account> people = accountsById(result.getContent().stream().map(Delivery::getCourierId)
                .filter(Objects::nonNull).toList());
        return PageResponse.from(result, delivery -> {
            Order order = orders.get(delivery.getOrderId());
            return new AdminDeliverySummary(delivery.getId(), delivery.getOrderId(), order.getStatus().name(),
                    order.getCreatedAt(), courier(delivery, people), order.getRestaurantName());
        });
    }

    public AdminOrderDetail orderDetail(UUID id) {
        Order order = queries.findWithLines(id).orElseThrow(OrderNotFoundException::new);
        Delivery delivery = deliveries.findByOrderId(id).orElse(null);
        Set<UUID> ids = new HashSet<>();
        ids.add(order.getCustomerId());
        if (delivery != null && delivery.getCourierId() != null) {
            ids.add(delivery.getCourierId());
        }
        List<OrderStatusChange> history = changes.findByOrderIdOrderByChangedAtAsc(id);
        history.stream().map(OrderStatusChange::getActorId).filter(Objects::nonNull).forEach(ids::add);
        Map<UUID, Account> people = accountsById(ids);
        Payment payment = payments.findByOrderId(id).orElse(null);
        return new AdminOrderDetail(order.getId(), order.getStatus().name(), order.getCreatedAt(),
                party(people.get(order.getCustomerId()), order.getCustomerId()),
                new Party(order.getRestaurantId(), order.getRestaurantName(), null),
                delivery == null ? null : courier(delivery, people), delivery == null ? null : delivery.getId(),
                order.getLines().stream().map(line -> new AdminOrderDetail.Line(line.getProductId(),
                        line.getProductName(), line.getUnitPrice(), line.getQuantity(), line.getLineTotal())).toList(),
                new AdminOrderDetail.Amounts(order.getSubtotal(), order.getDeliveryFee(), order.getCommission(),
                        order.getTotal()),
                new AdminOrderDetail.PaymentInfo(order.getPaymentMethod().name(),
                        payment == null ? null : payment.getStatus().name(),
                        payment == null ? null : payment.getAmount(), payment == null ? null : payment.getCurrency()),
                new AdminOrderDetail.DeliveryAddress(order.getDeliveryStreet(), order.getDeliveryDistrict(),
                        order.getDeliveryReference(), order.getDeliveryPhone()),
                entries(history, people),
                transactions.findByOrderIdOrderByCreatedAtAsc(id).stream()
                        .map(t -> new AdminOrderDetail.Transaction(t.getType().name(), t.getStatus().name(),
                                t.getAmount(), t.getResultCode(), t.getCreatedAt()))
                        .toList());
    }

    public AdminDeliveryDetail deliveryDetail(UUID id) {
        Delivery delivery = deliveries.findById(id).orElseThrow(OrderNotFoundException::new);
        Order order = queries.findById(delivery.getOrderId()).orElseThrow(OrderNotFoundException::new);
        List<OrderStatusChange> history = changes.findByOrderIdOrderByChangedAtAsc(order.getId());
        Set<UUID> ids = new HashSet<>();
        ids.add(order.getCustomerId());
        if (delivery.getCourierId() != null) {
            ids.add(delivery.getCourierId());
        }
        history.stream().map(OrderStatusChange::getActorId).filter(Objects::nonNull).forEach(ids::add);
        Map<UUID, Account> people = accountsById(ids);
        Payment payment = payments.findByOrderId(order.getId()).orElse(null);
        return new AdminDeliveryDetail(delivery.getId(), order.getId(), order.getStatus().name(),
                delivery.getReadyAt(), delivery.getAssignedAt(), courier(delivery, people), entries(history, people),
                summary(order, people.get(order.getCustomerId()), payment));
    }

    // ---- ensamblado ----

    private static AdminOrderSummary summary(Order order, Account customer, Payment payment) {
        return new AdminOrderSummary(order.getId(), order.getStatus().name(), order.getCreatedAt(), order.getTotal(),
                party(customer, order.getCustomerId()), new Party(order.getRestaurantId(), order.getRestaurantName(), null),
                payment == null ? null : payment.getStatus().name());
    }

    private static Party party(Account account, UUID id) {
        return account == null ? new Party(id, null, null) : new Party(id, account.getName(), account.getEmail());
    }

    private static Party courier(Delivery delivery, Map<UUID, Account> people) {
        UUID courierId = delivery.getCourierId();
        return courierId == null ? null : party(people.get(courierId), courierId);
    }

    private static List<StatusChangeEntry> entries(List<OrderStatusChange> rows, Map<UUID, Account> people) {
        return rows.stream().sorted(Comparator.comparing(OrderStatusChange::getChangedAt))
                .map(change -> new StatusChangeEntry(change.getFromStatus().name(), change.getToStatus().name(),
                        change.getChangedAt(), change.getActorId(),
                        change.getActorId() == null || !people.containsKey(change.getActorId()) ? null
                                : people.get(change.getActorId()).getName(),
                        change.getActorRole().name(), change.getReason()))
                .toList();
    }

    private Map<UUID, Account> accountsById(Collection<UUID> ids) {
        return accounts.findAllById(ids).stream().collect(Collectors.toMap(Account::getId, Function.identity()));
    }

    private Map<UUID, Payment> paymentsByOrder(Collection<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return queries.findPayments(orderIds).stream()
                .collect(Collectors.toMap(Payment::getOrderId, Function.identity()));
    }
}
