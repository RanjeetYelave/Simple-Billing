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

  getDocTimestamp(doc) {
    if (!doc) return 0;
    // 1. Extract calendar day (YYYY-MM-DD)
    const rawDate = doc.invoiceDate || doc.estimateDate || doc.createdAt || doc.updatedAt;
    let dateStr = '';
    if (rawDate) {
      if (typeof rawDate === 'string') {
        dateStr = rawDate.split('T')[0];
      } else if (rawDate instanceof Date) {
        dateStr = rawDate.toISOString().split('T')[0];
      }
    }

    let dayMs = 0;
    if (dateStr) {
      const parts = dateStr.split('-');
      if (parts.length === 3) {
        const y = parseInt(parts[0], 10);
        const m = parseInt(parts[1], 10) - 1;
        const d = parseInt(parts[2], 10);
        dayMs = new Date(y, m, d).getTime();
      }
    }
    if (isNaN(dayMs) || dayMs === 0) {
      dayMs = rawDate ? new Date(rawDate).getTime() : 0;
    }
    if (isNaN(dayMs)) dayMs = 0;

    // 2. Intra-day time precision (from createdAt, updatedAt, or time portion of rawDate)
    let timeMs = 0;
    const timeSource = doc.createdAt || doc.updatedAt || (typeof rawDate === 'string' && rawDate.includes('T') ? rawDate : null);
    if (timeSource) {
      const tDate = new Date(timeSource);
      if (!isNaN(tDate.getTime())) {
        timeMs = tDate.getHours() * 3600000 + tDate.getMinutes() * 60000 + tDate.getSeconds() * 1000 + tDate.getMilliseconds();
      }
    }

    return dayMs + timeMs;
  },

  compareDocuments(a, b, sortField = 'date', sortDir = 'desc') {
    let cmp = 0;
    if (sortField === 'date') {
      const da = BillsoftUtils.getDocTimestamp(a);
      const db = BillsoftUtils.getDocTimestamp(b);
      cmp = db - da; // default desc (newest first)
      if (cmp === 0) {
        cmp = (Number(b.id) || 0) - (Number(a.id) || 0);
      }
      return sortDir === 'asc' ? -cmp : cmp;
    } else if (sortField === 'amount') {
      const aa = Number(a.totalAmount) || 0;
      const ab = Number(b.totalAmount) || 0;
      cmp = ab - aa;
      if (cmp === 0) cmp = (Number(b.id) || 0) - (Number(a.id) || 0);
      return sortDir === 'asc' ? -cmp : cmp;
    } else if (sortField === 'status') {
      const sa = (a.status || '').toString().trim().toUpperCase();
      const sb = (b.status || '').toString().trim().toUpperCase();
      cmp = sa.localeCompare(sb);
      if (cmp === 0) cmp = (Number(b.id) || 0) - (Number(a.id) || 0);
      return sortDir === 'desc' ? -cmp : cmp;
    } else if (sortField === 'number') {
      const na = (a.docNumber || a.invoiceNumber || a.estimateNumber || ('#' + a.id)).toString().trim();
      const nb = (b.docNumber || b.invoiceNumber || b.estimateNumber || ('#' + b.id)).toString().trim();
      cmp = na.localeCompare(nb, undefined, { numeric: true, sensitivity: 'base' });
      if (cmp === 0) cmp = (Number(b.id) || 0) - (Number(a.id) || 0);
      return sortDir === 'desc' ? -cmp : cmp;
    } else if (sortField === 'customer') {
      const ca = ((a.customer && a.customer.name) || a.customerName || '').toString().trim();
      const cb = ((b.customer && b.customer.name) || b.customerName || '').toString().trim();
      cmp = ca.localeCompare(cb, undefined, { numeric: true, sensitivity: 'base' });
      if (cmp === 0) cmp = (Number(b.id) || 0) - (Number(a.id) || 0);
      return sortDir === 'desc' ? -cmp : cmp;
    }
    
    // Default fallback by ID
    cmp = (Number(b.id) || 0) - (Number(a.id) || 0);
    return sortDir === 'asc' ? -cmp : cmp;
  },

  getStatusClass(status) {
    const map = {
      DRAFT: 'badge-draft',
      UNPAID: 'badge-warning',
      FINAL: 'badge-warning',
      PAID: 'badge-paid',
      CANCELLED: 'badge-cancelled',
      ESTIMATE: 'badge-estimate',
      SENT: 'badge-sent',
      OVERDUE: 'badge-overdue',
    };
    return map[status] || 'badge-warning';
  },

  getStatusLabel(status) {
    const map = {
      DRAFT: 'Draft',
      UNPAID: 'Unpaid',
      FINAL: 'Unpaid',
      PAID: 'Paid',
      CANCELLED: 'Cancelled',
      ESTIMATE: 'Quotation',
      SENT: 'Sent',
      OVERDUE: 'Overdue',
    };
    return map[status] || status || 'Unpaid';
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

  downloadPdfUrl(url, filename) {
    // Navigate directly to the server URL.
    // Since the backend returns Content-Disposition: attachment, the browser
    // will download the file — and use the filename from the server header.
    const fullUrl = url.startsWith('http') ? url : `${window.location.origin}${url}`;
    const a = document.createElement('a');
    a.style.cssText = 'display:none;position:fixed;top:-100px;left:-100px';
    a.href = fullUrl;
    // Do NOT set a.download here — let the server Content-Disposition drive the filename
    document.body.appendChild(a);
    a.click();
    setTimeout(() => { if (a.parentNode) a.parentNode.removeChild(a); }, 1000);
  },

  downloadBlob(blob, filename) {
    if (!blob) return;
    const mimeType = filename.toLowerCase().endsWith('.pdf')
      ? 'application/pdf'
      : (blob.type || 'application/octet-stream');

    const reader = new FileReader();
    reader.onloadend = function () {
      if (!reader.result) return;

      // Native JavaFX Shell bridge
      if (window.javafxFileHelper && typeof window.javafxFileHelper.saveBase64File === 'function') {
        const parts = String(reader.result).split(',');
        const base64 = parts.length > 1 ? parts[1] : parts[0];
        window.javafxFileHelper.saveBase64File(base64, filename, mimeType);
        return;
      }

      // Standard browser download fallback
      const dataUrl = reader.result.replace(/^data:[^;]+/, 'data:' + mimeType);
      const a = document.createElement('a');
      a.style.cssText = 'display:none;position:fixed;top:-100px;left:-100px';
      a.href = dataUrl;
      a.download = filename;
      a.setAttribute('download', filename);
      document.body.appendChild(a);
      a.click();
      setTimeout(() => { if (a.parentNode) a.parentNode.removeChild(a); }, 1000);
    };
    reader.onerror = function (err) { console.error('FileReader error:', err); };
    reader.readAsDataURL(new Blob([blob], { type: mimeType }));
  },

  printBlob(blob) {
    if (blob.type !== 'application/pdf') {
      blob = new Blob([blob], { type: 'application/pdf' });
    }
    // Create print modal with PDF.js
    const overlay = document.createElement('div');
    overlay.className = 'print-modal-overlay';
    overlay.style.position = 'fixed';
    overlay.style.top = '0';
    overlay.style.left = '0';
    overlay.style.width = '100vw';
    overlay.style.height = '100vh';
    overlay.style.backgroundColor = 'rgba(0,0,0,0.85)';
    overlay.style.zIndex = '99999';
    overlay.style.display = 'flex';
    overlay.style.flexDirection = 'column';
    
    const header = document.createElement('div');
    header.style.padding = '12px 20px';
    header.style.backgroundColor = '#1e293b';
    header.style.display = 'flex';
    header.style.justifyContent = 'space-between';
    header.style.alignItems = 'center';
    
    const title = document.createElement('h3');
    title.style.color = '#fff';
    title.style.margin = '0';
    title.style.fontSize = '1.1rem';
    title.style.fontWeight = '500';
    title.innerText = 'Preparing Print Preview...';
    
    const actions = document.createElement('div');
    actions.style.display = 'flex';
    actions.style.gap = '10px';
    
    const actualPrintBtn = document.createElement('button');
    actualPrintBtn.innerText = 'Print';
    actualPrintBtn.className = 'btn btn-sm btn-success';
    actualPrintBtn.style.cursor = 'pointer';
    actualPrintBtn.style.border = 'none';
    actualPrintBtn.style.padding = '6px 16px';
    actualPrintBtn.style.borderRadius = '4px';
    actualPrintBtn.style.backgroundColor = '#10b981';
    actualPrintBtn.style.color = 'white';
    actualPrintBtn.style.display = 'none'; // hide until loaded
    
    const closeBtn = document.createElement('button');
    closeBtn.innerText = 'Close';
    closeBtn.className = 'btn btn-sm btn-outline';
    closeBtn.style.cursor = 'pointer';
    closeBtn.style.border = '1px solid #cbd5e1';
    closeBtn.style.padding = '5px 12px';
    closeBtn.style.borderRadius = '4px';
    closeBtn.style.background = 'transparent';
    closeBtn.style.color = '#fff';
    
    actions.appendChild(actualPrintBtn);
    actions.appendChild(closeBtn);
    
    header.appendChild(title);
    header.appendChild(actions);
    
    const content = document.createElement('div');
    content.id = 'pdf-render-container';
    content.style.flex = '1';
    content.style.overflowY = 'auto';
    content.style.padding = '20px';
    content.style.display = 'flex';
    content.style.flexDirection = 'column';
    content.style.alignItems = 'center';
    content.style.gap = '20px';
    
    overlay.appendChild(header);
    overlay.appendChild(content);
    document.body.appendChild(overlay);
    
    // Add print styles dynamically
    let printStyle = document.getElementById('print-pdf-style');
    if (!printStyle) {
      printStyle = document.createElement('style');
      printStyle.id = 'print-pdf-style';
      printStyle.innerHTML = `
        @page {
          size: auto;
          margin: 0mm;
        }
        @media print {
          html, body {
            margin: 0 !important;
            padding: 0 !important;
            background: #fff !important;
          }
          body > *:not(.print-modal-overlay) { display: none !important; }
          .print-modal-overlay { 
            position: static !important; 
            width: 100% !important; height: auto !important; 
            background: transparent !important; display: block !important; 
            overflow: visible !important;
          }
          .print-modal-overlay > div:first-child { display: none !important; }
          #pdf-render-container { 
            padding: 0 !important; margin: 0 !important;
            overflow: visible !important; 
            display: block !important; height: auto !important;
          }
          #pdf-render-container img { 
            max-width: 100% !important; width: 100% !important;
            display: block !important;
            page-break-after: always !important;
            break-after: page !important;
            margin: 0 !important;
            box-shadow: none !important;
          }
        }
      `;
      document.head.appendChild(printStyle);
    }
    
    closeBtn.onclick = () => {
      document.body.removeChild(overlay);
    };
    
    actualPrintBtn.onclick = () => {
      window.print();
    };
    
    // Use PDF.js to render
    if (typeof pdfjsLib === 'undefined') {
      title.innerText = 'Error: PDF engine not loaded.';
      return;
    }
    
    const fileReader = new FileReader();
    fileReader.onload = async function() {
      const typedarray = new Uint8Array(this.result);
      try {
        const pdf = await pdfjsLib.getDocument(typedarray).promise;
        title.innerText = 'Print Preview (' + pdf.numPages + ' Pages)';
        
        for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
          const page = await pdf.getPage(pageNum);
          const viewport = page.getViewport({ scale: 2.0 }); // higher scale for print quality
          
          const canvas = document.createElement('canvas');
          const context = canvas.getContext('2d');
          canvas.height = viewport.height;
          canvas.width = viewport.width;
          
          await page.render({ canvasContext: context, viewport: viewport }).promise;
          
          // Convert to image for better print layout
          const img = document.createElement('img');
          img.src = canvas.toDataURL('image/png');
          img.style.maxWidth = '100%';
          img.style.width = '210mm'; // A4 width approx
          img.style.boxShadow = '0 4px 6px rgba(0,0,0,0.3)';
          img.style.backgroundColor = '#fff';
          
          content.appendChild(img);
        }
        actualPrintBtn.style.display = 'inline-block';
      } catch (err) {
        title.innerText = 'Failed to load PDF preview.';
        console.error(err);
      }
    };
    fileReader.readAsArrayBuffer(blob);
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

/**
 * RupeeCRM / Billsoft WhatsApp Share & Direct Link Generator Engine
 */
const BillsoftWhatsApp = {
  cleanPhone(rawPhone) {
    if (!rawPhone) return '';
    let digits = String(rawPhone).replace(/\D/g, '');
    if (!digits) return '';
    // If Indian 10-digit number without country code
    if (digits.length === 10) {
      digits = '91' + digits;
    } else if (digits.length === 11 && digits.startsWith('0')) {
      digits = '91' + digits.substring(1);
    }
    return digits;
  },

  buildUrl(phone, messageText) {
    const clean = BillsoftWhatsApp.cleanPhone(phone);
    const encoded = encodeURIComponent(messageText || '');
    if (clean) {
      return `https://web.whatsapp.com/send?phone=${clean}&text=${encoded}`;
    }
    return `https://web.whatsapp.com/send?text=${encoded}`;
  },

  openDirect(phone, messageText) {
    const url = BillsoftWhatsApp.buildUrl(phone, messageText);
    window.open(url, '_blank', 'noopener,noreferrer');
  },

  openNativeApp(phone, messageText) {
    const clean = BillsoftWhatsApp.cleanPhone(phone);
    const encoded = encodeURIComponent(messageText || '');
    const url = clean ? `whatsapp://send?phone=${clean}&text=${encoded}` : `whatsapp://send?text=${encoded}`;
    window.location.href = url;
  },

  openWebShare(messageText) {
    const encoded = encodeURIComponent(messageText || '');
    window.open(`https://web.whatsapp.com/send?text=${encoded}`, '_blank', 'noopener,noreferrer');
  },

  sanitizeFileName(name, defaultExt = '.pdf') {
    if (!name) return `Document_${Date.now()}${defaultExt}`;
    let clean = String(name).trim().replace(/[/\\?%*:|"<>]/g, '_').replace(/\s+/g, '_');
    if (!clean.toLowerCase().endsWith('.pdf') && defaultExt === '.pdf') {
      clean += '.pdf';
    }
    return clean;
  },

  downloadBlob(blob, filename) {
    if (!blob) return;
    const cleanName = BillsoftWhatsApp.sanitizeFileName(filename);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = cleanName;
    document.body.appendChild(a);
    a.click();
    setTimeout(() => {
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    }, 2000);
  },

  previewBlob(blob) {
    if (!blob) return;
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank', 'noopener,noreferrer');
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  },

  canNativeShareFiles() {
    try {
      if (typeof navigator === 'undefined' || typeof navigator.canShare !== 'function' || !navigator.share) {
        return false;
      }
      const testFile = new File(['test'], 'test.pdf', { type: 'application/pdf' });
      return navigator.canShare({ files: [testFile] });
    } catch (e) {
      return false;
    }
  },

  async shareNativeWithFile(file, text, title) {
    if (!file || !navigator.share) return false;
    try {
      await navigator.share({
        files: [file],
        title: title || 'Document',
        text: text || ''
      });
      return true;
    } catch (e) {
      if (e.name === 'AbortError') return false;
      throw e;
    }
  },

  _formatMoney(num) {
    if (num == null || isNaN(num)) return '0.00';
    return Number(num).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  },

  _getUpiText(firm, amount, refNo) {
    if (!firm || !firm.upiId) return '';
    const cleanUpi = firm.upiId.trim();
    if (!cleanUpi) return '';
    const amtStr = amount ? BillsoftWhatsApp._formatMoney(amount) : '';
    let upiBlock = `\n💳 *UPI Payment:* \`${cleanUpi}\``;
    if (amount) {
      const upiLink = `upi://pay?pa=${encodeURIComponent(cleanUpi)}&pn=${encodeURIComponent(firm.firmName || 'Merchant')}&am=${amount}&cu=INR&tn=${encodeURIComponent(refNo || 'Payment')}`;
      upiBlock += `\n📲 *Pay via UPI Link:* ${upiLink}`;
    }
    return upiBlock;
  },

  formatInvoice(params = {}) {
    const { invoice = {}, firm = {}, customer = {} } = (params || {});
    const firmName = (firm && firm.firmName) || 'Our Business';
    const firmPhone = (firm && (firm.contactNumber || firm.phone)) || '';
    const custName = (customer && (customer.name || customer.customerName)) || (invoice && (invoice.customerName || invoice.clientName || (invoice.customer && invoice.customer.name))) || 'Valued Customer';
    const invNo = (invoice && (invoice.invoiceNumber || invoice.id)) || 'N/A';
    const invDate = BillsoftUtils.formatDate((invoice && (invoice.invoiceDate || invoice.createdAt)) || new Date());
    const items = (invoice && (invoice.items || invoice.invoiceItems)) || [];
    
    let itemsText = '';
    if (items.length > 0) {
      itemsText = items.map((it, idx) => {
        const name = (it && (it.productName || it.name || it.description)) || `Item ${idx + 1}`;
        const qty = (it && (it.quantity || it.qty)) || 1;
        const rate = BillsoftWhatsApp._formatMoney(it && (it.unitPrice || it.rate || it.price || 0));
        const total = BillsoftWhatsApp._formatMoney((it && (it.total || it.amount)) || (qty * ((it && it.unitPrice) || 0)));
        return `${idx + 1}. *${name}* (${qty} x ₹${rate}) = ₹${total}`;
      }).join('\n');
    }

    const subTotal = BillsoftWhatsApp._formatMoney(invoice && (invoice.subTotal || invoice.subtotal || invoice.totalAmount || 0));
    const taxAmt = BillsoftWhatsApp._formatMoney(invoice && (invoice.taxAmount || invoice.totalGst || 0));
    const netTotal = BillsoftWhatsApp._formatMoney(invoice && (invoice.netTotal || invoice.totalAmount || 0));
    const paidAmt = (invoice && invoice.paidAmount != null) ? BillsoftWhatsApp._formatMoney(invoice.paidAmount) : null;
    const balanceDue = BillsoftWhatsApp._formatMoney(invoice && (invoice.balanceDue != null ? invoice.balanceDue : (invoice.netTotal || 0)));

    let msg = `🧾 *TAX INVOICE — ${invNo}*\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `🏢 *${firmName}*\n`;
    if (firmPhone) msg += `📞 Contact: ${firmPhone}\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `👤 *Customer:* ${custName}\n`;
    msg += `📅 *Date:* ${invDate}\n`;
    if (invoice && invoice.dueDate) msg += `⏳ *Due Date:* ${BillsoftUtils.formatDate(invoice.dueDate)}\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    if (itemsText) {
      msg += `*BILL PARTICULARS:*\n${itemsText}\n`;
      msg += `────────────────────\n`;
    }
    msg += `Subtotal: ₹${subTotal}\n`;
    if (invoice && Number(invoice.taxAmount || invoice.totalGst || 0) > 0) {
      msg += `GST / Tax: ₹${taxAmt}\n`;
    }
    if (invoice && Number(invoice.discount || 0) > 0) {
      msg += `Discount: -₹${BillsoftWhatsApp._formatMoney(invoice.discount)}\n`;
    }
    msg += `*Net Total:* ₹${netTotal}\n`;
    if (paidAmt && Number(paidAmt) > 0) {
      msg += `Paid Amount: ₹${paidAmt}\n`;
    }
    msg += `*Balance Due:* ₹${balanceDue}\n`;

    const upiText = BillsoftWhatsApp._getUpiText(firm, invoice && (invoice.balanceDue != null ? invoice.balanceDue : invoice.netTotal), `INV-${invNo}`);
    if (upiText) {
      msg += `${upiText}\n`;
    }
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `_Thank you for your business!_ ✨`;
    return msg;
  },

  formatEstimate(params = {}) {
    const { estimate = {}, firm = {}, customer = {} } = (params || {});
    const firmName = (firm && firm.firmName) || 'Our Business';
    const custName = (customer && (customer.name || customer.customerName)) || (estimate && (estimate.customerName || estimate.clientName || (estimate.customer && estimate.customer.name))) || 'Valued Client';
    const estNo = (estimate && (estimate.estimateNumber || estimate.invoiceNumber || estimate.id)) || 'N/A';
    const estDate = BillsoftUtils.formatDate((estimate && (estimate.estimateDate || estimate.invoiceDate || estimate.createdAt)) || new Date());
    const items = (estimate && (estimate.items || estimate.invoiceItems)) || [];

    let itemsText = '';
    if (items.length > 0) {
      itemsText = items.map((it, idx) => {
        const name = (it && (it.productName || it.name || it.description)) || `Item ${idx + 1}`;
        const qty = (it && (it.quantity || it.qty)) || 1;
        const rate = BillsoftWhatsApp._formatMoney(it && (it.unitPrice || it.rate || it.price || 0));
        const total = BillsoftWhatsApp._formatMoney((it && (it.total || it.amount)) || (qty * ((it && it.unitPrice) || 0)));
        return `${idx + 1}. *${name}* (${qty} x ₹${rate}) = ₹${total}`;
      }).join('\n');
    }

    const netTotal = BillsoftWhatsApp._formatMoney(estimate && (estimate.netTotal || estimate.totalAmount || 0));

    let msg = `📋 *QUOTATION / ESTIMATE — ${estNo}*\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `🏢 *${firmName}*\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `👤 *Client:* ${custName}\n`;
    msg += `📅 *Date:* ${estDate}\n`;
    if (estimate && (estimate.validUntil || estimate.expiryDate)) {
      msg += `⏳ *Valid Until:* ${BillsoftUtils.formatDate(estimate.validUntil || estimate.expiryDate)}\n`;
    }
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    if (itemsText) {
      msg += `*ESTIMATED ITEMS:*\n${itemsText}\n`;
      msg += `────────────────────\n`;
    }
    msg += `*Estimated Total:* ₹${netTotal}\n`;
    if (estimate && estimate.notes) {
      msg += `📝 *Notes:* ${estimate.notes}\n`;
    }
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `_Please feel free to connect with us to proceed or customize!_ ✨`;
    return msg;
  },

  formatCustomerStatement(params = {}) {
    const { customer = {}, statement = {}, from, to, firm = {} } = (params || {});
    const firmName = (firm && firm.firmName) || 'Our Business';
    const custName = (customer && (customer.name || customer.customerName)) || 'Customer';
    const fromStr = from ? BillsoftUtils.formatDate(from) : 'Beginning';
    const toStr = to ? BillsoftUtils.formatDate(to) : 'Today';

    const opBal = BillsoftWhatsApp._formatMoney(statement && (statement.openingBalance || 0));
    const totalBilled = BillsoftWhatsApp._formatMoney(statement && (statement.totalInvoiced || statement.totalDebit || statement.totalAmount || 0));
    const totalPaid = BillsoftWhatsApp._formatMoney(statement && (statement.totalPaid || statement.totalCredit || 0));
    const closingBal = BillsoftWhatsApp._formatMoney(statement && (statement.closingBalance != null ? statement.closingBalance : (statement.balanceDue || 0)));

    let msg = `📊 *CUSTOMER STATEMENT OF ACCOUNT*\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `🏢 *${firmName}*\n`;
    msg += `👤 *Account:* ${custName}\n`;
    msg += `🗓️ *Period:* ${fromStr} to ${toStr}\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `• Opening Balance: ₹${opBal}\n`;
    msg += `• Total Invoiced: ₹${totalBilled}\n`;
    msg += `• Total Payments Received: ₹${totalPaid}\n`;
    msg += `────────────────────\n`;
    msg += `*Net Outstanding Balance: ₹${closingBal}*\n`;

    const upiText = BillsoftWhatsApp._getUpiText(firm, statement && statement.closingBalance, `STMT-${custName}`);
    if (upiText) {
      msg += `${upiText}\n`;
    }
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `_For statement queries or ledger reconciliation, please reply here._`;
    return msg;
  },

  formatPartyStatement(params = {}) {
    const { party = {}, statement = {}, from, to, firm = {} } = (params || {});
    const firmName = (firm && firm.firmName) || 'Our Business';
    const partyName = (party && (party.name || party.partyName)) || 'Supplier / Vendor';
    const fromStr = from ? BillsoftUtils.formatDate(from) : 'Beginning';
    const toStr = to ? BillsoftUtils.formatDate(to) : 'Today';

    const opBal = BillsoftWhatsApp._formatMoney(statement && (statement.openingBalance || 0));
    const totalPurchases = BillsoftWhatsApp._formatMoney(statement && (statement.totalPurchases || statement.totalBilled || statement.totalDebit || 0));
    const totalPaid = BillsoftWhatsApp._formatMoney(statement && (statement.totalPaid || statement.totalCredit || 0));
    const closingBal = BillsoftWhatsApp._formatMoney(statement && (statement.closingBalance != null ? statement.closingBalance : (statement.balancePayable || 0)));

    let msg = `📊 *VENDOR / SUPPLIER LEDGER STATEMENT*\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `🏢 *${firmName}*\n`;
    msg += `🤝 *Vendor:* ${partyName}\n`;
    msg += `🗓️ *Period:* ${fromStr} to ${toStr}\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `• Opening Balance: ₹${opBal}\n`;
    msg += `• Total Purchase Orders / Invoices: ₹${totalPurchases}\n`;
    msg += `• Total Payments Disbursed: ₹${totalPaid}\n`;
    msg += `────────────────────\n`;
    msg += `*Net Balance Payable: ₹${closingBal}*\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `_Statement generated via ${firmName} Ledger._`;
    return msg;
  },

  formatSalarySlip(params = {}) {
    const { employee = {}, statement = {}, periodLabel, firm = {} } = (params || {});
    const firmName = (firm && firm.firmName) || 'Our Company';
    const empName = (employee && (employee.fullName || employee.name)) || 'Employee';
    const empCode = (employee && (employee.employeeCode || employee.id)) || '';
    const designation = (employee && employee.designation) || 'Staff Member';
    const period = periodLabel || 'Current Month';

    const grossSalary = BillsoftWhatsApp._formatMoney(statement && (statement.grossSalary || (employee && employee.basicSalary) || 0));
    const advances = BillsoftWhatsApp._formatMoney(statement && (statement.advancesDeducted || statement.totalDeductions || 0));
    const netDisbursed = BillsoftWhatsApp._formatMoney(statement && (statement.netDisbursed || statement.netSalary || grossSalary));

    let msg = `💼 *SALARY PAYSLIP — ${period.toUpperCase()}*\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `🏢 *${firmName} — HR & Payroll*\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `👤 *Name:* ${empName}${empCode ? ' (' + empCode + ')' : ''}\n`;
    msg += `🏷️ *Designation:* ${designation}\n`;
    msg += `🗓️ *Pay Period:* ${period}\n`;
    if (statement && statement.presentDays != null) {
      msg += `📅 *Days Present:* ${statement.presentDays} ${statement.totalDays ? '/ ' + statement.totalDays : ''}\n`;
    }
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `• Gross Salary: ₹${grossSalary}\n`;
    if (statement && Number(statement.advancesDeducted || statement.totalDeductions || 0) > 0) {
      msg += `• Deductions / Advances: -₹${advances}\n`;
    }
    msg += `────────────────────\n`;
    msg += `💰 *NET DISBURSED SALARY: ₹${netDisbursed}*\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `_Confidential payroll communication from ${firmName}._`;
    return msg;
  },

  formatPurchaseOrder(params = {}) {
    const { po = {}, party = {}, firm = {} } = (params || {});
    const firmName = (firm && firm.firmName) || 'Our Business';
    const partyName = (party && party.name) || (po && (po.partyName || (po.party && po.party.name))) || 'Supplier';
    const poNo = (po && (po.poNumber || po.id)) || 'N/A';
    const poDate = BillsoftUtils.formatDate((po && (po.poDate || po.createdAt)) || new Date());
    const items = (po && (po.items || po.purchaseOrderItems)) || [];

    let itemsText = '';
    if (items.length > 0) {
      itemsText = items.map((it, idx) => {
        const name = (it && (it.productName || it.name)) || `Item ${idx + 1}`;
        const qty = (it && (it.quantity || it.qty)) || 1;
        const rate = BillsoftWhatsApp._formatMoney(it && (it.unitPrice || it.rate || 0));
        const total = BillsoftWhatsApp._formatMoney((it && it.total) || (qty * ((it && it.unitPrice) || 0)));
        return `${idx + 1}. *${name}* (${qty} x ₹${rate}) = ₹${total}`;
      }).join('\n');
    }

    const netTotal = BillsoftWhatsApp._formatMoney(po && (po.totalAmount || po.netTotal || 0));

    let msg = `📦 *PURCHASE ORDER — ${poNo}*\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `🏢 *${firmName}*\n`;
    msg += `🤝 *To Supplier:* ${partyName}\n`;
    msg += `📅 *PO Date:* ${poDate}\n`;
    if (po && po.expectedDeliveryDate) {
      msg += `🚚 *Expected Delivery:* ${BillsoftUtils.formatDate(po.expectedDeliveryDate)}\n`;
    }
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    if (itemsText) {
      msg += `*ORDERED ITEMS:*\n${itemsText}\n`;
      msg += `────────────────────\n`;
    }
    msg += `*Total Order Value:* ₹${netTotal}\n`;
    if (po && (po.notes || po.termsAndConditions)) {
      msg += `📝 *Instructions:* ${po.notes || po.termsAndConditions}\n`;
    }
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `_Please acknowledge this PO and confirm dispatch schedule._ ✨`;
    return msg;
  },

  formatPaymentReminder(params = {}) {
    const { customer = {}, invoice, balance, firm = {} } = (params || {});
    const firmName = (firm && firm.firmName) || 'Our Business';
    const custName = (customer && (customer.name || customer.customerName)) || (invoice && (invoice.customerName || (invoice.customer && invoice.customer.name))) || 'Valued Customer';
    const invNo = invoice ? (invoice.invoiceNumber || invoice.id) : null;
    const dueAmt = BillsoftWhatsApp._formatMoney(balance != null ? balance : (invoice ? (invoice.balanceDue || invoice.netTotal) : 0));

    let msg = `🔔 *PAYMENT REMINDER*\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `Dear *${custName}*,\n\n`;
    msg += `This is a friendly reminder from *${firmName}* regarding outstanding dues${invNo ? ' on Invoice *' + invNo + '*' : ''}.\n\n`;
    msg += `💰 *Pending Balance:* ₹${dueAmt}\n`;
    if (invoice && invoice.invoiceDate) {
      msg += `📅 *Invoice Date:* ${BillsoftUtils.formatDate(invoice.invoiceDate)}\n`;
    }
    if (invoice && invoice.dueDate) {
      msg += `⏳ *Due Date:* ${BillsoftUtils.formatDate(invoice.dueDate)}\n`;
    }

    const upiText = BillsoftWhatsApp._getUpiText(firm, balance != null ? balance : (invoice && invoice.balanceDue), invNo ? `REM-${invNo}` : `REM-${custName}`);
    if (upiText) {
      msg += `${upiText}\n`;
    }
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `Kindly arrange for the settlement at your convenience. If already processed, please ignore this notice.\n`;
    msg += `_Thank you for your cooperation!_ 🙏`;
    return msg;
  },

  formatPaymentReceipt(params = {}) {
    const { payment = {}, invoice = {}, customer = {}, firm = {} } = (params || {});
    const firmName = (firm && firm.firmName) || 'Our Business';
    const custName = (customer && (customer.name || customer.customerName)) || (invoice && (invoice.customerName || (invoice.customer && invoice.customer.name))) || 'Valued Customer';
    const payAmt = BillsoftWhatsApp._formatMoney(payment && (payment.amount || payment.paidAmount || 0));
    const payDate = BillsoftUtils.formatDate((payment && (payment.paymentDate || payment.createdAt)) || new Date());
    const payMode = (payment && (payment.paymentMode || payment.mode)) || 'Cash/Online';
    const refNo = (payment && (payment.referenceNumber || payment.id)) || '';

    let msg = `💳 *PAYMENT RECEIPT ACKNOWLEDGEMENT*\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `🏢 *${firmName}*\n`;
    msg += `👤 *Received From:* ${custName}\n`;
    msg += `💰 *Amount Received:* ₹${payAmt}\n`;
    msg += `📅 *Date:* ${payDate}\n`;
    msg += `🏷️ *Payment Mode:* ${payMode}\n`;
    if (refNo) msg += `🔖 *Reference #:* ${refNo}\n`;
    if (invoice && (invoice.invoiceNumber || invoice.id)) {
      msg += `🧾 *Applied to Invoice:* ${invoice.invoiceNumber || invoice.id}\n`;
      if (invoice.balanceDue != null) {
        msg += `⏳ *Remaining Balance:* ₹${BillsoftWhatsApp._formatMoney(invoice.balanceDue)}\n`;
      }
    }
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `_Payment successfully recorded. Thank you!_ 🙏`;
    return msg;
  },

  formatLetter(params = {}) {
    const { letter = {}, firm = {} } = (params || {});
    const firmName = (firm && firm.firmName) || 'Our Business';
    const recipientName = (letter && (letter.recipientName || letter.recipientCompany)) || 'Recipient';
    const letterNo = (letter && (letter.letterNumber || letter.id)) || '';
    const subject = (letter && letter.subject) || 'Official Communication';
    const letterDate = BillsoftUtils.formatDate((letter && letter.letterDate) || new Date());

    let msg = `📜 *OFFICIAL LETTER COMMUNICATION*\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `🏢 *From:* ${firmName}\n`;
    msg += `👤 *To:* ${recipientName}\n`;
    if (letterNo) msg += `🔖 *Ref #:* ${letterNo}\n`;
    msg += `📅 *Date:* ${letterDate}\n`;
    msg += `📌 *Subject:* ${subject}\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    if (letter && letter.content) {
      const snippet = letter.content.length > 280 ? letter.content.substring(0, 280) + '...' : letter.content;
      msg += `📝 *Summary / Excerpt:*\n${snippet}\n\n`;
    }
    msg += `📎 *Official Signed Document attached as PDF.*\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `_Sent via ${firmName}_`;
    return msg;
  },

  formatPayslip(params = {}) {
    const { employee = {}, salary = {}, firm = {} } = (params || {});
    const firmName = (firm && firm.firmName) || 'Our Business';
    const empName = (employee && employee.name) || 'Employee';
    const month = (salary && (salary.month || salary.salaryMonth)) || BillsoftUtils.formatDate(new Date(), 'MMM YYYY');
    const netSalary = BillsoftWhatsApp._formatMoney(salary && (salary.netSalary || salary.netAmount || salary.amount || employee.basicSalary || 0));
    const grossSalary = BillsoftWhatsApp._formatMoney(salary && (salary.grossSalary || salary.basicSalary || employee.basicSalary || 0));
    const deductions = BillsoftWhatsApp._formatMoney(salary && (salary.deductions || salary.advancesDeducted || 0));

    let msg = `💼 *SALARY PAYSLIP ACKNOWLEDGEMENT*\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `🏢 *${firmName}*\n`;
    msg += `👤 *Employee:* ${empName}\n`;
    if (employee && employee.designation) msg += `🏷️ *Designation:* ${employee.designation}\n`;
    msg += `📅 *Month:* ${month}\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `💵 *Gross Salary:* ₹${grossSalary}\n`;
    if (salary && salary.deductions > 0) msg += `➖ *Deductions:* ₹${deductions}\n`;
    msg += `💰 *Net Disbursed:* ₹${netSalary}\n`;
    msg += `━━━━━━━━━━━━━━━━━━━━\n`;
    msg += `📎 *Official Detailed Payslip attached as PDF.*\n`;
    msg += `_Generated via ${firmName}_`;
    return msg;
  }
};

window.BillsoftWhatsApp = BillsoftWhatsApp;
BillsoftUtils.whatsapp = BillsoftWhatsApp;

// ─── BILLSOFT SMART OMNIBAR & SEARCH ENGINE ───
const BillsoftSearchEngine = {
  // State code mapping for GSTIN identification
  GST_STATES: {
    '01': 'Jammu & Kashmir', '02': 'Himachal Pradesh', '03': 'Punjab', '04': 'Chandigarh',
    '05': 'Uttarakhand', '06': 'Haryana', '07': 'Delhi', '08': 'Rajasthan',
    '09': 'Uttar Pradesh', '10': 'Bihar', '11': 'Sikkim', '12': 'Arunachal Pradesh',
    '13': 'Nagaland', '14': 'Manipur', '15': 'Mizoram', '16': 'Tripura',
    '17': 'Meghalaya', '18': 'Assam', '19': 'West Bengal', '20': 'Jharkhand',
    '21': 'Odisha', '22': 'Chhattisgarh', '23': 'Madhya Pradesh', '24': 'Gujarat',
    '26': 'Dadra & Nagar Haveli and Daman & Diu', '27': 'Maharashtra', '28': 'Andhra Pradesh',
    '29': 'Karnataka', '30': 'Goa', '31': 'Lakshadweep', '32': 'Kerala',
    '33': 'Tamil Nadu', '34': 'Puducherry', '35': 'Andaman & Nicobar Islands',
    '36': 'Telangana', '37': 'Andhra Pradesh (New)', '38': 'Ladakh'
  },

  ensureArray(val) {
    if (!val) return [];
    if (Array.isArray(val)) return val;
    if (Array.isArray(val.content)) return val.content;
    if (Array.isArray(val.data)) return val.data;
    if (Array.isArray(val.items)) return val.items;
    if (Array.isArray(val.invoices)) return val.invoices;
    if (Array.isArray(val.customers)) return val.customers;
    if (Array.isArray(val.products)) return val.products;
    if (Array.isArray(val.parties)) return val.parties;
    if (Array.isArray(val.employees)) return val.employees;
    return [];
  },

  // Helper to dispatch precision subtab navigation
  dispatchNavigate(detail) {
    if (!detail || !detail.page) return;
    window.__billsoftPendingNav = { ...detail, ts: Date.now() };
    window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail }));
    setTimeout(() => {
      window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail }));
    }, 40);
    setTimeout(() => {
      window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail }));
    }, 150);
  },

  // 1. Multilingual Action & Intent Dictionary with Precision Sub-Tab Targets
  ACTIONS: [
    {
      id: 'create_invoice',
      title: 'Create Tax Invoice',
      subtitle: 'Open new invoice creation workspace',
      category: 'actions',
      icon: '📄',
      badge: 'Billing',
      target: { page: 'invoices', subTab: 'create', quickDocType: 'invoice' },
      keywords: [
        // English
        'create invoice', 'new invoice', 'new bill', 'tax invoice', 'sales bill', 'generate bill', 'print invoice', 'make invoice', 'sales order', 'pos bill', 'cash memo', 'billing', 'sell goods', 'order bill', 'add invoice', 'invoice create', 'gst invoice', 'gst bill', 'retail bill', 'sale entry', 'counter bill', 'billing desk', 'make bill', 'start bill', 'new sale', 'quick bill',
        // Hindi / Hinglish
        'bill banao', 'naya bill', 'bill banana', 'invoice banao', 'bill banaye', 'bikri', 'bikri bill', 'saman becha', 'mal becha', 'bill print karna', 'bill nikalo', 'bill bhejo', 'bill katna', 'bill kaatna', 'naya invoice', 'becho', 'bikri invoice', 'pukka bill', 'pakka bill', 'gst parcha', 'bikri parcha', 'bill faadna', 'bill fado', 'bill kaato', 'invoice kato', 'parcha banao', 'sauda',
        // Marathi (Romanized)
        'bill banva', 'naveen bill', 'bill tayar kara', 'bikri bill', 'pavti banva', 'pavti', 'dukan bill', 'mal vikri', 'bill dya', 'bill kadha', 'invoice banva', 'pavti kadha', 'vikri pavti', 'dukanat bill', 'pakkhe bill', 'pakki pavti', 'chalan banva', 'vikri nod', 'mal vikla', 'vikri noond',
        // Typos & Phonetics
        'invoce', 'invice', 'invoyce', 'bil', 'biil', 'biill', 'billl', 'taxinvoice', 'crat invoice', 'bll', 'bil bano', 'bil bnao', 'naya bil', 'nvin bill', 'bil tayar', 'sale bil', 'crete invoice', 'invocie', 'invois', 'invoise', 'banaoo bill'
      ],
      action: (ctx) => {
        if (ctx.onQuickCreate) ctx.onQuickCreate('invoice');
        else BillsoftSearchEngine.dispatchNavigate({ page: 'invoices', subTab: 'create', quickDocType: 'invoice' });
      }
    },
    {
      id: 'create_quotation',
      title: 'Create Quotation / Estimate',
      subtitle: 'Prepare price proposal or draft estimate',
      category: 'actions',
      icon: '📝',
      badge: 'Estimates',
      target: { page: 'invoices', subTab: 'create', quickDocType: 'quotation' },
      keywords: [
        // English
        'create quotation', 'new quotation', 'new quote', 'estimate', 'cost estimate', 'price quote', 'rate sheet', 'proforma invoice', 'draft bill', 'price proposal', 'bidding', 'rates', 'add quote', 'create estimate', 'tentative bill', 'rough bill', 'quotation generate', 'send quotation',
        // Hindi / Hinglish
        'kaccha bill', 'kacha bill', 'kacha bil', 'estimate banao', 'rate batao', 'quotation bhejo', 'andaja bill', 'bhav batao', 'kimat quotation', 'quote banana', 'kacchi receipt', 'andaza bill', 'kaccha parcha', 'rate quotation', 'bhav list', 'kaccha hisab', 'estimate parcha', 'rate parcha',
        // Marathi (Romanized)
        'kacha bill', 'andaj patrak', 'andaje bill', 'quotation dya', 'dukan quote', 'dar patrak', 'kimat andaj', 'kacchi pavti', 'andajpatrak', 'bhav sanga', 'quote banva', 'dar suchi', 'bhav patrak', 'andajik bill', 'kaccha pavti', 'kache bill',
        // Typos
        'qoute', 'quot', 'quoat', 'estimat', 'estimet', 'estemate', 'kacha bil', 'qotation', 'qoutation', 'estymate', 'prforma', 'quatation', 'andaj patra', 'andajpatr', 'qutaion'
      ],
      action: (ctx) => {
        if (ctx.onQuickCreate) ctx.onQuickCreate('quotation');
        else BillsoftSearchEngine.dispatchNavigate({ page: 'invoices', subTab: 'create', quickDocType: 'quotation' });
      }
    },
    {
      id: 'view_invoices',
      title: 'All Invoices & Sales History',
      subtitle: 'Browse, filter, and export tax invoices',
      category: 'actions',
      icon: '📑',
      badge: 'Invoices',
      target: { page: 'invoices', subTab: 'invoices' },
      keywords: [
        'all invoices', 'view invoices', 'invoice list', 'sales history', 'past bills', 'invoices list', 'search bills', 'past sales', 'tax bills list',
        'purane bill', 'saare bill', 'bills dekho', 'bikri history', 'bikri list', 'pichle bill', 'saari bikri', 'bikri record',
        'sarva bill', 'puravath bill', 'bill yadi', 'magil bill', 'sagle bill', 'vikri yadi', 'vikri itihas',
        'invoce list', 'all bils', 'billist', 'invoces'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'invoices', subTab: 'invoices' });
      }
    },
    {
      id: 'view_quotations',
      title: 'All Quotations & Estimates',
      subtitle: 'Review estimates, pipeline & convert to invoices',
      category: 'actions',
      icon: '📝',
      badge: 'Estimates',
      target: { page: 'invoices', subTab: 'quotations' },
      keywords: [
        'all quotations', 'view quotations', 'estimates list', 'quotes list', 'all quotes', 'estimates history', 'proforma list',
        'saare estimate', 'kacche bill list', 'purane quote', 'quotation list', 'bhav yadi',
        'sarva quotation', 'andaj patrak yadi', 'kacche bill yadi',
        'qoute list', 'estiamtes list'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'invoices', subTab: 'quotations' });
      }
    },
    {
      id: 'view_returns',
      title: 'Sales Returns & Credit Notes',
      subtitle: 'Manage customer returns, refunds & credit notes',
      category: 'actions',
      icon: '↩️',
      badge: 'Returns',
      target: { page: 'invoices', subTab: 'returns' },
      keywords: [
        'sales return', 'credit note', 'returns list', 'customer return', 'refund invoice', 'returned goods', 'credit notes list',
        'mal wapas', 'bikri wapas', 'return bill', 'wapas aya saman', 'credit note banao', 'return maal',
        'mal parat', 'vikri parat', 'parat chalan', 'credit note kadha',
        'sal return', 'crdit note', 'salereturn'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'invoices', subTab: 'returns' });
      }
    },
    {
      id: 'customer_statements',
      title: 'Customer Statements & Khata Dues',
      subtitle: 'View ledger, outstanding debt & send payment reminders',
      category: 'actions',
      icon: '📊',
      badge: 'Ledger',
      target: { page: 'paperwork', tab: 'statements', statementMode: 'customer' },
      keywords: [
        // English
        'customer statement', 'customer ledger', 'customer dues', 'outstanding balance', 'pending balance', 'payment reminder', 'debt recovery', 'account statement', 'client dues', 'receivables', 'customer khata', 'hisab kitab', 'ledger balance', 'khata statement', 'party ledger', 'party khata', 'dues list', 'debtors list', 'unpaid balance', 'pending money', 'credit list', 'statement', 'statements',
        // Hindi / Hinglish
        'hisab', 'hisaab', 'hisab kitab', 'khata', 'grahak hisab', 'grahak ka hisab', 'udhaari', 'udhari', 'baki paisa', 'baki hisab', 'baaki', 'baki lena', 'grahak dues', 'customer reminder', 'khata bahi', 'len den', 'vasooli', 'vasuli reminder', 'udhar vasooli', 'kiske baki hai', 'kitna paisa lena hai', 'grahak ka baki', 'lena baki', 'udhari list', 'baki bahi', 'khata book', 'bahikhata', 'baki hishob',
        // Marathi (Romanized)
        'khatedar', 'grahak hishob', 'khate hishob', 'bakki', 'bakki shillak', 'baki rakkam', 'udhari khate', 'lekhajokha', 'jama kharch', 'hishob patrak', 'pavti hishob', 'grahak yadi', 'yene baki', 'udhari list', 'grahak baki', 'kiti paise yene ahet', 'yene rakkam', 'baki hishob', 'shillak baki', 'khate wahi', 'khata wahi',
        // Typos
        'statment', 'stetement', 'statemnt', 'hisabkitab', 'hisabb', 'udharii', 'udhari list', 'clint statement', 'ledgr', 'legder', 'baaki list', 'khata book', 'hisaab kitaab', 'hisab ktab', 'hisab kithab'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'paperwork', tab: 'statements', statementMode: 'customer' });
      }
    },
    {
      id: 'vendor_statements',
      title: 'Vendor Statements & Supplier Ledger',
      subtitle: 'Check vendor hisab, pending payables & purchase bills',
      category: 'actions',
      icon: '🏭',
      badge: 'Vendor Khata',
      target: { page: 'paperwork', tab: 'statements', statementMode: 'party' },
      keywords: [
        'vendor statement', 'supplier statement', 'party statement', 'vendor ledger', 'supplier ledger', 'payables', 'creditors list', 'money to pay', 'supplier dues', 'vendor balance', 'supplier hisab',
        'party ka hisab', 'supplier ka hisab', 'vyapari ka hisab', 'maal wale ka hisab', 'paisa dena hai', 'dena baki', 'kisko paisa dena hai', 'supplier khata', 'vendor baki', 'party hisab', 'party baki',
        'puravathadar hishob', 'party hishob', 'dene baki', 'vyapari baki', 'mal puravathadar hisab', 'kiti paise dene ahet', 'dene rakkam', 'party baki yadi',
        'suplier statment', 'vendr ledger', 'puravatha hisab', 'party statment'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'paperwork', tab: 'statements', statementMode: 'party' });
      }
    },
    {
      id: 'firm_statements',
      title: 'Firm Journal & Master Statement',
      subtitle: 'Complete chronological record of all purchases, sales & payments',
      category: 'actions',
      icon: '📑',
      badge: 'Audit Journal',
      target: { page: 'paperwork', tab: 'statements', statementMode: 'firm' },
      keywords: [
        'firm statement', 'company ledger', 'journal', 'all transactions', 'audit statement', 'general ledger', 'business statement', 'cash book', 'day book', 'daily journal', 'transaction history',
        'dukaan ka hisab', 'firm ka khata', 'saara hisab', 'saari entry', 'rojnamcha', 'roznamcha', 'din bhar ka hisab', 'aaj ka transaction', 'pura hisab',
        'dukanacha hishob', 'sarva transactions', 'rojkird', 'rojvahi', 'rojchya nondi', 'sampurna hishob', 'karkhanacha hishob',
        'jornal', 'rozkird', 'rojnamchaa', 'daybook'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'paperwork', tab: 'statements', statementMode: 'firm' });
      }
    },
    {
      id: 'create_po',
      title: 'Create Purchase Order (PO)',
      subtitle: 'Order stock & raw materials from suppliers',
      category: 'actions',
      icon: '📋',
      badge: 'Procurement',
      target: { page: 'paperwork', tab: 'orders' },
      keywords: [
        // English
        'create purchase order', 'new purchase order', 'create po', 'new po', 'order goods', 'buy inventory', 'procurement', 'order raw material', 'purchase voucher', 'raise po', 'supplier order', 'stock order', 'buy stock', 'order mal', 'purchase requisition', 'purchase orders',
        // Hindi / Hinglish
        'po banao', 'maal mangwana', 'mal khareedna', 'kharidi order', 'order bhejo', 'supplier order', 'naya purchase order', 'saman khareed', 'samaan order', 'kharidi parcha', 'mal order karo', 'stock mangvao', 'order lagao', 'supplier ko order',
        // Marathi (Romanized)
        'po banva', 'kharedi order', 'mal kharedi', 'kharedi nod', 'dukan saman order', 'puravatha order', 'saman magva', 'naveen kharedi', 'order dya', 'mal magva', 'kharedi pavti', 'saman order kara',
        // Typos
        'purchas', 'purhase', 'prchase', 'purchse', 'purcahse', 'puchase order', 'purches', 'po ordr', 'po order', 'kharedi ordr', 'purchese'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'paperwork', tab: 'orders' });
      }
    },
    {
      id: 'write_letter',
      title: 'Write Official Business Letter',
      subtitle: 'Draft official notices, authorizations, certificates & letters',
      category: 'actions',
      icon: '✉️',
      badge: 'Letters',
      target: { page: 'paperwork', tab: 'letters' },
      keywords: [
        'letter', 'business letter', 'official letter', 'letterpad', 'letterhead', 'memo', 'notice', 'declaration', 'circular', 'certificate', 'authorization letter', 'noc', 'recommendation', 'write letter',
        'letter banao', 'chithi', 'patra', 'letterhead print', 'official letter', 'notice bhejo', 'patra likho', 'dukan ka letter', 'certificate banao', 'chithi likho',
        'patra', 'kagadpatra', 'dukan patra', 'official patra', 'kagad', 'patravyavahar', 'patralekhan', 'notice patra', 'dakhal patra', 'letterhead tayar kara', 'patra liha',
        'letr', 'leter', 'lettar', 'letrhead', 'letrpad', 'offical letter', 'lettr', 'patrr', 'leterhead'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'paperwork', tab: 'letters' });
      }
    },
    {
      id: 'hr_payroll',
      title: 'Monthly Payroll & Send Payslips',
      subtitle: 'Calculate staff salaries, advances & send WhatsApp payslips',
      category: 'actions',
      icon: '🧑‍💼',
      badge: 'HR / Payroll',
      target: { page: 'hr', hrTab: 'payroll' },
      keywords: [
        // English
        'payslip', 'send payslip', 'pay slip', 'salary slip', 'salary statement', 'salary voucher', 'wage slip', 'compensation slip', 'salary sheet', 'staff payment', 'salary download', 'employee salary', 'payroll', 'monthly salary', 'salary calculation', 'staff payslips', 'whatsapp payslip',
        // Hindi / Hinglish
        'salary slip', 'payslip bhejo', 'tankhah', 'tankha', 'pagar', 'pagar slip', 'salary do', 'kamdar salary', 'staff pagar', 'naukar ki salary', 'karmachari pagar', 'pagar chi pavti', 'pagar bhejo', 'salary certificate', 'salary nikaal', 'pagar book', 'tankha slip', 'pagar banao', 'tankhwa do', 'salary kitni bani', 'naukar pagar',
        // Marathi (Romanized)
        'pagar slip', 'pagar patrak', 'pagar dya', 'pagar chi chithi', 'kamgar pagar', 'karmachari yadi', 'naukar pagar', 'pagar patra', 'pagar vadhav', 'pagar receipt', 'mahinyacha pagar', 'kamgar nondani', 'pagar vatan', 'kamgar salary', 'pagar hishob', 'pagar pathva',
        // Typos
        'payslipp', 'slary', 'slary slip', 'salry', 'salery', 'selery', 'tankhwa', 'pagarr', 'pagr', 'tankha slip', 'employe', 'emplyee', 'pay roll', 'payrl', 'payslp', 'salari', 'salry slip'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'hr', hrTab: 'payroll' });
      }
    },
    {
      id: 'hr_attendance',
      title: 'Staff Attendance & Punch Log',
      subtitle: 'Mark daily attendance, manage leaves & absent days',
      category: 'actions',
      icon: '📅',
      badge: 'Attendance',
      target: { page: 'hr', hrTab: 'attendance' },
      keywords: [
        'staff attendance', 'attendance sheet', 'mark attendance', 'daily attendance', 'staff punch', 'absent list', 'present staff', 'leave tracker', 'attendance record', 'who is absent', 'who is present', 'attendance',
        'hazari', 'haziri', 'attendance lagao', 'staff hazari', 'kon nahi aaya', 'kon aaya hai', 'chutti list', 'aaj kon present hai', 'hazari lagana', 'hazari book', 'karmachari hazari',
        'hajeri', 'kamgar hajeri', 'hajeri patrak', 'aaj kon aale', 'aaj kon gairhajir', 'hajeri nond', 'gairhajeri', 'hajeri book', 'kamgar attendance',
        'attandance', 'atendance', 'attendence', 'hazari', 'hajiri', 'atendanc', 'punching'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'hr', hrTab: 'attendance' });
      }
    },
    {
      id: 'hr_advances',
      title: 'Staff Salary Advances & Deductions',
      subtitle: 'Record employee salary advance loans and deduction history',
      category: 'actions',
      icon: '💰',
      badge: 'Advances',
      target: { page: 'hr', hrTab: 'advances' },
      keywords: [
        'salary advance', 'employee advance', 'staff advance', 'loan to staff', 'advance payment', 'advance salary', 'advance deduction', 'advances',
        'advance diya', 'advance pagar', 'salary advance', 'staff ko advance', 'advance entry', 'advance katauti', 'advance hisab',
        'advance dila', 'pagar advance', 'kamgar advance', 'advance rakkam', 'advance kapat',
        'advanc', 'advanse', 'advans'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'hr', hrTab: 'advances' });
      }
    },
    {
      id: 'add_customer',
      title: 'Add New Customer',
      subtitle: 'Register customer with phone, GSTIN & opening balance',
      category: 'actions',
      icon: '👤',
      badge: 'Customers',
      target: { page: 'firm', tab: 'customers' },
      keywords: [
        'add customer', 'new customer', 'create customer', 'add client', 'new buyer', 'register customer', 'customer contact', 'customer directory', 'customers', 'all customers',
        'grahak jodo', 'naya grahak', 'customer banao', 'customer add karo', 'naya client', 'khata kholo', 'naya khata', 'party jodo',
        'naveen grahak', 'grahak joda', 'grahak nondani', 'naveen khatedar', 'khate suru kara', 'grahak nond', 'sarva grahak',
        'cutomer', 'custmer', 'custmor', 'custemer', 'customr', 'ad customer', 'new cust', 'grahak add'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'customers' });
        window.dispatchEvent(new CustomEvent('billsoft:open-add-customer'));
      }
    },
    {
      id: 'add_product',
      title: 'Add New Product / Inventory Item',
      subtitle: 'Add item name, SKU, price, HSN code & opening stock',
      category: 'actions',
      icon: '📦',
      badge: 'Catalog',
      target: { page: 'firm', tab: 'inventory' },
      keywords: [
        'add product', 'new product', 'create item', 'add item', 'new stock', 'inventory add', 'add goods', 'new sku', 'barcode item', 'pricing', 'add item to catalog', 'inventory', 'stock list', 'all products', 'item list',
        'item jodo', 'naya item', 'saman jodo', 'naya saman', 'naya maal', 'maal add karo', 'vastu jodo', 'rate list entry', 'item banao', 'stock dekho',
        'naveen vastu', 'vastu joda', 'saman nondani', 'mal joda', 'naveen mal', 'bhandar nond', 'dar suchi', 'sarva vastu', 'shillak mal',
        'prduct', 'prodct', 'itm', 'itms', 'stck', 'inventry', 'invntory', 'stok', 'naya itam', 'naveen itam'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'inventory' });
        window.dispatchEvent(new CustomEvent('billsoft:open-add-product'));
      }
    },
    {
      id: 'add_vendor',
      title: 'Add New Vendor / Supplier',
      subtitle: 'Add wholesale supplier with contact & opening balance',
      category: 'actions',
      icon: '🏭',
      badge: 'Parties',
      target: { page: 'firm', tab: 'parties' },
      keywords: [
        'add vendor', 'new vendor', 'add party', 'new party', 'add supplier', 'new supplier', 'wholesaler', 'distributor', 'register vendor', 'all vendors', 'suppliers list',
        'party jodo', 'naya vyapari', 'supplier jodo', 'naya supplier', 'naya party', 'maal supplier', 'party banana', 'vyapari yadi',
        'naveen party', 'puravathadar joda', 'vyapari joda', 'naveen puravathadar', 'party nondani', 'sarva vyapari',
        'suplier', 'supplr', 'vendr', 'vendur', 'prty', 'puravatha'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'parties' });
        window.dispatchEvent(new CustomEvent('billsoft:open-add-party'));
      }
    },
    {
      id: 'record_expense',
      title: 'Record Daily Expense',
      subtitle: 'Log office expenses, rent, bills, fuel & petty cash',
      category: 'actions',
      icon: '💸',
      badge: 'Planner',
      target: { page: 'planner', plannerTab: 'expenses' },
      keywords: [
        'record expense', 'add expense', 'new expense', 'log expense', 'spent', 'cost', 'daily expense', 'petty cash', 'tea expense', 'rent', 'electricity bill', 'payment out', 'cash outflow', 'office expenses', 'expenses list', 'view expenses', 'chai kharcha', 'fuel expense', 'travel expense', 'expenses',
        'kharcha', 'kharch', 'kharcha jodo', 'paisa diya', 'roz ka kharcha', 'dukaan kharcha', 'rent diya', 'bill bhara', 'office kharch', 'petrol kharcha', 'kiraya', 'kharche ka hisab', 'kharcha entry', 'aaj ka kharcha', 'paisa gaya', 'chhota kharcha',
        'kharch', 'rozacha kharch', 'dukan kharch', 'bhade dile', 'bijli bill', 'kharchachi nond', 'shillak kharch', 'rokh kharch', 'mahinyacha kharch', 'kharch entry', 'naveen kharch', 'aajcha kharch', 'chaha kharch', 'petrol kharch',
        'expnse', 'expens', 'expenc', 'exps', 'khrcha', 'spnd', 'outflo', 'paty cash', 'expence', 'kharchaa'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'planner', plannerTab: 'expenses' });
        window.dispatchEvent(new CustomEvent('billsoft:open-add-expense'));
      }
    },
    {
      id: 'add_note',
      title: 'Add Sticky Note / Task',
      subtitle: 'Save a quick reminder, task or reminder note to planner',
      category: 'actions',
      icon: '📌',
      badge: 'Planner',
      target: { page: 'planner', plannerTab: 'board' },
      keywords: [
        'add note', 'new note', 'create task', 'add reminder', 'todo', 'to do', 'task list', 'sticky note', 'planner board', 'reminder list',
        'note banao', 'task banao', 'yaad rakho', 'reminder lagao', 'kaam note karo', 'parcha note',
        'kamachi nond', 'tippan liha', 'aathvan theva', 'task nond', 'kam yadi',
        'remider', 'remindr', 'stiky note', 'to do list'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'planner', plannerTab: 'board' });
        window.dispatchEvent(new CustomEvent('billsoft:open-add-note'));
      }
    },
    {
      id: 'backup_data',
      title: 'Database Backup & Restore',
      subtitle: 'Export or restore database snapshots for offline safekeeping',
      category: 'actions',
      icon: '💾',
      badge: 'Security',
      target: { page: 'settings', tab: 'backup' },
      keywords: [
        'backup', 'download backup', 'export data', 'save database', 'database backup', 'full backup', 'data backup', 'restore data',
        'backup lo', 'data save karo', 'backup download', 'data bacha ke rakho', 'backup file',
        'backup gya', 'data theva', 'surakshit theva',
        'bakup', 'backp', 'bakcup', 'databackup'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'settings', tab: 'backup' });
      }
    },
    {
      id: 'software_updates',
      title: 'Check Software Updates',
      subtitle: 'View release notes and install latest RupeeCRM updates',
      category: 'actions',
      icon: '🔄',
      badge: 'Updates',
      target: { page: 'settings', tab: 'updates' },
      keywords: [
        'update', 'check update', 'software update', 'version', 'latest version', 'system update', 'upgrade app',
        'update karo', 'naya version', 'software update karo',
        'naveen version', 'update kara',
        'updat', 'softwere'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'settings', tab: 'updates' });
      }
    },
    {
      id: 'security_settings',
      title: 'Security & App Lock PIN',
      subtitle: 'Configure passcode, screen timeout and access controls',
      category: 'actions',
      icon: '🔒',
      badge: 'Security',
      target: { page: 'settings', tab: 'security' },
      keywords: [
        'security', 'pin lock', 'app lock', 'password', 'change pin', 'screen timeout', 'access control',
        'password badlo', 'pin badlo', 'lock lagao', 'suraksha',
        'suraksha setting', 'pin badla',
        'securty', 'pasword'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'settings', tab: 'security' });
      }
    },
    {
      id: 'toggle_theme',
      title: 'Toggle Dark / Light Theme',
      subtitle: 'Switch display contrast mode',
      category: 'actions',
      icon: '🌓',
      badge: 'Display',
      keywords: [
        'toggle theme', 'dark mode', 'light mode', 'night mode', 'dark theme', 'black mode', 'color theme',
        'dark karo', 'light karo', 'raat mode', 'kaala mode',
        'dark mode lav', 'light mode lav', 'theme badla',
        'darck mode', 'ligt mode', 'nightmode'
      ],
      action: () => {
        const curr = document.documentElement.getAttribute('data-theme') || 'dark';
        const next = curr === 'dark' ? 'light' : 'dark';
        document.documentElement.setAttribute('data-theme', next);
        localStorage.setItem('theme', next);
        window.dispatchEvent(new CustomEvent('theme-changed', { detail: next }));
        if (window.showToast) window.showToast(`Switched to ${next} mode`, 'info');
      }
    },
    {
      id: 'lock_workspace',
      title: 'Lock Workspace Now',
      subtitle: 'Require PIN / password to resume access',
      category: 'actions',
      icon: '🔒',
      badge: 'Security',
      keywords: [
        'lock workspace', 'lock screen', 'lock app', 'pin lock', 'lock session', 'protect screen',
        'screen lock karo', 'lock lagao', 'band karo',
        'screen band kara', 'lock kara',
        'lok screen', 'loc app', 'pasword lock'
      ],
      action: () => {
        window.dispatchEvent(new CustomEvent('billsoft:lock-session'));
      }
    },
    {
      id: 'sync_data',
      title: 'Sync Database & Refresh (Alt + R)',
      subtitle: 'Re-sync all data with the local server',
      category: 'actions',
      icon: '🔄',
      badge: 'Sync',
      keywords: [
        'sync data', 'refresh app', 'refresh data', 'reload data', 'sync database', 'in-app refresh',
        'sync karo', 'refresh karo', 'data update karo',
        'sync kara', 'refresh kara', 'data punha aana',
        'synk', 'refrsh', 'syncing'
      ],
      action: (ctx) => {
        if (ctx.onRefresh) ctx.onRefresh();
        else window.dispatchEvent(new CustomEvent('billsoft:app-refresh'));
      }
    },
    {
      id: 'tax_reports',
      title: 'Tax & GST Reports / Sales Registers',
      subtitle: 'Review monthly sales registers, GSTR-1, GSTR-3B & Tax Breakdown',
      category: 'actions',
      icon: '📊',
      badge: 'Tax / Reports',
      target: { page: 'invoices', subTab: 'invoices' },
      keywords: [
        'gst report', 'tax report', 'sales report', 'gstr 1', 'gstr 3b', 'gstr1', 'gstr3b', 'gst summary', 'tax summary', 'sales register', 'ca report', 'audit report', 'hsn summary', 'tax register', 'tax filings',
        'gst hisab', 'tax hishob', 'gst file', 'tax report nikalo', 'gst parcha', 'tax parcha',
        'kar patrak', 'gst patrak', 'vikri patrak'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'invoices', subTab: 'invoices' });
      }
    },
    {
      id: 'open_settings',
      title: 'Open Settings & Business Profile',
      subtitle: 'Configure firm details, invoice numbering, taxes & printers',
      category: 'actions',
      icon: '⚙️',
      badge: 'Settings',
      target: { page: 'settings', tab: 'general' },
      keywords: [
        'settings', 'preferences', 'firm details', 'company settings', 'tax settings', 'printer settings', 'configuration',
        'setting badlo', 'company info', 'tax setting', 'printer setting',
        'setting kara', 'dukan mahiti', 'kar setting',
        'setings', 'settngs', 'config'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'settings', tab: 'general' });
      }
    }
  ],

  // 2. Indian Currency Amount in Words Converter
  numberToWordsIndian(n) {
    if (n == null || isNaN(n)) return '';
    let num = parseFloat(n);
    if (num === 0) return 'Zero Rupees Only';
    if (num < 0) return 'Minus ' + this.numberToWordsIndian(Math.abs(num));

    const a = ['', 'One ', 'Two ', 'Three ', 'Four ', 'Five ', 'Six ', 'Seven ', 'Eight ', 'Nine ', 'Ten ', 'Eleven ', 'Twelve ', 'Thirteen ', 'Fourteen ', 'Fifteen ', 'Sixteen ', 'Seventeen ', 'Eighteen ', 'Nineteen '];
    const b = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];

    const numToWordsLessThanThousand = (val) => {
      let str = '';
      if (val > 99) {
        str += a[Math.floor(val / 100)] + 'Hundred ';
        val %= 100;
      }
      if (val > 19) {
        str += b[Math.floor(val / 10)] + (val % 10 ? ' ' + a[val % 10] : ' ');
      } else if (val > 0) {
        str += a[val];
      }
      return str;
    };

    const wholePart = Math.floor(num);
    const decimalPart = Math.round((num - wholePart) * 100);

    let crore = Math.floor(wholePart / 10000000);
    let remCrore = wholePart % 10000000;
    let lakh = Math.floor(remCrore / 100000);
    let remLakh = remCrore % 100000;
    let thousand = Math.floor(remLakh / 1000);
    let remThousand = remLakh % 1000;

    let res = '';
    if (crore > 0) res += numToWordsLessThanThousand(crore) + 'Crore ';
    if (lakh > 0) res += numToWordsLessThanThousand(lakh) + 'Lakh ';
    if (thousand > 0) res += numToWordsLessThanThousand(thousand) + 'Thousand ';
    if (remThousand > 0) res += numToWordsLessThanThousand(remThousand);

    res = res.trim() + ' Rupees';

    if (decimalPart > 0) {
      res += ' and ' + numToWordsLessThanThousand(decimalPart).trim() + ' Paise';
    }

    return res + ' Only';
  },

  // 3. GSTIN Checksum & Structure Validator
  validateGstin(input) {
    if (!input) return null;
    const clean = input.toUpperCase().trim().replace(/[^A-Z0-9]/g, '');
    if (clean.length !== 15) return null;

    const gstinRegex = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/;
    const isValidFormat = gstinRegex.test(clean);
    const stateCode = clean.substring(0, 2);
    const pan = clean.substring(2, 12);
    const entityNum = clean.charAt(12);
    const stateName = this.GST_STATES[stateCode] || 'Unknown State';

    return {
      gstin: clean,
      isValid: isValidFormat,
      stateCode,
      stateName,
      pan,
      entityNumber: entityNum
    };
  },

  // 4. Safe Math Evaluator (No eval)
  evaluateMath(expr) {
    if (!expr || typeof expr !== 'string') return null;
    let clean = expr.replace(/,/g, '').trim();

    // Check if it's an equation
    const mathChars = /^[\d\s\+\-\*\/\.\(\)\%]+$/;
    if (!mathChars.test(clean)) return null;

    // Must have at least one operator
    if (!/[\+\-\*\/]/.test(clean)) return null;

    try {
      // Replace % with /100
      let sanitized = clean.replace(/(\d+(\.\d+)?)%/g, '($1/100)');

      // Evaluate safely using Function constructor with no scope access
      const calc = new Function(`return (${sanitized});`)();
      if (typeof calc === 'number' && !isNaN(calc) && isFinite(calc)) {
        return {
          expression: expr,
          result: calc,
          formatted: BillsoftUtils.formatCurrency(calc)
        };
      }
    } catch (e) { }
    return null;
  },

  // 5. Token Fuzzy Matching (Levenshtein Distance)
  levenshtein(a, b) {
    if (a === b) return 0;
    if (!a.length) return b.length;
    if (!b.length) return a.length;

    const matrix = [];
    for (let i = 0; i <= b.length; i++) matrix[i] = [i];
    for (let j = 0; j <= a.length; j++) matrix[0][j] = j;

    for (let i = 1; i <= b.length; i++) {
      for (let j = 1; j <= a.length; j++) {
        if (b.charAt(i - 1) === a.charAt(j - 1)) {
          matrix[i][j] = matrix[i - 1][j - 1];
        } else {
          matrix[i][j] = Math.min(
            matrix[i - 1][j - 1] + 1, // substitution
            matrix[i][j - 1] + 1,     // insertion
            matrix[i - 1][j] + 1      // deletion
          );
        }
      }
    }
    return matrix[b.length][a.length];
  },

  fuzzyMatch(query, target) {
    if (!query || !target) return false;
    const q = query.toLowerCase().trim();
    const t = target.toLowerCase().trim();

    if (t.includes(q)) return { match: true, score: 100, exact: true };

    const qTokens = q.split(/\s+/).filter(Boolean);
    const tTokens = t.split(/\s+/).filter(Boolean);

    let totalScore = 0;
    for (const qTok of qTokens) {
      let bestTokenScore = 0;
      for (const tTok of tTokens) {
        if (tTok.startsWith(qTok) || tTok.includes(qTok)) {
          bestTokenScore = Math.max(bestTokenScore, 90);
          continue;
        }
        if (qTok.length >= 3) {
          const dist = this.levenshtein(qTok, tTok);
          const maxLen = Math.max(qTok.length, tTok.length);
          const similarity = (1 - dist / maxLen) * 100;
          if (similarity >= 65) {
            bestTokenScore = Math.max(bestTokenScore, similarity);
          }
        }
      }
      totalScore += bestTokenScore;
    }

    const avgScore = totalScore / qTokens.length;
    return avgScore >= 60 ? { match: true, score: avgScore, exact: false } : { match: false, score: 0, exact: false };
  },

  // 6. "Did You Mean?" Suggestions Builder
  getSuggestions(query) {
    if (!query || query.length < 3) return [];
    const q = query.toLowerCase().trim();
    const suggestions = [];

    for (const act of this.ACTIONS) {
      for (const kw of act.keywords) {
        if (kw === q) continue; // exact matches don't need suggestion
        const dist = this.levenshtein(q, kw);
        if (dist <= 2 && q.length >= 4) {
          if (!suggestions.find(s => s.id === act.id)) {
            suggestions.push({
              id: act.id,
              action: act,
              suggestedKeyword: kw,
              title: act.title,
              icon: act.icon
            });
          }
          break;
        }
      }
    }
    return suggestions.slice(0, 3);
  },

  // ─── 7. SMART IN-PLACE ASSISTANT NLP PARSERS ───

  // A. Smart Inline Expense Parser ("kharcha 120 chai nashta", "expense 500 petrol", "kharch 2500 office rent")
  parseInlineExpense(raw) {
    const q = raw.trim();
    let m = q.match(/^(?:kharcha|expense|kharch|spent|petty cash)\s+(\d+(?:\.\d+)?)\s*(?:(?:\b(?:for|on|pe|cha|chya|ka|ke|ki|ko)\b)\s*(.+)|(.+))?$/i);
    if (!m) {
      m = q.match(/^(?:kharcha|expense|kharch)\s+(.+?)\s+(\d+(?:\.\d+)?)$/i);
      if (m) {
        return {
          amount: parseFloat(m[2]),
          title: (m[1] || 'Daily Expense').trim()
        };
      }
    } else {
      const title = (m[2] || m[3] || 'Daily Expense').trim();
      return {
        amount: parseFloat(m[1]),
        title: title || 'Daily Expense'
      };
    }
    return null;
  },

  // B. Smart Inline Sticky Note Parser ("todo call sharma ji", "note check gst returns", "task dispatch order")
  parseInlineNote(raw) {
    const q = raw.trim();
    const m = q.match(/^(?:todo|note|task|remind|yaad|aathvan|tippan)\s+(.+)$/i);
    if (m && m[1] && m[1].trim().length >= 3) {
      return {
        title: m[1].trim()
      };
    }
    return null;
  },

  // C. Smart Quick Customer Registration Parser ("customer Ramesh Patil 9822334455 Kolhapur", "grahak Vijay 9811223344")
  parseInlineCustomer(raw) {
    const q = raw.trim();
    const m = q.match(/^(?:customer|grahak|client)\s+([A-Za-z\s]+?)\s+(\d{10})(?:\s+(.+))?$/i);
    if (m) {
      return {
        name: m[1].trim(),
        phone: m[2].trim(),
        city: (m[3] || '').trim()
      };
    }
    return null;
  },

  // D. Smart WhatsApp Dues Reminder Parser ("remind rahul", "dues mahesh", "hisab sachin")
  parseInlineDuesReminder(raw, customers = [], firm = {}) {
    const q = raw.trim();
    const m = q.match(/^(?:remind|dues|hisab|hisaab|udhari|baki|reminder)\s+([A-Za-z\s]+)$/i);
    if (m) {
      const searchName = m[1].trim().toLowerCase();
      const matchedCust = customers.find(c => c && c.name && c.name.toLowerCase().includes(searchName));
      if (matchedCust) {
        const bal = parseFloat(matchedCust.balance || matchedCust.openingBalance || 0);
        return {
          customer: matchedCust,
          balance: bal,
          name: matchedCust.name,
          phone: matchedCust.phone || ''
        };
      }
    }
    return null;
  },

  // E. Smart Business Analytics Snapshot ("today sales", "total udhari", "low stock")
  parseBusinessMetrics(raw, invoices = [], customers = [], products = []) {
    const q = raw.trim().toLowerCase();

    // 1. Today's Sales Snapshot
    if (/^(?:today(?:'s)?\s+(?:sales?|revenue|income|collection|billing)|aaj\s+(?:ka\s+)?(?:sale|dhanda|bikri|hisab)|aajchi\s+(?:vikri|sale|dhanda))$/i.test(q)) {
      const todayStr = new Date().toISOString().slice(0, 10);
      const todayInvoices = invoices.filter(inv => {
        const d = (inv.invoiceDate || inv.createdAt || '').slice(0, 10);
        return d === todayStr;
      });
      const totalAmount = todayInvoices.reduce((sum, inv) => sum + (parseFloat(inv.netTotal || inv.totalAmount || 0)), 0);
      const paidAmount = todayInvoices.reduce((sum, inv) => sum + (parseFloat(inv.paidAmount || (inv.status === 'PAID' ? (inv.netTotal || inv.totalAmount || 0) : 0))), 0);
      const pendingAmount = Math.max(0, totalAmount - paidAmount);

      return {
        type: 'today_sales',
        title: `📊 Today's Sales: ${BillsoftUtils.formatCurrency(totalAmount)}`,
        subtitle: `${todayInvoices.length} Bills Generated • ${BillsoftUtils.formatCurrency(paidAmount)} Collected • ${BillsoftUtils.formatCurrency(pendingAmount)} Pending`,
        totalAmount,
        billsCount: todayInvoices.length,
        todayInvoices
      };
    }

    // 2. Total Receivables / Customer Dues
    if (/^(?:total\s+(?:dues|receivables|udhari|outstanding)|pending\s+dues|baki\s+lena|yene\s+baki|saari\s+udhari)$/i.test(q)) {
      const debtors = customers.filter(c => parseFloat(c.balance || c.openingBalance || 0) > 0);
      const totalDues = debtors.reduce((sum, c) => sum + parseFloat(c.balance || c.openingBalance || 0), 0);

      return {
        type: 'total_dues',
        title: `💰 Total Receivables: ${BillsoftUtils.formatCurrency(totalDues)}`,
        subtitle: `Pending across ${debtors.length} Customers with outstanding dues`,
        totalDues,
        debtorsCount: debtors.length
      };
    }

    // 3. Low Stock / Out of Stock
    if (/^(?:low\s+stock|out\s+of\s+stock|khatam\s+saman|stock\s+alert|kam\s+stock|shillak\s+sampali)$/i.test(q)) {
      const lowStockItems = products.filter(p => {
        const stock = parseFloat(p.stock || p.openingStock || 0);
        const minStock = parseFloat(p.minStockLevel || p.reorderLevel || 5);
        return stock <= minStock;
      });

      return {
        type: 'low_stock',
        title: `⚠️ ${lowStockItems.length} Products Low / Out of Stock`,
        subtitle: lowStockItems.length > 0 ? `Critical: ${lowStockItems.slice(0, 3).map(p => `${p.name} (${p.stock || 0})`).join(', ')}${lowStockItems.length > 3 ? '...' : ''}` : 'All products have healthy inventory levels',
        items: lowStockItems
      };
    }

    return null;
  },

  // F. Cashier Change Calculator ("change for 2000 bill 1435", "baki chutta 500 bill 120")
  parseCashChange(raw) {
    const q = raw.trim();
    const m = q.match(/^(?:change\s+(?:for\s+)?|baki\s+chutta\s+)(\d+(?:\.\d+)?)\s+(?:bill|for)\s+(\d+(?:\.\d+)?)$/i) ||
      q.match(/^(?:change|baki)\s+(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)$/i);
    if (m) {
      const tendered = parseFloat(m[1]);
      const bill = parseFloat(m[2]);
      if (tendered >= bill) {
        const change = tendered - bill;
        let rem = change;
        const denoms = [500, 200, 100, 50, 20, 10, 5, 2, 1];
        const breakdown = [];
        for (const d of denoms) {
          const count = Math.floor(rem / d);
          if (count > 0) {
            breakdown.push(`${count}x₹${d}`);
            rem %= d;
          }
        }
        return {
          tendered,
          bill,
          change,
          breakdownStr: breakdown.join(', ') || 'Exact Amount'
        };
      }
    }
    return null;
  },

  // G. Bill Splitter ("split 4500 by 4", "vibhagani 3000 madhe 3")
  parseBillSplit(raw) {
    const q = raw.trim();
    const m = q.match(/^(?:split|divide|vibhagani)\s+(\d+(?:\.\d+)?)\s+(?:by|in|madhe|me)\s+(\d+)$/i);
    if (m) {
      const amount = parseFloat(m[1]);
      const people = parseInt(m[2], 10);
      if (amount > 0 && people > 0) {
        const perPerson = amount / people;
        return {
          amount,
          people,
          perPerson: Math.round(perPerson * 100) / 100
        };
      }
    }
    return null;
  },

  // ─── 8. COMPREHENSIVE QUERY PARSER & SEARCH AGGREGATOR ───
  search(query, ctx = {}) {
    const raw = (query || '').trim();
    const q = raw.toLowerCase();
    const results = {
      smartTasks: [],
      utilities: [],
      actions: [],
      customers: [],
      invoices: [],
      products: [],
      parties: [],
      staff: [],
      suggestions: []
    };

    if (!raw) return results;

    const customers = this.ensureArray(ctx.customers);
    const invoices = this.ensureArray(ctx.invoices);
    const products = this.ensureArray(ctx.products);
    const parties = this.ensureArray(ctx.parties);
    const staff = this.ensureArray(ctx.staff);

    // ─────────────────────────────────────────────────────────────
    // A. SMART INLINE TASK EXECUTORS
    // ─────────────────────────────────────────────────────────────

    // 1. Smart Expense Logger
    const inlineExpense = this.parseInlineExpense(raw);
    if (inlineExpense && inlineExpense.amount > 0) {
      results.smartTasks.push({
        type: 'smart_expense',
        title: `💸 Record Expense: ${BillsoftUtils.formatCurrency(inlineExpense.amount)}`,
        subtitle: `Title: "${inlineExpense.title}" • Category: General • Click or press Enter to save`,
        icon: '💸',
        badge: '⚡ Quick Save',
        payload: inlineExpense,
        action: async () => {
          try {
            if (API.expenses && API.expenses.create) {
              const today = new Date().toISOString().slice(0, 10);
              await API.expenses.create({
                title: inlineExpense.title,
                amount: inlineExpense.amount,
                expenseDate: today,
                category: 'General'
              });
              if (window.showToast) window.showToast(`✓ Expense of ₹${inlineExpense.amount} recorded successfully!`, 'success');
              window.dispatchEvent(new CustomEvent('billsoft:app-refresh'));
            }
          } catch (e) {
            if (window.showToast) window.showToast('Failed to record expense: ' + (e.message || 'Error'), 'error');
          }
        }
      });
    }

    // 2. Smart Sticky Note Logger
    const inlineNote = this.parseInlineNote(raw);
    if (inlineNote && inlineNote.title) {
      results.smartTasks.push({
        type: 'smart_note',
        title: `📌 Save Sticky Note: "${inlineNote.title}"`,
        subtitle: 'Save to Planner Board • Click or press Enter to add note',
        icon: '📌',
        badge: '⚡ Quick Note',
        payload: inlineNote,
        action: async () => {
          try {
            if (API.notes && API.notes.create) {
              await API.notes.create({
                title: inlineNote.title,
                color: 'yellow'
              });
              if (window.showToast) window.showToast(`✓ Sticky note added to Planner!`, 'success');
              window.dispatchEvent(new CustomEvent('billsoft:app-refresh'));
            }
          } catch (e) {
            if (window.showToast) window.showToast('Failed to save note: ' + (e.message || 'Error'), 'error');
          }
        }
      });
    }

    // 3. Smart Quick Customer Registration
    const inlineCust = this.parseInlineCustomer(raw);
    if (inlineCust && inlineCust.name && inlineCust.phone) {
      results.smartTasks.push({
        type: 'smart_customer',
        title: `👤 Register Customer: ${inlineCust.name} (📞 ${inlineCust.phone})`,
        subtitle: `${inlineCust.city ? 'City: ' + inlineCust.city + ' • ' : ''}Click to add to customer directory immediately`,
        icon: '👤',
        badge: '⚡ Quick Register',
        payload: inlineCust,
        action: async () => {
          try {
            if (API.customers && API.customers.create) {
              await API.customers.create({
                name: inlineCust.name,
                phone: inlineCust.phone,
                city: inlineCust.city || '',
                openingBalance: 0
              });
              if (window.showToast) window.showToast(`✓ Customer ${inlineCust.name} registered successfully!`, 'success');
              window.dispatchEvent(new CustomEvent('billsoft:app-refresh'));
            }
          } catch (e) {
            if (window.showToast) window.showToast('Failed to add customer: ' + (e.message || 'Error'), 'error');
          }
        }
      });
    }

    // 4. Smart WhatsApp Dues Reminder
    const inlineDues = this.parseInlineDuesReminder(raw, customers, ctx.firm);
    if (inlineDues) {
      const f = ctx.firm || {};
      const msg = `Dear ${inlineDues.name},\nThis is a gentle reminder that your outstanding balance with *${f.firmName || 'our store'}* is *${BillsoftUtils.formatCurrency(inlineDues.balance)}*.\nKindly clear the pending dues at your earliest convenience. Thank you!`;
      results.smartTasks.push({
        type: 'smart_dues_reminder',
        title: `💬 WhatsApp Dues Reminder: ${inlineDues.name}`,
        subtitle: `Pending Balance: ${BillsoftUtils.formatCurrency(inlineDues.balance)} • 📞 ${inlineDues.phone || 'No phone'}`,
        icon: '💬',
        badge: 'WhatsApp Dues',
        payload: inlineDues,
        action: () => {
          if (inlineDues.phone) {
            const cleanPhone = String(inlineDues.phone).replace(/\D/g, '');
            const url = `https://wa.me/${cleanPhone.length === 10 ? '91' + cleanPhone : cleanPhone}?text=${encodeURIComponent(msg)}`;
            window.open(url, '_blank');
          } else {
            if (navigator.clipboard) navigator.clipboard.writeText(msg);
            if (window.showToast) window.showToast('Copied reminder message to clipboard', 'info');
          }
        }
      });
    }

    // 5. Smart Live Business Intelligence Analytics
    const bizMetrics = this.parseBusinessMetrics(raw, invoices, customers, products);
    if (bizMetrics) {
      if (bizMetrics.type === 'today_sales') {
        results.smartTasks.push({
          type: 'smart_metrics',
          title: bizMetrics.title,
          subtitle: bizMetrics.subtitle,
          icon: '📊',
          badge: 'Today Snapshot',
          action: () => {
            BillsoftSearchEngine.dispatchNavigate({ page: 'invoices', subTab: 'invoices' });
          }
        });
      } else if (bizMetrics.type === 'total_dues') {
        results.smartTasks.push({
          type: 'smart_metrics',
          title: bizMetrics.title,
          subtitle: bizMetrics.subtitle,
          icon: '💰',
          badge: 'Outstanding Dues',
          action: () => {
            BillsoftSearchEngine.dispatchNavigate({ page: 'paperwork', tab: 'statements', statementMode: 'customer' });
          }
        });
      } else if (bizMetrics.type === 'low_stock') {
        results.smartTasks.push({
          type: 'smart_metrics',
          title: bizMetrics.title,
          subtitle: bizMetrics.subtitle,
          icon: '⚠️',
          badge: 'Inventory Alert',
          action: () => {
            BillsoftSearchEngine.dispatchNavigate({ page: 'paperwork', tab: 'orders' });
          }
        });
      }
    }

    // 6. Cashier Change Calculator
    const cashChange = this.parseCashChange(raw);
    if (cashChange) {
      results.utilities.push({
        type: 'change',
        title: `💵 Return Change: ${BillsoftUtils.formatCurrency(cashChange.change)}`,
        subtitle: `Tendered: ${BillsoftUtils.formatCurrency(cashChange.tendered)} • Bill: ${BillsoftUtils.formatCurrency(cashChange.bill)} • Breakdown: ${cashChange.breakdownStr}`,
        icon: '💵',
        badge: 'Cashier Helper',
        action: () => {
          if (navigator.clipboard) navigator.clipboard.writeText(`Change to return: ₹${cashChange.change} (${cashChange.breakdownStr})`);
          if (window.showToast) window.showToast('Copied change breakdown', 'success');
        }
      });
    }

    // 7. Bill Splitter
    const billSplit = this.parseBillSplit(raw);
    if (billSplit) {
      results.utilities.push({
        type: 'split',
        title: `👥 Split Bill: ${BillsoftUtils.formatCurrency(billSplit.perPerson)} per person`,
        subtitle: `Total Amount: ${BillsoftUtils.formatCurrency(billSplit.amount)} split between ${billSplit.people} people`,
        icon: '👥',
        badge: 'Bill Splitter',
        action: () => {
          if (navigator.clipboard) navigator.clipboard.writeText(`₹${billSplit.perPerson} each for ${billSplit.people} people (Total: ₹${billSplit.amount})`);
          if (window.showToast) window.showToast('Copied split amount', 'success');
        }
      });
    }

    // ─────────────────────────────────────────────────────────────
    // B. UTILITIES (Math, GST, Words, GSTIN, UPI QR, Margin, Date, Bank)
    // ─────────────────────────────────────────────────────────────

    // Math
    const mathResult = this.evaluateMath(raw);
    if (mathResult) {
      results.utilities.push({
        type: 'math',
        title: `🧮 Result: ${mathResult.formatted}`,
        subtitle: `Equation: ${mathResult.expression} = ${mathResult.result}`,
        value: mathResult.result,
        icon: '🧮',
        badge: 'Calculator',
        action: () => {
          if (navigator.clipboard) navigator.clipboard.writeText(String(mathResult.result));
          if (window.showToast) window.showToast(`Copied result: ${mathResult.result}`, 'success');
        }
      });
    }

    // GST
    const gstMatch = q.match(/^(?:(?:reverse\s+)?(?:gst|tax)\s+)?(\d+(?:\.\d+)?)\s*%\s*(?:(?:on|of)\s+)?(\d+(?:\.\d+)?)$/i) ||
      q.match(/^(?:gst|tax)\s+(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)$/i);
    const isReverseGst = q.includes('reverse') || q.includes('inclusive');

    if (gstMatch) {
      const rate = parseFloat(gstMatch[1]);
      const amount = parseFloat(gstMatch[2]);
      if (rate > 0 && amount > 0) {
        let baseAmount, gstAmount, totalAmount;
        if (isReverseGst) {
          baseAmount = amount / (1 + (rate / 100));
          gstAmount = amount - baseAmount;
          totalAmount = amount;
        } else {
          baseAmount = amount;
          gstAmount = (amount * rate) / 100;
          totalAmount = amount + gstAmount;
        }
        const cgst = gstAmount / 2;
        const sgst = gstAmount / 2;

        results.utilities.push({
          type: 'gst',
          title: `💰 Base: ${BillsoftUtils.formatCurrency(baseAmount)} | Total: ${BillsoftUtils.formatCurrency(totalAmount)}`,
          subtitle: `${rate}% GST = ${BillsoftUtils.formatCurrency(gstAmount)} (CGST ${rate / 2}%: ${BillsoftUtils.formatCurrency(cgst)}, SGST ${rate / 2}%: ${BillsoftUtils.formatCurrency(sgst)})`,
          icon: '🏛️',
          badge: isReverseGst ? 'Reverse GST' : 'GST Calculator',
          action: () => {
            if (navigator.clipboard) navigator.clipboard.writeText(`Base: ${baseAmount.toFixed(2)}, GST (${rate}%): ${gstAmount.toFixed(2)}, Total: ${totalAmount.toFixed(2)}`);
            if (window.showToast) window.showToast('Copied GST breakdown to clipboard', 'success');
          }
        });
      }
    }

    // Words
    const wordsMatch = q.match(/^(?:words|in words|rupees|cheque|word)\s+(\d+(?:\.\d+)?)$/i);
    if (wordsMatch) {
      const numVal = parseFloat(wordsMatch[1]);
      const wordsStr = this.numberToWordsIndian(numVal);
      results.utilities.push({
        type: 'words',
        title: `✍️ ${wordsStr}`,
        subtitle: `Amount: ${BillsoftUtils.formatCurrency(numVal)}`,
        icon: '✍️',
        badge: 'Amount in Words',
        action: () => {
          if (navigator.clipboard) navigator.clipboard.writeText(wordsStr);
          if (window.showToast) window.showToast('Copied words to clipboard', 'success');
        }
      });
    }

    // GSTIN Validation
    const gstinExtract = raw.toUpperCase().match(/[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}/) ||
      (q.startsWith('gstin') ? raw.replace(/^gstin\s*/i, '').trim().toUpperCase() : null);
    if (gstinExtract) {
      const val = typeof gstinExtract === 'string' ? this.validateGstin(gstinExtract) : this.validateGstin(gstinExtract[0]);
      if (val && val.isValid) {
        results.utilities.push({
          type: 'gstin',
          title: `🏛️ Valid GSTIN: ${val.gstin}`,
          subtitle: `State: ${val.stateName} (${val.stateCode}) • PAN: ${val.pan}`,
          icon: '🏛️',
          badge: 'GSTIN Verified',
          action: () => {
            BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'customers' });
            window.dispatchEvent(new CustomEvent('billsoft:open-add-customer', { detail: { gstin: val.gstin } }));
          }
        });
      }
    }

    // Dynamic UPI QR Code
    const upiMatch = q.match(/^(?:upi|qr|pay qr|gpay|phonepe)\s+(\d+(?:\.\d+)?)$/i);
    if (upiMatch) {
      const upiAmt = parseFloat(upiMatch[1]);
      const firmObj = ctx.firm || {};
      const upiId = firmObj.upiId || firmObj.upi || 'ourbusiness@upi';
      const firmName = firmObj.firmName || 'RupeeCRM Store';
      const upiUrl = `upi://pay?pa=${encodeURIComponent(upiId)}&pn=${encodeURIComponent(firmName)}&am=${upiAmt.toFixed(2)}&cu=INR`;
      const qrImageUrl = `https://api.qrserver.com/v1/create-qr-code/?size=240x240&data=${encodeURIComponent(upiUrl)}`;

      results.utilities.push({
        type: 'upi_qr',
        title: `📱 UPI Payment QR: ${BillsoftUtils.formatCurrency(upiAmt)}`,
        subtitle: `Pay to: ${upiId} (${firmName})`,
        qrUrl: qrImageUrl,
        amount: upiAmt,
        icon: '📱',
        badge: 'Instant QR Code',
        action: () => {
          if (navigator.clipboard) navigator.clipboard.writeText(upiUrl);
          if (window.showToast) window.showToast('Copied UPI Payment Link', 'success');
        }
      });
    }

    // Margin / Profit Calculator
    const marginMatch = q.match(/^(?:margin|profit)\s+(?:cost\s+)?(\d+(?:\.\d+)?)\s+(?:price\s+|sell\s+)?(\d+(?:\.\d+)?)$/i);
    if (marginMatch) {
      const cost = parseFloat(marginMatch[1]);
      const price = parseFloat(marginMatch[2]);
      if (price > 0 && cost >= 0) {
        const profit = price - cost;
        const marginPct = (profit / price) * 100;
        const markupPct = cost > 0 ? (profit / cost) * 100 : 0;

        results.utilities.push({
          type: 'margin',
          title: `📈 Profit: ${BillsoftUtils.formatCurrency(profit)} | Margin: ${marginPct.toFixed(2)}% | Markup: ${markupPct.toFixed(2)}%`,
          subtitle: `Cost: ${BillsoftUtils.formatCurrency(cost)} • Selling Price: ${BillsoftUtils.formatCurrency(price)}`,
          icon: '📈',
          badge: 'Margin Calculator',
          action: () => {
            if (navigator.clipboard) navigator.clipboard.writeText(`Profit: ${profit.toFixed(2)}, Margin: ${marginPct.toFixed(1)}%, Markup: ${markupPct.toFixed(1)}%`);
            if (window.showToast) window.showToast('Copied margin breakdown', 'success');
          }
        });
      }
    }

    // Due Date Calculator
    const dateMatch = q.match(/^(?:date|due date|days)\s*\+?\s*(\d+)\s*(?:days)?$/i);
    if (dateMatch) {
      const offsetDays = parseInt(dateMatch[1], 10);
      const targetDate = new Date();
      targetDate.setDate(targetDate.getDate() + offsetDays);
      const dayNames = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
      const formattedTarget = BillsoftUtils.formatDate(targetDate);
      const dayName = dayNames[targetDate.getDay()];

      results.utilities.push({
        type: 'date',
        title: `📅 ${formattedTarget} (${dayName})`,
        subtitle: `Date after ${offsetDays} days from today`,
        icon: '📅',
        badge: 'Due Date Helper',
        action: () => {
          if (navigator.clipboard) navigator.clipboard.writeText(formattedTarget);
          if (window.showToast) window.showToast(`Copied date: ${formattedTarget}`, 'success');
        }
      });
    }

    // Firm Banking Details
    if (['bank', 'bank details', 'ifsc', 'account', 'bank account', 'upi', 'qr code', 'firm gstin', 'company address'].some(k => q.includes(k))) {
      const f = ctx.firm || {};
      results.utilities.push({
        type: 'bank_details',
        title: `🏛️ ${f.firmName || 'Our Business'} — Bank & Tax Info`,
        subtitle: `Bank: ${f.bankName || 'N/A'} • A/C: ${f.accountNumber || 'N/A'} • IFSC: ${f.ifscCode || 'N/A'} • GSTIN: ${f.gstin || 'N/A'}`,
        firm: f,
        icon: '🏛️',
        badge: 'Firm Info',
        action: () => {
          const txt = `*${f.firmName || 'Our Firm'} Bank Details:*\nBank: ${f.bankName || ''}\nA/C No: ${f.accountNumber || ''}\nIFSC: ${f.ifscCode || ''}\nUPI: ${f.upiId || ''}\nGSTIN: ${f.gstin || ''}`;
          if (navigator.clipboard) navigator.clipboard.writeText(txt);
          if (window.showToast) window.showToast('Copied firm banking info to clipboard', 'success');
        }
      });
    }

    // ─────────────────────────────────────────────────────────────
    // C. ACTION & INTENT SEARCH WITH MULTILINGUAL & TYPO MATCHING
    // ─────────────────────────────────────────────────────────────
    for (const act of this.ACTIONS) {
      let matched = false;
      for (const kw of act.keywords) {
        const fm = this.fuzzyMatch(raw, kw);
        if (fm.match) {
          matched = true;
          results.actions.push({ ...act, score: fm.score });
          break;
        }
      }
      if (!matched) {
        const tm = this.fuzzyMatch(raw, act.title);
        if (tm.match) {
          results.actions.push({ ...act, score: tm.score });
        }
      }
    }
    // Sort actions by relevance score
    results.actions.sort((a, b) => (b.score || 0) - (a.score || 0));

    // ─────────────────────────────────────────────────────────────
    // D. ENTITY MATCHING (Customers, Invoices, Products, Vendors, Staff)
    // ─────────────────────────────────────────────────────────────
    const qClean = raw.replace(/^(?:customer|cust|grahak|client|vendor|supplier|party|product|item|saman|staff|employee|naukar|kamgar|bill|invoice)\s+/i, '').trim();
    const matchText = (target) => {
      if (!target) return false;
      const m1 = this.fuzzyMatch(raw, target);
      if (m1 && m1.match) return true;
      if (qClean) {
        const m2 = this.fuzzyMatch(qClean, target);
        if (m2 && m2.match) return true;
      }
      return false;
    };
    const matchSub = (target) => {
      if (!target) return false;
      const str = String(target).toLowerCase();
      return str.includes(q) || (qClean ? str.includes(qClean.toLowerCase()) : false);
    };

    for (const c of customers) {
      if (!c) continue;
      if (matchText(c.name) || matchSub(c.phone) || matchSub(c.gstin) || matchSub(c.city)) {
        results.customers.push(c);
      }
    }

    for (const inv of invoices) {
      if (!inv) continue;
      if (matchSub(inv.invoiceNumber) || matchSub(inv.estimateNumber) || matchText(inv.customerName) || matchSub(inv.customerName)) {
        results.invoices.push(inv);
      }
    }

    for (const p of products) {
      if (!p) continue;
      if (matchText(p.name) || matchSub(p.sku) || matchSub(p.barcode) || matchSub(p.category)) {
        results.products.push(p);
      }
    }

    for (const p of parties) {
      if (!p) continue;
      if (matchText(p.name) || matchText(p.contactPerson) || matchSub(p.phone) || matchSub(p.gstin) || matchSub(p.city)) {
        results.parties.push(p);
      }
    }

    for (const emp of staff) {
      if (!emp) continue;
      if (matchText(emp.name) || matchText(emp.role) || matchText(emp.designation) || matchSub(emp.phone) || matchSub(emp.department)) {
        results.staff.push(emp);
      }
    }

    // Suggestions if results are sparse
    if (results.smartTasks.length === 0 && results.actions.length === 0 && results.customers.length === 0 && results.invoices.length === 0 && results.utilities.length === 0) {
      results.suggestions = this.getSuggestions(raw);
    }

    return results;
  }
};

window.BillsoftSearchEngine = BillsoftSearchEngine;
BillsoftUtils.searchEngine = BillsoftSearchEngine;

