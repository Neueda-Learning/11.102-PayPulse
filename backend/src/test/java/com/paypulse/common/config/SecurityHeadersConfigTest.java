package com.paypulse.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityHeadersConfigTest.PingController.class)
@Import(SecurityHeadersConfig.class)
class SecurityHeadersConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private io.github.bucket4j.distributed.proxy.ProxyManager<byte[]> proxyManager;

    @RestController
    static class PingController {
        @GetMapping("/ping")
        String ping() {
            return "ok";
        }
    }

    @Test
    void security_headers_are_present() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-XSS-Protection", "0"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'"));
    }
}

