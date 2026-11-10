/// <reference types="cypress" />

// US-01: o backend agora exige login (Authorization: Bearer) em todas as rotas protegidas.
// Estes comandos fazem login de verdade em POST /auth/login e injetam a sessão no app, no lugar dos
// antigos MOCK_ROLE/MOCK_ACTOR_ID e dos cabeçalhos X-Authenticated-User-*.

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Cypress {
    /** Mesmo formato que o frontend guarda em localStorage (chave AUTH_SESSION). */
    interface E2ESession {
      token: string;
      email: string;
      role: 'ADMIN' | 'CLIENT';
      projectId: string | null;
    }

    interface E2ECreatedProject {
      id: string;
      email: string;
      /** Senha provisória devolvida só na criação do projeto; usada para logar como o Cliente. */
      provisionalPassword: string;
    }

    interface Chainable {
      /** Login real via API. Falha o teste se as credenciais forem recusadas. */
      apiLogin(email: string, password: string): Chainable<E2ESession>;
      /** Login como o Admin semeado (ADMIN_EMAIL / ADMIN_PASSWORD). */
      adminLogin(): Chainable<E2ESession>;
      /** cy.request autenticado como Admin (o cabeçalho Authorization é acrescentado). */
      adminRequest(options: Partial<RequestOptions>): Chainable<Response<any>>;
      /** Cria um projeto como Admin (POST /projects) e devolve o id, o e-mail e a senha provisória. */
      createProjectAsAdmin(body: Record<string, unknown>): Chainable<E2ECreatedProject>;
      /** Abre o app já logado com a sessão informada. */
      visitAs(session: E2ESession, path?: string): Chainable<AUTWindow>;
      /** Envia o PDF da planta como o Cliente da sessão (multipart via XHR). */
      uploadPdfAs(
        session: E2ESession,
        projectId: string,
        areaM2: string,
        filename: string,
        content: string,
      ): Chainable<unknown>;
    }
  }
}

const apiBase = () => String(Cypress.expose('API_BASE_URL'));

Cypress.Commands.add('apiLogin', (email: string, password: string) => {
  return cy
    .request({ method: 'POST', url: `${apiBase()}/auth/login`, body: { email, password }, log: false })
    .then((res) => {
      expect(res.status, `login de ${email}`).to.eq(200);
      const session: Cypress.E2ESession = {
        token: res.body.token,
        email: res.body.email,
        role: res.body.role === 'ADMIN' ? 'ADMIN' : 'CLIENT',
        projectId: res.body.projectId ?? null,
      };
      return session;
    });
});

Cypress.Commands.add('adminLogin', () => {
  return cy.apiLogin(String(Cypress.expose('ADMIN_EMAIL')), String(Cypress.expose('ADMIN_PASSWORD')));
});

Cypress.Commands.add('adminRequest', (options) => {
  return cy.adminLogin().then((admin) => {
    return cy.request({
      ...options,
      headers: { ...(options.headers ?? {}), Authorization: `Bearer ${admin.token}` },
    } as Partial<Cypress.RequestOptions> & { url: string });
  });
});

Cypress.Commands.add('createProjectAsAdmin', (body) => {
  return cy.adminRequest({ method: 'POST', url: `${apiBase()}/projects`, body }).then((res) => {
    expect(res.status, 'criação do projeto').to.eq(201);
    expect(res.body.provisionalPassword, 'senha provisória na resposta').to.be.a('string');
    const created: Cypress.E2ECreatedProject = {
      id: res.body.id,
      email: res.body.contactEmail,
      provisionalPassword: res.body.provisionalPassword,
    };
    return created;
  });
});

Cypress.Commands.add('visitAs', (session, path = '/') => {
  return cy.visit(path, {
    onBeforeLoad(win) {
      win.localStorage.setItem('AUTH_SESSION', JSON.stringify(session));
    },
  });
});

Cypress.Commands.add('uploadPdfAs', (session, projectId, areaM2, filename, content) => {
  const formData = new FormData();
  formData.append('areaM2', areaM2);
  formData.append('file', new Blob([content], { type: 'application/pdf' }), filename);

  // O cy.window() precisa de uma página carregada; a tela de login serve.
  cy.visit('/');
  return cy.window().then(() => {
    return new Cypress.Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open('POST', `${apiBase()}/projects/${projectId}/pdf`);
      xhr.setRequestHeader('Authorization', `Bearer ${session.token}`);
      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) resolve(xhr);
        else reject(new Error(`Upload do PDF falhou: HTTP ${xhr.status} - ${xhr.responseText}`));
      };
      xhr.onerror = () => reject(new Error('Erro de rede ao enviar o PDF'));
      xhr.send(formData);
    });
  });
});

export {};
