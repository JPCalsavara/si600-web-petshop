const viewports: Cypress.ViewportPreset[] = ['macbook-15', 'iphone-x'];

// US-01 - Login e perfis de acesso. Usa o Admin semeado (ADMIN_EMAIL/ADMIN_PASSWORD) e cria um Cliente real
// pela API, que entra com a senha provisória devolvida na criação do projeto.
viewports.forEach((viewport) => {
  describe(`US-01 - Login e perfis de acesso (${viewport})`, () => {
    let clientEmail = '';
    let clientPassword = '';
    let clientProjectId = '';

    const adminEmail = () => String(Cypress.expose('ADMIN_EMAIL'));
    const adminPassword = () => String(Cypress.expose('ADMIN_PASSWORD'));
    const apiBase = () => String(Cypress.expose('API_BASE_URL'));

    const fillLogin = (email: string, password: string) => {
      if (email) cy.get('#login-email').clear().type(email);
      if (password) cy.get('#login-password').clear().type(password, { log: false });
      cy.contains('button', 'Entrar').click();
    };

    const storedSession = () =>
      cy.window().then((win) => {
        const raw = win.localStorage.getItem('AUTH_SESSION');
        return raw ? JSON.parse(raw) : null;
      });

    before(() => {
      const stamp = `${Date.now()}-${viewport === 'macbook-15' ? 'd' : 'm'}`;
      clientEmail = `login-${stamp}@teste.com`;

      cy.createProjectAsAdmin({
        name: `Empresa Login ${stamp}`,
        document: `${Date.now()}7`.slice(0, 14),
        address: 'Rua do Login, 1',
        area: 'B2C',
        email: clientEmail,
        feeIds: [],
        pdfDeadline: '2050-12-31',
        paymentDeadline: '2050-12-31',
      }).then((project) => {
        clientProjectId = project.id;
        clientPassword = project.provisionalPassword;
      });
    });

    beforeEach(() => {
      cy.viewport(viewport);
      cy.visit('/');
    });

    it('1. Caso Bom (Admin): entra com e-mail e senha, vê o menu administrativo e sai pelo botão Sair', () => {
      cy.contains('h1', 'Gestão de Eventos').should('be.visible');

      fillLogin(adminEmail(), adminPassword());

      cy.contains('button', 'Catálogo de Taxas').should('exist');
      storedSession().then((session) => {
        expect(session.role).to.eq('ADMIN');
        expect(session.token).to.be.a('string').and.not.be.empty;
      });

      cy.contains('button', 'Sair').click({ force: true });

      cy.contains('h1', 'Gestão de Eventos').should('be.visible');
      storedSession().should('be.null');
    });

    it('2. Caso Bom (Cliente): vê só o próprio projeto e não vê o menu administrativo', () => {
      fillLogin(clientEmail, clientPassword);

      cy.contains('Dados Gerais do PDF').should('be.visible');
      cy.contains('button', 'Catálogo de Taxas').should('not.exist');
      storedSession().then((session) => {
        expect(session.role).to.eq('CLIENT');
        expect(session.projectId).to.eq(clientProjectId);
      });
    });

    it('3. Isolamento de perfil: o token do Cliente é recusado (403) nas rotas de Admin', () => {
      cy.apiLogin(clientEmail, clientPassword).then((client) => {
        const headers = { Authorization: `Bearer ${client.token}` };

        cy.request({ url: `${apiBase()}/projects/admin`, headers, failOnStatusCode: false })
          .its('status')
          .should('eq', 403);
        cy.request({ url: `${apiBase()}/admin/quotes`, headers, failOnStatusCode: false })
          .its('status')
          .should('eq', 403);
        cy.request({ url: `${apiBase()}/projects/${clientProjectId}`, headers })
          .its('status')
          .should('eq', 200);
      });
    });

    it('4. Caso Ruim: senha errada mostra o erro padronizado e permanece na tela de login', () => {
      fillLogin(adminEmail(), 'senha-errada-123');

      cy.get('[role="alert"]').should('be.visible').and('contain.text', 'E-mail ou senha inválidos.');
      cy.contains('h1', 'Gestão de Eventos').should('be.visible');
      storedSession().should('be.null');
    });

    it('5. Caso Ruim: e-mail inexistente recebe a mesma mensagem (não revela se a conta existe)', () => {
      fillLogin('ninguem-existe@teste.com', 'qualquer-senha');

      cy.get('[role="alert"]').should('be.visible').and('contain.text', 'E-mail ou senha inválidos.');
      storedSession().should('be.null');
    });

    it('6. Caso Incompleto: enviar o formulário em branco não chama o backend e pede e-mail e senha', () => {
      cy.intercept('POST', '**/auth/login').as('login');

      cy.contains('button', 'Entrar').click();

      cy.get('[role="alert"]').should('be.visible').and('contain.text', 'Informe o e-mail e a senha.');
      cy.get('@login.all').should('have.length', 0);
    });

    it('7. Caso Incompleto: só o e-mail preenchido também é barrado', () => {
      fillLogin(adminEmail(), '');

      cy.get('[role="alert"]').should('be.visible').and('contain.text', 'Informe o e-mail e a senha.');
    });

    it('8. API sem token ou com token inválido responde 401', () => {
      cy.request({ url: `${apiBase()}/projects/admin`, failOnStatusCode: false }).its('status').should('eq', 401);
      cy.request({
        url: `${apiBase()}/projects/admin`,
        headers: { Authorization: 'Bearer token-que-nao-existe' },
        failOnStatusCode: false,
      })
        .its('status')
        .should('eq', 401);
    });

    it('9. Sessão expirada ou inválida no navegador volta para a tela de login', () => {
      cy.visitAs({ token: 'token-invalido', email: adminEmail(), role: 'ADMIN', projectId: null });

      cy.contains('h1', 'Gestão de Eventos', { timeout: 10000 }).should('be.visible');
      storedSession().should('be.null');
    });
  });
});
