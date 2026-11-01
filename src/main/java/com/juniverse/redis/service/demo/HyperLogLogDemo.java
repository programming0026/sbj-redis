package com.juniverse.redis.service.demo;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <b>HyperLogLog</b>: cuenta elementos DISTINTOS de forma aproximada (error ≈ 0,81 %) usando siempre unos 12 KB, sin
 * importar si son mil o mil millones. Ideal para "visitantes únicos". Comandos: PFADD, PFCOUNT, PFMERGE.
 */
@Service
public class HyperLogLogDemo {

    private final StringRedisTemplate redis;

    /**
     * Crea el servicio.
     *
     * @param redis plantilla de Redis para texto
     */
    public HyperLogLogDemo(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Registra elementos (PFADD); repetirlos no cambia la cuenta.
     *
     * @param key clave
     * @param values identificadores (por ejemplo, ids de visitante)
     */
    public void add(String key, String... values) {
        redis.opsForHyperLogLog().add(key, values);
    }

    /**
     * Cantidad aproximada de elementos distintos (PFCOUNT).
     *
     * @param keys uno o varios HyperLogLog (con varios cuenta la unión sin duplicar)
     * @return cuenta estimada
     */
    public long count(String... keys) {
        return redis.opsForHyperLogLog().size(keys);
    }

    /**
     * Une varios HyperLogLog en uno nuevo (PFMERGE), por ejemplo los visitantes de cada día en los de la semana.
     *
     * @param destination clave destino
     * @param sources claves origen
     */
    public void merge(String destination, String... sources) {
        redis.opsForHyperLogLog().union(destination, sources);
    }
}
