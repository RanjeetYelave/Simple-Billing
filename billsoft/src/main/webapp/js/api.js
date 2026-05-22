/**
 * Billsoft API Client
 * All methods return Promises. PDF downloads return blobs.
 * Multi-firm support: firmId is read from localStorage and appended to queries.
 */
const API = {
  BASE_URL: 'http://localhost:8080',

  // current firm ID from localStorage
  get firmId() {
    const id = localStorage.getItem('currentFirmId');
    return id ? parseInt(id, 10) : null;
  },

  set firmId(id) {
    if (id) localStorage.setItem('currentFirmId', id);
    else localStorage.removeItem('currentFirmId');
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

  async _request(url, options = {}) {
    const isFormData = options.body instanceof FormData;
    const defaultHeaders = isFormData ? {} : { 'Content-Type': 'application/json' };
    
    const config = {
      ...options,
      headers: { ...defaultHeaders, ...options.headers },
    };
    
    if (config.body && typeof config.body === 'object' && !isFormData && !(config.body instanceof Blob)) {
      config.body = JSON.stringify(config.body);
    }
    const response = await fetch(`${this.BASE_URL}${url}`, config);
    if (!response.ok) {
      const text = await response.text().catch(() => '');
      const err = new Error(`HTTP ${response.status}: ${text}`);
      err.status = response.status;
      throw err;
    }
    return response;
  },

  async _json(url, options = {}) {
    const res = await this._request(url, options);
    return res.json();
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
      // No firms exist - create one
      return this.firm.create().then(created => {
        this.firmId = created.id;
        return this.firmId;
      });
    }).catch(e => {
      console.warn('Failed to ensure firm is ready:', e);
      return null;
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

  // ── Products ──
  products: {
    list: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/products'))),
    get: (id) => API._json(`/api/products/${id}`),
    create: (data) => API._ensureFirmReady().then(() => API._json('/api/products', {
      method: 'POST',
      body: { ...data, firmId: API.firmId || data.firmId }
    })),
    update: (id, data) => API._json(`/api/products/${id}`, { method: 'PUT', body: data }),
    delete: (id) => API._request(`/api/products/${id}`, { method: 'DELETE' }),
  },

  // ── Invoices ──
  invoices: {
    list: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/invoices'))),
    listEstimates: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/invoices/estimates'))),
    listFinal: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/invoices/final'))),
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
      return res.blob();
    },
    analyticsByCustomer: (id) => API._json(`/api/invoices/analytics/customer/${id}`),
    analyticsSearch: (name) => API._json(`/api/invoices/analytics/search?name=${encodeURIComponent(name)}`),
  },

  // ── Firm (multi-row) ──
  firm: {
    list: () => API._json('/api/firm'),
    get: (id) => {
      if (id == null) return Promise.reject(new Error('firmId is required'));
      return API._json(`/api/firm/${id}`);
    },
    create: () => API._json('/api/firm', { method: 'POST' }),
    update: (id, data) => API._json(`/api/firm/${id}`, { method: 'PUT', body: data }),
    delete: (id) => API._request(`/api/firm/${id}`, { method: 'DELETE' }),
  },

  // ── Analytics ──
  analytics: {
    firm: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/analytics/firm'))),
  },

  // ── Statements ──
  statements: {
    customer: (id, from, to) => {
      return API._json(API._qs(`/api/statements/customer/${id}`, { from, to }));
    },
    customerPdf: async (id, from, to) => {
      const res = await API._request(API._qs(`/api/statements/customer/${id}/pdf`, { from, to }));
      return res.blob();
    },
    firm: (from, to) => {
      return API._json(API._qs('/api/statements/firm', { from, to }));
    },
    firmPdf: async (from, to) => {
      const res = await API._request(API._qs('/api/statements/firm/pdf', { from, to }));
      return res.blob();
    },
  },

  // ── Authentication ──
  auth: {
    status: () => API._json('/api/auth/status'),
    enable: (password) => API._json('/api/auth/enable', { method: 'POST', body: { password } }),
    disable: (password) => API._json('/api/auth/disable', { method: 'POST', body: { password } }),
    changePassword: (oldPassword, newPassword) => API._json('/api/auth/change-password', { method: 'POST', body: { oldPassword, newPassword } }),
    login: (firmId, password) => API._json('/api/auth/login', { method: 'POST', body: { firmId, password } }),
    resetPasswordMaster: (masterPassword, newPassword) => API._json('/api/auth/reset-password-master', { method: 'POST', body: { masterPassword, newPassword } })
  },

  // ─── Backup & Restore ───
  backup: {
    exportUrl: () => `${API.BASE_URL}/api/backup/export?firmId=${API.firmId}`,
    import: async (file, mode) => {
      const formData = new FormData();
      formData.append('file', file);
      if (API.firmId) formData.append('firmId', API.firmId);
      formData.append('mode', mode || 'merge');
      return API._json('/api/backup/import', { method: 'POST', body: formData });
    },
    factoryReset: () => API._json('/api/backup/factory-reset', { method: 'POST' })
  },

  // ─── Employees ───
  employees: {
    verifyPin: (pin) => API._json('/api/employees/verify-pin', { method: 'POST', body: { pin } }),
    changePin: (oldPin, newPin) => API._json('/api/employees/change-pin', { method: 'POST', body: { oldPin, newPin } }),
    getAll: () => API._json(API._qs('/api/employees')),
    create: (data) => API._json('/api/employees', { method: 'POST', body: data }),
    update: (id, data) => API._json(`/api/employees/${id}`, { method: 'PUT', body: data }),
    delete: (id) => API._request(`/api/employees/${id}`, { method: 'DELETE' }),
    getAdvances: (id) => API._json(`/api/employees/${id}/advances`),
    addAdvance: (id, data) => API._json(`/api/employees/${id}/advances`, { method: 'POST', body: data }),
    getSalaries: (id) => API._json(`/api/employees/${id}/salaries`),
    processSalary: (id, data) => API._json(`/api/employees/${id}/salaries`, { method: 'POST', body: data }),
    getPromotions: (id) => API._json(`/api/employees/${id}/promotions`),
    addPromotion: (id, data) => API._json(`/api/employees/${id}/promotions`, { method: 'POST', body: data }),
    getYtd: (id) => API._json(`/api/employees/${id}/ytd`),
    applyPromotions: () => API._json('/api/employees/apply-promotions', { method: 'POST' }),
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
    }
  },

  // ─── System & Updates ───
  system: {
    updateStatus: () => API._json('/api/system/update-status'),
    applyUpdate: () => API._json('/api/system/apply-update', {
      method: 'POST'
    })
  }
};