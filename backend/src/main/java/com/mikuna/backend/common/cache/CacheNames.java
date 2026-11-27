package com.mikuna.backend.common.cache;

/** Nombres de las cachés de consultas públicas (spec 015). Deben coincidir con spring.cache.cache-names. */
public final class CacheNames {

    /** Una sola entrada: la instantánea de restaurantes visibles. */
    public static final String PUBLIC_RESTAURANTS = "public-restaurants";

    /** Por restaurante: sus productos activos y disponibles. */
    public static final String PUBLIC_CATALOG = "public-catalog";

    private CacheNames() {
    }
}
