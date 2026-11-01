package com.juniverse.redis.model;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Producto. Implementa {@link Serializable} porque la caché lo guarda en Redis con serialización de JDK.
 *
 * @param id identificador
 * @param name nombre
 * @param price precio
 */
public record Product(long id, String name, BigDecimal price) implements Serializable {

    private static final long serialVersionUID = 1L;
}
