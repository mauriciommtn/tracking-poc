package com.poc.tracking.gateway;

import com.poc.tracking.model.DeliveryStatus;
import com.poc.tracking.model.Scenario;
import com.poc.tracking.model.TrackingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Testes unitários para {@link EventStore}.
 *
 * <p>Cobre persistência, recuperação por orderId, latest event, contagem e limpeza.
 *
 * @author Mauricio Nogueira
 * @version 1.0.0
 */
@DisplayName("EventStore - Testes Unitários")
class EventStoreTest {

    private EventStore store;

    @BeforeEach
    void setUp() {
        store = new EventStore();
    }

    private TrackingEvent makeEvent(String orderId, Scenario scenario) {
        return TrackingEvent.of(orderId, -23.55, -46.63, DeliveryStatus.IN_TRANSIT, scenario);
    }

    // ─── save ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("save: deve persistir evento corretamente")
    void save_shouldPersistEvent() {
        TrackingEvent event = makeEvent("ORDER-001", Scenario.POLLING);
        store.save(event);

        assertThat(store.countByOrderId("ORDER-001")).isEqualTo(1);
    }

    @Test
    @DisplayName("save: deve lançar NullPointerException para evento nulo")
    void save_shouldThrowForNullEvent() {
        assertThatNullPointerException().isThrownBy(() -> store.save(null));
    }

    @Test
    @DisplayName("save: múltiplos eventos para o mesmo orderId devem ser acumulados")
    void save_shouldAccumulateEventsForSameOrder() {
        store.save(makeEvent("ORDER-002", Scenario.POLLING));
        store.save(makeEvent("ORDER-002", Scenario.POLLING));
        store.save(makeEvent("ORDER-002", Scenario.POLLING));

        assertThat(store.countByOrderId("ORDER-002")).isEqualTo(3);
    }

    @Test
    @DisplayName("save: eventos de pedidos diferentes devem ser isolados")
    void save_shouldIsolateEventsByOrderId() {
        store.save(makeEvent("ORDER-A", Scenario.POLLING));
        store.save(makeEvent("ORDER-B", Scenario.EVENT_DRIVEN));

        assertThat(store.countByOrderId("ORDER-A")).isEqualTo(1);
        assertThat(store.countByOrderId("ORDER-B")).isEqualTo(1);
    }

    // ─── findByOrderId ──────────────────────────────────────────────────────

    @Test
    @DisplayName("findByOrderId: deve retornar lista com todos os eventos salvos")
    void findByOrderId_shouldReturnAllEvents() {
        store.save(makeEvent("ORDER-001", Scenario.POLLING));
        store.save(makeEvent("ORDER-001", Scenario.POLLING));

        List<TrackingEvent> result = store.findByOrderId("ORDER-001");
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findByOrderId: deve retornar lista vazia para orderId inexistente")
    void findByOrderId_shouldReturnEmptyForUnknownOrder() {
        List<TrackingEvent> result = store.findByOrderId("ORDER-NOT-EXISTS");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByOrderId: lista retornada deve ser imutável")
    void findByOrderId_shouldReturnImmutableList() {
        store.save(makeEvent("ORDER-001", Scenario.POLLING));
        List<TrackingEvent> result = store.findByOrderId("ORDER-001");

        assertThatThrownBy(() -> result.add(makeEvent("ORDER-001", Scenario.POLLING)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ─── findLatestByOrderId ─────────────────────────────────────────────────

    @Test
    @DisplayName("findLatestByOrderId: deve retornar o último evento inserido")
    void findLatestByOrderId_shouldReturnLastInserted() {
        TrackingEvent first = makeEvent("ORDER-001", Scenario.POLLING);
        TrackingEvent last = makeEvent("ORDER-001", Scenario.POLLING);
        store.save(first);
        store.save(last);

        Optional<TrackingEvent> result = store.findLatestByOrderId("ORDER-001");
        assertThat(result).isPresent().contains(last);
    }

    @Test
    @DisplayName("findLatestByOrderId: deve retornar Optional vazio para ordem inexistente")
    void findLatestByOrderId_shouldReturnEmptyForUnknownOrder() {
        Optional<TrackingEvent> result = store.findLatestByOrderId("ORDER-NONE");
        assertThat(result).isEmpty();
    }

    // ─── clearByOrderId e clearAll ──────────────────────────────────────────

    @Test
    @DisplayName("clearByOrderId: deve remover apenas os eventos do pedido especificado")
    void clearByOrderId_shouldRemoveOnlySpecifiedOrder() {
        store.save(makeEvent("ORDER-A", Scenario.POLLING));
        store.save(makeEvent("ORDER-B", Scenario.POLLING));

        store.clearByOrderId("ORDER-A");

        assertThat(store.countByOrderId("ORDER-A")).isZero();
        assertThat(store.countByOrderId("ORDER-B")).isEqualTo(1);
    }

    @Test
    @DisplayName("clearAll: deve remover todos os eventos de todos os pedidos")
    void clearAll_shouldRemoveEverything() {
        store.save(makeEvent("ORDER-A", Scenario.POLLING));
        store.save(makeEvent("ORDER-B", Scenario.EVENT_DRIVEN));

        store.clearAll();

        assertThat(store.totalOrders()).isZero();
    }

    // ─── totalOrders ────────────────────────────────────────────────────────

    @Test
    @DisplayName("totalOrders: deve retornar número correto de pedidos distintos")
    void totalOrders_shouldReturnCorrectCount() {
        store.save(makeEvent("ORDER-1", Scenario.POLLING));
        store.save(makeEvent("ORDER-2", Scenario.POLLING));
        store.save(makeEvent("ORDER-1", Scenario.POLLING)); // duplicata

        assertThat(store.totalOrders()).isEqualTo(2);
    }
}
