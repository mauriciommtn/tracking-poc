package com.poc.tracking.model;

/**
 * Representa os possíveis status de uma entrega rastreada.
 *
 * <p>Os status seguem o fluxo natural de uma entrega, desde a confirmação
 * do pedido até a entrega final ou ocorrência de falha.
 *
 * @author PoC Team
 * @version 1.0.0
 */
public enum DeliveryStatus {

    /** Pedido confirmado e aguardando processamento. */
    PENDING,

    /** Pedido em processamento no centro de distribuição. */
    PROCESSING,

    /** Pedido coletado e em trânsito para destino. */
    IN_TRANSIT,

    /** Entregador próximo ao endereço de entrega. */
    OUT_FOR_DELIVERY,

    /** Entrega realizada com sucesso. */
    DELIVERED,

    /** Ocorreu uma falha ou atraso na entrega. */
    FAILED
}
