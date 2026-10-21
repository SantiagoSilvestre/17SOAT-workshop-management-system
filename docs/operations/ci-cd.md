# CI/CD

## Integração contínua

O workflow `CI` executa o check único `Quality gate` em Pull Requests e pushes para `dev` e `main`, usando Temurin 21 e
o Maven Wrapper. No resumo de cada execução, a seção **Artifacts** disponibiliza por 30 dias:

- `test-reports-*`, com os relatórios Surefire/Failsafe produzidos;
- `jacoco-report-*`, com os relatórios HTML, XML e CSV do JaCoCo.

Um novo commit cancela a execução obsoleta da mesma proposta. A proteção das branches deve exigir o contexto exato
`Quality gate`; renomear o job requer coordenar antes a regra correspondente no GitHub.

### Scan de segurança

O workflow `Security scan` é separado do `CI` de propósito: ele não participa do check obrigatório `Quality gate`, para
que os scans não deixem toda Pull Request mais lenta nem a bloqueiem por uma CVE publicada em biblioteca de terceiros.
Ele é disparado manualmente (**Actions → Security scan → Run workflow**) e tem dois jobs:

- `Dependency-Check (SCA)`, que analisa as dependências do `pom.xml`. Requer o secret `NVD_API_KEY`;
- `OWASP ZAP (DAST)`, que sobe a aplicação via Docker Compose e a escaneia a partir do contrato OpenAPI, autenticado
  como `ADMIN`.

Os artifacts ficam disponíveis por 30 dias:

- `dependency-check-report-*`, com o relatório HTML/JSON do OWASP Dependency-Check;
- `zap-report-*`, com o relatório HTML/JSON do OWASP ZAP.

**O output bruto dessas ferramentas nunca é commitado**. Isso evita registrar no histórico do Git um mapa detalhado de
vulnerabilidades por versão, inclusive depois de uma correção. A análise narrativa fica em
`docs/security/vulnerability-report.md` e referencia qual execução de CI gerou cada evidência.

Para rodar localmente:

```bash
export NVD_API_KEY=<chave da NVD>
make sca            # analise de dependencias

make docker-up      # sobe a aplicacao
make dast           # escaneia a API em execucao; use DAST_BASE_URL=... para outro host
```

Se a porta 3306 já estiver ocupada, use `DB_PORT=3307 make docker-up`. No Git Bash do Windows o alvo `dast` já define
`MSYS_NO_PATHCONV=1`, sem o qual o volume do ZAP não é montado.

## Pipeline de entrega (CD)

O workflow `CD` (`.github/workflows/cd.yml`) entrega a aplicação num cluster kind criado dentro do próprio runner e
descartado no fim. Ele roda em pushes na branch de entrega, num fork onde o desenvolvedor é dono (environments e
secrets exigem admin). O `ci.yml` não muda.

| Job | Depende de | O que faz |
|---|---|---|
| `Build and test` | — | `./mvnw clean verify` (testes e gate JaCoCo) |
| `Build and push image` | `Build and test` | `docker build` e push de `ghcr.io/<dono em minúsculas>/workshop-management-system:<sha do commit>` com o `GITHUB_TOKEN` |
| `Deploy to kind and smoke test` | `Build and push image` | Terraform `infra/local/cluster` e `infra/local/database`, `secrets.env` a partir do environment, `kubectl apply -k k8s/overlays/local` com a imagem do SHA e smoke test |

O smoke test espera o rollout, exige readiness `UP`, faz login com o admin de bootstrap e chama
`GET /api/service-orders` com o token (deve dar `200`). Se algo falhar, o step `Diagnostics` mostra pods, `describe` e
as últimas 200 linhas do log do app. Uma falha em `Build and test` faz os outros dois jobs serem pulados.

**Nome da branch de entrega.** Ele aparece **só** em `on.push.branches` no topo do `cd.yml` (hoje
`feat/terraform-san`). Para trocar, edite essa linha. Registry, nome da imagem e dono vêm de `env:` e de
`github.repository_owner`, então o mesmo arquivo funciona no fork e no repositório do grupo.

**Configuração do fork (uma vez):**

1. Fork do repositório na conta pessoal e, na aba **Actions**, habilitar os workflows.
2. **Settings → Environments → New environment** `kind-ci`, com os secrets `APP_SECURITY_JWT_SECRET` e
   `APP_SECURITY_ESTIMATE_GATEWAY_HMAC_SECRET`. Use valores aleatórios novos de pelo menos 32 bytes (o JWT recusa
   chaves menores), por exemplo no PowerShell:
   `$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)`.
3. `git remote add fork https://github.com/<usuario>/17SOAT-workshop-management-system.git` e
   `git push -u fork <branch de entrega>`.
4. Conferir se o pacote `workshop-management-system` em **Packages** está público (D9). Publicado pelo
   `GITHUB_TOKEN`, ele herda a visibilidade do repositório. O kind do runner puxa a imagem sem login.

Uma execução completa leva ~6 min (meta: até 40 min).
