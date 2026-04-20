package com.poc.tracking.controller;

import com.poc.tracking.emulator.EventEmulator;
import com.poc.tracking.gateway.ApiGateway;
import com.poc.tracking.metrics.PerformanceMonitor;
import com.poc.tracking.model.Scenario;
import com.poc.tracking.model.TrackingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Controller REST para o Cenário 2 - Grupo Experimental (Event-Driven / SSE).
 *
 * <p>Implementa a arquitetura baseada em eventos onde o servidor envia notificações
 * proativamente ao cliente via Server-Sent Events (SSE) assim que o status é alterado.
 *
 * <p>Endpoints disponíveis:
 * <ul>
 *   <li>{@code GET /api/events/stream} - Stream SSE de eventos em tempo real.</li>
 *   <li>{@code POST /api/events/simulate} - Inicia simulação de carga para o Cenário 2.</li>
 *   <li>{@code GET /api/events/metrics} - Retorna estatísticas do Cenário 2.</li>
 * </ul>
 *
 * @author PoC Team
 * @version 1.0.0
 * @see ApiGateway
 * @see PerformanceMonitor
 */
@RestController
@RequestMapping("/api/events")
public class EventDrivenController {

    private static final Logger log = LoggerFactory.getLogger(EventDrivenController.class);

    private final EventEmulator emulator;
    private final ApiGateway gateway;
    private final PerformanceMonitor monitor;

    @Value("${tracking.emulator.total-events:100}")
    private int totalEvents;

    /**
     * Constrói o controller com as dependências necessárias.
     *
     * @param emulator emulador de eventos
     * @param gateway  gateway de processamento assíncrono
     * @param monitor  monitor de performance
     */
    public EventDrivenController(EventEmulator emulator, ApiGateway gateway,
                                  PerformanceMonitor monitor) {
        this.emulator = emulator;
        this.gateway = gateway;
        this.monitor = monitor;
    }

    /**
     * Stream SSE de eventos de rastreamento em tempo real.
     *
     * <p>Este endpoint implementa o Cenário 2 (Event-Driven). O cliente se conecta
     * uma única vez e recebe eventos proativamente via Server-Sent Events.
     * Não há necessidade de polling periódico.
     *
     * <p>O stream é infinito e permanece aberto até que o cliente desconecte.
     * Cada evento emitido inclui todos os dados de rastreamento: coordenadas GPS,
     * status de entrega e metadados temporais.
     *
     * @return {@link Flux} de {@link TrackingEvent} via {@code text/event-stream}
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<TrackingEvent> streamEvents() {
        log.info("Cliente conectado ao stream SSE de eventos.");
        return gateway.streamEvents()
                .filter(e -> e.scenario() == Scenario.EVENT_DRIVEN)
                .doOnNext(e -> log.debug("Evento SSE enviado: eventId={}", e.eventId()))
                .doOnCancel(() -> log.info("Cliente desconectado do stream SSE."));
    }

    /**
     * Inicia uma simulação de carga para o Cenário 2 (Event-Driven).
     *
     * <p>Gera eventos que são emitidos imediatamente no stream SSE,
     * sem espera por consulta do cliente. Os clientes conectados recebem
     * as atualizações em tempo real.
     *
     * @param orderId  identificador do pedido (padrão: "ORDER-EVT-001")
     * @param quantity número de eventos a simular
     * @return mapa com resultado: {@code orderId, eventsGenerated, scenario}
     */
    @PostMapping("/simulate")
    public ResponseEntity<Map<String, Object>> simulate(
            @RequestParam(defaultValue = "ORDER-EVT-001") String orderId,
            @RequestParam(required = false) Integer quantity) {

        int count = (quantity != null && quantity > 0) ? quantity : totalEvents;
        log.info("Iniciando simulação Cenário 2 (Event-Driven): orderId={}, events={}", orderId, count);

        List<TrackingEvent> events = emulator.generateBatch(orderId, count, Scenario.EVENT_DRIVEN);
        events.forEach(gateway::process);

        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "eventsGenerated", count,
                "scenario", Scenario.EVENT_DRIVEN.name()
        ));
    }

    /**
     * Retorna estatísticas descritivas de performance do Cenário 2 (Event-Driven).
     *
     * @return mapa com estatísticas de latência: mean, median, p95, p99, stdDev, min, max
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Double>> getMetrics() {
        Map<String, Double> stats = monitor.getStatistics(Scenario.EVENT_DRIVEN);
        return ResponseEntity.ok(stats);
    }
}
