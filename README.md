# Ticketing Billing Platform

Microservico simplificado de cobranças de bilhetagem. Implementa criação de cobranças com lock distribuído, consulta por ID com versionamento, processamento de webhook PIX e validação de checkout de cartão de crédito.

## Stack Tecnológica

| Camada       | Tecnologia                                |
|--------------|-------------------------------------------|
| Linguagem    | Java 25                                   |
| Framework    | Spring Boot 4.0.6                         |
| Build        | Maven (wrapper incluso)                   |
| Persistência | JPA/Hibernate + PostgreSQL 16             |
| Lock         | Redis 7 (fallback in-memory)              |
| Mensageria   | Apache Kafka 4.1.1                        |
| Testes       | JUnit 5, Mockito, H2 (integracao)        |
| Cobertura    | JaCoCo (minimo 70% no pacote `service`)   |

## Como Executar

### Pré-requisitos

- JDK 25+
- Docker e Docker Compose

### 1. Subir infraestrutura (PostgreSQL, Redis, Kafka)

```bash
cd ticketing-billing-service
docker-compose up -d
```

Verificar se os serviços estao prontos:

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

### 3. Iniciar a aplicação

```bash
# Linux/macOS
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

A aplicação inicia em **http://localhost:8080**.

### 4. Rodar os testes

```bash
./mvnw test
```

Os testes utilizam H2 em memória e nao dependem de infraestrutura externa.

### 5. Parar o ambiente

```bash
# Parar aplicação: Ctrl+C no terminal

# Parar infraestrutura (preserva dados)
docker-compose down

# Parar e remover dados
docker-compose down -v
```

## Premissas Adotadas

- **Clientes externos sao mocks/fakes**: `PagamentoGatewayClient`, `CheckoutValidationClient`, `StatusConsultaExternaClient` simulam integracao. O foco e no comportamento de negocio, nao na integracao real.
- **UserContext mockavel**: simula usuário autenticado com `idUsuario`, `givenName`, `familyName` e `cpf`.
- **Lock distribuído dual-mode**: Redis em produção com `@ConditionalOnBean`; fallback automatico para `ConcurrentHashMap` in-memory quando Redis esta indisponivel (`@ConditionalOnMissingBean`).
- **Versionamento de cobranças**: cada mudança de status (reprocessamento, finalização via webhook) cria uma nova versao com referencia a anterior via `cobrancaPaiId`. A consulta retorna sempre a versao mais recente.
- **Timezone oficial**: `America/Sao_Paulo` para todas as datas de negócio.
- **PIX**: expiração de 30 minutos após criação.
- **Defaults**: `metodo = PIX` e `tipo = RECARGA` quando não informados no request.
- **Banco de testes**: H2 em memória com perfil `test`, sem dependência de Docker.

## Trade-offs

| Decisão                             | Benefício                                       | Custo                                                  |
|-------------------------------------|-------------------------------------------------|--------------------------------------------------------|
| **Redis lock vs. lock em banco**    | Baixa latência, operação atômica com TTL nativo | Exige Redis rodando; mitigado com fallback in-memory   |
| **Mocks de integração**             | Autonomia total, foco nas regras de negócio     | Não valida contrato real com serviços externos         |
| **Versionamento por novo registro** | Historico completo e auditável de cada cobrança | Maior volume de dados vs. update in-place              |
| **DDL auto-update (Hibernate)**     | Prático para desenvolvimento rapido             | Inadequado para produção — ideal usar Flyway/Liquibase |
| **Kafka para eventos**              | Desacopla consumidores, extensível              | Adiciona complexidade de infraestrutura                |
| **Strategy pattern (PIX/Cartão)**   | Extensível para novos métodos de pagamento      | Leve overhead para apenas 2 implementações             |

## Endpoints e Exemplos de Requests/Responses

Base path: `/api/v1/cobrancas`

---

### POST `/api/v1/cobrancas` — Criar Cobrança

Cria uma cobrança com lock distribuído por usuário (chave `cobrancas:{idUsuario}`, TTL 5s).

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

**Request (Cartão de Crédito):**

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

### GET `/api/v1/cobrancas/{id}` — Consultar Cobrança

Retorna a versão mais recente da cobrança. Para cobranças PIX com status pendente, consulta status externo e cria nova versão se houve mudança.

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

Recebe notificações de pagamento PIX. Para cada item com `txid` válido, finaliza a cobrança pendente criando nova versão com status `FINALIZADA`.

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

### POST `/api/v1/cobrancas/{transactionId}/validate` — Validar Checkout Cartão

Valida o desafio 3D Secure de uma cobrança de cartão de crédito.

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

| Status | Cenario                                                  | Exemplo                                                       |
|--------|----------------------------------------------------------|---------------------------------------------------------------|
| `409 Conflict` | Lock indisponível (cobrança em andamento para o usuário) | `{"erro": "Geração de cobrança em andamento."}`               |
| `404 Not Found` | Cobrança não encontrada por ID                           | `{"erro": "Cobrança não encontrada: 99"}`                     |
| `404 Not Found` | Cobrança não encontrada por transactionId                | `{"erro": "Cobrança não encontrada para transactionId: xyz"}` |
| `500 Internal Server Error` | Erro inesperado na criação                               | `{"erro": "Erro ao criar cobrança."}`                         |

## Arquitetura

```
com.ticketing.billing
├── controller/       # REST endpoints
├── service/          # Logica de negócio (CobrancaService, CobrancaEventPublisher)
│   └── strategy/     # Estratégias de criação por método (PIX, Cartão)
├── repository/       # JPA repositories
├── domain/           # Entidades e enums
├── dto/              # Request/Response DTOs
├── integration/      # Clientes externos (mocks)
├── lock/             # Lock distribuído (Redis + fallback in-memory)
├── exception/        # Excecoes de negócio e handler global
└── config/           # Configurações (Redis, Kafka)
```

Para detalhes completos da especificação, consulte [docs/project-plan.md](docs/project-plan.md).
