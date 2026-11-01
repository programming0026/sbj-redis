package com.juniverse.redis.repository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/** Guarda en memoria los mensajes que llegan por pub/sub (Redis no los conserva: si nadie escucha, se pierden). */
@Component
public class MessageStore {

    private final List<String> messages = new CopyOnWriteArrayList<>();

    /**
     * Guarda un mensaje recibido.
     *
     * @param message contenido
     */
    public void add(String message) {
        messages.add(message);
    }

    /**
     * Mensajes recibidos hasta ahora.
     *
     * @return copia de la lista
     */
    public List<String> all() {
        return List.copyOf(messages);
    }

    /** Vacía la lista. */
    public void clear() {
        messages.clear();
    }
}
