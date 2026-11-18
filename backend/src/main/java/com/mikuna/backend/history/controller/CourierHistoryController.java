package com.mikuna.backend.history.controller;

import com.mikuna.backend.catalog.dto.PageResponse;
import com.mikuna.backend.common.security.AccountPrincipal;
import com.mikuna.backend.history.dto.HistoryItem;
import com.mikuna.backend.history.service.HistoryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Historial de las entregas a cargo del repartidor propio (protegido, rol COURIER). */
@RestController
@RequestMapping("/api/courier/deliveries")
public class CourierHistoryController {

    private final HistoryService history;

    public CourierHistoryController(HistoryService history) {
        this.history = history;
    }

    @GetMapping("/history")
    public PageResponse<HistoryItem> history(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(required = false) String status, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return history.courierHistory(principal.id(), status, from, to, page, size);
    }
}
