package com.poc.tracking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Classe principal da aplicação de Prova de Conceito (PoC) para rastreamento.
 *
 * <p>Esta PoC compara duas abordagens arquiteturais para sistemas de rastreamento em tempo real:
 * <ul>
 *   <li><strong>Cenário 1 (Polling):</strong> O cliente consulta a API em intervalos fixos.</li>
 *   <li><strong>Cenário 2 (Event-Driven):</strong> O servidor notifica o cliente proativamente via SSE.</li>
 * </ul>
 *
 * <p>Componentes principais:
 * <ul>
 *   <li>{@code EventEmulator} - Gera eventos de rastreamento GPS e de status.</li>
 *   <li>{@code ApiGateway} - Processa e roteia eventos de forma assíncrona.</li>
 *   <li>{@code PerformanceMonitor} - Coleta métricas de latência e throughput via Micrometer/Prometheus.</li>
 * </ul>
 *
 * @author Mauricio Nogueira
 * @version 1.0.0
 */
@SpringBootApplication
@EnableScheduling
public class TrackingPocApplication {

    /**
     * Ponto de entrada da aplicação Spring Boot.
     *
     * @param args argumentos de linha de comando
     */
    public static void main(String[] args) {
        SpringApplication.run(TrackingPocApplication.class, args);
    }
}
