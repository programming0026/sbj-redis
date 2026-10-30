package com.juniverse.redis.ops;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <b>Streams</b>: un registro de eventos de solo añadir, con identificadores ordenados en el tiempo y <i>grupos de
 * consumidores</i> que reparten el trabajo y confirman cada mensaje (como un Kafka muy ligero).
 * Comandos: XADD, XRANGE, XGROUP CREATE, XREADGROUP, XACK.
 */
@Service
public class StreamsDemo {

    /**
     * Mensaje leído de un stream.
     *
     * @param id identificador generado por Redis (marca de tiempo + secuencia)
     * @param fields contenido
     */
    public record StreamMessage(String id, Map<String, String> fields) {
    }

    private final StringRedisTemplate redis;

    /**
     * Crea el servicio.
     *
     * @param redis plantilla de Redis para texto
     */
    public StreamsDemo(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Añade un evento al final del stream (XADD).
     *
     * @param stream nombre del stream
     * @param fields contenido del evento
     * @return id asignado
     */
    public String append(String stream, Map<String, String> fields) {
        RecordId id = redis.opsForStream().add(StreamRecords.newRecord().in(stream).ofMap(fields));
        return id.getValue();
    }

    /**
     * Lee todos los eventos, en orden (XRANGE - +).
     *
     * @param stream nombre del stream
     * @return eventos
     */
    public List<StreamMessage> readAll(String stream) {
        return toMessages(redis.opsForStream().range(stream, Range.unbounded()));
    }

    /**
     * Crea un grupo de consumidores que empieza desde el principio del stream (XGROUP CREATE). El stream debe existir.
     *
     * @param stream nombre del stream
     * @param group nombre del grupo
     */
    public void createGroup(String stream, String group) {
        redis.opsForStream().createGroup(stream, ReadOffset.from("0-0"), group);
    }

    /**
     * Un consumidor del grupo pide mensajes que nadie del grupo haya recibido (XREADGROUP ... &gt;). Cada mensaje se
     * entrega a UN solo consumidor, así se reparte el trabajo.
     *
     * @param stream nombre del stream
     * @param group grupo
     * @param consumer nombre del consumidor
     * @param count máximo de mensajes
     * @return mensajes entregados (quedan "pendientes" hasta confirmarlos)
     */
    public List<StreamMessage> readGroup(String stream, String group, String consumer, int count) {
        return toMessages(redis.opsForStream().read(Consumer.from(group, consumer), StreamReadOptions.empty().count(count),
                StreamOffset.create(stream, ReadOffset.lastConsumed())));
    }

    /**
     * Confirma que un mensaje se procesó (XACK); si no se confirma, otro consumidor podría reclamarlo.
     *
     * @param stream nombre del stream
     * @param group grupo
     * @param id id del mensaje
     * @return cuántos mensajes se confirmaron
     */
    public long acknowledge(String stream, String group, String id) {
        Long acked = redis.opsForStream().acknowledge(stream, group, id);
        return acked == null ? 0 : acked;
    }

    /**
     * Mensajes entregados pero aún sin confirmar (XPENDING).
     *
     * @param stream nombre del stream
     * @param group grupo
     * @return cantidad de pendientes
     */
    public long pendingCount(String stream, String group) {
        return redis.opsForStream().pending(stream, group).getTotalPendingMessages();
    }

    private static List<StreamMessage> toMessages(List<MapRecord<String, Object, Object>> records) {
        List<StreamMessage> messages = new ArrayList<>();
        if (records == null) {
            return messages;
        }
        for (MapRecord<String, Object, Object> record : records) {
            Map<String, String> fields = new java.util.LinkedHashMap<>();
            record.getValue().forEach((k, v) -> fields.put(String.valueOf(k), String.valueOf(v)));
            messages.add(new StreamMessage(record.getId().getValue(), fields));
        }
        return messages;
    }
}
