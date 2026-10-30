package com.juniverse.redis.pubsub;

import com.juniverse.redis.config.RedisConfig;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <b>Pub/Sub</b>: mensajería de difusión en tiempo real. El que publica no sabe quién escucha y el mensaje solo lo reciben
 * los suscriptores conectados en ese instante (no se guarda: para eso están los Streams).
 */
@Service
public class Publisher {

    private final StringRedisTemplate redis;

    /**
     * Crea el servicio.
     *
     * @param redis plantilla de Redis para texto
     */
    public Publisher(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Publica un mensaje en el canal (PUBLISH).
     *
     * @param message contenido
     * @return cuántos suscriptores lo recibieron
     */
    public long publish(String message) {
        Long receivers = redis.convertAndSend(RedisConfig.CHANNEL, message);
        return receivers == null ? 0 : receivers;
    }
}
