package com.poc.tracking.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Testes unitários para o record {@link MetricRecord}.
 *
 * @author Mauricio Nogueira
 * @version 1.0.0
 */
@DisplayName("MetricRecord - Testes Unitários")
class MetricRecordTest {

    @Test
    @DisplayName("of: deve criar registro com todos os campos preenchidos")
    void of_shouldCreateRecordWithAllFields() {
        MetricRecord record = MetricRecord.of("evt-123", Scenario.POLLING, 42L);

        assertThat(record.eventId()).isEqualTo("evt-123");
        assertThat(record.scenario()).isEqualTo(Scenario.POLLING);
        assertThat(record.latencyMs()).isEqualTo(42L);
        assertThat(record.collectedAt()).isNotNull();
    }

    @Test
    @DisplayName("of: collectedAt deve ser próximo ao momento de criação")
    void of_collectedAtShouldBeNearNow() {
        Instant before = Instant.now().minusMillis(100);
        MetricRecord record = MetricRecord.of("evt-001", Scenario.EVENT_DRIVEN, 10L);
        Instant after = Instant.now().plusMillis(100);

        assertThat(record.collectedAt()).isAfter(before).isBefore(after);
    }

    @Test
    @DisplayName("record: deve suportar igualdade por valor")
    void record_shouldSupportValueEquality() {
        Instant ts = Instant.now();
        MetricRecord r1 = new MetricRecord("evt-1", Scenario.POLLING, 50L, ts);
        MetricRecord r2 = new MetricRecord("evt-1", Scenario.POLLING, 50L, ts);

        assertThat(r1).isEqualTo(r2);
    }

    @Test
    @DisplayName("of: latência zero deve ser aceita")
    void of_shouldAcceptZeroLatency() {
        MetricRecord record = MetricRecord.of("evt-zero", Scenario.POLLING, 0L);
        assertThat(record.latencyMs()).isZero();
    }
}
