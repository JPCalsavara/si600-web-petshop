describe('US-04 e US-08 - fluxo do PDF do estande', () => {
  let projectId = '';

  before(() => {
    // 1. Acessa como Admin e cria o projeto via API para isolar o teste do UI de criação
    cy.request({
      method: 'POST',
      url: 'http://localhost:8080/api/projects',
      headers: {
        'X-Authenticated-User-Role': 'ADMIN',
        'X-Authenticated-User-Id': 'admin-1',
      },
      body: {
        name: `Empresa E2E ${Date.now()}`,
        document: `${Date.now()}0`, // 14 dígitos
        address: 'Rua de Teste, 123',
        area: 'B2C',
        email: `e2e-${Date.now()}@teste.com`,
        pdfDeadline: '2050-12-31',
        paymentDeadline: '2050-12-31',
        feeIds: [],
      }
    }).then((resp) => {
      projectId = resp.body.id;
    });
  });

  const loginAsClient = () => {
    cy.visit('/', {
      onBeforeLoad(win) {
        win.localStorage.setItem('MOCK_ROLE', 'CLIENT');
        win.localStorage.setItem('MOCK_PROJECT_ID', projectId);
      }
    });
  };

  const loginAsAdmin = () => {
    cy.visit('/', {
      onBeforeLoad(win) {
        win.localStorage.setItem('MOCK_ROLE', 'ADMIN');
        win.localStorage.setItem('MOCK_PROJECT_ID', '');
      }
    });
  };

  it('Sad Path 1: Envio sem arquivo', () => {
    loginAsClient();
    cy.contains('Dados Gerais do PDF').should('be.visible');
    
    // Como a validação do frontend bloqueia input[required], ignoramos essa etapa
    // Tenta submeter direto
    cy.contains('button', 'Enviar PDF para análise').click();
    
    cy.contains(/Selecione um arquivo PDF/i).should('be.visible');
  });

  it('Sad Path 2: Submissão com extensão/conteúdo inválido', () => {
    loginAsClient();
    
    cy.get('input[type="number"]').type('50');
    cy.get('input[type="file"]').selectFile('cypress/fixtures/invalid.docx', { force: true });
    
    cy.contains('button', 'Enviar PDF para análise').click();
    
    // O frontend valida a extensão antes de mandar
    cy.contains(/O arquivo deve estar no formato PDF/i).should('be.visible');
  });

  it('Sad Path 3: Submissão com área zerada/negativa', () => {
    loginAsClient();
    
    cy.get('input[type="number"]').clear().type('-5');
    cy.get('input[type="file"]').selectFile('cypress/fixtures/dummy.pdf', { force: true });
    
    cy.contains('button', 'Enviar PDF para análise').click();
    
    cy.contains(/Informe a metragem oficial/i).should('be.visible');
  });

  it('Happy Path: Submissão de PDF válida com sucesso', () => {
    loginAsClient();
    
    cy.get('input[type="number"]').clear().type('50');
    cy.get('input[type="file"]').selectFile('cypress/fixtures/dummy.pdf');
    cy.contains('button', 'Enviar PDF para análise').click();
    
    // Deve transitar para o estado de "Em análise"
    cy.contains('PDF em Análise', { timeout: 10000 }).should('be.visible');
  });

  it('Admin Sad Path: Tentar rejeitar sem justificativa', () => {
    loginAsAdmin();
    
    cy.contains('button', 'Projetos').click();
    cy.contains('button', 'Análise de PDFs').click();
    
    // Expande o painel do nosso projeto
    cy.contains(projectId).click();
    
    // Tenta reprovar sem preencher a justificativa
    cy.contains('button', 'Reprovar').click();
    
    // O backend (DecisionRequest) valida @NotBlank na justificativa
    cy.contains(/justificativa.*obrigatória/i).should('be.visible');
  });
  
  it('Admin Happy Path: Reprovar o PDF', () => {
    loginAsAdmin();
    cy.contains('button', 'Análise de PDFs').click();
    cy.contains(projectId).click();
    
    cy.get('textarea[placeholder="Justificativa..."]').type('Faltou a marcação das saídas de emergência.');
    cy.contains('button', 'Reprovar').click();
    
    // Deve sumir da lista ou exibir mensagem de sucesso
    cy.contains('Faltou a marcação das saídas de emergência.').should('not.exist');
  });
});
