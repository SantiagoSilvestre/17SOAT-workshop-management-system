# Workshop Management System

API REST para uma oficina mecânica, implementada como monólito modular com Java 21 e Spring Boot 4.

## Arquitetura

Os módulos do Spring Modulith seguem as fronteiras do domínio:

- `registration`: clientes, veículos e catálogo de serviços;
- `servicelifecycle`: ordens de serviço, diagnósticos, Estimates, execuções e técnicos;
- `stockprocurement`: estoque, reservas, demandas, compras, recebimentos e baixo estoque;
- `identity`: contas, credenciais, autenticação JWT e papéis de acesso.

O sistema é um único processo e deploy, mas cada módulo preserva suas camadas de domínio, aplicação e infraestrutura.
Integrações síncronas usam APIs Java públicas, ports e adapters in-process; reações assíncronas usam eventos Spring.

O MySQL 8.0 foi escolhido pela natureza relacional e transacional do domínio, garantindo integridade referencial e
operações atômicas para os fluxos de Ordem de Serviço e estoque. A decisão completa está na
[ADR-001](docs/adr/ADR-001-escolha-do-banco-de-dados.md).

Consulte [AGENTS.md](AGENTS.md) antes de alterar a aplicação.

## Documentação

- [Execução local com Docker Compose](docs/setup/local-docker-compose.md)
- [Configuração](docs/setup/configuration.md)
- [Verificação de saúde](docs/operations/health-checks.md)
- [CI/CD](docs/operations/ci-cd.md)
- [API: autenticação e recursos](docs/api/README.md)
- [Teste manual pelo Postman](docs/api/manual-testing.md)
- [Contribuindo](CONTRIBUTING.md)
