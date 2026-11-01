package com.juniverse.redis.service.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

/**
 * <b>Conjuntos ordenados</b>: cada elemento tiene una puntuación y Redis los mantiene ordenados. Es la estructura ideal
 * para <i>rankings</i>. Comandos: ZINCRBY, ZADD, ZREVRANGE, ZREVRANK, ZSCORE, ZRANGEBYSCORE.
 */
@Service
public class SortedSetsDemo {

    private static final String KEY = "demo:leaderboard";

    /**
     * Posición en el ranking.
     *
     * @param rank posición (1 = primero)
     * @param player jugador
     * @param score puntos
     */
    public record Entry(long rank, String player, double score) {
    }

    private final StringRedisTemplate redis;

    /**
     * Crea el servicio.
     *
     * @param redis plantilla de Redis para texto
     */
    public SortedSetsDemo(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Suma puntos a un jugador (ZINCRBY); si no existe lo crea con esos puntos.
     *
     * @param player jugador
     * @param points puntos a sumar
     * @return puntuación total
     */
    public double addPoints(String player, double points) {
        return redis.opsForZSet().incrementScore(KEY, player, points);
    }

    /**
     * Los mejores N jugadores de mayor a menor (ZREVRANGE ... WITHSCORES).
     *
     * @param n cuántos devolver
     * @return ranking
     */
    public List<Entry> top(int n) {
        Set<TypedTuple<String>> tuples = redis.opsForZSet().reverseRangeWithScores(KEY, 0, n - 1L);
        List<Entry> result = new ArrayList<>();
        long position = 1;
        for (TypedTuple<String> tuple : tuples) {
            result.add(new Entry(position++, tuple.getValue(), tuple.getScore()));
        }
        return result;
    }

    /**
     * Posición de un jugador (ZREVRANK, que empieza en 0, por eso se suma 1).
     *
     * @param player jugador
     * @return posición, o {@code null} si no está en el ranking
     */
    public Long rank(String player) {
        Long rank = redis.opsForZSet().reverseRank(KEY, player);
        return rank == null ? null : rank + 1;
    }

    /**
     * Puntuación de un jugador (ZSCORE).
     *
     * @param player jugador
     * @return puntos, o {@code null} si no existe
     */
    public Double score(String player) {
        return redis.opsForZSet().score(KEY, player);
    }

    /**
     * Jugadores con puntuación entre dos valores, de menor a mayor (ZRANGEBYSCORE).
     *
     * @param min puntuación mínima (inclusive)
     * @param max puntuación máxima (inclusive)
     * @return jugadores en ese rango
     */
    public Set<String> betweenScores(double min, double max) {
        return redis.opsForZSet().rangeByScore(KEY, min, max);
    }
}
