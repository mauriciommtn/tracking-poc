package com.poc.tracking.controller;

import com.poc.tracking.emulator.EventEmulator;
import com.poc.tracking.gateway.EventStore;
import com.poc.tracking.metrics.PerformanceMonitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração para {@link ComparisonController}.
 *
 * <p>Valida o fluxo completo de comparação entre os cenários Polling e Event-Driven,
 * incluindo execução conjunta, geração de relatório e reset de estado.
 *
 * @author Mauricio Nogueira
 * @version 1.0.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("ComparisonController - Testes de Integração")
class ComparisonControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private PerformanceMonitor monitor;

    @Autowired
    private EventEmulator emulator;

    @BeforeEach
    void setUp() {
        eventStore.clearAll();
        monitor.reset();
        emulator.reset();
    }

    // ─── POST /run ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /run: deve retornar 200 com resultado de ambos cenários")
    void runComparison_shouldReturn200WithBothScenarios() throws Exception {
        mockMvc.perform(post("/api/comparison/run")
                        .param("quantity", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.polling").exists())
                .andExpect(jsonPath("$.eventDriven").exists());
    }

    @Test
    @DisplayName("POST /run: eventsPerScenario deve refletir quantity informado")
    void runComparison_eventsPerScenarioShouldMatchQuantity() throws Exception {
        mockMvc.perform(post("/api/comparison/run")
                        .param("quantity", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventsPerScenario").value(20));
    }

    @Test
    @DisplayName("POST /run: deve incluir latências brutas de ambos os cenários")
    void runComparison_shouldIncludeRawLatencies() throws Exception {
        mockMvc.perform(post("/api/comparison/run")
                        .param("quantity", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latenciesPoll").isArray())
                .andExpect(jsonPath("$.latenciesEvent").isArray())
                .andExpect(jsonPath("$.latenciesPoll", hasSize(5)))
                .andExpect(jsonPath("$.latenciesEvent", hasSize(5)));
    }

    @Test
    @DisplayName("POST /run: estatísticas de polling devem conter count correto")
    void runComparison_pollingStatsShouldHaveCorrectCount() throws Exception {
        mockMvc.perform(post("/api/comparison/run")
                        .param("quantity", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.polling.count").value(15.0));
    }

    @Test
    @DisplayName("POST /run: estatísticas de eventDriven devem conter count correto")
    void runComparison_eventDrivenStatsShouldHaveCorrectCount() throws Exception {
        mockMvc.perform(post("/api/comparison/run")
                        .param("quantity", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventDriven.count").value(15.0));
    }

    @Test
    @DisplayName("POST /run: deve usar quantity padrão quando não informado")
    void runComparison_shouldUseDefaultQuantity() throws Exception {
        mockMvc.perform(post("/api/comparison/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventsPerScenario").isNumber());
    }

    // ─── GET /report ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /report: deve retornar 200 com estrutura de relatório")
    void getReport_shouldReturn200WithReportStructure() throws Exception {
        mockMvc.perform(get("/api/comparison/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.polling").exists())
                .andExpect(jsonPath("$.eventDriven").exists());
    }

    @Test
    @DisplayName("GET /report: deve incluir análise comparativa após execução")
    void getReport_shouldIncludeComparativeAnalysis() throws Exception {
        mockMvc.perform(post("/api/comparison/run").param("quantity", "20"));

        mockMvc.perform(get("/api/comparison/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latencyImprovementPercent").isNumber())
                .andExpect(jsonPath("$.fasterScenario").isString());
    }

    @Test
    @DisplayName("GET /report: resposta deve ser JSON")
    void getReport_shouldReturnJson() throws Exception {
        mockMvc.perform(get("/api/comparison/report"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // ─── DELETE /reset ────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /reset: deve retornar 200 com mensagem de confirmação")
    void reset_shouldReturn200WithConfirmation() throws Exception {
        mockMvc.perform(delete("/api/comparison/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reset concluído"));
    }

    @Test
    @DisplayName("DELETE /reset: após reset, métricas de polling devem estar zeradas")
    void reset_pollingMetricsShouldBeZeroAfterReset() throws Exception {
        mockMvc.perform(post("/api/comparison/run").param("quantity", "10"));
        mockMvc.perform(delete("/api/comparison/reset"));

        mockMvc.perform(get("/api/polling/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0.0));
    }

    @Test
    @DisplayName("DELETE /reset: após reset, métricas de eventDriven devem estar zeradas")
    void reset_eventDrivenMetricsShouldBeZeroAfterReset() throws Exception {
        mockMvc.perform(post("/api/comparison/run").param("quantity", "10"));
        mockMvc.perform(delete("/api/comparison/reset"));

        mockMvc.perform(get("/api/events/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0.0));
    }
}
