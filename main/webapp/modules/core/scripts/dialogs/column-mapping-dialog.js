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
  elmts.dependenciesExplanation.text("Required columns:");
  elmts.newColumnsExplanation.text("Created columns:");

  elmts.applyButton.val($.i18n('core-buttons/perform-op'));
  elmts.backButton.text($.i18n('core-buttons/previous'));

  var trHeader = $('<tr></tr>')
    .append($('<th></th>').text('In the recipe'))    
    .append($('<th></th>').text('In this project'))
  trHeader.clone()
    .appendTo(elmts.dependenciesTableHead);
  trHeader
    .appendTo(elmts.newColumnsTableHead);

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
      );
    if (expectedToExist) {
      tr.appendTo(elmts.dependenciesTableBody);
    } else {
      tr.appendTo(elmts.newColumnsTableBody);
    }
    idx++;
  }

  var level = DialogSystem.showDialog(frame);

  elmts.backButton.on('click',function() {
    DialogSystem.dismissUntil(level - 1);
  });

  let visualizer = new RecipeVisualizer(analyzedOperations.steps);
  visualizer.draw();

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
      this.hoverTimeout = null;
      this.tooltip = null;
    }

    draw() {
        let operationsWithIds = this.computeColumnIds();
        console.log(operationsWithIds);
        let columnPositions = this.computeColumnPositions(operationsWithIds);

        const svg = $("#recipe-svg");
        let sliceHeight = 35;
        let columnDistance = 30;
        let columnHoverMargin = 10;
        let opaqueMargin = 5;
        let columnColor = '#888';
        let columnWidth = 2;
        let dependencyRadius = 5;

        // Resize canvas to make it of a fitting size
        let maxX = 0;
        let maxY = 0;
        for (const column of operationsWithIds.columns) {
            maxX = Math.max(maxX, (columnPositions.get(column.id) + 2) * columnDistance);
            maxY = Math.max(maxY, column.end * sliceHeight);
        }
        svg.attr("viewBox", `${-columnDistance/2} ${-sliceHeight/2} ${maxX + columnDistance} ${maxY + sliceHeight/2}`);
        svg.attr("width", `${maxX + 1.5*columnDistance}`);

        // Draw all column lines
        for(const column of operationsWithIds.columns) {
            let xPos = (columnPositions.get(column.id) + 1) * columnDistance;
            let line = $(document.createElementNS('http://www.w3.org/2000/svg', 'line'))
              .attr('id', `column-${column.id}`)
              .attr('x1', xPos)
              .attr('y1', (column.start - 0.5) * sliceHeight)
              .attr('x2', xPos)
              .attr('y2', (column.end + 0.5) * sliceHeight)
              .attr("stroke", columnColor)
              .attr("stroke-width", columnWidth)
              .attr("alt", column.name)
              .appendTo(svg);
            let hoverArea = $(document.createElementNS('http://www.w3.org/2000/svg', 'rect'))
              .attr('x', xPos - columnHoverMargin)
              .attr('y', (column.start - 0.5) * sliceHeight)
              .attr('width', columnHoverMargin * 2)
              .attr('height', (column.end - column.start + 1)*sliceHeight)
              .attr('fill', 'white')
              .attr('fill-opacity', 0)
              .appendTo(svg);
            this.setUpTooltip(svg, hoverArea, line, 8, column.name, true);
        }

        // Draw all operations
        let sliceId = 0;
        for(const slice of operationsWithIds.translatedOperations) {
          if (slice.operation.columnsDiff == null) {
            let rect = $(document.createElementNS('http://www.w3.org/2000/svg', 'rect'))
              .attr('id', `opaque-${sliceId}`)
              .attr('x', opaqueMargin)
              .attr('y', sliceId * sliceHeight + opaqueMargin)
              .attr('width', maxX - 2*opaqueMargin)
              .attr('height', sliceHeight - 2*opaqueMargin)
              .attr('stroke', 'black')
              .attr('stroke-width', 1)
              .attr('fill', 'white')
              .appendTo(svg);
            this.addOperationLogo(svg, maxX / 2, (sliceId + 0.5) * sliceHeight, slice.operation.operation);
            this.setUpTooltip(svg, rect, rect, 5, slice.operation.operation.description, false);
          } else {
            // Draw operation circles for added columns
            for (const addedColumn of slice.columnIds.added) {
              let columnId = addedColumn.id;
              let xPos = (columnPositions.get(columnId) + 1) * columnDistance;
              let yPos = (sliceId + 0.5)* sliceHeight;
              if (addedColumn.afterId !== undefined) {
                let xPosSource = (columnPositions.get(addedColumn.afterId) + 1) * columnDistance;
                var line = $(document.createElementNS('http://www.w3.org/2000/svg', 'line'))
                  .attr('x1', xPos)
                  .attr('y1', yPos)
                  .attr('x2', xPosSource)
                  .attr('y2', yPos)
                  .attr('stroke', columnColor)
                  .attr('stroke-width', columnWidth)
                  .appendTo(svg);
              }
              this.makeOperationCircle(svg, xPos, yPos, `circle-${sliceId}-${columnId}`, slice.operation.operation);
            }

            // Draw operation circles for modified and deleted columns
            for (const columnId of slice.columnIds.modified.concat(slice.columnIds.deleted)) {
              let xPos = (columnPositions.get(columnId) + 1) * columnDistance;
              let yPos = (sliceId + 0.5)* sliceHeight;
              this.makeOperationCircle(svg, xPos, yPos, `circle-${sliceId}-${columnId}`, slice.operation.operation);
            }

            // Draw other column dependencies
            let modifiedOrDeleted = new Set(slice.columnIds.modified.concat(slice.columnIds.deleted));
            for (const columnId of slice.columnIds.dependencies || []) {
              if (!modifiedOrDeleted.has(columnId)) {
                let xPos = (columnPositions.get(columnId) + 1) * columnDistance;
                let yPos = (sliceId + 0.5) * sliceHeight;
                let circle = $(document.createElementNS('http://www.w3.org/2000/svg', 'circle'))
                  .attr('cx', xPos)
                  .attr('cy', yPos)
                  .attr('r', dependencyRadius)
                  .attr('fill', columnColor)
                  .appendTo(svg);
                this.setUpTooltip(svg, circle, circle, dependencyRadius, operationsWithIds.columns[columnId].name, false);
              }
            }
          }
          sliceId++;
        }
    }

    makeOperationCircle(svg, xPos, yPos, id, operation) {
      let circleRadius = 12;
      let circleHoverRadius = 15;
      let circle = $(document.createElementNS('http://www.w3.org/2000/svg', 'circle'))
        .attr('id', id)
        .attr('cx', xPos)
        .attr('cy', yPos)
        .attr('r', circleRadius)
        .attr('stroke', 'black')
        .attr('fill', 'white')
        .appendTo(svg);
      this.addOperationLogo(svg, xPos, yPos, operation);
      let hoverArea = $(document.createElementNS('http://www.w3.org/2000/svg', 'circle'))
        .attr('id', id)
        .attr('cx', xPos)
        .attr('cy', yPos)
        .attr('r', circleHoverRadius)
        .attr('opacity', 0)
        .attr('fill', 'white')
        .appendTo(svg);
      this.setUpTooltip(svg, hoverArea, circle, circleRadius, operation.description, false);
      return circle;
    }

    addOperationLogo(svg, xPos, yPos, operation) {
      let firstLetter = operation.description[0];
      $(document.createElementNS('http://www.w3.org/2000/svg', 'text'))
        .attr('x', xPos)
        .attr('y', yPos)
        .attr('dominant-baseline', 'middle')
        .attr('text-anchor', 'middle')
        .text(firstLetter)
        .appendTo(svg);
    }

    setUpTooltip(svg, hoverArea, relativeTo, tooltipOffset, text, useCursorY) {
      let self = this;
      hoverArea.on("mouseenter", function(event) {
        if (self.hoverTimeout != null) {
          clearTimeout(self.hoverTimeout);
          self.hoverTimeout = null;
        }
        self.hoverTimeout = setTimeout(function() {
          self.hoverTimeout = null;
          if (self.tooltip != null) {
            self.tooltip.remove();
            self.tooltip = null;
          }
          let rect = relativeTo[0].getBoundingClientRect();
          let x = (rect.left + rect.right) / 2;
          let y = (rect.top + rect.bottom) / 2;
          if (useCursorY) {
            y = event.clientY;
          }
          self.tooltip = $("<div></div>")
            .css("left", x + tooltipOffset)
            .text(text)
            .attr('class', 'recipe-tooltip')
            .appendTo(svg.parent());
          // TODO: center vertically?
          self.tooltip.css("top", y);//  - 0.5 * tooltip.height());
        }, 300);

        hoverArea.on("mouseleave", function() {
          if (self.hoverTimeout != null) {
            clearTimeout(self.hoverTimeout);
            self.hoverTimeout = null;
          }
          if (self.tooltip != null) {
            self.tooltip.remove();
            self.tooltip = null;
          }
        });
      });
    }

    computeColumnPositions(operationsWithIds) {
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

        // main loop of the topological sort algorithm, greedily allocating
        // positions to columns which are no longer constrained to be to the right of any column
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

        return positionMap;
    }
  
    computeColumnIds() {
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

        columnIds.dependencies = [];
        var fullDependencies = operation.dependencies || [];
        if (operation.columnsDiff != null) {
          let addedColumns = operation.columnsDiff.added
            .filter(added => added.afterName != undefined)
            .map(added => added.afterName);
          fullDependencies = fullDependencies.concat(addedColumns);
        }
        for(const columnName of new Set(fullDependencies)) {
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
            }
            columnIds.dependencies.push(currentColumns[columnName]);
        }

        if (operation.columnsDiff === null) {
            // make all current columns end here
            for(const [name, id] of Object.entries(currentColumns)) {
                columns[id].end = i;
            }
            currentColumns = {};
            currentColumnIds = [];
            lastColumnReset = i + 1;
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
                    start: i + 1,
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
                        constraints.push({left: columnId, right: currentColumnIds[indexOfAfterId + 2]});
                    }
                } else {
                    // if no afterName is provided, the column gets added at the end of the list
                    currentColumnIds.push(columnId);
                    if (currentColumnIds.length > 1) {
                        constraints.push({left: currentColumnIds[currentColumnIds.length - 2], right: columnId});
                    }
                }
                columnIds.added.push(addedColumnAsId);
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
