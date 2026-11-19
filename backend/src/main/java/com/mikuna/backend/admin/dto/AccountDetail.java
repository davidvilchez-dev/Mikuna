package com.mikuna.backend.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/** Detalle de una cuenta con su registro de acciones administrativas (la más reciente primero). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountDetail(AccountSummary account, RestaurantInfo restaurant, CourierInfo courier,
        List<ActionEntry> actions) {

    public record RestaurantInfo(String category, String description, String address, String zone, String phone,
            boolean available) {
    }

    public record CourierInfo(boolean available, Instant freeSince) {
    }
}
