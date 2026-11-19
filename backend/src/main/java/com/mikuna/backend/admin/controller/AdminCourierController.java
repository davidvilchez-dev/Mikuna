package com.mikuna.backend.admin.controller;

import com.mikuna.backend.admin.dto.AccountDetail;
import com.mikuna.backend.admin.dto.AccountSummary;
import com.mikuna.backend.admin.service.AdminAccountQueryService;
import com.mikuna.backend.admin.service.AdminAccountService;
import com.mikuna.backend.catalog.dto.PageResponse;
import com.mikuna.backend.common.security.AccountPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Vista operativa de repartidores (protegido, rol ADMIN). El administrador sale del token. */
@RestController
@RequestMapping("/api/admin/couriers")
public class AdminCourierController {

    private final AdminAccountQueryService queries;
    private final AdminAccountService accounts;

    public AdminCourierController(AdminAccountQueryService queries, AdminAccountService accounts) {
        this.queries = queries;
        this.accounts = accounts;
    }

    @GetMapping
    public PageResponse<AccountSummary> list(@RequestParam(required = false) String operation,
            @RequestParam(required = false) String q, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return queries.listCouriers(operation, q, page, size);
    }

    @GetMapping("/{id}")
    public AccountDetail detail(@PathVariable UUID id) {
        return queries.courierDetail(id);
    }

    @PostMapping("/{id}/deactivate")
    public AccountDetail deactivate(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id) {
        return accounts.setCourierOperation(principal.id(), id, false);
    }

    @PostMapping("/{id}/activate")
    public AccountDetail activate(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id) {
        return accounts.setCourierOperation(principal.id(), id, true);
    }
}
