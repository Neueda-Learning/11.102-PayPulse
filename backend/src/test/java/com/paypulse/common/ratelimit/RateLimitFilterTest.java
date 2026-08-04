package com.paypulse.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves FR-11/NFR-11: N+1th request within the window is rejected with 429.
 * Owner: M1.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "paypulse.rate-limit.per-client-requests-per-minute=3",
                "paypulse.rate-limit.global-requests-per-minute=1000"
        })
class RateLimitFilterTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void exceedingPerClientLimit_returns429WithHeaders() {
        String url = "http://localhost:" + port + "/api/v1/accounts";

        for (int i = 0; i < 3; i++) {
            ResponseEntity<String> ok = restTemplate.getForEntity(url, String.class);
            assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        ResponseEntity<String> blocked = restTemplate.getForEntity(url, String.class);

        assertThat(blocked.getStatusCode().value()).isEqualTo(429);
        assertThat(blocked.getHeaders().getFirst("Retry-After")).isNotNull();
        assertThat(blocked.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
    }
}