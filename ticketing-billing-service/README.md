# Ticketing Billing Service

Microserviço Spring Boot do **ticketing-billing-platform**.

> Documentação principal (como executar, premissas, trade-offs, exemplos de requests/responses): [../README.md](../README.md)

## Estrutura do Projeto

| Pacote | Responsabilidade                                                                                                                                                                   |
|--------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `controller` | Endpoints REST (`CobrancaController`)                                                                                                                                              |
| `service` | Logica de negócio (`CobrancaService`, `CobrancaEventPublisher`)                                                                                                                    |
| `service.strategy` | Estrategias de criacao por metodo: `PixCriacaoStrategy`, `CartaoCreditoCriacaoStrategy`, `CobrancaCriacaoStrategyRegistry`                                                         |
| `repository` | Acesso a dados (`CobrancaRepository` — JPA)                                                                                                                                        |
| `domain` | Entidade `Cobranca` e enums (`CobrancaTipoEnum`, `CobrancaMetodoEnum`, `CobrancaStatusEnum`)                                                                                       |
| `dto` | Objetos de transferência: `CobrancaRequestDTO`, `CobrancaBasicoResponseDTO`, `CobrancaCompletoResponseDTO`, `PixWebhookDTO`, `CheckoutValidationRequestDTO`, `CobrancaCriadaEvent` |
| `integration` | Clientes externos mockaveis: `PagamentoGatewayClient`, `CheckoutValidationClient`, `StatusConsultaExternaClient`, `UserContext`                                                    |
| `lock` | Lock distribuído: `LockService` (interface), `RedisLockService`, `InMemoryLockService`, `LockExecutor`                                                                             |
| `exception` | Exceções de negócio e `GlobalExceptionHandler`                                                                                                                                     |
| `config` | `RedisConfig`, `KafkaTopicConfig`                                                                                                                                                  |

## Infraestrutura (Docker Compose)

O `docker-compose.yml` neste diretório provisiona:

| Servico | Porta | Detalhes                                            |
|---------|-------|-----------------------------------------------------|
| PostgreSQL 16 | 5432 | DB: `brb_ticketing`, user/pass: `brasilia/brasilia` |
| Redis 7 | 6379 | Lock distribuído                                    |
| Kafka 4.1.1 | 9092 | Eventos de cobrança (tópico: `cobrancas.criada`)    |
| Kafka UI | 8084 | Interface web para visualização do cluster          |

## Comandos Úteis

```bash
# Infraestrutura
docker-compose up -d          # subir todos os serviços
docker-compose down            # parar (preserva dados)
docker-compose down -v         # parar e remover dados

# Build
./mvnw clean package           # Linux/macOS
mvnw.cmd clean package         # Windows

# Executar aplicação (porta 8080)
./mvnw spring-boot:run         # Linux/macOS
mvnw.cmd spring-boot:run       # Windows

# Testes
./mvnw test                    # todos os testes
./mvnw test -Dtest=CobrancaServiceTest          # classe especifica
./mvnw test -Dtest=CobrancaServiceTest#testName # metodo especifico
```

## Testes

### Unitários

| Classe | Cobertura                                                                                                                     |
|--------|-------------------------------------------------------------------------------------------------------------------------------|
| `CobrancaServiceTest` | Criação PIX/Cartão, lock indisponível, erro inesperado, webhook PIX (finaliza/ignora), checkout, consulta com reprocessamento |
| `LockExecutorTest` | Garante unlock no `finally` mesmo com exceção                                                                                 |

### Integração

| Classe | Escopo                                                                                   |
|--------|------------------------------------------------------------------------------------------|
| `CobrancaIntegrationTest` | Fluxo HTTP completo via MockMvc — criação, consulta, webhook PIX e validacao de checkout |

**Configuração de teste** (`application-test.yaml`):
- Banco: H2 em memória (`jdbc:h2:mem:testdb`)
- Redis e Kafka desabilitados (exclusão via auto-configuration)
- DDL: `create-drop` (schema limpo a cada execução)

### Cobertura

JaCoCo configurado com mínimo de **70% de cobertura de linhas** no pacote `service`.
