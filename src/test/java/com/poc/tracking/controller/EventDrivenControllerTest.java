package com.poc.tracking.controller;

import com.poc.tracking.emulator.EventEmulator;
import com.poc.tracking.gateway.ApiGateway;
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
 * Testes de integração para {@link EventDrivenController}.
 *
 * <p>Valida os endpoints HTTP do Cenário 2 (Event-Driven), incluindo
 * simulação de carga e coleta de métricas.
 *
 * <p>O endpoint SSE ({@code /stream}) é testado pela verificação de que
 * o stream é iniciado corretamente e tem o content-type adequado.
 *
 * @author Mauricio Nogueira
 * @version 1.0.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("EventDrivenController - Testes de Integração")
class EventDrivenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private ApiGateway gateway;

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

    // ─── GET /stream ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /stream: deve responder com content-type text/event-stream")
    void streamEvents_shouldRespondWithEventStreamContentType() throws Exception {
        mockMvc.perform(get("/api/events/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    // ─── POST /simulate ──────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /simulate: deve retornar 200 com resultado da simulação")
    void simulate_shouldReturn200WithResult() throws Exception {
        mockMvc.perform(post("/api/events/simulate")
                        .param("orderId", "ORDER-EVT-TEST")
                        .param("quantity", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORDER-EVT-TEST"))
                .andExpect(jsonPath("$.eventsGenerated").value(5))
                .andExpect(jsonPath("$.scenario").value("EVENT_DRIVEN"));
    }

    @Test
    @DisplayName("POST /simulate: deve usar orderId padrão quando não informado")
    void simulate_shouldUseDefaultOrderId() throws Exception {
        mockMvc.perform(post("/api/events/simulate")
                        .param("quantity", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORDER-EVT-001"));
    }

    @Test
    @DisplayName("POST /simulate: deve registrar métricas para o cenário EVENT_DRIVEN")
    void simulate_shouldRegisterMetricsForEventDrivenScenario() throws Exception {
        mockMvc.perform(post("/api/events/simulate")
                        .param("orderId", "ORDER-EVT-METRICS")
                        .param("quantity", "15"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/events/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(15.0));
    }

    @Test
    @DisplayName("POST /simulate: eventsGenerated deve corresponder ao quantity enviado")
    void simulate_eventsGeneratedShouldMatchQuantity() throws Exception {
        mockMvc.perform(post("/api/events/simulate")
                        .param("orderId", "ORDER-EVT-COUNT")
                        .param("quantity", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventsGenerated").value(25));
    }

    // ─── GET /metrics ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /metrics: deve retornar count=0 quando sem dados de EVENT_DRIVEN")
    void getMetrics_shouldReturnZeroCountWhenEmpty() throws Exception {
        mockMvc.perform(get("/api/events/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0.0));
    }

    @Test
    @DisplayName("GET /metrics: deve conter estatísticas descritivas após simulação")
    void getMetrics_shouldContainDescriptiveStats() throws Exception {
        mockMvc.perform(post("/api/events/simulate")
                        .param("orderId", "ORDER-EVT-STATS")
                        .param("quantity", "30"));

        mockMvc.perform(get("/api/events/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mean").isNumber())
                .andExpect(jsonPath("$.median").isNumber())
                .andExpect(jsonPath("$.p95").isNumber())
                .andExpect(jsonPath("$.stdDev").isNumber());
    }

    @Test
    @DisplayName("GET /metrics: resposta deve ser do tipo JSON")
    void getMetrics_shouldReturnJsonContentType() throws Exception {
        mockMvc.perform(get("/api/events/metrics"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
