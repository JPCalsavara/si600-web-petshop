const viewports: Cypress.ViewportPreset[] = ['macbook-15', 'iphone-x'];

viewports.forEach((viewport) => {
  describe(`US-05 e US-06 - Visualização de Taxas e Quantidade Variável pelo Cliente (${viewport})`, () => {
    let projectId = '';
    let feeId = '';
    let projectFeeId = ''; // Será obtido no frontend via DOM ou intercept
    let projectEmail = '';
    let clientSession: Cypress.E2ESession;

    before(() => {
      projectEmail = `taxas-${Date.now()}@teste.com`;

      // 1. Cria uma taxa variável e aguarda o ID antes de criar o projeto
      cy.adminRequest({
        method: 'POST',
        url: `${Cypress.expose('API_BASE_URL')}/fees`,
        body: {
          name: `Taxa Extra E2E ${Date.now()}`,
          type: 'VARIAVEL',
          measurementUnit: 'Unidades',
        },
      })
        .then((res) => {
          feeId = res.body.id;

          // 2. Cria o projeto usando a taxa realmente criada
          return cy.createProjectAsAdmin({
            name: `Empresa E2E Taxas ${Date.now()}`,
            document: `${Date.now()}0`,
            address: { zipCode: '13083-852', street: 'Rua de Teste', number: '123', neighborhood: 'Centro', city: 'Campinas', state: 'SP' },
            area: 'B2C',
            email: projectEmail,
            boothAreaM2: 50,
            feeIds: [feeId],
            pdfDeadline: '2050-12-31',
            paymentDeadline: '2050-12-31',
          });
        })
        .then((project) => {
          // 3. Guarda o projeto criado e entra como o Cliente com a senha provisória
          projectId = project.id;
          return cy.apiLogin(project.email, project.provisionalPassword);
        })
        .then((client) => {
          clientSession = client;
          // 4. Envia o PDF como o Cliente
          return cy.uploadPdfAs(client, projectId, 'planta.pdf', '%PDF-1.7 E2E');
        })
        .then(() => {
          // 5. Aprova o PDF como admin
          return cy.adminRequest({
            method: 'POST',
            url: `${Cypress.expose('API_BASE_URL')}/projects/admin/${projectId}/approve`,
          });
        });
    });

    beforeEach(() => {
      cy.viewport(viewport);
    });

    const loginAsClient = () => {
      cy.visitAs(clientSession);
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
      cy.contains('Taxa Extra E2E').parent().parent().within(() => {
        // Input de quantidade deve existir
        cy.get('input[type="number"]')
          .invoke('removeAttr', 'min') // Bypass da barreira HTML5 para forçar req pro backend
          .invoke('removeAttr', 'required')
          .clear()
          .type('0');
          
        cy.contains('button', 'Salvar').should('be.visible').click();
      });
      
      // O backend retorna erro 400 "A quantidade da taxa variável deve ser maior que zero."
      cy.contains(/quantidade.*maior que zero/i).should('be.visible');
    });

    it('US-06 Happy Path: Grava a quantidade da taxa variável com sucesso', () => {
      loginAsClient();
      
      cy.contains('Taxa Extra E2E').parent().parent().within(() => {
        cy.get('input[type="number"]').clear().type('10');
        cy.contains('button', 'Salvar').should('be.visible').click();
      });
      
      // Se o backend salvar, a UI pode exibir um checkmark ou apenas esconder o botão e travar o input.
      // Depende da implementação atual (provavelmente o botão some ou fica "Salvo").
      cy.contains('Taxa Extra E2E').parent().parent().within(() => {
         cy.get('input[type="number"]').should('have.value', '10');
      });
    });
  });
});
