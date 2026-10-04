# US-04 / US-08 — Envio, aprovação e reprovação do PDF do estande

A implementação segue as telas fornecidas no pacote complementar `SI600 - 2026(1).pdf` como referência visual. As telas mostram a navegação lateral de Gestão de Eventos, a página Projetos com as colunas Empresa/CNPJ/Expositor/Estante/Data/Status e a tela de Novo Projeto com metragem do estande. O comportamento específico de upload/aprovação/reprovação segue os critérios das USs, pois as telas não especificam todas as interações desses estados.

## Fluxo

Cliente: Projeto em `AGUARDANDO_PDF` ou `REPROVADO` -> envia metragem + PDF -> `PDF_EM_ANALISE`.

Admin: Projetos filtrados por `PDF_EM_ANALISE` -> download -> aprovar (`APROVADO`) ou reprovar com justificativa (`REPROVADO`).

Reenvio limpa a auditoria da decisão anterior e retorna para `PDF_EM_ANALISE`.

## Storage

PDFs são armazenados em Backblaze B2 via API S3. O banco mantém apenas metadados e a object key. Downloads usam URL assinada temporária.

## Autorização

O backend exige ator `CLIENT` ou `ADMIN`. Cliente só consulta/envia no próprio projeto. Como o repositório original não contém autenticação/RBAC, a fronteira provisória usa `X-Authenticated-User-Id` e `X-Authenticated-User-Role`, devendo ser substituída pelo contexto do IdP/gateway da aplicação antes de produção.

## Criação de projeto (US-03, back-end)

`POST /api/projects` (somente ADMIN) cria o `Project` no PostgreSQL com status `AGUARDANDO_PDF`.

Corpo: `name`, `document`, `address`, `area` (B2C, Music Hub, Music Sport ou Internacional), `email`, `pdfDeadline` e `paymentDeadline` (yyyy-mm-dd, ambas obrigatórias; pagamento não pode ser anterior ao envio) e `representativeName` (opcional, padrão: o nome).

Respostas: `201` criado; `400` validação; `403` papel diferente de ADMIN; `409` e-mail ou CPF/CNPJ já cadastrado (comparação só por dígitos, com constraint unique no banco).

O e-mail normalizado (minúsculas) vira o `ownerId`, ou seja, o cliente acessa o projeto enviando o próprio e-mail em `X-Authenticated-User-Id`. Senha temporária e vínculo de taxas dependem de US-01 e US-02 e ainda não estão no back-end.
