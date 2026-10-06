---
name: feature-builder
description: Orquestrador de desenvolvimento de ponta a ponta (E2E). Lê a RFC da branch, executa o ciclo TDD para Backend e Frontend, implementa, roda Gatekeeper e auto-corrige falhas.
---

# Feature Builder Skill

Esta skill orquestra o desenvolvimento completo de uma funcionalidade, atuando como um pipeline autônomo que vai desde a leitura da RFC (conforme ADR 0006) até a validação rigorosa de qualidade.

## Quando usar

Acione esta skill quando você (Agente) receber a instrução de desenvolver uma funcionalidade inteira, assumindo que a RFC já foi elaborada e commitada na branch atual.

## Fluxo de Execução Obrigatório

Siga os passos abaixo rigorosamente na ordem apresentada. Como você atua de forma autônoma, use as ferramentas disponíveis para invocar sub-agentes ou executar as skills recomendadas.

### Passo 1: Leitura do Escopo (Docs-Driven)
* Localize o documento `.md` mais recente em `docs/rfc/` ou leia a RFC commitada na raiz da branch atual.
* Extraia todas as regras de negócio, modelagem de domínio, contratos de API e requisitos de UI descritos.
* **Critério de parada:** Se não houver uma RFC clara, aborte a execução e instrua o usuário a rodar a skill `refine-issue` (ADR 0006) primeiro.

### Passo 2: Ciclo de Backend (Java/Spring Boot)
1. **Construção de Testes (`tdd`):**
   * Gere os testes de integração (`@SpringBootTest` com banco real) cobrindo os cenários Feliz, Triste e Malformado (ADR 0002).
   * Verifique se os testes estão falhando (Red phase).
2. **Implementação (`implement`):**
   * Desenvolva o Controller, Service e Repositories necessários para fazer os testes passarem.
   * Respeite os padrões descritos em `CONTEXT.md` e na documentação arquitetural.

### Passo 3: Ciclo de Frontend (React/Vite)
1. **Prototipagem e Telas (`ui-builder`):**
   * Acione a skill `ui-builder` para implementar as páginas e componentes descritos na RFC, consumindo os endpoints reais do Backend que acabaram de ser criados.
   * Adicione a tipagem rigorosa para a API (via Axios).
2. **Testes de UI/E2E:**
   * Crie ou atualize os testes do Cypress em `cypress/e2e/` para cobrir os fluxos do usuário nas telas novas.

### Passo 4: Code Explanation (Explain)
* Escreva um resumo estruturado e conciso detalhando:
  * O fluxo implementado.
  * As classes e componentes principais criados.
  * Como a integração entre o back e o front foi realizada.
* Exiba esse resumo para o usuário (ou grave no Artifact Directory para exibição).

### Passo 5: Validação do Quality Gate (Gatekeeper)
Execute a verificação de todos os testes e do Gatekeeper. Para testes, rode os comandos do repositório:
```bash
# Backend
cd backend && ./mvnw test
# Frontend (se houver vitest configurado)
cd frontend && npm run test
# Gatekeeper AI
bash .agents/skills/ai-gatekeeper-reviewer/scripts/run_review.sh --target .
```

### Passo 6: Auto-Correção (Diagnosing Bugs)
* **Se o Gatekeeper ou algum teste falhar:**
  1. Leia os relatórios de falha (logs do Maven, Cypress ou o review em markdown do Gatekeeper).
  2. Acione o ciclo da skill `diagnosing-bugs` para isolar, hipotetizar e fixar o problema.
  3. Re-execute o Passo 5.
  4. **Limite:** Faça isso no máximo **3 vezes**. Se o erro persistir na 3ª tentativa, aborte o pipeline e exiba as falhas para o desenvolvedor resolver manualmente.

## Finalização
Após passar com sucesso pelo Gatekeeper (Quality Gate verde), a skill deve parar e exibir um relatório final. 
Em seguida, **instrua explicitamente o usuário (desenvolvedor humano)** a acionar a skill `git-flow`. A skill `git-flow` será a responsável por:
1. Validar as mudanças finais.
2. Gerar as mensagens de commit semânticas (`feat(US-XX): ...`).
3. Fazer o push (subida) para a branch remota.
4. Acompanhar e garantir a abertura do Merge Request.
