# AI Gatekeeper System & Context Harness

Este diretório contém os scripts Python, utilitários e especificações de dependências do subsistema **AI Gatekeeper**, responsável pela avaliação semântica automatizada de código e alinhamento com as normas arquiteturais do repositório **SI600 Web Petshop**.

---

## Arquivos e Responsabilidades

Arquivo | Responsabilidade
:--- | :---
`gatekeeper.py` | Grafo multi-agente LangGraph (diagnóstico de testes, revisão de diretrizes semânticas, triagem SonarQube e veredito do Tech Lead Supervisor).
`llm_factory.py` | Fábrica de modelos de linguagem suportando provedores Gemini, OpenAI, Anthropic e Ollama.
`sonar_adapter.py` | Adaptador para carregar e consultar relatórios de análise estática do SonarQube.
`context_harness.py` | Extração e busca vetorial por similaridade semântica em regras e diretrizes do repositório.
`build_harness.py` | CLI para escanear a documentação (`docs/`, `README.md`, `CONTEXT.md`) e gerar o índice `context_harness.json`.
`simulate_gatekeeper.py` | Script de teste / simulação de veredito do Gatekeeper.
`gitlab_reporter.py` | Publica o relatório do Gatekeeper e status de aprovação de commit no GitLab (Unicamp) via API REST.
`requirements-gatekeeper.txt` | Lista de dependências Python necessárias para execução do Gatekeeper e Context Harness.

---

## Execução

O orquestrador principal para agentes e desenvolvedores é executado a partir da skill:

```bash
bash .agents/skills/ai-gatekeeper-reviewer/scripts/run_review.sh --target .
```

Para gerar/atualizar o índice vetorial de regras manualmente:
```bash
python3 docs/gatekeeper/build_harness.py --docs docs README.md CONTEXT.md --output context_harness.json
```
