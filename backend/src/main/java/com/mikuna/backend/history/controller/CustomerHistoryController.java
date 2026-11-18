package com.mikuna.backend.history.controller;

import com.mikuna.backend.catalog.dto.PageResponse;
import com.mikuna.backend.common.security.AccountPrincipal;
import com.mikuna.backend.history.dto.HistoryItem;
import com.mikuna.backend.history.dto.RatingRequest;
import com.mikuna.backend.history.dto.RatingResponse;
import com.mikuna.backend.history.service.HistoryService;
import com.mikuna.backend.history.service.RatingService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Historial y calificación de los pedidos propios (protegido, rol CUSTOMER). El cliente sale del token. */
@RestController
@RequestMapping("/api/customer/orders")
public class CustomerHistoryController {

    private final HistoryService history;
    private final RatingService ratings;

    public CustomerHistoryController(HistoryService history, RatingService ratings) {
        this.history = history;
        this.ratings = ratings;
    }

    @GetMapping("/history")
    public PageResponse<HistoryItem> history(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(required = false) String status, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return history.customerHistory(principal.id(), status, from, to, page, size);
    }

    @PostMapping("/{id}/rating")
    @ResponseStatus(HttpStatus.CREATED)
    public RatingResponse rate(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody RatingRequest request) {
        return RatingResponse.of(ratings.rate(principal.id(), id, request.value()));
    }
}
