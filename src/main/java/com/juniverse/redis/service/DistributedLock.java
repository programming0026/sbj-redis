package com.juniverse.redis.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Bloqueo distribuido: garantiza que solo UNA instancia de la aplicación ejecute algo a la vez (por ejemplo, una tarea
 * programada). Se toma con {@code SET key token NX PX ttl}: solo uno lo consigue y el TTL evita que un proceso caído deje el
 * bloqueo para siempre. Se libera con un script Lua que borra solo si el token es el propio, para no quitarle el bloqueo a otro.
 *
 * <p>Es suficiente para un Redis único; con réplicas o clústeres críticos se estudia Redlock/Redisson.</p>
 */
@Service
public class DistributedLock {

    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    /**
     * Crea el servicio.
     *
     * @param redis plantilla de Redis para texto
     */
    public DistributedLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Intenta tomar el bloqueo sin esperar.
     *
     * @param name nombre del recurso
     * @param ttl tiempo máximo que se retiene aunque el dueño falle
     * @return el token que prueba la propiedad, o {@code null} si otro ya lo tiene
     */
    public String tryLock(String name, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent("demo:lock:" + name, token, ttl);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    /**
     * Libera el bloqueo solo si el token coincide.
     *
     * @param name nombre del recurso
     * @param token token devuelto por {@link #tryLock}
     * @return {@code true} si se liberó; {@code false} si expiró o pertenece a otro
     */
    public boolean unlock(String name, String token) {
        Long released = redis.execute(RELEASE, List.of("demo:lock:" + name), token);
        return released != null && released == 1;
    }

    /**
     * Ejecuta una acción protegida por el bloqueo y lo libera siempre al terminar.
     *
     * @param name nombre del recurso
     * @param ttl tiempo máximo de retención
     * @param action acción a ejecutar
     * @param <T> tipo del resultado
     * @return el resultado, o {@code null} si no se pudo tomar el bloqueo (otra instancia lo tenía)
     */
    public <T> T withLock(String name, Duration ttl, Supplier<T> action) {
        String token = tryLock(name, ttl);
        if (token == null) {
            return null;
        }
        try {
            return action.get();
        } finally {
            unlock(name, token);
        }
    }
}
