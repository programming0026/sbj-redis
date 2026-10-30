package com.juniverse.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.juniverse.redis.cache.ProductService;
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
import com.juniverse.redis.pubsub.MessageStore;
import com.juniverse.redis.pubsub.Publisher;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Pruebas de integración contra un Redis real que levanta Testcontainers (requiere Docker).
 */
@SpringBootTest(properties = "app.demo=false")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Redis: operaciones por tipo de dato y patrones")
class RedisIT {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired private WebApplicationContext context;
    @Autowired private StringRedisTemplate template;
    @Autowired private StringsDemo strings;
    @Autowired private HashesDemo hashes;
    @Autowired private ListsDemo lists;
    @Autowired private SetsDemo sets;
    @Autowired private SortedSetsDemo sortedSets;
    @Autowired private GeoDemo geo;
    @Autowired private HyperLogLogDemo hyperLogLog;
    @Autowired private BitmapsDemo bitmaps;
    @Autowired private StreamsDemo streams;
    @Autowired private TransactionsDemo transactions;
    @Autowired private DistributedLock lock;
    @Autowired private Publisher publisher;
    @Autowired private MessageStore messageStore;
    @Autowired private ProductService productService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        template.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        messageStore.clear();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("strings: SET con TTL, INCR atómico, SETNX, MSET/MGET y GETDEL")
    void stringsOperations() {
        strings.save("saludo", "hola", Duration.ofSeconds(60));
        assertEquals("hola", strings.get("saludo").orElseThrow());
        long ttl = strings.ttlSeconds("saludo");
        assertTrue(ttl > 50 && ttl <= 60);
        assertEquals(-2, strings.ttlSeconds("no-existe"));

        assertEquals(1, strings.increment("contador"));
        assertEquals(6, strings.incrementBy("contador", 5));

        assertTrue(strings.setIfAbsent("unico", "a", Duration.ofSeconds(10)));
        assertFalse(strings.setIfAbsent("unico", "b", Duration.ofSeconds(10)));
        assertEquals("a", strings.get("unico").orElseThrow());

        strings.multiSet(Map.of("k1", "v1", "k2", "v2"));
        assertEquals(java.util.Arrays.asList("v1", null, "v2"), strings.multiGet(List.of("k1", "k3", "k2")));

        assertEquals("v1", strings.getAndDelete("k1").orElseThrow());
        assertTrue(strings.get("k1").isEmpty());
        assertTrue(strings.delete("k2"));
    }

    @Test
    @DisplayName("la clave expira sola cuando vence el TTL")
    void expiration() throws Exception {
        strings.save("efimero", "x", Duration.ofMillis(300));
        Awaitility.await().atMost(3, TimeUnit.SECONDS).until(() -> strings.get("efimero").isEmpty());
    }

    @Test
    @DisplayName("hashes: campos, HINCRBY y borrado de un campo")
    void hashesOperations() {
        hashes.saveProfile("ana", Map.of("nombre", "Ana", "ciudad", "Quito", "visitas", "0"));
        assertEquals("Ana", hashes.field("ana", "nombre"));
        assertEquals(3, hashes.profile("ana").size());
        assertEquals(2, hashes.incrementField("ana", "visitas", 2));
        hashes.deleteField("ana", "ciudad");
        assertEquals(2, hashes.fieldCount("ana"));
        assertNull(hashes.field("ana", "ciudad"));
    }

    @Test
    @DisplayName("listas: cola FIFO, espera bloqueante y 'recientes' acotados")
    void listsOperations() {
        lists.enqueue("cola", "t1");
        lists.enqueue("cola", "t2");
        assertEquals("t1", lists.dequeue("cola"));
        assertEquals("t2", lists.dequeue("cola"));
        assertNull(lists.dequeue("cola"));
        assertNull(lists.dequeueBlocking("cola", Duration.ofSeconds(1)));

        for (int i = 1; i <= 5; i++) {
            lists.addRecent("recientes", "item" + i, 3);
        }
        assertEquals(List.of("item5", "item4", "item3"), lists.all("recientes"));
        assertEquals(3, lists.size("recientes"));
    }

    @Test
    @DisplayName("conjuntos: intersección, unión, diferencia y pertenencia")
    void setsOperations() {
        assertEquals(3, sets.add("java", "ana", "luis", "eva"));
        assertEquals(1, sets.add("java", "ana", "pedro"));
        sets.add("python", "eva", "juan");
        assertEquals(Set.of("eva"), sets.intersection("java", "python"));
        assertEquals(5, sets.union("java", "python").size());
        assertEquals(Set.of("ana", "luis", "pedro"), sets.difference("java", "python"));
        assertTrue(sets.contains("java", "luis"));
        assertTrue(sets.members("java").contains(sets.randomMember("java")));
        assertEquals(1, sets.remove("java", "luis"));
        assertEquals(3, sets.size("java"));
    }

    @Test
    @DisplayName("conjuntos ordenados: ranking, posición, puntuación y rango por puntos")
    void sortedSetsOperations() {
        sortedSets.addPoints("ana", 100);
        sortedSets.addPoints("luis", 80);
        sortedSets.addPoints("eva", 120);
        assertEquals(150, sortedSets.addPoints("luis", 70));

        List<SortedSetsDemo.Entry> top = sortedSets.top(2);
        assertEquals("luis", top.get(0).player());
        assertEquals(1, top.get(0).rank());
        assertEquals("eva", top.get(1).player());
        assertEquals(3L, sortedSets.rank("ana"));
        assertEquals(100.0, sortedSets.score("ana"));
        assertNull(sortedSets.rank("nadie"));
        assertEquals(Set.of("ana", "eva"), sortedSets.betweenScores(90, 130));
    }

    @Test
    @DisplayName("geo: distancia y búsqueda por radio")
    void geoOperations() {
        geo.addPlace("Palermo", 13.361389, 38.115556);
        geo.addPlace("Catania", 15.087269, 37.502669);
        geo.addPlace("Agrigento", 13.576355, 37.311100);

        assertEquals(166.27, geo.distanceKm("Palermo", "Catania"), 0.5);
        assertNull(geo.distanceKm("Palermo", "Atlantis"));

        List<GeoDemo.Nearby> near = geo.nearby(15, 37, 100);
        assertEquals("Catania", near.get(0).name());
        assertEquals(1, near.size());
        assertEquals(3, geo.nearby(15, 37, 300).size());
    }

    @Test
    @DisplayName("HyperLogLog: cuenta aproximada de únicos, ignora repetidos y une conjuntos")
    void hyperLogLogOperations() {
        for (int i = 0; i < 1000; i++) {
            hyperLogLog.add("dia1", "user" + i);
            hyperLogLog.add("dia1", "user" + i);
        }
        long count = hyperLogLog.count("dia1");
        assertTrue(Math.abs(count - 1000) <= 20, "estimación " + count);

        for (int i = 500; i < 1500; i++) {
            hyperLogLog.add("dia2", "user" + i);
        }
        hyperLogLog.merge("semana", "dia1", "dia2");
        long union = hyperLogLog.count("semana");
        assertTrue(Math.abs(union - 1500) <= 30, "estimación " + union);
    }

    @Test
    @DisplayName("bitmaps: usuarios activos, conteo e intersección de días")
    void bitmapsOperations() {
        for (long user : new long[] {1, 5, 9, 100}) {
            bitmaps.markActive("lunes", user);
        }
        for (long user : new long[] {5, 9, 200}) {
            bitmaps.markActive("martes", user);
        }
        assertTrue(bitmaps.wasActive("lunes", 5));
        assertFalse(bitmaps.wasActive("lunes", 6));
        assertEquals(4, bitmaps.activeCount("lunes"));
        assertEquals(2, bitmaps.activeBothDays("lunes", "martes"));
    }

    @Test
    @DisplayName("streams: añadir, leer, grupo de consumidores, confirmar")
    void streamsOperations() {
        String first = streams.append("eventos", Map.of("tipo", "pedido", "total", "99"));
        streams.append("eventos", Map.of("tipo", "pago", "total", "99"));
        assertNotNull(first);
        assertEquals(2, streams.readAll("eventos").size());
        assertEquals("pedido", streams.readAll("eventos").get(0).fields().get("tipo"));

        streams.createGroup("eventos", "workers");
        List<StreamsDemo.StreamMessage> w1 = streams.readGroup("eventos", "workers", "w1", 1);
        List<StreamsDemo.StreamMessage> w2 = streams.readGroup("eventos", "workers", "w2", 10);
        assertEquals(1, w1.size());
        assertEquals(1, w2.size());
        assertEquals("pedido", w1.get(0).fields().get("tipo"));
        assertEquals("pago", w2.get(0).fields().get("tipo"));
        assertTrue(streams.readGroup("eventos", "workers", "w1", 10).isEmpty());

        assertEquals(2, streams.pendingCount("eventos", "workers"));
        assertEquals(1, streams.acknowledge("eventos", "workers", w1.get(0).id()));
        assertEquals(1, streams.pendingCount("eventos", "workers"));
    }

    @Test
    @DisplayName("pub/sub: el suscriptor recibe lo publicado")
    void pubSub() {
        Awaitility.await().atMost(10, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(300)).until(() -> {
            publisher.publish("hola suscriptores");
            return !messageStore.all().isEmpty();
        });
        assertEquals("hola suscriptores", messageStore.all().get(0));
    }

    @Test
    @DisplayName("transacción MULTI/EXEC, pipeline y script Lua")
    void transactionsPipelineAndLua() {
        strings.multiSet(Map.of("a", "100", "b", "0"));
        List<Object> results = transactions.transfer("a", "b", 30);
        assertEquals(2, results.size());
        assertEquals("70", strings.get("a").orElseThrow());
        assertEquals("30", strings.get("b").orElseThrow());

        assertEquals(3, transactions.bulkSetPipelined(Map.of("p1", "1", "p2", "2", "p3", "3")));
        assertEquals("2", strings.get("p2").orElseThrow());

        assertEquals(1, transactions.incrementBelow("tope", 2));
        assertEquals(2, transactions.incrementBelow("tope", 2));
        assertEquals(-1, transactions.incrementBelow("tope", 2));
        assertEquals("2", strings.get("tope").orElseThrow());
    }

    @Test
    @DisplayName("bloqueo distribuido: exclusivo, solo el dueño libera, expira solo")
    void distributedLock() throws Exception {
        String token = lock.tryLock("tarea", Duration.ofSeconds(10));
        assertNotNull(token);
        assertNull(lock.tryLock("tarea", Duration.ofSeconds(10)));
        assertFalse(lock.unlock("tarea", "token-ajeno"));
        assertTrue(lock.unlock("tarea", token));
        assertNotNull(lock.tryLock("tarea", Duration.ofSeconds(10)));

        assertNotNull(lock.tryLock("corto", Duration.ofMillis(200)));
        Awaitility.await().atMost(3, TimeUnit.SECONDS).until(() -> lock.tryLock("corto", Duration.ofSeconds(5)) != null);

        assertEquals("hecho", lock.withLock("otra", Duration.ofSeconds(5), () -> "hecho"));
        assertNotNull(lock.tryLock("otra", Duration.ofSeconds(5)), "withLock liberó el bloqueo al terminar");
    }

    @Test
    @DisplayName("caché de Spring: la segunda lectura no toca la 'base de datos'; PUT actualiza; DELETE invalida")
    void springCache() throws Exception {
        int before = productService.databaseReads();
        mvc.perform(get("/api/products/1")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Laptop"));
        mvc.perform(get("/api/products/1")).andExpect(status().isOk());
        assertEquals(before + 1, productService.databaseReads());
        assertEquals(1, template.keys("juniverse:cache:products:*").size());
        Long ttl = template.getExpire("juniverse:cache:products:1");
        assertTrue(ttl != null && ttl > 500 && ttl <= 600);

        mvc.perform(put("/api/products/1").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Laptop Pro\",\"price\":20000.00}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/products/1")).andExpect(jsonPath("$.name").value("Laptop Pro"));
        assertEquals(before + 1, productService.databaseReads(), "@CachePut dejó el valor nuevo en la caché");

        mvc.perform(delete("/api/products/1")).andExpect(status().isNoContent());
        // el borrado de la entrada se aplica en Redis de forma casi inmediata (una de cada pocas ejecuciones lo vio con un instante de retraso)
        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(() -> template.keys("juniverse:cache:products:*").isEmpty());
        mvc.perform(get("/api/products/1")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("API: ranking")
    void leaderboardApi() throws Exception {
        mvc.perform(post("/api/leaderboard/ana").param("points", "50")).andExpect(jsonPath("$.rank").value(1));
        mvc.perform(post("/api/leaderboard/luis").param("points", "80")).andExpect(jsonPath("$.rank").value(1));
        mvc.perform(get("/api/leaderboard").param("top", "2"))
                .andExpect(jsonPath("$[0].player").value("luis"))
                .andExpect(jsonPath("$[1].player").value("ana"));
    }

    @Test
    @DisplayName("API: limitador, la sexta petición recibe 429")
    void rateLimitApi() throws Exception {
        for (int i = 1; i <= 5; i++) {
            mvc.perform(post("/api/ratelimit/cliente1")).andExpect(status().isOk()).andExpect(jsonPath("$.remaining").value(5 - i));
        }
        mvc.perform(post("/api/ratelimit/cliente1")).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        mvc.perform(post("/api/ratelimit/cliente2")).andExpect(status().isOk());
    }
}
