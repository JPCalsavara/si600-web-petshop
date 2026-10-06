# US-08 — Notificação por e-mail da decisão sobre o PDF (API do Gmail)

Quando o Admin aprova ou reprova o PDF do estande, o cliente recebe um e-mail no endereço cadastrado na criação do projeto (US-03, campo `contactEmail`).

- Aprovação: confirma a aprovação e a metragem oficial registrada.
- Reprovação: envia a justificativa informada pelo Admin, o prazo limite de envio e a orientação para reenviar o PDF.

## Comportamento

- O e-mail só é enviado depois do commit da decisão. Decisão revertida não gera e-mail.
- Falha no envio (Gmail fora do ar, credencial inválida) é registrada em log e nunca desfaz a aprovação ou reprovação. A resposta da API não depende do e-mail.
- Com o Gmail ativo, o envio roda em outra thread (`AsyncEmailSender`); a lentidão do Google não atrasa a resposta.
- Projeto sem `contactEmail` (criado antes da US-03) é decidido normalmente, sem e-mail, com um aviso no log.
- O log nunca registra o conteúdo do e-mail, o destinatário nem credenciais.

## Arquitetura

```text
ProjectPdfService.approve/reject
        |
        v
PdfDecisionNotifier  --(afterCommit)-->  EmailSender
                                            |-- LoggingEmailSender   (padrão, dev: não envia)
                                            `-- AsyncEmailSender -> GmailEmailSender
                                                                      |-- OAuth2: refresh token -> access token (cache)
                                                                      `-- POST gmail/v1/users/me/messages/send
```

Não há SDK do Google nem dependência nova: `GmailEmailSender` usa o `HttpClient` do JDK e o Jackson que o Spring já traz. O mesmo padrão do storage é seguido: ligado e desligado por propriedade, com implementação inerte em desenvolvimento.

## Variáveis de ambiente

| Variável | Descrição |
|---|---|
| `GMAIL_ENABLED` | `true` para enviar de verdade. Padrão `false` (nenhum e-mail sai). |
| `GMAIL_SENDER` | Endereço da conta Gmail que autorizou o acesso. É o remetente (From). |
| `GMAIL_CLIENT_ID` | Client ID OAuth do Google Cloud. |
| `GMAIL_CLIENT_SECRET` | Client secret OAuth. |
| `GMAIL_REFRESH_TOKEN` | Refresh token da conta remetente, com o escopo `https://www.googleapis.com/auth/gmail.send`. |

Com `GMAIL_ENABLED=true` e qualquer uma das quatro credenciais vazia, a aplicação não sobe e informa qual variável falta. As credenciais ficam só no back-end e nunca devem ir para o repositório.

## Como obter as credenciais

1. No Google Cloud Console, crie um projeto e ative a **Gmail API**.
2. Em "Tela de permissão OAuth", configure o app e adicione a conta remetente como usuário de teste (ou publique o app: refresh tokens de apps em modo "Testing" expiram em 7 dias).
3. Em "Credenciais", crie um **ID do cliente OAuth** do tipo "Aplicativo da Web" e adicione `https://developers.google.com/oauthplayground` como URI de redirecionamento.
4. No [OAuth Playground](https://developers.google.com/oauthplayground), clique na engrenagem, marque "Use your own OAuth credentials" e informe o Client ID e o secret. Autorize o escopo `https://www.googleapis.com/auth/gmail.send` com a conta remetente e troque o código por tokens.
5. Copie o **refresh token** para `GMAIL_REFRESH_TOKEN`.

O Gmail só permite enviar em nome da própria conta autorizada. Para um remetente institucional, use uma conta (ou Google Workspace) dedicada ao sistema.

## Testes

- `GmailEmailSenderTest`: o cliente real é exercitado contra um servidor HTTP local que simula o Google (token OAuth, envio, reaproveitamento de token, renovação em 401, erro 500, servidor fora do ar, destinatário com quebra de linha) e confere a mensagem MIME decodificada, com acentos e assunto longo.
- `PdfDecisionEmailIntegrationTest`: seam HTTP + banco, com um `EmailSender` de captura no lugar do provedor externo. Cobre aprovar, reprovar com justificativa, falha de e-mail sem desfazer a decisão, projeto sem e-mail, justificativa em branco, JSON malformado, cliente tentando decidir, projeto inexistente, decisão repetida e projeto fora de `PDF_EM_ANALISE`.
