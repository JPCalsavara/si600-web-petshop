const viewports: Cypress.ViewportPreset[] = ['macbook-15', 'iphone-x'];

viewports.forEach((viewport) => {
  describe(`US-18: Dashboard do Administrador (${viewport})`, () => {
    let inactiveCompany = '';
    let pendingApprovalCompany = '';
    let approvalProjectId = '';

    before(() => {
      const suffix = `${Date.now()}-${viewport}`;
      inactiveCompany = `Stande Inativo ${suffix}`;
      pendingApprovalCompany = `Stande Em Analise ${suffix}`;
      const pendingEmail = `analise-${suffix}@teste.com`;

      cy.request({
        method: 'POST',
        url: `${Cypress.expose('API_BASE_URL')}/projects`,
        headers: {
          'X-Authenticated-User-Role': 'ADMIN',
          'X-Authenticated-User-Id': 'admin-1',
        },
        body: {
          name: inactiveCompany,
          document: `${Date.now()}1`.slice(0, 14),
          address: 'Av. Dashboard, 100',
          area: 'B2C',
          email: `inativo-${suffix}@teste.com`,
          feeIds: [],
          pdfDeadline: '2050-12-31',
          paymentDeadline: '2050-12-31',
        },
      });

      cy.request({
        method: 'POST',
        url: `${Cypress.expose('API_BASE_URL')}/projects`,
        headers: {
          'X-Authenticated-User-Role': 'ADMIN',
          'X-Authenticated-User-Id': 'admin-1',
        },
        body: {
          name: pendingApprovalCompany,
          document: `${Date.now()}2`.slice(0, 14),
          address: 'Av. Dashboard, 200',
          area: 'Music Hub',
          email: pendingEmail,
          feeIds: [],
          pdfDeadline: '2050-12-31',
          paymentDeadline: '2050-12-31',
        },
      }).then((res) => {
        approvalProjectId = res.body.id;

        const formData = new FormData();
        formData.append('areaM2', '75.5');
        const blob = new Blob(['%PDF-1.7 E2E Dashboard'], { type: 'application/pdf' });
        formData.append('file', blob, 'planta_dashboard.pdf');

        cy.visit('/');
        return cy.window().then((win) => {
          return new Cypress.Promise((resolve, reject) => {
            const xhr = new XMLHttpRequest();
            xhr.open('POST', `${Cypress.expose('API_BASE_URL')}/projects/${approvalProjectId}/pdf`);
            xhr.setRequestHeader('X-Authenticated-User-Role', 'CLIENT');
            xhr.setRequestHeader('X-Authenticated-User-Id', pendingEmail);
            xhr.onload = () => {
              if (xhr.status >= 200 && xhr.status < 300) resolve(xhr);
              else reject(new Error(`Upload falhou: HTTP ${xhr.status}`));
            };
            xhr.onerror = () => reject(new Error('Erro de rede no upload do PDF'));
            xhr.send(formData);
          });
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

    it('1. Caso Bom: exibe o dashboard real em formato Kanban com KPIs, filtros e atalho de análise de planta', () => {
      cy.get('[data-testid="dashboard-kpi-grid"]').should('be.visible');
      cy.get('[data-testid="kpi-total-projects"]').should('be.visible');
      cy.get('[data-testid="kpi-never-accessed"]').should('be.visible');
      cy.get('[data-testid="kpi-pending-approvals"]').should('be.visible');
      cy.get('[data-testid="kpi-critical-deadlines"]').should('be.visible');
      cy.get('[data-testid="kpi-pending-quotes"]').should('be.visible');
      cy.get('[data-testid="kpi-estimated-revenue"]').should('be.visible');

      cy.get('[data-testid="kanban-board"]').should('be.visible');
      cy.get('[data-testid="inactive-exhibitors-panel"]').should('be.visible');
      cy.get('[data-testid="pending-deadlines-panel"]').should('be.visible');
      cy.get('[data-testid="on-track-panel"]').should('be.visible');

      cy.get('[data-testid="dashboard-search-input"]').clear().type(inactiveCompany);
      cy.contains(inactiveCompany).should('be.visible');
      cy.get('[data-testid="badge-never-accessed"]').should('contain.text', 'Nunca acessou');

      cy.get('[data-testid="dashboard-search-input"]').clear().type(pendingApprovalCompany);
      cy.contains(pendingApprovalCompany).should('be.visible');
      cy.get(`[data-testid="shortcut-review-pdf-${approvalProjectId}"]`).should('be.visible').click();
      cy.contains('Análise de Plantas (PDF)').should('be.visible');
    });

    it('2. Caso Ruim / Borda: busca por termo inexistente exibe estado vazio nas colunas e permite limpar filtros', () => {
      cy.get('[data-testid="dashboard-search-input"]').clear().type('EMPRESA_INEXISTENTE_999999');
      cy.contains('Nenhum expositor inativo registrado.').should('be.visible');
      cy.contains('Nenhum projeto com pendências no momento.').should('be.visible');
      cy.contains('Nenhum projeto regularizado registrado.').should('be.visible');

      cy.get('[data-testid="btn-clear-dashboard-filters"]').should('be.visible').click();
      cy.get('[data-testid="dashboard-search-input"]').should('have.value', '');
    });
  });
});
