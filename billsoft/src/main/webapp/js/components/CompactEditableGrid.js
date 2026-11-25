/**
 * CompactEditableGrid.js — Shared Excel-like Compact Spreadsheet Component
 *
 * Designed for high-density bulk operations:
 * - 28px compact row height with continuous 1px collapse borders
 * - Frameless edge-to-edge cell editors (no inset Bootstrap boxes)
 * - Complete spreadsheet keyboard navigation (Arrow keys, Tab, Enter, Esc, F2, Delete)
 * - Multi-row/multi-column Excel & Google Sheets copy/paste with auto-expansion & header intelligence
 * - Active cell highlight, modified cell tracking, and real-time inline validation indicators
 * - Support for advanced column toggles (e.g. SKU and Barcode hidden by default)
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define(['exports', 'react'], factory);
  } else if (typeof exports === 'object' && typeof exports.nodeName !== 'string') {
    factory(exports, require('react'));
  } else {
    var exp = {};
    factory(exp, root.React);
    root.CompactEditableGrid = exp.CompactEditableGrid;
    root.parseSpreadsheetClipboard = exp.parseSpreadsheetClipboard;
  }
})(typeof self !== 'undefined' ? self : this, function (exports, React) {
  'use strict';

  var useState = React.useState;
  var useEffect = React.useEffect;
  var useRef = React.useRef;
  var useMemo = React.useMemo;
  var useCallback = React.useCallback;

  // ═══════════════════════════════════════════════════════════════════
  // CLIPBOARD ENGINE: Robust TSV / CSV Parser
  // ═══════════════════════════════════════════════════════════════════
  function parseSpreadsheetClipboard(rawText) {
    if (!rawText || typeof rawText !== 'string' || !rawText.trim()) return [];
    var trimmed = rawText.trim();

    // Check if TSV (Excel / Google Sheets default format)
    if (trimmed.indexOf('\t') !== -1) {
      var lines = trimmed.split(/\r?\n/);
      var tsvRows = [];
      for (var l = 0; l < lines.length; l++) {
        var line = lines[l];
        if (!line && l === lines.length - 1) continue;
        var cols = line.split('\t').map(function (c) {
          return c.trim().replace(/^["']|["']$/g, '');
        });
        tsvRows.push(cols);
      }
      return tsvRows;
    }

    // Standard CSV parser handling quotes, escaped quotes (""), and embedded commas
    var rows = [];
    var curLine = [];
    var curVal = '';
    var inQuotes = false;
    var chars = trimmed.split('');

    for (var i = 0; i < chars.length; i++) {
      var c = chars[i];
      if (inQuotes) {
        if (c === '"') {
          if (i + 1 < chars.length && chars[i + 1] === '"') {
            curVal += '"';
            i++;
          } else {
            inQuotes = false;
          }
        } else {
          curVal += c;
        }
      } else {
        if (c === '"') {
          inQuotes = true;
        } else if (c === ',') {
          curLine.push(curVal.trim());
          curVal = '';
        } else if (c === '\r') {
          if (i + 1 < chars.length && chars[i + 1] === '\n') i++;
          curLine.push(curVal.trim());
          curVal = '';
          if (curLine.length > 0 && curLine.some(function (x) { return Boolean(x); })) {
            rows.push(curLine);
          }
          curLine = [];
        } else if (c === '\n') {
          curLine.push(curVal.trim());
          curVal = '';
          if (curLine.length > 0 && curLine.some(function (x) { return Boolean(x); })) {
            rows.push(curLine);
          }
          curLine = [];
        } else {
          curVal += c;
        }
      }
    }
    if (curVal.length > 0 || curLine.length > 0) {
      curLine.push(curVal.trim());
      if (curLine.length > 0 && curLine.some(function (x) { return Boolean(x); })) {
        rows.push(curLine);
      }
    }
    return rows;
  }

  // Header detection aliases mapping normalized string to column keys
  var HEADER_ALIASES = {
    // Name
    'name': 'name', 'productname': 'name', 'itemname': 'name', 'product': 'name', 'item': 'name', 'title': 'name',
    // Category
    'category': 'category', 'cat': 'category', 'group': 'category', 'productcategory': 'category',
    // Unit
    'unit': 'unit', 'uom': 'unit', 'unitofmeasure': 'unit',
    // Selling Price
    'price': 'price', 'sellingprice': 'price', 'rate': 'price', 'baseprice': 'price', 'unitprice': 'price', 'mrp': 'price',
    // Cost Price
    'cost': 'costPrice', 'costprice': 'costPrice', 'purchaseprice': 'costPrice', 'buyprice': 'costPrice',
    // Stock / Opening / Physical
    'stock': 'stock', 'initialstock': 'initialStock', 'openingstock': 'initialStock', 'stockquantity': 'stockQuantity',
    'qty': 'stock', 'quantity': 'stock', 'physicalstock': 'physicalStockCount', 'physicalcount': 'physicalStockCount',
    'actualstock': 'physicalStockCount', 'physical': 'physicalStockCount', 'reconciledstock': 'physicalStockCount',
    // Min Stock
    'min': 'minStockLevel', 'minstock': 'minStockLevel', 'minstocklevel': 'minStockLevel', 'minimumstock': 'minStockLevel', 'alertlevel': 'minStockLevel',
    // HSN / SAC
    'hsn': 'hsnCode', 'sac': 'hsnCode', 'hsncode': 'hsnCode', 'saccode': 'hsnCode', 'hsnsac': 'hsnCode', 'hsnsaccode': 'hsnCode',
    // SKU (Advanced)
    'sku': 'sku', 'skucode': 'sku', 'itemcode': 'sku',
    // Barcode (Advanced)
    'barcode': 'barcode', 'barcodenumber': 'barcode', 'upc': 'barcode', 'ean': 'barcode',
    // GST
    'gst': 'gstPercentage', 'gstpercentage': 'gstPercentage', 'tax': 'gstPercentage', 'taxrate': 'gstPercentage',
    // Item Type
    'type': 'itemType', 'itemtype': 'itemType',
    // Description
    'desc': 'description', 'description': 'description', 'note': 'description', 'notes': 'description'
  };

  // ═══════════════════════════════════════════════════════════════════
  // COMPACT EDITABLE GRID COMPONENT
  // ═══════════════════════════════════════════════════════════════════
  function CompactEditableGrid(props) {
    var columns = props.columns || [];
    var rows = props.rows || [];
    var onChangeRow = props.onChangeRow;
    var onDeleteRow = props.onDeleteRow;
    var onAddRow = props.onAddRow;
    var canAddRows = Boolean(props.canAddRows);
    var canDeleteRows = Boolean(props.canDeleteRows);
    var showAdvanced = Boolean(props.showAdvanced);
    var onToggleAdvanced = props.onToggleAdvanced;
    var modifiedTracker = props.modifiedTracker; // (row, colKey) => boolean
    var externalErrors = props.errors || {}; // { [rowIdOrIdx]: { [colKey]: 'error' } }
    var maxHeight = props.maxHeight || '460px';
    var emptyMessage = props.emptyMessage || 'No records in grid.';
    var gridId = props.gridId || 'compact-grid-' + Math.random().toString(36).substr(2, 6);

    var containerRef = useRef(null);
    var activeInputRef = useRef(null);

    // Active cell coordinate: { rowIdx: number, colKey: string }
    var _active = useState({ rowIdx: 0, colKey: null });
    var activeCell = _active[0];
    var setActiveCell = _active[1];

    // Filter visible columns according to showAdvanced flag
    var visibleColumns = useMemo(function () {
      return columns.filter(function (col) {
        if (col.isAdvanced && !showAdvanced) return false;
        return true;
      });
    }, [columns, showAdvanced]);

    // Visible editable columns list for horizontal key navigation
    var editableColumns = useMemo(function () {
      return visibleColumns.filter(function (col) { return !col.readOnly; });
    }, [visibleColumns]);

    // Initial active column key setup
    useEffect(function () {
      if (!activeCell.colKey && editableColumns.length > 0) {
        setActiveCell({ rowIdx: 0, colKey: editableColumns[0].key });
      }
    }, [editableColumns, activeCell.colKey]);

    // Focus active cell input whenever activeCell changes
    var focusCell = useCallback(function (rowIdx, colKey) {
      setActiveCell({ rowIdx: rowIdx, colKey: colKey });
      setTimeout(function () {
        if (!containerRef.current) return;
        var selector = 'input[data-row="' + rowIdx + '"][data-col="' + colKey + '"], select[data-row="' + rowIdx + '"][data-col="' + colKey + '"], textarea[data-row="' + rowIdx + '"][data-col="' + colKey + '"]';
        var el = containerRef.current.querySelector(selector);
        if (el) {
          el.focus();
          if (el.select) el.select();
        }
      }, 10);
    }, []);

    // ── KEYBOARD NAVIGATION HANDLER ──
    var handleCellKeyDown = function (e, rowIdx, colKey) {
      // Allow global modifiers like Ctrl+Enter / Cmd+Enter to bubble for modal saving
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
        return;
      }

      var colIndexInEditable = -1;
      for (var i = 0; i < editableColumns.length; i++) {
        if (editableColumns[i].key === colKey) {
          colIndexInEditable = i;
          break;
        }
      }

      var isInput = e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA';
      var textLen = isInput ? (e.target.value || '').length : 0;
      var selStart = isInput ? e.target.selectionStart : 0;
      var selEnd = isInput ? e.target.selectionEnd : 0;

      if (e.key === 'Escape') {
        e.preventDefault();
        e.target.blur();
        return;
      }

      if (e.key === 'F2') {
        e.preventDefault();
        if (isInput && e.target.select) e.target.select();
        return;
      }

      // Enter: Commit cell and navigate DOWN
      if (e.key === 'Enter') {
        e.preventDefault();
        if (rowIdx < rows.length - 1) {
          focusCell(rowIdx + 1, colKey);
        } else if (canAddRows && onAddRow) {
          onAddRow(1);
          setTimeout(function () {
            focusCell(rowIdx + 1, editableColumns[0] ? editableColumns[0].key : colKey);
          }, 30);
        }
        return;
      }

      // Tab / Shift+Tab: Move horizontally to next/previous editable column
      if (e.key === 'Tab') {
        e.preventDefault();
        if (e.shiftKey) {
          // Backward
          if (colIndexInEditable > 0) {
            focusCell(rowIdx, editableColumns[colIndexInEditable - 1].key);
          } else if (rowIdx > 0) {
            focusCell(rowIdx - 1, editableColumns[editableColumns.length - 1].key);
          }
        } else {
          // Forward
          if (colIndexInEditable < editableColumns.length - 1) {
            focusCell(rowIdx, editableColumns[colIndexInEditable + 1].key);
          } else if (rowIdx < rows.length - 1) {
            focusCell(rowIdx + 1, editableColumns[0].key);
          } else if (canAddRows && onAddRow) {
            onAddRow(1);
            setTimeout(function () {
              focusCell(rowIdx + 1, editableColumns[0].key);
            }, 30);
          }
        }
        return;
      }

      // Arrow Up
      if (e.key === 'ArrowUp') {
        if (rowIdx > 0) {
          e.preventDefault();
          focusCell(rowIdx - 1, colKey);
        }
        return;
      }

      // Arrow Down
      if (e.key === 'ArrowDown') {
        if (rowIdx < rows.length - 1) {
          e.preventDefault();
          focusCell(rowIdx + 1, colKey);
        } else if (canAddRows && onAddRow) {
          e.preventDefault();
          onAddRow(1);
          setTimeout(function () {
            focusCell(rowIdx + 1, colKey);
          }, 30);
        }
        return;
      }

      // Arrow Left: Move left when at start of input
      if (e.key === 'ArrowLeft') {
        if (!isInput || (selStart === 0 && selEnd === 0)) {
          if (colIndexInEditable > 0) {
            e.preventDefault();
            focusCell(rowIdx, editableColumns[colIndexInEditable - 1].key);
          } else if (rowIdx > 0) {
            e.preventDefault();
            focusCell(rowIdx - 1, editableColumns[editableColumns.length - 1].key);
          }
        }
        return;
      }

      // Arrow Right: Move right when at end of input
      if (e.key === 'ArrowRight') {
        if (!isInput || (selStart === textLen && selEnd === textLen)) {
          if (colIndexInEditable < editableColumns.length - 1) {
            e.preventDefault();
            focusCell(rowIdx, editableColumns[colIndexInEditable + 1].key);
          } else if (rowIdx < rows.length - 1) {
            e.preventDefault();
            focusCell(rowIdx + 1, editableColumns[0].key);
          }
        }
        return;
      }
    };

    // ── CLIPBOARD ENGINE: MULTI-ROW / MULTI-COLUMN SPREADSHEET PASTE ──
    var handleCellPaste = function (e, startRowIdx, startColKey) {
      var clipboardData = e.clipboardData || window.clipboardData;
      if (!clipboardData) return;
      var raw = clipboardData.getData('text/plain');
      if (!raw || !raw.trim()) return;

      var parsedRows = parseSpreadsheetClipboard(raw);
      if (parsedRows.length === 0) return;

      e.preventDefault();

      // Check for Header Detection on first row
      var firstLine = parsedRows[0];
      var normHeaders = firstLine.map(function (h) {
        return (h || '').toLowerCase().replace(/[^a-z0-9]/g, '');
      });

      var isHeader = normHeaders.some(function (n) {
        return Boolean(HEADER_ALIASES[n]);
      });

      var targetDataLines = parsedRows;
      var colKeyMap = {}; // index in line -> column key

      if (isHeader) {
        targetDataLines = parsedRows.slice(1);
        normHeaders.forEach(function (h, idx) {
          var mappedKey = HEADER_ALIASES[h];
          if (mappedKey) {
            // Check if mappedKey exists in columns
            var colExists = columns.some(function (c) { return c.key === mappedKey; });
            if (colExists && colKeyMap[idx] === undefined) {
              colKeyMap[idx] = mappedKey;
            }
          }
        });
      }

      // If no valid headers detected, map columns sequentially starting from active column
      if (Object.keys(colKeyMap).length === 0) {
        var startColIdx = 0;
        for (var c = 0; c < editableColumns.length; c++) {
          if (editableColumns[c].key === startColKey) {
            startColIdx = c;
            break;
          }
        }
        for (var ci = 0; ci < firstLine.length; ci++) {
          var targetCol = editableColumns[startColIdx + ci];
          if (targetCol) {
            colKeyMap[ci] = targetCol.key;
          }
        }
      }

      // Ensure grid has enough rows to hold incoming pasted data
      var neededRows = startRowIdx + targetDataLines.length;
      if (neededRows > rows.length && canAddRows && onAddRow) {
        var rowsToAdd = neededRows - rows.length;
        onAddRow(rowsToAdd);
      }

      // Apply values row by row
      for (var r = 0; r < targetDataLines.length; r++) {
        var lineValues = targetDataLines[r];
        var targetRowIdx = startRowIdx + r;
        if (targetRowIdx >= rows.length && !canAddRows) break;

        for (var cIdx = 0; cIdx < lineValues.length; cIdx++) {
          var colKey = colKeyMap[cIdx];
          if (colKey && onChangeRow) {
            var rawVal = lineValues[cIdx];
            // Find column definition to apply custom parse if specified
            var colDef = columns.find(function (cd) { return cd.key === colKey; });
            var parsedVal = (colDef && typeof colDef.parse === 'function')
              ? colDef.parse(rawVal)
              : rawVal;
            onChangeRow(targetRowIdx, colKey, parsedVal);
          }
        }
      }

      if (typeof window.showToast === 'function') {
        window.showToast('Pasted ' + targetDataLines.length + ' rows from spreadsheet clipboard', 'info');
      }
    };

    return React.createElement('div', {
      className: 'compact-grid-wrapper',
      style: { display: 'flex', flexDirection: 'column', width: '100%' }
    },
      // Optional Grid Controls Header (Show Advanced Columns toggle + Row counts)
      React.createElement('div', {
        className: 'compact-grid-toolbar',
        style: {
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          padding: '6px 12px', background: 'var(--bg-card-header, var(--bg-card))',
          borderBottom: '1px solid var(--border)', fontSize: '0.78rem'
        }
      },
        React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 12 } },
          React.createElement('span', { style: { fontWeight: 600, color: 'var(--text-secondary)' } },
            rows.length + ' rows total'
          ),
          onToggleAdvanced && React.createElement('label', {
            style: { display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer', userSelect: 'none', margin: 0, fontWeight: 500 }
          },
            React.createElement('input', {
              type: 'checkbox',
              checked: showAdvanced,
              onChange: onToggleAdvanced,
              style: { cursor: 'pointer' }
            }),
            'Show Advanced Columns (SKU, Barcode)'
          )
        ),
        React.createElement('div', { style: { color: 'var(--text-muted)', fontSize: '0.74rem' } },
          '💡 Tip: Arrow keys navigate cells. Enter moves down. Paste (Ctrl+V / ⌘V) supports Excel / Sheets.'
        )
      ),

      // Scrollable Spreadsheet Container
      React.createElement('div', {
        ref: containerRef,
        className: 'compact-grid-container',
        tabIndex: 0,
        style: {
          overflowX: 'auto',
          overflowY: 'auto',
          maxHeight: maxHeight,
          background: 'var(--bg-card)',
          position: 'relative'
        }
      },
        React.createElement('table', {
          id: gridId,
          className: 'compact-grid-table',
          style: {
            width: '100%',
            borderCollapse: 'collapse',
            tableLayout: 'fixed',
            fontSize: '0.8rem'
          }
        },
          // Header
          React.createElement('thead', {
            style: { position: 'sticky', top: 0, zIndex: 10, background: 'var(--bg-card-header, #1e293b)' }
          },
            React.createElement('tr', null,
              canDeleteRows && React.createElement('th', {
                style: { width: 36, textAlign: 'center', padding: '4px 2px', border: '1px solid var(--border)', background: 'inherit' }
              }, '#'),
              visibleColumns.map(function (col) {
                var thStyle = {
                  padding: '5px 8px',
                  border: '1px solid var(--border)',
                  background: 'inherit',
                  color: 'var(--text-secondary)',
                  fontWeight: 700,
                  fontSize: '0.74rem',
                  textTransform: 'uppercase',
                  letterSpacing: '0.03em',
                  textAlign: col.align || (col.type === 'number' ? 'right' : 'left'),
                  width: col.width || (col.minWidth ? col.minWidth : 'auto'),
                  minWidth: col.minWidth || 80,
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis'
                };
                return React.createElement('th', { key: col.key, style: thStyle },
                  col.label + (col.required ? ' *' : '')
                );
              }),
              canDeleteRows && React.createElement('th', {
                style: { width: 36, textAlign: 'center', padding: '4px 2px', border: '1px solid var(--border)', background: 'inherit' }
              }, '')
            )
          ),

          // Body
          React.createElement('tbody', null,
            rows.length === 0 ? React.createElement('tr', null,
              React.createElement('td', {
                colSpan: visibleColumns.length + (canDeleteRows ? 2 : 0),
                style: { textAlign: 'center', padding: '24px 12px', color: 'var(--text-secondary)' }
              }, emptyMessage)
            ) : rows.map(function (row, rowIdx) {
              var rowId = row._id || row.id || rowIdx;
              var rowErrors = externalErrors[rowId] || externalErrors[rowIdx] || {};

              return React.createElement('tr', {
                key: rowId,
                style: { height: 38, minHeight: 38 }
              },
                // Row index
                canDeleteRows && React.createElement('td', {
                  style: {
                    textAlign: 'center',
                    border: '1px solid var(--border)',
                    color: 'var(--text-muted)',
                    fontSize: '0.72rem',
                    fontWeight: 600,
                    background: 'var(--bg-secondary, rgba(0,0,0,0.02))',
                    userSelect: 'none'
                  }
                }, rowIdx + 1),

                // Data Cells
                visibleColumns.map(function (col) {
                  var cellVal = row[col.key] !== undefined ? row[col.key] : '';
                  var isReadOnly = typeof col.readOnly === 'function' ? col.readOnly(row) : Boolean(col.readOnly);
                  var isModified = modifiedTracker ? Boolean(modifiedTracker(row, col.key)) : false;
                  var cellError = rowErrors[col.key] || (col.validate ? col.validate(cellVal, row) : null);
                  var isCellActive = activeCell.rowIdx === rowIdx && activeCell.colKey === col.key;
                  var isWrapCol = Boolean(col.multiline || col.key === 'name');

                  var tdStyle = {
                    padding: 0,
                    height: 38,
                    minHeight: 38,
                    border: '1px solid var(--border)',
                    position: 'relative',
                    verticalAlign: 'middle',
                    background: isModified
                      ? 'rgba(16, 185, 129, 0.08)'
                      : (isReadOnly ? 'var(--bg-secondary, rgba(0,0,0,0.02))' : 'transparent'),
                    boxShadow: cellError
                      ? 'inset 0 0 0 1.5px #ef4444'
                      : (isCellActive ? 'inset 0 0 0 2px var(--primary, #10b981)' : 'none')
                  };

                  if (isReadOnly) {
                    var displayVal = (typeof col.format === 'function')
                      ? col.format(cellVal, row)
                      : String(cellVal || '-');

                    return React.createElement('td', {
                      key: col.key,
                      style: tdStyle,
                      title: cellError || displayVal
                    },
                      React.createElement('div', {
                        style: isWrapCol ? {
                          padding: '3px 6px',
                          fontSize: '0.78rem',
                          lineHeight: '1.25',
                          textAlign: col.align || 'left',
                          color: isModified ? 'var(--primary, #10b981)' : 'var(--text-secondary)',
                          fontWeight: isModified ? 600 : 'normal',
                          whiteSpace: 'normal',
                          wordBreak: 'break-word',
                          display: '-webkit-box',
                          WebkitLineClamp: 2,
                          WebkitBoxOrient: 'vertical',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          boxSizing: 'border-box'
                        } : {
                          padding: '4px 8px',
                          fontSize: '0.8rem',
                          textAlign: col.align || (col.type === 'number' ? 'right' : 'left'),
                          color: isModified ? 'var(--primary, #10b981)' : 'var(--text-secondary)',
                          fontWeight: isModified ? 600 : 'normal',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis'
                        }
                      }, displayVal)
                    );
                  }

                  // Editable Cell
                  if (col.type === 'select') {
                    return React.createElement('td', {
                      key: col.key,
                      style: tdStyle,
                      title: cellError || ''
                    },
                      React.createElement('select', {
                        'data-row': rowIdx,
                        'data-col': col.key,
                        value: cellVal,
                        onChange: function (e) {
                          if (onChangeRow) onChangeRow(rowIdx, col.key, e.target.value);
                        },
                        onFocus: function () { setActiveCell({ rowIdx: rowIdx, colKey: col.key }); },
                        onKeyDown: function (e) { handleCellKeyDown(e, rowIdx, col.key); },
                        style: {
                          width: '100%',
                          height: '100%',
                          border: 'none',
                          outline: 'none',
                          background: 'transparent',
                          fontSize: '0.8rem',
                          color: 'var(--text-primary)',
                          padding: '2px 4px',
                          cursor: 'pointer'
                        }
                      },
                        (col.options || []).map(function (opt) {
                          var optVal = typeof opt === 'object' ? opt.value : opt;
                          var optLabel = typeof opt === 'object' ? opt.label : opt;
                          return React.createElement('option', { key: optVal, value: optVal }, optLabel);
                        })
                      )
                    );
                  }

                  if (isWrapCol) {
                    return React.createElement('td', {
                      key: col.key,
                      style: tdStyle,
                      title: cellError || (cellVal != null ? String(cellVal) : '')
                    },
                      !isCellActive ? React.createElement('div', {
                        tabIndex: 0,
                        'data-row': rowIdx,
                        'data-col': col.key,
                        onFocus: function () { focusCell(rowIdx, col.key); },
                        onClick: function () { focusCell(rowIdx, col.key); },
                        style: {
                          width: '100%',
                          height: '100%',
                          maxHeight: '36px',
                          padding: '2px 6px',
                          fontSize: '0.78rem',
                          lineHeight: '1.25',
                          textAlign: col.align || 'left',
                          color: isModified ? 'var(--primary, #10b981)' : 'var(--text-primary)',
                          fontWeight: isModified ? 600 : 'normal',
                          whiteSpace: 'normal',
                          wordBreak: 'break-word',
                          display: '-webkit-box',
                          WebkitLineClamp: 2,
                          WebkitBoxOrient: 'vertical',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          boxSizing: 'border-box',
                          cursor: 'text'
                        }
                      }, cellVal != null ? String(cellVal) : '') :
                      React.createElement('textarea', {
                        rows: 2,
                        'data-row': rowIdx,
                        'data-col': col.key,
                        placeholder: col.placeholder || '',
                        value: cellVal != null ? String(cellVal) : '',
                        autoFocus: true,
                        onChange: function (e) {
                          var val = e.target.value.replace(/[\r\n]+/g, ' ');
                          if (onChangeRow) onChangeRow(rowIdx, col.key, val);
                        },
                        onFocus: function (e) {
                          setActiveCell({ rowIdx: rowIdx, colKey: col.key });
                          if (e.target && e.target.select) e.target.select();
                        },
                        onKeyDown: function (e) { handleCellKeyDown(e, rowIdx, col.key); },
                        onPaste: function (e) { handleCellPaste(e, rowIdx, col.key); },
                        style: {
                          width: '100%',
                          height: '100%',
                          minHeight: '32px',
                          maxHeight: '38px',
                          border: 'none',
                          outline: 'none',
                          background: 'transparent',
                          padding: '2px 6px',
                          fontSize: '0.78rem',
                          lineHeight: '1.25',
                          color: 'var(--text-primary)',
                          boxSizing: 'border-box',
                          resize: 'none',
                          overflow: 'hidden',
                          wordBreak: 'break-word',
                          whiteSpace: 'normal',
                          fontFamily: 'inherit',
                          verticalAlign: 'middle',
                          display: '-webkit-box',
                          WebkitLineClamp: 2,
                          WebkitBoxOrient: 'vertical'
                        }
                      })
                    );
                  }

                  return React.createElement('td', {
                    key: col.key,
                    style: tdStyle,
                    title: cellError || ''
                  },
                    React.createElement('input', {
                      type: col.type === 'number' ? 'text' : 'text',
                      inputMode: col.type === 'number' ? 'decimal' : 'text',
                      'data-row': rowIdx,
                      'data-col': col.key,
                      list: col.datalistId || null,
                      placeholder: col.placeholder || '',
                      value: cellVal != null ? String(cellVal) : '',
                      onChange: function (e) {
                        var val = e.target.value;
                        if (onChangeRow) onChangeRow(rowIdx, col.key, val);
                      },
                      onFocus: function (e) {
                        setActiveCell({ rowIdx: rowIdx, colKey: col.key });
                        if (e.target && e.target.select) e.target.select();
                      },
                      onKeyDown: function (e) { handleCellKeyDown(e, rowIdx, col.key); },
                      onPaste: function (e) { handleCellPaste(e, rowIdx, col.key); },
                      style: {
                        width: '100%',
                        height: '100%',
                        border: 'none',
                        outline: 'none',
                        background: 'transparent',
                        padding: '4px 8px',
                        fontSize: '0.8rem',
                        fontFamily: (col.key === 'sku' || col.key === 'barcode' || col.key === 'hsnCode') ? 'monospace' : 'inherit',
                        textAlign: col.align || (col.type === 'number' ? 'right' : 'left'),
                        color: 'var(--text-primary)',
                        boxSizing: 'border-box'
                      }
                    })
                  );
                }),

                // Delete Row Button
                canDeleteRows && React.createElement('td', {
                  style: {
                    textAlign: 'center',
                    border: '1px solid var(--border)',
                    padding: 0
                  }
                },
                  React.createElement('button', {
                    type: 'button',
                    tabIndex: -1,
                    className: 'btn btn-ghost btn-xs',
                    onClick: function () { if (onDeleteRow) onDeleteRow(rowIdx); },
                    style: {
                      padding: '2px 6px',
                      color: 'var(--text-muted)',
                      fontSize: '0.75rem',
                      lineHeight: 1
                    },
                    title: 'Delete row'
                  }, '✕')
                )
              );
            })
          )
        )
      ),

      // Footer Row Adder for Bulk Creation
      canAddRows && onAddRow && React.createElement('div', {
        style: {
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '8px 12px',
          background: 'var(--bg-card-header, var(--bg-card))',
          borderTop: '1px solid var(--border)',
          fontSize: '0.8rem'
        }
      },
        React.createElement('div', { style: { display: 'flex', gap: 6 } },
          React.createElement('button', {
            type: 'button',
            className: 'btn btn-outline btn-xs',
            onClick: function () { onAddRow(1); }
          }, '+ 1 Row'),
          React.createElement('button', {
            type: 'button',
            className: 'btn btn-outline btn-xs',
            onClick: function () { onAddRow(5); }
          }, '+ 5 Rows'),
          React.createElement('button', {
            type: 'button',
            className: 'btn btn-outline btn-xs',
            onClick: function () { onAddRow(10); }
          }, '+ 10 Rows')
        ),
        React.createElement('div', { style: { color: 'var(--text-muted)', fontSize: '0.74rem' } },
          'Press Enter on last row to auto-add new row.'
        )
      )
    );
  }

  exports.CompactEditableGrid = CompactEditableGrid;
  exports.parseSpreadsheetClipboard = parseSpreadsheetClipboard;
});
