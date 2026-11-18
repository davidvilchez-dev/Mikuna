package com.mikuna.backend.payment.repository;

import com.mikuna.backend.payment.model.PaymentTransaction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    List<PaymentTransaction> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
