# RFC: US-03 - Criação Inicial do Projeto Backend

| **Status** | Implementado |
|---|---|
| **Data** | 06/10/2026 |
| **Versão** | 1.0 |

---

## Contextualização

### Entendendo o problema
Antes de qualquer funcionalidade de negócio ser desenvolvida, é necessário estabelecer a base do projeto backend, configurar o banco de dados e garantir que as ferramentas de CI/CD consigam compilar, testar e analisar o código de forma automatizada e sem fricção.

### Explicando a solução de forma macro
Configuração do Spring Boot com Java 21, Spring Data JPA, Testcontainers para testes de integração com banco real, e a base de CI (SonarCloud, GitHub Actions, LangGraph Gatekeeper).

---

## Implementação

Esta US não envolve a criação de rotas de negócio, mas sim as fundações arquiteturais:
- Inicialização do Spring Boot via Maven.
- Configuração de Flyway/Hibernate para gerenciamento de schema.
- Configuração global de tratamento de exceções (Global Exception Handler com `ProblemDetail` - RFC 7807).
- `docker-compose.yml` para banco de dados local.

### Principal Desafio
Garantir que a arquitetura híbrida de CI (GitLab -> GitHub -> Sonar) funcionasse de maneira fluida para todos os desenvolvedores. Resolvido pelo isolamento das etapas no GitHub Actions e sync bidirecional.
