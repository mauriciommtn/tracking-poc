package com.poc.tracking.emulator;

import com.poc.tracking.model.DeliveryStatus;
import com.poc.tracking.model.Scenario;
import com.poc.tracking.model.TrackingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Testes unitários para {@link EventEmulator}.
 *
 * <p>Cobre geração de eventos individuais, em lote, validação de parâmetros,
 * reset de contador e progressão de status de entrega.
 *
 * @author PoC Team
 * @version 1.0.0
 */
@DisplayName("EventEmulator - Testes Unitários")
class EventEmulatorTest {

    private EventEmulator emulator;

    @BeforeEach
    void setUp() {
        emulator = new EventEmulator();
        emulator.reset();
    }

    // ─── generateEvent ──────────────────────────────────────────────────────

    @Test
    @DisplayName("generateEvent: deve retornar evento não nulo com campos preenchidos")
    void generateEvent_shouldReturnNonNullEvent() {
        TrackingEvent event = emulator.generateEvent("ORDER-001", Scenario.POLLING);

        assertThat(event).isNotNull();
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.orderId()).isEqualTo("ORDER-001");
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("generateEvent: deve preservar orderId e scenario informados")
    void generateEvent_shouldPreserveOrderIdAndScenario() {
        TrackingEvent event = emulator.generateEvent("ORDER-XYZ", Scenario.EVENT_DRIVEN);

        assertThat(event.orderId()).isEqualTo("ORDER-XYZ");
        assertThat(event.scenario()).isEqualTo(Scenario.EVENT_DRIVEN);
    }

    @Test
    @DisplayName("generateEvent: coordenadas GPS devem estar próximas a São Paulo")
    void generateEvent_coordinatesShouldBeNearSaoPaulo() {
        TrackingEvent event = emulator.generateEvent("ORDER-001", Scenario.POLLING);

        assertThat(event.latitude()).isBetween(-23.57, -23.54);
        assertThat(event.longitude()).isBetween(-46.65, -46.62);
    }

    @Test
    @DisplayName("generateEvent: deve lançar IllegalArgumentException para orderId nulo")
    void generateEvent_shouldThrowForNullOrderId() {
        assertThatThrownBy(() -> emulator.generateEvent(null, Scenario.POLLING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderId");
    }

    @Test
    @DisplayName("generateEvent: deve lançar IllegalArgumentException para orderId vazio")
    void generateEvent_shouldThrowForBlankOrderId() {
        assertThatThrownBy(() -> emulator.generateEvent("  ", Scenario.POLLING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("generateEvent: cada evento deve ter eventId único")
    void generateEvent_shouldGenerateUniqueEventIds() {
        TrackingEvent e1 = emulator.generateEvent("ORDER-001", Scenario.POLLING);
        TrackingEvent e2 = emulator.generateEvent("ORDER-001", Scenario.POLLING);

        assertThat(e1.eventId()).isNotEqualTo(e2.eventId());
    }

    @Test
    @DisplayName("generateEvent: status deve ser um dos valores válidos do enum")
    void generateEvent_statusShouldBeValidEnum() {
        TrackingEvent event = emulator.generateEvent("ORDER-001", Scenario.POLLING);

        assertThat(event.status()).isNotNull();
        assertThat(DeliveryStatus.values()).contains(event.status());
    }

    // ─── generateBatch ──────────────────────────────────────────────────────

    @Test
    @DisplayName("generateBatch: deve retornar lista com quantidade exata solicitada")
    void generateBatch_shouldReturnCorrectSize() {
        List<TrackingEvent> batch = emulator.generateBatch("ORDER-001", 10, Scenario.POLLING);

        assertThat(batch).hasSize(10);
    }

    @Test
    @DisplayName("generateBatch: todos os eventos devem ter o orderId correto")
    void generateBatch_allEventsShouldHaveCorrectOrderId() {
        List<TrackingEvent> batch = emulator.generateBatch("ORDER-BATCH", 5, Scenario.EVENT_DRIVEN);

        assertThat(batch).allMatch(e -> "ORDER-BATCH".equals(e.orderId()));
    }

    @Test
    @DisplayName("generateBatch: todos os eventos devem ter o scenario correto")
    void generateBatch_allEventsShouldHaveCorrectScenario() {
        List<TrackingEvent> batch = emulator.generateBatch("ORDER-001", 5, Scenario.EVENT_DRIVEN);

        assertThat(batch).allMatch(e -> Scenario.EVENT_DRIVEN == e.scenario());
    }

    @Test
    @DisplayName("generateBatch: deve lançar IllegalArgumentException para quantity <= 0")
    void generateBatch_shouldThrowForZeroQuantity() {
        assertThatThrownBy(() -> emulator.generateBatch("ORDER-001", 0, Scenario.POLLING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -10, -100})
    @DisplayName("generateBatch: deve lançar IllegalArgumentException para quantities negativas")
    void generateBatch_shouldThrowForNegativeQuantity(int quantity) {
        assertThatThrownBy(() -> emulator.generateBatch("ORDER-001", quantity, Scenario.POLLING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("generateBatch: todos os eventIds devem ser únicos no lote")
    void generateBatch_shouldGenerateUniqueEventIds() {
        List<TrackingEvent> batch = emulator.generateBatch("ORDER-001", 20, Scenario.POLLING);

        long uniqueIds = batch.stream().map(TrackingEvent::eventId).distinct().count();
        assertThat(uniqueIds).isEqualTo(20);
    }

    // ─── reset e getEventCount ──────────────────────────────────────────────

    @Test
    @DisplayName("getEventCount: deve retornar zero após reset")
    void getEventCount_shouldReturnZeroAfterReset() {
        emulator.generateBatch("ORDER-001", 5, Scenario.POLLING);
        emulator.reset();

        assertThat(emulator.getEventCount()).isZero();
    }

    @Test
    @DisplayName("getEventCount: deve incrementar corretamente após geração")
    void getEventCount_shouldIncrementCorrectly() {
        emulator.generateBatch("ORDER-001", 3, Scenario.POLLING);
        emulator.generateEvent("ORDER-001", Scenario.POLLING);

        assertThat(emulator.getEventCount()).isEqualTo(4);
    }
}
