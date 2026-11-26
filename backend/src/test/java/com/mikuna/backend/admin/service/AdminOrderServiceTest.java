package com.mikuna.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.mikuna.backend.account.repository.AccountRepository;
import com.mikuna.backend.admin.repository.AdminOrderQueryRepository;
import com.mikuna.backend.common.exception.OrderNotFoundException;
import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.delivery.repository.DeliveryRepository;
import com.mikuna.backend.order.repository.OrderStatusChangeRepository;
import com.mikuna.backend.payment.repository.PaymentRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdminOrderServiceTest {

    private AdminOrderQueryRepository queries;
    private DeliveryRepository deliveries;
    private AdminOrderService service;

    @BeforeEach
    void setUp() {
        queries = mock(AdminOrderQueryRepository.class);
        deliveries = mock(DeliveryRepository.class);
        service = new AdminOrderService(queries, mock(AccountRepository.class), mock(PaymentRepository.class),
                deliveries, mock(OrderStatusChangeRepository.class),
                mock(com.mikuna.backend.payment.repository.PaymentTransactionRepository.class));
    }

    @Test
    void unknownOrderStatusIsRejectedNamingTheField() {
        assertThatThrownBy(() -> service.listOrders("NOPE", null, null))
                .isInstanceOf(RegistrationValidationException.class)
                .extracting(e -> ((RegistrationValidationException) e).getField()).isEqualTo("status");
    }

    @Test
    void deliveryStatusMustBeOneWhereADeliveryExists() {
        assertThatThrownBy(() -> service.listDeliveries("PENDING", null, null))
                .isInstanceOf(RegistrationValidationException.class)
                .extracting(e -> ((RegistrationValidationException) e).getField()).isEqualTo("status");
        assertThatThrownBy(() -> service.listDeliveries("CANCELLED", null, null))
                .isInstanceOf(RegistrationValidationException.class);
    }

    @Test
    void pageOutOfRangeIsRejected() {
        assertThatThrownBy(() -> service.listOrders(null, -1, null))
                .isInstanceOf(RegistrationValidationException.class);
        assertThatThrownBy(() -> service.listDeliveries(null, 0, 101))
                .isInstanceOf(RegistrationValidationException.class);
    }

    @Test
    void unknownOrderOrDeliveryIsNotFound() {
        UUID id = UUID.randomUUID();
        Mockito.when(queries.findWithLines(id)).thenReturn(Optional.empty());
        Mockito.when(deliveries.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.orderDetail(id)).isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> service.deliveryDetail(id)).isInstanceOf(OrderNotFoundException.class);
    }
}
