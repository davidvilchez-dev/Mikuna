package com.mikuna.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mikuna.backend.notification.model.NotificationType;
import com.mikuna.backend.notification.service.NotificationRecipients.Draft;
import com.mikuna.backend.notification.service.NotificationRecipients.Participants;
import com.mikuna.backend.order.model.ActorRole;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.model.OrderStatusChangedEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-010: regla fija de destinatarios de los avisos (research §6). */
class NotificationRecipientsTest {

    private final UUID orderId = UUID.randomUUID();
    private final UUID customer = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID courier = UUID.randomUUID();
    private final Participants withCourier = new Participants(orderId, customer, owner, courier);
    private final Participants withoutCourier = new Participants(orderId, customer, owner, null);

    private List<Draft> plan(OrderStatus from, OrderStatus to, UUID actor, ActorRole role, Participants p) {
        return NotificationRecipients.plan(
                new OrderStatusChangedEvent(UUID.randomUUID(), orderId, from, to, actor, role), p);
    }

    private static List<UUID> recipients(List<Draft> drafts) {
        return drafts.stream().map(Draft::recipientId).toList();
    }

    @Test
    void aceptarRechazarPrepararYListoSoloAvisanAlCliente() {
        assertThat(recipients(plan(OrderStatus.PENDING, OrderStatus.ACCEPTED, owner, ActorRole.RESTAURANT,
                withoutCourier))).containsExactly(customer);
        assertThat(recipients(plan(OrderStatus.PENDING, OrderStatus.REJECTED, owner, ActorRole.RESTAURANT,
                withoutCourier))).containsExactly(customer);
        assertThat(recipients(plan(OrderStatus.ACCEPTED, OrderStatus.PREPARING, owner, ActorRole.RESTAURANT,
                withoutCourier))).containsExactly(customer);
        List<Draft> ready = plan(OrderStatus.PREPARING, OrderStatus.READY, owner, ActorRole.RESTAURANT,
                withoutCourier);
        assertThat(recipients(ready)).containsExactly(customer);
        assertThat(ready.get(0).type()).isEqualTo(NotificationType.READY);
    }

    @Test
    void cancelarAvisaAlRestauranteYAlRepartidorAsignadoPeroNoAlClienteQueCancela() {
        List<Draft> byCustomer = plan(OrderStatus.PENDING, OrderStatus.CANCELLED, customer, ActorRole.CUSTOMER,
                withCourier);

        assertThat(recipients(byCustomer)).containsExactlyInAnyOrder(owner, courier);
    }

    @Test
    void cancelarSinRepartidorSoloAvisaAlRestaurante() {
        assertThat(recipients(plan(OrderStatus.PENDING, OrderStatus.CANCELLED, customer, ActorRole.CUSTOMER,
                withoutCourier))).containsExactly(owner);
    }

    @Test
    void laCancelacionDelSistemaAvisaAClienteYRestaurante() {
        assertThat(recipients(plan(OrderStatus.PENDING, OrderStatus.CANCELLED, null, ActorRole.SYSTEM,
                withoutCourier))).containsExactlyInAnyOrder(customer, owner);
    }

    @Test
    void elVencimientoAvisaAClienteYRestauranteConElMotivo() {
        List<Draft> drafts = NotificationRecipients.plan(new OrderStatusChangedEvent(UUID.randomUUID(), orderId,
                OrderStatus.PENDING, OrderStatus.CANCELLED, null, ActorRole.SYSTEM,
                com.mikuna.backend.order.service.OrderExpiryPolicy.REASON), withoutCourier);

        assertThat(recipients(drafts)).containsExactly(customer, owner);
        assertThat(drafts).allSatisfy(d -> assertThat(d.type()).isEqualTo(NotificationType.CANCELLED));
        assertThat(drafts.get(0).message()).contains("el restaurante no respondió a tiempo");
        assertThat(drafts.get(1).message()).contains("porque no se respondió a tiempo");
    }

    @Test
    void asignarAvisaALosTresParticipantes() {
        List<Draft> drafts = plan(OrderStatus.READY, OrderStatus.ASSIGNED, null, ActorRole.SYSTEM, withCourier);

        assertThat(recipients(drafts)).containsExactlyInAnyOrder(customer, owner, courier);
        assertThat(drafts).allMatch(d -> d.type() == NotificationType.ASSIGNED);
    }

    @Test
    void laAsignacionDelAdministradorAvisaALosTresParticipantes() {
        List<Draft> drafts = plan(OrderStatus.READY, OrderStatus.ASSIGNED, UUID.randomUUID(), ActorRole.ADMIN,
                withCourier);

        assertThat(recipients(drafts)).containsExactlyInAnyOrder(customer, owner, courier);
    }

    @Test
    void recogerYEntregarAvisanAClienteYRestaurante() {
        assertThat(recipients(plan(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP, courier, ActorRole.COURIER,
                withCourier))).containsExactlyInAnyOrder(customer, owner);
        assertThat(recipients(plan(OrderStatus.PICKED_UP, OrderStatus.DELIVERED, courier, ActorRole.COURIER,
                withCourier))).containsExactlyInAnyOrder(customer, owner);
    }

    @Test
    void elRechazoDeLaAsignacionNoAvisaANadie() {
        assertThat(plan(OrderStatus.ASSIGNED, OrderStatus.READY, courier, ActorRole.COURIER, withCourier)).isEmpty();
    }

    @Test
    void sinRestauranteNiRepartidorSoloSeAvisaAlCliente() {
        Participants onlyCustomer = new Participants(orderId, customer, null, null);

        assertThat(recipients(plan(OrderStatus.READY, OrderStatus.ASSIGNED, null, ActorRole.SYSTEM, onlyCustomer)))
                .containsExactly(customer);
    }

    @Test
    void losMensajesCitanElPedidoYCaben() {
        List<Draft> drafts = plan(OrderStatus.READY, OrderStatus.ASSIGNED, null, ActorRole.SYSTEM, withCourier);

        assertThat(drafts).allSatisfy(d -> {
            assertThat(d.message()).contains("#" + orderId.toString().substring(0, 8));
            assertThat(d.message().length()).isLessThanOrEqualTo(200);
        });
    }
}
