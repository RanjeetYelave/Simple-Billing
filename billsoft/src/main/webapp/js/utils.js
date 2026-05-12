/**
 * Billsoft Utility Functions
 */
const BillsoftUtils = {
  formatCurrency(amount) {
    if (amount == null || isNaN(amount)) return '₹0.00';
    return new Intl.NumberFormat('en-IN', {
      style: 'currency', currency: 'INR',
      minimumFractionDigits: 2, maximumFractionDigits: 2,
    }).format(amount);
  },

  formatDate(dateStr) {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return dateStr;
    return d.toLocaleDateString('en-IN', { year: 'numeric', month: 'short', day: 'numeric' });
  },

  formatDateTime(dtStr) {
    if (!dtStr) return '';
    const d = new Date(dtStr);
    if (isNaN(d.getTime())) return dtStr;
    return d.toLocaleDateString('en-IN', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
  },

  getStatusClass(status) {
    const map = {
      DRAFT: 'badge-draft', ESTIMATE: 'badge-estimate', FINAL: 'badge-final',
      SENT: 'badge-sent', PAID: 'badge-paid', OVERDUE: 'badge-overdue', CANCELLED: 'badge-cancelled',
    };
    return map[status] || 'badge-draft';
  },

  getStatusLabel(status) {
    const map = {
      DRAFT: 'Draft', ESTIMATE: 'Estimate', FINAL: 'Final',
      SENT: 'Sent', PAID: 'Paid', OVERDUE: 'Overdue', CANCELLED: 'Cancelled',
    };
    return map[status] || status;
  },

  debounce(fn, ms = 300) {
    let timer;
    return (...args) => {
      clearTimeout(timer);
      timer = setTimeout(() => fn(...args), ms);
    };
  },

  generateId() {
    return Math.random().toString(36).substring(2, 10);
  },

  downloadBlob(blob, filename) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  },

  todayStr() {
    return new Date().toISOString().split('T')[0];
  },

  futureDateStr(days) {
    const d = new Date();
    d.setDate(d.getDate() + days);
    return d.toISOString().split('T')[0];
  },
};
