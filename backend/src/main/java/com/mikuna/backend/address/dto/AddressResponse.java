package com.mikuna.backend.address.dto;

import com.mikuna.backend.address.model.Address;
import java.time.Instant;
import java.util.UUID;

/** Dirección propia. La dirección seleccionada del pedido se ve en el carrito, no aquí. */
public record AddressResponse(UUID id, String label, String street, String district, String reference,
        String contactPhone, Instant createdAt, Instant updatedAt) {

    public static AddressResponse from(Address address) {
        return new AddressResponse(address.getId(), address.getLabel(), address.getStreet(), address.getDistrict(),
                address.getReference(), address.getContactPhone(), address.getCreatedAt(), address.getUpdatedAt());
    }
}
