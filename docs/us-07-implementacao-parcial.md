# US-07 — implementação e validação

## Estado desta entrega

Esta entrega implementa parte importante do fluxo da US-07, mas **não deve ser considerada concluída**: a taxa personalizada exclusiva do projeto ainda não está implementada. Também não foi possível consultar a descrição da história no CSV, pois nenhum CSV/work-item exportado foi incluído no ZIP recebido.

## Fluxo alterado

- O administrador pode consultar e salvar a seleção de taxas ativas do catálogo depois que o projeto estiver em `APROVADO` ou `AGUARDANDO_PAGAMENTO`.
- A alteração de configuração é transacional, rejeita IDs duplicados e impede remover uma taxa que já tenha quantidade, cotação ou pagamento.
- A lista de cotação passa a considerar taxas dos projetos aprovados, incluindo taxas não variáveis; taxas variáveis continuam exigindo quantidade enviada pelo cliente.
- O valor mostrado ao cliente vem da cotação vinculada ao projeto, e não de um cálculo automático baseado no valor padrão do catálogo. O cliente vê um estado pendente quando ainda não existe cotação.
- O catálogo de demonstração é criado idempotentemente somente quando `app.fees.seed-demo=true` (ativado no perfil `dev`). Os valores são explicitamente descritos como demonstração e não como tarifas oficiais.

## Endpoints

- `GET /api/projects/{projectId}/fees/configuration`: lista os vínculos de taxas do projeto para administrador.
- `PUT /api/projects/{projectId}/fees/configuration`: recebe `{ "feeIds": ["..."] }` e substitui a seleção de taxas de catálogo de forma transacional, respeitando as restrições de remoção.
- `GET /api/admin/quotes`: lista taxas em estado de cotação/recotação para projetos aprovados.
- `PUT /api/projects/{projectId}/fees/{projectFeeId}/quote`: grava o valor, ator autenticado e horário; bloqueia taxas variáveis sem quantidade e pagamentos já gerados.

## Catálogo de demonstração

O perfil `dev` define `app.fees.seed-demo: ${SEED_DEMO_FEES:true}`. Para desabilitar o seed no ambiente local, use `SEED_DEMO_FEES=false`. O seed não substitui taxas já existentes com o mesmo nome. Os valores e regras das taxas são demonstrativos e precisam de validação da organização do evento antes de uso real. A regra oficial de cálculo de TFE, energia e extintor permanece uma decisão de negócio.

## Testes executados

- Frontend: `node node_modules/typescript/bin/tsc -b` — passou.
- Frontend: `node node_modules/vite/bin/vite.js build` — passou; houve apenas aviso de importação dinâmica/estática já existente no módulo `api.ts`.
- Frontend: `node node_modules/vitest/vitest.mjs run` — 3 arquivos passaram, 32 testes passaram e 7 testes de integração foram ignorados/skipped.
- Backend: não executado. `./mvnw test -q` não conseguiu baixar Maven Wrapper de `repo.maven.apache.org`; não há `mvn` instalado no ambiente. Portanto, nenhuma afirmação de sucesso dos testes backend é feita.

## Pendências para concluir a US-07

1. Implementar taxa personalizada no contexto do projeto, com opção de salvá-la no catálogo global, persistência e validação completa por tipo. O modelo atual associa `ProjectFee` obrigatoriamente a uma entidade `Fee` e ainda não distingue uma taxa exclusivamente local de uma taxa do catálogo; essa mudança exige uma decisão de modelo e migration compatível com produção (`ddl-auto=validate`).
2. Atualizar/adicionar testes backend para configuração de taxas, bloqueio de remoção, cotação de tipos não variáveis e proteção de pagamento gerado, e executá-los num ambiente com Maven disponível.
3. Revisar a apresentação da coluna de taxas na lista: agora há acesso à configuração para projetos aprovados, mas a quantidade/resumo de taxas não é carregada na listagem.
4. Confirmar as regras oficiais de cobrança e unidades para TFE, energia elétrica por kVA, limpeza, extintor e credencial extra; não há fonte de valores oficiais no repositório.
5. Obter o CSV/work-items do GitLab para conferir o texto original e eventuais dependências da US-07.
6. Confirmar a integração de notificação de cotação com a infraestrutura existente. Esta entrega não cria provedor de e-mail nem altera o fluxo da US-08.
