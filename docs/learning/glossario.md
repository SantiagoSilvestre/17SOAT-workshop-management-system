# Glossário de infraestrutura

Glossário de aprendizado da Fase 2. Cada termo tem uma explicação curta, uma analogia com a oficina e um link para
onde ele aparece no projeto. Os termos são aprofundados no passo em que forem usados pela primeira vez; entradas
marcadas com _(introdução)_ ainda serão expandidas.

## Arquitetura e processo

### ADR (Architecture Decision Record)

Documento curto que registra uma decisão de arquitetura: contexto, opções avaliadas, decisão e trade-offs aceitos.
Nasce `Proposed` e só um humano o promove a `Accepted`. É a "ata da reunião" que explica por que a oficina comprou
este elevador e não outro. Usado em [`docs/adr/`](../adr/), por exemplo na
[ADR-008](../adr/ADR-008-estrategia-de-ambientes-kind-local-e-aws.md).

### Trade-off

O que se perde ao escolher uma opção. Toda decisão tem um, e a ADR obriga a registrá-lo na seção "Negativas".

### SDD e especificação funcional

SDD (Spec-Driven Development) é escrever e aprovar o comportamento antes do código. A especificação funcional diz
**o quê** e **por quê**, em linguagem de negócio; o **como** fica na technical-spec. Analogia: a ordem de serviço
descreve o defeito e o resultado esperado antes de o mecânico escolher as ferramentas. Ver
[functional-spec da infra](../features/platform/kubernetes-infrastructure-delivery/functional-spec.md).

### Critério de aceite

Frase verificável de forma independente que diz quando um comportamento está pronto ("sem carga, as cópias voltam ao
mínimo"). Se não dá para testar, não é critério de aceite. Analogia: o checklist de entrega do veículo.

## Containers

### Container x VM

Uma VM simula um computador inteiro, com sistema operacional próprio; um container é só um processo isolado que
compartilha o kernel (o núcleo do sistema operacional) da máquina. Por isso o container sobe em segundos e ocupa
megabytes, não gigabytes. Analogia: a VM é uma oficina inteira alugada; o container é um box dentro da mesma oficina.

### Imagem, Dockerfile e camadas

A imagem é o "molde" somente leitura de onde os containers nascem; o [Dockerfile](../../Dockerfile) é a receita
dela. Cada instrução (`COPY`, `RUN`) gera uma camada, e o Docker reaproveita (cacheia) as camadas que não mudaram: por
isso o `pom.xml` é copiado antes do `src`. Analogia: o kit de peças padrão montado uma vez e reutilizado em cada box.

### Multi-stage build

Dockerfile com mais de um `FROM`: o primeiro estágio (Maven + JDK) compila; o último (só JRE) recebe apenas o `.jar`
por `COPY --from`. A imagem final não leva compilador nem código-fonte: fica menor e com menos vulnerabilidades. Ver o
[Dockerfile](../../Dockerfile).

### Usuário não-root e UID numérico

O processo roda como `USER 1000`, e não como root (administrador): se alguém explorar uma falha da aplicação, não
ganha poder sobre o container. O UID numérico permite ao Kubernetes verificar `runAsNonRoot`; com um nome, ele não
sabe se o usuário é root. Analogia: o estagiário tem crachá de box, não a chave do cofre.

### `ENTRYPOINT` na forma exec, PID 1 e `SIGTERM`

Na forma exec (`["java", "-jar", ...]`) a JVM é o PID 1, o primeiro processo do container, e recebe o `SIGTERM` (o
aviso "termine o que está fazendo") quando o Docker ou o Kubernetes param o container. Um script na frente só é seguro
se terminar com `exec "$@"` (que troca o bash pela JVM); sem o `exec`, o sinal para no bash e a JVM é morta à força
(`SIGKILL`) depois do prazo. Ver o [Dockerfile](../../Dockerfile).

### `JAVA_TOOL_OPTIONS` e `MaxRAMPercentage`

`JAVA_TOOL_OPTIONS` é lida pela própria JVM em qualquer forma de inicialização (o antigo `JAVA_OPTS` só funcionava
com um script que o repassasse, e era ignorado). `-XX:MaxRAMPercentage=75.0` limita o heap a 75% da memória do
container, deixando o resto para threads e metaspace: com limite de 1 Gi, o heap fica em ~768 Mi.

### Variáveis de ambiente e contrato `DB_*`

Valores entregues ao processo de fora do código (`DB_URL`, `DB_PASSWORD`): a mesma imagem roda no compose, no kind e
na AWS, mudando só o ambiente. O projeto usa um único nome por configuração (D1 da
[technical-spec](../features/platform/kubernetes-infrastructure-delivery/technical-spec.md)). Analogia: a mesma
ferramenta, calibrada de outro jeito em cada oficina.

### `depends_on` com `service_healthy`

No [docker-compose.yml](../../docker-compose.yml), o app só é iniciado quando o healthcheck do MySQL passa. Isso
substituiu o `wait-for-mysql.sh`, que exigia `mysql-client` e `netcat` na imagem; no Kubernetes, quem segura o
tráfego até o banco responder é a readiness.

### Tag de imagem

Etiqueta que identifica uma versão de uma imagem (`app:3d0cdb3`). Sem tag, o Docker usa `latest`, um nome
reaproveitado que cada push sobrescreve. A tag pelo SHA do commit é única e rastreável: é o número de série da peça.
Decidida na [ADR-008](../adr/ADR-008-estrategia-de-ambientes-kind-local-e-aws.md).

### CRLF x LF e shebang

Fim de linha: Linux usa `\n` (LF), Windows usa `\r\n` (CRLF); com `core.autocrlf=true` o git entrega CRLF na pasta.
O shebang (`#!/bin/bash`, 1ª linha do script) diz ao Linux quem executa o arquivo; com CRLF ele vira `bash\r`, que não
existe, e o container falha com "no such file or directory". Correção: `*.sh text eol=lf` no `.gitattributes`.
Analogia: um caractere invisível no nome da ferramenta do manual. Ver o achado do
[CP7](../features/servicelifecycle/service-order-full-intake/implementation-plan.md).

### `docker compose up --build`

Sem `--build`, o Compose reaproveita a imagem que já existe, mesmo com o código alterado; com `--build`, ele
recompila a imagem antes de subir. Sintoma de imagem velha: endpoint novo ausente no Swagger. Ver
[docker-compose.yml](../../docker-compose.yml).

### WSL2 e `.wslconfig`

No Windows, o Docker Desktop roda os containers dentro de uma VM Linux leve (WSL2). O `~/.wslconfig` define o teto de
memória e de CPUs dessa VM; são tetos, não reservas. Mudanças só valem após `wsl --shutdown`. Analogia: o tamanho do
galpão alugado: não cabem mais boxes do que o galpão comporta. Ajustado para 7 GB/5 CPUs no
[CP4 da infra](../features/platform/kubernetes-infrastructure-delivery/implementation-plan.md).

### Digest de imagem

Impressão digital (`@sha256:...`) do conteúdo exato de uma imagem. A tag (`v1.35.0`) pode ser republicada; o digest,
não. Fixar por digest garante a mesma imagem no PC e na pipeline. Usado no node do kind em
[variables.tf](../../infra/local/cluster/variables.tf).

## Kubernetes

### Pod _(introdução)_

Menor unidade que o Kubernetes executa; aqui, uma cópia da aplicação rodando. Pods são descartáveis: morrem, são
recriados e mudam de endereço. Analogia: um box de atendimento (ou uma sala de aula).

### Service _(introdução)_

Endereço fixo que recebe as requisições e as encaminha para qualquer pod disponível. Existe porque os pods mudam;
o cliente (Postman) chama sempre o Service e não precisa saber qual pod responde. Analogia: a secretaria da escola,
que manda o aluno para qualquer sala com aquela aula.

### kind

"Kubernetes IN Docker": cria um cluster Kubernetes em que cada node é um container Docker
(`workshop-control-plane` no `docker ps`). Usado no ambiente local e dentro do job da pipeline. Alternativas:
minikube e k3d. Escolhido na [ADR-008](../adr/ADR-008-estrategia-de-ambientes-kind-local-e-aws.md); criado pela
stack [infra/local/cluster](../../infra/local/cluster/main.tf), sem instalar o kind (o provider embute a biblioteca).

### Namespace

Divisão lógica do cluster que agrupa recursos com nomes próprios (`workshop`, `kube-system`). Apagar o namespace
apaga tudo dentro dele, inclusive PVCs. Por isso tem um único dono: a stack
[database](../../infra/local/database/main.tf). Analogia: uma ala da oficina com seus boxes.

### Label e selector

Etiquetas chave=valor nos objetos (`app=mysql`). O selector de um Service ou StatefulSet escolhe "todos os pods com
esta etiqueta"; o `kubectl get pods -l app=mysql` usa o mesmo mecanismo. Analogia: a etiqueta colorida na chave do
carro que diz em qual box ele está.

### StorageClass e reclaim policy

A StorageClass cria o disco (PV) automaticamente quando surge um PVC; no kind, a `standard` usa uma pasta dentro do
node. A reclaim policy diz o que acontece com o disco quando o PVC some: `Delete` apaga, `Retain` guarda. Analogia:
o almoxarifado que entrega o armário sob pedido e o esvazia (ou não) na devolução.

### Helm e chart

O Helm é o gerenciador de pacotes do Kubernetes; um chart é um pacote de YAMLs parametrizáveis por `values`. O
metrics-server é instalado pelo chart oficial via provider `helm`, sem instalar o Helm. Analogia: um kit de peças já
montado, em vez de parafusos avulsos. Ver [infra/local/cluster](../../infra/local/cluster/main.tf).

### kubeconfig

Arquivo com o endereço do cluster e a **credencial** de acesso a ele; é o que o `kubectl` lê para saber com quem
falar. Quem tem o arquivo administra o cluster, por isso `kubeconfig*` está no [.gitignore](../../.gitignore).
Analogia: a chave da oficina com o endereço gravado no chaveiro.

### minikube

Outra ferramenta de Kubernetes local, focada na experiência de desenvolvimento: traz addons prontos
(`minikube addons enable metrics-server`) e atalhos de acesso (`minikube service`, `minikube tunnel`). O kind é mais
cru (instala-se tudo manualmente), mas é mais leve, sobe mais rápido e é o padrão em pipelines de CI. Analogia:
minikube é a oficina-escola com ferramentas já penduradas; kind é o galpão vazio que monta e desmonta rápido.

### Liveness x readiness

Duas perguntas diferentes que o Kubernetes faz à aplicação. **Liveness**: "você está vivo?"; se não, o pod é
reiniciado. **Readiness**: "você consegue atender agora?"; se não, sai do Service até melhorar. A liveness não deve
depender do banco, senão uma queda do banco reinicia todos os pods à toa. Analogia: o mecânico está no turno
(liveness) x tem as ferramentas para pegar um carro agora (readiness). Ver
[functional-spec da infra](../features/platform/kubernetes-infrastructure-delivery/functional-spec.md).
No projeto, a liveness olha só o estado interno (`livenessState`) e a readiness olha `readinessState` + `db`
([actuator.properties](../../src/main/resources/actuator.properties)). Banco fora → readiness `503` e liveness `200`,
provado em [HealthEndpointDatabaseDownTest](../../src/test/java/br/com/fiap/workshop_management_system/HealthEndpointDatabaseDownTest.java).

### Health check (healthcheck do compose)

Comando que o orquestrador roda periodicamente para saber se o container está saudável. No
[docker-compose.yml](../../docker-compose.yml), o app chama a readiness com `curl -f` (falha em 4xx/5xx), e o
`start_period` dá tempo ao boot antes de contar falhas. O Kubernetes ignora o `HEALTHCHECK` do Dockerfile e usa os
próprios probes; por isso ele não está na imagem.

### Rolling update _(introdução)_

Forma padrão de o Deployment trocar de versão: sobe os pods novos e só desliga os antigos quando os novos ficam
prontos. Se a versão nova nunca fica pronta, a antiga continua atendendo.

### startupProbe

Terceira verificação de saúde, válida só durante o boot. Enquanto ela não passa, a liveness não é feita; assim um boot
lento (Flyway, JVM) não é confundido com processo travado. Analogia: não cobrar produtividade do mecânico enquanto ele
troca de roupa. Ver [technical-spec da infra](../features/platform/kubernetes-infrastructure-delivery/technical-spec.md).

### Requests e limits

`request` é quanto de CPU/memória o pod tem reservado (usado para escolher o node e como base do HPA); `limit` é o
teto. Quem passa do limite de memória é morto (`OOMKilled`); quem passa do de CPU é desacelerado. Analogia: vaga
reservada no pátio × tamanho máximo do box.

### HPA (Horizontal Pod Autoscaler)

Ajusta o número de réplicas de um Deployment entre um mínimo e um máximo, comparando o consumo médio com um alvo
percentual da request (ex.: 70% de CPU). Analogia: o gerente chama mais mecânicos quando a fila cresce. Ver
[technical-spec da infra](../features/platform/kubernetes-infrastructure-delivery/technical-spec.md).

### metrics-server

Componente que coleta CPU e memória dos pods a partir do kubelet de cada node; sem ele o HPA fica cego. No kind roda
com `--kubelet-insecure-tls`, porque o certificado do kubelet é autoassinado. Analogia: o medidor de fila do gerente.

### Kustomize (base e overlay)

Ferramenta embutida no `kubectl` (`apply -k`) que monta os YAMLs a partir de uma `base` comum e de `overlays` com só
as diferenças de cada ambiente. Analogia: ficha padrão de revisão + anexo por modelo de carro.

### ConfigMap e Secret

Objetos que entregam configuração ao pod, normalmente como variáveis de ambiente. ConfigMap para valores comuns;
Secret para sensíveis (acesso mais restrito, fora do `describe`). Analogia: quadro de avisos × cofre.

### StatefulSet e PV/PVC

StatefulSet é o controlador para quem guarda dados: nome estável (`mysql-0`) e disco próprio. O PVC
(PersistentVolumeClaim) é o pedido de disco; o PV é o disco entregue. O disco sobrevive à morte do pod. Analogia: o
arquivo de fichas fica no lugar mesmo que o arquivista troque. Usado no MySQL local.

### NodePort, extraPortMappings e port-forward

NodePort abre uma porta fixa (30000–32767) no node e encaminha ao Service. No kind o node é um container, então o
`extraPortMappings` liga essa porta ao `localhost` do PC. `kubectl port-forward` também dá acesso, mas prende o
tráfego em um único pod, o que invalida um teste de HPA. Analogia: janela de atendimento que dá para a rua.

## Terraform

### IaC (Infraestrutura como Código)

Descrever em arquivos o que deve existir e deixar uma ferramenta fazer a realidade bater com eles. O mesmo código
recria o ambiente do zero, no PC e na pipeline. Analogia: a ficha técnica do modelo, que qualquer mecânico segue e
chega ao mesmo carro. Ver [infra/local](../../infra/local).

### Provider

Plugin que ensina o Terraform a falar com uma plataforma (`tehcyx/kind`, `hashicorp/helm`, `hashicorp/kubernetes`,
`hashicorp/random`; na AWS, `hashicorp/aws`). Versões fixadas em `versions.tf` com `~> X.Y.0` (só correções).
Analogia: o fornecedor homologado pelo qual o almoxarifado pede peças.

### Resource e dependência implícita

`resource "tipo" "nome" { }` descreve uma coisa que deve existir. Quando um resource usa o atributo de outro
(`random_password.app.result` no Secret), o Terraform deduz a ordem de criação e a inverte no `destroy`; a listagem do
`plan` é alfabética e não mostra essa ordem. Analogia: itens da ficha técnica que dependem de outros.

### Variable e output

Variable é a entrada da stack (`cluster_name = "workshop"`); output é a saída, mostrada no fim do `apply`
(`kubeconfig_context`). O output de uma stack alimenta a variable de outra. Analogia: a OS que chega e o laudo que
sai. Ver [outputs.tf](../../infra/local/cluster/outputs.tf).

### `init` / `plan` / `apply` / `destroy`

`init` baixa os providers e cria/confere o lock file; `plan` mostra o que será criado (`+`), alterado (`~`) ou
destruído (`-`) sem tocar em nada; `apply` executa após `yes`; `destroy` remove tudo o que está no state. Analogia:
diagnóstico, orçamento, aprovação e execução da OS; nunca se aprova sem ler o orçamento.

### Valor sensível (`sensitive`)

Valor que o Terraform esconde no `plan` (`(sensitive value)`), como senhas do `random_password`. Continua em texto
puro no state. Depende de o provider marcar o atributo: o `tehcyx/kind` não marca a chave do cluster, que aparece
no `destroy` (achado registrado no
[plano da infra](../features/platform/kubernetes-infrastructure-delivery/implementation-plan.md)).

### Stack, state e lock file

Stack é uma pasta com `init/plan/apply` próprios (aqui, `cluster` e `database` por ambiente). O state é o inventário
do que o Terraform criou e pode conter segredos: nunca é versionado. O `.terraform.lock.hcl` fixa as versões dos
providers e **é** versionado. Analogia: inventário do almoxarifado × lista de fornecedores homologados. Ver
[technical-spec da infra](../features/platform/kubernetes-infrastructure-delivery/technical-spec.md).

### `.terraform/`, `.tfvars` e plano salvo

`.terraform/` é a pasta onde o `terraform init` baixa os providers (binários grandes, recriados a partir do lock file).
`*.tfvars` guarda os valores das variáveis de cada desenvolvedor (região, IP, senhas); versiona-se só um
`*.tfvars.example` com valores fictícios. Um plano salvo (`terraform plan -out=*.tfplan`) carrega os mesmos valores
sensíveis do state. Os três ficam fora do git pelo [.gitignore](../../.gitignore).

### `.gitignore` e negação (`!`)

Lista de padrões de arquivos que o git não oferece para commit. `!padrão` reabre uma exceção (`!*.tfvars.example`).
Só protege arquivos **ainda não versionados**: se um segredo já foi commitado, ignorar depois não o apaga do
histórico (é preciso trocar o segredo). Por isso o CP3 da infra vem **antes** de qualquer Terraform. Conferência:
`git check-ignore -v <arquivo>` mostra qual linha casou; `git status` é a prova final.

## Aplicação

### Actuator

Módulo do Spring Boot que expõe endpoints operacionais prontos, como `/actuator/health/liveness` e `/readiness`, e já
sabe checar o banco. Analogia: o painel de luzes do carro. Aqui só `health` é exposto, sem token e sem detalhes
(`show-details=never`): mostrar qual componente falhou ou a versão do banco ajudaria um atacante. A regra de acesso
fica no [SecurityConfig](../../src/main/java/br/com/fiap/workshop_management_system/identity/SecurityConfig.java).

### Arquivo de propriedades sombreado nos testes

`src/test/resources/application.properties` tem o mesmo nome do arquivo principal e o **substitui** nos testes (vence
o primeiro achado no classpath). A configuração que precisa ser testada de verdade fica num arquivo próprio, importado
pelos dois (`spring.config.import=classpath:actuator.properties`); senão o teste validaria uma cópia.

### Teste de mutação (manual)

Quebrar o código ou a configuração de propósito e conferir que algum teste fica vermelho. No CP2 da infra, tirar o
`db` da readiness e mostrar os componentes derrubou 3 testes: é a prova de que eles não são um falso verde.

### Clean Architecture / Hexagonal: porta e adapter

O código fica em camadas: `domain` (regras puras), `application` (casos de uso) e `infrastructure` (HTTP, banco,
outros módulos). O caso de uso não chama o mundo de fora direto: ele declara uma **porta** (interface dele, ex.
`CustomerIntakePort`), e um **adapter** na infraestrutura a implementa, traduzindo tipos e erros de outro módulo.
Analogia: a oficina pede "uma peça com estas medidas" (porta); o comprador sabe qual fornecedor e qual catálogo usar
(adapter). Trocar de fornecedor não muda a oficina. Ver `serviceorder/infrastructure/registration` e o
[CP3](../features/servicelifecycle/service-order-full-intake/implementation-plan.md).

### Dependência cíclica entre pacotes

Quando o pacote A usa o B e o B usa o A, nenhum dos dois pode mudar ou ser testado sem o outro: viram um bloco só.
A saída é um terceiro pacote que depende dos dois (aqui, `servicelifecycle/intake` usa `serviceorder` e
`estimate`). Analogia: em vez de o mecânico e o orçamentista dependerem um do outro, o atendente coordena os dois.
Ver o [CP4](../features/servicelifecycle/service-order-full-intake/implementation-plan.md).

### Códigos HTTP: 4xx x 5xx (400, 401, 403, 404, 409, 500)

4xx = erro de quem chama; 5xx = erro de quem responde. 400: pedido mal formado. 401: sem identificação (falta o
token). 403: identificado, mas sem permissão (ex.: `TECHNICIAN` na abertura completa). 404: o recurso não existe.
409: o pedido é válido, mas conflita com o estado atual (cliente arquivado, placa de outro dono). 500: falha interna.
Analogia: 401 é chegar sem crachá; 403 é ter crachá de mecânico e tentar entrar no caixa. Ver o
[CP5](../features/servicelifecycle/service-order-full-intake/implementation-plan.md).

### Endpoint de orquestração aditivo

Endpoint novo que chama, em sequência, casos de uso que já existem (cadastro, abertura de OS, diagnóstico, orçamento),
sem mudar os endpoints antigos. "Aditivo" = só acrescenta, e por isso não quebra quem usa o contrato atual. Analogia:
um atendente que leva a ficha completa aos vários guichês da oficina. Ver
[abertura completa de OS](../features/servicelifecycle/service-order-full-intake/functional-spec.md).

### Transação e atomicidade

Transação é um bloco de gravações no banco tratado como unidade: ou tudo é confirmado (_commit_), ou tudo é desfeito
(_rollback_). Atomicidade é essa garantia de "tudo ou nada". Analogia: se a OS é recusada no último guichê, a ficha do
cliente cadastrada no primeiro também é rasgada. Ver a regra "Atomicidade" da
[abertura completa de OS](../features/servicelifecycle/service-order-full-intake/functional-spec.md).

### Propagação de transação (`REQUIRED` x `MANDATORY`)

Regra do Spring sobre o que um método `@Transactional` faz quando é chamado dentro de outra transação. `REQUIRED` (o
padrão) entra na transação que já existe ou abre uma nova. `MANDATORY` exige que já exista uma, e falha se não
existir. Analogia: `MANDATORY` é o guichê que só atende quem chega com a ficha aberta no balcão. Ver
[technical-spec da abertura completa](../features/servicelifecycle/service-order-full-intake/technical-spec.md).

### Spring Modulith e Named Interface

O Modulith verifica, por teste (`ModuleStructureTest`), que um módulo só usa de outro o que foi publicado. A Named
Interface é esse "balcão público" do módulo, por exemplo o pacote `application/api` de `registration`. Analogia: o
almoxarifado atende pela janela, e ninguém entra na prateleira dele. Ver a
[ADR-005](../adr/ADR-005-inter-module-integration-contract.md).

### Deadlock e ordem de lock

Deadlock é quando duas transações esperam uma pela outra para sempre: A trancou a peça 1 e quer a 2, enquanto B
trancou a 2 e quer a 1. O banco detecta e derruba uma delas. A prevenção clássica é sempre trancar na **mesma ordem**
(aqui, IDs ordenados). Analogia: dois mecânicos pegando chave e macaco; se ambos pegam primeiro a chave, ninguém trava.
Ver `QuoteStockItemsUseCase` no
[CP2](../features/servicelifecycle/service-order-full-intake/implementation-plan.md).

### Teste unitário, teste de integração e mock

Teste unitário testa uma classe isolada, trocando as dependências por **mocks** (dublês do Mockito que respondem o que
o teste mandar), e roda em milissegundos. Teste de integração sobe o Spring e um banco de verdade (aqui, H2 em
memória) para provar que as peças funcionam juntas, por exemplo um rollback. Analogia: testar o freio na bancada x
testar o carro na pista. Ver os testes do
[CP1](../features/servicelifecycle/service-order-full-intake/implementation-plan.md).

### Teste de aceitação (ponta a ponta) e rastreabilidade

Teste que exercita o fluxo inteiro como o usuário faria (HTTP → caso de uso → banco), um por critério de aceite da
spec. Rastreabilidade é a tabela "critério → teste que o prova": mostra que nada da spec ficou sem verificação.
Analogia: o test-drive depois do conserto, com a ficha da OS na mão, item por item. Ver a tabela do
[CP6](../features/servicelifecycle/service-order-full-intake/implementation-plan.md).

### Condição de corrida e lock pessimista

Condição de corrida: duas requisições simultâneas veem "não existe" ao mesmo tempo e as duas tentam criar o mesmo
registro. Lock pessimista (`SELECT ... FOR UPDATE`) tranca a linha lida até o fim da transação. A constraint `UNIQUE`
do banco é a última barreira. Analogia: dois atendentes cadastrando o mesmo cliente; o sistema aceita um e o outro
recebe "já existe, tente de novo". Ver
[technical-spec da abertura completa](../features/servicelifecycle/service-order-full-intake/technical-spec.md).

### Validação em cascata (`@Valid`) e elemento nulo

`@Valid` numa lista manda o Bean Validation validar **cada objeto** dela, mas um elemento `null` não tem o que
validar e passa. Para rejeitá-lo, a anotação vai no tipo do elemento: `List<@NotNull IntakeServiceRequest>`.
Sem isso, `"services": [null]` virava `NullPointerException` e `500`. Analogia: conferir cada peça da caixa, mas não
perceber um espaço vazio onde deveria haver uma peça. Ver o achado do
[CP8](../features/servicelifecycle/service-order-full-intake/implementation-plan.md).

### Cobertura de código (JaCoCo)

Percentual do código executado pelos testes: instruções (bytecode), linhas e branches (cada caminho de um `if`).
O JaCoCo mede e o `./mvnw verify` falha abaixo de 80%. Cobertura alta não prova que o teste confere algo; ela
mostra o que **nunca** foi exercitado (aqui, o endereço do intake). Analogia: a lista de itens do check-up que o
mecânico nem chegou a olhar. Relatório em `target/site/jacoco/index.html`.

### OpenAPI e teste de contrato

OpenAPI é a descrição da API em JSON (`/v3/api-docs`), gerada pelo Springdoc a partir das anotações do controller; o
Swagger UI a desenha. O teste de contrato lê esse JSON e falha se um endpoint, um código de resposta ou um campo sumir
ou aparecer sem querer, como um `price` no body da abertura completa. Analogia: o catálogo de serviços impresso no
balcão, conferido contra o sistema antes de cada mudança. Ver `OpenApiContractTest` e o
[CP7](../features/servicelifecycle/service-order-full-intake/implementation-plan.md).

### Postman, collection e Newman

Postman é um cliente HTTP; a collection é o arquivo versionado com as requests, variáveis (`{{baseUrl}}`,
`{{authToken}}`) e scripts (`pre-request` prepara dados, `test` confere a resposta e guarda IDs). Newman executa a
mesma collection na linha de comando (`make e2e`), o que a transforma em smoke test. Analogia: o roteiro de
test-drive escrito, que qualquer mecânico repete igual. Ver
[a collection](../api/postman/workshop-management-system.postman_collection.json).

## CI/CD

### GHCR

GitHub Container Registry (`ghcr.io`): registry de imagens ligado à conta do GitHub, onde a pipeline publica a imagem
com tag pelo SHA. Analogia: estoque central de peças.

### Environment do GitHub

Agrupamento de secrets e variáveis que só os jobs que declaram `environment: <nome>` enxergam (aqui, `kind-ci` e
`aws-academy` no fork). Analogia: um cofre por filial.

### k6

Ferramenta de teste de carga com cenários em JavaScript e limiares de aprovação; roda pelo container `grafana/k6`.
Usada para provocar a escala do HPA.

### Smoke test _(introdução)_

Teste rápido e raso depois do deploy, que só confirma que o sistema "liga": a aplicação ficou pronta e uma chamada
básica à API responde. O nome vem da eletrônica: ligar o aparelho e ver se sai fumaça. Analogia: dar a partida no
carro depois do conserto, antes do test-drive completo.

### Runner do GitHub Actions _(introdução)_

Máquina virtual temporária, no datacenter do GitHub, que executa um job e é apagada no fim. Ela não enxerga a sua
rede de casa; por isso a pipeline cria o próprio cluster kind dentro dela. Ver
[ADR-008](../adr/ADR-008-estrategia-de-ambientes-kind-local-e-aws.md).

## AWS

### AWS Academy Learner Lab _(introdução)_

Conta AWS de estudo com crédito limitado, credenciais temporárias que expiram a cada sessão e permissões restritas.
Analogia: laboratório da faculdade, com equipamento real, mas com horário e regras. Ver
[ADR-008](../adr/ADR-008-estrategia-de-ambientes-kind-local-e-aws.md).

### IAM Role / LabRole _(introdução)_

Uma role do IAM é um conjunto de permissões ("o que este serviço pode fazer"). Não é caminho de rede: nenhuma
requisição passa por ela. No Learner Lab não se pode criar roles; usa-se a `LabRole` fornecida. Analogia: o crachá
do mecânico.

### Internet Gateway (IGW) _(introdução)_

Porta da VPC para a internet, por onde as requisições entram e as respostas voltam. Analogia: o portão principal
da oficina. Não existe no ambiente local.

### NAT Gateway _(introdução)_

Saída para a internet de quem está em uma subnet privada e precisa **iniciar** uma conexão (ex.: o node do EKS
puxando a imagem do registry). Não recebe conexões de fora e não é o caminho de volta das respostas. Analogia: porta
dos fundos só de saída. Tem custo por hora.

### EKS _(introdução)_

Kubernetes gerenciado pela AWS: ela opera o control plane (o "cérebro" do cluster). Analogia: terceirizar a gerência
do pátio. Planejado na [ADR-008](../adr/ADR-008-estrategia-de-ambientes-kind-local-e-aws.md).

### RDS _(introdução)_

Banco de dados gerenciado pela AWS (aqui, MySQL), com backup e manutenção feitos por ela. Analogia: arquivo de fichas
mantido por uma empresa especializada. Planejado na
[ADR-008](../adr/ADR-008-estrategia-de-ambientes-kind-local-e-aws.md).
