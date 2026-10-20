/**
 * ProductIntelligence.js
 * Comprehensive Product Insights, Demand Forecasting & Smart Restocking UI components.
 * Native SVG Visualization, Explainable Replenishment Advisory, and Cross-Navigation.
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define(['react'], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory(require('react'));
  } else {
    const exports = factory(root.React);
    root.DemandForecastChart = exports.DemandForecastChart;
    root.ProductDetailDrawer = exports.ProductDetailDrawer;
    root.ProductIntelligenceHub = exports.ProductIntelligenceHub;
    root.SmartReorderView = exports.SmartReorderView;
    root.BulkProductAddModal = exports.BulkProductAddModal;
    root.PricingProfileManager = exports.PricingProfileManager;
  }
}(typeof self !== 'undefined' ? self : this, function (React) {
  const { useState, useEffect, useMemo, useCallback } = React;

  // Helper formatting
  const fmtNum = (val, dec = 2) => {
    if (val === null || val === undefined || isNaN(Number(val))) return '0';
    return Number(val).toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: dec });
  };

  const fmtCurrency = (val) => {
    if (typeof BillsoftUtils !== 'undefined' && BillsoftUtils.formatCurrency) {
      return BillsoftUtils.formatCurrency(val || 0);
    }
    return '₹' + fmtNum(val, 2);
  };

  // ═══════════════════════════════════════════════════════════════════
  // 1. DEMAND FORECAST CHART (Native SVG)
  // ═══════════════════════════════════════════════════════════════════
  function DemandForecastChart({
    historicalPoints = [],
    trajectoryPoints = [],
    selectedHorizon = 30,
    confidenceLevel = 'MEDIUM',
    confidenceScore = 80
  }) {
    const [hoveredPoint, setHoveredPoint] = useState(null);

    // Limit trajectory by selected horizon
    const activeTrajectory = useMemo(() => {
      if (!trajectoryPoints || trajectoryPoints.length === 0) return [];
      return trajectoryPoints.slice(0, selectedHorizon);
    }, [trajectoryPoints, selectedHorizon]);

    const activeHistorical = useMemo(() => {
      if (!historicalPoints || historicalPoints.length === 0) return [];
      // Show up to last 90 days of history on chart
      return historicalPoints.slice(-90);
    }, [historicalPoints]);

    const svgWidth = 760;
    const svgHeight = 240;
    const padLeft = 55;
    const padRight = 30;
    const padTop = 30;
    const padBottom = 35;

    const chartW = svgWidth - padLeft - padRight;
    const chartH = svgHeight - padTop - padBottom;

    // Combine data points
    const combinedData = useMemo(() => {
      const items = [];
      activeHistorical.forEach(h => {
        items.push({
          date: h.date,
          value: Number(h.netDemand != null ? h.netDemand : (h.grossDemand || 0)),
          isForecast: false,
          raw: h
        });
      });
      activeTrajectory.forEach(t => {
        items.push({
          date: t.date,
          value: Number(t.predictedDemand || 0),
          isForecast: true,
          raw: t
        });
      });
      return items;
    }, [activeHistorical, activeTrajectory]);

    const maxValue = useMemo(() => {
      let max = 5;
      combinedData.forEach(d => {
        if (d.value > max) max = d.value;
      });
      return max * 1.15;
    }, [combinedData]);

    const histCount = activeHistorical.length;
    const totalCount = combinedData.length;

    const points = useMemo(() => {
      if (totalCount === 0) return [];
      return combinedData.map((d, i) => {
        const x = padLeft + (totalCount > 1 ? (i / (totalCount - 1)) * chartW : chartW / 2);
        const y = padTop + chartH - (maxValue > 0 ? (d.value / maxValue) * chartH : 0);
        return { ...d, x, y, index: i };
      });
    }, [combinedData, totalCount, chartW, chartH, maxValue]);

    // Split paths for historical and forecast
    const histPoints = points.filter(p => !p.isForecast);
    const forecastPoints = points.filter(p => p.isForecast);

    // Build historical line & area path
    let histLinePath = '';
    let histAreaPath = '';
    if (histPoints.length > 0) {
      histLinePath = `M ${histPoints[0].x} ${histPoints[0].y}`;
      for (let i = 1; i < histPoints.length; i++) {
        histLinePath += ` L ${histPoints[i].x} ${histPoints[i].y}`;
      }
      histAreaPath = `${histLinePath} L ${histPoints[histPoints.length - 1].x} ${padTop + chartH} L ${histPoints[0].x} ${padTop + chartH} Z`;
    }

    // Build forecast line path connecting from last historical point
    let forecastLinePath = '';
    let forecastAreaPath = '';
    if (forecastPoints.length > 0) {
      const startPt = histPoints.length > 0 ? histPoints[histPoints.length - 1] : forecastPoints[0];
      forecastLinePath = `M ${startPt.x} ${startPt.y}`;
      for (let i = 0; i < forecastPoints.length; i++) {
        forecastLinePath += ` L ${forecastPoints[i].x} ${forecastPoints[i].y}`;
      }
      forecastAreaPath = `${forecastLinePath} L ${forecastPoints[forecastPoints.length - 1].x} ${padTop + chartH} L ${startPt.x} ${padTop + chartH} Z`;
    }

    const todayX = histPoints.length > 0 ? histPoints[histPoints.length - 1].x : padLeft;

    if (combinedData.length === 0) {
      return React.createElement('div', {
        style: {
          height: 200,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'var(--text-secondary)'
        }
      },
        React.createElement('div', { style: { fontSize: '1.75rem', marginBottom: 8 } }, '📈'),
        React.createElement('div', { style: { fontWeight: 600 } }, 'No forecast trajectory available yet.'),
        React.createElement('div', { style: { fontSize: '0.8rem', marginTop: 4 } }, 'Run incremental or nightly sync to generate demand trajectory.')
      );
    }

    return React.createElement('div', { style: { width: '100%', position: 'relative' } },
      React.createElement('svg', {
        viewBox: `0 0 ${svgWidth} ${svgHeight}`,
        style: { width: '100%', height: 'auto', overflow: 'visible' },
        onMouseLeave: () => setHoveredPoint(null)
      },
        // Gradients & Defs
        React.createElement('defs', null,
          React.createElement('linearGradient', { id: 'histAreaGrad', x1: '0', y1: '0', x2: '0', y2: '1' },
            React.createElement('stop', { offset: '0%', stopColor: '#3b82f6', stopOpacity: '0.28' }),
            React.createElement('stop', { offset: '100%', stopColor: '#3b82f6', stopOpacity: '0.02' })
          ),
          React.createElement('linearGradient', { id: 'forecastAreaGrad', x1: '0', y1: '0', x2: '0', y2: '1' },
            React.createElement('stop', { offset: '0%', stopColor: '#8b5cf6', stopOpacity: '0.22' }),
            React.createElement('stop', { offset: '100%', stopColor: '#8b5cf6', stopOpacity: '0.01' })
          )
        ),

        // Gridlines
        [0, 0.25, 0.5, 0.75, 1.0].map((frac, idx) => {
          const y = padTop + chartH - frac * chartH;
          const val = maxValue * frac;
          return React.createElement('g', { key: idx },
            React.createElement('line', {
              x1: padLeft,
              y1: y,
              x2: svgWidth - padRight,
              y2: y,
              stroke: 'var(--border, #e2e8f0)',
              strokeDasharray: frac === 0 ? 'none' : '3,3',
              strokeWidth: 1
            }),
            React.createElement('text', {
              x: padLeft - 8,
              y: y + 4,
              textAnchor: 'end',
              fontSize: 10,
              fill: 'var(--text-secondary, #64748b)'
            }, fmtNum(val, 1))
          );
        }),

        // Today Divider Marker
        histPoints.length > 0 && React.createElement('g', null,
          React.createElement('line', {
            x1: todayX,
            y1: padTop,
            x2: todayX,
            y2: padTop + chartH,
            stroke: '#6366f1',
            strokeWidth: 1.5,
            strokeDasharray: '4,4'
          }),
          React.createElement('text', {
            x: todayX,
            y: padTop - 10,
            textAnchor: 'middle',
            fontSize: 10,
            fontWeight: 700,
            fill: '#6366f1'
          }, 'TODAY (Horizon Start)')
        ),

        // Historical Area & Line
        histAreaPath && React.createElement('path', { d: histAreaPath, fill: 'url(#histAreaGrad)' }),
        histLinePath && React.createElement('path', {
          d: histLinePath,
          fill: 'none',
          stroke: '#3b82f6',
          strokeWidth: 2.2,
          strokeLinecap: 'round'
        }),

        // Forecast Area & Line
        forecastAreaPath && React.createElement('path', { d: forecastAreaPath, fill: 'url(#forecastAreaGrad)' }),
        forecastLinePath && React.createElement('path', {
          d: forecastLinePath,
          fill: 'none',
          stroke: '#8b5cf6',
          strokeWidth: 2.2,
          strokeDasharray: '5,3',
          strokeLinecap: 'round'
        }),

        // Interactive Points / Hover Targets
        points.map((p, i) => {
          const isSelected = hoveredPoint && hoveredPoint.index === p.index;
          return React.createElement('g', {
            key: i,
            onMouseEnter: () => setHoveredPoint(p),
            style: { cursor: 'pointer' }
          },
            // Invisible wider touch hit target
            React.createElement('circle', { cx: p.x, cy: p.y, r: 8, fill: 'transparent' }),
            (isSelected || i === 0 || i === points.length - 1 || (!p.isForecast && i === histPoints.length - 1)) &&
            React.createElement('circle', {
              cx: p.x,
              cy: p.y,
              r: isSelected ? 5.5 : 3.5,
              fill: p.isForecast ? '#8b5cf6' : '#3b82f6',
              stroke: '#ffffff',
              strokeWidth: 2
            })
          );
        })
      ),

      // Tooltip Card
      hoveredPoint && React.createElement('div', {
        style: {
          position: 'absolute',
          left: Math.min(Math.max(hoveredPoint.x - 70, 10), svgWidth - 160),
          top: Math.max(hoveredPoint.y - 65, 0),
          background: 'rgba(15, 23, 42, 0.92)',
          color: '#ffffff',
          padding: '6px 12px',
          borderRadius: 8,
          fontSize: '0.75rem',
          pointerEvents: 'none',
          boxShadow: '0 4px 12px rgba(0,0,0,0.25)',
          zIndex: 10,
          whiteSpace: 'nowrap'
        }
      },
        React.createElement('div', { style: { fontWeight: 700, fontSize: '0.8rem', display: 'flex', alignItems: 'center', gap: 6 } },
          hoveredPoint.isForecast ? '🔮 Forecast' : '📊 Actual Sales',
          React.createElement('span', { style: { opacity: 0.75, fontWeight: 400 } }, hoveredPoint.date)
        ),
        React.createElement('div', { style: { marginTop: 3, display: 'flex', gap: 10 } },
          React.createElement('span', null, 'Demand: ', React.createElement('b', null, fmtNum(hoveredPoint.value, 2), ' units')),
          hoveredPoint.isForecast && confidenceLevel && React.createElement('span', { style: { color: '#a78bfa' } }, `(${confidenceLevel} Conf)`)
        )
      ),

      // Legend
      React.createElement('div', {
        style: {
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginTop: 10,
          padding: '0 8px',
          fontSize: '0.78rem',
          color: 'var(--text-secondary)'
        }
      },
        React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 16 } },
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 6 } },
            React.createElement('span', { style: { width: 12, height: 3, background: '#3b82f6', borderRadius: 2, display: 'inline-block' } }),
            'Historical Demand'
          ),
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 6 } },
            React.createElement('span', { style: { width: 12, height: 3, background: '#8b5cf6', borderTop: '2px dashed #8b5cf6', borderRadius: 2, display: 'inline-block' } }),
            `Forecast Trajectory (${selectedHorizon}D)`
          )
        ),
        React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
          React.createElement('span', { className: `badge-conf-${(confidenceLevel || 'LOW').toLowerCase()}` },
            `Confidence: ${confidenceLevel} (${fmtNum(confidenceScore, 0)}%)`
          )
        )
      )
    );
  }

  // ═══════════════════════════════════════════════════════════════════
  // 2. PRODUCT DETAIL DRAWER
  // ═══════════════════════════════════════════════════════════════════
  function ProductDetailDrawer({ productId, onClose, onUseRecommendation, onViewSmartReorder }) {
    const [loading, setLoading] = useState(true);
    const [insights, setInsights] = useState(null);
    const [trends, setTrends] = useState(null);
    const [trajectory, setTrajectory] = useState(null);
    const [explanation, setExplanation] = useState(null);
    const [config, setConfig] = useState(null);
    const [selectedHorizon, setSelectedHorizon] = useState(30);
    const [showConfigModal, setShowConfigModal] = useState(false);
    const [configForm, setConfigForm] = useState({
      leadTimeDays: 7,
      reviewPeriodDays: 14,
      minimumOrderQty: 1,
      packSize: 1,
      safetyStockOverride: ''
    });

    const loadData = useCallback(() => {
      if (!productId) return;
      setLoading(true);
      Promise.all([
        API.forecast.getInsights(productId),
        API.forecast.getTrends(productId, '90d').catch(() => null),
        API.forecast.getTrajectory(productId).catch(() => null),
        API.forecast.getRestockExplanation(productId).catch(() => null),
        API.forecast.getConfig(productId).catch(() => null)
      ]).then(([ins, tr, traj, expl, cfg]) => {
        setInsights(ins);
        setTrends(tr);
        setTrajectory(traj);
        setExplanation(expl);
        setConfig(cfg);
        if (cfg) {
          setConfigForm({
            leadTimeDays: cfg.leadTimeDays || 7,
            reviewPeriodDays: cfg.reviewPeriodDays || 14,
            minimumOrderQty: cfg.minimumOrderQty || 1,
            packSize: cfg.packSize || 1,
            safetyStockOverride: cfg.safetyStockOverride != null ? String(cfg.safetyStockOverride) : ''
          });
        }
        setLoading(false);
      }).catch(err => {
        console.error('Failed to load product intelligence details:', err);
        setLoading(false);
      });
    }, [productId]);

    useEffect(() => {
      loadData();
    }, [loadData]);

    const handleSaveConfig = async () => {
      try {
        await API.forecast.updateConfig(productId, {
          leadTimeDays: parseInt(configForm.leadTimeDays, 10) || 7,
          reviewPeriodDays: parseInt(configForm.reviewPeriodDays, 10) || 14,
          minimumOrderQty: parseFloat(configForm.minimumOrderQty) || 1,
          packSize: parseFloat(configForm.packSize) || 1,
          safetyStockOverride: configForm.safetyStockOverride ? parseFloat(configForm.safetyStockOverride) : null
        });
        if (typeof showToast === 'function') showToast('Product replenishment parameters updated', 'success');
        setShowConfigModal(false);
        loadData();
      } catch (err) {
        console.error(err);
        if (typeof showToast === 'function') showToast('Failed to update config', 'error');
      }
    };

    if (!productId) return null;

    return React.createElement('div', {
      className: 'forecast-drawer-backdrop',
      onClick: (e) => { if (e.target === e.currentTarget) onClose(); }
    },
      React.createElement('div', { className: 'forecast-drawer-panel' },
        // Header
        React.createElement('div', { className: 'forecast-drawer-header' },
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 12 } },
            React.createElement('div', {
              style: {
                width: 42,
                height: 42,
                borderRadius: 10,
                background: 'rgba(99, 102, 241, 0.1)',
                color: '#6366f1',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '1.3rem'
              }
            }, '📊'),
            React.createElement('div', null,
              React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
                React.createElement('h3', { style: { margin: 0, fontSize: '1.2rem', fontWeight: 700 } },
                  insights ? insights.productName : 'Product Intelligence'
                ),
                insights && React.createElement('span', {
                  className: 'badge',
                  style: { fontSize: '0.7rem', textTransform: 'uppercase' }
                }, insights.itemType || 'GOODS')
              ),
              React.createElement('div', { style: { fontSize: '0.78rem', color: 'var(--text-secondary)', marginTop: 2 } },
                `SKU: ${insights && insights.sku ? insights.sku : 'N/A'} • Category: ${insights && insights.category ? insights.category : 'General'}`
              )
            )
          ),
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
            React.createElement('button', {
              className: 'btn btn-outline btn-sm',
              onClick: () => setShowConfigModal(!showConfigModal),
              title: 'Edit Lead Time, MOQ, Pack Size & Safety Stock'
            }, '⚙️ Params'),
            React.createElement('button', {
              className: 'btn btn-ghost btn-sm',
              onClick: onClose,
              style: { fontSize: '1.2rem', padding: '4px 10px' }
            }, '✕')
          )
        ),

        // Config Drawer Section (if expanded)
        showConfigModal && React.createElement('div', {
          style: {
            background: 'var(--bg-secondary)',
            borderBottom: '1px solid var(--border)',
            padding: '16px 24px',
            display: 'flex',
            flexDirection: 'column',
            gap: 12
          }
        },
          React.createElement('div', { style: { fontWeight: 700, fontSize: '0.88rem' } }, '⚙️ Replenishment & Forecast Parameters'),
          React.createElement('div', {
            style: {
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))',
              gap: 12
            }
          },
            React.createElement('div', null,
              React.createElement('label', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)', display: 'block', marginBottom: 4 } }, 'Lead Time (Days)'),
              React.createElement('input', {
                type: 'number',
                className: 'form-control form-control-sm',
                value: configForm.leadTimeDays,
                onChange: (e) => setConfigForm({ ...configForm, leadTimeDays: e.target.value })
              })
            ),
            React.createElement('div', null,
              React.createElement('label', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)', display: 'block', marginBottom: 4 } }, 'Review Period (Days)'),
              React.createElement('input', {
                type: 'number',
                className: 'form-control form-control-sm',
                value: configForm.reviewPeriodDays,
                onChange: (e) => setConfigForm({ ...configForm, reviewPeriodDays: e.target.value })
              })
            ),
            React.createElement('div', null,
              React.createElement('label', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)', display: 'block', marginBottom: 4 } }, 'MOQ (Min Order)'),
              React.createElement('input', {
                type: 'number',
                className: 'form-control form-control-sm',
                value: configForm.minimumOrderQty,
                onChange: (e) => setConfigForm({ ...configForm, minimumOrderQty: e.target.value })
              })
            ),
            React.createElement('div', null,
              React.createElement('label', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)', display: 'block', marginBottom: 4 } }, 'Pack Size Multiple'),
              React.createElement('input', {
                type: 'number',
                className: 'form-control form-control-sm',
                value: configForm.packSize,
                onChange: (e) => setConfigForm({ ...configForm, packSize: e.target.value })
              })
            ),
            React.createElement('div', null,
              React.createElement('label', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)', display: 'block', marginBottom: 4 } }, 'Safety Stock Override'),
              React.createElement('input', {
                type: 'number',
                placeholder: 'Auto (Calculated)',
                className: 'form-control form-control-sm',
                value: configForm.safetyStockOverride,
                onChange: (e) => setConfigForm({ ...configForm, safetyStockOverride: e.target.value })
              })
            )
          ),
          React.createElement('div', { style: { display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 4 } },
            React.createElement('button', { className: 'btn btn-ghost btn-xs', onClick: () => setShowConfigModal(false) }, 'Cancel'),
            React.createElement('button', { className: 'btn btn-primary btn-xs', onClick: handleSaveConfig }, 'Save Parameters')
          )
        ),

        // Body Content
        React.createElement('div', { className: 'forecast-drawer-body' },
          loading ? React.createElement('div', {
            style: { padding: 40, textAlign: 'center', color: 'var(--text-secondary)' }
          }, 'Loading Product Intelligence...') :

            React.createElement(React.Fragment, null,
              // 1. KPI Strip
              React.createElement('div', {
                style: {
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))',
                  gap: 12
                }
              },
                // Current Stock
                React.createElement('div', { className: 'card', style: { padding: '10px 14px' } },
                  React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)', fontWeight: 600 } }, 'Current Stock'),
                  React.createElement('div', { style: { fontSize: '1.25rem', fontWeight: 700, marginTop: 2 } }, fmtNum(insights.currentStock, 1)),
                  React.createElement('div', { style: { fontSize: '0.7rem', color: 'var(--text-secondary)' } }, `${insights.unit || 'pcs'}`)
                ),
                // Incoming PO
                React.createElement('div', { className: 'card', style: { padding: '10px 14px' } },
                  React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)', fontWeight: 600 } }, 'Incoming PO'),
                  React.createElement('div', { style: { fontSize: '1.25rem', fontWeight: 700, marginTop: 2, color: '#6366f1' } }, fmtNum(insights.incomingStock, 1)),
                  React.createElement('div', { style: { fontSize: '0.7rem', color: 'var(--text-secondary)' } }, 'Issued Orders')
                ),
                // 30D Forecast
                React.createElement('div', { className: 'card', style: { padding: '10px 14px' } },
                  React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)', fontWeight: 600 } }, '30D Demand'),
                  React.createElement('div', { style: { fontSize: '1.25rem', fontWeight: 700, marginTop: 2, color: '#8b5cf6' } }, fmtNum(insights.forecast30Days, 1)),
                  React.createElement('div', { style: { fontSize: '0.7rem', color: 'var(--text-secondary)' } }, 'Projected')
                ),
                // WASP
                React.createElement('div', { className: 'card', style: { padding: '10px 14px' } },
                  React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)', fontWeight: 600 } }, 'WASP (Avg Price)'),
                  React.createElement('div', { style: { fontSize: '1.25rem', fontWeight: 700, marginTop: 2, color: '#10b981' } },
                    insights.weightedAverageSellingPrice ? fmtCurrency(insights.weightedAverageSellingPrice) : 'N/A'
                  ),
                  React.createElement('div', { style: { fontSize: '0.7rem', color: 'var(--text-secondary)' } }, 'Realized')
                ),
                // Stock Coverage
                React.createElement('div', { className: 'card', style: { padding: '10px 14px' } },
                  React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)', fontWeight: 600 } }, 'Stock Coverage'),
                  React.createElement('div', { style: { fontSize: '1.25rem', fontWeight: 700, marginTop: 2 } },
                    insights.stockCoverageDays != null ? `${fmtNum(insights.stockCoverageDays, 0)} days` : 'N/A'
                  ),
                  React.createElement('div', { style: { fontSize: '0.7rem', color: 'var(--text-secondary)' } }, 'At Current Rate')
                ),
                // Confidence
                React.createElement('div', { className: 'card', style: { padding: '10px 14px' } },
                  React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)', fontWeight: 600 } }, 'Confidence'),
                  React.createElement('div', { style: { marginTop: 4 } },
                    React.createElement('span', { className: `badge-conf-${(insights.forecastConfidenceLevel || 'LOW').toLowerCase()}` },
                      `${insights.forecastConfidenceLevel || 'LOW'} (${fmtNum(insights.forecastConfidenceScore, 0)}%)`
                    )
                  ),
                  React.createElement('div', { style: { fontSize: '0.7rem', color: 'var(--text-secondary)', marginTop: 4 } },
                    trajectory ? (trajectory.selectedModel || 'Historical Mean') : 'Model'
                  )
                )
              ),

              // 2. Forecast Section & Native Chart
              React.createElement('div', { className: 'forecast-section-card' },
                React.createElement('div', {
                  style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 10 }
                },
                  React.createElement('div', null,
                    React.createElement('h4', { style: { margin: 0, fontSize: '0.95rem', fontWeight: 700 } }, '📈 Demand Trajectory & Historical Velocity'),
                    React.createElement('div', { style: { fontSize: '0.76rem', color: 'var(--text-secondary)', marginTop: 2 } },
                      `Continuous statistical time-series model (${trajectory ? trajectory.selectedModel : 'Auto-selected'})`
                    )
                  ),
                  // Horizon Selector Buttons
                  React.createElement('div', { className: 'btn-group', style: { display: 'flex', gap: 4 } },
                    [30, 90, 180, 365].map(h =>
                      React.createElement('button', {
                        key: h,
                        className: `btn btn-xs ${selectedHorizon === h ? 'btn-primary' : 'btn-outline'}`,
                        onClick: () => setSelectedHorizon(h)
                      }, `${h}D`)
                    )
                  )
                ),

                // Chart Container
                React.createElement('div', { className: 'forecast-chart-wrapper' },
                  React.createElement(DemandForecastChart, {
                    historicalPoints: trends ? trends.points : [],
                    trajectoryPoints: trajectory ? trajectory.trajectory : [],
                    selectedHorizon: selectedHorizon,
                    confidenceLevel: insights.forecastConfidenceLevel,
                    confidenceScore: insights.forecastConfidenceScore
                  })
                )
              ),

              // 3. Restock Advisory & Action Box
              React.createElement('div', {
                className: 'forecast-section-card',
                style: {
                  border: insights.restockUrgency === 'OUT_OF_STOCK' || insights.restockUrgency === 'CRITICAL'
                    ? '1.5px solid #f59e0b' : '1px solid var(--border)'
                }
              },
                React.createElement('div', { style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 10 } },
                  React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 10 } },
                    React.createElement('span', { style: { fontSize: '1.3rem' } }, '⚡'),
                    React.createElement('div', null,
                      React.createElement('h4', { style: { margin: 0, fontSize: '0.95rem', fontWeight: 700 } }, 'Smart Replenishment Advisory'),
                      React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-secondary)' } },
                        'Advisory replenishment based on lead time, demand velocity & safety stock'
                      )
                    )
                  ),
                  React.createElement('span', {
                    className: `badge-urgency-${(insights.restockUrgency || 'HEALTHY').toLowerCase().replace(/_/g, '-')}`
                  }, insights.restockUrgency || 'HEALTHY')
                ),

                // Restock Breakdown Grid
                explanation && React.createElement('div', {
                  style: {
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
                    gap: 10,
                    background: 'var(--bg-secondary)',
                    padding: '12px 14px',
                    borderRadius: 8,
                    fontSize: '0.78rem'
                  }
                },
                  React.createElement('div', null,
                    React.createElement('div', { style: { color: 'var(--text-secondary)' } }, 'Horizon Demand:'),
                    React.createElement('div', { style: { fontWeight: 700, marginTop: 2 } }, `${fmtNum(explanation.totalPlanningHorizonDemand, 1)} units`)
                  ),
                  React.createElement('div', null,
                    React.createElement('div', { style: { color: 'var(--text-secondary)' } }, 'Safety Stock:'),
                    React.createElement('div', { style: { fontWeight: 700, marginTop: 2 } }, `${fmtNum(explanation.safetyStock, 1)} units`)
                  ),
                  React.createElement('div', null,
                    React.createElement('div', { style: { color: 'var(--text-secondary)' } }, 'Target Stock Level:'),
                    React.createElement('div', { style: { fontWeight: 700, marginTop: 2 } }, `${fmtNum(explanation.targetStockLevel, 1)} units`)
                  ),
                  React.createElement('div', null,
                    React.createElement('div', { style: { color: 'var(--text-secondary)' } }, 'Net Position (Stock+PO):'),
                    React.createElement('div', { style: { fontWeight: 700, marginTop: 2 } }, `${fmtNum(explanation.netInventoryPosition, 1)} units`)
                  ),
                  React.createElement('div', null,
                    React.createElement('div', { style: { color: 'var(--text-secondary)' } }, 'Recommended Order:'),
                    React.createElement('div', { style: { fontWeight: 800, color: '#4f46e5', fontSize: '0.95rem', marginTop: 2 } },
                      `${fmtNum(explanation.finalRecommendedOrderQty, 0)} ${insights.unit || 'pcs'}`
                    )
                  )
                ),

                // Explanation Text
                explanation && explanation.explanationText && React.createElement('div', {
                  style: {
                    fontSize: '0.78rem',
                    color: 'var(--text-secondary)',
                    lineHeight: 1.45,
                    borderLeft: '3px solid #6366f1',
                    paddingLeft: 10
                  }
                }, explanation.explanationText),

                // Action Bar
                React.createElement('div', {
                  style: { display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 10, marginTop: 6 }
                },
                  React.createElement('button', {
                    className: 'btn btn-primary',
                    style: { display: 'flex', alignItems: 'center', gap: 6, fontWeight: 700 },
                    onClick: () => {
                      const qty = explanation ? explanation.finalRecommendedOrderQty : insights.recommendedOrderQty;
                      if (onUseRecommendation) {
                        onUseRecommendation(productId, qty && Number(qty) > 0 ? Number(qty) : 1);
                      }
                    }
                  }, '⚡ Use Recommendation (Create PO)')
                )
              ),

              // 4. Two-Column Analytics: Price & Sales Insights
              React.createElement('div', {
                style: {
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
                  gap: 16
                }
              },
                // Price Analytics Card
                React.createElement('div', { className: 'forecast-section-card' },
                  React.createElement('h4', { style: { margin: 0, fontSize: '0.9rem', fontWeight: 700 } }, '🏷️ Realized Price Economics'),
                  React.createElement('div', { style: { display: 'flex', flexDirection: 'column', gap: 8, fontSize: '0.8rem', marginTop: 4 } },
                    React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between' } },
                      React.createElement('span', { style: { color: 'var(--text-secondary)' } }, 'Current Listed Price:'),
                      React.createElement('span', { style: { fontWeight: 600 } }, fmtCurrency(insights.currentPrice))
                    ),
                    React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between' } },
                      React.createElement('span', { style: { color: 'var(--text-secondary)' } }, 'Weighted Avg Realized (WASP):'),
                      React.createElement('span', { style: { fontWeight: 700, color: '#10b981' } },
                        insights.weightedAverageSellingPrice ? fmtCurrency(insights.weightedAverageSellingPrice) : 'N/A'
                      )
                    ),
                    React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between' } },
                      React.createElement('span', { style: { color: 'var(--text-secondary)' } }, 'Lowest Historical Sold Price:'),
                      React.createElement('span', { style: { fontWeight: 600 } },
                        insights.minRealizedPrice ? fmtCurrency(insights.minRealizedPrice) : 'N/A'
                      )
                    ),
                    React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between' } },
                      React.createElement('span', { style: { color: 'var(--text-secondary)' } }, 'Highest Historical Sold Price:'),
                      React.createElement('span', { style: { fontWeight: 600 } },
                        insights.maxRealizedPrice ? fmtCurrency(insights.maxRealizedPrice) : 'N/A'
                      )
                    )
                  )
                ),

                // Sales & Demand History Card
                React.createElement('div', { className: 'forecast-section-card' },
                  React.createElement('h4', { style: { margin: 0, fontSize: '0.9rem', fontWeight: 700 } }, '📊 Lifetime Demand & Fulfillment'),
                  React.createElement('div', { style: { display: 'flex', flexDirection: 'column', gap: 8, fontSize: '0.8rem', marginTop: 4 } },
                    React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between' } },
                      React.createElement('span', { style: { color: 'var(--text-secondary)' } }, 'Gross Requested Units:'),
                      React.createElement('span', { style: { fontWeight: 600 } }, fmtNum(insights.grossUnitsSold, 1))
                    ),
                    React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between' } },
                      React.createElement('span', { style: { color: 'var(--text-secondary)' } }, 'Physically Fulfilled Units:'),
                      React.createElement('span', { style: { fontWeight: 600, color: '#10b981' } }, fmtNum(insights.fulfilledUnitsSold, 1))
                    ),
                    React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between' } },
                      React.createElement('span', { style: { color: 'var(--text-secondary)' } }, 'Stockout Shortage Units:'),
                      React.createElement('span', { style: { fontWeight: 600, color: '#ef4444' } }, fmtNum(insights.shortageUnits, 1))
                    ),
                    React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between' } },
                      React.createElement('span', { style: { color: 'var(--text-secondary)' } }, 'Sales Returned Units:'),
                      React.createElement('span', { style: { fontWeight: 600, color: '#d97706' } }, fmtNum(insights.returnedUnits, 1))
                    ),
                    React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between' } },
                      React.createElement('span', { style: { color: 'var(--text-secondary)' } }, 'Net Realized Revenue:'),
                      React.createElement('span', { style: { fontWeight: 700, color: 'var(--text-primary)' } }, fmtCurrency(insights.netSales))
                    ),
                    React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between' } },
                      React.createElement('span', { style: { color: 'var(--text-secondary)' } }, 'Distinct Named Customers:'),
                      React.createElement('span', { style: { fontWeight: 600 } }, insights.uniqueCustomerCount || 0)
                    )
                  )
                )
              )
            )
        )
      )
    );
  }

  // ═══════════════════════════════════════════════════════════════════
  // 3. PRODUCT INTELLIGENCE HUB (Firm -> Products Subtab)
  // ═══════════════════════════════════════════════════════════════════
  function ProductIntelligenceHub() {
    const [products, setProducts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [syncing, setSyncing] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedCategory, setSelectedCategory] = useState('ALL');
    const [itemTypeFilter, setItemTypeFilter] = useState('ALL');
    const [selectedProductId, setSelectedProductId] = useState(null);

    const loadProducts = useCallback(() => {
      setLoading(true);
      API.products.list().then(prods => {
        setProducts(prods || []);
        setLoading(false);
      }).catch(err => {
        console.error('Failed to load products:', err);
        setLoading(false);
      });
    }, []);

    useEffect(() => {
      loadProducts();
    }, [loadProducts]);

    const handleSync = async () => {
      setSyncing(true);
      try {
        const res = await API.forecast.adminSync();
        if (typeof showToast === 'function') {
          showToast(`Forecast sync complete: ${res.forecastsGenerated || 0} models computed.`, 'success');
        }
        loadProducts();
      } catch (err) {
        console.error(err);
        if (typeof showToast === 'function') showToast('Failed to trigger forecast sync', 'error');
      } finally {
        setSyncing(false);
      }
    };

    const handleUseRecommendation = (prodId, suggestedQty) => {
      window.__billsoftPendingNav = {
        page: 'firm',
        tab: 'orders',
        prefillPoItem: {
          productId: prodId,
          suggestedQty: suggestedQty
        },
        ts: Date.now()
      };
      window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', {
        detail: {
          page: 'firm',
          tab: 'orders',
          prefillPoItem: {
            productId: prodId,
            suggestedQty: suggestedQty
          }
        }
      }));
    };

    const categories = useMemo(() => {
      const set = new Set();
      products.forEach(p => { if (p.category) set.add(p.category); });
      return Array.from(set).sort();
    }, [products]);

    const filteredProducts = useMemo(() => {
      return products.filter(p => {
        if (searchQuery) {
          const q = searchQuery.toLowerCase();
          const matchName = p.name && p.name.toLowerCase().includes(q);
          const matchSku = p.sku && p.sku.toLowerCase().includes(q);
          if (!matchName && !matchSku) return false;
        }
        if (selectedCategory !== 'ALL' && p.category !== selectedCategory) return false;
        if (itemTypeFilter !== 'ALL' && (p.itemType || 'GOODS') !== itemTypeFilter) return false;
        return true;
      });
    }, [products, searchQuery, selectedCategory, itemTypeFilter]);

    // Overall Catalog Summary KPIs
    const catalogSummary = useMemo(() => {
      let totalStock = 0;
      let lowStockCount = 0;
      let serviceCount = 0;
      products.forEach(p => {
        if ((p.itemType || 'GOODS') === 'SERVICE') {
          serviceCount++;
        } else {
          const stock = Number(p.stockQuantity || 0);
          totalStock += stock;
          const minLvl = Number(p.minStockLevel || 5);
          if (stock <= minLvl) lowStockCount++;
        }
      });
      return {
        totalProducts: products.length,
        totalStockUnits: totalStock,
        lowStockAlerts: lowStockCount,
        serviceItems: serviceCount
      };
    }, [products]);

    return React.createElement('div', { className: 'forecast-hub-container' },
      // Top KPI Cards
      React.createElement('div', { className: 'forecast-kpi-grid' },
        React.createElement('div', { className: 'forecast-kpi-card' },
          React.createElement('div', { className: 'forecast-kpi-title' }, 'Active Catalog'),
          React.createElement('div', { className: 'forecast-kpi-val' }, catalogSummary.totalProducts),
          React.createElement('div', { className: 'forecast-kpi-sub' }, `${catalogSummary.serviceItems} services, ${catalogSummary.totalProducts - catalogSummary.serviceItems} physical goods`)
        ),
        React.createElement('div', { className: 'forecast-kpi-card' },
          React.createElement('div', { className: 'forecast-kpi-title' }, 'Total Stock Inventory'),
          React.createElement('div', { className: 'forecast-kpi-val', style: { color: '#10b981' } }, fmtNum(catalogSummary.totalStockUnits, 0)),
          React.createElement('div', { className: 'forecast-kpi-sub' }, 'Physical units in warehouse')
        ),
        React.createElement('div', { className: 'forecast-kpi-card' },
          React.createElement('div', { className: 'forecast-kpi-title', style: { color: catalogSummary.lowStockAlerts > 0 ? '#f59e0b' : 'var(--text-secondary)' } }, 'Low Stock Alerts'),
          React.createElement('div', { className: 'forecast-kpi-val', style: { color: catalogSummary.lowStockAlerts > 0 ? '#f59e0b' : 'var(--text-primary)' } }, catalogSummary.lowStockAlerts),
          React.createElement('div', { className: 'forecast-kpi-sub' }, 'Products at or below min level')
        ),
        React.createElement('div', { className: 'forecast-kpi-card' },
          React.createElement('div', { className: 'forecast-kpi-title' }, 'Forecasting Engine'),
          React.createElement('div', { className: 'forecast-kpi-val', style: { color: '#6366f1' } }, 'Multi-Model'),
          React.createElement('div', { className: 'forecast-kpi-sub' }, 'SES, Holt-Winters, Croston, SBA, TSB')
        )
      ),

      // Filter & Action Toolbar
      React.createElement('div', {
        className: 'card',
        style: {
          padding: '14px 18px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: 12
        }
      },
        React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap', flex: 1 } },
          // Search input
          React.createElement('input', {
            type: 'text',
            placeholder: '🔍 Search product name or SKU...',
            className: 'form-control',
            style: { maxWidth: 280 },
            value: searchQuery,
            onChange: (e) => setSearchQuery(e.target.value)
          }),
          // Category selector
          React.createElement('select', {
            className: 'form-control',
            style: { width: 'auto' },
            value: selectedCategory,
            onChange: (e) => setSelectedCategory(e.target.value)
          },
            React.createElement('option', { value: 'ALL' }, 'All Categories'),
            categories.map(c => React.createElement('option', { key: c, value: c }, c))
          ),
          // Type selector
          React.createElement('select', {
            className: 'form-control',
            style: { width: 'auto' },
            value: itemTypeFilter,
            onChange: (e) => setItemTypeFilter(e.target.value)
          },
            React.createElement('option', { value: 'ALL' }, 'All Product Types'),
            React.createElement('option', { value: 'GOODS' }, '📦 Goods (Physical)'),
            React.createElement('option', { value: 'SERVICE' }, '🛠️ Service (Non-Physical)')
          )
        ),
        React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
          React.createElement('button', {
            className: 'btn btn-secondary',
            onClick: () => {
              window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', {
                detail: { page: 'firm', tab: 'inventory' }
              }));
            },
            style: { display: 'flex', alignItems: 'center', gap: 6 }
          }, '📦 Inventory Manager'),
          React.createElement('button', {
            className: 'btn btn-outline',
            onClick: handleSync,
            disabled: syncing,
            style: { display: 'flex', alignItems: 'center', gap: 6 }
          }, syncing ? '⏳ Computing...' : '🔄 Re-sync Forecasts')
        )
      ),

      // Products Intelligence Table
      React.createElement('div', { className: 'table-container card', style: { padding: 0, overflow: 'hidden' } },
        React.createElement('table', { className: 'data-table', style: { width: '100%', margin: 0 } },
          React.createElement('thead', null,
            React.createElement('tr', null,
              React.createElement('th', null, 'Product / SKU'),
              React.createElement('th', null, 'Type'),
              React.createElement('th', { style: { textAlign: 'right' } }, 'Stock'),
              React.createElement('th', { style: { textAlign: 'right' } }, 'Listed Price'),
              React.createElement('th', null, 'Category'),
              React.createElement('th', { style: { textAlign: 'center' } }, 'Actions')
            )
          ),
          React.createElement('tbody', null,
            loading ? React.createElement('tr', null,
              React.createElement('td', { colSpan: 6, style: { textAlign: 'center', padding: 30, color: 'var(--text-secondary)' } },
                'Loading product intelligence records...'
              )
            ) : filteredProducts.length === 0 ? React.createElement('tr', null,
              React.createElement('td', { colSpan: 6, style: { textAlign: 'center', padding: 30, color: 'var(--text-secondary)' } },
                'No products match the selected criteria.'
              )
            ) : filteredProducts.map(p => {
              const isService = (p.itemType || 'GOODS') === 'SERVICE';
              const stock = Number(p.stockQuantity || 0);
              const minLvl = Number(p.minStockLevel || 5);
              return React.createElement('tr', {
                key: p.id,
                style: { cursor: 'pointer' },
                onClick: () => setSelectedProductId(p.id)
              },
                React.createElement('td', null,
                  React.createElement('div', { style: { fontWeight: 600, color: 'var(--text-primary)' } }, p.name),
                  React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)' } },
                    p.sku ? `SKU: ${p.sku}` : 'No SKU'
                  )
                ),
                React.createElement('td', null,
                  React.createElement('span', {
                    className: 'badge',
                    style: { fontSize: '0.7rem', background: isService ? 'rgba(99,102,241,0.1)' : 'rgba(16,185,129,0.1)', color: isService ? '#6366f1' : '#059669' }
                  }, isService ? 'SERVICE' : 'GOODS')
                ),
                React.createElement('td', { style: { textAlign: 'right' } },
                  isService ? React.createElement('span', { style: { color: 'var(--text-secondary)', fontSize: '0.75rem' } }, 'N/A')
                    : React.createElement('span', {
                      style: {
                        fontWeight: 700,
                        color: stock <= 0 ? '#ef4444' : (stock <= minLvl ? '#f59e0b' : 'inherit')
                      }
                    }, `${fmtNum(stock, 0)} ${p.unit || 'pcs'}`)
                ),
                React.createElement('td', { style: { textAlign: 'right', fontWeight: 600 } },
                  fmtCurrency(p.price)
                ),
                React.createElement('td', null,
                  React.createElement('span', { style: { fontSize: '0.8rem', color: 'var(--text-secondary)' } }, p.category || 'General')
                ),
                React.createElement('td', { style: { textAlign: 'center' } },
                  React.createElement('div', {
                    style: { display: 'inline-flex', gap: 6 },
                    onClick: (e) => e.stopPropagation()
                  },
                    React.createElement('button', {
                      className: 'btn btn-outline btn-xs',
                      style: { display: 'flex', alignItems: 'center', gap: 4 },
                      onClick: () => setSelectedProductId(p.id)
                    }, '📊 Insights'),
                    !isService && React.createElement('button', {
                      className: 'btn btn-primary btn-xs',
                      style: { display: 'flex', alignItems: 'center', gap: 4 },
                      onClick: () => handleUseRecommendation(p.id, 10)
                    }, '⚡ Reorder')
                  )
                )
              );
            })
          )
        )
      ),

      // Detail Drawer Modal
      selectedProductId && React.createElement(ProductDetailDrawer, {
        productId: selectedProductId,
        onClose: () => setSelectedProductId(null),
        onUseRecommendation: handleUseRecommendation
      })
    );
  }

  // ═══════════════════════════════════════════════════════════════════
  // 4. SMART REORDER VIEW (Inventory Subtab)
  // ═══════════════════════════════════════════════════════════════════
  // ═══════════════════════════════════════════════════════════════════
  // 4. SMART REORDER QUEUE (Forecast-Driven Replenishment & Multi-Vendor POs)
  // ═══════════════════════════════════════════════════════════════════
  // ═══════════════════════════════════════════════════════════════════
  // 4. SMART REORDER QUEUE (Unified Dual-Signal Replenishment & PO Workspace)
  // ═══════════════════════════════════════════════════════════════════
  function SmartReorderView({ onOpenInsights, onPrefillPo }) {
    const [recommendations, setRecommendations] = useState([]);
    const [loading, setLoading] = useState(true);
    const [urgencyFilter, setUrgencyFilter] = useState('ALL');
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [detailProductId, setDetailProductId] = useState(null);

    // Popover explanation dialog state
    const [infoModal, setInfoModal] = useState(null); // { type: 'FORECAST' | 'MIN_STOCK', item: rec }

    // Unified PO Creation Workspace State
    const [showOrderWorkspace, setShowOrderWorkspace] = useState(false);
    const [workspaceStep, setWorkspaceStep] = useState(1); // 1: Order Items & Configuration, 2: Review Orders by Vendor, 3: Completion Hub
    const [workspaceLoading, setWorkspaceLoading] = useState(false);
    const [orderExecuting, setOrderExecuting] = useState(false);

    // Catalog, Vendor & Category Data
    const [catalogProducts, setCatalogProducts] = useState([]);
    const [partiesList, setPartiesList] = useState([]);
    const [vendorHistory, setVendorHistory] = useState({});
    const [categoryList, setCategoryList] = useState([]);

    // Order Lines
    const [orderItems, setOrderItems] = useState([]);
    const [createdPos, setCreatedPos] = useState([]);

    // Product Search Autocomplete inside workspace
    const [searchQuery, setSearchQuery] = useState('');
    const [showSearchDropdown, setShowSearchDropdown] = useState(false);

    // PO Options
    const [expectedDeliveryDate, setExpectedDeliveryDate] = useState(
      typeof BillsoftUtils !== 'undefined' && BillsoftUtils.futureDateStr ? BillsoftUtils.futureDateStr(7) : ''
    );
    const [orderNotes, setOrderNotes] = useState('Replenishment purchase order generated from Smart Reorder Queue.');
    const [hidePricesOnPo, setHidePricesOnPo] = useState(false);

    // Sub-Modal 1: Add Vendor (z-index 10050)
    const [showAddVendorModal, setShowAddVendorModal] = useState(false);
    const [targetVendorRowKey, setTargetVendorRowKey] = useState(null);
    const [savingVendor, setSavingVendor] = useState(false);
    const [vendorForm, setVendorForm] = useState({
      name: '',
      contactPerson: '',
      phone: '',
      email: '',
      gstin: '',
      address: ''
    });

    // Sub-Modal 2: Create New Product (z-index 10050)
    const [showCreateProductModal, setShowCreateProductModal] = useState(false);
    const [savingProduct, setSavingProduct] = useState(false);
    const [productForm, setProductForm] = useState({
      name: '',
      sku: '',
      barcode: '',
      category: '',
      unit: 'pcs',
      costPrice: '',
      price: '',
      hsnCode: '',
      gstPercentage: '0',
      minStockLevel: '5',
      preferredPartyId: '',
      description: ''
    });

    // Session duplicate tracker
    const [recentlyOrderedMap, setRecentlyOrderedMap] = useState({});

    const [pageSize, setPageSize] = useState(4);
    const [totalElements, setTotalElements] = useState(0);

    // Load recommendations from backend
    const loadRecommendations = useCallback(() => {
      setLoading(true);
      API.forecast.getRecommendations({
        urgency: urgencyFilter,
        page: page,
        size: pageSize
      }).then(res => {
        const content = res.content || [];
        setRecommendations(content);
        setTotalPages(res.totalPages || Math.max(1, Math.ceil(content.length / pageSize)));
        setTotalElements(res.totalElements != null ? res.totalElements : content.length);
        setLoading(false);
      }).catch(err => {
        console.error('Failed to load replenishment recommendations:', err);
        setLoading(false);
      });
    }, [urgencyFilter, page, pageSize]);

    useEffect(() => {
      loadRecommendations();
    }, [loadRecommendations]);

    // Ensure table rows never exceed pageSize even if backend returned an unpaginated array
    const displayedRecommendations = useMemo(() => {
      if (!recommendations || recommendations.length === 0) return [];
      if (totalPages > 1 && recommendations.length <= pageSize) {
        return recommendations;
      }
      const start = page * pageSize;
      return recommendations.slice(start, start + pageSize);
    }, [recommendations, page, pageSize, totalPages]);

    const effectiveTotalPages = useMemo(() => {
      if (totalPages > 1) return totalPages;
      if (recommendations && recommendations.length > pageSize) {
        return Math.ceil(recommendations.length / pageSize);
      }
      return Math.max(1, totalPages);
    }, [totalPages, recommendations, pageSize]);

    const effectiveTotalCount = totalElements || (recommendations ? recommendations.length : 0);
    const safePage = Math.max(0, page);
    const safeTotalPages = Math.max(1, effectiveTotalPages);
    const pageStartIndex = safePage * pageSize;
    const pageEndIndex = pageStartIndex + displayedRecommendations.length;

    useEffect(() => {
      const handlePoUpdated = (evt) => {
        try {
          loadRecommendations();
        } catch (e) {}
      };
      window.addEventListener('billsoft:po-updated', handlePoUpdated);
      return () => {
        window.removeEventListener('billsoft:po-updated', handlePoUpdated);
      };
    }, [loadRecommendations]);

    // Check if item is actionable (has positive recommended quantity and not recently ordered in current session)
    const isItemActionable = (r) => {
      const rId = r.productId || r.id;
      if (recentlyOrderedMap[rId]) return false;
      const recQty = Number(r.recommendedOrderQty || 0);
      return recQty > 0;
    };

    // Helper to format Reason label
    const formatReasonLabel = (reason) => {
      switch (reason) {
        case 'FORECAST':
          return 'Forecast';
        case 'MINIMUM_STOCK':
          return 'Minimum Stock';
        case 'BOTH':
          return 'Forecast + Minimum Stock';
        case 'HEALTHY':
          return 'Healthy';
        default:
          return reason || 'Forecast';
      }
    };

    const getReasonBadgeStyle = (reason) => {
      switch (reason) {
        case 'FORECAST':
          return { background: 'rgba(99, 102, 241, 0.12)', color: '#4f46e5', border: '1px solid rgba(99, 102, 241, 0.25)' };
        case 'MINIMUM_STOCK':
          return { background: 'rgba(245, 158, 11, 0.12)', color: '#d97706', border: '1px solid rgba(245, 158, 11, 0.25)' };
        case 'BOTH':
          return { background: 'rgba(16, 185, 129, 0.12)', color: '#059669', border: '1px solid rgba(16, 185, 129, 0.25)' };
        case 'HEALTHY':
        default:
          return { background: 'rgba(107, 114, 128, 0.12)', color: '#4b5563', border: '1px solid rgba(107, 114, 128, 0.2)' };
      }
    };

    // Helper to resolve vendor for a product (Product preferred vendor -> Vendor History -> Unassigned)
    const resolveVendorForProduct = (prodId, prod, parties, histMap) => {
      let resolvedPartyId = null;
      let provenance = 'UNASSIGNED';
      let provenanceDetail = 'No historical vendor found';

      if (prod && prod.preferredPartyId && parties.some(v => v.id === prod.preferredPartyId)) {
        resolvedPartyId = prod.preferredPartyId;
        provenance = 'PREFERRED';
        const vObj = parties.find(v => v.id === prod.preferredPartyId);
        provenanceDetail = `Preferred: ${vObj ? vObj.name : ''}`;
      } else if (histMap && histMap[prodId] && parties.some(v => v.id === histMap[prodId].partyId)) {
        resolvedPartyId = histMap[prodId].partyId;
        provenance = 'SMART';
        const vh = histMap[prodId];
        provenanceDetail = `From ${vh.lastPoNumber || 'past order'}${vh.lastPoDate ? ' (' + (typeof BillsoftUtils !== 'undefined' ? BillsoftUtils.formatDate(vh.lastPoDate) : vh.lastPoDate) + ')' : ''}`;
      }

      return { partyId: resolvedPartyId, provenance, provenanceDetail };
    };

    // Open Unified PO Creation Workspace
    const handleOpenOrderWorkspace = async (prefillRec = null) => {
      setWorkspaceStep(1);
      setWorkspaceLoading(true);
      setShowOrderWorkspace(true);
      setSearchQuery('');
      setShowSearchDropdown(false);

      try {
        const [prods, parts, history] = await Promise.all([
          API.products.list(),
          API.parties.list(),
          API.purchaseOrders.getVendorHistory().catch(() => ({}))
        ]);

        const prodList = prods || [];
        const partyList = parts || [];
        const histMap = history || {};

        setCatalogProducts(prodList);
        setPartiesList(partyList);
        setVendorHistory(histMap);

        // Derive unique categories for new product creation
        const cats = Array.from(new Set(prodList.map(p => p.category).filter(Boolean))).sort();
        setCategoryList(cats);

        // If opened with a specific recommendation prefill
        if (prefillRec) {
          const pId = prefillRec.productId || prefillRec.id;
          const matchedProd = prodList.find(p => p.id === pId) || {};
          const { partyId, provenance, provenanceDetail } = resolveVendorForProduct(pId, matchedProd, partyList, histMap);
          const orderQty = prefillRec.recommendedOrderQty && Number(prefillRec.recommendedOrderQty) > 0
            ? Math.max(1, Math.round(Number(prefillRec.recommendedOrderQty)))
            : 1;
          const unitCost = matchedProd.costPrice != null && Number(matchedProd.costPrice) >= 0
            ? Number(matchedProd.costPrice)
            : (prefillRec.unitCost != null && Number(prefillRec.unitCost) >= 0 ? Number(prefillRec.unitCost) : 0);

          setOrderItems([{
            _key: 'item_' + pId + '_' + Date.now(),
            productId: pId,
            name: prefillRec.productName || matchedProd.name || `Product #${pId}`,
            sku: prefillRec.sku || matchedProd.sku || '',
            unit: prefillRec.unit || matchedProd.unit || 'pcs',
            orderQty: orderQty,
            unitCost: unitCost,
            gstPercentage: matchedProd.gstPercentage != null ? Number(matchedProd.gstPercentage) : 0,
            partyId: partyId,
            provenance: provenance,
            provenanceDetail: provenanceDetail,
            source: prefillRec.recommendationReason || 'FORECAST'
          }]);
        } else {
          setOrderItems([]);
        }
      } catch (err) {
        console.error('Failed to load catalog and vendor data:', err);
        if (typeof showToast === 'function') showToast('Failed to load catalog and vendor data', 'error');
      } finally {
        setWorkspaceLoading(false);
      }
    };

    // Add a recommendation to current order items
    const handleAddRecommendationToOrder = (rec) => {
      const pId = rec.productId || rec.id;
      const existing = orderItems.find(it => it.productId === pId);
      if (existing) {
        updateOrderItem(existing._key, { orderQty: Number(existing.orderQty || 1) + 1 });
        if (typeof showToast === 'function') showToast(`Increased quantity for ${rec.productName}`, 'info');
        return;
      }

      const matchedProd = catalogProducts.find(p => p.id === pId) || {};
      const { partyId, provenance, provenanceDetail } = resolveVendorForProduct(pId, matchedProd, partiesList, vendorHistory);
      const orderQty = rec.recommendedOrderQty && Number(rec.recommendedOrderQty) > 0
        ? Math.max(1, Math.round(Number(rec.recommendedOrderQty)))
        : 1;
      const unitCost = matchedProd.costPrice != null && Number(matchedProd.costPrice) >= 0
        ? Number(matchedProd.costPrice)
        : (rec.unitCost != null && Number(rec.unitCost) >= 0 ? Number(rec.unitCost) : 0);

      const newItem = {
        _key: 'item_' + pId + '_' + Date.now(),
        productId: pId,
        name: rec.productName || matchedProd.name || `Product #${pId}`,
        sku: rec.sku || matchedProd.sku || '',
        unit: rec.unit || matchedProd.unit || 'pcs',
        orderQty: orderQty,
        unitCost: unitCost,
        gstPercentage: matchedProd.gstPercentage != null ? Number(matchedProd.gstPercentage) : 0,
        partyId: partyId,
        provenance: provenance,
        provenanceDetail: provenanceDetail,
        source: rec.recommendationReason || 'FORECAST'
      };

      setOrderItems(prev => [...prev, newItem]);
      if (typeof showToast === 'function') showToast(`Added "${rec.productName}" to order`, 'success');
    };

    // Add an existing catalog product manually
    const handleAddCatalogProduct = (prod) => {
      if (!prod) return;
      const pId = prod.id;
      const existing = orderItems.find(it => it.productId === pId);
      if (existing) {
        updateOrderItem(existing._key, { orderQty: Number(existing.orderQty || 1) + 1 });
        if (typeof showToast === 'function') showToast(`Increased quantity for ${prod.name}`, 'info');
        setSearchQuery('');
        setShowSearchDropdown(false);
        return;
      }

      const rec = recommendations.find(r => (r.productId || r.id) === pId);
      const { partyId, provenance, provenanceDetail } = resolveVendorForProduct(pId, prod, partiesList, vendorHistory);

      let orderQty = 1;
      let source = 'MANUAL';
      if (rec && Number(rec.recommendedOrderQty || 0) > 0) {
        orderQty = Math.max(1, Math.round(Number(rec.recommendedOrderQty)));
        source = rec.recommendationReason || 'FORECAST';
      }

      const unitCost = prod.costPrice != null && Number(prod.costPrice) >= 0
        ? Number(prod.costPrice)
        : 0;

      const newItem = {
        _key: 'item_' + pId + '_' + Date.now(),
        productId: pId,
        name: prod.name,
        sku: prod.sku || '',
        unit: prod.unit || 'pcs',
        orderQty: orderQty,
        unitCost: unitCost,
        gstPercentage: prod.gstPercentage != null ? Number(prod.gstPercentage) : 0,
        partyId: partyId,
        provenance: provenance,
        provenanceDetail: provenanceDetail,
        source: source
      };

      setOrderItems(prev => [...prev, newItem]);
      setSearchQuery('');
      setShowSearchDropdown(false);
      if (typeof showToast === 'function') showToast(`Added "${prod.name}" to order`, 'success');
    };

    const updateOrderItem = (itemKey, updates) => {
      setOrderItems(prev => prev.map(it => it._key === itemKey ? { ...it, ...updates } : it));
    };

    const removeOrderItem = (itemKey) => {
      setOrderItems(prev => prev.filter(it => it._key !== itemKey));
    };

    const handleVendorChange = (itemKey, partyIdStr) => {
      const parsedId = partyIdStr ? parseInt(partyIdStr, 10) : null;
      const vObj = partiesList.find(p => p.id === parsedId);
      updateOrderItem(itemKey, {
        partyId: parsedId,
        provenance: parsedId ? 'USER' : 'UNASSIGNED',
        provenanceDetail: parsedId ? `Assigned: ${vObj ? vObj.name : ''}` : 'No vendor assigned'
      });
    };

    // Filter catalog products for search autocomplete
    const filteredCatalog = useMemo(() => {
      if (!searchQuery.trim()) return catalogProducts.slice(0, 8);
      const q = searchQuery.toLowerCase().trim();
      return catalogProducts.filter(p =>
        (p.name || '').toLowerCase().includes(q) ||
        (p.sku || '').toLowerCase().includes(q) ||
        (p.barcode || '').toLowerCase().includes(q) ||
        (p.category || '').toLowerCase().includes(q)
      ).slice(0, 10);
    }, [catalogProducts, searchQuery]);

    // Recommendations available to add that are not already in orderItems
    const availableRecommendations = useMemo(() => {
      const addedIds = new Set(orderItems.map(it => it.productId));
      return recommendations.filter(r => {
        const rId = r.productId || r.id;
        return isItemActionable(r) && !addedIds.has(rId);
      });
    }, [recommendations, orderItems, recentlyOrderedMap]);

    // Group order items by vendor for Review and Generation
    const groupedVendorOrders = useMemo(() => {
      const groups = {};
      orderItems.forEach(it => {
        const pId = it.partyId || 'UNASSIGNED';
        if (!groups[pId]) {
          const partyObj = partiesList.find(p => p.id === it.partyId) || null;
          groups[pId] = {
            partyId: it.partyId,
            party: partyObj,
            partyName: partyObj ? partyObj.name : 'Unassigned Vendor',
            items: [],
            subtotal: 0,
            totalGst: 0,
            grandTotal: 0
          };
        }
        const qty = Number(it.orderQty) || 0;
        const cost = Number(it.unitCost) || 0;
        const gstPct = Number(it.gstPercentage) || 0;
        const lineTaxable = qty * cost;
        const lineGst = (lineTaxable * gstPct) / 100;
        const lineTotal = lineTaxable + lineGst;

        groups[pId].items.push({ ...it, lineTaxable, lineGst, lineTotal });
        groups[pId].subtotal += lineTaxable;
        groups[pId].totalGst += lineGst;
        groups[pId].grandTotal += lineTotal;
      });
      return Object.values(groups);
    }, [orderItems, partiesList]);

    const unassignedItems = useMemo(() => {
      return orderItems.filter(it => !it.partyId);
    }, [orderItems]);

    const totalOrderAmount = useMemo(() => {
      return groupedVendorOrders.reduce((sum, g) => sum + g.grandTotal, 0);
    }, [groupedVendorOrders]);

    // Proceed from Items to Review Step with validation
    const handleProceedToReview = () => {
      if (orderItems.length === 0) {
        if (typeof showToast === 'function') showToast('Please add at least one product to the order', 'error');
        return;
      }
      if (unassignedItems.length > 0) {
        if (typeof showToast === 'function') {
          showToast(`${unassignedItems.length} product(s) need a vendor before purchase orders can be created.`, 'error');
        }
        return;
      }
      // Check for zero or negative quantities
      const invalidQty = orderItems.find(it => !it.orderQty || Number(it.orderQty) <= 0);
      if (invalidQty) {
        if (typeof showToast === 'function') showToast(`Please specify a valid quantity for "${invalidQty.name}"`, 'error');
        return;
      }
      setWorkspaceStep(2);
    };

    // Execute Multi-Vendor Batch PO Creation
    const handleExecuteBatchPo = async () => {
      if (orderExecuting) return;
      const validVendors = groupedVendorOrders.filter(g => g.partyId);
      if (validVendors.length === 0) {
        if (typeof showToast === 'function') showToast('No assigned vendors found.', 'error');
        return;
      }

      setOrderExecuting(true);
      try {
        const clientRequestId = 'req_smart_queue_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
        const todayStr = (typeof BillsoftUtils !== 'undefined' && BillsoftUtils.todayStr)
          ? BillsoftUtils.todayStr()
          : new Date().toISOString().split('T')[0];

        const payload = {
          clientRequestId: clientRequestId,
          hidePricesOnPo: hidePricesOnPo,
          expectedDeliveryDate: expectedDeliveryDate || null,
          notes: orderNotes || 'Replenishment order from Smart Reorder Queue.',
          orders: validVendors.map(g => ({
            party: { id: g.partyId },
            partyId: g.partyId,
            poDate: todayStr,
            expectedDeliveryDate: expectedDeliveryDate || null,
            notes: orderNotes || 'Replenishment order from Smart Reorder Queue.',
            hidePricesOnPo: hidePricesOnPo,
            status: 'ISSUED',
            items: g.items.map(it => ({
              productId: it.productId,
              productName: it.name,
              description: '',
              hsnCode: it.sku || '',
              quantity: Number(it.orderQty) || 1,
              unit: it.unit || 'pcs',
              unitPrice: Number(it.unitCost) || 0,
              discountValue: 0,
              gstPercent: Number(it.gstPercentage) || 0
            }))
          })),
          updatePreferredVendors: []
        };

        const res = await API.purchaseOrders.createBatch(payload);
        const posList = Array.isArray(res) ? res : [res];
        setCreatedPos(posList);
        setWorkspaceStep(3);

        // Update in-session duplicate prevention map
        const newOrderedMap = { ...recentlyOrderedMap };
        orderItems.forEach(it => {
          if (it.productId) {
            newOrderedMap[it.productId] = {
              orderQty: it.orderQty,
              poNumbers: posList.map(p => p.poNumber).filter(Boolean).join(', '),
              timestamp: Date.now()
            };
          }
        });
        setRecentlyOrderedMap(newOrderedMap);

        if (typeof showToast === 'function') {
          showToast(`Successfully created ${posList.length} Purchase Order(s) across ${validVendors.length} vendor(s)!`, 'success');
        }

        // Sync forecast and refresh queue
        if (API.forecast && API.forecast.sync) {
          API.forecast.sync().then(() => loadRecommendations()).catch(() => loadRecommendations());
        } else {
          loadRecommendations();
        }
        window.dispatchEvent(new CustomEvent('billsoft:app-refresh'));
      } catch (err) {
        console.error('Batch PO execution failed:', err);
        if (typeof showToast === 'function') {
          showToast(err.message || 'Failed to create purchase orders', 'error');
        }
      } finally {
        setOrderExecuting(false);
      }
    };

    // Sub-Modal Handler: Quick Create Vendor
    const handleSaveNewVendor = async () => {
      if (!vendorForm.name.trim()) {
        if (typeof showToast === 'function') showToast('Vendor Name is required', 'error');
        return;
      }
      setSavingVendor(true);
      try {
        const body = {
          name: vendorForm.name.trim(),
          contactPerson: vendorForm.contactPerson || null,
          phone: vendorForm.phone || null,
          email: vendorForm.email || null,
          gstin: vendorForm.gstin || null,
          address: vendorForm.address || null,
          type: 'SUPPLIER'
        };
        const created = await API.parties.create(body);
        setPartiesList(prev => [created, ...prev]);

        if (targetVendorRowKey) {
          updateOrderItem(targetVendorRowKey, {
            partyId: created.id,
            provenance: 'USER',
            provenanceDetail: `Assigned newly created vendor: ${created.name}`
          });
        }
        setShowAddVendorModal(false);
        setVendorForm({ name: '', contactPerson: '', phone: '', email: '', gstin: '', address: '' });
        setTargetVendorRowKey(null);
        if (typeof showToast === 'function') showToast(`Created vendor "${created.name}"`, 'success');
      } catch (e) {
        if (typeof showToast === 'function') showToast(e.message || 'Failed to create vendor', 'error');
      } finally {
        setSavingVendor(false);
      }
    };

    // Sub-Modal Handler: Create New Real Product
    const handleSaveNewProduct = async () => {
      if (!productForm.name.trim()) {
        if (typeof showToast === 'function') showToast('Product Name is required', 'error');
        return;
      }
      if (!productForm.category.trim()) {
        if (typeof showToast === 'function') showToast('Category is required', 'error');
        return;
      }
      if (productForm.costPrice === '' || isNaN(Number(productForm.costPrice)) || Number(productForm.costPrice) < 0) {
        if (typeof showToast === 'function') showToast('Valid Purchase Cost is required', 'error');
        return;
      }

      setSavingProduct(true);
      try {
        const payload = {
          name: productForm.name.trim(),
          sku: productForm.sku ? productForm.sku.trim() : null,
          barcode: productForm.barcode ? productForm.barcode.trim() : null,
          category: productForm.category.trim(),
          unit: productForm.unit || 'pcs',
          costPrice: parseFloat(productForm.costPrice) || 0,
          price: productForm.price !== '' ? parseFloat(productForm.price) : (parseFloat(productForm.costPrice) || 0),
          hsnCode: productForm.hsnCode ? productForm.hsnCode.trim() : null,
          gstPercentage: parseFloat(productForm.gstPercentage) || 0,
          minStockLevel: productForm.minStockLevel !== '' ? parseInt(productForm.minStockLevel, 10) : 5,
          preferredPartyId: productForm.preferredPartyId ? parseInt(productForm.preferredPartyId, 10) : null,
          description: productForm.description ? productForm.description.trim() : null,
          stockQuantity: 0
        };

        const created = await API.products.create(payload);
        setCatalogProducts(prev => [created, ...prev]);

        // Add category to list if new
        if (!categoryList.includes(created.category)) {
          setCategoryList(prev => [...prev, created.category].sort());
        }

        // Automatically add to current order items
        const { partyId, provenance, provenanceDetail } = resolveVendorForProduct(created.id, created, partiesList, vendorHistory);
        const newItem = {
          _key: 'item_' + created.id + '_' + Date.now(),
          productId: created.id,
          name: created.name,
          sku: created.sku || '',
          unit: created.unit || 'pcs',
          orderQty: 1,
          unitCost: created.costPrice != null ? Number(created.costPrice) : 0,
          gstPercentage: created.gstPercentage != null ? Number(created.gstPercentage) : 0,
          partyId: partyId,
          provenance: provenance,
          provenanceDetail: provenanceDetail,
          source: 'MANUAL'
        };

        setOrderItems(prev => [...prev, newItem]);
        setShowCreateProductModal(false);
        setProductForm({
          name: '',
          sku: '',
          barcode: '',
          category: '',
          unit: 'pcs',
          costPrice: '',
          price: '',
          hsnCode: '',
          gstPercentage: '0',
          minStockLevel: '5',
          preferredPartyId: '',
          description: ''
        });

        if (typeof showToast === 'function') {
          showToast(`Created product "${created.name}" and added to order`, 'success');
        }
      } catch (e) {
        if (typeof showToast === 'function') showToast(e.message || 'Failed to create product', 'error');
      } finally {
        setSavingProduct(false);
      }
    };

    // Print and Download Handlers for Step 3
    const handlePrintAllPos = () => {
      if (createdPos.length === 0) return;
      const printWindow = window.open('', '_blank');
      if (!printWindow) {
        if (typeof showToast === 'function') showToast('Popup blocked. Please allow popups to print.', 'error');
        return;
      }

      printWindow.document.write(`
        <!DOCTYPE html>
        <html>
        <head>
          <title>Purchase Orders</title>
          <style>
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; margin: 0; padding: 20px; color: #111; }
            .po-container { page-break-after: always; padding: 20px; border: 1px solid #ddd; border-radius: 8px; margin-bottom: 30px; }
            .po-container:last-child { page-break-after: auto; }
            .po-header { display: flex; justify-content: space-between; border-bottom: 2px solid #333; padding-bottom: 12px; margin-bottom: 15px; }
            table { width: 100%; border-collapse: collapse; margin-top: 15px; }
            th, td { border: 1px solid #ddd; padding: 8px; text-align: left; font-size: 13px; }
            th { background-color: #f3f4f6; }
            .text-right { text-align: right; }
            @media print {
              body { padding: 0; }
              .po-container { border: none; margin: 0; padding: 0; }
            }
          </style>
        </head>
        <body>
          ${createdPos.map(po => `
            <div class="po-container">
              <div class="po-header">
                <div>
                  <h2 style="margin:0 0 4px 0;">PURCHASE ORDER</h2>
                  <div style="font-size:14px;font-weight:bold;color:#4f46e5;">${po.poNumber || 'PO'}</div>
                  <div style="font-size:12px;color:#666;">Date: ${typeof BillsoftUtils !== 'undefined' ? BillsoftUtils.formatDate(po.poDate) : (po.poDate || '')}</div>
                </div>
                <div style="text-align:right;">
                  <div style="font-size:12px;color:#666;">Vendor / Supplier</div>
                  <h3 style="margin:2px 0 0 0;">${po.partyName || (po.party ? po.party.name : '—')}</h3>
                  ${po.partyGstin ? `<div style="font-size:12px;">GSTIN: ${po.partyGstin}</div>` : ''}
                </div>
              </div>
              <table>
                <thead>
                  <tr>
                    <th style="width:40px;">#</th>
                    <th>Item & Description</th>
                    <th style="width:70px;text-align:center;">Qty</th>
                    <th style="width:70px;text-align:center;">Unit</th>
                    ${!po.hidePricesOnPo ? `
                      <th style="width:100px;text-align:right;">Unit Price</th>
                      <th style="width:70px;text-align:center;">GST %</th>
                      <th style="width:110px;text-align:right;">Total</th>
                    ` : ''}
                  </tr>
                </thead>
                <tbody>
                  ${(po.items || []).map((it, idx) => `
                    <tr>
                      <td>${idx + 1}</td>
                      <td><strong>${it.productName || (it.product ? it.product.name : '')}</strong></td>
                      <td style="text-align:center;">${it.quantity}</td>
                      <td style="text-align:center;">${it.unit || 'pcs'}</td>
                      ${!po.hidePricesOnPo ? `
                        <td style="text-align:right;">${typeof BillsoftUtils !== 'undefined' ? BillsoftUtils.formatCurrency(it.unitPrice || 0) : it.unitPrice}</td>
                        <td style="text-align:center;">${it.gstPercent || 0}%</td>
                        <td style="text-align:right;">${typeof BillsoftUtils !== 'undefined' ? BillsoftUtils.formatCurrency(it.totalAmount || 0) : it.totalAmount}</td>
                      ` : ''}
                    </tr>
                  `).join('')}
                </tbody>
              </table>
              ${!po.hidePricesOnPo ? `
                <div style="margin-top:15px;display:flex;justify-content:flex-end;">
                  <table style="width:260px;margin:0;">
                    <tr><td style="border:none;"><strong>Grand Total:</strong></td><td style="border:none;text-align:right;font-size:15px;font-weight:bold;color:#4f46e5;">${typeof BillsoftUtils !== 'undefined' ? BillsoftUtils.formatCurrency(po.totalAmount || 0) : po.totalAmount}</td></tr>
                  </table>
                </div>
              ` : '<div style="margin-top:15px;font-size:12px;color:#666;font-style:italic;">* Quantities only. Pricing columns omitted.</div>'}
            </div>
          `).join('')}
        </body>
        </html>
      `);
      printWindow.document.close();
      printWindow.focus();
      setTimeout(() => {
        printWindow.print();
      }, 500);
    };

    const handleDownloadCombinedPdf = async () => {
      const ids = createdPos.map(p => p.id);
      if (ids.length === 0) return;
      try {
        if (API.purchaseOrders && API.purchaseOrders.downloadBatchPdf) {
          const blob = await API.purchaseOrders.downloadBatchPdf(ids);
          if (typeof BillsoftUtils !== 'undefined' && BillsoftUtils.downloadBlob) {
            BillsoftUtils.downloadBlob(blob, `Combined_POs_${ids.length}_vendors.pdf`);
          }
        }
      } catch (err) {
        console.error('Failed to download combined PO PDF:', err);
        if (typeof showToast === 'function') showToast('Failed to download combined PDF', 'error');
      }
    };

    const handleDownloadZip = async () => {
      const ids = createdPos.map(p => p.id);
      if (ids.length === 0) return;
      try {
        if (API.purchaseOrders && API.purchaseOrders.downloadBatchZip) {
          const blob = await API.purchaseOrders.downloadBatchZip(ids);
          if (typeof BillsoftUtils !== 'undefined' && BillsoftUtils.downloadBlob) {
            BillsoftUtils.downloadBlob(blob, `PurchaseOrders_Bundle_${ids.length}_vendors.zip`);
          }
        }
      } catch (err) {
        console.error('Failed to download PO ZIP bundle:', err);
        if (typeof showToast === 'function') showToast('Failed to download ZIP bundle', 'error');
      }
    };

    const handleDownloadSinglePoPdf = async (po) => {
      if (!po || !po.id) return;
      try {
        if (API.purchaseOrders && API.purchaseOrders.downloadPdf) {
          const blob = await API.purchaseOrders.downloadPdf(po.id);
          if (typeof BillsoftUtils !== 'undefined' && BillsoftUtils.downloadBlob) {
            BillsoftUtils.downloadBlob(blob, `${po.poNumber || 'PO'}.pdf`);
          }
        }
      } catch (err) {
        console.error('Failed to download PO PDF:', err);
        if (typeof showToast === 'function') showToast('Failed to download PO PDF', 'error');
      }
    };

    // Keyboard navigation helper for Invoice-style table cells
    const handleCellKeyDown = (e, rowIndex, fieldName) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        const nextRow = orderItems[rowIndex + 1];
        if (nextRow) {
          const nextInput = document.getElementById(`input_${fieldName}_${rowIndex + 1}`);
          if (nextInput) nextInput.focus();
        }
      }
    };

    return React.createElement('div', { style: { display: 'flex', flexDirection: 'column', gap: 10 } },
      // ═══════════════════════════════════════════════════════════════════
      // QUEUE HEADER & COMPACT PRIMARY ACTION
      // ═══════════════════════════════════════════════════════════════════
      React.createElement('div', {
        className: 'card',
        style: {
          padding: '6px 14px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: 8,
          marginBottom: 8,
          background: 'var(--bg-card)',
          border: '1px solid var(--border)',
          borderRadius: 8
        }
      },
        React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
          React.createElement('span', { style: { fontSize: '0.95rem', fontWeight: 700, color: 'var(--text-primary)' } }, '⚡ Smart Reorder Queue'),
          React.createElement('span', {
            style: {
              fontSize: '0.74rem',
              color: 'var(--text-secondary)',
              borderLeft: '1px solid var(--border)',
              paddingLeft: 8
            }
          }, 'Replenishment advisory')
        ),

        // Action Group: Urgency Filters & Single Primary Action [ Order Products ]
        React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' } },
          React.createElement('div', { className: 'btn-group', style: { display: 'flex', gap: 4 } },
            [
              { id: 'ALL', label: 'All' },
              { id: 'OUT_OF_STOCK', label: 'Out of Stock' },
              { id: 'CRITICAL', label: 'Critical' },
              { id: 'REORDER_NEEDED', label: 'Reorder Needed' },
              { id: 'HEALTHY', label: 'Healthy' }
            ].map(f =>
              React.createElement('button', {
                key: f.id,
                className: `btn btn-xs ${urgencyFilter === f.id ? 'btn-primary' : 'btn-outline'}`,
                style: { padding: '3px 9px', fontSize: '0.76rem' },
                onClick: () => { setUrgencyFilter(f.id); setPage(0); }
              }, f.label)
            )
          ),

          // Single Unified Primary Action
          React.createElement('button', {
            id: 'btn-order-products',
            className: 'btn btn-primary',
            style: {
              fontWeight: 700,
              padding: '5px 14px',
              fontSize: '0.82rem',
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              boxShadow: '0 1px 4px rgba(79, 70, 229, 0.2)'
            },
            onClick: () => handleOpenOrderWorkspace(null)
          }, 'Order Products')
        )
      ),

      // ═══════════════════════════════════════════════════════════════════
      // RECOMMENDATIONS TABLE (6-Column Coalesced Responsive Layout)
      // ═══════════════════════════════════════════════════════════════════
      React.createElement('div', {
        className: 'card firm-table-card',
        style: {
          padding: 0,
          width: '100%',
          boxSizing: 'border-box',
          border: '1px solid var(--border)',
          borderRadius: 10,
          background: 'var(--bg-card)',
          display: 'flex',
          flexDirection: 'column'
        }
      },
        React.createElement('div', {
          className: 'table-container',
          style: {
            overflowX: 'auto',
            width: '100%'
          }
        },
          React.createElement('table', { className: 'data-table', style: { width: '100%', margin: 0, borderCollapse: 'collapse' } },
            React.createElement('thead', null,
              React.createElement('tr', null,
                React.createElement('th', { style: { padding: '8px 12px', width: '25%' } }, 'Product / Identity'),
                React.createElement('th', { style: { textAlign: 'right', width: '13%' } }, 'Stock / Min'),
                React.createElement('th', { style: { textAlign: 'right', width: '11%' } }, 'Incoming PO'),
                React.createElement('th', { style: { textAlign: 'right', width: '20%' } }, 'Forecast / Threshold'),
                React.createElement('th', { style: { textAlign: 'right', width: '13%' } }, 'Final Rec'),
                React.createElement('th', { style: { textAlign: 'center', width: '18%', paddingRight: 12 } }, 'Action / Status')
              )
            ),
            React.createElement('tbody', null,
              loading ? React.createElement('tr', null,
                React.createElement('td', { colSpan: 6, style: { textAlign: 'center', padding: '24px 0', color: 'var(--text-secondary)' } },
                  'Loading replenishment recommendations...'
                )
              ) : displayedRecommendations.length === 0 ? React.createElement('tr', null,
                React.createElement('td', { colSpan: 6, style: { textAlign: 'center', padding: '24px 0', color: 'var(--text-secondary)' } },
                  'No products require replenishment under the selected filter.'
                )
              ) : displayedRecommendations.map(r => {
                const rId = r.productId || r.id;
                const isRecentlyOrdered = !!recentlyOrderedMap[rId];
                const actionable = isItemActionable(r);
                const urgencyClass = `badge-urgency-${(r.urgencyStatus || 'HEALTHY').toLowerCase().replace(/_/g, '-')}`;
                const reasonText = formatReasonLabel(r.recommendationReason);
                const reasonStyle = getReasonBadgeStyle(r.recommendationReason);

                const currentPhysical = r.currentStock != null ? Number(r.currentStock) : 0;
                const incomingPo = r.incomingStock != null ? Number(r.incomingStock) : 0;
                const minStock = r.minStockLevel != null ? Number(r.minStockLevel) : 0;
                const forecastRec = r.forecastRecommendedQty != null ? Number(r.forecastRecommendedQty) : 0;
                const threshReq = r.thresholdRequirement != null ? Number(r.thresholdRequirement) : Math.max(0, minStock - (currentPhysical + incomingPo));
                const finalRec = r.recommendedOrderQty != null ? Number(r.recommendedOrderQty) : Math.max(forecastRec, threshReq);

                return React.createElement('tr', {
                  key: r.id || r.productId,
                  style: {
                    background: isRecentlyOrdered ? 'rgba(16, 185, 129, 0.04)' : 'transparent',
                    opacity: !actionable && !isRecentlyOrdered ? 0.8 : 1
                  }
                },
                  // Column 1: Product / Identity (Name + SKU/ID + PO ISSUED badge)
                  React.createElement('td', { style: { padding: '5px 10px' } },
                    React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' } },
                      React.createElement('span', { style: { fontWeight: 600, color: 'var(--text-primary)', wordBreak: 'break-word', fontSize: '0.84rem', lineHeight: 1.2 } }, r.productName),
                      isRecentlyOrdered && React.createElement('span', {
                        className: 'badge',
                        style: { background: 'rgba(16, 185, 129, 0.15)', color: '#10B981', fontSize: '0.65rem', fontWeight: 700, padding: '0 4px' }
                      }, 'PO ISSUED')
                    ),
                    React.createElement('div', { style: { fontSize: '0.7rem', color: 'var(--text-secondary)', marginTop: 1 } },
                      r.sku ? `SKU: ${r.sku}` : `ID: #${r.productId}`
                    )
                  ),
                  // Column 2: Current Physical Stock & Min Stock
                  React.createElement('td', { style: { textAlign: 'right', padding: '5px 8px' } },
                    React.createElement('div', { style: { fontWeight: 700, color: 'var(--text-primary)', fontSize: '0.84rem', lineHeight: 1.2 } },
                      `${fmtNum(currentPhysical, 0)} ${r.unit || 'pcs'}`
                    ),
                    React.createElement('div', { style: { fontSize: '0.68rem', color: 'var(--text-secondary)', marginTop: 1 } },
                      `Min: ${fmtNum(minStock, 0)}`
                    )
                  ),
                  // Column 3: Incoming ISSUED PO Stock
                  React.createElement('td', { style: { textAlign: 'right', padding: '5px 8px', fontWeight: 600, fontSize: '0.84rem', color: incomingPo > 0 ? '#6366f1' : 'var(--text-secondary)' } },
                    fmtNum(incomingPo, 0)
                  ),
                  // Column 4: Forecast Recommended & Threshold Required with (i) controls
                  React.createElement('td', { style: { textAlign: 'right', padding: '5px 8px' } },
                    React.createElement('div', { style: { display: 'flex', flexDirection: 'column', gap: 1, alignItems: 'flex-end' } },
                      React.createElement('div', { style: { display: 'inline-flex', alignItems: 'center', gap: 3 } },
                        React.createElement('span', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)' } }, 'FC:'),
                        React.createElement('span', { style: { fontWeight: 600, fontSize: '0.78rem', color: '#4f46e5' } }, fmtNum(forecastRec, 0)),
                        React.createElement('button', {
                          className: 'btn-icon',
                          style: { background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer', padding: 0, fontSize: '0.68rem', lineHeight: 1 },
                          title: 'Forecast explanation',
                          onClick: () => setInfoModal({ type: 'FORECAST', item: r })
                        }, 'ℹ️')
                      ),
                      React.createElement('div', { style: { display: 'inline-flex', alignItems: 'center', gap: 3 } },
                        React.createElement('span', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)' } }, 'Min:'),
                        React.createElement('span', { style: { fontWeight: 600, fontSize: '0.78rem', color: '#d97706' } }, fmtNum(threshReq, 0)),
                        React.createElement('button', {
                          className: 'btn-icon',
                          style: { background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer', padding: 0, fontSize: '0.68rem', lineHeight: 1 },
                          title: 'Minimum stock explanation',
                          onClick: () => setInfoModal({ type: 'MIN_STOCK', item: r })
                        }, 'ℹ️')
                      )
                    )
                  ),
                  // Column 5: Final Recommended
                  React.createElement('td', { style: { textAlign: 'right', padding: '5px 8px', fontWeight: 800, color: '#4f46e5', fontSize: '0.86rem' } },
                    `${fmtNum(finalRec, 0)} ${r.unit || 'pcs'}`
                  ),
                  // Column 6: Action & Status (Urgency + Reason + Insights & + Order CTAs)
                  React.createElement('td', { style: { textAlign: 'center', padding: '5px 10px' } },
                    React.createElement('div', { style: { display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 } },
                      React.createElement('div', { style: { display: 'inline-flex', gap: 4 } },
                        React.createElement('button', {
                          className: 'btn btn-outline btn-xs',
                          style: { padding: '1px 7px', fontSize: '0.72rem', height: 22, minHeight: 22, lineHeight: '18px' },
                          onClick: () => {
                            if (onOpenInsights) onOpenInsights(r.productId);
                            else setDetailProductId(r.productId);
                          }
                        }, 'Insights'),
                        React.createElement('button', {
                          className: 'btn btn-primary btn-xs',
                          style: { fontWeight: 700, padding: '1px 8px', fontSize: '0.72rem', height: 22, minHeight: 22, lineHeight: '18px' },
                          disabled: isRecentlyOrdered || !actionable,
                          onClick: () => handleOpenOrderWorkspace(r)
                        }, isRecentlyOrdered ? 'Ordered' : '+ Order')
                      ),
                      React.createElement('div', { style: { display: 'inline-flex', gap: 3, alignItems: 'center' } },
                        React.createElement('span', { className: urgencyClass, style: { fontSize: '0.62rem', padding: '0 4px', lineHeight: '14px' } }, r.urgencyStatus || 'HEALTHY'),
                        React.createElement('span', {
                          style: {
                            fontSize: '0.62rem',
                            fontWeight: 700,
                            padding: '0 4px',
                            borderRadius: 3,
                            whiteSpace: 'nowrap',
                            lineHeight: '14px',
                            ...reasonStyle
                          }
                        }, reasonText)
                      )
                    )
                  )
                );
              })
            )
          )
        ),
        // Docked Pagination Footer for Smart Reorder Queue
        React.createElement('div', { className: 'firm-docked-pagination' },
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 10 } },
            React.createElement('span', null,
              effectiveTotalCount > 0
                ? `Showing ${pageStartIndex + 1}–${Math.min(pageEndIndex, effectiveTotalCount)} of ${effectiveTotalCount} recommendations (Page ${safePage + 1} of ${safeTotalPages})`
                : 'No replenishment items'
            ),
            React.createElement('div', { style: { display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: '0.76rem', color: 'var(--text-secondary)' } },
              React.createElement('span', null, 'Per page:'),
              [4, 8, 12, 24].map(sz =>
                React.createElement('button', {
                  key: sz,
                  className: `btn btn-xs ${pageSize === sz ? 'btn-primary' : 'btn-outline'}`,
                  style: { padding: '1px 6px', fontSize: '0.72rem', height: 20, minHeight: 20, lineHeight: '18px' },
                  onClick: () => { setPageSize(sz); setPage(0); }
                }, sz)
              )
            )
          ),
          React.createElement('div', { style: { display: 'flex', gap: 6, alignItems: 'center' } },
            React.createElement('button', {
              className: 'btn btn-xs btn-outline',
              disabled: safePage <= 0,
              onClick: () => setPage(p => Math.max(0, p - 1))
            }, '◀ Prev'),
            React.createElement('span', { style: { fontWeight: 600, padding: '0 4px', fontSize: '0.8rem' } }, `${safePage + 1} / ${safeTotalPages}`),
            React.createElement('button', {
              className: 'btn btn-xs btn-outline',
              disabled: safePage >= safeTotalPages - 1,
              onClick: () => setPage(p => p + 1)
            }, 'Next ▶')
          )
        )
      ),

      // ═══════════════════════════════════════════════════════════════════
      // COMPACT EXPLANATION POPOVER DIALOG
      // ═══════════════════════════════════════════════════════════════════
      infoModal && React.createElement('div', {
        className: 'modal-overlay',
        style: { zIndex: 10030 },
        onClick: () => setInfoModal(null)
      },
        React.createElement('div', {
          className: 'modal-content card',
          style: { maxWidth: 420, width: '92%', margin: '120px auto', padding: 20 },
          onClick: e => e.stopPropagation()
        },
          React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 } },
            React.createElement('h4', { style: { margin: 0, fontSize: '1rem', fontWeight: 700, color: 'var(--text-primary)' } },
              infoModal.type === 'FORECAST' ? 'Forecast Recommendation' : 'Minimum Stock Requirement'
            ),
            React.createElement('button', {
              style: { background: 'none', border: 'none', fontSize: '1.1rem', cursor: 'pointer' },
              onClick: () => setInfoModal(null)
            }, '✕')
          ),

          React.createElement('div', { style: { fontSize: '0.85rem', lineHeight: 1.5, color: 'var(--text-secondary)' } },
            React.createElement('div', { style: { fontWeight: 600, color: 'var(--text-primary)', marginBottom: 6 } },
              infoModal.item.productName
            ),
            infoModal.type === 'FORECAST' ? (
              React.createElement('div', null,
                React.createElement('p', { style: { margin: '0 0 10px 0' } },
                  'Based on historical sales demand, forecast horizon, lead time and safety stock.'
                ),
                React.createElement('div', { style: { background: 'var(--bg-secondary)', padding: '10px 12px', borderRadius: 6, fontSize: '0.8rem', display: 'flex', flexDirection: 'column', gap: 4 } },
                  React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between' } },
                    React.createElement('span', null, 'Forecast Recommended:'),
                    React.createElement('strong', { style: { color: '#4f46e5' } }, `${fmtNum(infoModal.item.forecastRecommendedQty || 0, 0)} ${infoModal.item.unit || 'pcs'}`)
                  ),
                  React.createElement('div', { display: 'flex', justifyContent: 'space-between' },
                    React.createElement('span', null, '30-Day Demand:'),
                    React.createElement('strong', null, fmtNum(infoModal.item.forecast30d || 0, 1))
                  ),
                  React.createElement('div', { display: 'flex', justifyContent: 'space-between' },
                    React.createElement('span', null, 'Expected Stock-out:'),
                    React.createElement('strong', null, infoModal.item.expectedStockoutDate || 'Healthy')
                  )
                )
              )
            ) : (
              React.createElement('div', null,
                React.createElement('div', { style: { background: 'var(--bg-secondary)', padding: '10px 12px', borderRadius: 6, fontSize: '0.8rem', display: 'flex', flexDirection: 'column', gap: 6 } },
                  React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between' } },
                    React.createElement('span', null, 'Current Physical Stock:'),
                    React.createElement('strong', null, fmtNum(infoModal.item.currentStock || 0, 0))
                  ),
                  React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between' } },
                    React.createElement('span', null, 'Incoming ISSUED PO:'),
                    React.createElement('strong', { style: { color: '#6366f1' } }, fmtNum(infoModal.item.incomingStock || 0, 0))
                  ),
                  React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between', borderTop: '1px solid var(--border)', paddingTop: 4 } },
                    React.createElement('span', null, 'Net Inventory:'),
                    React.createElement('strong', null, fmtNum((infoModal.item.currentStock || 0) + (infoModal.item.incomingStock || 0), 0))
                  ),
                  React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between' } },
                    React.createElement('span', null, 'Configured Minimum Stock:'),
                    React.createElement('strong', null, fmtNum(infoModal.item.minStockLevel || 0, 0))
                  ),
                  React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between', borderTop: '1px solid var(--border)', paddingTop: 4 } },
                    React.createElement('span', null, 'Additional Required:'),
                    React.createElement('strong', { style: { color: '#d97706' } }, `${fmtNum(infoModal.item.thresholdRequirement || 0, 0)} ${infoModal.item.unit || 'pcs'}`)
                  )
                )
              )
            )
          ),

          React.createElement('div', { style: { display: 'flex', justifyContent: 'flex-end', marginTop: 16 } },
            React.createElement('button', {
              className: 'btn btn-outline btn-sm',
              onClick: () => setInfoModal(null)
            }, 'Close')
          )
        )
      ),

      // ═══════════════════════════════════════════════════════════════════
      // INVOICE-STYLE ORDER CREATION WORKSPACE (MODAL / FULL OVERLAY)
      // ═══════════════════════════════════════════════════════════════════
      showOrderWorkspace && React.createElement('div', {
        className: 'modal-overlay',
        style: { zIndex: 10000 },
        onClick: () => { if (!orderExecuting) setShowOrderWorkspace(false); }
      },
        React.createElement('div', {
          className: 'modal-content card',
          style: { maxWidth: 1080, width: '96%', maxHeight: '92vh', margin: '30px auto', padding: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' },
          onClick: e => e.stopPropagation()
        },
          // Modal Header
          React.createElement('div', {
            style: {
              padding: '16px 24px',
              borderBottom: '1px solid var(--border)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              background: 'var(--bg-secondary)'
            }
          },
            React.createElement('div', null,
              React.createElement('h3', { style: { margin: 0, fontSize: '1.25rem', fontWeight: 700 } },
                workspaceStep === 1 ? 'Create Purchase Orders' :
                  (workspaceStep === 2 ? 'Review Purchase Orders' : 'Purchase Orders Created')
              ),
              React.createElement('div', { style: { fontSize: '0.78rem', color: 'var(--text-secondary)', marginTop: 2 } },
                workspaceStep === 1 ? 'Select recommendations or add catalog items, configure quantities, and verify suppliers.' :
                  (workspaceStep === 2 ? 'Multi-vendor summary. Confirm order details before generating official purchase orders.' :
                    'Official Purchase Orders created and issued successfully.')
              )
            ),

            React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 12 } },
              // Stepper indicator
              React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 8, fontSize: '0.8rem', fontWeight: 600 } },
                React.createElement('span', { style: { color: workspaceStep === 1 ? '#4f46e5' : 'var(--text-secondary)' } }, '1. Items & Vendors'),
                React.createElement('span', { style: { color: 'var(--border)' } }, '→'),
                React.createElement('span', { style: { color: workspaceStep === 2 ? '#4f46e5' : 'var(--text-secondary)' } }, '2. Review Orders'),
                React.createElement('span', { style: { color: 'var(--border)' } }, '→'),
                React.createElement('span', { style: { color: workspaceStep === 3 ? '#4f46e5' : 'var(--text-secondary)' } }, '3. Completion')
              ),
              React.createElement('button', {
                style: { background: 'none', border: 'none', fontSize: '1.2rem', cursor: 'pointer', marginLeft: 12 },
                onClick: () => setShowOrderWorkspace(false)
              }, '✕')
            )
          ),

          // Modal Scrollable Body
          React.createElement('div', { style: { padding: '20px 24px', overflowY: 'auto', flex: 1 } },
            workspaceLoading ? React.createElement('div', { style: { textAlign: 'center', padding: '60px 0' } },
              React.createElement('div', { className: 'spinner', style: { margin: '0 auto 12px' } }),
              React.createElement('div', { style: { color: 'var(--text-secondary)' } }, 'Loading catalog and vendor profiles...')
            ) : (
              workspaceStep === 1 ? React.createElement(React.Fragment, null,
                // Top Search Bar + Create New Product Action
                React.createElement('div', { style: { display: 'flex', gap: 12, alignItems: 'center', marginBottom: 18 } },
                  React.createElement('div', { style: { position: 'relative', flex: 1 } },
                    React.createElement('input', {
                      className: 'input w-full',
                      style: { fontSize: '0.9rem', padding: '8px 12px' },
                      placeholder: 'Search products, SKU or barcode...',
                      value: searchQuery,
                      onFocus: () => setShowSearchDropdown(true),
                      onChange: e => { setSearchQuery(e.target.value); setShowSearchDropdown(true); }
                    }),
                    showSearchDropdown && React.createElement('div', {
                      className: 'autocomplete-dropdown',
                      style: {
                        position: 'absolute',
                        top: 'calc(100% + 4px)',
                        left: 0,
                        right: 0,
                        maxHeight: 260,
                        background: 'var(--bg-card)',
                        border: '1px solid var(--border)',
                        borderRadius: 8,
                        boxShadow: '0 8px 24px rgba(0,0,0,0.15)',
                        zIndex: 100,
                        overflowY: 'auto'
                      }
                    },
                      filteredCatalog.length === 0 ? (
                        React.createElement('div', { style: { padding: '12px 16px', fontSize: '0.85rem', color: 'var(--text-secondary)' } },
                          'No matching products in catalog. Click "Create New Product" to add a new item.'
                        )
                      ) : (
                        filteredCatalog.map(p => {
                          const recFound = recommendations.find(r => (r.productId || r.id) === p.id);
                          const hasRec = recFound && Number(recFound.recommendedOrderQty || 0) > 0;
                          return React.createElement('div', {
                            key: p.id,
                            style: {
                              padding: '10px 14px',
                              borderBottom: '1px solid var(--border)',
                              cursor: 'pointer',
                              display: 'flex',
                              justifyContent: 'space-between',
                              alignItems: 'center'
                            },
                            onMouseEnter: e => e.currentTarget.style.background = 'var(--bg-hover)',
                            onMouseLeave: e => e.currentTarget.style.background = 'transparent',
                            onClick: () => handleAddCatalogProduct(p)
                          },
                            React.createElement('div', null,
                              React.createElement('div', { style: { fontWeight: 600, fontSize: '0.88rem' } }, p.name),
                              React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)' } },
                                `${p.sku ? `SKU: ${p.sku} • ` : ''}Category: ${p.category || 'General'} • Cost: ${fmtCurrency(p.costPrice || 0)}`
                              )
                            ),
                            React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
                              hasRec ? React.createElement('span', {
                                style: { fontSize: '0.72rem', fontWeight: 700, color: '#4f46e5', background: 'rgba(99, 102, 241, 0.1)', padding: '2px 6px', borderRadius: 4 }
                              }, `Rec: ${Math.round(recFound.recommendedOrderQty)}`) : null,
                              React.createElement('span', { style: { fontSize: '0.82rem', fontWeight: 600, color: 'var(--primary)' } }, '+ Add')
                            )
                          );
                        })
                      )
                    )
                  ),

                  // Create New Product Button
                  React.createElement('button', {
                    id: 'btn-create-new-product',
                    className: 'btn btn-outline',
                    style: { fontWeight: 600, whiteSpace: 'nowrap', display: 'flex', alignItems: 'center', gap: 6 },
                    onClick: () => setShowCreateProductModal(true)
                  }, '+ Create New Product')
                ),

                // RECOMMENDED FOR REORDER (Vertical Reflowing List)
                availableRecommendations.length > 0 && React.createElement('div', {
                  style: {
                    background: 'var(--bg-secondary)',
                    border: '1px solid var(--border)',
                    borderRadius: 8,
                    padding: '12px 16px',
                    marginBottom: 18
                  }
                },
                  React.createElement('div', { style: { fontSize: '0.82rem', fontWeight: 700, color: 'var(--text-primary)', marginBottom: 8, display: 'flex', alignItems: 'center', justifyContent: 'space-between' } },
                    React.createElement('span', null, `RECOMMENDED FOR REORDER (${availableRecommendations.length})`),
                    React.createElement('span', { style: { fontSize: '0.74rem', color: 'var(--text-secondary)', fontWeight: 400 } },
                      'Suggestions based on forecast demand and minimum stock'
                    )
                  ),
                  React.createElement('div', {
                    style: {
                      maxHeight: 180,
                      overflowY: 'auto',
                      border: '1px solid var(--border)',
                      borderRadius: 6,
                      background: 'var(--bg-card)'
                    }
                  },
                    React.createElement('table', { style: { width: '100%', margin: 0, fontSize: '0.82rem', borderCollapse: 'collapse' } },
                      React.createElement('thead', { style: { position: 'sticky', top: 0, background: 'var(--bg-secondary)', zIndex: 1, borderBottom: '1px solid var(--border)' } },
                        React.createElement('tr', null,
                          React.createElement('th', { style: { textAlign: 'left', padding: '6px 12px', fontWeight: 600 } }, 'Product'),
                          React.createElement('th', { style: { textAlign: 'right', padding: '6px 12px', fontWeight: 600 } }, 'Current / Min'),
                          React.createElement('th', { style: { textAlign: 'right', padding: '6px 12px', fontWeight: 600 } }, 'Recommended'),
                          React.createElement('th', { style: { textAlign: 'center', padding: '6px 12px', fontWeight: 600 } }, 'Signal Reason'),
                          React.createElement('th', { style: { textAlign: 'center', padding: '6px 12px', width: 80 } }, 'Action')
                        )
                      ),
                      React.createElement('tbody', null,
                        availableRecommendations.map(r => {
                          const finalQty = Math.round(Number(r.recommendedOrderQty || 1));
                          const reasonKey = r.recommendationReason || 'HEALTHY';
                          let reasonBadgeStyle = { background: 'rgba(100, 116, 139, 0.1)', color: 'var(--text-secondary)' };
                          let reasonText = 'Healthy';
                          if (reasonKey === 'BOTH') {
                            reasonBadgeStyle = { background: 'rgba(124, 58, 237, 0.12)', color: '#7c3aed' };
                            reasonText = 'Forecast & Min';
                          } else if (reasonKey === 'FORECAST') {
                            reasonBadgeStyle = { background: 'rgba(79, 70, 229, 0.12)', color: '#4f46e5' };
                            reasonText = 'Forecast';
                          } else if (reasonKey === 'MINIMUM_STOCK') {
                            reasonBadgeStyle = { background: 'rgba(217, 119, 6, 0.12)', color: '#d97706' };
                            reasonText = 'Min Stock';
                          }

                          return React.createElement('tr', {
                            key: r.id || r.productId,
                            style: { borderBottom: '1px solid var(--border)' }
                          },
                            React.createElement('td', { style: { padding: '6px 12px' } },
                              React.createElement('div', { style: { fontWeight: 600, color: 'var(--text-primary)' } }, r.productName),
                              React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)' } },
                                `${r.sku ? `SKU: ${r.sku} • ` : ''}${r.category || 'General'}`
                              )
                            ),
                            React.createElement('td', { style: { textAlign: 'right', padding: '6px 12px', color: 'var(--text-secondary)' } },
                              `${fmtNum(r.currentStock || 0, 0)} / ${fmtNum(r.minStockLevel || 0, 0)}`
                            ),
                            React.createElement('td', { style: { textAlign: 'right', padding: '6px 12px', fontWeight: 700, color: '#4f46e5' } },
                              `Rec: ${finalQty} ${r.unit || 'pcs'}`
                            ),
                            React.createElement('td', { style: { textAlign: 'center', padding: '6px 12px' } },
                              React.createElement('span', {
                                style: {
                                  fontSize: '0.72rem',
                                  fontWeight: 700,
                                  padding: '2px 8px',
                                  borderRadius: 4,
                                  whiteSpace: 'nowrap',
                                  ...reasonBadgeStyle
                                }
                              }, reasonText)
                            ),
                            React.createElement('td', { style: { textAlign: 'center', padding: '6px 12px' } },
                              React.createElement('button', {
                                className: 'btn btn-xs btn-primary',
                                style: { fontWeight: 700, padding: '3px 10px' },
                                onClick: () => handleAddRecommendationToOrder(r)
                              }, '+ Add')
                            )
                          );
                        })
                      )
                    )
                  )
                ),

                // ORDER ITEMS (Invoice-Style Table)
                React.createElement('div', { style: { marginBottom: 12 } },
                  React.createElement('div', { style: { fontSize: '0.88rem', fontWeight: 700, color: 'var(--text-primary)', marginBottom: 8 } },
                    'ORDER ITEMS'
                  ),

                  orderItems.length === 0 ? React.createElement('div', {
                    style: { textAlign: 'center', padding: '40px 20px', border: '1px dashed var(--border)', borderRadius: 8 }
                  },
                    React.createElement('div', { style: { fontSize: '1.8rem', marginBottom: 6 } }, '📋'),
                    React.createElement('div', { style: { fontWeight: 700, fontSize: '1rem' } }, 'No items in order'),
                    React.createElement('div', { style: { fontSize: '0.82rem', color: 'var(--text-secondary)', marginTop: 4 } },
                      'Search for products above, add recommendations, or create a new product to begin.'
                    )
                  ) : React.createElement('div', { className: 'table-container', style: { border: '1px solid var(--border)', borderRadius: 8, overflow: 'hidden' } },
                    React.createElement('table', { style: { width: '100%', margin: 0 } },
                      React.createElement('thead', null, React.createElement('tr', null,
                        React.createElement('th', { style: { width: '28%', padding: '10px 14px' } }, 'Product'),
                        React.createElement('th', { style: { width: '12%', textAlign: 'center' } }, 'Quantity'),
                        React.createElement('th', { style: { width: '14%', textAlign: 'right' } }, 'Unit Cost'),
                        React.createElement('th', { style: { width: '28%' } }, 'Vendor'),
                        React.createElement('th', { style: { width: '12%', textAlign: 'right' } }, 'Total'),
                        React.createElement('th', { style: { width: '6%', textAlign: 'center' } }, '')
                      )),
                      React.createElement('tbody', null,
                        orderItems.map((row, idx) => {
                          const isUnassigned = !row.partyId;
                          const lineTotal = (Number(row.orderQty) || 0) * (Number(row.unitCost) || 0);

                          return React.createElement('tr', {
                            key: row._key,
                            style: { background: isUnassigned ? 'rgba(245, 158, 11, 0.04)' : undefined }
                          },
                            // Product Name & Source
                            React.createElement('td', { style: { padding: '8px 14px' } },
                              React.createElement('div', { style: { fontWeight: 600, fontSize: '0.88rem' } }, row.name),
                              React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)' } },
                                `${row.sku ? `SKU: ${row.sku} • ` : ''}Source: ${formatReasonLabel(row.source)}`
                              )
                            ),

                            // Quantity (Inline Editable with Enter key support)
                            React.createElement('td', { style: { textAlign: 'center' } },
                              React.createElement('input', {
                                id: `input_qty_${idx}`,
                                type: 'number',
                                min: '1',
                                step: 'any',
                                className: 'input input-sm',
                                style: { width: 80, textAlign: 'center', fontWeight: 700 },
                                value: row.orderQty,
                                onKeyDown: e => handleCellKeyDown(e, idx, 'qty'),
                                onChange: e => updateOrderItem(row._key, { orderQty: Math.max(1, parseFloat(e.target.value) || 1) })
                              })
                            ),

                            // Unit Cost (Procurement Cost defaulting to costPrice)
                            React.createElement('td', { style: { textAlign: 'right' } },
                              React.createElement('input', {
                                id: `input_cost_${idx}`,
                                type: 'number',
                                min: '0',
                                step: 'any',
                                className: 'input input-sm',
                                style: { width: 95, textAlign: 'right' },
                                value: row.unitCost,
                                onKeyDown: e => handleCellKeyDown(e, idx, 'cost'),
                                onChange: e => updateOrderItem(row._key, { unitCost: parseFloat(e.target.value) || 0 })
                              })
                            ),

                            // Vendor Selector
                            React.createElement('td', null,
                              React.createElement('div', { style: { display: 'flex', gap: 6, alignItems: 'center' } },
                                React.createElement('select', {
                                  className: 'select select-sm w-full',
                                  style: {
                                    borderColor: isUnassigned ? '#f59e0b' : undefined,
                                    fontWeight: isUnassigned ? 600 : 400
                                  },
                                  value: row.partyId || '',
                                  onChange: e => handleVendorChange(row._key, e.target.value)
                                },
                                  React.createElement('option', { value: '' }, 'Choose Vendor...'),
                                  partiesList.map(v => React.createElement('option', { key: v.id, value: v.id }, v.name))
                                ),
                                React.createElement('button', {
                                  className: 'btn btn-outline btn-xs',
                                  style: { fontWeight: 700, padding: '2px 6px' },
                                  title: 'Add New Vendor',
                                  onClick: () => { setTargetVendorRowKey(row._key); setShowAddVendorModal(true); }
                                }, '+')
                              ),
                              row.provenanceDetail && React.createElement('div', {
                                style: { fontSize: '0.68rem', color: isUnassigned ? '#d97706' : 'var(--text-secondary)', marginTop: 2 }
                              }, row.provenanceDetail)
                            ),

                            // Line Total
                            React.createElement('td', { style: { textAlign: 'right', fontWeight: 600, fontSize: '0.88rem' } },
                              fmtCurrency(lineTotal)
                            ),

                            // Remove Action
                            React.createElement('td', { style: { textAlign: 'center' } },
                              React.createElement('button', {
                                className: 'btn-icon-danger',
                                onClick: () => removeOrderItem(row._key),
                                style: { background: 'none', border: 'none', cursor: 'pointer', opacity: 0.6, fontSize: '0.9rem' },
                                title: 'Remove line'
                              }, '✕')
                            )
                          );
                        })
                      )
                    )
                  )
                ),

                // Inline Vendor Validation Warning (Compact)
                unassignedItems.length > 0 && React.createElement('div', {
                  style: {
                    padding: '8px 12px',
                    background: '#fffbeb',
                    border: '1px solid #fde68a',
                    borderRadius: 6,
                    fontSize: '0.82rem',
                    color: '#b45309',
                    marginBottom: 12,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6
                  }
                },
                  React.createElement('span', null, 'ℹ️'),
                  React.createElement('span', null,
                    `${unassignedItems.length} product(s) need a vendor before purchase orders can be created.`
                  )
                )
              ) : (
                workspaceStep === 2 ? React.createElement(React.Fragment, null,
                  // STEP 2: REVIEW PURCHASE ORDERS (Grouped by Vendor)
                  React.createElement('div', { style: { display: 'flex', flexWrap: 'wrap', gap: 14, marginBottom: 18 } },
                    React.createElement('div', { style: { flex: 1, minWidth: 200 } },
                      React.createElement('label', { style: { fontSize: '0.78rem', fontWeight: 600, display: 'block', marginBottom: 4 } }, 'Expected Delivery Date'),
                      React.createElement('input', {
                        type: 'date',
                        className: 'input w-full',
                        value: expectedDeliveryDate,
                        onChange: e => setExpectedDeliveryDate(e.target.value)
                      })
                    ),
                    React.createElement('div', { style: { flex: 2, minWidth: 260 } },
                      React.createElement('label', { style: { fontSize: '0.78rem', fontWeight: 600, display: 'block', marginBottom: 4 } }, 'Default Order Notes'),
                      React.createElement('input', {
                        type: 'text',
                        className: 'input w-full',
                        value: orderNotes,
                        onChange: e => setOrderNotes(e.target.value)
                      })
                    ),
                    React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 8, padding: '0 8px', marginTop: 20 } },
                      React.createElement('input', {
                        type: 'checkbox',
                        id: 'chkHidePrices',
                        checked: hidePricesOnPo,
                        onChange: e => setHidePricesOnPo(e.target.checked)
                      }),
                      React.createElement('label', { htmlFor: 'chkHidePrices', style: { fontSize: '0.82rem', cursor: 'pointer' } }, 'Hide Prices on Vendor Document')
                    )
                  ),

                  // Vendor Cards
                  React.createElement('div', { style: { display: 'flex', flexDirection: 'column', gap: 14 } },
                    groupedVendorOrders.map((group, gIdx) => {
                      return React.createElement('div', {
                        key: group.partyId || gIdx,
                        style: {
                          border: '1px solid var(--border)',
                          borderRadius: 8,
                          padding: '14px 18px',
                          background: 'var(--bg-card)'
                        }
                      },
                        React.createElement('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10, borderBottom: '1px solid var(--border)', paddingBottom: 8 } },
                          React.createElement('div', null,
                            React.createElement('h4', { style: { margin: 0, fontSize: '0.95rem', fontWeight: 700 } }, group.partyName),
                            React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)' } },
                              `${group.items.length} item(s) • PO #${gIdx + 1}`
                            )
                          ),
                          React.createElement('div', { style: { textAlign: 'right' } },
                            React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-secondary)' } }, 'Vendor PO Total'),
                            React.createElement('div', { style: { fontSize: '1.05rem', fontWeight: 800, color: '#4f46e5' } },
                              fmtCurrency(group.grandTotal)
                            )
                          )
                        ),
                        React.createElement('table', { style: { width: '100%', fontSize: '0.82rem' } },
                          React.createElement('thead', null, React.createElement('tr', { style: { borderBottom: '1px solid var(--border)' } },
                            React.createElement('th', { style: { textAlign: 'left', padding: '4px 0' } }, 'Item'),
                            React.createElement('th', { style: { textAlign: 'center', width: 80 } }, 'Qty'),
                            React.createElement('th', { style: { textAlign: 'right', width: 100 } }, 'Unit Cost'),
                            React.createElement('th', { style: { textAlign: 'right', width: 110 } }, 'Total')
                          )),
                          React.createElement('tbody', null,
                            group.items.map(it => React.createElement('tr', { key: it._key },
                              React.createElement('td', { style: { padding: '6px 0' } },
                                React.createElement('strong', null, it.name),
                                it.sku && React.createElement('span', { style: { fontSize: '0.7rem', color: 'var(--text-secondary)', marginLeft: 6 } }, `(${it.sku})`)
                              ),
                              React.createElement('td', { style: { textAlign: 'center' } }, `${it.orderQty} ${it.unit}`),
                              React.createElement('td', { style: { textAlign: 'right' } }, fmtCurrency(it.unitCost)),
                              React.createElement('td', { style: { textAlign: 'right', fontWeight: 600 } }, fmtCurrency(it.lineTotal))
                            ))
                          )
                        )
                      );
                    })
                  )
                ) : (
                  // STEP 3: SUCCESS HUB
                  workspaceStep === 3 ? React.createElement('div', { style: { textAlign: 'center', padding: '24px 20px' } },
                    React.createElement('div', { style: { fontSize: '2.5rem', marginBottom: 8 } }, '🎉'),
                    React.createElement('h3', { style: { margin: 0, fontSize: '1.3rem', fontWeight: 800 } },
                      `${createdPos.length} Purchase Order(s) Created Successfully`
                    ),
                    React.createElement('p', { style: { margin: '6px 0 20px', color: 'var(--text-secondary)', fontSize: '0.85rem' } },
                      'All purchase orders have been saved in ISSUED status and linked to respective suppliers.'
                    ),

                    // Quick Actions
                    React.createElement('div', { style: { display: 'flex', justifyContent: 'center', gap: 10, flexWrap: 'wrap', marginBottom: 24 } },
                      React.createElement('button', {
                        className: 'btn btn-primary',
                        style: { fontWeight: 700 },
                        onClick: handlePrintAllPos
                      }, 'Print All Purchase Orders'),
                      React.createElement('button', {
                        className: 'btn btn-outline',
                        style: { fontWeight: 600 },
                        onClick: handleDownloadCombinedPdf
                      }, 'Download Combined PDF'),
                      React.createElement('button', {
                        className: 'btn btn-outline',
                        style: { fontWeight: 600 },
                        onClick: handleDownloadZip
                      }, 'Download ZIP Bundle')
                    ),

                    // PO Summary Cards
                    React.createElement('div', { style: { maxWidth: 640, margin: '0 auto', textAlign: 'left', border: '1px solid var(--border)', borderRadius: 8, overflow: 'hidden' } },
                      React.createElement('div', { style: { background: 'var(--bg-secondary)', padding: '10px 16px', fontWeight: 700, fontSize: '0.82rem', borderBottom: '1px solid var(--border)' } },
                        'Generated Purchase Orders'
                      ),
                      createdPos.map(po => React.createElement('div', {
                        key: po.id,
                        style: { padding: '12px 16px', borderBottom: '1px solid var(--border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }
                      },
                        React.createElement('div', null,
                          React.createElement('div', { style: { fontWeight: 700, color: '#4f46e5' } }, po.poNumber || `PO #${po.id}`),
                          React.createElement('div', { style: { fontSize: '0.78rem', color: 'var(--text-secondary)' } },
                            `${po.partyName || (po.party ? po.party.name : 'Vendor')} • ${(po.items || []).length} item(s)`
                          )
                        ),
                        React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 10 } },
                          React.createElement('div', { style: { fontWeight: 700 } }, fmtCurrency(po.totalAmount)),
                          React.createElement('button', {
                            className: 'btn btn-xs btn-outline',
                            onClick: () => handleDownloadSinglePoPdf(po)
                          }, 'PDF')
                        )
                      ))
                    )
                  ) : null
                )
              )
            )
          ),

          // Modal Footer
          React.createElement('div', {
            style: {
              padding: '14px 24px',
              borderTop: '1px solid var(--border)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              background: 'var(--bg-secondary)'
            }
          },
            workspaceStep === 1 ? React.createElement(React.Fragment, null,
              React.createElement('div', { style: { fontSize: '0.85rem', color: 'var(--text-secondary)' } },
                `${orderItems.length} item(s) • ${groupedVendorOrders.filter(g => g.partyId).length} vendor(s) • Total: ${fmtCurrency(totalOrderAmount)}`
              ),
              React.createElement('div', { style: { display: 'flex', gap: 10 } },
                React.createElement('button', { className: 'btn btn-outline', onClick: () => setShowOrderWorkspace(false) }, 'Cancel'),
                React.createElement('button', {
                  className: 'btn btn-primary',
                  style: { fontWeight: 700 },
                  disabled: orderItems.length === 0 || unassignedItems.length > 0,
                  onClick: handleProceedToReview
                }, 'Review Orders →')
              )
            ) : (
              workspaceStep === 2 ? React.createElement(React.Fragment, null,
                React.createElement('div', { style: { fontSize: '0.9rem', fontWeight: 700, color: '#4f46e5' } },
                  `Grand Total: ${fmtCurrency(totalOrderAmount)}`
                ),
                React.createElement('div', { style: { display: 'flex', gap: 10 } },
                  React.createElement('button', { className: 'btn btn-outline', onClick: () => setWorkspaceStep(1), disabled: orderExecuting }, '← Back to Order Items'),
                  React.createElement('button', {
                    className: 'btn btn-primary',
                    style: { fontWeight: 700 },
                    disabled: orderExecuting,
                    onClick: handleExecuteBatchPo
                  }, orderExecuting ? 'Creating POs...' : `Create ${groupedVendorOrders.filter(g => g.partyId).length} Purchase Orders`)
                )
              ) : (
                React.createElement('div', { style: { display: 'flex', justifyContent: 'flex-end', width: '100%' } },
                  React.createElement('button', {
                    className: 'btn btn-primary',
                    onClick: () => {
                      setShowOrderWorkspace(false);
                      loadRecommendations();
                    }
                  }, 'Done & Return to Queue')
                )
              )
            )
          )
        )
      ),

      // ═══════════════════════════════════════════════════════════════════
      // SUB-MODAL 1: ADD NEW VENDOR (z-index 10050 above PO Workspace)
      // ═══════════════════════════════════════════════════════════════════
      showAddVendorModal && React.createElement('div', {
        className: 'modal-overlay',
        style: { zIndex: 10050 },
        onClick: () => { if (!savingVendor) setShowAddVendorModal(false); }
      },
        React.createElement('div', {
          className: 'modal-content card',
          style: { maxWidth: 440, width: '92%', margin: '60px auto', padding: 22 },
          onClick: e => e.stopPropagation()
        },
          React.createElement('h3', { style: { margin: '0 0 14px 0', fontSize: '1.1rem', fontWeight: 700 } }, 'Add New Vendor'),
          React.createElement('div', { style: { display: 'flex', flexDirection: 'column', gap: 10 } },
            React.createElement('input', {
              className: 'input',
              placeholder: 'Vendor / Supplier Name *',
              value: vendorForm.name,
              onChange: e => setVendorForm(f => ({ ...f, name: e.target.value }))
            }),
            React.createElement('input', {
              className: 'input',
              placeholder: 'Contact Person',
              value: vendorForm.contactPerson,
              onChange: e => setVendorForm(f => ({ ...f, contactPerson: e.target.value }))
            }),
            React.createElement('input', {
              className: 'input',
              placeholder: 'Phone Number',
              value: vendorForm.phone,
              onChange: e => setVendorForm(f => ({ ...f, phone: e.target.value }))
            }),
            React.createElement('input', {
              className: 'input',
              placeholder: 'Email Address',
              value: vendorForm.email,
              onChange: e => setVendorForm(f => ({ ...f, email: e.target.value }))
            }),
            React.createElement('input', {
              className: 'input',
              placeholder: 'GSTIN (Optional)',
              value: vendorForm.gstin,
              onChange: e => setVendorForm(f => ({ ...f, gstin: e.target.value }))
            }),
            React.createElement('textarea', {
              className: 'input',
              rows: 2,
              placeholder: 'Address (Optional)',
              value: vendorForm.address,
              onChange: e => setVendorForm(f => ({ ...f, address: e.target.value }))
            })
          ),
          React.createElement('div', { style: { display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 16 } },
            React.createElement('button', { className: 'btn btn-outline', onClick: () => setShowAddVendorModal(false), disabled: savingVendor }, 'Cancel'),
            React.createElement('button', { className: 'btn btn-primary', onClick: handleSaveNewVendor, disabled: savingVendor }, savingVendor ? 'Saving...' : 'Save & Select Vendor')
          )
        )
      ),

      // ═══════════════════════════════════════════════════════════════════
      // SUB-MODAL 2: CREATE NEW PRODUCT (z-index 10050 above PO Workspace)
      // ═══════════════════════════════════════════════════════════════════
      showCreateProductModal && React.createElement('div', {
        className: 'modal-overlay',
        style: { zIndex: 10050 },
        onClick: () => { if (!savingProduct) setShowCreateProductModal(false); }
      },
        React.createElement('div', {
          className: 'modal-content card',
          style: { maxWidth: 580, width: '94%', maxHeight: '90vh', overflowY: 'auto', margin: '40px auto', padding: 22 },
          onClick: e => e.stopPropagation()
        },
          React.createElement('h3', { style: { margin: '0 0 14px 0', fontSize: '1.15rem', fontWeight: 700 } }, 'Create New Product'),
          React.createElement('div', { style: { display: 'flex', flexDirection: 'column', gap: 12 } },
            // Name
            React.createElement('div', null,
              React.createElement('label', { style: { fontSize: '0.78rem', fontWeight: 600, display: 'block', marginBottom: 4 } }, 'Product Name *'),
              React.createElement('input', {
                className: 'input w-full',
                placeholder: 'e.g. 4K Smart TV',
                value: productForm.name,
                onChange: e => setProductForm(f => ({ ...f, name: e.target.value }))
              })
            ),

            // SKU & Barcode
            React.createElement('div', { style: { display: 'flex', gap: 10 } },
              React.createElement('div', { style: { flex: 1 } },
                React.createElement('label', { style: { fontSize: '0.78rem', fontWeight: 600, display: 'block', marginBottom: 4 } }, 'SKU'),
                React.createElement('input', {
                  className: 'input w-full',
                  placeholder: 'e.g. TV-4K-01',
                  value: productForm.sku,
                  onChange: e => setProductForm(f => ({ ...f, sku: e.target.value }))
                })
              ),
              React.createElement('div', { style: { flex: 1 } },
                React.createElement('label', { style: { fontSize: '0.78rem', fontWeight: 600, display: 'block', marginBottom: 4 } }, 'Barcode'),
                React.createElement('input', {
                  className: 'input w-full',
                  placeholder: 'e.g. 8901234567890',
                  value: productForm.barcode,
                  onChange: e => setProductForm(f => ({ ...f, barcode: e.target.value }))
                })
              )
            ),

            // Category & Unit
            React.createElement('div', { style: { display: 'flex', gap: 10 } },
              React.createElement('div', { style: { flex: 2 } },
                React.createElement('label', { style: { fontSize: '0.78rem', fontWeight: 600, display: 'block', marginBottom: 4 } }, 'Category *'),
                React.createElement('input', {
                  className: 'input w-full',
                  list: 'categoryOptions',
                  placeholder: 'Select or type category...',
                  value: productForm.category,
                  onChange: e => setProductForm(f => ({ ...f, category: e.target.value }))
                }),
                React.createElement('datalist', { id: 'categoryOptions' },
                  categoryList.map(c => React.createElement('option', { key: c, value: c }))
                )
              ),
              React.createElement('div', { style: { flex: 1 } },
                React.createElement('label', { style: { fontSize: '0.78rem', fontWeight: 600, display: 'block', marginBottom: 4 } }, 'Unit'),
                React.createElement('input', {
                  className: 'input w-full',
                  placeholder: 'pcs, kg, etc.',
                  value: productForm.unit,
                  onChange: e => setProductForm(f => ({ ...f, unit: e.target.value }))
                })
              )
            ),

            // Purchase Cost * & Selling Price
            React.createElement('div', { style: { display: 'flex', gap: 10 } },
              React.createElement('div', { style: { flex: 1 } },
                React.createElement('label', { style: { fontSize: '0.78rem', fontWeight: 600, display: 'block', marginBottom: 4 } }, 'Purchase Cost (₹) *'),
                React.createElement('input', {
                  type: 'number',
                  min: '0',
                  step: 'any',
                  className: 'input w-full',
                  placeholder: 'Procurement cost',
                  value: productForm.costPrice,
                  onChange: e => setProductForm(f => ({ ...f, costPrice: e.target.value }))
                })
              ),
              React.createElement('div', { style: { flex: 1 } },
                React.createElement('label', { style: { fontSize: '0.78rem', fontWeight: 600, display: 'block', marginBottom: 4 } }, 'Selling Price (₹)'),
                React.createElement('input', {
                  type: 'number',
                  min: '0',
                  step: 'any',
                  className: 'input w-full',
                  placeholder: 'Retail price',
                  value: productForm.price,
                  onChange: e => setProductForm(f => ({ ...f, price: e.target.value }))
                })
              )
            ),

            // Minimum Stock & GST %
            React.createElement('div', { style: { display: 'flex', gap: 10 } },
              React.createElement('div', { style: { flex: 1 } },
                React.createElement('label', { style: { fontSize: '0.78rem', fontWeight: 600, display: 'block', marginBottom: 4 } }, 'Minimum Stock'),
                React.createElement('input', {
                  type: 'number',
                  min: '0',
                  className: 'input w-full',
                  placeholder: 'e.g. 5',
                  value: productForm.minStockLevel,
                  onChange: e => setProductForm(f => ({ ...f, minStockLevel: e.target.value }))
                })
              ),
              React.createElement('div', { style: { flex: 1 } },
                React.createElement('label', { style: { fontSize: '0.78rem', fontWeight: 600, display: 'block', marginBottom: 4 } }, 'GST %'),
                React.createElement('select', {
                  className: 'select w-full',
                  value: productForm.gstPercentage,
                  onChange: e => setProductForm(f => ({ ...f, gstPercentage: e.target.value }))
                },
                  ['0', '5', '12', '18', '28'].map(g => React.createElement('option', { key: g, value: g }, `${g}%`))
                )
              ),
              React.createElement('div', { style: { flex: 1 } },
                React.createElement('label', { style: { fontSize: '0.78rem', fontWeight: 600, display: 'block', marginBottom: 4 } }, 'HSN Code'),
                React.createElement('input', {
                  className: 'input w-full',
                  placeholder: 'HSN code',
                  value: productForm.hsnCode,
                  onChange: e => setProductForm(f => ({ ...f, hsnCode: e.target.value }))
                })
              )
            ),

            // Preferred Vendor
            React.createElement('div', null,
              React.createElement('label', { style: { fontSize: '0.78rem', fontWeight: 600, display: 'block', marginBottom: 4 } }, 'Preferred Vendor'),
              React.createElement('select', {
                className: 'select w-full',
                value: productForm.preferredPartyId,
                onChange: e => setProductForm(f => ({ ...f, preferredPartyId: e.target.value }))
              },
                React.createElement('option', { value: '' }, 'None (Select Vendor)'),
                partiesList.map(v => React.createElement('option', { key: v.id, value: v.id }, v.name))
              )
            ),

            // Description
            React.createElement('div', null,
              React.createElement('label', { style: { fontSize: '0.78rem', fontWeight: 600, display: 'block', marginBottom: 4 } }, 'Description (Optional)'),
              React.createElement('textarea', {
                className: 'input w-full',
                rows: 2,
                placeholder: 'Product specifications / notes',
                value: productForm.description,
                onChange: e => setProductForm(f => ({ ...f, description: e.target.value }))
              })
            )
          ),

          React.createElement('div', { style: { display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 18 } },
            React.createElement('button', { className: 'btn btn-outline', onClick: () => setShowCreateProductModal(false), disabled: savingProduct }, 'Cancel'),
            React.createElement('button', { className: 'btn btn-primary', onClick: handleSaveNewProduct, disabled: savingProduct }, savingProduct ? 'Saving Product...' : 'Save & Add to Order')
          )
        )
      ),

      // Product Insights Detail Drawer
      detailProductId && React.createElement(ProductDetailDrawer, {
        productId: detailProductId,
        onClose: () => setDetailProductId(null),
        onUseRecommendation: (pId, qty) => {
          setDetailProductId(null);
          const foundRec = recommendations.find(r => (r.productId || r.id) === pId);
          if (foundRec) {
            handleOpenOrderWorkspace({ ...foundRec, recommendedOrderQty: qty });
          }
        }
      })
    );
  }

  // ═══════════════════════════════════════════════════════════════════
  // 5. BULK PRODUCT ADD MODAL (Spreadsheet Batch Input)
  // ═══════════════════════════════════════════════════════════════════
  // ═══════════════════════════════════════════════════════════════════
  // 5. BULK PRODUCT ADD MODAL (Spreadsheet Batch Input)
  // ═══════════════════════════════════════════════════════════════════
  function BulkProductAddModal({ onClose, onSuccess, existingCategories = [] }) {
    const modalContainerRef = React.useRef(null);

    const createEmptyRow = () => ({
      _id: Math.random().toString(36).substr(2, 9),
      name: '',
      sku: '',
      barcode: '',
      category: '',
      itemType: 'GOODS',
      price: '',
      costPrice: '',
      gstPercentage: '0',
      initialStock: '0',
      minStockLevel: '5',
      unit: 'pcs',
      description: ''
    });

    const [rows, setRows] = useState([createEmptyRow(), createEmptyRow(), createEmptyRow()]);
    const [saving, setSaving] = useState(false);
    const [errorMsg, setErrorMsg] = useState(null);
    const [showPasteBox, setShowPasteBox] = useState(false);
    const [pasteText, setPasteText] = useState('');

    const focusCell = (rowIdx, colIdx) => {
      const container = modalContainerRef.current;
      if (!container) return;
      const tableRows = container.querySelectorAll('.bulk-product-table tbody tr');
      const targetRow = tableRows[rowIdx];
      if (!targetRow) return;
      const controls = targetRow.querySelectorAll('input:not([type="hidden"]), select');
      let target = controls[colIdx];
      if (target && target.disabled) {
        for (let c = colIdx + 1; c < controls.length; c++) {
          if (!controls[c].disabled) {
            target = controls[c];
            break;
          }
        }
      }
      if (target) {
        target.focus();
        if (target.select) target.select();
      }
    };

    // Auto-focus first input on mount
    useEffect(() => {
      const timer = setTimeout(() => {
        focusCell(0, 0);
      }, 60);
      return () => clearTimeout(timer);
    }, []);

    const updateRow = (index, field, value) => {
      setRows(prev => {
        const next = [...prev];
        next[index] = { ...next[index], [field]: value };
        return next;
      });
    };

    const addRows = (count = 1) => {
      const newRows = [];
      for (let i = 0; i < count; i++) newRows.push(createEmptyRow());
      setRows(prev => [...prev, ...newRows]);
    };

    const removeRow = (index) => {
      if (rows.length <= 1) {
        setRows([createEmptyRow()]);
        return;
      }
      setRows(prev => prev.filter((_, idx) => idx !== index));
    };

    const handleCellKeyDown = (e, rowIdx, colIdx) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
        e.preventDefault();
        handleSaveBatch();
        return;
      }

      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
        return;
      }

      const totalRows = rows.length;
      const row = rows[rowIdx];
      const isService = row && row.itemType === 'SERVICE';
      const lastColIdx = 10; // 10 is Unit

      if (e.key === 'Enter') {
        e.preventDefault();
        let nextCol = colIdx + 1;
        if (isService && (nextCol === 8 || nextCol === 9)) {
          nextCol = 10;
        }
        if (nextCol <= lastColIdx) {
          focusCell(rowIdx, nextCol);
        } else {
          if (rowIdx < totalRows - 1) {
            focusCell(rowIdx + 1, 0);
          } else {
            addRows(1);
            setTimeout(() => {
              focusCell(rowIdx + 1, 0);
            }, 50);
          }
        }
      } else if (e.key === 'ArrowDown') {
        if (rowIdx < totalRows - 1) {
          e.preventDefault();
          focusCell(rowIdx + 1, colIdx);
        } else {
          e.preventDefault();
          addRows(1);
          setTimeout(() => {
            focusCell(rowIdx + 1, colIdx);
          }, 50);
        }
      } else if (e.key === 'ArrowUp') {
        if (rowIdx > 0) {
          e.preventDefault();
          focusCell(rowIdx - 1, colIdx);
        }
      } else if (e.key === 'ArrowRight') {
        const isInput = e.target.tagName === 'INPUT';
        const atEnd = !isInput || (e.target.selectionStart === e.target.value.length);
        if (atEnd) {
          let nextCol = colIdx + 1;
          if (isService && (nextCol === 8 || nextCol === 9)) nextCol = 10;
          if (nextCol <= lastColIdx) {
            e.preventDefault();
            focusCell(rowIdx, nextCol);
          } else if (rowIdx < totalRows - 1) {
            e.preventDefault();
            focusCell(rowIdx + 1, 0);
          }
        }
      } else if (e.key === 'ArrowLeft') {
        const isInput = e.target.tagName === 'INPUT';
        const atStart = !isInput || (e.target.selectionStart === 0);
        if (atStart) {
          let prevCol = colIdx - 1;
          if (isService && (prevCol === 9 || prevCol === 8)) prevCol = 7;
          if (prevCol >= 0) {
            e.preventDefault();
            focusCell(rowIdx, prevCol);
          } else if (rowIdx > 0) {
            e.preventDefault();
            focusCell(rowIdx - 1, lastColIdx);
          }
        }
      }
    };

    const handlePasteProcess = () => {
      if (!pasteText || !pasteText.trim()) {
        setShowPasteBox(false);
        return;
      }
      const lines = pasteText.trim().split(/\r?\n/);
      const parsedRows = [];
      for (const line of lines) {
        if (!line.trim()) continue;
        const parts = line.split('\t').length > 1 ? line.split('\t') : line.split(',');
        const name = (parts[0] || '').trim();
        if (!name) continue;
        parsedRows.push({
          _id: Math.random().toString(36).substr(2, 9),
          name: name,
          sku: (parts[1] || '').trim(),
          barcode: (parts[2] || '').trim(),
          category: (parts[3] || '').trim(),
          price: (parts[4] || '').trim(),
          costPrice: (parts[5] || '').trim(),
          gstPercentage: (parts[6] || '0').trim(),
          initialStock: (parts[7] || '0').trim(),
          minStockLevel: (parts[8] || '5').trim(),
          unit: (parts[9] || 'pcs').trim(),
          itemType: (parts[10] || 'GOODS').trim().toUpperCase() === 'SERVICE' ? 'SERVICE' : 'GOODS',
          description: (parts[11] || '').trim()
        });
      }

      if (parsedRows.length > 0) {
        setRows(prev => {
          const nonEmpty = prev.filter(r => r.name && r.name.trim());
          return [...nonEmpty, ...parsedRows];
        });
        if (typeof showToast === 'function') {
          showToast(`Imported ${parsedRows.length} rows from clipboard data`, 'success');
        }
      }
      setPasteText('');
      setShowPasteBox(false);
    };

    const handleSaveBatch = async () => {
      setErrorMsg(null);
      const validRows = rows.filter(r => r.name && r.name.trim());
      if (validRows.length === 0) {
        setErrorMsg('Please enter at least one product with a name.');
        return;
      }

      // Check for negative numbers or client-side anomalies
      for (let i = 0; i < validRows.length; i++) {
        const r = validRows[i];
        if (r.price !== '' && Number(r.price) < 0) {
          setErrorMsg(`Row ${i + 1} (${r.name}): Selling price cannot be negative.`);
          return;
        }
        if (r.costPrice !== '' && Number(r.costPrice) < 0) {
          setErrorMsg(`Row ${i + 1} (${r.name}): Cost price cannot be negative.`);
          return;
        }
        if (r.initialStock !== '' && Number(r.initialStock) < 0) {
          setErrorMsg(`Row ${i + 1} (${r.name}): Initial stock cannot be negative.`);
          return;
        }
      }

      const payload = validRows.map(r => ({
        name: r.name.trim(),
        sku: r.sku ? r.sku.trim() : null,
        barcode: r.barcode ? r.barcode.trim() : null,
        category: r.category ? r.category.trim() : null,
        itemType: r.itemType || 'GOODS',
        price: r.price !== '' && !isNaN(Number(r.price)) ? Number(r.price) : 0,
        costPrice: r.costPrice !== '' && !isNaN(Number(r.costPrice)) ? Number(r.costPrice) : null,
        gstPercentage: r.gstPercentage !== '' && !isNaN(Number(r.gstPercentage)) ? Number(r.gstPercentage) : 0,
        initialStock: r.initialStock !== '' && !isNaN(Number(r.initialStock)) ? Number(r.initialStock) : 0,
        minStockLevel: r.minStockLevel !== '' && !isNaN(Number(r.minStockLevel)) ? Number(r.minStockLevel) : 5,
        unit: r.unit ? r.unit.trim() : 'pcs',
        description: r.description ? r.description.trim() : null,
        inInventory: true
      }));

      setSaving(true);
      try {
        const saved = await API.products.createBulk(payload);
        if (typeof showToast === 'function') {
          showToast(`Successfully created ${saved.length} products in bulk!`, 'success');
        }
        if (onSuccess) onSuccess(saved);
        onClose();
      } catch (err) {
        console.error('Bulk product add error:', err);
        setErrorMsg(err.message || 'Failed to save products batch. Atomic rollback applied.');
      } finally {
        setSaving(false);
      }
    };

    return React.createElement('div', {
      className: 'modal-backdrop',
      ref: modalContainerRef,
      style: {
        position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
        background: 'rgba(15, 23, 42, 0.65)', backdropFilter: 'blur(4px)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1100, padding: 16
      },
      onClick: (e) => { if (e.target === e.currentTarget) onClose(); }
    },
      React.createElement('div', {
        className: 'card',
        style: {
          width: '95vw', maxWidth: 1250, maxHeight: '92vh',
          display: 'flex', flexDirection: 'column', padding: 0,
          background: 'var(--bg-card)', borderRadius: 14, overflow: 'hidden', boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.3)'
        }
      },
        // Modal Header
        React.createElement('div', {
          style: {
            padding: '16px 24px', borderBottom: '1px solid var(--border)',
            display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: 'var(--bg-card-header)'
          }
        },
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 10 } },
            React.createElement('span', { style: { fontSize: '1.4rem' } }, '📦'),
            React.createElement('div', null,
              React.createElement('h3', { style: { margin: 0, fontSize: '1.15rem', fontWeight: 700 } }, 'Bulk Product Add (Batch Mode)'),
              React.createElement('div', { style: { fontSize: '0.78rem', color: 'var(--text-secondary)', marginTop: 2 } },
                'Fast keyboard entry: Enter moves between cells, auto-creates rows on last cell. Press Ctrl+Enter to save.'
              )
            )
          ),
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
            React.createElement('button', {
              className: 'btn btn-outline btn-sm',
              onClick: () => setShowPasteBox(!showPasteBox),
              style: { display: 'flex', alignItems: 'center', gap: 6 }
            }, '📋 Paste from Excel / CSV'),
            React.createElement('button', {
              className: 'btn btn-ghost btn-sm',
              onClick: onClose,
              style: { fontSize: '1.2rem', padding: '4px 10px' }
            }, '✕')
          )
        ),

        // Paste box modal section
        showPasteBox && React.createElement('div', {
          style: {
            background: 'var(--bg-secondary)', padding: 16, borderBottom: '1px solid var(--border)',
            display: 'flex', flexDirection: 'column', gap: 10
          }
        },
          React.createElement('div', { style: { fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-primary)' } },
            'Paste tab-separated (from Excel/Sheets) or comma-separated rows with columns: Name, SKU, Barcode, Category, Price, CostPrice, GST%, InitialStock, MinStock, Unit, ItemType'
          ),
          React.createElement('textarea', {
            className: 'form-control',
            rows: 4,
            placeholder: 'Paste rows here...',
            value: pasteText,
            onChange: (e) => setPasteText(e.target.value),
            style: { fontFamily: 'monospace', fontSize: '0.78rem' }
          }),
          React.createElement('div', { style: { display: 'flex', justifyContent: 'flex-end', gap: 8 } },
            React.createElement('button', { className: 'btn btn-ghost btn-xs', onClick: () => setShowPasteBox(false) }, 'Cancel'),
            React.createElement('button', { className: 'btn btn-primary btn-xs', onClick: handlePasteProcess }, 'Parse & Insert Rows')
          )
        ),

        // Error message banner
        errorMsg && React.createElement('div', {
          style: {
            background: 'rgba(239, 68, 68, 0.1)', color: '#dc2626', padding: '10px 20px',
            borderBottom: '1px solid rgba(239, 68, 68, 0.25)', fontSize: '0.82rem', fontWeight: 600,
            display: 'flex', alignItems: 'center', gap: 8
          }
        }, '⚠️ ', errorMsg),

        // Table Content
        React.createElement('div', {
          style: { flex: 1, overflow: 'auto', padding: '12px 20px' }
        },
          React.createElement('table', { className: 'data-table bulk-product-table', style: { width: '100%', minWidth: 1050, fontSize: '0.8rem' } },
            React.createElement('thead', null,
              React.createElement('tr', null,
                React.createElement('th', { style: { width: 40, textAlign: 'center' } }, '#'),
                React.createElement('th', { style: { minWidth: 200 } }, 'Product Name *'),
                React.createElement('th', { style: { width: 110 } }, 'SKU'),
                React.createElement('th', { style: { width: 110 } }, 'Barcode'),
                React.createElement('th', { style: { width: 130 } }, 'Category'),
                React.createElement('th', { style: { width: 100 } }, 'Type'),
                React.createElement('th', { style: { width: 110, textAlign: 'right' } }, 'Price (₹)'),
                React.createElement('th', { style: { width: 110, textAlign: 'right' } }, 'Cost Price (₹)'),
                React.createElement('th', { style: { width: 90, textAlign: 'right' } }, 'GST %'),
                React.createElement('th', { style: { width: 90, textAlign: 'right' } }, 'Init Stock'),
                React.createElement('th', { style: { width: 90, textAlign: 'right' } }, 'Min Stock'),
                React.createElement('th', { style: { width: 80 } }, 'Unit'),
                React.createElement('th', { style: { width: 40, textAlign: 'center' } }, '')
              )
            ),
            React.createElement('tbody', null,
              rows.map((row, idx) => React.createElement('tr', { key: row._id || idx },
                // #
                React.createElement('td', { style: { textAlign: 'center', color: 'var(--text-secondary)', fontWeight: 600 } }, idx + 1),
                // 0: Name
                React.createElement('td', null,
                  React.createElement('input', {
                    type: 'text',
                    placeholder: 'e.g. Basmati Rice 5kg',
                    className: 'form-control form-control-sm',
                    style: { width: '100%' },
                    value: row.name,
                    onChange: (e) => updateRow(idx, 'name', e.target.value),
                    onFocus: (e) => { if (e.target && e.target.select) e.target.select(); },
                    onKeyDown: (e) => handleCellKeyDown(e, idx, 0)
                  })
                ),
                // 1: SKU
                React.createElement('td', null,
                  React.createElement('input', {
                    type: 'text',
                    placeholder: 'SKU-001',
                    className: 'form-control form-control-sm',
                    value: row.sku,
                    onChange: (e) => updateRow(idx, 'sku', e.target.value),
                    onFocus: (e) => { if (e.target && e.target.select) e.target.select(); },
                    onKeyDown: (e) => handleCellKeyDown(e, idx, 1)
                  })
                ),
                // 2: Barcode
                React.createElement('td', null,
                  React.createElement('input', {
                    type: 'text',
                    placeholder: '890123...',
                    className: 'form-control form-control-sm',
                    value: row.barcode,
                    onChange: (e) => updateRow(idx, 'barcode', e.target.value),
                    onFocus: (e) => { if (e.target && e.target.select) e.target.select(); },
                    onKeyDown: (e) => handleCellKeyDown(e, idx, 2)
                  })
                ),
                // 3: Category
                React.createElement('td', null,
                  React.createElement('input', {
                    type: 'text',
                    placeholder: 'Grains',
                    list: 'bulk-cat-list',
                    className: 'form-control form-control-sm',
                    value: row.category,
                    onChange: (e) => updateRow(idx, 'category', e.target.value),
                    onFocus: (e) => { if (e.target && e.target.select) e.target.select(); },
                    onKeyDown: (e) => handleCellKeyDown(e, idx, 3)
                  })
                ),
                // 4: Type
                React.createElement('td', null,
                  React.createElement('select', {
                    className: 'form-control form-control-sm',
                    value: row.itemType,
                    onChange: (e) => updateRow(idx, 'itemType', e.target.value),
                    onKeyDown: (e) => handleCellKeyDown(e, idx, 4)
                  },
                    React.createElement('option', { value: 'GOODS' }, 'Goods'),
                    React.createElement('option', { value: 'SERVICE' }, 'Service')
                  )
                ),
                // 5: Price
                React.createElement('td', null,
                  React.createElement('input', {
                    type: 'number',
                    step: 'any',
                    placeholder: '0.00',
                    className: 'form-control form-control-sm',
                    style: { textAlign: 'right' },
                    value: row.price,
                    onChange: (e) => updateRow(idx, 'price', e.target.value),
                    onFocus: (e) => { if (e.target && e.target.select) e.target.select(); },
                    onKeyDown: (e) => handleCellKeyDown(e, idx, 5)
                  })
                ),
                // 6: Cost Price
                React.createElement('td', null,
                  React.createElement('input', {
                    type: 'number',
                    step: 'any',
                    placeholder: '0.00',
                    className: 'form-control form-control-sm',
                    style: { textAlign: 'right' },
                    value: row.costPrice,
                    onChange: (e) => updateRow(idx, 'costPrice', e.target.value),
                    onFocus: (e) => { if (e.target && e.target.select) e.target.select(); },
                    onKeyDown: (e) => handleCellKeyDown(e, idx, 6)
                  })
                ),
                // 7: GST %
                React.createElement('td', null,
                  React.createElement('input', {
                    type: 'number',
                    step: 'any',
                    placeholder: '0',
                    className: 'form-control form-control-sm',
                    style: { textAlign: 'right' },
                    value: row.gstPercentage,
                    onChange: (e) => updateRow(idx, 'gstPercentage', e.target.value),
                    onFocus: (e) => { if (e.target && e.target.select) e.target.select(); },
                    onKeyDown: (e) => handleCellKeyDown(e, idx, 7)
                  })
                ),
                // 8: Initial Stock
                React.createElement('td', null,
                  React.createElement('input', {
                    type: 'number',
                    step: 'any',
                    disabled: row.itemType === 'SERVICE',
                    placeholder: '0',
                    className: 'form-control form-control-sm',
                    style: { textAlign: 'right' },
                    value: row.initialStock,
                    onChange: (e) => updateRow(idx, 'initialStock', e.target.value),
                    onFocus: (e) => { if (e.target && e.target.select) e.target.select(); },
                    onKeyDown: (e) => handleCellKeyDown(e, idx, 8)
                  })
                ),
                // 9: Min Stock
                React.createElement('td', null,
                  React.createElement('input', {
                    type: 'number',
                    step: 'any',
                    disabled: row.itemType === 'SERVICE',
                    placeholder: '5',
                    className: 'form-control form-control-sm',
                    style: { textAlign: 'right' },
                    value: row.minStockLevel,
                    onChange: (e) => updateRow(idx, 'minStockLevel', e.target.value),
                    onFocus: (e) => { if (e.target && e.target.select) e.target.select(); },
                    onKeyDown: (e) => handleCellKeyDown(e, idx, 9)
                  })
                ),
                // 10: Unit
                React.createElement('td', null,
                  React.createElement('input', {
                    type: 'text',
                    placeholder: 'pcs',
                    className: 'form-control form-control-sm',
                    value: row.unit,
                    onChange: (e) => updateRow(idx, 'unit', e.target.value),
                    onFocus: (e) => { if (e.target && e.target.select) e.target.select(); },
                    onKeyDown: (e) => handleCellKeyDown(e, idx, 10)
                  })
                ),
                // Delete Row Action
                React.createElement('td', { style: { textAlign: 'center' } },
                  React.createElement('button', {
                    className: 'btn btn-ghost btn-xs',
                    style: { color: '#ef4444', padding: '2px 6px' },
                    title: 'Remove Row',
                    onClick: () => removeRow(idx)
                  }, '✕')
                )
              ))
            )
          ),
          React.createElement('datalist', { id: 'bulk-cat-list' },
            existingCategories.map(c => React.createElement('option', { key: c, value: c }))
          )
        ),

        // Modal Footer
        React.createElement('div', {
          style: {
            padding: '14px 24px', borderTop: '1px solid var(--border)', background: 'var(--bg-card-header)',
            display: 'flex', alignItems: 'center', justifyContent: 'space-between'
          }
        },
          React.createElement('div', { style: { display: 'flex', gap: 8 } },
            React.createElement('button', {
              className: 'btn btn-outline btn-sm',
              onClick: () => addRows(1)
            }, '+ Add 1 Row'),
            React.createElement('button', {
              className: 'btn btn-outline btn-sm',
              onClick: () => addRows(5)
            }, '+ Add 5 Rows'),
            React.createElement('button', {
              className: 'btn btn-ghost btn-sm',
              onClick: () => setRows(rows.filter(r => r.name && r.name.trim()).concat([createEmptyRow()]))
            }, '🧹 Clean Empty')
          ),
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 10 } },
            React.createElement('span', { style: { fontSize: '0.8rem', color: 'var(--text-secondary)' } },
              `${rows.filter(r => r.name && r.name.trim()).length} valid product(s) ready`
            ),
            React.createElement('button', {
              className: 'btn btn-ghost',
              onClick: onClose,
              disabled: saving
            }, 'Cancel'),
            React.createElement('button', {
              className: 'btn btn-primary',
              disabled: saving || rows.filter(r => r.name && r.name.trim()).length === 0,
              onClick: handleSaveBatch,
              style: { fontWeight: 700 }
            }, saving ? '⏳ Creating Products...' : '💾 Save Batch (Atomic Import)')
          )
        )
      )
    );
  }

  // ═══════════════════════════════════════════════════════════════════
  // 6. PRICING PROFILES & CATEGORY MATRIX MANAGEMENT
  // ═══════════════════════════════════════════════════════════════════
  function PricingProfileManager() {
    const [profiles, setProfiles] = useState([]);
    const [rules, setRules] = useState([]);
    const [categories, setCategories] = useState([]);
    const [products, setProducts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [savingMatrix, setSavingMatrix] = useState(false);
    const [simProfileId, setSimProfileId] = useState(null);
    const [effectivePriceMap, setEffectivePriceMap] = useState({});

    // Profile form modal state
    const [showProfileModal, setShowProfileModal] = useState(false);
    const [editProfile, setEditProfile] = useState(null);
    const [profileForm, setProfileForm] = useState({
      name: '',
      code: '',
      description: '',
      isDefault: false,
      isActive: true
    });

    // Local matrix edits state: { `${category}__${profileId}`: { adjustmentType: 'PERCENT'|'FIXED', adjustmentValue: number } }
    const [matrixEdits, setMatrixEdits] = useState({});
    const [pricingSubTab, setPricingSubTab] = useState('PROFILES');
    const [simPage, setSimPage] = useState(1);
    const simPageSize = 8;

    const loadAll = useCallback(async () => {
      setLoading(true);
      try {
        const [profList, ruleList, catList, prodList] = await Promise.all([
          API.pricingProfiles.list(),
          API.categoryPricingRules.list().catch(() => []),
          API.products.categories().catch(() => []),
          API.products.list().catch(() => [])
        ]);

        setProfiles(profList || []);
        setRules(ruleList || []);
        setProducts(prodList || []);

        // Aggregate unique categories from both products and explicit category rules
        const catSet = new Set(catList || []);
        (prodList || []).forEach(p => { if (p.category) catSet.add(p.category.trim()); });
        (ruleList || []).forEach(r => { if (r.categoryName) catSet.add(r.categoryName.trim()); });
        const allCats = Array.from(catSet).sort();
        setCategories(allCats);

        // Populate matrixEdits map from rules
        const map = {};
        (ruleList || []).forEach(r => {
          if (r.categoryName && r.pricingProfileId) {
            const key = `${r.categoryName.trim()}__${r.pricingProfileId}`;
            map[key] = {
              adjustmentType: r.adjustmentType || 'PERCENT',
              adjustmentValue: r.adjustmentValue != null ? String(r.adjustmentValue) : '0'
            };
          }
        });
        setMatrixEdits(map);

        // Default simulation profile to first default or first profile
        const defProf = (profList || []).find(p => p.isDefault) || (profList || [])[0];
        if (defProf && !simProfileId) {
          setSimProfileId(defProf.id);
        }
      } catch (err) {
        console.error('Failed to load pricing profiles and rules:', err);
        if (typeof showToast === 'function') showToast('Failed to load pricing profiles', 'error');
      } finally {
        setLoading(false);
      }
    }, [simProfileId]);

    useEffect(() => {
      loadAll();
    }, [loadAll]);

    // Load effective prices when simulator profile changes
    useEffect(() => {
      if (simProfileId) {
        API.products.getEffectivePrices(simProfileId)
          .then(res => {
            if (res && res.effectivePrices) {
              setEffectivePriceMap(res.effectivePrices);
            }
          })
          .catch(err => console.error('Failed to fetch effective prices:', err));
      }
    }, [simProfileId, rules]);

    const handleOpenCreateProfile = () => {
      setEditProfile(null);
      setProfileForm({
        name: '',
        code: '',
        description: '',
        isDefault: false,
        isActive: true
      });
      setShowProfileModal(true);
    };

    const handleOpenEditProfile = (p) => {
      setEditProfile(p);
      setProfileForm({
        name: p.name || '',
        code: p.code || '',
        description: p.description || '',
        isDefault: Boolean(p.isDefault),
        isActive: p.isActive != null ? p.isActive : true
      });
      setShowProfileModal(true);
    };

    const handleSaveProfile = async () => {
      if (!profileForm.name || !profileForm.name.trim()) {
        if (typeof showToast === 'function') showToast('Profile name is required', 'warning');
        return;
      }
      try {
        if (editProfile) {
          await API.pricingProfiles.update(editProfile.id, profileForm);
          if (typeof showToast === 'function') showToast('Pricing profile updated', 'success');
        } else {
          await API.pricingProfiles.create(profileForm);
          if (typeof showToast === 'function') showToast('Pricing profile created', 'success');
        }
        setShowProfileModal(false);
        loadAll();
      } catch (err) {
        console.error(err);
        if (typeof showToast === 'function') showToast(err.message || 'Failed to save profile', 'error');
      }
    };

    const handleDeleteProfile = async (id, name) => {
      if (!confirm(`Are you sure you want to delete pricing profile "${name}"? All associated category rules will also be deleted.`)) {
        return;
      }
      try {
        await API.pricingProfiles.delete(id);
        if (typeof showToast === 'function') showToast('Pricing profile deleted', 'success');
        loadAll();
      } catch (err) {
        console.error(err);
        if (typeof showToast === 'function') showToast(err.message || 'Failed to delete profile', 'error');
      }
    };

    const matrixContainerRef = React.useRef(null);
    const profileModalRef = React.useRef(null);

    const focusMatrixControl = (catIdx, profIdx, controlType = 'input') => {
      const container = matrixContainerRef.current;
      if (!container) return;
      const tableRows = container.querySelectorAll('.category-matrix-table tbody tr');
      const targetRow = tableRows[catIdx];
      if (!targetRow) return;
      const cells = targetRow.querySelectorAll('td');
      // cells[0] is the category header TD, so profile cells start at cells[profIdx + 1]
      const targetCell = cells[profIdx + 1];
      if (!targetCell) return;
      const target = controlType === 'select' ? targetCell.querySelector('select') : targetCell.querySelector('input');
      if (target) {
        target.focus();
        if (target.select) target.select();
      }
    };

    const handleMatrixKeyDown = (e, catIdx, profIdx, controlType) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
        e.preventDefault();
        handleSaveCategoryMatrix();
        return;
      }

      const numCats = categories.length;
      const numProfs = profiles.length;

      if (e.key === 'Enter') {
        e.preventDefault();
        if (controlType === 'select') {
          focusMatrixControl(catIdx, profIdx, 'input');
        } else {
          // From input: move to next profile or next row
          if (profIdx < numProfs - 1) {
            focusMatrixControl(catIdx, profIdx + 1, 'input');
          } else if (catIdx < numCats - 1) {
            focusMatrixControl(catIdx + 1, 0, 'input');
          } else {
            handleSaveCategoryMatrix();
          }
        }
      } else if (e.key === 'ArrowUp') {
        if (catIdx > 0) {
          e.preventDefault();
          focusMatrixControl(catIdx - 1, profIdx, controlType);
        }
      } else if (e.key === 'ArrowDown') {
        if (catIdx < numCats - 1) {
          e.preventDefault();
          focusMatrixControl(catIdx + 1, profIdx, controlType);
        }
      } else if (e.key === 'ArrowRight') {
        const isInput = controlType === 'input';
        const atEnd = !isInput || (e.target.selectionStart === e.target.value.length);
        if (atEnd) {
          if (controlType === 'select') {
            e.preventDefault();
            focusMatrixControl(catIdx, profIdx, 'input');
          } else if (profIdx < numProfs - 1) {
            e.preventDefault();
            focusMatrixControl(catIdx, profIdx + 1, 'input');
          } else if (catIdx < numCats - 1) {
            e.preventDefault();
            focusMatrixControl(catIdx + 1, 0, 'input');
          }
        }
      } else if (e.key === 'ArrowLeft') {
        const isInput = controlType === 'input';
        const atStart = !isInput || (e.target.selectionStart === 0);
        if (atStart) {
          if (controlType === 'input') {
            e.preventDefault();
            focusMatrixControl(catIdx, profIdx, 'select');
          } else if (profIdx > 0) {
            e.preventDefault();
            focusMatrixControl(catIdx, profIdx - 1, 'input');
          } else if (catIdx > 0) {
            e.preventDefault();
            focusMatrixControl(catIdx - 1, numProfs - 1, 'input');
          }
        }
      }
    };

    const updateMatrixCell = (category, profileId, field, value) => {
      const key = `${category}__${profileId}`;
      setMatrixEdits(prev => {
        const curr = prev[key] || { adjustmentType: 'PERCENT', adjustmentValue: '0' };
        return {
          ...prev,
          [key]: { ...curr, [field]: value }
        };
      });
    };

    const handleSaveCategoryMatrix = async () => {
      setSavingMatrix(true);
      try {
        const rulesList = [];
        categories.forEach(cat => {
          profiles.forEach(prof => {
            const key = `${cat}__${prof.id}`;
            const cell = matrixEdits[key];
            if (cell) {
              const valNum = parseFloat(cell.adjustmentValue) || 0;
              // Save rule if non-zero or explicitly configured
              rulesList.push({
                categoryName: cat,
                pricingProfileId: prof.id,
                adjustmentType: cell.adjustmentType || 'PERCENT',
                adjustmentValue: valNum
              });
            }
          });
        });

        const saved = await API.categoryPricingRules.save(rulesList);
        setRules(saved || []);
        if (typeof showToast === 'function') showToast('Category Pricing Matrix saved successfully!', 'success');
        // Refresh simulator
        if (simProfileId) {
          API.products.getEffectivePrices(simProfileId).then(res => {
            if (res && res.effectivePrices) setEffectivePriceMap(res.effectivePrices);
          });
        }
      } catch (err) {
        console.error(err);
        if (typeof showToast === 'function') showToast(err.message || 'Failed to save category pricing matrix', 'error');
      } finally {
        setSavingMatrix(false);
      }
    };

    const simTotalPages = Math.max(1, Math.ceil(products.length / simPageSize));
    const safeSimPage = Math.min(simPage, simTotalPages);
    const paginatedSimProducts = products.slice((safeSimPage - 1) * simPageSize, safeSimPage * simPageSize);

    return React.createElement('div', { style: { display: 'flex', flexDirection: 'column', gap: 10, height: '100%' } },
      // Subtab Segmented Bar
      React.createElement('div', {
        className: 'card',
        style: {
          padding: '8px 14px', marginBottom: 2, display: 'flex',
          justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10
        }
      },
        React.createElement('div', { style: { display: 'flex', gap: 8, alignItems: 'center' } },
          React.createElement('button', {
            className: `btn btn-sm ${pricingSubTab === 'PROFILES' ? 'btn-primary' : 'btn-outline'}`,
            style: { padding: '4px 12px', fontSize: '0.82rem', fontWeight: 600 },
            onClick: () => setPricingSubTab('PROFILES')
          }, `🏷️ Pricing Profiles (${profiles.length})`),
          React.createElement('button', {
            className: `btn btn-sm ${pricingSubTab === 'MATRIX' ? 'btn-primary' : 'btn-outline'}`,
            style: { padding: '4px 12px', fontSize: '0.82rem', fontWeight: 600 },
            onClick: () => setPricingSubTab('MATRIX')
          }, `📊 Category Matrix (${categories.length} Categories)`),
          React.createElement('button', {
            className: `btn btn-sm ${pricingSubTab === 'SIMULATOR' ? 'btn-primary' : 'btn-outline'}`,
            style: { padding: '4px 12px', fontSize: '0.82rem', fontWeight: 600 },
            onClick: () => setPricingSubTab('SIMULATOR')
          }, '🔮 Live Effective Simulator')
        ),
        pricingSubTab === 'PROFILES' && React.createElement('button', {
          className: 'btn btn-sm btn-primary',
          onClick: handleOpenCreateProfile,
          style: { display: 'flex', alignItems: 'center', gap: 6, fontWeight: 700 }
        }, '+ New Pricing Profile'),
        pricingSubTab === 'MATRIX' && React.createElement('button', {
          className: 'btn btn-sm btn-primary',
          disabled: savingMatrix,
          onClick: handleSaveCategoryMatrix,
          style: { fontWeight: 700, display: 'flex', alignItems: 'center', gap: 6 }
        }, savingMatrix ? '⏳ Saving Matrix...' : '💾 Save Category Pricing Matrix'),
        pricingSubTab === 'SIMULATOR' && React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
          React.createElement('label', { style: { fontSize: '0.8rem', fontWeight: 600 } }, 'Profile:'),
          React.createElement('select', {
            className: 'form-control form-control-sm',
            style: { width: 'auto', fontWeight: 600, height: 28, fontSize: '0.78rem' },
            value: simProfileId || '',
            onChange: (e) => setSimProfileId(parseInt(e.target.value, 10))
          },
            profiles.map(p => React.createElement('option', { key: p.id, value: p.id },
              `${p.name} ${p.isDefault ? '(Default)' : `(${p.code})`}`
            ))
          )
        )
      ),

      // Tab 1: Pricing Profiles Cards
      pricingSubTab === 'PROFILES' && React.createElement('div', {
        className: 'card firm-table-card',
        style: { padding: '14px 18px' }
      },
        React.createElement('div', {
          style: {
            display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 12
          }
        },
          loading ? React.createElement('div', { style: { padding: 20, color: 'var(--text-secondary)' } }, 'Loading profiles...') :
            profiles.map(p => React.createElement('div', {
              key: p.id,
              className: 'card',
              style: {
                padding: '12px 14px', background: 'var(--bg-secondary)', border: p.isDefault ? '1.5px solid #6366f1' : '1px solid var(--border)',
                borderRadius: 10, position: 'relative', display: 'flex', flexDirection: 'column', justifyContent: 'space-between'
              }
            },
              React.createElement('div', null,
                React.createElement('div', { style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 } },
                  React.createElement('div', { style: { fontWeight: 700, fontSize: '0.92rem', color: 'var(--text-primary)' } }, p.name),
                  React.createElement('div', { style: { display: 'flex', gap: 4 } },
                    p.isDefault && React.createElement('span', {
                      className: 'badge',
                      style: { background: 'rgba(99, 102, 241, 0.15)', color: '#4f46e5', fontWeight: 700, fontSize: '0.68rem' }
                    }, '★ DEFAULT'),
                    !p.isActive && React.createElement('span', {
                      className: 'badge',
                      style: { background: 'rgba(239, 68, 68, 0.1)', color: '#ef4444', fontSize: '0.68rem' }
                    }, 'INACTIVE')
                  )
                ),
                React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)', marginTop: 4, fontFamily: 'monospace' } },
                  `Code: ${p.code || 'N/A'}`
                ),
                React.createElement('div', { style: { fontSize: '0.76rem', color: 'var(--text-secondary)', marginTop: 4, minHeight: 28 } },
                  p.description || 'No description provided.'
                )
              ),
              React.createElement('div', {
                style: {
                  display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 6, marginTop: 8,
                  borderTop: '1px solid var(--border)', paddingTop: 6
                }
              },
                React.createElement('button', {
                  className: 'btn btn-ghost btn-xs',
                  onClick: () => handleOpenEditProfile(p)
                }, '✏️ Edit'),
                !p.isDefault && React.createElement('button', {
                  className: 'btn btn-ghost btn-xs',
                  style: { color: '#ef4444' },
                  onClick: () => handleDeleteProfile(p.id, p.name)
                }, '🗑️ Delete')
              )
            ))
        )
      ),

      // Tab 2: Category Pricing Adjustment Matrix
      pricingSubTab === 'MATRIX' && React.createElement('div', {
        className: 'card firm-table-card',
        ref: matrixContainerRef,
        style: { display: 'flex', flexDirection: 'column' }
      },
        React.createElement('div', { style: { padding: '8px 14px', background: 'var(--bg-secondary)', borderBottom: '1px solid var(--border)', fontSize: '0.76rem', color: 'var(--text-secondary)' } },
          '💡 Configure percentage markup (+%), discount (-%), or fixed rupee adjustments (₹). Press Enter or Arrow keys to move between cells. Ctrl+Enter to save.'
        ),
        categories.length === 0 ? React.createElement('div', {
          style: { padding: 30, textAlign: 'center', color: 'var(--text-secondary)' }
        }, 'No categories found. Create products with categories to populate the matrix.') :
          React.createElement('div', { className: 'table-container' },
            React.createElement('table', { className: 'data-table category-matrix-table', style: { width: '100%', margin: 0, fontSize: '0.82rem' } },
              React.createElement('thead', null,
                React.createElement('tr', null,
                  React.createElement('th', { style: { minWidth: 160, background: 'var(--bg-secondary)', fontWeight: 700 } }, 'Category Name'),
                  profiles.map(p => React.createElement('th', {
                    key: p.id,
                    style: { minWidth: 180, textAlign: 'center', background: p.isDefault ? 'rgba(99, 102, 241, 0.08)' : 'inherit' }
                  },
                    React.createElement('div', { style: { fontWeight: 700, color: 'var(--text-primary)' } }, p.name),
                    React.createElement('div', { style: { fontSize: '0.7rem', color: 'var(--text-secondary)', fontWeight: 400 } },
                      p.isDefault ? '(Default Base)' : p.code
                    )
                  ))
                )
              ),
              React.createElement('tbody', null,
                categories.map((cat, catIdx) => React.createElement('tr', { key: cat },
                  React.createElement('td', { style: { fontWeight: 600, background: 'var(--bg-secondary)', color: 'var(--text-primary)' } },
                    `📁 ${cat}`
                  ),
                  profiles.map((prof, profIdx) => {
                    const key = `${cat}__${prof.id}`;
                    const cell = matrixEdits[key] || { adjustmentType: 'PERCENT', adjustmentValue: '0' };
                    const isPct = (cell.adjustmentType || 'PERCENT') === 'PERCENT';
                    const numVal = parseFloat(cell.adjustmentValue) || 0;
                    return React.createElement('td', {
                      key: prof.id,
                      style: {
                        textAlign: 'center', verticalAlign: 'middle',
                        background: prof.isDefault ? 'rgba(99, 102, 241, 0.02)' : 'inherit'
                      }
                    },
                      React.createElement('div', { style: { display: 'inline-flex', alignItems: 'center', gap: 4, background: 'var(--bg-card)', padding: '2px 5px', borderRadius: 6, border: '1px solid var(--border)' } },
                        React.createElement('select', {
                          className: 'form-control form-control-sm',
                          style: { width: 50, padding: '2px 4px', height: 24, fontSize: '0.75rem', fontWeight: 600 },
                          value: cell.adjustmentType || 'PERCENT',
                          onChange: (e) => updateMatrixCell(cat, prof.id, 'adjustmentType', e.target.value),
                          onKeyDown: (e) => handleMatrixKeyDown(e, catIdx, profIdx, 'select')
                        },
                          React.createElement('option', { value: 'PERCENT' }, '%'),
                          React.createElement('option', { value: 'FIXED' }, '₹')
                        ),
                        React.createElement('input', {
                          type: 'number',
                          step: 'any',
                          placeholder: '0',
                          className: 'form-control form-control-sm',
                          style: {
                            width: 70, textAlign: 'right', padding: '2px 6px', height: 24, fontSize: '0.8rem',
                            fontWeight: numVal !== 0 ? 700 : 400,
                            color: numVal > 0 ? '#10b981' : (numVal < 0 ? '#ef4444' : 'inherit')
                          },
                          value: cell.adjustmentValue != null ? cell.adjustmentValue : '',
                          onChange: (e) => updateMatrixCell(cat, prof.id, 'adjustmentValue', e.target.value),
                          onFocus: (e) => { if (e.target && e.target.select) e.target.select(); },
                          onKeyDown: (e) => handleMatrixKeyDown(e, catIdx, profIdx, 'input')
                        }),
                        React.createElement('span', {
                          style: { fontSize: '0.7rem', color: 'var(--text-secondary)', minWidth: 16, textAlign: 'left' }
                        }, isPct ? '%' : '₹')
                      )
                    );
                  })
                ))
              )
            )
          )
      ),

      // Tab 3: Live Effective Price Simulator
      pricingSubTab === 'SIMULATOR' && React.createElement('div', {
        className: 'card firm-table-card'
      },
        React.createElement('div', { className: 'table-container' },
          React.createElement('table', { className: 'data-table', style: { width: '100%', margin: 0, fontSize: '0.8rem' } },
            React.createElement('thead', null,
              React.createElement('tr', null,
                React.createElement('th', null, 'Product Name'),
                React.createElement('th', null, 'SKU'),
                React.createElement('th', null, 'Category'),
                React.createElement('th', { style: { textAlign: 'right' } }, 'Base Price (₹)'),
                React.createElement('th', { style: { textAlign: 'center' } }, 'Applied Rule'),
                React.createElement('th', { style: { textAlign: 'right', color: '#4f46e5' } }, 'Effective Selling Price (₹)')
              )
            ),
            React.createElement('tbody', null,
              paginatedSimProducts.map(prod => {
                const effective = effectivePriceMap[prod.id] != null ? effectivePriceMap[prod.id] : prod.price;
                const base = Number(prod.price || 0);
                const diff = Number(effective) - base;
                return React.createElement('tr', { key: prod.id },
                  React.createElement('td', { style: { fontWeight: 600 } }, prod.name),
                  React.createElement('td', { style: { color: 'var(--text-secondary)' } }, prod.sku || '—'),
                  React.createElement('td', null,
                    prod.category ? React.createElement('span', { className: 'badge', style: { fontSize: '0.72rem' } }, prod.category) : '—'
                  ),
                  React.createElement('td', { style: { textAlign: 'right', fontWeight: 600 } }, fmtCurrency(prod.price)),
                  React.createElement('td', { style: { textAlign: 'center' } },
                    diff === 0 ? React.createElement('span', { style: { color: 'var(--text-secondary)', fontSize: '0.75rem' } }, 'Standard (0%)') :
                      React.createElement('span', {
                        style: {
                          fontSize: '0.75rem', fontWeight: 700,
                          color: diff > 0 ? '#10b981' : '#ef4444'
                        }
                      }, `${diff > 0 ? '+' : ''}${fmtCurrency(diff)} (${((diff / (base || 1)) * 100).toFixed(1)}%)`)
                  ),
                  React.createElement('td', { style: { textAlign: 'right', fontWeight: 800, fontSize: '0.9rem', color: '#4f46e5' } },
                    fmtCurrency(effective)
                  )
                );
              })
            )
          )
        ),
        React.createElement('div', { className: 'firm-docked-pagination' },
          React.createElement(PaginationControls, {
            currentPage: safeSimPage,
            totalPages: simTotalPages,
            totalItems: products.length,
            pageSize: simPageSize,
            compact: true,
            onPageChange: setSimPage
          })
        )
      ),

      // Profile Modal (Create / Edit)
      showProfileModal && React.createElement('div', {
        className: 'modal-backdrop',
        ref: profileModalRef,
        style: {
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(15, 23, 42, 0.65)', backdropFilter: 'blur(4px)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1200, padding: 16
        },
        onClick: (e) => { if (e.target === e.currentTarget) setShowProfileModal(false); },
        onKeyDown: (e) => {
          if (e.key === 'Escape') {
            e.preventDefault();
            setShowProfileModal(false);
          } else if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
            e.preventDefault();
            handleSaveProfile();
          }
        }
      },
        React.createElement('div', {
          className: 'card',
          style: {
            width: 480, maxWidth: '95vw', padding: 24, borderRadius: 12,
            background: 'var(--bg-card)', boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.3)'
          }
        },
          React.createElement('h3', { style: { margin: 0, marginBottom: 16, fontSize: '1.15rem', fontWeight: 700 } },
            editProfile ? '✏️ Edit Pricing Profile' : '➕ Create Pricing Profile'
          ),
          React.createElement('div', { style: { display: 'flex', flexDirection: 'column', gap: 14 } },
            React.createElement('div', null,
              React.createElement('label', { style: { fontSize: '0.78rem', fontWeight: 600, display: 'block', marginBottom: 4 } }, 'Profile Name *'),
              React.createElement('input', {
                type: 'text',
                autoFocus: true,
                placeholder: 'e.g. Wholesale Tier 1, VIP Corporate',
                className: 'form-control',
                value: profileForm.name,
                onChange: (e) => setProfileForm({ ...profileForm, name: e.target.value }),
                onFocus: (e) => { if (e.target && e.target.select) e.target.select(); },
                onKeyDown: (e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    const next = profileModalRef.current && profileModalRef.current.querySelector('input[name="profileCode"]');
                    if (next) next.focus();
                  }
                }
              })
            ),
            React.createElement('div', null,
              React.createElement('label', { style: { fontSize: '0.78rem', fontWeight: 600, display: 'block', marginBottom: 4 } }, 'Profile Code'),
              React.createElement('input', {
                type: 'text',
                name: 'profileCode',
                placeholder: 'WHOLESALE_1 (Auto-generated if blank)',
                className: 'form-control',
                value: profileForm.code,
                onChange: (e) => setProfileForm({ ...profileForm, code: e.target.value }),
                onFocus: (e) => { if (e.target && e.target.select) e.target.select(); },
                onKeyDown: (e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    const next = profileModalRef.current && profileModalRef.current.querySelector('textarea');
                    if (next) next.focus();
                  }
                }
              })
            ),
            React.createElement('div', null,
              React.createElement('label', { style: { fontSize: '0.78rem', fontWeight: 600, display: 'block', marginBottom: 4 } }, 'Description'),
              React.createElement('textarea', {
                rows: 2,
                placeholder: 'Optional notes or description of this tier',
                className: 'form-control',
                value: profileForm.description,
                onChange: (e) => setProfileForm({ ...profileForm, description: e.target.value }),
                onKeyDown: (e) => {
                  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
                    e.preventDefault();
                    handleSaveProfile();
                  }
                }
              })
            ),
            React.createElement('div', { style: { display: 'flex', gap: 20, marginTop: 4 } },
              React.createElement('label', { style: { display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.82rem', cursor: 'pointer' } },
                React.createElement('input', {
                  type: 'checkbox',
                  checked: profileForm.isDefault,
                  onChange: (e) => setProfileForm({ ...profileForm, isDefault: e.target.checked })
                }),
                'Set as Default Profile'
              ),
              React.createElement('label', { style: { display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.82rem', cursor: 'pointer' } },
                React.createElement('input', {
                  type: 'checkbox',
                  checked: profileForm.isActive,
                  onChange: (e) => setProfileForm({ ...profileForm, isActive: e.target.checked })
                }),
                'Active'
              )
            )
          ),
          React.createElement('div', { style: { display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 20 } },
            React.createElement('button', { className: 'btn btn-ghost', onClick: () => setShowProfileModal(false) }, 'Cancel'),
            React.createElement('button', { className: 'btn btn-primary', onClick: handleSaveProfile }, 'Save Profile')
          )
        )
      )
    );
  }

  // ═══════════════════════════════════════════════════════════════════
  // 7. PRODUCT INTELLIGENCE HUB (Tabs: Catalog, Pricing, Restock Queue)
  // ═══════════════════════════════════════════════════════════════════
  // ═══════════════════════════════════════════════════════════════════
  // 7. PRODUCT INTELLIGENCE HUB (Catalog, Master Maintenance, Pricing, Restock Queue)
  // ═══════════════════════════════════════════════════════════════════
  function ProductIntelligenceHub() {
    const [hubTab, setHubTab] = useState('INTELLIGENCE'); // 'INTELLIGENCE' | 'PRICING' | 'SMART_REORDER'
    const [showBulkAddModal, setShowBulkAddModal] = useState(false);

    const [products, setProducts] = useState([]);
    const [parties, setParties] = useState([]);
    const [loading, setLoading] = useState(true);
    const [syncing, setSyncing] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedCategory, setSelectedCategory] = useState('ALL');
    const [itemTypeFilter, setItemTypeFilter] = useState('ALL');
    const [inventoryFilter, setInventoryFilter] = useState('ALL'); // 'ALL' | 'IN_INVENTORY' | 'NOT_IN_INVENTORY'
    const [needsSetupOnly, setNeedsSetupOnly] = useState(false);
    const [selectedProductId, setSelectedProductId] = useState(null);

    // Single Row Inline Edit State
    const [editingRowId, setEditingRowId] = useState(null);
    const [singleEditForm, setSingleEditForm] = useState({});
    const [savingSingle, setSavingSingle] = useState(false);

    // Bulk Edit Mode Workspace State
    const [isBulkEditMode, setIsBulkEditMode] = useState(false);
    const [draftChanges, setDraftChanges] = useState({}); // { [productId]: { field: val, ... } }
    const [showReviewModal, setShowReviewModal] = useState(false);
    const [savingBulk, setSavingBulk] = useState(false);
    const [bulkError, setBulkError] = useState(null);
    const [showUnsavedPrompt, setShowUnsavedPrompt] = useState(false);
    const [pendingTab, setPendingTab] = useState(null);

    // Product Master Pagination State
    const [masterPage, setMasterPage] = useState(1);
    const [masterPageSize, setMasterPageSize] = useState(8);

    const bulkTableRef = React.useRef(null);

    const loadProducts = useCallback(() => {
      setLoading(true);
      Promise.all([
        API.products.list({ all: true }).catch(() => []),
        API.parties ? API.parties.list().catch(() => []) : Promise.resolve([])
      ]).then(([prods, partyList]) => {
        setProducts(prods || []);
        setParties(partyList || []);
        setLoading(false);
      }).catch(err => {
        console.error('Failed to load products or parties:', err);
        setLoading(false);
      });
    }, []);

    useEffect(() => {
      loadProducts();
    }, [loadProducts]);

    const handleSync = async () => {
      setSyncing(true);
      try {
        const res = await API.forecast.adminSync();
        if (typeof showToast === 'function') {
          showToast(`Forecast sync complete: ${res.forecastsGenerated || 0} models computed.`, 'success');
        }
        loadProducts();
      } catch (err) {
        console.error(err);
        if (typeof showToast === 'function') showToast('Failed to trigger forecast sync', 'error');
      } finally {
        setSyncing(false);
      }
    };

    const handleUseRecommendation = (prodId, suggestedQty) => {
      window.__billsoftPendingNav = {
        page: 'firm',
        tab: 'orders',
        prefillPoItem: {
          productId: prodId,
          suggestedQty: suggestedQty
        },
        ts: Date.now()
      };
      window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', {
        detail: {
          page: 'firm',
          tab: 'orders',
          prefillPoItem: {
            productId: prodId,
            suggestedQty: suggestedQty
          }
        }
      }));
    };

    const categories = useMemo(() => {
      const set = new Set();
      products.forEach(p => { if (p.category && p.category.trim()) set.add(p.category.trim()); });
      return Array.from(set).sort();
    }, [products]);

    const partyMap = useMemo(() => {
      const map = {};
      parties.forEach(pt => { map[pt.id] = pt.name || `Vendor #${pt.id}`; });
      return map;
    }, [parties]);

    // Check if a product needs setup
    const isNeedsSetup = (p) => {
      const noCat = !p.category || p.category.trim() === '' || p.category.trim().toUpperCase() === 'UNCATEGORIZED';
      const noCost = p.costPrice == null || Number(p.costPrice) <= 0;
      return noCat || noCost;
    };

    const needsSetupCount = useMemo(() => {
      return products.filter(isNeedsSetup).length;
    }, [products]);

    const filteredProducts = useMemo(() => {
      return products.filter(p => {
        if (searchQuery) {
          const q = searchQuery.toLowerCase().trim();
          const matchName = p.name && p.name.toLowerCase().includes(q);
          const matchSku = p.sku && p.sku.toLowerCase().includes(q);
          const matchBarcode = p.barcode && p.barcode.toLowerCase().includes(q);
          if (!matchName && !matchSku && !matchBarcode) return false;
        }
        if (selectedCategory === 'UNCATEGORIZED') {
          if (p.category && p.category.trim() !== '' && p.category.trim().toUpperCase() !== 'UNCATEGORIZED') {
            return false;
          }
        } else if (selectedCategory !== 'ALL') {
          if ((p.category || '').trim() !== selectedCategory) return false;
        }
        if (itemTypeFilter !== 'ALL' && (p.itemType || 'GOODS') !== itemTypeFilter) return false;
        if (inventoryFilter === 'IN_INVENTORY' && !p.inInventory) return false;
        if (inventoryFilter === 'NOT_IN_INVENTORY' && p.inInventory) return false;
        if (needsSetupOnly && !isNeedsSetup(p)) return false;
        return true;
      });
    }, [products, searchQuery, selectedCategory, itemTypeFilter, inventoryFilter, needsSetupOnly]);

    useEffect(() => {
      setMasterPage(1);
    }, [searchQuery, selectedCategory, itemTypeFilter, inventoryFilter, needsSetupOnly]);

    const totalMasterPages = Math.max(1, Math.ceil(filteredProducts.length / masterPageSize));
    const safeMasterPage = Math.min(masterPage, totalMasterPages);
    const paginatedMasterProducts = useMemo(() => {
      const start = (safeMasterPage - 1) * masterPageSize;
      return filteredProducts.slice(start, start + masterPageSize);
    }, [filteredProducts, safeMasterPage, masterPageSize]);

    // Overall Catalog Summary KPIs
    const catalogSummary = useMemo(() => {
      let totalStock = 0;
      let lowStockCount = 0;
      let serviceCount = 0;
      let untrackedCount = 0;
      products.forEach(p => {
        if ((p.itemType || 'GOODS') === 'SERVICE') {
          serviceCount++;
        } else if (!p.inInventory) {
          untrackedCount++;
        } else {
          const stock = Number(p.stockQuantity || 0);
          totalStock += stock;
          const minLvl = Number(p.minStockLevel || 5);
          if (stock <= minLvl) lowStockCount++;
        }
      });
      return {
        totalProducts: products.length,
        totalStockUnits: totalStock,
        lowStockAlerts: lowStockCount,
        serviceItems: serviceCount,
        untrackedItems: untrackedCount
      };
    }, [products]);

    // Track total modified products and fields in draft
    const changedProductIds = useMemo(() => Object.keys(draftChanges).map(Number), [draftChanges]);
    const totalChangesCount = useMemo(() => {
      let count = 0;
      Object.values(draftChanges).forEach(obj => {
        count += Object.keys(obj).length;
      });
      return count;
    }, [draftChanges]);

    // Helper to get effective cell value (draft vs original)
    const getCellVal = (p, field) => {
      if (draftChanges[p.id] && draftChanges[p.id][field] !== undefined) {
        return draftChanges[p.id][field];
      }
      return p[field] != null ? p[field] : '';
    };

    // Update draft cell
    const handleBulkCellChange = (prodId, field, newVal, originalVal) => {
      setDraftChanges(prev => {
        const prodDraft = { ...(prev[prodId] || {}) };
        const isSame = (String(newVal ?? '').trim() === String(originalVal ?? '').trim()) || (newVal === originalVal);
        if (isSame) {
          delete prodDraft[field];
        } else {
          prodDraft[field] = newVal;
        }
        const next = { ...prev };
        if (Object.keys(prodDraft).length === 0) {
          delete next[prodId];
        } else {
          next[prodId] = prodDraft;
        }
        return next;
      });
    };

    // Keyboard navigation in bulk table
    const handleBulkKeyDown = (e, rowIdx, colIdx) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
        e.preventDefault();
        if (changedProductIds.length > 0) setShowReviewModal(true);
        return;
      }
      if (e.key === 'Escape') {
        e.target.blur();
        return;
      }
      if (e.key === 'Enter') {
        e.preventDefault();
        const container = bulkTableRef.current;
        if (!container) return;
        const rows = container.querySelectorAll('tbody tr');
        const nextRow = rows[rowIdx + 1];
        if (nextRow) {
          const inputs = nextRow.querySelectorAll('input:not([type="hidden"]), select');
          const target = inputs[colIdx] || inputs[0];
          if (target) {
            target.focus();
            if (target.select) target.select();
          }
        }
      }
    };

    // Single inline edit actions
    const handleStartSingleEdit = (p) => {
      setEditingRowId(p.id);
      setSingleEditForm({
        name: p.name || '',
        sku: p.sku || '',
        barcode: p.barcode || '',
        category: p.category || '',
        price: p.price != null ? String(p.price) : '',
        costPrice: p.costPrice != null ? String(p.costPrice) : '',
        minStockLevel: p.minStockLevel != null ? String(p.minStockLevel) : '5',
        unit: p.unit || 'pcs',
        hsnCode: p.hsnCode || '',
        gstPercentage: p.gstPercentage != null ? String(p.gstPercentage) : '0',
        preferredPartyId: p.preferredPartyId ? String(p.preferredPartyId) : '',
        description: p.description || '',
        trackInInventory: !p.inInventory ? false : true,
        openingStock: '0'
      });
    };

    const handleCancelSingleEdit = () => {
      setEditingRowId(null);
      setSingleEditForm({});
    };

    const handleSaveSingleEdit = async (prodId) => {
      const form = singleEditForm;
      if (!form.name || !form.name.trim()) {
        if (typeof showToast === 'function') showToast('Product name cannot be empty', 'error');
        return;
      }
      setSavingSingle(true);
      try {
        const prod = products.find(x => x.id === prodId);
        const payload = {
          name: (form.name || '').trim(),
          sku: (form.sku || '').trim() || null,
          barcode: (form.barcode || '').trim() || null,
          category: (form.category || '').trim() || null,
          price: form.price !== '' && form.price != null ? Number(form.price) : 0,
          costPrice: form.costPrice !== '' && form.costPrice != null ? Number(form.costPrice) : 0,
          minStockLevel: form.minStockLevel !== '' && form.minStockLevel != null ? Number(form.minStockLevel) : 5,
          unit: (form.unit || 'pcs').trim(),
          hsnCode: (form.hsnCode || '').trim() || null,
          gstPercentage: form.gstPercentage !== '' && form.gstPercentage != null ? Number(form.gstPercentage) : 0,
          preferredPartyId: form.preferredPartyId ? Number(form.preferredPartyId) : null,
          description: form.description || null
        };
        if (form.trackInInventory && (!prod || !prod.inInventory)) {
          payload.inInventory = true;
          if (form.openingStock && Number(form.openingStock) > 0) {
            payload.stockQuantity = Number(form.openingStock);
          }
        }
        const updated = await API.products.update(prodId, payload);
        if (typeof showToast === 'function') showToast(`Product '${updated.name}' updated successfully.`, 'success');
        setEditingRowId(null);
        setSingleEditForm({});
        await loadProducts();
      } catch (err) {
        console.error(err);
        if (typeof showToast === 'function') showToast(err.message || 'Failed to update product', 'error');
      } finally {
        setSavingSingle(false);
      }
    };

    // Bulk Save execution
    const handleConfirmBulkSave = async () => {
      if (changedProductIds.length === 0) return;
      setSavingBulk(true);
      setBulkError(null);
      try {
        const updates = changedProductIds.map(id => {
          const changes = draftChanges[id] || {};
          const prod = products.find(x => x.id === id) || {};
          const row = { productId: id };
          if (changes.name !== undefined) row.name = changes.name.trim();
          if (changes.sku !== undefined) row.sku = changes.sku.trim();
          if (changes.barcode !== undefined) row.barcode = changes.barcode.trim();
          if (changes.category !== undefined) row.category = changes.category.trim();
          if (changes.price !== undefined) row.price = Number(changes.price);
          if (changes.costPrice !== undefined) row.costPrice = Number(changes.costPrice);
          if (changes.minStockLevel !== undefined) row.minStockLevel = Number(changes.minStockLevel);
          if (changes.unit !== undefined) row.unit = changes.unit.trim();
          if (changes.hsnCode !== undefined) row.hsnCode = changes.hsnCode.trim();
          if (changes.gstPercentage !== undefined) row.gstPercentage = Number(changes.gstPercentage);
          if (changes.preferredPartyId !== undefined) row.preferredPartyId = changes.preferredPartyId ? Number(changes.preferredPartyId) : 0;
          if (changes.description !== undefined) row.description = changes.description;
          if (changes.trackInInventory !== undefined) {
            row.trackInInventory = Boolean(changes.trackInInventory);
          }
          if (changes.openingStock !== undefined) {
            row.openingStock = Number(changes.openingStock);
          }
          return row;
        });

        const resp = await API.products.updateBulk(updates);
        if (typeof showToast === 'function') {
          showToast(`Successfully updated ${resp.updatedCount || updates.length} products.`, 'success');
        }
        setDraftChanges({});
        setShowReviewModal(false);
        setIsBulkEditMode(false);
        loadProducts();
      } catch (err) {
        console.error('Bulk save error:', err);
        setBulkError(err.message || 'Failed to save bulk changes. Check for duplicate SKUs or invalid values.');
      } finally {
        setSavingBulk(false);
      }
    };

    // Navigation Tab Guard
    const handleTabChange = (newTab) => {
      if (isBulkEditMode && totalChangesCount > 0 && newTab !== hubTab) {
        setPendingTab(newTab);
        setShowUnsavedPrompt(true);
      } else {
        setHubTab(newTab);
      }
    };

    // Diff details for Review modal
    const reviewDiffs = useMemo(() => {
      const diffs = [];
      let catChanges = 0;
      let priceChanges = 0;
      let inventoryChanges = 0;

      const FIELD_LABELS = {
        name: 'Product Name',
        sku: 'SKU Code',
        barcode: 'Barcode',
        category: 'Category',
        price: 'Base Price',
        costPrice: 'Cost Price',
        minStockLevel: 'Min Stock Level',
        unit: 'Unit',
        hsnCode: 'HSN Code',
        gstPercentage: 'GST %',
        preferredPartyId: 'Preferred Vendor',
        description: 'Description',
        trackInInventory: 'Track in Inventory',
        openingStock: 'Opening Stock'
      };

      changedProductIds.forEach(prodId => {
        const prod = products.find(x => x.id === prodId);
        if (!prod) return;
        const changes = draftChanges[prodId] || {};
        Object.keys(changes).forEach(field => {
          const oldVal = prod[field];
          const newVal = changes[field];
          if (field === 'category') catChanges++;
          if (field === 'price' || field === 'costPrice') priceChanges++;
          if (field === 'trackInInventory' || field === 'openingStock') inventoryChanges++;

          let oldDisplay = oldVal != null ? String(oldVal) : '—';
          let newDisplay = newVal != null ? String(newVal) : '—';

          if (field === 'price' || field === 'costPrice') {
            oldDisplay = fmtCurrency(oldVal || 0);
            newDisplay = fmtCurrency(newVal || 0);
          } else if (field === 'preferredPartyId') {
            oldDisplay = oldVal ? (partyMap[oldVal] || `Vendor #${oldVal}`) : 'None';
            newDisplay = newVal ? (partyMap[newVal] || `Vendor #${newVal}`) : 'None';
          } else if (field === 'category') {
            oldDisplay = oldVal || 'UNCATEGORIZED';
            newDisplay = newVal || 'UNCATEGORIZED';
          } else if (field === 'trackInInventory') {
            oldDisplay = prod.inInventory ? 'Yes (Tracked)' : 'No (Catalog Only)';
            newDisplay = newVal ? 'Yes (Track in Inventory)' : 'No';
          }

          diffs.push({
            prodId,
            prodName: prod.name,
            sku: prod.sku || 'No SKU',
            field,
            label: FIELD_LABELS[field] || field,
            oldDisplay,
            newDisplay
          });
        });
      });

      return {
        diffs,
        totalProducts: changedProductIds.length,
        catChanges,
        priceChanges,
        inventoryChanges
      };
    }, [changedProductIds, draftChanges, products, partyMap]);

    return React.createElement('div', { className: 'forecast-hub-container', style: { display: 'flex', flexDirection: 'column', gap: 16 } },
      // Category Datalist for autocomplete
      React.createElement('datalist', { id: 'master-categories-datalist' },
        categories.map(c => React.createElement('option', { key: c, value: c }))
      ),

      // Top Navigation Tabs & Quick Actions Bar
      React.createElement('div', {
        style: {
          display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12
        }
      },
        React.createElement('div', { className: 'sub-tabs-bar', style: { margin: 0 } },
          React.createElement('button', {
            className: `btn ${hubTab === 'INTELLIGENCE' ? 'btn-primary' : 'btn-ghost'}`,
            onClick: () => handleTabChange('INTELLIGENCE')
          }, '📊 Catalog & Product Master'),
          React.createElement('button', {
            className: `btn ${hubTab === 'PRICING' ? 'btn-primary' : 'btn-ghost'}`,
            onClick: () => handleTabChange('PRICING')
          }, '🏷️ Pricing Profiles & Matrix'),
          React.createElement('button', {
            className: `btn ${hubTab === 'SMART_REORDER' ? 'btn-primary' : 'btn-ghost'}`,
            onClick: () => handleTabChange('SMART_REORDER')
          }, '⚡ Smart Replenishment Queue')
        ),
        React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
          hubTab === 'INTELLIGENCE' && React.createElement('button', {
            className: `btn ${isBulkEditMode ? 'btn-secondary' : 'btn-primary'}`,
            onClick: () => {
              if (isBulkEditMode && totalChangesCount > 0) {
                if (confirm('Exit Bulk Edit Mode? Any unsaved draft changes will be discarded.')) {
                  setDraftChanges({});
                  setIsBulkEditMode(false);
                }
              } else {
                setIsBulkEditMode(!isBulkEditMode);
                setEditingRowId(null);
              }
            },
            style: { display: 'flex', alignItems: 'center', gap: 6, fontWeight: 700 }
          }, isBulkEditMode ? '✖️ Exit Bulk Edit' : '📋 Bulk Edit Mode'),

          React.createElement('button', {
            className: 'btn btn-outline',
            onClick: () => setShowBulkAddModal(true),
            style: { display: 'flex', alignItems: 'center', gap: 6 }
          }, '➕ Bulk Add Products'),

          hubTab === 'INTELLIGENCE' && React.createElement('button', {
            className: 'btn btn-outline',
            onClick: handleSync,
            disabled: syncing,
            style: { display: 'flex', alignItems: 'center', gap: 6 }
          }, syncing ? '⏳ Computing...' : '🔄 Re-sync Forecasts')
        )
      ),

      // TAB 1: PRICING PROFILES & MATRIX
      hubTab === 'PRICING' && React.createElement(PricingProfileManager, null),

      // TAB 2: SMART REORDER
      hubTab === 'SMART_REORDER' && React.createElement(SmartReorderView, {
        onOpenInsights: (prodId) => setSelectedProductId(prodId),
        onPrefillPo: handleUseRecommendation
      }),

      // TAB 3: PRODUCT MASTER & INTELLIGENCE
      hubTab === 'INTELLIGENCE' && React.createElement(React.Fragment, null,
        // Top KPI Ribbon
        React.createElement('div', { className: 'firm-kpi-ribbon' },
          React.createElement('div', { className: 'firm-kpi-ribbon-item' },
            React.createElement('span', { className: 'label' }, 'Active Products'),
            React.createElement('span', { className: 'value' }, catalogSummary.totalProducts),
            React.createElement('span', { className: 'subtext' }, `${catalogSummary.untrackedItems || 0} catalog, ${Math.max(0, catalogSummary.totalProducts - (catalogSummary.untrackedItems || 0))} tracked`)
          ),
          React.createElement('div', { className: 'firm-kpi-ribbon-item' },
            React.createElement('span', { className: 'label' }, 'Total Stock'),
            React.createElement('span', { className: 'value', style: { color: '#10b981' } }, fmtNum(catalogSummary.totalStockUnits, 0)),
            React.createElement('span', { className: 'subtext' }, 'Units in warehouse ledger')
          ),
          React.createElement('div', {
            className: 'firm-kpi-ribbon-item',
            style: { cursor: 'pointer' },
            onClick: () => {
              setInventoryFilter(inventoryFilter === 'IN_INVENTORY' ? 'ALL' : 'IN_INVENTORY');
            }
          },
            React.createElement('span', { className: 'label', style: { color: catalogSummary.lowStockAlerts > 0 ? '#f59e0b' : 'inherit' } }, 'Low Stock Alerts'),
            React.createElement('span', { className: 'value', style: { color: catalogSummary.lowStockAlerts > 0 ? '#f59e0b' : 'inherit' } }, catalogSummary.lowStockAlerts),
            React.createElement('span', { className: 'subtext' }, 'Products <= min level')
          ),
          React.createElement('div', {
            className: 'firm-kpi-ribbon-item',
            style: { cursor: 'pointer' },
            onClick: () => setNeedsSetupOnly(!needsSetupOnly)
          },
            React.createElement('span', { className: 'label', style: { color: needsSetupCount > 0 ? '#6366f1' : 'inherit' } }, 'Needs Master Setup'),
            React.createElement('span', { className: 'value', style: { color: needsSetupCount > 0 ? '#6366f1' : 'inherit' } }, needsSetupCount),
            React.createElement('span', { className: 'subtext' }, needsSetupOnly ? 'Filtered: Needs setup' : 'Missing cost/category')
          )
        ),

        // Bulk Edit Banner if active
        isBulkEditMode && React.createElement('div', {
          style: {
            background: 'linear-gradient(90deg, rgba(99, 102, 241, 0.12), rgba(16, 185, 129, 0.08))',
            border: '1px solid rgba(99, 102, 241, 0.3)',
            borderRadius: 8,
            padding: '10px 16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: 10
          }
        },
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 10 } },
            React.createElement('span', { style: { fontSize: '1.2rem' } }, '📋'),
            React.createElement('div', null,
              React.createElement('div', { style: { fontWeight: 700, color: 'var(--text-primary)' } },
                'Bulk Product Master Edit Workspace'
              ),
              React.createElement('div', { style: { fontSize: '0.8rem', color: 'var(--text-secondary)' } },
                'Edit cells directly in the table. You can search or filter categories without losing unsaved edits. Press Tab to move across cells, Enter to move down.'
              )
            )
          ),
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
            React.createElement('span', {
              className: 'badge',
              style: {
                background: totalChangesCount > 0 ? 'rgba(99,102,241,0.2)' : 'rgba(100,116,139,0.2)',
                color: totalChangesCount > 0 ? '#6366f1' : 'var(--text-secondary)',
                fontWeight: 700,
                fontSize: '0.85rem'
              }
            }, `${changedProductIds.length} products modified (${totalChangesCount} fields)`),
            React.createElement('button', {
              className: 'btn btn-primary btn-sm',
              disabled: totalChangesCount === 0,
              onClick: () => setShowReviewModal(true),
              style: { fontWeight: 700 }
            }, '🔍 Review Changes & Save')
          )
        ),

        // Filter & Action Toolbar
        React.createElement('div', {
          className: 'card',
          style: {
            padding: '8px 14px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: 10,
            marginBottom: 8
          }
        },
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', flex: 1 } },
            React.createElement('input', {
              type: 'text',
              placeholder: '🔍 Search name, SKU, or barcode...',
              className: 'form-control form-control-sm',
              style: { maxWidth: 220 },
              value: searchQuery,
              onChange: (e) => setSearchQuery(e.target.value)
            }),
            React.createElement('select', {
              className: 'form-control form-control-sm',
              style: { width: 'auto' },
              value: selectedCategory,
              onChange: (e) => setSelectedCategory(e.target.value)
            },
              React.createElement('option', { value: 'ALL' }, 'All Categories'),
              React.createElement('option', { value: 'UNCATEGORIZED' }, '⚠️ UNCATEGORIZED'),
              categories.map(c => React.createElement('option', { key: c, value: c }, c))
            ),
            React.createElement('select', {
              className: 'form-control form-control-sm',
              style: { width: 'auto' },
              value: inventoryFilter,
              onChange: (e) => setInventoryFilter(e.target.value)
            },
              React.createElement('option', { value: 'ALL' }, 'All Status'),
              React.createElement('option', { value: 'IN_INVENTORY' }, '📦 In Inventory'),
              React.createElement('option', { value: 'NOT_IN_INVENTORY' }, '📄 Catalog Only')
            ),
            React.createElement('button', {
              className: `btn ${needsSetupOnly ? 'btn-primary' : 'btn-outline'} btn-sm`,
              onClick: () => setNeedsSetupOnly(!needsSetupOnly),
              style: { display: 'flex', alignItems: 'center', gap: 4 }
            }, `⚠️ Needs Setup (${needsSetupCount})`)
          ),
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
            React.createElement('span', { style: { fontSize: '0.8rem', color: 'var(--text-secondary)' } },
              `Showing ${paginatedMasterProducts.length} of ${filteredProducts.length} products`
            )
          )
        ),

        // Product Master Table
        React.createElement('div', {
          ref: bulkTableRef,
          className: 'table-container card firm-table-card',
          style: { padding: 0, overflowX: 'auto', flex: 1, minHeight: 0 }
        },
          React.createElement('table', {
            className: 'data-table',
            style: { width: '100%', margin: 0, borderCollapse: 'collapse', minWidth: (isBulkEditMode || editingRowId) ? 1200 : '100%' }
          },
            React.createElement('thead', { style: { position: 'sticky', top: 0, background: 'var(--bg-card, #1e293b)', zIndex: 10 } },
              (isBulkEditMode || editingRowId) ? React.createElement('tr', null,
                React.createElement('th', { style: { minWidth: 200 } }, 'Product Name'),
                React.createElement('th', { style: { minWidth: 110 } }, 'SKU'),
                React.createElement('th', { style: { minWidth: 110 } }, 'Barcode'),
                React.createElement('th', { style: { minWidth: 140 } }, 'Category'),
                React.createElement('th', { style: { textAlign: 'right', minWidth: 110 } }, 'Base Price (₹)'),
                React.createElement('th', { style: { textAlign: 'right', minWidth: 110 } }, 'Cost Price (₹)'),
                React.createElement('th', { style: { textAlign: 'right', minWidth: 95 } }, 'Min Stock'),
                React.createElement('th', { style: { minWidth: 80 } }, 'Unit'),
                React.createElement('th', { style: { minWidth: 90 } }, 'HSN'),
                React.createElement('th', { style: { minWidth: 85 } }, 'GST %'),
                React.createElement('th', { style: { minWidth: 150 } }, 'Preferred Vendor'),
                React.createElement('th', { style: { minWidth: 140 } }, isBulkEditMode ? 'Track Inventory' : 'Stock Status'),
                React.createElement('th', { style: { textAlign: 'center', minWidth: isBulkEditMode ? 80 : 130 } }, 'Actions')
              ) : React.createElement('tr', null,
                React.createElement('th', { style: { width: '26%' } }, 'Product Identity'),
                React.createElement('th', { style: { width: '14%' } }, 'Category'),
                React.createElement('th', { style: { width: '18%', textAlign: 'right' } }, 'Commercials'),
                React.createElement('th', { style: { width: '14%' } }, 'Stock Status'),
                React.createElement('th', { style: { width: '14%' } }, 'Tax & Vendor'),
                React.createElement('th', { style: { width: '14%', textAlign: 'center' } }, 'Actions')
              )
            ),
            React.createElement('tbody', null,
              loading ? React.createElement('tr', null,
                React.createElement('td', { colSpan: (isBulkEditMode || editingRowId) ? 13 : 6, style: { textAlign: 'center', padding: 30, color: 'var(--text-secondary)' } },
                  'Loading product master records...'
                )
              ) : filteredProducts.length === 0 ? React.createElement('tr', null,
                React.createElement('td', { colSpan: (isBulkEditMode || editingRowId) ? 13 : 6, style: { textAlign: 'center', padding: 30, color: 'var(--text-secondary)' } },
                  'No products match the selected criteria.'
                )
              ) : paginatedMasterProducts.map((p, rowIdx) => {
                const isService = (p.itemType || 'GOODS') === 'SERVICE';
                const isTracked = Boolean(p.inInventory);
                const stock = Number(p.stockQuantity || 0);
                const minLvl = Number(p.minStockLevel || 5);
                const isModified = Boolean(draftChanges[p.id]);
                const isEditingSingle = editingRowId === p.id;

                // ── ROW IN BULK EDIT MODE ──
                if (isBulkEditMode) {
                  const valName = getCellVal(p, 'name');
                  const valSku = getCellVal(p, 'sku');
                  const valBarcode = getCellVal(p, 'barcode');
                  const valCategory = getCellVal(p, 'category');
                  const valPrice = getCellVal(p, 'price');
                  const valCostPrice = getCellVal(p, 'costPrice');
                  const valMinStock = getCellVal(p, 'minStockLevel');
                  const valUnit = getCellVal(p, 'unit');
                  const valHsn = getCellVal(p, 'hsnCode');
                  const valGst = getCellVal(p, 'gstPercentage');
                  const valVendor = getCellVal(p, 'preferredPartyId');
                  const valTrack = getCellVal(p, 'trackInInventory') !== '' ? Boolean(getCellVal(p, 'trackInInventory')) : isTracked;
                  const valOpeningStock = getCellVal(p, 'openingStock');

                  return React.createElement('tr', {
                    key: p.id,
                    style: {
                      background: isModified ? 'rgba(99, 102, 241, 0.07)' : 'transparent',
                      borderLeft: isModified ? '3px solid #6366f1' : '3px solid transparent'
                    }
                  },
                    // Product Name Input
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('input', {
                        type: 'text',
                        className: 'form-control form-control-sm',
                        value: valName,
                        style: { fontWeight: 600, width: '100%' },
                        onChange: (e) => handleBulkCellChange(p.id, 'name', e.target.value, p.name),
                        onKeyDown: (e) => handleBulkKeyDown(e, rowIdx, 0)
                      })
                    ),
                    // SKU Input
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('input', {
                        type: 'text',
                        className: 'form-control form-control-sm',
                        placeholder: 'SKU',
                        value: valSku,
                        style: { width: '100%', fontFamily: 'monospace' },
                        onChange: (e) => handleBulkCellChange(p.id, 'sku', e.target.value, p.sku),
                        onKeyDown: (e) => handleBulkKeyDown(e, rowIdx, 1)
                      })
                    ),
                    // Barcode Input
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('input', {
                        type: 'text',
                        className: 'form-control form-control-sm',
                        placeholder: 'Barcode',
                        value: valBarcode,
                        style: { width: '100%', fontFamily: 'monospace' },
                        onChange: (e) => handleBulkCellChange(p.id, 'barcode', e.target.value, p.barcode),
                        onKeyDown: (e) => handleBulkKeyDown(e, rowIdx, 2)
                      })
                    ),
                    // Category Input with Datalist
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('input', {
                        type: 'text',
                        list: 'master-categories-datalist',
                        className: 'form-control form-control-sm',
                        placeholder: 'Category',
                        value: valCategory,
                        style: { width: '100%' },
                        onChange: (e) => handleBulkCellChange(p.id, 'category', e.target.value, p.category),
                        onKeyDown: (e) => handleBulkKeyDown(e, rowIdx, 3)
                      })
                    ),
                    // Selling Price Input
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('input', {
                        type: 'number',
                        step: 'any',
                        min: '0',
                        className: 'form-control form-control-sm',
                        value: valPrice,
                        style: { textAlign: 'right', fontWeight: 600, width: '100%' },
                        onChange: (e) => handleBulkCellChange(p.id, 'price', e.target.value, p.price),
                        onKeyDown: (e) => handleBulkKeyDown(e, rowIdx, 4)
                      })
                    ),
                    // Cost Price Input
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('input', {
                        type: 'number',
                        step: 'any',
                        min: '0',
                        className: 'form-control form-control-sm',
                        value: valCostPrice,
                        style: { textAlign: 'right', width: '100%' },
                        onChange: (e) => handleBulkCellChange(p.id, 'costPrice', e.target.value, p.costPrice),
                        onKeyDown: (e) => handleBulkKeyDown(e, rowIdx, 5)
                      })
                    ),
                    // Min Stock Level Input
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('input', {
                        type: 'number',
                        step: 'any',
                        min: '0',
                        className: 'form-control form-control-sm',
                        value: valMinStock,
                        style: { textAlign: 'right', width: '100%' },
                        onChange: (e) => handleBulkCellChange(p.id, 'minStockLevel', e.target.value, p.minStockLevel),
                        onKeyDown: (e) => handleBulkKeyDown(e, rowIdx, 6)
                      })
                    ),
                    // Unit Input
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('input', {
                        type: 'text',
                        className: 'form-control form-control-sm',
                        value: valUnit,
                        style: { width: '100%' },
                        onChange: (e) => handleBulkCellChange(p.id, 'unit', e.target.value, p.unit),
                        onKeyDown: (e) => handleBulkKeyDown(e, rowIdx, 7)
                      })
                    ),
                    // HSN Code Input
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('input', {
                        type: 'text',
                        className: 'form-control form-control-sm',
                        value: valHsn,
                        style: { width: '100%' },
                        onChange: (e) => handleBulkCellChange(p.id, 'hsnCode', e.target.value, p.hsnCode),
                        onKeyDown: (e) => handleBulkKeyDown(e, rowIdx, 8)
                      })
                    ),
                    // GST Percentage Select
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('select', {
                        className: 'form-control form-control-sm',
                        value: valGst,
                        style: { width: '100%' },
                        onChange: (e) => handleBulkCellChange(p.id, 'gstPercentage', e.target.value, p.gstPercentage),
                        onKeyDown: (e) => handleBulkKeyDown(e, rowIdx, 9)
                      },
                        React.createElement('option', { value: '0' }, '0%'),
                        React.createElement('option', { value: '5' }, '5%'),
                        React.createElement('option', { value: '12' }, '12%'),
                        React.createElement('option', { value: '18' }, '18%'),
                        React.createElement('option', { value: '28' }, '28%')
                      )
                    ),
                    // Preferred Vendor Select
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('select', {
                        className: 'form-control form-control-sm',
                        value: valVendor,
                        style: { width: '100%' },
                        onChange: (e) => handleBulkCellChange(p.id, 'preferredPartyId', e.target.value, p.preferredPartyId),
                        onKeyDown: (e) => handleBulkKeyDown(e, rowIdx, 10)
                      },
                        React.createElement('option', { value: '' }, 'None (Select Vendor)'),
                        parties.map(pt => React.createElement('option', { key: pt.id, value: pt.id }, pt.name))
                      )
                    ),
                    // Inventory Onboarding Cell
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      isTracked ? React.createElement('span', {
                        className: 'badge',
                        style: { background: 'rgba(16,185,129,0.15)', color: '#10b981', fontSize: '0.75rem' }
                      }, `Tracked (${fmtNum(stock, 0)})`)
                        : React.createElement('div', { style: { display: 'flex', flexDirection: 'column', gap: 4 } },
                          React.createElement('label', { style: { display: 'flex', alignItems: 'center', gap: 4, fontSize: '0.75rem', cursor: 'pointer', margin: 0 } },
                            React.createElement('input', {
                              type: 'checkbox',
                              checked: Boolean(valTrack),
                              onChange: (e) => handleBulkCellChange(p.id, 'trackInInventory', e.target.checked, false)
                            }),
                            'Track Stock'
                          ),
                          valTrack && React.createElement('input', {
                            type: 'number',
                            min: '0',
                            step: 'any',
                            placeholder: 'Open Qty',
                            className: 'form-control form-control-xs',
                            value: valOpeningStock || '',
                            onChange: (e) => handleBulkCellChange(p.id, 'openingStock', e.target.value, '0'),
                            style: { width: 85, fontSize: '0.75rem' }
                          })
                        )
                    ),
                    // Actions (Pill indicator)
                    React.createElement('td', { style: { textAlign: 'center', padding: '6px 8px' } },
                      isModified ? React.createElement('span', {
                        className: 'badge',
                        style: { background: '#6366f1', color: '#fff', fontSize: '0.7rem' }
                      }, 'Edited') : React.createElement('span', { style: { color: 'var(--text-secondary)', fontSize: '0.75rem' } }, '—')
                    )
                  );
                }

                // ── ROW IN SINGLE INLINE EDIT MODE ──
                if (isEditingSingle) {
                  return React.createElement('tr', {
                    key: p.id,
                    style: { background: 'rgba(99, 102, 241, 0.08)', borderLeft: '3px solid #6366f1' }
                  },
                    // Name
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('input', {
                        type: 'text',
                        className: 'form-control form-control-sm',
                        value: singleEditForm.name,
                        onChange: (e) => setSingleEditForm({ ...singleEditForm, name: e.target.value })
                      })
                    ),
                    // SKU
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('input', {
                        type: 'text',
                        className: 'form-control form-control-sm',
                        value: singleEditForm.sku,
                        onChange: (e) => setSingleEditForm({ ...singleEditForm, sku: e.target.value })
                      })
                    ),
                    // Barcode
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('input', {
                        type: 'text',
                        className: 'form-control form-control-sm',
                        value: singleEditForm.barcode,
                        onChange: (e) => setSingleEditForm({ ...singleEditForm, barcode: e.target.value })
                      })
                    ),
                    // Category
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('input', {
                        type: 'text',
                        list: 'master-categories-datalist',
                        className: 'form-control form-control-sm',
                        value: singleEditForm.category,
                        onChange: (e) => setSingleEditForm({ ...singleEditForm, category: e.target.value })
                      })
                    ),
                    // Base Price
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('input', {
                        type: 'number',
                        step: 'any',
                        min: '0',
                        className: 'form-control form-control-sm',
                        style: { textAlign: 'right', fontWeight: 600 },
                        value: singleEditForm.price,
                        onChange: (e) => setSingleEditForm({ ...singleEditForm, price: e.target.value })
                      })
                    ),
                    // Cost Price
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('input', {
                        type: 'number',
                        step: 'any',
                        min: '0',
                        className: 'form-control form-control-sm',
                        style: { textAlign: 'right' },
                        value: singleEditForm.costPrice,
                        onChange: (e) => setSingleEditForm({ ...singleEditForm, costPrice: e.target.value })
                      })
                    ),
                    // Min Stock
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('input', {
                        type: 'number',
                        step: 'any',
                        min: '0',
                        className: 'form-control form-control-sm',
                        style: { textAlign: 'right' },
                        value: singleEditForm.minStockLevel,
                        onChange: (e) => setSingleEditForm({ ...singleEditForm, minStockLevel: e.target.value })
                      })
                    ),
                    // Unit
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('input', {
                        type: 'text',
                        className: 'form-control form-control-sm',
                        value: singleEditForm.unit,
                        onChange: (e) => setSingleEditForm({ ...singleEditForm, unit: e.target.value })
                      })
                    ),
                    // HSN
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('input', {
                        type: 'text',
                        className: 'form-control form-control-sm',
                        value: singleEditForm.hsnCode,
                        onChange: (e) => setSingleEditForm({ ...singleEditForm, hsnCode: e.target.value })
                      })
                    ),
                    // GST
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('select', {
                        className: 'form-control form-control-sm',
                        value: singleEditForm.gstPercentage,
                        onChange: (e) => setSingleEditForm({ ...singleEditForm, gstPercentage: e.target.value })
                      },
                        React.createElement('option', { value: '0' }, '0%'),
                        React.createElement('option', { value: '5' }, '5%'),
                        React.createElement('option', { value: '12' }, '12%'),
                        React.createElement('option', { value: '18' }, '18%'),
                        React.createElement('option', { value: '28' }, '28%')
                      )
                    ),
                    // Vendor
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      React.createElement('select', {
                        className: 'form-control form-control-sm',
                        value: singleEditForm.preferredPartyId,
                        onChange: (e) => setSingleEditForm({ ...singleEditForm, preferredPartyId: e.target.value })
                      },
                        React.createElement('option', { value: '' }, 'None'),
                        parties.map(pt => React.createElement('option', { key: pt.id, value: pt.id }, pt.name))
                      )
                    ),
                    // Inventory Status
                    React.createElement('td', { style: { padding: '6px 8px' } },
                      isTracked ? React.createElement('span', {
                        className: 'badge',
                        style: { background: 'rgba(16,185,129,0.15)', color: '#10b981', fontSize: '0.75rem' }
                      }, `Tracked (${fmtNum(stock, 0)})`)
                        : React.createElement('div', { style: { display: 'flex', flexDirection: 'column', gap: 4 } },
                          React.createElement('label', { style: { display: 'flex', alignItems: 'center', gap: 4, fontSize: '0.75rem', cursor: 'pointer', margin: 0 } },
                            React.createElement('input', {
                              type: 'checkbox',
                              checked: Boolean(singleEditForm.trackInInventory),
                              onChange: (e) => setSingleEditForm({ ...singleEditForm, trackInInventory: e.target.checked })
                            }),
                            'Track Stock'
                          ),
                          singleEditForm.trackInInventory && React.createElement('input', {
                            type: 'number',
                            min: '0',
                            step: 'any',
                            placeholder: 'Open Qty',
                            className: 'form-control form-control-xs',
                            value: singleEditForm.openingStock,
                            onChange: (e) => setSingleEditForm({ ...singleEditForm, openingStock: e.target.value }),
                            style: { width: 85, fontSize: '0.75rem' }
                          })
                        )
                    ),
                    // Save / Cancel Actions
                    React.createElement('td', { style: { textAlign: 'center', padding: '6px 8px' } },
                      React.createElement('div', { style: { display: 'inline-flex', gap: 4 } },
                        React.createElement('button', {
                          className: 'btn btn-primary btn-xs',
                          disabled: savingSingle,
                          onClick: () => handleSaveSingleEdit(p.id)
                        }, savingSingle ? '⏳' : '💾 Save'),
                        React.createElement('button', {
                          className: 'btn btn-ghost btn-xs',
                          disabled: savingSingle,
                          onClick: handleCancelSingleEdit
                        }, '✖️')
                      )
                    )
                  );
                }

                // ── NORMAL DISPLAY ROW (6-COLUMN COALESCED) ──
                const marginPct = (p.price && p.costPrice && p.price > 0)
                  ? Math.round(((p.price - p.costPrice) / p.price) * 100)
                  : null;
                return React.createElement('tr', {
                  key: p.id,
                  style: { cursor: 'pointer' },
                  onClick: () => setSelectedProductId(p.id)
                },
                  // 1. Product Identity
                  React.createElement('td', null,
                    React.createElement('div', { style: { fontWeight: 600, color: 'var(--text-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: 220 } }, p.name),
                    React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 6, marginTop: 2, flexWrap: 'wrap' } },
                      React.createElement('span', { style: { fontFamily: 'monospace', fontSize: '0.75rem', color: p.sku ? 'var(--text-secondary)' : 'var(--text-muted, #94a3b8)' } },
                        p.sku || 'No SKU'
                      ),
                      p.barcode && React.createElement('span', { style: { fontFamily: 'monospace', fontSize: '0.72rem', color: 'var(--text-secondary)' } },
                        `· ${p.barcode}`
                      ),
                      isNeedsSetup(p) && React.createElement('span', {
                        className: 'badge',
                        style: { background: 'rgba(234,179,8,0.15)', color: '#ca8a04', fontSize: '0.65rem', padding: '1px 5px' }
                      }, 'Setup')
                    )
                  ),
                  // 2. Category
                  React.createElement('td', null,
                    React.createElement('span', {
                      style: {
                        fontSize: '0.8rem',
                        fontWeight: p.category && p.category !== 'UNCATEGORIZED' ? 500 : 400,
                        color: (!p.category || p.category === 'UNCATEGORIZED') ? '#f59e0b' : 'var(--text-primary)'
                      }
                    }, p.category || 'UNCATEGORIZED')
                  ),
                  // 3. Commercials (Base Price + Cost Price + Margin)
                  React.createElement('td', { style: { textAlign: 'right' } },
                    React.createElement('div', { style: { fontWeight: 700, color: 'var(--text-primary)', fontSize: '0.88rem' } },
                      fmtCurrency(p.price)
                    ),
                    React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-secondary)', marginTop: 2 } },
                      p.costPrice ? `${fmtCurrency(p.costPrice)}${marginPct != null ? ` (${marginPct}% mrg)` : ''}` : 'Cost: —'
                    )
                  ),
                  // 4. Stock Status
                  React.createElement('td', null,
                    isService ? React.createElement('span', {
                      className: 'badge',
                      style: { background: 'rgba(99,102,241,0.1)', color: '#6366f1', fontSize: '0.7rem' }
                    }, 'SERVICE')
                      : !isTracked ? React.createElement('span', {
                        className: 'badge',
                        style: { background: 'rgba(100,116,139,0.15)', color: 'var(--text-secondary)', fontSize: '0.7rem' }
                      }, 'Catalog Only')
                        : React.createElement('div', null,
                          React.createElement('span', {
                            style: {
                              fontWeight: 700,
                              fontSize: '0.85rem',
                              color: stock <= 0 ? '#ef4444' : (stock <= minLvl ? '#f59e0b' : '#10b981')
                            }
                          }, `${fmtNum(stock, 0)} ${p.unit || 'pcs'}`),
                          React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)', marginTop: 1 } },
                            `Min: ${fmtNum(minLvl, 0)}`
                          )
                        )
                  ),
                  // 5. Tax & Vendor
                  React.createElement('td', null,
                    React.createElement('div', { style: { fontSize: '0.78rem', color: 'var(--text-primary)' } },
                      `${p.gstPercentage != null ? p.gstPercentage + '%' : '0%'} GST${p.hsnCode ? ` · ${p.hsnCode}` : ''}`
                    ),
                    React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: 130, marginTop: 1 } },
                      p.preferredPartyId ? (partyMap[p.preferredPartyId] || `Vendor #${p.preferredPartyId}`) : 'No vendor'
                    )
                  ),
                  // 6. Actions
                  React.createElement('td', { style: { textAlign: 'center' } },
                    React.createElement('div', {
                      style: { display: 'inline-flex', gap: 4, alignItems: 'center' },
                      onClick: (e) => e.stopPropagation()
                    },
                      React.createElement('button', {
                        className: 'btn btn-outline btn-xs',
                        style: { padding: '2px 6px', fontSize: '0.75rem' },
                        title: 'Edit product',
                        onClick: () => handleStartSingleEdit(p)
                      }, '✏️ Edit'),
                      React.createElement('button', {
                        className: 'btn btn-outline btn-xs',
                        style: { padding: '2px 6px', fontSize: '0.75rem' },
                        title: 'View Insights',
                        onClick: () => setSelectedProductId(p.id)
                      }, '📊'),
                      !isService && isTracked && React.createElement('button', {
                        className: 'btn btn-primary btn-xs',
                        style: { padding: '2px 6px', fontSize: '0.75rem' },
                        title: 'Quick Reorder',
                        onClick: () => handleUseRecommendation(p.id, 10)
                      }, '⚡')
                    )
                  )
                );
              })
            )
          )
        ),

        // Docked pagination footer
        React.createElement('div', { className: 'firm-docked-pagination' },
          React.createElement('div', null,
            `Showing ${filteredProducts.length === 0 ? 0 : (safeMasterPage - 1) * masterPageSize + 1}–${Math.min(safeMasterPage * masterPageSize, filteredProducts.length)} of ${filteredProducts.length} products`
          ),
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
            React.createElement('span', { style: { fontSize: '0.78rem', color: 'var(--text-secondary)' } }, 'Rows:'),
            React.createElement('select', {
              className: 'form-control form-control-sm',
              style: { width: 'auto', padding: '2px 6px', height: 26, fontSize: '0.78rem' },
              value: masterPageSize,
              onChange: (e) => {
                setMasterPageSize(Number(e.target.value));
                setMasterPage(1);
              }
            },
              React.createElement('option', { value: 8 }, '8'),
              React.createElement('option', { value: 15 }, '15'),
              React.createElement('option', { value: 25 }, '25'),
              React.createElement('option', { value: 50 }, '50')
            ),
            React.createElement('button', {
              className: 'btn btn-outline btn-xs',
              disabled: safeMasterPage <= 1,
              onClick: () => setMasterPage(p => Math.max(1, p - 1))
            }, '◀ Prev'),
            React.createElement('span', { style: { fontSize: '0.8rem', fontWeight: 600 } }, `${safeMasterPage} / ${totalMasterPages}`),
            React.createElement('button', {
              className: 'btn btn-outline btn-xs',
              disabled: safeMasterPage >= totalMasterPages,
              onClick: () => setMasterPage(p => Math.min(totalMasterPages, p + 1))
            }, 'Next ▶')
          )
        )
      ),

      // REVIEW CHANGES MODAL
      showReviewModal && React.createElement('div', {
        className: 'modal-overlay',
        style: {
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.65)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 9999, padding: 20
        }
      },
        React.createElement('div', {
          className: 'modal-content card',
          style: {
            maxWidth: 820, width: '100%', maxHeight: '88vh', display: 'flex',
            flexDirection: 'column', padding: '24px 28px', gap: 16, overflow: 'hidden'
          }
        },
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between' } },
            React.createElement('div', null,
              React.createElement('h3', { style: { margin: 0, fontSize: '1.25rem', fontWeight: 700 } }, '🔍 Review Bulk Product Master Changes'),
              React.createElement('p', { style: { margin: '4px 0 0', color: 'var(--text-secondary)', fontSize: '0.85rem' } },
                'Inspect the modified fields before confirming atomic persistence to the database.'
              )
            ),
            React.createElement('button', {
              className: 'btn btn-ghost btn-sm',
              onClick: () => setShowReviewModal(false)
            }, '✖️')
          ),

          // Error banner if save failed
          bulkError && React.createElement('div', {
            style: {
              background: 'rgba(239, 68, 68, 0.12)', border: '1px solid rgba(239, 68, 68, 0.4)',
              color: '#ef4444', padding: '10px 14px', borderRadius: 6, fontSize: '0.85rem'
            }
          }, `❌ ${bulkError}`),

          // Summary Metrics Cards
          React.createElement('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 10 } },
            React.createElement('div', { className: 'card', style: { padding: '10px 14px' } },
              React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-secondary)' } }, 'Products Changed'),
              React.createElement('div', { style: { fontSize: '1.3rem', fontWeight: 700, color: '#6366f1' } }, reviewDiffs.totalProducts)
            ),
            React.createElement('div', { className: 'card', style: { padding: '10px 14px' } },
              React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-secondary)' } }, 'Category Updates'),
              React.createElement('div', { style: { fontSize: '1.3rem', fontWeight: 700 } }, reviewDiffs.catChanges)
            ),
            React.createElement('div', { className: 'card', style: { padding: '10px 14px' } },
              React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-secondary)' } }, 'Price/Cost Changes'),
              React.createElement('div', { style: { fontSize: '1.3rem', fontWeight: 700 } }, reviewDiffs.priceChanges)
            ),
            React.createElement('div', { className: 'card', style: { padding: '10px 14px' } },
              React.createElement('div', { style: { fontSize: '0.75rem', color: 'var(--text-secondary)' } }, 'Inventory Onboardings'),
              React.createElement('div', { style: { fontSize: '1.3rem', fontWeight: 700, color: '#10b981' } }, reviewDiffs.inventoryChanges)
            )
          ),

          // Detailed Diff Table
          React.createElement('div', { style: { overflowY: 'auto', flex: 1, border: '1px solid var(--border-color, #334155)', borderRadius: 6 } },
            React.createElement('table', { className: 'data-table', style: { width: '100%', margin: 0 } },
              React.createElement('thead', null,
                React.createElement('tr', null,
                  React.createElement('th', null, 'Product'),
                  React.createElement('th', null, 'Field'),
                  React.createElement('th', null, 'Original Value'),
                  React.createElement('th', null, 'New Value')
                )
              ),
              React.createElement('tbody', null,
                reviewDiffs.diffs.map((d, i) => React.createElement('tr', { key: i },
                  React.createElement('td', null,
                    React.createElement('div', { style: { fontWeight: 600 } }, d.prodName),
                    React.createElement('div', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)' } }, `SKU: ${d.sku}`)
                  ),
                  React.createElement('td', null,
                    React.createElement('span', { className: 'badge', style: { fontSize: '0.75rem' } }, d.label)
                  ),
                  React.createElement('td', { style: { color: 'var(--text-secondary)', textDecoration: 'line-through' } },
                    d.oldDisplay
                  ),
                  React.createElement('td', { style: { fontWeight: 700, color: '#10b981' } },
                    d.newDisplay
                  )
                ))
              )
            )
          ),

          // Footer Actions
          React.createElement('div', { style: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 10 } },
            React.createElement('button', {
              className: 'btn btn-ghost',
              onClick: () => setShowReviewModal(false)
            }, '⬅️ Back to Editing'),
            React.createElement('button', {
              className: 'btn btn-primary',
              disabled: savingBulk || reviewDiffs.totalProducts === 0,
              onClick: handleConfirmBulkSave,
              style: { fontWeight: 700, padding: '10px 24px' }
            }, savingBulk ? '⏳ Persisting Changes...' : '💾 Confirm & Save All Changes')
          )
        )
      ),

      // UNSAVED CHANGES PROMPT
      showUnsavedPrompt && React.createElement('div', {
        className: 'modal-overlay',
        style: {
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 10000, padding: 20
        }
      },
        React.createElement('div', {
          className: 'modal-content card',
          style: { maxWidth: 440, width: '100%', padding: '24px 28px', display: 'flex', flexDirection: 'column', gap: 16 }
        },
          React.createElement('h4', { style: { margin: 0, fontSize: '1.1rem', fontWeight: 700 } }, '⚠️ Unsaved Bulk Edits'),
          React.createElement('p', { style: { margin: 0, color: 'var(--text-secondary)', fontSize: '0.9rem' } },
            `You have ${totalChangesCount} unsaved field changes across ${changedProductIds.length} products. If you leave, these draft changes will be lost.`
          ),
          React.createElement('div', { style: { display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 10 } },
            React.createElement('button', {
              className: 'btn btn-ghost',
              onClick: () => setShowUnsavedPrompt(false)
            }, 'Continue Editing'),
            React.createElement('button', {
              className: 'btn btn-danger',
              onClick: () => {
                setDraftChanges({});
                setIsBulkEditMode(false);
                setShowUnsavedPrompt(false);
                if (pendingTab) setHubTab(pendingTab);
              }
            }, 'Discard & Proceed')
          )
        )
      ),

      // Detail Drawer Modal
      selectedProductId && React.createElement(ProductDetailDrawer, {
        productId: selectedProductId,
        onClose: () => setSelectedProductId(null),
        onUseRecommendation: handleUseRecommendation
      }),

      // Bulk Add Modal
      showBulkAddModal && React.createElement(BulkProductAddModal, {
        onClose: () => setShowBulkAddModal(false),
        onSuccess: () => loadProducts(),
        existingCategories: categories
      })
    );
  }

  return {
    DemandForecastChart,
    ProductDetailDrawer,
    ProductIntelligenceHub,
    SmartReorderView,
    BulkProductAddModal,
    PricingProfileManager
  };
}));
