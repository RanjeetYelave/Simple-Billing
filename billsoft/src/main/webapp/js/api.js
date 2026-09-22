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
    list: (firmIdOverride) => {
      const fid = firmIdOverride || API.firmId;
      const url = fid ? `/api/customers?firmId=${fid}` : '/api/customers';
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._json(url, { headers });
    },
    get: (id) => API._json(`/api/customers/${id}`),
    create: (data, firmIdOverride) => {
      const fid = firmIdOverride || (data && data.firmId) || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._json('/api/customers', {
        method: 'POST',
        headers,
        body: { ...data, firmId: fid }
      });
    },
    update: (id, data) => API._json(`/api/customers/${id}`, { method: 'PUT', body: data }),
    delete: (id) => API._request(`/api/customers/${id}`, { method: 'DELETE' }),
    get360: (id) => API._json(`/api/customers/${id}/360`),
    outstandingInvoices: (id) => API._json(`/api/customers/${id}/outstanding-invoices`),
    payments: (id) => API._json(`/api/customers/${id}/payments`),
    settle: (id, data) => API._json(`/api/customers/${id}/settle`, { method: 'POST', body: data }),
    deletePayment: (paymentId) => API._request(`/api/customers/payments/${paymentId}`, { method: 'DELETE' }),
  },

  // ── Parties (Vendors / Suppliers) ──
  parties: {
    list: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/parties'))),
    listSummaries: (page, size) => API._ensureFirmReady().then(() => API._json(API._qs('/api/parties/summaries', {
      ...(page !== undefined ? { page } : {}),
      ...(size !== undefined ? { size } : {})
    }))),
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
    unallocatedPayments: (partyId) => API._ensureFirmReady().then(() => API._json(API._qs(`/api/parties/${partyId}/unallocated-payments`))),
    recordPayment: (partyId, data) => API._ensureFirmReady().then(() => API._json(`/api/parties/${partyId}/payments`, {
      method: 'POST',
      body: { ...data, firmId: API.firmId || data.firmId }
    })),
    deletePayment: (paymentId) => API._request(`/api/parties/payments/${paymentId}`, { method: 'DELETE' }),
  },

  // ── Purchase Orders ──
  purchaseOrders: {
    list: (partyId, page, size) => API._ensureFirmReady().then(() => API._json(API._qs('/api/purchase-orders', {
      ...(partyId ? { partyId } : {}),
      ...(page !== undefined ? { page } : {}),
      ...(size !== undefined ? { size } : {})
    }))),
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
    adjustAdvance: (id, data) => API._ensureFirmReady().then(() => API._json(API._qs(`/api/purchase-orders/${id}/adjust-advance`), {
      method: 'POST',
      body: { ...data, firmId: API.firmId }
    })),
    unadjustPayment: (paymentId) => API._ensureFirmReady().then(() => API._json(API._qs(`/api/purchase-orders/payments/${paymentId}/unadjust`), {
      method: 'POST',
      body: { firmId: API.firmId }
    })),
    delete: (id) => API._request(`/api/purchase-orders/${id}`, { method: 'DELETE' }),
    pdfUrl: (id) => API._qs(API.BASE_URL + `/api/purchase-orders/${id}/pdf`),
    downloadPdf: async (id) => {
      const res = await API._request(API._qs(`/api/purchase-orders/${id}/pdf`));
      const blob = await res.blob();
      return new Blob([blob], { type: 'application/pdf' });
    },
    createBatch: (data) => API._ensureFirmReady().then(() => API._json('/api/purchase-orders/batch', {
      method: 'POST',
      body: { ...data, firmId: API.firmId || data.firmId }
    })),
    getVendorHistory: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/purchase-orders/vendor-history'))),
    batchPdfUrl: (ids) => API._qs(API.BASE_URL + `/api/purchase-orders/batch/pdf`, { ids: Array.isArray(ids) ? ids.join(',') : ids }),
    batchZipUrl: (ids) => API._qs(API.BASE_URL + `/api/purchase-orders/batch/zip`, { ids: Array.isArray(ids) ? ids.join(',') : ids })
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
    list: (params = {}, firmIdOverride) => {
      const fid = firmIdOverride || (typeof params === 'object' && params && params.firmId) || API.firmId;
      const q = typeof params === 'object' && params !== null ? { ...params } : {};
      if (fid) q.firmId = fid;
      const qs = new URLSearchParams(q).toString();
      const url = qs ? `/api/products?${qs}` : '/api/products';
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._json(url, { headers });
    },
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
    movements: (id, params = {}) => API._ensureFirmReady().then(() => API._json(API._qs(`/api/products/${id}/movements`, params))),
    allMovements: (params = {}) => API._ensureFirmReady().then(() => API._json(API._qs('/api/products/movements', params))),
  },

  // ── Invoices ──
  invoices: {
    list: (page = 0, size = 50) => API._ensureFirmReady().then(() => API._json(API._qs('/api/invoices', { page, size }))),
    listEstimates: (page = 0, size = 50) => API._ensureFirmReady().then(() => API._json(API._qs('/api/invoices/estimates', { page, size }))),
    listFinal: (page = 0, size = 50) => API._ensureFirmReady().then(() => API._json(API._qs('/api/invoices/final', { page, size }))),
    get: (id) => API._json(`/api/invoices/${id}`),
    create: (data, firmIdOverride) => {
      const fid = firmIdOverride || (data && data.firmId) || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._json('/api/invoices', {
        method: 'POST',
        headers,
        body: { ...(data || {}), firmId: fid }
      });
    },
    createEstimate: (data, firmIdOverride) => {
      const fid = firmIdOverride || (data && data.firmId) || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._json('/api/invoices/estimate', {
        method: 'POST',
        headers,
        body: { ...(data || {}), firmId: fid }
      });
    },
    preview: (data, firmIdOverride) => {
      const fid = firmIdOverride || (data && data.firmId) || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._json('/api/invoices/preview', {
        method: 'POST',
        headers,
        body: { ...(data || {}), firmId: fid }
      });
    },
    update: (id, data, firmIdOverride) => {
      const fid = firmIdOverride || (data && data.firmId) || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._json(`/api/invoices/${id}`, {
        method: 'PUT',
        headers,
        body: { ...(data || {}), firmId: fid }
      });
    },
    delete: (id) => API._request(`/api/invoices/${id}`, { method: 'DELETE' }),
    markPaid: (id, paid) => API._json(`/api/invoices/${id}/paid?paid=${paid}`, { method: 'PUT' }),
    updateStatus: (id, status) => API._json(`/api/invoices/${id}/status?status=${status}`, { method: 'PUT' }),
    recordPayment: (id, data, firmIdOverride) => {
      const fid = firmIdOverride || (data && data.firmId) || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._json(`/api/invoices/${id}/payments`, {
        method: 'POST',
        headers,
        body: { ...(data || {}), firmId: fid }
      });
    },
    getPayments: (id, firmIdOverride) => {
      const fid = firmIdOverride || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._json(`/api/invoices/${id}/payments`, { headers });
    },
    convertEstimate: (id, data, firmIdOverride) => {
      const fid = firmIdOverride || (data && data.firmId) || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._json(`/api/invoices/convert/${id}`, {
        method: 'POST',
        headers,
        body: { ...(data || {}), firmId: fid }
      });
    },
    getLinkedInvoice: (id) => API._json(`/api/invoices/${id}/linked-invoice`),
    nextInvoiceNumber: (firmIdOverride) => {
      const fid = firmIdOverride || API.firmId;
      const url = fid ? `/api/invoices/next-invoice-number?firmId=${fid}` : '/api/invoices/next-invoice-number';
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._json(url, { headers });
    },
    nextEstimateNumber: (firmIdOverride) => {
      const fid = firmIdOverride || API.firmId;
      const url = fid ? `/api/invoices/next-estimate-number?firmId=${fid}` : '/api/invoices/next-estimate-number';
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._json(url, { headers });
    },
    nextNumber: (firmIdOverride) => API.invoices.nextInvoiceNumber(firmIdOverride),
    downloadPdf: async (id, optionsOrSize = 'A4') => {
      let url = `/api/invoices/${id}/pdf`;
      if (typeof optionsOrSize === 'string') {
        const cleanSize = optionsOrSize.trim();
        if (cleanSize && cleanSize !== '[object Object]') {
          url += `?size=${encodeURIComponent(cleanSize)}`;
        } else {
          url += `?size=A4`;
        }
      } else if (optionsOrSize && typeof optionsOrSize === 'object') {
        const params = new URLSearchParams();
        const fmt = (typeof optionsOrSize.format === 'string' && optionsOrSize.format && optionsOrSize.format !== '[object Object]')
          ? optionsOrSize.format.trim()
          : ((typeof optionsOrSize.size === 'string' && optionsOrSize.size && optionsOrSize.size !== '[object Object]') ? optionsOrSize.size.trim() : null);
        if (fmt) params.append('format', fmt);

        const thm = (typeof optionsOrSize.theme === 'string' && optionsOrSize.theme && optionsOrSize.theme !== '[object Object]')
          ? optionsOrSize.theme.trim()
          : null;
        if (thm) params.append('theme', thm);

        const rawCol = optionsOrSize.color || optionsOrSize.themeColor;
        const col = (typeof rawCol === 'string' && rawCol && rawCol !== '[object Object]')
          ? rawCol.trim()
          : null;
        if (col) params.append('color', col);

        const qs = params.toString();
        if (qs) url += `?${qs}`;
      }
      const res = await API._request(url);
      if (!res.ok) {
        throw new Error(`PDF download failed with HTTP status ${res.status}`);
      }
      const blob = await res.blob();
      return new Blob([blob], { type: 'application/pdf' });
    },
    pdfUrl: (id, optionsOrSize = 'A4') => {
      let url = `/api/invoices/${id}/pdf`;
      if (typeof optionsOrSize === 'string') {
        const cleanSize = optionsOrSize.trim();
        if (cleanSize && cleanSize !== '[object Object]') {
          url += `?size=${encodeURIComponent(cleanSize)}`;
        } else {
          url += `?size=A4`;
        }
      } else if (optionsOrSize && typeof optionsOrSize === 'object') {
        const params = new URLSearchParams();
        const fmt = (typeof optionsOrSize.format === 'string' && optionsOrSize.format && optionsOrSize.format !== '[object Object]')
          ? optionsOrSize.format.trim()
          : ((typeof optionsOrSize.size === 'string' && optionsOrSize.size && optionsOrSize.size !== '[object Object]') ? optionsOrSize.size.trim() : null);
        if (fmt) params.append('format', fmt);

        const thm = (typeof optionsOrSize.theme === 'string' && optionsOrSize.theme && optionsOrSize.theme !== '[object Object]')
          ? optionsOrSize.theme.trim()
          : null;
        if (thm) params.append('theme', thm);

        const rawCol = optionsOrSize.color || optionsOrSize.themeColor;
        const col = (typeof rawCol === 'string' && rawCol && rawCol !== '[object Object]')
          ? rawCol.trim()
          : null;
        if (col) params.append('color', col);

        const qs = params.toString();
        if (qs) url += `?${qs}`;
      }
      return url;
    },
    analyticsByCustomer: (id) => API._json(`/api/invoices/analytics/customer/${id}`),
    analyticsSearch: (name) => API._json(`/api/invoices/analytics/search?name=${encodeURIComponent(name)}`),
    deletePayment: (paymentId) => API._request(`/api/invoices/payments/${paymentId}`, { method: 'DELETE' }),
  },

  // ── Sales Returns (Credit Notes) ──
  returns: {
    create: (invoiceId, data) => API._ensureFirmReady().then(() => API._json(`/api/invoices/${invoiceId}/returns`, { method: 'POST', body: { ...data, firmId: API.firmId || data.firmId } })),
    listByInvoice: (invoiceId) => API._json(`/api/invoices/${invoiceId}/returns`),
    list: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/returns'))),
    get: (id) => API._json(`/api/returns/${id}`),
    downloadPdf: async (id, size = 'A4') => {
      const res = await API._request(`/api/returns/${id}/pdf?size=${size}`);
      const blob = await res.blob();
      return new Blob([blob], { type: 'application/pdf' });
    },
    nextNumber: () => API._json(API._qs('/api/invoices/next-return-number')),
  },

  // ── Reminders ──
  reminders: {
    list: (firmIdOverride) => {
      const fid = firmIdOverride || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._json(API._qs('/api/reminders'), { headers }));
    },
    listByFirm: (firmId) => API._json(`/api/reminders/firm/${firmId}`, { headers: { 'X-Firm-Id': String(firmId) } }),
    create: (data, firmIdOverride) => {
      const fid = firmIdOverride || (data && data.firmId) || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._json('/api/reminders', { method: 'POST', headers, body: { ...data, firmId: fid } }));
    },
    update: (id, data, firmIdOverride) => {
      const fid = firmIdOverride || (data && data.firmId) || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._json(`/api/reminders/${id}`, { method: 'PUT', headers, body: data });
    },
    delete: (id, firmIdOverride) => {
      const fid = firmIdOverride || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._request(`/api/reminders/${id}`, { method: 'DELETE', headers }));
    },
    markDone: (id, firmIdOverride) => {
      const fid = firmIdOverride || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._json(`/api/reminders/${id}/done`, { method: 'PUT', headers }));
    },
  },

  // ── Notes ──
  notes: {
    list: (firmIdOverride) => {
      const fid = firmIdOverride || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._json(API._qs('/api/notes'), { headers }));
    },
    listByFirm: (firmId) => API._json(`/api/notes/firm/${firmId}`, { headers: { 'X-Firm-Id': String(firmId) } }),
    create: (data, firmIdOverride) => {
      const fid = firmIdOverride || (data && data.firmId) || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._json('/api/notes', { method: 'POST', headers, body: { ...data, firmId: fid } }));
    },
    update: (id, data, firmIdOverride) => {
      const fid = firmIdOverride || (data && data.firmId) || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._json(`/api/notes/${id}`, { method: 'PUT', headers, body: data });
    },
    delete: (id, firmIdOverride) => {
      const fid = firmIdOverride || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._request(`/api/notes/${id}`, { method: 'DELETE', headers }));
    },
  },

  // ── Expenses ──
  expenses: {
    list: (params = {}, firmIdOverride) => {
      const fid = firmIdOverride || (typeof params === 'object' && params && params.firmId) || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => {
        const q = typeof params === 'object' && params !== null ? params : {};
        return API._json(API._qs('/api/expenses', q), { headers });
      });
    },
    listByFirm: (firmId) => API._json(`/api/expenses/firm/${firmId}`, { headers: { 'X-Firm-Id': String(firmId) } }),
    create: (data, firmIdOverride) => {
      const fid = firmIdOverride || (data && data.firmId) || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._json('/api/expenses', { method: 'POST', headers, body: { ...data, firmId: fid } }));
    },
    update: (id, data, firmIdOverride) => {
      const fid = firmIdOverride || (data && data.firmId) || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._json(`/api/expenses/${id}`, { method: 'PUT', headers, body: data });
    },
    delete: (id, firmIdOverride) => {
      const fid = firmIdOverride || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._request(`/api/expenses/${id}`, { method: 'DELETE', headers }));
    },
    summary: (firmIdOverride) => {
      const fid = firmIdOverride || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._json(API._qs('/api/expenses/summary'), { headers }));
    },
    categories: (firmIdOverride) => {
      const fid = firmIdOverride || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._json(API._qs('/api/expenses/categories'), { headers }));
    },
  },

  // ── Savings Tracker ──
  savings: {
    list: (params = {}, firmIdOverride) => {
      const fid = firmIdOverride || (typeof params === 'object' && params && params.firmId) || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => {
        const q = typeof params === 'object' && params !== null ? params : {};
        return API._json(API._qs('/api/savings', q), { headers });
      });
    },
    listByFirm: (firmId) => API._json(`/api/savings/firm/${firmId}`, { headers: { 'X-Firm-Id': String(firmId) } }),
    create: (data, firmIdOverride) => {
      const fid = firmIdOverride || (data && data.firmId) || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._json('/api/savings', { method: 'POST', headers, body: { ...data, firmId: fid } }));
    },
    update: (id, data, firmIdOverride) => {
      const fid = firmIdOverride || (data && data.firmId) || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._json(`/api/savings/${id}`, { method: 'PUT', headers, body: data });
    },
    delete: (id, firmIdOverride) => {
      const fid = firmIdOverride || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._request(`/api/savings/${id}`, { method: 'DELETE', headers }));
    },
    summary: (firmIdOverride) => {
      const fid = firmIdOverride || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._json(API._qs('/api/savings/summary'), { headers }));
    },
    categories: (firmIdOverride) => {
      const fid = firmIdOverride || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._json(API._qs('/api/savings/categories'), { headers }));
    },
  },

  // ── Goals & Habit Tracker ──
  goals: {
    list: (firmIdOverride) => {
      const fid = firmIdOverride || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._json(API._qs('/api/goals'), { headers }));
    },
    get: (id) => API._json(`/api/goals/${id}`),
    create: (data, firmIdOverride) => {
      const fid = firmIdOverride || (data && data.firmId) || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._json('/api/goals', { method: 'POST', headers, body: { ...data, firmId: fid } }));
    },
    update: (id, data, firmIdOverride) => {
      const fid = firmIdOverride || (data && data.firmId) || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._json(`/api/goals/${id}`, { method: 'PUT', headers, body: data });
    },
    delete: (id, firmIdOverride) => {
      const fid = firmIdOverride || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._request(`/api/goals/${id}`, { method: 'DELETE', headers }));
    },
    checkIn: (id) => API._json(`/api/goals/${id}/check-in`, { method: 'POST' }),
    incrementStreak: (id, days = 1) => API._json(API._qs(`/api/goals/${id}/increment-streak`, { days }), { method: 'POST' }),
    quickSaving: (id, amount = 1000, paymentMode = 'UPI', notes = 'Quick Goal Contribution') => API._json(`/api/goals/${id}/quick-saving`, { method: 'POST', body: { amount, paymentMode, notes } }),
    deduct: (id, amount = 1000, paymentMode = 'UPI', notes = 'Goal Deduction / Withdrawal') => API._json(`/api/goals/${id}/deduct`, { method: 'POST', body: { amount, paymentMode, notes } }),
    reconcile: (id, targetValue = 0, notes = '') => API._json(`/api/goals/${id}/reconcile`, { method: 'POST', body: { targetValue, notes } }),
    getSavings: (id) => API._json(`/api/goals/${id}/savings`),
    getTimeline: (id) => API._json(`/api/goals/${id}/timeline`),
    increment: (id, payloadOrDelta = 1) => {
      if (typeof payloadOrDelta === 'object' && payloadOrDelta !== null) {
        return API._json(`/api/goals/${id}/increment`, { method: 'POST', body: payloadOrDelta });
      }
      return API._json(API._qs(`/api/goals/${id}/increment`, { delta: payloadOrDelta }), { method: 'POST' });
    },
    reset: (id) => API._json(`/api/goals/${id}/reset`, { method: 'POST' }),
  },

  // ── Messages ──
  messages: {
    list: (firmIdOverride) => {
      const fid = firmIdOverride || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._json(API._qs('/api/messages'), { headers }));
    },
    create: (data, firmIdOverride) => {
      const fid = firmIdOverride || (data && data.firmId) || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._json('/api/messages', { method: 'POST', headers, body: { ...data, firmId: fid } }));
    },
    markRead: (id, firmIdOverride) => {
      const fid = firmIdOverride || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._json(`/api/messages/${id}/read`, { method: 'PUT', headers }));
    },
    delete: (id, firmIdOverride) => {
      const fid = firmIdOverride || API.firmId;
      const headers = fid ? { 'X-Firm-Id': String(fid) } : {};
      return API._ensureFirmReady().then(() => API._request(`/api/messages/${id}`, { method: 'DELETE', headers }));
    },
  },
  // ── Firm (multi-row) ──
  firm: {
    list: () => API._json('/api/firm'),
    get: (id) => {
      if (id == null) return Promise.reject(new Error('firmId is required'));
      return API._json(`/api/firm/${id}`);
    },
    getCurrent: async () => {
      try {
        const fid = API.firmId;
        if (fid) {
          const f = await API.firm.get(fid);
          if (f && (f.id || f.firmName)) return f;
        }
      } catch (e) {}
      try {
        const list = await API.firm.list();
        if (Array.isArray(list) && list.length > 0) {
          if (!API.firmId) API.firmId = list[0].id;
          return list[0];
        }
      } catch (e) {}
      return { firmName: 'Our Business', phone: '' };
    },
    create: (data) => API._json('/api/firm', { method: 'POST', body: data }),
    update: (id, data) => API._json(`/api/firm/${id}`, { method: 'PUT', body: data }),
    savePrintPreferences: (id, prefs) => {
      if (id == null) return Promise.reject(new Error('firmId is required'));
      return API._json(`/api/firm/${id}/print-preferences`, { method: 'PATCH', body: prefs });
    },
    delete: (id) => API._request(`/api/firm/${id}`, { method: 'DELETE' }),
  },

  // ── Analytics ──
  analytics: {
    firm: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/analytics/firm'))),
    customerStats: (id) => API._ensureFirmReady().then(() => API._json(API._qs(`/api/analytics/customer/${id}`))),
    productStats: (id) => API._ensureFirmReady().then(() => API._json(API._qs(`/api/analytics/product/${id}`))),
  },

  // ── Standalone Isolated KPI Facade Layer ──
  kpis: {
    dashboard: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/kpis/dashboard'))),
    customers: () => API._ensureFirmReady().then(() => API._json(API._qs('/api/kpis/customers'))),
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
    status: () => API._json('/api/licensing/status'),
    qr: () => API._json('/api/licensing/qr'),
    checkOnline: () => API._json('/api/licensing/check-online', { method: 'POST' }),
    initTrial: () => API._json('/api/licensing/init-trial', { method: 'POST' }),
    getMessages: () => API._json('/api/licensing/messages'),
    markMessageRead: (id) => API._json(`/api/licensing/messages/${id}/read`, { method: 'POST' }),
    snoozeLicense: (duration) => API._json('/api/licensing/snooze/license', { method: 'POST', body: { duration } }),
    snoozeDataProtection: (duration) => API._json('/api/licensing/snooze/dataprotection', { method: 'POST', body: { duration } }),
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
    inspectAuto: () => API._json('/api/backup/auto/inspect'),
    restoreAuto: async (firmIds, mode, targetFirmId) => {
      const formData = new FormData();
      if (firmIds && Array.isArray(firmIds)) {
        firmIds.forEach(id => formData.append('firmIds', id));
      }
      formData.append('mode', mode || 'clone');
      if (targetFirmId) formData.append('targetFirmId', targetFirmId);
      return API._json('/api/backup/auto/restore', { method: 'POST', body: formData });
    },
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
    list: () => API._json(API._qs('/api/employees')),
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

  // ─── Quotations / Estimates Helper ───
  estimates: {
    list: (page = 0, size = 50) => API.invoices.listEstimates(page, size),
    create: (data, firmIdOverride) => API.invoices.createEstimate(data, firmIdOverride),
    convert: (id, data, firmIdOverride) => API.invoices.convertEstimate(id, data, firmIdOverride),
    downloadPdf: (id, optionsOrSize = 'A4') => API.invoices.downloadPdf(id, optionsOrSize),
    pdfUrl: (id, optionsOrSize = 'A4') => API.invoices.pdfUrl(id, optionsOrSize)
  },
  quotations: {
    list: (page = 0, size = 50) => API.invoices.listEstimates(page, size),
    create: (data, firmIdOverride) => API.invoices.createEstimate(data, firmIdOverride),
    convert: (id, data, firmIdOverride) => API.invoices.convertEstimate(id, data, firmIdOverride),
    downloadPdf: (id, optionsOrSize = 'A4') => API.invoices.downloadPdf(id, optionsOrSize),
    pdfUrl: (id, optionsOrSize = 'A4') => API.invoices.pdfUrl(id, optionsOrSize)
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
    health: () => API._json('/api/health'),
    diagnostics: () => API._json('/api/health/diagnostics'),
    heartbeat: (firmId) => API._json(API._qs('/api/system/heartbeat', { firmId })),
    getNetworkStatus: (forceRefresh) => API._json(API._qs('/api/system/network-status', { forceRefresh }))
  },

  // ─── OmniSearch Read-Model Service (Authoritative Business Query Layer) ───
  omnisearch: {
    customer: (query, customerId) => API._json(API._qs('/api/omnisearch/customer', { query, customerId })),
    vendor: (query, vendorId) => API._json(API._qs('/api/omnisearch/vendor', { query, vendorId })),
    invoice: (query, invoiceId, estimateNumber) => API._json(API._qs('/api/omnisearch/invoice', { query, invoiceId, estimateNumber })),
    sales: (period, startDate, endDate, groupBy) => API._json(API._qs('/api/omnisearch/sales', { period, startDate, endDate, groupBy })),
    expenses: (period, startDate, endDate, category) => API._json(API._qs('/api/omnisearch/expenses', { period, startDate, endDate, category })),
    inventory: (query, filter) => API._json(API._qs('/api/omnisearch/inventory', { query, filter })),
    hr: (query, employeeId, date) => API._json(API._qs('/api/omnisearch/hr', { query, employeeId, date })),
    salaries: (employeeName, period) => API._json(API._qs('/api/omnisearch/salaries', { employeeName, period })),
    advances: (employeeName) => API._json(API._qs('/api/omnisearch/advances', { employeeName })),
    returns: (query, period) => API._json(API._qs('/api/omnisearch/returns', { query, period })),
    collections: (query, period, mode) => API._json(API._qs('/api/omnisearch/collections', { query, period, mode })),
    vendorPayouts: (vendorName, period) => API._json(API._qs('/api/omnisearch/vendor-payouts', { vendorName, period })),
    purchaseOrders: (query, status) => API._json(API._qs('/api/omnisearch/purchase-orders', { query, status })),
    stockMovements: (query, period) => API._json(API._qs('/api/omnisearch/stock-movements', { query, period })),
    ledger: (entityType, name, id) => API._json(API._qs('/api/omnisearch/ledger', { entityType, name, id })),
    aggregate: () => API._json(API._qs('/api/omnisearch/aggregate')),
    search: (query) => API._json(API._qs('/api/omnisearch/search', { query }))
  },

  // ─── Licensing & Expiry Snooze ───
  licensing: {
    status: () => API._json('/api/licensing/status'),
    checkOnline: () => API._json('/api/licensing/check-online', { method: 'POST' }),
    activate: (payload) => API._json('/api/licensing/activate', { method: 'POST', body: payload }),
    initTrial: () => API._json('/api/licensing/init-trial', { method: 'POST' }),
    qr: () => API._json('/api/licensing/qr'),
    messages: () => API._json('/api/licensing/messages'),
    markMessageRead: (id) => API._json(`/api/licensing/messages/${id}/read`, { method: 'POST' }),
    snoozeLicense: (duration) => API._json('/api/licensing/snooze/license', { method: 'POST', body: { duration } }),
    snoozeDataProtection: (duration) => API._json('/api/licensing/snooze/dataprotection', { method: 'POST', body: { duration } })
  },

  // ─── Data Protection Cloud Vault ───
  dataProtection: {
    status: () => API._json('/api/dataprotection/status'),
    backupNow: () => API._json('/api/dataprotection/backup-now', { method: 'POST' }),
    inspectCloud: (machineId, licenseId) => API._json('/api/dataprotection/inspect-cloud', { method: 'POST', body: { machineId, licenseId } }),
    restoreCloud: (params) => API._json(API._qs('/api/dataprotection/restore-cloud', params), { method: 'POST' })
  },

  // ─── Canonical Notifications (Bell + Inbox + Preferences) ───
  notifications: {
    summary: (firmId) => API._json(API._qs('/api/notifications/summary', { firmId })),
    list: (params = {}) => API._json(API._qs('/api/notifications', params)),
    get: (id) => API._json(`/api/notifications/${id}`),
    markRead: (id) => API._json(`/api/notifications/${id}/read`, { method: 'POST' }),
    markAllRead: (firmId) => API._json(API._qs('/api/notifications/read-all', { firmId }), { method: 'POST' }),
    snooze: (id, duration = '1d') => API._json(`/api/notifications/${id}/snooze`, { method: 'POST', body: { duration } }),
    dismiss: (id) => API._json(`/api/notifications/${id}/dismiss`, { method: 'POST' }),
    executeAction: (id, actionChoice = 'PRIMARY', payload = null) => API._json(`/api/notifications/${id}/action`, { method: 'POST', body: { actionChoice, payload } }),
    action: (id, actionChoice = 'PRIMARY', payload = null) => API._json(`/api/notifications/${id}/action`, { method: 'POST', body: { actionChoice, payload } }),
    getPreferences: (firmId) => API._json(API._qs('/api/notifications/preferences', { firmId })),
    savePreferences: (prefs, firmId) => API._json(API._qs('/api/notifications/preferences', { firmId }), { method: 'PUT', body: prefs })
  },

  // ─── Global Announcements ───
  announcements: {
    get: (force = false) => API._json(force ? '/api/announcements?force=true' : '/api/announcements')
  },

  // ─── Saved / Uncatalogued Items & Deduplication ───
  savedItems: {
    getUnifiedAutocomplete: (q) => API._json(API._qs('/api/items/autocomplete', { q })),
    list: () => API._json(API._qs('/api/saved-items')),
    promote: (id, payload) => API._json(API._qs(`/api/saved-items/${id}/promote`), { method: 'POST', body: payload }),
    delete: (id) => API._json(API._qs(`/api/saved-items/${id}`), { method: 'DELETE' })
  },
  deduplication: {
    getCandidates: () => API._json(API._qs('/api/items/duplicates')),
    dismiss: (payload) => API._json(API._qs('/api/items/duplicates/dismiss'), { method: 'POST', body: payload }),
    merge: (payload) => API._json(API._qs('/api/items/merge'), { method: 'POST', body: payload }),
    batchMerge: (payload) => API._json(API._qs('/api/items/merge/batch'), { method: 'POST', body: payload })
  }
};
