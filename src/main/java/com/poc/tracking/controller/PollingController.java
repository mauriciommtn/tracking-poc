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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller REST para o Cenário 1 - Grupo de Controle (Polling).
 *
 * <p>Implementa o modelo de consulta tradicional onde o cliente faz requisições
 * HTTP GET em intervalos fixos para obter o estado atual do rastreamento.
 *
 * <p>Endpoints disponíveis:
 * <ul>
 *   <li>{@code GET /api/polling/{orderId}} - Retorna o último evento de um pedido.</li>
 *   <li>{@code GET /api/polling/{orderId}/history} - Retorna histórico completo.</li>
 *   <li>{@code POST /api/polling/simulate} - Inicia simulação de carga para o Cenário 1.</li>
 *   <li>{@code GET /api/polling/metrics} - Retorna estatísticas do Cenário 1.</li>
 * </ul>
 *
 * @author Mauricio Nogueira
 * @version 1.0.0
 * @see ApiGateway
 * @see EventStore
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/polling")
public class PollingController {

    private static final Logger log = LoggerFactory.getLogger(PollingController.class);

    private final EventEmulator emulator;
    private final ApiGateway gateway;
    private final EventStore eventStore;
    private final PerformanceMonitor monitor;

    @Value("${tracking.emulator.total-events:100}")
    private int totalEvents;

    /**
     * Constrói o controller com as dependências necessárias.
     *
     * @param emulator    emulador de eventos
     * @param gateway     gateway de processamento assíncrono
     * @param eventStore  repositório de eventos
     * @param monitor     monitor de performance
     */
    public PollingController(EventEmulator emulator, ApiGateway gateway,
                              EventStore eventStore, PerformanceMonitor monitor) {
        this.emulator = emulator;
        this.gateway = gateway;
        this.eventStore = eventStore;
        this.monitor = monitor;
    }

    /**
     * Endpoint de polling: retorna o estado atual de rastreamento de um pedido.
     *
     * <p>Este endpoint simula como o cliente consultaria a API periodicamente no Cenário 1.
     * Cada chamada representa uma "polling request" e contribui para o cálculo de latência.
     *
     * @param orderId identificador do pedido a consultar
     * @return {@link ResponseEntity} com o último {@link TrackingEvent} ou 404 se não encontrado
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<TrackingEvent> getLatestEvent(@PathVariable String orderId) {
        log.debug("Polling request para orderId={}", orderId);
        Optional<TrackingEvent> event = eventStore.findLatestByOrderId(orderId);
        return event.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retorna o histórico completo de eventos de rastreamento de um pedido.
     *
     * @param orderId identificador do pedido
     * @return lista de todos os eventos registrados
     */
    @GetMapping("/{orderId}/history")
    public ResponseEntity<List<TrackingEvent>> getEventHistory(@PathVariable String orderId) {
        List<TrackingEvent> history = eventStore.findByOrderId(orderId);
        if (history.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(history);
    }

    /**
     * Inicia uma simulação de carga para o Cenário 1 (Polling).
     *
     * <p>Gera {@code totalEvents} eventos para o pedido especificado e os processa
     * pelo gateway. Os resultados ficam disponíveis nos endpoints de consulta e métricas.
     *
     * @param orderId  identificador do pedido para simulação (padrão: "ORDER-POLL-001")
     * @param quantity número de eventos a simular (padrão: valor de {@code tracking.emulator.total-events})
     * @return mapa com resultado da simulação: {@code orderId, eventsGenerated, scenario}
     */
    @PostMapping("/simulate")
    public ResponseEntity<Map<String, Object>> simulate(
            @RequestParam(defaultValue = "ORDER-POLL-001") String orderId,
            @RequestParam(required = false) Integer quantity) {

        int count = (quantity != null && quantity > 0) ? quantity : totalEvents;
        log.info("Iniciando simulação Cenário 1 (Polling): orderId={}, events={}", orderId, count);

        List<TrackingEvent> events = emulator.generateBatch(orderId, count, Scenario.POLLING);
        events.forEach(gateway::process);

        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "eventsGenerated", count,
                "scenario", Scenario.POLLING.name()
        ));
    }

    /**
     * Retorna estatísticas descritivas de performance do Cenário 1 (Polling).
     *
     * <p>As estatísticas incluem: contagem, média, mediana, P95, P99, desvio padrão,
     * mínimo e máximo de latência em milissegundos.
     *
     * @return mapa com estatísticas de latência do cenário de polling
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Double>> getMetrics() {
        Map<String, Double> stats = monitor.getStatistics(Scenario.POLLING);
        return ResponseEntity.ok(stats);
    }
}
