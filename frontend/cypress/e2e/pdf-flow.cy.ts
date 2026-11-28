const viewports: Cypress.ViewportPreset[] = ['macbook-15', 'iphone-x'];

viewports.forEach((viewport) => {
  describe(`US-04 e US-08 - fluxo do PDF do estande (${viewport})`, () => {
    let projectId = '';
    let projectEmail = '';
    let projectName = '';
    let clientSession: Cypress.E2ESession;

    before(() => {
      projectEmail = `e2e-${Date.now()}@teste.com`;
      projectName = `Empresa E2E ${Date.now()}`;
      // 1. Acessa como Admin e cria o projeto via API para isolar o teste do UI de criação
      cy.createProjectAsAdmin({
        name: projectName,
        document: `${Date.now()}0`, // 14 dígitos
        address: { zipCode: '13083-852', street: 'Rua de Teste', number: '123', neighborhood: 'Centro', city: 'Campinas', state: 'SP' },
        area: 'B2C',
        email: projectEmail,
        pdfDeadline: '2050-12-31',
        paymentDeadline: '2050-12-31',
        boothAreaM2: 42.5,
        feeIds: [],
      })
        .then((project) => {
          projectId = project.id;
          // 2. O Cliente entra com a senha provisória devolvida na criação
          return cy.apiLogin(project.email, project.provisionalPassword);
        })
        .then((client) => {
          clientSession = client;
        });
    });

    beforeEach(() => {
      cy.viewport(viewport);
    });

    const loginAsClient = () => {
      cy.visitAs(clientSession);
    };

    const loginAsAdmin = () => {
      cy.adminLogin().then((admin) => cy.visitAs(admin));
    };

    it('Sad Path 1: Envio sem arquivo', () => {
      loginAsClient();
      cy.contains('Dados Gerais do PDF').should('be.visible');

      cy.contains('button', 'Enviar PDF para análise').should('be.visible').click();
      
      cy.contains(/Selecione um arquivo PDF/i).should('be.visible');
    });

    it('Sad Path 2: Submissão com extensão/conteúdo inválido', () => {
      loginAsClient();

      cy.get('input[type="file"]').selectFile('cypress/fixtures/invalid.docx', { force: true });
      
      cy.contains('button', 'Enviar PDF para análise').should('be.visible').click();
      
      // O frontend valida a extensão antes de mandar
      cy.contains(/O arquivo deve estar no formato PDF/i).should('be.visible');
    });

    it('BUG-04: Cliente vê a metragem definida pelo Admin e não consegue alterá-la', () => {
      loginAsClient();

      cy.contains('Metragem do estande').should('be.visible');
      cy.contains('42.5 m²').should('be.visible');
      // A metragem é somente leitura: o formulário de envio tem apenas o campo de arquivo.
      cy.get('form input[type="number"]').should('not.exist');
    });

    it('Happy Path: Submissão de PDF válida com sucesso', () => {
      loginAsClient();

      cy.get('input[type="file"]').selectFile('cypress/fixtures/dummy.pdf');
      cy.contains('button', 'Enviar PDF para análise').should('be.visible').click();
      
      // Deve transitar para o estado de "Em análise"
      cy.contains(/PDF EM AN[AÁ]LISE/i, { timeout: 10000 }).should('be.visible');
    });

    it('Admin Sad Path: Tentar rejeitar sem justificativa', () => {
      loginAsAdmin();
      
      // O menu principal já abre em Projetos
      cy.contains('button', 'Análise de PDFs').should('be.visible').click();
      
      // Clica em Reprovar na linha da tabela
      cy.contains('tr', projectName).find('button').contains('Reprovar').should('be.visible').click();
      
      // Tenta reprovar sem preencher a justificativa (clica no Confirmar do modal)
      cy.contains('button', 'Confirmar reprovação').should('be.visible').click();
      
      // O backend (DecisionRequest) valida @NotBlank na justificativa
      cy.contains(/justificativa.*obrigatória/i).should('be.visible');
    });
    
    it('Admin Happy Path: Reprovar o PDF', () => {
      loginAsAdmin();
      cy.contains('button', 'Análise de PDFs').should('be.visible').click();
      
      // Clica em Reprovar na linha da tabela
      cy.contains('tr', projectName).find('button').contains('Reprovar').should('be.visible').click();
      
      cy.get('textarea[placeholder="Descreva os ajustes que o cliente precisa realizar."]').type('Faltou a marcação das saídas de emergência.');
      cy.contains('button', 'Confirmar reprovação').should('be.visible').click();
      
      // Deve sumir da lista (pois o filtro default é 'PDF_EM_ANALISE')
      cy.contains('tr', projectName).should('not.exist');
    });
  });

  describe(`BUG-02 - Aprovação de PDF abre configuração/cotação de taxas do catálogo (${viewport})`, () => {
    let approveProjectId = '';
    let approveProjectName = '';
    let predefinedFeeName = '';
    let extraFeeName = '';
    let clientSession: Cypress.E2ESession;

    before(() => {
      const ts = Date.now();
      approveProjectName = `Empresa BUG02 ${ts}`;
      predefinedFeeName = `AAA Taxa Predefinida ${ts}`;
      extraFeeName = `AAA Taxa Aprovacao ${ts}`;

      // Cria duas taxas fixas com prefixo AAA para aparecerem na primeira página do catálogo
      cy.adminRequest({
        method: 'POST',
        url: `${Cypress.expose('API_BASE_URL')}/fees`,
        body: { name: predefinedFeeName, type: 'FIXA', amount: 150.0 },
      })
        .then((fee1Resp) => {
          const predefinedFeeId = fee1Resp.body.id;

          return cy
            .adminRequest({
              method: 'POST',
              url: `${Cypress.expose('API_BASE_URL')}/fees`,
              body: { name: extraFeeName, type: 'FIXA', amount: 250.0 },
            })
            .then(() =>
              cy.createProjectAsAdmin({
                name: approveProjectName,
                document: `${ts}1`.slice(0, 14),
                address: { zipCode: '13083-852', street: 'Rua BUG-02', number: '456', neighborhood: 'Centro', city: 'Campinas', state: 'SP' },
                area: 'B2C',
                email: `e2e-bug02-${ts}@teste.com`,
                pdfDeadline: '2050-12-31',
                paymentDeadline: '2050-12-31',
                boothAreaM2: 45,
                feeIds: [predefinedFeeId],
              }),
            );
        })
        .then((project) => {
          approveProjectId = project.id;
          return cy.apiLogin(project.email, project.provisionalPassword);
        })
        .then((client) => {
          clientSession = client;
          // O Cliente envia somente o PDF: a metragem (45 m²) já foi definida pelo Admin na criação.
          return cy.uploadPdfAs(client, approveProjectId, 'planta_bug02.pdf', '%PDF-1.7 E2E BUG02');
        });
    });

    beforeEach(() => {
      cy.viewport(viewport);
    });

    it('BUG-04: a aprovação não pede metragem e abre direto a configuração de taxas', () => {
      cy.adminLogin().then((admin) => cy.visitAs(admin));
      cy.contains('button', 'Análise de PDFs').click();

      cy.contains('tr', approveProjectName).within(() => {
        // O campo "Metragem aprovada" foi removido da análise do PDF.
        cy.get('input[type="number"]').should('not.exist');
        cy.contains('button', 'Aprovar').click();
      });

      // Direciona automaticamente para o modal de configuração de taxas com o catálogo
      cy.get('[data-testid="project-fees-modal"]', { timeout: 10000 }).should('be.visible');
      cy.get('[data-testid="project-fees-config-page"]').should('be.visible');

      // Taxa pré-definida já aparece vinculada e marcada; seleciona a nova taxa do catálogo
      cy.contains(predefinedFeeName).should('be.visible');
      cy.contains(extraFeeName).should('be.visible').click();

      cy.contains('button', 'Salvar configuração').first().click();
      cy.contains('[role="status"]', /Configuração de taxas salva com sucesso/i).should('be.visible');

      cy.get('[data-testid="project-fees-modal"]').within(() => {
        cy.contains('Taxas Atualmente Vinculadas ao Projeto (2)').should('be.visible');
        cy.get('button[aria-label="Cancelar"]').click();
      });
      cy.get('[data-testid="project-fees-modal"]').should('not.exist');

      // Cliente visualiza a lista consolidada (pré-definida + vinculada na aprovação)
      cy.visitAs(clientSession);
      cy.contains('Taxas e Pagamentos', { timeout: 10000 }).should('be.visible');
      cy.contains(predefinedFeeName).should('be.visible');
      cy.contains(extraFeeName).should('be.visible');
    });
  });
});
