# Guia de Configuração: CI/CD no GitLab com GitHub Actions, AI Gatekeeper e SonarQube

Este tutorial explica como utilizar o **GitHub Actions** como motor de CI/CD em nuvem para o repositório do GitLab institucional (`gitlab.unicamp.br`), integrando o **AI Gatekeeper (Gemini)** e o **SonarCloud/SonarQube** para validar todos os Merge Requests automaticamente com custo zero de infraestrutura.

---

## 1. O Repositório deve ser Público ou Privado?

Você pode escolher tanto **Público** quanto **Privado** no GitHub:

| Característica | Repositório Público | Repositório Privado |
| :--- | :--- | :--- |
| **SonarCloud (`sonarcloud.io`)** | **100% Gratuito para sempre**, sem limites de linhas de código ou expiração. | Gratuito por 14 dias de teste (ou gratuito via GitHub Student Developer Pack). |
| **GitHub Actions** | Minutos de execução **ilimitados** e gratuitos. | **2.000 minutos gratuitos por mês** (suficiente para ~400 execuções). |
| **Privacidade do Código** | Código aberto visível na web (suas chaves/secrets continuam 100% protegidas e invisíveis). | Código restrito apenas a você e colaboradores convidados. |
| **Recomendação** | **Recomendado**, a menos que o professor exija sigilo do código. | Utilize caso haja restrição expressa de código aberto na disciplina. |

> [!NOTE]
> Suas chaves de API (`GOOGLE_API_KEY`, `GITLAB_TOKEN`, `SONAR_TOKEN`) ficam salvas nos **GitHub Secrets criptografados**. Elas **nunca** aparecem no código, nos logs públicos ou para outros usuários.

---

## 2. Passo 1: Criar o Repositório no GitHub

1. Acesse [github.com/new](https://github.com/new).
2. Defina o nome do repositório (ex: `si600-web-petshop`).
3. Escolha **Public** (ou **Private**).
4. **IMPORTANTE**: **NÃO** marque as opções de inicializar com *README*, *.gitignore* ou *License* (o repositório deve ser criado vazio).
5. Clique em **Create repository**.
6. No seu terminal local, adicione o GitHub como remote secundário:
   ```bash
   git remote add github https://github.com/<seu-usuario>/si600-web-petshop.git
   ```

---

## 3. Passo 2: Configurar a Chave do Gemini nos Secrets

1. **Obter a chave do Gemini**:
   - Acesse o [Google AI Studio](https://aistudio.google.com/).
   - Faça login com sua conta Google e clique em **Get API Key** $\rightarrow$ **Create API key**.
   - Copie a chave gerada (iniciada com `AIzaSy...`).

2. **Adicionar no GitHub Secrets**:
   - No seu repositório do GitHub recém-criado, acesse:  
     `Settings` $\rightarrow$ `Secrets and variables` $\rightarrow$ `Actions`.
   - Clique em **New repository secret**.
   - **Name**: `GOOGLE_API_KEY`
   - **Secret**: Cole a chave do Google AI Studio.
   - Clique em **Add secret**.

*(Opcional: se quiser usar outro provedor de LLM, basta adicionar `OPENAI_API_KEY` ou `ANTHROPIC_API_KEY`).*

---

## 4. Passo 3: Configurar o SonarCloud (SonarQube Gratuito na Nuvem)

1. **Criar conta no SonarCloud**:
   - Acesse [sonarcloud.io](https://sonarcloud.io/) e clique em **Log in with GitHub**.
2. **Importar o Repositório**:
   - Clique no ícone de adição `+` no menu superior $\rightarrow$ **Analyze new project**.
   - Selecione sua organização do GitHub e marque o repositório `si600-web-petshop`.
   - Clique em **Set Up**.
3. **Gerar Token de Autenticação**:
   - Clique na sua foto de perfil no canto superior direito $\rightarrow$ **My Account** $\rightarrow$ **Security**.
   - Em *Generate Token*, digite um nome (ex: `github-actions-gatekeeper`) e clique em **Generate**.
   - Copie o token exibido.
4. **Cadastrar Secrets no GitHub**:
   - No GitHub (`Settings -> Secrets and variables -> Actions`), adicione:
     - **`SONAR_TOKEN`**: Cole o token gerado no passo anterior.
     - **`SONAR_PROJECT_KEY`**: Chave do projeto exibida na página inicial do seu projeto no SonarCloud (ex: `<usuario>_si600-web-petshop`).
     - **`SONAR_HOST_URL`**: `https://sonarcloud.io` (opcional, já é o valor padrão).

---

## 5. Passo 4: Conectar ao GitLab Unicamp (Feedback no MR)

Para que o GitHub Actions comente o relatório diretamente no seu Merge Request no `gitlab.unicamp.br`:

1. **Gerar Token no GitLab Unicamp**:
   - Acesse o [gitlab.unicamp.br](https://gitlab.unicamp.br).
   - Clique na sua foto de perfil $\rightarrow$ **Preferences** (ou *Edit profile*) $\rightarrow$ **Access Tokens** no menu lateral.
   - Clique em **Add new token**:
     - **Token name**: `github-actions-reporter`
     - **Expiration date**: Selecione uma data posterior ao término do semestre.
     - **Scopes**: Marque `api` e `read_repository`.
   - Clique em **Create personal access token** e copie o token (`glpat-...`).

2. **Identificar o ID do Projeto no GitLab**:
   - Acesse a página inicial do projeto no `gitlab.unicamp.br`:  
     `si600-2026/turma-a/grupo-b/si600-web-petshop`.
   - Logo abaixo do título do projeto, localize o número em **Project ID** (ex: `12345`).

3. **Cadastrar Secrets no GitHub**:
   - No GitHub (`Settings -> Secrets and variables -> Actions`), adicione:
     - **`GITLAB_TOKEN`**: Cole o token pessoal (`glpat-...`).
     - **`GITLAB_PROJECT_ID`**: O número do Project ID (ex: `12345`) ou o path `si600-2026/turma-a/grupo-b/si600-web-petshop`.
     - **`GITLAB_URL`**: `https://gitlab.unicamp.br` (opcional, padrão do script).

---

## 6. Passo 5: Sincronização entre GitLab e GitHub

Para que o GitHub Actions seja disparado automaticamente quando houver novas alterações, escolha uma das seguintes formas de sincronização:

### Opção A: Push Duplo no Git Local (Mais Fácil e Recomendado)
Configure o Git na sua máquina para enviar o código para o GitLab e para o GitHub em um único comando `git push`:

```bash
# Adiciona o envio para o GitLab e para o GitHub no mesmo remote 'origin'
git remote set-url --add --push origin https://gitlab.unicamp.br/si600-2026/turma-a/grupo-b/si600-web-petshop.git
git remote set-url --add --push origin https://github.com/<seu-usuario>/si600-web-petshop.git
```

A partir de agora, sempre que você rodar `git push`, o código é enviado **automaticamente para os dois lugares ao mesmo tempo**!

### Opção B: Espelhamento Automático no GitLab Unicamp
Se o GitLab da Unicamp tiver a opção habilitada:
1. No GitLab: `Settings` $\rightarrow$ `Repository` $\rightarrow$ `Mirroring repositories`.
2. **Git repository URL**: `https://<seu-usuario>@github.com/<seu-usuario>/si600-web-petshop.git`.
3. **Mirror direction**: `Push`.
4. **Password**: Cole um GitHub Personal Access Token (com permissão `repo`).
5. Clique em **Mirror repository**.

---

## 7. Como Funciona no Dia a Dia da Equipe

1. O integrante desenvolve na sua branch: `member/joao-calsavara`.
2. Executa `git push origin member/joao-calsavara` e abre o MR para a branch `dev` no GitLab Unicamp.
3. O código é sincronizado com o GitHub, que dispara o workflow `.github/workflows/gatekeeper-ci.yml`.
4. O GitHub Actions na nuvem:
   - Roda os testes de integração.
   - Roda o SonarCloud.
   - Invoca o **AI Gatekeeper (Gemini)** com a sua chave segura.
5. O script `gitlab_reporter.py` conecta no GitLab da Unicamp e:
   - **Posta o comentário com o relatório completo na timeline do Merge Request**.
   - **Atualiza o status do commit para VERDE (Approved) ou VERMELHO (Rejected)**.
6. A equipe e o professor visualizam tudo diretamente no GitLab da Unicamp!
