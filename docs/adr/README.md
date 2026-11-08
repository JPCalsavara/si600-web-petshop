# Architectural Decision Records (ADRs)

Este diretório contém os registros formais de decisões arquiteturais do projeto **SI600 Web Eventos**.

## Catálogo de Decisões

Número | Arquivo | Título | Status | Data
:--- | :--- | :--- | :--- | :---
**ADR 0001** | [`0001-estrategia-de-testes-integracao-e-e2e.md`](0001-estrategia-de-testes-integracao-e-e2e.md) | Estratégia de Testes Automatizados — Adoção Exclusiva de Testes de Integração e E2E | Aceito | 2026-09-23
**ADR 0002** | [`0002-padroes-de-cenarios-de-teste-bons-ruins-incompletos.md`](0002-padroes-de-cenarios-de-teste-bons-ruins-incompletos.md) | Padrão Tripartite de Cenários de Teste — Casos Bons, Casos Ruins e Casos Incompletos | Aceito | 2026-09-23
**ADR 0003 (Storage)** | [`0003_armazenamento_de_pdfs.md`](0003_armazenamento_de_pdfs.md) | Estratégia de Armazenamento de PDFs (Supabase) | Substituído por [ADR 0004](0004_armazenamento_b2_para_pdfs_de_estande.md) | 2026-09-28
**ADR 0003 (Git)** | [`0003-squash-and-merge.md`](0003-squash-and-merge.md) | Adoção de Squash and Merge para Integração de Branches | Cancelado | 2026-11-08
**ADR 0004** | [`0004_armazenamento_b2_para_pdfs_de_estande.md`](0004_armazenamento_b2_para_pdfs_de_estande.md) | Object Storage dos PDFs de Estande com Backblaze B2 | Aceito | 2026-10-04
**ADR 0005** | [`0005-padronizacao-de-codigo-com-eslint-e-prettier.md`](0005-padronizacao-de-codigo-com-eslint-e-prettier.md) | Padronização de Código no Frontend com ESLint e Prettier | Aceito | 2026-10-05
**ADR 0006** | [`0006-obrigatoriedade-rfc-antes-do-codigo.md`](0006-obrigatoriedade-rfc-antes-do-codigo.md) | Documentação Arquitetural Consolidada em RFCs Gerais e Refinamento nos Cards | Aceito | 2026-11-08

---

## RFCs Gerais do Projeto (`docs/rfc/`)

Documento | Público-Alvo | Conteúdo Principal
:--- | :--- | :---
[**RFC Geral do Backend**](../rfc/rfc-backend.md) | Engenharia / Arquitetura | Arquitetura em camadas, seams (`ObjectStorage`, `EmailSender`, `Clock`, `ActorResolver`), catálogo REST `/api/*`, regras de negócio e **MER final em Mermaid**.
[**RFC Geral do Frontend**](../rfc/rfc-frontend.md) | Engenharia / Arquitetura | Módulos profundos de domínio (rejeição de Atomic Design), seam `ActorSession` + HTTP, páginas, validação e estratégia de testes E2E.
[**RFC Executiva (PO & Cliente)**](../rfc/rfc-po-visao-cliente.md) | Product Owner / Cliente | Visão integrada de negócio do Backend e Frontend, jornadas do Expositor e Administrador, regras financeiras/prazos e MER traduzido em linguagem acessível.

---

## Como propor uma nova ADR

1. Use a skill `/domain-modeling` ou crie um novo arquivo numerado sequencialmente (`docs/adr/000X-nome-da-decisao.md`).
2. Siga o formato canônico:
   - **Título**: `ADR 000X: Breve descrição da decisão`
   - **Status**: Proposto / Aceito / Cancelado / Substituído por ADR-YYYY
   - **Data**: Data da decisão (YYYY-MM-DD)
   - **Contexto**: O problema ou desafio arquitetural que motivou a decisão.
   - **Decisão**: A solução escolhida e como deve ser implementada.
   - **Consequências**: Impactos positivos e negativos da decisão.
