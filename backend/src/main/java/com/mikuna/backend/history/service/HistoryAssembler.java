package com.mikuna.backend.history.service;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.repository.AccountRepository;
import com.mikuna.backend.history.dto.HistoryItem;
import com.mikuna.backend.history.model.Rating;
import com.mikuna.backend.history.repository.HistoryQueryRepository;
import com.mikuna.backend.history.repository.RatingRepository;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.payment.model.Payment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Convierte una página de pedidos en filas de historial según el rol. Pagos, calificaciones, líneas y nombres de
 * cliente se obtienen por lote (una consulta por tipo, no por fila).
 */
@Component
public class HistoryAssembler {

    /** Vista que construye cada rol; define qué campos se incluyen (FR-016, FR-017). */
    public enum View { CUSTOMER, RESTAURANT, COURIER }

    private final HistoryQueryRepository queries;
    private final RatingRepository ratings;
    private final AccountRepository accounts;

    public HistoryAssembler(HistoryQueryRepository queries, RatingRepository ratings, AccountRepository accounts) {
        this.queries = queries;
        this.ratings = ratings;
        this.accounts = accounts;
    }

    public List<HistoryItem> assemble(List<Order> page, View view) {
        if (page.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = page.stream().map(Order::getId).toList();
        Map<UUID, Order> withLines = queries.findWithLines(ids).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        Map<UUID, Integer> ratingByOrder = new HashMap<>();
        for (Rating rating : ratings.findByOrderIdIn(ids)) {
            ratingByOrder.put(rating.getOrderId(), rating.getValue());
        }
        Map<UUID, Payment> payments = view == View.COURIER ? Map.of()
                : queries.findPayments(ids).stream().collect(Collectors.toMap(Payment::getOrderId, p -> p));
        Map<UUID, String> names = view == View.CUSTOMER ? Map.of() : customerNames(page);
        return page.stream().map(order -> item(withLines.getOrDefault(order.getId(), order), view,
                ratingByOrder.get(order.getId()), payments.get(order.getId()), names.get(order.getCustomerId())))
                .toList();
    }

    private Map<UUID, String> customerNames(List<Order> page) {
        List<UUID> customerIds = page.stream().map(Order::getCustomerId).distinct().toList();
        return accounts.findAllById(customerIds).stream().collect(Collectors.toMap(Account::getId, Account::getName));
    }

    private static HistoryItem item(Order order, View view, Integer rating, Payment payment, String customerName) {
        boolean courier = view == View.COURIER;
        List<HistoryItem.Line> lines = order.getLines().stream()
                .map(l -> courier ? new HistoryItem.Line(null, l.getProductName(), null, l.getQuantity(), null)
                        : new HistoryItem.Line(l.getProductId(), l.getProductName(), l.getUnitPrice(),
                                l.getQuantity(), l.getLineTotal()))
                .toList();
        HistoryItem.RestaurantInfo restaurant = view == View.RESTAURANT ? null
                : new HistoryItem.RestaurantInfo(order.getRestaurantId(), order.getRestaurantName());
        HistoryItem.DeliveryAddress address = courier
                ? new HistoryItem.DeliveryAddress(order.getDeliveryStreet(), order.getDeliveryDistrict(),
                        order.getDeliveryReference())
                : null;
        HistoryItem.Totals totals = courier ? null
                : new HistoryItem.Totals(order.getSubtotal(), order.getDeliveryFee(), order.getCommission(),
                        order.getTotal());
        HistoryItem.PaymentInfo paymentInfo = courier || payment == null ? null
                : new HistoryItem.PaymentInfo(order.getPaymentMethod().name(), payment.getStatus().name());
        boolean canRate = view == View.CUSTOMER && order.getStatus() == OrderStatus.DELIVERED && rating == null;
        return new HistoryItem(order.getId(), order.getStatus().name(), order.getCreatedAt(), restaurant,
                view == View.CUSTOMER ? null : customerName, address, lines, totals,
                courier ? order.getDeliveryFee() : null, paymentInfo, rating, canRate);
    }
}
