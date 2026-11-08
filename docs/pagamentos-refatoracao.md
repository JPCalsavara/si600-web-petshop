# Entrega da simplificação de pagamentos

Configuração, comandos, endpoints e limitações operacionais: [pagamentos.md](pagamentos.md).

## Resultado

Cliente Mercado Pago concreto, sem interface com implementação única; serviço sem notificações ou eventos locais sem consumidores. A reserva transacional e as restrições únicas foram preservadas. O fluxo manual usa o banco persistente existente. Autenticação Basic de desenvolvimento exige perfil dev e habilitação explícita; integração técnica existente continua protegida por Bearer e escopo de obrigações.

Nenhuma cobrança real ou de teste foi enviada ao Mercado Pago. Nenhuma operação foi executada no banco PostgreSQL compartilhado. Os testes automatizados usam HTTP local simulado e banco H2 real isolado.

## Validação

- Backend: `cd backend && ./mvnw -o clean package` — compilação, empacotamento e 335 testes aprovados.
- Frontend: `npm --prefix frontend run build` — TypeScript e Vite aprovados.
- Frontend: `npm --prefix frontend test` — 43 testes aprovados, 7 testes de integração existentes ignorados pela configuração da suíte.
- Build de produção sem a página, sua rota ou código de criação de pagamentos.
- `bash -n scripts/pagamentos-dev.sh` e `git diff --check` aprovados.
- Gatekeeper tentado com `--skip-tests` após execução independente das suítes. Não concluiu: dependência Python `langchain_google_genai` ausente; Sonar sem token/chave. Não há aprovação do Gatekeeper.

O fluxo com credencial do provedor e o comando de inicialização completo não foram executados: a configuração privada foi criada como modelo a preencher, sem importar segredos antigos. A emissão manual depende das permissões e da credencial da aplicação de teste.

## Inventário

O estado inicial já continha alterações e arquivos não rastreados de pagamentos. A lista abaixo registra a intervenção desta refatoração, incluindo arquivos locais preexistentes que ainda não haviam sido versionados. Não inclui alterações anteriores preservadas sem intervenção.

### Criados

- `.envpagamentos (privado, ignorado pelo Git)`
- `.envpagamentos.example`
- `scripts/pagamentos-dev.sh`
- `frontend/src/pages/PaymentTestPage/PaymentTestPage.tsx`
- `frontend/src/pages/PaymentTestPage/PaymentTestPage.module.scss`
- `backend/src/test/java/br/unicamp/ft/si600/eventos/payment/PaymentDevelopmentIntegrationTest.java`
- `docs/pagamentos.md`
- `docs/pagamentos-refatoracao.md`

### Modificados/adaptados

- `.env.example`
- `.github/workflows/gatekeeper-ci.yml`
- `.gitignore`
- `README.md`
- `backend/pom.xml`
- `backend/src/main/java/br/unicamp/ft/si600/eventos/config/MercadoPagoConfiguration.java`
- `backend/src/main/java/br/unicamp/ft/si600/eventos/config/PaymentHttpConfiguration.java`
- `backend/src/main/java/br/unicamp/ft/si600/eventos/exception/PaymentHttpExceptionHandler.java`
- `backend/src/main/java/br/unicamp/ft/si600/eventos/gateway/MercadoPagoGateway.java`
- `backend/src/main/java/br/unicamp/ft/si600/eventos/payment/http/PaymentAccessPolicy.java`
- `backend/src/main/java/br/unicamp/ft/si600/eventos/payment/http/PaymentBoundaryFilter.java`
- `backend/src/main/java/br/unicamp/ft/si600/eventos/payment/http/PaymentHttpPaths.java`
- `backend/src/main/java/br/unicamp/ft/si600/eventos/service/PaymentService.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/payment-financial-states-migration.sql`
- `backend/src/main/resources/db/payment-schema.sql`
- `backend/src/main/resources/mercadopago.yml`
- `backend/src/test/java/br/unicamp/ft/si600/eventos/payment/MercadoPagoGatewayIntegrationTest.java`
- `backend/src/test/java/br/unicamp/ft/si600/eventos/payment/PaymentEndpointsDisabledIntegrationTest.java`
- `backend/src/test/java/br/unicamp/ft/si600/eventos/payment/PaymentHttpIntegrationSupport.java`
- `backend/src/test/java/br/unicamp/ft/si600/eventos/payment/PaymentIntegrationSupport.java`
- `backend/src/test/java/br/unicamp/ft/si600/eventos/payment/PaymentServiceIntegrationTest.java`
- `docs/mercado-pago-gateway.md`
- `docs/mercado-pago-homologacao-boleto.md`
- `docs/payment-service.md`
- `frontend/src/App.tsx`

### Removidos

- `.env.mp-e2e`
- `.env.mp-e2e.example`
- `backend/Containerfile.webhook`
- `backend/src/main/java/br/unicamp/ft/si600/eventos/config/PaymentWebhookProperties.java`
- `backend/src/main/java/br/unicamp/ft/si600/eventos/controller/MercadoPagoWebhookController.java`
- `backend/src/main/java/br/unicamp/ft/si600/eventos/entity/PaymentWebhookReceipt.java`
- `backend/src/main/java/br/unicamp/ft/si600/eventos/gateway/RestMercadoPagoGateway.java`
- `backend/src/main/java/br/unicamp/ft/si600/eventos/payment/webhook/PaymentWebhookService.java`
- `backend/src/main/java/br/unicamp/ft/si600/eventos/payment/webhook/WebhookReceiptStore.java`
- `backend/src/main/java/br/unicamp/ft/si600/eventos/payment/webhook/WebhookSignatureVerifier.java`
- `backend/src/main/java/br/unicamp/ft/si600/eventos/repository/PaymentWebhookReceiptRepository.java`
- `backend/src/main/java/br/unicamp/ft/si600/eventos/service/PaymentStatusChanged.java`
- `backend/src/test/java/br/unicamp/ft/si600/eventos/payment/MercadoPagoDotenvIntegrationTest.java`
- `backend/src/test/java/br/unicamp/ft/si600/eventos/payment/MercadoPagoSandboxSettings.java`
- `backend/src/test/java/br/unicamp/ft/si600/eventos/payment/MercadoPagoSandboxTest.java`
- `backend/src/test/java/br/unicamp/ft/si600/eventos/payment/MercadoPagoSecondBatchTest.java`
- `backend/src/test/java/br/unicamp/ft/si600/eventos/payment/PaymentHmacContractIntegrationTest.java`
- `backend/src/test/java/br/unicamp/ft/si600/eventos/payment/PaymentIntegrationSmokeTest.java`
- `backend/src/test/java/br/unicamp/ft/si600/eventos/payment/PaymentServiceEndToEndSandboxTest.java`
- `backend/src/test/java/br/unicamp/ft/si600/eventos/payment/PaymentWebhookDiagnosticsIntegrationTest.java`
- `backend/src/test/java/br/unicamp/ft/si600/eventos/payment/PaymentWebhookIntegrationTest.java`
- `backend/src/test/java/br/unicamp/ft/si600/eventos/payment/PaymentWebhookUnconfiguredIntegrationTest.java`
- `backend/src/test/java/br/unicamp/ft/si600/eventos/payment/e2e/MpE2eApplication.java`
- `docs/mercado-pago-homologacao-ponta-a-ponta.md`
- `docs/mercado-pago-plano-segunda-bateria.md`
- `docs/mercado-pago-segunda-bateria-resultados.md`
- `docs/mercado-pago-segunda-homologacao-e2e.md`
- `docs/mercado-pago-teste-real-10-requests.md`
- `docs/mercado-pago-webhook-401-diagnostico.md`
- `docs/mercado-pago-webhook-fontes.md`
- `docs/mercado-pago-webhook-homologacao.md`
- `docs/payment-e2e-validation.md`
- `docs/payment-http-api-webhook.md`
- `docs/payment-integration-smoke-report.md`
- `docs/payment-stage3-validation.md`
- `docs/rfc/TEC-03-payment-api-webhook.md`
- `docs/rfc/TEC-04-mercado-pago-e2e.md`
- `scripts/mp-e2e.sh`
- `scripts/mp-e2e/README.md`
- `scripts/mp-e2e/application.properties`
- `scripts/mp-e2e/ngrok.yml`
- `scripts/mp-e2e/runner.py`
- `scripts/mp-e2e/stub.py`
- `scripts/mp-e2e/test_runner.py`
