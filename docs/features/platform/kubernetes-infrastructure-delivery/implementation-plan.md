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

**Status: Completed (2026-10-18): 10 de 10 critérios; deploy em 2026-10-17, teste de carga/HPA em 2026-10-18.**

- [x] `k8s/base`: Deployment, Service, ConfigMap, HPA, Mailpit e WireMock, conforme a technical-spec.
- [x] `k8s/overlays/local`: NodePort 30080/30025, HPA 1–3, perfil `dev` com seeds, imagem
      `workshop-management-system:local`, `secretGenerator` e `secrets.env.example`.
- [x] Makefile: `k8s-local-deploy` (build `:local`, `kind load`, ConfigMap do WireMock a partir de
      `docker/wiremock/mappings`, `kubectl apply -k`), `k8s-local-delete` e `load-test`.
- [x] `load-test/hpa-load.js` (k6) com limiares `http_req_failed < 1%` e `p(95) < 2s`.

Desvios registrados:

- **ConfigMap do app:** em vez de `app-configmap.yaml` + um segundo ConfigMap no overlay, a base usa
  `configMapGenerator` (`workshop-app-config`, a partir de `app-config.env`) e o overlay faz `behavior: merge` com
  `SPRING_PROFILES_ACTIVE=dev`/`APP_SEED_ENABLED=true`. Motivo: um único ConfigMap com hash no nome (mudança de
  configuração dispara rolling update) e a base continua válida sozinha para o overlay `aws`.
- **`kind load`:** o CLI do kind não é pré-requisito, então o deploy faz o que o `kind load docker-image` faz por
  dentro: `docker save --platform linux/<arch>` → `docker cp` para o node → `ctr --namespace=k8s.io images import`.
  O `--platform` evita a falha de import com o containerd image store do Docker Desktop (atestações sem conteúdo).
  O destino é `/var/tmp`, não `/tmp`: no node do kind, `/tmp` é `tmpfs`, e o `docker cp` gravou por baixo da montagem
  (`ctr: open /tmp/...: no such file or directory`, observado em 2026-10-17).
- **k6:** o script é montado por volume (`-v .../load-test:/scripts:ro`) em vez de `run - < arquivo`, porque o
  PowerShell 5.1 não aceita o operador `<`; `--add-host=host.docker.internal:host-gateway` para funcionar também no
  Linux (pipeline). Imagem `grafana/k6:1.3.0`.
- **HPA 1–3 e janela de 60 s ficam na base** (valores locais); o overlay `aws` sobrescreve.
- **`startupProbe` 3 → 5 min (`failureThreshold` 36 → 60), calibração aprovada pelo Santiago (2026-10-17):** o
  primeiro boot em banco vazio levou **165,6 s** (Flyway aplicou 25 migrations em 2m18s), a 14 s do limite de 180 s
  da technical-spec; com o banco já migrado, o pod recriado ficou pronto em 27 s. Matar o container no meio de uma
  migration deixaria DDL do MySQL (não transacional) pela metade. 5 min cabem no `progressDeadlineSeconds: 300`. É
  um valor calibrado com evidência, não estrutural: a lentidão do MySQL local (~5,5 s por migration) está em
  investigação.
- **MySQL local com durabilidade relaxada (aprovado pelo Santiago, 2026-10-18):** `args` `--skip-log-bin`,
  `--innodb-flush-log-at-trx-commit=2` e `--innodb-doublewrite=OFF` só na stack `infra/local/database` (banco
  descartável; não se aplica ao RDS). Flyway 2m18s → **1m55s**, boot 165,6 s → **139,6 s** (−17%). A causa principal
  não é o commit: o `execution_time` do `flyway_schema_history` mostrou DML em 2–5 ms e DDL lento, e um teste num banco
  descartável mediu `INSERT`/`UPDATE` 0,00 s, `ADD COLUMN` (instantâneo) 0,27 s, `CREATE TABLE` 1,23 s e
  `ADD CONSTRAINT CHECK` (reconstrói a tabela) 1,50 s. O custo é criar e sincronizar arquivos `.ibd` no disco do kind
  sobre WSL2, que nenhuma flag desliga. A startupProbe de 5 min continua (folga suficiente). Sem dívida técnica.
- **Observação para o time (fora do escopo):** consolidar (*squash*) as 25 migrations num schema só com `CREATE`s
  eliminaria os `ALTER`s do primeiro boot (estimativa: ~25–30 s de Flyway), mas muda checksums, exige recriar todos os
  bancos e afeta as branches de todos; o `AGENTS.md` só permite isso antes do baseline operacional e com spec aprovada.
- **HPA só por CPU (aprovado pelo Santiago, 2026-10-18):** no 1º teste de carga a escala desceu 3 → 2 e travou: 2 pods
  ociosos a 48% de memória → `ceil(2 × 48 / 85) = 2` (`describe hpa`: `ScalingActive ... from memory resource
  utilization`), porque a JVM não devolve o heap. A métrica de memória foi removida do `app-hpa.yaml` (base, vale para
  local e AWS); a memória segue limitada pelo `limit` de 1 Gi e pelo `MaxRAMPercentage`. Nota na technical-spec, sem
  voltar a Draft: o risco já estava previsto e o comportamento pedido (escalar e voltar ao mínimo) não muda.
- **Imagem `:local` reconstruída:** o Deployment não muda (mesma tag), então o deploy faz `rollout restart` quando ele
  já existe.

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

**Status: Completed (2026-10-20, adiantado): 5 de 5 critérios.**

- [x] Criar o fork pessoal (`SantiagoSilvestre/17SOAT-workshop-management-system`); environment `kind-ci` com
      `APP_SECURITY_JWT_SECRET` e `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET` (48 bytes aleatórios do
      `RandomNumberGenerator`; `Keys.hmacShaKeyFor` exige ≥ 32 bytes).
- [x] `.github/workflows/cd.yml` com jobs `build-test` → `image` → `deploy-kind`, gatilho só em `feat/terraform-san`
      (um único ponto), `REGISTRY`/`IMAGE_NAME` em `env:`, actions fixadas por SHA e smoke test.
- [x] Tornar o pacote GHCR público depois do primeiro push (D9): já nasceu público (ver desvios).
- [x] README: onde fica o nome da branch no workflow e como trocá-lo (seção "Pipeline de entrega").

Desvios registrados:

- **Dono da imagem calculado:** `${GITHUB_REPOSITORY_OWNER,,}` (minúsculas, exigência do GHCR) em vez de um nome fixo;
  o job `image` exporta a referência completa como `outputs.image` e o `deploy-kind` a consome. No repositório do grupo,
  o mesmo arquivo publica em `ghcr.io/systemivan/...` sem edição.
- **Login no GHCR por `docker login --password-stdin`** com o `GITHUB_TOKEN`, sem `docker/login-action`/
  `build-push-action`: uma action de terceiros a menos para fixar por SHA. Terraform pela `hashicorp/setup-terraform`
  v4.0.1 (`dfe3c3f8...`), versão `1.16.6` igual à local, `terraform_wrapper: false`.
- **Pacote GHCR público sem ação manual:** o pull anônimo do manifest `:d902d1b...` respondeu `200` logo após o
  primeiro run. O pacote publicado pelo `GITHUB_TOKEN` fica ligado ao repositório de origem e herdou a visibilidade
  pública do fork.
- **Retry na readiness do smoke test (`46cd5b2`):** o run `39363382338` falhou com
  `curl: (56) Recv failure: Connection reset by peer` 13 ms depois de `successfully rolled out` (pod `1/1`, 0
  restarts). O pod fica Ready antes de o kube-proxy programar o endpoint na regra do NodePort; o `curl` passou a usar
  `--retry 12 --retry-delay 5 --retry-all-errors --max-time 5` (até ~1 min). Um app que nunca fica pronto continua
  reprovando o job.
- **Commit de teste revertido:** `8a9f474` (teste que falha de propósito) desfeito por `git revert` (`1cc7409`), sem
  reescrever o histórico já publicado no fork.

Verificação: execução verde no fork, com link registrado e duração total ≤ 40 min; commit temporário com teste
quebrado → `image` e `deploy-kind` não rodam (depois reverter); push em outra branch → `cd.yml` não dispara; logs sem
segredos (procurar pelos valores); `ci.yml` intacto (`git diff` vazio no arquivo).

Commit sugerido: `feat(platform): add delivery pipeline with ephemeral kind cluster and smoke test`

### CP7 — README local e vídeo de garantia (passo 10, previsto 22/10)

**Status: In Progress (2026-10-20).**

**Escopo ampliado (aprovado por Santiago Silvestre em 2026-10-20):** além de documentar o ambiente local, o README de
776 linhas é reorganizado em documentos com uma responsabilidade cada (Diátaxis: tutorial, how-to, referência,
explicação). O README vira a capa (≤ 150 linhas) e o detalhe vai para `docs/`. `docs/Architecture.md`,
`docs/Architecture-Decisions.md`, `docs/adr/` e `docs/features/` não mudam de lugar, porque o `CLAUDE.md`, as regras
por épico e o agente `architecture-reviewer` apontam para eles. Referências ao `README.md` em specs antigas são
registro histórico e não são reescritas. É uma mudança só de documentação (plano conciso do `AGENTS.md`), dividida em
commits focados:

- [x] 1. Mover as seções do README para `docs/setup/`, `docs/operations/`, `docs/api/` e `CONTRIBUTING.md` sem
      reescrever o texto (só títulos e alvos de links mudam); incorporar o `DOCKER.md` e remover o `HELP.md`
      (boilerplate do Spring Initializr); apontar para os novos caminhos as referências vivas (glossário, comentário do
      `cd.yml`, descrição da collection, `manual-test-guide.md` do RF40). Verificado por comparação linha a linha:
      nenhuma linha de conteúdo perdida.
- [x] 2. `docs/architecture/overview.md`: visão C4 (contexto, containers, módulos, implantação local e pipeline) com 5
      diagramas mermaid, escrita a partir do código atual (dependências entre módulos, listeners, HPA, namespaces). A
      seção "Arquitetura" do README foi absorvida e virou link. Verificado: os 5 diagramas renderizam no
      `mermaid-cli` 11.4.2 e o link checker não acusou links quebrados. Divergências encontradas e **não
      corrigidas** (documentos compartilhados, fora do escopo): a seção "Current Repository State" do `docs/Architecture.md` ainda descreve os
      módulos `customer/technician/parts`; o `docs/PROJECT-STRUCTURE.md` não cita o módulo `identity`; o README dizia
      "MySQL 8.0", mas o compose e o kind usam `mysql:8.4`.
- [ ] 3. `docs/setup/local-kubernetes.md` (Terraform → deploy no kind → verificação → destroy, com equivalentes sem
      `make`, recursos criados por stack) e `docs/operations/load-testing-hpa.md`; traduzir e deduplicar o conteúdo
      vindo do `DOCKER.md` (a frase "Mailpit is not deployed to Kubernetes" está desatualizada).
- [ ] 4. README como capa (objetivos da fase, diagrama, quickstart, mapa "quero... → leia") e `docs/README.md`
      (índice por público).
- [ ] 5. `AGENTS.md` (linhas que exigem o teste manual "no README") e demais links apontando para os novos caminhos.
- [ ] Postman/README: fluxo de teste manual atualizado (regra do `AGENTS.md`).
- [ ] Gravar o vídeo local: deploy, pipeline, consumo das APIs e HPA escalando.

Verificação: README ≤ 150 linhas e sem conteúdo duplicado; link checker com 0 links quebrados (inclusive âncoras);
mermaid renderizado no GitHub; seguir só o `docs/setup/local-kubernetes.md` do zero em um cluster novo
(`infra-local-down` → `up` → deploy → Postman → carga) sem passo implícito.

Commits sugeridos: `docs(platform): move readme sections into dedicated docs without rewording`, seguido dos commits 2
a 5 acima, e por fim `docs(platform): document local kubernetes environment and delivery pipeline`.

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
| Segredos e logs | Secrets por environment; `secrets.env` não versionado; token mascarado | Média | `.gitignore` (CP3), `umask 077`, `::add-mask::`, revisão dos logs; CP6: secrets só no job `deploy-kind` (`environment: kind-ci`), entregues por `env:`, sem `set -x`; busca nos logs do run `39360660986` sem nenhum valor exposto | Resolvido para `kind-ci` (CP6); AWS no CP8 |
| Persistência e migrations | Nenhuma migration nova; state com senha do banco | Média | state local fora do git; S3 criptografado e privado | Pendente |
| Respostas de erro | Health não expõe detalhes | Baixa | `show-details`/`show-components=never` em `actuator.properties`, importado também pelos testes; banco fora → só `{"status":"DOWN"}` | Resolvido (CP2) |
| Dependências | Actuator (BOM do Boot), imagens com tag fixa, actions por SHA | Baixa | fixar versões | Pendente |
| Abuso de endpoints | Health pode ser chamado em massa | Baixa | resposta barata; sem cache; fora de escopo limitar taxa | Pendente |
| Container | Não-root numérico, FS somente leitura, sem capabilities | — | `securityContext` do app: `runAsNonRoot`/`runAsUser: 1000`, `allowPrivilegeEscalation: false`, `readOnlyRootFilesystem` (só `/tmp` em `emptyDir` de 256 Mi), `drop: [ALL]`, `seccomp RuntimeDefault`, `automountServiceAccountToken: false`; o app subiu sem `Read-only file system`. Mailpit/WireMock (imagens de terceiros): `allowPrivilegeEscalation: false` + seccomp, sem token | Resolvido (CP5) |
| Secret no `last-applied-configuration` | O `kubectl apply` grava o objeto inteiro numa anotação, inclusive o `data` (base64) do Secret `workshop-app` | Baixa | Quem pode ler a anotação já pode ler o Secret (mesma permissão `get secrets`); valores locais são só de desenvolvimento. Na AWS, considerar `kubectl apply --server-side`, que não grava essa anotação | Aceito (local); revisar no CP8 |
| `--kubelet-insecure-tls` | TLS do kubelet não verificado | Baixa | só no kind descartável (`infra/local/cluster/main.tf`); EKS usa add-on | Resolvido (CP4) |
| Credenciais do kind no plano | O provider `tehcyx/kind` não marca `client_key`/`kubeconfig` como sensíveis: `plan`/`destroy` imprimem a chave privada de admin do cluster (observado no CP4) | Baixa | API server só em `127.0.0.1`, cluster descartável (a chave morre no `destroy`); kubeconfig fora do repositório; não colar saídas de `destroy` em canais públicos; no CP6, o cluster vive só no runner efêmero, mas os logs do job ficam visíveis no fork: revisar se o `plan`/`destroy` do kind deve rodar com saída filtrada. CP6: a pipeline só faz `apply` sobre state vazio (atributos `(known after apply)`) e nunca `destroy` (o runner é descartado); busca nos logs do run `39360660986` sem `PRIVATE KEY`/`client-key-data`, então nenhum filtro é necessário | Aceito (local); resolvido na pipeline (CP6) |
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
| CP5 | `kubectl kustomize k8s/overlays/local` → 8 objetos; ConfigMap `workshop-app-config-gbdf27b9fm` e Secret `workshop-app-dt9kffbhb5` com hash; NodePorts 30080/30025 | 2026-10-17 |
| CP5 | Imagem no node (Santiago): `docker save --platform linux/amd64` → `docker cp` para `/var/tmp` → `ctr -n k8s.io images import` → `crictl images` lista `docker.io/library/workshop-management-system local 0002eb3fea1ce 165MB` (mesmo config digest do build). Tentativa em `/tmp` falhou: `tmpfs` no node | 2026-10-17 |
| CP5 | `kubectl apply -k k8s/overlays/local` → 9 objetos `created`; `rollout status` → `successfully rolled out`; 4 pods `1/1`, 0 restarts | 2026-10-17 |
| CP5 | Log: Flyway `Successfully applied 25 migrations ... now at version v20260926043630 (execution time 02:18.305s)`; `Started ... in 165.559 seconds` (banco vazio) | 2026-10-17 |
| CP5 | `curl localhost:8080/actuator/health/readiness` → `{"status":"UP"}`; `describe deployment` → `envFrom` com `workshop-app-config-*`/`workshop-db` (ConfigMaps) e `workshop-db`/`workshop-app-*` (Secrets), sem valores | 2026-10-17 |
| CP5 | Recriação: `kubectl delete pod -l app.kubernetes.io/name=workshop-app` → pod novo do mesmo ReplicaSet `1/1` em 27 s (banco já migrado) | 2026-10-17 |
| CP5 | Persistência: `POST /api/customers` (CPF 529.982.247-25) → id `13008937-...`; `kubectl delete pod mysql-0` → `mysql-0` `1/1` em 20 s; app `0/1` (readiness) e de volta a `1/1` sem restart; `GET /api/customers/{id}` → cliente presente. CPF 111.444.777-35 → 409 (já criado pelo seed `dev`, D10 comprovada) | 2026-10-17 |
| CP5 | Rolling update (probe 36 → 60): `apply -k` → ReplicaSet novo `5d589d5c8c`; 2 pods durante ~30 s; o antigo só terminou depois que o novo ficou `1/1` (`maxSurge: 1`, `maxUnavailable: 0`) | 2026-10-17 |
| CP5 | Versão ruim: `set env DB_URL=jdbc:mysql://inexistente:3306/x` → pod novo `Error`/`CrashLoopBackOff` (3 restarts em 96 s); antigo `1/1`; readiness `UP`; `get rs` → `5d589d5c8c 1/1/1`, `76bfc6cd55 1/1/0`; `rollout undo` → `76bfc6cd55` a 0; `kubectl diff -k` → sem diferenças | 2026-10-17 |
| CP5 | Postman: 27 passos do `make e2e` (Newman, um processo por pasta) contra `localhost:8080` → 27/27. Corrigido antes: o pre-request de `Open full intake with services` gravava `intakeCustomerTaxId`/`intakeLicensePlate` só como variável de collection, perdida entre processos do Newman (`Run Open full intake with services first`); agora grava também no environment | 2026-10-17 |
| CP5 | Ambiente recriado com o MySQL relaxado: `SELECT @@log_bin, @@innodb_flush_log_at_trx_commit, @@innodb_doublewrite` → `0 2 OFF`; Flyway `Successfully applied 25 migrations ... (execution time 01:55.249s)`; `Started ... in 139.643 seconds` (banco vazio) | 2026-10-18 |
| CP5 | `flyway_schema_history.execution_time`: DML 2 ms/5 ms; `initial schema` 20,2 s, `add customer address` 15,7 s (8 `ALTER`). Banco `bench` descartável (`mysql -vvv`): `INSERT`/`UPDATE` 0,00 s, `ADD COLUMN` 0,27 s, `CREATE TABLE` 1,23 s, `ADD CONSTRAINT CHECK` 1,50 s; `bench` apagado | 2026-10-18 |
| CP5 | Carga 1 (HPA CPU + memória), k6 `grafana/k6:1.3.0` só em `localhost:8080`: CPU 154% → 2 pods, 183% → 3 pods; pod novo nasce a ~1000m (boot da JVM) sem cascata; 6714 requisições, **0 falhas**, p95 84 ms. Fim da carga: 3 → 2 em ~1 min e **travou em 2** (memória 48%/85%, `ScalingActive ... from memory`) | 2026-10-18 |
| CP5 | Após remover a memória (`kubectl diff -k` → só o bloco `memory` sai; `apply` → `configured`): 2 → 1 ocioso em ~1 min. Carga 2: CPU 104% → 2, 3 pods no pico (máx. 124%); 6727 requisições, **0 falhas**, p95 77 ms, máx. 836 ms; fim da carga → 3 → 2 → **1** em ~1 min. Critérios "escala sobe", "escala desce" e "mesmo endereço" ✅ | 2026-10-18 |
| CP6 | Run verde [`39360660986`](https://github.com/SantiagoSilvestre/17SOAT-workshop-management-system/actions/runs/39360660986) (`d902d1b`): `build-test` 1m53s, `image` 1m40s, `deploy-kind` 2m35s, **total 6m17s** (meta ≤ 40 min). Banco vazio no runner: step `Smoke test` (rollout incluído) em 36 s; no run `39363382338`, `Started ... in 17.791 seconds`, contra ~140 s no kind sobre WSL2 (confirma o gargalo de disco do CP5); `Smoke test passed for ghcr.io/santiagosilvestre/workshop-management-system:d902d1b...` | 2026-10-20 |
| CP6 | Pull anônimo `GET ghcr.io/v2/santiagosilvestre/workshop-management-system/manifests/d902d1b...` → `200` (pacote público, D9) | 2026-10-20 |
| CP6 | Logs do run `39360660986` (zip, `Select-String`): nenhum `PRIVATE KEY`/`client-key-data`/`eyJ`; `client_key`/`client_certificate` só como `(known after apply)`; secrets do `kind-ci`, `GITHUB_TOKEN` e cabeçalho `Authorization` como `***` | 2026-10-20 |
| CP6 | Push em outra branch (`git push fork feat/terraform-san:test/cd-trigger-probe`, depois apagada) → nenhuma execução nova (Santiago) | 2026-10-20 |
| CP6 | Teste quebrado: run [`39363151926`](https://github.com/SantiagoSilvestre/17SOAT-workshop-management-system/actions/runs/39363151926) (`8a9f474`) → `Build and test` falhou em `Run quality gate`; `Build and push image` e `Deploy to kind and smoke test` **skipped** | 2026-10-20 |
| CP6 | Revert `1cc7409`: run [`39363382338`](https://github.com/SantiagoSilvestre/17SOAT-workshop-management-system/actions/runs/39363382338) falhou no smoke test (corrida com o kube-proxy, ver desvios); após o retry, run verde [`39364405763`](https://github.com/SantiagoSilvestre/17SOAT-workshop-management-system/actions/runs/39364405763) (`46cd5b2`), **total 5m29s** | 2026-10-20 |
| CP6 | `git diff --quiet 3d0cdb3 HEAD -- .github/workflows/ci.yml` → sem diferenças (`ci.yml` intacto) | 2026-10-20 |
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
