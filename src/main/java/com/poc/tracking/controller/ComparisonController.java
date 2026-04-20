package com.poc.tracking.controller;

import com.poc.tracking.emulator.EventEmulator;
import com.poc.tracking.gateway.ApiGateway;
import com.poc.tracking.gateway.EventStore;
import com.poc.tracking.metrics.PerformanceMonitor;
import com.poc.tracking.model.Scenario;
import com.poc.tracking.model.TrackingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller de comparação entre os dois cenários da PoC.
 *
 * <p>Fornece endpoints para executar e comparar os resultados dos dois cenários
 * em uma única operação, facilitando a análise estatística descritiva.
 *
 * <p>Endpoints disponíveis:
 * <ul>
 *   <li>{@code POST /api/comparison/run} - Executa ambos os cenários e retorna comparativo.</li>
 *   <li>{@code GET /api/comparison/report} - Gera relatório comparativo de métricas.</li>
 *   <li>{@code DELETE /api/comparison/reset} - Reseta estado para novo experimento.</li>
 * </ul>
 *
 * @author PoC Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/comparison")
public class ComparisonController {

    private static final Logger log = LoggerFactory.getLogger(ComparisonController.class);

    private final EventEmulator emulator;
    private final ApiGateway gateway;
    private final EventStore eventStore;
    private final PerformanceMonitor monitor;

    @Value("${tracking.emulator.total-events:100}")
    private int totalEvents;

    /**
     * Constrói o controller com as dependências necessárias.
     *
     * @param emulator   emulador de eventos
     * @param gateway    gateway de processamento
     * @param eventStore repositório de eventos
     * @param monitor    monitor de performance
     */
    public ComparisonController(EventEmulator emulator, ApiGateway gateway,
                                 EventStore eventStore, PerformanceMonitor monitor) {
        this.emulator = emulator;
        this.gateway = gateway;
        this.eventStore = eventStore;
        this.monitor = monitor;
    }

    /**
     * Executa ambos os cenários sequencialmente e retorna os resultados comparativos.
     *
     * <p>O método:
     * <ol>
     *   <li>Reseta o estado para garantir isolamento.</li>
     *   <li>Executa o Cenário 1 (Polling) com {@code quantity} eventos.</li>
     *   <li>Executa o Cenário 2 (Event-Driven) com a mesma quantidade.</li>
     *   <li>Retorna estatísticas comparativas de ambos os cenários.</li>
     * </ol>
     *
     * @param quantity número de eventos por cenário (padrão: {@code tracking.emulator.total-events})
     * @return mapa comparativo com estatísticas de ambos os cenários
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runComparison(
            @RequestParam(required = false) Integer quantity) {

        int count = (quantity != null && quantity > 0) ? quantity : totalEvents;
        log.info("Iniciando comparação completa com {} eventos por cenário.", count);

        // Reset estado
        emulator.reset();
        eventStore.clearAll();
        monitor.reset();

        // Cenário 1: Polling
        List<TrackingEvent> pollingEvents = emulator.generateBatch("ORDER-COMP-POLL", count, Scenario.POLLING);
        pollingEvents.forEach(gateway::process);

        // Cenário 2: Event-Driven
        List<TrackingEvent> eventDrivenEvents = emulator.generateBatch("ORDER-COMP-EVT", count, Scenario.EVENT_DRIVEN);
        eventDrivenEvents.forEach(gateway::process);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventsPerScenario", count);
        result.put("polling", monitor.getStatistics(Scenario.POLLING));
        result.put("eventDriven", monitor.getStatistics(Scenario.EVENT_DRIVEN));
        result.put("latenciesPoll", monitor.getRawLatencies(Scenario.POLLING));
        result.put("latenciesEvent", monitor.getRawLatencies(Scenario.EVENT_DRIVEN));

        return ResponseEntity.ok(result);
    }

    /**
     * Gera um relatório comparativo de métricas entre os dois cenários.
     *
     * <p>O relatório calcula a diferença percentual de latência média entre
     * os cenários, indicando qual abordagem é mais eficiente.
     *
     * @return mapa com relatório: estatísticas de cada cenário e análise comparativa
     */
    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> getReport() {
        Map<String, Double> pollStats = monitor.getStatistics(Scenario.POLLING);
        Map<String, Double> eventStats = monitor.getStatistics(Scenario.EVENT_DRIVEN);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("polling", pollStats);
        report.put("eventDriven", eventStats);

        // Análise comparativa
        if (pollStats.containsKey("mean") && eventStats.containsKey("mean")) {
            double pollMean = pollStats.get("mean");
            double eventMean = eventStats.get("mean");
            double improvement = pollMean > 0 ? ((pollMean - eventMean) / pollMean * 100) : 0;
            report.put("latencyImprovementPercent", Math.round(improvement * 100.0) / 100.0);
            report.put("fasterScenario", eventMean < pollMean ? Scenario.EVENT_DRIVEN.name() : Scenario.POLLING.name());
        }

        return ResponseEntity.ok(report);
    }

    /**
     * Reseta completamente o estado da PoC para um novo ciclo de experimento.
     *
     * <p>Limpa eventos armazenados, métricas e contadores do emulador.
     *
     * @return mensagem de confirmação
     */
    @DeleteMapping("/reset")
    public ResponseEntity<Map<String, String>> reset() {
        emulator.reset();
        eventStore.clearAll();
        monitor.reset();
        log.info("Estado da PoC resetado.");
        return ResponseEntity.ok(Map.of("status", "reset concluído"));
    }
}
