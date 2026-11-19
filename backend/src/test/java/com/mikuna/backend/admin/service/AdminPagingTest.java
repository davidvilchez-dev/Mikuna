package com.mikuna.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mikuna.backend.common.exception.RegistrationValidationException;
import com.mikuna.backend.order.model.OrderStatus;
import org.junit.jupiter.api.Test;

class AdminPagingTest {

    @Test
    void defaultsToTheFirstPageOfTwenty() {
        assertThat(AdminPaging.pageable(null, null).getPageNumber()).isZero();
        assertThat(AdminPaging.pageable(null, null).getPageSize()).isEqualTo(20);
    }

    @Test
    void rejectsPagesAndSizesOutOfRangeNamingTheField() {
        assertThatThrownBy(() -> AdminPaging.pageable(-1, 10)).isInstanceOf(RegistrationValidationException.class)
                .extracting(e -> ((RegistrationValidationException) e).getField()).isEqualTo("page");
        assertThatThrownBy(() -> AdminPaging.pageable(0, 0)).isInstanceOf(RegistrationValidationException.class)
                .extracting(e -> ((RegistrationValidationException) e).getField()).isEqualTo("size");
        assertThatThrownBy(() -> AdminPaging.pageable(0, 101)).isInstanceOf(RegistrationValidationException.class);
        assertThat(AdminPaging.pageable(2, 100).getPageSize()).isEqualTo(100);
    }

    @Test
    void enumFilterTreatsBlankAsNoFilterAndRejectsUnknownValues() {
        assertThat(AdminPaging.enumFilter("status", null, OrderStatus.class)).isNull();
        assertThat(AdminPaging.enumFilter("status", "  ", OrderStatus.class)).isNull();
        assertThat(AdminPaging.enumFilter("status", " DELIVERED ", OrderStatus.class)).isEqualTo(OrderStatus.DELIVERED);
        assertThatThrownBy(() -> AdminPaging.enumFilter("status", "NOPE", OrderStatus.class))
                .isInstanceOf(RegistrationValidationException.class)
                .extracting(e -> ((RegistrationValidationException) e).getField()).isEqualTo("status");
    }

    @Test
    void likePatternLowercasesAndEscapesWildcards() {
        assertThat(AdminPaging.likePattern(null)).isEqualTo("%");
        assertThat(AdminPaging.likePattern("   ")).isEqualTo("%");
        assertThat(AdminPaging.likePattern(" Ana ")).isEqualTo("%ana%");
        assertThat(AdminPaging.likePattern("100%_!")).isEqualTo("%100!%!_!!%");
    }
}
