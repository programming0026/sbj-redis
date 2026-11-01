package com.juniverse.redis.service.demo;

import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <b>Conjuntos</b>: elementos únicos sin orden, con operaciones de teoría de conjuntos hechas en el servidor.
 * Comandos: SADD, SMEMBERS, SISMEMBER, SINTER, SUNION, SDIFF, SRANDMEMBER, SREM, SCARD.
 */
@Service
public class SetsDemo {

    private final StringRedisTemplate redis;

    /**
     * Crea el servicio.
     *
     * @param redis plantilla de Redis para texto
     */
    public SetsDemo(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Añade elementos (SADD); los repetidos se ignoran.
     *
     * @param key clave
     * @param members elementos
     * @return cuántos eran nuevos
     */
    public long add(String key, String... members) {
        Long added = redis.opsForSet().add(key, members);
        return added == null ? 0 : added;
    }

    /**
     * Todos los elementos (SMEMBERS).
     *
     * @param key clave
     * @return elementos del conjunto
     */
    public Set<String> members(String key) {
        return redis.opsForSet().members(key);
    }

    /**
     * Pertenencia (SISMEMBER).
     *
     * @param key clave
     * @param member elemento
     * @return {@code true} si está en el conjunto
     */
    public boolean contains(String key, String member) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(key, member));
    }

    /**
     * Intersección (SINTER): elementos presentes en ambos conjuntos.
     *
     * @param a primer conjunto
     * @param b segundo conjunto
     * @return elementos comunes
     */
    public Set<String> intersection(String a, String b) {
        return redis.opsForSet().intersect(a, b);
    }

    /**
     * Unión (SUNION).
     *
     * @param a primer conjunto
     * @param b segundo conjunto
     * @return elementos de cualquiera de los dos
     */
    public Set<String> union(String a, String b) {
        return redis.opsForSet().union(a, b);
    }

    /**
     * Diferencia (SDIFF): elementos de {@code a} que no están en {@code b}.
     *
     * @param a conjunto base
     * @param b conjunto a restar
     * @return elementos solo de {@code a}
     */
    public Set<String> difference(String a, String b) {
        return redis.opsForSet().difference(a, b);
    }

    /**
     * Elemento al azar sin quitarlo (SRANDMEMBER): un sorteo.
     *
     * @param key clave
     * @return un elemento aleatorio, o {@code null} si está vacío
     */
    public String randomMember(String key) {
        return redis.opsForSet().randomMember(key);
    }

    /**
     * Quita elementos (SREM).
     *
     * @param key clave
     * @param members elementos
     * @return cuántos se quitaron
     */
    public long remove(String key, Object... members) {
        Long removed = redis.opsForSet().remove(key, members);
        return removed == null ? 0 : removed;
    }

    /**
     * Cantidad de elementos (SCARD).
     *
     * @param key clave
     * @return tamaño
     */
    public long size(String key) {
        Long size = redis.opsForSet().size(key);
        return size == null ? 0 : size;
    }
}
