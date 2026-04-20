package com.poc.tracking.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Testes unitários para o record {@link TrackingEvent}.
 *
 * <p>Cobre criação via factory method, unicidade de IDs, cálculo de latência
 * e imutabilidade garantida pelo record Java.
 *
 * @author PoC Team
 * @version 1.0.0
 */
@DisplayName("TrackingEvent - Testes Unitários")
class TrackingEventTest {

    @Test
    @DisplayName("of: deve criar evento com todos os campos preenchidos")
    void of_shouldCreateEventWithAllFields() {
        TrackingEvent event = TrackingEvent.of("ORDER-001", -23.55, -46.63,
                DeliveryStatus.IN_TRANSIT, Scenario.POLLING);

        assertThat(event.eventId()).isNotBlank();
        assertThat(event.orderId()).isEqualTo("ORDER-001");
        assertThat(event.latitude()).isEqualTo(-23.55);
        assertThat(event.longitude()).isEqualTo(-46.63);
        assertThat(event.status()).isEqualTo(DeliveryStatus.IN_TRANSIT);
        assertThat(event.scenario()).isEqualTo(Scenario.POLLING);
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("of: dois eventos criados sequencialmente devem ter IDs diferentes")
    void of_shouldGenerateUniqueIds() {
        TrackingEvent e1 = TrackingEvent.of("ORDER-001", -23.55, -46.63,
                DeliveryStatus.PENDING, Scenario.POLLING);
        TrackingEvent e2 = TrackingEvent.of("ORDER-001", -23.55, -46.63,
                DeliveryStatus.PENDING, Scenario.POLLING);

        assertThat(e1.eventId()).isNotEqualTo(e2.eventId());
    }

    @Test
    @DisplayName("of: timestamp deve ser próximo ao momento de criação")
    void of_timestampShouldBeNearNow() {
        Instant before = Instant.now().minusMillis(100);
        TrackingEvent event = TrackingEvent.of("ORDER-001", 0.0, 0.0,
                DeliveryStatus.DELIVERED, Scenario.EVENT_DRIVEN);
        Instant after = Instant.now().plusMillis(100);

        assertThat(event.timestamp()).isAfter(before).isBefore(after);
    }

    @Test
    @DisplayName("latencyMs: deve retornar valor não negativo")
    void latencyMs_shouldReturnNonNegativeValue() {
        TrackingEvent event = TrackingEvent.of("ORDER-001", -23.55, -46.63,
                DeliveryStatus.IN_TRANSIT, Scenario.POLLING);

        assertThat(event.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("latencyMs: deve aumentar com o tempo decorrido")
    void latencyMs_shouldIncreaseOverTime() throws InterruptedException {
        TrackingEvent event = TrackingEvent.of("ORDER-001", -23.55, -46.63,
                DeliveryStatus.IN_TRANSIT, Scenario.POLLING);

        long first = event.latencyMs();
        Thread.sleep(10);
        long second = event.latencyMs();

        assertThat(second).isGreaterThanOrEqualTo(first);
    }

    @Test
    @DisplayName("record: deve preservar igualdade por valor (semantics de record)")
    void record_shouldSupportValueEquality() {
        String eventId = "fixed-id";
        Instant ts = Instant.now();

        TrackingEvent e1 = new TrackingEvent(eventId, "ORDER-001", -23.55, -46.63,
                DeliveryStatus.IN_TRANSIT, ts, Scenario.POLLING);
        TrackingEvent e2 = new TrackingEvent(eventId, "ORDER-001", -23.55, -46.63,
                DeliveryStatus.IN_TRANSIT, ts, Scenario.POLLING);

        assertThat(e1).isEqualTo(e2);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
    }
}
