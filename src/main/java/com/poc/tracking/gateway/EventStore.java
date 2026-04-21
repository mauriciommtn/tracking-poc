package com.poc.tracking.gateway;

import com.poc.tracking.model.TrackingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Armazenamento em memória para eventos de rastreamento.
 *
 * <p>Atua como repositório central de eventos processados pelo {@link ApiGateway}.
 * Utiliza estruturas thread-safe para suportar acesso concorrente nos dois cenários.
 *
 * <p>No Cenário 1 (Polling), os eventos são consultados por ID de pedido.
 * No Cenário 2 (Event-Driven), os eventos são publicados assim que chegam,
 * sem necessidade de consulta periódica.
 *
 * @author Mauricio Nogueira
 * @version 1.0.0
 */
@Component
public class EventStore {

    private static final Logger log = LoggerFactory.getLogger(EventStore.class);

    /** Mapa de orderId → lista de eventos, thread-safe */
    private final Map<String, List<TrackingEvent>> store = new ConcurrentHashMap<>();

    /**
     * Persiste um evento de rastreamento no armazenamento em memória.
     *
     * @param event evento a ser persistido (não pode ser nulo)
     * @throws NullPointerException se o evento for nulo
     */
    public void save(TrackingEvent event) {
        Objects.requireNonNull(event, "Evento não pode ser nulo");
        store.computeIfAbsent(event.orderId(), k -> Collections.synchronizedList(new ArrayList<>()))
             .add(event);
        log.debug("Evento salvo: eventId={}, orderId={}", event.eventId(), event.orderId());
    }

    /**
     * Recupera todos os eventos de um pedido específico.
     *
     * <p>Usado principalmente no Cenário 1 (Polling) para retornar o estado atual ao cliente.
     *
     * @param orderId identificador do pedido
     * @return lista imutável de eventos, ou lista vazia se não houver eventos
     */
    public List<TrackingEvent> findByOrderId(String orderId) {
        return List.copyOf(store.getOrDefault(orderId, Collections.emptyList()));
    }

    /**
     * Recupera o evento mais recente de um pedido.
     *
     * <p>Retorna o último evento inserido, que representa o estado atual do rastreamento.
     *
     * @param orderId identificador do pedido
     * @return {@link Optional} com o evento mais recente, ou vazio se não houver eventos
     */
    public Optional<TrackingEvent> findLatestByOrderId(String orderId) {
        List<TrackingEvent> events = store.getOrDefault(orderId, Collections.emptyList());
        if (events.isEmpty()) return Optional.empty();
        return Optional.of(events.get(events.size() - 1));
    }

    /**
     * Retorna o número total de eventos armazenados para um pedido.
     *
     * @param orderId identificador do pedido
     * @return quantidade de eventos
     */
    public int countByOrderId(String orderId) {
        return store.getOrDefault(orderId, Collections.emptyList()).size();
    }

    /**
     * Remove todos os eventos de um pedido específico.
     *
     * @param orderId identificador do pedido
     */
    public void clearByOrderId(String orderId) {
        store.remove(orderId);
        log.info("Eventos removidos para orderId={}", orderId);
    }

    /**
     * Limpa completamente o armazenamento.
     *
     * <p>Deve ser chamado entre execuções dos cenários para garantir isolamento.
     */
    public void clearAll() {
        store.clear();
        log.info("EventStore completamente limpo.");
    }

    /**
     * Retorna o número total de pedidos distintos no armazenamento.
     *
     * @return número de pedidos distintos
     */
    public int totalOrders() {
        return store.size();
    }
}
