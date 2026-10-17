# Plano de Implementação: Abertura completa de Service Order

| Campo | Valor |
|---|---|
| Feature | `service-order-full-intake` |
| Status | In Progress |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-10-16 |
| Especificação funcional | `./functional-spec.md` (`Approved` em 2026-10-16) |
| Especificação técnica | `./technical-spec.md` (`Approved` em 2026-10-16) |

## Objetivo da execução

Entregar `POST /api/service-orders/intake`, conforme as specs aprovadas: identificar-ou-cadastrar Customer e Vehicle,
abrir a Service Order e, se houver serviços, registrar o Diagnosis com preços cotados nos cadastros e gerar o Estimate,
tudo numa única transação. Nenhum endpoint existente muda de contrato ou de comportamento.

## Regras de condução

- Ler `AGENTS.md`, as duas specs aprovadas e `.claude/rules/epic-3-service-lifecycle.md` antes de alterar código.
- Executar um checkpoint por vez, com no máximo um `In Progress`, e atualizar o status aqui (`Pending`,
  `In Progress`, `Completed`).
- Rodar os testes indicados antes de concluir cada checkpoint e registrar a evidência.
- Não importar pacotes internos de outro módulo; só `application/api` (ADR-005).
- Não alterar a assinatura nem o comportamento de `CreateServiceOrderUseCase`, `AssignDiagnosisAssigneeUseCase`,
  `PerformDiagnosisUseCase`, `GenerateEstimateUseCase`, `CreateCustomerUseCase` ou `CreateVehicleUseCase`. Se
  for preciso extrair lógica compartilhada em registration (ex.: montagem do Customer/Vehicle), a extração não pode
  mudar o comportamento observável dos casos de uso atuais.
- Interromper e devolver as specs a `Draft` se surgir decisão material diferente da aprovada.
- Git: nenhum commit sem pedido explícito do responsável. Ao fim de cada checkpoint, apenas sugerir a mensagem.

## Estado dos checkpoints

| Ordem | Checkpoint | Status |
|---:|---|---|
| 0 | Preparar a execução | Completed |
| 1 | APIs públicas de registration (Customer e Vehicle intake) | Completed |
| 2 | APIs de cotação (Catalog Service e Stock Item) | Pending |
| 3 | Portas, adapters e exceções em `servicelifecycle` | Pending |
| 4 | Caso de uso orquestrador | Pending |
| 5 | Endpoint HTTP, autorização e tradução de falhas | Pending |
| 6 | Testes de integração ponta a ponta e atomicidade | Pending |
| 7 | OpenAPI, Postman e README | Pending |
| 8 | Revisão de segurança e gates finais | Pending |

## Checkpoint 0 — Preparar a execução

### Alterações

- Confirmar a branch `feat/terraform-san` e um `git status --short` sem mudanças inesperadas.
- Rodar a suíte antes de qualquer mudança, para ter a linha de base.

### Verificação

- `./mvnw test` verde (registrar o número de testes e o tempo).

## Checkpoint 1 — APIs públicas de registration

### Alterações

- `registration/customer/application/api/`: `CustomerIntakeApi`, `CustomerIntakeCommand`, `CustomerIntakeResult`,
  `CustomerIntakeOutcome` e `package-info.java` com `@NamedInterface("customer-intake-api")`.
- Implementação em `registration/customer/application/usecase/` (ex.: `IdentifyOrRegisterCustomerUseCase`), com
  `@Transactional(propagation = MANDATORY)`.
- `registration/vehicle/application/api/`: `VehicleIntakeApi`, `VehicleIntakeCommand`, `VehicleIntakeResult` e
  `VehicleIntakeOutcome`.
- `VehicleRepository.findByLicensePlateForUpdate(LicensePlate)`, com a implementação JPA (`PESSIMISTIC_WRITE`).
- Implementação em `registration/vehicle/application/usecase/` (ex.: `IdentifyOrRegisterVehicleUseCase`).

### Verificação

- Testes unitários: Customer `REUSED` (sem alterar nome nem contato), `ARCHIVED`, `CREATED` e dados ausentes para
  cadastro novo; Vehicle `REUSED`, `OWNED_BY_ANOTHER_CUSTOMER`, `ARCHIVED`, `CREATED` e chassi duplicado.
- Teste de persistência para `findByLicensePlateForUpdate` contra o banco de teste.
- `ModuleStructureTest` verde.

### Commit sugerido

`feat(registration): add customer and vehicle intake public APIs`

## Checkpoint 2 — APIs de cotação

### Alterações

- `registration/servicecatalog/application/api/`: `CatalogServiceQuoteApi` e `CatalogServiceQuote`, com a
  implementação no módulo.
- `stockprocurement/stock/application/api/`: `StockItemQuoteApi`, `StockItemQuote` e `package-info.java` com
  `@NamedInterface("stock-item-quote-api")`, com a implementação no módulo.

### Verificação

- Testes unitários: Catalog Service ativo, arquivado e inexistente; Stock Item ativo, inativo e inexistente (ausente
  da lista).
- `ModuleStructureTest` verde.

### Commit sugerido

`feat(registration): add catalog service and stock item quote APIs`. Como o commit toca dois módulos, uma alternativa
é separá-lo em dois commits, um por módulo, com os escopos `registration` e `stockprocurement`.

## Checkpoint 3 — Portas, adapters e exceções em `servicelifecycle`

### Alterações

- Portas em `servicelifecycle/serviceorder/application/port/`: `CustomerIntakePort`, `VehicleIntakePort`,
  `CatalogServiceQuotePort` e `StockItemQuotePort`, com os tipos de resultado próprios do módulo.
- Adapters em `infrastructure/registration/` e no novo `infrastructure/stock/`, traduzindo outcomes e
  `IllegalArgumentException` para tipos e exceções de `servicelifecycle`.
- Exceções novas em `application/exception/`: `ServiceOrderIntakeInvalidDataException`,
  `ServiceOrderIntakeCustomerArchivedException`, `ServiceOrderIntakeVehicleOwnershipConflictException`,
  `ServiceOrderIntakeVehicleChassisConflictException` e `ServiceOrderStockItemInactiveException`.

### Verificação

- Testes unitários dos adapters, um por outcome traduzido.
- `ModuleStructureTest` verde. É aqui que o Modulith reprovaria um import interno indevido.

### Commit sugerido

`feat(servicelifecycle): add intake ports and registration/stock adapters`

## Checkpoint 4 — Caso de uso orquestrador

### Alterações

- DTOs em `application/dto/`: `OpenServiceOrderIntakeRequest` (e os records aninhados) e
  `ServiceOrderIntakeResponse`.
- `OpenServiceOrderIntakeUseCase` com `@Transactional`, na ordem da technical-spec (D1).

### Verificação

- `OpenServiceOrderIntakeUseCaseTest` (dublês para portas e casos de uso): ordem das chamadas; sem serviços → sem
  atribuição, sem diagnóstico e sem Estimate; serviços sem técnico → `ServiceOrderIntakeInvalidDataException`; nome e
  preço do request de diagnóstico vindos da cotação; Catalog Service arquivado e inexistente; Stock Item inativo e
  inexistente; snapshot montado a partir do resultado do Vehicle.

### Commit sugerido

`feat(servicelifecycle): add service order full intake use case`

## Checkpoint 5 — Endpoint HTTP, autorização e tradução de falhas

### Alterações

- `ServiceOrderController`: `POST /intake` → `201` com `Location`, anotado com `@Operation` e `@ApiResponses`.
- `ServiceLifecycleExceptionHandler`: handlers das exceções novas e de `DataIntegrityViolationException` →
  `409 CONCURRENT_REGISTRATION`, com mensagem genérica.
- `identity/SecurityConfig`: regra `POST /api/service-orders/intake` para `MANAGER` e `ADMIN`, declarada antes da regra
  geral de `/api/service-orders/**`.

### Verificação

- MockMvc: 201; 400 (Bean Validation e dados de domínio); 401 sem token; 403 com `TECHNICIAN`; mapeamento de cada 404 e
  409 da tabela da technical-spec.
- Regressão: `ServiceOrderControllerCreateTest` e os testes de segurança existentes passam sem alteração.

### Commit sugerido

`feat(servicelifecycle): expose service order full intake endpoint`

## Checkpoint 6 — Testes de integração ponta a ponta e atomicidade

### Alterações

- `ServiceOrderControllerIntakeTest`, contra o banco de teste, com fixtures próprias (Catalog Service, Stock Item,
  Technician).

### Verificação

Um teste por critério de aceite da functional-spec, incluindo:

- Customer e Vehicle novos com serviços → `AWAITING_APPROVAL`, Estimate gravado, preços iguais aos cadastros mesmo com
  `price` arbitrário enviado no JSON;
- reaproveitamento sem alterar o cadastro;
- abertura sem serviços → `RECEIVED`;
- **atomicidade:** Customer e Vehicle novos + Catalog Service arquivado → `409` e as contagens de `customers`,
  `vehicles`, `service_orders` e `estimates` iguais às de antes;
- falta de estoque → sucesso, com indisponibilidade registrada na Service Execution;
- `EstimateGenerated` entregue ao listener só em sucesso.

### Commit sugerido

`test(servicelifecycle): cover service order full intake end to end`

## Checkpoint 7 — OpenAPI, Postman e README

### Alterações

- Conferir no `/v3/api-docs` o endpoint novo com os códigos documentados.
- Postman: request "Abrir OS completa" (com e sem serviços), com script que captura `serviceOrderId` e `estimateId`.
- README: seção de teste manual (pré-requisitos, ordem das requests, variáveis e resultados esperados).

### Verificação

- Execução manual pelo responsável, com a aplicação rodando localmente: os dois exemplos do Postman retornam `201` e o
  `GET /api/estimates/{estimateId}` mostra os totais.

### Commit sugerido

`docs(servicelifecycle): document full intake in openapi, postman and readme`

## Checkpoint 8 — Revisão de segurança e gates finais

### Alterações

- Registrar abaixo a revisão de segurança final, item por item (a prévia está na technical-spec).
- Registrar o achado separado: `StockItemInactiveException` não é mapeado no endpoint de Diagnosis atual (possível
  `500`). Fica fora desta feature, para uma correção própria.
- Atualizar o status das specs e deste plano para `Implemented` só depois de todos os gates passarem.

### Verificação

- `make verify` verde, sem testes ignorados.
- `make coverage`: ≥ 80% no total, sem reduzir a cobertura do código alterado.
- `ModuleStructureTest` verde.

## Revisão de segurança

| Item | Resultado | Mitigação / evidência |
|---|---|---|
| Validação de entrada e mass assignment | Pendente | |
| Autenticação e autorização | Pendente | |
| Exposição de dados de Customer/Vehicle | Pendente | |
| Segredos e logs sensíveis | Pendente | |
| SQL, persistência e migrations | N/A para migrations (sem schema novo); persistência pendente | |
| Respostas de erro e vazamento de informação | Pendente | |
| Dependências novas | N/A: nenhuma dependência nova | |
| Abuso do endpoint (volume, concorrência, manipulação de preço) | Pendente | |

## Evidências de verificação

Registrar aqui, por checkpoint, os comandos executados, os resultados (número de testes, cobertura) e as verificações
manuais.

### CP0 — 2026-10-16

- Branch `feat/terraform-san` em `66b8f10`; `git status --short` só com os arquivos locais não versionados
  (`contexto-proxima-sessao.md`, `teste.md`).
- `./mvnw -q test`: exit 0 em ~122 s. Surefire: 168 suítes, **828 testes, 0 falhas, 0 erros, 0 ignorados**.
- Banco de teste: H2 em memória (`MODE=MySQL`); não depende do Docker.

### CP1 — 2026-10-16

- Código: `customer/application/api` (nova named interface `customer-intake-api`),
  `IdentifyOrRegisterCustomerUseCase`, `VehicleIntake*` no pacote `vehicle/application/api` existente,
  `IdentifyOrRegisterVehicleUseCase` e `VehicleRepository.findByLicensePlateForUpdate` (JPA com `PESSIMISTIC_WRITE`).
- Testes novos: `IdentifyOrRegisterCustomerUseCaseTest` (7), `IdentifyOrRegisterVehicleUseCaseTest` (10) e
  `RegistrationIntakeApiIntegrationTest` (4, contra H2: exige transação, cadastra e reaproveita, placa de outro dono e
  **rollback do consumidor descarta Customer e Vehicle criados na mesma transação**). A busca por placa com lock é
  exercitada nesse teste de integração.
- `ModuleStructureTest`: 2/2 verdes.
- `./mvnw -q test`: 171 suítes, **849 testes** (828 + 21), 0 falhas, 0 erros, 0 ignorados.
- Uma falha intermediária (colisão da sequência de CPF de teste com `ServiceOrderControllerCreateTest`, ambos em
  `940_000_000`) foi corrigida movendo o novo teste para a faixa livre `960_000_000`.
- Refinamentos não materiais em relação à technical-spec, sem efeito no contrato HTTP:
  - `CustomerRepository.save` e `VehicleRepository.save` usam `saveAndFlush` e já traduzem a violação de unicidade no
    próprio `save`. Por isso a concorrência chega como `CustomerIntakeConflictException`/`VehicleIntakeConflictException`
    (públicas, no pacote `api`), e não como `DataIntegrityViolationException` no commit. O resultado HTTP previsto
    (`409 CONCURRENT_REGISTRATION`) não muda; o mapeamento fica no CP3/CP5.
  - O chassi duplicado em Vehicle novo é um outcome (`CHASSIS_ALREADY_REGISTERED`), e não uma exceção, como os demais
    casos de rejeição. O resultado HTTP previsto (`409 VEHICLE_CHASSIS_ALREADY_EXISTS`) não muda.
  - Customer inexistente ou inativo ao cadastrar o Vehicle (só possível com arquivamento concorrente entre os dois
    passos) lança `IllegalStateException`, mapeada hoje em `servicelifecycle` como `409 INVALID_STATE_TRANSITION`.

## Rollback ou recuperação

Mudança aditiva, sem migration: reverter os commits da feature remove o endpoint, sem efeito em dados existentes nem em
contratos atuais. Registros criados pelo endpoint antes do rollback continuam válidos, porque são Customers, Vehicles,
Service Orders e Estimates comuns.
