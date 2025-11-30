package com.rc.md.analytics;

import com.rc.md.analytics.candle.CandleEntity;
import com.rc.md.analytics.candle.CandleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class HistoryControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14-alpine")
            .withDatabaseName("md_db")
            .withUsername("md_user")
            .withPassword("md_password");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CandleRepository candleRepository;

    @BeforeEach
    void setup() {
        candleRepository.deleteAll();
    }

    @Test
    void historyEndpointReturnsExpectedCandleData() throws Exception {
        String symbol = "BTCUSD";
        int intervalSec = 60;
        long bucketSec = 1_700_000_000L;
        OffsetDateTime bucketStart = OffsetDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(bucketSec), ZoneOffset.UTC);

        CandleEntity candle = new CandleEntity();
        candle.setSymbol(symbol);
        candle.setIntervalSec(intervalSec);
        candle.setBucketStart(bucketStart);
        candle.setOpen(100.0);
        candle.setHigh(110.0);
        candle.setLow(90.0);
        candle.setClose(105.0);
        candle.setVolume(5L);
        candleRepository.save(candle);

        long from = bucketSec - 60;
        long to = bucketSec + 60;

        mockMvc.perform(get("/history")
                        .param("symbol", symbol)
                        .param("interval", "1m")
                        .param("from", String.valueOf(from))
                        .param("to", String.valueOf(to)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.s", is("ok")))
                .andExpect(jsonPath("$.t[0]", is((int) bucketSec)))
                .andExpect(jsonPath("$.o[0]", is(100.0)))
                .andExpect(jsonPath("$.h[0]", is(110.0)))
                .andExpect(jsonPath("$.l[0]", is(90.0)))
                .andExpect(jsonPath("$.c[0]", is(105.0)))
                .andExpect(jsonPath("$.v[0]", is(5)));
    }
}
