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

### Tag de imagem

Etiqueta que identifica uma versão de uma imagem (`app:3d0cdb3`). Sem tag, o Docker usa `latest`, um nome
reaproveitado que cada push sobrescreve. A tag pelo SHA do commit é única e rastreável: é o número de série da peça.
Decidida na [ADR-008](../adr/ADR-008-estrategia-de-ambientes-kind-local-e-aws.md).

## Kubernetes

### Pod _(introdução)_

Menor unidade que o Kubernetes executa; aqui, uma cópia da aplicação rodando. Pods são descartáveis: morrem, são
recriados e mudam de endereço. Analogia: um box de atendimento (ou uma sala de aula).

### Service _(introdução)_

Endereço fixo que recebe as requisições e as encaminha para qualquer pod disponível. Existe porque os pods mudam;
o cliente (Postman) chama sempre o Service e não precisa saber qual pod responde. Analogia: a secretaria da escola,
que manda o aluno para qualquer sala com aquela aula.

### kind _(introdução)_

"Kubernetes IN Docker": cria um cluster Kubernetes em que cada node é um container Docker. Usado no ambiente local e
dentro do job da pipeline. Alternativas: minikube e k3d. Escolhido na
[ADR-008](../adr/ADR-008-estrategia-de-ambientes-kind-local-e-aws.md).

### minikube

Outra ferramenta de Kubernetes local, focada na experiência de desenvolvimento: traz addons prontos
(`minikube addons enable metrics-server`) e atalhos de acesso (`minikube service`, `minikube tunnel`). O kind é mais
cru (instala-se tudo manualmente), mas é mais leve, sobe mais rápido e é o padrão em pipelines de CI. Analogia:
minikube é a oficina-escola com ferramentas já penduradas; kind é o galpão vazio que monta e desmonta rápido.

### Liveness x readiness _(introdução)_

Duas perguntas diferentes que o Kubernetes faz à aplicação. **Liveness**: "você está vivo?"; se não, o pod é
reiniciado. **Readiness**: "você consegue atender agora?"; se não, sai do Service até melhorar. A liveness não deve
depender do banco, senão uma queda do banco reinicia todos os pods à toa. Analogia: o mecânico está no turno
(liveness) x tem as ferramentas para pegar um carro agora (readiness). Ver
[functional-spec da infra](../features/platform/kubernetes-infrastructure-delivery/functional-spec.md).

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

### Stack, state e lock file

Stack é uma pasta com `init/plan/apply` próprios (aqui, `cluster` e `database` por ambiente). O state é o inventário
do que o Terraform criou e pode conter segredos: nunca é versionado. O `.terraform.lock.hcl` fixa as versões dos
providers e **é** versionado. Analogia: inventário do almoxarifado × lista de fornecedores homologados. Ver
[technical-spec da infra](../features/platform/kubernetes-infrastructure-delivery/technical-spec.md).

## Aplicação

### Actuator

Módulo do Spring Boot que expõe endpoints operacionais prontos, como `/actuator/health/liveness` e `/readiness`, e já
sabe checar o banco. Analogia: o painel de luzes do carro.

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
