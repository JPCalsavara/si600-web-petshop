# US-10 — Notificações de prazo por e-mail

Uma rotina diária avisa por e-mail quando um prazo do Projeto se aproxima ou vence. Detalhes de desenho e alternativas descartadas estão em [rfc/US-10-notificacoes-prazo.md](rfc/US-10-notificacoes-prazo.md). O envio usa a infraestrutura de e-mail da US-08 ([us-08-notificacao-email-gmail.md](us-08-notificacao-email-gmail.md)).

## Quem recebe o quê

| Tipo (`DeadlineType`) | Prazo avaliado | Condição | Destinatário |
|---|---|---|---|
| `PDF_CLIENTE` | `pdfDeadline` | status `AGUARDANDO_PDF` ou `REPROVADO` | Cliente (`contactEmail`) |
| `APROVACAO_ADMIN` | `pdfDeadline` | status `PDF_EM_ANALISE` | Admin (`GMAIL_ADMIN`, senão `GMAIL_SENDER`) |
| `PAGAMENTO_CLIENTE` | `paymentDeadline` | status `APROVADO` ou `AGUARDANDO_PAGAMENTO` com taxa pendente | Cliente (`contactEmail`) |

Projetos `CONCLUIDO` são ignorados. O e-mail sempre informa o Projeto, o prazo limite e a ação pendente.

## Marcos

Um e-mail por marco, calculado pela **data** do prazo no fuso `America/Sao_Paulo` (não pelo horário):

| Marco | Quando |
|---|---|
| `D_MENOS_3` | faltam 2 ou 3 dias |
| `D_MENOS_1` | falta 1 dia |
| `NO_DIA` | o prazo é hoje (vale o dia inteiro, mesmo com a hora já passada) |
| `ATRASADO` | o prazo era ontem ou antes |

Prazo a 4 dias ou mais, ou nulo, não gera aviso. O marco é o mais avançado já atingido, então se a rotina ficar um dia fora do ar o aviso sai no dia seguinte. Depois do aviso de atraso nada mais é enviado: não há e-mail diário.

## Comportamento

- **Sem duplicidade:** cada envio é gravado em `deadline_notifications`. A chave única `(project_id, deadline_type, milestone, deadline_date, recipient)` impede reenvio do mesmo marco. Como `deadline_date` faz parte da chave, um novo prazo definido pelo Admin (US-03) volta a notificar.
- **Falha no envio:** nada é gravado e a rotina seguinte tenta de novo. O job usa o remetente síncrono (`AsyncEmailSender.delegate()`) justamente para saber se o envio funcionou.
- **Pendência regularizada:** é reavaliada a cada execução. PDF enviado, projeto aprovado sem taxas ou taxas já com pagamento gerado encerram os avisos.
- **Taxa pendente:** toda taxa fora do conjunto `SETTLED` em `DeadlineNotificationService`, hoje `{PAGAMENTO_GERADO}`. Ao criar o status "Pago" (US-12), incluí-lo ali.
- **Isolamento:** cada projeto é processado em transação própria; erro em um não impede os demais. Projeto sem e-mail de destino é ignorado com aviso no log.
- **Gmail desligado (`GMAIL_ENABLED=false`):** o `LoggingEmailSender` não envia e não lança erro, então a rotina **registra o marco como enviado** mesmo sem e-mail ter saído. Em desenvolvimento, apague as linhas de `deadline_notifications` para repetir um teste.

## Variáveis de ambiente

| Variável | Descrição |
|---|---|
| `GMAIL_ADMIN` | Destinatário dos avisos ao Admin. Se vazio, usa `GMAIL_SENDER`. |
| `DEADLINE_JOB_ENABLED` | `false` desliga a rotina. Padrão `true`. |
| `DEADLINE_CRON` | Expressão cron (Spring, 6 campos). Padrão `0 0 8 * * *` (08:00, `America/Sao_Paulo`). |

As variáveis `GMAIL_*` de envio são as da US-08.

## Arquitetura

```text
DeadlineNotificationScheduler (@Scheduled, 08:00)
        |
        v
DeadlineNotificationService.run()
   |-- ProjectRepository.findAllByStatusIn(status != CONCLUIDO)
   |-- DeadlineEvaluator           (marco por data no fuso do evento; Clock injetável)
   |-- DeadlineEmailTemplates      (assunto e corpo)
   |-- EmailSender (síncrono)      (US-08)
   `-- DeadlineNotificationRepository  (registro e checagem de duplicidade)
```

`DeadlineSchedulingConfiguration` habilita o agendamento e fornece o `Clock` (UTC), substituível nos testes.

## Testes

`DeadlineNotificationIntegrationTest` (seam `DeadlineNotificationService.run()`, banco real, `MutableTestClock` e `CapturingEmailSender`; ADR 0001 e 0002). Cobre:

- **Bons:** aviso a 3 dias, sequência completa de marcos sem duplicar, aviso ao Admin, pagamento listando as taxas pendentes, reenvio após reprovação, novo prazo voltando a notificar.
- **Ruins:** falha do provedor sem registro e com nova tentativa, PDF já enviado, aprovado sem taxas, taxas com pagamento gerado, projeto sem e-mail, Admin sem `GMAIL_ADMIN` nem `GMAIL_SENDER`.
- **Limites:** 4 dias, 2 dias, no dia com hora passada, dia anterior, prazo em UTC que cai em outro dia em São Paulo, `GMAIL_ADMIN` em branco ou com espaços.

Nos testes o job fica desligado (`notification.deadlines.enabled: false` em `src/test/resources/application.yml`) e é disparado manualmente.
