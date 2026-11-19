package com.mikuna.backend.address.service;

import com.mikuna.backend.address.dto.AddressRequest;
import com.mikuna.backend.address.dto.AddressResponse;
import com.mikuna.backend.address.model.Address;
import com.mikuna.backend.address.repository.AddressRepository;
import com.mikuna.backend.common.exception.AddressNotFoundException;
import com.mikuna.backend.common.exception.CartConflictException;
import com.mikuna.backend.common.exception.OutsideDeliveryZoneException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Libreta de direcciones del cliente (RF-12): solo dentro de la zona cubierta y solo las propias. */
@Service
public class AddressService {

    static final int MAX_ADDRESSES = 10;

    private final AddressRepository addresses;
    private final DeliveryZone zone;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AddressService(AddressRepository addresses, DeliveryZone zone, ApplicationEventPublisher events,
            Clock clock) {
        this.addresses = addresses;
        this.zone = zone;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public AddressResponse create(UUID customerId, AddressRequest request) {
        requireCovered(request.district());
        if (addresses.countByCustomerId(customerId) >= MAX_ADDRESSES) {
            throw new CartConflictException(CartConflictException.ADDRESS_LIMIT,
                    "Alcanzaste el máximo de " + MAX_ADDRESSES + " direcciones");
        }
        Address address = Address.create(customerId, request.label(), request.street(), request.district(),
                request.reference(), request.contactPhone(), request.latitude(), request.longitude(), Instant.now(clock));
        return AddressResponse.from(addresses.save(address));
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(UUID customerId) {
        return addresses.findByCustomerIdOrderByCreatedAtAsc(customerId).stream().map(AddressResponse::from).toList();
    }

    @Transactional
    public AddressResponse update(UUID customerId, UUID id, AddressRequest request) {
        Address address = addresses.findByIdAndCustomerId(id, customerId).orElseThrow(AddressNotFoundException::new);
        requireCovered(request.district());
        address.update(request.label(), request.street(), request.district(), request.reference(),
                request.contactPhone(), request.latitude(), request.longitude());
        return AddressResponse.from(addresses.save(address));
    }

    @Transactional
    public void delete(UUID customerId, UUID id) {
        Address address = addresses.findByIdAndCustomerId(id, customerId).orElseThrow(AddressNotFoundException::new);
        addresses.delete(address);
        events.publishEvent(new AddressDeletedEvent(customerId, id));
    }

    private void requireCovered(String district) {
        if (!zone.covers(district)) {
            throw new OutsideDeliveryZoneException();
        }
    }
}
