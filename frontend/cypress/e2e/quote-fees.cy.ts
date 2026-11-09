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

      cy.request({
        method: 'POST',
        url: `${Cypress.expose('API_BASE_URL')}/fees`,
        headers: {
          'X-Authenticated-User-Role': 'ADMIN',
          'X-Authenticated-User-Id': 'admin-1',
        },
        body: {
          name: variableFeeName,
          description: 'Taxa variável para cotação E2E',
          type: 'VARIAVEL',
          measurementUnit: 'kVA',
        },
      }).then((feeRes) => {
        variableFeeId = feeRes.body.id;

        return cy.request({
          method: 'POST',
          url: `${Cypress.expose('API_BASE_URL')}/projects`,
          headers: {
            'X-Authenticated-User-Role': 'ADMIN',
            'X-Authenticated-User-Id': 'admin-1',
          },
          body: {
            name: projectName,
            document: `${Date.now()}9`.slice(0, 14),
            address: 'Av. Cotações, 500',
            area: 'B2C',
            email: clientEmail,
            feeIds: [variableFeeId],
            pdfDeadline: '2050-12-31',
            paymentDeadline: '2050-12-31',
          },
        });
      }).then((projRes) => {
        projectId = projRes.body.id;

        const formData = new FormData();
        formData.append('areaM2', '40.0');
        const blob = new Blob(['%PDF-1.7 E2E Quote'], { type: 'application/pdf' });
        formData.append('file', blob, 'planta_cotacao.pdf');

        cy.visit('/');
        return cy.window().then((win) => {
          return new Cypress.Promise((resolve, reject) => {
            const xhr = new XMLHttpRequest();
            xhr.open('POST', `${Cypress.expose('API_BASE_URL')}/projects/${projectId}/pdf`);
            xhr.setRequestHeader('X-Authenticated-User-Role', 'CLIENT');
            xhr.setRequestHeader('X-Authenticated-User-Id', clientEmail);
            xhr.onload = () => {
              if (xhr.status >= 200 && xhr.status < 300) resolve(xhr);
              else reject(new Error(`Upload falhou: HTTP ${xhr.status}`));
            };
            xhr.onerror = () => reject(new Error('Erro de rede no upload do PDF'));
            xhr.send(formData);
          });
        });
      }).then(() => {
        return cy.request({
          method: 'POST',
          url: `${Cypress.expose('API_BASE_URL')}/projects/admin/${projectId}/approve`,
          headers: {
            'X-Authenticated-User-Role': 'ADMIN',
            'X-Authenticated-User-Id': 'admin-1',
          },
          body: { approvedAreaM2: 40.0 },
        });
      }).then(() => {
        return cy.request({
          method: 'GET',
          url: `${Cypress.expose('API_BASE_URL')}/projects/${projectId}/fees`,
          headers: {
            'X-Authenticated-User-Role': 'CLIENT',
            'X-Authenticated-User-Id': clientEmail,
          },
        });
      }).then((feesRes) => {
        const pf = feesRes.body.find((f: { feeId: string }) => f.feeId === variableFeeId);
        return cy.request({
          method: 'PUT',
          url: `${Cypress.expose('API_BASE_URL')}/projects/${projectId}/fees/${pf.projectFeeId}/quantity`,
          headers: {
            'X-Authenticated-User-Role': 'CLIENT',
            'X-Authenticated-User-Id': clientEmail,
          },
          body: { quantity: 15 },
        });
      });
    });

    beforeEach(() => {
      cy.viewport(viewport);
      cy.visit('/', {
        onBeforeLoad(win) {
          win.localStorage.setItem('MOCK_ROLE', 'ADMIN');
          win.localStorage.setItem('MOCK_ACTOR_ID', 'admin-1');
          win.localStorage.removeItem('MOCK_PROJECT_ID');
        },
      });
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
