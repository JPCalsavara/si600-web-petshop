# Domain Docs

How engineering, testing, and review skills consume this repository's domain documentation when exploring the codebase.

## Before exploring, read these

- **`CONTEXT.md`** at the repo root — ubiquitous language, petshop entities, business invariants, and architecture seams.
- **`docs/adr/`** — architectural decision records governing testing strategy (`0001-estrategia-de-testes-integracao-e-e2e.md`), scenario coverage patterns (`0002-padroes-de-cenarios-de-teste-bons-ruins-incompletos.md`), and system design.
- **`docs/guidelines.md`** — coding standards and architecture rules.

If any of these files do not exist, proceed silently. The `/domain-modeling` skill creates or updates them when terms or decisions are resolved.

## File structure

Single-context repository layout:

```text
/
├── CONTEXT.md
├── docs/adr/
│   ├── README.md
│   ├── 0001-estrategia-de-testes-integracao-e-e2e.md
│   └── 0002-padroes-de-cenarios-de-teste-bons-ruins-incompletos.md
├── docs/agents/
│   ├── domain.md
│   ├── issue-tracker.md
│   └── triage-labels.md
└── src/ (or backend/frontend modules)
```

## Use the glossary's vocabulary

When output names a domain concept (in an issue title, an API contract, a test name, or a gatekeeper review finding), use the term as defined in `CONTEXT.md` (e.g., `Cliente`, `Pet`, `Servico`, `Agendamento`, `Atendimento`). Do not drift to synonyms the glossary explicitly avoids.

## Flag ADR conflicts

If proposed changes contradict an existing ADR (such as adding isolated unit tests with mocks instead of integration/e2e tests), surface it explicitly rather than silently overriding.
