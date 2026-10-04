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
