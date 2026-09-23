# Issue Tracker: GitLab

Issues and specs for this repository live as GitLab issues under `si600-2026/turma-a/grupo-b/si600-web-petshop` (hosted on `gitlab.unicamp.br`). Use the [`glab`](https://gitlab.com/gitlab-org/cli) CLI for all issue and merge request operations.

## Conventions

- **Create an issue**: `glab issue create --title "..." --description "..."`. Use a heredoc for multi-line descriptions. Pass `--description -` to open an editor.
- **Read an issue**: `glab issue view <number> --comments`. Use `-F json` for machine-readable output.
- **List issues**: `glab issue list -F json` with appropriate `--label` filters.
- **Comment on an issue**: `glab issue note <number> --message "..."`. GitLab calls comments "notes".
- **Apply / remove labels**: `glab issue update <number> --label "..."` / `--unlabel "..."`. Multiple labels can be comma-separated or set by repeating the flag.
- **Close**: `glab issue close <number>`. `glab issue close` does not accept a closing comment, so post the explanation first with `glab issue note <number> --message "..."`, then close.
- **Merge requests**: GitLab calls PRs "merge requests". Use `glab mr create`, `glab mr view`, `glab mr note`, etc. Feature branches must target `dev` (`glab mr create --target dev`), while release branches target `main` (`glab mr create --target main`). See [docs/branching-strategy.md](file:///home/jpcalsavara/projetos/andamento/si600-web-petshop/docs/branching-strategy.md).

The repository remote is automatically inferred from `git remote -v` (`origin`).

## Issue Refinement to RFC

To refine a GitLab issue into an authoritative RFC specification before implementation, use the **`refine-issue`** skill. It fetches the issue via `glab`, conducts a relentless `/grill-me` interview with the developer, and generates `docs/rfc/rfc-<NN>-<slug>.md` based on `docs/rfc/rfc-modelo.md`.

## Merge Requests as a Triage Surface

**MRs as a request surface: no.**

When reviewing merge requests and branches, use `ai-gatekeeper-reviewer`, `git-flow`, or `code-review`.

## When a skill says "publish to the issue tracker"

Create a GitLab issue using `glab issue create`.

## When a skill says "fetch the relevant ticket"

Run `glab issue view <number> --comments`.

## Wayfinding Operations

Used by `/wayfinder`. The **map** is a single issue with **child** issues as tickets:

- **Map**: a single issue labelled `wayfinder:map`, holding the Notes / Decisions-so-far / Fog body (`glab issue create --label wayfinder:map`).
- **Child ticket**: an issue carrying `Part of #<map>` at the top of its description and labels `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`). Once claimed, the ticket is assigned to the driving developer.
- **Blocking**: Add a note with `/blocked_by #<blocker>` (`glab issue note <child> --message "/blocked_by #<blocker>"`). Alternatively, include `Blocked by: #<n>` at the top of the description.
- **Claim**: `glab issue update <n> --assignee @me`.
- **Resolve**: `glab issue note <n> --message "<answer>"`, then `glab issue close <n>`, then append the summary to the map issue.
