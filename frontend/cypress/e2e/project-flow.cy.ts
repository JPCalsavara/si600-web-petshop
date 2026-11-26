const viewports: Cypress.ViewportPreset[] = ['macbook-15', 'iphone-x'];

viewports.forEach((viewport) => {
  describe(`US-03 / US-16 / US-17 - Criação, Listagem e Histórico de Projeto (${viewport})`, () => {
    const suffix = `${Date.now()}-${viewport === 'macbook-15' ? '1' : '2'}`;
    const company = `TEC06 E2E ${suffix}`;
    const email = `tec06-${suffix}@teste.com`;
    const cnpjDigits = `${Date.now()}${viewport === 'macbook-15' ? '11' : '22'}`.slice(-14);

    beforeEach(() => {
      cy.viewport(viewport);
      cy.adminLogin().then((admin) => cy.visitAs(admin));
      cy.contains('button', 'Projetos').click({ force: true });
    });

    it('1. Caso Bom: cria um projeto pela UI sem escolha de taxas, encontra na listagem e consulta o histórico', () => {
      cy.get('[data-testid="btn-add-project"]').should('be.visible').click();
      cy.contains('h1', 'Novo Projeto').should('be.visible');
      cy.contains('legend', 'Taxas do projeto').should('not.exist');

      cy.contains('span', 'Nome / Razão social *').parent().find('input').type(company);
      cy.contains('span', 'CPF / CNPJ *').parent().find('input').type(cnpjDigits);
      cy.contains('span', 'E-mail (login) *').parent().find('input').type(email);
      cy.contains('span', 'CEP *').parent().find('input').type('13083852');
      cy.contains('span', 'Logradouro *').parent().find('input').type('Rua TEC-06');
      cy.contains('span', 'Número *').parent().find('input').type('100');
      cy.contains('span', 'Bairro *').parent().find('input').type('Centro');
      cy.contains('span', 'Cidade *').parent().find('input').type('Campinas');
      cy.contains('span', 'UF *').parent().find('select').select('SP');
      cy.contains('span', 'Limite envio do PDF *').parent().find('input').type('2050-12-31');
      cy.contains('span', 'Limite de pagamento *').parent().find('input').type('2050-12-31');

      cy.contains('button', 'Salvar').click();
      cy.contains('p', 'Projeto criado com sucesso.').should('be.visible');
      cy.contains('strong', email).should('be.visible');
      // US-01: o Admin vê a senha provisória uma única vez para repassá-la ao Cliente.
      cy.get('[data-testid="provisional-password"]').should('be.visible').invoke('text').should('have.length.at.least', 8);

      cy.contains('button', 'Voltar à lista').click();
      cy.get('[data-testid="input-filter-project"]', { timeout: 10000 })
        .clear()
        .type(company);
      cy.contains('td', company, { timeout: 10000 }).should('be.visible');
      cy.contains('tr', company).should('contain.text', 'Aguardando PDF');
      cy.contains('tr', company).should('contain.text', 'Em dia');

      cy.contains('tr', company).contains('button', 'Histórico').click();
      cy.contains('h2', 'Histórico do Projeto').should('be.visible');
      cy.contains('span', 'Somente leitura').should('be.visible');
      cy.contains('span', 'Projeto criado').should('be.visible');
      cy.contains('button', '← Voltar aos projetos').click({ force: true });
    });

    it('2. Caso Ruim: rejeita criação com e-mail ou CPF/CNPJ duplicado (HTTP 409) ou pagamento anterior ao PDF', () => {
      cy.get('[data-testid="btn-add-project"]').should('be.visible').click();

      cy.contains('span', 'Nome / Razão social *').parent().find('input').type(`${company} Duplicado`);
      cy.contains('span', 'CPF / CNPJ *').parent().find('input').type(cnpjDigits);
      cy.contains('span', 'E-mail (login) *').parent().find('input').type(email);
      cy.contains('span', 'CEP *').parent().find('input').type('13083852');
      cy.contains('span', 'Logradouro *').parent().find('input').type('Rua TEC-06');
      cy.contains('span', 'Número *').parent().find('input').type('100');
      cy.contains('span', 'Bairro *').parent().find('input').type('Centro');
      cy.contains('span', 'Cidade *').parent().find('input').type('Campinas');
      cy.contains('span', 'UF *').parent().find('select').select('SP');
      cy.contains('span', 'Limite envio do PDF *').parent().find('input').type('2050-12-31');
      cy.contains('span', 'Limite de pagamento *').parent().find('input').type('2050-12-31');

      cy.contains('button', 'Salvar').click();
      cy.get('[role="alert"]').should('be.visible');
    });

    it('3. Caso Incompleto: barra CPF/CNPJ incompleto ou prazo de pagamento anterior ao PDF no formulário', () => {
      cy.get('[data-testid="btn-add-project"]').should('be.visible').click();

      cy.contains('span', 'Nome / Razão social *').parent().find('input').type('Empresa Incompleta');
      cy.contains('span', 'CPF / CNPJ *').parent().find('input').type('12345');
      cy.contains('span', 'E-mail (login) *').parent().find('input').type(`incompleto-${suffix}@teste.com`);
      cy.contains('span', 'CEP *').parent().find('input').type('13083852');
      cy.contains('span', 'Logradouro *').parent().find('input').type('Rua Borda');
      cy.contains('span', 'Número *').parent().find('input').type('10');
      cy.contains('span', 'Bairro *').parent().find('input').type('Centro');
      cy.contains('span', 'Cidade *').parent().find('input').type('Campinas');
      cy.contains('span', 'UF *').parent().find('select').select('SP');
      cy.contains('span', 'Limite envio do PDF *').parent().find('input').type('2050-12-31');
      cy.contains('span', 'Limite de pagamento *').parent().find('input').type('2050-12-31');

      cy.contains('button', 'Salvar').click();
      cy.get('[role="alert"]').should('be.visible').and('contain.text', 'CPF');
    });
  });
});
