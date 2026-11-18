package com.mikuna.backend.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mikuna.backend.account.model.Account;
import com.mikuna.backend.account.model.Role;
import com.mikuna.backend.account.repository.AccountRepository;
import com.mikuna.backend.common.exception.CourierNotCandidateException;
import com.mikuna.backend.common.exception.CourierStillEnabledException;
import com.mikuna.backend.common.exception.InvalidOrderTransitionException;
import com.mikuna.backend.common.exception.OrderNotFoundException;
import com.mikuna.backend.delivery.dto.PendingDeliveryResponse;
import com.mikuna.backend.delivery.model.CourierAvailability;
import com.mikuna.backend.delivery.model.Delivery;
import com.mikuna.backend.delivery.repository.CourierAvailabilityRepository;
import com.mikuna.backend.delivery.repository.DeliveryRepository;
import com.mikuna.backend.order.model.ActorRole;
import com.mikuna.backend.order.model.Order;
import com.mikuna.backend.order.model.OrderStatus;
import com.mikuna.backend.order.model.OrderTransitionTest;
import com.mikuna.backend.order.repository.OrderRepository;
import com.mikuna.backend.order.service.OrderStatusService;
import com.mikuna.backend.support.TestAccounts;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** RF-24 y FR-010a: asignación manual a candidatos, liberación por repartidor inhabilitado y lista de pendientes. */
class DeliveryAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    private final OrderRepository orders = mock(OrderRepository.class);
    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final CourierAvailabilityRepository availability = mock(CourierAvailabilityRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final DeliveryAssignmentService assignment = mock(DeliveryAssignmentService.class);
    private final OrderStatusService orderStatus = mock(OrderStatusService.class);
    private final DeliveryDispatcher dispatcher = mock(DeliveryDispatcher.class);
    private final UUID adminId = UUID.randomUUID();
    private final UUID courier = UUID.randomUUID();
    private DeliveryAdminService service;
    private Order order;
    private Delivery delivery;

    @BeforeEach
    void setUp() {
        service = new DeliveryAdminService(orders, deliveries, availability, accounts, assignment, orderStatus,
                dispatcher);
        order = OrderTransitionTest.orderIn(OrderStatus.READY);
        delivery = delivery(order);
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(deliveries.findByOrderId(order.getId())).thenReturn(Optional.of(delivery));
    }

    private static Delivery delivery(Order forOrder) {
        Delivery d = new Delivery(forOrder.getId(), NOW);
        ReflectionTestUtils.setField(d, "id", UUID.randomUUID());
        return d;
    }

    @Test
    void asignaAUnCandidatoConElAdministradorComoResponsable() {
        when(availability.findByIdForUpdate(courier)).thenReturn(Optional.of(new CourierAvailability(courier, NOW)));
        when(availability.countCandidate(courier, delivery.getId())).thenReturn(1L);

        PendingDeliveryResponse response = service.assign(adminId, order.getId(), courier);

        verify(assignment).assign(order, delivery, courier, adminId, ActorRole.ADMIN);
        assertThat(response.courierId()).isEqualTo(courier);
    }

    @Test
    void noAsignaUnNoCandidatoNiUnPedidoQueNoEstaListoNiUnaEntregaYaAsignada() {
        assertThatThrownBy(() -> service.assign(adminId, order.getId(), courier))
                .isInstanceOf(CourierNotCandidateException.class); // sin fila de disponibilidad

        when(availability.findByIdForUpdate(courier)).thenReturn(Optional.of(new CourierAvailability(courier, NOW)));
        when(availability.countCandidate(courier, delivery.getId())).thenReturn(0L);
        assertThatThrownBy(() -> service.assign(adminId, order.getId(), courier))
                .isInstanceOf(CourierNotCandidateException.class);

        ReflectionTestUtils.setField(delivery, "courierId", UUID.randomUUID());
        assertThatThrownBy(() -> service.assign(adminId, order.getId(), courier))
                .isInstanceOf(InvalidOrderTransitionException.class);

        Order assigned = OrderTransitionTest.orderIn(OrderStatus.ASSIGNED);
        when(orders.findByIdForUpdate(assigned.getId())).thenReturn(Optional.of(assigned));
        when(deliveries.findByOrderId(assigned.getId())).thenReturn(Optional.of(delivery(assigned)));
        assertThatThrownBy(() -> service.assign(adminId, assigned.getId(), courier))
                .isInstanceOf(InvalidOrderTransitionException.class);

        assertThatThrownBy(() -> service.assign(adminId, UUID.randomUUID(), courier))
                .isInstanceOf(OrderNotFoundException.class);
        when(deliveries.findByOrderId(order.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assign(adminId, order.getId(), courier))
                .isInstanceOf(OrderNotFoundException.class);
        verify(assignment, never()).assign(any(), any(), any(), any(), any());
    }

    @Test
    void liberarUnaEntregaDeUnRepartidorInhabilitadaLaDejaListaYDespacha() {
        Order assigned = OrderTransitionTest.orderIn(OrderStatus.ASSIGNED);
        Delivery d = delivery(assigned);
        ReflectionTestUtils.setField(d, "courierId", courier);
        when(orders.findByIdForUpdate(assigned.getId())).thenReturn(Optional.of(assigned));
        when(deliveries.findByOrderId(assigned.getId())).thenReturn(Optional.of(d));
        Account disabled = TestAccounts.disabled(TestAccounts.account(Role.COURIER, "c@mail.com", "hash"));
        when(accounts.findById(courier)).thenReturn(Optional.of(disabled));

        PendingDeliveryResponse response = service.release(adminId, assigned.getId());

        assertThat(d.getCourierId()).isNull();
        verify(orderStatus).changeStatus(assigned, OrderStatus.READY, adminId, ActorRole.ADMIN, null);
        verify(dispatcher).dispatch(assigned.getId());
        assertThat(response.courierId()).isNull();
    }

    @Test
    void noLiberaSiElRepartidorSigueHabilitadoOElEstadoNoEsAsignado() {
        Order assigned = OrderTransitionTest.orderIn(OrderStatus.ASSIGNED);
        Delivery d = delivery(assigned);
        ReflectionTestUtils.setField(d, "courierId", courier);
        when(orders.findByIdForUpdate(assigned.getId())).thenReturn(Optional.of(assigned));
        when(deliveries.findByOrderId(assigned.getId())).thenReturn(Optional.of(d));
        when(accounts.findById(courier))
                .thenReturn(Optional.of(TestAccounts.account(Role.COURIER, "c@mail.com", "hash")));

        assertThatThrownBy(() -> service.release(adminId, assigned.getId()))
                .isInstanceOf(CourierStillEnabledException.class);
        assertThat(d.getCourierId()).isEqualTo(courier);
        assertThatThrownBy(() -> service.release(adminId, order.getId()))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThatThrownBy(() -> service.release(adminId, UUID.randomUUID()))
                .isInstanceOf(OrderNotFoundException.class);
        verify(orderStatus, never()).changeStatus(any(), any(), any(), any(), any());
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void liberarConCuentaInexistenteEsPermitido() {
        Order assigned = OrderTransitionTest.orderIn(OrderStatus.ASSIGNED);
        Delivery d = delivery(assigned);
        ReflectionTestUtils.setField(d, "courierId", courier);
        when(orders.findByIdForUpdate(assigned.getId())).thenReturn(Optional.of(assigned));
        when(deliveries.findByOrderId(assigned.getId())).thenReturn(Optional.of(d));
        when(accounts.findById(courier)).thenReturn(Optional.empty());

        service.release(adminId, assigned.getId());

        assertThat(d.getCourierId()).isNull();
    }

    @Test
    void laListaIncluyePendientesConCandidatosYAsignadasAInhabilitados() {
        Account candidate = TestAccounts.account(Role.COURIER, "a@mail.com", "hash");
        CourierAvailability row = new CourierAvailability(candidate.getId(), NOW);
        when(deliveries.findPending(OrderStatus.READY)).thenReturn(List.of(delivery));
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(availability.findCandidateIds(delivery.getId())).thenReturn(List.of(candidate.getId()));
        when(accounts.findAllById(List.of(candidate.getId()))).thenReturn(List.of(candidate));
        when(availability.findAllById(List.of(candidate.getId()))).thenReturn(List.of(row));

        Order assigned = OrderTransitionTest.orderIn(OrderStatus.ASSIGNED);
        Delivery d = delivery(assigned);
        ReflectionTestUtils.setField(d, "courierId", courier);
        when(deliveries.findAssignedToDisabledCourier(OrderStatus.ASSIGNED)).thenReturn(List.of(d));
        when(orders.findById(assigned.getId())).thenReturn(Optional.of(assigned));

        List<PendingDeliveryResponse> list = service.listPending();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).status()).isEqualTo("READY");
        assertThat(list.get(0).candidates()).hasSize(1);
        assertThat(list.get(0).candidates().get(0).name()).isEqualTo(candidate.getName());
        assertThat(list.get(1).courierDisabled()).isTrue();
        assertThat(list.get(1).courierId()).isEqualTo(courier);
    }
}
