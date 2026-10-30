package com.juniverse.redis.ops;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoSearchCommandArgs;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <b>Geoespacial</b>: guarda coordenadas y responde "¿qué hay cerca de aquí?" (por debajo es un conjunto ordenado).
 * Comandos: GEOADD, GEODIST, GEOSEARCH.
 */
@Service
public class GeoDemo {

    private static final String KEY = "demo:places";

    /**
     * Lugar cercano.
     *
     * @param name nombre
     * @param distanceKm distancia al punto de búsqueda en kilómetros
     */
    public record Nearby(String name, double distanceKm) {
    }

    private final StringRedisTemplate redis;

    /**
     * Crea el servicio.
     *
     * @param redis plantilla de Redis para texto
     */
    public GeoDemo(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Registra un lugar (GEOADD). Atención al orden: longitud primero, latitud después.
     *
     * @param name nombre del lugar
     * @param longitude longitud
     * @param latitude latitud
     */
    public void addPlace(String name, double longitude, double latitude) {
        redis.opsForGeo().add(KEY, new Point(longitude, latitude), name);
    }

    /**
     * Distancia entre dos lugares (GEODIST).
     *
     * @param a primer lugar
     * @param b segundo lugar
     * @return kilómetros, o {@code null} si alguno no existe
     */
    public Double distanceKm(String a, String b) {
        Distance distance = redis.opsForGeo().distance(KEY, a, b, Metrics.KILOMETERS);
        return distance == null ? null : distance.getValue();
    }

    /**
     * Lugares dentro de un radio, del más cercano al más lejano (GEOSEARCH).
     *
     * @param longitude longitud del centro
     * @param latitude latitud del centro
     * @param radiusKm radio en kilómetros
     * @return lugares encontrados con su distancia
     */
    public List<Nearby> nearby(double longitude, double latitude, double radiusKm) {
        GeoResults<GeoLocation<String>> results = redis.opsForGeo().search(KEY,
                GeoReference.fromCoordinate(new Point(longitude, latitude)),
                new Distance(radiusKm, Metrics.KILOMETERS),
                GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().sortAscending());
        List<Nearby> list = new ArrayList<>();
        for (GeoResult<GeoLocation<String>> result : results) {
            list.add(new Nearby(result.getContent().getName(), result.getDistance().getValue()));
        }
        return list;
    }
}
