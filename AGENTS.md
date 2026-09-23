# AI Agents Guidelines & Skill Registry

This document guides AI coding assistants (Google Antigravity / AGY, GitHub Copilot, Claude Code, OpenAI GPT / Cursor) operating within the **SI600 Web Petshop** repository.

---

## Agent Skills Configuration

### Issue Tracker
Issues and Merge Requests live as GitLab issues under `si600-2026/turma-a/grupo-b/si600-web-petshop` (hosted on `gitlab.unicamp.br`). Use the `glab` CLI for all operations. See [docs/agents/issue-tracker.md](docs/agents/issue-tracker.md).

### Triage Labels
Canonical triage roles mapped to repository labels (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See [docs/agents/triage-labels.md](docs/agents/triage-labels.md).

### Domain Docs
Single-context layout with ubiquitous domain vocabulary in [CONTEXT.md](CONTEXT.md), architectural decisions in [docs/adr/](docs/adr/README.md), and standard RFC template in [docs/rfc/rfc-modelo.md](docs/rfc/rfc-modelo.md). See [docs/agents/domain.md](docs/agents/domain.md).

### Branching & Merge Request Strategy
Hierarchical branch model with mandatory Merge Requests:
- `main`: Production / final stable release (protected, direct push forbidden).
- `dev`: Active integration branch (protected, direct push forbidden).
- `member/<slug>`: Dedicated development branch for each team member.
- **Mandatory Flow**: `member/<slug>` -> `dev` (via feature MR) -> `main` (via release MR). Direct pushes to `dev` and `main` are strictly prohibited.
- **Mandatory Approvals**: All MRs require a minimum of 2 approvals from team members before merging. See [docs/branching-strategy.md](docs/branching-strategy.md).

---

## Mandatory Testing Directives

Per project architectural decisions, all agents and contributors MUST adhere strictly to the following testing standards:

1. **Only Integration and E2E Tests ([ADR 0001](docs/adr/0001-estrategia-de-testes-integracao-e-e2e.md))**:
   - **Do NOT write isolated unit tests with mocked databases or internal service mocks.**
   - All tests must verify behavior at public seams (API endpoints, database integration, middlewares, or browser-based E2E journeys).
2. **Tripartite Scenario Coverage ([ADR 0002](docs/adr/0002-padroes-de-cenarios-de-teste-bons-ruins-incompletos.md))**:
   Every test suite for an endpoint or business flow must include:
   - **Casos Bons (Happy Path)**: Valid inputs, expected 200/201 HTTP status, database persistence verified.
   - **Casos Ruins (Sad Path / Business Errors)**: Rule violations, conflicts (409), unauthorized/forbidden (401/403), not found (404), unprocessable entity (422), ensuring zero dirty database writes.
   - **Casos Incompletos (Boundary & Malformed Payloads)**: Missing required fields, null/empty values, boundary violations, invalid types, ensuring 400 Bad Request with detailed error matrices and zero unhandled 500 errors.

---

## Skill Registry

The repository provides 41 modular, cross-agent skills located under `.agents/skills/`:

### Quality Gatekeeper Skills
Skill Name | Path | Purpose
:--- | :--- | :---
**`ai-gatekeeper-reviewer`** | `.agents/skills/ai-gatekeeper-reviewer/SKILL.md` | Master Quality Gate reviewer orchestrating tests, SonarQube, diff analysis, and LangGraph evaluation.
**`sonarqube-runner`** | `.agents/skills/sonarqube-runner/SKILL.md` | Queries SonarQube/SonarCloud issues via REST API or triggers scanner scans.
**`context-harness`** | `.agents/skills/context-harness/SKILL.md` | Extracts repository rules and generates local vector embedding index (`context_harness.json`).

### Engineering Skills (Matt Pocock Suite)
Skill Name | Purpose
:--- | :---
**`setup-matt-pocock-skills`** | Scaffolds repository configuration for issue tracker, triage labels, and domain docs.
**`ask-matt`** | Guides agent and developer on which skill to use for any development situation.
**`code-review`** | Performs two-axis code review (Standards + Spec) using parallel sub-agents.
**`implement`** | Builds work described by specs or tickets, driving TDD and code review.
**`tdd`** | Test-driven development with red-green-refactor loop at public seams.
**`diagnosing-bugs`** | Disciplined debugging loop (failing integration test -> isolate -> hypothesis -> instrument -> fix).
**`domain-modeling`** | Challenges domain terminology and updates `CONTEXT.md` and ADRs.
**`to-spec`** / **`to-tickets`** | Synthesizes discussion into formal specs and tracer-bullet tickets.
**`triage`** | Moves GitLab issues through canonical triage state machine.
**`improve-codebase-architecture`** | Scans codebase for deepening opportunities and visual architecture reports.
**`wayfinder`** | Plans complex multi-session initiatives as dependency graphs on GitLab issues.
**`resolving-merge-conflicts`** | Resolves git merge conflicts hunk by hunk without aborting.
**`codebase-design`** | Reference guidelines on module depth, seams, interfaces, and locality.
**`prototype`** | Builds throwaway prototypes to explore UI or state models.
**`research`** | Investigates questions against primary sources and outputs cited markdown documents.
**`wizard`** | Creates interactive bash scripts for manual setup and credential provisioning.

### Productivity & Workflow Skills
Skill Name | Purpose
:--- | :---
**`grill-me`** / **`grill-with-docs`** | Relentless interactive interview resolving ambiguity and updating domain docs.
**`grilling`** | Core interview primitive for extracting requirements.
**`handoff`** / **`claude-handoff`** | Serializes session state into structured resumption files.
**`teach`** | Multi-session concept learning workflow.
**`to-questionnaire`** | Writes targeted questionnaires for external stakeholders.
**`wait-what`** | Corrective repitching of misunderstood concepts using domain glossary.
**`writing-for-agents`** | Reference manual for authoring agent-consumed documents.
**`refine-issue`** | Refines GitLab issue via grill-me interview into an authoritative RFC based on docs/rfc/rfc-modelo.md.
**`git-flow`** | Pre-commit & pre-MR quality gate: deterministic tests, AI Gatekeeper, and mandatory MR to dev.
**`retro`** | Runs retrospective analysis on completed initiatives.
**`implement-spec`** | Executes implementation directly from an approved specification.
**`setup-pre-commit`** | Configures pre-commit git hooks.
**`git-guardrails-claude-code`** | Safety guardrails for git commands.
**`loop-me`** | Autonomous agent iteration loop.

---

## Universal Review Workflow

Before committing changes or creating a Merge Request, execute the gatekeeper check:

```bash
bash .agents/skills/ai-gatekeeper-reviewer/scripts/run_review.sh --target .
```

To automatically apply remediation patches:
```bash
bash .agents/skills/ai-gatekeeper-reviewer/scripts/run_review.sh --target . --apply-patch
```

---

## Behavioral Standards
- **Zero Emojis**: Maintain professional technical output without emojis in documentation, code comments, commit messages, or reports.
- **Language**: Domain documentation, ADRs, and issues may be in Portuguese or technical English; code, comments, and commit messages should follow repository standards.
- **Verification**: Ensure integration and E2E test suites pass and satisfy ADR 0001 and ADR 0002 before completing tasks.
