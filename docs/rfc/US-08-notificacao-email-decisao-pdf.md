# RFC: US-08 - Notificação por Email e Decisão PDF

| **Status** | Implementado |
|---|---|
| **Data** | 06/10/2026 |
| **Versão** | 1.0 |

---

## Contextualização

### Entendendo o problema
Após o envio do PDF e a análise sobre a viabilidade da planta do estande (US-04), é imprescindível que o expositor receba uma resposta assíncrona. Sem o e-mail, o usuário precisaria verificar ativamente o sistema para saber se foi aprovado.

### Explicando a solução de forma macro
Integração com um servidor SMTP (ou mock local / mailtrap para dev) utilizando `spring-boot-starter-mail`. Quando a decisão final de aprovação ou rejeição do PDF é tomada pelo backend, dispara-se um evento ou serviço de envio de email informando o status detalhado.

---

## Implementação

### Componentes Chave
- `MailService`: Componente responsável por criar e enviar o `MimeMessage`.
- `application.yml` ou `.env.local`: Configuração de credenciais do SMTP.
- Tratamento de Falhas: Caso o servidor de e-mail esteja fora do ar, a notificação falhará e deve ser feito log de erro para retry futuro, ou a transação de aprovação do PDF pode falhar (dependendo se é crítico ser atômico).

### Diretriz Obrigatória de Testes
> [!IMPORTANT]
> **Padrão do Projeto: Apenas Testes de Integração e E2E (Sem Mocks Unitários)**
> Para evitar mocks de email que não testem nada real, o projeto utiliza SMTP embutido (`GreenMail` com Testcontainers ou SMTP simulado) para validar se o email foi efetivamente retido na caixa de saída da porta local durante os testes de integração.

### Fluxos Detalhados Textuais
1. **Happy Path**: PDF aprovado -> Serviço de email é chamado -> Mensagem enfileirada -> `200 OK`.
2. **Sad Path**: Falha nas variáveis de ambiente `.env.local` impede conexão com o SMTP -> O backend loga o erro apropriadamente e avisa que o email falhou sem derrubar todo o processo do banco de dados se não for estritamente acoplado.
