package com.juniverse.redis.web;

import com.juniverse.redis.cache.Product;
import com.juniverse.redis.cache.ProductService;
import com.juniverse.redis.ops.SortedSetsDemo;
import com.juniverse.redis.patterns.RateLimiter;
import com.juniverse.redis.pubsub.MessageStore;
import com.juniverse.redis.pubsub.Publisher;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API REST para probar con {@code curl} los casos más usados: caché, ranking, limitador y pub/sub. */
@RestController
public class RedisController {

    private final ProductService products;
    private final SortedSetsDemo leaderboard;
    private final RateLimiter rateLimiter;
    private final Publisher publisher;
    private final MessageStore messages;

    /**
     * Crea el controlador.
     *
     * @param products servicio con caché
     * @param leaderboard ranking
     * @param rateLimiter limitador
     * @param publisher publicador de mensajes
     * @param messages mensajes recibidos
     */
    public RedisController(ProductService products, SortedSetsDemo leaderboard, RateLimiter rateLimiter, Publisher publisher, MessageStore messages) {
        this.products = products;
        this.leaderboard = leaderboard;
        this.rateLimiter = rateLimiter;
        this.publisher = publisher;
        this.messages = messages;
    }

    /**
     * Producto (con caché en Redis).
     *
     * @param id identificador
     * @return el producto
     */
    @GetMapping("/api/products/{id}")
    public Product product(@PathVariable long id) {
        return products.find(id);
    }

    /**
     * Crea o reemplaza un producto y actualiza la caché.
     *
     * @param id identificador (viene de la ruta, no del cuerpo)
     * @param request nombre y precio
     * @return el producto guardado
     */
    @PutMapping("/api/products/{id}")
    public Product saveProduct(@PathVariable long id, @RequestBody ProductRequest request) {
        return products.save(new Product(id, request.name(), request.price()));
    }

    /**
     * Cuerpo de la petición para guardar un producto.
     *
     * @param name nombre
     * @param price precio
     */
    public record ProductRequest(String name, java.math.BigDecimal price) {
    }

    /**
     * Borra un producto y su entrada de caché.
     *
     * @param id identificador
     */
    @DeleteMapping("/api/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable long id) {
        products.delete(id);
    }

    /**
     * Lecturas reales a la base de datos (para comprobar que la caché evita consultas).
     *
     * @return contador de lecturas
     */
    @GetMapping("/api/products/stats")
    public Map<String, Integer> productStats() {
        return Map.of("databaseReads", products.databaseReads());
    }

    /**
     * Suma puntos a un jugador.
     *
     * @param player jugador
     * @param points puntos
     * @return puntuación total
     */
    @PostMapping("/api/leaderboard/{player}")
    public Map<String, Object> addPoints(@PathVariable String player, @RequestParam double points) {
        return Map.of("player", player, "score", leaderboard.addPoints(player, points), "rank", leaderboard.rank(player));
    }

    /**
     * Mejores jugadores.
     *
     * @param top cuántos devolver
     * @return ranking
     */
    @GetMapping("/api/leaderboard")
    public List<SortedSetsDemo.Entry> top(@RequestParam(defaultValue = "10") int top) {
        return leaderboard.top(top);
    }

    /**
     * Limitador: 5 peticiones cada 10 segundos por cliente. La sexta responde 429.
     *
     * @param client identificador del cliente
     * @return 200 con las peticiones restantes o 429 con {@code Retry-After}
     */
    @PostMapping("/api/ratelimit/{client}")
    public ResponseEntity<Map<String, Object>> rateLimit(@PathVariable String client) {
        RateLimiter.Result result = rateLimiter.hit(client, 5, Duration.ofSeconds(10));
        if (result.allowed()) {
            return ResponseEntity.ok(Map.of("allowed", true, "remaining", result.remaining()));
        }
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", String.valueOf(result.retryAfterSeconds()))
                .body(Map.of("allowed", false, "retryAfterSeconds", result.retryAfterSeconds()));
    }

    /**
     * Publica un mensaje en el canal.
     *
     * @param body objeto con el campo {@code text}
     * @return cuántos suscriptores lo recibieron
     */
    @PostMapping("/api/messages")
    public Map<String, Long> publish(@RequestBody Map<String, String> body) {
        return Map.of("receivers", publisher.publish(body.getOrDefault("text", "")));
    }

    /**
     * Mensajes recibidos por el suscriptor de esta aplicación.
     *
     * @return mensajes
     */
    @GetMapping("/api/messages")
    public List<String> received() {
        return messages.all();
    }
}
