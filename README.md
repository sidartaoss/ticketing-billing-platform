# Ticketing Billing Platform

Microservico simplificado de cobrancas de bilhetagem. Implementa criacao de cobrancas com lock distribuido, consulta por ID com versionamento, processamento de webhook PIX e validacao de checkout de cartao de credito.

## Stack Tecnologica

| Camada         | Tecnologia                                |
|----------------|-------------------------------------------|
| Linguagem      | Java 25                                   |
| Framework      | Spring Boot 4.0.6                         |
| Build          | Maven (wrapper incluso)                   |
| Persistencia   | JPA/Hibernate + PostgreSQL 16             |
| Lock           | Redis 7 (fallback in-memory)              |
| Mensageria     | Apache Kafka 4.1.1                        |
| Testes         | JUnit 5, Mockito, H2 (integracao)        |
| Cobertura      | JaCoCo (minimo 70% no pacote `service`)   |

## Como Executar

### Pre-requisitos

- JDK 25+
- Docker e Docker Compose

### 1. Subir infraestrutura (PostgreSQL, Redis, Kafka)

```bash
cd ticketing-billing-service
docker-compose up -d
```

Verificar se os servicos estao prontos:

```bash
docker compose exec postgres pg_isready -U brasilia
# esperado: accepting connections

docker compose exec redis redis-cli ping
# esperado: PONG
```

### 2. Compilar o projeto

```bash
# Linux/macOS
./mvnw clean package

# Windows
mvnw.cmd clean package
```

### 3. Iniciar a aplicacao

```bash
# Linux/macOS
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

A aplicacao inicia em **http://localhost:8080**.

### 4. Rodar os testes

```bash
./mvnw test
```

Os testes utilizam H2 em memoria e nao dependem de infraestrutura externa.

### 5. Parar o ambiente

```bash
# Parar aplicacao: Ctrl+C no terminal

# Parar infraestrutura (preserva dados)
docker-compose down

# Parar e remover dados
docker-compose down -v
```

## Premissas Adotadas

- **Clientes externos sao mocks/fakes**: `PagamentoGatewayClient`, `CheckoutValidationClient`, `StatusConsultaExternaClient` simulam integracao. O foco e no comportamento de negocio, nao na integracao real.
- **UserContext mockavel**: simula usuario autenticado com `idUsuario`, `givenName`, `familyName` e `cpf`.
- **Lock distribuido dual-mode**: Redis em producao com `@ConditionalOnBean`; fallback automatico para `ConcurrentHashMap` in-memory quando Redis esta indisponivel (`@ConditionalOnMissingBean`).
- **Versionamento de cobrancas**: cada mudanca de status (reprocessamento, finalizacao via webhook) cria uma nova versao com referencia a anterior via `cobrancaPaiId`. A consulta retorna sempre a versao mais recente.
- **Timezone oficial**: `America/Sao_Paulo` para todas as datas de negocio.
- **PIX**: expiracao de 30 minutos apos criacao.
- **Defaults**: `metodo = PIX` e `tipo = RECARGA` quando nao informados no request.
- **Banco de testes**: H2 em memoria com perfil `test`, sem dependencia de Docker.

## Trade-offs

| Decisao | Beneficio | Custo |
|---------|-----------|-------|
| **Redis lock vs. lock em banco** | Baixa latencia, operacao atomica com TTL nativo | Exige Redis rodando; mitigado com fallback in-memory |
| **Mocks de integracao** | Autonomia total, foco nas regras de negocio | Nao valida contrato real com servicos externos |
| **Versionamento por novo registro** | Historico completo e auditavel de cada cobranca | Maior volume de dados vs. update in-place |
| **DDL auto-update (Hibernate)** | Pratico para desenvolvimento rapido | Inadequado para producao — ideal usar Flyway/Liquibase |
| **Kafka para eventos** | Desacopla consumidores, extensivel | Adiciona complexidade de infraestrutura |
| **Strategy pattern (PIX/Cartao)** | Extensivel para novos metodos de pagamento | Leve overhead para apenas 2 implementacoes |

## Endpoints e Exemplos de Requests/Responses

Base path: `/api/v1/cobrancas`

---

### POST `/api/v1/cobrancas` — Criar Cobranca

Cria uma cobranca com lock distribuido por usuario (chave `cobrancas:{idUsuario}`, TTL 5s).

**Request (PIX):**

```bash
curl -X POST http://localhost:8080/api/v1/cobrancas \
  -H "Content-Type: application/json" \
  -d '{
    "valor": 25.50,
    "tipo": "RECARGA",
    "metodo": "PIX"
  }'
```

**Response `201 Created`:**

```json
{
  "id": 1,
  "txid": "abc123",
  "copiaECola": "000201...",
  "dataExpiracao": "2026-04-30T12:30:00",
  "transactionId": null,
  "acsUrl": null,
  "threeDsPayload": null
}
```

**Request (Cartao de Credito):**

```bash
curl -X POST http://localhost:8080/api/v1/cobrancas \
  -H "Content-Type: application/json" \
  -d '{
    "valor": 100.00,
    "tipo": "ENVIO_CARTAO",
    "metodo": "CARTAO_CREDITO"
  }'
```

**Response `201 Created`:**

```json
{
  "id": 2,
  "txid": null,
  "copiaECola": null,
  "dataExpiracao": null,
  "transactionId": "txn-xyz-123",
  "acsUrl": null,
  "threeDsPayload": null
}
```

---

### GET `/api/v1/cobrancas/{id}` — Consultar Cobranca

Retorna a versao mais recente da cobranca. Para cobrancas PIX com status pendente, consulta status externo e cria nova versao se houve mudanca.

```bash
curl http://localhost:8080/api/v1/cobrancas/1
```

**Response `200 OK`:**

```json
{
  "id": 1,
  "txid": "abc123",
  "idUsuario": "user-1",
  "tipo": "RECARGA",
  "metodo": "PIX",
  "status": "SOLICITADA",
  "valorSolicitado": 25.50,
  "valorPago": null,
  "dataCriacao": "2026-04-30T12:00:00",
  "dataExpiracao": "2026-04-30T12:30:00",
  "dataFinalizada": null
}
```

---

### POST `/api/v1/cobrancas/webhook/pix` — Webhook PIX

Recebe notificacoes de pagamento PIX. Para cada item com `txid` valido, finaliza a cobranca pendente criando nova versao com status `FINALIZADA`.

```bash
curl -X POST http://localhost:8080/api/v1/cobrancas/webhook/pix \
  -H "Content-Type: application/json" \
  -d '{
    "pix": [
      {
        "txid": "abc123",
        "horario": "2026-04-30T13:02:30Z",
        "valor": 25.50
      }
    ]
  }'
```

**Response `200 OK`:** corpo vazio.

---

### POST `/api/v1/cobrancas/{transactionId}/validate` — Validar Checkout Cartao

Valida o desafio 3D Secure de uma cobranca de cartao de credito.

```bash
curl -X POST http://localhost:8080/api/v1/cobrancas/txn-xyz-123/validate \
  -H "Content-Type: application/json" \
  -d '{
    "cavv": "AAABBB",
    "xid": "XYZ",
    "eci": "05"
  }'
```

**Response `200 OK`:**

```json
{
  "id": 2,
  "txid": null,
  "idUsuario": "user-1",
  "tipo": "ENVIO_CARTAO",
  "metodo": "CARTAO_CREDITO",
  "status": "FINALIZADA",
  "valorSolicitado": 100.00,
  "valorPago": 100.00,
  "dataCriacao": "2026-04-30T12:00:00",
  "dataExpiracao": null,
  "dataFinalizada": "2026-04-30T13:05:00"
}
```

---

### Respostas de Erro

| Status | Cenario | Exemplo |
|--------|---------|---------|
| `409 Conflict` | Lock indisponivel (cobranca em andamento para o usuario) | `{"erro": "Geracao de cobranca em andamento."}` |
| `404 Not Found` | Cobranca nao encontrada por ID | `{"erro": "Cobranca nao encontrada: 99"}` |
| `404 Not Found` | Cobranca nao encontrada por transactionId | `{"erro": "Cobranca nao encontrada para transactionId: xyz"}` |
| `500 Internal Server Error` | Erro inesperado na criacao | `{"erro": "Erro ao criar cobranca."}` |

## Arquitetura

```
com.ticketing.billing
├── controller/       # REST endpoints
├── service/          # Logica de negocio (CobrancaService, CobrancaEventPublisher)
│   └── strategy/     # Estrategias de criacao por metodo (PIX, Cartao)
├── repository/       # JPA repositories
├── domain/           # Entidades e enums
├── dto/              # Request/Response DTOs
├── integration/      # Clientes externos (mocks)
├── lock/             # Lock distribuido (Redis + fallback in-memory)
├── exception/        # Excecoes de negocio e handler global
└── config/           # Configuracoes (Redis, Kafka)
```

Para detalhes completos da especificacao, consulte [docs/project-plan.md](docs/project-plan.md).
