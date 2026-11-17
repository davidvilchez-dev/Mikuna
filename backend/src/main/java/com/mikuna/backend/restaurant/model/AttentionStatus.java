package com.mikuna.backend.restaurant.model;

/** Situación de atención de un restaurante aprobado, tal como la ve el público (spec 004). */
public enum AttentionStatus {
    /** Disponible y dentro de su horario del día. */
    OPEN,
    /** Disponible, pero fuera de su horario del día. */
    OUTSIDE_HOURS,
    /** El titular pausó su disponibilidad. */
    PAUSED
}
