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

/** Cuentas de acceso de clientes, restaurantes y repartidores (protegido, rol ADMIN). El administrador sale del token. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminAccountQueryService queries;
    private final AdminAccountService accounts;

    public AdminUserController(AdminAccountQueryService queries, AdminAccountService accounts) {
        this.queries = queries;
        this.accounts = accounts;
    }

    @GetMapping
    public PageResponse<AccountSummary> list(@RequestParam(required = false) String role,
            @RequestParam(required = false) String status, @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return queries.listUsers(role, status, q, page, size);
    }

    @GetMapping("/{id}")
    public AccountDetail detail(@PathVariable UUID id) {
        return queries.userDetail(id);
    }

    @PostMapping("/{id}/disable")
    public AccountDetail disable(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id) {
        return accounts.setAccess(principal.id(), id, false);
    }

    @PostMapping("/{id}/enable")
    public AccountDetail enable(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id) {
        return accounts.setAccess(principal.id(), id, true);
    }
}
