const viewports: Cypress.ViewportPreset[] = ['macbook-15', 'iphone-x'];

viewports.forEach((viewport) => {
  describe(`US-07: Configuração de Taxas do Projeto e Cotação de Taxas Variáveis (${viewport})`, () => {
    let projectId = '';
    let projectName = '';
    let variableFeeName = '';
    let variableFeeId = '';

    before(() => {
      const suffix = `${Date.now()}-${viewport === 'macbook-15' ? 'd' : 'm'}`;
      projectName = `Empresa Cotacao ${suffix}`;
      variableFeeName = `Energia Trifasica ${suffix}`;
      const clientEmail = `cotacao-${suffix}@teste.com`;
      let clientToken = '';

      cy.adminRequest({
        method: 'POST',
        url: `${Cypress.expose('API_BASE_URL')}/fees`,
        body: {
          name: variableFeeName,
          description: 'Taxa variável para cotação E2E',
          type: 'VARIAVEL',
          measurementUnit: 'kVA',
        },
      })
        .then((feeRes) => {
          variableFeeId = feeRes.body.id;

          return cy.createProjectAsAdmin({
            name: projectName,
            document: `${Date.now()}9`.slice(0, 14),
            address: 'Av. Cotações, 500',
            area: 'B2C',
            email: clientEmail,
            feeIds: [variableFeeId],
            pdfDeadline: '2050-12-31',
            paymentDeadline: '2050-12-31',
          });
        })
        .then((project) => {
          projectId = project.id;
          return cy.apiLogin(project.email, project.provisionalPassword);
        })
        .then((client) => {
          clientToken = client.token;
          return cy.uploadPdfAs(client, projectId, '40.0', 'planta_cotacao.pdf', '%PDF-1.7 E2E Quote');
        })
        .then(() => {
          return cy.adminRequest({
            method: 'POST',
            url: `${Cypress.expose('API_BASE_URL')}/projects/admin/${projectId}/approve`,
            body: { approvedAreaM2: 40.0 },
          });
        })
        .then(() => {
          return cy.request({
            method: 'GET',
            url: `${Cypress.expose('API_BASE_URL')}/projects/${projectId}/fees`,
            headers: { Authorization: `Bearer ${clientToken}` },
          });
        })
        .then((feesRes) => {
          const pf = feesRes.body.find((f: { feeId: string }) => f.feeId === variableFeeId);
          return cy.request({
            method: 'PUT',
            url: `${Cypress.expose('API_BASE_URL')}/projects/${projectId}/fees/${pf.projectFeeId}/quantity`,
            headers: { Authorization: `Bearer ${clientToken}` },
            body: { quantity: 15 },
          });
        });
    });

    beforeEach(() => {
      cy.viewport(viewport);
      cy.adminLogin().then((admin) => cy.visitAs(admin));
    });

    it('1. Caso Incompleto / Ruim: bloqueia cotação com valor zero ou inválido na tela de Cotações', () => {
      if (viewport === 'iphone-x') {
        cy.contains('button', '☰').click({ force: true });
      }
      cy.contains('button', 'Cotação de taxas').click({ force: true });
      cy.contains('h1', 'Cotação de taxas').should('be.visible');

      cy.contains('article', variableFeeName).within(() => {
        cy.get('input[type="number"]').clear().type('0');
        cy.contains('button', /Salvar cotação|Salvar recotação/).click();
      });

      cy.get('[role="alert"]')
        .should('be.visible')
        .and('contain.text', 'maior que zero');
    });

    it('2. Caso Bom: registra a cotação financeira com sucesso e exibe status Cotada', () => {
      if (viewport === 'iphone-x') {
        cy.contains('button', '☰').click({ force: true });
      }
      cy.contains('button', 'Cotação de taxas').click({ force: true });
      cy.contains('h1', 'Cotação de taxas').should('be.visible');

      cy.contains('article', variableFeeName).within(() => {
        cy.get('input[type="number"]').clear().type('480.50');
        cy.contains('button', /Salvar cotação|Salvar recotação/).click();
      });

      cy.get('[role="status"]').should('be.visible').and('contain.text', 'salva com sucesso');
      cy.contains('article', variableFeeName).should('contain.text', 'Cotada');
    });

    it('3. Caso Bom & Bloqueio de Remoção: abre Configurar Taxas do Projeto e impede desmarcar taxa já cotada', () => {
      cy.contains('button', 'Projetos').click({ force: true });
      cy.get('[data-testid="input-filter-project"]').clear().type(projectName);
      cy.get(`[data-testid="btn-config-fee-${projectId}"]`).click({ force: true });

      cy.get('[data-testid="project-fees-config-page"]').should('be.visible');
      cy.contains('Taxas Atualmente Vinculadas ao Projeto').should('be.visible');
      cy.contains(variableFeeName).should('be.visible');
    });
  });
});
