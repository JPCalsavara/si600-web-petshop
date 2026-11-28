const viewports: Cypress.ViewportPreset[] = ['macbook-15', 'iphone-x'];

viewports.forEach((viewport) => {
  describe(`US-16 - Histórico do Projeto (${viewport})`, () => {
    let projectId = '';
    let projectEmail = '';
    let projectName = '';
    let clientSession: Cypress.E2ESession;

    before(() => {
      const stamp = Date.now();
      projectEmail = `us16-${stamp}@teste.com`;
      projectName = `Empresa US16 ${stamp}`;

      cy.createProjectAsAdmin({
        name: projectName,
        document: `${String(stamp).slice(-11)}000`,
        address: { zipCode: '13083-852', street: 'Av. Exposições', number: '500', neighborhood: 'Centro', city: 'Campinas', state: 'SP' },
        area: 'B2C',
        email: projectEmail,
        pdfDeadline: '2050-12-31',
        paymentDeadline: '2050-12-31',
        feeIds: [],
      })
        .then((project) => {
          projectId = project.id;
          return cy.apiLogin(project.email, project.provisionalPassword);
        })
        .then((client) => {
          clientSession = client;
        });
    });

    beforeEach(() => {
      cy.viewport(viewport);
    });

    it('Cliente envia PDF e visualiza a linha do tempo somente leitura na aba Histórico', () => {
      cy.visitAs(clientSession);

      cy.get('input[type="file"]').selectFile('cypress/fixtures/dummy.pdf');
      cy.contains('button', 'Enviar PDF para análise').should('be.visible').click();
      cy.contains(/PDF enviado com sucesso/i, { timeout: 10000 }).should('be.visible');

      cy.contains('button', 'Histórico').should('be.visible').click();
      cy.contains('h2', 'Histórico do Projeto').should('be.visible');
      cy.contains('span', 'Somente leitura').should('be.visible');

      cy.get('ol li').should('have.length.at.least', 2);
      cy.get('ol li').first().should('contain.text', 'Projeto criado');
      cy.get('ol li').eq(1).should('contain.text', 'Envio de PDF da planta');

      // Garante ausência de botões de edição, exclusão ou exportação CSV para Cliente
      cy.get('[aria-label="Histórico do projeto"]').within(() => {
        cy.contains('button', /editar|excluir|apagar|remover/i).should('not.exist');
        cy.get('[data-testid="btn-export-csv-timeline"]').should('not.exist');
      });
    });

    it('Administrador consulta o histórico completo do projeto e exporta os dados em CSV (US-16 e US-17)', () => {
      cy.intercept('GET', `**/api/projects/${projectId}/export/csv`).as('exportCsv');

      cy.adminLogin().then((admin) => cy.visitAs(admin));

      cy.contains('button', 'Projetos').click({ force: true });

      cy.get('[data-testid="input-filter-project"]', { timeout: 10000 })
        .clear()
        .type(projectName);

      // Exporta CSV diretamente na listagem de projetos
      cy.contains('tr', projectName, { timeout: 10000 })
        .contains('button', 'Exportar em CSV')
        .click();

      cy.wait('@exportCsv').then((interception) => {
        expect(interception.response?.statusCode).to.equal(200);
        expect(interception.response?.headers['content-type']).to.include('text/csv');
      });

      cy.contains('tr', projectName)
        .contains('button', 'Histórico')
        .click();

      cy.contains('h2', 'Histórico do Projeto').should('be.visible');
      cy.contains('span', 'Somente leitura').should('be.visible');
      cy.contains('span', 'Projeto criado').should('be.visible');
      cy.contains('span', 'Envio de PDF da planta').should('be.visible');

      // Exporta CSV também pela tela de Histórico do projeto
      cy.get('[data-testid="btn-export-csv-timeline"]')
        .should('be.visible')
        .and('contain.text', 'Exportar em CSV')
        .click();

      cy.wait('@exportCsv').its('response.statusCode').should('eq', 200);

      cy.contains('button', '← Voltar aos projetos').click();
      cy.contains('h1', 'Projetos').should('be.visible');
    });
  });
});
