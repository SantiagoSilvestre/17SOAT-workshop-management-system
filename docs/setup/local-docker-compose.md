# Execução local com Docker Compose

Para executar com Docker, são necessários Docker Compose e GNU Make. Java 21 é necessário apenas para executar a
aplicação fora dos containers com `make run-dev`.

```bash
make docker-up
```

Caso o GNU Make não esteja disponível, execute diretamente:

```bash
docker compose up -d --build
```

**Tempo de inicialização:** todos os containers iniciam automaticamente. O MySQL pode levar até 90 segundos para se tornar saudável; o app só é iniciado depois disso (`depends_on` com `condition: service_healthy` no `docker-compose.yml`). A aplicação fica pronta em aproximadamente 3 minutos. Para monitorar o progresso:

```bash
docker logs -f workshop-app
```

A aplicação está pronta quando `docker compose ps` mostra `workshop-app` como `(healthy)`: o healthcheck do compose
chama o mesmo endpoint de readiness usado pelo Kubernetes (ver
[Verificação de saúde](../operations/health-checks.md)).

O ambiente Docker local utiliza o perfil `dev` e carrega dados de demonstração idempotentes de Customer e Stock Item.
Copie `.env.example` para `.env` para alterar esse comportamento. Os seeds ficam desativados no perfil padrão da
aplicação.

URLs úteis:

- API base: `http://localhost:8080/api`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- External Supplier System simulator (WireMock): `http://localhost:8089`

Execute `make help` para consultar comandos de Docker e banco de dados. `make docker-reset` remove explicitamente o
volume local do banco e é necessário uma vez ao adotar o baseline inicial do Flyway sobre uma base criada antes pelo
Hibernate.

## Start

```bash
cp .env.example .env
make docker-up
make docker-logs
```

The compose stack exposes the application on port `8080` and MySQL on port `3306` by default. MySQL data is retained in
the `mysql_data` volume.

The local defaults enable `SPRING_PROFILES_ACTIVE=dev` and `APP_SEED_ENABLED=true`. Change either value in `.env` to run
without demonstration records. Flyway applies schema migrations before Hibernate validates the mappings.

## Commands

```bash
make docker-ps
make docker-logs
make db-shell
make docker-down
```

After migrating a database previously managed by `ddl-auto=update`, recreate it once:

```bash
make docker-reset
make docker-up
```

`docker-reset` deletes the local MySQL volume. It is never executed by build, test or startup targets.
