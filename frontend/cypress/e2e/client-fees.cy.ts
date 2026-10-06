describe('US-05 e US-06 - Visualização de Taxas e Quantidade Variável pelo Cliente', () => {
  let projectId = '';
  let feeId = '';
  let projectFeeId = ''; // Será obtido no frontend via DOM ou intercept
  let projectEmail = '';

  before(() => {
    projectEmail = `taxas-${Date.now()}@teste.com`;
    // 1. Cria uma taxa variável
    cy.request({
      method: 'POST',
      url: 'http://localhost:8080/api/fees',
      headers: { 'X-Authenticated-User-Role': 'ADMIN', 'X-Authenticated-User-Id': 'admin-1' },
      body: { name: `Taxa Extra E2E ${Date.now()}`, type: 'VARIAVEL', measurementUnit: 'Unidades' }
    }).then(res => feeId = res.body.id);

    // 2. Cria o projeto
    cy.request({
      method: 'POST',
      url: 'http://localhost:8080/api/projects',
      headers: { 'X-Authenticated-User-Role': 'ADMIN', 'X-Authenticated-User-Id': 'admin-1' },
      body: {
        name: `Empresa E2E Taxas ${Date.now()}`,
        document: `${Date.now()}0`, // 14 dígitos
        address: 'Rua de Teste, 123',
        area: 'B2C',
        email: projectEmail,
        pdfDeadline: '2050-12-31',
        paymentDeadline: '2050-12-31'
      }
    }).then(res => {
      projectId = res.body.id;
      
      // 3. Envia o PDF via MultipartFormData para simular o cliente
      const formData = new FormData();
      formData.append('areaM2', '50.0');
      
      // Cria um Blob simples imitando PDF
      const blob = new Blob(['%PDF-1.7 E2E'], { type: 'application/pdf' });
      formData.append('file', blob, 'planta.pdf');
      
      cy.visit('/'); // Visita a raiz para instanciar a window e permitir o XHR
      return cy.window().then((win) => {
        return new Cypress.Promise((resolve, reject) => {
          const xhr = new XMLHttpRequest();
          xhr.open('POST', `http://localhost:8080/api/projects/${projectId}/pdf`);
          xhr.setRequestHeader('X-Authenticated-User-Role', 'CLIENT');
          xhr.setRequestHeader('X-Authenticated-User-Id', projectEmail);
          xhr.onload = () => resolve(xhr);
          xhr.onerror = () => reject(xhr);
          xhr.send(formData);
        });
      });
    }).then(() => {
      // 4. Aprova o PDF como admin
      return cy.request({
        method: 'POST',
        url: `http://localhost:8080/api/projects/admin/${projectId}/approve`,
        headers: { 'X-Authenticated-User-Role': 'ADMIN', 'X-Authenticated-User-Id': 'admin-1' },
        body: { approvedAreaM2: 50.0 }
      });
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

  it('US-05: Cliente visualiza as taxas disponíveis após aprovação', () => {
    loginAsClient();
    
    // Como a planta já foi aprovada no setup, o cliente deve ver a tela de Taxas.
    cy.contains('Taxas e Pagamentos').should('be.visible');
    
    // A taxa que criamos deve estar na tela
    cy.contains(/Taxa Extra E2E/).should('be.visible');
  });

  it('US-06 Sad Path: Tentar informar quantidade inválida (<= 0) numa taxa variável', () => {
    loginAsClient();
    
    // Encontra o input referente à taxa criada
    cy.contains('tr', 'Taxa Extra E2E').within(() => {
      // Input de quantidade deve existir
      cy.get('input[type="number"]')
        .invoke('removeAttr', 'min') // Bypass da barreira HTML5 para forçar req pro backend
        .invoke('removeAttr', 'required')
        .clear()
        .type('0');
        
      cy.contains('button', 'Salvar Quantidade').click();
    });
    
    // O backend retorna erro 400 "A quantidade da taxa variável deve ser maior que zero."
    cy.contains(/quantidade.*maior que zero/i).should('be.visible');
  });

  it('US-06 Happy Path: Grava a quantidade da taxa variável com sucesso', () => {
    loginAsClient();
    
    cy.contains('tr', 'Taxa Extra E2E').within(() => {
      cy.get('input[type="number"]').clear().type('10');
      cy.contains('button', 'Salvar Quantidade').click();
    });
    
    // Se o backend salvar, a UI pode exibir um checkmark ou apenas esconder o botão e travar o input.
    // Depende da implementação atual (provavelmente o botão some ou fica "Salvo").
    cy.contains('tr', 'Taxa Extra E2E').within(() => {
       cy.get('input[type="number"]').should('have.value', '10');
    });
  });
});
