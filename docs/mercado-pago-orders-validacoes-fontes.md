# Orders API — validações em fontes oficiais

Consulta documental: 08/11/2026. Nenhuma chamada autenticada ou chamada à API foi executada nesta pesquisa. Foram consultadas somente páginas públicas oficiais do Mercado Pago e Mercado Livre.

## Valor e formato monetário

O [guia oficial de boleto](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/payment-integration/websites/boleto) define `total_amount` como string e exemplifica `"50.00"`, repetido em `transactions.payments[0].amount`. A [referência de criação](https://www.mercadopago.com.br/developers/pt/reference/online-payments/checkout-api/create-order/post) também documenta string para o total e erro quando o total diverge da soma dos pagamentos.

**Não foi localizado um mínimo ou máximo monetário numérico específico para boleto nas páginas consultadas.** O código `invalid_transaction_amount`, observado no lote local, não foi encontrado por esse nome na tabela pública atual consultada. Não se atribui a ele uma faixa numérica sem evidência.

As evidências locais de R$ 50,00 aceito e R$ 0,01 rejeitado não determinam o mínimo. R$ 50,00 é uma referência homologada; não um limite inferior demonstrado. O cenário negativo pode usar R$ 0,01 como **valor comprovadamente rejeitado**, mas não deve afirmar que mediu a fronteira monetária exata.

## Pagador, vencimento e resposta

O mesmo [guia de boleto](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/payment-integration/websites/boleto) exige e-mail, tipo/número do documento e os seis campos de endereço: `street_name`, `street_number`, `zip_code`, `neighborhood`, `city`, `state`. `S/N` é admitido para endereço sem número; o estado brasileiro usa duas letras. `config.location.state_id` é opcional, embora conste no exemplo.

O vencimento do pagamento usa `transactions.payments[].expiration_time`, duração ISO 8601: **1 a 30 dias**, com recomendação de pelo menos três. Datas que caiam no fim de semana são ajustadas para dia útil. Não enviar `expiration_time` da order na raiz com `processing_mode=automatic`; o campo da raiz se refere ao modo manual. Os campos transitórios do boleto ficam em `transactions.payments[].payment_method`.

A criação pode ser assíncrona. `processing` sem detalhes não demonstra falha de parsing nem pagamento; não autoriza outra criação automática. A verificação final sem pagar o boleto é limitada, conforme o [guia de teste de boleto](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/integration-test/boleto).

## Idempotência e erros

A [tabela oficial de erros da Orders API](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/payment-management/integration-errors/api) lista:

- HTTP 400 `required_properties` para propriedades obrigatórias ausentes e `property_value` para valores inválidos, inclusive regras de e-mail/localização. Portanto, o código genérico sozinho não identifica o campo que falhou.
- HTTP 409 `idempotency_key_already_used` para chave já utilizada.
- HTTP 429 `usage_quota_exceeded`; consultar `Retry-After`.
- HTTP 500 `idempotency_validation_failed` ou `internal_error`.

A orientação genérica dessa página para chave nova em certas falhas não resolve a ambiguidade de uma criação já enviada. **Para esta bateria, prevalece a política autorizada: sem retry e sem chave nova para a mesma tentativa.** Não há garantia de que todo replay devolverá uma Order com 2xx. O teste deve registrar o HTTP efetivo e comparar IDs se houver resposta de sucesso.

## Credenciais e identidade

A [documentação de credenciais](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/resources/credentials) informa que tokens de teste podem começar por `APP_USR`; o prefixo isolado não comprova uma conta de teste. Ela mostra explicitamente o header `Authorization: Bearer …` e o endpoint de identidade `https://api.mercadolibre.com/users/me`.

A [documentação oficial de usuários do Mercado Livre](https://developers.mercadolivre.com.br/en_us/nodejs/vehicles-manage-users) exemplifica a resposta de `/users/me` com `tags` contendo `test_user`, identificando-a como conta de teste. Uma futura execução deverá exigir essa marca na resposta autenticada contemporânea e recusar continuar se ausente. A consulta documental atual não revalida a credencial do `.env`.
