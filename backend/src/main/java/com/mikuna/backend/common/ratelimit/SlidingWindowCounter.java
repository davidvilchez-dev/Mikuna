package com.mikuna.backend.common.ratelimit;

import java.time.Duration;

/**
 * Contador de eventos en una ventana deslizante por clave (spec 015, research §5). Lo usan el freno de fuerza
 * bruta del inicio de sesión y el límite de mensajes del asistente. Hay dos implementaciones: en memoria (una
 * instancia, por defecto) y en Redis (compartida entre instancias, perfil "redis", con respaldo en memoria).
 * Las claves no deben llevar datos personales en claro.
 */
public interface SlidingWindowCounter {

    /** Segundos hasta que la clave vuelva a admitir un evento; 0 si aún está por debajo de {@code max}. */
    long waitSeconds(String key, int max, Duration window);

    /** Registra un evento sin comprobar el máximo. */
    void record(String key, Duration window);

    /** Registra el evento y devuelve 0 si había cupo; si no, devuelve la espera sin registrar (atómico). */
    long tryAcquire(String key, int max, Duration window);

    /** Olvida los eventos de la clave. */
    void clear(String key);

    /** Solo para pruebas: olvida todo. */
    void clearAll();

    /**
     * Espera hasta que salga de la ventana el evento que completa el máximo: el más antiguo de los últimos
     * {@code max}. Mínimo 1 s, igual que el cálculo original de las specs 002 y 013.
     */
    static long waitSeconds(long millisUntilFree) {
        return Math.max(1, millisUntilFree / 1000 + 1);
    }
}
