# Ambiente Kubernetes local (kind + Terraform)

Este guia sobe no seu computador o mesmo desenho usado na pipeline: um cluster Kubernetes
([kind](https://kind.sigs.k8s.io)), o MySQL e o metrics-server provisionados por Terraform, e a aplicação implantada
por Kustomize com probes, rolling update e escala automática (HPA). O desenho está na
[visão geral da arquitetura, seção 4.1](../architecture/overview.md#41-kubernetes-local-kind).

Para só desenvolver e chamar a API, o [Docker Compose](local-docker-compose.md) é mais rápido.

## Resumo

| # | Etapa | Comando | Tempo (1ª vez) |
| --- | --- | --- | --- |
| 1 | Criar o cluster | `terraform -chdir=infra/local/cluster apply` | 3 a 5 min |
| 2 | Criar o banco | `terraform -chdir=infra/local/database apply` | ~5 min |
| 3 | Implantar a aplicação | `make k8s-local-deploy` | ~2 min de build + até 3 min de boot |
| 4 | Verificar | `kubectl get pods,svc,hpa -n workshop` | — |
| 5 | Usar | Postman, Swagger, Mailpit, [teste de carga](../operations/load-testing-hpa.md) | — |
| 6 | Destruir | `destroy` do banco e depois do cluster | ~20 s |

Os alvos `make infra-local-up` e `make infra-local-down` executam as etapas 1–2 e 6 de uma vez, com `-auto-approve`.
Enquanto estiver aprendendo, prefira rodar o Terraform diretamente e ler cada `plan` antes de confirmar.

## Pré-requisitos

| Item | Exigência | Como verificar |
| --- | --- | --- |
| Docker Desktop (WSL2 no Windows) | rodando, com ≥ 6 GiB de memória e ≥ 4 CPUs | `docker info --format '{{.MemTotal}} {{.NCPU}}'` |
| Terraform | ≥ 1.10 | `terraform version` |
| kubectl | qualquer versão recente (vem com o Docker Desktop) | `kubectl version --client` |
| Portas livres no host | 8080 e 8025 (pare o Docker Compose antes: `docker compose down`) | — |

kind, Helm e k6 **não** precisam ser instalados: os providers do Terraform embutem o kind e o Helm, e o k6 roda em
container. No Windows, a memória do WSL2 é definida em `%UserProfile%\.wslconfig` (`memory=7GB` funcionou numa máquina
de 16 GB).

Todos os comandos abaixo rodam na raiz do repositório e funcionam no PowerShell e no Bash, exceto onde indicado.

## 1. Criar o cluster

```bash
terraform -chdir=infra/local/cluster init
terraform -chdir=infra/local/cluster plan
terraform -chdir=infra/local/cluster apply
```

`-chdir` executa o Terraform dentro da pasta da stack sem mudar o diretório atual. O `init` baixa os providers (só
na primeira vez ou quando as versões mudam); o `plan` mostra o que será criado; o `apply` mostra o plano de novo e
pede `yes`.

**Cria:**

| Recurso | Detalhe |
| --- | --- |
| Cluster kind `workshop` | 1 nó (`workshop-control-plane`), imagem `kindest/node:v1.35.0` fixada por digest |
| Mapeamento de portas | `localhost:8080` → NodePort 30080 (API) e `localhost:8025` → NodePort 30025 (Mailpit) |
| kubeconfig | Contexto `kind-workshop` gravado em `~/.kube/config` e definido como o atual |
| metrics-server | Chart Helm `3.14.0` no namespace `kube-system`, com `--kubelet-insecure-tls` (o kubelet do kind usa certificado autoassinado) |

**Verificar:**

```bash
kubectl get nodes          # workshop-control-plane   Ready
kubectl top nodes          # mostra CPU e memória; "metrics not available yet" some em ~1 min
```

> O `plan` e o `destroy` desta stack imprimem a chave de administrador do cluster, porque o provider `tehcyx/kind` não
> a marca como sensível. É aceitável num cluster local e descartável; não cole essa saída em lugares públicos.

## 2. Criar o banco

```bash
terraform -chdir=infra/local/database init
terraform -chdir=infra/local/database apply
```

Esta stack depende do cluster da etapa 1 (usa o contexto `kind-workshop`), por isso vem depois.

**Cria:**

| Recurso | Detalhe |
| --- | --- |
| Namespace `workshop` | Onde ficam o banco e a aplicação. É do Terraform, não do Kustomize: um `kubectl delete -k` nunca apaga o banco |
| Senhas | `random_password` para root e para o usuário da aplicação (24 caracteres) |
| Secret `mysql-root` | Senha de root do MySQL |
| ConfigMap `workshop-db` | `DB_URL` apontando para `mysql.workshop.svc.cluster.local:3306/workshop` |
| Secret `workshop-db` | `DB_USERNAME=workshop_user` e `DB_PASSWORD` |
| Service `mysql` | ClusterIP na porta 3306 (acessível só dentro do cluster) |
| StatefulSet `mysql` | `mysql:8.4`, readiness por `mysqladmin ping`, PVC `data` de 1 Gi |

O MySQL local roda com durabilidade relaxada (`--skip-log-bin`, `--innodb-flush-log-at-trx-commit=2`,
`--innodb-doublewrite=OFF`) para o primeiro boot da aplicação ficar mais rápido. Isso vale só para este ambiente
descartável; um banco real (como o RDS) mantém os padrões.

**Verificar:**

```bash
kubectl get statefulset,pvc -n workshop    # mysql 1/1; PVC data-mysql-0 Bound
```

O `apply` só termina quando o MySQL está pronto. Na primeira vez, ele baixa a imagem (~240 MB) e inicializa o banco.

## 3. Implantar a aplicação

### Com make (Bash, Git Bash, Linux, macOS)

```bash
make k8s-local-deploy
```

### Sem make (PowerShell)

```powershell
# Segredos locais: o secrets.env é ignorado pelo git; o modelo traz valores só para desenvolvimento
if (-not (Test-Path k8s/overlays/local/secrets.env)) {
    Copy-Item k8s/overlays/local/secrets.env.example k8s/overlays/local/secrets.env
}

# Imagem: build local e carga no containerd do nó do kind (é o que "kind load docker-image" faz por dentro)
docker build -t workshop-management-system:local .
$arch = docker version --format '{{.Server.Arch}}'
New-Item -ItemType Directory -Force target | Out-Null
docker save --platform "linux/$arch" -o target/workshop-app-local.tar workshop-management-system:local
docker cp target/workshop-app-local.tar workshop-control-plane:/var/tmp/workshop-app-local.tar
docker exec workshop-control-plane ctr --namespace=k8s.io images import /var/tmp/workshop-app-local.tar
docker exec workshop-control-plane rm /var/tmp/workshop-app-local.tar
Remove-Item target/workshop-app-local.tar

# Mapeamentos do simulador do fornecedor (mesmos arquivos do Docker Compose)
kubectl create configmap supplier-simulator-mappings -n workshop --from-file=docker/wiremock/mappings `
    --dry-run=client -o yaml | kubectl apply -f -

# Manifestos e espera pelo rollout
kubectl apply -k k8s/overlays/local
kubectl rollout status deployment/workshop-app -n workshop --timeout=300s
```

Por que cada detalhe:

- **`--platform` no `docker save`:** sem ele, o arquivo inclui manifestos de atestação sem conteúdo, e o `ctr import`
  falha.
- **`/var/tmp` e não `/tmp`:** dentro do nó do kind, `/tmp` é um `tmpfs` montado por cima do disco; o arquivo copiado
  ficaria invisível para o `ctr`.
- **`--dry-run=client -o yaml | kubectl apply -f -`:** gera o ConfigMap e o aplica, o que funciona tanto na criação
  quanto numa atualização (um `create` puro falharia na segunda vez).

**Ao reimplantar depois de mudar o código**, a tag `:local` não muda, então o Kubernetes não vê diferença no
Deployment. Depois do `kubectl apply -k`, force a troca dos pods (o `make k8s-local-deploy` já faz isso):

```bash
kubectl rollout restart deployment/workshop-app -n workshop
kubectl rollout status deployment/workshop-app -n workshop --timeout=300s
```

**Cria (Kustomize, `k8s/overlays/local`):**

| Recurso | Detalhe |
| --- | --- |
| Deployment `workshop-app` | Imagem `:local`, perfil `dev` com seeds, probes de startup/liveness/readiness, request 250m/768Mi e limit 1000m/1Gi |
| Service `workshop-app` | NodePort 30080 → porta 8080 dos pods prontos |
| HPA `workshop-app` | 1 a 3 pods, alvo de 70% de CPU |
| ConfigMap `workshop-app-config` e Secret `workshop-app` | Configuração e segredos (ver [Configuração](configuration.md)) |
| Mailpit | Deployment + Service NodePort 30025 |
| Simulador do fornecedor | Deployment + Service ClusterIP, com os mapeamentos do ConfigMap acima |

## 4. Verificar

```bash
kubectl get pods,svc,hpa -n workshop
curl http://localhost:8080/actuator/health/readiness
```

Resultado esperado:

- Pods `workshop-app-...`, `mailpit-...`, `supplier-simulator-...` e `mysql-0` em `1/1 Running`, com 0 restarts.
- Readiness: `{"status":"UP"}`.
- HPA com `TARGETS` em percentual de CPU (por exemplo, `cpu: 2%/70%`) e `REPLICAS 1`.

Com o banco vazio, o primeiro boot leva 2 a 3 minutos (o Flyway aplica todas as migrations). Nesse intervalo, o pod
fica `0/1 Running` sem reiniciar: a `startupProbe` tolera até 5 minutos. Com o banco já migrado, o boot leva ~30 s.

## 5. Usar

| Serviço | URL |
| --- | --- |
| API | `http://localhost:8080/api` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Mailpit | `http://localhost:8025` |

Os endereços são os mesmos do Docker Compose, então a collection Postman funciona sem nenhuma mudança: siga o
[teste manual](../api/manual-testing.md) ou rode `make e2e`. Para ver o HPA escalar, siga o
[teste de carga](../operations/load-testing-hpa.md).

O acesso passa por `localhost:8080` → NodePort → Service, que distribui as requisições entre **todos** os pods prontos.
Por isso não se usa `kubectl port-forward` aqui: ele prende o tráfego num único pod e esconde a escala.

## 6. Destruir

Ao terminar, libere memória e disco. A ordem importa: **primeiro o banco, depois o cluster**.

```bash
terraform -chdir=infra/local/database destroy
terraform -chdir=infra/local/cluster destroy
```

A stack `database` precisa do cluster para conseguir apagar seus recursos. Se o cluster for destruído antes, o
`destroy` do banco falha sem apagar nada e o state fica órfão (veja [Problemas comuns](#problemas-comuns)).

Opções mais leves:

| Objetivo | Comando | Mantém |
| --- | --- | --- |
| Remover só a aplicação | `kubectl delete -k k8s/overlays/local` | Namespace, MySQL e dados |
| Liberar a memória do Windows | Fechar o Docker Desktop e rodar `wsl --shutdown` | Nada em execução; o cluster volta ao reabrir o Docker |

## Problemas comuns

| Sintoma | Causa provável | O que fazer |
| --- | --- | --- |
| `apply` do cluster falha com porta em uso | Docker Compose (ou outro processo) usando 8080 ou 8025 | `docker compose down` e repetir |
| `kubectl` responde de outro cluster | Contexto atual não é o do kind | `kubectl config use-context kind-workshop` |
| Pod do app `0/1 Running` por minutos | Primeiro boot com banco vazio (Flyway) | Aguardar até 5 min; acompanhar com `kubectl logs -f deployment/workshop-app -n workshop` |
| Pod do app em `CrashLoopBackOff` | O processo Java terminou no boot (ex.: banco inacessível) | `kubectl logs deployment/workshop-app -n workshop --previous`; corrigir; se foi um deploy ruim, `kubectl rollout undo deployment/workshop-app -n workshop` |
| Pod em `ErrImagePull` ou `ImagePullBackOff` | A imagem `:local` não foi carregada no nó, então o Kubernetes tenta baixá-la do Docker Hub, onde ela não existe | Repetir a carga da imagem (etapa 3); conferir com `docker exec workshop-control-plane crictl images` |
| HPA com `<unknown>/70%` | metrics-server ainda coletando (~1 min após subir o pod) | Aguardar; se persistir, `kubectl top pods -n workshop` |
| `destroy` do banco falha com `connection refused` | O cluster foi destruído antes | O state ficou órfão: apague `infra/local/database/terraform.tfstate*` (ambiente local e descartável) |
| Tudo lento, pods `Pending` | Pouca memória para o WSL2 | Aumentar `memory` no `.wslconfig`, `wsl --shutdown` e reabrir o Docker |

## Segurança

- O `secrets.env` e os `terraform.tfstate` (que contêm as senhas geradas) são ignorados pelo git. Nunca os versione.
- Os valores de `secrets.env.example` servem só para este cluster local.
- A conta `admin`/`changeme123`, criada por migration, existe em todos os ambientes. Aqui ela só é acessível por
  `localhost`.
