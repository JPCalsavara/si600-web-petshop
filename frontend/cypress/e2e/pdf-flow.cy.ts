describe('US-04 e US-08 - fluxo do PDF do estande', () => {
  it('exibe a área de cliente para envio do PDF', () => {
    cy.visit('/');
    cy.contains('Dados Gerais do PDF').should('be.visible');
    cy.contains('Metragem oficial do estande').should('be.visible');
    cy.get('input[type="file"]').should('have.attr', 'accept', 'application/pdf,.pdf');
  });

  it('exibe a área administrativa para análise', () => {
    cy.visit('/');
    // Execute este cenário com VITE_USER_ROLE=ADMIN no ambiente E2E.
    if (Cypress.env('USER_ROLE') === 'ADMIN') {
      cy.contains('Clientes Pendentes de Aprovação').should('be.visible');
      cy.contains('PDF em Análise').should('be.visible');
    }
  });
});
