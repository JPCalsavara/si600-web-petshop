describe('Ambiente de Desenvolvimento - Conexão Front-Back', () => {
  it('deve carregar a página inicial e verificar a seção de status', () => {
    cy.visit('/');
    cy.contains('SI600 - Sistema de Eventos').should('be.visible');
    cy.contains('Status do Ambiente de Desenvolvimento (TEC-01)').should('be.visible');
  });
});
