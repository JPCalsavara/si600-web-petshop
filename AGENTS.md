# AI Agents Guidelines & Skill Registry

This document guides AI coding assistants (Google Antigravity / AGY, GitHub Copilot, Claude Code, OpenAI GPT / Cursor) operating within the **SI600 Web Petshop** repository.

---

## 1. Core Operating Principles

Every AI agent working in this repository must operate strictly under the following four pillars:

1. **Deterministic Quality Gate First**: Always run tests and local Gatekeeper verification before committing or proposing a Merge Request.
2. **Strict Branching & Approval Compliance**: Direct pushes to `main` and `dev` are strictly forbidden. All integration flows through Merge Requests requiring a minimum of 2 human team approvals.
3. **Seam-Level Integration Testing (ADR 0001 & ADR 0002)**: Never write isolated unit tests with mocked databases or mocked internal services. Test at public HTTP seams and database integrations covering Happy, Sad, and Malformed scenarios.
4. **Zero Emojis & Professional Tone**: Maintain professional technical output without emojis in documentation, code comments, commit messages, or reports.

---

## 2. Standard CI/CD Pipeline & Quality Gate

Due to runner constraints on `gitlab.unicamp.br`, the repository employs a cloud-based hybrid CI/CD bridge hosted on GitHub Actions (`.github/workflows/gatekeeper-ci.yml`) synchronized bidirectionally with GitLab Unicamp.

### Pipeline Architecture
```text
[Developer push origin]
         │
         ├──> [GitLab Unicamp] (Source of truth for Issues & MRs)
         │           ▲
         │           │ (Status update & MR review comment via gitlab_reporter.py)
         └──> [GitHub Mirror]
                     │ (Triggers GitHub Actions)
                     ▼
         ┌────────────────────────────────────────────────────────┐
         │ Stage 1: Build & Automated Tests                       │
         │ - Java 21 / Spring Boot integration tests              │
         │ - Frontend linting & Cypress headless tests            │
         ├────────────────────────────────────────────────────────┤
         │ Stage 2: SonarCloud Static Analysis                    │
         │ - Quality Gate, bugs, vulnerabilities, code smells     │
         ├────────────────────────────────────────────────────────┤
         │ Stage 3: AI Gatekeeper Reviewer (LangGraph + Gemini)   │
         │ - Evaluates diff against CONTEXT.md, ADRs, standards   │
         │ - Enforces tripartite tests & zero unhandled 500s      │
         ├────────────────────────────────────────────────────────┤
         │ Stage 4: GitLab Reporter                               │
         │ - Sets commit status (success/failed) on GitLab        │
         │ - Posts structured review markdown on the active MR    │
         └────────────────────────────────────────────────────────┘
```

### Required Secrets (Configured on GitHub Actions)
* `GOOGLE_API_KEY`: Gemini API key for LangGraph gatekeeper analysis.
* `SONAR_TOKEN` & `SONAR_PROJECT_KEY`: SonarCloud credentials (`JPCalsavara_si600-web-petshop`).
* `GITLAB_TOKEN` & `GITLAB_PROJECT_ID`: GitLab Unicamp API token (`glpat-...`) and project ID `6372`.
* `GITLAB_URL`: `https://gitlab.unicamp.br`.

---

## 3. Branching Strategy & Merge Request Policy

### Branch Hierarchy
* **`main`**: Production / final stable release. Protected branch. Direct push is strictly blocked.
* **`dev`**: Active integration branch for the entire team. Protected branch. Direct push is strictly blocked.
* **`member/<slug>`**: Dedicated development branch for each team member (e.g., `member/joao-calsavara`, `member/felipe-moreira`, `member/gabriel-santos`, `member/julyo-silva`, `member/lorenzo-pugina`, `member/samuel-souza`, `member/samuel-martins`).

### Mandatory Workflow
1. Integrant checks out `dev`, pulls latest changes, and merges into their `member/<slug>`.
2. All feature work is implemented in `member/<slug>`.
3. Before pushing, the developer runs the local Gatekeeper check:
   ```bash
   bash .agents/skills/ai-gatekeeper-reviewer/scripts/run_review.sh --target .
   ```
4. Push to remote:
   ```bash
   git push origin member/<slug>
   ```
   *(Pushes simultaneously to GitLab Unicamp and GitHub mirror).*
5. Open a Merge Request on GitLab Unicamp from `member/<slug>` targeting `dev`.
6. **Mandatory 2-Approval Rule**: The MR requires at least **2 approvals from different team members** before merging. Author self-approval is forbidden.
7. Sprint / Milestone Release: Open an MR from `dev` to `main`, requiring at least 2 approvals and a passing CI Quality Gate.

See full specification in [docs/branching-strategy.md](docs/branching-strategy.md).

---

## 4. Development & Stack Standards

### Official Technology Stack
* **Backend**: Java 21 LTS with Spring Boot (Spring Web, Spring Data JPA, Bean Validation, PostgreSQL driver).
* **Frontend**: React 18+ SPA built with Vite and TypeScript / JavaScript.
* **Database**: PostgreSQL 16 managed via `docker-compose.yml` (`localhost:5432`, db: `petshop_db`, user: `petshop_user`, pass: `petshop_pass`).
* **E2E & Component Testing**: Cypress for end-to-end user journey validation and critical component tests.

### Architecture & Conventions
* **Backend Layering**:
  - `controller`: Exposes REST endpoints under `/api/...` with Bean Validation annotations (`@Valid`, `@NotNull`, `@NotBlank`, `@Size`, `@Min`).
  - `service`: Encapsulates business logic, state transitions, and transactional boundaries (`@Transactional`).
  - `repository`: Spring Data JPA interfaces.
  - `model` / `entity`: JPA entities mapping the ubiquitous language in [CONTEXT.md](CONTEXT.md).
  - `dto`: Request and response records/classes decoupled from entities.
  - `exception`: Global exception handler (`@RestControllerAdvice`) returning RFC 7807 (`ProblemDetail`) payloads for all 4xx/5xx responses.
* **Frontend Architecture**:
  - Modular component structure (`src/components`, `src/pages`, `src/services`, `src/types`).
  - Typed HTTP client consuming `/api/...` endpoints with centralized error handling.
  - CORS properly configured on Spring Boot to allow `http://localhost:5173`.

---

## 5. Mandatory Testing Directives

Per project architectural decisions, all agents and contributors MUST adhere strictly to the following testing standards:

1. **Only Integration and E2E Tests ([ADR 0001](docs/adr/0001-estrategia-de-testes-integracao-e-e2e.md))**:
   - **Do NOT write isolated unit tests with mocked databases, mocked EntityManager, or mocked internal service beans.**
   - All tests must verify behavior at public seams (API endpoints via `@SpringBootTest`, real database persistence, or browser journeys via Cypress).
2. **Tripartite Scenario Coverage ([ADR 0002](docs/adr/0002-padroes-de-cenarios-de-teste-bons-ruins-incompletos.md))**:
   Every test suite for an endpoint or business flow must include:
   - **Casos Bons (Happy Path)**: Valid inputs, expected 200/201 HTTP status, database persistence and side effects verified.
   - **Casos Ruins (Sad Path / Business Errors)**: Rule violations, conflicts (409), unauthorized/forbidden (401/403), not found (404), unprocessable entity (422), ensuring transaction rollback and zero dirty database writes.
   - **Casos Incompletos (Boundary & Malformed Payloads)**: Missing required fields, null/empty values, boundary violations, invalid types, ensuring 400 Bad Request with RFC 7807 error matrices and zero unhandled 500 errors.

---

## 6. Skill Registry

The repository provides modular, cross-agent skills located under `.agents/skills/`:

### Quality Gatekeeper Skills
Skill Name | Path | Purpose
:--- | :--- | :---
**`ai-gatekeeper-reviewer`** | `.agents/skills/ai-gatekeeper-reviewer/SKILL.md` | Master Quality Gate reviewer orchestrating tests, SonarQube, diff analysis, and LangGraph evaluation.
**`sonarqube-runner`** | `.agents/skills/sonarqube-runner/SKILL.md` | Queries SonarQube/SonarCloud issues via REST API or triggers scanner scans.
**`context-harness`** | `.agents/skills/context-harness/SKILL.md` | Extracts repository rules and generates local vector embedding index (`context_harness.json`).

### Engineering Skills (Matt Pocock Suite)
Skill Name | Purpose
:--- | :---
**`refine-issue`** | Refines GitLab issue via grill-me interview into an authoritative RFC based on `docs/rfc/rfc-modelo.md`.
**`git-flow`** | Pre-commit & pre-MR quality gate: deterministic tests, AI Gatekeeper, and mandatory MR to dev.
**`tdd`** | Test-driven development with red-green-refactor loop at public seams.
**`code-review`** | Performs two-axis code review (Standards + Spec) using parallel sub-agents.
**`implement`** | Builds work described by specs or tickets, driving TDD and code review.
**`diagnosing-bugs`** | Disciplined debugging loop (failing integration test -> isolate -> hypothesis -> instrument -> fix).
**`domain-modeling`** | Challenges domain terminology and updates `CONTEXT.md` and ADRs.
**`codebase-design`** | Reference guidelines on module depth, seams, interfaces, and locality.

---

## 7. Universal Review Workflow

Before committing changes or creating a Merge Request, execute the gatekeeper check:

```bash
bash .agents/skills/ai-gatekeeper-reviewer/scripts/run_review.sh --target .
```

To automatically apply remediation patches:
```bash
bash .agents/skills/ai-gatekeeper-reviewer/scripts/run_review.sh --target . --apply-patch
```
