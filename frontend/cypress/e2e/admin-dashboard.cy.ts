describe('US-18: Dashboard do Administrador', () => {
  const mockDashboardData = {
    inactiveExhibitors: [
      {
        projectId: 'proj-inactive-1',
        companyName: 'Stande Inativo Alfa',
        representativeName: 'Carlos Silva',
        contactEmail: 'carlos@alfa.com',
        createdAt: '2026-10-15T10:00:00Z',
        lastAccessAt: null,
        daysSinceLastAccess: null,
        neverAccessed: true,
      },
      {
        projectId: 'proj-inactive-2',
        companyName: 'Stande Inativo Beta',
        representativeName: 'Mariana Lima',
        contactEmail: 'mariana@beta.com',
        createdAt: '2026-10-10T10:00:00Z',
        lastAccessAt: '2026-10-21T10:00:00Z',
        daysSinceLastAccess: 4,
        neverAccessed: false,
      },
    ],
    pendingDeadlines: [
      {
        id: 'proj-deadline-1',
        companyName: 'Stande Prazo Vencido',
        representativeName: 'Roberto Dias',
        category: 'B2C',
        status: 'AGUARDANDO_PDF',
        pdfDeadline: '2026-10-20T23:59:59Z',
        paymentDeadline: null,
        alertTag: 'ATRASADO',
      },
      {
        id: 'proj-deadline-2',
        companyName: 'Stande Prazo Proximo',
        representativeName: 'Juliana Costa',
        category: 'Music Hub',
        status: 'AGUARDANDO_PDF',
        pdfDeadline: '2026-10-27T23:59:59Z',
        paymentDeadline: null,
        alertTag: 'PRAZO_PROXIMO',
      },
    ],
    pendingApprovals: [
      {
        projectId: 'proj-approval-1',
        companyName: 'Stande Pendente Aprovacao',
        pdfOriginalFilename: 'planta_estande_v1.pdf',
        pdfUploadedAt: '2026-10-24T14:30:00Z',
        boothAreaM2: 75.5,
        status: 'PDF_EM_ANALISE',
      },
    ],
  };

  beforeEach(() => {
    cy.intercept('GET', '**/projects/admin/dashboard', {
      statusCode: 200,
      body: mockDashboardData,
    }).as('getDashboard');

    cy.intercept('GET', '**/projects/admin*', {
      statusCode: 200,
      body: [],
    }).as('getAdminProjects');

    cy.visit('/', {
      onBeforeLoad(win) {
        win.localStorage.setItem('MOCK_ROLE', 'ADMIN');
        win.localStorage.removeItem('MOCK_PROJECT_ID');
      },
    });
  });

  it('exibe o dashboard em formato Kanban com contadores por grupo ("Nunca acessou", "Pendentes", "Em dia")', () => {
    cy.wait('@getDashboard');

    // Valida visualização Kanban ativa por padrão
    cy.get('[data-testid="kanban-board"]').should('be.visible');

    // 1. Coluna Nunca Acessou com contador
    cy.get('[data-testid="inactive-exhibitors-panel"]').should('be.visible');
    cy.get('[data-testid="count-never-accessed"]').should('contain.text', '2');
    cy.contains('Stande Inativo Alfa').should('be.visible');
    cy.get('[data-testid="badge-never-accessed"]')
      .should('be.visible')
      .and('contain.text', 'Nunca acessou');

    cy.contains('Stande Inativo Beta').should('be.visible');
    cy.contains('4 dia(s) atrás').should('be.visible');

    // 2. Coluna Pendentes com contador
    cy.get('[data-testid="pending-deadlines-panel"]').should('be.visible');
    cy.get('[data-testid="count-pending"]').should('contain.text', '3');
    cy.contains('Stande Prazo Vencido').should('be.visible');
    cy.get('[data-testid="badge-alert-ATRASADO"]').should('contain.text', 'Atrasado');

    cy.contains('Stande Prazo Proximo').should('be.visible');
    cy.get('[data-testid="badge-alert-PRAZO_PROXIMO"]').should('contain.text', 'Prazo Próximo');

    // Card de aprovação de planta dentro de Pendentes
    cy.get('[data-testid="pending-approvals-panel"]').should('be.visible');
    cy.contains('Stande Pendente Aprovacao').should('be.visible');
    cy.contains('planta_estande_v1.pdf').should('be.visible');
    cy.contains('75.5 m²').should('be.visible');

    // 3. Coluna Em Dia com contador
    cy.get('[data-testid="on-track-panel"]').should('be.visible');
    cy.get('[data-testid="count-on-track"]').should('contain.text', '0');

    // 4. Teste de alternância para o modo Tabelas e volta para Kanban
    cy.get('[data-testid="btn-view-table"]').click();
    cy.get('[data-testid="inactive-exhibitors-panel-table"]').should('be.visible');
    cy.get('[data-testid="btn-view-kanban"]').click();
    cy.get('[data-testid="kanban-board"]').should('be.visible');

    // 5. Atalho de clique direto para a tela de aprovação técnica
    cy.get('[data-testid="shortcut-review-pdf-proj-approval-1"]')
      .should('be.visible')
      .click();

    // Valida navegação direta para a tela de análise técnica de PDF
    cy.contains('button', 'Voltar aos projetos').should('be.visible');
    cy.contains('h1', 'Aprovação de Projetos').should('be.visible');
  });

  it('pagina os projetos de 4 em 4 no grupo quando houver mais de 4 itens', () => {
    const paginatedData = {
      inactiveExhibitors: [
        { projectId: 'p1', companyName: 'Empresa Alfa 1', representativeName: 'Rep 1', contactEmail: 'e1@t.com', createdAt: '2026-10-01T10:00:00Z', lastAccessAt: null, daysSinceLastAccess: null, neverAccessed: true },
        { projectId: 'p2', companyName: 'Empresa Alfa 2', representativeName: 'Rep 2', contactEmail: 'e2@t.com', createdAt: '2026-10-02T10:00:00Z', lastAccessAt: null, daysSinceLastAccess: null, neverAccessed: true },
        { projectId: 'p3', companyName: 'Empresa Alfa 3', representativeName: 'Rep 3', contactEmail: 'e3@t.com', createdAt: '2026-10-03T10:00:00Z', lastAccessAt: null, daysSinceLastAccess: null, neverAccessed: true },
        { projectId: 'p4', companyName: 'Empresa Alfa 4', representativeName: 'Rep 4', contactEmail: 'e4@t.com', createdAt: '2026-10-04T10:00:00Z', lastAccessAt: null, daysSinceLastAccess: null, neverAccessed: true },
        { projectId: 'p5', companyName: 'Empresa Alfa 5', representativeName: 'Rep 5', contactEmail: 'e5@t.com', createdAt: '2026-10-05T10:00:00Z', lastAccessAt: null, daysSinceLastAccess: null, neverAccessed: true },
      ],
      pendingDeadlines: [],
      pendingApprovals: [],
    };

    cy.intercept('GET', '**/projects/admin/dashboard', {
      statusCode: 200,
      body: paginatedData,
    }).as('getPaginatedDashboard');

    cy.visit('/', {
      onBeforeLoad(win) {
        win.localStorage.setItem('MOCK_ROLE', 'ADMIN');
        win.localStorage.removeItem('MOCK_PROJECT_ID');
      },
    });

    cy.wait('@getPaginatedDashboard');

    // Valida exibição dos 4 primeiros projetos na página 1
    cy.contains('Empresa Alfa 1').should('be.visible');
    cy.contains('Empresa Alfa 4').should('be.visible');
    cy.contains('Empresa Alfa 5').should('not.exist');
    cy.get('[data-testid="page-info-never-accessed"]').should('contain.text', '1 / 2');

    // Avança para a página 2
    cy.get('[data-testid="btn-next-never-accessed"]').click();
    cy.contains('Empresa Alfa 5').should('be.visible');
    cy.contains('Empresa Alfa 1').should('not.exist');
    cy.get('[data-testid="page-info-never-accessed"]').should('contain.text', '2 / 2');

    // Retorna para a página 1
    cy.get('[data-testid="btn-prev-never-accessed"]').click();
    cy.contains('Empresa Alfa 1').should('be.visible');
    cy.get('[data-testid="page-info-never-accessed"]').should('contain.text', '1 / 2');
  });
});
