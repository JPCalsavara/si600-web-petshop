# ADR 0003: Adoção de Squash and Merge para Integração de Branches

## Status
Aceito (Accepted)

## Data
2026-10-03

## Contexto

Com a adoção do fluxo de desenvolvimento baseado em issues, os desenvolvedores trabalham em branches dedicadas (`<tipo>/<ID-da-issue>-<titulo>`). Durante o desenvolvimento de uma funcionalidade ou correção de bug, é comum que a branch acumule pequenos commits com descrições não padronizadas, progressos parciais, WIP (Work In Progress), correções de typos ou ajustes de lint.

Se usarmos o Merge Commit padrão para integrar essas branches à branch `dev`, todo esse histórico detalhado e frequentemente irrelevante será transferido para a branch principal. Isso resulta em um histórico poluído, complexo e difícil de auditar, dificultando o uso de ferramentas como `git bisect`, a reversão (revert) de funcionalidades e a leitura dos changelogs de release.

A equipe precisa de um histórico nas branches principais (`dev` e `main`) que seja linear, legível, atômico e que represente entregas de valor ("o que" foi feito), ocultando os detalhes granulares do desenvolvimento ("como" foi feito).

## Decisão

Fica determinado que todos os Merge Requests (MRs) integrados à branch `dev` devem ser mesclados utilizando a estratégia de **Squash and Merge**.

1. **Um Commit por MR/Issue**: Ao realizar o merge, todos os commits da branch de desenvolvimento devem ser comprimidos (squashed) em um único commit coeso na branch `dev`.
2. **Padrão de Mensagem de Commit**: A mensagem do commit resultante do squash deve obrigatoriamente seguir o padrão estipulado: `<tipo>(<ID-da-issue>): <descrição curta no imperativo>`. 
3. **Corpo do Commit**: O corpo do commit pode conter a lista dos commits originais para registro histórico e referenciar o número do Merge Request.
4. **Descarte da Branch Fonte**: Após o merge, a branch individual deve ser removida do repositório remoto para não acumular lixo.

## Consequências

- **Positivas**: 
  - Histórico do repositório limpo, focado em entregas de alto nível, operando como documentação confiável.
  - Reversão de código (`git revert`) simples e segura, pois funcionalidades inteiras residem em commits atômicos.
  - Geração de changelog baseada em histórico extremamente simplificada e precisa.
- **Negativas**: 
  - O histórico granular com os passos exatos tomados pelo desenvolvedor se perde após o merge e exclusão da branch. A equipe entende que esse *trade-off* é altamente vantajoso.
