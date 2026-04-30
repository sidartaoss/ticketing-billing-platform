# Ticketing Billing Service

Microservico Spring Boot do **ticketing-billing-platform**.

> Documentacao principal (como executar, premissas, trade-offs, exemplos de requests/responses): [../README.md](../README.md)

## Estrutura do Projeto

| Pacote | Responsabilidade |
|--------|------------------|
| `controller` | Endpoints REST (`CobrancaController`) |
| `service` | Logica de negocio (`CobrancaService`, `CobrancaEventPublisher`) |
| `service.strategy` | Estrategias de criacao por metodo: `PixCriacaoStrategy`, `CartaoCreditoCriacaoStrategy`, `CobrancaCriacaoStrategyRegistry` |
| `repository` | Acesso a dados (`CobrancaRepository` — JPA) |
| `domain` | Entidade `Cobranca` e enums (`CobrancaTipoEnum`, `CobrancaMetodoEnum`, `CobrancaStatusEnum`) |
| `dto` | Objetos de transferencia: `CobrancaRequestDTO`, `CobrancaBasicoResponseDTO`, `CobrancaCompletoResponseDTO`, `PixWebhookDTO`, `CheckoutValidationRequestDTO`, `CobrancaCriadaEvent` |
| `integration` | Clientes externos mockaveis: `PagamentoGatewayClient`, `CheckoutValidationClient`, `StatusConsultaExternaClient`, `UserContext` |
| `lock` | Lock distribuido: `LockService` (interface), `RedisLockService`, `InMemoryLockService`, `LockExecutor` |
| `exception` | Excecoes de negocio e `GlobalExceptionHandler` |
| `config` | `RedisConfig`, `KafkaTopicConfig` |

## Infraestrutura (Docker Compose)

O `docker-compose.yml` neste diretorio provisiona:

| Servico | Porta | Detalhes |
|---------|-------|----------|
| PostgreSQL 16 | 5432 | DB: `brb_ticketing`, user/pass: `brasilia/brasilia` |
| Redis 7 | 6379 | Lock distribuido |
| Kafka 4.1.1 | 9092 | Eventos de cobranca (topico: `cobrancas.criada`) |
| Kafka UI | 8084 | Interface web para visualizacao do cluster |

## Comandos Uteis

```bash
# Infraestrutura
docker-compose up -d          # subir todos os servicos
docker-compose down            # parar (preserva dados)
docker-compose down -v         # parar e remover dados

# Build
./mvnw clean package           # Linux/macOS
mvnw.cmd clean package         # Windows

# Executar aplicacao (porta 8080)
./mvnw spring-boot:run         # Linux/macOS
mvnw.cmd spring-boot:run       # Windows

# Testes
./mvnw test                    # todos os testes
./mvnw test -Dtest=CobrancaServiceTest          # classe especifica
./mvnw test -Dtest=CobrancaServiceTest#testName # metodo especifico
```

## Testes

### Unitarios

| Classe | Cobertura |
|--------|-----------|
| `CobrancaServiceTest` | Criacao PIX/Cartao, lock indisponivel, erro inesperado, webhook PIX (finaliza/ignora), checkout, consulta com reprocessamento |
| `LockExecutorTest` | Garante unlock no `finally` mesmo com excecao |

### Integracao

| Classe | Escopo |
|--------|--------|
| `CobrancaIntegrationTest` | Fluxo HTTP completo via MockMvc — criacao, consulta, webhook PIX e validacao de checkout |

**Configuracao de teste** (`application-test.yaml`):
- Banco: H2 em memoria (`jdbc:h2:mem:testdb`)
- Redis e Kafka desabilitados (exclusao via auto-configuration)
- DDL: `create-drop` (schema limpo a cada execucao)

### Cobertura

JaCoCo configurado com minimo de **70% de cobertura de linhas** no pacote `service`.
