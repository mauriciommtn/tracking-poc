package com.poc.tracking.controller;

import com.poc.tracking.emulator.EventEmulator;
import com.poc.tracking.gateway.ApiGateway;
import com.poc.tracking.gateway.EventStore;
import com.poc.tracking.metrics.PerformanceMonitor;
import com.poc.tracking.model.DeliveryStatus;
import com.poc.tracking.model.Scenario;
import com.poc.tracking.model.TrackingEvent;
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
 * Testes de integração para {@link PollingController}.
 *
 * <p>Utiliza {@link SpringBootTest} com contexto completo e {@link MockMvc}
 * para validar os endpoints HTTP do Cenário 1 (Polling).
 *
 * @author Mauricio Nogueira
 * @version 1.0.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("PollingController - Testes de Integração")
class PollingControllerTest {

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

    // ─── GET /{orderId} ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /{orderId}: deve retornar 404 para pedido inexistente")
    void getLatestEvent_shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/api/polling/ORDER-UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{orderId}: deve retornar 200 com evento mais recente")
    void getLatestEvent_shouldReturn200WithLatestEvent() throws Exception {
        TrackingEvent event = TrackingEvent.of("ORDER-001", -23.55, -46.63,
                DeliveryStatus.IN_TRANSIT, Scenario.POLLING);
        gateway.process(event);

        mockMvc.perform(get("/api/polling/ORDER-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORDER-001"))
                .andExpect(jsonPath("$.scenario").value("POLLING"))
                .andExpect(jsonPath("$.eventId").isNotEmpty());
    }

    @Test
    @DisplayName("GET /{orderId}: deve retornar o último evento após múltiplas inserções")
    void getLatestEvent_shouldReturnLastInsertedEvent() throws Exception {
        TrackingEvent first = TrackingEvent.of("ORDER-002", -23.55, -46.63,
                DeliveryStatus.PENDING, Scenario.POLLING);
        TrackingEvent last = TrackingEvent.of("ORDER-002", -23.56, -46.64,
                DeliveryStatus.DELIVERED, Scenario.POLLING);
        gateway.process(first);
        gateway.process(last);

        mockMvc.perform(get("/api/polling/ORDER-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    // ─── GET /{orderId}/history ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /{orderId}/history: deve retornar 404 para pedido sem histórico")
    void getEventHistory_shouldReturn404WhenEmpty() throws Exception {
        mockMvc.perform(get("/api/polling/ORDER-EMPTY/history"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{orderId}/history: deve retornar todos os eventos do pedido")
    void getEventHistory_shouldReturnAllEvents() throws Exception {
        gateway.process(TrackingEvent.of("ORDER-003", -23.55, -46.63,
                DeliveryStatus.PENDING, Scenario.POLLING));
        gateway.process(TrackingEvent.of("ORDER-003", -23.56, -46.64,
                DeliveryStatus.IN_TRANSIT, Scenario.POLLING));

        mockMvc.perform(get("/api/polling/ORDER-003/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ─── POST /simulate ──────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /simulate: deve retornar 200 com resultado da simulação")
    void simulate_shouldReturn200WithResult() throws Exception {
        mockMvc.perform(post("/api/polling/simulate")
                        .param("orderId", "ORDER-SIM-001")
                        .param("quantity", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORDER-SIM-001"))
                .andExpect(jsonPath("$.eventsGenerated").value(5))
                .andExpect(jsonPath("$.scenario").value("POLLING"));
    }

    @Test
    @DisplayName("POST /simulate: deve persistir os eventos gerados no store")
    void simulate_shouldPersistEvents() throws Exception {
        mockMvc.perform(post("/api/polling/simulate")
                        .param("orderId", "ORDER-SIM-002")
                        .param("quantity", "10"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/polling/ORDER-SIM-002/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)));
    }

    @Test
    @DisplayName("POST /simulate: deve usar orderId padrão quando não informado")
    void simulate_shouldUseDefaultOrderId() throws Exception {
        mockMvc.perform(post("/api/polling/simulate")
                        .param("quantity", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORDER-POLL-001"));
    }

    // ─── GET /metrics ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /metrics: deve retornar 200 com count=0 quando sem dados")
    void getMetrics_shouldReturnZeroCountWhenEmpty() throws Exception {
        mockMvc.perform(get("/api/polling/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0.0));
    }

    @Test
    @DisplayName("GET /metrics: deve retornar estatísticas após simulação")
    void getMetrics_shouldReturnStatisticsAfterSimulation() throws Exception {
        mockMvc.perform(post("/api/polling/simulate")
                        .param("orderId", "ORDER-METRIC")
                        .param("quantity", "20"));

        mockMvc.perform(get("/api/polling/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(20.0))
                .andExpect(jsonPath("$.mean").isNumber())
                .andExpect(jsonPath("$.p95").isNumber());
    }

    @Test
    @DisplayName("GET /metrics: resposta deve ser JSON")
    void getMetrics_shouldReturnJsonContentType() throws Exception {
        mockMvc.perform(get("/api/polling/metrics"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
