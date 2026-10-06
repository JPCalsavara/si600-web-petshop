# SI600 - Sistema de Eventos (Turma A - Grupo B)

Um sistema de automação para pré-cotação/cotação do aluguel de estandes em eventos desenvolvido na disciplina SI600 da Faculdade de Tecnologia da UNICAMP (FT/UNICAMP).

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
si600-web-eventos/
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

Existem duas formas de rodar o projeto localmente: executando toda a infraestrutura de uma vez via Docker Compose (recomendado) ou executando os serviços individualmente.

### Opção 1: Subir tudo via Docker Compose (Recomendado)

Na raiz do repositório, onde está o arquivo `docker-compose.yml`, execute:

```bash
docker compose up --build
```

Isso subirá simultaneamente:
1. O banco de dados PostgreSQL (`localhost:5432`).
2. O Backend Spring Boot (`http://localhost:8080/api`).
3. O Frontend React com Vite Dev Server (`http://localhost:5173`).

A aplicação web estará imediatamente acessível no seu navegador em **http://localhost:5173**, e a comunicação com a API será resolvida automaticamente.

---

### Opção 2: Executar Individualmente (Modo Desenvolvimento Raiz)

Se preferir rodar as aplicações manualmente para ter mais controle ou usar debuggers das IDEs:

**1. Subir apenas o Banco de Dados (PostgreSQL):**
```bash
docker compose up -d postgres
```

**2. Executar o Backend (Spring Boot):**
```bash
cd backend
./mvnw spring-boot:run
```
*(A API estará acessível em `http://localhost:8080/api`)*

**3. Executar o Frontend (React + Vite):**
Em outro terminal, instale as dependências e inicie o Vite:
```bash
cd frontend
npm install
npm run dev
```
*(A aplicação web estará acessível em `http://localhost:5173`)*

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
   Cada funcionalidade deve contemplar as três frentes tanto nos **Testes de Integração (Backend)** quanto nos **Testes E2E (Frontend)**, cobrindo inclusive a exibição correta de erros na interface:
   * **Casos Bons (Happy Path):** Entradas válidas, HTTP 200/201, persistência confirmada e fluxo visual completo.
   * **Casos Ruins (Sad Path / Erros de Negócio):** Violação de regras de negócio, HTTP 401/403/404/409/422 sem escrita suja no banco, validando as mensagens de erro 4xx exibidas na tela para o usuário.
   * **Casos Incompletos (Payloads malformados ou incompletos):** Campos obrigatórios ausentes, tipos inválidos, limites ultrapassados, garantindo HTTP 400 Bad Request (zero erros 500) e feedback claro na interface.

---

## 6. Fluxo de Trabalho Git e Contribuição

### Estrutura de Branches
* `main`: Produção / release estável. Protegida contra push direto.
* `dev`: Integração contínua da equipe. Protegida contra push direto.
* `<tipo>/<ID-da-issue>-<titulo>`: Branch baseada na issue em que o integrante está trabalhando. Tipos válidos: `feat`, `fix`, `docs`, `chore`.

### Padrão de Commits
O padrão de commits segue o formato Conventional Commits adaptado com a issue:
`<tipo>(<ID-da-issue>): <descrição curta no imperativo>`

### Fluxo Obrigatório de Merge Requests (Padrão Guiado por IA)

O processo padrão da equipe utiliza a suíte de skills de Inteligência Artificial do repositório para garantir qualidade, aderência arquitetural e completude dos testes:

1. **Criação da Branch:** A partir da `dev`, o desenvolvedor ou agente cria a nova branch `<tipo>/<ID-da-issue>-<titulo>`.
2. **Fase 1: Refinamento (Skill `refine-issue`):** Invoque o agente com a skill `refine-issue` ou inicie um `/grill-me` sobre a issue. O agente entrevistará o usuário, fará as decisões de design e gerará automaticamente a **RFC** baseada no modelo `docs/rfc/rfc-modelo.md` como o primeiro commit da branch (Obrigatório conforme **ADR 0006**).
3. **Fase 2: Desenvolvimento (Skill `feature-builder`):** Ative a skill `feature-builder` (ou `ui-builder` para frontend puro / `tdd` para rotinas isoladas). O orquestrador lerá a RFC e implementará o ciclo E2E (Testes de Integração Backend -> Endpoint -> Interface React) fazendo autocorreções até que os testes passem.
4. **Fase 3: Quality Gate (Skill `git-flow`):** Antes de empurrar o código, invoque o `git-flow`. Ele rodará o *AI Gatekeeper*, validará as métricas de código, corrigirá débitos e criará os commits semânticos automaticamente.
5. **Abertura do Merge Request:** O desenvolvedor ou o `git-flow` faz o push (`git push origin <branch>`) e abre o Merge Request apontando para `dev` com a opção *Squash and Merge*.
6. **Aprovação Obrigatória:** O MR exige a aprovação do Gatekeeper no pipeline (CI) e pelo menos **1 aprovação manual** de outro membro da equipe.

*(Para o processo puramente manual sem assistência da IA, as mesmas regras de qualidade e fluxo se aplicam — o desenvolvedor apenas executa manualmente os scripts do gatekeeper listados em `AGENTS.md`.)*

Consulte os detalhes em [docs/branching-strategy.md](docs/branching-strategy.md) e [docs/adr/0003-squash-and-merge.md](docs/adr/0003-squash-and-merge.md).

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

* Felipe Ferreira Moreira
* Gabriela Santos Januário
* Gabriel Matheus Pereira Dos Santos
* João Pedro Leite Calsavara
* Julyo Elias Hidalgo Da Silva
* Lorenzo De Oliveira Pugina
* Samuel Calegnan dos Santos Souza
* Samuel Lima Martins
