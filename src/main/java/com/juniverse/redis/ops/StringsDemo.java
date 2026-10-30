package com.juniverse.redis.ops;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <b>Strings</b>: el tipo básico (texto o números). Sirven para valores simples, contadores y banderas con caducidad.
 * Comandos: SET, GET, SETNX, INCR, EXPIRE/TTL, MSET/MGET, GETDEL.
 */
@Service
public class StringsDemo {

    private final StringRedisTemplate redis;

    /**
     * Crea el servicio.
     *
     * @param redis plantilla de Redis para texto
     */
    public StringsDemo(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Guarda un valor con tiempo de vida (SET key value EX seconds).
     *
     * @param key clave
     * @param value valor
     * @param ttl tiempo de vida; pasada esa duración Redis lo borra solo
     */
    public void save(String key, String value, Duration ttl) {
        redis.opsForValue().set(key, value, ttl);
    }

    /**
     * Lee un valor (GET).
     *
     * @param key clave
     * @return el valor, o vacío si no existe o expiró
     */
    public Optional<String> get(String key) {
        return Optional.ofNullable(redis.opsForValue().get(key));
    }

    /**
     * Incrementa un contador en 1 (INCR). Es atómico: varios clientes a la vez nunca pierden incrementos.
     *
     * @param key clave del contador (si no existe parte de 0)
     * @return valor tras incrementar
     */
    public long increment(String key) {
        return redis.opsForValue().increment(key);
    }

    /**
     * Suma una cantidad a un contador (INCRBY).
     *
     * @param key clave del contador
     * @param delta cantidad (puede ser negativa)
     * @return valor tras sumar
     */
    public long incrementBy(String key, long delta) {
        return redis.opsForValue().increment(key, delta);
    }

    /**
     * Guarda solo si la clave NO existe (SET key value NX EX): la base de los bloqueos y de la idempotencia.
     *
     * @param key clave
     * @param value valor
     * @param ttl tiempo de vida
     * @return {@code true} si se guardó, {@code false} si ya existía
     */
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, value, ttl));
    }

    /**
     * Segundos de vida restantes (TTL).
     *
     * @param key clave
     * @return segundos restantes; -1 si no caduca; -2 si no existe
     */
    public long ttlSeconds(String key) {
        Long ttl = redis.getExpire(key);
        return ttl == null ? -2 : ttl;
    }

    /**
     * Borra una clave (DEL).
     *
     * @param key clave
     * @return {@code true} si existía
     */
    public boolean delete(String key) {
        return Boolean.TRUE.equals(redis.delete(key));
    }

    /**
     * Guarda varias claves en una sola llamada (MSET).
     *
     * @param values pares clave-valor
     */
    public void multiSet(Map<String, String> values) {
        redis.opsForValue().multiSet(values);
    }

    /**
     * Lee varias claves en una sola llamada (MGET); las que no existen vienen como {@code null}.
     *
     * @param keys claves
     * @return valores en el mismo orden
     */
    public List<String> multiGet(List<String> keys) {
        return redis.opsForValue().multiGet(keys);
    }

    /**
     * Lee y borra en un solo paso (GETDEL): útil para códigos de un solo uso.
     *
     * @param key clave
     * @return el valor que había, o vacío
     */
    public Optional<String> getAndDelete(String key) {
        return Optional.ofNullable(redis.opsForValue().getAndDelete(key));
    }
}
