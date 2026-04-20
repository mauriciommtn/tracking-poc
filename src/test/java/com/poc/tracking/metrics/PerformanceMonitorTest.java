package com.poc.tracking.metrics;

import com.poc.tracking.model.DeliveryStatus;
import com.poc.tracking.model.Scenario;
import com.poc.tracking.model.TrackingEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Testes unitários para {@link PerformanceMonitor}.
 *
 * <p>Cobre registro de métricas, cálculo de estatísticas descritivas,
 * latências brutas, agrupamento por cenário e reset.
 *
 * @author PoC Team
 * @version 1.0.0
 */
@DisplayName("PerformanceMonitor - Testes Unitários")
class PerformanceMonitorTest {

    private PerformanceMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new PerformanceMonitor(new SimpleMeterRegistry());
    }

    private TrackingEvent makeEvent(Scenario scenario) {
        return TrackingEvent.of("ORDER-001", -23.55, -46.63, DeliveryStatus.IN_TRANSIT, scenario);
    }

    // ─── record ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("record: deve adicionar registro de métrica em memória")
    void record_shouldAddMetricRecord() {
        monitor.record(makeEvent(Scenario.POLLING), 50L);

        assertThat(monitor.getRawLatencies(Scenario.POLLING)).hasSize(1);
    }

    @Test
    @DisplayName("record: latência registrada deve ser igual à fornecida")
    void record_shouldStoreCorrectLatency() {
        monitor.record(makeEvent(Scenario.POLLING), 123L);

        long stored = monitor.getRawLatencies(Scenario.POLLING).get(0);
        assertThat(stored).isEqualTo(123L);
    }

    @Test
    @DisplayName("record: cenários distintos devem ser isolados")
    void record_shouldIsolateScenarios() {
        monitor.record(makeEvent(Scenario.POLLING), 10L);
        monitor.record(makeEvent(Scenario.EVENT_DRIVEN), 20L);

        assertThat(monitor.getRawLatencies(Scenario.POLLING)).hasSize(1);
        assertThat(monitor.getRawLatencies(Scenario.EVENT_DRIVEN)).hasSize(1);
    }

    // ─── getStatistics ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getStatistics: deve retornar count=0 quando não há registros")
    void getStatistics_shouldReturnZeroCountWhenEmpty() {
        Map<String, Double> stats = monitor.getStatistics(Scenario.POLLING);

        assertThat(stats).containsEntry("count", 0.0);
    }

    @Test
    @DisplayName("getStatistics: count deve refletir número de registros")
    void getStatistics_countShouldMatchRecordCount() {
        monitor.record(makeEvent(Scenario.POLLING), 10L);
        monitor.record(makeEvent(Scenario.POLLING), 20L);
        monitor.record(makeEvent(Scenario.POLLING), 30L);

        Map<String, Double> stats = monitor.getStatistics(Scenario.POLLING);
        assertThat(stats.get("count")).isEqualTo(3.0);
    }

    @Test
    @DisplayName("getStatistics: média deve ser calculada corretamente")
    void getStatistics_meanShouldBeCorrect() {
        monitor.record(makeEvent(Scenario.POLLING), 10L);
        monitor.record(makeEvent(Scenario.POLLING), 20L);
        monitor.record(makeEvent(Scenario.POLLING), 30L);

        Map<String, Double> stats = monitor.getStatistics(Scenario.POLLING);
        assertThat(stats.get("mean")).isEqualTo(20.0);
    }

    @Test
    @DisplayName("getStatistics: min e max devem ser corretos")
    void getStatistics_minMaxShouldBeCorrect() {
        monitor.record(makeEvent(Scenario.POLLING), 5L);
        monitor.record(makeEvent(Scenario.POLLING), 50L);
        monitor.record(makeEvent(Scenario.POLLING), 25L);

        Map<String, Double> stats = monitor.getStatistics(Scenario.POLLING);
        assertThat(stats.get("min")).isEqualTo(5.0);
        assertThat(stats.get("max")).isEqualTo(50.0);
    }

    @Test
    @DisplayName("getStatistics: deve conter todas as chaves esperadas")
    void getStatistics_shouldContainAllExpectedKeys() {
        monitor.record(makeEvent(Scenario.EVENT_DRIVEN), 10L);

        Map<String, Double> stats = monitor.getStatistics(Scenario.EVENT_DRIVEN);
        assertThat(stats).containsKeys("count", "mean", "median", "p95", "p99", "stdDev", "min", "max");
    }

    @Test
    @DisplayName("getStatistics: desvio padrão deve ser zero para valores iguais")
    void getStatistics_stdDevShouldBeZeroForEqualValues() {
        monitor.record(makeEvent(Scenario.POLLING), 10L);
        monitor.record(makeEvent(Scenario.POLLING), 10L);
        monitor.record(makeEvent(Scenario.POLLING), 10L);

        Map<String, Double> stats = monitor.getStatistics(Scenario.POLLING);
        assertThat(stats.get("stdDev")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("getStatistics: p99 deve ser maior ou igual a p95")
    void getStatistics_p99ShouldBeGreaterOrEqualToP95() {
        for (int i = 1; i <= 100; i++) {
            monitor.record(makeEvent(Scenario.POLLING), (long) i);
        }

        Map<String, Double> stats = monitor.getStatistics(Scenario.POLLING);
        assertThat(stats.get("p99")).isGreaterThanOrEqualTo(stats.get("p95"));
    }

    @Test
    @DisplayName("getStatistics: mediana deve estar entre min e max")
    void getStatistics_medianShouldBeBetweenMinAndMax() {
        for (int i = 1; i <= 10; i++) {
            monitor.record(makeEvent(Scenario.POLLING), (long) i * 10);
        }

        Map<String, Double> stats = monitor.getStatistics(Scenario.POLLING);
        assertThat(stats.get("median"))
                .isGreaterThanOrEqualTo(stats.get("min"))
                .isLessThanOrEqualTo(stats.get("max"));
    }

    // ─── getRawLatencies ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getRawLatencies: deve retornar lista vazia para cenário sem dados")
    void getRawLatencies_shouldReturnEmptyForUnknownScenario() {
        assertThat(monitor.getRawLatencies(Scenario.EVENT_DRIVEN)).isEmpty();
    }

    @Test
    @DisplayName("getRawLatencies: deve retornar latências em ordem crescente")
    void getRawLatencies_shouldReturnSortedLatencies() {
        monitor.record(makeEvent(Scenario.POLLING), 30L);
        monitor.record(makeEvent(Scenario.POLLING), 10L);
        monitor.record(makeEvent(Scenario.POLLING), 20L);

        List<Long> latencies = monitor.getRawLatencies(Scenario.POLLING);
        assertThat(latencies).containsExactly(10L, 20L, 30L);
    }

    // ─── getAllRecords ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllRecords: deve agrupar registros por cenário")
    void getAllRecords_shouldGroupByScenario() {
        monitor.record(makeEvent(Scenario.POLLING), 10L);
        monitor.record(makeEvent(Scenario.EVENT_DRIVEN), 5L);

        Map<Scenario, ?> allRecords = monitor.getAllRecords();
        assertThat(allRecords).containsKeys(Scenario.POLLING, Scenario.EVENT_DRIVEN);
    }

    // ─── reset ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reset: deve limpar todos os registros de métricas")
    void reset_shouldClearAllRecords() {
        monitor.record(makeEvent(Scenario.POLLING), 10L);
        monitor.record(makeEvent(Scenario.EVENT_DRIVEN), 20L);

        monitor.reset();

        assertThat(monitor.getRawLatencies(Scenario.POLLING)).isEmpty();
        assertThat(monitor.getRawLatencies(Scenario.EVENT_DRIVEN)).isEmpty();
    }

    @Test
    @DisplayName("reset: getStatistics deve retornar count=0 após reset")
    void reset_statisticsShouldShowZeroAfterReset() {
        monitor.record(makeEvent(Scenario.POLLING), 10L);
        monitor.reset();

        assertThat(monitor.getStatistics(Scenario.POLLING)).containsEntry("count", 0.0);
    }
}
