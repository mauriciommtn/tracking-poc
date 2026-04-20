package com.poc.tracking.emulator;

import com.poc.tracking.model.DeliveryStatus;
import com.poc.tracking.model.Scenario;
import com.poc.tracking.model.TrackingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Emulador de eventos de rastreamento para a PoC.
 *
 * <p>Responsável por gerar eventos sintéticos de rastreamento GPS e mudanças de
 * status de entrega, simulando o comportamento de um sistema real de logística.
 * Os eventos são gerados de forma determinística com variação controlada para
 * permitir comparação estatística entre os cenários.
 *
 * <p>Parâmetros de configuração via {@code application.yml}:
 * <ul>
 *   <li>{@code tracking.emulator.total-events} - Total de eventos por ciclo de teste.</li>
 *   <li>{@code tracking.emulator.event-interval-ms} - Intervalo entre eventos gerados.</li>
 * </ul>
 *
 * @author PoC Team
 * @version 1.0.0
 * @see TrackingEvent
 */
@Component
public class EventEmulator {

    private static final Logger log = LoggerFactory.getLogger(EventEmulator.class);

    /** Latitude base para São Paulo, SP */
    private static final double BASE_LATITUDE = -23.5505;

    /** Longitude base para São Paulo, SP */
    private static final double BASE_LONGITUDE = -46.6333;

    /** Variação máxima de coordenada por evento (graus) */
    private static final double COORDINATE_DELTA = 0.01;

    private static final List<DeliveryStatus> STATUS_SEQUENCE = List.of(
            DeliveryStatus.PENDING,
            DeliveryStatus.PROCESSING,
            DeliveryStatus.IN_TRANSIT,
            DeliveryStatus.OUT_FOR_DELIVERY,
            DeliveryStatus.DELIVERED
    );

    private final Random random = new Random(42L);
    private final AtomicInteger eventCounter = new AtomicInteger(0);

    @Value("${tracking.emulator.total-events:100}")
    private int totalEvents;

    /**
     * Gera um único evento de rastreamento para o cenário e pedido especificados.
     *
     * <p>As coordenadas GPS são calculadas com base em um deslocamento incremental
     * a partir de uma coordenada base (São Paulo), simulando movimento real de entrega.
     *
     * @param orderId  identificador do pedido a ser rastreado
     * @param scenario cenário da PoC ({@link Scenario#POLLING} ou {@link Scenario#EVENT_DRIVEN})
     * @return evento de rastreamento gerado
     * @throws IllegalArgumentException se {@code orderId} for nulo ou vazio
     */
    public TrackingEvent generateEvent(String orderId, Scenario scenario) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId não pode ser nulo ou vazio");
        }

        int count = eventCounter.incrementAndGet();
        double lat = BASE_LATITUDE + (random.nextDouble() * COORDINATE_DELTA * 2 - COORDINATE_DELTA);
        double lon = BASE_LONGITUDE + (random.nextDouble() * COORDINATE_DELTA * 2 - COORDINATE_DELTA);
        DeliveryStatus status = resolveStatus(count);

        TrackingEvent event = TrackingEvent.of(orderId, lat, lon, status, scenario);
        log.debug("Evento gerado [{}]: orderId={}, status={}, scenario={}", event.eventId(), orderId, status, scenario);
        return event;
    }

    /**
     * Gera uma lista de eventos de rastreamento em lote para simulação de carga.
     *
     * <p>O método gera {@code quantity} eventos consecutivos para o mesmo pedido,
     * com progressão de status simulando o ciclo de vida de uma entrega.
     *
     * @param orderId  identificador do pedido
     * @param quantity número de eventos a gerar (deve ser positivo)
     * @param scenario cenário da PoC
     * @return lista imutável de eventos gerados
     * @throws IllegalArgumentException se {@code quantity} for menor ou igual a zero
     */
    public List<TrackingEvent> generateBatch(String orderId, int quantity, Scenario scenario) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity deve ser maior que zero, recebido: " + quantity);
        }

        log.info("Gerando lote de {} eventos para orderId={}, scenario={}", quantity, orderId, scenario);
        return java.util.stream.IntStream.range(0, quantity)
                .mapToObj(i -> generateEvent(orderId, scenario))
                .toList();
    }

    /**
     * Reseta o contador interno de eventos.
     *
     * <p>Deve ser chamado entre execuções dos cenários para garantir
     * que os testes iniciem com estado limpo.
     */
    public void reset() {
        eventCounter.set(0);
        log.info("Contador de eventos resetado.");
    }

    /**
     * Retorna o número total de eventos gerados desde o último reset.
     *
     * @return quantidade de eventos gerados
     */
    public int getEventCount() {
        return eventCounter.get();
    }

    /**
     * Resolve o status de entrega com base na sequência de ciclo de vida.
     *
     * @param count contador de eventos atual
     * @return status correspondente ao estágio atual
     */
    private DeliveryStatus resolveStatus(int count) {
        int index = Math.min(count / Math.max(1, totalEvents / STATUS_SEQUENCE.size()),
                STATUS_SEQUENCE.size() - 1);
        return STATUS_SEQUENCE.get(index);
    }
}
