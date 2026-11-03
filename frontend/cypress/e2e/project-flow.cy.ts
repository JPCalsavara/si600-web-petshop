describe('TEC-06 - criação e listagem de projeto integradas ao backend', () => {
  it('cria um projeto pela UI e o encontra na listagem do backend', () => {
    const suffix = Date.now();
    const company = `TEC06 E2E ${suffix}`;
    const email = `tec06-${suffix}@teste.com`;

    cy.visit('/', {
      onBeforeLoad(win) {
        win.localStorage.setItem('MOCK_ROLE', 'ADMIN');
        win.localStorage.removeItem('MOCK_PROJECT_ID');
      },
    });

    cy.contains('button', 'Adicionar').should('be.visible').click();
    cy.contains('h1', 'Novo Projeto').should('be.visible');

    cy.contains('span', 'Nome / Razão social *').parent().find('input').type(company);
    cy.contains('span', 'CPF / CNPJ *').parent().find('input').type(`12.345.678/0001-${String(suffix).slice(-2)}`);
    cy.contains('span', 'E-mail (login) *').parent().find('input').type(email);
    cy.contains('span', 'Endereço *').parent().find('input').type('Rua TEC-06, 100');
    cy.contains('span', 'Limite envio do PDF *').parent().find('input').type('2050-12-31');
    cy.contains('span', 'Limite de pagamento *').parent().find('input').type('2050-12-31');

    cy.contains('button', 'Salvar').click();
    cy.contains('p', 'Projeto criado com sucesso.').should('be.visible');
    cy.contains('strong', email).should('be.visible');

    cy.contains('button', 'Voltar à lista').click();
    cy.contains('td', company, { timeout: 10000 }).should('be.visible');
    cy.contains('tr', company).should('contain.text', 'Aguardando PDF');
    cy.contains('tr', company).should('contain.text', 'Em dia');
  });
});
