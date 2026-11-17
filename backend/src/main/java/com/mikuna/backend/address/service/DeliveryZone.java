package com.mikuna.backend.address.service;

import com.mikuna.backend.common.config.DeliveryProperties;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Zona de reparto cubierta: lista de distritos configurada (RF-12), sin distinguir mayúsculas ni tildes. */
@Component
public class DeliveryZone {

    private final Set<String> districts;

    public DeliveryZone(DeliveryProperties properties) {
        this.districts = properties.districts().stream().map(DeliveryZone::fold).collect(Collectors.toSet());
    }

    public boolean covers(String district) {
        return district != null && !district.isBlank() && districts.contains(fold(district));
    }

    private static String fold(String text) {
        String decomposed = Normalizer.normalize(text.trim(), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
