# Plano de Implementação: Infraestrutura Kubernetes, IaC e entrega contínua

| Campo | Valor |
|---|---|
| Feature | `kubernetes-infrastructure-delivery` |
| Status | Approved |
| Responsável | Santiago Silvestre |
| Atualizado em | 2026-10-17 |
| Aprovado por | Santiago Silvestre |
| Aprovado em | 2026-10-14 |
| Especificação técnica | `./technical-spec.md` (Approved, Santiago Silvestre, 2026-10-14) |

## Premissas

- Branch `feat/terraform-san`. Cada checkpoint termina com uma mensagem de commit **sugerida**; o commit só acontece
  com pedido explícito.
- O Santiago executa os comandos principais (`docker build/compose`, `terraform`, `kubectl apply`, k6, observação do
  HPA) e cola as saídas; as evidências são registradas aqui.
- A abertura de OS completa (passo 3) é uma feature própria em `docs/features/servicelifecycle/` e não faz parte deste
  plano. Ela só precisa estar pronta antes do vídeo (CP7).
- Ao fim de cada checkpoint, o `docs/learning/glossario.md` é atualizado com os conceitos novos.

## Pré-requisitos da máquina local (verificar no CP4)

| Item | Exigência | Verificação |
|---|---|---|
| Docker Desktop (WSL2) | rodando, com ≥ 6 GiB de memória e ≥ 4 CPUs | `docker info --format '{{.MemTotal}} {{.NCPU}}'` |
| Terraform | ≥ 1.10 | `terraform version` |
| kubectl | já instalado (Docker Desktop) | `kubectl version --client` |
| kind / helm / k6 | não precisam ser instalados: o provider `tehcyx/kind` e o provider `helm` embutem as bibliotecas, e o k6 roda por container | — |

## Checkpoints

### CP1 — Container (passo 4, previsto 17/10)

**Status: Completed (2026-10-17).**

- [x] `Dockerfile`: remover `wait-for-mysql.sh` e a instalação de pacotes; `USER 1000`;
      `ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"`; `ENTRYPOINT ["java", "-jar", "/app/app.jar"]`.
- [x] Apagar `docker/wait-for-mysql.sh`.
- [x] `docker-compose.yml`: contrato D1 (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`); remover `DDL_AUTO`/`SHOW_SQL`;
      repassar `SUPPLIER_API_KEY`; Mailpit com tag fixa (`v1.31.4`). O healthcheck do app em
      `/actuator/health/readiness` foi movido para o CP2, quando o endpoint existir (`curl` já existe na imagem base).
- [x] `.env.example`: remover `JAVA_OPTS`, `DDL_AUTO` e `SHOW_SQL`; documentar `SUPPLIER_API_KEY`.
- [x] `application.properties`: linha duplicada `app.seed.enabled` — **N/A**: na leitura de 2026-10-17 o arquivo tem
      só `app.seed.enabled=false`, e o `application-dev.properties` reativa por `APP_SEED_ENABLED` (comportamento
      esperado). Nada a remover.
- [x] Atualizar as referências do README (e do `DOCKER.md`) ao compose e ao `wait-for-mysql.sh`.
- [x] Extra (achado da abertura de OS, sessão 4): `.gitattributes` com `*.sh text eol=lf`.

Verificação: `docker compose up -d --build` → API responde em `localhost:8080`; `docker compose exec app id -u` = `1000`;
`docker image inspect` sem segredos em `Env`; `make verify` verde.

Commit sugerido: `refactor(platform): align container image and compose with env var contract`

### CP2 — Saúde (passo 5, previsto 17/10)

**Status: Completed (2026-10-17).**

- [x] `pom.xml`: `spring-boot-starter-actuator`.
- [x] Propriedades `management.*` da technical-spec. **Desvio de implementação:** ficam em
      `src/main/resources/actuator.properties`, importado por `spring.config.import` tanto pelo `application.properties`
      principal quanto pelo de testes. O de testes tem o mesmo nome e sombreia o principal; sem o import, os testes
      validariam uma cópia e não detectariam, por exemplo, a remoção de `show-details=never`. As propriedades e os
      valores são os da technical-spec.
- [x] `SecurityConfig`: `permitAll()` só para `GET` nos três caminhos de health.
- [x] Testes MockMvc (`HealthEndpointTest`, `HealthEndpointDatabaseDownTest`): os três caminhos `200 UP` sem token e
      sem `components`/`details`; `POST` → `401`; `/actuator/env` e `/actuator/beans` → `401` sem token e `404` com
      token de `ADMIN` (não expostos); com o indicador `dbHealthIndicator` em `DOWN`, readiness `503` e liveness `200`.
      Mutação manual (readiness sem `db` + `show-components=always`) → 3 testes vermelhos, depois restaurado.
- [x] Healthcheck do app no compose (`curl -fsS` na readiness; `start_period: 180s`).
- [x] Postman: pasta "Health" (`Liveness probe`, `Readiness probe`), primeiros passos do `make e2e`; README com a
      seção "Verificação de saúde" (e remoção das mensagens do antigo `wait-for-mysql.sh`, que ficaram do CP1).

Verificação: `make verify` verde (incluindo `ModuleStructureTest` e JaCoCo ≥ 80%); `docker compose ps` mostra o app
`healthy`; `docker compose stop mysql` → readiness `503` e liveness `200`; `docker compose start mysql` → readiness
volta para `200`.

Commit sugerido: `feat(platform): expose liveness and readiness health probes`

### CP3 — `.gitignore` (passo 6, previsto 17/10)

**Status: Completed (2026-10-17).**

- [x] Ignorar `**/.terraform/`, `*.tfstate`, `*.tfstate.*`, `*.tfvars` (exceto `*.tfvars.example`), `crash.log`,
      `kubeconfig*` e `k8s/overlays/*/secrets.env`. Extras: `*.tfplan` (plano salvo contém os mesmos valores
      sensíveis do state) e `crash.*.log`.
- [x] **Não** ignorar `.terraform.lock.hcl`.
- **Nota para o CP4:** o provider `tehcyx/kind` grava, por padrão, o kubeconfig como `<nome-do-cluster>-config` na
  pasta da stack, nome que **não** casa com `kubeconfig*` (verificado com o arquivo fictício `local-kind-config`).
  No CP4, definir `kubeconfig_path` explicitamente com um nome que comece por `kubeconfig`.

Verificação: criar arquivos fictícios (`infra/local/cluster/terraform.tfstate`, `k8s/overlays/local/secrets.env`) e
confirmar com `git status --ignored` / `git check-ignore -v` que ficam ignorados; depois apagá-los.

Commit sugerido: `chore(platform): ignore terraform state, tfvars, kubeconfig and k8s secrets`

### CP4 — `infra/local` (passo 7, previsto 19/10)

**Status: Completed (2026-10-17, adiantado).**

- [x] Verificar os pré-requisitos da máquina e registrar a saída. O Docker tinha 2,84 GiB/2 CPUs, limitado pelo
      `~/.wslconfig`; ajustado para `memory=7GB`, `processors=5`, `swap=2GB` e `autoMemoryReclaim=gradual` (máquina
      com 16 GB/12 CPUs lógicas).
- [x] Consultar o Terraform Registry e fixar as versões (2026-10-17): `tehcyx/kind ~> 0.11.0`,
      `hashicorp/helm ~> 3.3.0`, `hashicorp/kubernetes ~> 3.3.0`, `hashicorp/random ~> 3.9.0` e chart
      `metrics-server` `3.14.0` (app `0.9.0`). Node `kindest/node:v1.35.0` fixado por digest (imagem padrão do kind
      v0.31.0, embutido no provider).
- [x] `infra/local/cluster`: cluster `workshop` (1 node, `extraPortMappings` 30080→8080 e 30025→8025) e
      metrics-server com `--kubelet-insecure-tls`; outputs. **Desvio da nota do CP3:** o kubeconfig é mesclado em
      `~/.kube/config` (fora do repositório) em vez de um arquivo `kubeconfig*` na stack, de modo que nenhuma
      credencial nasce dentro do projeto; o `destroy` remove o contexto (comportamento lido no código do provider).
- [x] `infra/local/database`: namespace `workshop`, `random_password`, Secret `mysql-root`, ConfigMap/Secret
      `workshop-db`, StatefulSet `mysql` com PVC de 1 Gi e Service `mysql`. Readiness por `mysqladmin ping` em
      `127.0.0.1` e, de propósito, sem liveness (a primeira inicialização leva ~95 s).
- [x] `.terraform.lock.hcl` das duas stacks versionados, com checksums de `windows_amd64` e `linux_amd64`
      (`terraform providers lock`), para a pipeline (CP6) conferir os mesmos binários.
- [x] Makefile: `infra-local-up` e `infra-local-down`.

Verificação (Santiago executa): `terraform init/plan/apply` nas duas stacks, em ordem; `kubectl get nodes` → `Ready`;
`kubectl top nodes` responde (metrics-server ok); `kubectl get pods,pvc -n workshop` → `mysql-0` `Running` e PVC
`Bound`; `terraform destroy` (database, depois cluster) remove tudo e `docker ps` não mostra mais o node do kind.

Commit sugerido: `feat(platform): provision local kind cluster, metrics-server and mysql with terraform`

### CP5 — Manifestos e HPA (passo 8, previsto 20/10)

- [ ] `k8s/base`: Deployment, Service, ConfigMap, HPA, Mailpit e WireMock, conforme a technical-spec.
- [ ] `k8s/overlays/local`: NodePort 30080/30025, HPA 1–3, perfil `dev` com seeds, imagem
      `workshop-management-system:local`, `secretGenerator` e `secrets.env.example`.
- [ ] Makefile: `k8s-local-deploy` (build `:local`, `kind load`, ConfigMap do WireMock a partir de
      `docker/wiremock/mappings`, `kubectl apply -k`), `k8s-local-delete` e `load-test`.
- [ ] `load-test/hpa-load.js` (k6) com limiares `http_req_failed < 1%` e `p(95) < 2s`.

Verificação (Santiago executa e registra):

| Critério | Comando / ação | Esperado |
|---|---|---|
| Deploy pronto | `kubectl rollout status deployment/workshop-app -n workshop` | `successfully rolled out` |
| Migrations | log do app | Flyway aplicou as migrations; sem erro de `validate` |
| Postman | collection contra `http://localhost:8080` | fluxo do README passa |
| ConfigMap/Secret | `kubectl describe deployment workshop-app -n workshop` | `envFrom` com ConfigMaps e Secrets; valores sensíveis não exibidos |
| Recriação de pod | `kubectl delete pod <app>` | pod novo criado automaticamente |
| Dados persistem | criar cliente, `kubectl delete pod mysql-0`, consultar de novo | cliente continua existindo |
| Atualização sem queda | `kubectl set env deployment/workshop-app DB_URL=jdbc:mysql://inexistente:3306/x` | pod novo nunca fica pronto e o antigo continua respondendo; depois `kubectl rollout undo` |
| Escala sobe | k6 + `kubectl get hpa -n workshop -w` | réplicas > 1 e ≤ 3 |
| Escala desce | aguardar sem carga | réplicas voltam a 1 (se a memória segurar, ajustar alvo/request e registrar aqui) |
| Mesmo endereço | k6 apontado só para `localhost:8080` | sem troca de URL durante a escala |

Commit sugerido: `feat(platform): add kustomize manifests, hpa and k6 load test for local cluster`

### CP6 — Pipeline com kind (passo 9, previsto 21/10)

- [ ] Criar o fork pessoal; environment `kind-ci` com `APP_SECURITY_JWT_SECRET` e
      `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET` (valores aleatórios novos).
- [ ] `.github/workflows/cd.yml` com jobs `build-test` → `image` → `deploy-kind`, gatilho só em `feat/terraform-san`
      (um único ponto), `REGISTRY`/`IMAGE_NAME` em `env:`, actions fixadas por SHA e smoke test.
- [ ] Tornar o pacote GHCR público depois do primeiro push (D9).
- [ ] README: onde fica o nome da branch no workflow e como trocá-lo.

Verificação: execução verde no fork, com link registrado e duração total ≤ 40 min; commit temporário com teste
quebrado → `image` e `deploy-kind` não rodam (depois reverter); push em outra branch → `cd.yml` não dispara; logs sem
segredos (procurar pelos valores); `ci.yml` intacto (`git diff` vazio no arquivo).

Commit sugerido: `feat(platform): add delivery pipeline with ephemeral kind cluster and smoke test`

### CP7 — README local e vídeo de garantia (passo 10, previsto 22/10)

- [ ] README: objetivos da fase, diagrama mermaid (componentes, infra e fluxo de deploy), execução local com compose,
      provisionamento com Terraform, deploy no kind, teste de carga, recursos criados por stack, pipeline e link para
      a collection.
- [ ] Postman/README: fluxo de teste manual atualizado (regra do `AGENTS.md`).
- [ ] Gravar o vídeo local: deploy, pipeline, consumo das APIs e HPA escalando.

Verificação: seguir o README do zero em um cluster novo (`infra-local-down` → `up` → deploy) sem passo implícito.

Commit sugerido: `docs(platform): document local kubernetes environment and delivery pipeline`

### CP8 — AWS Academy (passo 11, previsto 23–24/10; data-limite 24/10, 23h)

- [ ] **Antes de qualquer código:** confirmar região, `LabRole`, tipos de instância, disponibilidade de EKS/NAT e
      duração da sessão; marcar o item pendente da ADR-008. Se o desenho mudar, a technical-spec volta a `Draft`.
- [ ] `infra/aws/bootstrap`, `infra/aws/cluster` e `infra/aws/database`, conforme a technical-spec.
- [ ] `k8s/overlays/aws`: LoadBalancer com `loadBalancerSourceRanges`, HPA 2–4, sem seeds.
- [ ] Environment `aws-academy` e `.github/workflows/cd-aws.yml` (`workflow_dispatch`).
- [ ] Apresentar e registrar a estimativa de crédito por hora **antes** do primeiro `apply`.
- [ ] README: renovação de credenciais por sessão, ordem de `apply`/`destroy` e lista de recursos.

Verificação: `apply` das três stacks; workflow AWS verde; API responde pelo hostname do load balancer apenas a partir
do IP liberado; HPA escala com k6; credencial expirada → falha em `aws sts get-caller-identity`; `destroy`
(database → cluster) e conferência no console de que não sobrou EKS, NAT, ELB, RDS nem EIP.

Commit sugerido: `feat(platform): provision aws eks and rds with terraform and add aws deploy workflow`

### CP9 — Entrega final (passo 12, previsto 26/10, freeze às 12h)

- [ ] README final, vídeo final e PDF.
- [ ] Marcar a feature como `Implemented` só se todos os gates passarem.

## Revisão de segurança

A preencher durante a implementação. Linha de base (achados da technical-spec):

| Item | Achado | Severidade | Mitigação | Status |
|---|---|---|---|---|
| Validação de entrada / mass assignment | Nenhum endpoint de negócio muda | N/A | — | N/A |
| Autenticação e autorização | Health sem token, só `GET` em três caminhos | Baixa | sem detalhes/componentes; `POST` → 401; `/actuator/env` e `/beans` → 401 sem token e 404 com ADMIN (`HealthEndpointTest`; `curl` manual → 401) | Resolvido (CP2) |
| Exposição de dados | Admin de bootstrap `admin/changeme123` exposto por endpoint público na AWS | **Alta** | `loadBalancerSourceRanges` com o IP do desenvolvedor e `destroy` após cada uso | Pendente (CP8) |
| Segredos e logs | Secrets por environment; `secrets.env` não versionado; token mascarado | Média | `.gitignore` (CP3), `umask 077`, `::add-mask::`, revisão dos logs | Pendente |
| Persistência e migrations | Nenhuma migration nova; state com senha do banco | Média | state local fora do git; S3 criptografado e privado | Pendente |
| Respostas de erro | Health não expõe detalhes | Baixa | `show-details`/`show-components=never` em `actuator.properties`, importado também pelos testes; banco fora → só `{"status":"DOWN"}` | Resolvido (CP2) |
| Dependências | Actuator (BOM do Boot), imagens com tag fixa, actions por SHA | Baixa | fixar versões | Pendente |
| Abuso de endpoints | Health pode ser chamado em massa | Baixa | resposta barata; sem cache; fora de escopo limitar taxa | Pendente |
| Container | Não-root numérico, FS somente leitura, sem capabilities | — | `securityContext` | Pendente (CP5) |
| `--kubelet-insecure-tls` | TLS do kubelet não verificado | Baixa | só no kind descartável (`infra/local/cluster/main.tf`); EKS usa add-on | Resolvido (CP4) |
| Credenciais do kind no plano | O provider `tehcyx/kind` não marca `client_key`/`kubeconfig` como sensíveis: `plan`/`destroy` imprimem a chave privada de admin do cluster (observado no CP4) | Baixa | API server só em `127.0.0.1`, cluster descartável (a chave morre no `destroy`); kubeconfig fora do repositório; não colar saídas de `destroy` em canais públicos; no CP6, o cluster vive só no runner efêmero, mas os logs do job ficam visíveis no fork: revisar se o `plan`/`destroy` do kind deve rodar com saída filtrada | Aceito (local); revisar no CP6 |
| Senhas do MySQL local | Geradas por `random_password` (24 caracteres), entregues por Secret; nunca aparecem no plano, só no state | Baixa | state fora do git (CP3); `describe` mostra só a origem (`secret_key_ref`); MySQL só `ClusterIP`, sem porta no Windows | Resolvido (CP4) |

## Evidências de verificação

Registrar por checkpoint: comando, trecho relevante da saída, data e (para a pipeline) link da execução.

| Checkpoint | Evidência | Data |
|---|---|---|
| CP1 | `./mvnw clean verify`: exit 0, 926 testes, 0 falhas, 0 ignorados | 2026-10-17 |
| CP1 | `docker compose up -d --build` (Santiago): app `Up`, MySQL/Mailpit/WireMock `healthy`, Mailpit `v1.31.4` | 2026-10-17 |
| CP1 | `docker compose exec app id -u` → `1000`; `echo $JAVA_TOOL_OPTIONS` → `-XX:MaxRAMPercentage=75.0` | 2026-10-17 |
| CP1 | `docker image inspect ... .Config.Env` → só `PATH`, `JAVA_HOME`, `LANG*`, `JAVA_VERSION`, `JAVA_TOOL_OPTIONS` (sem segredos) | 2026-10-17 |
| CP1 | `GET /swagger-ui.html` → `302` (redirect do springdoc para `/swagger-ui/index.html`): API no ar em `localhost:8080` | 2026-10-17 |
| CP2 | `./mvnw clean verify`: exit 0, 936 testes (+10), 0 falhas, 0 ignorados; JaCoCo 94,5% instruções, 77,8% branches | 2026-10-17 |
| CP2 | `docker compose up -d --build app` (Santiago) → `docker compose ps`: `workshop-app ... (healthy)` | 2026-10-17 |
| CP2 | `curl` sem token: liveness `200 {"status":"UP"}`, readiness `200 {"status":"UP"}`, `/actuator/env` `401 UNAUTHORIZED` | 2026-10-17 |
| CP2 | `docker compose stop mysql` → readiness `503 {"status":"DOWN"}`, liveness `200 {"status":"UP"}`, app `Up 8 minutes (unhealthy)` (não reiniciou) | 2026-10-17 |
| CP2 | `docker compose start mysql` → readiness volta a `200 {"status":"UP"}` | 2026-10-17 |
| CP3 | Arquivos fictícios + `git check-ignore -v`/`git status`: ignorados `terraform.tfstate`, `.tfstate.backup`, `.terraform/`, `kubeconfig`, `terraform.tfvars`, `aws.tfplan`, `crash.log`, `k8s/overlays/{local,aws}/secrets.env`; versionáveis `.terraform.lock.hcl`, `terraform.tfvars.example`, `secrets.env.example`. Fictícios apagados | 2026-10-17 |
| CP4 | Pré-requisitos (Santiago): `terraform version` → `v1.16.6`; `kubectl version --client` → `v1.36.1` (Kustomize `v5.8.1`); `docker info` → `7273299968 5` (6,77 GiB, 5 CPUs) após o ajuste do `.wslconfig` | 2026-10-17 |
| CP4 | `terraform fmt -check -recursive infra` → exit 0 | 2026-10-17 |
| CP4 | `cluster`: `plan` → `2 to add`; `apply` → `2 added`; `kubectl get nodes` → `workshop-control-plane Ready v1.35.0`; metrics-server `1/1 Running`, 0 restarts; `kubectl top nodes` → `124m`, `573Mi`; `docker ps` → `0.0.0.0:8080->30080`, `0.0.0.0:8025->30025`, `127.0.0.1:59720->6443` | 2026-10-17 |
| CP4 | `database`: `plan` → `8 to add` (Secrets e senhas como `sensitive value`); `apply` → `8 added`; `mysql-0 1/1 Running`, 0 restarts; PVC `data-mysql-0 Bound 1Gi standard` em `workshop`; PV com `RECLAIM POLICY Delete`; `top pods` → `12m`, `424Mi` | 2026-10-17 |
| CP4 | Readiness falhou 18× durante a primeira inicialização (~95 s, servidor temporário com `port: 0`) sem reiniciar o pod; `SHOW DATABASES` como `workshop_user` com a senha do Secret `workshop-db` → `workshop` (D6 comprovada) | 2026-10-17 |
| CP4 | Persistência: `CREATE TABLE prova` + `INSERT 42` → `kubectl delete pod mysql-0` → pod recriado `1/1` em 14 s → `SELECT` → `42` | 2026-10-17 |
| CP4 | `destroy` `database` → `8 destroyed` (namespace por último, 13 s); `destroy` `cluster` → `2 destroyed`; `docker ps` vazio; `kubectl config get-contexts` sem `kind-workshop` | 2026-10-17 |

## Rollback ou recuperação

- **Deploy ruim no cluster:** `kubectl rollout undo deployment/workshop-app -n workshop`. O rolling update com
  `maxUnavailable: 0` mantém a versão anterior atendendo enquanto a nova não fica pronta.
- **Ambiente local corrompido:** `terraform destroy` em `database` e depois em `cluster`, seguido de novo `apply`. Os
  dados locais são descartáveis.
- **Pipeline com falha:** nenhuma etapa posterior roda; o cluster do job é descartado com o runner. Corrigir e fazer
  novo push.
- **State AWS travado:** confirmar que não há `apply` em curso antes de `terraform force-unlock`.
- **Esquecer recursos na AWS:** `destroy` das stacks `database` e `cluster`; conferir no console EKS, EC2 (NAT, ELB,
  EIP) e RDS.
- **Migrations:** nenhuma migration nova nesta feature. A recuperação de schema segue o processo normal do Flyway.
