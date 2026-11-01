package com.juniverse.redis.service.demo;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Agrupar comandos: <b>transacción</b> (MULTI/EXEC: se ejecutan seguidos y sin que otro cliente se interponga),
 * <b>pipeline</b> (enviar muchos comandos de golpe para ahorrar viajes de red) y <b>script Lua</b> (lógica propia que se
 * ejecuta de forma atómica dentro del servidor).
 *
 * <p>Ojo: las transacciones de Redis no tienen <i>rollback</i>; si un comando falla, los demás igualmente se ejecutan.</p>
 */
@Service
public class TransactionsDemo {

    /** Incrementa solo si el valor actual es menor al máximo; devuelve el nuevo valor o -1. */
    private static final DefaultRedisScript<Long> INCREMENT_BELOW = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            if current < tonumber(ARGV[1]) then
              return redis.call('INCR', KEYS[1])
            end
            return -1
            """, Long.class);

    private final StringRedisTemplate redis;

    /**
     * Crea el servicio.
     *
     * @param redis plantilla de Redis para texto
     */
    public TransactionsDemo(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Transfiere una cantidad entre dos contadores dentro de MULTI/EXEC: ambos cambios se aplican juntos.
     *
     * @param from clave origen
     * @param to clave destino
     * @param amount cantidad
     * @return resultados de cada comando de la transacción
     */
    @SuppressWarnings("unchecked")
    public List<Object> transfer(String from, String to, long amount) {
        return redis.execute(new SessionCallback<List<Object>>() {
            @Override
            public <K, V> List<Object> execute(RedisOperations<K, V> operations) {
                RedisOperations<String, String> ops = (RedisOperations<String, String>) operations;
                ops.multi();
                ops.opsForValue().decrement(from, amount);
                ops.opsForValue().increment(to, amount);
                return ops.exec();
            }
        });
    }

    /**
     * Guarda muchas claves con un pipeline: todos los SET viajan en un solo envío y las respuestas llegan juntas.
     *
     * @param values pares clave-valor
     * @return cantidad de respuestas recibidas
     */
    public int bulkSetPipelined(Map<String, String> values) {
        List<Object> results = redis.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            values.forEach((key, value) -> connection.stringCommands().set(key.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8)));
            return null;
        });
        return results.size();
    }

    /**
     * Ejecuta un script Lua atómico: leer, decidir e incrementar sin que otro cliente se interponga.
     *
     * @param key clave del contador
     * @param max límite (el contador nunca lo supera)
     * @return nuevo valor, o -1 si ya se alcanzó el máximo
     */
    public long incrementBelow(String key, long max) {
        Long result = redis.execute(INCREMENT_BELOW, List.of(key), String.valueOf(max));
        return result == null ? -1 : result;
    }
}
