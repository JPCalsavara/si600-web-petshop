// Requer Cypress instalado, backend real e Vite iniciado com VITE_USER_ROLE=ADMIN.
describe('US-02 — catálogo administrativo integrado à API', () => {
  const name = `Taxa E2E ${Date.now()}`;
  const openCatalog = () => {
    cy.visit('/');
    cy.contains('button', 'Catálogo de Taxas').click();
    cy.contains('h1', 'Catálogo de Taxas').should('be.visible');
  };
  const newFee = () => cy.contains('button', 'Nova Taxa').click();
  const save = () => cy.contains('button', 'Salvar').click();

  it('lista, cria os tipos, edita, confirma/cancela desativação e mantém inativas', () => {
    openCatalog();
    newFee();
    cy.get('[name=name]').type(name);
    cy.get('[name=amount]').type('100.50');
    save();
    cy.contains('tr', name).should('contain', 'Ativa').and('contain', 'Fixa');
    cy.contains('tr', name).contains('button', 'Editar').click();
    cy.get('[name=type]').select('VARIAVEL');
    cy.get('[name=amount]').should('not.exist');
    cy.get('[name=measurementUnit]').type('kVA');
    save();
    cy.contains('tr', name).should('contain', 'Variável').and('contain', 'kVA');
    cy.contains('tr', name).contains('button', 'Desativar').click();
    cy.get('dialog').contains('button', 'Cancelar').click();
    cy.contains('tr', name).should('contain', 'Ativa');
    cy.contains('tr', name).contains('button', 'Desativar').click();
    cy.get('dialog').contains('button', 'Confirmar desativação').click();
    cy.contains('tr', name).should('contain', 'Inativa');
    cy.contains('tr', name).contains('button', 'Desativar').should('not.exist');
    openCatalog();
    cy.contains('tr', name).should('contain', 'Inativa');

    newFee();
    cy.get('[name=name]').type(`${name} m²`);
    cy.get('[name=type]').select('POR_METRAGEM');
    cy.get('[name=areaPricingMode]').select('VALOR_POR_M2');
    cy.get('[name=amountPerM2]').type('5');
    save();
    cy.contains('tr', `${name} m²`).should('contain', 'por m²');
    newFee();
    cy.get('[name=name]').type(`${name} unidades`);
    cy.get('[name=type]').select('POR_METRAGEM');
    cy.get('[name=areaPricingMode]').select('UNIDADES_POR_INTERVALO');
    cy.get('[name=areaPerUnitM2]').type('25');
    cy.get('[name=unitAmount]').type('30');
    save();
    cy.contains('tr', `${name} unidades`).should('contain', '1 unidade a cada 25');
    newFee();
    cy.get('[name=name]').type(`${name} variável`);
    cy.get('[name=type]').select('VARIAVEL');
    cy.get('[name=measurementUnit]').type('kVA');
    save();
    cy.contains('tr', `${name} variável`).should('contain', 'valor a definir');
  });

  it('valida obrigatoriedade, positividade e associa erros reais do backend', () => {
    openCatalog();
    newFee();
    save();
    cy.contains('Informe o nome da taxa.').should('be.visible');
    cy.contains('Informe um valor maior que zero.').should('be.visible');
    cy.get('[name=name]').type(`${name} inválida`);
    cy.get('[name=amount]').type('-1');
    save();
    cy.get('[name=amount]').should('have.attr', 'aria-invalid', 'true');
    cy.get('[name=amount]').clear().type('10.001');
    save();
    cy.get('[name=amount]').should('have.attr', 'aria-invalid', 'true');
    cy.contains('Revise os campos indicados.').should('be.visible');
  });

  it('mantém a página utilizável se a integração não responder', () => {
    // Falha de transporte na fronteira HTTP; não substitui regras de domínio por mocks.
    cy.intercept('GET', '**/api/fees', { forceNetworkError: true }).as('offline');
    openCatalog();
    cy.contains('Não foi possível carregar o catálogo.').should('be.visible');
    cy.contains('button', 'Tentar novamente').should('be.visible');
  });
});
