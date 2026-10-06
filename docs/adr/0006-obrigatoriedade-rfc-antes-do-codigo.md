# ADR 0006: Obrigatoriedade de RFC antes do Desenvolvimento (Docs-Driven Development)

## Data
2026-10-05

## Status
Aceito

## Contexto
Durante o desenvolvimento do sistema, identificamos que muitas vezes o escopo de novas funcionalidades (User Stories) e decisões arquiteturais ficam ambíguos ou com detalhes faltando ao iniciar o código. Essa falta de refinamento técnico antes do código gera retrabalho, idas e vindas de código, e discussões longas durante a etapa de Code Review.
Precisamos de um mecanismo que obrigue o desenvolvedor (ou o agente de Inteligência Artificial assistente) a refinar e especificar todos os detalhes da tarefa antes de escrever a primeira linha de código, assegurando que o domínio, as interfaces e as regras de negócio estejam completamente esclarecidas.

## Decisão
Estabelecemos a prática obrigatória de elaboração de um **RFC (Request for Comments)** como passo inicial e mandatário para todas as User Stories e tarefas de grande impacto (mudanças arquiteturais) do sistema. O fluxo de trabalho segue as seguintes diretrizes:

1. **Escopo:** A regra se aplica rigorosamente a User Stories e features (`feat/US-XX`). Tarefas triviais de manutenção (`chore`) e pequenos `fixes` não exigem uma RFC formal, mas devem seguir a comunicação clara nas issues.
2. **Abordagem de Refinamento (Grill-me):** Antes de gerar qualquer código, o desenvolvedor ou agente deve adotar uma abordagem ativa de investigação. Se a issue estiver carente de detalhes, o agente de IA deve iniciar imediatamente uma sessão interativa de perguntas (estilo `/grill-me`) junto ao usuário ou ao Product Owner para sanar quaisquer ambiguidades de design, fluxo, persistência ou UI.
3. **Template Base:** A especificação refinada deve ser redigida baseada no template oficial (`docs/rfc/rfc-modelo.md`).
4. **Fluxo do Git:** A RFC em formato Markdown `.md` deve ser o **primeiro commit** na própria branch da feature (`feat/US-XX-...`). Não é necessário abrir um Merge Request exclusivo apenas para a RFC; ela será revisada em conjunto com o código da funcionalidade no mesmo Merge Request, mas garante que o desenvolvimento ocorreu a partir de um planejamento estruturado.

## Consequências
* **Positivas:** 
  * Garantia de alinhamento e visão técnica clara antes da implementação.
  * Redução drástica de tempo de retrabalho e de loops prolongados de refatoração de código.
  * O repositório passa a ter um histórico riquíssimo de decisões de design para cada funcionalidade documentadas dentro de `docs/rfc`.
  * Os agentes de IA operarão de forma mais autônoma e assertiva (Docs-Driven), pois saberão extamente o que construir com base na RFC validada.
* **Negativas:**
  * Adiciona uma etapa extra de documentação antes da escrita de código, o que pode passar a sensação de lentidão no começo da sprint (Trade-off a favor da qualidade e menor refatoração futura).
