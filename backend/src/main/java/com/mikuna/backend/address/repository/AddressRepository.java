package com.mikuna.backend.address.repository;

import com.mikuna.backend.address.model.Address;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    /** Dirección propia; una ajena es indistinguible de una inexistente. */
    Optional<Address> findByIdAndCustomerId(UUID id, UUID customerId);

    List<Address> findByCustomerIdOrderByCreatedAtAsc(UUID customerId);

    long countByCustomerId(UUID customerId);
}
