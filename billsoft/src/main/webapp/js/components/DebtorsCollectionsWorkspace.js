/**
 * RupeeCRM — Debtors & Collections Management Operating Center
 * Reference: RUPEECRM-DEBTORS-COLLECTIONS-END-TO-END-20261126
 */
(function() {
  const { useState, useEffect, useCallback, useMemo, createElement: h, Fragment } = React;

  function DebtorsCollectionsWorkspace(props) {
    const { onSettle, onCustomer360, onWhatsApp, onStatement, onCreateBill } = props;

    const [activeSubTab, setActiveSubTab] = useState('overview'); // overview | debtors | overdue | followups | reports
    const [overview, setOverview] = useState(null);
    const [debtorsList, setDebtorsList] = useState([]);
    const [overdueInvoices, setOverdueInvoices] = useState([]);
    const [followUps, setFollowUps] = useState([]);
    const [loading, setLoading] = useState(true);

    // Filters and search for All Debtors
    const [debtorSearch, setDebtorSearch] = useState('');
    const [debtorFilter, setDebtorFilter] = useState('all'); // all | overdue | critical | promised | advance
    const [debtorSortBy, setDebtorSortBy] = useState('default');
    const [debtorSortDir, setDebtorSortDir] = useState('desc');
    const [debtorPage, setDebtorPage] = useState(1);
    const debtorPageSize = 10;

    // Follow-ups filter
    const [followUpFilter, setFollowUpFilter] = useState('DUE_TODAY'); // DUE_TODAY | UPCOMING | OVERDUE | ALL

    // Bulk statement zip date range
    const [bulkFrom, setBulkFrom] = useState('');
    const [bulkTo, setBulkTo] = useState('');
    const [isDownloadingZip, setIsDownloadingZip] = useState(false);

    // Modals
    const [promiseModalCustomer, setPromiseModalCustomer] = useState(null);
    const [promiseAmount, setPromiseAmount] = useState('');
    const [promiseDate, setPromiseDate] = useState('');
    const [promiseNotes, setPromiseNotes] = useState('');
    const [isSavingPromise, setIsSavingPromise] = useState(false);

    const [activityModalCustomer, setActivityModalCustomer] = useState(null);
    const [activityType, setActivityType] = useState('CALL_LOGGED');
    const [activityDesc, setActivityDesc] = useState('');
    const [isSavingActivity, setIsSavingActivity] = useState(false);

    const [rescheduleModalPromise, setRescheduleModalPromise] = useState(null);
    const [rescheduleDate, setRescheduleDate] = useState('');
    const [rescheduleNotes, setRescheduleNotes] = useState('');

    // Load Overview Data
    const loadOverview = useCallback(async () => {
      try {
        const data = await API.debtors.overview();
        setOverview(data);
      } catch (err) {
        console.error('Failed to load debtors overview:', err);
      }
    }, []);

    // Load Debtors List
    const loadDebtors = useCallback(async () => {
      try {
        const data = await API.debtors.list({
          search: debtorSearch,
          filter: debtorFilter,
          sortBy: debtorSortBy,
          sortDir: debtorSortDir
        });
        setDebtorsList(Array.isArray(data) ? data : []);
      } catch (err) {
        console.error('Failed to load debtors register:', err);
        setDebtorsList([]);
      }
    }, [debtorSearch, debtorFilter, debtorSortBy, debtorSortDir]);

    // Load Overdue Invoices
    const loadOverdueInvoices = useCallback(async () => {
      try {
        const data = await API.debtors.firmOverdueInvoices();
        setOverdueInvoices(Array.isArray(data) ? data : []);
      } catch (err) {
        console.error('Failed to load overdue invoices:', err);
        setOverdueInvoices([]);
      }
    }, []);

    // Load Follow-ups
    const loadFollowUps = useCallback(async () => {
      try {
        const data = await API.debtors.followUps(followUpFilter);
        setFollowUps(Array.isArray(data) ? data : []);
      } catch (err) {
        console.error('Failed to load follow-ups:', err);
        setFollowUps([]);
      }
    }, [followUpFilter]);

    // Master Refresh
    const refreshAll = useCallback(async () => {
      setLoading(true);
      try {
        await Promise.all([
          loadOverview(),
          loadDebtors(),
          loadOverdueInvoices(),
          loadFollowUps()
        ]);
      } finally {
        setLoading(false);
      }
    }, [loadOverview, loadDebtors, loadOverdueInvoices, loadFollowUps]);

    useEffect(() => {
      refreshAll();
    }, [refreshAll]);

    // When switching sub-tabs, trigger refresh for active view
    useEffect(() => {
      if (activeSubTab === 'overview') loadOverview();
      else if (activeSubTab === 'debtors') loadDebtors();
      else if (activeSubTab === 'overdue') loadOverdueInvoices();
      else if (activeSubTab === 'followups') loadFollowUps();
    }, [activeSubTab, loadOverview, loadDebtors, loadOverdueInvoices, loadFollowUps]);

    // Handle Create Promise
    const handleSavePromise = async () => {
      if (!promiseModalCustomer || !promiseAmount || !promiseDate) {
        if (typeof showToast === 'function') showToast('Please enter promised amount and date', 'warning');
        return;
      }
      setIsSavingPromise(true);
      try {
        await API.debtors.createPromise({
          customerId: promiseModalCustomer.customerId,
          promisedAmount: parseFloat(promiseAmount),
          promisedDate: promiseDate,
          notes: promiseNotes
        });
        if (typeof showToast === 'function') showToast('Promise to pay recorded successfully', 'success');
        setPromiseModalCustomer(null);
        setPromiseAmount('');
        setPromiseDate('');
        setPromiseNotes('');
        refreshAll();
      } catch (err) {
        if (typeof showToast === 'function') showToast('Failed to record promise: ' + (err.message || 'Error'), 'error');
      } finally {
        setIsSavingPromise(false);
      }
    };

    // Handle Log Activity
    const handleSaveActivity = async () => {
      if (!activityModalCustomer || !activityDesc) {
        if (typeof showToast === 'function') showToast('Please enter activity notes', 'warning');
        return;
      }
      setIsSavingActivity(true);
      try {
        await API.debtors.logActivity({
          customerId: activityModalCustomer.customerId,
          activityType: activityType,
          description: activityDesc
        });
        if (typeof showToast === 'function') showToast('Activity recorded in collection timeline', 'success');
        setActivityModalCustomer(null);
        setActivityDesc('');
        refreshAll();
      } catch (err) {
        if (typeof showToast === 'function') showToast('Failed to record activity: ' + (err.message || 'Error'), 'error');
      } finally {
        setIsSavingActivity(false);
      }
    };

    // Handle Reschedule Promise
    const handleSaveReschedule = async () => {
      if (!rescheduleModalPromise || !rescheduleDate) {
        if (typeof showToast === 'function') showToast('Please enter new promised date', 'warning');
        return;
      }
      try {
        await API.debtors.updatePromiseStatus(rescheduleModalPromise.id, {
          status: 'RESCHEDULED',
          rescheduledDate: rescheduleDate,
          notes: rescheduleNotes
        });
        if (typeof showToast === 'function') showToast('Promise rescheduled successfully', 'success');
        setRescheduleModalPromise(null);
        setRescheduleDate('');
        setRescheduleNotes('');
        refreshAll();
      } catch (err) {
        if (typeof showToast === 'function') showToast('Failed to reschedule promise: ' + (err.message || 'Error'), 'error');
      }
    };

    // Handle Bulk ZIP Statement Download
    const handleDownloadBulkZip = () => {
      setIsDownloadingZip(true);
      try {
        const url = API.statements.customerBulkZipUrl(bulkFrom, bulkTo);
        window.location.href = url;
        if (typeof showToast === 'function') showToast('Preparing and streaming customer statements ZIP...', 'info');
      } catch (err) {
        if (typeof showToast === 'function') showToast('Failed to download statements: ' + err.message, 'error');
      } finally {
        setTimeout(() => setIsDownloadingZip(false), 3000);
      }
    };

    // Format Currency Helper
    const formatInr = (val) => {
      if (typeof BillsoftUtils !== 'undefined' && BillsoftUtils.formatCurrency) {
        return BillsoftUtils.formatCurrency(val || 0);
      }
      const num = Number(val || 0);
      return '₹' + num.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    };

    // Sub-Tabs Nav
    const subNavTabs = [
      { id: 'overview', label: 'Overview & Aging' },
      { id: 'debtors', label: `All Debtors (${overview ? overview.totalDebtorsCount : debtorsList.length})` },
      { id: 'overdue', label: `Overdue Invoices (${overdueInvoices.length})` },
      { id: 'followups', label: `Follow-ups & Promises (${followUps.length})` },
      { id: 'reports', label: 'Statements & Reports' }
    ];

    // Paginated Debtors
    const totalDebtorPages = Math.max(1, Math.ceil(debtorsList.length / debtorPageSize));
    const safeDebtorPage = Math.min(debtorPage, totalDebtorPages);
    const paginatedDebtors = debtorsList.slice((safeDebtorPage - 1) * debtorPageSize, safeDebtorPage * debtorPageSize);

    return h('div', { className: 'debtors-collections-workspace', style: { display: 'flex', flexDirection: 'column', gap: 14 } },

      // Sub-Tabs Navigation Bar
      h('div', {
        style: {
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          borderBottom: '1px solid var(--border)',
          paddingBottom: 8,
          flexWrap: 'wrap',
          gap: 8
        }
      },
        h('div', { style: { display: 'flex', gap: 6 } },
          subNavTabs.map(t => h('button', {
            key: t.id,
            className: `btn btn-sm ${activeSubTab === t.id ? 'btn-primary' : 'btn-outline'}`,
            style: { fontWeight: 600, fontSize: '0.82rem', padding: '6px 14px', borderRadius: 20 },
            onClick: () => setActiveSubTab(t.id)
          }, t.label))
        ),
        h('div', { style: { display: 'flex', gap: 8 } },
          h('button', {
            className: 'btn btn-outline btn-sm',
            title: 'Refresh Data',
            onClick: refreshAll
          }, '🔄 Refresh')
        )
      ),

      // ── SUB-VIEW 1: OVERVIEW & AGING ──
      activeSubTab === 'overview' && h('div', { style: { display: 'flex', flexDirection: 'column', gap: 16 } },
        // KPI Summary Cards
        h('div', {
          style: {
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))',
            gap: 12
          }
        },
          h('div', { className: 'kpi-card', style: { padding: '14px 16px', background: 'var(--bg-secondary)', border: '1px solid var(--border)', borderRadius: 10 } },
            h('div', { style: { fontSize: '0.74rem', textTransform: 'uppercase', color: 'var(--text-secondary)', fontWeight: 600 } }, 'Total Outstanding'),
            h('div', { style: { fontSize: '1.4rem', fontWeight: 800, color: '#b91c1c', marginTop: 4, fontFamily: 'var(--font-mono, monospace)' } },
              formatInr(overview ? overview.totalOutstanding : 0)
            ),
            h('div', { style: { fontSize: '0.74rem', color: 'var(--text-secondary)', marginTop: 2 } },
              `${overview ? overview.totalDebtorsCount : 0} customers with positive balance`
            )
          ),
          h('div', { className: 'kpi-card', style: { padding: '14px 16px', background: 'var(--bg-secondary)', border: '1px solid var(--border)', borderRadius: 10 } },
            h('div', { style: { fontSize: '0.74rem', textTransform: 'uppercase', color: 'var(--text-secondary)', fontWeight: 600 } }, 'Past Due / Overdue'),
            h('div', { style: { fontSize: '1.4rem', fontWeight: 800, color: '#c2410c', marginTop: 4, fontFamily: 'var(--font-mono, monospace)' } },
              formatInr(overview ? overview.overdueAmount : 0)
            ),
            h('div', { style: { fontSize: '0.74rem', color: 'var(--text-secondary)', marginTop: 2 } },
              `${overview ? overview.overdueDebtorsCount : 0} debtors with overdue invoices`
            )
          ),
          h('div', { className: 'kpi-card', style: { padding: '14px 16px', background: 'var(--bg-secondary)', border: '1px solid var(--border)', borderRadius: 10 } },
            h('div', { style: { fontSize: '0.74rem', textTransform: 'uppercase', color: 'var(--text-secondary)', fontWeight: 600 } }, 'Collected This Month'),
            h('div', { style: { fontSize: '1.4rem', fontWeight: 800, color: '#16a34a', marginTop: 4, fontFamily: 'var(--font-mono, monospace)' } },
              formatInr(overview ? overview.collectedThisMonth : 0)
            ),
            h('div', { style: { fontSize: '0.74rem', color: 'var(--text-secondary)', marginTop: 2 } },
              'Payments received in current month'
            )
          ),
          h('div', { className: 'kpi-card', style: { padding: '14px 16px', background: 'var(--bg-secondary)', border: '1px solid var(--border)', borderRadius: 10 } },
            h('div', { style: { fontSize: '0.74rem', textTransform: 'uppercase', color: 'var(--text-secondary)', fontWeight: 600 } }, 'Advance Store Credit'),
            h('div', { style: { fontSize: '1.4rem', fontWeight: 800, color: '#2563eb', marginTop: 4, fontFamily: 'var(--font-mono, monospace)' } },
              formatInr(overview ? overview.advanceCredit : 0)
            ),
            h('div', { style: { fontSize: '0.74rem', color: 'var(--text-secondary)', marginTop: 2 } },
              'Held in customer accounts'
            )
          )
        ),

        // Receivables Aging Breakdown
        h('div', {
          style: {
            background: 'var(--bg-secondary)',
            border: '1px solid var(--border)',
            borderRadius: 10,
            padding: 16
          }
        },
          h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 } },
            h('h4', { style: { margin: 0, fontSize: '0.98rem', fontWeight: 700 } }, '📊 Receivables Aging Analysis'),
            h('span', { style: { fontSize: '0.78rem', color: 'var(--text-secondary)' } }, 'Based on invoice due dates')
          ),
          h('div', {
            style: {
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
              gap: 12
            }
          },
            h('div', { style: { padding: 12, border: '1px solid #bbf7d0', background: '#f0fdf4', borderRadius: 8 } },
              h('div', { style: { fontSize: '0.75rem', fontWeight: 700, color: '#166534' } }, '0 – 30 Days (Current)'),
              h('div', { style: { fontSize: '1.2rem', fontWeight: 800, color: '#15803d', marginTop: 4, fontFamily: 'var(--font-mono, monospace)' } },
                formatInr(overview ? overview.aging0To30 : 0)
              ),
              h('div', { style: { fontSize: '0.72rem', color: '#166534', marginTop: 2 } }, 'Within normal terms')
            ),
            h('div', { style: { padding: 12, border: '1px solid #fef08a', background: '#fefce8', borderRadius: 8 } },
              h('div', { style: { fontSize: '0.75rem', fontWeight: 700, color: '#854d0e' } }, '31 – 60 Days (Attention)'),
              h('div', { style: { fontSize: '1.2rem', fontWeight: 800, color: '#a16207', marginTop: 4, fontFamily: 'var(--font-mono, monospace)' } },
                formatInr(overview ? overview.aging31To60 : 0)
              ),
              h('div', { style: { fontSize: '0.72rem', color: '#854d0e', marginTop: 2 } }, 'Mild follow-up required')
            ),
            h('div', { style: { padding: 12, border: '1px solid #fed7aa', background: '#fff7ed', borderRadius: 8 } },
              h('div', { style: { fontSize: '0.75rem', fontWeight: 700, color: '#9a3412' } }, '61 – 90 Days (Urgent)'),
              h('div', { style: { fontSize: '1.2rem', fontWeight: 800, color: '#c2410c', marginTop: 4, fontFamily: 'var(--font-mono, monospace)' } },
                formatInr(overview ? overview.aging61To90 : 0)
              ),
              h('div', { style: { fontSize: '0.72rem', color: '#9a3412', marginTop: 2 } }, 'Formal reminder needed')
            ),
            h('div', { style: { padding: 12, border: '1px solid #fecaca', background: '#fef2f2', borderRadius: 8 } },
              h('div', { style: { fontSize: '0.75rem', fontWeight: 700, color: '#991b1b' } }, '90+ Days (Critical / Delinquent)'),
              h('div', { style: { fontSize: '1.2rem', fontWeight: 800, color: '#b91c1c', marginTop: 4, fontFamily: 'var(--font-mono, monospace)' } },
                formatInr(overview ? overview.aging90Plus : 0)
              ),
              h('div', { style: { fontSize: '0.72rem', color: '#991b1b', marginTop: 2 } },
                `${overview ? overview.criticalDebtorsCount : 0} high-risk accounts`
              )
            )
          )
        ),

        // Today's Follow-up Queue
        h('div', {
          style: {
            background: 'var(--bg-secondary)',
            border: '1px solid var(--border)',
            borderRadius: 10,
            padding: 16
          }
        },
          h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 } },
            h('h4', { style: { margin: 0, fontSize: '0.98rem', fontWeight: 700 } }, `⏰ Today's Collections Queue (${overview && overview.todayFollowUps ? overview.todayFollowUps.length : 0})`),
            h('button', {
              className: 'btn btn-outline btn-xs',
              onClick: () => setActiveSubTab('followups')
            }, 'View All Follow-ups ▶')
          ),
          (!overview || !overview.todayFollowUps || overview.todayFollowUps.length === 0) ?
            h('div', { style: { padding: 20, textAlign: 'center', color: 'var(--text-secondary)', fontSize: '0.86rem' } },
              '🎉 All clear! No collection follow-ups or promises due today.'
            ) :
            h('div', { style: { display: 'flex', flexDirection: 'column', gap: 8 } },
              overview.todayFollowUps.map(f => h('div', {
                key: f.id + '_' + f.source,
                style: {
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  padding: '10px 14px',
                  background: 'var(--bg-primary)',
                  border: '1px solid var(--border)',
                  borderRadius: 8,
                  flexWrap: 'wrap',
                  gap: 8
                }
              },
                h('div', { style: { display: 'flex', alignItems: 'center', gap: 10 } },
                  h('span', {
                    className: `badge ${f.type === 'PROMISE_FOLLOW_UP' ? 'badge-primary' : 'badge-outline'}`,
                    style: { fontSize: '0.7rem' }
                  }, f.type === 'PROMISE_FOLLOW_UP' ? 'Promise to Pay' : 'Reminder'),
                  h('div', null,
                    h('div', { style: { fontWeight: 700, fontSize: '0.88rem' } }, f.customerName),
                    h('div', { style: { fontSize: '0.74rem', color: 'var(--text-secondary)' } },
                      `${f.title} ${f.customerPhone ? '• ' + f.customerPhone : ''}`
                    )
                  )
                ),
                h('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
                  h('span', { style: { fontWeight: 700, fontSize: '0.88rem', color: '#b91c1c', fontFamily: 'var(--font-mono, monospace)' } },
                    f.outstandingAmount > 0 ? formatInr(f.outstandingAmount) : ''
                  ),
                  h('button', {
                    className: 'btn btn-primary btn-xs',
                    onClick: () => {
                      if (onSettle) onSettle({ id: f.customerId, name: f.customerName });
                    }
                  }, 'Settle'),
                  h('button', {
                    className: 'btn btn-outline btn-xs',
                    style: { color: '#16a34a' },
                    onClick: () => {
                      if (onWhatsApp) onWhatsApp({ id: f.customerId, name: f.customerName, phone: f.customerPhone });
                    }
                  }, 'WhatsApp')
                )
              ))
            )
        )
      ),

      // ── SUB-VIEW 2: ALL DEBTORS REGISTER ──
      activeSubTab === 'debtors' && h('div', { style: { display: 'flex', flexDirection: 'column', gap: 12 } },
        // Filter & Search Controls
        h('div', {
          style: {
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 10,
            background: 'var(--bg-secondary)',
            padding: '10px 14px',
            borderRadius: 8,
            border: '1px solid var(--border)'
          }
        },
          h('div', { style: { display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap', flex: 1 } },
            h('input', {
              type: 'text',
              className: 'form-control form-control-sm',
              placeholder: 'Search debtor name, phone, email...',
              style: { maxWidth: 260 },
              value: debtorSearch,
              onChange: (e) => setDebtorSearch(e.target.value)
            }),
            h('select', {
              className: 'form-control form-control-sm',
              style: { maxWidth: 170 },
              value: debtorFilter,
              onChange: (e) => setDebtorFilter(e.target.value)
            },
              h('option', { value: 'all' }, 'All Debtors'),
              h('option', { value: 'overdue' }, 'Overdue Only'),
              h('option', { value: 'critical' }, 'High Risk / Critical'),
              h('option', { value: 'promised' }, 'Has Active Promise'),
              h('option', { value: 'advance' }, 'Advance Credit Only'),
              h('option', { value: 'all_including_zero' }, 'All Customers')
            ),
            h('select', {
              className: 'form-control form-control-sm',
              style: { maxWidth: 170 },
              value: debtorSortBy,
              onChange: (e) => setDebtorSortBy(e.target.value)
            },
              h('option', { value: 'default' }, 'Sort: Priority Ranking'),
              h('option', { value: 'outstanding' }, 'Sort: Outstanding Due'),
              h('option', { value: 'overdue' }, 'Sort: Overdue Amount'),
              h('option', { value: 'days' }, 'Sort: Days Overdue'),
              h('option', { value: 'name' }, 'Sort: Customer Name')
            )
          ),
          h('div', { style: { display: 'flex', gap: 6 } },
            h('a', {
              href: API.debtors.exportCsvUrl(),
              className: 'btn btn-outline btn-xs',
              download: true
            }, '📥 CSV'),
            h('a', {
              href: API.debtors.exportPdfUrl(),
              className: 'btn btn-outline btn-xs',
              download: true
            }, '📄 PDF Aging')
          )
        ),

        // Debtor Register Table
        h('div', { style: { overflowX: 'auto', border: '1px solid var(--border)', borderRadius: 8 } },
          h('table', { className: 'table table-hover', style: { width: '100%', margin: 0, fontSize: '0.82rem' } },
            h('thead', null,
              h('tr', { style: { background: 'var(--bg-secondary)', borderBottom: '2px solid var(--border)' } },
                h('th', { style: { padding: '8px 12px' } }, 'Customer Identity'),
                h('th', { style: { padding: '8px 12px', textAlign: 'right' } }, 'Outstanding Due'),
                h('th', { style: { padding: '8px 12px', textAlign: 'right' } }, 'Overdue'),
                h('th', { style: { padding: '8px 12px' } }, 'Aging Breakdown'),
                h('th', { style: { padding: '8px 12px' } }, 'Risk Status'),
                h('th', { style: { padding: '8px 12px' } }, 'Promise / Follow-up'),
                h('th', { style: { padding: '8px 12px', textAlign: 'center' } }, 'Actions')
              )
            ),
            h('tbody', null,
              paginatedDebtors.length === 0 ?
                h('tr', null,
                  h('td', { colSpan: 7, style: { padding: 30, textAlign: 'center', color: 'var(--text-secondary)' } },
                    'No debtors match the selected filters.'
                  )
                ) :
                paginatedDebtors.map(d => h('tr', {
                  key: d.customerId,
                  style: { borderBottom: '1px solid var(--border)' }
                },
                  // Customer Identity
                  h('td', { style: { padding: '10px 12px' } },
                    h('div', { style: { fontWeight: 700, color: 'var(--text-primary)' } }, d.customerName),
                    h('div', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)' } },
                      `ID #${d.customerId} ${d.customerPhone ? '• ' + d.customerPhone : ''}`
                    ),
                    // Counterparty supplier link indicator
                    d.hasSupplierCounterpart && h('div', { style: { marginTop: 3 } },
                      h('span', {
                        className: 'badge',
                        style: { background: '#f1f5f9', color: '#475569', fontSize: '0.68rem', border: '1px solid #cbd5e1' },
                        title: 'Also registered as Supplier in this firm'
                      }, `🔄 Counterparty Supplier (Payable: ${formatInr(d.supplierPayableAmount || 0)})`)
                    )
                  ),
                  // Outstanding Due
                  h('td', { style: { padding: '10px 12px', textAlign: 'right', fontWeight: 700, fontFamily: 'var(--font-mono, monospace)' } },
                    d.outstandingAmount > 0 ?
                      h('span', { style: { color: '#b91c1c' } }, formatInr(d.outstandingAmount)) :
                      d.advanceCredit > 0 ?
                        h('span', { style: { color: '#2563eb' } }, `Credit: ${formatInr(d.advanceCredit)}`) :
                        h('span', { style: { color: '#16a34a' } }, '₹0.00')
                  ),
                  // Overdue
                  h('td', { style: { padding: '10px 12px', textAlign: 'right', fontWeight: 600, fontFamily: 'var(--font-mono, monospace)' } },
                    d.overdueAmount > 0 ?
                      h('div', null,
                        h('span', { style: { color: '#c2410c' } }, formatInr(d.overdueAmount)),
                        h('div', { style: { fontSize: '0.70rem', color: 'var(--text-secondary)' } }, `${d.oldestOverdueDays}d overdue`)
                      ) :
                      h('span', { style: { color: 'var(--text-secondary)' } }, '-')
                  ),
                  // Aging Breakdown
                  h('td', { style: { padding: '10px 12px' } },
                    h('div', { style: { display: 'flex', gap: 4, flexWrap: 'wrap' } },
                      d.aging0To30 > 0 && h('span', { className: 'badge', style: { background: '#f0fdf4', color: '#166534', border: '1px solid #bbf7d0', fontSize: '0.68rem' } }, `0-30: ${formatInr(d.aging0To30)}`),
                      d.aging31To60 > 0 && h('span', { className: 'badge', style: { background: '#fefce8', color: '#854d0e', border: '1px solid #fef08a', fontSize: '0.68rem' } }, `31-60: ${formatInr(d.aging31To60)}`),
                      d.aging61To90 > 0 && h('span', { className: 'badge', style: { background: '#fff7ed', color: '#9a3412', border: '1px solid #fed7aa', fontSize: '0.68rem' } }, `61-90: ${formatInr(d.aging61To90)}`),
                      d.aging90Plus > 0 && h('span', { className: 'badge', style: { background: '#fef2f2', color: '#991b1b', border: '1px solid #fecaca', fontSize: '0.68rem' } }, `90+: ${formatInr(d.aging90Plus)}`)
                    )
                  ),
                  // Risk Status
                  h('td', { style: { padding: '10px 12px' } },
                    h('span', {
                      className: `badge ${d.riskLevel === 'HIGH' ? 'badge-danger' : d.riskLevel === 'MEDIUM' ? 'badge-warning' : 'badge-paid'}`,
                      style: { fontSize: '0.72rem', fontWeight: 600 },
                      title: d.riskReason
                    }, d.riskLevel + ' RISK'),
                    h('div', { style: { fontSize: '0.68rem', color: 'var(--text-secondary)', marginTop: 2, maxWidth: 140, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }, title: d.riskReason },
                      d.riskReason
                    )
                  ),
                  // Promise / Follow-up
                  h('td', { style: { padding: '10px 12px' } },
                    d.activePromiseAmount ?
                      h('div', null,
                        h('span', { className: 'badge badge-primary', style: { fontSize: '0.70rem' } },
                          `Promise: ${formatInr(d.activePromiseAmount)}`
                        ),
                        h('div', { style: { fontSize: '0.70rem', color: 'var(--text-secondary)', marginTop: 2 } },
                          `By ${d.activePromiseDate}`
                        )
                      ) :
                      h('span', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)' } }, 'None scheduled')
                  ),
                  // Actions
                  h('td', { style: { padding: '10px 12px', textAlign: 'center' } },
                    h('div', { style: { display: 'flex', gap: 4, justifyContent: 'center', flexWrap: 'wrap' } },
                      h('button', {
                        className: 'btn btn-primary btn-xs',
                        title: 'Settle Invoices',
                        onClick: () => {
                          if (onSettle) onSettle({ id: d.customerId, name: d.customerName });
                        }
                      }, 'Settle'),
                      h('button', {
                        className: 'btn btn-outline btn-xs',
                        title: 'Record Promise to Pay',
                        onClick: () => setPromiseModalCustomer(d)
                      }, 'Promise'),
                      h('button', {
                        className: 'btn btn-outline btn-xs',
                        title: 'Log Note / Activity',
                        onClick: () => setActivityModalCustomer(d)
                      }, 'Note'),
                      h('button', {
                        className: 'btn btn-outline btn-xs',
                        style: { color: '#16a34a' },
                        title: 'Share WhatsApp Reminder',
                        onClick: () => {
                          if (onWhatsApp) onWhatsApp({ id: d.customerId, name: d.customerName, phone: d.customerPhone });
                        }
                      }, 'WA'),
                      h('button', {
                        className: 'btn btn-outline btn-xs',
                        title: 'Customer 360',
                        onClick: () => {
                          if (onCustomer360) onCustomer360({ id: d.customerId, name: d.customerName });
                        }
                      }, '360')
                    )
                  )
                ))
            )
          )
        ),

        // Pagination
        h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 4px', fontSize: '0.78rem' } },
          h('span', null, `Showing ${debtorsList.length === 0 ? 0 : (safeDebtorPage - 1) * debtorPageSize + 1}–${Math.min(safeDebtorPage * debtorPageSize, debtorsList.length)} of ${debtorsList.length} debtors`),
          h('div', { style: { display: 'flex', gap: 4, alignItems: 'center' } },
            h('button', {
              className: 'btn btn-outline btn-xs',
              disabled: safeDebtorPage <= 1,
              onClick: () => setDebtorPage(p => Math.max(1, p - 1))
            }, '◀ Prev'),
            h('span', { style: { padding: '0 6px', fontWeight: 600 } }, `${safeDebtorPage} / ${totalDebtorPages}`),
            h('button', {
              className: 'btn btn-outline btn-xs',
              disabled: safeDebtorPage >= totalDebtorPages,
              onClick: () => setDebtorPage(p => Math.min(totalDebtorPages, p + 1))
            }, 'Next ▶')
          )
        )
      ),

      // ── SUB-VIEW 3: OVERDUE INVOICES ──
      activeSubTab === 'overdue' && h('div', { style: { display: 'flex', flexDirection: 'column', gap: 12 } },
        h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' } },
          h('h4', { style: { margin: 0, fontSize: '0.98rem', fontWeight: 700 } }, `📋 Active Overdue Invoices (${overdueInvoices.length})`),
          h('span', { style: { fontSize: '0.78rem', color: 'var(--text-secondary)' } }, 'Sorted by oldest due date')
        ),
        h('div', { style: { overflowX: 'auto', border: '1px solid var(--border)', borderRadius: 8 } },
          h('table', { className: 'table table-hover', style: { width: '100%', margin: 0, fontSize: '0.82rem' } },
            h('thead', null,
              h('tr', { style: { background: 'var(--bg-secondary)', borderBottom: '2px solid var(--border)' } },
                h('th', { style: { padding: '8px 12px' } }, 'Invoice #'),
                h('th', { style: { padding: '8px 12px' } }, 'Customer'),
                h('th', { style: { padding: '8px 12px' } }, 'Due Date'),
                h('th', { style: { padding: '8px 12px' } }, 'Days Overdue'),
                h('th', { style: { padding: '8px 12px', textAlign: 'right' } }, 'Invoice Total'),
                h('th', { style: { padding: '8px 12px', textAlign: 'right' } }, 'Balance Due'),
                h('th', { style: { padding: '8px 12px', textAlign: 'center' } }, 'Actions')
              )
            ),
            h('tbody', null,
              overdueInvoices.length === 0 ?
                h('tr', null,
                  h('td', { colSpan: 7, style: { padding: 30, textAlign: 'center', color: 'var(--text-secondary)' } },
                    '🎉 No overdue invoices across the firm!'
                  )
                ) :
                overdueInvoices.map(inv => h('tr', {
                  key: inv.invoiceId,
                  style: { borderBottom: '1px solid var(--border)' }
                },
                  h('td', { style: { padding: '10px 12px', fontWeight: 700 } }, inv.invoiceNumber),
                  h('td', { style: { padding: '10px 12px' } },
                    h('div', { style: { fontWeight: 600 } }, inv.customerName),
                    h('div', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)' } }, inv.customerPhone || '')
                  ),
                  h('td', { style: { padding: '10px 12px' } }, inv.dueDate),
                  h('td', { style: { padding: '10px 12px' } },
                    h('span', {
                      className: `badge ${inv.daysOverdue > 90 ? 'badge-danger' : inv.daysOverdue > 30 ? 'badge-warning' : 'badge-outline'}`,
                      style: { fontSize: '0.72rem' }
                    }, `${inv.daysOverdue} days (${inv.agingBucket})`)
                  ),
                  h('td', { style: { padding: '10px 12px', textAlign: 'right', fontFamily: 'var(--font-mono, monospace)' } },
                    formatInr(inv.totalAmount)
                  ),
                  h('td', { style: { padding: '10px 12px', textAlign: 'right', fontWeight: 700, color: '#b91c1c', fontFamily: 'var(--font-mono, monospace)' } },
                    formatInr(inv.balanceDue)
                  ),
                  h('td', { style: { padding: '10px 12px', textAlign: 'center' } },
                    h('div', { style: { display: 'flex', gap: 4, justifyContent: 'center' } },
                      h('button', {
                        className: 'btn btn-primary btn-xs',
                        onClick: () => {
                          if (onSettle) onSettle({ id: inv.customerId, name: inv.customerName }, [inv.invoiceId]);
                        }
                      }, 'Settle'),
                      h('button', {
                        className: 'btn btn-outline btn-xs',
                        style: { color: '#16a34a' },
                        onClick: () => {
                          if (onWhatsApp) onWhatsApp({ id: inv.customerId, name: inv.customerName, phone: inv.customerPhone });
                        }
                      }, 'WhatsApp')
                    )
                  )
                ))
            )
          )
        )
      ),

      // ── SUB-VIEW 4: FOLLOW-UPS & PROMISES ──
      activeSubTab === 'followups' && h('div', { style: { display: 'flex', flexDirection: 'column', gap: 12 } },
        h('div', {
          style: {
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 10
          }
        },
          h('div', { style: { display: 'flex', gap: 6 } },
            ['DUE_TODAY', 'UPCOMING', 'OVERDUE', 'ALL'].map(st => h('button', {
              key: st,
              className: `btn btn-xs ${followUpFilter === st ? 'btn-primary' : 'btn-outline'}`,
              style: { fontWeight: 600, borderRadius: 14, padding: '4px 12px' },
              onClick: () => setFollowUpFilter(st)
            }, st.replace('_', ' ')))
          ),
          h('span', { style: { fontSize: '0.78rem', color: 'var(--text-secondary)' } }, `${followUps.length} follow-ups in queue`)
        ),

        h('div', { style: { display: 'flex', flexDirection: 'column', gap: 8 } },
          followUps.length === 0 ?
            h('div', { style: { padding: 30, textAlign: 'center', color: 'var(--text-secondary)', background: 'var(--bg-secondary)', borderRadius: 8 } },
              'No scheduled follow-ups or promises for the selected filter.'
            ) :
            followUps.map(f => h('div', {
              key: f.id + '_' + f.source,
              style: {
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                padding: '12px 16px',
                background: 'var(--bg-secondary)',
                border: '1px solid var(--border)',
                borderRadius: 8,
                flexWrap: 'wrap',
                gap: 10
              }
            },
              h('div', { style: { display: 'flex', alignItems: 'center', gap: 12 } },
                h('span', {
                  className: `badge ${f.status === 'OVERDUE' || f.status === 'MISSED' ? 'badge-danger' : f.status === 'DUE_TODAY' ? 'badge-warning' : 'badge-primary'}`,
                  style: { fontSize: '0.72rem', fontWeight: 600 }
                }, f.status),
                h('div', null,
                  h('div', { style: { fontWeight: 700, fontSize: '0.90rem' } }, f.customerName),
                  h('div', { style: { fontSize: '0.78rem', color: 'var(--text-secondary)', marginTop: 2 } },
                    `${f.title} • Target Date: ${f.dueDate || 'Open'}`
                  ),
                  f.notes && h('div', { style: { fontSize: '0.74rem', color: 'var(--text-secondary)', fontStyle: 'italic', marginTop: 2 } },
                    `"${f.notes}"`
                  )
                )
              ),
              h('div', { style: { display: 'flex', alignItems: 'center', gap: 6 } },
                f.source === 'PROMISE' && h('button', {
                  className: 'btn btn-outline btn-xs',
                  onClick: () => {
                    setRescheduleModalPromise(f);
                    setRescheduleDate(f.dueDate || '');
                  }
                }, 'Reschedule'),
                h('button', {
                  className: 'btn btn-primary btn-xs',
                  onClick: () => {
                    if (onSettle) onSettle({ id: f.customerId, name: f.customerName });
                  }
                }, 'Settle'),
                h('button', {
                  className: 'btn btn-outline btn-xs',
                  style: { color: '#16a34a' },
                  onClick: () => {
                    if (onWhatsApp) onWhatsApp({ id: f.customerId, name: f.customerName, phone: f.customerPhone });
                  }
                }, 'WhatsApp')
              )
            ))
        )
      ),

      // ── SUB-VIEW 5: STATEMENTS & REPORTS ──
      activeSubTab === 'reports' && h('div', { style: { display: 'flex', flexDirection: 'column', gap: 16 } },
        // Report Download Cards
        h('div', {
          style: {
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
            gap: 14
          }
        },
          // Card 1: Debtors Register Export
          h('div', {
            style: {
              background: 'var(--bg-secondary)',
              border: '1px solid var(--border)',
              borderRadius: 10,
              padding: 16,
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'space-between'
            }
          },
            h('div', null,
              h('h4', { style: { margin: '0 0 6px 0', fontSize: '0.96rem', fontWeight: 700 } }, '📑 Debtor Register & Aging Export'),
              h('p', { style: { margin: 0, fontSize: '0.78rem', color: 'var(--text-secondary)' } },
                'Download complete register of all debtor balances, overdue amounts, and 0-30, 31-60, 61-90, 90+ days aging breakdown.'
              )
            ),
            h('div', { style: { display: 'flex', gap: 8, marginTop: 14 } },
              h('a', {
                href: API.debtors.exportCsvUrl(),
                className: 'btn btn-primary btn-sm',
                style: { flex: 1, textAlign: 'center' },
                download: true
              }, 'Download CSV'),
              h('a', {
                href: API.debtors.exportPdfUrl(),
                className: 'btn btn-outline btn-sm',
                style: { flex: 1, textAlign: 'center' },
                download: true
              }, 'Download PDF Report')
            )
          ),

          // Card 2: Bulk Customer Statements ZIP
          h('div', {
            style: {
              background: 'var(--bg-secondary)',
              border: '1px solid var(--border)',
              borderRadius: 10,
              padding: 16,
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'space-between'
            }
          },
            h('div', null,
              h('h4', { style: { margin: '0 0 6px 0', fontSize: '0.96rem', fontWeight: 700 } }, '📦 Bulk Customer Statements (ZIP)'),
              h('p', { style: { margin: 0, fontSize: '0.78rem', color: 'var(--text-secondary)' } },
                'Generate individual PDF statement ledgers for all debtors in the firm and package them into a single downloadable ZIP archive.'
              ),
              h('div', { style: { display: 'flex', gap: 8, marginTop: 10 } },
                h('div', { style: { flex: 1 } },
                  h('label', { style: { fontSize: '0.70rem', color: 'var(--text-secondary)' } }, 'From Date (Optional)'),
                  h('input', {
                    type: 'date',
                    className: 'form-control form-control-sm',
                    value: bulkFrom,
                    onChange: (e) => setBulkFrom(e.target.value)
                  })
                ),
                h('div', { style: { flex: 1 } },
                  h('label', { style: { fontSize: '0.70rem', color: 'var(--text-secondary)' } }, 'To Date (Optional)'),
                  h('input', {
                    type: 'date',
                    className: 'form-control form-control-sm',
                    value: bulkTo,
                    onChange: (e) => setBulkTo(e.target.value)
                  })
                )
              )
            ),
            h('button', {
              className: 'btn btn-primary btn-sm',
              style: { marginTop: 14 },
              disabled: isDownloadingZip,
              onClick: handleDownloadBulkZip
            }, isDownloadingZip ? 'Packaging ZIP Archive...' : '📦 Download Bulk Statements (.ZIP)')
          )
        )
      ),

      // ── MODAL 1: PROMISE TO PAY MODAL ──
      promiseModalCustomer && h('div', { className: 'modal-backdrop', style: { position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1050 } },
        h('div', { className: 'modal-dialog', style: { background: 'var(--bg-primary)', border: '1px solid var(--border)', borderRadius: 10, width: '100%', maxWidth: 440, padding: 20, boxShadow: '0 8px 30px rgba(0,0,0,0.2)' } },
          h('h4', { style: { margin: '0 0 12px 0', fontWeight: 700 } }, `Record Promise to Pay`),
          h('div', { style: { fontSize: '0.84rem', color: 'var(--text-secondary)', marginBottom: 14 } },
            `Customer: `, h('strong', { style: { color: 'var(--text-primary)' } }, promiseModalCustomer.customerName),
            ` (Outstanding: ${formatInr(promiseModalCustomer.outstandingAmount)})`
          ),
          h('div', { style: { display: 'flex', flexDirection: 'column', gap: 10 } },
            h('div', null,
              h('label', { style: { fontSize: '0.76rem', fontWeight: 600 } }, 'Promised Amount (₹) *'),
              h('input', {
                type: 'number',
                className: 'form-control',
                placeholder: 'e.g. 5000',
                value: promiseAmount,
                onChange: (e) => setPromiseAmount(e.target.value)
              })
            ),
            h('div', null,
              h('label', { style: { fontSize: '0.76rem', fontWeight: 600 } }, 'Promised Date *'),
              h('input', {
                type: 'date',
                className: 'form-control',
                value: promiseDate,
                onChange: (e) => setPromiseDate(e.target.value)
              })
            ),
            h('div', null,
              h('label', { style: { fontSize: '0.76rem', fontWeight: 600 } }, 'Notes / Terms'),
              h('textarea', {
                className: 'form-control',
                rows: 2,
                placeholder: 'e.g. Promised via phone to clear by NEFT...',
                value: promiseNotes,
                onChange: (e) => setPromiseNotes(e.target.value)
              })
            )
          ),
          h('div', { style: { display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 18 } },
            h('button', {
              className: 'btn btn-outline btn-sm',
              onClick: () => setPromiseModalCustomer(null)
            }, 'Cancel'),
            h('button', {
              className: 'btn btn-primary btn-sm',
              disabled: isSavingPromise,
              onClick: handleSavePromise
            }, isSavingPromise ? 'Recording...' : 'Save Promise')
          )
        )
      ),

      // ── MODAL 2: LOG ACTIVITY MODAL ──
      activityModalCustomer && h('div', { className: 'modal-backdrop', style: { position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1050 } },
        h('div', { className: 'modal-dialog', style: { background: 'var(--bg-primary)', border: '1px solid var(--border)', borderRadius: 10, width: '100%', maxWidth: 440, padding: 20, boxShadow: '0 8px 30px rgba(0,0,0,0.2)' } },
          h('h4', { style: { margin: '0 0 12px 0', fontWeight: 700 } }, `Log Collection Activity`),
          h('div', { style: { fontSize: '0.84rem', color: 'var(--text-secondary)', marginBottom: 14 } },
            `Customer: `, h('strong', { style: { color: 'var(--text-primary)' } }, activityModalCustomer.customerName)
          ),
          h('div', { style: { display: 'flex', flexDirection: 'column', gap: 10 } },
            h('div', null,
              h('label', { style: { fontSize: '0.76rem', fontWeight: 600 } }, 'Activity Type *'),
              h('select', {
                className: 'form-control',
                value: activityType,
                onChange: (e) => setActivityType(e.target.value)
              },
                h('option', { value: 'CALL_LOGGED' }, '📞 Phone Call'),
                h('option', { value: 'NOTE_ADDED' }, '📝 Internal Note'),
                h('option', { value: 'WHATSAPP_OPENED' }, '💬 WhatsApp Follow-up'),
                h('option', { value: 'REMINDER_PREPARED' }, '⏰ Reminder Scheduled')
              )
            ),
            h('div', null,
              h('label', { style: { fontSize: '0.76rem', fontWeight: 600 } }, 'Details / Notes *'),
              h('textarea', {
                className: 'form-control',
                rows: 3,
                placeholder: 'Record conversation details, feedback, or follow-up plan...',
                value: activityDesc,
                onChange: (e) => setActivityDesc(e.target.value)
              })
            )
          ),
          h('div', { style: { display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 18 } },
            h('button', {
              className: 'btn btn-outline btn-sm',
              onClick: () => setActivityModalCustomer(null)
            }, 'Cancel'),
            h('button', {
              className: 'btn btn-primary btn-sm',
              disabled: isSavingActivity,
              onClick: handleSaveActivity
            }, isSavingActivity ? 'Saving...' : 'Record Activity')
          )
        )
      ),

      // ── MODAL 3: RESCHEDULE PROMISE MODAL ──
      rescheduleModalPromise && h('div', { className: 'modal-backdrop', style: { position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1050 } },
        h('div', { className: 'modal-dialog', style: { background: 'var(--bg-primary)', border: '1px solid var(--border)', borderRadius: 10, width: '100%', maxWidth: 440, padding: 20, boxShadow: '0 8px 30px rgba(0,0,0,0.2)' } },
          h('h4', { style: { margin: '0 0 12px 0', fontWeight: 700 } }, `Reschedule Promise to Pay`),
          h('div', { style: { fontSize: '0.84rem', color: 'var(--text-secondary)', marginBottom: 14 } },
            `Customer: `, h('strong', { style: { color: 'var(--text-primary)' } }, rescheduleModalPromise.customerName),
            ` • Remaining: ${formatInr(rescheduleModalPromise.outstandingAmount)}`
          ),
          h('div', { style: { display: 'flex', flexDirection: 'column', gap: 10 } },
            h('div', null,
              h('label', { style: { fontSize: '0.76rem', fontWeight: 600 } }, 'New Promised Date *'),
              h('input', {
                type: 'date',
                className: 'form-control',
                value: rescheduleDate,
                onChange: (e) => setRescheduleDate(e.target.value)
              })
            ),
            h('div', null,
              h('label', { style: { fontSize: '0.76rem', fontWeight: 600 } }, 'Reason / Notes'),
              h('textarea', {
                className: 'form-control',
                rows: 2,
                placeholder: 'Reason for rescheduling...',
                value: rescheduleNotes,
                onChange: (e) => setRescheduleNotes(e.target.value)
              })
            )
          ),
          h('div', { style: { display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 18 } },
            h('button', {
              className: 'btn btn-outline btn-sm',
              onClick: () => setRescheduleModalPromise(null)
            }, 'Cancel'),
            h('button', {
              className: 'btn btn-primary btn-sm',
              onClick: handleSaveReschedule
            }, 'Reschedule')
          )
        )
      )
    );
  }

  if (typeof window !== 'undefined') {
    window.DebtorsCollectionsWorkspace = DebtorsCollectionsWorkspace;
  }
})();
