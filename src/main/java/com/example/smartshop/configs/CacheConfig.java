package com.example.smartshop.configs;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Cache Configuration for SmartShop
 *
 * Cache Strategy:
 * - Category: 30 min (rarely changes)
 * - Categories list: 20 min
 * - Product detail: 15 min (infrequent updates)
 * - Products list: 5 min (frequent updates)
 * - Product stock: 3 min (changes with every order/restock)
 * - Order: 5 min
 * - Orders list: 3 min (new orders frequently)
 * - Inventory logs: 2 min (needs fresh data)
 * - User: 15 min
 *
 * @author SmartShop Team
 * @version 2.0
 */
@Configuration
@EnableCaching
@Slf4j
public class CacheConfig implements CachingConfigurer {

    // ==================== TTL CONSTANTS ====================
    private static final int DEFAULT_TTL_MINUTES = 10;

    // Category caches (rarely change)
    private static final int CATEGORY_TTL_MINUTES = 30;
    private static final int CATEGORIES_LIST_TTL_MINUTES = 20;

    // Product caches
    private static final int PRODUCT_DETAIL_TTL_MINUTES = 15;
    private static final int PRODUCTS_LIST_TTL_MINUTES = 5;
    private static final int PRODUCT_STOCK_TTL_MINUTES = 3;

    // Order caches
    private static final int ORDER_TTL_MINUTES = 5;
    private static final int ORDERS_LIST_TTL_MINUTES = 3;

    // Inventory caches (needs fresh data)
    private static final int INVENTORY_LOG_TTL_MINUTES = 2;

    // User cache
    private static final int USER_TTL_MINUTES = 15;

    /**
     * Configure Redis CacheManager with custom settings for each cache region
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        log.info("=== Initializing Redis CacheManager ===");

        ObjectMapper objectMapper = createObjectMapper();
        RedisCacheConfiguration defaultConfig = createCacheConfig(objectMapper, DEFAULT_TTL_MINUTES);

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Category caches
        cacheConfigurations.put("category", createCacheConfig(objectMapper, CATEGORY_TTL_MINUTES));
        cacheConfigurations.put("categories", createCacheConfig(objectMapper, CATEGORIES_LIST_TTL_MINUTES));

        // Product caches
        cacheConfigurations.put("product", createCacheConfig(objectMapper, PRODUCT_DETAIL_TTL_MINUTES));
        cacheConfigurations.put("products", createCacheConfig(objectMapper, PRODUCTS_LIST_TTL_MINUTES));
        cacheConfigurations.put("productStock", createCacheConfig(objectMapper, PRODUCT_STOCK_TTL_MINUTES));

        // Order caches
        cacheConfigurations.put("order", createCacheConfig(objectMapper, ORDER_TTL_MINUTES));
        cacheConfigurations.put("orders", createCacheConfig(objectMapper, ORDERS_LIST_TTL_MINUTES));

        // Inventory caches
        cacheConfigurations.put("inventory-log", createCacheConfig(objectMapper, INVENTORY_LOG_TTL_MINUTES));

        // User cache
        cacheConfigurations.put("user", createCacheConfig(objectMapper, USER_TTL_MINUTES));

        log.info("Configured {} cache regions: {}", cacheConfigurations.size(), cacheConfigurations.keySet());

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware() // Support Spring transactions
                .build();
    }

    /**
     * Create ObjectMapper for Redis serialization
     * Supports Java 8 Date/Time API and polymorphic types
     */
    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Support LocalDateTime, LocalDate, etc.
        mapper.registerModule(new JavaTimeModule());

        // Serialize dates as ISO-8601 strings, not timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Enable polymorphic type handling (needed for inheritance)
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        // Ignore unknown properties during deserialization
        mapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false
        );

        return mapper;
    }

    /**
     * Create RedisCacheConfiguration with custom TTL
     *
     * @param objectMapper Jackson ObjectMapper for JSON serialization
     * @param ttlMinutes Time-to-live in minutes
     * @return Configured RedisCacheConfiguration
     */
    private RedisCacheConfiguration createCacheConfig(ObjectMapper objectMapper, int ttlMinutes) {
        return RedisCacheConfiguration.defaultCacheConfig()
                // Set TTL
                .entryTtl(Duration.ofMinutes(ttlMinutes))

                // Don't cache null values (prevents cache pollution)
                .disableCachingNullValues()

                // Serialize keys as strings
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()
                        )
                )

                // Serialize values as JSON
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer(objectMapper)
                        )
                );
    }

    /**
     * RedisTemplate for manual Redis operations (non-cache usage)
     * Useful for custom Redis commands outside Spring Cache abstraction
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // String serializer for keys
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // JSON serializer for values
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(createObjectMapper());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();

        log.info("RedisTemplate configured successfully");
        return template;
    }

    /**
     * Global Cache Error Handler
     *
     * CRITICAL: Cache failures should NOT break the application
     * When cache fails, fallback to database queries
     *
     * Best Practice: Fail gracefully and log errors
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {

            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.error("❌ Cache GET failed - cache: '{}', key: '{}', falling back to database",
                        cache.getName(), key);
                log.debug("Cache GET error details:", exception);
                // Don't throw exception - let application query DB instead
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.error("❌ Cache PUT failed - cache: '{}', key: '{}', continuing without caching",
                        cache.getName(), key);
                log.debug("Cache PUT error details:", exception);
                // Don't throw exception - continue processing
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.error("❌ Cache EVICT failed - cache: '{}', key: '{}', cache may be stale",
                        cache.getName(), key);
                log.debug("Cache EVICT error details:", exception);
                // Don't throw exception - stale cache will expire via TTL
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.error("❌ Cache CLEAR failed - cache: '{}', cache may be stale",
                        cache.getName());
                log.debug("Cache CLEAR error details:", exception);
                // Don't throw exception - stale cache will expire via TTL
            }
        };
    }
}