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
    if (res.status === 204) return null;
    const text = await res.text();
    if (!text || !text.trim()) return null;
    return JSON.parse(text);
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
    firm: (from, to) => {
      return API._json(API._qs('/api/statements/firm', { from, to }));
    },
    firmPdfUrl: (from, to) => {
      return API._qs('/api/statements/firm/pdf', { from, to });
    },
    firmPdf: async (from, to) => {
      const res = await API._request(API._qs('/api/statements/firm/pdf', { from, to }));
      const blob = await res.blob();
      return new Blob([blob], { type: 'application/pdf' });
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

  // ── Licensing ──
  license: {
    status: () => API._json('/api/license/status'),
    activate: (productKey) => API._json('/api/license/activate', { method: 'POST', body: { productKey } }),
    initTrial: () => API._json('/api/license/init-trial', { method: 'POST' }),
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
      upload: async (id, typeOrData, file) => {
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
            API._json(`/api/employees/${id}/documents`, {
              method: 'POST',
              body: { type: typeOrData, fileName: file.name, dataBase64: base64 }
            }).then(resolve).catch(reject);
          };
          reader.onerror = () => reject(new Error('Failed to read file'));
          reader.readAsDataURL(file);
        });
      },
      delete: (docId) => API._request(`/api/employees/documents/${docId}`, { method: 'DELETE' }),
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
    exportDevLogsUrl: () => `${API.BASE_URL}/api/system/dev-logs/export`
  }
};
