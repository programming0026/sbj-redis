package com.juniverse.redis.config;

import com.juniverse.redis.pubsub.MessageStore;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Configuración de Redis. Spring Boot ya crea la conexión (Lettuce) y un {@code StringRedisTemplate}; aquí solo se añade
 * lo específico del ejemplo: la política de la caché y el suscriptor de pub/sub.
 */
@Configuration
public class RedisConfig {

    /** Canal de pub/sub del ejemplo. */
    public static final String CHANNEL = "juniverse:news";

    /**
     * Política de la caché de Spring: las entradas viven 10 minutos, no se guardan nulos y las claves llevan un prefijo
     * para distinguirlas de otros datos en el mismo Redis. Los valores se serializan con JDK (por eso los objetos
     * cacheados son {@code Serializable}); en producción suele usarse JSON.
     *
     * @return configuración de la caché
     */
    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig(getClass().getClassLoader())
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> "juniverse:cache:" + cacheName + ":");
    }

    /**
     * Contenedor que mantiene la suscripción al canal en un hilo propio y entrega cada mensaje al {@link MessageStore}.
     *
     * @param factory conexión a Redis
     * @param store donde se guardan los mensajes recibidos
     * @return contenedor de listeners
     */
    @Bean
    public RedisMessageListenerContainer listenerContainer(RedisConnectionFactory factory, MessageStore store) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener((message, pattern) -> store.add(new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8)),
                new ChannelTopic(CHANNEL));
        return container;
    }
}
