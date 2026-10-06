# US-06 - Quantidade das taxas variáveis

O Cliente informa a quantidade de uma taxa variável (ex.: energia em kVA) e pode alterá-la depois.

## API (perfil CLIENT, apenas o próprio projeto)

`PUT /api/projects/{projectId}/fees/{projectFeeId}/quantity` com `{"quantity": 12.5}`.

Regras: quantidade > 0 (até 10 inteiros e 2 decimais, 400 caso contrário); só taxas `VARIAVEL` (422 nas demais);
projeto `APROVADO` ou `AGUARDANDO_PAGAMENTO` (422); taxa com `PAGAMENTO_GERADO` não muda (409); projeto ou taxa
de outro cliente retorna 404; Admin retorna 403. Cada envio ou alteração leva a taxa para `AGUARDANDO_COTACAO`.

## Dependências de outras histórias

- US-02: usa `Fee`, `FeeType.VARIAVEL` e `measurementUnit` do catálogo.
- US-05: a lista de taxas do projeto e o vínculo taxa-projeto são dela. Aqui entra apenas `ProjectFee`
  (`project_fees`: taxa, projeto, `quantity`, `status`, `quantityUpdatedAt`) como o mínimo para gravar a
  quantidade. Se a US-05 criar o próprio vínculo, trocar `ProjectFee` pelo dela e manter `quantity`/`status`.
- US-07/US-08: movem a taxa para `COTADA` e `PAGAMENTO_GERADO`. Uma nova quantidade invalida a cotação anterior.

## Frontend

`components/FeeQuantityInput` renderiza uma linha de taxa variável: campo com a unidade, status e botão
"Enviar para Cotação". A lista da US-05 deve renderizá-lo por taxa variável, passando `projectId`, a linha
(`ProjectFee`) e `onUpdated`. `services/feeQuantity.ts` valida o texto antes de chamar a API.

## Testes

`ProjectFeeQuantityIntegrationTest` (bons / ruins / incompletos, ADR 0001 e 0002) e `feeQuantity.test.ts`.
