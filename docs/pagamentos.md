# Pagamentos em desenvolvimento

O fluxo é Controller → PaymentService → MercadoPagoGateway → PostgreSQL. O cliente HTTP usa Orders (`/v1/orders`) e não tem retries de criação. O webhook de Orders é opcional e reutiliza a sincronização manual. Não há polling, tarefas agendadas nem túneis iniciados automaticamente.

## Preparação

Requisitos: Java 21, Node 20+, npm e Docker Compose ou Podman Compose. O PostgreSQL 16 é o serviço existente em `docker-compose.yml`, com volume `postgres_data` persistente. Não execute `down -v`.

Na raiz:

```bash
cp -n .envpagamentos.example .envpagamentos
npm --prefix frontend ci
```

Edite somente `.envpagamentos`. Preencha `MP_ACCESS_TOKEN` com a credencial da aplicação de teste, `PAYMENT_DEV_PASSWORD` com uma senha local de pelo menos 12 caracteres e `PAYMENT_FINGERPRINT_SECRET` com um segredo aleatório estável de pelo menos 32 caracteres. Não use credenciais de produção. Nenhum segredo é copiado de arquivos antigos. O arquivo privado é ignorado pelo Git; o exemplo não contém segredos.

O backend importa o arquivo da raiz quando executado tanto na raiz quanto em `backend/`. Variáveis do processo têm prioridade. Configurações de pagamentos foram retiradas dos arquivos de exemplo antigos; migre manualmente configurações privadas antigas, se necessário. O backend mantém os imports de outras funcionalidades.

## Iniciar

Um comando na raiz:

```bash
bash scripts/pagamentos-dev.sh
```

O comando inicia somente o PostgreSQL pelo Compose e executa backend e Vite localmente. Backend e frontend escutam em loopback. Aguarde a inicialização do Spring antes de usar os botões. Ctrl+C encerra os processos locais; o banco e seu volume permanecem. Não inicie simultaneamente os serviços backend/frontend do Compose nas mesmas portas.

Abra **http://localhost:5173/dev/pagamentos** e informe `PAYMENT_DEV_USERNAME` e `PAYMENT_DEV_PASSWORD`. Também há acesso pela seção Pagamentos no menu administrativo durante o desenvolvimento. A rota e o código da página são excluídos do build de produção. O backend exige perfil `dev`, `PAYMENT_DEV_ENABLED=true` e autenticação Basic para esse acesso. O proxy Vite encaminha `/api` ao backend; seu destino usa `VITE_PROXY_TARGET`, com padrão local. Tokens do Mercado Pago e da integração técnica permanecem no backend.

Preencha os dados, inclusive endereço e UF. O prazo permitido é de 3 a 30 dias. O e-mail de testes de boleto é `test_user_br@testuser.com`, preenchido inicialmente na página e validado pelo backend neste fluxo. Clique **Gerar novo boleto** para iniciar uma tentativa independente, mesmo após tentativas pendentes, recusadas ou incertas. **Consultar status** atualiza o banco e obtém o link. **Ver boleto** fica disponível somente para URLs HTTPS válidas. É possível consultar um pagamento antigo informando seu ID interno.

O navegador conserva somente o ID do último pagamento, sem CPF, endereço ou senha. Cada clique explícito gera um novo `requestId` UUID; o backend deriva uma obrigação exclusiva `dev-<requestId>`. A repetição acidental do mesmo corpo HTTP mantém a mesma tentativa e não cria outro boleto. A chave de uma cobrança anterior não é reutilizada pela página. Um bloqueio durante a requisição evita cliques simultâneos; não há retries, polling ou criação automática após falha de rede. Limpar formulário mantém todo o histórico no banco.

## Endpoints

Todos usam o contexto `/api` e retornam erros RFC 7807:

| Método | Caminho | Comportamento |
|---|---|---|
| POST | `/api/internal/payments/boleto` | Cria ou retorna a mesma tentativa; 200/202. Recusa técnica: 422; recusa no acesso de desenvolvimento: 200 com FALHOU |
| GET | `/api/internal/payments/{id}` | Consulta exclusivamente o PostgreSQL |
| GET | `/api/internal/payments/history/{page}` | Histórico de desenvolvimento autenticado, 20 registros por página, começando em 0 |
| GET | `/api/internal/payments/{id}/boleto` | Consulta o provedor, atualiza status local e retorna os detalhes |
| POST | `/api/internal/payments/{id}/synchronize` | Atualização explícita do status; aceita `{ "orderId": "ORD..." }` para recuperação assistida |
| GET | `/api/internal/payments/webhook-status` | Configuração resumida (`enabled`, `configured`), com a mesma autenticação interna |
| POST | `/api/webhooks/mercadopago` | Notificação Orders autenticada pela assinatura do Mercado Pago |

Criação recebe `requestId` UUID, `chargeReference`, `amount`, `description`, `expirationDays` e `payer` (`firstName`, `lastName`, `email`, `identificationType`, `identificationNumber`, `address`). O endereço contém `zipCode`, `streetName`, `streetNumber`, `neighborhood`, `city`, `state`.

O contrato backend técnico Bearer permanece disponível, desativado por padrão, para consumidores existentes. Se necessário, configure no mesmo `.envpagamentos` `PAYMENT_INTERNAL_ENABLED`, `PAYMENT_INTERNAL_TOKEN` (mínimo 32 caracteres), `PAYMENT_INTERNAL_INTEGRATION_ID` e `PAYMENT_INTERNAL_ALLOWED_OBLIGATIONS` (lista exata separada por vírgulas). O navegador não usa esse contrato técnico.

## Garantias e limites

A reserva é commitada antes do POST externo. Restrições únicas no banco protegem tentativa, obrigação e IDs do provedor. Timeout ou falha ambígua conserva a reserva e impede outro POST para a mesma tentativa. No acesso de desenvolvimento, uma nova ação explícita cria outra obrigação; em produção e na integração técnica normal, a obrigação original continua exclusiva mesmo com uma nova chave. Transações curtas não mantêm locks durante comunicação externa. A consulta verifica referência e valor antes de associar uma ordem.

Quando o provedor não devolve o ID da ordem, o status permanece incerto. A recuperação manual exige obter o ID no painel do Mercado Pago e enviá-lo ao endpoint de sincronização; não existe reemissão automática. Se uma notificação assinada chegar antes dessa recuperação, o webhook consulta a ordem diretamente e localiza a reserva pela `external_reference` retornada pelo próprio Mercado Pago. A associação só é persistida após validar referência, valor, ID da ordem e eventual ID de pagamento; nenhuma informação de estado ou valor do corpo da notificação é usada como fonte definitiva. O provedor é a fonte dos detalhes transitórios do boleto. A consulta manual continua disponível.

O schema não sofre exclusões: tabelas antigas de recibos podem permanecer no PostgreSQL, sem uso pelo código. `ddl-auto=update` do ambiente de desenvolvimento conserva os pagamentos existentes. Não rode testes automatizados contra o banco de desenvolvimento: os testes usam H2 isolado e HTTP local simulado, sem cobranças reais.

Referência do contrato externo: [boleto na Orders API do Mercado Pago](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/payment-integration/websites/boleto).

## Validação automatizada

```bash
(cd backend && ./mvnw test)
npm --prefix frontend run build
npm --prefix frontend test
```

Os testes de pagamentos cobrem criação, persistência, concorrência, idempotência, timeout, falhas do provedor, autenticação de desenvolvimento e validação. Nenhum teste remanescente emite boleto no Mercado Pago.

## Webhook de Orders

A implementação adiciona somente um controller e um verificador de assinatura. O controller obtém o pagamento pela ordem já persistida e chama `PaymentService.synchronizeOrder`, a mesma rotina do botão manual. O GET autenticado ao Mercado Pago, a validação de referência/valor/IDs, os locks, o controle de versão e as regras de transição são compartilhados. O corpo da notificação não determina status, valor ou pagamento local.

Não há tabela de recibos, fila ou deduplicação em memória. Cada entrega consulta o provedor novamente. Uma resposta idêntica não altera versão nem data no banco; snapshots pendentes não rebaixam resultados financeiros. A resposta 200 só ocorre depois da conclusão da transação. Falhas de consulta ou persistência não são marcadas como concluídas.

### Configuração

No mesmo `.envpagamentos`, adicione ou preencha:

```properties
PAYMENT_WEBHOOK_ENABLED=true
PAYMENT_WEBHOOK_SECRET=COLOCAR_ASSINATURA_SECRETA_DO_PAINEL
```

O valor acima é um marcador, rejeitado pelo backend. Use o segredo oficial da aplicação; não confunda com o access token. O exemplo vem desabilitado por segurança. O backend já importa essas variáveis pelo arquivo, sem alteração no script de inicialização. Reinicie com:

```bash
bash scripts/pagamentos-dev.sh
```

Em **Suas integrações → aplicação → Configuração de Webhooks**, selecione **Teste**, informe `https://SEU_DOMINIO/api/webhooks/mercadopago`, habilite **Order (Mercado Pago)** e salve. Copie a assinatura secreta para o arquivo privado e reinicie o backend. Não coloque o segredo na URL. Esse procedimento e os headers usados seguem a [documentação oficial de Orders](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/notifications).

### Assinatura e payload

O manifesto HMAC-SHA256 é `id:<data.id da URL>;request-id:<x-request-id>;ts:<ts literal>;`. A variante Orders do SDK Java preserva o ID como recebido; a variante documentada anteriormente converte o ID para minúsculas. O verificador aceita somente essas duas representações, com HMAC completo e comparação em tempo constante para ambas. O segmento `request-id` é omitido quando o header não existe. `x-signature` fornece `ts` e `v1`. A comparação é feita em tempo constante. Exige-se um único `data.id` na query, correspondendo ao ID do JSON; não há fallback para autenticar apenas o corpo.

O JSON completo fornecido para este projeto está em `backend/src/test/resources/payment/order-action-required.json`. Um corpo mínimo também é aceito:

```json
{"type":"order","data":{"id":"ORD..."}}
```

`action` é opcional; os demais dados do corpo são ignorados. Campos duplicados, JSON malformado, ID divergente e payload acima de 64 KiB são rejeitados. O ID usado no GET é o ID autenticado da URL.

Os exemplos oficiais de Orders apresentam `ts` em milissegundos, enquanto o [verificador do SDK Java](https://github.com/mercadopago/sdk-java/blob/master/src/main/java/com/mercadopago/webhook/WebhookSignatureValidator.java) interpreta segundos. Aceitamos os dois formatos, mantendo o texto original no HMAC. Como política local de replay, a idade máxima é de 24 horas e a antecipação máxima é de cinco minutos. Essa janela é uma decisão do projeto, não uma exigência fixa do provedor. Ela permite reentregas após falhas sem manter recibos. Mantenha o relógio do servidor sincronizado; assinaturas mais antigas precisam de uma nova entrega assinada pelo provedor ou de consulta manual.

### Expor somente o endpoint local

Use uma versão atual do ngrok com suporte a Traffic Policy e uma conta já configurada. Não há instalação nem inicialização automática de túnel pelo projeto. Em outro terminal, na raiz:

```bash
ngrok http http://127.0.0.1:8080 --traffic-policy-file scripts/mercadopago-webhook-policy.yml --inspect=false
```

A política permite encaminhar somente `POST /api/webhooks/mercadopago`; todos os outros caminhos e métodos recebem 404 no próprio túnel. Query params oficiais são preservados. Backend e frontend continuam em loopback, e a autenticação da API interna permanece ativa. Não execute o túnel sem essa política nem aponte para a porta 5173. A [configuração de Traffic Policy](https://ngrok.com/docs/gateway/traffic-policy/getting-started/agent-endpoints) e o [bloqueio por caminho/método](https://ngrok.com/docs/gateway/traffic-policy/examples/block-unwanted-requests) seguem o formato oficial do ngrok.

Antes de cadastrar a URL no painel, confira os bloqueios, sem enviar credenciais:

```bash
curl -i https://SEU_DOMINIO/api/internal/payments/webhook-status
curl -i -X POST https://SEU_DOMINIO/api/internal/payments/boleto
curl -i https://SEU_DOMINIO/dev/pagamentos
```

Os três devem retornar 404 no túnel. Um POST ao webhook sem assinatura deve retornar 401 quando o webhook estiver habilitado e configurado. Se o plano/versão do túnel não aceitar a política, não publique a porta inteira. O túnel é um serviço externo; não foi iniciado nem validado com entrega pública nesta implementação.

### Testar uma entrega real sem emitir outro boleto

No painel da mesma aplicação/ambiente, use **Simular notificação** ou reenvie uma entrega de Orders. Informe o ID de uma ordem de teste já criada, vinculada no banco local e acessível com o access token configurado. Não invente uma assinatura nem use apenas um JSON enviado por curl: o painel precisa enviar os headers oficiais e `data.id` na URL.

Uma simulação de evento pago não torna a ordem paga: o backend sempre consulta o estado real do provedor. A fixture em estado `action_required` continua aguardando pagamento. Verifique o HTTP da entrega no painel e os logs `MP_WEBHOOK` no backend. Na página `/dev/pagamentos`, informe seu usuário e senha e use **Atualizar informações**, na seção **Webhook**, para ler a configuração e o pagamento local sem consultar o provedor. O status e a data são os últimos valores persistidos, sejam eles atualizados manualmente ou pelo webhook. Não há indicação inventada da origem da atualização nem polling; outra entrega não modifica a tela aberta até uma ação do usuário.

### Respostas e limites

| HTTP | Situação |
|---|---|
| 200 | Sincronização concluída, incluindo entrega duplicada ou snapshot sem alteração |
| 400 | Query/JSON inválidos, tipo incompatível ou IDs divergentes |
| 401 | Assinatura ausente, inválida ou timestamp fora da janela |
| 404 | Webhook desabilitado, ordem inexistente no provedor ou pagamento não encontrado localmente |
| 409 | Ordem consultada incompatível com referência, valor ou IDs persistidos |
| 413 | Corpo maior que 64 KiB |
| 503 | Segredo ausente, provedor/banco indisponível ou conflito transitório de versão |

Uma ordem inexistente na API retorna 404 (`provider_order_not_found`). Quando a API encontra a ordem, mas sua `external_reference` não identifica um registro local, o webhook retorna 404 (`payment_not_found_local`). Falhas temporárias de comunicação retornam 503 para permitir reentrega. A referência usada para localizar o pagamento vem somente da resposta autenticada por TLS da API do Mercado Pago, nunca do corpo da notificação. O ID da ordem também não pode substituir uma associação local já feita com outra cobrança.

Os logs contêm somente ordem validada, tipo de evento sanitizado, resultado da validação e resultado da sincronização. Rejeições registram códigos internos: `SECRET_MISSING`, `SECRET_PLACEHOLDER`, `PARAMETERS_INVALID`, `SIGNATURE_MISSING`, `SIGNATURE_INVALID`, `HMAC_MISMATCH`, `TIMESTAMP_INVALID` ou `TIMESTAMP_OUT_OF_WINDOW`. No último caso, a idade em segundos é registrada para conferir o relógio e a janela. O processamento também distingue `provider_order_not_found`, `payment_not_found_local`, `provider_temporary_failure` e `persistence_failure`. O corpo de erro 401 permanece genérico; esses motivos não são expostos ao chamador. Não incluem assinatura, segredo, corpo, CPF, boleto ou dados bancários. O endpoint de configuração é protegido; habilitação e segredo configurado não comprovam que a URL pública foi cadastrada corretamente no painel.

Os testes HTTP usam fixture, servidor Mercado Pago simulado e banco H2 isolado. Cobrem assinatura, timestamps, payload mínimo/completo, duplicação, ordem desconhecida, boleto pendente/pago, eventos fora de ordem, falhas e reentrega, divergência de associação/valor e configuração desabilitada/incompleta. Nenhuma emissão no provedor real é executada.

### Arquivos desta implementação

Criados:

- `backend/src/main/java/br/unicamp/ft/si600/eventos/controller/MercadoPagoWebhookController.java`
- `backend/src/main/java/br/unicamp/ft/si600/eventos/payment/http/MercadoPagoWebhookSignature.java`
- `backend/src/test/java/br/unicamp/ft/si600/eventos/payment/PaymentWebhookIntegrationTest.java`
- `backend/src/test/java/br/unicamp/ft/si600/eventos/payment/PaymentWebhookConfigurationIntegrationTest.java` (inclui configuração ausente)
- `backend/src/test/resources/payment/order-action-required.json`
- `scripts/mercadopago-webhook-policy.yml`

Modificados:

- `PaymentService.java`: somente entrada por ordem associada, delegando à sincronização existente.
- `PaymentInternalController.java`: consulta autenticada de habilitação/configuração.
- `PaymentHttpPaths.java` e `PaymentBoundaryFilter.java`: encaminham o webhook à autenticação por assinatura, preservando Basic/Bearer nos endpoints internos.
- `PaymentHttpConfiguration.java`: registra o verificador para o contexto HTTP existente.
- `mercadopago.yml`, `.envpagamentos.example` e `.envpagamentos` privado: habilitação e segredo.
- `PaymentHttpIntegrationSupport.java` e `PaymentDevelopmentIntegrationTest.java`: registro HTTP e cobertura do acesso à configuração.
- `frontend/src/pages/PaymentTestPage/PaymentTestPage.tsx`: seção Webhook e leitura local explícita.
- `docs/pagamentos.md`: operação, segurança, respostas e limites.

O cliente Mercado Pago, geração de boletos, entidade, repository, schema, Vite e script de inicialização não precisaram ser alterados nesta implementação.

Validação desta entrega: `./mvnw -o clean package` aprovado com 355 testes; `npm run build` aprovado; `npm test` com 43 aprovados e sete testes existentes ignorados. Endpoint e assinatura verificados por HTTP real local, sem autenticação do frontend e sem chamadas ao Mercado Pago real. O comando do túnel e a entrega pública dependem da conta/configuração do usuário e não foram executados.

Gatekeeper local tentado com `--skip-tests` após as suítes independentes: não concluiu por ausência de `langchain_google_genai`; Sonar sem token/chave. Não há aprovação do Gatekeeper nesta entrega.

## Novos boletos e histórico em desenvolvimento

A política de múltiplas cobranças aplica-se somente à integração autenticada `development`, no perfil `dev` sem `prod`. Não há mudança de schema, de HMAC ou de sincronização. Em produção, inclusive quando `dev` também estiver ativo, continuam valendo a proteção por obrigação e as respostas de rejeição existentes. O histórico fica indisponível fora desse acesso de desenvolvimento.

Na página, **Atualizar histórico** lê as tentativas persistidas, inclusive recusadas/incertas e anteriores à reinicialização do backend. **Anterior/Próxima** percorrem todas as páginas. Cada linha mostra ID, valor, data e status. **Consultar** seleciona o registro local sem chamar o provedor; **Sincronizar** consulta o Mercado Pago e atualiza somente aquele pagamento usando a rotina existente. Não há polling.

Para o acesso de desenvolvimento, uma recusa do provedor é retornada como tentativa registrada com status `FALHOU`, e não bloqueia o botão de nova criação. Uma resposta incerta permanece `INCERTO` e não é automaticamente reenviada. Clicar novamente em **Gerar novo boleto** é uma escolha explícita de criar outra cobrança, mesmo quando o resultado anterior ainda é desconhecido. O histórico não é apagado ou sobrescrito.

A credencial em `.envpagamentos` deve ser copiada do ambiente **Teste** da aplicação de Orders. O prefixo `APP_USR` também é usado em credenciais de teste e não permite distinguir sozinho o ambiente; não foi introduzida uma checagem falsa por prefixo. A página usa o e-mail de comprador indicado na [documentação oficial de testes com boleto](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/integration-test/boleto); confira a [origem da credencial no painel](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/resources/credentials). Nenhuma credencial foi trocada automaticamente.

Validação do fluxo de novas tentativas em desenvolvimento: build limpo do backend com 383 testes aprovados; frontend compilado e 43 testes aprovados (sete existentes ignorados). Cobertura de múltiplas emissões simuladas, IDs/chaves distintos, replay, timeout, recusa, histórico paginado e proteção nos perfis prod e dev+prod. Nenhuma cobrança foi enviada ao Mercado Pago real.
