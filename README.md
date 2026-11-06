# Redis con Spring Boot 4: tipos de datos, caché, pub/sub, streams y patrones

Ejemplo con **Spring Boot 4** y **Java 21** que se conecta a **Redis** con Spring Data Redis (cliente Lettuce) y recorre sus operaciones: todos los tipos de datos principales, mensajería, transacciones, scripts Lua y los patrones más usados en la práctica (caché, bloqueo distribuido, limitador de peticiones, ranking).

## Qué muestra el ejemplo

| Clase | Tema | Comandos de Redis |
|-------|------|-------------------|
| `ops/StringsDemo` | **Strings**: valores con TTL, contadores atómicos, "guardar si no existe", lotes | SET EX, GET, INCR, INCRBY, SETNX, TTL, MSET, MGET, GETDEL, DEL |
| `ops/HashesDemo` | **Hashes**: un objeto (perfil) con campos | HSET, HGETALL, HGET, HINCRBY, HDEL, HLEN |
| `ops/ListsDemo` | **Listas**: cola FIFO, espera bloqueante, "últimos N" | LPUSH, RPOP, BRPOP, LTRIM, LRANGE, LLEN |
| `ops/SetsDemo` | **Conjuntos**: únicos y operaciones de conjuntos | SADD, SMEMBERS, SISMEMBER, SINTER, SUNION, SDIFF, SRANDMEMBER |
| `ops/SortedSetsDemo` | **Conjuntos ordenados**: ranking | ZINCRBY, ZREVRANGE, ZREVRANK, ZSCORE, ZRANGEBYSCORE |
| `ops/GeoDemo` | **Geoespacial**: distancia y lugares cercanos | GEOADD, GEODIST, GEOSEARCH |
| `ops/HyperLogLogDemo` | **HyperLogLog**: únicos aproximados con memoria fija | PFADD, PFCOUNT, PFMERGE |
| `ops/BitmapsDemo` | **Bitmaps**: usuarios activos por día con 1 bit cada uno | SETBIT, GETBIT, BITCOUNT, BITOP |
| `ops/StreamsDemo` | **Streams**: registro de eventos y grupos de consumidores | XADD, XRANGE, XGROUP, XREADGROUP, XACK, XPENDING |
| `pubsub/` | **Pub/Sub**: difusión en tiempo real con un `RedisMessageListenerContainer` | PUBLISH, SUBSCRIBE |
| `ops/TransactionsDemo` | **MULTI/EXEC**, **pipeline** y **scripts Lua** | MULTI, EXEC, EVAL |
| `cache/ProductService` | **Caché de Spring** con `@Cacheable`, `@CachePut` y `@CacheEvict`; TTL de 10 min y prefijo de claves | GET, SET EX, DEL |
| `patterns/DistributedLock` | **Bloqueo distribuido** (SET NX PX + liberación con Lua por token) | SET NX PX, EVAL |
| `patterns/RateLimiter` | **Limitador de peticiones** de ventana fija (INCR + PEXPIRE atómicos en Lua) | EVAL, TTL |

## Cuándo usar cada tipo

| Necesito... | Usa |
|-------------|-----|
| Un valor o contador simple, con caducidad | String |
| Guardar un objeto y cambiar un campo suelto | Hash |
| Cola de trabajos o últimos N elementos | Lista |
| Elementos únicos, etiquetas, "amigos en común", sorteos | Set |
| Ranking o ordenar por puntuación/fecha | Sorted Set |
| "Qué hay cerca de mí" | Geo |
| Visitantes únicos (sin necesitar exactitud) | HyperLogLog |
| Banderas de millones de usuarios (activo / no activo) | Bitmap |
| Eventos persistentes con varios consumidores que confirman | Stream |
| Avisar a todos los conectados ahora mismo (sin guardar) | Pub/Sub |

## Estructura

```text
src/main/java/com/juniverse/redis/
├── RedisApplication.java       # @EnableCaching
├── config/RedisConfig.java     # política de caché y suscriptor de pub/sub
├── controller/                 # RedisController (API)
├── service/
│   ├── ProductService.java     # caché de Spring
│   ├── DistributedLock.java, RateLimiter.java   # patrones
│   └── demo/                   # una clase por tipo de dato (+ transacciones)
├── repository/                 # MessageStore
├── messaging/                  # Publisher
├── model/                      # Product
├── dto/                        # ProductRequest
├── exception/                  # ProductNotFoundException
└── util/                       # DemoRunner (recorrido por consola)
```

## Requisitos

- JDK 21 y Maven 3.9 o superior.
- Docker (para levantar Redis y para las pruebas de integración).

## Ejecutar

```bash
docker compose up -d      # Redis 7 en localhost:6379
mvn spring-boot:run
```

Al arrancar, `DemoRunner` ejecuta un recorrido por todas las operaciones y lo escribe en el log (se desactiva con `app.demo=false`). Después puedes probar la API:

```bash
curl http://localhost:8080/api/products/1            # 1.ª vez: lee de la "base de datos"; las siguientes, de Redis
curl http://localhost:8080/api/products/stats        # {"databaseReads":1} aunque lo hayas pedido varias veces
curl -X PUT http://localhost:8080/api/products/1 -H "Content-Type: application/json" -d '{"name":"Laptop Pro","price":20000}'
curl -X DELETE http://localhost:8080/api/products/1  # invalida la entrada de la caché

curl -X POST "http://localhost:8080/api/leaderboard/ana?points=50"
curl "http://localhost:8080/api/leaderboard?top=5"

curl -X POST http://localhost:8080/api/ratelimit/cliente1   # 5 por cada 10 s; la sexta responde 429 + Retry-After

curl -X POST http://localhost:8080/api/messages -H "Content-Type: application/json" -d '{"text":"hola"}'
curl http://localhost:8080/api/messages
```

Para ver los datos directamente: `docker exec -it sbj-redis redis-cli` y luego `KEYS juniverse:*`, `TTL juniverse:cache:products:1`, `ZREVRANGE demo:leaderboard 0 -1 WITHSCORES`.

Cambia el servidor con las variables `REDIS_HOST` y `REDIS_PORT`.

## Pruebas

```bash
mvn verify
```

`RedisIT` (16 pruebas) levanta un contenedor `redis:7-alpine` con **Testcontainers** y limpia la base antes de cada prueba. Cubre todas las tablas anteriores: expiración real de claves, FIFO y bloqueo con timeout, ranking, distancia geográfica, error de HyperLogLog dentro del 2 %, grupos de consumidores con confirmación, pub/sub, MULTI/EXEC, pipeline, Lua, el bloqueo distribuido (exclusividad, token ajeno, expiración), la caché (cuántas lecturas reales hubo, TTL de la entrada, `@CachePut`, `@CacheEvict`) y el limitador. Si Docker no está disponible las pruebas se omiten.

## Buenas prácticas con Redis

- **Siempre pon TTL** a datos de caché y bloqueos; si no, la memoria crece sin límite.
- **Nunca uses `KEYS`** en producción (bloquea el servidor); usa `SCAN`. Aquí solo aparece en las pruebas.
- Prefija las claves (`app:entidad:id`) para evitar colisiones y poder inspeccionarlas.
- Para varias operaciones que deben ir juntas usa Lua; las transacciones MULTI/EXEC **no tienen rollback**.
- Pub/Sub no guarda mensajes (si nadie escucha se pierden); si necesitas entrega confiable usa Streams.
- La caché se serializa aquí con JDK (los objetos son `Serializable`); en producción es común JSON para poder leer y versionar los datos.
- El bloqueo distribuido de este ejemplo sirve para un Redis único; con réplicas o requisitos estrictos estudia Redlock/Redisson.
- Redis es en memoria: no lo trates como tu base de datos principal sin configurar persistencia (RDB/AOF).

Fat jar ejecutable: `mvn clean package` y `java -jar target/sbj-redis-0.0.1-SNAPSHOT.jar`.

## Licencia

Código de ejemplo de uso libre con fines educativos.
