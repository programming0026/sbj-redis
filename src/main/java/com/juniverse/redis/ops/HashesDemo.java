package com.juniverse.redis.ops;

import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <b>Hashes</b>: un mapa campo-valor dentro de una clave, ideal para representar un objeto (perfil de usuario) y poder
 * leer o cambiar un solo campo sin tocar el resto. Comandos: HSET, HGETALL, HGET, HINCRBY, HDEL, HLEN.
 */
@Service
public class HashesDemo {

    private final StringRedisTemplate redis;

    /**
     * Crea el servicio.
     *
     * @param redis plantilla de Redis para texto
     */
    public HashesDemo(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static String key(String user) {
        return "demo:profile:" + user;
    }

    /**
     * Guarda varios campos del perfil (HSET).
     *
     * @param user usuario
     * @param fields campos y valores
     */
    public void saveProfile(String user, Map<String, String> fields) {
        redis.opsForHash().putAll(key(user), fields);
    }

    /**
     * Lee el perfil completo (HGETALL).
     *
     * @param user usuario
     * @return todos los campos
     */
    public Map<Object, Object> profile(String user) {
        return redis.opsForHash().entries(key(user));
    }

    /**
     * Lee un solo campo (HGET).
     *
     * @param user usuario
     * @param field campo
     * @return el valor, o {@code null}
     */
    public String field(String user, String field) {
        Object value = redis.opsForHash().get(key(user), field);
        return value == null ? null : value.toString();
    }

    /**
     * Suma a un campo numérico de forma atómica (HINCRBY), por ejemplo un contador de visitas del perfil.
     *
     * @param user usuario
     * @param field campo numérico
     * @param delta cantidad a sumar
     * @return valor tras sumar
     */
    public long incrementField(String user, String field, long delta) {
        return redis.opsForHash().increment(key(user), field, delta);
    }

    /**
     * Elimina un campo (HDEL).
     *
     * @param user usuario
     * @param field campo
     */
    public void deleteField(String user, String field) {
        redis.opsForHash().delete(key(user), field);
    }

    /**
     * Cantidad de campos (HLEN).
     *
     * @param user usuario
     * @return número de campos
     */
    public long fieldCount(String user) {
        return redis.opsForHash().size(key(user));
    }
}
