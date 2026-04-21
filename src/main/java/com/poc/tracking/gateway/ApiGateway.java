package com.poc.tracking.gateway;

import com.poc.tracking.metrics.PerformanceMonitor;
import com.poc.tracking.model.TrackingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Objects;

/**
 * API Gateway / Middleware para processamento assíncrono de eventos de rastreamento.
 *
 * <p>Este componente é o coração da PoC. Ele:
 * <ol>
 *   <li>Recebe eventos do {@link com.poc.tracking.emulator.EventEmulator}.</li>
 *   <li>Persiste no {@link EventStore}.</li>
 *   <li>Registra métricas no {@link PerformanceMonitor}.</li>
 *   <li>Publica no stream reativo para o Cenário 2 (Event-Driven via SSE).</li>
 * </ol>
 *
 * <p>O {@link Sinks.Many} é configurado como multicast com buffer para suportar
 * múltiplos subscribers (clientes SSE) simultaneamente.
 *
 * @author Mauricio Nogueira
 * @version 1.0.0
 * @see EventStore
 * @see PerformanceMonitor
 */
@Service
public class ApiGateway {

    private static final Logger log = LoggerFactory.getLogger(ApiGateway.class);

    private final EventStore eventStore;
    private final PerformanceMonitor performanceMonitor;
    private final Sinks.Many<TrackingEvent> eventSink;

    /**
     * Constrói o {@code ApiGateway} com as dependências necessárias.
     *
     * @param eventStore         repositório de eventos em memória
     * @param performanceMonitor monitor de métricas de performance
     * @param bufferSize         tamanho do buffer do sink reativo (via {@code tracking.event.buffer-size})
     */
    public ApiGateway(EventStore eventStore,
                      PerformanceMonitor performanceMonitor,
                      @Value("${tracking.event.buffer-size:256}") int bufferSize) {
        this.eventStore = eventStore;
        this.performanceMonitor = performanceMonitor;
        this.eventSink = Sinks.many().multicast().onBackpressureBuffer(bufferSize);
    }

    /**
     * Processa um evento de rastreamento de forma assíncrona.
     *
     * <p>O processamento inclui:
     * <ol>
     *   <li>Persistência no {@link EventStore}.</li>
     *   <li>Registro de latência no {@link PerformanceMonitor}.</li>
     *   <li>Emissão no stream reativo (para clientes SSE do Cenário 2).</li>
     * </ol>
     *
     * <p>A latência é calculada como o tempo entre a criação do evento ({@code event.timestamp()})
     * e o momento do processamento no gateway.
     *
     * @param event evento de rastreamento a processar (não pode ser nulo)
     * @throws NullPointerException se o evento for nulo
     */
    public void process(TrackingEvent event) {
        Objects.requireNonNull(event, "Evento não pode ser nulo");
        long latency = event.latencyMs();

        eventStore.save(event);
        performanceMonitor.record(event, latency);

        Sinks.EmitResult result = eventSink.tryEmitNext(event);
        if (result.isFailure()) {
            log.warn("Falha ao emitir evento no sink: eventId={}, result={}", event.eventId(), result);
        }

        log.debug("Evento processado: eventId={}, latency={}ms, scenario={}", event.eventId(), latency, event.scenario());
    }

    /**
     * Retorna um {@link Flux} de eventos para o Cenário 2 (Event-Driven / SSE).
     *
     * <p>O Flux é um stream quente (hot stream) que emite eventos assim que chegam,
     * sem necessidade de consulta periódica pelo cliente.
     *
     * <p>Cada novo subscriber receberá apenas os eventos emitidos após a sua subscrição.
     *
     * @return stream reativo de eventos de rastreamento
     */
    public Flux<TrackingEvent> streamEvents() {
        return eventSink.asFlux()
                .doOnSubscribe(s -> log.info("Novo subscriber no stream de eventos SSE."))
                .doOnError(e -> log.error("Erro no stream de eventos: {}", e.getMessage()));
    }

    /**
     * Encerra o sink de eventos, completando todos os streams ativos.
     *
     * <p>Deve ser chamado no encerramento da aplicação para liberar recursos.
     */
    public void shutdown() {
        eventSink.tryEmitComplete();
        log.info("ApiGateway encerrado.");
    }
}
