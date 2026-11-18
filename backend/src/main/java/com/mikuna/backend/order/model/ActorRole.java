package com.mikuna.backend.order.model;

/** Quién origina un cambio de estado: una cuenta (restaurante o cliente) o el propio sistema. */
public enum ActorRole {
    RESTAURANT, CUSTOMER, SYSTEM, COURIER, ADMIN
}
