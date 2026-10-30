package com.juniverse.redis.ops;

import java.nio.charset.StandardCharsets;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.connection.RedisStringCommands.BitOperation;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <b>Bitmaps</b>: un string tratado como arreglo de bits. Con 1 bit por usuario, 1 millón de usuarios ocupan solo 125 KB.
 * Aquí: "usuarios activos por día". Comandos: SETBIT, GETBIT, BITCOUNT, BITOP.
 */
@Service
public class BitmapsDemo {

    private final StringRedisTemplate redis;

    /**
     * Crea el servicio.
     *
     * @param redis plantilla de Redis para texto
     */
    public BitmapsDemo(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static String key(String day) {
        return "demo:active:" + day;
    }

    /**
     * Marca a un usuario como activo ese día (SETBIT). El id del usuario es la posición del bit.
     *
     * @param day día (por ejemplo, 2026-01-15)
     * @param userId id numérico del usuario
     */
    public void markActive(String day, long userId) {
        redis.opsForValue().setBit(key(day), userId, true);
    }

    /**
     * Consulta si un usuario estuvo activo (GETBIT).
     *
     * @param day día
     * @param userId id del usuario
     * @return {@code true} si estuvo activo
     */
    public boolean wasActive(String day, long userId) {
        return Boolean.TRUE.equals(redis.opsForValue().getBit(key(day), userId));
    }

    /**
     * Cuántos usuarios estuvieron activos (BITCOUNT).
     *
     * @param day día
     * @return cantidad de bits en 1
     */
    public long activeCount(String day) {
        Long count = redis.execute((org.springframework.data.redis.core.RedisCallback<Long>) connection -> connection.stringCommands().bitCount(key(day).getBytes(StandardCharsets.UTF_8)));
        return count == null ? 0 : count;
    }

    /**
     * Usuarios activos en AMBOS días (BITOP AND) y cuántos son.
     *
     * @param dayA primer día
     * @param dayB segundo día
     * @return cantidad de usuarios activos los dos días
     */
    public long activeBothDays(String dayA, String dayB) {
        String destination = "demo:active:both:" + dayA + ":" + dayB;
        Long count = redis.execute((org.springframework.data.redis.core.RedisCallback<Long>) connection -> {
            connection.stringCommands().bitOp(BitOperation.AND, destination.getBytes(StandardCharsets.UTF_8),
                    key(dayA).getBytes(StandardCharsets.UTF_8), key(dayB).getBytes(StandardCharsets.UTF_8));
            return connection.stringCommands().bitCount(destination.getBytes(StandardCharsets.UTF_8));
        });
        return count == null ? 0 : count;
    }
}
