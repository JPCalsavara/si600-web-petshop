# ADR 0003: Adoção de Squash and Merge para Integração de Branches

## Status
Cancelado (Cancelled) — 2026-11-08

## Data Original
2026-10-03

## Contexto e Motivo do Cancelamento

Esta ADR determinava originalmente que todos os Merge Requests (MRs) integrados à branch `dev` deveriam obrigatoriamente utilizar a estratégia de **Squash and Merge**.

Por decisão da equipe em **2026-11-08**, a exigência de **Squash and Merge foi cancelada e não é mais utilizada** no fluxo de integração do repositório. Os commits semânticos individuais da branch da tarefa (`<tipo>(<ID-da-issue>): <descrição>`) são preservados diretamente no merge para manter a rastreabilidade completa da evolução técnica de cada entrega.
