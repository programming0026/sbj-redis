package com.juniverse.redis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Recorrido por Redis con Spring Data Redis: tipos de datos (strings, hashes, listas, conjuntos, conjuntos ordenados,
 * geo, HyperLogLog, bitmaps, streams), pub/sub, transacciones, pipeline, scripts Lua, caché, bloqueo distribuido
 * y limitador de peticiones.
 */
@SpringBootApplication
@EnableCaching
public class RedisApplication {

    /**
     * Arranca la aplicación.
     *
     * @param args argumentos de la línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(RedisApplication.class, args);
    }
}
