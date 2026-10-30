package com.juniverse.redis.patterns;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Limitador de peticiones de <i>ventana fija</i>: se permite un máximo de {@code limit} peticiones por cliente en cada
 * ventana de tiempo. Usa INCR + EXPIRE dentro de un script Lua para que el contador y su caducidad se creen de forma atómica
 * (si se hicieran por separado, un fallo entre ambos dejaría un contador que nunca expira).
 */
@Service
public class RateLimiter {

    private static final DefaultRedisScript<Long> HIT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    /**
     * Resultado de una petición.
     *
     * @param allowed si se permite
     * @param remaining peticiones que quedan en la ventana
     * @param retryAfterSeconds segundos hasta que se reinicia la ventana
     */
    public record Result(boolean allowed, long remaining, long retryAfterSeconds) {
    }

    private final StringRedisTemplate redis;

    /**
     * Crea el servicio.
     *
     * @param redis plantilla de Redis para texto
     */
    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Registra una petición del cliente y dice si se permite.
     *
     * @param client identificador del cliente (usuario, IP, API key)
     * @param limit máximo de peticiones por ventana
     * @param window duración de la ventana
     * @return resultado
     */
    public Result hit(String client, int limit, Duration window) {
        String key = "demo:ratelimit:" + client;
        Long count = redis.execute(HIT, List.of(key), String.valueOf(window.toMillis()));
        long used = count == null ? 0 : count;
        Long ttl = redis.getExpire(key);
        long retryAfter = ttl == null || ttl < 0 ? 0 : ttl;
        return new Result(used <= limit, Math.max(0, limit - used), retryAfter);
    }
}
