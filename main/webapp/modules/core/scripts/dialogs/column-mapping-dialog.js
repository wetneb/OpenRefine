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

  let visualizer = new RecipeVisualizer(analyzedOperations.steps);
  visualizer.computeLayout();

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

function addToMultiMap(multiMap, key, value) {
    let list = multiMap.get(key);
    if (list === undefined) {
        list = [];
        multiMap.set(key, list);
    }
    list.push(value);
}

class RecipeVisualizer {
    
    constructor(analyzedOperations) {
      this.analyzedOperations = analyzedOperations;
    }

    computeLayout() {
        let operationsWithIds = this.getColumnConstraints();
        console.log(operationsWithIds);

        // Do a topological sort
        let unconstrainedColumns = new Set();
        let positionMap = new Map();
        for (let i = 0; i != operationsWithIds.columns.length; i++) {
            unconstrainedColumns.add(i);
            positionMap.set(i, 0);
        }
        // Index the constraints in bidirectional maps
        let leftToRight = new Map();
        let rightToLeft = new Map();
        for (const constraint of operationsWithIds.constraints) {
            addToMultiMap(leftToRight, constraint.left, constraint.right);
            addToMultiMap(rightToLeft, constraint.right, constraint.left);
            unconstrainedColumns.delete(constraint.right);
        }

        let sorted = [];
        while (unconstrainedColumns.size > 0) {
            let id = unconstrainedColumns[Symbol.iterator]().next().value;
            unconstrainedColumns.delete(id);
            let position = positionMap.get(id);
            sorted.push(id);
            for(const rightId of leftToRight.get(id) || []) {
                positionMap.set(rightId, Math.max(positionMap.get(rightId), position + 1));
                let otherConstraints = rightToLeft.get(rightId);
                let newConstraints = otherConstraints.filter(x => x != id);
                rightToLeft.set(rightId, newConstraints);
                if (newConstraints.length == 0) {
                    unconstrainedColumns.add(rightId);
                }
            }
        }
        if (sorted.length < operationsWithIds.columns.length) {
            throw new Error("invalid column constraints: loop detected");
        }

        console.log(sorted);
        console.log(positionMap);
    }
  
    getColumnConstraints() {
      let currentColumns = {};
      let currentColumnIds = [];
      let constraints = [];
      let lastColumnReset = 0;
      let columns = [];
      let translatedOperations = [];
      for (let i = 0; i != this.analyzedOperations.length; i++) {
        let operation = this.analyzedOperations[i];

        let columnIds = {
        };

        if (operation.dependencies !== null) {
            columnIds.dependencies = [];
            for(const columnName of operation.dependencies) {
                if (currentColumns[columnName] === undefined) {
                    let columnId = columns.length;
                    currentColumns[columnName] = columnId;
                    currentColumnIds.push(columnId);
                    if (currentColumnIds.length > 1) {
                        constraints.push({left: currentColumnIds[currentColumnIds.length - 2], right: columnId});
                    }
                    columns.push({
                        id: columnId,
                        name: columnName,
                        start: lastColumnReset,
                        firstRequiredAt: i,
                    })
                    columnIds.dependencies.push(columnId);
                }
            }
        }

        if (operation.columnsDiff === null) {
            // make all current columns end here
            for(const [name, id] of Object.entries(currentColumns)) {
                columns[id].end = i;
            }
            currentColumns = {};
            currentColumnIds = [];
            lastColumnReset = i;
        } else {
            columnIds.added = [];
            columnIds.deleted = [];
            columnIds.modified = [];
            for(const deletedColumn of operation.columnsDiff.deleted) {
                let id = currentColumns[deletedColumn];
                if (id !== undefined) {
                    columns[id].end = i;
                    columnIds.deleted.push(id);
                    delete currentColumns[deletedColumn];
                    currentColumnIds = currentColumnIds.filter(x => x != id);
                }
            }
            for (const addedColumn of operation.columnsDiff.added) {
                // TODO check that it does not collide with existing column names
                let name = addedColumn.name;
                let columnId = columns.length;
                currentColumns[name] = columnId;
                columns.push({
                    id: columnId,
                    name,
                    start: i,
                });
                let addedColumnAsId = {
                    id: columnId
                };
                if (addedColumn.afterName != null) {
                    let afterId = currentColumns[addedColumn.afterName];
                    addedColumnAsId.afterId = afterId;
                    let indexOfAfterId = currentColumnIds.indexOf(afterId);
                    if (indexOfAfterId == -1) {
                        indexOfAfterId = currentColumnIds.length - 1;
                    } else {
                        constraints.push({left: afterId, right: columnId});
                    }
                    currentColumnIds.splice(indexOfAfterId + 1, 0, columnId);
                    if (indexOfAfterId + 2 < currentColumnIds.length) {
                        constraints.push({left: afterId, right: currentColumnIds[indexOfAfterId + 2]});
                    }
                } else {
                    // if no afterName is provided, the column gets added at the end of the list
                    currentColumnIds.push(columnId);
                    if (currentColumnIds.length > 1) {
                        constraints.push({left: currentColumnIds[currentColumnIds.length - 2], right: columnId});
                    }
                }
                columnIds.added.push(columnId);
            }
            for (const modifiedColumn of operation.columnsDiff.modified) {
                let id = currentColumns[modifiedColumn];
                if (id !== undefined) {
                    columnIds.modified.push(id);
                }
            }
        }

        translatedOperations.push({
            operation,
            columnIds,
        });
      }
      // fill the end field on all columns that remain at the end
      for(let column of columns) {
        if (column.end === undefined) {
            column.end = this.analyzedOperations.length;
        }
      }
      return {
        columns,
        constraints,
        translatedOperations,
      };
    }
  
  }
