package com.example.smartshop.configs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Custom Redis Health Indicator
 * Kiểm tra kết nối Redis và hiển thị status
 */
@Component
@Slf4j
public class RedisHealthCheck implements HealthIndicator {

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Override
    public Health health() {
        try {
            // Test ping Redis
            String pong = redisConnectionFactory.getConnection().ping();

            if ("PONG".equalsIgnoreCase(pong)) {
                log.debug("Redis health check: SUCCESS");
                return Health.up()
                        .withDetail("redis", "Connected")
                        .withDetail("ping", pong)
                        .withDetail("status", "Available")
                        .build();
            } else {
                log.warn("Redis health check: UNEXPECTED RESPONSE - {}", pong);
                return Health.down()
                        .withDetail("redis", "Unexpected response")
                        .withDetail("ping", pong)
                        .build();
            }
        } catch (Exception e) {
            log.error("Redis health check: FAILED", e);
            return Health.down()
                    .withDetail("redis", "Connection failed")
                    .withDetail("error", e.getMessage())
                    .withDetail("status", "Unavailable")
                    .build();
        }
    }
}