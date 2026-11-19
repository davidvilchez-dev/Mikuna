package com.mikuna.backend.admin.repository;

import com.mikuna.backend.delivery.model.Delivery;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.payment.model.Payment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consultas de solo lectura de pedidos y entregas de toda la plataforma (RF-33). Orden fijo: {@code createdAt} del
 * pedido e identificador descendentes, de modo que las páginas no repiten ni omiten filas.
 */
public interface AdminOrderQueryRepository extends JpaRepository<Order, UUID> {

    @Query("""
            select o from Order o where o.status in :statuses
            order by o.createdAt desc, o.id desc""")
    Page<Order> listOrders(@Param("statuses") Collection<OrderStatus> statuses, Pageable pageable);

    @Query(value = """
            select d from Delivery d join Order o on o.id = d.orderId where o.status in :statuses
            order by o.createdAt desc, d.id desc""",
            countQuery = "select count(d) from Delivery d join Order o on o.id = d.orderId "
                    + "where o.status in :statuses")
    Page<Delivery> listDeliveries(@Param("statuses") Collection<OrderStatus> statuses, Pageable pageable);

    @Query("select distinct o from Order o left join fetch o.lines where o.id = :id")
    Optional<Order> findWithLines(@Param("id") UUID id);

    @Query("select p from Payment p where p.orderId in :ids")
    List<Payment> findPayments(@Param("ids") Collection<UUID> ids);

    @Query("select d from Delivery d where d.orderId in :ids")
    List<Delivery> findDeliveries(@Param("ids") Collection<UUID> ids);
}
