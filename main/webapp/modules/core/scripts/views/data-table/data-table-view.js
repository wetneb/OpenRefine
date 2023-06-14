/*

Copyright 2010, Google Inc.
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

function DataTableView(div) {
  this._div = div;

  this._gridPagesSizes = JSON.parse(Refine.getPreference("ui.browsing.pageSize", null));
  this._gridPagesSizes = this._checkPaginationSize(this._gridPagesSizes, [ 5, 10, 25, 50, 100, 500, 1000 ]);
  this._pageSize = ( this._gridPagesSizes[0] < 10 ) ? 10 : this._gridPagesSizes[0];

  this._showRecon = true;
  this._collapsedColumnNames = {};
  this._sorting = { criteria: [] };
  this._columnHeaderUIs = [];
  this._shownulls = false;
  this._autoUpdateTimeout = undefined;
  this._lastRequestedPagination = undefined;

  this._showRows({start: 0});
}

DataTableView._extenders = [];

/*
  To extend, do something like this

  DataTableView.extendMenu(function(dataTableView, menu) {
      ...
    MenuSystem.appendTo(menu, [ "core/view" ], {
      "label": "Test",
      "click": function() {
          alert("Test");
      } 
    });
  });
*/
DataTableView.extendMenu = function(extender) {
  DataTableView._extenders.push(extender);
};

DataTableView.prototype.getSorting = function() {
  return this._sorting;
};

DataTableView.prototype.resize = function() {
  
  var topHeight =
    this._div.find(".viewpanel-header").outerHeight(true);
  var tableContainerIntendedHeight = this._div.innerHeight() - topHeight;
  
  var tableContainer = this._div.find(".data-table-container").css("display", "block");
  var tableContainerVPadding = tableContainer.outerHeight(true) - tableContainer.height();
  tableContainer.height((tableContainerIntendedHeight - tableContainerVPadding) + "px");
};

DataTableView.prototype.update = function(onDone, preservePage, onError) {
  var paginationOptions = { start: 0 };
  if (preservePage) {
    if (theProject.rowModel.start !== undefined) {
      paginationOptions = { start: theProject.rowModel.start };
    } else {
      paginationOptions = { end: theProject.rowModel.end };
    }
  }
  this._showRows(paginationOptions, onDone, onError);
};

DataTableView.prototype.render = function() {
  var self = this;

  var oldTableDiv = this._div.find(".data-table-container");
  var scrollLeft = (oldTableDiv.length > 0) ? oldTableDiv[0].scrollLeft : 0;

  // utility to convert px widths to em.
  // The factor is computed only on demand (and once) because it might trigger some
  // DOM rendering given that it uses computed styles
  var emFactor = null;
  var getEmFactor = function() {
    if (emFactor === null) {
      emFactor = parseFloat(getComputedStyle($(".data-table-container colgroup")[0]).fontSize);
    }
    return emFactor;
  };
  // store the current width of each column to be able to restore it later
  this._div.find(".data-table-container col").each(function(index) {
    var column = $(this);
    if (column.data('name')) {
      var width = column.width() / getEmFactor();
      DataTableView.columnWidthCache.set(column.data('name'), width);
    }
  });

  var html = $(
    '<div class="viewpanel-header">' +
      '<div class="viewpanel-rowrecord" bind="rowRecordControls">'+$.i18n('core-views/show-as')+': ' +
        '<span bind="modeSelectors"></span>' + 
      '</div>' +
      '<div class="viewpanel-pagesize" bind="pageSizeControls"></div>' +
      '<div class="viewpanel-sorting" bind="sortingControls"></div>' +
      '<div class="viewpanel-paging" bind="pagingControls"></div>' +
    '</div>' +
    '<div bind="dataTableContainer" class="data-table-container">' +
      '<table class="data-table">'+
        '<colgroup bind="colGroup"></colgroup>'+
        '<thead bind="tableHeader" class="data-table-header">'+
        '</thead>'+
        '<tbody bind="table" class="data-table">'+
        '</tbody>'+
      '</table>' +
    '</div>'
  );
  var elmts = DOM.bind(html);

  var renderBrowsingModeLink = function(label, value) {
    var a = $('<a href="javascript:{}"></a>')
    .addClass("viewPanel-browsingModes-mode")
    .text(label)
    .appendTo(elmts.modeSelectors);

    if (value == ui.browsingEngine.getMode()) {
      a.addClass("selected");
    } else {
      a.addClass("action").on('click',function(evt) {
        ui.browsingEngine.setMode(value);
      });
    }
  };
  renderBrowsingModeLink($.i18n('core-views/rows'), "row-based");
  renderBrowsingModeLink($.i18n('core-views/records'), "record-based");

  this._renderPagingControls(elmts.pageSizeControls, elmts.pagingControls);

  if (this._sorting.criteria.length > 0) {
    this._renderSortingControls(elmts.sortingControls);
  }

  this._renderDataTables(elmts.table[0], elmts.tableHeader[0], elmts.colGroup);
  this._div.empty().append(html);

  // show/hide null values in cells
  $(".data-table-null").toggle(self._shownulls);

  this.resize();
  
  elmts.dataTableContainer[0].scrollLeft = scrollLeft;
};

// update a grid after progress of computation of a long-running operation
DataTableView.prototype.rerenderChangedCells = function() {
  let self = this;
  $('tbody.data-table tr').each(function(localRowIndex) {
    var rowElement = $(this);
    var row = theProject.rowModel.rows[localRowIndex];
    rowElement.find('td').each(function(rawIndex) {
      let cellIndex = rawIndex - 3; // skip the first three cells (star, flag, row number)
      if (cellIndex >= 0) {
        let cell = row.cells[cellIndex];
        let previousCell = self._lastRenderedRowModel.rows[localRowIndex].cells[cellIndex];
        if (!deeplyEquals(cell, previousCell)) {
          if (!previousCell || !previousCell.p) {
            console.warn('re-rendering a cell which was not previously marked as pending');
          }
          let td = $(this);
          td.empty(); 
          new DataTableCellUI(self, cell, row.i, cellIndex, td[0]);
        }
      }
    });
  });
};

DataTableView.prototype.updateColumnStats = function() {
  var self = this;
  for (let columnHeaderUI of self._columnHeaderUIs) {
    columnHeaderUI.updateColumnStats();
  }
}

DataTableView.prototype._renderSortingControls = function(sortingControls) {
  var self = this;

  $('<a href="javascript:{}"></a>')
  .addClass("action")
  .text($.i18n('core-views/sort/single') + " ")
  .append($('<img>').attr("src", "images/down-arrow.png"))
  .appendTo(sortingControls)
  .on('click',function() {
    self._createSortingMenu(this);
  });
};

DataTableView.prototype._renderPagingControls = function(pageSizeControls, pagingControls) {
  var self = this;

  var rowIds = theProject.rowModel.rows.map(row => row.k);
  if (theProject.rowModel.start !== undefined) {
     rowIds.push(theProject.rowModel.start);
  } else {
     rowIds.push(theProject.rowModel.end);
  }
  var minRowId = Math.min(... rowIds);
  var maxRowId = Math.max(... rowIds);

  var firstPage = $('<a href="javascript:{}">&laquo; '+$.i18n('core-views/first')+'</a>').appendTo(pagingControls);
  var previousPage = $('<a href="javascript:{}">&lsaquo; '+$.i18n('core-views/previous')+'</a>').appendTo(pagingControls);
  if (theProject.rowModel.previousPageId !== undefined) {
    firstPage.addClass("action").on('click',function(evt) { self._onClickFirstPage(this, evt); });
    previousPage.addClass("action").on('click',function(evt) { self._onClickPreviousPage(this, evt); });
  } else {
    firstPage.addClass("inaction");
    previousPage.addClass("inaction");
  }

  // the engineStats might not be available yet at this point,
  // but we only need it for the total number of rows, to size the
  // input field appropriately, so the exact value is not critical
  var totalRows = 2000;
  if (theProject.engineStats) {
    totalRows = theProject.engineStats.totalRows;
  }

  var minRowInputSize = 20 + (8* totalRows.toString().length);
  var minRowInput = $('<input type="number">')
    .attr("id", "viewpanel-paging-current-min-row")
    .attr("min", 1)
    .attr("max", totalRows)
    .css("width", minRowInputSize)
    .val(minRowId + 1)
    .on('change', function(evt) { self._onChangeMinRow(this, evt); })
    .appendTo(pagingControls);
  $('<span>').addClass("viewpanel-pagingcount").html(" - " + (maxRowId + 1) + " ").appendTo(pagingControls);

  var nextPage = $('<a href="javascript:{}">'+$.i18n('core-views/next')+' &rsaquo;</a>').appendTo(pagingControls);
  var lastPage = $('<a href="javascript:{}">'+$.i18n('core-views/last')+' &raquo;</a>').appendTo(pagingControls);
  if (theProject.rowModel.nextPageId) {
    nextPage.addClass("action").on('click',function(evt) { self._onClickNextPage(this, evt); });
    lastPage.addClass("action").on('click',function(evt) { self._onClickLastPage(this, evt); });
  } else {
    nextPage.addClass("inaction");
    lastPage.addClass("inaction");
  }

  $('<span>'+$.i18n('core-views/show')+': </span>').appendTo(pageSizeControls);

  var renderPageSize = function(index) {
    var pageSize = self._gridPagesSizes[index];
    var a = $('<a href="javascript:{}"></a>')
    .addClass("viewPanel-pagingControls-page")
    .appendTo(pageSizeControls);
    if (pageSize == self._pageSize) {
      a.text(pageSize).addClass("selected");
    } else {
      a.text(pageSize).addClass("action").on('click',function(evt) {
        self._pageSize = pageSize;
        self.update(undefined, true);
      });
    }
  };
  
  for (var i = 0; i < self._gridPagesSizes.length; i++) {
    renderPageSize(i);
  }
  
  $('<span>')
  .text(theProject.rowModel.mode == "record-based" ? ' '+$.i18n('core-views/records') : ' '+$.i18n('core-views/rows'))
  .appendTo(pageSizeControls);
};

DataTableView.prototype._checkPaginationSize = function(gridPageSize, defaultGridPageSize) {
  var self = this;
  var newGridPageSize = [];
  
  if(gridPageSize == null || typeof gridPageSize != "object") return defaultGridPageSize;

  for (var i = 0; i < gridPageSize.length; i++) {
    if(typeof gridPageSize[i] == "number" && gridPageSize[i] > 0 && gridPageSize[i] < 10000)
      newGridPageSize.push(gridPageSize[i]);
  }

  if(newGridPageSize.length < 2) return defaultGridPageSize;
  
  var distinctValueFilter = (value, index, selfArray) => (selfArray.indexOf(value) == index);
  newGridPageSize.filter(distinctValueFilter);
  
  newGridPageSize.sort((a, b) => (a - b));
  
  return newGridPageSize;
};

DataTableView.prototype._renderDataTables = function(table, tableHeader, colGroup) {
  var self = this;

  var columns = theProject.columnModel.columns;

  /*------------------------------------------------------------
   *  Column Headers with Menus
   *------------------------------------------------------------
   */

  colGroup.empty();
  self._renderTableHeader(tableHeader, colGroup);

  /*------------------------------------------------------------
   *  Data Cells
   *------------------------------------------------------------
   */

  var rows = theProject.rowModel.rows;
  var renderRow = function(tr, r, row, even) {
    $(tr).empty();
    var cells = row.cells;
    var tdStar = tr.insertCell(tr.cells.length);
    var star = document.createElement('a');
    star.href = "javascript:{}";
    star.classList.add(row.starred ? "data-table-star-on" : "data-table-star-off");
    tdStar.appendChild(star).appendChild(document.createTextNode('\u00A0')); // NBSP
    star.addEventListener('click', function() {
    var newStarred = !row.starred;
      Refine.postOperation(
        {
          op: "core/annotate-one-row",
          rowId: row.i,
          star: true,
          value: newStarred
        },
        {},
        {
          onDone: function(o) {
            row.starred = newStarred;
            star.classList.remove(newStarred ? "data-table-star-off" : "data-table-star-on");
            star.classList.add(newStarred ? "data-table-star-on" : "data-table-star-off");
          }
        }
      );
    });
    
    var tdFlag = tr.insertCell(tr.cells.length);
    var flag = document.createElement('a');
    flag.classList.add(row.flagged ? "data-table-flag-on" : "data-table-flag-off");
    flag.href = "javascript:{}";
    tdFlag.appendChild(flag).appendChild(document.createTextNode('\u00A0'));
    flag.addEventListener('click', function() {
      var newFlagged = !row.flagged;
      Refine.postOperation(
        {
          op: "core/annotate-one-row",
          rowId: row.i,
          star: false,
          value: newFlagged
        },
        {},
        {
          onDone: function(o) {
            row.flagged = newFlagged;
            flag.classList.remove(newFlagged ? "data-table-flag-off" : "data-table-flag-on");
            flag.classList.add(newFlagged ? "data-table-flag-on" : "data-table-flag-off");
          }
        }
      );
    });

    var tdIndex = tr.insertCell(tr.cells.length);
    if (theProject.rowModel.mode == "record-based") {
      if ("j" in row) {
        $(tr).addClass("record");
        var div = document.createElement('div');
        div.innerHTML = (row.j + 1) + '.';
        tdIndex.appendChild(div);
      } else {
        var div = document.createElement('div');
        div.innerHTML = '\u00A0';
        tdIndex.appendChild(div);
      }
    } else {
      var div = document.createElement('div');
      div.innerHTML = (row.i + 1) + '.';
      tdIndex.appendChild(div);
    }

    $(tr).addClass(even ? "even" : "odd");

    for (var i = 0; i < columns.length; i++) {
      var column = columns[i];
      var td = tr.insertCell(tr.cells.length);
      if (self._collapsedColumnNames.hasOwnProperty(column.name)) {
        td.innerHTML = "&nbsp;";
      } else {
        var cell = (i < cells.length) ? cells[i] : null;
        new DataTableCellUI(self, cell, row.i, i, td);
      }
    }
  };

  var even = true;
  for (var r = 0; r < rows.length; r++) {
    var row = rows[r];
    var tr = table.insertRow(table.rows.length);
    if (theProject.rowModel.mode == "row-based" || "j" in row) {
      even = !even;
    }
    renderRow(tr, r, row, even);
  }
};

// cache which remembers the set width of each column (used when the grid is re-rendered)
DataTableView.columnWidthCache = new Map();

DataTableView.prototype._renderTableHeader = function(tableHeader, colGroup) {
  var self = this;
  var columns = theProject.columnModel.columns;
  var trHead = document.createElement('tr');
  tableHeader.append(trHead);

  // header for the first three columns (star, flag, row number)
  DOM.bind(
      $(trHead.appendChild(document.createElement("th")))
      .attr("colspan", "3")
      .addClass("column-header")
      .html(
        '<div class="column-header-title">' +
          '<button class="column-header-menu" bind="dropdownMenu"></button><span class="column-header-name">'+$.i18n('core-views/all')+'</span>' +
        '</div>'
      )
  ).dropdownMenu.on('click',function() {
    self._createMenuForAllColumns(this);
  });
  $('<col>').attr('span', 3).appendTo(colGroup);

  // headers for the normal columns
  this._columnHeaderUIs = [];
  var createColumnHeader = function(column, index) {
    var th = trHead.appendChild(document.createElement("th"));
    $(th).addClass("column-header").attr('title', column.name);
    var col = $('<col>')
        .attr('span', 1)
        .data('name', column.name)
        .appendTo(colGroup);
    var cachedWidth = DataTableView.columnWidthCache.get(column.name);
    if (cachedWidth !== undefined && !self._collapsedColumnNames.hasOwnProperty(column.name)) {
      col.width(cachedWidth + 'em');
    } else {
      // Not set in CSS directly because the user needs to be able to override that by dragging 
      col.css('min-width', '5em');
    }
    if (self._collapsedColumnNames.hasOwnProperty(column.name)) {
      $(th).html("&nbsp;").on('mousedown',function(evt) {
        delete self._collapsedColumnNames[column.name];
        self.render();
      });
    } else {
      var columnHeaderUI = new DataTableColumnHeaderUI(self, column, index, th, col);
      self._columnHeaderUIs.push(columnHeaderUI);

      // add resizing controls
      var resizerLeft = $('<div></div>').addClass('column-header-resizer-left')
            .appendTo(th);
      resizerLeft.on('mousedown', function(e) {
        // only capture left clicks
        if (e.button !== 0) {
          return;
        }
        columnHeaderUI._startResizing(e);
      });

      // add resizing control for the previous column (if uncollapsed)
      if (index > 0 && !self._collapsedColumnNames.hasOwnProperty(columns[index-1].name)) {
        var resizerRight = $('<div></div>').addClass('column-header-resizer-right')
              .appendTo(th);
        resizerRight.on('mousedown', function(e) {
          // only capture left clicks
          if (e.button !== 0) {
            return;
          }
          self._columnHeaderUIs[index - 1]._startResizing(e);
        });
      }
    }
  };

  for (var i = 0; i < columns.length; i++) {
    createColumnHeader(columns[i], i);
  }
}

DataTableView.prototype._showRows = function(paginationOptions, onDone, onError) {
  var self = this;
  self._cancelAutoUpdate();
  self._lastRequestedPagination = paginationOptions;
  var fullViewOptions = deepCopy({
    paginationOptions,
    engine: ui.browsingEngine.getJSON(),
    sorting: self._sorting,
    pageSize: self._pageSize
  });

  Refine.fetchRows(paginationOptions, this._pageSize, function() {
    if (self._lastRenderedViewOptions !== undefined &&
        deeplyEquals(self._lastRenderedViewOptions, fullViewOptions) &&
        deeplyEquals(theProject.columnModel, self._lastRenderedColumnModel) &&
        deeplyEquals(theProject.rowModel.rows.map(r => r.i), self._lastRenderedRowModel.rows.map(r => r.i)) &&
        theProject.rowModel.historyEntryId === self._lastRenderedRowModel.historyEntryId) {
      // do an incremental update of the DOM, only re-rendering cells which
      // have changed
      self.rerenderChangedCells();
    } else {
      // do a full re-render
      self.render();
    }
    self._lastRenderedViewOptions = fullViewOptions;
    self._lastRenderedColumnModel = theProject.columnModel
    self._lastRenderedRowModel = theProject.rowModel;

    // schedule some follow-up update if the grid has incomplete cells or potentially missing rows
    if (theProject.rowModel.needsRefreshing) {
      self._cancelAutoUpdate();
      self._autoUpdateTimeout = setTimeout(function() {
        self._showRows(self._lastRequestedPagination);
      }, 2000);
    }

    if (onDone) {
      onDone();
    }
  }, this._sorting, onError);
};

DataTableView.prototype._cancelAutoUpdate = function(elmt, evt) {
  var self = this;
  if (self._autoUpdateTimeout !== undefined) {
    clearTimeout(self._autoUpdateTimeout);
    self._autoUpdateTimeout = undefined;
  }
};

DataTableView.prototype._onClickPreviousPage = function(elmt, evt) {
  this._showRows({end: theProject.rowModel.previousPageId});
};

DataTableView.prototype._onClickNextPage = function(elmt, evt) {
  this._showRows({start: theProject.rowModel.nextPageId});
};

DataTableView.prototype._onClickFirstPage = function(elmt, evt) {
  this._showRows({start: 0});
};

DataTableView.prototype._onClickLastPage = function(elmt, evt) {
  this._showRows({end: theProject.engineStats.totalRows});
};

DataTableView.prototype._onChangeMinRow = function(elmt, evt) {
  var input = $('#viewpanel-paging-current-min-row');
  var newMinRow = input.val();
  var totalRows = undefined;
  if (theProject.engineStats) {
    totalRows = theProject.engineStats.totalRows;
  }
  if (newMinRow <= 0) {
    newMinRow = 1;
    input.val(newMinRow);
  } else if (totalRows !== undefined && newMinRow > totalRows) {
    newMinRow = totalRows;
    input.val(totalRows);
  }
  this._showRows({start: newMinRow - 1});
};

DataTableView.prototype._getSortingCriteriaCount = function() {
  return this._sorting.criteria.length;
};

DataTableView.prototype._sortedByColumn = function(columnName) {
  for (var i = 0; i < this._sorting.criteria.length; i++) {
    if (this._sorting.criteria[i].column == columnName) {
      return true;
    }
  }
  return false;
};

DataTableView.prototype._getSortingCriterionForColumn = function(columnName) {
  for (var i = 0; i < this._sorting.criteria.length; i++) {
    if (this._sorting.criteria[i].column == columnName) {
      return this._sorting.criteria[i];
    }
  }
  return null;
};

DataTableView.prototype._removeSortingCriterionOfColumn = function(columnName) {
  for (var i = 0; i < this._sorting.criteria.length; i++) {
    if (this._sorting.criteria[i].column == columnName) {
      this._sorting.criteria.splice(i, 1);
      break;
    }
  }
  this.update();
};

DataTableView.prototype._addSortingCriterion = function(criterion, alone) {
  if (alone) {
    this._sorting.criteria = [];
  } else {
    for (var i = 0; i < this._sorting.criteria.length; i++) {
      if (this._sorting.criteria[i].column == criterion.column) {
        this._sorting.criteria[i] = criterion;
        this.update();
        return;
      }
    }
  }
  this._sorting.criteria.push(criterion);
  this.update();
};

/** below can be move to seperate file **/
  var doTextTransformPrompt = function() {
    var frame = $(
        DOM.loadHTML("core", "scripts/views/data-table/text-transform-dialog.html")
        .replace("$EXPRESSION_PREVIEW_WIDGET$", ExpressionPreviewDialog.generateWidgetHtml()));

    var elmts = DOM.bind(frame);
    elmts.dialogHeader.text($.i18n('core-views/transform/header'));
    elmts.or_views_errorOn.text($.i18n('core-views/on-error'));
    elmts.or_views_keepOr.text($.i18n('core-views/keep-or'));
    elmts.or_views_setBlank.text($.i18n('core-views/set-blank'));
    elmts.or_views_storeErr.text($.i18n('core-views/store-err'));
    elmts.or_views_reTrans.text($.i18n('core-views/re-trans'));
    elmts.or_views_timesChang.text($.i18n('core-views/times-chang'));
    elmts.okButton.html($.i18n('core-buttons/ok'));
    elmts.cancelButton.text($.i18n('core-buttons/cancel'));    

    var level = DialogSystem.showDialog(frame);
    var dismiss = function() { DialogSystem.dismissUntil(level - 1); };

    elmts.cancelButton.on('click',dismiss);
    elmts.okButton.on('click',function() {
        new ExpressionColumnDialog(
                previewWidget.getExpression(true),
                $('input[name="text-transform-dialog-onerror-choice"]:checked')[0].value,
                elmts.repeatCheckbox[0].checked,
                elmts.repeatCountInput[0].value
        );
    });
    
    var previewWidget = new ExpressionPreviewDialog.Widget(
      elmts,
      -1,
      [],
      [],
      null
    );
    previewWidget._prepareUpdate = function(params) {
      params.repeat = elmts.repeatCheckbox[0].checked;
      params.repeatCount = elmts.repeatCountInput[0].value;
    };
  };
  /** above can be move to seperate file **/
  
DataTableView.prototype._createMenuForAllColumns = function(elmt) {
  var self = this;
  var menu = [
        {
            label: $.i18n('core-views/transform'),
            id: "core/facets",
            width: "200px",
            click: function() {
                   doTextTransformPrompt();
            }
        },
    {},
    {
      id: "core/common-transforms",
      label: $.i18n('core-views/edit-all-columns'),
      submenu: [
        {
          id: "core/trim-whitespace",
          label: $.i18n('core-views/trim-all'),
          click: function() { new commonTransformDialog("value.trim()", "core-views/trim-all/header"); }
        },
        {
          id: "core/collapse-whitespace",
          label: $.i18n('core-views/collapse-white'),
          click: function() { new commonTransformDialog("value.replace(/\\s+/,' ')", "core-views/collapse-white/header"); }
        },
        {},
        {
          id: "core/unescape-html-entities",
          label: $.i18n('core-views/unescape-html'),
          click: function() { new commonTransformDialog("value.unescape('html')","core-views/unescape-html/header" ); }
        },
        {
          id: "core/replace-smartquotes",
          label: $.i18n('core-views/replace-smartquotes'),
          click: function() { new commonTransformDialog("value.replace(/[\u2018\u2019\u201A\u201B\u2039\u203A\u201A]/,\"\\\'\").replace(/[\u201C\u201D\u00AB\u00BB\u201E]/,\"\\\"\")", "core-views/replace-smartquotes/header"); }
        },
        {},
        {
          id: "core/to-titlecase",
          label: $.i18n('core-views/titlecase'),
          click: function() { new commonTransformDialog("value.toTitlecase()", "core-views/titlecase/header"); }
        },
        {
          id: "core/to-uppercase",
          label: $.i18n('core-views/uppercase'),
          click: function() { new commonTransformDialog("value.toUppercase()","core-views/uppercase/header" ); }
        },
        {
          id: "core/to-lowercase",
          label: $.i18n('core-views/lowercase'),
          click: function() { new commonTransformDialog("value.toLowercase()", "core-views/lowercase/header"); }
        },
        {},
        {
          id: "core/to-number",
          label: $.i18n('core-views/to-number'),
          click: function() { new commonTransformDialog("value.toNumber()","core-views/to-number/header" ); }
        },
        {
          id: "core/to-date",
          label: $.i18n('core-views/to-date'),
          click: function() { new commonTransformDialog("value.toDate()","core-views/to-date/header" ); }
        },
        {
          id: "core/to-text",
          label: $.i18n('core-views/to-text'),
          click: function() { new commonTransformDialog("value.toString()","core-views/to-text/header" ); }
        },
        {},
        {
          id: "core/to-blank",
          label: $.i18n('core-views/blank-out'),
          click: function() { new commonTransformDialog("null", "core-views/blank-out/header"); }
        },
        {
          id: "core/to-empty",
          label: $.i18n('core-views/blank-out-empty'),
          click: function() { new commonTransformDialog("\"\"","core-views/blank-out-empty/header" ); }
        }
      ]
    },
    {
      label: $.i18n('core-views/facet'),
      id: "core/facets",
      width: "200px",
      submenu: [
        {
          label: $.i18n('core-views/facet-star'),
          id: "core/facet-by-star",
          click: function() {
            ui.browsingEngine.addFacet(
              "list", 
              {
                "name" : $.i18n('core-views/starred-rows'),
                "columnName" : "", 
                "expression" : "row.starred"
              },
              {
                "scroll" : false
              }
            );
          }
        },
        {
          label: $.i18n('core-views/facet-flag'),
          id: "core/facet-by-flag",
          click: function() {
            ui.browsingEngine.addFacet(
              "list", 
              {
                "name" : $.i18n('core-views/flagged-rows'),
                "columnName" : "", 
                "expression" : "row.flagged"
              },
              {
                "scroll" : false
              }
            );
          }
        },
        {
          label: $.i18n('core-views/facet-blank'),
          id: "core/facet-by-blank",
          click: function() {
            ui.browsingEngine.addFacet(
              "list", 
              {
                "name" : $.i18n('core-views/blank-rows'),
                "columnName" : "", 
                "expression" : "(filter(row.columnNames,cn,isNonBlank(cells[cn].value)).length()==0).toString()"
              },
              {
                "scroll" : false
              }
            );
          }
        },
        {
          label: $.i18n('core-views/blank-values'),
          id: "core/blank-values",
          click: function() {
            ui.browsingEngine.addFacet(
                "list",
                {
                  "name" : $.i18n('core-views/blank-values'),
                  "columnName" : "",
                  "expression" : "filter(row.columnNames,cn,isBlank(cells[cn].value))"
                },
                {
                  "scroll" : false
                }
            );
          }
        },
        {
          label: $.i18n('core-views/blank-records'),
          id: "core/blank-records",
          click: function() {
            ui.browsingEngine.addFacet(
                "list",
                {
                  "name" : $.i18n('core-views/blank-records'),
                  "columnName" : "",
                  "expression" : "filter(row.columnNames,cn,isBlank(if(row.record.fromRowIndex==row.index,row.record.cells[cn].value.join(\"\"),true)))"
                },
                {
                  "scroll" : false
                }
            );
          }
        },
        {
          label: $.i18n('core-views/non-blank-values'),
          id: "core/non-blank-values",
          click: function() {
            ui.browsingEngine.addFacet(
              "list", 
              {
                "name" : $.i18n('core-views/non-blank-values'),
                "columnName" : "", 
                "expression" : "filter(row.columnNames,cn,isNonBlank(cells[cn].value))"
              },
              {
                "scroll" : false
              }
            );
          }
        },
        {
          label: $.i18n('core-views/non-blank-records'),
          id: "core/non-blank-records",
          click: function() {
            ui.browsingEngine.addFacet(
              "list", 
              {
                "name" : $.i18n('core-views/non-blank-records'),
                "columnName" : "", 
                "expression" : "filter(row.columnNames,cn,isNonBlank(if(row.record.fromRowIndex==row.index,row.record.cells[cn].value.join(\"\"),null)))"
              },
              {
                "scroll" : false
              }
            );
          }
        }
      ]
    },
    {},
    {
      label: $.i18n('core-views/edit-rows'),
      id: "core/edit-rows",
      width: "200px",
      submenu: [
        {
          label: $.i18n('core-views/star-rows'),
          id: "core/star-rows",
          click: function() {
            Refine.postOperation(
                {
                  op: "core/row-star",
                  "starred" : "true"
                },
                { rowMetadataChanged: true });
          }
        },
        {
          label: $.i18n('core-views/unstar-rows'),
          id: "core/unstar-rows",
          click: function() {
            Refine.postOperation(
                {
                  op: "core/row-star",
                  "starred" : "false"
                },
                { rowMetadataChanged: true });
          }
        },
        {},
        {
          label: $.i18n('core-views/flag-rows'),
          id: "core/flag-rows",
          click: function() {
            Refine.postOperation(
                {
                  op: "core/row-flag",
                  "flagged" : "true"
                },
                { rowMetadataChanged: true });
          }
        },
        {
          label: $.i18n('core-views/unflag-rows'),
          id: "core/unflag-rows",
          click: function() {
            Refine.postOperation(
                {
                  op: "core/row-flag",
                  "flagged" : "false"
                },
                { rowMetadataChanged: true });
          }
        },
        {},
        {
          label: $.i18n('core-views/remove-matching'),
          id: "core/remove-rows",
          click: function() {
            Refine.postOperation({op: "core/row-removal" }, { rowMetadataChanged: true });
          }
        }
      ]
    },
    {
      label: $.i18n('core-views/edit-col'),
      id: "core/edit-columns",
      width: "200px",
      submenu: [
        {
          label: $.i18n('core-views/reorder-remove'),
          id: "core/reorder-columns",
          click: function() {
            new ColumnReorderingDialog();
          }
        },
        {},
        {
          label: $.i18n('core-views/fill-down'),
          id: "core/fill-down",
          click: function () {
            if (self._getSortingCriteriaCount() > 0) {
                self._createPendingSortWarningDialog(doAllFillDown);
            }
            else {
                doAllFillDown();
            }
          }
        },
        {
          label: $.i18n('core-views/blank-down'),
          id: "core/blank-down",
          click: function () {
            if (self._getSortingCriteriaCount() > 0) {
                self._createPendingSortWarningDialog(doAllBlankDown);
            }
            else {
                doAllBlankDown();
            }
          }
        }
      ]
    },
    {},
    {
      label: $.i18n('core-views/view'),
      id: "core/view",
      width: "200px",
      submenu: [
        {
          label: $.i18n('core-views/collapse-all'),
          id: "core/collapse-all-columns",
          click: function() {
            for (var i = 0; i < theProject.columnModel.columns.length; i++) {
              self._collapsedColumnNames[theProject.columnModel.columns[i].name] = true;
            }
            self.render();
          }
        },
        {
          label: $.i18n('core-views/expand-all'),
          id: "core/expand-all-columns",
          click: function() {
            self._collapsedColumnNames = [];
            self.render();
          }
        },
        {
          label: $.i18n('core-views/display-null'),
          id: "core/display-null",
          click: function() {
            $(".data-table-null").toggle();
            self._shownulls = !(self._shownulls);
          }
        }
      ]
    }
  ];

  for (var i = 0; i < DataTableView._extenders.length; i++) {
    DataTableView._extenders[i].call(null, this, menu);
  }

  MenuSystem.createAndShowStandardMenu(menu, elmt, { width: "120px", horizontal: false });
};

DataTableView.prototype._createSortingMenu = function(elmt) {
  var self = this;
  var items = [
    {
      "label" : $.i18n('core-views/remove-sort'),
      "click" : function() {
        self._sorting.criteria = [];
        self.update();
      }
    },
    {
      "label" : $.i18n('core-views/reorder-perma'),
      "click" : function() {
        Refine.postOperation(
          {
            op: "core/row-reorder",
            "sorting" : self._sorting,
            "mode" : ui.browsingEngine.getMode()
          },
          { rowMetadataChanged: true },
          {
            onDone: function() {
              self._sorting.criteria = [];
            }
          }
        );
      }
    },
    {}
  ];

  var getColumnHeaderUI = function(columnName) {
    for (var i = 0; i < self._columnHeaderUIs.length; i++) {
      var columnHeaderUI = self._columnHeaderUIs[i];
      if (columnHeaderUI.getColumn().name == columnName) {
        return columnHeaderUI;
      }
    }
    return null;
  };
  var createSubmenu = function(criterion) {
    var columnHeaderUI = getColumnHeaderUI(criterion.column);
    if (columnHeaderUI !== null) {
      items.push({
        "label" : $.i18n('core-views/by')+" " + criterion.column,
        "submenu" : columnHeaderUI.createSortingMenu()
      });
    }
  };
  for (var i = 0; i < this._sorting.criteria.length; i++) {
    createSubmenu(this._sorting.criteria[i]);
  }

  MenuSystem.createAndShowStandardMenu(items, elmt, { horizontal: false });
};

var doAllFillDown = function(onDone) {
  var operations = [];
  for(var i = theProject.columnModel.columns.length - 1; i >= 0; i--) {
    operations.push({
      op: "core/fill-down",
      columnName: theProject.columnModel.columns[i].name
    });
  }
  Refine.postOperations(
      operations,
      { modelsChanged: true},
      { onDone: onDone }
  );
};

var doAllBlankDown = function(onDone) {
  var operations = [];
  for(var i = 0; i < theProject.columnModel.columns.length; i++) {
    operations.push({
      op: "core/blank-down",
      columnName: theProject.columnModel.columns[i].name
    });
  }
  Refine.postOperations(
      operations,
      { modelsChanged: true },
      { onDone: onDone }
  );
};

DataTableView.prototype._updateCell = function(rowIndex, cellIndex, cell) {
  var rows = theProject.rowModel.rows;
  for (var r = 0; r < rows.length; r++) {
    var row = rows[r];
    if (row.i === rowIndex) {
      while (cellIndex >= row.cells.length) {
        row.cells.push(null);
      }
      row.cells[cellIndex] = cell;
      break;
    }
  }
};

DataTableView.sampleVisibleRows = function() {
  return {
     sortingConfig: {}, // add sorting config here if we want it to be respected by previews (more expensive)
     engineConfig: ui.browsingEngine.getJSON(),
     limit: 10
  };
};

DataTableView.promptExpressionOnVisibleRows = function(column, title, expression, onDone) {
  var o = DataTableView.sampleVisibleRows();

  var cellIndex = Refine.columnNameToColumnIndex(column.name);

  var self = this;
  new ExpressionPreviewDialog(
    title,
    cellIndex, 
    o.engineConfig,
    o.sortingConfig,
    o.limit,
    expression,
    onDone
  );
};

//This function takes a function as a parameter and creates a dialog window
//If the ok button is pressed, the function is executed
//If the cancel button is pressed instead, the window isdismissed and the function is not executed
DataTableView.prototype._createPendingSortWarningDialog = function(func) {
  var frame = $(DOM.loadHTML("core", "scripts/views/data-table/warn-of-pending-sort.html"));
  var elmts = DOM.bind(frame);

  elmts.or_views_warning.text($.i18n('core-views/warn-of-pending-sort'));
  elmts.dialogHeader.text($.i18n('core-views/warning'));
  elmts.okButton.html($.i18n('core-buttons/ok'));
  elmts.cancelButton.text($.i18n('core-buttons/cancel'));

  var level = DialogSystem.showDialog(frame);
  var dismiss = function() { DialogSystem.dismissLevel(level - 1); };

  elmts.cancelButton.on('click', function () {
     dismiss();
  });

  elmts.okButton.on('click', function () {
     func(dismiss);
  });

};
