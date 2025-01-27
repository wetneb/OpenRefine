describe(__filename, function () {
  it('Apply a JSON', function () {
    cy.loadAndVisitProject('food.mini');

    // Check some columns before the test
    cy.get('table.data-table thead th[title="Shrt_Desc"]').should('exist');
    cy.get('table.data-table thead th[title="Water"]').should('exist');


    // find the "apply" button
    cy.get('#or-proj-undoRedo').click();
    cy.wait(500); // eslint-disable-line
    cy.get('#refine-tabs-history .history-panel-controls')
      .contains('Apply')
      .click();
      
    // Load a recipe file
    const recipeFile = { filePath: 'recipe.json', mimeType: 'application/json' };
    cy.get('#file-input[type="file"]').attachFile(recipeFile);
    
    // Column mapping dialog
    cy.get('.dialog-header').contains('Map recipe columns to project columns');
    cy.get('select[name="column_0"]').contains('Energ_Kcal');
    cy.get('select[name="column_2"]').select('Shrt_Desc');

    cy.get('input[type="submit"]').contains('Run operations').click();

    // Check the effects on the project data
    cy.get('table.data-table thead th[title="Energ_Kcal"]').should(
      'not.to.exist'
    );

    cy.assertNotificationContainingText('3 operations applied');
    cy.get('a#or-proj-undoRedo > span.count')
      .invoke('text')
      .should('equal', '3 / 3');
    cy.get('#summary-bar > span')
      .invoke('text')
      .should('equal', '5 rows');

    cy.get('.notification-action a').should('to.contain', 'Undo').click();

    cy.get('a#or-proj-undoRedo > span.count')
      .invoke('text')
      .should('equal', '0 / 3');
  });

  it('Use an invalid JSON payload', function () {
    cy.loadAndVisitProject('food.mini');

    // find the "apply" button
    cy.get('#or-proj-undoRedo').click();
    cy.wait(500); // eslint-disable-line
    cy.get('#refine-tabs-history .history-panel-controls')
      .contains('Apply')
      .click();

    // Load an invalid recipe file
    const recipeFile = { filePath: 'donut-records.json', mimeType: 'application/json' };
    cy.get('#file-input[type="file"]').attachFile(recipeFile);
      
    cy.get('.dialog-container .history-operation-json-error').contains('Invalid recipe file');
  });

});
