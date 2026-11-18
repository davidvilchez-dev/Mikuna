package com.mikuna.backend.delivery.service;

import com.mikuna.backend.delivery.model.Delivery;
import com.mikuna.backend.delivery.repository.CourierLocationRepository;
import com.mikuna.backend.delivery.repository.DeliveryRepository;
import com.mikuna.backend.order.service.CourierLocationLookup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Ubicación vigente del repartidor asignado a la entrega del pedido; la de otro repartidor nunca se devuelve. */
@Component
public class CourierLocationLookupImpl implements CourierLocationLookup {

    private final DeliveryRepository deliveries;
    private final CourierLocationRepository locations;

    public CourierLocationLookupImpl(DeliveryRepository deliveries, CourierLocationRepository locations) {
        this.deliveries = deliveries;
        this.locations = locations;
    }

    @Override
    public Optional<Position> latestFor(UUID orderId) {
        Optional<Delivery> delivery = deliveries.findByOrderId(orderId);
        if (delivery.isEmpty() || delivery.get().getCourierId() == null) {
            return Optional.empty();
        }
        return locations.findByCourierIdAndOrderId(delivery.get().getCourierId(), orderId)
                .map(l -> new Position(l.getLatitude(), l.getLongitude(), l.getRecordedAt()));
    }
}
