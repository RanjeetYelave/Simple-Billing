/**
 * Billsoft API Client
 * All methods return Promises. PDF downloads return blobs.
 * Multi-firm support: firmId is read from localStorage and appended to queries.
 */
const API = {
  // Keep all calls on the origin that served the application. This lets the
  // browser client work on any configured local port without a desktop shell.
  BASE_URL: window.location.origin,

  // current firm ID from localStorage
  get firmId() {
    const id = localStorage.getItem('currentFirmId');
    return id ? parseInt(id, 10) : null;
  },

  set firmId(id) {
    if (id) localStorage.setItem('currentFirmId', id);
    else localStorage.removeItem('currentFirmId');
  },

  get token() {
    return localStorage.getItem('billsoft_auth_token') || sessionStorage.getItem('billsoft_auth_token') || '';
  },

  set token(t) {
    if (t) {
      localStorage.setItem('billsoft_auth_token', t);
      sessionStorage.setItem('billsoft_auth_token', t);
    } else {
      localStorage.removeItem('billsoft_auth_token');
      sessionStorage.removeItem('billsoft_auth_token');
    }
  },

  get employeePin() {
    return sessionStorage.getItem('employeePin') || '';
  },

  set employeePin(p) {
    if (p) sessionStorage.setItem('employeePin', p);
    else sessionStorage.removeItem('employeePin');
  },

  // helper to inject firmId into query strings
  _qs(url, extraParams = {}) {
    const params = new URLSearchParams();
    const fid = this.firmId;
    if (fid) params.set('firmId', fid);
    Object.entries(extraParams).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') params.set(k, v);
    });
    const q = params.toString();
    return q ? `${url}?${q}` : url;
  },

  sessionRequestsCount: 0,

  async _request(url, options = {}) {
    this.sessionRequestsCount = (this.sessionRequestsCount || 0) + 1;
    window.dispatchEvent(new CustomEvent('billsoft:request', { detail: { count: this.sessionRequestsCount, url } }));
    window.dispatchEvent(new CustomEvent('billsoft:activity'));
    const isFormData = options.body instanceof FormData;
    const defaultHeaders = isFormData ? {} : { 'Content-Type': 'application/json' };
    const firmHeader = this.firmId ? { 'X-Firm-Id': String(this.firmId) } : {};
    const pinHeader = (this.employeePin && url.includes('/api/employees')) ? { 'X-Employee-Pin': this.employeePin } : {};

    const config = {
      ...options,
      headers: { ...defaultHeaders, ...firmHeader, ...pinHeader, ...options.headers },
    };

    if (config.body && typeof config.body === 'object' && !isFormData && !(config.body instanceof Blob)) {
      config.body = JSON.stringify(config.body);
    }
    const response = await fetch(`${this.BASE_URL}${url}`, config);
    if (!response.ok) {
      const text = await response.text().catch(() => '');
      let errorMsg = `HTTP ${response.status}`;
      try {
        const json = JSON.parse(text);
        if (json && json.error) errorMsg = json.error;
        else if (json && json.message) errorMsg = json.message;
      } catch (e) {
        if (text && text.trim()) {
          errorMsg = text.length > 200 ? `HTTP ${response.status}` : `HTTP ${response.status}: ${text}`;
        } else if (response.status === 413) {
          errorMsg = 'File size is too large (exceeds 100MB limit).';
        }
      }
      const err = new Error(errorMsg);
      err.status = response.status;
      throw err;
    }
    return response;
  },

  async _json(url, options = {}) {
    const res = await this._request(url, options);
    if (res.status === 204) return null;
    const text = await res.text();
    if (!text || !text.trim()) return null;
    try {
      return JSON.parse(text);
    } catch (e) {
      return text;
    }
  },

  // -- Ensure there is a firm selected before operations that need it
  _ensureFirmReady() {
    if (this.firmId) return Promise.resolve(this.firmId);
    // Try to load firms and pick the first one
    return this.firm.list().then(firms => {
      if (firms && firms.length > 0) {
        this.firmId = firms[0].id;
        return this.firmId;
      }
      throw new Error("No firm configured");
    });
  },

  // ── Customers ──
  customers: {
    list: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/customers'))),
    get: (id) => API._json(`/api/customers/${id}`),
    create: (data) => API._ensureFirmReady().then(() => API._json('/api/customers', {
      method: 'POST',
      body: { ...data, firmId: API.firmId || data.firmId }
    })),
    update: (id, data) => API._json(`/api/customers/${id}`, { method: 'PUT', body: data }),
    delete: (id) => API._request(`/api/customers/${id}`, { method: 'DELETE' }),
  },

  // ── Parties (Vendors / Suppliers) ──
  parties: {
    list: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/parties'))),
    listSummaries: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/parties/summaries'))),
    get: (id) => API._json(`/api/parties/${id}`),
    financialSummary: (id) => API._ensureFirmReady().then(() => API._json(API._qs(`/api/parties/${id}/financial-summary`))),
    create: (data) => API._ensureFirmReady().then(() => API._json('/api/parties', {
      method: 'POST',
      body: { ...data, firmId: API.firmId || data.firmId }
    })),
    update: (id, data) => API._ensureFirmReady().then(() => API._json(`/api/parties/${id}`, {
      method: 'PUT',
      body: { ...data, firmId: API.firmId || data.firmId }
    })),
    delete: (id) => API._request(`/api/parties/${id}`, { method: 'DELETE' }),
    payments: (partyId) => API._ensureFirmReady().then(() => API._json(API._qs(`/api/parties/${partyId}/payments`))),
    recordPayment: (partyId, data) => API._ensureFirmReady().then(() => API._json(`/api/parties/${partyId}/payments`, {
      method: 'POST',
      body: { ...data, firmId: API.firmId || data.firmId }
    })),
    deletePayment: (paymentId) => API._request(`/api/parties/payments/${paymentId}`, { method: 'DELETE' }),
  },

  // ── Purchase Orders ──
  purchaseOrders: {
    list: (partyId) => API._ensureFirmReady().then(() => API._json(API._qs('/api/purchase-orders', partyId ? { partyId } : {}))),
    get: (id) => API._json(`/api/purchase-orders/${id}`),
    nextNumber: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/purchase-orders/next-number'))),
    create: (data) => API._ensureFirmReady().then(() => API._json('/api/purchase-orders', {
      method: 'POST',
      body: { ...data, firmId: API.firmId || data.firmId }
    })),
    update: (id, data) => API._ensureFirmReady().then(() => API._json(`/api/purchase-orders/${id}`, {
      method: 'PUT',
      body: { ...data, firmId: API.firmId || data.firmId }
    })),
    updateStatus: (id, status) => API._ensureFirmReady().then(() => API._json(API._qs(`/api/purchase-orders/${id}/status`), {
      method: 'PATCH',
      body: { status }
    })),
    recordPayment: (id, data) => API._ensureFirmReady().then(() => API._json(API._qs(`/api/purchase-orders/${id}/payments`), {
      method: 'POST',
      body: { ...data, firmId: API.firmId }
    })),
    delete: (id) => API._request(`/api/purchase-orders/${id}`, { method: 'DELETE' }),
    pdfUrl: (id) => API._qs(API.BASE_URL + `/api/purchase-orders/${id}/pdf`),
    downloadPdf: async (id) => {
      const res = await API._request(API._qs(`/api/purchase-orders/${id}/pdf`));
      const blob = await res.blob();
      return new Blob([blob], { type: 'application/pdf' });
    }
  },

  // ── Business Letters (Letter Pad) ──
  letters: {
    list: (filters = {}) => API._ensureFirmReady().then(() => API._json(API._qs('/api/letters', filters))),
    get: (id) => API._json(`/api/letters/${id}`),
    nextNumber: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/letters/next-number'))),
    create: (data) => API._ensureFirmReady().then(() => API._json('/api/letters', {
      method: 'POST',
      body: { ...data, firmId: API.firmId || data.firmId }
    })),
    update: (id, data) => API._ensureFirmReady().then(() => API._json(`/api/letters/${id}`, {
      method: 'PUT',
      body: { ...data, firmId: API.firmId || data.firmId }
    })),
    updateStatus: (id, status) => API._ensureFirmReady().then(() => API._json(API._qs(`/api/letters/${id}/status`), {
      method: 'PATCH',
      body: { status }
    })),
    delete: (id) => API._request(`/api/letters/${id}`, { method: 'DELETE' }),
    pdfUrl: (id) => API._qs(API.BASE_URL + `/api/letters/${id}/pdf`),
    downloadPdf: async (id) => {
      const res = await API._request(API._qs(`/api/letters/${id}/pdf`));
      const blob = await res.blob();
      return new Blob([blob], { type: 'application/pdf' });
    }
  },

  // ── Products & Inventory ──
  products: {
    list: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/products'))),
    get: (id) => API._json(`/api/products/${id}`),
    create: (data) => API._ensureFirmReady().then(() => API._json('/api/products', {
      method: 'POST',
      body: { ...data, firmId: API.firmId || data.firmId }
    })),
    update: (id, data) => API._json(`/api/products/${id}`, { method: 'PUT', body: data }),
    delete: (id) => API._request(`/api/products/${id}`, { method: 'DELETE' }),
    summary: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/products/summary'))),
    categories: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/products/categories'))),
    adjustStock: (id, data) => API._json(`/api/products/${id}/adjust-stock`, { method: 'POST', body: data }),
    movements: (id) => API._ensureFirmReady().then(() => API._json(API._qs(`/api/products/${id}/movements`))),
    allMovements: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/products/movements'))),
  },

  // ── Invoices ──
  invoices: {
    list: (page = 0, size = 50) => API._ensureFirmReady().then(() => API._json(API._qs('/api/invoices', { page, size }))),
    listEstimates: (page = 0, size = 50) => API._ensureFirmReady().then(() => API._json(API._qs('/api/invoices/estimates', { page, size }))),
    listFinal: (page = 0, size = 50) => API._ensureFirmReady().then(() => API._json(API._qs('/api/invoices/final', { page, size }))),
    get: (id) => API._json(`/api/invoices/${id}`),
    create: (data) => API._ensureFirmReady().then(() => API._json('/api/invoices', {
      method: 'POST',
      body: { ...data, firmId: API.firmId || data.firmId }
    })),
    createEstimate: (data) => API._ensureFirmReady().then(() => API._json('/api/invoices/estimate', {
      method: 'POST',
      body: { ...data, firmId: API.firmId || data.firmId }
    })),
    preview: (data) => API._ensureFirmReady().then(() => API._json('/api/invoices/preview', {
      method: 'POST',
      body: { ...data, firmId: API.firmId || data.firmId }
    })),
    update: (id, data) => API._json(`/api/invoices/${id}`, { method: 'PUT', body: data }),
    delete: (id) => API._request(`/api/invoices/${id}`, { method: 'DELETE' }),
    markPaid: (id, paid) => API._json(`/api/invoices/${id}/paid?paid=${paid}`, { method: 'PUT' }),
    updateStatus: (id, status) => API._json(`/api/invoices/${id}/status?status=${status}`, { method: 'PUT' }),
    convertEstimate: (id, data) => API._ensureFirmReady().then(() => API._json(`/api/invoices/convert/${id}`, {
      method: 'POST',
      body: { ...data, firmId: API.firmId || data.firmId }
    })),
    getLinkedInvoice: (id) => API._json(`/api/invoices/${id}/linked-invoice`),
    nextInvoiceNumber: () => API._json(API._qs('/api/invoices/next-invoice-number')),
    nextEstimateNumber: () => API._json(API._qs('/api/invoices/next-estimate-number')),
    downloadPdf: async (id, size = 'A4') => {
      const res = await API._request(`/api/invoices/${id}/pdf?size=${size}`);
      const blob = await res.blob();
      return new Blob([blob], { type: 'application/pdf' });
    },
    analyticsByCustomer: (id) => API._json(`/api/invoices/analytics/customer/${id}`),
    analyticsSearch: (name) => API._json(`/api/invoices/analytics/search?name=${encodeURIComponent(name)}`),
  },

  // ── Reminders ──
  reminders: {
    list: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/reminders'))),
    listByFirm: (firmId) => API._json(`/api/reminders/firm/${firmId}`),
    create: (data) => API._ensureFirmReady().then(() => API._json('/api/reminders', { method: 'POST', body: { ...data, firmId: API.firmId || data.firmId } })),
    update: (id, data) => API._json(`/api/reminders/${id}`, { method: 'PUT', body: data }),
    delete: (id) => API._ensureFirmReady().then(() => API._request(`/api/reminders/${id}`, { method: 'DELETE' })),
    markDone: (id) => API._ensureFirmReady().then(() => API._json(`/api/reminders/${id}/done`, { method: 'PUT' })),
  },

  // ── Notes ──
  notes: {
    list: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/notes'))),
    listByFirm: (firmId) => API._json(`/api/notes/firm/${firmId}`),
    create: (data) => API._ensureFirmReady().then(() => API._json('/api/notes', { method: 'POST', body: { ...data, firmId: API.firmId || data.firmId } })),
    update: (id, data) => API._json(`/api/notes/${id}`, { method: 'PUT', body: data }),
    delete: (id) => API._ensureFirmReady().then(() => API._request(`/api/notes/${id}`, { method: 'DELETE' })),
  },

  // ── Expenses ──
  expenses: {
    list: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/expenses'))),
    listByFirm: (firmId) => API._json(`/api/expenses/firm/${firmId}`),
    create: (data) => API._ensureFirmReady().then(() => API._json('/api/expenses', { method: 'POST', body: { ...data, firmId: API.firmId || data.firmId } })),
    update: (id, data) => API._json(`/api/expenses/${id}`, { method: 'PUT', body: data }),
    delete: (id) => API._ensureFirmReady().then(() => API._request(`/api/expenses/${id}`, { method: 'DELETE' })),
    summary: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/expenses/summary'))),
  },

  // ── Messages ──
  messages: {
    list: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/messages'))),
    create: (data) => API._ensureFirmReady().then(() => API._json('/api/messages', { method: 'POST', body: { ...data, firmId: API.firmId || data.firmId } })),
    markRead: (id) => API._ensureFirmReady().then(() => API._json(`/api/messages/${id}/read`, { method: 'PUT' })),
    delete: (id) => API._ensureFirmReady().then(() => API._request(`/api/messages/${id}`, { method: 'DELETE' })),
  },
  // ── Firm (multi-row) ──
  firm: {
    list: () => API._json('/api/firm'),
    get: (id) => {
      if (id == null) return Promise.reject(new Error('firmId is required'));
      return API._json(`/api/firm/${id}`);
    },
    create: (data) => API._json('/api/firm', { method: 'POST', body: data }),
    update: (id, data) => API._json(`/api/firm/${id}`, { method: 'PUT', body: data }),
    delete: (id) => API._request(`/api/firm/${id}`, { method: 'DELETE' }),
  },

  // ── Analytics ──
  analytics: {
    firm: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/analytics/firm'))),
    customerStats: (id) => API._ensureFirmReady().then(() => API._json(API._qs(`/api/analytics/customer/${id}`))),
    productStats: (id) => API._ensureFirmReady().then(() => API._json(API._qs(`/api/analytics/product/${id}`))),
  },

  // ── Statements ──
  statements: {
    customer: (id, from, to) => {
      return API._json(API._qs(`/api/statements/customer/${id}`, { from, to }));
    },
    customerPdfUrl: (id, from, to) => {
      return API._qs(`/api/statements/customer/${id}/pdf`, { from, to });
    },
    customerPdf: async (id, from, to) => {
      const res = await API._request(API._qs(`/api/statements/customer/${id}/pdf`, { from, to }));
      const blob = await res.blob();
      return new Blob([blob], { type: 'application/pdf' });
    },
    party: (id, from, to) => {
      return API._json(API._qs(`/api/statements/party/${id}`, { from, to }));
    },
    partyPdfUrl: (id, from, to) => {
      return API._qs(`/api/statements/party/${id}/pdf`, { from, to });
    },
    partyPdf: async (id, from, to) => {
      const res = await API._request(API._qs(`/api/statements/party/${id}/pdf`, { from, to }));
      const blob = await res.blob();
      return new Blob([blob], { type: 'application/pdf' });
    },
    firm: (from, to, firmId) => {
      return API._json(API._qs('/api/statements/firm', { firmId: firmId || API.firmId, from, to }));
    },
    firmPdfUrl: (from, to, firmId) => {
      return API._qs('/api/statements/firm/pdf', { firmId: firmId || API.firmId, from, to });
    },
    firmPdf: async (from, to, firmId) => {
      const res = await API._request(API._qs('/api/statements/firm/pdf', { firmId: firmId || API.firmId, from, to }));
      const blob = await res.blob();
      return new Blob([blob], { type: 'application/pdf' });
    },
  },

  // ── Authentication ──
  auth: {
    status: () => API._json('/api/auth/status'),
    enable: async (password) => {
      const res = await API._json('/api/auth/enable', { method: 'POST', body: { password } });
      if (res && res.token) API.token = res.token;
      return res;
    },
    disable: async (password) => {
      const res = await API._json('/api/auth/disable', { method: 'POST', body: { password } });
      API.token = null;
      return res;
    },
    changePassword: async (oldPassword, newPassword) => {
      const res = await API._json('/api/auth/change-password', { method: 'POST', body: { oldPassword, newPassword } });
      if (res && res.token) API.token = res.token;
      return res;
    },
    login: async (firmId, password) => {
      const res = await API._json('/api/auth/login', { method: 'POST', body: { firmId, password } });
      if (res && res.token) API.token = res.token;
      return res;
    },
    resetPasswordMaster: async (masterPassword, newPassword) => {
      const res = await API._json('/api/auth/reset-password-master', { method: 'POST', body: { masterPassword, newPassword } });
      if (res && res.token) API.token = res.token;
      return res;
    }
  },

  // ── Licensing ──
  license: {
    status: () => API._json('/api/license/status'),
    activate: (productKey) => API._json('/api/license/activate', { method: 'POST', body: { productKey } }),
    initTrial: () => API._json('/api/license/init-trial', { method: 'POST' }),
  },

  // ─── Backup & Restore ───
  backup: {
    exportUrl: () => `${API.BASE_URL}/api/backup/export?firmId=${API.firmId}`,
    exportAllUrl: () => `${API.BASE_URL}/api/backup/export/all`,
    inspect: async (file) => {
      const formData = new FormData();
      formData.append('file', file);
      return API._json('/api/backup/inspect', { method: 'POST', body: formData });
    },
    importSelective: async (file, firmIds, mode, targetFirmId) => {
      const formData = new FormData();
      formData.append('file', file);
      if (firmIds && Array.isArray(firmIds)) {
        firmIds.forEach(id => formData.append('firmIds', id));
      }
      formData.append('mode', mode || 'clone');
      if (targetFirmId) formData.append('targetFirmId', targetFirmId);
      return API._json('/api/backup/import/selective', { method: 'POST', body: formData });
    },
    import: async (file, mode) => {
      const formData = new FormData();
      formData.append('file', file);
      if (API.firmId) formData.append('firmId', API.firmId);
      formData.append('mode', mode || 'merge');
      return API._json('/api/backup/import', { method: 'POST', body: formData });
    },
    autoStatus: () => API._json('/api/backup/auto/status'),
    runAutoBackupNow: () => API._json('/api/backup/auto/run-now', { method: 'POST' }),
    downloadAutoBackupUrl: () => `${API.BASE_URL}/api/backup/auto/download`,
    factoryReset: (password) => API._json('/api/backup/factory-reset', {
      method: 'POST',
      body: { confirm: 'RESET SOFTWARE', password: password || '', masterPassword: password || '' }
    })
  },

  // ─── Employees ───
  employees: {
    verifyPin: async (pin) => {
      const res = await API._json('/api/employees/verify-pin', { method: 'POST', body: { pin } });
      if (res && res.valid) {
        API.employeePin = pin;
      }
      return res;
    },
    changePin: async (oldPin, newPin) => {
      const res = await API._json('/api/employees/change-pin', { method: 'POST', body: { oldPin, newPin } });
      if (res && res.status === 'success') {
        API.employeePin = newPin;
      }
      return res;
    },
    getAll: () => API._json(API._qs('/api/employees')),
    create: (data) => API._json('/api/employees', { method: 'POST', body: data }),
    update: (id, data) => API._json(`/api/employees/${id}`, { method: 'PUT', body: data }),
    delete: (id) => API._request(`/api/employees/${id}`, { method: 'DELETE' }),
    changeStatus: (id, isActive, reason) => API._json(`/api/employees/${id}/status`, { method: 'PUT', body: { isActive, reason } }),
    getAdvances: (id) => API._json(`/api/employees/${id}/advances`),
    addAdvance: (id, data) => API._json(`/api/employees/${id}/advances`, { method: 'POST', body: data }),
    getSalaries: (id) => API._json(`/api/employees/${id}/salaries`),
    processSalary: (id, data) => API._json(`/api/employees/${id}/salaries`, { method: 'POST', body: data }),
    getPromotions: (id) => API._json(`/api/employees/${id}/promotions`),
    addPromotion: (id, data) => API._json(`/api/employees/${id}/promotions`, { method: 'POST', body: data }),
    getYtd: (id) => API._json(`/api/employees/${id}/ytd`),
    applyPromotions: () => API._json(API._qs('/api/employees/apply-promotions'), { method: 'POST' }),
    // PDF downloads - returns blob
    downloadPayslipPdf: async (id, salaryId) => {
      const res = await API._request(`/api/employees/${id}/salaries/${salaryId}/payslip`);
      return res.blob();
    },
    downloadStatementPdf: async (id, from, to) => {
      const params = {};
      if (from) params.from = from;
      if (to) params.to = to;
      const res = await API._request(API._qs(`/api/employees/${id}/statement`, params));
      return res.blob();
    },
    // F10: Employee Analytics
    analytics: () => API._json(API._qs('/api/employees/analytics')),
    // F2: Bulk Salary Processing
    bulkSalary: (data) => API._json('/api/employees/bulk-salary', { method: 'POST', body: data }),
    // F1: Attendance
    attendance: {
      get: (id) => API._json(`/api/employees/${id}/attendance`),
      set: (id, date, status, remarks) => API._json(`/api/employees/${id}/attendance`, {
        method: 'POST',
        body: { date, status, remarks }
      }),
      getRange: (id, from, to) => API._json(`/api/employees/${id}/attendance/range?from=${from}&to=${to}`),
      getMonth: (id, year, month) => API._json(`/api/employees/${id}/attendance/month/${year}/${month}`)
    },
    // F3: Documents
    documents: {
      list: (id) => API._json(`/api/employees/${id}/documents`),
      upload: async (id, typeOrData, file, customName) => {
        if (typeof typeOrData === 'object' && typeOrData.dataBase64) {
          return API._json(`/api/employees/${id}/documents`, {
            method: 'POST',
            body: typeOrData
          });
        }
        return new Promise((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = () => {
            const base64 = reader.result;
            const finalName = customName || (file && file.name) || 'document';
            API._json(`/api/employees/${id}/documents`, {
              method: 'POST',
              body: { type: typeOrData || 'Other Document', fileName: finalName, dataBase64: base64 }
            }).then(resolve).catch(reject);
          };
          reader.onerror = () => reject(new Error('Failed to read file'));
          reader.readAsDataURL(file);
        });
      },
      delete: (docId) => API._request(`/api/employees/documents/${docId}`, { method: 'DELETE' }),
      viewUrl: (docId) => `${API.BASE_URL}/api/employees/documents/${docId}/view`,
      downloadUrl: (docId) => `${API.BASE_URL}/api/employees/documents/${docId}/download`
    },
    // F4: Leave Management
    leaves: {
      list: (id) => API._json(`/api/employees/${id}/leaves`),
      request: (id, data) => API._json(`/api/employees/${id}/leaves`, { method: 'POST', body: data }),
      approve: (leaveId) => API._json(`/api/employees/leaves/${leaveId}/approve`, { method: 'PUT' }),
      reject: (leaveId, reason) => API._json(`/api/employees/leaves/${leaveId}/reject`, { method: 'PUT', body: { reason } })
    },
    // F5: CSV Export
    exportCsv: () => API._request(API._qs('/api/employees/export/csv')).then(r => r.blob()),
    exportSalariesCsv: (id) => API._request(`/api/employees/${id}/salaries/export/csv`).then(r => r.blob()),
    exportAdvancesCsv: (id) => API._request(`/api/employees/${id}/advances/export/csv`).then(r => r.blob())
  },

  // ─── System & Updates ───
  system: {
    updateStatus: () => API._json('/api/system/update-status'),
    applyUpdate: () => API._json('/api/system/apply-update', {
      method: 'POST'
    }),
    getDevLogs: () => API._json('/api/system/dev-logs'),
    setDevLogs: (enabled) => API._json('/api/system/dev-logs', {
      method: 'POST',
      body: { enabled }
    }),
    exportDevLogsUrl: () => `${API.BASE_URL}/api/system/dev-logs/export`,
    metrics: () => API._json('/api/health/metrics'),
    health: () => API._json('/api/health')
  }
};
