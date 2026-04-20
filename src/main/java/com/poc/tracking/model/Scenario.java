package com.poc.tracking.model;

/**
 * Define os cenários de execução da PoC.
 *
 * <p>Os cenários representam as duas abordagens arquiteturais comparadas:
 * <ul>
 *   <li>{@link #POLLING} - Modelo de consulta periódica (Grupo de Controle).</li>
 *   <li>{@link #EVENT_DRIVEN} - Modelo baseado em eventos (Grupo Experimental).</li>
 * </ul>
 *
 * @author PoC Team
 * @version 1.0.0
 */
public enum Scenario {

    /**
     * Cenário 1 - Grupo de Controle.
     * O cliente solicita informações em intervalos fixos (polling).
     */
    POLLING,

    /**
     * Cenário 2 - Grupo Experimental.
     * O servidor envia informações proativamente via Server-Sent Events (SSE).
     */
    EVENT_DRIVEN
}
