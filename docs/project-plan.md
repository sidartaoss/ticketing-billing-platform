# Ticketing Billing Service — Planejamento Geral do Projeto

## 1. Visão Geral

Implementar do zero um microservico simplificado de cobrancas de bilhetagem:

- criacao de cobranca com lock distribuido
- consulta de cobranca por id
- processamento de webhook PIX
- validacao de checkout de cartao

## Stack Tecnológica

- Stack obrigatoria: Java 17+, Spring Boot 3, Maven, JPA/Hibernate, JUnit 5, Mockito
- Stack recomendada: Redis (lock), Kafka (publicacao de evento)

## Contrato funcional obrigatorio

## 1) Dominio minimo

Implemente uma entidade `Cobranca` com os campos:

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

Enums obrigatorios:

- `CobrancaTipoEnum`: `RECARGA`, `RECARGA_TERCEIROS`, `ENVIO_CARTAO`
- `CobrancaMetodoEnum`: `PIX`, `CARTAO_CREDITO`
- `CobrancaStatusEnum` (codes):
  - `SOLICITADA(2)`
  - `EXPIRADA(3)`
  - `ERRO_APROVACAO_PEDIDO(4)`
  - `FINALIZADA(5)`
  - `EM_REPROCESSAMENTO(6)`
  - `ERRO_ANALISE_PENDENTE(9)`

## 2) Endpoints obrigatorios

Base path: `/api/v1/cobrancas`

### 2.1 POST `/api/v1/cobrancas`

Cria uma cobranca.

Request body (minimo):

```json
{
  "valor": 25.50,
  "tipo": "RECARGA",
  "metodo": "PIX"
}
```

Comportamento esperado:

- Extrair usuario autenticado de um `UserContext` (mockavel), com:
  - `idUsuario`
  - `givenName`
  - `familyName`
  - `cpf`
- Aplicar lock por usuario na chave `cobrancas:{idUsuario}` com TTL 5 segundos.
- Criar cobranca com:
  - status inicial `SOLICITADA`
  - `nomeSolicitante = givenName + " " + familyName`
  - `dataCriacao = now()`
  - defaults:
    - `metodo = PIX` quando nao informado
    - `tipo = RECARGA` quando nao informado
- Executar estrategia de criacao conforme metodo:
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

Consulta uma cobranca por id.

Comportamento esperado:

- Retornar `404` se nao existir.
- Se houver versao filha (reprocessamento), retornar a mais recente.
- Para cobrancas PIX com status em `{2,3,4,6,9}`, permitir consulta de status externo (mockavel) e, se mudou, persistir nova versao com referencia da anterior.
- Retornar `200 OK` com payload detalhado.

Payload minimo de resposta:

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

Recebe notificacoes PIX.

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
  - buscar cobranca mais recente por `txid`
  - se nao encontrada: ignorar
  - se ja estiver `FINALIZADA`: ignorar
  - senao, criar nova versao com status `FINALIZADA`, `dataFinalizada` com timezone de Sao Paulo e `valorPago`.
- Retornar `200 OK`.

### 2.4 POST `/api/v1/cobrancas/{transactionId}/validate`

Valida desafio de checkout cartao.

Request body (exemplo):

```json
{
  "cavv": "AAABBB",
  "xid": "XYZ",
  "eci": "05"
}
```

Comportamento esperado:

- Buscar cobranca por `transactionId`, senao `404`.
- Chamar client externo mockavel de validacao.
- Atualizar cobranca com dados de autorizacao retornados.
- Persistir alteracoes e retornar `200 OK`.

## Regras de negocio obrigatorias

- Lock distribuido deve sempre liberar no `finally`.
- Falha de lock deve retornar erro de negocio `Geracao de cobranca em andamento.`
- Erros inesperados na criacao devem retornar erro de negocio `Erro ao criar cobranca.`
- Timezone oficial para datas de negocio: `America/Sao_Paulo`.

## Arquitetura minima esperada

Pacotes sugeridos:

- `controller`
- `service`
- `service.strategy`
- `repository`
- `domain` (entities/enums)
- `dto`
- `integration` (clients mockaveis)
- `lock`
- `exception`

Componentes obrigatorios:

- `CobrancaService`
- `CobrancaCriacaoStrategy` + implementacoes PIX/CARTAO
- `CobrancaCriacaoStrategyRegistry`
- `LockService` e `LockExecutor`
- `CobrancaRepository`
- `PixWebhookDTO`, `CobrancaRequestDTO`, `CobrancaBasicoResponseDTO`, `CobrancaCompletoResponseDTO`

## Testes obrigatorios

## Unitarios (obrigatorio)

Cobrir no minimo:

1. `criarCobranca` sucesso PIX.
2. `criarCobranca` com lock indisponivel.
3. `criarCobranca` com excecao inesperada deve mapear para erro de negocio.
4. `processarNotificacaoWebhookPix` finalizando cobranca pendente.
5. `processarNotificacaoWebhookPix` ignorando cobranca ja finalizada.
6. `validarCheckout` atualizando cobranca existente.
7. `LockExecutor` garante unlock no `finally` mesmo com excecao.

## Integracao (diferencial)

- Fluxo `POST /cobrancas` -> `GET /cobrancas/{id}`.
- Fluxo `POST /webhook/pix` mudando status para `FINALIZADA`.

## Meta minima de cobertura

- 70% no pacote `service`.

## Dados e mocks externos

Como o projeto e independente, os seguintes clientes podem ser fake/mock:

- `PagamentoGatewayClient`
- `PixWebhookProvider` (se desejar desacoplar)
- `CheckoutValidationClient`
- `StatusConsultaExternaClient`

O importante e o comportamento de negocio, nao a integracao real.

## O que sera avaliado

- Corretude das regras de negocio
- Qualidade de modelagem e separacao de responsabilidades
- Robustez em concorrencia (lock)
- Qualidade dos testes (valor, isolamento, legibilidade)
- Tratamento de erro e contrato HTTP
- Clareza do README

## Entrega

Enviar link do repositorio contendo:

- Codigo-fonte
- Testes automatizados
- `README.md` com:
  - como executar
  - premissas adotadas
  - trade-offs
  - exemplos de requests/responses

## Checklist final do candidato

- [ ] `POST /api/v1/cobrancas` cria cobranca com lock por usuario
- [ ] `GET /api/v1/cobrancas/{id}` retorna payload detalhado
- [ ] `POST /api/v1/cobrancas/webhook/pix` finaliza cobranca por txid
- [ ] `POST /api/v1/cobrancas/{transactionId}/validate` atualiza checkout
- [ ] testes unitarios obrigatorios implementados
- [ ] cobertura minima atingida
