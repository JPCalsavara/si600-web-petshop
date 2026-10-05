# ADR 0004: Object Storage dos PDFs de Estande com Backblaze B2

## Status
Aceito para US-04/US-08

## Contexto
O ADR 0003 define object storage privado, upload mediado pela API e URLs assinadas, mas cita Supabase Storage como fornecedor. Para US-04/US-08, o fornecedor requerido é Backblaze B2.

## Decisão
Os PDFs de planta de estande serão armazenados em um bucket privado do Backblaze B2 por sua API compatível com S3. O backend recebe o multipart, valida MIME, assinatura `%PDF-` e limite de 10 MB, grava o objeto e persiste somente metadados/chave do objeto no PostgreSQL.

Downloads são realizados por URLs assinadas com validade de 15 minutos. O frontend nunca recebe credenciais do B2.

A integração usa o AWS SDK for Java S3 apenas como cliente do protocolo S3 compatível; não há dependência da infraestrutura AWS.

## Segurança
A autorização é feita antes da geração da URL assinada. Cliente só acessa projetos cujo `ownerId` corresponde à identidade autenticada; administrador pode acessar projetos para análise.

O repositório atual não contém um provedor de autenticação/RBAC. Por isso, a implementação isola essa dependência atrás de `Actor`/`ActorFilter`; no scaffold atual, a identidade é recebida pelos headers `X-Authenticated-User-Id` e `X-Authenticated-User-Role`. Em produção, o gateway/provedor de identidade deve autenticar o usuário, remover headers externos com esses nomes e injetar os valores confiáveis.

## Consequências
O banco armazena metadados e auditoria, não bytes do PDF. Reenvios criam uma nova chave e removem a chave anterior após a persistência do novo registro.
