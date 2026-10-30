package com.juniverse.redis.cache;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * <b>Caché de Spring con Redis</b>: las anotaciones evitan repetir consultas lentas. El "mapa" hace de base de datos y
 * cuenta cuántas lecturas reales se hicieron, para ver que la caché funciona.
 * <ul>
 *   <li>{@code @Cacheable}: si el resultado ya está en Redis lo devuelve sin ejecutar el método.</li>
 *   <li>{@code @CachePut}: ejecuta el método y actualiza la caché con el resultado.</li>
 *   <li>{@code @CacheEvict}: borra la entrada de la caché.</li>
 * </ul>
 */
@Service
public class ProductService {

    private final Map<Long, Product> database = new ConcurrentHashMap<>();
    private final AtomicInteger databaseReads = new AtomicInteger();

    /** Carga tres productos de ejemplo. */
    public ProductService() {
        database.put(1L, new Product(1, "Laptop", new BigDecimal("15999.00")));
        database.put(2L, new Product(2, "Mouse", new BigDecimal("349.90")));
        database.put(3L, new Product(3, "Teclado", new BigDecimal("1299.00")));
    }

    /**
     * Busca un producto. La primera vez consulta la "base de datos"; las siguientes salen de Redis hasta que la entrada expire.
     * Las excepciones no se cachean.
     *
     * @param id identificador
     * @return el producto
     * @throws ProductNotFoundException si no existe
     */
    @Cacheable(cacheNames = "products", key = "#id")
    public Product find(long id) {
        databaseReads.incrementAndGet();
        Product product = database.get(id);
        if (product == null) {
            throw new ProductNotFoundException(id);
        }
        return product;
    }

    /**
     * Guarda un producto y deja la caché actualizada con el valor nuevo (así no se sirve un dato viejo).
     *
     * @param product producto
     * @return el producto guardado
     */
    @CachePut(cacheNames = "products", key = "#product.id()")
    public Product save(Product product) {
        database.put(product.id(), product);
        return product;
    }

    /**
     * Elimina un producto y su entrada en la caché.
     *
     * @param id identificador
     */
    @CacheEvict(cacheNames = "products", key = "#id")
    public void delete(long id) {
        database.remove(id);
    }

    /**
     * Lecturas reales a la "base de datos" desde el arranque.
     *
     * @return cantidad de lecturas
     */
    public int databaseReads() {
        return databaseReads.get();
    }
}
