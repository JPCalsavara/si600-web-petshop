# SI600 - Web Petshop (Turma A - Grupo B)

Sistema de gestão e comércio eletrônico para petshop desenvolvido na disciplina SI600 da Faculdade de Tecnologia da UNICAMP (FT/UNICAMP).

---

## 1. Visão Geral da Arquitetura

O sistema é construído sobre uma arquitetura cliente-servidor desacoplada com persistência relacional:

* **Backend:** Java 21, Spring Boot (Spring Web, Spring Data JPA, Bean Validation).
* **Frontend:** React 18+ com Vite e TypeScript / JavaScript.
* **Banco de Dados:** PostgreSQL 16 (executado via Docker Compose).
* **Testes:**
  * Backend: Testes de integração com `@SpringBootTest` e `Testcontainers` / banco real (conforme ADR 0001 e ADR 0002).
  * Frontend / E2E: Cypress cobrindo jornadas de ponta a ponta e integração com o backend.
* **Qualidade e Governança:**
  * SonarCloud (Análise Estática de Código).
  * AI Gatekeeper Reviewer (Orquestrador LangGraph com Google Gemini).
  * CI/CD via GitHub Actions espelhado com GitLab Unicamp.

---

## 2. Pré-requisitos de Desenvolvimento

Para executar e contribuir com o projeto, instale:

* **Java Development Kit (JDK):** Versão 21 LTS (ex: Eclipse Temurin).
* **Node.js:** Versão 20 LTS ou superior e gerenciador `npm`.
* **Docker e Docker Compose:** Para subir o banco PostgreSQL e serviços auxiliares.
* **Git:** Para versionamento e fluxo de branches.

---

## 3. Estrutura do Repositório

```text
si600-web-petshop/
├── .agents/                    # Skills e automações de engenharia e revisão de IA
├── .github/workflows/          # Pipelines de CI/CD (AI Gatekeeper, SonarCloud, Testes)
├── backend/                    # Aplicação Spring Boot (Java 21)
├── frontend/                   # Aplicação Web React (Vite)
├── cypress/                    # Testes End-to-End (E2E)
├── docs/                       # Documentação técnica e governança
│   ├── adr/                    # Architecture Decision Records (ADRs)
│   ├── rfc/                    # Request for Comments / Especificações técnicas
│   ├── gatekeeper/             # Código-fonte do AI Gatekeeper Reviewer
│   ├── branching-strategy.md   # Política de branches e aprovações
│   └── ci-cd-gitlab-github-bridge.md # Arquitetura do pipeline espelhado
├── docker-compose.yml          # Definição do PostgreSQL local
├── CONTEXT.md                  # Glossário ubíquo e visão de domínio do projeto
├── AGENTS.md                   # Diretrizes para assistentes de IA e desenvolvedores
└── README.md                   # Guia de início rápido e execução
```

---

## 4. Como Executar o Ambiente Localmente

### Passo 1: Subir o Banco de Dados (PostgreSQL)

Na raiz do repositório, execute:

```bash
docker compose up -d postgres
```

O PostgreSQL estará disponível em `localhost:5432` com as credenciais padrão:
* **Database:** `petshop_db`
* **Username:** `petshop_user`
* **Password:** `petshop_pass`

---

### Passo 2: Executar o Backend (Spring Boot)

Acesse a pasta do backend e inicie a aplicação com o Maven Wrapper:

```bash
cd backend
./mvnw spring-boot:run
```

A API estará acessível em `http://localhost:8080/api`.
* Endpoint de verificação de integridade (Health Check): `GET http://localhost:8080/api/health`

---

### Passo 3: Executar o Frontend (React + Vite)

Em outro terminal, acesse a pasta do frontend, instale as dependências e inicie o servidor de desenvolvimento:

```bash
cd frontend
npm install
npm run dev
```

A aplicação web estará acessível em `http://localhost:5173`.

---

### Passo 4: Executar os Testes

#### Testes de Integração do Backend
```bash
cd backend
./mvnw test
```

#### Testes End-to-End com Cypress
```bash
cd frontend
# Modo interativo com interface gráfica:
npx cypress open

# Modo headless (linha de comando / CI):
npx cypress run
```

---

## 5. Diretrizes de Qualidade e Testes

O projeto segue duas Decisões Arquiteturais obrigatórias:

1. **Apenas Testes de Integração e E2E ([ADR 0001](docs/adr/0001-estrategia-de-testes-integracao-e-e2e.md)):**
   * É proibida a escrita de testes unitários isolados com mocks de banco de dados ou services internos.
   * Todos os testes devem validar o comportamento nas costuras públicas (endpoints HTTP, persistência real no banco de dados e fluxos no navegador).
2. **Cobertura Tripartite de Cenários ([ADR 0002](docs/adr/0002-padroes-de-cenarios-de-teste-bons-ruins-incompletos.md)):**
   Cada funcionalidade deve contemplar:
   * **Casos Bons (Happy Path):** Entradas válidas, HTTP 200/201, persistência confirmada.
   * **Casos Ruins (Sad Path / Erros de Negócio):** Violação de regras de negócio, HTTP 401/403/404/409/422 sem escrita suja no banco.
   * **Casos Incompletos (Payloads malformados ou incompletos):** Campos obrigatórios ausentes, tipos inválidos, limites ultrapassados, garantindo HTTP 400 Bad Request e zero erros 500 não tratados.

---

## 6. Fluxo de Trabalho Git e Contribuição

### Estrutura de Branches
* `main`: Produção / release estável. Protegida contra push direto.
* `dev`: Integração contínua da equipe. Protegida contra push direto.
* `member/<nome-sobrenome>`: Branch pessoal de cada integrante para desenvolvimento de features.

### Fluxo Obrigatório de Merge Requests
1. O desenvolvedor implementa a funcionalidade em sua branch `member/<nome-sobrenome>`.
2. Executa a validação local do Gatekeeper:
   ```bash
   bash .agents/skills/ai-gatekeeper-reviewer/scripts/run_review.sh --target .
   ```
3. Realiza o push para a sua branch pessoal:
   ```bash
   git push origin member/<nome-sobrenome>
   ```
4. Abre um Merge Request para a branch `dev`.
5. **Aprovação Obrigatória:** O MR requer no mínimo **2 aprovações** de outros membros da equipe antes do merge.
6. A cada fechamento de Sprint/Release, é aberto um MR de `dev` para `main` com 2 aprovações requeridas.

Consulte os detalhes em [docs/branching-strategy.md](docs/branching-strategy.md).

---

## 7. Pipeline de CI/CD e Governança

Devido a restrições de runners compartilhados no GitLab da Unicamp, o projeto opera um pipeline híbrido espelhado:
* Todo `git push` no remote `origin` atualiza simultaneamente o GitLab Unicamp e o repositório espelho no GitHub.
* O GitHub Actions dispara o pipeline de validação contendo:
  * Análise estática no **SonarCloud**.
  * Execução dos testes automatizados.
  * Análise semântica e arquitetural com o **AI Gatekeeper Reviewer** (Google Gemini).
  * Envio automático do status e relatório de revisão para o GitLab Unicamp.

Consulte os detalhes em [docs/ci-cd-gitlab-github-bridge.md](docs/ci-cd-gitlab-github-bridge.md).

---

## 8. Equipe do Projeto (Grupo B)

* Felipe Ferreira Moreira (`member/felipe-moreira`)
* Gabriel da Silva Santos (`member/gabriel-santos`)
* João Pedro Leite Calsavara (`member/joao-calsavara`)
* Julyo Cesar Silva dos Santos (`member/julyo-silva`)
* Lorenzo de Oliveira Pugina (`member/lorenzo-pugina`)
* Samuel Alcantara de Souza (`member/samuel-souza`)
* Samuel Martins dos Santos (`member/samuel-martins`)
