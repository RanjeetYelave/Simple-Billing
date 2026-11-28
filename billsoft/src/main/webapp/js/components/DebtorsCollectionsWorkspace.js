/**
 * RupeeCRM — Debtors & Collections Management Operating Center
 * Redesigned for Multiple Payment Promises, Manual Allocation & Independent Follow-ups.
 */
(function() {
  'use strict';
  const { useState, useEffect, useCallback, useMemo, createElement: h, Fragment } = React;

  function DebtorsCollectionsWorkspace(props) {
    const { onSettle, onCustomer360, onWhatsApp, onStatement, onCreateBill } = props;

    // Sub-Tabs: overview | debtors | overdue | promises | followups | reports
    const [activeSubTab, setActiveSubTab] = useState('overview');
    const [overview, setOverview] = useState(null);
    const [debtorsList, setDebtorsList] = useState([]);
    const [overdueInvoices, setOverdueInvoices] = useState([]);
    const [followUps, setFollowUps] = useState([]);
    const [promisesWorkspace, setPromisesWorkspace] = useState({
      activeCount: 0,
      dueTodayCount: 0,
      overdueCount: 0,
      fulfilledCount: 0,
      promises: []
    });
    const [loading, setLoading] = useState(true);

    // Filters and search for All Debtors
    const [debtorSearch, setDebtorSearch] = useState('');
    const [debtorFilter, setDebtorFilter] = useState('all'); // all | overdue | critical | promised | advance
    const [debtorSortBy, setDebtorSortBy] = useState('default');
    const [debtorSortDir, setDebtorSortDir] = useState('desc');
    const [debtorPage, setDebtorPage] = useState(1);
    const debtorPageSize = 10;

    // Follow-ups filter
    const [followUpFilter, setFollowUpFilter] = useState('ALL'); // ALL | DUE_TODAY | UPCOMING | OVERDUE
    const [activeMoreMenuCustomerId, setActiveMoreMenuCustomerId] = useState(null);

    // Payment Promises filter & search
    const [promiseFilter, setPromiseFilter] = useState('ALL'); // ALL | UPCOMING | DUE_TODAY | OVERDUE | PARTIALLY_FULFILLED | FULFILLED | CANCELLED
    const [promiseSearch, setPromiseSearch] = useState('');

    // Bulk statement zip date range
    const [bulkFrom, setBulkFrom] = useState('');
    const [bulkTo, setBulkTo] = useState('');
    const [isDownloadingZip, setIsDownloadingZip] = useState(false);
    const [isRefreshing, setIsRefreshing] = useState(false);

    // Create Promise Modal State
    const [createPromiseModal, setCreatePromiseModal] = useState({
      isOpen: false,
      customer: null,
      customerId: '',
      invoiceId: '',
      customerInvoices: [],
      promisedAmount: '',
      promisedDate: '',
      notes: '',
      availableBalance: null,
      invoiceOutstanding: null,
      loadingInvoices: false,
      saving: false
    });

    // Edit Promise Modal State
    const [editPromiseModal, setEditPromiseModal] = useState({
      isOpen: false,
      promise: null,
      promisedAmount: '',
      promisedDate: '',
      notes: '',
      saving: false
    });

    // Promise History Modal State
    const [historyModal, setHistoryModal] = useState({
      isOpen: false,
      promise: null,
      loading: false,
      items: []
    });

    // Log Activity Modal State
    const [activityModalCustomer, setActivityModalCustomer] = useState(null);
    const [activityType, setActivityType] = useState('CALL_LOGGED');
    const [activityDesc, setActivityDesc] = useState('');
    const [isSavingActivity, setIsSavingActivity] = useState(false);

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

    // Load Promises Workspace
    const loadPromises = useCallback(async () => {
      try {
        const data = await API.debtors.promises({
          status: promiseFilter,
          search: promiseSearch
        });
        setPromisesWorkspace(data || {
          activeCount: 0,
          dueTodayCount: 0,
          overdueCount: 0,
          fulfilledCount: 0,
          promises: []
        });
      } catch (err) {
        console.error('Failed to load promises workspace:', err);
      }
    }, [promiseFilter, promiseSearch]);

    // Load Follow-ups (Cleanly separated)
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
          loadPromises(),
          loadFollowUps()
        ]);
      } finally {
        setLoading(false);
      }
    }, [loadOverview, loadDebtors, loadOverdueInvoices, loadPromises, loadFollowUps]);

    const handleUserRefresh = async () => {
      if (isRefreshing) return;
      setIsRefreshing(true);
      try {
        await Promise.all([
          loadOverview(),
          loadDebtors(),
          loadOverdueInvoices(),
          loadPromises(),
          loadFollowUps()
        ]);
        if (typeof showToast === 'function') {
          showToast('Debtors & Collections refreshed successfully', 'success');
        }
      } catch (err) {
        console.error('Refresh failed:', err);
        if (typeof showToast === 'function') {
          showToast('Failed to refresh debtors data', 'error');
        }
      } finally {
        setIsRefreshing(false);
      }
    };

    // Open Create Promise Modal
    const openCreatePromiseModal = async (cust, preselectedInvoiceId = null) => {
      const customerId = cust ? (cust.customerId || cust.id) : null;
      setCreatePromiseModal({
        isOpen: true,
        customer: cust,
        customerId: customerId ? String(customerId) : '',
        invoiceId: preselectedInvoiceId ? String(preselectedInvoiceId) : '',
        customerInvoices: [],
        promisedAmount: '',
        promisedDate: '',
        notes: '',
        availableBalance: null,
        invoiceOutstanding: null,
        loadingInvoices: !!customerId,
        saving: false
      });

      if (customerId) {
        try {
          const invs = await API.customers.outstandingInvoices(customerId);
          const validInvs = Array.isArray(invs) ? invs : [];
          let avail = null;
          let invOut = null;
          if (preselectedInvoiceId) {
            const targetInv = validInvs.find(i => i.invoiceId === preselectedInvoiceId);
            if (targetInv) {
              invOut = parseFloat(targetInv.outstandingAmount) || 0;
              const pRes = await API.debtors.invoicePromises(preselectedInvoiceId);
              const pList = Array.isArray(pRes) ? pRes : [];
              const activeCommitted = pList
                .filter(p => p.status !== 'CANCELLED' && p.status !== 'FULFILLED')
                .reduce((sum, p) => sum + (parseFloat(p.remainingAmount) || 0), 0);
              avail = Math.max(0, invOut - activeCommitted);
            }
          }
          setCreatePromiseModal(prev => ({
            ...prev,
            customerInvoices: validInvs,
            availableBalance: avail,
            invoiceOutstanding: invOut,
            loadingInvoices: false
          }));
        } catch (err) {
          console.error('Failed loading customer invoices:', err);
          setCreatePromiseModal(prev => ({ ...prev, loadingInvoices: false }));
        }
      }
    };

    const handleSelectCreateInvoice = async (invIdStr) => {
      const invId = invIdStr ? parseInt(invIdStr, 10) : null;
      let avail = null;
      let invOut = null;
      if (invId) {
        const target = createPromiseModal.customerInvoices.find(i => i.invoiceId === invId);
        if (target) {
          invOut = parseFloat(target.outstandingAmount) || 0;
          try {
            const pRes = await API.debtors.invoicePromises(invId);
            const pList = Array.isArray(pRes) ? pRes : [];
            const activeCommitted = pList
              .filter(p => p.status !== 'CANCELLED' && p.status !== 'FULFILLED')
              .reduce((sum, p) => sum + (parseFloat(p.remainingAmount) || 0), 0);
            avail = Math.max(0, invOut - activeCommitted);
          } catch (e) {
            avail = invOut;
          }
        }
      }
      setCreatePromiseModal(prev => ({
        ...prev,
        invoiceId: invIdStr,
        availableBalance: avail,
        invoiceOutstanding: invOut
      }));
    };

    const handleSaveCreatePromise = async () => {
      const { customerId, invoiceId, promisedAmount, promisedDate, notes, availableBalance } = createPromiseModal;
      if (!customerId) {
        if (typeof showToast === 'function') showToast('Please select a customer', 'warning');
        return;
      }
      const parsedAmt = parseFloat(promisedAmount);
      if (isNaN(parsedAmt) || parsedAmt <= 0) {
        if (typeof showToast === 'function') showToast('Promised amount must be greater than 0', 'warning');
        return;
      }
      if (!promisedDate) {
        if (typeof showToast === 'function') showToast('Please select promised date', 'warning');
        return;
      }
      if (availableBalance !== null && parsedAmt > availableBalance + 0.01) {
        if (typeof showToast === 'function') {
          showToast(`Promised amount (₹${parsedAmt.toFixed(2)}) exceeds available uncommitted balance (₹${availableBalance.toFixed(2)})`, 'error');
        }
        return;
      }

      setCreatePromiseModal(prev => ({ ...prev, saving: true }));
      try {
        await API.debtors.createPromise({
          customerId: parseInt(customerId, 10),
          invoiceId: invoiceId ? parseInt(invoiceId, 10) : null,
          promisedAmount: parsedAmt,
          promisedDate,
          notes: notes.trim() || undefined
        });
        if (typeof showToast === 'function') showToast('Payment promise recorded successfully', 'success');
        setCreatePromiseModal(prev => ({ ...prev, isOpen: false }));
        loadPromises();
        loadOverview();
        loadDebtors();
      } catch (err) {
        if (typeof showToast === 'function') showToast(err.message || 'Failed to record promise', 'error');
      } finally {
        setCreatePromiseModal(prev => ({ ...prev, saving: false }));
      }
    };

    // Open Edit Promise Modal
    const openEditPromiseModal = (pr) => {
      setEditPromiseModal({
        isOpen: true,
        promise: pr,
        promisedAmount: String(pr.promisedAmount || ''),
        promisedDate: pr.promisedDate || '',
        notes: pr.notes || '',
        saving: false
      });
    };

    const handleSaveEditPromise = async () => {
      const { promise, promisedAmount, promisedDate, notes } = editPromiseModal;
      if (!promise) return;
      const parsedAmt = parseFloat(promisedAmount);
      if (isNaN(parsedAmt) || parsedAmt <= 0) {
        if (typeof showToast === 'function') showToast('Promised amount must be greater than 0', 'warning');
        return;
      }
      const fulfilled = parseFloat(promise.fulfilledAmount) || 0;
      if (parsedAmt < fulfilled) {
        if (typeof showToast === 'function') showToast(`Promised amount cannot be less than already fulfilled ₹${fulfilled.toFixed(2)}`, 'error');
        return;
      }
      if (!promisedDate) {
        if (typeof showToast === 'function') showToast('Please select promised date', 'warning');
        return;
      }

      setEditPromiseModal(prev => ({ ...prev, saving: true }));
      try {
        await API.debtors.editPromise(promise.id, {
          promisedAmount: parsedAmt,
          promisedDate,
          notes: notes.trim() || undefined
        });
        if (typeof showToast === 'function') showToast('Payment promise updated successfully', 'success');
        setEditPromiseModal(prev => ({ ...prev, isOpen: false }));
        loadPromises();
        loadOverview();
      } catch (err) {
        if (typeof showToast === 'function') showToast(err.message || 'Failed to update promise', 'error');
      } finally {
        setEditPromiseModal(prev => ({ ...prev, saving: false }));
      }
    };

    // Handle Cancel Promise
    const handleCancelPromise = async (pr) => {
      const confirmed = window.confirm(
        `Cancel payment promise of ₹${pr.remainingAmount} by ${pr.customerName} on ${pr.promisedDate}?\n\nThis will be permanently recorded in history and will no longer count as an active commitment.`
      );
      if (!confirmed) return;

      const reason = window.prompt('Enter reason for cancellation (optional):', 'Customer requested cancellation');
      try {
        await API.debtors.cancelPromise(pr.id, reason || 'Cancelled by user');
        if (typeof showToast === 'function') showToast('Promise cancelled successfully', 'info');
        loadPromises();
        loadOverview();
      } catch (err) {
        if (typeof showToast === 'function') showToast(err.message || 'Failed to cancel promise', 'error');
      }
    };

    // Open History Modal
    const openHistoryModal = async (pr) => {
      setHistoryModal({
        isOpen: true,
        promise: pr,
        loading: true,
        items: []
      });
      try {
        const items = await API.debtors.promiseHistory(pr.id);
        setHistoryModal(prev => ({
          ...prev,
          loading: false,
          items: Array.isArray(items) ? items : []
        }));
      } catch (err) {
        console.error('Failed loading history:', err);
        setHistoryModal(prev => ({ ...prev, loading: false }));
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
        loadFollowUps();
      } catch (err) {
        if (typeof showToast === 'function') showToast('Failed to record activity: ' + (err.message || 'Error'), 'error');
      } finally {
        setIsSavingActivity(false);
      }
    };

    const getLocalCalendarDate = () => {
      const now = new Date();
      const y = now.getFullYear();
      const m = String(now.getMonth() + 1).padStart(2, '0');
      const d = String(now.getDate()).padStart(2, '0');
      return `${y}-${m}-${d}`;
    };

    // Exports
    const handleExportCsv = async () => {
      try {
        await API._ensureFirmReady();
        const url = API._qs('/api/debtors/export/csv');
        const res = await API._request(url);
        if (!res.ok) {
          if (typeof showToast === 'function') showToast('Failed to export CSV', 'error');
          return;
        }
        const blob = await res.blob();
        const blobUrl = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = blobUrl;
        a.download = `Debtors_Register_${getLocalCalendarDate()}.csv`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(blobUrl);
        if (typeof showToast === 'function') showToast('Debtors Register CSV downloaded', 'success');
      } catch (err) {
        if (typeof showToast === 'function') showToast(err.message || 'CSV export failed', 'error');
      }
    };

    const handleExportPdf = async () => {
      try {
        if (typeof showToast === 'function') showToast('Generating Debtors Aging Report PDF...', 'info');
        await API._ensureFirmReady();
        const url = API._qs('/api/debtors/export/pdf');
        const res = await API._request(url);
        if (!res.ok) {
          if (typeof showToast === 'function') showToast('Failed to export PDF', 'error');
          return;
        }
        const blob = await res.blob();
        const filename = `Debtors_Aging_Report_${getLocalCalendarDate()}.pdf`;
        if (typeof BillsoftUtils !== 'undefined' && typeof BillsoftUtils.downloadBlob === 'function') {
          BillsoftUtils.downloadBlob(blob, filename);
        } else {
          const blobUrl = window.URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = blobUrl;
          a.download = filename;
          document.body.appendChild(a);
          a.click();
          document.body.removeChild(a);
          window.URL.revokeObjectURL(blobUrl);
        }
        if (typeof showToast === 'function') showToast('Aging Report PDF downloaded successfully', 'success');
      } catch (err) {
        if (typeof showToast === 'function') showToast(err.message || 'PDF export failed', 'error');
      }
    };

    const handleDownloadCustomerStatement = async (d) => {
      try {
        const custId = d.customerId || d.id;
        const custName = d.customerName || d.name || 'Customer';
        if (typeof showToast === 'function') showToast(`Generating statement for ${custName}...`, 'info');
        const blob = await API.statements.customerPdf(custId);
        const sanitizedName = custName.replace(/[^a-zA-Z0-9_-]/g, '_');
        const today = getLocalCalendarDate();
        const filename = `Statement-${sanitizedName}-${today}.pdf`;
        if (typeof BillsoftUtils !== 'undefined' && typeof BillsoftUtils.downloadBlob === 'function') {
          BillsoftUtils.downloadBlob(blob, filename);
        } else {
          const blobUrl = window.URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = blobUrl;
          a.download = filename;
          document.body.appendChild(a);
          a.click();
          document.body.removeChild(a);
          window.URL.revokeObjectURL(blobUrl);
        }
        if (typeof showToast === 'function') showToast(`Statement for ${custName} downloaded successfully!`, 'success');
      } catch (err) {
        console.error(err);
        if (typeof showToast === 'function') showToast(err.message || 'Failed to download statement', 'error');
      }
    };

    const handleDownloadBulkZip = () => {
      setIsDownloadingZip(true);
      try {
        const url = API.statements.customerBulkZipUrl(bulkFrom, bulkTo);
        window.location.href = url;
        if (typeof showToast === 'function') showToast('Preparing customer statements ZIP...', 'info');
      } catch (err) {
        if (typeof showToast === 'function') showToast('Failed to download statements: ' + err.message, 'error');
      } finally {
        setTimeout(() => setIsDownloadingZip(false), 3000);
      }
    };

    // Close More menu when clicking outside
    useEffect(() => {
      if (!activeMoreMenuCustomerId) return;
      const handleClickOutside = (e) => {
        if (!e.target.closest('.debtor-more-menu-container')) {
          setActiveMoreMenuCustomerId(null);
        }
      };
      const handleKeyDown = (e) => {
        if (e.key === 'Escape') setActiveMoreMenuCustomerId(null);
      };
      document.addEventListener('mousedown', handleClickOutside);
      document.addEventListener('keydown', handleKeyDown);
      return () => {
        document.removeEventListener('mousedown', handleClickOutside);
        document.removeEventListener('keydown', handleKeyDown);
      };
    }, [activeMoreMenuCustomerId]);

    useEffect(() => {
      refreshAll();
    }, [refreshAll]);

    useEffect(() => {
      if (activeSubTab === 'overview') loadOverview();
      else if (activeSubTab === 'debtors') loadDebtors();
      else if (activeSubTab === 'overdue') loadOverdueInvoices();
      else if (activeSubTab === 'promises') loadPromises();
      else if (activeSubTab === 'followups') loadFollowUps();
    }, [activeSubTab, loadOverview, loadDebtors, loadOverdueInvoices, loadPromises, loadFollowUps]);

    // Format Currency Helper
    const formatInr = (val) => {
      if (typeof BillsoftUtils !== 'undefined' && BillsoftUtils.formatCurrency) {
        return BillsoftUtils.formatCurrency(val || 0);
      }
      const num = Number(val || 0);
      return '₹' + num.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    };

    // Sub-Tabs Nav: exactly 5 primary + reports
    const subNavTabs = [
      { id: 'overview', label: 'Overview & Aging' },
      { id: 'debtors', label: `All Debtors (${overview ? overview.totalDebtorsCount : debtorsList.length})` },
      { id: 'overdue', label: `Overdue Invoices (${overdueInvoices.length})` },
      { id: 'promises', label: `Payment Promises (${promisesWorkspace.activeCount})` },
      { id: 'followups', label: `Follow-ups (${followUps.filter(f => f.source !== 'PROMISE').length})` },
      { id: 'reports', label: 'Statements & Reports' }
    ];

    // Paginated Debtors
    const totalDebtorPages = Math.max(1, Math.ceil(debtorsList.length / debtorPageSize));
    const safeDebtorPage = Math.min(debtorPage, totalDebtorPages);
    const paginatedDebtors = debtorsList.slice((safeDebtorPage - 1) * debtorPageSize, safeDebtorPage * debtorPageSize);

    // Filter pure follow-up tasks (excluding promises)
    const pureFollowUps = followUps.filter(f => f.source !== 'PROMISE');

    // Check if any invoice has reconciliation required
    const reconciliationInvoices = useMemo(() => {
      const map = {};
      (promisesWorkspace.promises || []).forEach(p => {
        if (p.reconciliationRequired && p.invoiceId && !map[p.invoiceId]) {
          map[p.invoiceId] = p;
        }
      });
      return Object.values(map);
    }, [promisesWorkspace.promises]);

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
        h('div', { style: { display: 'flex', gap: 6, flexWrap: 'wrap' } },
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
            disabled: isRefreshing || loading,
            onClick: handleUserRefresh
          }, isRefreshing ? '⏳ Refreshing...' : '🔄 Refresh')
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
              )
            ),
            h('div', { style: { padding: 12, border: '1px solid #fed7aa', background: '#fff7ed', borderRadius: 8 } },
              h('div', { style: { fontSize: '0.75rem', fontWeight: 700, color: '#c2410c' } }, '31 – 60 Days'),
              h('div', { style: { fontSize: '1.2rem', fontWeight: 800, color: '#ea580c', marginTop: 4, fontFamily: 'var(--font-mono, monospace)' } },
                formatInr(overview ? overview.aging31To60 : 0)
              )
            ),
            h('div', { style: { padding: 12, border: '1px solid #fecaca', background: '#fef2f2', borderRadius: 8 } },
              h('div', { style: { fontSize: '0.75rem', fontWeight: 700, color: '#b91c1c' } }, '61 – 90 Days'),
              h('div', { style: { fontSize: '1.2rem', fontWeight: 800, color: '#dc2626', marginTop: 4, fontFamily: 'var(--font-mono, monospace)' } },
                formatInr(overview ? overview.aging61To90 : 0)
              )
            ),
            h('div', { style: { padding: 12, border: '1px solid #f87171', background: '#fee2e2', borderRadius: 8 } },
              h('div', { style: { fontSize: '0.75rem', fontWeight: 700, color: '#991b1b' } }, '90+ Days (Critical)'),
              h('div', { style: { fontSize: '1.2rem', fontWeight: 800, color: '#991b1b', marginTop: 4, fontFamily: 'var(--font-mono, monospace)' } },
                formatInr(overview ? overview.aging90Plus : 0)
              )
            )
          )
        ),

        // Quick Navigation Tiles
        h('div', { style: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 14 } },
          h('div', {
            style: {
              background: 'var(--bg-secondary)',
              border: '1px solid var(--border)',
              borderRadius: 10,
              padding: 16,
              cursor: 'pointer'
            },
            onClick: () => setActiveSubTab('promises')
          },
            h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' } },
              h('h4', { style: { margin: 0, fontSize: '0.96rem', fontWeight: 700 } }, '🤝 Payment Promises Queue'),
              h('span', { className: 'badge badge-primary' }, `${promisesWorkspace.activeCount} Active`)
            ),
            h('p', { style: { margin: '6px 0 0 0', fontSize: '0.80rem', color: 'var(--text-secondary)' } },
              `${promisesWorkspace.dueTodayCount} due today • ${promisesWorkspace.overdueCount} overdue commitments`
            )
          ),
          h('div', {
            style: {
              background: 'var(--bg-secondary)',
              border: '1px solid var(--border)',
              borderRadius: 10,
              padding: 16,
              cursor: 'pointer'
            },
            onClick: () => setActiveSubTab('followups')
          },
            h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' } },
              h('h4', { style: { margin: 0, fontSize: '0.96rem', fontWeight: 700 } }, '⏰ Follow-ups & Reminders'),
              h('span', { className: 'badge badge-outline' }, `${pureFollowUps.length} Pending`)
            ),
            h('p', { style: { margin: '6px 0 0 0', fontSize: '0.80rem', color: 'var(--text-secondary)' } },
              'Calls, visits, notes and customer reminders scheduled'
            )
          )
        )
      ),

      // ── SUB-VIEW 2: ALL DEBTORS ──
      activeSubTab === 'debtors' && h('div', { style: { display: 'flex', flexDirection: 'column', gap: 12 } },
        // Filter & Search Controls
        h('div', {
          style: {
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 10
          }
        },
          h('div', { style: { display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' } },
            h('input', {
              type: 'text',
              className: 'input',
              style: { width: 220, height: 32, fontSize: '0.82rem' },
              placeholder: 'Search customer name or phone...',
              value: debtorSearch,
              onChange: (e) => setDebtorSearch(e.target.value)
            }),
            h('select', {
              className: 'input',
              style: { width: 140, height: 32, fontSize: '0.82rem' },
              value: debtorFilter,
              onChange: (e) => setDebtorFilter(e.target.value)
            },
              h('option', { value: 'all' }, 'All Debtors'),
              h('option', { value: 'overdue' }, 'Has Overdue'),
              h('option', { value: 'critical' }, 'Critical 90+ Days'),
              h('option', { value: 'promised' }, 'Has Active Promise'),
              h('option', { value: 'advance' }, 'Advance Credit')
            )
          ),
          h('div', { style: { display: 'flex', gap: 6 } },
            h('button', {
              className: 'btn btn-outline btn-sm',
              onClick: handleExportCsv
            }, 'Export CSV')
          )
        ),

        // Debtors Table
        h('div', { style: { overflowX: 'auto', border: '1px solid var(--border)', borderRadius: 8 } },
          h('table', { className: 'table table-hover', style: { width: '100%', margin: 0, fontSize: '0.82rem' } },
            h('thead', null,
              h('tr', { style: { background: 'var(--bg-secondary)', borderBottom: '2px solid var(--border)' } },
                h('th', { style: { padding: '8px 12px' } }, 'Customer'),
                h('th', { style: { padding: '8px 12px' } }, 'Contact'),
                h('th', { style: { padding: '8px 12px', textAlign: 'right' } }, 'Total Outstanding'),
                h('th', { style: { padding: '8px 12px', textAlign: 'right' } }, 'Past Due / Overdue'),
                h('th', { style: { padding: '8px 12px' } }, 'Next Commitment'),
                h('th', { style: { padding: '8px 12px', textAlign: 'center' } }, 'Actions')
              )
            ),
            h('tbody', null,
              paginatedDebtors.length === 0 ?
                h('tr', null,
                  h('td', { colSpan: 6, style: { padding: 30, textAlign: 'center', color: 'var(--text-secondary)' } },
                    'No debtors match the selected filter criteria.'
                  )
                ) :
                paginatedDebtors.map(d => h('tr', {
                  key: d.customerId,
                  style: { borderBottom: '1px solid var(--border)' }
                },
                  h('td', { style: { padding: '10px 12px', fontWeight: 600 } },
                    h('div', null, d.customerName),
                    d.hasActivePromise && h('span', { className: 'badge badge-primary', style: { fontSize: '0.68rem', marginTop: 2 } }, '🤝 Active Promise')
                  ),
                  h('td', { style: { padding: '10px 12px', color: 'var(--text-secondary)' } }, d.customerPhone || '—'),
                  h('td', { style: { padding: '10px 12px', textAlign: 'right', fontWeight: 700, color: '#b91c1c', fontFamily: 'var(--font-mono, monospace)' } },
                    formatInr(d.outstandingAmount)
                  ),
                  h('td', { style: { padding: '10px 12px', textAlign: 'right', color: d.overdueAmount > 0 ? '#c2410c' : 'var(--text-secondary)', fontFamily: 'var(--font-mono, monospace)' } },
                    formatInr(d.overdueAmount)
                  ),
                  h('td', { style: { padding: '10px 12px', fontSize: '0.78rem' } },
                    d.nextFollowUpDate ? d.nextFollowUpDate : 'None scheduled'
                  ),
                  h('td', { style: { padding: '10px 12px', textAlign: 'center' } },
                    h('div', { style: { display: 'flex', gap: 6, justifyContent: 'center' } },
                      h('button', {
                        className: 'btn btn-primary btn-xs',
                        onClick: () => {
                          if (onSettle) onSettle({ id: d.customerId, name: d.customerName, outstanding: d.outstandingAmount });
                        }
                      }, 'Settle'),
                      h('button', {
                        className: 'btn btn-outline btn-xs',
                        onClick: () => openCreatePromiseModal(d)
                      }, '🤝 Promise'),
                      h('button', {
                        className: 'btn btn-outline btn-xs',
                        onClick: () => {
                          setActivityModalCustomer(d);
                          setActivityType('CALL_LOGGED');
                        }
                      }, '📝 Log Note')
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
                    }, `${inv.daysOverdue} days`)
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
                        onClick: () => openCreatePromiseModal({ id: inv.customerId, name: inv.customerName, customerName: inv.customerName }, inv.invoiceId)
                      }, '🤝 Promise')
                    )
                  )
                ))
            )
          )
        )
      ),

      // ── SUB-VIEW 4: PAYMENT PROMISES (STANDALONE VIEW) ──
      activeSubTab === 'promises' && h('div', { style: { display: 'flex', flexDirection: 'column', gap: 14 } },
        // Top Summary Metric Cards
        h('div', {
          style: {
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
            gap: 10
          }
        },
          h('div', { className: 'kpi-card', style: { padding: '10px 14px', background: 'var(--bg-secondary)', border: '1px solid var(--border)', borderRadius: 8 } },
            h('div', { style: { fontSize: '0.72rem', textTransform: 'uppercase', color: 'var(--text-secondary)', fontWeight: 600 } }, 'Active Promises'),
            h('div', { style: { fontSize: '1.3rem', fontWeight: 800, color: 'var(--primary, #059669)', marginTop: 2 } }, promisesWorkspace.activeCount),
            h('div', { style: { fontSize: '0.70rem', color: 'var(--text-secondary)' } }, 'Commitments pending fulfillment')
          ),
          h('div', { className: 'kpi-card', style: { padding: '10px 14px', background: 'var(--bg-secondary)', border: '1px solid var(--border)', borderRadius: 8 } },
            h('div', { style: { fontSize: '0.72rem', textTransform: 'uppercase', color: 'var(--text-secondary)', fontWeight: 600 } }, 'Due Today'),
            h('div', { style: { fontSize: '1.3rem', fontWeight: 800, color: '#d97706', marginTop: 2 } }, promisesWorkspace.dueTodayCount),
            h('div', { style: { fontSize: '0.70rem', color: 'var(--text-secondary)' } }, 'Expected collections today')
          ),
          h('div', { className: 'kpi-card', style: { padding: '10px 14px', background: 'var(--bg-secondary)', border: '1px solid var(--border)', borderRadius: 8 } },
            h('div', { style: { fontSize: '0.72rem', textTransform: 'uppercase', color: 'var(--text-secondary)', fontWeight: 600 } }, 'Overdue Promises'),
            h('div', { style: { fontSize: '1.3rem', fontWeight: 800, color: '#dc2626', marginTop: 2 } }, promisesWorkspace.overdueCount),
            h('div', { style: { fontSize: '0.70rem', color: 'var(--text-secondary)' } }, 'Target date has passed')
          ),
          h('div', { className: 'kpi-card', style: { padding: '10px 14px', background: 'var(--bg-secondary)', border: '1px solid var(--border)', borderRadius: 8 } },
            h('div', { style: { fontSize: '0.72rem', textTransform: 'uppercase', color: 'var(--text-secondary)', fontWeight: 600 } }, 'Fulfilled Promises'),
            h('div', { style: { fontSize: '1.3rem', fontWeight: 800, color: '#16a34a', marginTop: 2 } }, promisesWorkspace.fulfilledCount),
            h('div', { style: { fontSize: '0.70rem', color: 'var(--text-secondary)' } }, 'Fully paid & settled')
          )
        ),

        // Reconciliation Warning Alert Banners
        reconciliationInvoices.map(p => h('div', {
          key: 'recon_' + p.invoiceId,
          style: {
            background: '#fffbeb',
            border: '1px solid #fde68a',
            borderRadius: 8,
            padding: '10px 14px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 10
          }
        },
          h('div', null,
            h('div', { style: { fontWeight: 700, fontSize: '0.84rem', color: '#b45309' } },
              `⚠️ Reconciliation Required: ${p.customerName} — Invoice #${p.invoiceNumber}`
            ),
            h('div', { style: { fontSize: '0.76rem', color: '#92400e', marginTop: 2 } },
              `Remaining active promises (${formatInr(p.invoiceRemainingPromisesTotal)}) exceed invoice outstanding (${formatInr(p.invoiceOutstanding)}) by ${formatInr(p.excessPromisedAmount)} due to unallocated payment.`
            )
          ),
          h('button', {
            className: 'btn btn-outline btn-xs',
            style: { borderColor: '#d97706', color: '#b45309', fontWeight: 700, whiteSpace: 'nowrap' },
            onClick: () => openEditPromiseModal(p)
          }, '✏️ Reconcile Promise')
        )),

        // Filter Bar & Action Button
        h('div', {
          style: {
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 8
          }
        },
          h('div', { style: { display: 'flex', gap: 6, flexWrap: 'wrap' } },
            [
              { id: 'ALL', label: 'All' },
              { id: 'UPCOMING', label: 'Upcoming' },
              { id: 'DUE_TODAY', label: 'Due Today' },
              { id: 'OVERDUE', label: 'Overdue' },
              { id: 'PARTIALLY_FULFILLED', label: 'Partially Fulfilled' },
              { id: 'FULFILLED', label: 'Fulfilled' },
              { id: 'CANCELLED', label: 'Cancelled' }
            ].map(f => h('button', {
              key: f.id,
              className: `btn btn-xs ${promiseFilter === f.id ? 'btn-primary' : 'btn-outline'}`,
              style: { fontWeight: 600, borderRadius: 14, padding: '3px 10px', fontSize: '0.76rem' },
              onClick: () => setPromiseFilter(f.id)
            }, f.label))
          ),
          h('div', { style: { display: 'flex', gap: 8, alignItems: 'center' } },
            h('input', {
              type: 'text',
              className: 'input',
              style: { width: 190, height: 28, fontSize: '0.78rem' },
              placeholder: 'Search customer, invoice...',
              value: promiseSearch,
              onChange: (e) => setPromiseSearch(e.target.value)
            }),
            h('button', {
              className: 'btn btn-primary btn-sm',
              style: { padding: '4px 12px', fontSize: '0.80rem', fontWeight: 600 },
              onClick: () => openCreatePromiseModal(null)
            }, '➕ Record Promise')
          )
        ),

        // Promises List / Cards
        h('div', { style: { display: 'flex', flexDirection: 'column', gap: 8 } },
          (promisesWorkspace.promises || []).length === 0 ?
            h('div', { style: { padding: 30, textAlign: 'center', color: 'var(--text-secondary)', background: 'var(--bg-secondary)', borderRadius: 8, fontSize: '0.84rem' } },
              'No payment promises recorded matching this filter.'
            ) :
            (promisesWorkspace.promises || []).map(pr => {
              // Status Badge Styling
              let badgeColor = '#2563eb';
              let badgeBg = '#eff6ff';
              let badgeBorder = '#bfdbfe';
              if (pr.displayStatus === 'FULFILLED') {
                badgeColor = '#166534'; badgeBg = '#dcfce7'; badgeBorder = '#86efac';
              } else if (pr.displayStatus === 'OVERDUE') {
                badgeColor = '#991b1b'; badgeBg = '#fee2e2'; badgeBorder = '#fecaca';
              } else if (pr.displayStatus === 'DUE_TODAY') {
                badgeColor = '#92400e'; badgeBg = '#fef3c7'; badgeBorder = '#fde68a';
              } else if (pr.displayStatus === 'PARTIALLY_FULFILLED') {
                badgeColor = '#0369a1'; badgeBg = '#e0f2fe'; badgeBorder = '#bae6fd';
              } else if (pr.displayStatus === 'CANCELLED') {
                badgeColor = '#64748b'; badgeBg = '#f1f5f9'; badgeBorder = '#cbd5e1';
              }

              return h('div', {
                key: pr.id,
                style: {
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  padding: '12px 16px',
                  background: 'var(--bg-card, #ffffff)',
                  border: '1px solid var(--border)',
                  borderRadius: 8,
                  flexWrap: 'wrap',
                  gap: 12
                }
              },
                // Left Column: Customer & Invoice Details
                h('div', { style: { flex: '1 1 260px' } },
                  h('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
                    h('strong', { style: { fontSize: '0.92rem', color: 'var(--text)' } }, pr.customerName),
                    h('span', {
                      style: {
                        fontSize: '0.70rem',
                        fontWeight: 700,
                        padding: '1px 8px',
                        borderRadius: 999,
                        background: badgeBg,
                        color: badgeColor,
                        border: '1px solid ' + badgeBorder
                      }
                    }, pr.displayStatus.replace('_', ' '))
                  ),
                  h('div', { style: { fontSize: '0.78rem', color: 'var(--text-secondary)', marginTop: 2 } },
                    pr.invoiceId ? `Invoice #${pr.invoiceNumber} • Outstanding: ${formatInr(pr.invoiceOutstanding)}` : 'Customer General Promise'
                  ),
                  pr.notes && h('div', { style: { fontSize: '0.74rem', color: 'var(--text-secondary)', fontStyle: 'italic', marginTop: 2 } },
                    `"${pr.notes}"`
                  )
                ),

                // Middle Column: Financials & Commitment
                h('div', { style: { flex: '1 1 220px', display: 'flex', flexDirection: 'column', gap: 2 } },
                  h('div', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.04em' } }, 'Promised Commitment'),
                  h('div', { style: { fontSize: '1.05rem', fontWeight: 800, color: 'var(--primary, #059669)', fontFamily: 'var(--font-mono, monospace)' } },
                    formatInr(pr.promisedAmount)
                  ),
                  h('div', { style: { fontSize: '0.76rem', color: 'var(--text)' } },
                    `Promised Date: `, h('strong', null, pr.promisedDate || '—')
                  ),
                  h('div', { style: { fontSize: '0.74rem', color: 'var(--text-secondary)' } },
                    `Fulfilled: ${formatInr(pr.fulfilledAmount)} • Remaining: `,
                    h('strong', { style: { color: pr.remainingAmount > 0 ? '#b91c1c' : '#166534' } }, formatInr(pr.remainingAmount))
                  )
                ),

                // Right Column: Actions
                h('div', { style: { display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' } },
                  pr.status !== 'CANCELLED' && pr.status !== 'FULFILLED' && pr.remainingAmount > 0 && h('button', {
                    className: 'btn btn-primary btn-xs',
                    style: { fontWeight: 600 },
                    onClick: () => {
                      if (onSettle) {
                        onSettle(
                          { id: pr.customerId, name: pr.customerName, outstanding: pr.invoiceOutstanding },
                          pr.invoiceId ? [pr.invoiceId] : []
                        );
                      }
                    }
                  }, '💳 Record Payment'),
                  h('button', {
                    className: 'btn btn-outline btn-xs',
                    style: { color: '#16a34a' },
                    onClick: () => {
                      if (onWhatsApp) onWhatsApp({ id: pr.customerId, name: pr.customerName, phone: pr.customerPhone });
                    }
                  }, '💬 WhatsApp'),
                  pr.status !== 'CANCELLED' && h('button', {
                    className: 'btn btn-outline btn-xs',
                    onClick: () => openEditPromiseModal(pr)
                  }, '✏️ Edit'),
                  h('button', {
                    className: 'btn btn-outline btn-xs',
                    title: 'Audit History',
                    onClick: () => openHistoryModal(pr)
                  }, '📜 History'),
                  pr.status !== 'CANCELLED' && pr.status !== 'FULFILLED' && h('button', {
                    className: 'btn btn-ghost btn-xs',
                    style: { color: '#dc2626' },
                    onClick: () => handleCancelPromise(pr)
                  }, '✕ Cancel')
                )
              );
            })
        )
      ),

      // ── SUB-VIEW 5: FOLLOW-UPS (SEPARATE FROM PROMISES) ──
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
            ['ALL', 'DUE_TODAY', 'UPCOMING', 'OVERDUE'].map(st => h('button', {
              key: st,
              className: `btn btn-xs ${followUpFilter === st ? 'btn-primary' : 'btn-outline'}`,
              style: { fontWeight: 600, borderRadius: 14, padding: '4px 12px' },
              onClick: () => setFollowUpFilter(st)
            }, st.replace('_', ' ')))
          ),
          h('span', { style: { fontSize: '0.78rem', color: 'var(--text-secondary)' } }, `${pureFollowUps.length} follow-up actions in queue`)
        ),

        h('div', { style: { display: 'flex', flexDirection: 'column', gap: 8 } },
          pureFollowUps.length === 0 ?
            h('div', { style: { padding: 30, textAlign: 'center', color: 'var(--text-secondary)', background: 'var(--bg-secondary)', borderRadius: 8 } },
              '🎉 No collection follow-ups or reminder tasks in queue for this filter.'
            ) :
            pureFollowUps.map(f => h('div', {
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
                  className: `badge ${f.status === 'OVERDUE' ? 'badge-danger' : f.status === 'DUE_TODAY' ? 'badge-warning' : 'badge-primary'}`,
                  style: { fontSize: '0.72rem', fontWeight: 600 }
                }, f.status),
                h('div', null,
                  h('div', { style: { fontWeight: 700, fontSize: '0.90rem' } }, f.customerName),
                  h('div', { style: { fontSize: '0.78rem', color: 'var(--text-secondary)', marginTop: 2 } },
                    `${f.title} • Target: ${f.dueDate || 'Open'}`
                  ),
                  f.notes && h('div', { style: { fontSize: '0.74rem', color: 'var(--text-secondary)', fontStyle: 'italic', marginTop: 2 } },
                    `"${f.notes}"`
                  )
                )
              ),
              h('div', { style: { display: 'flex', alignItems: 'center', gap: 6 } },
                h('button', {
                  className: 'btn btn-outline btn-xs',
                  onClick: () => {
                    setActivityModalCustomer(f);
                    setActivityType('CALL_LOGGED');
                  }
                }, '📝 Log Note'),
                h('button', {
                  className: 'btn btn-outline btn-xs',
                  style: { color: '#16a34a' },
                  onClick: () => {
                    if (onWhatsApp) onWhatsApp({ id: f.customerId, name: f.customerName, phone: f.customerPhone });
                  }
                }, '💬 WhatsApp')
              )
            ))
        )
      ),

      // ── SUB-VIEW 6: STATEMENTS & REPORTS ──
      activeSubTab === 'reports' && h('div', { style: { display: 'flex', flexDirection: 'column', gap: 16 } },
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
              h('button', {
                className: 'btn btn-primary btn-sm',
                style: { flex: 1, textAlign: 'center' },
                onClick: handleExportCsv
              }, 'Download CSV'),
              h('button', {
                className: 'btn btn-outline btn-sm',
                style: { flex: 1, textAlign: 'center' },
                onClick: handleExportPdf
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

      // ── MODAL 1: RECORD NEW PROMISE MODAL ──
      createPromiseModal.isOpen && h('div', {
        className: 'modal-overlay modal-foreground',
        style: {
          position: 'fixed',
          inset: 0,
          background: 'rgba(0,0,0,0.6)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: (typeof window !== 'undefined' && window.RupeeModalStack) ? window.RupeeModalStack.getZIndex('debtor_create_promise_modal', 'foreground') : 10500
        },
        onClick: () => setCreatePromiseModal(prev => ({ ...prev, isOpen: false }))
      },
        h('div', {
          className: 'card modal-content',
          style: {
            background: 'var(--bg-card, #ffffff)',
            color: 'var(--text, #1e293b)',
            border: '1px solid var(--border, #cbd5e1)',
            borderRadius: 12,
            width: '92%',
            maxWidth: 480,
            padding: 22,
            boxShadow: '0 20px 25px -5px rgba(0,0,0,0.25)'
          },
          onClick: e => e.stopPropagation()
        },
          h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 } },
            h('h4', { style: { margin: 0, fontWeight: 700, fontSize: '1.05rem', color: 'var(--text)' } }, '🤝 Record Payment Promise'),
            h('button', { className: 'btn btn-ghost btn-sm', onClick: () => setCreatePromiseModal(prev => ({ ...prev, isOpen: false })) }, '✕')
          ),

          // Form fields
          h('div', { style: { display: 'flex', flexDirection: 'column', gap: 10 } },
            // Customer selection (if not preselected)
            !createPromiseModal.customer && h('div', null,
              h('label', { style: { fontSize: '0.78rem', fontWeight: 600, color: 'var(--text)', marginBottom: 4, display: 'block' } }, 'Customer *'),
              h('select', {
                className: 'input',
                style: { width: '100%' },
                value: createPromiseModal.customerId,
                onChange: (e) => {
                  const cId = e.target.value;
                  const cObj = debtorsList.find(d => String(d.customerId) === cId);
                  openCreatePromiseModal(cObj);
                }
              },
                h('option', { value: '' }, 'Select Customer...'),
                debtorsList.map(d => h('option', { key: d.customerId, value: String(d.customerId) }, `${d.customerName} (Bal: ${formatInr(d.outstandingAmount)})`))
              )
            ),

            createPromiseModal.customer && h('div', { style: { fontSize: '0.84rem', color: 'var(--text-secondary)', padding: '6px 10px', background: 'var(--bg-hover)', borderRadius: 6 } },
              `Customer: `, h('strong', { style: { color: 'var(--text)' } }, createPromiseModal.customer.customerName || createPromiseModal.customer.name)
            ),

            // Invoice selection
            createPromiseModal.customerId && h('div', null,
              h('label', { style: { fontSize: '0.78rem', fontWeight: 600, color: 'var(--text)', marginBottom: 4, display: 'block' } }, 'Target Invoice (Optional)'),
              createPromiseModal.loadingInvoices ?
                h('div', { style: { fontSize: '0.78rem', color: 'var(--text-secondary)' } }, 'Loading customer invoices...') :
                h('select', {
                  className: 'input',
                  style: { width: '100%' },
                  value: createPromiseModal.invoiceId,
                  onChange: (e) => handleSelectCreateInvoice(e.target.value)
                },
                  h('option', { value: '' }, 'General Customer Balance (No specific invoice)'),
                  createPromiseModal.customerInvoices.map(inv => h('option', { key: inv.invoiceId, value: String(inv.invoiceId) },
                    `#${inv.invoiceNumber} (Outstanding: ${formatInr(inv.outstandingAmount)})`
                  ))
                )
            ),

            // Available Balance Notice
            createPromiseModal.availableBalance !== null && h('div', {
              style: {
                padding: '6px 10px',
                borderRadius: 6,
                background: '#f0fdf4',
                border: '1px solid #bbf7d0',
                fontSize: '0.78rem',
                color: '#166534'
              }
            },
              `Available uncommitted balance on this invoice: `,
              h('strong', null, formatInr(createPromiseModal.availableBalance))
            ),

            // Amount field
            h('div', null,
              h('label', { style: { fontSize: '0.78rem', fontWeight: 600, color: 'var(--text)', marginBottom: 4, display: 'block' } }, 'Promised Amount (₹) *'),
              h('input', {
                type: 'number',
                className: 'input',
                style: { width: '100%', fontWeight: 700, fontFamily: 'var(--font-mono, monospace)' },
                placeholder: 'e.g. 15000',
                value: createPromiseModal.promisedAmount,
                onChange: (e) => setCreatePromiseModal(prev => ({ ...prev, promisedAmount: e.target.value }))
              })
            ),

            // Date field
            h('div', null,
              h('label', { style: { fontSize: '0.78rem', fontWeight: 600, color: 'var(--text)', marginBottom: 4, display: 'block' } }, 'Promised Date *'),
              h('input', {
                type: 'date',
                className: 'input',
                style: { width: '100%' },
                value: createPromiseModal.promisedDate,
                onChange: (e) => setCreatePromiseModal(prev => ({ ...prev, promisedDate: e.target.value }))
              })
            ),

            // Notes field
            h('div', null,
              h('label', { style: { fontSize: '0.78rem', fontWeight: 600, color: 'var(--text)', marginBottom: 4, display: 'block' } }, 'Notes / Terms'),
              h('textarea', {
                className: 'input',
                style: { width: '100%' },
                rows: 2,
                placeholder: 'e.g. Committed to transfer ₹15,000 via NEFT by noon...',
                value: createPromiseModal.notes,
                onChange: (e) => setCreatePromiseModal(prev => ({ ...prev, notes: e.target.value }))
              })
            )
          ),

          // Footer
          h('div', { style: { display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 18 } },
            h('button', {
              className: 'btn btn-outline btn-sm',
              onClick: () => setCreatePromiseModal(prev => ({ ...prev, isOpen: false }))
            }, 'Cancel'),
            h('button', {
              className: 'btn btn-primary btn-sm',
              disabled: createPromiseModal.saving,
              onClick: handleSaveCreatePromise
            }, createPromiseModal.saving ? 'Saving...' : 'Save Commitment')
          )
        )
      ),

      // ── MODAL 2: EDIT PROMISE MODAL ──
      editPromiseModal.isOpen && editPromiseModal.promise && h('div', {
        className: 'modal-overlay modal-foreground',
        style: {
          position: 'fixed',
          inset: 0,
          background: 'rgba(0,0,0,0.6)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: (typeof window !== 'undefined' && window.RupeeModalStack) ? window.RupeeModalStack.getZIndex('debtor_edit_promise_modal', 'foreground') : 10500
        },
        onClick: () => setEditPromiseModal(prev => ({ ...prev, isOpen: false }))
      },
        h('div', {
          className: 'card modal-content',
          style: {
            background: 'var(--bg-card, #ffffff)',
            color: 'var(--text, #1e293b)',
            border: '1px solid var(--border, #cbd5e1)',
            borderRadius: 12,
            width: '92%',
            maxWidth: 440,
            padding: 22,
            boxShadow: '0 20px 25px -5px rgba(0,0,0,0.25)'
          },
          onClick: e => e.stopPropagation()
        },
          h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 } },
            h('h4', { style: { margin: 0, fontWeight: 700, fontSize: '1.05rem', color: 'var(--text)' } }, '✏️ Edit Payment Commitment'),
            h('button', { className: 'btn btn-ghost btn-sm', onClick: () => setEditPromiseModal(prev => ({ ...prev, isOpen: false })) }, '✕')
          ),

          h('div', { style: { fontSize: '0.80rem', color: 'var(--text-secondary)', padding: '6px 10px', background: 'var(--bg-hover)', borderRadius: 6, marginBottom: 12 } },
            `Customer: `, h('strong', { style: { color: 'var(--text)' } }, editPromiseModal.promise.customerName),
            editPromiseModal.promise.invoiceNumber ? ` • Invoice #${editPromiseModal.promise.invoiceNumber}` : '',
            h('div', { style: { marginTop: 2 } }, `Already Fulfilled: ${formatInr(editPromiseModal.promise.fulfilledAmount)}`)
          ),

          h('div', { style: { display: 'flex', flexDirection: 'column', gap: 10 } },
            h('div', null,
              h('label', { style: { fontSize: '0.78rem', fontWeight: 600, color: 'var(--text)', marginBottom: 4, display: 'block' } }, 'Promised Amount (₹) *'),
              h('input', {
                type: 'number',
                className: 'input',
                style: { width: '100%', fontWeight: 700, fontFamily: 'var(--font-mono, monospace)' },
                value: editPromiseModal.promisedAmount,
                onChange: (e) => setEditPromiseModal(prev => ({ ...prev, promisedAmount: e.target.value }))
              })
            ),
            h('div', null,
              h('label', { style: { fontSize: '0.78rem', fontWeight: 600, color: 'var(--text)', marginBottom: 4, display: 'block' } }, 'Promised Date *'),
              h('input', {
                type: 'date',
                className: 'input',
                style: { width: '100%' },
                value: editPromiseModal.promisedDate,
                onChange: (e) => setEditPromiseModal(prev => ({ ...prev, promisedDate: e.target.value }))
              })
            ),
            h('div', null,
              h('label', { style: { fontSize: '0.78rem', fontWeight: 600, color: 'var(--text)', marginBottom: 4, display: 'block' } }, 'Notes / Reason for Revision'),
              h('textarea', {
                className: 'input',
                style: { width: '100%' },
                rows: 2,
                value: editPromiseModal.notes,
                onChange: (e) => setEditPromiseModal(prev => ({ ...prev, notes: e.target.value }))
              })
            )
          ),

          h('div', { style: { display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 18 } },
            h('button', {
              className: 'btn btn-outline btn-sm',
              onClick: () => setEditPromiseModal(prev => ({ ...prev, isOpen: false }))
            }, 'Cancel'),
            h('button', {
              className: 'btn btn-primary btn-sm',
              disabled: editPromiseModal.saving,
              onClick: handleSaveEditPromise
            }, editPromiseModal.saving ? 'Updating...' : 'Save Changes')
          )
        )
      ),

      // ── MODAL 3: AUDIT HISTORY MODAL ──
      historyModal.isOpen && historyModal.promise && h('div', {
        className: 'modal-overlay modal-foreground',
        style: {
          position: 'fixed',
          inset: 0,
          background: 'rgba(0,0,0,0.6)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: (typeof window !== 'undefined' && window.RupeeModalStack) ? window.RupeeModalStack.getZIndex('debtor_history_modal', 'foreground') : 10500
        },
        onClick: () => setHistoryModal(prev => ({ ...prev, isOpen: false }))
      },
        h('div', {
          className: 'card modal-content',
          style: {
            background: 'var(--bg-card, #ffffff)',
            color: 'var(--text, #1e293b)',
            border: '1px solid var(--border, #cbd5e1)',
            borderRadius: 12,
            width: '92%',
            maxWidth: 520,
            padding: 22,
            boxShadow: '0 20px 25px -5px rgba(0,0,0,0.25)'
          },
          onClick: e => e.stopPropagation()
        },
          h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 } },
            h('h4', { style: { margin: 0, fontWeight: 700, fontSize: '1.05rem', color: 'var(--text)' } }, `📜 Commitment Audit History`),
            h('button', { className: 'btn btn-ghost btn-sm', onClick: () => setHistoryModal(prev => ({ ...prev, isOpen: false })) }, '✕')
          ),

          h('div', { style: { fontSize: '0.80rem', color: 'var(--text-secondary)', padding: '6px 10px', background: 'var(--bg-hover)', borderRadius: 6, marginBottom: 14 } },
            `Customer: `, h('strong', { style: { color: 'var(--text)' } }, historyModal.promise.customerName),
            ` • Promise #${historyModal.promise.id} (${formatInr(historyModal.promise.promisedAmount)})`
          ),

          historyModal.loading ?
            h('div', { style: { padding: 20, textAlign: 'center', color: 'var(--text-secondary)' } }, 'Loading audit history...') :
            historyModal.items.length === 0 ?
              h('div', { style: { padding: 20, textAlign: 'center', color: 'var(--text-secondary)' } }, 'No audit logs recorded for this promise.') :
              h('div', { style: { display: 'flex', flexDirection: 'column', gap: 8, maxHeight: 300, overflowY: 'auto' } },
                historyModal.items.map(item => h('div', {
                  key: item.id,
                  style: {
                    padding: '8px 12px',
                    borderRadius: 6,
                    background: 'var(--bg-secondary)',
                    border: '1px solid var(--border)',
                    fontSize: '0.78rem'
                  }
                },
                  h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' } },
                    h('strong', { style: { color: 'var(--primary)' } }, item.actionType),
                    h('span', { style: { fontSize: '0.72rem', color: 'var(--text-secondary)' } },
                      item.createdAt ? new Date(item.createdAt).toLocaleString() : ''
                    )
                  ),
                  item.notes && h('div', { style: { marginTop: 4, color: 'var(--text)' } }, item.notes),
                  h('div', { style: { fontSize: '0.70rem', color: 'var(--text-secondary)', marginTop: 4 } },
                    `By: ${item.performedBy || 'User'}`
                  )
                ))
              ),

          h('div', { style: { display: 'flex', justifyContent: 'flex-end', marginTop: 16 } },
            h('button', {
              className: 'btn btn-outline btn-sm',
              onClick: () => setHistoryModal(prev => ({ ...prev, isOpen: false }))
            }, 'Close')
          )
        )
      ),

      // ── MODAL 4: LOG ACTIVITY MODAL ──
      activityModalCustomer && h('div', {
        className: 'modal-overlay modal-foreground',
        style: {
          position: 'fixed',
          inset: 0,
          background: 'rgba(0,0,0,0.6)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: (typeof window !== 'undefined' && window.RupeeModalStack) ? window.RupeeModalStack.getZIndex('debtor_activity_modal', 'foreground') : 10500
        },
        onClick: () => setActivityModalCustomer(null)
      },
        h('div', {
          className: 'card modal-content',
          style: {
            background: 'var(--bg-card, #ffffff)',
            color: 'var(--text, #1e293b)',
            border: '1px solid var(--border, #cbd5e1)',
            borderRadius: 12,
            width: '92%',
            maxWidth: 440,
            padding: 22,
            boxShadow: '0 20px 25px -5px rgba(0,0,0,0.25)'
          },
          onClick: e => e.stopPropagation()
        },
          h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 } },
            h('h4', { style: { margin: 0, fontWeight: 700, fontSize: '1.05rem', color: 'var(--text)' } }, `Log Collection Note / Activity`),
            h('button', { className: 'btn btn-ghost btn-sm', onClick: () => setActivityModalCustomer(null) }, '✕')
          ),
          h('div', { style: { fontSize: '0.84rem', color: 'var(--text-secondary)', marginBottom: 14, padding: '8px 10px', background: 'var(--bg-hover)', borderRadius: 8 } },
            `Customer: `, h('strong', { style: { color: 'var(--text)' } }, activityModalCustomer.customerName || activityModalCustomer.name)
          ),
          h('div', { style: { display: 'flex', flexDirection: 'column', gap: 10 } },
            h('div', null,
              h('label', { style: { fontSize: '0.78rem', fontWeight: 600, color: 'var(--text)', marginBottom: 4, display: 'block' } }, 'Activity Type *'),
              h('select', {
                className: 'input',
                style: { width: '100%' },
                value: activityType,
                onChange: (e) => setActivityType(e.target.value)
              },
                h('option', { value: 'CALL_LOGGED' }, '📞 Phone Call'),
                h('option', { value: 'NOTE_ADDED' }, '📝 Internal Note'),
                h('option', { value: 'WHATSAPP_OPENED' }, '💬 WhatsApp Communication'),
                h('option', { value: 'REMINDER_PREPARED' }, '⏰ Reminder Scheduled')
              )
            ),
            h('div', null,
              h('label', { style: { fontSize: '0.78rem', fontWeight: 600, color: 'var(--text)', marginBottom: 4, display: 'block' } }, 'Details / Feedback *'),
              h('textarea', {
                className: 'input',
                style: { width: '100%' },
                rows: 3,
                placeholder: 'Record conversation details, feedback, next action plan...',
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
      )
    );
  }

  if (typeof window !== 'undefined') {
    window.DebtorsCollectionsWorkspace = DebtorsCollectionsWorkspace;
  }
})();
