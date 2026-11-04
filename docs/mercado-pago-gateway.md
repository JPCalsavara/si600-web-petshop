# Gateway Mercado Pago — Etapa 1

Gateway backend para uma order de boleto com uma transação, entregue na Etapa 1.
A [Etapa 2](payment-service.md) acrescenta PaymentService e persistência mínima.
Não há endpoints SI600 ou integração com Project/ProjectFee. A exposição operacional
exige autenticação e autorização em uma etapa posterior.

## Contrato Java

`MercadoPagoGateway.createBoleto(BoletoRequest)` envia um POST; `getOrder(orderId)`
envia um GET explícito. Retornam `OrderResult`, incluindo os estados independentes
da order e do pagamento, IDs, valor e dados transitórios do boleto. Estado externo
desconhecido é preservado; o gateway não declara uma taxa paga.
`paymentExpiresAt` expõe `transactions.payments[0].date_of_expiration` como
OffsetDateTime, preservando o instante e offset recebidos. Pode ser nulo quando
ausente; o gateway não infere vencimento a partir da duração solicitada. O teste
isolado informa esse vencimento sem exibir dados do pagador ou conteúdo do boleto.
`providerHttpStatus` preserva o HTTP real da resposta de sucesso (por exemplo,
201 na criação e 200 na consulta), permitindo registrar a homologação sem corpo bruto.

O chamador backend é responsável por autorização e origem confiável dos dados.
Bean Validation verifica formato, não comprova identidade, titularidade ou dívida.
Documento deve ser CPF/CNPJ com quantidade correspondente de dígitos; a validade
cadastral real depende do provedor. Pessoa jurídica ainda requer validação real.

São necessários UUID de referência e UUID v4 de idempotência. Mesma tentativa:
mesma chave, referência e dados. O adaptador não gera nem troca chaves sozinho.
Use BigDecimal positivo, até duas casas, e duração inteira de 3 a 30 dias conforme
política do ADR. A compatibilidade com o prazo do Projeto cabe ao chamador.

O resultado pode estar em `processing`, sem payment ID ou dados de boleto.
Nenhum GET adicional ou polling acontece automaticamente. Uma consulta explícita
posterior permite recuperar os dados quando disponíveis. Campos de boleto podem
ser nulos e nunca devem ser logados ou persistidos. `toString()` é redigido para
não reproduzir dados pessoais, credenciais ou conteúdo do boleto.

## Configuração backend

| Variável | Padrão | Uso |
|---|---|---|
| MP_ENABLED | false | Registra o bean quando true |
| MP_ACCESS_TOKEN | vazio | Credencial backend; obrigatória quando habilitado |
| MP_BASE_URL | https://api.mercadopago.com | Provedor ou stub HTTP em 127.0.0.1 |
| MP_CONNECT_TIMEOUT | 3s | Conexão |
| MP_READ_TIMEOUT | 10s | Espera da resposta |

Não há Public Key, segredo frontend ou segredo de webhook nesta etapa.
Não passar token em argumento Maven, arquivo versionado ou linha de comando.
Fornecer via `.env` local ignorado pelo Git, ambiente ou gerenciador de segredos. Com integração desligada, o
backend continua funcionando sem credenciais. Quando ligada, configuração
incompleta impede a inicialização. Redirecionamentos HTTP não são seguidos.

### Arquivo .env sem dependência dotenv

`application.yml` importa `mercadopago.yml`, que usa o Config Data nativo do Spring
Boot com a indicação `[.properties]` para ler `.env` sem extensão. O mesmo recurso
é carregado pelos testes isolados de sandbox, sem iniciar a aplicação ou banco.
Use `KEY=VALUE`, sem aspas, `export` ou comandos de shell. Não é executado `source`.

O projeto aceita execução a partir da raiz ou de `backend/`. Os imports procuram
`../.env`, `.env` e `backend/.env`, relativos ao diretório atual. Quando existem
arquivos na raiz e em backend, o de backend prevalece. Variáveis do processo e
propriedades `-D` prevalecem sobre os arquivos. O `.env.local` legado continua
importado pelo backend após esses arquivos; não é carregado pelo experimento isolado.

Arquivo local mínimo na raiz (substituir somente localmente o placeholder):

```dotenv
MP_ENABLED=true
MP_ACCESS_TOKEN=COLOCAR_TOKEN_NOVO_AQUI
MP_BASE_URL=https://api.mercadopago.com
```

O `.gitignore` já ignora `.env` em qualquer diretório. `.env.example` é um modelo
versionável sem credenciais. Usar um token novo após rotação; não reaproveitar
tokens compartilhados anteriormente. O sandbox recusa o placeholder acima.
Configurar o token não confirma o ambiente nem ativa chamadas reais automaticamente.

Os testes automatizados normais mantêm sua configuração isolada em
`src/test/resources/application.yml`, sem carregar credenciais locais. Apenas o
experimento de sandbox carrega o arquivo local explicitamente.

## Erros e recuperação

`MercadoPagoException` expõe `kind`, HTTP do provedor (zero se ausente),
`retryAfter` opcional e `creationMayHaveSucceeded`. Não retém resposta bruta
ou causa externa que possa reproduzir dados sensíveis.

| Situação | kind | Conduta do chamador |
|---|---|---|
| 400/422 | INVALID_REQUEST | Corrigir entrada |
| 401/403 | AUTHENTICATION | Conferir credencial/permissão sem expor detalhes |
| 402 | PROCESSING_FAILED | A order pode existir; não gerar outra cegamente |
| 404 | NOT_FOUND | Conferir identificação |
| 409 | CONFLICT | Investigar tentativa/chave anterior |
| 423 | LOCKED | Aguardar tentativa em processamento |
| 429 | RATE_LIMIT | Respeitar Retry-After antes de repetir |
| 5xx/transporte | UNAVAILABLE | Resultado de criação pode ser desconhecido |
| Sucesso inconsistente | INVALID_RESPONSE | Não assumir geração nem baixa |
| Outros HTTP, inclusive redirect | REJECTED | Investigar sem replay automático |

Não há retries automáticos: o gateway devolve Retry-After (segundos ou data HTTP)
para que o futuro coordenador aplique espera e limite de tentativas. Timeout,
resposta inválida e certos erros podem ocorrer depois de criar a order. Repetir
com chave nova não é uma recuperação segura. O mesmo request pode ser reenviado
com a chave original; o comportamento efetivo do provedor em repetição/409
precisa ser confirmado no sandbox antes de emissão operacional.

## Testes automatizados sem credenciais

Com Java 21, em backend/:

```bash
./mvnw test -Dtest=MercadoPagoGatewayIntegrationTest
./mvnw test
```

O gateway real chama um HttpServer local do JDK. Nenhuma dependência nova é
necessária. Os cenários incluem JSON/headers, estados, idempotência no transporte,
respostas incompletas, dados inválidos, erros HTTP e timeout. Isso valida o contrato
simulado; não comprova aceitação de pagador ou emissão pelo Mercado Pago.

## Experimento isolado no sandbox

Os dois testes de `MercadoPagoSandboxTest` ficam desabilitados por padrão. Não
iniciam Spring ou banco; leem `.env` pelo Config Data, com prioridade para o
ambiente do processo. Nenhuma chamada real é autorizada sem confirmação da conta.

1. Obter as credenciais adequadas da aplicação/conta de teste no painel oficial.
   Não usar credenciais da conta organizadora real. O prefixo do token não é
   prova de ambiente: o teste não consegue distinguir credenciais reais pelo texto.
2. Copiar `backend/src/test/resources/mercadopago/boleto-sandbox.example.json`
   para arquivo privado fora do repositório. O documento/endereço são exemplos
   fictícios, não um pagador comprovadamente aceito. Preencher dados de teste
   adequados e novos UUIDs para a primeira tentativa (idempotência: UUID v4).
3. Preencher o NOVO `MP_ACCESS_TOKEN` no `.env` local, sem imprimir seu valor.
4. Somente depois de confirmar a conta de teste, acrescentar ao `.env`:

```dotenv
MP_SANDBOX_ENABLED=true
MP_SANDBOX_ACCOUNT_CONFIRMED=TEST_ACCOUNT
MP_SANDBOX_REQUEST_FILE=/caminho/privado/boleto.json
MP_SANDBOX_ACTION=create
```

5. Com Java 21, executar em backend/:

```bash
./mvnw test -Dtest=MercadoPagoSandboxTest
```

Da raiz, o comando equivalente é:

```bash
./backend/mvnw -f backend/pom.xml test -Dtest=MercadoPagoSandboxTest
```

Use caminho absoluto no arquivo de requisição para não depender do diretório
de execução. O experimento exige a URL oficial, `MP_ENABLED=true`, token preenchido,
confirmação `TEST_ACCOUNT` e ação explícita. Nenhuma confirmação é inferida do token.

O teste emite uma chamada de criação e informa IDs/estados, nunca o boleto ou
pagador. Guardar o arquivo privado e a chave sem alterações se houver timeout.
Não gerar novos UUIDs para repetir a mesma tentativa.

Para uma consulta explícita posterior, editar o `.env`:

```dotenv
MP_SANDBOX_ACTION=get
MP_SANDBOX_ORDER_ID=COLOCAR_ORDER_ID_AQUI
```

```bash
./mvnw test -Dtest=MercadoPagoSandboxTest
```

Após o experimento, definir `MP_SANDBOX_ENABLED=false` no `.env`. Enquanto estiver
true e com ação create, repetir o comando reenvia a mesma tentativa. Não deixar
emissão de sandbox ativada durante execuções gerais da suíte.

Registrar somente IDs e estados da experiência, data, códigos HTTP e presença
dos campos. Não anexar resposta bruta ou dados cadastrais ao relatório.

## Documentação consultada e pendências reais

Consultada em 03/11/2026:

- [Guia de boleto](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/payment-integration/websites/boleto)
- [Teste de boleto](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/integration-test/boleto)
- [Criar order](https://www.mercadopago.com.br/developers/pt/reference/online-payments/checkout-api/create-order/post)
- [Consultar order](https://www.mercadopago.com.br/developers/pt/reference/online-payments/checkout-api/get-order/get)
- [Erros](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/payment-management/integration-errors/api)
- [Config Data Spring Boot 3.3](https://docs.spring.io/spring-boot/3.3/reference/features/external-config.html)

O guia atual aceita e-mail válido no teste e S/N para endereço sem número,
divergindo do PDF. Há divergência entre a orientação de token de teste do guia
e a referência de erro invalid_credentials, que menciona usuários de teste com
credenciais de produção. Confirmar a modalidade correta da conta no painel;
não trocar para a conta real como tentativa de contornar erro.

A [homologação real de 03/11/2026](mercado-pago-homologacao-boleto.md) confirmou
criação com o pagador CPF fictício, consultas, dados de boleto e vencimento.
O replay da mesma chave retornou 409, sem segunda order devolvida. Esse conflito
não deve ser contornado gerando outra chave para a mesma tentativa.
Ainda faltam validações de CNPJ, processamento assíncrono e recuperação de
resultado incerto. A baixa real não é validada pelo teste de criação de boleto.
Os testes com stub continuam separados da evidência de homologação real.

## Próximas etapas (não implementadas)

RFC aprovada antes de avançar: persistência de tentativa e request imutável,
recuperação após timeout, autenticação técnica dos endpoints, PaymentService
desacoplado, evento/interface de mudança de estado após persistência e webhook
assinado com GET confirmatório. Nenhum consumidor deve atualizar Projeto ou Taxa
diretamente a partir de uma notificação não confirmada. Evitar GET duplicado e
polling implícito. Cadastro, frontend e regras financeiras pertencem aos colegas.
