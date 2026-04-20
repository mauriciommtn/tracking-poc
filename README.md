# Tracking PoC — Polling vs Event-Driven

> **Prova de Conceito** comparando a arquitetura de **Polling** (Cenário 1) com a arquitetura **Event-Driven via SSE** (Cenário 2) para sistemas de rastreamento de entregas em tempo real.

---

## Sumário

- [Visão Geral](#visão-geral)
- [Arquitetura](#arquitetura)
- [Componentes Principais](#componentes-principais)
- [Cenários da PoC](#cenários-da-poc)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [Tecnologias](#tecnologias)
- [Como Executar](#como-executar)
- [Endpoints da API](#endpoints-da-api)
- [Métricas e Observabilidade](#métricas-e-observabilidade)
- [Testes](#testes)
- [Geração de Javadoc](#geração-de-javadoc)
- [Análise Estatística](#análise-estatística)

---

## Visão Geral

Esta PoC investiga a diferença de latência e eficiência entre dois paradigmas de comunicação para sistemas de rastreamento logístico:

| Cenário | Modelo | Descrição |
|---------|--------|-----------|
| **Cenário 1** (Controle) | Polling | O cliente consulta a API em intervalos fixos |
| **Cenário 2** (Experimental) | Event-Driven (SSE) | O servidor notifica o cliente proativamente ao detectar mudanças |

Os dados coletados são submetidos à análise estatística descritiva (média, mediana, P95, P99, desvio padrão) para identificar qual abordagem oferece menor latência e maior transparência ao usuário final.

---

## Arquitetura

```
┌──────────────────────────────────────────────────────────────────┐
│                         tracking-poc                             │
│                                                                  │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────┐  │
│  │  EventEmulator  │───▶│   ApiGateway    │───▶│ EventStore  │  │
│  │  (Gerador de    │    │  (Middleware    │    │ (In-Memory  │  │
│  │   eventos GPS   │    │   assíncrono)   │    │  Storage)   │  │
│  │   e status)     │    │                 │    └─────────────┘  │
│  └─────────────────┘    │   Sinks.Many    │          │          │
│                         │   (Flux/SSE)    │          │          │
│                         └────────┬────────┘          │          │
│                                  │                   │          │
│              ┌───────────────────┼───────────────────┘          │
│              │                   │                              │
│  ┌───────────▼──────┐  ┌─────────▼──────────┐                  │
│  │PerformanceMonitor│  │    Controllers      │                  │
│  │  (Micrometer +   │  │  ┌──────────────┐  │                  │
│  │   Prometheus)    │  │  │PollingCtrl   │  │ ◀── Cenário 1    │
│  └──────────────────┘  │  ├──────────────┤  │                  │
│                         │  │EventDrivenCtrl│ │ ◀── Cenário 2    │
│                         │  ├──────────────┤  │                  │
│                         │  │ComparisonCtrl│  │ ◀── Comparação   │
│                         │  └──────────────┘  │                  │
│                         └────────────────────┘                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## Componentes Principais

### 1. `EventEmulator` — Emulador de Eventos

**Localização:** `com.poc.tracking.emulator.EventEmulator`

Responsável por gerar eventos sintéticos de rastreamento simulando um sistema real de logística.

| Método | Descrição |
|--------|-----------|
| `generateEvent(orderId, scenario)` | Gera um único evento com coordenadas GPS próximas a São Paulo e status progressivo |
| `generateBatch(orderId, quantity, scenario)` | Gera lote de eventos para simulação de carga |
| `reset()` | Reseta o contador de eventos para novo ciclo de teste |
| `getEventCount()` | Retorna o número de eventos gerados desde o último reset |

**Detalhes técnicos:**
- Coordenadas GPS baseadas em São Paulo (`-23.5505, -46.6333`) com variação controlada de `±0.01°`
- Status progride sequencialmente: `PENDING → PROCESSING → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED`
- Seed fixo no `Random` (`42L`) para reprodutibilidade nos testes

---

### 2. `ApiGateway` — Middleware Assíncrono

**Localização:** `com.poc.tracking.gateway.ApiGateway`

Núcleo da PoC. Processa eventos, persiste no `EventStore`, registra métricas e publica no stream reativo.

| Método | Descrição |
|--------|-----------|
| `process(event)` | Processa um evento: persiste, registra métricas e emite no stream SSE |
| `streamEvents()` | Retorna `Flux<TrackingEvent>` para clientes SSE (Cenário 2) |
| `shutdown()` | Encerra o sink de eventos e completa todos os streams ativos |

**Detalhes técnicos:**
- Utiliza `Sinks.Many.multicast().onBackpressureBuffer(bufferSize)` do Project Reactor
- Buffer configurável via `tracking.event.buffer-size` (padrão: `256`)
- Stream é **hot**: cada subscriber recebe apenas eventos emitidos após sua conexão

---

### 3. `EventStore` — Repositório em Memória

**Localização:** `com.poc.tracking.gateway.EventStore`

Armazenamento thread-safe de eventos processados.

| Método | Descrição |
|--------|-----------|
| `save(event)` | Persiste evento indexado por `orderId` |
| `findByOrderId(orderId)` | Retorna histórico completo de um pedido |
| `findLatestByOrderId(orderId)` | Retorna o evento mais recente (estado atual) |
| `countByOrderId(orderId)` | Conta eventos de um pedido |
| `clearByOrderId(orderId)` | Remove eventos de um pedido específico |
| `clearAll()` | Limpa todo o armazenamento |
| `totalOrders()` | Retorna número de pedidos distintos |

**Detalhes técnicos:**
- `ConcurrentHashMap` para acesso concorrente seguro
- Listas sincronizadas por pedido via `Collections.synchronizedList`
- Listas retornadas são **imutáveis** (`List.copyOf`)

---

### 4. `PerformanceMonitor` — Monitor de Performance

**Localização:** `com.poc.tracking.metrics.PerformanceMonitor`

Coleta e analisa métricas de latência dos dois cenários.

| Método | Descrição |
|--------|-----------|
| `record(event, latencyMs)` | Registra latência no Micrometer e em memória |
| `getStatistics(scenario)` | Calcula estatísticas descritivas completas |
| `getRawLatencies(scenario)` | Retorna lista ordenada de latências brutas |
| `getAllRecords()` | Retorna todos os registros agrupados por cenário |
| `reset()` | Limpa todos os registros em memória |

**Métricas Micrometer exportadas:**
- `tracking.event.latency` — Timer com tags `scenario` e `status`
- `tracking.event.count` — Contador de eventos por `scenario` e `status`

**Estatísticas calculadas:**

| Estatística | Descrição |
|-------------|-----------|
| `count` | Número total de amostras |
| `mean` | Latência média aritmética (ms) |
| `median` | Percentil 50 (P50) |
| `p95` | Percentil 95 — latência abaixo da qual 95% das requisições se enquadram |
| `p99` | Percentil 99 |
| `stdDev` | Desvio padrão |
| `min` | Menor latência observada |
| `max` | Maior latência observada |

---

### 5. Controllers

#### `PollingController` — Cenário 1

**Base path:** `/api/polling`

Implementa o modelo de consulta periódica. Cada requisição `GET` simula uma "polling request".

#### `EventDrivenController` — Cenário 2

**Base path:** `/api/events`

Implementa a arquitetura Event-Driven via SSE. O endpoint `/stream` mantém conexão aberta e envia eventos proativamente.

#### `ComparisonController` — Análise Comparativa

**Base path:** `/api/comparison`

Executa os dois cenários em sequência e produz relatório comparativo.

---

## Cenários da PoC

### Cenário 1 — Grupo de Controle (Polling)

```
Cliente                    Servidor
  │                           │
  │──── GET /polling/{id} ───▶│  (a cada 1s)
  │◀─── { status, coords } ───│
  │                           │
  │──── GET /polling/{id} ───▶│  (1s depois)
  │◀─── { status, coords } ───│
  │           ...             │
```

**Problema:** O cliente faz N requisições desnecessárias quando não há mudança de status. A latência inclui o tempo de espera até a próxima consulta.

### Cenário 2 — Grupo Experimental (Event-Driven / SSE)

```
Cliente                    Servidor
  │                           │
  │── GET /events/stream ────▶│  (uma única conexão)
  │                           │
  │                     [status muda]
  │◀── event: { status } ─────│  (imediato)
  │                           │
  │                     [coords mudam]
  │◀── event: { coords } ─────│  (imediato)
  │           ...             │
```

**Vantagem:** O cliente é notificado imediatamente na mudança. Zero requisições desnecessárias.

---

## Estrutura do Projeto

```
tracking-poc/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/poc/tracking/
    │   │   ├── TrackingPocApplication.java      # Ponto de entrada Spring Boot
    │   │   ├── config/
    │   │   │   └── AppConfig.java               # Thread pool e beans de infraestrutura
    │   │   ├── controller/
    │   │   │   ├── PollingController.java        # Endpoints Cenário 1
    │   │   │   ├── EventDrivenController.java    # Endpoints Cenário 2 (SSE)
    │   │   │   └── ComparisonController.java     # Endpoints de comparação
    │   │   ├── emulator/
    │   │   │   └── EventEmulator.java            # Gerador de eventos sintéticos
    │   │   ├── gateway/
    │   │   │   ├── ApiGateway.java               # Middleware assíncrono central
    │   │   │   └── EventStore.java               # Repositório em memória
    │   │   ├── metrics/
    │   │   │   └── PerformanceMonitor.java        # Coleta e análise de métricas
    │   │   └── model/
    │   │       ├── TrackingEvent.java            # Record: evento de rastreamento
    │   │       ├── MetricRecord.java             # Record: registro de métrica
    │   │       ├── DeliveryStatus.java           # Enum: status de entrega
    │   │       └── Scenario.java                 # Enum: cenário da PoC
    │   └── resources/
    │       └── application.yml                   # Configurações da aplicação
    └── test/
        └── java/com/poc/tracking/
            ├── controller/
            │   ├── PollingControllerTest.java    # Testes integração Cenário 1
            │   ├── EventDrivenControllerTest.java # Testes integração Cenário 2
            │   └── ComparisonControllerTest.java  # Testes integração comparação
            ├── emulator/
            │   └── EventEmulatorTest.java        # Testes unitários do emulador
            ├── gateway/
            │   ├── ApiGatewayTest.java           # Testes unitários do gateway
            │   └── EventStoreTest.java           # Testes unitários do store
            ├── metrics/
            │   └── PerformanceMonitorTest.java   # Testes unitários do monitor
            └── model/
                ├── TrackingEventTest.java        # Testes do record TrackingEvent
                └── MetricRecordTest.java         # Testes do record MetricRecord
```

---

## Tecnologias

| Tecnologia | Versão | Uso |
|------------|--------|-----|
| Java | 21 | Linguagem principal; Records, Pattern Matching, Virtual Threads |
| Spring Boot | 3.2.5 | Framework web e injeção de dependências |
| Spring WebFlux | 3.2.5 | Suporte reativo para SSE (Server-Sent Events) |
| Project Reactor | 3.6.x | `Flux`, `Sinks` para streaming assíncrono |
| Micrometer | 1.12.x | Abstração de métricas |
| Prometheus | — | Backend de armazenamento de métricas (via Actuator) |
| JUnit 5 | 5.10.x | Framework de testes |
| AssertJ | 3.25.x | Asserções fluentes nos testes |
| reactor-test | 3.6.x | `StepVerifier` para testes de streams reativos |
| MockMvc | — | Testes de integração dos controllers |
| Lombok | 1.18.x | Redução de boilerplate |

---

## Como Executar

### Pré-requisitos

- Java 21+
- Maven 3.9+

### Compilar e executar testes

```bash
cd tracking-poc
mvn clean verify
```

### Executar a aplicação

```bash
mvn spring-boot:run
```

A aplicação sobe em `http://localhost:8080`.

### Configurações relevantes (`application.yml`)

```yaml
tracking:
  polling:
    interval-ms: 1000         # Intervalo sugerido de polling (ms)
  event:
    buffer-size: 256          # Buffer do sink SSE
  emulator:
    total-events: 100         # Eventos por ciclo de simulação
    event-interval-ms: 200    # Intervalo entre eventos no emulador
```

---

## Endpoints da API

### Cenário 1 — Polling (`/api/polling`)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/polling/{orderId}` | Retorna evento mais recente do pedido |
| `GET` | `/api/polling/{orderId}/history` | Retorna histórico completo |
| `POST` | `/api/polling/simulate?orderId=X&quantity=N` | Simula N eventos de polling |
| `GET` | `/api/polling/metrics` | Estatísticas descritivas do Cenário 1 |

### Cenário 2 — Event-Driven (`/api/events`)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `GET` | `/api/events/stream` | Stream SSE de eventos em tempo real |
| `POST` | `/api/events/simulate?orderId=X&quantity=N` | Simula N eventos event-driven |
| `GET` | `/api/events/metrics` | Estatísticas descritivas do Cenário 2 |

### Comparação (`/api/comparison`)

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/api/comparison/run?quantity=N` | Executa ambos cenários e retorna comparativo |
| `GET` | `/api/comparison/report` | Relatório comparativo com análise de eficiência |
| `DELETE` | `/api/comparison/reset` | Reseta todo o estado para novo experimento |

### Exemplo: Executar comparação completa

```bash
# 1. Rodar ambos cenários com 50 eventos cada
curl -X POST "http://localhost:8080/api/comparison/run?quantity=50"

# 2. Obter relatório comparativo
curl http://localhost:8080/api/comparison/report

# Resposta esperada:
# {
#   "polling": { "count": 50, "mean": 1.2, "p95": 3.0, ... },
#   "eventDriven": { "count": 50, "mean": 0.4, "p95": 1.0, ... },
#   "latencyImprovementPercent": 66.67,
#   "fasterScenario": "EVENT_DRIVEN"
# }
```

---

## Métricas e Observabilidade

### Prometheus

Com a aplicação rodando, acesse:

```
http://localhost:8080/actuator/prometheus
```

Métricas disponíveis:
- `tracking_event_latency_seconds_*` — Histograma de latência por cenário/status
- `tracking_event_count_total` — Contador de eventos processados

### Actuator

```bash
# Health check
curl http://localhost:8080/actuator/health

# Todas as métricas disponíveis
curl http://localhost:8080/actuator/metrics

# Métrica específica
curl http://localhost:8080/actuator/metrics/tracking.event.latency
```

---

## Testes

### Cobertura de testes

| Classe | Tipo | Testes |
|--------|------|--------|
| `EventEmulator` | Unitário | 11 testes — geração, validação, batch, reset |
| `EventStore` | Unitário | 11 testes — persistência, recuperação, limpeza |
| `ApiGateway` | Unitário | 8 testes — processo, stream reativo, shutdown |
| `PerformanceMonitor` | Unitário | 14 testes — record, estatísticas, reset |
| `TrackingEvent` | Unitário | 5 testes — factory, latência, igualdade |
| `MetricRecord` | Unitário | 4 testes — factory, igualdade |
| `PollingController` | Integração | 10 testes — todos os endpoints |
| `EventDrivenController` | Integração | 9 testes — todos os endpoints |
| `ComparisonController` | Integração | 10 testes — run, report, reset |

### Executar testes específicos

```bash
# Apenas testes unitários
mvn test -Dtest="EventEmulatorTest,EventStoreTest,ApiGatewayTest,PerformanceMonitorTest"

# Apenas testes de integração
mvn test -Dtest="*ControllerTest"

# Todos os testes
mvn test
```

---

## Geração de Javadoc

```bash
mvn javadoc:javadoc
```

Documentação gerada em: `target/site/apidocs/index.html`

---

## Análise Estatística

### Interpretando os resultados

Após executar `POST /api/comparison/run`, o endpoint `GET /api/comparison/report` retorna:

```json
{
  "polling": {
    "count": 100,
    "mean": 1.5,
    "median": 1.0,
    "p95": 4.0,
    "p99": 7.0,
    "stdDev": 1.2,
    "min": 0.0,
    "max": 8.0
  },
  "eventDriven": {
    "count": 100,
    "mean": 0.3,
    "median": 0.0,
    "p95": 1.0,
    "p99": 2.0,
    "stdDev": 0.4,
    "min": 0.0,
    "max": 3.0
  },
  "latencyImprovementPercent": 80.0,
  "fasterScenario": "EVENT_DRIVEN"
}
```

### Guia de interpretação

| Métrica | O que indica |
|---------|-------------|
| `mean` | Latência média — sensível a outliers |
| `median` | Latência típica — mais robusta a outliers |
| `p95` | 95% das requisições são mais rápidas que este valor |
| `p99` | SLA típico de produção |
| `stdDev` | Variabilidade — menor = mais consistente |
| `latencyImprovementPercent` | Redução percentual de latência do Event-Driven vs Polling |

### Dados para histograma/dispersão

Use o array `latenciesPoll` e `latenciesEvent` retornados em `/api/comparison/run` para plotar:
- **Histograma de frequência** de latências por cenário
- **Gráfico de dispersão** latência × ordem do evento

---

## Autor

PoC Team — Java 21 + Spring Boot 3.2 + Project Reactor
