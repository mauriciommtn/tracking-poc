package com.poc.tracking.config;

import java.util.concurrent.Executor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Configuração principal da aplicação de PoC de Rastreamento.
 *
 * <p>Define beans de infraestrutura como executor assíncrono e registry de métricas.
 * O {@link SimpleMeterRegistry} é utilizado como fallback para ambientes sem Prometheus.
 *
 * @author Mauricio Nogueira
 * @version 1.0.0
 */
@Configuration
public class AppConfig {

    /**
     * Configura o pool de threads para processamento assíncrono de eventos.
     *
     * <p>Parâmetros configurados:
     * <ul>
     *   <li>Core pool size: 4 threads</li>
     *   <li>Max pool size: 16 threads</li>
     *   <li>Queue capacity: 500 tarefas</li>
     *   <li>Thread name prefix: {@code tracking-async-}</li>
     * </ul>
     *
     * @return executor configurado para tarefas assíncronas
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("tracking-async-");
        executor.initialize();
        return executor;
    }

    /**
     * Fornece um {@link SimpleMeterRegistry} como fallback para testes e ambientes
     * onde o Prometheus não está configurado.
     *
     * @return registry de métricas simples
     */
    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry simpleMeterRegistry() {
        return new SimpleMeterRegistry();
    }

}
