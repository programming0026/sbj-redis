package com.juniverse.redis.dto;

/**
 * Cuerpo de la petición para guardar un producto.
 *
 * @param name nombre
 * @param price precio
 */
public record ProductRequest(String name, java.math.BigDecimal price) {
}
