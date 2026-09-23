# Architectural Decision Records (ADRs)

Este diretório contém os registros formais de decisões arquiteturais do projeto **SI600 Web Petshop**.

## Catálogo de Decisões

Número | Título | Status | Data
:--- | :--- | :--- | :---
[**ADR 0001**](0001-estrategia-de-testes-integracao-e-e2e.md) | Estratégia de Testes Automatizados — Adoção Exclusiva de Testes de Integração e E2E | Aceito | 2026-09-23
[**ADR 0002**](0002-padroes-de-cenarios-de-teste-bons-ruins-incompletos.md) | Padrão Tripartite de Cenários de Teste — Casos Bons, Casos Ruins e Casos Incompletos | Aceito | 2026-09-23

---

## Como propor uma nova ADR

1. Use a skill `/domain-modeling` ou crie um novo arquivo numerado sequencialmente (`docs/adr/000X-nome-da-decisao.md`).
2. Siga o formato canônico:
   - **Título**: `ADR 000X: Breve descrição da decisão`
   - **Status**: Proposto / Aceito / Depreciado / Substituído por ADR-YYYY
   - **Data**: Data da decisão (YYYY-MM-DD)
   - **Contexto**: O problema ou desafio arquitetural que motivou a decisão.
   - **Decisão**: A solução escolhida e como deve ser implementada.
   - **Consequências**: Impactos positivos e negativos da decisão.
