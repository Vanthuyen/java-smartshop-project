package com.example.smartshop.services.serviceimpl;

import com.example.smartshop.services.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class RedisServiceImpl implements RedisService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String BLACKLIST_TOKEN_PREFIX = "BLACKLIST:";
    private static final String PRODUCT_STOCK_PREFIX = "product:stock:";
    private static final long STOCK_CACHE_TTL_MINUTES = 10;
    /**
     * @param token
     * @param expirationMillis
     */
    @Override
    public void addToBlacklist(String token, long expirationMillis) {
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        ops.set(BLACKLIST_TOKEN_PREFIX + token, "blacklisted", expirationMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * @param token
     * @return
     */
    @Override
    public boolean isBlacklisted(String token) {
        return stringRedisTemplate.hasKey(BLACKLIST_TOKEN_PREFIX + token);
    }

    @Override
    public Integer getStock(Long productId) {
        try {
            String key = PRODUCT_STOCK_PREFIX + productId;
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value != null) {
                log.debug("Cache hit for product stock: productId={}", productId);
                return Integer.parseInt(value);
            }
            log.debug("Cache miss for product stock: productId={}", productId);
            return null;
        } catch (Exception e) {
            log.error("Failed to get stock from Redis: productId={}", productId, e);
            return null;
        }
    }

    @Override
    public void updateStock(Long productId, Integer stock) {
        try {
            String key = PRODUCT_STOCK_PREFIX + productId;
            stringRedisTemplate.opsForValue().set(
                    key,
                    String.valueOf(stock),
                    STOCK_CACHE_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
            log.debug("Updated stock cache: productId={}, stock={}", productId, stock);
        } catch (Exception e) {
            log.error("Failed to update stock in Redis: productId={}, stock={}", productId, stock, e);
        }
    }

    @Override
    public void evictStock(Long productId) {
        try {
            String key = PRODUCT_STOCK_PREFIX + productId;
            Boolean deleted = stringRedisTemplate.delete(key);
            log.debug("Evicted stock cache: productId={}, success={}", productId, deleted);
        } catch (Exception e) {
            log.error("Failed to evict stock from Redis: productId={}", productId, e);
        }
    }
}
