# PaymentService — Etapa 2

Interface Java backend sem controllers, webhook, tarefas agendadas ou dependência
de Project/ProjectFee. O chamador autentica a integração e fornece dados confiáveis
do pagador, valor e referência estável da obrigação. Os IDs da integração e da
obrigação devem ser opacos, sem CPF/CNPJ, e-mail ou outros dados pessoais.

## Contrato

```java
PaymentResponse generateBoleto(GenerateBoletoCommand command);
PaymentResponse getPayment(String integrationId, UUID paymentId);
BoletoDetailsResponse getBoleto(String integrationId, UUID paymentId);
PaymentResponse synchronizeOrder(String integrationId, UUID paymentId);
PaymentResponse synchronizeOrder(String integrationId, UUID paymentId, String candidateOrderId);
```

GenerateBoletoCommand contém integrationId, obligationReference, requestId, amount
(BigDecimal positivo, até duas casas), currency (somente BRL), description,
expirationDays (3–30) e Payer do contrato do gateway. Não recebe chave de
idempotência nem external_reference do chamador; o módulo os controla.

O external_reference é Payment.id. A chave de idempotência UUID v4 é persistida
antes do POST. PaymentResponse retorna IDs, valor, moeda, estado local, estados
externos independentes, datas e último erro sanitizado; não retorna HMAC, segredo,
pagador ou chave de idempotência. BoletoDetailsResponse contém apenas em memória
ticketUrl, linha digitável e código de barras, com toString redigido.

## Persistência e idempotência

- Uma tentativa canônica por `(integrationId, obligationReference)`.
- Uma requisição por `(integrationId, requestId)`.
- Chave de idempotência, order ID e payment ID externos globalmente únicos.
- Mesmo requestId com mesmos dados retorna o registro local, sem POST ou GET.
- Mesmo requestId com entrada alterada é CONFLICT, inclusive se mudar o pagador.
- Outro requestId para obrigação já reservada é CONFLICT, inclusive após falha,
  expiração ou pagamento. Não há reabertura nesta etapa.
- Corridas são arbitradas pelo banco; um perdedor da mesma requisição relê a
  reserva numa nova transação. Atualizações de estado usam lock pessimista e versão.

O serviço faz commit da reserva CRIANDO antes do HTTP. Não há transação aberta
durante POST/GET. Métodos que podem usar HTTP rejeitam transação ambiente com
AMBIENT_TRANSACTION; chame-os fora de @Transactional. getPayment usa apenas banco
e permite leitura em nova transação, inclusive por consumidor de evento pós-commit.

## Falhas e recuperação

Erros de criação são registrados e devolvidos em PaymentResponse: erros conclusivos
(400/422, credenciais, rate limit) ficam FALHOU; 409, timeout, 5xx, respostas
inconsistentes e resultados ambíguos ficam INCERTO. Nenhuma categoria autoriza
outro POST automaticamente. O 409 homologado não é interpretado como nova tentativa.

Falha de persistência após sucesso externo lança PaymentException(STORAGE_FAILURE)
com paymentId. A reserva inicial permanece CRIANDO e bloqueia reemissão. Uma
requisição idêntica pode recuperar essa reserva via generateBoleto ou getPayment,
sem depender de uma chave guardada no cliente.

Sem order ID conhecido:

1. synchronizeOrder sem candidato apenas devolve o estado local; não inventa GET.
2. getBoleto retorna RECOVERY_REQUIRED, sem chamar o provedor.
3. Obter o ID candidato no painel da conta correta, associado ao external_reference
   (UUID local), ou por um futuro mecanismo autenticado de notificação.
4. Chamar synchronizeOrder com esse candidato. O módulo faz GET e confere referência,
   valor, associação existente e unicidade dos IDs antes de gravar.

Se ninguém consegue obter esse ID, a tentativa fica bloqueada para recuperação
assistida. Não há promessa de recuperação automática por referência: o gateway
homologado só oferece GET por order ID. Não gerar outra chave ou outra referência
para contornar a reserva. Não há lease/retry de CRIANDO, nem nova tentativa depois
de uma falha conclusiva; essas políticas exigem uma etapa de reabertura autorizada.

Com order conhecida, getBoleto e synchronizeOrder fazem um único GET explícito.
Falha do GET propaga MercadoPagoException sanitizada sem apagar a informação local.
Não há polling, consulta remota em getPayment ou GET extra após geração.

## Estados e eventos

Estão separados o estado interno (CRIANDO, GERADO, INCERTO, FALHOU, PAGO,
CANCELADO, EXPIRADO) e os códigos externos de order e pagamento. Códigos futuros
são preservados com estado local INCERTO. Registrar canceled não implementa
cancelamento: não existe operação de cancelamento do módulo.

Somente GET autenticado com order e transação `processed/accredited` confirma
PAGO. Um POST que retorne esses códigos ainda exige sincronização por GET.
Depois de PAGO, snapshots pendentes não sobrescrevem estado ou snapshot confirmado,
inclusive quando consultas concorrentes terminam fora de ordem.

PaymentStatusChanged é publicado após commit apenas quando muda o estado interno.
O evento inclui paymentId, integrationId, estados anterior/novo e instante, sem PII.
É um evento local best effort: não há outbox, histórico ou garantia durável de
entrega. Falha de consumidor não reverte o pagamento; interessados podem consultar
getPayment e tratar eventos de forma idempotente.

## Configuração e schema

Além da configuração do gateway, definir PAYMENT_FINGERPRINT_SECRET com ao menos
32 bytes UTF-8 aleatórios e estáveis no ambiente/.env privado. Não usar o placeholder
do .env.example. O valor não é registrado, enviado ao provedor ou persistido.
Ele protege a impressão HMAC-SHA256 da entrada sem armazenar o pagador. Sua ausência
bloqueia geração, mas não impede a aplicação ou leituras locais de inicializarem.
O formato canônico v1 usa campos UTF-8 em ordem explícita com prefixos de
comprimento; não depende de configuração do ObjectMapper nem de delimitadores
que poderiam aparecer nos valores. O valor monetário é normalizado para duas casas.

Não trocar esse segredo enquanto houver tentativas que precisem ser comparadas:
rotação com versão de chave ainda não foi implementada. Não adicionar automaticamente
segredo ou credencial ao .env existente. DTOs com valores não suportados falham antes
da reserva e do HTTP. Não há suporte a outras moedas além do boleto BRL brasileiro.

O [SQL inicial](../backend/src/main/resources/db/payment-schema.sql) é revisável e
não é executado automaticamente pela aplicação em produção. Como o perfil prod
usa ddl-auto=validate, aplicar o schema numa implantação autorizada antes de subir
esta versão. CREATE TABLE IF NOT EXISTS não atualiza uma tabela antiga; verificar
colunas e constraints ao implantar. Não foi adicionada dependência de migration.

## Testes

Java 21, em backend/:

```bash
./mvnw test -Dtest=PaymentServiceIntegrationTest -DMP_SANDBOX_ENABLED=false
./mvnw test -DMP_SANDBOX_ENABLED=false
```

O teste usa gateway real, HttpServer externo e JPA real. Seu schema SQL inicial
é carregado em banco isolado, e hibernate update cria as demais tabelas que o
scan existente de entidades inclui. Nenhuma chamada real ao Mercado Pago ocorre.
Para usar PostgreSQL 16 efêmero, fornecer as propriedades de teste abaixo com
credenciais exclusivas do banco descartável:

```bash
./mvnw test -Dtest=PaymentServiceIntegrationTest -DMP_SANDBOX_ENABLED=false \
  -Dpayment.test.jdbc-url=jdbc:postgresql://127.0.0.1:55432/payment_test \
  -Dpayment.test.username=payment_test \
  -Dpayment.test.password=payment_test_pass
```

Esses valores são fixtures para banco temporário, não credenciais do Mercado Pago
ou de produção. Os testes verificam geração/repetição, oito chamadores concorrentes,
reserva visível antes do HTTP, recuperação após timeout e falha de persistência,
unicidade, isolamento, estados, eventos pós-commit e ausência de dados proibidos.

Validação de 03/11/2026: os 43 cenários do serviço passaram no PostgreSQL 16
isolado, incluindo o schema SQL, constraints e concorrência. O banco temporário
não tem conexão ou volume compartilhado com o banco operacional do projeto.
A regressão completa com Java 21 passou com 294 aprovados, zero falhas, zero erros
e dois testes reais de sandbox ignorados (296 casos descobertos). O PostgreSQL
descartável foi encerrado após a validação. Nenhuma credencial real foi usada nos testes.

## Próximas etapas não implementadas

Autenticação técnica dos endpoints, webhook assinado com GET confirmatório,
contratos de reabertura e entrega durável de eventos exigem autorização própria.
Os colegas integram permissões, valores, prazos e efeitos sobre Project/ProjectFee
sem acoplar essas entidades ao PaymentService.
