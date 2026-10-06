describe('US-04 e US-08 - fluxo do PDF do estande', () => {
  let projectId = '';
  let projectEmail = '';
  let projectName = '';

  before(() => {
    projectEmail = `e2e-${Date.now()}@teste.com`;
    projectName = `Empresa E2E ${Date.now()}`;
    // 1. Acessa como Admin e cria o projeto via API para isolar o teste do UI de criação
    cy.request({
      method: 'POST',
      url: 'http://localhost:8080/api/projects',
      headers: {
        'X-Authenticated-User-Role': 'ADMIN',
        'X-Authenticated-User-Id': 'admin-1',
      },
      body: {
        name: projectName,
        document: `${Date.now()}0`, // 14 dígitos
        address: 'Rua de Teste, 123',
        area: 'B2C',
        email: projectEmail,
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
        win.localStorage.setItem('MOCK_ACTOR_ID', projectEmail);
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
    
    cy.get('input[type="number"]').type('50'); // Preenche a área para não barrar na área
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
    cy.contains(/PDF EM ANALISE/i, { timeout: 10000 }).should('be.visible');
  });

  it('Admin Sad Path: Tentar rejeitar sem justificativa', () => {
    loginAsAdmin();
    
    // O menu principal já abre em Projetos
    cy.contains('button', 'Análise de PDFs').click();
    
    // Clica em Reprovar na linha da tabela
    cy.contains('tr', projectName).find('button').contains('Reprovar').click();
    
    // Tenta reprovar sem preencher a justificativa (clica no Confirmar do modal)
    cy.contains('button', 'Confirmar reprovação').click();
    
    // O backend (DecisionRequest) valida @NotBlank na justificativa
    cy.contains(/justificativa.*obrigatória/i).should('be.visible');
  });
  
  it('Admin Happy Path: Reprovar o PDF', () => {
    loginAsAdmin();
    cy.contains('button', 'Análise de PDFs').click();
    
    // Clica em Reprovar na linha da tabela
    cy.contains('tr', projectName).find('button').contains('Reprovar').click();
    
    cy.get('textarea[placeholder="Descreva os ajustes que o cliente precisa realizar."]').type('Faltou a marcação das saídas de emergência.');
    cy.contains('button', 'Confirmar reprovação').click();
    
    // Deve sumir da lista (pois o filtro default é 'PDF_EM_ANALISE')
    cy.contains('tr', projectName).should('not.exist');
  });
});
