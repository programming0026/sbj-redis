package com.juniverse.redis.service.demo;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <b>Listas</b>: secuencias ordenadas con inserción rápida en ambos extremos. Sirven como cola de tareas
 * (LPUSH + RPOP = FIFO) y como "últimos N elementos" (LPUSH + LTRIM).
 */
@Service
public class ListsDemo {

    private final StringRedisTemplate redis;

    /**
     * Crea el servicio.
     *
     * @param redis plantilla de Redis para texto
     */
    public ListsDemo(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Encola una tarea por la izquierda (LPUSH).
     *
     * @param queue nombre de la cola
     * @param task tarea
     * @return tamaño de la cola tras encolar
     */
    public long enqueue(String queue, String task) {
        return redis.opsForList().leftPush(queue, task);
    }

    /**
     * Saca la tarea más antigua por la derecha (RPOP): así la cola es FIFO.
     *
     * @param queue nombre de la cola
     * @return la tarea, o {@code null} si está vacía
     */
    public String dequeue(String queue) {
        return redis.opsForList().rightPop(queue);
    }

    /**
     * Como {@link #dequeue} pero espera hasta {@code timeout} a que llegue algo (BRPOP): un consumidor sin consultar en bucle.
     *
     * @param queue nombre de la cola
     * @param timeout tiempo máximo de espera
     * @return la tarea, o {@code null} si se agotó el tiempo
     */
    public String dequeueBlocking(String queue, Duration timeout) {
        return redis.opsForList().rightPop(queue, timeout);
    }

    /**
     * Añade un elemento a una lista de "recientes" que conserva solo los últimos {@code max} (LPUSH + LTRIM).
     *
     * @param key clave de la lista
     * @param item elemento
     * @param max cantidad máxima a conservar
     */
    public void addRecent(String key, String item, int max) {
        redis.opsForList().leftPush(key, item);
        redis.opsForList().trim(key, 0, max - 1L);
    }

    /**
     * Elementos de la lista, del más nuevo al más antiguo (LRANGE 0 -1).
     *
     * @param key clave de la lista
     * @return todos los elementos
     */
    public List<String> all(String key) {
        return redis.opsForList().range(key, 0, -1);
    }

    /**
     * Longitud de la lista (LLEN).
     *
     * @param key clave de la lista
     * @return número de elementos
     */
    public long size(String key) {
        Long size = redis.opsForList().size(key);
        return size == null ? 0 : size;
    }
}
