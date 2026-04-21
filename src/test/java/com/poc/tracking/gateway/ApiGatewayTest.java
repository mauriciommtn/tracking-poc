package com.poc.tracking.gateway;

import com.poc.tracking.metrics.PerformanceMonitor;
import com.poc.tracking.model.DeliveryStatus;
import com.poc.tracking.model.Scenario;
import com.poc.tracking.model.TrackingEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Testes unitários para {@link ApiGateway}.
 *
 * <p>Cobre processamento de eventos, persistência no store, registro de métricas
 * e publicação no stream reativo para SSE.
 *
 * @author Mauricio Nogueira
 * @version 1.0.0
 */
@DisplayName("ApiGateway - Testes Unitários")
class ApiGatewayTest {

    private ApiGateway gateway;
    private EventStore eventStore;
    private PerformanceMonitor monitor;

    @BeforeEach
    void setUp() {
        eventStore = new EventStore();
        monitor = new PerformanceMonitor(new SimpleMeterRegistry());
        gateway = new ApiGateway(eventStore, monitor, 256);
    }

    private TrackingEvent makeEvent(String orderId, Scenario scenario) {
        return TrackingEvent.of(orderId, -23.55, -46.63, DeliveryStatus.IN_TRANSIT, scenario);
    }

    // ─── process ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("process: deve salvar evento no EventStore")
    void process_shouldSaveEventInStore() {
        TrackingEvent event = makeEvent("ORDER-001", Scenario.POLLING);
        gateway.process(event);

        Optional<TrackingEvent> stored = eventStore.findLatestByOrderId("ORDER-001");
        assertThat(stored).isPresent().contains(event);
    }

    @Test
    @DisplayName("process: deve registrar métrica no PerformanceMonitor")
    void process_shouldRecordMetric() {
        TrackingEvent event = makeEvent("ORDER-001", Scenario.POLLING);
        gateway.process(event);

        assertThat(monitor.getRawLatencies(Scenario.POLLING)).hasSize(1);
    }

    @Test
    @DisplayName("process: deve lançar NullPointerException para evento nulo")
    void process_shouldThrowForNullEvent() {
        assertThatNullPointerException().isThrownBy(() -> gateway.process(null));
    }

    @Test
    @DisplayName("process: múltiplos eventos devem ser todos persistidos")
    void process_shouldPersistMultipleEvents() {
        gateway.process(makeEvent("ORDER-002", Scenario.POLLING));
        gateway.process(makeEvent("ORDER-002", Scenario.POLLING));
        gateway.process(makeEvent("ORDER-002", Scenario.POLLING));

        assertThat(eventStore.countByOrderId("ORDER-002")).isEqualTo(3);
    }

    @Test
    @DisplayName("process: deve registrar métrica com latência não negativa")
    void process_shouldRecordNonNegativeLatency() {
        gateway.process(makeEvent("ORDER-001", Scenario.EVENT_DRIVEN));

        long latency = monitor.getRawLatencies(Scenario.EVENT_DRIVEN).get(0);
        assertThat(latency).isGreaterThanOrEqualTo(0);
    }

    // ─── streamEvents ───────────────────────────────────────────────────────

    @Test
    @DisplayName("streamEvents: deve emitir evento processado no Flux")
    void streamEvents_shouldEmitProcessedEvent() {
        TrackingEvent event = makeEvent("ORDER-001", Scenario.EVENT_DRIVEN);

        StepVerifier.create(gateway.streamEvents().take(1))
                .then(() -> gateway.process(event))
                .expectNextMatches(e -> e.eventId().equals(event.eventId()))
                .verifyComplete();
    }

    @Test
    @DisplayName("streamEvents: deve retornar Flux não nulo")
    void streamEvents_shouldReturnNonNullFlux() {
        assertThat(gateway.streamEvents()).isNotNull();
    }

    @Test
    @DisplayName("streamEvents: múltiplos eventos devem ser emitidos em ordem")
    void streamEvents_shouldEmitMultipleEventsInOrder() {
        TrackingEvent e1 = makeEvent("ORDER-001", Scenario.EVENT_DRIVEN);
        TrackingEvent e2 = makeEvent("ORDER-001", Scenario.EVENT_DRIVEN);

        StepVerifier.create(gateway.streamEvents().take(2))
                .then(() -> {
                    gateway.process(e1);
                    gateway.process(e2);
                })
                .expectNextMatches(e -> e.eventId().equals(e1.eventId()))
                .expectNextMatches(e -> e.eventId().equals(e2.eventId()))
                .verifyComplete();
    }

    // ─── shutdown ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("shutdown: deve completar o stream de eventos")
    void shutdown_shouldCompleteStream() {
        StepVerifier.create(gateway.streamEvents())
                .then(() -> gateway.shutdown())
                .verifyComplete();
    }
}
