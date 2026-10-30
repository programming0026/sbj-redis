package com.juniverse.redis.web;

import com.juniverse.redis.ops.BitmapsDemo;
import com.juniverse.redis.ops.GeoDemo;
import com.juniverse.redis.ops.HashesDemo;
import com.juniverse.redis.ops.HyperLogLogDemo;
import com.juniverse.redis.ops.ListsDemo;
import com.juniverse.redis.ops.SetsDemo;
import com.juniverse.redis.ops.SortedSetsDemo;
import com.juniverse.redis.ops.StreamsDemo;
import com.juniverse.redis.ops.StringsDemo;
import com.juniverse.redis.ops.TransactionsDemo;
import com.juniverse.redis.patterns.DistributedLock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Recorrido por consola de todas las operaciones al arrancar. Usa claves con un identificador de ejecución para no
 * interferir con datos previos. Se desactiva con {@code app.demo=false}.
 */
@Component
@ConditionalOnProperty(name = "app.demo", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DemoRunner.class);

    private final StringRedisTemplate redis;
    private final StringsDemo strings;
    private final HashesDemo hashes;
    private final ListsDemo lists;
    private final SetsDemo sets;
    private final SortedSetsDemo sortedSets;
    private final GeoDemo geo;
    private final HyperLogLogDemo hyperLogLog;
    private final BitmapsDemo bitmaps;
    private final StreamsDemo streams;
    private final TransactionsDemo transactions;
    private final DistributedLock lock;

    /**
     * Crea el runner con todos los servicios del ejemplo.
     *
     * @param redis plantilla de Redis
     * @param strings strings
     * @param hashes hashes
     * @param lists listas
     * @param sets conjuntos
     * @param sortedSets conjuntos ordenados
     * @param geo geoespacial
     * @param hyperLogLog HyperLogLog
     * @param bitmaps bitmaps
     * @param streams streams
     * @param transactions transacciones, pipeline y Lua
     * @param lock bloqueo distribuido
     */
    public DemoRunner(StringRedisTemplate redis, StringsDemo strings, HashesDemo hashes, ListsDemo lists, SetsDemo sets, SortedSetsDemo sortedSets,
            GeoDemo geo, HyperLogLogDemo hyperLogLog, BitmapsDemo bitmaps, StreamsDemo streams, TransactionsDemo transactions, DistributedLock lock) {
        this.redis = redis;
        this.strings = strings;
        this.hashes = hashes;
        this.lists = lists;
        this.sets = sets;
        this.sortedSets = sortedSets;
        this.geo = geo;
        this.hyperLogLog = hyperLogLog;
        this.bitmaps = bitmaps;
        this.streams = streams;
        this.transactions = transactions;
        this.lock = lock;
    }

    @Override
    public void run(ApplicationArguments args) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        LOG.info("PING -> {}", redis.execute((org.springframework.data.redis.core.RedisCallback<String>) connection -> connection.ping()));

        strings.save("demo:" + id + ":saludo", "hola redis", Duration.ofSeconds(30));
        LOG.info("STRING   get={} ttl={}s incr={},{}", strings.get("demo:" + id + ":saludo").orElse("?"), strings.ttlSeconds("demo:" + id + ":saludo"),
                strings.increment("demo:" + id + ":visitas"), strings.increment("demo:" + id + ":visitas"));

        hashes.saveProfile(id, Map.of("nombre", "Ana", "ciudad", "Quito"));
        LOG.info("HASH     perfil={} visitas={}", hashes.profile(id), hashes.incrementField(id, "visitas", 1));

        lists.enqueue("demo:" + id + ":cola", "tarea-1");
        lists.enqueue("demo:" + id + ":cola", "tarea-2");
        LOG.info("LIST     FIFO -> {} luego {}", lists.dequeue("demo:" + id + ":cola"), lists.dequeue("demo:" + id + ":cola"));

        sets.add("demo:" + id + ":java", "ana", "luis", "eva");
        sets.add("demo:" + id + ":python", "eva", "juan");
        LOG.info("SET      en ambos={} unión={}", sets.intersection("demo:" + id + ":java", "demo:" + id + ":python"),
                sets.union("demo:" + id + ":java", "demo:" + id + ":python"));

        sortedSets.addPoints("ana-" + id, 120);
        sortedSets.addPoints("luis-" + id, 90);
        sortedSets.addPoints("ana-" + id, 40);
        LOG.info("ZSET     ranking={}", sortedSets.top(3));

        geo.addPlace("Palermo-" + id, 13.361389, 38.115556);
        geo.addPlace("Catania-" + id, 15.087269, 37.502669);
        LOG.info("GEO      distancia Palermo-Catania = {} km", geo.distanceKm("Palermo-" + id, "Catania-" + id));

        hyperLogLog.add("demo:" + id + ":visitantes", "u1", "u2", "u3", "u1", "u2");
        LOG.info("HLL      visitantes únicos ≈ {}", hyperLogLog.count("demo:" + id + ":visitantes"));

        bitmaps.markActive("dia-" + id, 5);
        bitmaps.markActive("dia-" + id, 9);
        LOG.info("BITMAP   activos={} usuario5={}", bitmaps.activeCount("dia-" + id), bitmaps.wasActive("dia-" + id, 5));

        String stream = "demo:" + id + ":eventos";
        streams.append(stream, Map.of("tipo", "pedido", "total", "99"));
        streams.createGroup(stream, "workers");
        LOG.info("STREAM   grupo recibe={}", streams.readGroup(stream, "workers", "w1", 10));

        strings.multiSet(Map.of("demo:" + id + ":a", "100", "demo:" + id + ":b", "0"));
        LOG.info("MULTI    transferencia -> {}", transactions.transfer("demo:" + id + ":a", "demo:" + id + ":b", 30));
        LOG.info("PIPELINE {} respuestas | LUA incrementBelow={}", transactions.bulkSetPipelined(Map.of("demo:" + id + ":p1", "1", "demo:" + id + ":p2", "2")),
                transactions.incrementBelow("demo:" + id + ":limite", 2));

        LOG.info("LOCK     tarea ejecutada={}", lock.withLock("demo-" + id, Duration.ofSeconds(5), () -> "sí, con el bloqueo tomado"));
        LOG.info("Prueba la API: GET /api/products/1 (caché), POST /api/leaderboard/ana?points=10, POST /api/ratelimit/cliente1, POST /api/messages");
    }
}
