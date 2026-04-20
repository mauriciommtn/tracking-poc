package com.poc.tracking.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Representa um evento de rastreamento gerado pelo emulador.
 *
 * <p>Cada evento contém coordenadas GPS, status de entrega, e metadados de temporização
 * usados para calcular latência nos dois cenários da PoC.
 *
 * @param eventId       Identificador único do evento (UUID).
 * @param orderId       Identificador do pedido rastreado.
 * @param latitude      Latitude da coordenada GPS atual.
 * @param longitude     Longitude da coordenada GPS atual.
 * @param status        Status atual da entrega.
 * @param timestamp     Instante em que o evento foi gerado.
 * @param scenario      Cenário ao qual o evento pertence (POLLING ou EVENT_DRIVEN).
 *
 * @author PoC Team
 * @version 1.0.0
 */
public record TrackingEvent(
        String eventId,
        String orderId,
        double latitude,
        double longitude,
        DeliveryStatus status,
        Instant timestamp,
        Scenario scenario
) {

    /**
     * Cria um novo {@code TrackingEvent} com ID gerado automaticamente e timestamp atual.
     *
     * @param orderId   identificador do pedido
     * @param latitude  latitude GPS
     * @param longitude longitude GPS
     * @param status    status de entrega
     * @param scenario  cenário da PoC
     * @return novo evento de rastreamento
     */
    public static TrackingEvent of(String orderId, double latitude, double longitude,
                                   DeliveryStatus status, Scenario scenario) {
        return new TrackingEvent(
                UUID.randomUUID().toString(),
                orderId,
                latitude,
                longitude,
                status,
                Instant.now(),
                scenario
        );
    }

    /**
     * Calcula a latência em milissegundos entre a criação do evento e o momento atual.
     *
     * @return latência em milissegundos
     */
    public long latencyMs() {
        return Instant.now().toEpochMilli() - timestamp.toEpochMilli();
    }
}
