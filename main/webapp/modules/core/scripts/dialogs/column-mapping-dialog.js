/*

Copyright 2024, OpenRefine contributors
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

 * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
 * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,           
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY           
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */

function ColumnMappingDialog(operations, analyzedOperations) {
  var self = this;
  var frame = $(DOM.loadHTML("core", "scripts/dialogs/column-mapping-dialog.html"));
  var elmts = DOM.bind(frame);

  var columnDependencies = analyzedOperations.dependencies;
  var newColumns = analyzedOperations.newColumns;
  
  elmts.dialogHeader.text($.i18n('core-project/map-columns'));
  elmts.explanation.text("Select which columns the recipe should be applied to.");

  elmts.applyButton.val($.i18n('core-buttons/perform-op'));
  elmts.backButton.text($.i18n('core-buttons/previous'));

  var trHeader = $('<tr></tr>')
    .append($('<th></th>').text('In the recipe'))    
    .append($('<th></th>').text('In the project'))
    .appendTo(elmts.tableHead);

  let columnExists = function(columnName) {
    return theProject.columnModel.columns.find(column => column.name === columnName) !== undefined;
  };

  let allColumns = columnDependencies.map(c => [true, c]).concat(newColumns.map(c => [false, c]));
  var idx = 0;
  for (const tuple of allColumns) {
    var expectedToExist = tuple[0];
    var columnName = tuple[1];
    var name = `column_${idx}`;
    var defaultValue = columnName;
    if (columnExists(columnName) != expectedToExist) {
      defaultValue = '';
    }
    var tr = $('<tr></tr>')
      .append(
        $('<td></td>').append(
         $('<label></label>').attr("for", name).text(columnName))
      ).append(
        $('<td></td>').append(
          $('<input type="text" />')
             .attr('value', defaultValue)
             .data('originalName', columnName)
             .data('expectedToExist', expectedToExist)
             .attr('required', 'true')
             .attr('name', name))
      ).appendTo(elmts.tableBody);
    idx++;
  }

  var level = DialogSystem.showDialog(frame);

  elmts.backButton.on('click',function() {
    DialogSystem.dismissUntil(level - 1);
  });

  elmts.form.on('submit',function(e) {
    e.preventDefault();
    // collect the column mapping from the form
    var renames = {
     dependencies: {},
     newColumns: {}
    };
    var errorFound = false;
    elmts.tableBody.find('input').each(function(index, child) {
      let inputElem = $(child);
      let fromColumn = inputElem.data('originalName');
      let toColumn = inputElem.val();
      let expectedToExist = inputElem.data('expectedToExist');
      if (columnExists(toColumn) !== expectedToExist) {
        errorFound = true;
        inputElem.addClass('invalid');
        alert(`Invalid column ${toColumn}, ` + (expectedToExist ? 'expected to exist' : 'expected not to exist'));
      } else {
        if (expectedToExist) {
          renames.dependencies[fromColumn] = toColumn;
        } else {
          renames.newColumns[fromColumn] = toColumn;
        }
      }
    });
    
    if (!errorFound) {
      Refine.postCoreProcess(
          "apply-operations",
          {},
          {
            operations: JSON.stringify(operations),
            renames: JSON.stringify(renames)
          },
          { everythingChanged: true },
          {
            onDone: function(o) {
              if (o.code == "pending") {
                // Something might have already been done and so it's good to update
                Refine.update({ everythingChanged: true });
              }
              DialogSystem.dismissUntil(level - 1);
            },
            onError: function(e) {
              elmts.errorContainer.text($.i18n('core-project/json-invalid', e.message));   
            },
          }
      );
    }
  });
}

