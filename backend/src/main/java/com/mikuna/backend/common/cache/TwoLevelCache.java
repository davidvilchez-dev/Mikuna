package com.mikuna.backend.common.cache;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

/**
 * Caché de dos niveles (spec 015, research §2 bis): nivel 1 en la memoria de la instancia (Caffeine, sin
 * serializar) y nivel 2 compartido en Redis. Las lecturas resuelven casi siempre en memoria; Redis evita que cada
 * instancia vuelva a la base de datos tras un descarte. Al descartar se borran ambos niveles y se avisa a las
 * demás instancias (pub/sub) para que borren su nivel 1; así un cambio se ve al instante en todas.
 *
 * <p>Si Redis falla, la caché sigue funcionando solo con el nivel 1 (FR-006): una falla del nivel 2 nunca se
 * propaga.
 */
public class TwoLevelCache implements Cache {

    private static final Logger log = LoggerFactory.getLogger(TwoLevelCache.class);

    private final String name;
    private final com.github.benmanes.caffeine.cache.Cache<String, Object> local;
    private final Cache shared;
    private final Consumer<String> invalidation;
    private static final long WARN_EVERY_MS = 60_000;
    private volatile long lastWarning;

    /** {@code invalidation} recibe la clave (o {@code *}) descartada para avisar a las demás instancias. */
    public TwoLevelCache(String name, com.github.benmanes.caffeine.cache.Cache<String, Object> local, Cache shared,
            Consumer<String> invalidation) {
        this.name = name;
        this.local = local;
        this.shared = shared;
        this.invalidation = invalidation;
    }

    /** Las claves del nivel 1 son texto para poder descartarlas con lo que llega por pub/sub. */
    static String localKey(Object key) {
        return String.valueOf(key);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return local;
    }

    public com.github.benmanes.caffeine.cache.Cache<String, Object> localCache() {
        return local;
    }

    @Override
    public ValueWrapper get(Object key) {
        Object value = local.getIfPresent(localKey(key));
        if (value != null) {
            return new SimpleValueWrapper(value);
        }
        ValueWrapper fromShared = sharedGet(key);
        if (fromShared != null && fromShared.get() != null) {
            local.put(localKey(key), fromShared.get());
            return fromShared;
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        ValueWrapper wrapper = get(key);
        Object value = wrapper == null ? null : wrapper.get();
        if (value != null && type != null && !type.isInstance(value)) {
            throw new IllegalStateException("Valor en caché de tipo inesperado: " + value.getClass().getName());
        }
        return (T) value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper wrapper = get(key);
        if (wrapper != null) {
            return (T) wrapper.get();
        }
        try {
            T value = valueLoader.call();
            put(key, value);
            return value;
        } catch (Exception failure) {
            throw new ValueRetrievalException(key, valueLoader, failure);
        }
    }

    @Override
    public void put(Object key, Object value) {
        if (value == null) {
            return;
        }
        local.put(localKey(key), value);
        try {
            shared.put(key, value);
        } catch (RuntimeException unavailable) {
            warn("guardar", key, unavailable);
        }
    }

    @Override
    public void evict(Object key) {
        local.invalidate(localKey(key));
        try {
            shared.evict(key);
        } catch (RuntimeException unavailable) {
            warn("descartar", key, unavailable);
        }
        notifyOthers(localKey(key));
    }

    @Override
    public void clear() {
        local.invalidateAll();
        try {
            shared.clear();
        } catch (RuntimeException unavailable) {
            warn("vaciar", "*", unavailable);
        }
        notifyOthers("*");
    }

    /** Aviso de otra instancia: solo se borra el nivel 1 (el nivel 2 ya lo borró quien cambió el dato). */
    public void evictLocal(String key) {
        if ("*".equals(key)) {
            local.invalidateAll();
        } else {
            local.invalidate(key);
        }
    }

    private ValueWrapper sharedGet(Object key) {
        try {
            return shared.get(key);
        } catch (RuntimeException unavailable) {
            warn("leer", key, unavailable);
            return null;
        }
    }

    private void notifyOthers(String key) {
        try {
            invalidation.accept(key);
        } catch (RuntimeException unavailable) {
            warn("avisar el descarte de", key, unavailable);
        }
    }

    private void warn(String action, Object key, RuntimeException failure) {
        long now = System.currentTimeMillis();
        if (now - lastWarning < WARN_EVERY_MS) {
            return;
        }
        lastWarning = now;
        log.warn("Redis no disponible al {} {}[{}]; se sigue con la memoria local: {}", action, name, key,
                failure.toString());
    }
}
