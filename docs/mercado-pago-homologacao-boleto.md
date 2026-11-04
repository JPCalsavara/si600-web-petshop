# Homologação de boleto — Orders API

Data: 03/11/2026. Branch: member/samuel-lima. Somente conta de teste.

## Confirmação do ambiente

Antes de criar cobrança, a consulta autenticada de leitura
`GET https://api.mercadolibre.com/users/me` retornou HTTP 200 e a marca
`test_user`. Nenhum nome, e-mail, documento, identificação da conta, token ou
resposta bruta foi registrado. Não foi usado o prefixo do token como prova.

O `.env` foi preservado. A confirmação e os parâmetros do experimento foram
fornecidos somente nesta execução, sem habilitar automaticamente testes futuros.

## Cenário

Usado o JSON fictício `boleto-sandbox.example.json`, em cópia privada temporária,
com novos UUIDs de referência e idempotência. Não houve alteração dos dados
fictícios do pagador nem uso de CPF/CNPJ real. A repetição usou exatamente o mesmo
arquivo e chave, sem uma segunda chave de criação.

## Operações reais

| Operação | HTTP | Resultado |
|---|---|---|
| POST /v1/orders | 201 | Boleto de teste criado |
| GET /v1/orders/{id} | 200 | Mesma order e pagamento consultados |
| POST /v1/orders com mesma chave e dados | 409 | Repetição rejeitada como CONFLICT; nenhuma segunda order retornada |
| GET /v1/orders/{id} após repetição | 200 | Order original preservada e consultável |

Identificadores técnicos:

- Order: `ORDTST01M6HX0YDW2N0W096MV2M33MRX`.
- Pagamento: `PAY01M6HX0YEEXTZ8Z5P889RYB9VB`.
- Status da order e do pagamento: `action_required`.
- Detalhe: `waiting_payment`.
- Vencimento retornado: `2026-11-11T02:59:59Z`, equivalente a
  `10/11/2026 23:59:59 America/Sao_Paulo`.
- `ticket_url`, `digitable_line` e `barcode_content`: presentes nas consultas.
  Os conteúdos não são registrados neste relatório.

O gateway conferiu referência e valor da resposta de criação contra a requisição.
Os GETs preservaram os mesmos IDs, estados e vencimento, sem polling.

## Idempotência e diferenças observadas

O reenvio não devolveu o resultado anterior com 2xx: devolveu HTTP 409. O
gateway propagou a exceção sanitizada `CONFLICT`, sem trocar a chave. Por isso
essa execução do teste de criação terminou com BUILD FAILURE: o teste espera
criação bem-sucedida, e não possui uma ação separada para replay com conflito
esperado. Isso não foi ocultado nem corrigido desativando testes.

O 409 e a consulta da order original confirmam o comportamento observado do
reenvio; não foi feita busca global de todas as orders da conta. O chamador
futuro precisa guardar a associação entre tentativa e order para recuperar a
cobrança por GET, em vez de esperar que todo POST repetido retorne a order.

Os IDs de teste começam por ORDTST, compatíveis com a validação atual do gateway.
A API retornou o vencimento em UTC; a conversão para o fuso do evento explica
a diferença da data textual, sem alterar a regra de duração enviada.

Foi acrescentado somente `providerHttpStatus` ao resultado técnico para registrar
o HTTP real de sucesso; erros já preservavam o HTTP na exceção. Não houve mudança
na política de retries nem implementação de módulos financeiros.

## Limites da homologação

Emissão e consulta de boleto foram efetivamente realizadas no ambiente de teste.
Não houve pagamento, baixa, teste de CNPJ, webhook, cancelamento ou implementação
de PaymentService, controllers, Pix e persistência financeira.

A regressão local posterior, com Java 21 e chamadas reais desabilitadas, concluiu
com BUILD SUCCESS: 253 casos descobertos, 251 aprovados, zero falhas, zero erros
e dois testes de sandbox ignorados nessa execução. Os experimentos reais acima
foram executados separadamente. Nenhum git add, commit ou push foi realizado.

## Fontes oficiais consultadas

- [Teste de boleto](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/integration-test/boleto)
- [Credenciais e consulta de identidade](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/resources/credentials)
- [Contas de teste](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/resources/test-accounts)
