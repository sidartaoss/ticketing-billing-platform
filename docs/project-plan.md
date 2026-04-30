# Ticketing Billing Service — Planejamento Geral do Projeto

## 1. Visão Geral

Implementar do zero um microserviço simplificado de cobranças de bilhetagem:

- criação de cobrança com lock distribuído
- consulta de cobrança por id
- processamento de webhook PIX
- validação de checkout de cartão

## Stack Tecnológica

- **Stack principal:** Java 17+, Spring Boot 3, Maven, JPA/Hibernate, JUnit 5, Mockito
- **Stack complementar:** Redis (lock), Kafka (publicação de evento)

## Especificação Funcional

## 1) Modelo de Domínio

A entidade `Cobranca` deve conter os seguintes campos:

- `id` (Long)
- `idUsuario` (String)
- `nomeSolicitante` (String)
- `tipo` (enum)
- `metodo` (enum)
- `status` (enum)
- `valorSolicitacao` (BigDecimal)
- `valorPago` (BigDecimal, nullable)
- `txid` (String, nullable)
- `copiaECola` (String, nullable)
- `transactionId` (String, nullable)
- `acsUrl` (String, nullable)
- `threeDsPayload` (String, nullable)
- `dataCriacao` (LocalDateTime)
- `dataExpiracao` (LocalDateTime, nullable)
- `dataFinalizada` (LocalDateTime, nullable)

Enumerações do domínio:

- `CobrancaTipoEnum`: `RECARGA`, `RECARGA_TERCEIROS`, `ENVIO_CARTAO`
- `CobrancaMetodoEnum`: `PIX`, `CARTAO_CREDITO`
- `CobrancaStatusEnum` (codes):
  - `SOLICITADA(2)`
  - `EXPIRADA(3)`
  - `ERRO_APROVACAO_PEDIDO(4)`
  - `FINALIZADA(5)`
  - `EM_REPROCESSAMENTO(6)`
  - `ERRO_ANALISE_PENDENTE(9)`

## 2) Endpoints da API

Base path: `/api/v1/cobrancas`

### 2.1 POST `/api/v1/cobrancas`

Cria uma cobrança.

Request body:

```json
{
  "valor": 25.50,
  "tipo": "RECARGA",
  "metodo": "PIX"
}
```

Comportamento esperado:

- Extrair usuário autenticado de um `UserContext` (mockável), com:
  - `idUsuario`
  - `givenName`
  - `familyName`
  - `cpf`
- Aplicar lock por usuário na chave `cobrancas:{idUsuario}` com TTL 5 segundos.
- Criar cobrança com:
  - status inicial `SOLICITADA`
  - `nomeSolicitante = givenName + " " + familyName`
  - `dataCriacao = now()`
  - defaults:
    - `metodo = PIX` quando não informado
    - `tipo = RECARGA` quando não informado
- Executar estratégia de criação conforme método:
  - PIX: preencher `txid`, `copiaECola`, `dataExpiracao`
  - CARTAO_CREDITO: preencher `transactionId`; opcionalmente `acsUrl`/`threeDsPayload`
- Persistir e retornar `201 Created`.

Response body:

```json
{
  "id": 123,
  "txid": "abc123",
  "copiaECola": "000201...",
  "dataExpiracao": "2026-04-15T12:00:00"
}
```

### 2.2 GET `/api/v1/cobrancas/{id}`

Consulta uma cobrança por id.

Comportamento esperado:

- Retornar `404` se não existir.
- Se houver versão filha (reprocessamento), retornar a mais recente.
- Para cobranças PIX com status em `{2,3,4,6,9}`, permitir consulta de status externo (mockável) e, se mudou, persistir nova versão com referência da anterior.
- Retornar `200 OK` com payload detalhado.

Payload de resposta:

```json
{
  "id": 123,
  "txid": "abc123",
  "idUsuario": "user-1",
  "tipo": "RECARGA",
  "metodo": "PIX",
  "status": "SOLICITADA",
  "valorSolicitado": 25.50,
  "valorPago": null,
  "dataCriacao": "2026-04-15T10:00:00",
  "dataExpiracao": "2026-04-15T12:00:00",
  "dataFinalizada": null
}
```

### 2.3 POST `/api/v1/cobrancas/webhook/pix`

Recebe notificações PIX.

Request body:

```json
{
  "pix": [
    {
      "txid": "abc123",
      "horario": "2026-04-15T13:02:30Z",
      "valor": 25.50
    }
  ]
}
```

Comportamento esperado:

- Se payload nulo/vazio: ignorar com `200`.
- Para cada item:
  - se `txid` vazio: ignorar
  - buscar cobrança mais recente por `txid`
  - se não encontrada: ignorar
  - se já estiver `FINALIZADA`: ignorar
  - senão, criar nova versão com status `FINALIZADA`, `dataFinalizada` com timezone de São Paulo e `valorPago`.
- Retornar `200 OK`.

### 2.4 POST `/api/v1/cobrancas/{transactionId}/validate`

Valida checkout de cartão.

Request body (exemplo):

```json
{
  "cavv": "AAABBB",
  "xid": "XYZ",
  "eci": "05"
}
```

Comportamento esperado:

- Buscar cobrança por `transactionId`, senão `404`.
- Chamar client externo mockável de validação.
- Atualizar cobrança com dados de autorização retornados.
- Persistir alterações e retornar `200 OK`.

## Regras de Negócio

- Lock distribuído deve sempre liberar no `finally`.
- Falha de lock deve retornar erro de negócio `Geração de cobrança em andamento.`
- Erros inesperados na criação devem retornar erro de negócio `Erro ao criar cobrança.`
- Timezone oficial para datas de negócio: `America/Sao_Paulo`.

## Arquitetura do Projeto

Pacotes sugeridos:

- `controller`
- `service`
- `service.strategy`
- `repository`
- `domain` (entities/enums)
- `dto`
- `integration` (clients mockáveis)
- `lock`
- `exception`

Componentes principais:

- `CobrancaService`
- `CobrancaCriacaoStrategy` + implementações PIX/CARTAO
- `CobrancaCriacaoStrategyRegistry`
- `LockService` e `LockExecutor`
- `CobrancaRepository`
- `PixWebhookDTO`, `CobrancaRequestDTO`, `CobrancaBasicoResponseDTO`, `CobrancaCompletoResponseDTO`

## Estratégia de Testes

### Testes Unitários

Cenários a cobrir:

1. `criarCobranca` sucesso PIX.
2. `criarCobranca` com lock indisponível.
3. `criarCobranca` com exceção inesperada deve mapear para erro de negócio.
4. `processarNotificacaoWebhookPix` finalizando cobrança pendente.
5. `processarNotificacaoWebhookPix` ignorando cobrança já finalizada.
6. `validarCheckout` atualizando cobrança existente.
7. `LockExecutor` garante unlock no `finally` mesmo com exceção.

### Testes de Integração

- Fluxo `POST /cobrancas` -> `GET /cobrancas/{id}`.
- Fluxo `POST /webhook/pix` mudando status para `FINALIZADA`.

### Meta de Cobertura de Testes

- 70% no pacote `service`.

## Dados e Mocks Externos

Como o projeto é independente, os seguintes clientes podem ser fake/mock:

- `PagamentoGatewayClient`
- `PixWebhookProvider` (se desejar desacoplar)
- `CheckoutValidationClient`
- `StatusConsultaExternaClient`

O importante é o comportamento de negócio, não a integração real.

## Critérios de Qualidade

- Corretude das regras de negócio
- Qualidade de modelagem e separação de responsabilidades
- Robustez em concorrência (lock)
- Qualidade dos testes (valor, isolamento, legibilidade)
- Tratamento de erro e contrato HTTP
- Clareza do README

## Documentação Esperada

O repositório deve conter:

- Código-fonte
- Testes automatizados
- `README.md` com:
  - como executar
  - premissas adotadas
  - trade-offs
  - exemplos de requests/responses

## Checklist de Implementação

- [ ] `POST /api/v1/cobrancas` cria cobrança com lock por usuário
- [ ] `GET /api/v1/cobrancas/{id}` retorna payload detalhado
- [ ] `POST /api/v1/cobrancas/webhook/pix` finaliza cobrança por txid
- [ ] `POST /api/v1/cobrancas/{transactionId}/validate` atualiza checkout
- [ ] testes unitários implementados
- [ ] meta de cobertura atingida
