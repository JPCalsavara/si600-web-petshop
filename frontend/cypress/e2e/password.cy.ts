const viewports: Cypress.ViewportPreset[] = ['macbook-15', 'iphone-x'];

// US-14 - Troca e recuperação de senha. Cria Clientes reais pela API (senha provisória devolvida na criação)
// e usa o Admin semeado (ADMIN_EMAIL/ADMIN_PASSWORD) para atender os pedidos de recuperação.
viewports.forEach((viewport) => {
  describe(`US-14 - Troca e recuperação de senha (${viewport})`, () => {
    const NEW_PASSWORD = 'nova-senha-456';
    const apiBase = () => String(Cypress.expose('API_BASE_URL'));
    let sequence = 0;

    const newClient = () => {
      sequence = (sequence + 1) % 10;
      const stamp = `${Date.now()}-${viewport === 'macbook-15' ? 'd' : 'm'}${sequence}`;
      return cy
        .createProjectAsAdmin({
          name: `Empresa Senha ${stamp}`,
          document: `${Date.now()}${sequence}`.slice(0, 14),
          address: { zipCode: '13083-852', street: 'Rua da Senha', number: '1', neighborhood: 'Centro', city: 'Campinas', state: 'SP' },
          area: 'B2C',
          email: `senha-${stamp}@teste.com`,
          feeIds: [],
          pdfDeadline: '2050-12-31',
          paymentDeadline: '2050-12-31',
        })
        .then((project) => ({ email: project.email, password: project.provisionalPassword }));
    };

    const openChangePassword = () => cy.contains('button', 'Alterar senha').click({ force: true });

    const fillChangePassword = (current: string, next: string, confirmation: string) => {
      if (current) cy.get('#current-password').clear().type(current, { log: false });
      if (next) cy.get('#new-password').clear().type(next, { log: false });
      if (confirmation) cy.get('#confirm-password').clear().type(confirmation, { log: false });
      cy.contains('button', 'Salvar nova senha').click();
    };

    const storedSession = () =>
      cy.window().then((win) => {
        const raw = win.localStorage.getItem('AUTH_SESSION');
        return raw ? JSON.parse(raw) : null;
      });

    beforeEach(() => {
      cy.viewport(viewport);
    });

    // ---------- Casos bons ----------

    it('1. Caso Bom: o Cliente troca a senha pelo perfil; a nova funciona e a antiga deixa de valer', () => {
      newClient().then((client) => {
        cy.apiLogin(client.email, client.password).then((session) => cy.visitAs(session));

        openChangePassword();
        fillChangePassword(client.password, NEW_PASSWORD, NEW_PASSWORD);

        cy.get('[role="status"]').should('contain.text', 'Senha alterada com sucesso');
        cy.apiLogin(client.email, NEW_PASSWORD).its('role').should('eq', 'CLIENT');
        cy.request({
          method: 'POST',
          url: `${apiBase()}/auth/login`,
          body: { email: client.email, password: client.password },
          failOnStatusCode: false,
        })
          .its('status')
          .should('eq', 401);
      });
    });

    it('2. Caso Bom: "Esqueci a senha" chega ao Admin, que gera a senha temporária e o usuário volta a entrar', () => {
      newClient().then((client) => {
        cy.visit('/');
        cy.contains('button', 'Esqueci a senha').click();
        cy.get('#forgot-email').type(client.email);
        cy.contains('button', 'Solicitar recuperação').click();
        cy.get('[role="status"]').should('contain.text', 'enviado ao administrador');

        cy.adminLogin().then((admin) => cy.visitAs(admin));
        cy.contains('button', 'Usuários').click({ force: true });
        cy.contains('[data-testid="password-reset-row"]', client.email)
          .contains('button', 'Gerar senha temporária')
          .click();

        cy.get('[data-testid="temporary-password"]')
          .invoke('text')
          .then((temporary) => {
            cy.apiLogin(client.email, temporary.trim()).its('role').should('eq', 'CLIENT');
          });
        cy.contains('[data-testid="password-reset-row"]', client.email).should('not.exist');
      });
    });

    // ---------- Casos ruins ----------

    it('3. Caso Ruim: senha atual errada mostra o erro e NÃO derruba a sessão (422, não 401)', () => {
      newClient().then((client) => {
        cy.apiLogin(client.email, client.password).then((session) => cy.visitAs(session));

        openChangePassword();
        fillChangePassword('senha-errada-999', NEW_PASSWORD, NEW_PASSWORD);

        cy.get('[role="alert"]').should('be.visible').and('contain.text', 'Senha atual incorreta.');
        storedSession().should('not.be.null');
        cy.apiLogin(client.email, client.password).its('role').should('eq', 'CLIENT');
      });
    });

    it('4. Caso Ruim: nova senha fraca, diferente da confirmação ou igual à atual não chega ao backend', () => {
      newClient().then((client) => {
        cy.apiLogin(client.email, client.password).then((session) => cy.visitAs(session));
        cy.intercept('POST', '**/auth/change-password').as('change');

        openChangePassword();
        fillChangePassword(client.password, 'curta1', 'curta1');
        cy.get('[role="alert"]').should('contain.text', 'pelo menos 8 caracteres');

        fillChangePassword(client.password, 'somenteletras', 'somenteletras');
        cy.get('[role="alert"]').should('contain.text', 'uma letra e um número');

        fillChangePassword(client.password, NEW_PASSWORD, 'outra-senha-789');
        cy.get('[role="alert"]').should('contain.text', 'A confirmação não confere');

        fillChangePassword(client.password, client.password, client.password);
        cy.get('[role="alert"]').should('contain.text', 'diferente da atual');

        cy.get('@change.all').should('have.length', 0);
      });
    });

    it('5. Caso Ruim: e-mail não cadastrado recebe a mesma resposta (não revela se a conta existe)', () => {
      cy.visit('/');
      cy.contains('button', 'Esqueci a senha').click();
      cy.get('#forgot-email').type('ninguem-existe@teste.com');
      cy.contains('button', 'Solicitar recuperação').click();

      cy.get('[role="status"]').should('contain.text', 'enviado ao administrador');
      cy.get('[role="alert"]').should('not.exist');
    });

    it('6. Isolamento de perfil: o token do Cliente é recusado (403) nas rotas de recuperação do Admin', () => {
      newClient().then((client) => {
        cy.apiLogin(client.email, client.password).then((session) => {
          const headers = { Authorization: `Bearer ${session.token}` };
          cy.request({ url: `${apiBase()}/admin/password-resets`, headers, failOnStatusCode: false })
            .its('status')
            .should('eq', 403);
        });
      });
    });

    // ---------- Casos incompletos ----------

    it('7. Caso Incompleto: trocar a senha com o formulário em branco não chama o backend', () => {
      newClient().then((client) => {
        cy.apiLogin(client.email, client.password).then((session) => cy.visitAs(session));
        cy.intercept('POST', '**/auth/change-password').as('change');

        openChangePassword();
        cy.contains('button', 'Salvar nova senha').click();
        cy.get('[role="alert"]').should('be.visible').and('contain.text', 'Informe a senha atual.');

        cy.get('#current-password').type(client.password, { log: false });
        cy.contains('button', 'Salvar nova senha').click();
        cy.get('[role="alert"]').should('contain.text', 'Informe a nova senha.');

        cy.get('@change.all').should('have.length', 0);
      });
    });

    it('8. Caso Incompleto: "Esqueci a senha" sem e-mail pede o e-mail e não chama o backend', () => {
      cy.visit('/');
      cy.intercept('POST', '**/auth/forgot-password').as('forgot');
      cy.contains('button', 'Esqueci a senha').click();
      cy.contains('button', 'Solicitar recuperação').click();

      cy.get('[role="alert"]').should('be.visible').and('contain.text', 'Informe o e-mail.');
      cy.get('@forgot.all').should('have.length', 0);
    });
  });
});
