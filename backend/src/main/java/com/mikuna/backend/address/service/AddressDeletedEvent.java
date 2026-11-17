package com.mikuna.backend.address.service;

import java.util.UUID;

/**
 * Se publica, dentro de la misma transacción, al eliminar una dirección. El carrito lo escucha para dejar
 * de seleccionarla, sin que {@code address} dependa de {@code cart}.
 */
public record AddressDeletedEvent(UUID customerId, UUID addressId) {
}
