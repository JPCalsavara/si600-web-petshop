const viewports: Cypress.ViewportPreset[] = ['macbook-15', 'iphone-x'];

viewports.forEach((viewport) => {
  describe(`US-16 - Histórico do Projeto (${viewport})`, () => {
    let projectId = '';
    let projectEmail = '';
    let projectName = '';

    before(() => {
      const stamp = Date.now();
      projectEmail = `us16-${stamp}@teste.com`;
      projectName = `Empresa US16 ${stamp}`;

      cy.request({
        method: 'POST',
        url: `${Cypress.expose('API_BASE_URL')}/projects`,
        headers: {
          'X-Authenticated-User-Role': 'ADMIN',
          'X-Authenticated-User-Id': 'admin-us16',
        },
        body: {
          name: projectName,
          document: `${String(stamp).slice(-11)}000`,
          address: 'Av. Exposições, 500',
          area: 'B2C',
          email: projectEmail,
          pdfDeadline: '2050-12-31',
          paymentDeadline: '2050-12-31',
          feeIds: [],
        },
      }).then((resp) => {
        projectId = resp.body.id;
      });
    });

    beforeEach(() => {
      cy.viewport(viewport);
    });

    it('Cliente envia PDF e visualiza a linha do tempo somente leitura na aba Histórico', () => {
      cy.visit('/', {
        onBeforeLoad(win) {
          win.localStorage.setItem('MOCK_ROLE', 'CLIENT');
          win.localStorage.setItem('MOCK_ACTOR_ID', projectEmail);
          win.localStorage.setItem('MOCK_PROJECT_ID', projectId);
        },
      });

      cy.get('input[type="number"]').clear().type('45');
      cy.get('input[type="file"]').selectFile('cypress/fixtures/dummy.pdf');
      cy.contains('button', 'Enviar PDF para análise').should('be.visible').click();
      cy.contains(/PDF enviado com sucesso/i, { timeout: 10000 }).should('be.visible');

      cy.contains('button', 'Histórico').should('be.visible').click();
      cy.contains('h2', 'Histórico do Projeto').should('be.visible');
      cy.contains('span', 'Somente leitura').should('be.visible');

      cy.get('ol li').should('have.length.at.least', 2);
      cy.get('ol li').first().should('contain.text', 'Projeto criado');
      cy.get('ol li').eq(1).should('contain.text', 'Envio de PDF da planta');

      // Garante ausência de botões de edição ou exclusão na linha do tempo
      cy.get('[aria-label="Histórico do projeto"]').within(() => {
        cy.contains('button', /editar|excluir|apagar|remover/i).should('not.exist');
      });
    });

    it('Administrador consulta o histórico completo do projeto a partir da listagem', () => {
      cy.visit('/', {
        onBeforeLoad(win) {
          win.localStorage.setItem('MOCK_ROLE', 'ADMIN');
          win.localStorage.setItem('MOCK_ACTOR_ID', 'admin-us16');
          win.localStorage.removeItem('MOCK_PROJECT_ID');
        },
      });

      cy.contains('tr', projectName, { timeout: 10000 })
        .contains('button', 'Histórico')
        .click();

      cy.contains('h2', 'Histórico do Projeto').should('be.visible');
      cy.contains('span', 'Somente leitura').should('be.visible');
      cy.contains('span', 'Projeto criado').should('be.visible');
      cy.contains('span', 'Envio de PDF da planta').should('be.visible');

      cy.contains('button', '← Voltar aos projetos').click();
      cy.contains('h1', 'Projetos').should('be.visible');
    });
  });
});
