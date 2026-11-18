package com.mikuna.backend.history.repository;

import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.payment.model.Payment;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consultas de solo lectura del historial (RF-30). La pertenencia va dentro de cada consulta: el actor sale del
 * token y nunca se filtra después. Orden fijo: {@code createdAt} e {@code id} descendentes, de modo que el desempate
 * hace el orden total y las páginas no repiten ni omiten operaciones.
 */
public interface HistoryQueryRepository extends JpaRepository<Order, UUID> {

    @Query("""
            select o from Order o
            where o.customerId = :customerId and o.status in :statuses
              and o.createdAt >= :from and o.createdAt < :to
            order by o.createdAt desc, o.id desc""")
    Page<Order> findForCustomer(@Param("customerId") UUID customerId,
            @Param("statuses") Collection<OrderStatus> statuses, @Param("from") Instant from,
            @Param("to") Instant to, Pageable pageable);

    @Query("""
            select o from Order o
            where o.restaurantId = :restaurantId and o.status in :statuses
              and o.createdAt >= :from and o.createdAt < :to
            order by o.createdAt desc, o.id desc""")
    Page<Order> findForRestaurant(@Param("restaurantId") UUID restaurantId,
            @Param("statuses") Collection<OrderStatus> statuses, @Param("from") Instant from,
            @Param("to") Instant to, Pageable pageable);

    /** Pedidos cuya entrega tiene al repartidor a cargo; las entregas rechazadas o liberadas ya no lo tienen. */
    @Query("""
            select o from Order o
            where o.status in :statuses and o.createdAt >= :from and o.createdAt < :to
              and exists (select 1 from Delivery d where d.orderId = o.id and d.courierId = :courierId)
            order by o.createdAt desc, o.id desc""")
    Page<Order> findForCourier(@Param("courierId") UUID courierId,
            @Param("statuses") Collection<OrderStatus> statuses, @Param("from") Instant from,
            @Param("to") Instant to, Pageable pageable);

    /** Carga las líneas de la página de una sola vez (sin una consulta por pedido). */
    @Query("select distinct o from Order o left join fetch o.lines where o.id in :ids")
    List<Order> findWithLines(@Param("ids") Collection<UUID> ids);

    @Query("select p from Payment p where p.orderId in :ids")
    List<Payment> findPayments(@Param("ids") Collection<UUID> ids);
}
