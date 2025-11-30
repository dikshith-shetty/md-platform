package com.rc.md.analytics.unit;

import com.rc.md.analytics.api.HistoryController;
import com.rc.md.analytics.candle.CandleRepository;
import com.rc.md.analytics.config.IntervalConfig;
import com.rc.md.common.config.IntervalDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HistoryControllerValidationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        CandleRepository repo = Mockito.mock(CandleRepository.class);

        IntervalDefinition d1 = new IntervalDefinition();
        d1.setId("1m");
        d1.setSeconds(60);

        IntervalConfig cfg = new IntervalConfig();
        cfg.setIntervals(List.of(d1));

        HistoryController controller = new HistoryController(repo, cfg);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void returnsErrorWhenFromGreaterThanTo() throws Exception {
        mockMvc.perform(get("/history")
                        .param("symbol", "BTCUSD")
                        .param("interval", "1m")
                        .param("from", "100")
                        .param("to", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.s", is("error")));
    }

    @Test
    void returnsErrorWhenIntervalUnsupported() throws Exception {
        mockMvc.perform(get("/history")
                        .param("symbol", "BTCUSD")
                        .param("interval", "5m")
                        .param("from", "0")
                        .param("to", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.s", is("error")));
    }
}
