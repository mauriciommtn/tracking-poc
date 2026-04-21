package com.poc.tracking.model;

import java.time.Instant;

/**
 * Registro imutável de uma métrica de desempenho coletada durante a execução da PoC.
 *
 * <p>Cada registro associa um evento a sua latência medida, permitindo análise
 * estatística posterior comparando os dois cenários.
 *
 * @param eventId       ID do evento rastreado.
 * @param scenario      Cenário ao qual a métrica pertence.
 * @param latencyMs     Latência medida em milissegundos.
 * @param collectedAt   Instante em que a métrica foi coletada.
 *
 * @author Mauricio Nogueira
 * @version 1.0.0
 */
public record MetricRecord(
        String eventId,
        Scenario scenario,
        long latencyMs,
        Instant collectedAt
) {

    /**
     * Cria um {@code MetricRecord} com timestamp atual.
     *
     * @param eventId   ID do evento
     * @param scenario  cenário da execução
     * @param latencyMs latência em milissegundos
     * @return novo registro de métrica
     */
    public static MetricRecord of(String eventId, Scenario scenario, long latencyMs) {
        return new MetricRecord(eventId, scenario, latencyMs, Instant.now());
    }
}
