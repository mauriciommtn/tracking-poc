package com.poc.tracking.metrics;

import com.poc.tracking.model.MetricRecord;
import com.poc.tracking.model.Scenario;
import com.poc.tracking.model.TrackingEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Monitor de performance para coleta e análise de métricas da PoC.
 *
 * <p>Integra-se com o Micrometer para exportar métricas ao Prometheus e
 * também mantém registros em memória para análise estatística descritiva.
 *
 * <p>Métricas coletadas:
 * <ul>
 *   <li>{@code tracking.event.latency} - Timer com latência por cenário.</li>
 *   <li>{@code tracking.event.count} - Contador de eventos processados.</li>
 * </ul>
 *
 * <p>Estatísticas calculadas:
 * <ul>
 *   <li>Média (mean)</li>
 *   <li>Mediana (P50)</li>
 *   <li>Percentil 95 (P95)</li>
 *   <li>Percentil 99 (P99)</li>
 *   <li>Desvio padrão</li>
 *   <li>Mínimo e máximo</li>
 * </ul>
 *
 * @author Mauricio Nogueira
 * @version 1.0.0
 */
@Component
public class PerformanceMonitor {

    private static final Logger log = LoggerFactory.getLogger(PerformanceMonitor.class);

    private final MeterRegistry meterRegistry;
    private final List<MetricRecord> records = new CopyOnWriteArrayList<>();

    /**
     * Constrói o {@code PerformanceMonitor} com o registro de métricas do Micrometer.
     *
     * @param meterRegistry registro de métricas (injetado pelo Spring Boot Actuator)
     */
    public PerformanceMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Registra a métrica de latência de um evento processado.
     *
     * <p>O registro é feito em dois níveis:
     * <ol>
     *   <li>No Micrometer (para exportação ao Prometheus via {@code /actuator/prometheus}).</li>
     *   <li>Em memória (para cálculo de estatísticas descritivas via {@link #getStatistics}).</li>
     * </ol>
     *
     * @param event     evento de rastreamento processado
     * @param latencyMs latência medida em milissegundos
     */
    public void record(TrackingEvent event, long latencyMs) {
        // Micrometer timer
        Timer.builder("tracking.event.latency")
                .tag("scenario", event.scenario().name())
                .tag("status", event.status().name())
                .description("Latência de processamento de eventos de rastreamento")
                .register(meterRegistry)
                .record(Duration.ofMillis(latencyMs));

        // Contador de eventos por cenário
        meterRegistry.counter("tracking.event.count",
                "scenario", event.scenario().name(),
                "status", event.status().name())
                .increment();

        // Registro em memória para análise estatística
        MetricRecord metricRecord = MetricRecord.of(event.eventId(), event.scenario(), latencyMs);
        records.add(metricRecord);

        log.debug("Métrica registrada: eventId={}, scenario={}, latency={}ms",
                event.eventId(), event.scenario(), latencyMs);
    }

    /**
     * Calcula estatísticas descritivas para um cenário específico.
     *
     * <p>As estatísticas são calculadas sobre todas as latências registradas
     * para o cenário fornecido desde o início da execução (ou último reset).
     *
     * @param scenario cenário para o qual calcular as estatísticas
     * @return mapa com chaves: {@code count, mean, median, p95, p99, stdDev, min, max}
     *         ou mapa com {@code count=0} se não houver registros
     */
    public Map<String, Double> getStatistics(Scenario scenario) {
        List<Long> latencies = records.stream()
                .filter(r -> r.scenario() == scenario)
                .map(MetricRecord::latencyMs)
                .sorted()
                .toList();

        if (latencies.isEmpty()) {
            return Map.of("count", 0.0);
        }

        int n = latencies.size();
        double mean = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        double variance = latencies.stream()
                .mapToDouble(l -> Math.pow(l - mean, 2))
                .average()
                .orElse(0);

        Map<String, Double> stats = new LinkedHashMap<>();
        stats.put("count", (double) n);
        stats.put("mean", mean);
        stats.put("median", percentile(latencies, 50));
        stats.put("p95", percentile(latencies, 95));
        stats.put("p99", percentile(latencies, 99));
        stats.put("stdDev", Math.sqrt(variance));
        stats.put("min", (double) latencies.get(0));
        stats.put("max", (double) latencies.get(n - 1));

        return Collections.unmodifiableMap(stats);
    }

    /**
     * Retorna todos os registros de métricas coletados, agrupados por cenário.
     *
     * @return mapa imutável de {@link Scenario} → lista de {@link MetricRecord}
     */
    public Map<Scenario, List<MetricRecord>> getAllRecords() {
        return records.stream().collect(Collectors.groupingBy(MetricRecord::scenario));
    }

    /**
     * Retorna a lista de latências brutas de um cenário, em ordem crescente.
     *
     * <p>Útil para geração de histogramas e gráficos de dispersão.
     *
     * @param scenario cenário desejado
     * @return lista de latências ordenadas em milissegundos
     */
    public List<Long> getRawLatencies(Scenario scenario) {
        return records.stream()
                .filter(r -> r.scenario() == scenario)
                .map(MetricRecord::latencyMs)
                .sorted()
                .toList();
    }

    /**
     * Limpa todos os registros de métricas em memória.
     *
     * <p>Deve ser chamado entre execuções dos cenários para garantir isolamento.
     */
    public void reset() {
        records.clear();
        log.info("PerformanceMonitor resetado.");
    }

    /**
     * Calcula o valor de um percentil específico em uma lista ordenada de latências.
     *
     * @param sorted lista de latências em ordem crescente
     * @param percentile percentil desejado (0-100)
     * @return valor do percentil calculado
     */
    private double percentile(List<Long> sorted, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
