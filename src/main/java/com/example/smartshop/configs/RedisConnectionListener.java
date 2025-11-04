package com.example.smartshop.configs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Kiểm tra kết nối Redis khi application khởi động
 */
@Component
@Slf4j
public class RedisConnectionListener {

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @EventListener(ApplicationReadyEvent.class)
    public void checkRedisConnection() {
        try {
            String pong = redisConnectionFactory.getConnection().ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                log.info("✅ Redis connection successful! Status: CONNECTED");
                log.info("Redis host: {}", redisConnectionFactory.getConnection().getConfig("bind"));
            } else {
                log.warn("⚠️ Redis connection established but unexpected response: {}", pong);
            }
        } catch (Exception e) {
            log.error("❌ Redis connection FAILED! Application will continue but caching is disabled.", e);
            log.error("Error details: {}", e.getMessage());
            log.warn("⚠️ Please check:");
            log.warn("  1. Redis container is running: docker ps | grep redis");
            log.warn("  2. Redis host/port is correct in application.yml");
            log.warn("  3. Network connection: docker network inspect <network_name>");
        }
    }
}