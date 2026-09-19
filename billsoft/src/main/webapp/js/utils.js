/**
 * Billsoft Utility Functions
 */
const BillsoftUtils = {
  getInitials(name) {
    if (!name || typeof name !== 'string') return '??';
    const parts = name.trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return '??';
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  },

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

  countUnicodeChars(str) {
    if (!str) return 0;
    return Array.from(str).length;
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
    fileReader.onload = async function () {
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

  // Decouples glued numbers and words/units: "30mm" -> "30 mm", "700upi" -> "700 upi", "18%gst" -> "18% gst"
  splitGluedTokens(text) {
    if (!text || typeof text !== 'string') return '';
    let s = text.trim();

    // 1. Separate percentage from glued words (e.g. "18%gst" -> "18% gst", "10%discount" -> "10% discount")
    s = s.replace(/(\d+(?:\.\d+)?%)([a-zA-Z]+)/g, '$1 $2');

    // 2. Separate numbers and percentages (e.g. "18percent" -> "18 percent")
    s = s.replace(/(\d+(?:\.\d+)?)(%|percent|take|dar)\b/gi, '$1 $2');

    // 3. Separate number glued to unit or word (e.g. "30mm" -> "30 mm", "700upi" -> "700 upi", "100usd" -> "100 usd", "5000advance" -> "5000 advance", "45kg" -> "45 kg")
    // BUT preserve GSTIN patterns (15 chars, starts with 2 digits) and standard identifiers
    s = s.replace(/\b(\d+(?:\.\d+)?)([a-zA-Z]+)\b/g, (match, p1, p2) => {
      if (match.length === 15 && /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/i.test(match)) {
        return match;
      }
      return `${p1} ${p2}`;
    });

    // 4. Separate word glued to number (e.g. "upi700" -> "upi 700", "gst18" -> "gst 18", "advance5000" -> "advance 5000")
    s = s.replace(/\b([a-zA-Z]+)(\d+(?:\.\d+)?)\b/g, (match, p1, p2) => {
      const p1Lower = p1.toLowerCase();
      const knownKeywords = ['upi', 'qr', 'gpay', 'phonepe', 'bhim', 'gst', 'tax', 'split', 'change', 'discount', 'markup', 'margin', 'rate', 'cost', 'price', 'advance', 'salary', 'bill', 'inv', 'quo', 'words', 'word', 'cheque', 'format'];
      if (knownKeywords.includes(p1Lower) || p1.length <= 4) {
        return `${p1} ${p2}`;
      }
      return match;
    });

    return s.replace(/\s+/g, ' ').trim();
  },

  // Computes Damerau-Levenshtein distance (insertions, deletions, substitutions, transpositions)
  damerauLevenshtein(a, b) {
    if (a === b) return 0;
    if (!a || !a.length) return (b || '').length;
    if (!b || !b.length) return a.length;

    const al = a.length;
    const bl = b.length;
    const d = [];

    for (let i = 0; i <= al; i++) {
      d[i] = [i];
    }
    for (let j = 0; j <= bl; j++) {
      d[0][j] = j;
    }

    for (let i = 1; i <= al; i++) {
      for (let j = 1; j <= bl; j++) {
        const cost = a.charAt(i - 1) === b.charAt(j - 1) ? 0 : 1;
        d[i][j] = Math.min(
          d[i - 1][j] + 1,        // deletion
          d[i][j - 1] + 1,        // insertion
          d[i - 1][j - 1] + cost   // substitution
        );

        // Transposition check (Damerau addition: swapped adjacent letters)
        if (i > 1 && j > 1 &&
            a.charAt(i - 1) === b.charAt(j - 2) &&
            a.charAt(i - 2) === b.charAt(j - 1)) {
          d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
        }
      }
    }

    return d[al][bl];
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

if (typeof window !== 'undefined') {
  window.BillsoftWhatsApp = BillsoftWhatsApp;
}
BillsoftUtils.whatsapp = BillsoftWhatsApp;

// ─── UNIVERSAL NATURAL-LANGUAGE CONVERSION ENGINE ───
const CurrencyRateProvider = {
  ratesToUsd: {
    USD: 1.0,
    INR: 0.01167,    // 1 USD ≈ 85.69 INR (1 INR = 0.01167 USD)
    EUR: 1.085,      // 1 EUR = 1.085 USD
    GBP: 1.295,      // 1 GBP = 1.295 USD
    AED: 0.2723,     // 1 USD = 3.6725 AED
    AUD: 0.655,      // 1 AUD = 0.655 USD
    CAD: 0.725,      // 1 CAD = 0.725 USD
    SGD: 0.755,      // 1 SGD = 0.755 USD
    JPY: 0.0067      // 1 USD = 149.25 JPY
  },
  lastUpdated: 'Live Reference Rate',
  getRate(fromCode, toCode) {
    if (!fromCode || !toCode) return null;
    fromCode = fromCode.toUpperCase();
    toCode = toCode.toUpperCase();
    if (fromCode === toCode) return 1.0;
    const fromToUsd = this.ratesToUsd[fromCode];
    const toToUsd = this.ratesToUsd[toCode];
    if (fromToUsd && toToUsd) {
      return fromToUsd / toToUsd;
    }
    return null;
  },
  setRate(code, rateToUsd) {
    if (code && typeof rateToUsd === 'number' && rateToUsd > 0) {
      this.ratesToUsd[code.toUpperCase()] = rateToUsd;
    }
  }
};

const ConversionRegistry = {
  categories: {
    LENGTH: {
      name: 'Length',
      icon: '📏',
      baseUnit: 'METER',
      units: {
        MILLIMETER: { name: 'Millimeter', symbol: 'mm', factor: 0.001, aliases: ['mm', 'millimeter', 'millimeters', 'millimetre', 'millimetres', 'mili', 'milli'] },
        CENTIMETER: { name: 'Centimeter', symbol: 'cm', factor: 0.01, aliases: ['cm', 'centimeter', 'centimeters', 'centimetre', 'centimetres', 'centi', 'cms'] },
        METER: { name: 'Meter', symbol: 'm', factor: 1.0, aliases: ['m', 'meter', 'meters', 'metre', 'metres', 'mtr', 'mtrs', 'meeter', 'metrs', 'meterss'] },
        KILOMETER: { name: 'Kilometer', symbol: 'km', factor: 1000.0, aliases: ['km', 'kilometer', 'kilometers', 'kilometre', 'kilometres', 'kms', 'kilo meter'] },
        INCH: { name: 'Inch', symbol: 'in', factor: 0.0254, aliases: ['in', 'inch', 'inches', 'inchs', '"'] },
        FOOT: { name: 'Foot', symbol: 'ft', factor: 0.3048, aliases: ['ft', 'foot', 'feet', 'feets', 'foots', '\''] },
        YARD: { name: 'Yard', symbol: 'yd', factor: 0.9144, aliases: ['yd', 'yds', 'yard', 'yards', 'gaj', 'gaz'] },
        MILE: { name: 'Mile', symbol: 'mi', factor: 1609.344, aliases: ['mi', 'mile', 'miles'] }
      }
    },
    WEIGHT: {
      name: 'Weight / Mass',
      icon: '⚖️',
      baseUnit: 'GRAM',
      units: {
        MILLIGRAM: { name: 'Milligram', symbol: 'mg', factor: 0.001, aliases: ['mg', 'milligram', 'milligrams', 'milligramme', 'milligrammes', 'mili gram'] },
        GRAM: { name: 'Gram', symbol: 'g', factor: 1.0, aliases: ['g', 'gm', 'gms', 'grm', 'grms', 'gram', 'grams', 'gramme', 'grammes', 'gramm', 'gramms'] },
        KILOGRAM: { name: 'Kilogram', symbol: 'kg', factor: 1000.0, aliases: ['kg', 'kgs', 'k.g.', 'kilo', 'kilos', 'kilogram', 'kilograms', 'kilogramme', 'kilogrammes', 'kilo gram'] },
        QUINTAL: { name: 'Quintal', symbol: 'q', factor: 100000.0, aliases: ['quintal', 'quintals', 'kavntal', 'kuntal', 'qtl', 'qtls'] },
        TONNE: { name: 'Metric Tonne', symbol: 't', factor: 1000000.0, aliases: ['tonne', 'tonnes', 'metric tonne', 'metric ton', 'ton', 'tons', 't'] },
        TOLA: { name: 'Tola', symbol: 'tola', factor: 10.0, aliases: ['tola', 'tolas', 'tole'] }, // 10g metric tola
        OUNCE: { name: 'Ounce', symbol: 'oz', factor: 28.349523125, aliases: ['oz', 'ounce', 'ounces'] },
        POUND: { name: 'Pound (Mass)', symbol: 'lb', factor: 453.59237, aliases: ['lb', 'lbs', 'pound', 'pounds', 'pound mass', 'pounds mass'] }
      }
    },
    VOLUME: {
      name: 'Volume',
      icon: '🧪',
      baseUnit: 'LITER',
      units: {
        MILLILITER: { name: 'Milliliter', symbol: 'ml', factor: 0.001, aliases: ['ml', 'mltr', 'milliliter', 'milliliters', 'millilitre', 'millilitres', 'cc'] },
        LITER: { name: 'Liter', symbol: 'l', factor: 1.0, aliases: ['l', 'ltr', 'ltrs', 'liter', 'liters', 'litre', 'litres', 'leeter'] },
        CUBIC_FOOT: { name: 'Cubic Foot', symbol: 'cu ft', factor: 28.316846592, aliases: ['cubic foot', 'cubic feet', 'cu ft', 'cuft', 'cft', 'ft3', 'ft^3'] },
        BRASS: { name: 'Brass (Volume)', symbol: 'brass', factor: 2831.6846592, aliases: ['brass', 'brs'] },
        CUBIC_METER: { name: 'Cubic Meter', symbol: 'm³', factor: 1000.0, aliases: ['cubic meter', 'cubic meters', 'cubic metre', 'cubic metres', 'cbm', 'm3', 'm^3'] },
        GALLON: { name: 'US Gallon', symbol: 'gal', factor: 3.785411784, aliases: ['gal', 'gallon', 'gallons', 'us gallon', 'us gal'] },
        QUART: { name: 'Quart', symbol: 'qt', factor: 0.946352946, aliases: ['qt', 'quart', 'quarts'] },
        PINT: { name: 'Pint', symbol: 'pt', factor: 0.473176473, aliases: ['pt', 'pint', 'pints'] },
        CUP: { name: 'Cup', symbol: 'cup', factor: 0.2365882365, aliases: ['cup', 'cups'] }
      }
    },
    AREA: {
      name: 'Area',
      icon: '📐',
      baseUnit: 'SQUARE_METER',
      units: {
        SQUARE_MILLIMETER: { name: 'Square Millimeter', symbol: 'sq mm', factor: 0.000001, aliases: ['sq mm', 'sqmm', 'square millimeter', 'square millimeters', 'square millimetre', 'mm2', 'mm^2'] },
        SQUARE_CENTIMETER: { name: 'Square Centimeter', symbol: 'sq cm', factor: 0.0001, aliases: ['sq cm', 'sqcm', 'square centimeter', 'square centimeters', 'square centimetre', 'cm2', 'cm^2'] },
        SQUARE_METER: { name: 'Square Meter', symbol: 'sq m', factor: 1.0, aliases: ['sq m', 'sqm', 'sq meter', 'sq meters', 'sq metre', 'sq metres', 'square meter', 'square meters', 'square metre', 'square metres', 'm2', 'm^2', 'sqmtr', 'sqmtrs'] },
        SQUARE_KILOMETER: { name: 'Square Kilometer', symbol: 'sq km', factor: 1000000.0, aliases: ['sq km', 'sqkm', 'square kilometer', 'square kilometers', 'km2', 'km^2'] },
        SQUARE_INCH: { name: 'Square Inch', symbol: 'sq in', factor: 0.00064516, aliases: ['sq in', 'sqin', 'square inch', 'square inches', 'in2', 'in^2'] },
        SQUARE_FOOT: { name: 'Square Foot', symbol: 'sq ft', factor: 0.09290304, aliases: ['sq ft', 'sqft', 'sq foot', 'sq feet', 'square foot', 'square feet', 'ft2', 'ft^2'] },
        SQUARE_YARD: { name: 'Square Yard', symbol: 'sq yd', factor: 0.83612736, aliases: ['sq yd', 'sqyd', 'sq yard', 'sq yards', 'square yard', 'square yards', 'sq gaj', 'sq gaz'] },
        ACRE: { name: 'Acre', symbol: 'acre', factor: 4046.8564224, aliases: ['acre', 'acres', 'ekad'] },
        HECTARE: { name: 'Hectare', symbol: 'ha', factor: 10000.0, aliases: ['ha', 'hectare', 'hectares', 'hektar'] },
        BIGHA: { name: 'Bigha', symbol: 'bigha', factor: 2500.0, aliases: ['bigha', 'bighas', 'vigha'] },
        GUNTHA: { name: 'Guntha', symbol: 'guntha', factor: 101.17141056, aliases: ['guntha', 'gunthe', 'gunta'] }
      }
    },
    TEMPERATURE: {
      name: 'Temperature',
      icon: '🌡️',
      isCustom: true,
      units: {
        CELSIUS: { name: 'Celsius', symbol: '°C', aliases: ['c', 'celsius', 'centigrade', 'deg c', 'degree c', 'degrees c', 'degree celsius', 'degrees celsius', '°c'] },
        FAHRENHEIT: { name: 'Fahrenheit', symbol: '°F', aliases: ['f', 'fahrenheit', 'deg f', 'degree f', 'degrees f', 'degree fahrenheit', 'degrees fahrenheit', '°f'] },
        KELVIN: { name: 'Kelvin', symbol: 'K', aliases: ['k', 'kelvin', 'deg k', 'degree k', 'degrees k', 'kelvins'] }
      },
      convert: (val, fromKey, toKey) => {
        let c;
        if (fromKey === 'CELSIUS') c = val;
        else if (fromKey === 'FAHRENHEIT') c = (val - 32) * (5 / 9);
        else if (fromKey === 'KELVIN') c = val - 273.15;
        else return null;

        const round = (n) => Math.round(n * 10000) / 10000;
        if (toKey === 'CELSIUS') return { result: c, formula: fromKey === 'FAHRENHEIT' ? `(${val}°F - 32) × 5/9 = ${round(c)}°C` : `${val}K - 273.15 = ${round(c)}°C` };
        if (toKey === 'FAHRENHEIT') {
          const f = (c * 9 / 5) + 32;
          return { result: f, formula: fromKey === 'CELSIUS' ? `(${val}°C × 9/5) + 32 = ${round(f)}°F` : `(${val}K - 273.15) × 9/5 + 32 = ${round(f)}°F` };
        }
        if (toKey === 'KELVIN') {
          const k = c + 273.15;
          return { result: k, formula: fromKey === 'CELSIUS' ? `${val}°C + 273.15 = ${round(k)}K` : `(${val}°F - 32) × 5/9 + 273.15 = ${round(k)}K` };
        }
        return null;
      }
    },
    TIME: {
      name: 'Time',
      icon: '⏱️',
      baseUnit: 'SECOND',
      units: {
        MILLISECOND: { name: 'Millisecond', symbol: 'ms', factor: 0.001, aliases: ['ms', 'millisecond', 'milliseconds', 'millisec', 'millisecs'] },
        SECOND: { name: 'Second', symbol: 's', factor: 1.0, aliases: ['s', 'sec', 'secs', 'second', 'seconds', 'sekand'] },
        MINUTE: { name: 'Minute', symbol: 'min', factor: 60.0, aliases: ['min', 'mins', 'minute', 'minutes'] },
        HOUR: { name: 'Hour', symbol: 'hr', factor: 3600.0, aliases: ['hr', 'hrs', 'hour', 'hours', 'ghante', 'tas'] },
        DAY: { name: 'Day', symbol: 'day', factor: 86400.0, aliases: ['day', 'days', 'din', 'divas', 'd'] },
        WEEK: { name: 'Week', symbol: 'wk', factor: 604800.0, aliases: ['wk', 'wks', 'week', 'weeks', 'hafta', 'hafte', 'athvada'] },
        MONTH: { name: 'Month (Avg 30.44d)', symbol: 'mo', factor: 2629800.0, aliases: ['mo', 'month', 'months', 'mahina', 'mahine'] },
        YEAR: { name: 'Year (365d)', symbol: 'yr', factor: 31536000.0, aliases: ['yr', 'yrs', 'year', 'years', 'saal', 'varsh'] }
      }
    },
    CURRENCY: {
      name: 'Currency',
      icon: '💱',
      isCurrency: true,
      units: {
        INR: { name: 'Indian Rupee', symbol: '₹', code: 'INR', aliases: ['inr', '₹', 'rs', 'rs.', 'rupee', 'rupees', 'rupaye', 'rupay', 'rupya', 'rupayee'] },
        USD: { name: 'US Dollar', symbol: '$', code: 'USD', aliases: ['usd', '$', 'dollar', 'dollars', 'us dollar', 'us dollars', 'buck', 'bucks'] },
        EUR: { name: 'Euro', symbol: '€', code: 'EUR', aliases: ['eur', '€', 'euro', 'euros'] },
        GBP: { name: 'British Pound', symbol: '£', code: 'GBP', aliases: ['gbp', '£', 'pound', 'pounds', 'british pound', 'quid', 'sterling'] },
        AED: { name: 'UAE Dirham', symbol: 'AED', code: 'AED', aliases: ['aed', 'dirham', 'dirhams', 'uae dirham'] },
        AUD: { name: 'Australian Dollar', symbol: 'A$', code: 'AUD', aliases: ['aud', 'a$', 'australian dollar', 'aud dollar'] },
        CAD: { name: 'Canadian Dollar', symbol: 'C$', code: 'CAD', aliases: ['cad', 'c$', 'canadian dollar', 'cad dollar'] },
        SGD: { name: 'Singapore Dollar', symbol: 'S$', code: 'SGD', aliases: ['sgd', 's$', 'singapore dollar', 'sgd dollar'] },
        JPY: { name: 'Japanese Yen', symbol: '¥', code: 'JPY', aliases: ['jpy', '¥', 'yen', 'yens', 'japanese yen'] }
      }
    }
  }
};

// Build Alias Index sorted by length descending
const _ConversionAliasIndex = [];
for (const [catKey, cat] of Object.entries(ConversionRegistry.categories)) {
  for (const [uKey, u] of Object.entries(cat.units)) {
    for (const alias of u.aliases) {
      _ConversionAliasIndex.push({
        alias: alias.toLowerCase(),
        catKey,
        unitKey: uKey,
        unit: u,
        length: alias.length
      });
    }
  }
}
_ConversionAliasIndex.sort((a, b) => b.length - a.length);

const UniversalConversionEngine = {
  CurrencyRateProvider,
  ConversionRegistry,

  round(val) {
    if (val === 0) return 0;
    if (Math.abs(val) < 0.0001) return Number(val.toPrecision(4));
    return Math.round(val * 10000) / 10000;
  },

  resolveUnit(str) {
    if (!str) return null;
    const s = str.trim().toLowerCase().replace(/[.,;:?]/g, '');
    if (!s) return null;

    // Handle ambiguous "pound" / "pounds"
    if (s === 'pound' || s === 'pounds') {
      return {
        isAmbiguous: true,
        term: s,
        options: [
          { catKey: 'WEIGHT', unitKey: 'POUND', label: '10 lb weight (Mass)', unit: ConversionRegistry.categories.WEIGHT.units.POUND },
          { catKey: 'CURRENCY', unitKey: 'GBP', label: '£10 British currency (GBP)', unit: ConversionRegistry.categories.CURRENCY.units.GBP }
        ]
      };
    }

    // Direct match against alias index
    for (const item of _ConversionAliasIndex) {
      if (item.alias === s) {
        return { catKey: item.catKey, unitKey: item.unitKey, unit: item.unit };
      }
    }

    // Fuzzy match for typos (e.g. "metr", "milimeter", "celcius", "farenheit", "dolar", "rupe")
    if (s.length >= 3) {
      let bestMatch = null;
      let minDistance = 999;
      for (const item of _ConversionAliasIndex) {
        if (item.alias.length >= 3 && Math.abs(item.alias.length - s.length) <= 2) {
          const dist = BillsoftUtils.damerauLevenshtein(s, item.alias);
          const maxAllowed = s.length <= 4 ? 1 : 2;
          if (dist <= maxAllowed && dist < minDistance) {
            minDistance = dist;
            bestMatch = item;
          }
        }
      }
      if (bestMatch) {
        return { catKey: bestMatch.catKey, unitKey: bestMatch.unitKey, unit: bestMatch.unit };
      }
    }

    return null;
  },

  parseNumericValue(str) {
    if (!str) return null;
    let s = str.trim().toLowerCase();
    s = s.replace(/^[₹$€£]\s*/, '');

    let multiplier = 1;
    if (/\b(?:lakh|lakhs|lac|lacs)\b/i.test(s)) {
      multiplier = 100000;
      s = s.replace(/\b(?:lakh|lakhs|lac|lacs)\b/gi, '').trim();
    } else if (/\b(?:crore|crores|cr|koti)\b/i.test(s)) {
      multiplier = 10000000;
      s = s.replace(/\b(?:crore|crores|cr|koti)\b/gi, '').trim();
    } else if (/\b(?:million|m)\b/i.test(s) && !/\b(meter|metre|minute)\b/i.test(s)) {
      multiplier = 1000000;
      s = s.replace(/\b(?:million)\b/gi, '').trim();
    } else if (/\b(?:billion|b)\b/i.test(s)) {
      multiplier = 1000000000;
      s = s.replace(/\b(?:billion)\b/gi, '').trim();
    }

    // Remove commas
    s = s.replace(/,/g, '').trim();

    // Fractions (e.g. "1/2", "3/4")
    if (/^\d+\s*\/\s*\d+$/.test(s)) {
      const parts = s.split('/');
      const n = parseFloat(parts[0]);
      const d = parseFloat(parts[1]);
      if (d !== 0) return (n / d) * multiplier;
    }

    const num = parseFloat(s);
    return !isNaN(num) && isFinite(num) ? num * multiplier : null;
  },

  parse(rawQuery) {
    if (!rawQuery || typeof rawQuery !== 'string') return null;
    // Decouple glued tokens (e.g. "30mm" -> "30 mm", "100usd" -> "100 usd")
    const decoupled = BillsoftUtils.splitGluedTokens(rawQuery);
    const raw = decoupled.trim();
    if (!raw) return null;

    let q = raw.toLowerCase()
      .replace(/[\?]/g, '')
      .replace(/\s+/g, ' ')
      .trim();

    // 1. Currency Reverse Prefix: "USD 1000 to INR", "USD 1000 in rupees"
    const currPrefixMatch = q.match(/^([a-z$€£₹]+)\s*([\d,/]+(?:\.\d+)?(?:\s*(?:lakh|crore|million|billion))?)\s*(?:to|in|into|as|converted\s+to|turned\s+into|->|=>|=)\s*(.+)$/i);
    if (currPrefixMatch) {
      const fromCurrStr = currPrefixMatch[1];
      const valStr = currPrefixMatch[2];
      const toCurrStr = currPrefixMatch[3];
      const numVal = this.parseNumericValue(valStr);
      const fromRes = this.resolveUnit(fromCurrStr);
      const toRes = this.resolveUnit(toCurrStr);
      if (numVal !== null && fromRes && !fromRes.isAmbiguous && toRes && !toRes.isAmbiguous) {
        return this.executeConversion(numVal, fromRes, toRes, raw);
      }
    }

    // 2. "how many <target> (is|are|in|in a|per) <value> <source>"
    const howManyMatch = q.match(/^how\s+many\s+([a-z\s°"']+?)\s+(?:is|are|in|in\s+a|per)\s+([\d,/]+(?:\.\d+)?(?:\s*(?:lakh|crore|million|billion))?)\s+([a-z\s°"']+)$/i);
    if (howManyMatch) {
      const targetStr = howManyMatch[1];
      const valStr = howManyMatch[2];
      const sourceStr = howManyMatch[3];
      const val = this.parseNumericValue(valStr);
      const fromRes = this.resolveUnit(sourceStr);
      const toRes = this.resolveUnit(targetStr);
      if (val !== null && fromRes && toRes) {
        return this.executeConversion(val, fromRes, toRes, raw);
      }
    }

    // 3. "how much is <value> <source> (in|into|to) <target>" / "what is <value> <source> (in|into|to) <target>"
    const howMuchMatch = q.match(/^(?:how\s+much|what)\s+is\s+([\d,/]+(?:\.\d+)?(?:\s*(?:lakh|crore|million|billion))?)\s+([a-z\s°"']+?)\s+(?:in|into|to|as)\s+([a-z\s°"']+)$/i);
    if (howMuchMatch) {
      const valStr = howMuchMatch[1];
      const sourceStr = howMuchMatch[2];
      const targetStr = howMuchMatch[3];
      const val = this.parseNumericValue(valStr);
      const fromRes = this.resolveUnit(sourceStr);
      const toRes = this.resolveUnit(targetStr);
      if (val !== null && fromRes && toRes) {
        return this.executeConversion(val, fromRes, toRes, raw);
      }
    }

    // 4. "<source> to <target> <value>" (e.g. "feet to meters 10", "inr to usd 1000", "kg to grams 5")
    const revOrdMatch = q.match(/^(?:convert\s+)?([a-z\s°"']+?)\s+(?:to|in|into|->|=>|=)\s+([a-z\s°"']+?)\s+([\d,/]+(?:\.\d+)?(?:\s*(?:lakh|crore|million|billion))?)$/i);
    if (revOrdMatch) {
      const sourceStr = revOrdMatch[1];
      const targetStr = revOrdMatch[2];
      const valStr = revOrdMatch[3];
      const val = this.parseNumericValue(valStr);
      const fromRes = this.resolveUnit(sourceStr);
      const toRes = this.resolveUnit(targetStr);
      if (val !== null && fromRes && toRes) {
        return this.executeConversion(val, fromRes, toRes, raw);
      }
    }

    // 5. Hinglish & Marathi Natural Language Formulations
    const hinglishMatch = q.match(/^([\d,/]+(?:\.\d+)?(?:\s*(?:lakh|crore|million|billion))?)\s+([a-z\s°"']+?)\s+(?:ko\s+)?([a-z\s°"']+?)\s+(?:mein|me|madhe|madhye|che|cha)\s*(?:convert\s+karo|badlo|kitna|kitne|kiti|hoto|hote\s+hai|ahe)?$/i) ||
      q.match(/^([\d,/]+(?:\.\d+)?(?:\s*(?:lakh|crore|million|billion))?)\s+([a-z\s°"']+?)\s+che\s+([a-z\s°"']+?)\s+kiti$/i);
    if (hinglishMatch) {
      const valStr = hinglishMatch[1];
      const sourceStr = hinglishMatch[2];
      const targetStr = hinglishMatch[3];
      const val = this.parseNumericValue(valStr);
      const fromRes = this.resolveUnit(sourceStr);
      const toRes = this.resolveUnit(targetStr);
      if (val !== null && fromRes && toRes) {
        return this.executeConversion(val, fromRes, toRes, raw);
      }
    }

    // 6. Standard core pattern
    const stdMatch = q.match(/^(?:convert|turn|change)?\s*([\d,/]+(?:\.\d+)?(?:\s*(?:lakh|crore|million|billion))?)\s+([a-z\s°"'\$\€\£\₹]+?)\s+(?:to|in|into|as|->|=>|=|in\s+to|converted\s+to|turned\s+into)\s+([a-z\s°"'\$\€\£\₹]+)$/i) ||
      q.match(/^(?:convert|turn|change)?\s*([\d,/]+(?:\.\d+)?(?:\s*(?:lakh|crore|million|billion))?)\s+([a-z\$\€\£\₹]+)\s+([a-z\$\€\£\₹]+)$/i);

    if (stdMatch) {
      const valStr = stdMatch[1];
      const sourceStr = stdMatch[2];
      const targetStr = stdMatch[3];
      const val = this.parseNumericValue(valStr);
      const fromRes = this.resolveUnit(sourceStr);
      const toRes = this.resolveUnit(targetStr);

      if (val !== null && fromRes && toRes) {
        return this.executeConversion(val, fromRes, toRes, raw);
      }
    }

    // 6.5 Universal Bag-of-Words & Permutation Fallback
    // Handles any sequence: e.g. "mtr in 30 mm", "convert 30mm into meter", "30 mm to mtr", "inr 1000 usd", "usd from 500 inr"
    const numMatch = q.match(/([\d,/]+(?:\.\d+)?(?:\s*(?:lakh|crore|million|billion))?)/);
    if (numMatch) {
      const val = this.parseNumericValue(numMatch[0]);
      if (val !== null && val > 0) {
        const textWithoutNum = q.replace(numMatch[0], ' ').trim();
        const rawTokens = textWithoutNum.split(/\s+/).filter(Boolean);
        const noiseWords = new Set(['convert', 'turn', 'change', 'into', 'to', 'in', 'as', 'from', 'che', 'cha', 'me', 'mein', 'madhe', 'madhye', 'ko', 'se', 'varun', 'pasun', 'kiti', 'kitna', 'kitne', 'badlo', 'karo', 'hote', 'hoto', 'ahe', 'hai', 'of', 'per', 'a', 'the', 'is', 'are', 'what', 'how', 'many', 'much', '->', '=>', '=']);

        const recognized = [];
        for (let i = 0; i < rawTokens.length; i++) {
          const t1 = rawTokens[i];
          if (noiseWords.has(t1)) continue;

          // Try 2-word combo
          if (i + 1 < rawTokens.length) {
            const twoWord = t1 + ' ' + rawTokens[i + 1];
            const res2 = this.resolveUnit(twoWord);
            if (res2 && !res2.isAmbiguous) {
              recognized.push({ res: res2, token: twoWord, index: i });
              i++;
              continue;
            }
          }
          const res1 = this.resolveUnit(t1);
          if (res1) {
            recognized.push({ res: res1, token: t1, index: i });
          }
        }

        if (recognized.length >= 2) {
          let fromRes = recognized[0].res;
          let toRes = recognized[1].res;

          const tok0Idx = q.indexOf(recognized[0].token);
          const tok1Idx = q.indexOf(recognized[1].token);
          const numIdx = q.indexOf(numMatch[0]);
          const dist0 = Math.abs(tok0Idx - (numIdx + numMatch[0].length));
          const dist1 = Math.abs(tok1Idx - (numIdx + numMatch[0].length));

          if (dist1 <= 3) {
            // Number directly attached/precedes token 1: e.g. "mtr in 30 mm" -> 30 mm is from, mtr is to
            fromRes = recognized[1].res;
            toRes = recognized[0].res;
          } else if (dist0 <= 3) {
            // Number directly attached/precedes token 0: e.g. "30 mm to mtr" -> 30 mm is from, mtr is to
            fromRes = recognized[0].res;
            toRes = recognized[1].res;
          } else {
            const beforeTok0 = q.substring(0, tok0Idx).trim().split(/\s+/).pop();
            const beforeTok1 = q.substring(0, tok1Idx).trim().split(/\s+/).pop();

            if (['to', 'in', 'into', 'as', 'mein', 'me', 'madhe'].includes(beforeTok0)) {
              toRes = recognized[0].res;
              fromRes = recognized[1].res;
            } else if (['to', 'in', 'into', 'as', 'mein', 'me', 'madhe'].includes(beforeTok1)) {
              fromRes = recognized[0].res;
              toRes = recognized[1].res;
            } else if (['from', 'varun', 'pasun', 'se'].includes(beforeTok0)) {
              fromRes = recognized[0].res;
              toRes = recognized[1].res;
            } else if (['from', 'varun', 'pasun', 'se'].includes(beforeTok1)) {
              toRes = recognized[0].res;
              fromRes = recognized[1].res;
            }
          }

          if (fromRes && toRes) {
            return this.executeConversion(val, fromRes, toRes, raw);
          }
        }
      }
    }

    // 7. Single unit without target (e.g. "10 feet", "convert 10 m", "5 kg", "1000 inr", "10 pounds")
    const singleMatch = q.match(/^(?:convert|turn)?\s*([\d,/]+(?:\.\d+)?(?:\s*(?:lakh|crore|million|billion))?)\s+([a-z\s°"'\$\€\£\₹]+)$/i);
    if (singleMatch) {
      const valStr = singleMatch[1];
      const sourceStr = singleMatch[2];
      const val = this.parseNumericValue(valStr);
      const fromRes = this.resolveUnit(sourceStr);
      if (val !== null && fromRes) {
        if (fromRes.isAmbiguous) {
          return {
            type: 'conversion_ambiguous',
            val,
            title: `What do you mean by "${fromRes.term}"?`,
            subtitle: `Select conversion type for "${fromRes.term}":`,
            options: fromRes.options.map(opt => ({
              label: opt.label,
              query: opt.catKey === 'CURRENCY' ? `${val} GBP to USD` : `${val} lb to kg`
            })),
            icon: '⚖️'
          };
        }

        const cat = ConversionRegistry.categories[fromRes.catKey];
        const suggestions = Object.entries(cat.units)
          .filter(([k]) => k !== fromRes.unitKey)
          .slice(0, 4)
          .map(([_, u]) => u.symbol || u.name.toLowerCase());

        return {
          type: 'conversion_suggestion',
          val,
          fromRes,
          title: `Convert ${val} ${fromRes.unit.name} to:`,
          subtitle: `Suggested: ${suggestions.join(' · ')}`,
          suggestions,
          icon: cat.icon || '📏'
        };
      }
    }

    return null;
  },

  executeConversion(val, fromRes, toRes, rawQuery) {
    // Contextual disambiguation if one side is known
    if (fromRes.isAmbiguous && !toRes.isAmbiguous) {
      const match = fromRes.options.find(opt => opt.catKey === toRes.catKey);
      if (match) fromRes = { catKey: match.catKey, unitKey: match.unitKey, unit: match.unit };
    }
    if (toRes.isAmbiguous && !fromRes.isAmbiguous) {
      const match = toRes.options.find(opt => opt.catKey === fromRes.catKey);
      if (match) toRes = { catKey: match.catKey, unitKey: match.unitKey, unit: match.unit };
    }

    // Handle Ambiguity in source or target
    if (fromRes.isAmbiguous) {
      return {
        type: 'conversion_ambiguous',
        val,
        title: `What do you mean by "${fromRes.term}"?`,
        subtitle: `Select conversion type for "${fromRes.term}":`,
        options: fromRes.options.map(opt => ({
          label: opt.label,
          query: `${val} ${opt.unit.symbol || opt.unit.name} to ${toRes.unit?.symbol || toRes.unit?.name}`
        })),
        icon: '⚖️'
      };
    }
    if (toRes.isAmbiguous) {
      return {
        type: 'conversion_ambiguous',
        val,
        title: `What do you mean by "${toRes.term}"?`,
        subtitle: `Select conversion type for "${toRes.term}":`,
        options: toRes.options.map(opt => ({
          label: opt.label,
          query: `${val} ${fromRes.unit?.symbol || fromRes.unit?.name} to ${opt.unit.symbol || opt.unit.name}`
        })),
        icon: '⚖️'
      };
    }

    // Check category compatibility
    if (fromRes.catKey !== toRes.catKey) {
      const fromCat = ConversionRegistry.categories[fromRes.catKey]?.name || fromRes.catKey;
      const toCat = ConversionRegistry.categories[toRes.catKey]?.name || toRes.catKey;
      return {
        type: 'conversion_error',
        title: `⚠️ Incompatible Units: ${fromCat} → ${toCat}`,
        subtitle: `Cannot convert ${fromRes.unit.name} (${fromCat}) to ${toRes.unit.name} (${toCat}).`,
        icon: '⚠️'
      };
    }

    const catKey = fromRes.catKey;
    const cat = ConversionRegistry.categories[catKey];

    // Currency Conversion
    if (catKey === 'CURRENCY') {
      const rate = CurrencyRateProvider.getRate(fromRes.unit.code, toRes.unit.code);
      if (rate === null) {
        return {
          type: 'conversion_error',
          title: `💱 Exchange Rate Unavailable`,
          subtitle: `Live exchange rate for ${fromRes.unit.code} → ${toRes.unit.code} is currently unavailable.`,
          icon: '💱'
        };
      }
      const converted = val * rate;
      const formattedVal = val.toLocaleString('en-IN', { maximumFractionDigits: 4 });
      const formattedConverted = converted.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 4 });
      const cleanResultStr = `${toRes.unit.symbol}${formattedConverted} ${toRes.unit.code}`;

      return {
        type: 'conversion',
        category: 'Currency',
        icon: '💱',
        val,
        fromUnit: fromRes.unit,
        toUnit: toRes.unit,
        result: converted,
        cleanResultStr,
        title: `${fromRes.unit.symbol}${formattedVal} ${fromRes.unit.code} → ${toRes.unit.symbol}${formattedConverted} ${toRes.unit.code}`,
        subtitle: `Rate: 1 ${fromRes.unit.code} = ${this.round(rate)} ${toRes.unit.code} • Updated: ${CurrencyRateProvider.lastUpdated}`,
        formula: `${fromRes.unit.symbol}${formattedVal} × ${this.round(rate)} = ${toRes.unit.symbol}${formattedConverted}`,
        reverseQuery: `${this.round(converted)} ${toRes.unit.code} to ${fromRes.unit.code}`
      };
    }

    // Temperature (Custom Non-linear)
    if (cat.isCustom && cat.convert) {
      const conv = cat.convert(val, fromRes.unitKey, toRes.unitKey);
      if (!conv) return null;
      const cleanResultStr = `${this.round(conv.result)}${toRes.unit.symbol}`;
      return {
        type: 'conversion',
        category: cat.name,
        icon: cat.icon,
        val,
        fromUnit: fromRes.unit,
        toUnit: toRes.unit,
        result: conv.result,
        cleanResultStr,
        title: `${val}${fromRes.unit.symbol} → ${this.round(conv.result)}${toRes.unit.symbol}`,
        subtitle: `${cat.name} conversion • Formula: ${conv.formula}`,
        formula: conv.formula,
        reverseQuery: `${this.round(conv.result)} ${toRes.unit.name.toLowerCase()} to ${fromRes.unit.name.toLowerCase()}`
      };
    }

    // Standard multiplicative units (Length, Weight, Volume, Area, Time)
    const baseVal = val * fromRes.unit.factor;
    const converted = baseVal / toRes.unit.factor;
    const rounded = this.round(converted);
    const cleanResultStr = `${rounded.toLocaleString('en-IN', { maximumFractionDigits: 6 })} ${toRes.unit.name.toLowerCase()}`;
    const formulaStr = `${val} ${fromRes.unit.symbol} × ${(fromRes.unit.factor / toRes.unit.factor).toPrecision(5)} = ${rounded} ${toRes.unit.symbol}`;

    return {
      type: 'conversion',
      category: cat.name,
      icon: cat.icon,
      val,
      fromUnit: fromRes.unit,
      toUnit: toRes.unit,
      result: converted,
      cleanResultStr,
      title: `${val.toLocaleString('en-IN')} ${fromRes.unit.name.toLowerCase()} → ${rounded.toLocaleString('en-IN', { maximumFractionDigits: 6 })} ${toRes.unit.name.toLowerCase()}`,
      subtitle: `${fromRes.unit.symbol} → ${toRes.unit.symbol} • ${formulaStr}`,
      formula: formulaStr,
      reverseQuery: `${rounded} ${toRes.unit.name.toLowerCase()} to ${fromRes.unit.name.toLowerCase()}`
    };
  }
};

if (typeof window !== 'undefined') {
  window.CurrencyRateProvider = CurrencyRateProvider;
  window.ConversionRegistry = ConversionRegistry;
  window.UniversalConversionEngine = UniversalConversionEngine;
}
BillsoftUtils.conversionEngine = UniversalConversionEngine;

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
    let target = { ...detail };
    const legacyTab = target.tab;
    if (target.page === 'paperwork' || target.page === 'purchases' || target.page === 'orders' || target.page === 'statements' || target.page === 'letters') {
      target.page = 'firm';
      target.tab = 'paperwork';
      target.subTab = target.subTab || (
        legacyTab === 'orders' || legacyTab === 'letters' || legacyTab === 'statements'
          ? legacyTab
          : (detail.page === 'statements' ? 'statements' : (detail.page === 'letters' ? 'letters' : 'orders'))
      );
    } else if (target.page === 'firm' && (target.tab === 'orders' || target.tab === 'letters' || target.tab === 'statements')) {
      target.subTab = target.subTab || target.tab;
      target.tab = 'paperwork';
    }
    window.__billsoftPendingNav = { ...target, ts: Date.now() };
    window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: target }));
    setTimeout(() => {
      window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: target }));
    }, 40);
    setTimeout(() => {
      window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: target }));
    }, 150);
  },

  // 1. Comprehensive Multilingual Action & Intent Dictionary with Precision Sub-Tab Targets & Slash Commands
  ACTIONS: [
    {
      id: 'create_invoice',
      title: 'Create Tax Invoice',
      subtitle: 'Open new invoice creation workspace',
      category: 'actions',
      icon: '📄',
      badge: 'Billing',
      slashCommand: '/inv',
      target: { page: 'invoices', subTab: 'create', quickDocType: 'invoice' },
      keywords: [
        // Slash commands & Shorthands
        '/inv', '/bill', '/sale', '/pos', '/newbill', '/taxbill', '/invoice', '/makebill', 'inv', 'bill', 'pos', 'sales',
        // English
        'create invoice', 'new invoice', 'new bill', 'tax invoice', 'sales bill', 'generate bill', 'print invoice', 'make invoice', 'sales order', 'pos bill', 'cash memo', 'billing', 'sell goods', 'order bill', 'add invoice', 'invoice create', 'gst invoice', 'gst bill', 'retail bill', 'sale entry', 'counter bill', 'billing desk', 'make bill', 'start bill', 'new sale', 'quick bill', 'how to make invoice', 'i want to create bill', 'generate new invoice', 'bill a customer', 'prepare sale invoice', 'b2b invoice', 'b2c bill', 'invoice maker', 'instant bill', 'sales voucher', 'counter sale', 'fast bill', 'pos sale', 'barcode bill',
        // Hindi / Hinglish
        'bill banao', 'naya bill', 'bill banana', 'invoice banao', 'bill banaye', 'bikri', 'bikri bill', 'saman becha', 'mal becha', 'bill print karna', 'bill nikalo', 'bill bhejo', 'bill katna', 'bill kaatna', 'naya invoice', 'becho', 'bikri invoice', 'pukka bill', 'pakka bill', 'gst parcha', 'bikri parcha', 'bill faadna', 'bill fado', 'bill kaato', 'invoice kato', 'parcha banao', 'sauda', 'ek naya bill banao', 'bill banana hai', 'bill kaise banaye', 'customer ka bill', 'sale ki entry', 'pakki raseed', 'bikri ka bill', 'sauda darj karo', 'bill nikal do', 'bikri ki raseed', 'rohad bill', 'rokh bikri', 'b2b bill banao', 'b2c bill banao', 'बिल बनाओ', 'नया बिल', 'बिल बनाना है', 'पक्का बिल', 'बिक्री बिल', 'बिल काटो', 'बिल फाड़ो', 'टैक्स इनवॉइस', 'बिल प्रिंट', 'पर्चा बनाओ', 'बिक्री पर्चा',
        // Marathi (Romanized & Devanagari)
        'bill banva', 'naveen bill', 'bill tayar kara', 'bikri bill', 'pavti banva', 'pavti', 'dukan bill', 'mal vikri', 'bill dya', 'bill kadha', 'invoice banva', 'pavti kadha', 'vikri pavti', 'dukanat bill', 'pakkhe bill', 'pakki pavti', 'chalan banva', 'vikri nod', 'mal vikla', 'vikri noond', 'mala bill banvaycha ahe', 'ek nawa bill kadha', 'grahakala bill dya', 'vikri nondva', 'vikri nondani', 'pakki pavti banva', 'vikrilela saman', 'rokh vikri', 'dukanatli vikri', 'grahak pavti', 'नवीन बिल बनवा', 'बिल बनवा', 'नवीन बिल', 'पावती बनवा', 'पावती द्या', 'पावती काढा', 'पक्के बिल', 'विक्री पावती', 'विक्री नोंद', 'विक्री बिल', 'चलन बनवा',
        // Typos & Phonetics
        'invoce', 'invice', 'invoyce', 'bil', 'biil', 'biill', 'billl', 'taxinvoice', 'crat invoice', 'bll', 'bil bano', 'bil bnao', 'naya bil', 'nvin bill', 'bil tayar', 'sale bil', 'crete invoice', 'invocie', 'invois', 'invoise', 'banaoo bill', 'nva bill', 'pakka bil', 'pukka bil', 'pavti bano', 'invoive', 'invoic'
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
      slashCommand: '/quo',
      target: { page: 'invoices', subTab: 'create', quickDocType: 'quotation' },
      keywords: [
        // Slash commands & Shorthands
        '/quo', '/est', '/quote', '/kaccha', '/rough', '/quotation', '/estimate', 'quo', 'quote', 'est', 'estimate',
        // English
        'create quotation', 'new quotation', 'new quote', 'estimate', 'cost estimate', 'price quote', 'rate sheet', 'proforma invoice', 'draft bill', 'price proposal', 'bidding', 'rates', 'add quote', 'create estimate', 'tentative bill', 'rough bill', 'quotation generate', 'send quotation', 'make quote', 'send price quote', 'give estimation', 'proforma bill', 'quotation creation', 'rate estimation', 'quotation maker', 'project estimate', 'rough quotation',
        // Hindi / Hinglish
        'kaccha bill', 'kacha bill', 'kacha bil', 'estimate banao', 'rate batao', 'quotation bhejo', 'andaja bill', 'bhav batao', 'kimat quotation', 'quote banana', 'kacchi receipt', 'andaza bill', 'kaccha parcha', 'rate quotation', 'bhav list', 'kaccha hisab', 'estimate parcha', 'rate parcha', 'kacha parcha banao', 'estimate nikalna hai', 'rate quote karo', 'kacche me bill', 'bhav patra', 'andaja lagao', 'bhav patra banao', 'rough bill banao', 'कच्चा बिल', 'कच्चा पर्चा', 'कोटेशन बनाओ', 'एस्टीमेट', 'भाव बताओ', 'कच्ची रसीद', 'अंदाजा बिल', 'अंदाज पर्चा',
        // Marathi (Romanized & Devanagari)
        'kacha bill', 'andaj patrak', 'andaje bill', 'quotation dya', 'dukan quote', 'dar patrak', 'kimat andaj', 'kacchi pavti', 'andajpatrak', 'bhav sanga', 'quote banva', 'dar suchi', 'bhav patrak', 'andajik bill', 'kaccha pavti', 'kache bill', 'andaje kharch', 'kimat sanga', 'kacche bill kadha', 'andajpatra kadha', 'darpatrak banva', 'अंदाज पत्रक', 'कच्चे बिल', 'दर पत्रक', 'भाव सांगा', 'कोटेशन द्या', 'अंदाजपत्रक', 'कच्ची पावती', 'अंदाज पत्रक बनवा',
        // Typos
        'qoute', 'quot', 'quoat', 'estimat', 'estimet', 'estemate', 'kacha bil', 'qotation', 'qoutation', 'estymate', 'prforma', 'quatation', 'andaj patra', 'andajpatr', 'qutaion', 'kaccha bil', 'kache bil'
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
      slashCommand: '/allinv',
      target: { page: 'invoices', subTab: 'invoices' },
      keywords: [
        // Slash commands & Shorthands
        '/allinv', '/bills', '/invoices', '/saleshistory', '/salelist', '/allbills', 'allinv', 'bills', 'invoices', 'salelist',
        // English
        'all invoices', 'view invoices', 'invoice list', 'sales history', 'past bills', 'invoices list', 'search bills', 'past sales', 'tax bills list', 'show all bills', 'previous bills', 'sales log', 'invoice registers', 'billing history', 'find invoice', 'open invoices', 'filter invoices', 'unpaid invoices', 'paid invoices',
        // Hindi / Hinglish
        'purane bill', 'saare bill', 'bills dekho', 'bikri history', 'bikri list', 'pichle bill', 'saari bikri', 'bikri record', 'bill ki list', 'saare invoice dikhao', 'purana bill nikalo', 'bikri kitni hui', 'saare bill dekho', 'bikri bahi', 'पुराने बिल', 'सारे बिल', 'बिक्री लिस्ट', 'बिल देखो', 'बिक्री हिस्ट्री', 'बिल सूची', 'बिक्री रजिस्टर',
        // Marathi (Romanized & Devanagari)
        'sarva bill', 'puravath bill', 'bill yadi', 'magil bill', 'sagle bill', 'vikri yadi', 'vikri itihas', 'magil vikri', 'sagle bills dakhva', 'billanche list', 'vikri nondvahi', 'सर्व बिल', 'बिल यादी', 'मागील बिल', 'विक्री यादी', 'सगळे बिल', 'विक्री इतिहास', 'विक्री नोंदवही',
        // Typos
        'invoce list', 'all bils', 'billist', 'invoces', 'past bils', 'billlist', 'saleshist'
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
      slashCommand: '/allquo',
      target: { page: 'invoices', subTab: 'quotations' },
      keywords: [
        // Slash commands & Shorthands
        '/allquo', '/quotes', '/estimates', '/kacchalist', '/allquotes', 'allquo', 'quotes', 'estimates',
        // English
        'all quotations', 'view quotations', 'estimates list', 'quotes list', 'all quotes', 'estimates history', 'proforma list', 'draft estimates', 'quotation records',
        // Hindi / Hinglish
        'saare estimate', 'kacche bill list', 'purane quote', 'quotation list', 'bhav yadi', 'kacche parcho ki list', 'saare quote dikhao', 'कच्चे बिल लिस्ट', 'कोटेशन लिस्ट', 'सारे एस्टीमेट',
        // Marathi (Romanized & Devanagari)
        'sarva quotation', 'andaj patrak yadi', 'kacche bill yadi', 'sarva andajpatrake', 'magil quotes', 'सर्व अंदाजपत्रक', 'अंदाजपत्रक यादी', 'कच्चे बिल यादी',
        // Typos
        'qoute list', 'estiamtes list', 'all qoutes', 'estlist'
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
      slashCommand: '/ret',
      target: { page: 'invoices', subTab: 'returns' },
      keywords: [
        // Slash commands & Shorthands
        '/ret', '/returns', '/creditnote', '/cn', '/refund', '/salesreturn', 'ret', 'returns', 'creditnote', 'cn',
        // English
        'sales return', 'credit note', 'returns list', 'customer return', 'refund invoice', 'returned goods', 'credit notes list', 'return items', 'goods returned', 'reverse invoice', 'return voucher',
        // Hindi / Hinglish
        'mal wapas', 'bikri wapas', 'return bill', 'wapas aya saman', 'credit note banao', 'return maal', 'saman wapas aaya', 'bikri wapsi', 'maal return hua', 'wapsi bill', 'माल वापस', 'बिक्री वापसी', 'क्रेडिट नोट', 'समान वापस आया',
        // Marathi (Romanized & Devanagari)
        'mal parat', 'vikri parat', 'parat chalan', 'credit note kadha', 'parat aalela mal', 'vikri parat pavti', 'parat malachi nond', 'माल परत', 'विक्री परत', 'परत चलन', 'क्रेडिट नोट काढा',
        // Typos
        'sal return', 'crdit note', 'salereturn', 'retun', 'returnd'
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
      slashCommand: '/khata',
      target: { page: 'firm', tab: 'paperwork', subTab: 'statements', statementMode: 'customer' },
      keywords: [
        // Slash commands & Shorthands
        '/khata', '/due', '/debt', '/custstat', '/ledger', '/vasooli', '/udhari', '/receivables', 'khata', 'due', 'debt', 'custstat', 'ledger', 'vasooli', 'udhari',
        // English
        'customer statement', 'customer ledger', 'customer dues', 'outstanding balance', 'pending balance', 'payment reminder', 'debt recovery', 'account statement', 'client dues', 'receivables', 'customer khata', 'hisab kitab', 'ledger balance', 'khata statement', 'party ledger', 'party khata', 'dues list', 'debtors list', 'unpaid balance', 'pending money', 'credit list', 'statement', 'statements', 'how much customer owes', 'who has unpaid bills', 'pending payments', 'debtors ledger',
        // Hindi / Hinglish
        'hisab', 'hisaab', 'hisab kitab', 'khata', 'grahak hisab', 'grahak ka hisab', 'udhaari', 'udhari', 'baki paisa', 'baki hisab', 'baaki', 'baki lena', 'grahak dues', 'customer reminder', 'khata bahi', 'len den', 'vasooli', 'vasuli reminder', 'udhar vasooli', 'kiske baki hai', 'kitna paisa lena hai', 'grahak ka baki', 'lena baki', 'udhari list', 'baki bahi', 'khata book', 'bahikhata', 'baki hishob', 'kiska kitna udhar hai', 'paisa kab aayega', 'udhari kitni baki hai', 'bahikhata kholo', 'ग्राहक हिसाब', 'खाता', 'उधारी', 'बाकी पैसा', 'उधारी लिस्ट', 'वसूली', 'खाता बही', 'किसे कितना लेना है', 'बाकी हिसाब', 'लेन देन', 'खाता बुक',
        // Marathi (Romanized & Devanagari)
        'khatedar', 'grahak hishob', 'khate hishob', 'bakki', 'bakki shillak', 'baki rakkam', 'udhari khate', 'lekhajokha', 'jama kharch', 'hishob patrak', 'pavti hishob', 'grahak yadi', 'yene baki', 'udhari list', 'grahak baki', 'kiti paise yene ahet', 'yene rakkam', 'baki hishob', 'shillak baki', 'khate wahi', 'khata wahi', 'konache paise baki ahet', 'udhari kiti ahe', 'grahakache khate', 'yene baki patrak', 'ग्राहक हिशोब', 'उधारी खाते', 'येणे बाकी', 'बाकी रक्कम', 'हिशोब पत्रक', 'खाते वही', 'कोणाचे पैसे बाकी आहेत', 'उधारी यादी', 'शिल्लक बाकी',
        // Typos
        'statment', 'stetement', 'statemnt', 'hisabkitab', 'hisabb', 'udharii', 'udhari list', 'clint statement', 'ledgr', 'legder', 'baaki list', 'khata book', 'hisaab kitaab', 'hisab ktab', 'hisab kithab', 'khta'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'paperwork', subTab: 'statements', statementMode: 'customer' });
      }
    },
    {
      id: 'vendor_statements',
      title: 'Vendor Statements & Supplier Ledger',
      subtitle: 'Check vendor hisab, pending payables & purchase bills',
      category: 'actions',
      icon: '🏭',
      badge: 'Vendor Khata',
      slashCommand: '/venstat',
      target: { page: 'firm', tab: 'paperwork', subTab: 'statements', statementMode: 'party' },
      keywords: [
        // Slash commands & Shorthands
        '/venstat', '/suppstat', '/payables', '/supplierkhata', '/partykhata', '/creditors', 'venstat', 'suppstat', 'payables', 'creditors',
        // English
        'vendor statement', 'supplier statement', 'party statement', 'vendor ledger', 'supplier ledger', 'payables', 'creditors list', 'money to pay', 'supplier dues', 'vendor balance', 'supplier hisab', 'how much we owe supplier', 'bills to pay', 'pending payables', 'supplier account statement', 'creditors ledger',
        // Hindi / Hinglish
        'party ka hisab', 'supplier ka hisab', 'vyapari ka hisab', 'maal wale ka hisab', 'paisa dena hai', 'dena baki', 'kisko paisa dena hai', 'supplier khata', 'vendor baki', 'party hisab', 'party baki', 'vyapari ko kitna dena hai', 'maal wale ka baki', 'supplier ko kitna baki hai', 'सप्लायर हिसाब', 'पार्टी हिसाब', 'देना बाकी', 'व्यापारी खाता', 'किसको पैसा देना है', 'सप्लायर का बाकी',
        // Marathi (Romanized & Devanagari)
        'puravathadar hishob', 'party hishob', 'dene baki', 'vyapari baki', 'mal puravathadar hisab', 'kiti paise dene ahet', 'dene rakkam', 'party baki yadi', 'vyaparache khate', 'puravathadara che dene', 'puravathadar khate wahi', 'पुरवठादार हिशोब', 'देणे बाकी', 'पार्टी हिशोब', 'व्यापारी बाकी', 'किती पैसे देणे आहेत', 'देणे रक्कम',
        // Typos
        'suplier statment', 'vendr ledger', 'puravatha hisab', 'party statment', 'deena baki', 'vender hisab'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'paperwork', subTab: 'statements', statementMode: 'party' });
      }
    },
    {
      id: 'firm_statements',
      title: 'Firm Journal & Master Statement',
      subtitle: 'Complete chronological record of all purchases, sales & payments',
      category: 'actions',
      icon: '📑',
      badge: 'Audit Journal',
      slashCommand: '/daybook',
      target: { page: 'firm', tab: 'paperwork', subTab: 'statements', statementMode: 'firm' },
      keywords: [
        // Slash commands & Shorthands
        '/daybook', '/journal', '/rojnamcha', '/audit', '/cashbook', '/masterledger', 'daybook', 'journal', 'rojnamcha', 'cashbook',
        // English
        'firm statement', 'company ledger', 'journal', 'all transactions', 'audit statement', 'general ledger', 'business statement', 'cash book', 'day book', 'daily journal', 'transaction history', 'master ledger', 'daily sales and purchase log', 'company account', 'financial journal',
        // Hindi / Hinglish
        'dukaan ka hisab', 'firm ka khata', 'saara hisab', 'saari entry', 'rojnamcha', 'roznamcha', 'din bhar ka hisab', 'aaj ka transaction', 'pura hisab', 'dukan ka bahi khata', 'aaj ki jama kharch', 'roj ka jama kharcha', 'दुकान का हिसाब', 'रोजनामचा', 'दिन भर का हिसाब', 'पूरा हिसाब', 'फर्म खाता', 'कैश बुक',
        // Marathi (Romanized & Devanagari)
        'dukanacha hishob', 'sarva transactions', 'rojkird', 'rojvahi', 'rojchya nondi', 'sampurna hishob', 'karkhanacha hishob', 'aajcha hishob', 'sarva lekhajokha', 'rojche vyavahar', 'karkhana journal', 'दुकान हिशोब', 'रोजकीर्द', 'रोजवही', 'संपूर्ण हिशोब', 'रोजच्या नोंदी', 'सर्व व्यवहार',
        // Typos
        'jornal', 'rozkird', 'rojnamchaa', 'daybook', 'cashbok', 'rojkirdh'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'paperwork', subTab: 'statements', statementMode: 'firm' });
      }
    },
    {
      id: 'create_purchase_order',
      title: 'Create Purchase Order (Supplier PO)',
      subtitle: 'Order stock & raw materials from suppliers',
      category: 'actions',
      icon: '📋',
      badge: 'Procurement',
      slashCommand: '/po',
      target: { page: 'firm', tab: 'paperwork', subTab: 'orders' },
      keywords: [
        // Slash commands & Shorthands
        '/po', '/buy', '/order', '/procure', '/purchaseorder', '/newpo', 'po', 'buy', 'purchase',
        // English
        'create purchase order', 'new purchase order', 'create po', 'new po', 'order goods', 'buy inventory', 'procurement', 'order raw material', 'purchase voucher', 'raise po', 'supplier order', 'stock order', 'buy stock', 'order mal', 'purchase requisition', 'purchase orders', 'place order to vendor',
        // Hindi / Hinglish
        'po banao', 'maal mangwana', 'mal khareedna', 'kharidi order', 'order bhejo', 'supplier order', 'naya purchase order', 'saman khareed', 'samaan order', 'kharidi parcha', 'mal order karo', 'stock mangvao', 'order lagao', 'supplier ko order', 'naya mal mangwao', 'खरीदी आर्डर', 'पीओ बनाओ', 'माल मंगवाना', 'सप्लायर आर्डर', 'सामान आर्डर करो', 'नया पीओ',
        // Marathi (Romanized & Devanagari)
        'po banva', 'kharedi order', 'mal kharedi', 'kharedi nod', 'dukan saman order', 'puravatha order', 'saman magva', 'naveen kharedi', 'order dya', 'mal magva', 'kharedi pavti', 'saman order kara', 'puravathadarala order', 'पीओ बनवा', 'खरेदी ऑर्डर', 'माल मागवा', 'सामान ऑर्डर करा', 'नवीन खरेदी', 'पुरवठा ऑर्डर',
        // Typos
        'purchas', 'purhase', 'prchase', 'purchse', 'purcahse', 'puchase order', 'purches', 'po ordr', 'po order', 'kharedi ordr', 'purchese'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'paperwork', subTab: 'orders' });
      }
    },
    {
      id: 'view_orders',
      title: 'All Purchase Orders & Inward Log',
      subtitle: 'Track pending supplier orders, inward shipments & purchases',
      category: 'actions',
      icon: '📦',
      badge: 'Procurement',
      slashCommand: '/allpo',
      target: { page: 'firm', tab: 'paperwork', subTab: 'orders' },
      keywords: [
        '/allpo', '/orders', '/purchases', '/polist', 'allpo', 'orders', 'purchases',
        'all purchase orders', 'po list', 'view po', 'inward orders', 'supplier orders list', 'purchases log',
        'saare po', 'saari kharidi', 'kharidi list', 'order list', 'सारे पीओ', 'खरीदी लिस्ट',
        'sarva kharedi', 'kharedi yadi', 'puravatha orders yadi', 'सर्व खरेदी ऑर्डर', 'खरेदी नोंद'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'paperwork', subTab: 'orders' });
      }
    },
    {
      id: 'write_letter',
      title: 'Write Official Business Letter',
      subtitle: 'Draft official notices, authorizations, certificates & letters',
      category: 'actions',
      icon: '✉️',
      badge: 'Letters',
      slashCommand: '/letter',
      target: { page: 'firm', tab: 'paperwork', subTab: 'letters' },
      keywords: [
        // Slash commands & Shorthands
        '/letter', '/notice', '/chithi', '/memo', '/letterhead', '/certificate', 'letter', 'notice', 'memo',
        // English
        'letter', 'business letter', 'official letter', 'letterpad', 'letterhead', 'memo', 'notice', 'declaration', 'circular', 'certificate', 'authorization letter', 'noc', 'recommendation', 'write letter', 'draft letter', 'issue notice', 'official certificate',
        // Hindi / Hinglish
        'letter banao', 'chithi', 'patra', 'letterhead print', 'official letter', 'notice bhejo', 'patra likho', 'dukan ka letter', 'certificate banao', 'chithi likho', 'लेटर बनाओ', 'चिट्ठी लिखो', 'पत्र लिखो', 'नोटिस भेजो', 'लेटरहेड', 'प्रमाण पत्र',
        // Marathi (Romanized & Devanagari)
        'patra', 'kagadpatra', 'dukan patra', 'official patra', 'kagad', 'patravyavahar', 'patralekhan', 'notice patra', 'dakhal patra', 'letterhead tayar kara', 'patra liha', 'पत्र लिहा', 'कागदपत्रे', 'दुकान पत्र', 'पत्रव्यवहार', 'प्रमाणपत्र',
        // Typos
        'letr', 'leter', 'lettar', 'letrhead', 'letrpad', 'offical letter', 'lettr', 'patrr', 'leterhead'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'paperwork', subTab: 'letters' });
      }
    },
    {
      id: 'view_letters',
      title: 'Official Letters & Correspondence',
      subtitle: 'View sent letters, notices, declarations & certificates',
      category: 'actions',
      icon: '📁',
      badge: 'Letters',
      slashCommand: '/letters',
      target: { page: 'firm', tab: 'paperwork', subTab: 'letters' },
      keywords: [
        '/letters', '/notices', '/allletters', '/patravyavahar', 'letters', 'notices',
        'all letters', 'view letters', 'saved letters', 'letter history', 'notice register',
        'saare letter', 'chithi list', 'patra list', 'सारे पत्र', 'चिट्ठी लिस्ट',
        'sarva patre', 'patravyavahar nond', 'सर्व पत्रे', 'कागदपत्रे यादी'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'paperwork', subTab: 'letters' });
      }
    },
    {
      id: 'hr_payroll',
      title: 'Monthly Payroll & Send Payslips',
      subtitle: 'Calculate staff salaries, advances & send WhatsApp payslips',
      category: 'actions',
      icon: '🧑‍💼',
      badge: 'HR / Payroll',
      slashCommand: '/pay',
      target: { page: 'hr', hrTab: 'payroll' },
      keywords: [
        // Slash commands & Shorthands
        '/pay', '/payroll', '/salary', '/slip', '/tankha', '/pagar', '/payslip', 'pay', 'salary', 'payroll', 'pagar', 'tankha',
        // English
        'payslip', 'send payslip', 'pay slip', 'salary slip', 'salary statement', 'salary voucher', 'wage slip', 'compensation slip', 'salary sheet', 'staff payment', 'salary download', 'employee salary', 'payroll', 'monthly salary', 'salary calculation', 'staff payslips', 'whatsapp payslip', 'pay staff', 'employee salary list',
        // Hindi / Hinglish
        'salary slip', 'payslip bhejo', 'tankhah', 'tankha', 'pagar', 'pagar slip', 'salary do', 'kamdar salary', 'staff pagar', 'naukar ki salary', 'karmachari pagar', 'pagar chi pavti', 'pagar bhejo', 'salary certificate', 'salary nikaal', 'pagar book', 'tankha slip', 'pagar banao', 'tankhwa do', 'salary kitni bani', 'naukar pagar', 'staff ko salary deni hai', 'tankha kitni hui', 'मंथली सैलरी', 'सैलरी स्लिप', 'पगार', 'तनख्वाह', 'कर्मचारी पगार', 'पगार स्लिप', 'सैलरी भेजो',
        // Marathi (Romanized & Devanagari)
        'pagar slip', 'pagar patrak', 'pagar dya', 'pagar chi chithi', 'kamgar pagar', 'karmachari yadi', 'naukar pagar', 'pagar patra', 'pagar vadhav', 'pagar receipt', 'mahinyacha pagar', 'kamgar nondani', 'pagar vatan', 'kamgar salary', 'pagar hishob', 'pagar pathva', 'kamgarana pagar dya', 'pagar kiti zala', 'पगार स्लिप', 'पगार पत्रक', 'कामगार पगार', 'पगार द्या', 'महिन्याचा पगार', 'कर्मचारी पगार', 'पगार हिशोब', 'पगार पावती',
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
      slashCommand: '/att',
      target: { page: 'hr', hrTab: 'attendance' },
      keywords: [
        // Slash commands & Shorthands
        '/att', '/attendance', '/hazari', '/punch', '/hajeri', '/leaves', 'att', 'attendance', 'hazari', 'hajeri',
        // English
        'staff attendance', 'attendance sheet', 'mark attendance', 'daily attendance', 'staff punch', 'absent list', 'present staff', 'leave tracker', 'attendance record', 'who is absent', 'who is present', 'attendance', 'punch log', 'employee attendance', 'daily punch', 'muster roll', 'shift log',
        // Hindi / Hinglish
        'hazari', 'haziri', 'attendance lagao', 'staff hazari', 'kon nahi aaya', 'kon aaya hai', 'chutti list', 'aaj kon present hai', 'hazari lagana', 'hazari book', 'karmachari hazari', 'aaj kon kon aaya', 'attendance sheet bharo', 'हाजिरी', 'हाजिरी लगाओ', 'स्टाफ हाजिरी', 'कौन नहीं आया', 'छुट्टी लिस्ट', 'आज कौन प्रेजेंट है', 'हाजिरी रजिस्टर',
        // Marathi (Romanized & Devanagari)
        'hajeri', 'kamgar hajeri', 'hajeri patrak', 'aaj kon aale', 'aaj kon gairhajir', 'hajeri nond', 'gairhajeri', 'hajeri book', 'kamgar attendance', 'aaj chi hajeri', 'karmachari hajeri', 'हजेरी', 'कामगार हजेरी', 'हजेरी पत्रक', 'आज कोण आले', 'गैरहजेरी', 'हजेरी नोंद', 'हजेरी वही',
        // Typos
        'attandance', 'atendance', 'attendence', 'hazari', 'hajiri', 'atendanc', 'punching', 'attndance', 'hajri', 'hazri'
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
      slashCommand: '/adv',
      target: { page: 'hr', hrTab: 'advances' },
      keywords: [
        // Slash commands & Shorthands
        '/adv', '/advance', '/loan', '/uchal', '/deduction', 'adv', 'advance', 'loan', 'uchal',
        // English
        'salary advance', 'employee advance', 'staff advance', 'loan to staff', 'advance payment', 'advance salary', 'advance deduction', 'advances', 'staff loan', 'salary loan', 'advance tracking',
        // Hindi / Hinglish
        'advance diya', 'advance pagar', 'salary advance', 'staff ko advance', 'advance entry', 'advance katauti', 'advance hisab', 'kamdar ko advance', 'सैलरी एडवांस', 'एडवांस दिया', 'एडवांस कटौती', 'स्टाफ एडवांस', 'लोन दिया',
        // Marathi (Romanized & Devanagari)
        'advance dila', 'pagar advance', 'kamgar advance', 'advance rakkam', 'advance kapat', 'uchal', 'kamgar uchal', 'advance hishob', 'एडव्हान्स दिला', 'पगार ॲडव्हान्स', 'उचल', 'कामगार उचल', 'ॲडव्हान्स रक्कम', 'कपात',
        // Typos
        'advanc', 'advanse', 'advans', 'uchall', 'advanz'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'hr', hrTab: 'advances' });
      }
    },
    {
      id: 'hr_employees',
      title: 'Staff Directory & Employee Master',
      subtitle: 'Manage employee profiles, designations, salaries & contacts',
      category: 'actions',
      icon: '👥',
      badge: 'HR / Staff',
      slashCommand: '/staff',
      target: { page: 'hr', hrTab: 'staff' },
      keywords: [
        '/staff', '/employees', '/kamgar', '/karmachari', '/team', 'staff', 'employees', 'kamgar',
        'employee master', 'staff directory', 'all staff', 'all employees', 'add employee', 'manage team',
        'karmachari list', 'staff ki list', 'naukar list', 'कर्मचारी लिस्ट', 'स्टाफ डायरेक्टरी',
        'kamgar yadi', 'karmachari nondani', 'कामगार यादी', 'कर्मचारी नोंदणी'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'hr', hrTab: 'staff' });
      }
    },
    {
      id: 'add_customer',
      title: 'Add New Customer',
      subtitle: 'Register customer with phone, GSTIN & opening balance',
      category: 'actions',
      icon: '👤',
      badge: 'Customers',
      slashCommand: '/cust',
      target: { page: 'firm', tab: 'customers' },
      keywords: [
        // Slash commands & Shorthands
        '/cust', '/addcust', '/nc', '/newcust', '/grahak', 'cust', 'addcust', 'grahak',
        // English
        'add customer', 'new customer', 'create customer', 'add client', 'new buyer', 'register customer', 'customer contact', 'customer directory', 'customers', 'all customers', 'new debtor', 'add account', 'save customer', 'customer onboard', 'client registration',
        // Hindi / Hinglish
        'grahak jodo', 'naya grahak', 'customer banao', 'customer add karo', 'naya client', 'khata kholo', 'naya khata', 'party jodo', 'naya grahak banao', 'grahak ka number likho', 'naya khatedar', 'customer jodo', 'naya customer', 'naya customer jodo', 'ग्राहक जोड़ो', 'नया ग्राहक', 'कस्टमर जोड़ो', 'नया खाता खोलो', 'पार्टी जोड़ो',
        // Marathi (Romanized & Devanagari)
        'naveen grahak', 'grahak joda', 'grahak nondani', 'naveen khatedar', 'khate suru kara', 'grahak nond', 'sarva grahak', 'nawa grahak joda', 'grahakache nav liha', 'nawa customer joda', 'nawa customer', 'nava customer', 'customer joda', 'नवीन ग्राहक', 'ग्राहक जोडा', 'नवीन खातेदार', 'ग्राहक नोंदणी', 'खाते सुरू करा',
        // Typos
        'cutomer', 'custmer', 'custmor', 'custemer', 'customr', 'ad customer', 'new cust', 'grahak add', 'custmr add'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'customers' });
        window.dispatchEvent(new CustomEvent('billsoft:open-add-customer'));
      }
    },
    {
      id: 'view_customers',
      title: 'Customer Directory & Address Book',
      subtitle: 'Browse all registered customers, dues & phone contacts',
      category: 'actions',
      icon: '📖',
      badge: 'Customers',
      slashCommand: '/customers',
      target: { page: 'firm', tab: 'customers' },
      keywords: [
        '/customers', '/allcust', '/clientlist', '/debtors', 'customers', 'allcust',
        'view customers', 'all customers list', 'customer directory', 'client book', 'debtors directory',
        'saare grahak', 'customer list', 'grahako ki list', 'सारे ग्राहक', 'कस्टमर लिस्ट',
        'sarva grahak', 'grahak yadi', 'सर्व ग्राहक', 'ग्राहक यादी'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'customers' });
      }
    },
    {
      id: 'add_product',
      title: 'Add New Product / Inventory Item',
      subtitle: 'Add item name, SKU, price, HSN code & opening stock',
      category: 'actions',
      icon: '📦',
      badge: 'Catalog',
      slashCommand: '/prod',
      target: { page: 'firm', tab: 'inventory' },
      keywords: [
        // Slash commands & Shorthands
        '/prod', '/addprod', '/item', '/stock', '/newitem', '/sku', 'prod', 'item', 'stock',
        // English
        'add product', 'new product', 'create item', 'add item', 'new stock', 'inventory add', 'add goods', 'new sku', 'barcode item', 'pricing', 'add item to catalog', 'inventory', 'stock list', 'all products', 'item list', 'catalogue', 'create sku', 'stock entry',
        // Hindi / Hinglish
        'item jodo', 'naya item', 'saman jodo', 'naya saman', 'naya maal', 'maal add karo', 'vastu jodo', 'rate list entry', 'item banao', 'stock dekho', 'naya maal aaya', 'samaan register karo', 'आइटम जोड़ो', 'नया आइटम', 'सामान जोड़ो', 'नया माल', 'स्टॉक जोड़ो', 'वस्तु जोड़ो',
        // Marathi (Romanized & Devanagari)
        'naveen vastu', 'vastu joda', 'saman nondani', 'mal joda', 'naveen mal', 'bhandar nond', 'dar suchi', 'sarva vastu', 'shillak mal', 'nava mal joda', 'samanachi nond kara', 'नवीन वस्तू', 'वस्तू जोडा', 'सामान नोंदणी', 'नवीन माल', 'माल जोडा', 'शिल्लक माल',
        // Typos
        'prduct', 'prodct', 'itm', 'itms', 'stck', 'inventry', 'invntory', 'stok', 'naya itam', 'naveen itam'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'inventory' });
        window.dispatchEvent(new CustomEvent('billsoft:open-add-product'));
      }
    },
    {
      id: 'view_inventory',
      title: 'Inventory & Product Catalog',
      subtitle: 'Check real-time stock levels, pricing, HSN codes & low stock',
      category: 'actions',
      icon: '📊',
      badge: 'Inventory',
      slashCommand: '/inventory',
      target: { page: 'firm', tab: 'inventory' },
      keywords: [
        '/inventory', '/products', '/items', '/stocklist', '/catalog', 'inventory', 'products', 'items',
        'view stock', 'all products list', 'item catalog', 'inventory status', 'stock balance',
        'saara stock', 'maal ka hisab', 'saman list', 'स्टॉक लिस्ट', 'इन्वेंट्री', 'सामान लिस्ट',
        'shillak mal yadi', 'vastu suchi', 'सर्व माल', 'शिल्लक माल', 'साठा नोंद'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'inventory' });
      }
    },
    {
      id: 'add_vendor',
      title: 'Add New Vendor / Supplier',
      subtitle: 'Add wholesale supplier with contact & opening balance',
      category: 'actions',
      icon: '🏭',
      badge: 'Parties',
      slashCommand: '/vend',
      target: { page: 'firm', tab: 'parties' },
      keywords: [
        // Slash commands & Shorthands
        '/vend', '/addparty', '/supp', '/newvendor', '/wholesaler', 'vend', 'supp', 'party',
        // English
        'add vendor', 'new vendor', 'add party', 'new party', 'add supplier', 'new supplier', 'wholesaler', 'distributor', 'register vendor', 'all vendors', 'suppliers list', 'merchant', 'dealer', 'supplier onboard',
        // Hindi / Hinglish
        'party jodo', 'naya vyapari', 'supplier jodo', 'naya supplier', 'naya party', 'maal supplier', 'party banana', 'vyapari yadi', 'naya distributor jodo', 'सप्लायर जोड़ो', 'पार्टी जोड़ो', 'नया व्यापारी', 'नया सप्लायर', 'होलसेलर जोड़ो',
        // Marathi (Romanized & Devanagari)
        'naveen party', 'puravathadar joda', 'vyapari joda', 'naveen puravathadar', 'party nondani', 'sarva vyapari', 'nawa vyapari joda', 'नवीन पार्टी', 'पुरवठादार जोडा', 'व्यापारी जोडा', 'नवीन पुरवठादार', 'पार्टी नोंदणी',
        // Typos
        'suplier', 'supplr', 'vendr', 'vendur', 'prty', 'puravatha'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'parties' });
        window.dispatchEvent(new CustomEvent('billsoft:open-add-party'));
      }
    },
    {
      id: 'view_parties',
      title: 'Vendor & Supplier Directory',
      subtitle: 'Browse all wholesale suppliers, contact persons & balances',
      category: 'actions',
      icon: '🏭',
      badge: 'Parties',
      slashCommand: '/vendors',
      target: { page: 'firm', tab: 'parties' },
      keywords: [
        '/vendors', '/suppliers', '/parties', '/creditors', 'vendors', 'suppliers', 'parties',
        'view vendors', 'all suppliers list', 'party directory', 'wholesaler directory',
        'saare supplier', 'vyapari list', 'सारे सप्लायर', 'व्यापारी लिस्ट',
        'sarva puravathadar', 'vyapari yadi', 'सर्व पुरवठादार', 'व्यापारी यादी'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'parties' });
      }
    },
    {
      id: 'record_expense',
      title: 'Record Daily Expense',
      subtitle: 'Log office expenses, rent, bills, fuel & petty cash',
      category: 'actions',
      icon: '💸',
      badge: 'Planner',
      slashCommand: '/exp',
      target: { page: 'planner', plannerTab: 'expenses' },
      keywords: [
        // Slash commands & Shorthands
        '/exp', '/expense', '/kharcha', '/spent', '/pettycash', '/cost', 'exp', 'expense', 'kharcha', 'pettycash',
        // English
        'record expense', 'add expense', 'new expense', 'log expense', 'spent', 'cost', 'daily expense', 'petty cash', 'tea expense', 'rent', 'electricity bill', 'payment out', 'cash outflow', 'office expenses', 'expenses list', 'view expenses', 'chai kharcha', 'fuel expense', 'travel expense', 'expenses', 'pouch money', 'office bills', 'log cost',
        // Hindi / Hinglish
        'kharcha', 'kharch', 'kharcha jodo', 'paisa diya', 'roz ka kharcha', 'dukaan kharcha', 'rent diya', 'bill bhara', 'office kharch', 'petrol kharcha', 'kiraya', 'kharche ka hisab', 'kharcha entry', 'aaj ka kharcha', 'paisa gaya', 'chhota kharcha', 'chai nashta kharcha', 'खर्चा', 'खर्च जोड़ो', 'पैसा दिया', 'दुकान का खर्चा', 'किराया दिया', 'बिजली बिल', 'चाय का खर्चा', 'पेट्रोल खर्चा',
        // Marathi (Romanized & Devanagari)
        'kharch', 'rozacha kharch', 'dukan kharch', 'bhade dile', 'bijli bill', 'kharchachi nond', 'shillak kharch', 'rokh kharch', 'mahinyacha kharch', 'kharch entry', 'naveen kharch', 'aajcha kharch', 'chaha kharch', 'petrol kharch', 'kharcha liha', 'खर्च', 'रोजचा खर्च', 'खर्च नोंदवा', 'दुकान खर्च', 'भाडे दिले', 'चहा खर्च', 'लाईट बिल', 'पैसे दिले',
        // Typos
        'expnse', 'expens', 'expenc', 'exps', 'khrcha', 'spnd', 'outflo', 'paty cash', 'expence', 'kharchaa', 'khrch'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'planner', plannerTab: 'expenses' });
        window.dispatchEvent(new CustomEvent('billsoft:open-add-expense'));
      }
    },
    {
      id: 'view_expenses',
      title: 'Daily Expenses & Petty Cash Log',
      subtitle: 'Review category breakdowns, monthly totals & outflow',
      category: 'actions',
      icon: '📉',
      badge: 'Planner',
      slashCommand: '/expenses',
      target: { page: 'planner', plannerTab: 'expenses' },
      keywords: [
        '/expenses', '/allexp', '/kharchalist', '/outflow', 'expenses', 'allexp',
        'all expenses', 'view expenses list', 'expense sheet', 'monthly expenses',
        'saare kharche', 'kharche ki list', 'सारे खर्चे', 'खर्चा लिस्ट',
        'sarva kharch', 'kharchachi yadi', 'सर्व खर्च', 'खर्च नोंदवही'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'planner', plannerTab: 'expenses' });
      }
    },
    {
      id: 'add_saving',
      title: 'Record Investment / Savings',
      subtitle: 'Log SIP, mutual fund, gold or cash savings deposit & link to goals',
      category: 'actions',
      icon: '💰',
      badge: 'Planner',
      slashCommand: '/save',
      target: { page: 'planner', plannerTab: 'savings' },
      keywords: [
        '/save', '/saving', '/bachat', '/invest', '/sip', '/deposit', 'save', 'saving', 'bachat', 'invest', 'sip',
        'add saving', 'new saving', 'record saving', 'log saving', 'invest money', 'sip investment', 'gold saving', 'fixed deposit', 'emergency fund', 'savings ledger', 'savings', 'deposits',
        'bachat karo', 'bachat jodo', 'paisa bachao', 'invest karo', 'sip katao', 'बचत करो', 'बचत जोड़ो', 'पैसा बचाओ', 'निवेश करो',
        'bachat nondwa', 'paise vachva', 'shillak theva', 'guntavanuk kara', 'गुंतवणूक करा', 'बचत नोंदवा', 'पैसे वाचवा',
        'savng', 'savngs', 'bachatt', 'invst', 'bchat', 'deposite'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'planner', plannerTab: 'savings' });
        window.dispatchEvent(new CustomEvent('billsoft:open-add-saving'));
      }
    },
    {
      id: 'view_savings',
      title: 'Savings & Reserves Ledger',
      subtitle: 'Review total savings, category allocations, and progress against goals',
      category: 'actions',
      icon: '📈',
      badge: 'Planner',
      slashCommand: '/savings',
      target: { page: 'planner', plannerTab: 'savings' },
      keywords: [
        '/savings', '/investments', '/bachatlist', '/reserves', 'savings', 'investments',
        'all savings', 'view savings list', 'savings sheet', 'monthly savings',
        'saari bachat', 'bachat ki list', 'सारी बचत', 'बचत लिस्ट',
        'sarva bachat', 'bachat yadi', 'सर्व बचत', 'बचत नोंदवही',
        'savng', 'savngs', 'resrve'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'planner', plannerTab: 'savings' });
      }
    },
    {
      id: 'add_goal',
      title: 'Set Goal / Daily Habit Streak',
      subtitle: 'Create savings targets, daily habits, milestones or quit-vice counters',
      category: 'actions',
      icon: '🎯',
      badge: 'Planner',
      slashCommand: '/goal',
      target: { page: 'planner', plannerTab: 'goals' },
      keywords: [
        '/goal', '/streak', '/habit', '/target', '/milestone', 'goal', 'streak', 'habit', 'target',
        'add goal', 'new goal', 'create habit', 'new streak', 'quit habit', 'set target', 'financial goal', 'daily streak', 'habits', 'goals',
        'lakshya banao', 'aadat sudharo', 'streak shuru', 'लक्ष्य बनाओ', 'आदत सुधारो', 'स्ट्रिक शुरू करो',
        'dhyey theva', 'naveen goal', 'सवय लावा', 'ध्येय ठरवा', 'सवयी',
        'gol', 'gaol', 'gool', 'strk', 'hbit', 'habbit', 'dhyey', 'lakshy'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'planner', plannerTab: 'goals' });
        window.dispatchEvent(new CustomEvent('billsoft:open-add-goal'));
      }
    },
    {
      id: 'view_goals',
      title: 'Goals, Streaks & Habits Hub',
      subtitle: 'Track live savings goals, daily check-in streaks and milestones',
      category: 'actions',
      icon: '🔥',
      badge: 'Planner',
      slashCommand: '/goals',
      target: { page: 'planner', plannerTab: 'goals' },
      keywords: [
        '/goals', '/streaks', '/habits', '/targets', 'goals', 'streaks', 'habits',
        'view goals', 'all goals', 'my goals', 'habit tracker', 'streak counter',
        'saare goals', 'meray goals', 'सारे लक्ष्य', 'आदतें',
        'sarva dhyey', 'सवयी', 'gol', 'habits list'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'planner', plannerTab: 'goals' });
      }
    },
    {
      id: 'add_note',
      title: 'Add Sticky Note / Task',
      subtitle: 'Save a quick reminder, task or reminder note to planner',
      category: 'actions',
      icon: '📌',
      badge: 'Planner',
      slashCommand: '/todo',
      target: { page: 'planner', plannerTab: 'board' },
      keywords: [
        // Slash commands & Shorthands
        '/todo', '/note', '/task', '/memo', '/remind', '/yaad', 'todo', 'note', 'task', 'memo',
        // English
        'add note', 'new note', 'create task', 'add reminder', 'todo', 'to do', 'task list', 'sticky note', 'planner board', 'reminder list', 'memo', 'jot down', 'remind me', 'save task', 'quick memo',
        // Hindi / Hinglish
        'note banao', 'task banao', 'yaad rakho', 'reminder lagao', 'kaam note karo', 'parcha note', 'yaad dhyan', 'note likho', 'नोट बनाओ', 'टास्क बनाओ', 'याद रखो', 'रिमाइंडर लगाओ', 'काम नोट करो',
        // Marathi (Romanized & Devanagari)
        'kamachi nond', 'tippan liha', 'aathvan theva', 'task nond', 'kam yadi', 'tippan', 'aathvan', 'कामाची नोंद', 'आठवण ठेवा', 'टिप्पण लिहा', 'टास्क नोंद', 'काम यादी',
        // Typos
        'remider', 'remindr', 'stiky note', 'to do list', 'notte', 'todu', 'todoo'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'planner', plannerTab: 'board' });
        window.dispatchEvent(new CustomEvent('billsoft:open-add-note'));
      }
    },
    {
      id: 'view_board',
      title: 'Planner Board & Task Manager',
      subtitle: 'Manage Kanban tasks, pending reminders & sticky notes',
      category: 'actions',
      icon: '📋',
      badge: 'Planner',
      slashCommand: '/board',
      target: { page: 'planner', plannerTab: 'board' },
      keywords: [
        '/board', '/planner', '/tasks', '/sticky', '/notes', 'board', 'planner', 'tasks',
        'task board', 'kanban board', 'sticky board', 'planner desk', 'to do board',
        'task dekho', 'planner kholo', 'प्लानर बोर्ड', 'टास्क बोर्ड',
        'kamache board', 'नियोजन फलक', 'कामांचा तक्ता'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'planner', plannerTab: 'board' });
      }
    },
    {
      id: 'open_personal_dashboard',
      title: 'Personal Dashboard & Wealth Hub',
      subtitle: 'Switch to personal wealth, savings, goals and expense outflow cockpit',
      category: 'actions',
      icon: '💎',
      badge: 'Dashboard',
      slashCommand: '/personal',
      target: { page: 'dashboard', dashboardSegment: 'personal' },
      keywords: [
        '/personal', '/wealth', '/myfinances', '/personalfin', 'personal', 'wealth',
        'personal dashboard', 'personal wealth', 'personal finance', 'my goals', 'my savings', 'personal cockpit',
        'nij kharcha', 'personal hisab', 'khud ka kharcha', 'vyaktigat dashboard', 'khud ki bachat', 'personal dashboard kholo',
        'व्यक्तिगत डैशबोर्ड', 'पर्सनल डैशबोर्ड', 'माझे वित्त', 'माझे डॅशबोर्ड',
        'persnal', 'personel', 'dashbord'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'dashboard', dashboardSegment: 'personal' });
      }
    },
    {
      id: 'open_business_dashboard',
      title: 'Business Dashboard & Sales Overview',
      subtitle: 'Switch to business metrics, revenue, receivables and cashflow cockpit',
      category: 'actions',
      icon: '📊',
      badge: 'Dashboard',
      slashCommand: '/biz',
      target: { page: 'dashboard', dashboardSegment: 'business' },
      keywords: [
        '/biz', '/business', '/salesdash', '/maindash', 'biz', 'business',
        'business dashboard', 'sales dashboard', 'business overview', 'revenue analytics', 'turnover',
        'vyapar dashboard', 'dukaan dashboard', 'business hisab', 'dhandha dashboard', 'business cockpit',
        'बिजनेस डैशबोर्ड', 'व्यापार डॅशबोर्ड', 'दुकान डैशबोर्ड',
        'busines', 'bizness', 'dashbord'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'dashboard', dashboardSegment: 'business' });
      }
    },
    {
      id: 'backup_data',
      title: 'Database Backup & Restore',
      subtitle: 'Export or restore database snapshots for offline safekeeping',
      category: 'actions',
      icon: '💾',
      badge: 'Security',
      slashCommand: '/backup',
      target: { page: 'settings', tab: 'backup' },
      keywords: [
        // Slash commands & Shorthands
        '/backup', '/export', '/save', '/restore', '/db', 'backup', 'export', 'restore',
        // English
        'backup', 'download backup', 'export data', 'save database', 'database backup', 'full backup', 'data backup', 'restore data', 'save my data', 'export json', 'offline backup', 'snapshot', 'backup settings',
        // Hindi / Hinglish
        'backup lo', 'data save karo', 'backup download', 'data bacha ke rakho', 'backup file', 'saara data download karo', 'बैकअप लो', 'डाटा सेव करो', 'बैकअप डाउनलोड', 'डाटा सुरक्षित रखो',
        // Marathi (Romanized & Devanagari)
        'backup gya', 'data theva', 'surakshit theva', 'backup utarva', 'सर्व डेटा जतन करा', 'डेटा सुरक्षित ठेवा', 'बॅकअप घ्या',
        // Typos
        'bakup', 'backp', 'bakcup', 'bckup', 'databackup', 'bakup data'
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
      slashCommand: '/update',
      target: { page: 'settings', tab: 'updates' },
      keywords: [
        '/update', '/upgrade', '/version', '/latest', 'update', 'upgrade', 'version',
        'check update', 'software update', 'latest version', 'system update', 'upgrade app', 'app version', 'whats new', 'new release',
        'update karo', 'naya version', 'software update karo', 'app upgrade karo', 'अपडेट करो', 'नया वर्जन', 'सॉफ्टवेयर अपडेट',
        'naveen version', 'update kara', 'software update', 'नवीन आवृत्ती', 'अपडेट करा',
        'updat', 'softwere', 'versn'
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
      slashCommand: '/security',
      target: { page: 'settings', tab: 'security' },
      keywords: [
        '/security', '/pin', '/password', '/locksetting', 'security', 'pin', 'password',
        'security settings', 'pin lock', 'app lock', 'change pin', 'screen timeout', 'access control', 'change password', 'pin code', 'passcode',
        'password badlo', 'pin badlo', 'lock lagao', 'suraksha', 'pin code badalna', 'पासवर्ड बदलो', 'पिन बदलो', 'सुरक्षा सेटिंग',
        'suraksha setting', 'pin badla', 'password badla', 'सुरक्षा सेटिंग्ज', 'पिन बदला', 'पासवर्ड बदला',
        'securty', 'pasword', 'pinlock'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'settings', tab: 'security' });
      }
    },
    {
      id: 'firm_profile',
      title: 'Business Profile & Bank Details',
      subtitle: 'Update company address, logo, bank account, IFSC & UPI ID',
      category: 'actions',
      icon: '🏢',
      badge: 'Profile',
      slashCommand: '/profile',
      target: { page: 'firm', tab: 'profile' },
      keywords: [
        '/profile', '/firm', '/company', '/bankinfo', '/gstininfo', 'profile', 'firm', 'company',
        'business profile', 'firm details', 'company info', 'edit profile', 'bank profile', 'upi profile', 'gst profile', 'upload logo',
        'company details', 'dukan profile', 'dukan ki jankari', 'कंपनी प्रोफाइल', 'दुकान प्रोफाइल',
        'karkhana mahiti', 'vyapar profile', 'कंपनी माहिती', 'व्यवसाय तपशील'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'profile' });
      }
    },
    {
      id: 'open_settings',
      title: 'Open Settings & Preferences',
      subtitle: 'Configure invoice numbering, tax rates, thermal printers & templates',
      category: 'actions',
      icon: '⚙️',
      badge: 'Settings',
      slashCommand: '/settings',
      target: { page: 'settings', tab: 'general' },
      keywords: [
        '/settings', '/config', '/pref', '/setup', 'settings', 'config', 'preferences',
        'preferences', 'tax settings', 'printer settings', 'configuration', 'business setup', 'options', 'thermal printer', 'invoice format',
        'setting badlo', 'tax setting', 'printer setting', 'सेटिंग्स', 'टैक्स सेटिंग', 'प्रिंटर सेटिंग',
        'setting kara', 'kar setting', 'printer mahiti', 'सेटिंग्ज', 'कर सेटिंग्ज'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'settings', tab: 'general' });
      }
    },
    {
      id: 'toggle_theme',
      title: 'Toggle Dark / Light Theme',
      subtitle: 'Switch display contrast mode',
      category: 'actions',
      icon: '🌓',
      badge: 'Display',
      slashCommand: '/theme',
      keywords: [
        '/theme', '/dark', '/light', '/mode', '/night', 'theme', 'dark', 'light', 'nightmode',
        'toggle theme', 'dark mode', 'light mode', 'night mode', 'dark theme', 'black mode', 'color theme', 'switch theme', 'white theme', 'contrast',
        'dark karo', 'light karo', 'raat mode', 'kaala mode', 'safed mode', 'theme badlo', 'डार्क मोड', 'नाइट मोड', 'लाइट मोड', 'थीम बदलो', 'काला मोड',
        'dark mode lav', 'light mode lav', 'theme badla', 'ratri mode', 'रंग बदला', 'डार्क मोड लावा', 'नाईट मोड', 'लाईट मोड',
        'darck mode', 'ligt mode', 'nightmode', 'theem'
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
      slashCommand: '/lock',
      keywords: [
        '/lock', '/screenlock', '/protect', '/tala', 'lock', 'screenlock',
        'lock workspace', 'lock screen', 'lock app', 'pin lock', 'lock session', 'protect screen', 'secure app', 'lock out', 'lock now', 'close screen',
        'screen lock karo', 'lock lagao', 'band karo', 'tala lagao', 'tala maro', 'dukan band karo', 'screen band karo', 'लॉक लगाओ', 'ताला लगाओ', 'स्क्रीन लॉक करो', 'बंद करो',
        'screen band kara', 'lock kara', 'tala lava', 'tala thoka', 'screen band', 'सुरक्षित करा', 'टाळा लावा', 'स्क्रीन बंद करा', 'लॉक करा',
        'lok screen', 'loc app', 'pasword lock', 'tala lagao'
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
      slashCommand: '/sync',
      keywords: [
        '/sync', '/reload', '/refresh', '/taza', 'sync', 'reload', 'refresh',
        'sync data', 'refresh app', 'refresh data', 'reload data', 'sync database', 'in-app refresh', 'sync now', 'fetch latest', 'pull updates',
        'sync karo', 'refresh karo', 'data update karo', 'taza karo', 'सिंक करो', 'रिफ्रेश करो', 'डाटा ताजा करो',
        'sync kara', 'refresh kara', 'data punha aana', 'ताजे करा', 'रिफ्रेश करा', 'डेटा सिंक करा',
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
      slashCommand: '/gst',
      target: { page: 'invoices', subTab: 'invoices' },
      keywords: [
        '/gst', '/tax', '/gstr1', '/gstr3b', '/salesregister', '/ca', 'gst', 'tax', 'gstr1', 'gstr3b',
        'gst report', 'tax report', 'sales report', 'gstr 1', 'gstr 3b', 'gst summary', 'tax summary', 'sales register', 'ca report', 'audit report', 'hsn summary', 'tax register', 'tax filings', 'monthly sales report',
        'gst hisab', 'tax hishob', 'gst file', 'tax report nikalo', 'gst parcha', 'tax parcha', 'ca ko bhejna hai', 'gst return report', 'जीएसटी रिपोर्ट', 'टैक्स रिपोर्ट', 'बिक्री रजिस्टर', 'जीएसटीआर १', 'जीएसटी हिसाब',
        'kar patrak', 'gst patrak', 'vikri patrak', 'ca sathi report', 'कर पत्रक', 'जीएसटी पत्रक', 'विक्री नोंदवही', 'सीए रिपोर्ट',
        'gstr', 'tax reprt', 'gst summary'
      ],
      action: (ctx) => {
        BillsoftSearchEngine.dispatchNavigate({ page: 'invoices', subTab: 'invoices' });
      }
    },
    {
      id: 'open_upi_qr',
      title: 'Live UPI Payment QR Code Generator',
      subtitle: 'Generate on-screen payment QR for any customer amount',
      category: 'actions',
      icon: '📱',
      badge: 'UPI Tool',
      slashCommand: '/qr',
      keywords: [
        '/qr', '/upi', '/payqr', '/scan', '/bhim', 'qr', 'upi', 'payqr',
        'upi qr', 'generate upi qr', 'payment qr', 'qr code', 'scan and pay', 'receive payment qr', 'bhim upi qr', 'upi code', 'instant payment qr', 'quick qr',
        'upi qr banao', 'qr code nikalo', 'scan karke paisa', 'payment ka qr', 'paisa lene ka qr', 'यूपीआई क्यूआर', 'क्यूआर कोड', 'पेमेंट क्यूआर',
        'upi qr kadha', 'qr code banva', 'paise ghenyasathi qr', 'यूपीआय क्यूआर', 'क्यूआर कोड बनवा',
        'upi code', 'qrcod', 'pay qr'
      ],
      action: () => {
        const queryEvent = new CustomEvent('billsoft:set-omni-query', { detail: 'upi 500' });
        window.dispatchEvent(queryEvent);
      }
    },
    {
      id: 'open_calculators',
      title: 'Commercial & Financial Math Hub',
      subtitle: 'Access GST calculations, cashier change, loan EMI & unit converters',
      category: 'actions',
      icon: '🧮',
      badge: 'Math Tools',
      slashCommand: '/calc',
      keywords: [
        '/calc', '/math', '/gstcalc', '/change', '/split', '/margin', '/emi', 'calc', 'math',
        'calculator', 'math tools', 'gst calculator', 'cash change calculator', 'bill splitter', 'loan emi calculator', 'discount calculator', 'markup calculator', 'profit margin', 'unit converter', 'words to number',
        'calculator kholo', 'hisaab kitb calculator', 'chhoot hisab', 'bache hue paise', 'कैलकुलेटर', 'हिसाब कैलकुलेटर', 'जीएसटी कैलकुलेटर',
        'ganan yantra', 'calculator dakhva', 'hishob calculation', 'कॅल्क्युलेटर', 'हिशोब कॅल्क्युलेटर',
        'calulator', 'claculator'
      ],
      action: () => {
        const queryEvent = new CustomEvent('billsoft:set-omni-query', { detail: 'gst 18% on 5000' });
        window.dispatchEvent(queryEvent);
      }
    },
    {
      id: 'toggle_fullscreen',
      title: 'Toggle Fullscreen Mode',
      subtitle: 'Expand workspace to distraction-free full display',
      category: 'actions',
      icon: '🔳',
      badge: 'Display',
      slashCommand: '/full',
      keywords: [
        '/full', '/fullscreen', '/screen', '/f11', 'full', 'fullscreen',
        'fullscreen', 'exit fullscreen', 'full screen', 'maximize screen', 'purna screen', 'screen badi karo',
        'फुलस्क्रीन', 'बड़ी स्क्रीन'
      ],
      action: () => {
        if (!document.fullscreenElement) {
          document.documentElement.requestFullscreen().catch(() => { });
        } else {
          document.exitFullscreen().catch(() => { });
        }
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

  // 4. Safe Math Evaluator (Arithmetic & Percentage Engine)
  evaluateMath(expr) {
    if (!expr || typeof expr !== 'string') return null;
    const raw = expr.trim();

    // Check 1: Percentage of query ("5% of 20000", "15% on 8500", "15 percent of 8500", "20000 ka 5%", "20000 cha 5%", "5% * 20000", "20000 * 5%")
    const pctOfMatch = raw.match(/^(\d+(?:\.\d+)?)\s*(?:%|percent|take|pratishat)\s*(?:of|on|pe|var|madhe|in|\*|x)?\s*(?:₹|rs\.?|\$)?\s*(\d+(?:\.\d+)?)$/i) ||
      raw.match(/^(?:what\s+is\s+)?(?:₹|rs\.?|\$)?\s*(\d+(?:\.\d+)?)\s*(?:ka|cha|ke|chi|pe|var|\*|x|of|on)\s*(\d+(?:\.\d+)?)\s*(?:%|percent|take|pratishat)/i) ||
      raw.match(/^(?:calc|calculate)?\s*(\d+(?:\.\d+)?)\s*(?:%|percent)\s*(?:of|on|\*)\s*(?:₹|rs\.?|\$)?\s*(\d+(?:\.\d+)?)$/i);
    if (pctOfMatch) {
      let rate, base;
      if (raw.match(/^(?:what\s+is\s+)?(?:₹|rs\.?|\$)?\s*(\d+(?:\.\d+)?)\s*(?:ka|cha|ke|chi|pe|var|\*|x|of|on)\s*(\d+(?:\.\d+)?)\s*(?:%|percent|take|pratishat)/i)) {
        base = parseFloat(pctOfMatch[1]);
        rate = parseFloat(pctOfMatch[2]);
      } else {
        rate = parseFloat(pctOfMatch[1]);
        base = parseFloat(pctOfMatch[2]);
      }
      if (base > 0 && rate >= 0) {
        const result = (base * rate) / 100;
        const remaining = base - result;
        const totalWith = base + result;
        return {
          type: 'percentage_of',
          expression: `${rate}% of ${BillsoftUtils.formatCurrency(base)}`,
          result: Math.round(result * 100) / 100,
          title: `🔢 ${rate}% of ${BillsoftUtils.formatCurrency(base)} = ${BillsoftUtils.formatCurrency(result)}`,
          subtitle: `Base: ${BillsoftUtils.formatCurrency(base)} • Net (-${rate}%): ${BillsoftUtils.formatCurrency(remaining)} • Gross (+${rate}%): ${BillsoftUtils.formatCurrency(totalWith)}`,
          formatted: BillsoftUtils.formatCurrency(result),
          isCalculatedResult: true,
          isEquation: true,
          priority: 100
        };
      }
    }

    // Check 2: Commercial Percentage Addition ("8500 + 18%", "18% + 8500", "100 + 5%")
    const pctAddMatch1 = raw.match(/^(\d+(?:\.\d+)?)\s*\+\s*(\d+(?:\.\d+)?)\s*%(?:\s*(?:gst|tax))?$/i);
    const pctAddMatch2 = raw.match(/^(\d+(?:\.\d+)?)\s*%\s*\+\s*(\d+(?:\.\d+)?)$/i);
    if (pctAddMatch1 || pctAddMatch2) {
      const base = parseFloat(pctAddMatch1 ? pctAddMatch1[1] : pctAddMatch2[2]);
      const rate = parseFloat(pctAddMatch1 ? pctAddMatch1[2] : pctAddMatch2[1]);
      const addVal = (base * rate) / 100;
      const total = base + addVal;
      return {
        type: 'percentage_add',
        expression: `${BillsoftUtils.formatCurrency(base)} + ${rate}%`,
        result: Math.round(total * 100) / 100,
        title: `🧮 Result: ${BillsoftUtils.formatCurrency(total)} (+${rate}%)`,
        subtitle: `Base: ${BillsoftUtils.formatCurrency(base)} + ${rate}% (${BillsoftUtils.formatCurrency(addVal)}) = ${BillsoftUtils.formatCurrency(total)}`,
        formatted: BillsoftUtils.formatCurrency(total),
        isCalculatedResult: true,
        isEquation: true,
        priority: 100
      };
    }

    // Check 3: Commercial Percentage Subtraction / Discount ("100 - 15%", "8500 - 10%", "100 - 15% discount")
    const pctSubMatch = raw.match(/^(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s*%(?:\s*(?:discount|off))?$/i);
    if (pctSubMatch) {
      const base = parseFloat(pctSubMatch[1]);
      const rate = parseFloat(pctSubMatch[2]);
      const subVal = (base * rate) / 100;
      const total = Math.max(0, base - subVal);
      return {
        type: 'percentage_sub',
        expression: `${BillsoftUtils.formatCurrency(base)} - ${rate}%`,
        result: Math.round(total * 100) / 100,
        title: `🧮 Result: ${BillsoftUtils.formatCurrency(total)} (-${rate}%)`,
        subtitle: `Base: ${BillsoftUtils.formatCurrency(base)} - ${rate}% (${BillsoftUtils.formatCurrency(subVal)}) = ${BillsoftUtils.formatCurrency(total)}`,
        formatted: BillsoftUtils.formatCurrency(total),
        isCalculatedResult: true,
        isEquation: true,
        priority: 100
      };
    }

    // Check 4: General Arithmetic Expressions ("1250 + 450", "25 * 18", "1000 / 4", "(100 + 20) * 5", "2^8", "2**8")
    let clean = raw.replace(/,/g, '').replace(/₹|rs\.?|\$/gi, '').trim();
    clean = clean.replace(/\^/g, '**');

    const mathChars = /^[\d\s\+\-\*\/\.\(\)\%\*]+$/;
    if (mathChars.test(clean) && /[\+\-\*\/]/.test(clean)) {
      try {
        let sanitized = clean.replace(/(\d+(\.\d+)?)%/g, '($1/100)');
        const calc = new Function(`return (${sanitized});`)();
        if (typeof calc === 'number' && !isNaN(calc) && isFinite(calc)) {
          const rounded = Math.round(calc * 10000) / 10000;
          return {
            type: 'arithmetic',
            expression: raw,
            result: rounded,
            title: `🧮 Result: ${BillsoftUtils.formatCurrency(rounded)}`,
            subtitle: `Equation: ${raw} = ${rounded}`,
            formatted: BillsoftUtils.formatCurrency(rounded),
            isCalculatedResult: true,
            isEquation: true,
            priority: 100
          };
        }
      } catch (e) { }
    }

    return null;
  },

  // Helper to normalize natural queries by removing filler & conversational padding
  cleanNaturalQuery(raw) {
    if (!raw) return '';
    return raw.toLowerCase()
      // Remove leading slash if user typed /command
      .replace(/^\//, '')
      // Remove punctuation & currency symbols
      .replace(/[?!,.:;'"(){}\[\]₹$]/g, ' ')
      // Normalize common conversational filler phrases (English, Hindi, Marathi, Hinglish)
      .replace(/\b(?:mujhe|humko|mera|meri|mere|aap|kya|bhai|bhaiya|zara|ek|kripya|please|can you|could you|how to|i want to|i need to|help me|want to|tell me|show me|give me|open|go to|open up|mala|amhi|amche|kahi|thoda|plz|pls|sir|madam|bhaiji|sahab|jarur|krupaya)\b/gi, ' ')
      .replace(/\b(?:karna hai|kar do|karo na|karo|banao na|banaye|banana hai|karna chahta hu|kare|hota hai|lagao|chahiye|dekhna hai|nikalo|bhejo|batao|karaycha ahe|kara na|dakhva|sanga|kadha|dya|pahije|ahe|hote|zala|zali|ahet|liha|likho)\b/gi, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  },

  // Phonetic & Transliteration Normalizer for Indian Vernacular Dialects
  normalizePhonetics(str) {
    if (!str) return '';
    return str.toLowerCase()
      .replace(/aa+/g, 'a')
      .replace(/ee+/g, 'i')
      .replace(/oo+/g, 'u')
      .replace(/ii+/g, 'i')
      .replace(/uu+/g, 'u')
      .replace(/w/g, 'v')
      .replace(/z/g, 'j')
      .replace(/sh/g, 's')
      .replace(/kh/g, 'k')
      .replace(/gh/g, 'g')
      .replace(/th/g, 't')
      .replace(/dh/g, 'd')
      .replace(/ph/g, 'f')
      .replace(/bh/g, 'b')
      .replace(/ch/g, 'c')
      .replace(/(.)\1+/g, '$1'); // collapse duplicate consecutive consonants (e.g. billl -> bil, pakkhe -> pake)
  },

  // 5. Token Fuzzy Matching (Damerau-Levenshtein Distance + Phonetics + Permutations)
  damerauLevenshtein(a, b) {
    return BillsoftUtils.damerauLevenshtein(a, b);
  },

  levenshtein(a, b) {
    return BillsoftUtils.damerauLevenshtein(a, b);
  },

  STOP_WORDS: new Set([
    'a', 'an', 'the', 'is', 'are', 'was', 'were', 'to', 'at', 'in', 'on', 'for', 'of', 'by', 'with', 'from',
    'me', 'my', 'we', 'us', 'you', 'your', 'he', 'him', 'she', 'her', 'it', 'its', 'they', 'them',
    'am', 'pm', 'and', 'or', 'do', 'does', 'did', 'be', 'been', 'being', 'have', 'has', 'had',
    'ko', 'ka', 'ke', 'ki', 'se', 'pe', 'par', 'cha', 'chya', 'var', 'karo', 'kar', 'karna', 'bhejo',
    'dya', 'ghya', 'baje', 'vajta', 'please', 'plz', 'pls'
  ]),

  escapeRegExp(string) {
    return (string || '').replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  },

  fuzzyMatch(query, target) {
    if (!query || !target) return { match: false, score: 0 };
    const qDecoupled = BillsoftUtils.splitGluedTokens(query);
    const q = qDecoupled.toLowerCase().trim();
    const t = target.toLowerCase().trim();

    // 1. Direct match (e.g. /inv, /bill, invoice)
    if (t === q) return { match: true, score: 100, exact: true };
    if (t.startsWith('/') && q.startsWith('/') && t === q) return { match: true, score: 100, exact: true };
    if (t.startsWith('/') && q === t.slice(1)) return { match: true, score: 100, exact: true };
    if (q.startsWith('/') && t === q.slice(1)) return { match: true, score: 100, exact: true };

    // If target is a slash command (e.g. /att, /inv), don't match regular natural text unless query starts with '/'
    if (t.startsWith('/') && !q.startsWith('/')) {
      return { match: false, score: 0 };
    }

    // Stop-word check: if target keyword is a stop-word or short token (<= 2 chars), require exact boundary match
    if (this.STOP_WORDS.has(t) || t.length <= 2) {
      const rx = new RegExp(`\\b${this.escapeRegExp(t)}\\b`, 'i');
      if (rx.test(q)) {
        return { match: true, score: 35, exact: false };
      }
      return { match: false, score: 0 };
    }

    // 2. Word-boundary exact keyword match
    const wordRx = new RegExp(`\\b${this.escapeRegExp(t)}\\b`, 'i');
    if (wordRx.test(q)) {
      const score = 88 + Math.min(12, (t.length / q.length) * 12);
      return { match: true, score, exact: false };
    }

    // 3. Substring match for target including query (e.g. q="invoic", t="invoice")
    if (t.includes(q) && q.length >= 3) {
      const score = 84 + Math.min(16, (q.length / t.length) * 16);
      return { match: true, score, exact: false };
    }

    // 4. Cleaned natural query match
    const qClean = this.cleanNaturalQuery(q);
    if (qClean && qClean !== q) {
      if (t === qClean) return { match: true, score: 98, exact: true };
      const qCleanRx = new RegExp(`\\b${this.escapeRegExp(t)}\\b`, 'i');
      if (qCleanRx.test(qClean)) {
        const score = 88 + Math.min(12, (t.length / qClean.length) * 12);
        return { match: true, score, exact: false };
      }
    }

    // 5. Full-word Damerau-Levenshtein typo & transposition tolerance (e.g. "invocie" -> "invoice", "custoemr" -> "customer")
    const testQuery = qClean || q;
    if (testQuery.length >= 3 && t.length >= 3) {
      const fullDist = this.damerauLevenshtein(testQuery, t);
      const maxLen = Math.max(testQuery.length, t.length);
      const maxAllowed = maxLen <= 5 ? 1 : (maxLen <= 9 ? 2 : 3);
      if (fullDist <= maxAllowed) {
        const typoScore = 94 - (fullDist * 6);
        return { match: true, score: typoScore, exact: false };
      }
    }

    // 6. Phonetic & dialect collapsed match
    const pQ = this.normalizePhonetics(qClean || q);
    const pT = this.normalizePhonetics(t);
    if (pQ && pT && (pT === pQ || (pT.length >= 4 && pQ.length >= 4 && (pT.includes(pQ) || pQ.includes(pT))))) {
      return { match: true, score: 90, exact: false };
    }

    // 7. Token-based Damerau-Levenshtein match with Stop-Word Filtering & Transposition
    const qTokens = (qClean || q).split(/\s+/).filter(tok => tok.length > 1 && !this.STOP_WORDS.has(tok));
    const tTokens = t.split(/\s+/).filter(tok => tok.length > 1 && !this.STOP_WORDS.has(tok));
    if (!qTokens.length || !tTokens.length) return { match: false, score: 0 };

    let matchedTokens = 0;
    let totalScore = 0;
    for (const qTok of qTokens) {
      let bestTokenScore = 0;
      for (const tTok of tTokens) {
        if (tTok === qTok) {
          bestTokenScore = 100;
          break;
        }
        if (qTok.length >= 3 && tTok.length >= 3) {
          if (tTok.startsWith(qTok) || qTok.startsWith(tTok)) {
            bestTokenScore = Math.max(bestTokenScore, 85);
            continue;
          }
          const dist = this.damerauLevenshtein(qTok, tTok);
          const maxLen = Math.max(qTok.length, tTok.length);
          const maxAllowed = maxLen <= 5 ? 1 : 2;
          if (dist <= maxAllowed) {
            const similarity = (1 - dist / maxLen) * 100;
            bestTokenScore = Math.max(bestTokenScore, similarity);
          }
        }
      }
      if (bestTokenScore >= 60) matchedTokens++;
      totalScore += bestTokenScore;
    }

    const coverage = matchedTokens / qTokens.length;
    const avgScore = totalScore / qTokens.length;
    if (coverage >= 0.5 && avgScore >= 55) {
      const finalScore = avgScore * coverage;
      return { match: true, score: finalScore, exact: false };
    }
    return { match: false, score: 0, exact: false };
  },

  // 6. Universal Natural Language Suggestions Builder
  getSuggestions(query) {
    if (!query) return [];
    const q = query.toLowerCase().trim();
    const qClean = this.cleanNaturalQuery(q) || q;
    const suggestions = [];

    // Check financial & utility suggestions ONLY for standalone numbers/percentages (e.g. "18%", "5%")
    if (/^\d+(\.\d+)?\s*%?$/.test(q)) {
      const num = parseFloat(q);
      if (!isNaN(num)) {
        suggestions.push({
          id: 'sug_gst',
          title: `Calculate ${num}% GST / Tax`,
          suggestedKeyword: `gst ${num}% on 10000`,
          icon: '🏛️',
          score: 95
        });
        suggestions.push({
          id: 'sug_discount',
          title: `Apply ${num}% Discount`,
          suggestedKeyword: `${num}% discount on 2500`,
          icon: '🏷️',
          score: 92
        });
        suggestions.push({
          id: 'sug_margin',
          title: `Compute ${num}% Profit Margin`,
          suggestedKeyword: `margin cost 1000 rate ${num}%`,
          icon: '📈',
          score: 90
        });
      }
    }

    for (const act of this.ACTIONS) {
      let bestActScore = 0;
      let matchedKw = '';

      for (const kw of act.keywords) {
        if (kw === q) continue; // exact matches don't need suggestion
        if (kw.startsWith(q) || kw.startsWith('/' + q)) {
          bestActScore = Math.max(bestActScore, 85);
          matchedKw = kw;
          continue;
        }
        if (qClean.length >= 2 && kw.length >= 2) {
          const dist = this.levenshtein(qClean, kw);
          const maxLen = Math.max(qClean.length, kw.length);
          const similarity = (1 - dist / maxLen) * 100;
          if (similarity > bestActScore) {
            bestActScore = similarity;
            matchedKw = kw;
          }
        }
      }

      if (bestActScore >= 45) {
        suggestions.push({
          id: act.id,
          action: act,
          suggestedKeyword: matchedKw || act.title,
          title: act.title,
          icon: act.icon,
          score: bestActScore
        });
      }
    }

    suggestions.sort((a, b) => b.score - a.score);
    return suggestions.slice(0, 4);
  },

  // ─────────────────────────────────────────────────────────────
  // 6. UNIVERSAL NLP SLOT & PARAMETER EXTRACTION HELPERS
  // ─────────────────────────────────────────────────────────────
  NLP: {
    // Extract numbers, scaled currency amounts (k, lakh, crore) and monetary units
    extractAmounts(text) {
      const clean = (text || '').replace(/,/g, '');
      const amounts = [];
      const scales = {
        k: 1000, thousand: 1000, hazar: 1000, hajar: 1000,
        lakh: 100000, lakhs: 100000, lac: 100000, lacs: 100000, l: 100000,
        crore: 10000000, crores: 10000000, cr: 10000000, koti: 10000000,
        million: 1000000, m: 1000000, billion: 1000000000, b: 1000000000
      };

      // 1. Scaled quantities: e.g. "5 lakh", "12.8k", "2 cr", "10 hazar"
      const scaledRegex = /\b(\d+(?:\.\d+)?)\s*(k|thousand|hazar|hajar|lakh|lakhs|lac|lacs|l|crore|crores|cr|koti|million|billion)\b/gi;
      let sm;
      while ((sm = scaledRegex.exec(clean)) !== null) {
        const val = parseFloat(sm[1]);
        const unit = sm[2].toLowerCase();
        if (scales[unit] && !isNaN(val)) {
          amounts.push({ val: val * scales[unit], raw: sm[0], isScaled: true, index: sm.index });
        }
      }

      // 2. Standalone numbers / currency amounts: e.g. "₹12800", "12800", "5000"
      const numRegex = /(?:₹|rs\.?|inr|\$)?\s*(\d+(?:\.\d+)?)/gi;
      let nm;
      while ((nm = numRegex.exec(clean)) !== null) {
        const val = parseFloat(nm[1]);
        if (!isNaN(val) && val > 0) {
          // Avoid duplicate with scaled
          const alreadyMatched = amounts.some(a => Math.abs(a.index - nm.index) < 4);
          if (!alreadyMatched) {
            const afterStr = clean.substring(nm.index + nm[0].length).trim();
            const isPct = /^%|percent|take|dar/i.test(afterStr);
            const isTenure = /^(?:months?|mahine|mahina|years?|saal|varsh|yr|yrs|days?|din)\b/i.test(afterStr);
            if (!isPct && !isTenure) {
              amounts.push({ val, raw: nm[0], isScaled: false, index: nm.index });
            }
          }
        }
      }

      return amounts;
    },

    // Extract percentage rates: e.g. "12%", "at 12%", "12 percent", "rate 9.5", "@18%", "5000 pe 18 gst", "5000 ka gst 18"
    extractRate(text) {
      if (!text) return null;
      const clean = text.replace(/,/g, '');

      // 1. Explicit % symbol or percent word: e.g. "18%", "18 %", "18 percent", "18 take", "18 dar"
      const pctMatch = clean.match(/(\d+(?:\.\d+)?)\s*(?:%|percent|take|dar|\bper\s*cent\b)/i);
      if (pctMatch) return parseFloat(pctMatch[1]);

      // 2. Rate suffix keywords: e.g. "18 gst", "18 tax", "18 discount", "18 byaj", "18 margin"
      const suffixMatch = clean.match(/\b(\d+(?:\.\d+)?)\s*(?:gst|tax|vat|byaj|vyaj|discount|suit|chhut)\b/i);
      if (suffixMatch) {
        const val = parseFloat(suffixMatch[1]);
        if (val <= 100) return val;
      }

      // 3. Rate prefix keywords: e.g. "at 18", "rate 18", "@ 18", "tax 18", "gst 18", "byaj 18", "discount 18", "suit 18", "chhut 18", "pe 18", "ka 18", "cha 18"
      const prefixMatch = clean.match(/(?:at|rate|@|tax|gst|cgst|sgst|igst|vat|byaj|vyaj|discount|suit|chhut|pe|ka|ke|ki|cha|chya|var)\s*(\d+(?:\.\d+)?)(?!\s*(?:months?|mahine|years?|saal|varsh|yr|days?|k|lakh|lac|cr|crore|hazar|thousand))/i);
      if (prefixMatch) {
        const val = parseFloat(prefixMatch[1]);
        if (val <= 100) return val;
      }

      // 4. GST context standard slab matching: [0, 0.25, 3, 5, 12, 18, 28]
      if (/\b(gst|tax|vat|cgst|sgst|igst)\b/i.test(clean)) {
        const nums = clean.match(/\b\d+(?:\.\d+)?\b/g);
        if (nums && nums.length >= 1) {
          const slabs = [0.25, 3, 5, 12, 18, 28];
          for (const s of slabs) {
            if (nums.some(n => parseFloat(n) === s)) {
              return s;
            }
          }
        }
      }

      return null;
    },

    // Extract tenure / duration: e.g. "6 months", "6m", "5 years", "5 yr", "3 saal", "12 mahine"
    extractTenure(text) {
      const clean = (text || '').replace(/,/g, '');
      const mMatch = clean.match(/(\d+(?:\.\d+)?)\s*(?:months?|mahine|mahina|m)\b/i);
      if (mMatch) {
        const m = parseFloat(mMatch[1]);
        return { months: m, years: m / 12, raw: mMatch[0] };
      }
      const yMatch = clean.match(/(\d+(?:\.\d+)?)\s*(?:years?|saal|varsh|yr|yrs|y)\b/i);
      if (yMatch) {
        const y = parseFloat(yMatch[1]);
        return { months: y * 12, years: y, raw: yMatch[0] };
      }
      return null;
    },

    // Extract Indian Phone Number (10 digits)
    extractPhone(text) {
      const m = (text || '').match(/\b([6-9]\d{9})\b/);
      return m ? m[1] : null;
    },

    // Extract Natural Language Date & Time (12h/24h, today, tomorrow, calendar dates, weekday, Hindi/Marathi phrases)
    extractDateTime(text, refDate = new Date()) {
      if (!text) return null;
      if (typeof window !== 'undefined' && window.OmnibarPipeline && window.OmnibarPipeline.RoleResolver && window.OmnibarPipeline.RoleResolver.extractDateTime) {
        return window.OmnibarPipeline.RoleResolver.extractDateTime(text, refDate);
      }
      if (typeof BillsoftOmnibarPipeline !== 'undefined' && BillsoftOmnibarPipeline.RoleResolver && BillsoftOmnibarPipeline.RoleResolver.extractDateTime) {
        return BillsoftOmnibarPipeline.RoleResolver.extractDateTime(text, refDate);
      }
      const q = text.trim();
      let targetDate = new Date(refDate.getTime());
      let hasDate = false;
      let hasTime = false;
      let rawTimeStr = '';
      let rawDateStr = '';

      const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
      const monthMap = {
        january: 0, jan: 0, february: 1, feb: 1, march: 2, mar: 2, april: 3, apr: 3,
        may: 4, june: 5, jun: 5, july: 6, jul: 6, august: 7, aug: 7,
        september: 8, sept: 8, sep: 8, october: 9, oct: 9, november: 10, nov: 10, december: 11, dec: 11
      };

      // 1. Explicit Calendar Dates
      const dateMonthRegex = /(?:\b(?:on|dated|for)\s+)?\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec)(?:\s*,?\s*(\d{4}))?\b/i;
      const dmMatch = q.match(dateMonthRegex);
      if (dmMatch) {
        const day = parseInt(dmMatch[1], 10);
        const mIdx = monthMap[dmMatch[2].toLowerCase()];
        const yr = dmMatch[3] ? parseInt(dmMatch[3], 10) : targetDate.getFullYear();
        if (mIdx !== undefined && day >= 1 && day <= 31) {
          targetDate.setFullYear(yr, mIdx, day);
          hasDate = true;
          rawDateStr = dmMatch[0];
        }
      }

      if (!hasDate) {
        const monthDateRegex = /(?:\b(?:on|dated|for)\s+)?\b(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec)\s+(\d{1,2})(?:st|nd|rd|th)?(?:\s*,?\s*(\d{4}))?\b/i;
        const mdMatch = q.match(monthDateRegex);
        if (mdMatch) {
          const mIdx = monthMap[mdMatch[1].toLowerCase()];
          const day = parseInt(mdMatch[2], 10);
          const yr = mdMatch[3] ? parseInt(mdMatch[3], 10) : targetDate.getFullYear();
          if (mIdx !== undefined && day >= 1 && day <= 31) {
            targetDate.setFullYear(yr, mIdx, day);
            hasDate = true;
            rawDateStr = mdMatch[0];
          }
        }
      }

      if (!hasDate) {
        const isoMatch = q.match(/\b(\d{4})[-/](\d{1,2})[-/](\d{1,2})\b/);
        if (isoMatch) {
          const yr = parseInt(isoMatch[1], 10);
          const mon = parseInt(isoMatch[2], 10) - 1;
          const day = parseInt(isoMatch[3], 10);
          if (mon >= 0 && mon <= 11 && day >= 1 && day <= 31) {
            targetDate.setFullYear(yr, mon, day);
            hasDate = true;
            rawDateStr = isoMatch[0];
          }
        }
      }

      if (!hasDate) {
        const dmyMatch = q.match(/(?:\b(?:on|dated|for)\s+)?\b(\d{1,2})[-/](\d{1,2})(?:[-/](\d{2,4}))?\b/);
        if (dmyMatch) {
          const day = parseInt(dmyMatch[1], 10);
          const mon = parseInt(dmyMatch[2], 10) - 1;
          let yr = dmyMatch[3] ? parseInt(dmyMatch[3], 10) : targetDate.getFullYear();
          if (yr < 100) yr += 2000;
          if (mon >= 0 && mon <= 11 && day >= 1 && day <= 31) {
            targetDate.setFullYear(yr, mon, day);
            hasDate = true;
            rawDateStr = dmyMatch[0];
          }
        }
      }

      // 2. Relative Offsets: "in 10 minutes", "in 2 hours", "in 3 days", "15 min baad"
      const relOffsetMatch = q.match(/\b(?:in\s+)?(\d+)\s*(mins?|minutes?|hrs?|hours?|ghante?|days?|din)\s*(?:baad|later|after)?\b/i);
      if (relOffsetMatch) {
        const val = parseInt(relOffsetMatch[1], 10);
        const unit = relOffsetMatch[2].toLowerCase();
        if (/mins?|minutes?/.test(unit)) {
          targetDate = new Date(targetDate.getTime() + val * 60 * 1000);
        } else if (/hrs?|hours?|ghante?/.test(unit)) {
          targetDate = new Date(targetDate.getTime() + val * 60 * 60 * 1000);
        } else if (/days?|din/.test(unit)) {
          targetDate.setDate(targetDate.getDate() + val);
        }
        hasDate = true;
        hasTime = true;
        rawDateStr = relOffsetMatch[0];
        rawTimeStr = relOffsetMatch[0];
      }

      // 3. Relative Dates: "today", "tomorrow", "kal", "udya", "day after tomorrow", "parso", "parva"
      if (!hasDate) {
        if (/\b(?:day\s+after\s+tomorrow|parso|parva)\b/i.test(q)) {
          targetDate.setDate(targetDate.getDate() + 2);
          hasDate = true;
          rawDateStr = q.match(/\b(?:day\s+after\s+tomorrow|parso|parva)\b/i)[0];
        } else if (/\b(?:tomorrow|kal|udya)\b/i.test(q)) {
          targetDate.setDate(targetDate.getDate() + 1);
          hasDate = true;
          rawDateStr = q.match(/\b(?:tomorrow|kal|udya)\b/i)[0];
        } else if (/\b(?:today|aaj|tonight|this\s+evening)\b/i.test(q)) {
          hasDate = true;
          rawDateStr = q.match(/\b(?:today|aaj|tonight|this\s+evening)\b/i)[0];
        }
      }

      // 4. Weekday detection
      if (!hasDate) {
        const daysMap = {
          sunday: 0, ravivar: 0, aitwar: 0,
          monday: 1, somvar: 1,
          tuesday: 2, mangalwar: 2, mangal: 2,
          wednesday: 3, budhwar: 3, budh: 3,
          thursday: 4, guruwar: 4, brihaspati: 4,
          friday: 5, shukrawar: 5, shukra: 5,
          saturday: 6, shaniwar: 6, shani: 6
        };
        const weekdayMatch = q.match(/\b(?:next|this|on)?\s*(sunday|monday|tuesday|wednesday|thursday|friday|saturday|ravivar|somvar|mangalwar|budhwar|guruwar|shukrawar|shaniwar)\b/i);
        if (weekdayMatch) {
          const targetDay = daysMap[weekdayMatch[1].toLowerCase()];
          if (targetDay !== undefined) {
            const curDay = targetDate.getDay();
            let diff = targetDay - curDay;
            if (diff <= 0) diff += 7;
            targetDate.setDate(targetDate.getDate() + diff);
            hasDate = true;
            rawDateStr = weekdayMatch[0];
          }
        }
      }

      // 5. 12-hour with am/pm
      const time12Match = q.match(/(?:at\s+)?\b(\d{1,2})(?::(\d{2})|\.(\d{2}))?\s*(am|pm)\b/i);
      if (time12Match) {
        let hours = parseInt(time12Match[1], 10);
        const minutes = parseInt(time12Match[2] || time12Match[3] || '0', 10);
        const meridiem = time12Match[4].toLowerCase();
        if (meridiem === 'pm' && hours < 12) hours += 12;
        if (meridiem === 'am' && hours === 12) hours = 0;
        targetDate.setHours(hours, minutes, 0, 0);
        hasTime = true;
        rawTimeStr = time12Match[0];
      }

      // 6. Desi time phrasing
      if (!hasTime) {
        const desiPrefixMatch = q.match(/\b(?:(?:shaam|dopahar|sandhyakali|ratre|raat|evening|night|subah|sakali|morning)(?:\s+(?:ko|chya|la|pe))?\s+)(\d{1,2})(?::(\d{2})|\.(\d{2}))?(?:\s*(?:baje|vajta|vaje))?\b/i);
        const desiSuffixMatch = q.match(/\b(\d{1,2})(?::(\d{2})|\.(\d{2}))?\s*(?:baje|vajta|vaje)(?:\s+(?:ko|chya|la|pe))?(?:\s+(?:shaam|dopahar|sandhyakali|ratre|raat|evening|night|subah|sakali|morning))?\b/i);
        const desiMatch = desiPrefixMatch || desiSuffixMatch;
        if (desiMatch) {
          let hours = parseInt(desiMatch[1], 10);
          const minutes = parseInt(desiMatch[2] || desiMatch[3] || '0', 10);
          const isEvening = /shaam|dopahar|sandhyakali|ratre|raat|evening|night/i.test(desiMatch[0]);
          if (isEvening && hours < 12) hours += 12;
          targetDate.setHours(hours, minutes, 0, 0);
          hasTime = true;
          rawTimeStr = desiMatch[0];
        }
      }

      // 7. 24-hour time
      if (!hasTime) {
        const time24Match = q.match(/(?:at\s+)?\b([01]?\d|2[0-3]):([0-5]\d)\b/i);
        if (time24Match) {
          const hours = parseInt(time24Match[1], 10);
          const minutes = parseInt(time24Match[2], 10);
          targetDate.setHours(hours, minutes, 0, 0);
          hasTime = true;
          rawTimeStr = time24Match[0];
        }
      }

      if (!hasDate && !hasTime) return null;

      const yyyy = targetDate.getFullYear();
      const mm = String(targetDate.getMonth() + 1).padStart(2, '0');
      const dd = String(targetDate.getDate()).padStart(2, '0');
      const isoDate = `${yyyy}-${mm}-${dd}`;
      const monStr = monthNames[targetDate.getMonth()];
      let label = `${targetDate.getDate()} ${monStr} ${yyyy}`;
      const now = new Date();
      if (now.toDateString() === targetDate.toDateString()) label = 'Today';
      else if (new Date(now.getTime() + 86400000).toDateString() === targetDate.toDateString()) label = 'Tomorrow';

      let formatted = label;
      if (hasTime) {
        let h = targetDate.getHours();
        const m = String(targetDate.getMinutes()).padStart(2, '0');
        const mer = h >= 12 ? 'PM' : 'AM';
        h = h % 12 || 12;
        formatted += `, ${h}:${m} ${mer}`;
      }

      let cleanTitle = q
        .replace(/^(?:please\s+)?(?:set\s+reminder\s+for|set\s+reminder|create\s+reminder\s+for|create\s+reminder|add\s+reminder\s+for|add\s+reminder|reminder\s+for|reminder\s+to|remind\s+me\s+to|remind\s+me|reminder|remind|todo\s+to|todo|task\s+to|task|alarm\s+for|alarm|yaad\s+dilao|yaad\s+dilana|aathvan\s+kara|athvan\s+kara)\s+/gi, '');

      if (rawDateStr) {
        for (const p of rawDateStr.split(/\s+/).filter(Boolean)) {
          cleanTitle = cleanTitle.replace(new RegExp(`\\b${p.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'gi'), ' ');
        }
      }
      if (rawTimeStr) {
        for (const p of rawTimeStr.split(/\s+/).filter(Boolean)) {
          cleanTitle = cleanTitle.replace(new RegExp(`\\b${p.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'gi'), ' ');
        }
      }
      cleanTitle = cleanTitle
        .replace(/\b(at|on|for|dated|by|today|tomorrow|yesterday|aaj|kal|udya|parso|tonight|pm|am|baje|vajta|shaam|subah|sakali|dopahar|raat|ko|la|pe)\b/gi, ' ')
        .replace(/\s+/g, ' ')
        .trim();

      if (cleanTitle.length > 0) {
        cleanTitle = cleanTitle.charAt(0).toUpperCase() + cleanTitle.slice(1);
      }

      return {
        date: isoDate,
        dueDate: isoDate,
        formatted,
        timeFormatted: formatted,
        dateFormatted: label,
        label,
        cleanTitle,
        isScheduled: hasDate || hasTime,
        rawMatch: [rawDateStr, rawTimeStr].filter(Boolean).join(' ')
      };
    }
  },

  // ─── 7. SMART IN-PLACE ASSISTANT NLP PARSERS (Universal Order Independence) ───

  // A. Smart Inline Expense Parser ("kharcha 120 chai nashta", "chai nashta 120 kharcha", "expense 500 petrol")
  parseInlineExpense(raw) {
    const q = (raw || '').trim();
    if (!/\b(kharcha|expense|petty|cost|kharch|kharcha\s+jodo)\b/i.test(q)) return null;

    const amounts = this.NLP.extractAmounts(q);
    if (amounts.length > 0) {
      const amount = amounts[0].val;
      const rawAmtEscaped = amounts[0].raw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      let notes = q
        .replace(/\b(kharcha|expense|petty|cost|kharch|kharcha\s+jodo|rs\.?|rupaye?|₹|\$|for|on|pe|cha|chya|ka|ke|ki|ko)\b/gi, ' ')
        .replace(new RegExp('(?:^|\\s+)' + rawAmtEscaped + '(?:\\s+|$)', 'gi'), ' ')
        .replace(/\s+/g, ' ')
        .trim();
      return {
        amount,
        title: notes ? (notes.charAt(0).toUpperCase() + notes.slice(1)) : 'Daily Expense'
      };
    }
    return { partial: true, intent: 'expense' };
  },

  // B. Smart Scheduled Reminder & Task Parser ("todo remind me to call bittu at 11 pm", "remind me to dispatch order tomorrow 4pm", "task check warehouse stock")
  parseInlineReminder(raw) {
    const q = raw.trim();
    if (!q || q.length < 3) return null;

    if (/^(?:note|sticky\s+note|tippan|chitthi)\b/i.test(q)) return null;

    const isExplicitReminder = /\b(remind|reminder|alarm|yaad\s+dilao|yaad\s+dilana|aathvan\s+kara|athvan\s+kara)\b/i.test(q);
    const isTaskOrTodo = /^(?:todo|task|kam|kaam)\b/i.test(q);
    const dt = this.NLP.extractDateTime(q);

    if (!isExplicitReminder && !isTaskOrTodo && !dt) return null;

    let title = (dt && dt.cleanTitle && dt.cleanTitle.length > 0) ? dt.cleanTitle : q
      .replace(/^(?:please\s+)?(?:set\s+reminder\s+for|set\s+reminder|create\s+reminder\s+for|create\s+reminder|add\s+reminder\s+for|add\s+reminder|reminder\s+for|reminder\s+to|remind\s+me\s+to|remind\s+me|reminder|remind|todo\s+to|todo|task\s+to|task|alarm\s+for|alarm|yaad\s+dilao|yaad\s+dilana|aathvan\s+kara|athvan\s+kara)\s+/gi, '')
      .replace(/^(?:todo|task|kam|kaam)\s+(?:to\s+)?/gi, '');

    if (dt && dt.rawMatch) {
      const rawParts = dt.rawMatch.split(/\s+/).filter(Boolean);
      for (const p of rawParts) {
        title = title.replace(new RegExp(`\\b${this.escapeRegExp(p)}\\b`, 'gi'), ' ');
      }
    }

    title = title
      .replace(/\b(at|on|for|today|tomorrow|aaj|kal|udya|parso|tonight|pm|am|baje|vajta|shaam|subah|sakali|dopahar|raat)\b/gi, ' ')
      .replace(/\s+/g, ' ')
      .trim();

    if (title.length > 0) {
      title = title.charAt(0).toUpperCase() + title.slice(1);
    }

    if (!title || title.length < 2) {
      if (isExplicitReminder || isTaskOrTodo) {
        return { partial: true, intent: 'reminder' };
      }
      return null;
    }

    const isScheduled = !!(dt && dt.isScheduled);
    const itemType = (isExplicitReminder || isScheduled) ? 'reminder' : 'task';

    return {
      title,
      dueDate: dt ? (dt.dueDate || dt.date) : null,
      timeFormatted: dt ? (dt.timeFormatted || dt.formatted || dt.label) : null,
      type: itemType,
      isScheduled
    };
  },

  // C. Smart Inline Sticky Note Parser ("note client wants 5% discount", "sticky note check balance")
  parseInlineNote(raw) {
    const q = raw.trim();
    const m = q.match(/^(?:note|sticky\s+note|chitthi|tippan)\s+(.+)$/i);
    if (m && m[1] && m[1].trim().length >= 3) {
      return {
        title: m[1].trim()
      };
    }
    return null;
  },

  // C. Smart Quick Customer Registration ("customer Manoj 9822113344 Kolhapur", "add customer Vijay 9811223344")
  parseInlineCustomer(raw) {
    const q = raw.trim();
    if (!/\b(customer|grahak|client|addcust|addparty)\b/i.test(q)) return null;

    const phone = this.NLP.extractPhone(q);
    const cleaned = q.replace(/\b(customer|grahak|client|addcust|addparty|add|new|naya)\b/gi, '').trim();
    const parts = cleaned.replace(phone || '', '').trim().split(/\s+/).filter(Boolean);

    let name = parts[0] ? parts.slice(0, Math.min(2, parts.length)).join(' ') : '';
    let city = parts.length > 2 ? parts.slice(2).join(' ') : (parts[1] && isNaN(parts[1]) ? parts[1] : '');

    if (name || phone) {
      return {
        name: name || 'New Customer',
        phone: phone || '',
        city: city || ''
      };
    }
    return { partial: true, intent: 'customer' };
  },

  // D. Smart WhatsApp Dues Reminder ("remind rahul", "remind rahul 4500 dues", "rahul dues remind")
  parseInlineDuesReminder(raw, customers = [], firm = {}) {
    const q = raw.trim();
    if (!/\b(remind|dues|hisab|hisaab|udhari|baki|reminder)\b/i.test(q)) return null;

    const amounts = this.NLP.extractAmounts(q);
    const customAmount = amounts.length > 0 ? amounts[0].val : null;

    const searchTokens = q
      .replace(/\b(remind|dues|hisab|hisaab|udhari|baki|reminder|ko|ka|ke|ki|cha|chya|var|pe|whatsapp|send|bhejo)\b/gi, ' ')
      .replace(/\b\d+(\.\d+)?\b/g, ' ')
      .trim()
      .toLowerCase()
      .split(/\s+/)
      .filter(Boolean);

    if (searchTokens.length > 0) {
      const searchName = searchTokens.join(' ');
      const matchedCust = customers.find(c => {
        if (!c || !c.name) return false;
        const cn = c.name.toLowerCase();
        return cn.includes(searchName) || searchTokens.some(tok => tok.length >= 3 && cn.includes(tok));
      });

      if (matchedCust) {
        const bal = customAmount !== null ? customAmount : parseFloat(matchedCust.balance || matchedCust.openingBalance || 0);
        return {
          customer: matchedCust,
          balance: bal,
          name: matchedCust.name,
          phone: matchedCust.phone || ''
        };
      }
    }
    return { partial: true, intent: 'remind' };
  },

  // D2. Smart Staff Attendance Action Parser ("mark attendance for rahul", "rahul attendance", "rahul hajeri")
  parseInlineAttendance(raw, staff = []) {
    const q = raw.trim();
    if (!/\b(attendance|hazari|hajeri|present|absent|punch|gairhajir|gairhajeri)\b/i.test(q)) return null;

    const tokens = q
      .replace(/\b(attendance|hazari|hajeri|present|absent|punch|gairhajir|gairhajeri|mark|lagao|karo|nond|nondva|for|ki|ka|ke|chi|cha|ko|la)\b/gi, ' ')
      .trim()
      .split(/\s+/)
      .filter(Boolean);

    if (tokens.length > 0) {
      const searchName = tokens.join(' ');
      const matchedEmp = staff.find(emp => {
        if (!emp || !emp.name) return false;
        const en = emp.name.toLowerCase();
        return en.includes(searchName) || tokens.some(tok => tok.length >= 3 && en.includes(tok));
      });
      if (matchedEmp) {
        return {
          employee: matchedEmp,
          name: matchedEmp.name
        };
      }
    }
    return { partial: true, intent: 'attendance' };
  },

  // D3. Smart Staff Salary Advance Parser ("5000 advance to ganesh", "ganesh 5000 advance", "advance 5000 ganesh")
  parseInlineAdvance(raw, staff = []) {
    const q = raw.trim();
    if (!/\b(advance|uchal|loan|pagar advance|salary advance)\b/i.test(q)) return null;

    const amounts = this.NLP.extractAmounts(q);
    const amount = amounts.length > 0 ? amounts[0].val : 0;

    const tokens = q
      .replace(/\b(advance|uchal|loan|pagar|salary|diya|dila|entry|katna|to|for|ko|ka|ke|ki|cha|chya|la|rs|rupaye|₹)\b/gi, ' ')
      .replace(/\b\d+(\.\d+)?\b/g, ' ')
      .trim()
      .split(/\s+/)
      .filter(Boolean);

    if (tokens.length > 0) {
      const searchName = tokens.join(' ');
      const matchedEmp = staff.find(emp => {
        if (!emp || !emp.name) return false;
        const en = emp.name.toLowerCase();
        return en.includes(searchName) || tokens.some(tok => tok.length >= 3 && en.includes(tok));
      });
      if (matchedEmp) {
        return {
          employee: matchedEmp,
          name: matchedEmp.name,
          amount
        };
      }
    }
    return { partial: true, intent: 'advance' };
  },

  // E. Smart Business Analytics Snapshot ("today sales", "total udhari", "low stock", "gst report", "bank details")
  parseBusinessMetrics(raw, invoices = [], customers = [], products = []) {
    const q = raw.trim().toLowerCase();

    // 1. Today's Sales Snapshot
    if (/\b(today(?:'s)?|aaj|aajka|aajchi)\b/i.test(q) && /\b(sales?|revenue|income|collection|billing|bikri|dhanda|hisab)\b/i.test(q)) {
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
    if (/\b(total|all|pending|saari|sarva)\b/i.test(q) && /\b(dues|receivables|udhari|outstanding|baki|lena)\b/i.test(q)) {
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
    if (/\b(low|out\s+of|khatam|kam|alert|shortage)\b/i.test(q) && /\b(stock|inventory|saman|mal|items?)\b/i.test(q)) {
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

  // E2. Dedicated GST / Tax Calculator (Universal Slot Extraction: Standard & Reverse/Inclusive)
  parseGst(raw) {
    const q = (raw || '').trim();
    if (!/\b(gst|tax|vat|cgst|sgst|igst)\b/i.test(q)) return null;

    const isReverseGst = /\b(reverse|inclusive|shamil|bina\s+gst|included|incl|shameel)\b/i.test(q) || /\bwith\b.+\bgst\b/i.test(q);
    const rate = this.NLP.extractRate(q);
    const amounts = this.NLP.extractAmounts(q);

    // Find the amount that is NOT the rate
    let amount = null;
    if (rate !== null) {
      const nonRateAmounts = amounts.filter(a => a.val !== rate);
      if (nonRateAmounts.length > 0) {
        amount = nonRateAmounts[0].val;
      } else if (amounts.length > 1) {
        amount = amounts.find(a => a.val !== rate)?.val || amounts[0].val;
      }
    } else if (amounts.length > 0) {
      amount = amounts[0].val;
    }

    if (rate !== null && amount !== null && amount > 0) {
      let baseAmount, gstAmount, totalAmount;
      if (isReverseGst) {
        totalAmount = amount;
        baseAmount = (totalAmount * 100) / (100 + rate);
        gstAmount = totalAmount - baseAmount;
      } else {
        baseAmount = amount;
        gstAmount = (baseAmount * rate) / 100;
        totalAmount = baseAmount + gstAmount;
      }
      return {
        rate,
        baseAmount: Math.round(baseAmount * 100) / 100,
        gstAmount: Math.round(gstAmount * 100) / 100,
        totalAmount: Math.round(totalAmount * 100) / 100,
        cgst: Math.round((gstAmount / 2) * 100) / 100,
        sgst: Math.round((gstAmount / 2) * 100) / 100,
        isReverse: isReverseGst
      };
    }

    // Partial query support (e.g. user typed "gst 5000" or "gst")
    if (amount !== null && amount > 0) {
      return {
        partial: true,
        amount,
        suggestions: [5, 12, 18, 28].map(r => {
          const gst = (amount * r) / 100;
          return { rate: r, base: amount, gst: Math.round(gst * 100) / 100, total: Math.round((amount + gst) * 100) / 100 };
        })
      };
    }

    return { partial: true, intent: 'gst' };
  },

  // F. Cash Register Change Calculator ("change for 2000 bill 1435", "bill 1435 paid 2000 note", "change 500 bill 320", "2000 diya bill 1435", "bill 1435 cash 2000")
  parseChange(raw) {
    const q = (raw || '').trim();
    if (!/\b(change|chutta|baki|wapas|note|tendered|paid|tender|diya|dila|cash)\b/i.test(q)) return null;

    const amounts = this.NLP.extractAmounts(q);
    if (amounts.length >= 2) {
      let tendered = null;
      let bill = null;

      const tenderedMatch = q.match(/(?:tendered|paid|cash|diya|note|dila|given)\s*(?:of|is|rs\.?|₹)?\s*(\d+(?:\.\d+)?)/i) ||
        q.match(/(\d+(?:\.\d+)?)\s*(?:tendered|paid|cash|diya|note|dila|ka\s+note|cha\s+note)/i);
      const billMatch = q.match(/(?:bill|amount|total|charge)\s*(?:of|is|rs\.?|₹)?\s*(\d+(?:\.\d+)?)/i) ||
        q.match(/(\d+(?:\.\d+)?)\s*(?:ka\s+bill|cha\s+bill|bill)/i);

      if (tenderedMatch && billMatch) {
        tendered = parseFloat(tenderedMatch[1]);
        bill = parseFloat(billMatch[1]);
      } else {
        const a1 = amounts[0].val;
        const a2 = amounts[1].val;
        tendered = Math.max(a1, a2);
        bill = Math.min(a1, a2);
      }

      if (tendered >= bill && bill > 0) {
        const change = Math.round((tendered - bill) * 100) / 100;
        const denoms = [500, 200, 100, 50, 20, 10, 5, 2, 1];
        let rem = Math.floor(change);
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
    return { partial: true, intent: 'change' };
  },

  // G. Bill Splitter ("split 4500 by 4", "4 people split 4500", "split 1200 between 3", "12000 4 log me split")
  parseBillSplit(raw) {
    const q = (raw || '').trim();
    if (!/\b(split|divide|divided|vibhagani|baanto|vata|hissa|bantna|per\s+person|pratyeki)\b/i.test(q)) return null;

    const amounts = this.NLP.extractAmounts(q);
    const pMatch = q.match(/\b(\d+)\s*(?:people|persons|jan|log|friends|members|ways|parts|heads)\b/i) ||
      q.match(/(?:by|in|into|between|among|across|madhe|me|janat|logo\s+me)\s+(\d+)\b/i) ||
      q.match(/\b(\d+)\s*(?:janat|logo\s+me|madhe|ways)\b/i);
    const people = pMatch ? parseInt(pMatch[1], 10) : (amounts.length >= 2 ? Math.min(amounts[0].val, amounts[1].val) : null);

    let amount = null;
    if (amounts.length > 0) {
      if (people) {
        const other = amounts.find(a => a.val !== people);
        amount = other ? other.val : amounts[0].val;
      } else {
        amount = amounts[0].val;
      }
    }

    if (amount > 0 && people > 0) {
      const perPerson = amount / people;
      return {
        amount,
        people,
        perPerson: Math.round(perPerson * 100) / 100
      };
    }

    if (amount > 0) {
      return {
        partial: true,
        amount,
        suggestions: [2, 3, 4, 5].map(cnt => ({ count: cnt, perPerson: Math.round((amount / cnt) * 100) / 100 }))
      };
    }

    return { partial: true, intent: 'split' };
  },

  // H. Discount Calculator ("10% discount on 5000", "5000 discount 10%", "5000 less 500", "5000 pe 10% discount")
  parseDiscount(raw) {
    const q = (raw || '').trim();
    if (!/\b(discount|chhut|suit|off|less|kam\s+karo)\b/i.test(q)) return null;

    const rate = this.NLP.extractRate(q);
    const amounts = this.NLP.extractAmounts(q);

    // Percentage discount
    if (rate !== null && amounts.length > 0) {
      const original = amounts.find(a => a.val !== rate)?.val || amounts[0].val;
      if (rate > 0 && original > 0) {
        const discountAmount = (original * rate) / 100;
        const finalPrice = Math.max(0, original - discountAmount);
        return { type: 'pct', original, rate, discountAmount: Math.round(discountAmount * 100) / 100, finalPrice: Math.round(finalPrice * 100) / 100 };
      }
    }

    // Flat discount (e.g. 500 off on 2500)
    if (amounts.length >= 2) {
      const discountAmount = Math.min(amounts[0].val, amounts[1].val);
      const original = Math.max(amounts[0].val, amounts[1].val);
      if (discountAmount > 0 && original > discountAmount) {
        const finalPrice = original - discountAmount;
        const r = (discountAmount / original) * 100;
        return { type: 'flat', original, rate: Math.round(r * 10) / 10, discountAmount, finalPrice };
      }
    }

    return { partial: true, intent: 'discount' };
  },

  // I. Markup Calculator ("25% markup on 800", "markup 800 25%")
  parseMarkup(raw) {
    const q = (raw || '').trim();
    if (!/\b(markup)\b/i.test(q)) return null;

    const rate = this.NLP.extractRate(q);
    const amounts = this.NLP.extractAmounts(q);
    if (rate !== null && amounts.length > 0) {
      const cost = amounts.find(a => a.val !== rate)?.val || amounts[0].val;
      if (rate > 0 && cost > 0) {
        const markupAmount = (cost * rate) / 100;
        const sellingPrice = cost + markupAmount;
        return { cost, rate, markupAmount: Math.round(markupAmount * 100) / 100, sellingPrice: Math.round(sellingPrice * 100) / 100 };
      }
    }
    return null;
  },

  // I2. Profit & Margin Calculator ("margin cost 800 price 1200", "cost 800 sell 1200", "profit 800 1200", "cp 500 sp 800")
  parseProfitMargin(raw) {
    const q = (raw || '').trim();
    if (!/\b(margin|profit|munafa|nafa|cost|selling|cp|sp)\b/i.test(q)) return null;

    const amounts = this.NLP.extractAmounts(q);
    if (amounts.length >= 2) {
      let cost = null;
      let price = null;

      const costMatch = q.match(/(?:cost|buy|cp|kharidi)\s*(?:price|of|is|rs\.?|₹)?\s*(\d+(?:\.\d+)?)/i) ||
        q.match(/(\d+(?:\.\d+)?)\s*(?:cost|cp|kharidi)/i);
      const sellMatch = q.match(/(?:sell|price|sp|bikri|selling)\s*(?:price|of|is|rs\.?|₹)?\s*(\d+(?:\.\d+)?)/i) ||
        q.match(/(\d+(?:\.\d+)?)\s*(?:sell|sp|bikri|selling)/i);

      if (costMatch && sellMatch) {
        cost = parseFloat(costMatch[1]);
        price = parseFloat(sellMatch[1]);
      } else {
        cost = Math.min(amounts[0].val, amounts[1].val);
        price = Math.max(amounts[0].val, amounts[1].val);
      }

      if (price > 0 && cost >= 0) {
        const profit = price - cost;
        const marginPct = (profit / price) * 100;
        const markupPct = cost > 0 ? (profit / cost) * 100 : 0;
        return {
          cost,
          price,
          profit: Math.round(profit * 100) / 100,
          marginPct: Math.round(marginPct * 100) / 100,
          markupPct: Math.round(markupPct * 100) / 100
        };
      }
    }
    return null;
  },

  // J. Commission Calculator ("5% commission on 45000", "45000 pe 5% dalali")
  parseCommission(raw) {
    const q = raw.trim();
    if (!/\b(commission|dalali)\b/i.test(q)) return null;

    const rate = this.NLP.extractRate(q);
    const amounts = this.NLP.extractAmounts(q);
    if (rate !== null && amounts.length > 0) {
      const totalAmount = amounts[0].val;
      if (rate > 0 && totalAmount > 0) {
        const commissionAmount = (totalAmount * rate) / 100;
        return { totalAmount, rate, commissionAmount };
      }
    }
    return null;
  },

  // K. Simple & Compound Interest ("interest 50000 at 8% for 3 yr", "ci 100000 7.5% 2yr")
  parseInterest(raw) {
    const q = raw.trim();
    if (!/\b(interest|byaj|vyaj|si|ci|simple\s+interest|compound\s+interest)\b/i.test(q)) return null;

    const isCompound = /\b(ci|compound|chakravadh)\b/i.test(q);
    const rate = this.NLP.extractRate(q);
    const tenure = this.NLP.extractTenure(q);
    const amounts = this.NLP.extractAmounts(q);
    const principal = amounts.length > 0 ? amounts[0].val : null;

    if (principal !== null && rate !== null && tenure !== null && principal > 0 && rate > 0 && tenure.years > 0) {
      const p = principal;
      const r = rate;
      const t = tenure.years;
      if (isCompound) {
        const amount = p * Math.pow(1 + (r / 100), t);
        const interest = amount - p;
        return { type: 'compound', principal: p, rate: r, time: t, interest: Math.round(interest * 100) / 100, total: Math.round(amount * 100) / 100 };
      } else {
        const interest = (p * r * t) / 100;
        const total = p + interest;
        return { type: 'simple', principal: p, rate: r, time: t, interest: Math.round(interest * 100) / 100, total: Math.round(total * 100) / 100 };
      }
    }
    return { partial: true, intent: 'interest' };
  },

  // L. Universal EMI Loan Calculator (Order-Independent Slot Extraction & Dynamic Completions)
  // Supports: "emi at 12% for 12800 amount for 6months", "12800 emi 6 months 12%", "emi 12800", "emi"
  parseEmi(raw) {
    const q = raw.trim();
    if (!/\b(emi|loan|kist|kisht|karja|hapta|installment|karjacha\s+hapta)\b/i.test(q)) return null;

    let rate = this.NLP.extractRate(q);
    let tenure = this.NLP.extractTenure(q);
    const amounts = this.NLP.extractAmounts(q);
    let principal = amounts.length > 0 ? amounts[0].val : null;

    // Positional slot inference for queries like "emi 500000 9.5 5"
    if (!tenure || rate === null) {
      const allNums = q.match(/\d+(?:\.\d+)?/g);
      if (allNums && allNums.length >= 3) {
        const pCandidate = parseFloat(allNums[0]);
        const rCandidate = parseFloat(allNums[1]);
        const tCandidate = parseFloat(allNums[2]);
        if (pCandidate >= 1000 && rCandidate > 0 && rCandidate <= 100 && tCandidate > 0 && tCandidate <= 50) {
          principal = pCandidate;
          if (rate === null) rate = rCandidate;
          if (tenure === null) tenure = { months: tCandidate * 12, years: tCandidate, raw: `${tCandidate} years` };
        }
      }
    }

    // Full calculation if all 3 slots are present
    if (principal !== null && rate !== null && tenure !== null && principal > 0 && rate > 0 && tenure.years > 0) {
      const p = principal;
      const rAnnual = rate;
      const tenureYears = tenure.years;
      const n = tenure.months;
      const r = (rAnnual / 12) / 100;
      const emi = (p * r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1);
      const totalAmount = emi * n;
      const totalInterest = totalAmount - p;

      return {
        principal: p,
        annualRate: rAnnual,
        tenureYears,
        months: n,
        emi: Math.round(emi),
        totalAmount: Math.round(totalAmount),
        totalInterest: Math.round(totalInterest)
      };
    }

    // Partial Slot Suggestions (e.g. user typed "emi 12800" or "emi")
    const baseP = (principal !== null && principal > 0) ? principal : 500000;
    const isCustomP = principal !== null && principal > 0;

    const sampleVariants = [
      { rate: 12, months: 6, label: '12% • 6 Months' },
      { rate: 10, months: 12, label: '10% • 1 Year' },
      { rate: 9.5, months: 24, label: '9.5% • 2 Years' },
      { rate: 8.5, months: 60, label: '8.5% • 5 Years' }
    ].map(v => {
      const r = (v.rate / 12) / 100;
      const n = v.months;
      const emiVal = Math.round((baseP * r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1));
      const tot = emiVal * n;
      return {
        principal: baseP,
        rate: v.rate,
        months: v.months,
        label: v.label,
        emi: emiVal,
        total: tot,
        syntax: `emi ${baseP} at ${v.rate}% for ${v.months < 12 ? `${v.months} months` : `${v.months / 12} years`}`
      };
    });

    return {
      partial: true,
      principal: principal,
      isCustomPrincipal: isCustomP,
      suggestions: sampleVariants
    };
  },



  // M. Statistical & Numeric Aggregators (Average, Min/Max, Difference, Growth %, Ratio Split)
  parseStatistics(raw) {
    const q = raw.trim().toLowerCase();

    // Average: "average 120 150 180", "avg 10, 20, 30", "sarasari 120 150 180"
    const avgMatch = q.match(/^(?:avg|average|sarasari|ausat|mean)\s+([\d\s,.\-+]+)$/i);
    if (avgMatch) {
      const nums = avgMatch[1].split(/[\s,]+/).map(parseFloat).filter(n => !isNaN(n));
      if (nums.length >= 2) {
        const sum = nums.reduce((a, b) => a + b, 0);
        const avg = sum / nums.length;
        return { type: 'average', nums, count: nums.length, sum, result: Math.round(avg * 100) / 100 };
      }
    }

    // Min / Max: "max 120 450 90", "min 120 450 90"
    const minMaxMatch = q.match(/^(?:max|min|maximum|minimum|sabse\s+bada|sabse\s+chhota|sarvat\s+motha)\s+([\d\s,.\-+]+)$/i);
    if (minMaxMatch) {
      const isMax = /^(?:max|maximum|sabse\s+bada|sarvat\s+motha)/i.test(q);
      const nums = minMaxMatch[1].split(/[\s,]+/).map(parseFloat).filter(n => !isNaN(n));
      if (nums.length >= 2) {
        const val = isMax ? Math.max(...nums) : Math.min(...nums);
        return { type: isMax ? 'max' : 'min', nums, result: val };
      }
    }

    // Absolute Difference & Variance: "difference 15000 12750", "diff 15000 12750", "farak 15000 12750"
    const diffMatch = q.match(/^(?:diff|difference|farak|antar)\s+(\d+(?:\.\d+)?)\s+(?:and|to|se|madhun)?\s*(\d+(?:\.\d+)?)$/i);
    if (diffMatch) {
      const a = parseFloat(diffMatch[1]);
      const b = parseFloat(diffMatch[2]);
      const diff = Math.abs(a - b);
      const pct = a > 0 ? (diff / a) * 100 : 0;
      return { type: 'difference', a, b, diff, pctVariance: Math.round(pct * 100) / 100 };
    }

    // Growth / Variance %: "growth from 4000 to 6000", "growth 4000 6000", "diff % 4000 6000"
    const growthMatch = q.match(/^(?:growth|increase|wadha)\s+(?:from\s+)?(\d+(?:\.\d+)?)\s+(?:to|se|varun)?\s*(\d+(?:\.\d+)?)$/i);
    if (growthMatch) {
      const initial = parseFloat(growthMatch[1]);
      const finalVal = parseFloat(growthMatch[2]);
      if (initial > 0) {
        const diff = finalVal - initial;
        const growthPct = (diff / initial) * 100;
        return { type: 'growth', initial, finalVal, diff, growthPct: Math.round(growthPct * 100) / 100 };
      }
    }

    // Ratio Splitter: "split 10000 in 2:3:5", "divide 10000 by 2:3:5", "ratio 5000 1:4", "10000 la 2:3:5 madhe vata"
    const ratioMatch = q.match(/^(?:split|divide|vata|baanto|ratio)\s+(\d+(?:\.\d+)?)\s+(?:in|into|by|madhe|me|ratio)?\s*(\d+(?::\d+)+)$/i) ||
      q.match(/^(\d+(?:\.\d+)?)\s+(?:in|into|by|madhe|me|ratio)\s*(\d+(?::\d+)+)\s*(?:split|divide|vata|baanto)/i) ||
      q.match(/^(?:ratio)\s+(\d+(?:\.\d+)?)\s+(\d+(?::\d+)+)$/i);
    if (ratioMatch) {
      const totalAmount = parseFloat(ratioMatch[1]);
      const ratioParts = ratioMatch[2].split(':').map(Number);
      const ratioSum = ratioParts.reduce((a, b) => a + b, 0);
      if (totalAmount > 0 && ratioSum > 0) {
        const shares = ratioParts.map(p => Math.round(((p / ratioSum) * totalAmount) * 100) / 100);
        return { type: 'ratio', totalAmount, ratioStr: ratioMatch[2], ratioParts, shares };
      }
    }

    return null;
  },

  // N. Indian Words to Number NLP Parser ("one lakh twenty five thousand", "ek lakh pacchis hazar", "don koti pannas lakh")
  parseWordsToNumber(raw) {
    const s = raw.toLowerCase().replace(/,/g, ' ').replace(/\band\b/g, ' ').replace(/\bcha\b|\bka\b|\bki\b|\bke\b|\brupaye?\b|\brs\b|\binr\b|\bonly\b/g, ' ').trim();
    if (!s) return null;

    const primaryWordRoots = [
      'zero', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine', 'ten',
      'eleven', 'twelve', 'thirteen', 'fourteen', 'fifteen', 'sixteen', 'seventeen', 'eighteen', 'nineteen',
      'twenty', 'thirty', 'forty', 'fifty', 'sixty', 'seventy', 'eighty', 'ninety',
      'hundred', 'thousand', 'lakh', 'lakhs', 'lac', 'lacs', 'crore', 'crores', 'koti', 'million', 'billion',
      'shunya', 'ek', 'do', 'don', 'teen', 'tin', 'char', 'panch', 'pas', 'saha', 'saat', 'sat', 'aath', 'ath', 'nau', 'nav', 'das', 'daha',
      'akara', 'barah', 'bara', 'terah', 'tera', 'chaudah', 'chauda', 'pandrah', 'pandhra', 'solah', 'sola', 'satrah', 'satra', 'atharah', 'athra', 'unnis', 'ekonvis',
      'vis', 'bees', 'ikkyis', 'ekvis', 'baais', 'bavis', 'teis', 'tevis', 'chaubees', 'chouvis', 'pacchis', 'pachis', 'pavis',
      'tees', 'tis', 'paintis', 'pasatis', 'chalis', 'paintalis', 'panchechalis', 'pachas', 'pannas', 'saath', 'sath', 'sattar', 'assi', 'aishi', 'nabbe', 'navvad',
      'sau', 'she', 'shambhar', 'hazar', 'hajar'
    ];

    const tokens = s.split(/\s+/).filter(Boolean);
    const hasWordTokens = tokens.some(t => primaryWordRoots.includes(t));
    if (!hasWordTokens) return null;

    // Reject arithmetic expressions, split, margin, or gst queries
    if (/[\+\-\*\/\%\^\=]/.test(raw) || /\b(split|divide|margin|profit|gst|tax|discount|markup|commission|avg|average|diff|growth|si|ci|emi)\b/i.test(raw)) return null;

    const map = {
      'zero': 0, 'shunya': 0, 'one': 1, 'ek': 1, 'a': 1, 'two': 2, 'do': 2, 'don': 2,
      'three': 3, 'teen': 3, 'tin': 3, 'four': 4, 'char': 4, 'five': 5, 'panch': 5, 'pas': 5,
      'six': 6, 'chha': 6, 'chhah': 6, 'saha': 6, 'seven': 7, 'saat': 7, 'sat': 7, 'eight': 8, 'aath': 8, 'ath': 8,
      'nine': 9, 'nau': 9, 'nav': 9, 'ten': 10, 'das': 10, 'daha': 10, 'eleven': 11, 'gyarah': 11, 'akara': 11,
      'twelve': 12, 'barah': 12, 'bara': 12, 'thirteen': 13, 'terah': 13, 'tera': 13, 'fourteen': 14, 'chaudah': 14, 'chauda': 14,
      'fifteen': 15, 'pandrah': 15, 'pandhra': 15, 'sixteen': 16, 'solah': 16, 'sola': 16, 'seventeen': 17, 'satrah': 17, 'satra': 17,
      'eighteen': 18, 'atharah': 18, 'athra': 18, 'nineteen': 19, 'unnis': 19, 'ekonvis': 19, 'twenty': 20, 'bees': 20, 'vis': 20,
      'twenty one': 21, 'ikkyis': 21, 'ekvis': 21, 'twenty two': 22, 'baais': 22, 'bavis': 22, 'twenty three': 23, 'teis': 23, 'tevis': 23,
      'twenty four': 24, 'chaubees': 24, 'chouvis': 24, 'twenty five': 25, 'pacchis': 25, 'pachis': 25, 'pavis': 25,
      'thirty': 30, 'tees': 30, 'tis': 30, 'thirty five': 35, 'paintis': 35, 'pasatis': 35, 'forty': 40, 'chalis': 40,
      'forty five': 45, 'paintalis': 45, 'panchechalis': 45, 'fifty': 50, 'pachas': 50, 'pannas': 50, 'sixty': 60, 'saath': 60, 'sath': 60,
      'seventy': 70, 'sattar': 70, 'eighty': 80, 'assi': 80, 'aishi': 80, 'ninety': 90, 'nabbe': 90, 'navvad': 90,
      'hundred': 100, 'sau': 100, 'she': 100, 'se': 100, 'shambhar': 100, 'thousand': 1000, 'hazar': 1000, 'hajar': 1000, 'k': 1000,
      'lakh': 100000, 'lac': 100000, 'lakhs': 100000, 'lacs': 100000, 'crore': 10000000, 'crores': 10000000, 'koti': 10000000, 'cr': 10000000,
      'million': 1000000, 'billion': 1000000000
    };

    if (tokens.length === 0) return null;

    let total = 0;
    let current = 0;
    let matchedAny = false;

    for (let i = 0; i < tokens.length; i++) {
      const t = tokens[i];
      if (i < tokens.length - 1 && map[t + ' ' + tokens[i + 1]] !== undefined) {
        current += map[t + ' ' + tokens[i + 1]];
        matchedAny = true;
        i++;
        continue;
      }
      if (map[t] !== undefined) {
        matchedAny = true;
        const val = map[t];
        if (val === 100) {
          current = (current === 0 ? 1 : current) * 100;
        } else if (val === 1000 || val === 100000 || val === 1000000 || val === 10000000 || val === 1000000000) {
          current = (current === 0 ? 1 : current) * val;
          total += current;
          current = 0;
        } else {
          current += val;
        }
      } else if (!isNaN(parseFloat(t))) {
        matchedAny = true;
        current += parseFloat(t);
      }
    }
    total += current;
    return matchedAny && total > 0 ? total : null;
  },

  // O. Lakh & Crore Scale Converter ("5.5 lakh in crore", "2.5 crore in lakh", "1.2 crore in million")
  parseLakhCrore(raw) {
    const q = raw.trim().toLowerCase();
    const m = q.match(/^(\d+(?:\.\d+)?)\s*(lakh|lac|crore|cr|koti|million|billion)\s*(?:in|to|madhe|me)?\s*(lakh|lac|crore|cr|koti|million|billion|inr|rupees)$/i);
    if (m) {
      const val = parseFloat(m[1]);
      const fromUnit = m[2];
      const toUnit = m[3];
      const scaleMap = { lakh: 100000, lac: 100000, crore: 10000000, cr: 10000000, koti: 10000000, million: 1000000, billion: 1000000000, inr: 1, rupees: 1 };
      if (scaleMap[fromUnit] && scaleMap[toUnit]) {
        const absoluteVal = val * scaleMap[fromUnit];
        const converted = absoluteVal / scaleMap[toUnit];
        return { val, fromUnit, toUnit, absoluteVal, converted };
      }
    }
    return null;
  },

  // P. Universal Natural-Language Conversion Engine (Length, Weight, Volume, Area, Temperature, Time, Currency)
  parseUnitConversion(raw) {
    return typeof UniversalConversionEngine !== 'undefined' ? UniversalConversionEngine.parse(raw) : null;
  },

  // Q. Date Math & Calendar Countdown ("30 days from today", "45 days ago", "days between A and B", "days left in month", "financial year end days")
  parseDateMath(raw) {
    const q = raw.trim().toLowerCase();
    const dayNames = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

    // 1. Days remaining in Month / Year / FY
    if (/^(?:days?\s+left\s+in\s+month|mahina\s+samplayla|mahine\s+ke\s+din\s+bache)$/i.test(q)) {
      const now = new Date();
      const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0);
      const diffDays = Math.max(0, lastDay.getDate() - now.getDate());
      return {
        type: 'month_end',
        title: `📅 ${diffDays} Days Left in ${now.toLocaleString('en-US', { month: 'long' })}`,
        subtitle: `Month ends on ${BillsoftUtils.formatDate(lastDay)} (${dayNames[lastDay.getDay()]})`
      };
    }

    if (/^(?:days?\s+left\s+in\s+year|year\s+end\s+days|varsh\s+sampayla)$/i.test(q)) {
      const now = new Date();
      const endYear = new Date(now.getFullYear(), 11, 31);
      const diffMs = endYear - now;
      const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
      return {
        type: 'year_end',
        title: `📅 ${diffDays} Days Left in ${now.getFullYear()}`,
        subtitle: `Year ends on ${BillsoftUtils.formatDate(endYear)}`
      };
    }

    if (/^(?:financial\s+year\s+end|fy\s+end|31\s+march|tax\s+year\s+end)$/i.test(q)) {
      const now = new Date();
      let fyEndYear = now.getFullYear();
      if (now.getMonth() >= 3) fyEndYear += 1;
      const fyEnd = new Date(fyEndYear, 2, 31);
      const diffMs = fyEnd - now;
      const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
      return {
        type: 'fy_end',
        title: `🏛️ ${diffDays} Days Left in FY ${fyEndYear - 1}-${String(fyEndYear).slice(2)}`,
        subtitle: `Financial Year closes on 31-Mar-${fyEndYear} for Tax & GST Audit filings`
      };
    }

    // 2. Relative Days: "30 days from today", "45 days ago", "aaj se 30 din baad"
    const relMatch = q.match(/^(\d+)\s*(?:days?|din|divas)\s*(?:from\s+today|after|later|baad|nantar)$/i) ||
      q.match(/^(?:in|after|aaj\s+se|aaj\s+pasun)\s*(\d+)\s*(?:days?|din|divas)/i);
    if (relMatch) {
      const offset = parseInt(relMatch[1], 10);
      const target = new Date();
      target.setDate(target.getDate() + offset);
      return {
        type: 'relative_future',
        title: `📅 ${BillsoftUtils.formatDate(target)} (${dayNames[target.getDay()]})`,
        subtitle: `${offset} days after today`
      };
    }

    const agoMatch = q.match(/^(\d+)\s*(?:days?|din|divas)\s*(?:ago|before\s+today|before|pehle|pahile|purvi)$/i) ||
      q.match(/^(?:before|pahile|pehle)\s*(\d+)\s*(?:days?|din|divas)/i);
    if (agoMatch) {
      const offset = parseInt(agoMatch[1], 10);
      const target = new Date();
      target.setDate(target.getDate() - offset);
      return {
        type: 'relative_past',
        title: `📅 ${BillsoftUtils.formatDate(target)} (${dayNames[target.getDay()]})`,
        subtitle: `${offset} days before today`
      };
    }

    // 3. Day Lookups: "next monday", "last friday", "next first of month"
    const dayLookup = q.match(/^(?:next|last|pudhcha|magcha)\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday|somvar|mangalvar|budhvar|guruvar|shukravar|shanivar|ravivar)$/i);
    if (dayLookup) {
      const isNext = /^(?:next|pudhcha)/i.test(q);
      const targetDayIdx = {
        sunday: 0, ravivar: 0, monday: 1, somvar: 1, tuesday: 2, mangalvar: 2,
        wednesday: 3, budhvar: 3, thursday: 4, guruvar: 4, friday: 5, shukravar: 5, saturday: 6, shanivar: 6
      }[dayLookup[1]];
      if (targetDayIdx !== undefined) {
        const now = new Date();
        const currentIdx = now.getDay();
        let delta = targetDayIdx - currentIdx;
        if (isNext) {
          if (delta <= 0) delta += 7;
        } else {
          if (delta >= 0) delta -= 7;
        }
        const target = new Date();
        target.setDate(target.getDate() + delta);
        return {
          type: 'day_lookup',
          title: `📅 ${BillsoftUtils.formatDate(target)} (${dayNames[target.getDay()]})`,
          subtitle: `${isNext ? 'Next' : 'Last'} ${dayNames[targetDayIdx]}`
        };
      }
    }

    return null;
  },

  // R. Text & String Manipulation Utilities ("upper ...", "word count ...", "slugify ...", "no space ...")
  parseTextUtilities(raw) {
    const q = raw.trim();

    // Uppercase
    if (/^(?:upper|uppercase|caps|capital)\s+(.+)$/i.test(q)) {
      const txt = q.replace(/^(?:upper|uppercase|caps|capital)\s+/i, '').trim();
      return { type: 'upper', title: txt.toUpperCase(), subtitle: 'Uppercase text', result: txt.toUpperCase() };
    }

    // Lowercase
    if (/^(?:lower|lowercase|small)\s+(.+)$/i.test(q)) {
      const txt = q.replace(/^(?:lower|lowercase|small)\s+/i, '').trim();
      return { type: 'lower', title: txt.toLowerCase(), subtitle: 'Lowercase text', result: txt.toLowerCase() };
    }

    // Capitalize / Title Case
    if (/^(?:capitalize|title\s+case|titlecase)\s+(.+)$/i.test(q)) {
      const txt = q.replace(/^(?:capitalize|title\s+case|titlecase)\s+/i, '').trim();
      const cap = txt.replace(/\b\w/g, l => l.toUpperCase());
      return { type: 'capitalize', title: cap, subtitle: 'Title Case text', result: cap };
    }

    // Word Count & Char Count
    if (/^(?:word\s+count|char\s+count|length)\s+(.+)$/i.test(q)) {
      const txt = q.replace(/^(?:word\s+count|char\s+count|length)\s+/i, '').trim();
      const words = txt.split(/\s+/).filter(Boolean).length;
      const chars = txt.length;
      const charsNoSpace = txt.replace(/\s+/g, '').length;
      return { type: 'count', title: `📝 ${words} Words • ${chars} Characters (${charsNoSpace} without spaces)`, subtitle: `Text: "${txt.slice(0, 40)}${txt.length > 40 ? '...' : ''}"`, result: `${words} words, ${chars} chars` };
    }

    // Reverse Text
    if (/^(?:reverse|ulta)\s+(.+)$/i.test(q)) {
      const txt = q.replace(/^(?:reverse|ulta)\s+/i, '').trim();
      const rev = txt.split('').reverse().join('');
      return { type: 'reverse', title: rev, subtitle: `Reversed: "${txt}"`, result: rev };
    }

    // Space Remover / Clean: "no space 98 22 11 33" -> "9822113344"
    if (/^(?:no\s+space|remove\s+spaces?|join)\s+(.+)$/i.test(q)) {
      const txt = q.replace(/^(?:no\s+space|remove\s+spaces?|join)\s+/i, '').trim();
      const clean = txt.replace(/\s+/g, '');
      return { type: 'nospace', title: clean, subtitle: 'Spaces removed', result: clean };
    }

    // Slugify
    if (/^(?:slugify|slug|url\s+slug)\s+(.+)$/i.test(q)) {
      const txt = q.replace(/^(?:slugify|slug|url\s+slug)\s+/i, '').trim();
      const slug = txt.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)+/g, '');
      return { type: 'slug', title: slug, subtitle: 'URL Slug', result: slug };
    }

    return null;
  },

  // ─── P. UNIVERSAL PERCENTAGE, STANDALONE AMOUNT & TEMPLATE ASSISTANT ───

  // P1. Dedicated Percentage Parameter / Slot Extraction Handler ("5%", "18%", "5% on 20000", "5% discount on 1000", "18% gst on 50000")
  parsePercentageQuery(raw) {
    const q = raw.trim();
    const rate = this.NLP.extractRate(q);
    if (rate === null || isNaN(rate) || rate < 0) return null;

    const amounts = this.NLP.extractAmounts(q);
    const amount = amounts.length > 0 ? amounts[0].val : null;

    // Case A: Rate and Amount both provided (e.g. "5% on 20000", "20000 pe 5%", "5% of 10000")
    if (amount !== null && amount > 0) {
      const gstAdd = (amount * rate) / 100;
      const totalGst = amount + gstAdd;
      const reverseBase = (amount * 100) / (100 + rate);
      const reverseGst = amount - reverseBase;
      const discountVal = (amount * rate) / 100;
      const discountedPrice = Math.max(0, amount - discountVal);
      const marginPrice = rate < 100 ? (amount / (1 - rate / 100)) : amount * 2;
      const marginProfit = marginPrice - amount;
      const markupPrice = amount + (amount * rate) / 100;

      const cards = [];

      // 1. Raw Percentage
      const pctRaw = {
        type: 'pct_raw',
        title: `🔢 ${rate}% of ${BillsoftUtils.formatCurrency(amount)} = ${BillsoftUtils.formatCurrency(gstAdd)}`,
        subtitle: `Math: (${rate} / 100) × ${amount} = ${gstAdd.toLocaleString('en-IN')}`,
        icon: '🔢',
        badge: 'Percentage of',
        isCalculatedResult: true,
        action: () => {
          if (navigator.clipboard) navigator.clipboard.writeText(String(gstAdd));
          if (window.showToast) window.showToast(`Copied: ${gstAdd}`, 'success');
        }
      };

      // 2. GST Addition
      const pctGst = {
        type: 'pct_gst',
        title: `🏛️ ${rate}% GST on ${BillsoftUtils.formatCurrency(amount)}: Total ${BillsoftUtils.formatCurrency(totalGst)}`,
        subtitle: `Base: ${BillsoftUtils.formatCurrency(amount)} • ${rate}% GST: ${BillsoftUtils.formatCurrency(gstAdd)} (CGST ${rate / 2}%: ${BillsoftUtils.formatCurrency(gstAdd / 2)}, SGST ${rate / 2}%: ${BillsoftUtils.formatCurrency(gstAdd / 2)})`,
        icon: '🏛️',
        badge: 'GST Addition',
        isCalculatedResult: true,
        action: () => {
          if (navigator.clipboard) navigator.clipboard.writeText(`Base: ₹${amount}, ${rate}% GST: ₹${gstAdd.toFixed(2)}, Total: ₹${totalGst.toFixed(2)}`);
          if (window.showToast) window.showToast(`Copied ${rate}% GST calculation`, 'success');
        }
      };

      // 3. Discount
      const pctDiscount = {
        type: 'pct_discount',
        title: `🏷️ ${rate}% Discount on ${BillsoftUtils.formatCurrency(amount)}: Final Price ${BillsoftUtils.formatCurrency(discountedPrice)}`,
        subtitle: `Original: ${BillsoftUtils.formatCurrency(amount)} • Save ${BillsoftUtils.formatCurrency(discountVal)} (-${rate}%) • Final: ${BillsoftUtils.formatCurrency(discountedPrice)}`,
        icon: '🏷️',
        badge: 'Discount',
        isCalculatedResult: true,
        action: () => {
          if (navigator.clipboard) navigator.clipboard.writeText(`Final Price: ₹${discountedPrice.toFixed(2)} (Saved ₹${discountVal.toFixed(2)} with ${rate}% discount)`);
          if (window.showToast) window.showToast(`Copied discount price`, 'success');
        }
      };

      // 4. Reverse GST
      const pctReverseGst = {
        type: 'pct_reverse_gst',
        title: `🏛️ ${BillsoftUtils.formatCurrency(amount)} (Incl. ${rate}% GST): Base ${BillsoftUtils.formatCurrency(reverseBase)}`,
        subtitle: `Tax-Inclusive: Total ${BillsoftUtils.formatCurrency(amount)} = Base ${BillsoftUtils.formatCurrency(reverseBase)} + GST (${rate}%): ${BillsoftUtils.formatCurrency(reverseGst)}`,
        icon: '🏛️',
        badge: 'Reverse GST',
        isCalculatedResult: true,
        action: () => {
          if (navigator.clipboard) navigator.clipboard.writeText(`Total: ₹${amount} (Incl. ${rate}% GST) = Base: ₹${reverseBase.toFixed(2)} + GST: ₹${reverseGst.toFixed(2)}`);
          if (window.showToast) window.showToast(`Copied reverse GST calculation`, 'success');
        }
      };

      // 5. Profit Margin
      const pctMargin = {
        type: 'pct_margin',
        title: `📈 ${rate}% Profit Margin on Cost ${BillsoftUtils.formatCurrency(amount)}: Selling Price ${BillsoftUtils.formatCurrency(marginPrice)}`,
        subtitle: `Cost: ${BillsoftUtils.formatCurrency(amount)} • Profit: ${BillsoftUtils.formatCurrency(marginProfit)} (${rate}% Margin) • Markup on cost: ${rate}% (${BillsoftUtils.formatCurrency(markupPrice)})`,
        icon: '📈',
        badge: 'Profit Margin',
        isCalculatedResult: true,
        action: () => {
          if (navigator.clipboard) navigator.clipboard.writeText(`Selling Price: ₹${marginPrice.toFixed(2)} (Profit: ₹${marginProfit.toFixed(2)} for ${rate}% margin)`);
          if (window.showToast) window.showToast(`Copied margin selling price`, 'success');
        }
      };

      const isRawPct = /\b(of|ka|cha|ke|chi|pe|var|\*|x|multiply|take|pratishat|pct)\b/i.test(q) && !/[+\-]/.test(q);
      const isDiscount = /[-]|less|minus|discount|chhoot|ghatao|off|sub/i.test(q);
      const isGst = /[+]|add|plus|gst|tax|vat|jodo|milao/i.test(q);
      const isMargin = /margin|markup|profit|selling/i.test(q);

      if (isDiscount) {
        return [pctDiscount, pctRaw, pctGst, pctMargin, pctReverseGst];
      } else if (isGst) {
        return [pctGst, pctReverseGst, pctRaw, pctDiscount, pctMargin];
      } else if (isMargin) {
        return [pctMargin, pctGst, pctDiscount, pctRaw, pctReverseGst];
      } else if (isRawPct) {
        return [pctRaw, pctGst, pctDiscount, pctMargin, pctReverseGst];
      } else {
        return [pctRaw, pctGst, pctDiscount, pctMargin, pctReverseGst];
      }
    }

    // Case B: Standalone Rate (e.g. "5%", "18%", "12%") -> Generate rich multi-tool suggestions
    const sampleAmounts = [1000, 5000, 10000, 50000];
    const cards = [];

    // Card 1: 5% GST Interactive Suggestions
    cards.push({
      type: 'gst_interactive',
      title: `🏛️ Calculate ${rate}% GST on Common Amounts`,
      subtitle: `Standard GST rate (${rate}%) • Click any sample amount or type e.g. "${rate}% on 25000"`,
      icon: '🏛️',
      badge: 'GST Calculator',
      rate,
      suggestions: sampleAmounts.map(amt => {
        const gst = (amt * rate) / 100;
        return {
          label: `${BillsoftUtils.formatCurrency(amt)} → ${BillsoftUtils.formatCurrency(amt + gst)} (+${rate}%)`,
          query: `gst ${rate}% on ${amt}`,
          total: amt + gst,
          gst
        };
      }),
      action: () => { }
    });

    // Card 2: 5% Discount Interactive Suggestions
    cards.push({
      type: 'discount_interactive',
      title: `🏷️ Apply ${rate}% Discount on Prices`,
      subtitle: `Discount calculation • Click any sample price or type e.g. "${rate}% discount on 2500"`,
      icon: '🏷️',
      badge: 'Discount Tool',
      rate,
      suggestions: sampleAmounts.map(amt => {
        const disc = (amt * rate) / 100;
        return {
          label: `${BillsoftUtils.formatCurrency(amt)} → ${BillsoftUtils.formatCurrency(amt - disc)} (Save ${BillsoftUtils.formatCurrency(disc)})`,
          query: `${rate}% discount on ${amt}`,
          finalPrice: amt - disc,
          saved: disc
        };
      }),
      action: () => { }
    });

    // Card 3: 5% Profit Margin & Markup
    cards.push({
      type: 'margin_interactive',
      title: `📈 ${rate}% Profit Margin & Markup Analysis`,
      subtitle: `Sample: Cost ₹1,000 with ${rate}% margin = Selling Price ₹${rate < 100 ? (1000 / (1 - rate / 100)).toFixed(2) : 2000}`,
      icon: '📈',
      badge: 'Margin Tool',
      rate,
      suggestions: [
        { label: `Cost ₹1,000 @ ${rate}% margin → Sell ₹${(1000 / (1 - rate / 100)).toFixed(0)}`, query: `margin cost 1000 rate ${rate}%` },
        { label: `Cost ₹5,000 @ ${rate}% margin → Sell ₹${(5000 / (1 - rate / 100)).toFixed(0)}`, query: `margin cost 5000 rate ${rate}%` }
      ],
      action: () => { }
    });

    // Card 4: 5% Loan EMI & Interest
    cards.push({
      type: 'emi_interactive',
      title: `🏦 Loan EMI & Interest at ${rate}% Annual Rate`,
      subtitle: `Sample: ₹1 Lakh at ${rate}% for 1 Year • Click to compute loan schedule`,
      icon: '🏦',
      badge: 'EMI Calculator',
      rate,
      suggestions: [
        { label: `₹1 Lakh @ ${rate}% for 1 Year`, query: `emi 1 lakh at ${rate}% for 1 year` },
        { label: `₹5 Lakh @ ${rate}% for 3 Years`, query: `emi 5 lakh at ${rate}% for 3 years` }
      ],
      action: () => { }
    });

    return cards;
  },

  // P2. Standalone Amount Slot-Filling ("5000", "12800", "1 lakh", "25000")
  parseStandaloneAmount(raw) {
    const q = raw.trim();
    // Only match if the query is strictly a number or currency amount (e.g. "5000", "₹12500", "1 lakh")
    if (!/^(?:₹|rs\.?|\$|€|£)?\s*\d+(?:,\d+)*(?:\.\d+)?\s*(?:lakh|crore|k|thousand|cr|lacs|lac|hazaar|hazar|hundred|sau)?$/i.test(q)) {
      return null;
    }
    if (/\b(gst|tax|vat|discount|chhut|split|divide|margin|profit|markup|commission|emi|loan|diff|growth|words|change|subah|shaam|baje|pm|am|todo|task|note|upi|qr|gpay|phonepe|paytm|bhim|payment|scan)\b/i.test(q)) {
      return null;
    }
    // 8-14 pure digits without currency prefix or multiplier are barcodes or phone numbers, not pricing amounts
    if (/^\d{8,14}$/.test(q.replace(/[\s-]/g, '')) && !/[₹$€£]/.test(q) && !/\b(lakh|crore|thousand|cr|lac)\b/i.test(q)) {
      return null;
    }
    const amounts = this.NLP.extractAmounts(q);
    if (amounts.length !== 1) return null;
    const amt = amounts[0].val;
    if (amt <= 0) return null;

    const cards = [];

    // 1. GST Slabs on this amount
    cards.push({
      type: 'gst_interactive',
      title: `🏛️ GST Breakdown for ${BillsoftUtils.formatCurrency(amt)}`,
      subtitle: `5%: ${BillsoftUtils.formatCurrency(amt * 1.05)} • 12%: ${BillsoftUtils.formatCurrency(amt * 1.12)} • 18%: ${BillsoftUtils.formatCurrency(amt * 1.18)} • 28%: ${BillsoftUtils.formatCurrency(amt * 1.28)}`,
      icon: '🏛️',
      badge: 'GST Calculator',
      amount: amt,
      suggestions: [5, 12, 18, 28].map(r => ({
        label: `${r}% GST → ${BillsoftUtils.formatCurrency(amt + (amt * r) / 100)}`,
        query: `gst ${r}% on ${amt}`,
        rate: r,
        total: amt + (amt * r) / 100
      })),
      action: () => {
        const gst18 = Math.round((amt * 1.18) * 100) / 100;
        if (navigator.clipboard) navigator.clipboard.writeText(`Base: ₹${amt}, 18% GST: ₹${Math.round(amt * 0.18 * 100) / 100}, Total: ₹${gst18}`);
        if (window.showToast) window.showToast(`Copied 18% GST breakdown for ₹${amt}`, 'success');
      }
    });

    // 2. Bill Splitter
    cards.push({
      type: 'split_interactive',
      title: `👥 Split ${BillsoftUtils.formatCurrency(amt)} evenly:`,
      subtitle: `2 people: ₹${Math.round(amt / 2)} • 3 people: ₹${Math.round(amt / 3)} • 4 people: ₹${Math.round(amt / 4)} • 5 people: ₹${Math.round(amt / 5)}`,
      icon: '👥',
      badge: 'Bill Splitter',
      amount: amt,
      suggestions: [2, 3, 4, 5].map(cnt => ({
        label: `${cnt} people → ₹${Math.round(amt / cnt)}`,
        query: `split ${amt} by ${cnt}`,
        count: cnt,
        perPerson: Math.round(amt / cnt)
      })),
      action: () => {
        if (navigator.clipboard) navigator.clipboard.writeText(`₹${amt} split in 4: ₹${Math.round(amt / 4)} each`);
        if (window.showToast) window.showToast('Copied 4-way split', 'success');
      }
    });

    // 3. Words Format
    const wordsStr = this.numberToWordsIndian(amt);
    if (wordsStr) {
      cards.push({
        type: 'words',
        title: `✍️ ${wordsStr}`,
        subtitle: `Indian Currency in Words (${BillsoftUtils.formatCurrency(amt)})`,
        icon: '✍️',
        badge: 'Words',
        action: () => {
          if (navigator.clipboard) navigator.clipboard.writeText(wordsStr);
          if (window.showToast) window.showToast('Copied words to clipboard', 'success');
        }
      });
    }

    return cards;
  },

  // P3. Keyword Template Guidance ("emi", "loan", "gst", "tax", "discount", "split", "change", "margin", "words", "upi", "qr", "kharcha", "udhari")
  parseKeywordTemplates(raw) {
    const q = raw.trim().toLowerCase();
    if (!q) return null;

    if (/^(?:emi|loan|kist|kisht|karja|hapta|installment)$/i.test(q)) {
      return [{
        type: 'emi_interactive',
        title: '🏦 Loan EMI & Interest Calculator',
        subtitle: 'Order-independent calculator • Click a sample template or type e.g. "emi 10 lakh at 8.5% for 5 years"',
        icon: '🏦',
        badge: 'EMI Calculator',
        suggestions: [
          { label: '₹1 Lakh @ 12% (1 Year)', query: 'emi 100000 at 12% for 1 year' },
          { label: '₹5 Lakh @ 9.5% (3 Years)', query: 'emi 500000 at 9.5% for 3 years' },
          { label: '₹10 Lakh @ 8.5% (5 Years)', query: 'emi 1000000 at 8.5% for 5 years' },
          { label: '₹12,800 @ 12% (6 Months)', query: 'emi at 12% for 12800 amount for 6months' }
        ],
        action: () => { }
      }];
    }

    if (/^(?:gst|tax|vat)$/i.test(q)) {
      return [{
        type: 'gst_interactive',
        title: '🏛️ GST & Tax Calculator',
        subtitle: 'Calculate GST additions & reverse GST breakdowns • Click sample or type e.g. "gst 18% on 5000"',
        icon: '🏛️',
        badge: 'GST Tool',
        suggestions: [
          { label: '18% GST on ₹5,000', query: 'gst 18% on 5000' },
          { label: '5% GST on ₹10,000', query: 'gst 5% on 10000' },
          { label: '₹20,000 Incl. 18% GST', query: '20000 inclusive 18% gst' },
          { label: '12% GST on ₹1,200', query: 'gst 12% on 1200' }
        ],
        action: () => { }
      }];
    }

    if (/^(?:discount|chhut|suit|off|less)$/i.test(q)) {
      return [{
        type: 'discount_interactive',
        title: '🏷️ Discount Calculator',
        subtitle: 'Compute percentage & flat discounts • Click sample or type e.g. "10% discount on 5000"',
        icon: '🏷️',
        badge: 'Discount Tool',
        suggestions: [
          { label: '10% off on ₹5,000', query: '10% discount on 5000' },
          { label: '15% off on ₹1,200', query: '15% discount on 1200' },
          { label: '₹500 off on ₹2,500', query: '500 off on 2500' },
          { label: '25% off on ₹8,000', query: '25% discount on 8000' }
        ],
        action: () => { }
      }];
    }

    if (/^(?:split|divide|vibhagani|baanto|vata)$/i.test(q)) {
      return [{
        type: 'split_interactive',
        title: '👥 Bill Splitter',
        subtitle: 'Split bill totals evenly per person • Click sample or type e.g. "split 4500 by 4"',
        icon: '👥',
        badge: 'Split Tool',
        suggestions: [
          { label: '₹4,500 split by 4', query: 'split 4500 by 4' },
          { label: '₹1,200 split by 3', query: 'split 1200 by 3' },
          { label: '₹10,000 split in 2:3:5', query: 'split 10000 in 2:3:5' },
          { label: '₹2,000 split by 5', query: 'split 2000 by 5' }
        ],
        action: () => { }
      }];
    }

    if (/^(?:margin|profit|markup|munafa|nafa)$/i.test(q)) {
      return [{
        type: 'margin_interactive',
        title: '📈 Profit Margin & Markup Calculator',
        subtitle: 'Compute profit margins on costs • Click sample or type e.g. "margin cost 800 price 1200"',
        icon: '📈',
        badge: 'Margin Tool',
        suggestions: [
          { label: 'Cost ₹800, Price ₹1,200', query: 'margin cost 800 price 1200' },
          { label: 'Cost ₹1,500, Price ₹2,200', query: 'margin cost 1500 price 2200' },
          { label: '25% markup on ₹800', query: '25% markup on 800' }
        ],
        action: () => { }
      }];
    }

    return null;
  },

  // S. System & App Control Commands
  parseSystemControls(raw) {
    const q = raw.trim().toLowerCase();

    if (/^(?:version|app\s+version|software\s+version|build)$/i.test(q)) {
      return {
        type: 'sys_version',
        title: '📦 RupeeCRM v3.4.0 (Enterprise Edition)',
        subtitle: 'Production Build • Click to check for updates',
        icon: '📦',
        action: () => {
          if (API.update && API.update.check) {
            API.update.check().then(res => {
              if (window.showToast) window.showToast(res && res.hasUpdate ? `Update available: v${res.latestVersion}` : '✓ You are using the latest version!', 'info');
            }).catch(() => { });
          }
        }
      };
    }

    if (/^(?:api\s+status|server\s+health|server\s+status|backend\s+status)$/i.test(q)) {
      return {
        type: 'sys_health',
        title: '🟢 API & Backend Server: ONLINE (Port 28080)',
        subtitle: 'Spring Boot 3.4.0 • Active REST Controllers Ready',
        icon: '🟢',
        action: () => {
          if (window.showToast) window.showToast('Backend REST API is 100% Healthy', 'success');
        }
      };
    }

    if (/^(?:database\s+status|db\s+status|db\s+health|sqlite\s+status)$/i.test(q)) {
      return {
        type: 'sys_db',
        title: '🗄️ Database Engine: SQLite / H2 Active',
        subtitle: 'Auto-backup enabled • Safe transaction journaling active',
        icon: '🗄️',
        action: () => {
          BillsoftSearchEngine.dispatchNavigate({ page: 'settings', tab: 'backup' });
        }
      };
    }

    if (/^(?:fullscreen|exit\s+fullscreen|purna\s+screen|screen\s+badi\s+karo)$/i.test(q)) {
      return {
        type: 'sys_fullscreen',
        title: document.fullscreenElement ? '🔲 Exit Fullscreen Mode' : '🔳 Enter Fullscreen Mode',
        subtitle: 'Toggle distraction-free full workspace view',
        icon: '🔳',
        action: () => {
          if (!document.fullscreenElement) {
            document.documentElement.requestFullscreen().catch(() => { });
          } else {
            document.exitFullscreen().catch(() => { });
          }
        }
      };
    }

    if (/^(?:zoom\s+in|zoom\s+out|reset\s+zoom|bada\s+dikhao|chhota\s+dikhao)$/i.test(q)) {
      const isZoomIn = q.includes('in') || q.includes('bada');
      const isReset = q.includes('reset');
      return {
        type: 'sys_zoom',
        title: isReset ? '🔍 Reset Interface Zoom (100%)' : isZoomIn ? '🔍 Zoom In Workspace (+10%)' : '🔍 Zoom Out Workspace (-10%)',
        subtitle: 'Adjust desktop display scaling',
        icon: '🔍',
        action: () => {
          const currentZoom = parseFloat(document.documentElement.style.zoom || '1.0');
          const newZoom = isReset ? 1.0 : isZoomIn ? Math.min(1.4, currentZoom + 0.1) : Math.max(0.8, currentZoom - 0.1);
          document.documentElement.style.zoom = String(newZoom);
          if (window.showToast) window.showToast(`Display scale: ${Math.round(newZoom * 100)}%`, 'info');
        }
      };
    }

    if (/^(?:reload\s+app|restart\s+app|refresh\s+window|punha\s+suru)$/i.test(q)) {
      return {
        type: 'sys_reload',
        title: '🔄 Reload Application & Views',
        subtitle: 'Re-initializes all components and cache',
        icon: '🔄',
        action: () => {
          window.location.reload();
        }
      };
    }

    return null;
  },

  // ─── 7.9 DOMINANT INTENT CLASSIFIER (DELEGATES AUTHORITATIVELY TO OMNIBAR PIPELINE) ───
  classifyDominantIntent(raw, q, ctx) {
    // Authoritative delegation to OmnibarPipeline
    const getPipeline = () => {
      if (typeof window !== 'undefined' && window.OmnibarPipeline) return window.OmnibarPipeline;
      if (typeof require !== 'undefined') {
        try { return require('./omnibarPipeline.js'); } catch (e) { }
      }
      return null;
    };

    const pipeline = getPipeline();
    if (pipeline && pipeline.IntentClassifier && pipeline.Normalizer) {
      const norm = pipeline.Normalizer.normalize(raw);
      const res = pipeline.IntentClassifier.classify(norm, ctx);
      if (res && res.topCandidate) {
        const top = res.topCandidate;
        if (top.domain === 'UPI') return 'UPI';
        if (top.domain === 'WORDS') return 'WORDS';
        if (top.domain === 'CONVERSION') return 'CONVERSION';
        if (top.intent === 'GST_CALCULATION') return 'GST';
        if (top.intent === 'CASHIER_CHANGE') return 'CHANGE';
        if (top.intent === 'BILL_SPLIT') return 'SPLIT';
        if (top.intent === 'DISCOUNT_CALCULATION') return 'DISCOUNT';
        if (top.intent === 'MARGIN_CALCULATION') return 'MARGIN';
        if (top.intent === 'LOAN_EMI_CALCULATION') return 'FINANCE';
        if (top.intent === 'ARITHMETIC_MATH') return 'MATH';
        if (top.domain === 'BI') return 'FINANCE';
      }
    }

    // Fallback: Standalone Number
    if (/^(?:₹|rs\.?|\$|€|£)?\s*\d+(?:,\d+)*(?:\.\d+)?\s*(?:lakh|crore|k|thousand|cr|lacs|lac|hazaar|hazar|hundred|sau)?$/i.test(q.trim())) {
      return 'STANDALONE_NUMBER';
    }

    return 'GENERAL';
  },

  // ─── 8. COMPREHENSIVE QUERY PARSER & SEARCH AGGREGATOR ───
  search(query, ctx = {}) {
    const rawOrig = (query || '').trim();
    // Decouple glued tokens across the query (e.g. "30mm" -> "30 mm", "700upi" -> "700 upi", "18%gst" -> "18% gst")
    const raw = BillsoftUtils.splitGluedTokens(rawOrig);
    const q = raw.toLowerCase();
    const results = {
      smartTasks: [],
      utilities: [],
      actions: [],
      customers: [],
      invoices: [],
      estimates: [],
      products: [],
      parties: [],
      staff: [],
      purchaseOrders: [],
      expenses: [],
      returns: [],
      letters: [],
      reminders: [],
      suggestions: [],
      assistantResult: null
    };

    if (!raw) return results;

    const customers = this.ensureArray(ctx.customers);
    const invoices = this.ensureArray(ctx.invoices);
    const estimates = this.ensureArray(ctx.estimates);
    const products = this.ensureArray(ctx.products);
    const parties = this.ensureArray(ctx.parties);
    const staff = this.ensureArray(ctx.staff);
    const purchaseOrders = this.ensureArray(ctx.purchaseOrders);
    const expenses = this.ensureArray(ctx.expenses);
    const returns = this.ensureArray(ctx.returns);
    const letters = this.ensureArray(ctx.letters);
    const reminders = this.ensureArray(ctx.reminders);

    // ─────────────────────────────────────────────────────────────
    // STEP 1: AUTHORITATIVE SEMANTIC REASONING PIPELINE (OmnibarPipeline)
    // ─────────────────────────────────────────────────────────────
    let semanticRes = null;
    try {
      semanticRes = this.processQuery(raw, ctx);
    } catch (e) {
      if (typeof console !== 'undefined' && console.warn) console.warn('[Omnibar] Semantic pipeline error:', e);
    }

    if (semanticRes && semanticRes.status && semanticRes.status !== 'NO_MATCH' && semanticRes.status !== 'SEARCH_RECORDS' && semanticRes.status !== 'ERROR') {
      results.assistantResult = semanticRes;

      // 1. DIRECT ACTIONS (Immediate Execution, e.g. Theme Toggle, App Lock)
      if (semanticRes.status === 'DIRECT_ACTION') {
        const actItem = {
          id: (semanticRes.capabilityId || 'direct_action').toLowerCase(),
          title: semanticRes.title || '⚡ Direct Action',
          subtitle: semanticRes.subtitle || 'Execute immediately',
          icon: semanticRes.capabilityId === 'ACT_THEME_TOGGLE' ? '🎨' : '🔒',
          badge: '⚡ Action',
          category: 'actions',
          action: () => {
            if (semanticRes.action === 'THEME_TOGGLE') {
              const isDark = document.body.classList.contains('theme-dark');
              if (isDark) {
                document.body.classList.remove('theme-dark');
                try { localStorage.setItem('billsoft_theme', 'light'); } catch (e) { }
              } else {
                document.body.classList.add('theme-dark');
                try { localStorage.setItem('billsoft_theme', 'dark'); } catch (e) { }
              }
              window.dispatchEvent(new CustomEvent('billsoft:theme-change'));
            } else if (semanticRes.action === 'APP_LOCK') {
              window.dispatchEvent(new CustomEvent('billsoft:app-lock'));
            }
          }
        };
        results.actions.push(actItem);
        return results;
      }

      // 2. DIRECT NAVIGATION
      if (semanticRes.status === 'NAVIGATE') {
        const navTarget = semanticRes.target;
        if (navTarget) {
          results.actions.push({
            id: (semanticRes.capabilityId || 'navigate').toLowerCase(),
            title: semanticRes.title || `Open ${navTarget.page}`,
            subtitle: semanticRes.subtitle || `Navigate directly to ${navTarget.page}`,
            icon: '⚡',
            badge: '⚡ Action',
            target: navTarget,
            action: () => BillsoftSearchEngine.dispatchNavigate(navTarget)
          });
          return results;
        }
      }

      // 3. ACTION PREVIEW (Mutations requiring confirmation card)
      if (semanticRes.status === 'ACTION_PREVIEW') {
        const cap = semanticRes.capabilityId || '';
        const kind = (semanticRes.editableConfig && semanticRes.editableConfig.kind) ||
                     (cap === 'ACT_CREATE_EXPENSE' ? 'expense' :
                      cap === 'ACT_CREATE_TASK' ? 'task' :
                      cap === 'ACT_CREATE_NOTE' ? 'note' :
                      cap === 'ACT_CREATE_CUSTOMER' ? 'customer' :
                      cap === 'ACT_MARK_ATTENDANCE' ? 'attendance' :
                      cap === 'ACT_RECORD_ADVANCE' ? 'advance' : 'task');
        const previewItem = {
          type: 'assistant_action_preview',
          itemType: 'assistant_action_preview',
          isAuthoritativeAnswer: true,
          priority: 1000,
          semanticResult: semanticRes,
          title: semanticRes.title || '⚡ Action Preview',
          subtitle: semanticRes.subtitle || 'Review parameters before executing',
          riskLevel: 'LOW',
          entities: semanticRes.entities,
          icon: semanticRes.icon || '⚡',
          badge: '⚡ Action',
          editableConfig: semanticRes.editableConfig || {
            kind: kind,
            initialValues: { ...(semanticRes.entities || {}) }
          },
          action: async () => {
            try {
              const data = (semanticRes.editableConfig && semanticRes.editableConfig.initialValues) || semanticRes.entities || {};
              if (kind === 'task' || kind === 'reminder') {
                if (API.reminders && API.reminders.create) {
                  let dVal = data.dueDate ? String(data.dueDate).trim() : '';
                  if (dVal.length === 10) dVal = dVal + 'T09:00:00';
                  await API.reminders.create({
                    title: data.title || 'Task',
                    type: data.type || (kind === 'reminder' ? 'reminder' : 'task'),
                    status: 'TODO',
                    priority: data.priority || 'MEDIUM',
                    dueDate: dVal
                  });
                  if (window.showToast) window.showToast(`✓ ${data.type === 'reminder' ? 'Reminder' : 'Task'} saved successfully!`, 'success');
                  window.dispatchEvent(new CustomEvent('billsoft:app-refresh'));
                }
              } else if (kind === 'expense') {
                if (API.expenses && API.expenses.create) {
                  await API.expenses.create({
                    title: data.title || 'General Expense',
                    amount: parseFloat(data.amount) || 0,
                    expenseDate: data.expenseDate || new Date().toISOString().slice(0, 10),
                    category: data.category || 'General',
                    paymentMode: data.paymentMode || 'Cash'
                  });
                  if (window.showToast) window.showToast(`✓ Expense of ₹${data.amount || 0} recorded successfully!`, 'success');
                  window.dispatchEvent(new CustomEvent('billsoft:app-refresh'));
                }
              } else if (kind === 'note') {
                if (API.notes && API.notes.create) {
                  await API.notes.create({
                    title: data.title || 'Sticky Note',
                    color: data.color || 'yellow'
                  });
                  if (window.showToast) window.showToast(`✓ Sticky note pinned to Planner!`, 'success');
                  window.dispatchEvent(new CustomEvent('billsoft:app-refresh'));
                }
              } else if (kind === 'customer') {
                if (API.customers && API.customers.create) {
                  await API.customers.create({
                    name: data.name || 'New Customer',
                    phone: data.phone || '',
                    city: data.city || '',
                    openingBalance: parseFloat(data.openingBalance) || 0
                  });
                  if (window.showToast) window.showToast(`✓ Customer ${data.name} registered!`, 'success');
                  window.dispatchEvent(new CustomEvent('billsoft:app-refresh'));
                }
              } else if (kind === 'attendance') {
                BillsoftSearchEngine.dispatchNavigate({ page: 'hr', hrTab: 'attendance' });
              } else if (kind === 'advance') {
                BillsoftSearchEngine.dispatchNavigate({ page: 'hr', hrTab: 'advances' });
              }
            } catch (e) {
              if (window.showToast) window.showToast('Action failed: ' + (e.message || 'Error'), 'error');
            }
          }
        };
        results.utilities.push(previewItem);
        results.smartTasks.push(previewItem);
        return results; // Decisive semantic result: do NOT merge unrelated search candidates
      }

      // 4. AMBIGUITY (Disambiguation card)
      if (semanticRes.status === 'AMBIGUOUS') {
        const ambigItem = {
          type: 'assistant_ambiguity',
          itemType: 'assistant_ambiguity',
          isAuthoritativeAnswer: true,
          priority: 1000,
          semanticResult: semanticRes,
          title: semanticRes.title || 'Multiple Interpretations Found',
          subtitle: semanticRes.subtitle || 'Please select your intended option:',
          candidates: semanticRes.candidates || [],
          icon: '⚖️',
          badge: '💡 Quick Help',
          action: () => { }
        };
        results.utilities.push(ambigItem);
        return results;
      }

      // 5. AUTHORITATIVE ANSWER (Special Calculations & Quick Help Business Data)
      if (semanticRes.status === 'ANSWER' || semanticRes.status === 'QUICK_HELP') {
        const isQuickHelp = semanticRes.category === 'QUICK_HELP';
        const badgeLabel = isQuickHelp ? '💡 Quick Help' : '✨ Special';
        const iconChar = isQuickHelp ? '📊' :
                         (semanticRes.capabilityId && semanticRes.capabilityId.startsWith('SPEC_MATH') ? '🧮' :
                          semanticRes.capabilityId === 'SPEC_PAY_UPI_QR' ? '📱' :
                          semanticRes.capabilityId === 'SPEC_COMM_WHATSAPP' ? '💬' : '✨');

        // Customer Invoices Lookup
        if (semanticRes.capabilityId === 'QH_CUSTOMER_INVS' && semanticRes.data && semanticRes.data.invoices) {
          const invList = semanticRes.data.invoices;
          const custName = semanticRes.data.customerName || 'Customer';
          results.utilities.push({
            type: 'assistant_answer',
            itemType: 'assistant_answer',
            isAuthoritativeAnswer: true,
            isCalculatedResult: true,
            priority: 1000,
            semanticResult: semanticRes,
            intent: semanticRes.capabilityId,
            title: semanticRes.title || `Found ${invList.length} invoice(s) for ${custName}`,
            subtitle: semanticRes.subtitle || `Displaying invoices for ${custName}`,
            data: semanticRes.data,
            icon: '📄',
            badge: '💡 Quick Help'
          });
          results.invoices = invList;
          return results;
        }

        const answerCard = {
          type: 'assistant_answer',
          itemType: 'assistant_answer',
          isAuthoritativeAnswer: true,
          isCalculatedResult: true,
          priority: 1000,
          semanticResult: semanticRes,
          intent: semanticRes.capabilityId,
          title: semanticRes.title || 'Authoritative Answer',
          subtitle: semanticRes.subtitle || `Category: ${semanticRes.category}`,
          data: semanticRes.data,
          entities: semanticRes.entities,
          icon: iconChar,
          badge: badgeLabel,
          action: () => {
            if (navigator.clipboard && semanticRes.title) {
              navigator.clipboard.writeText(semanticRes.title);
              if (typeof window !== 'undefined' && window.showToast) window.showToast('Copied result to clipboard!', 'success');
            }
          }
        };

        if (semanticRes.capabilityId === 'SPEC_PAY_UPI_QR' && semanticRes.data) {
          answerCard.qrUrl = semanticRes.data.qrUrl;
          answerCard.upiId = semanticRes.data.upiId;
          answerCard.amount = semanticRes.data.amount;
          answerCard.type = 'upi_qr';
        }

        results.utilities.push(answerCard);
        return results; // Decisive semantic answer: generic search candidates MUST NOT outrank it!
      }
    }

    // ─────────────────────────────────────────────────────────────
    // STEP 2: LEGACY SEARCH FALLBACK (When semantic pipeline returns SEARCH_RECORDS / NO_MATCH)
    // ─────────────────────────────────────────────────────────────
    // Classify Dominant Intent to prevent cross-domain contamination
    const dominantIntent = this.classifyDominantIntent(raw, q, ctx);

    // ─────────────────────────────────────────────────────────────
    // 1. UPI INTENT: Highest-priority mutually exclusive routing
    // ─────────────────────────────────────────────────────────────
    if (dominantIntent === 'UPI') {
      const amounts = this.NLP.extractAmounts(q);
      const upiAmt = amounts.length > 0 ? amounts[0].val : 0;
      const firmObj = ctx.firm || {};
      const rawUpiId = (firmObj.upiId && firmObj.upiId.trim()) || (firmObj.upi && firmObj.upi.trim()) || '';
      const upiId = rawUpiId || 'merchant@upi';
      const firmName = (firmObj.firmName && firmObj.firmName.trim()) || 'RupeeCRM Merchant';

      const amtParam = upiAmt > 0 ? `&am=${upiAmt.toFixed(2)}` : '';
      const amtText = upiAmt > 0 ? BillsoftUtils.formatCurrency(upiAmt) : 'Dynamic / Any Amount';
      const upiUrl = `upi://pay?pa=${upiId}&pn=${encodeURIComponent(firmName)}${amtParam}&cu=INR`;
      const qrImageUrl = `/api/omnisearch/qr?size=260&data=${encodeURIComponent(upiUrl)}`;

      results.utilities.push({
        type: 'upi_qr',
        isCalculatedResult: true,
        priority: 100,
        title: upiAmt > 0 ? `📱 UPI Payment QR: ${amtText}` : `📱 Live UPI Payment QR (${firmName})`,
        subtitle: rawUpiId ? `Pay to VPA: ${rawUpiId} (${firmName})` : `Pay to VPA: ${upiId} • Configure custom UPI ID in Business Profile`,
        qrUrl: qrImageUrl,
        upiId: upiId,
        amount: upiAmt,
        firmName: firmName,
        icon: '📱',
        badge: 'Live UPI QR',
        editableConfig: {
          kind: 'upi',
          initialValues: {
            amount: upiAmt || '',
            upiId: upiId,
            firmName: firmName
          }
        },
        action: () => {
          if (navigator.clipboard) navigator.clipboard.writeText(upiUrl);
          if (window.showToast) window.showToast(`Copied UPI Payment Link (${upiId})`, 'success');
        }
      });

      if (!rawUpiId) {
        results.actions.push({
          id: 'configure_upi',
          title: '⚙️ Configure Business UPI ID / VPA',
          subtitle: 'Set your shop VPA (e.g. yourname@okaxis, shop@upi) in Business Profile',
          category: 'actions',
          icon: '⚙️',
          badge: 'Profile Settings',
          target: { page: 'firm', tab: 'profile' },
          action: () => {
            BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'profile' });
          }
        });
      }

      return results;
    }

    // ─────────────────────────────────────────────────────────────
    // 2. CONVERSION INTENT: Suppress unrelated tools
    // ─────────────────────────────────────────────────────────────
    if (dominantIntent === 'CONVERSION') {
      const convRes = this.parseUnitConversion(raw);
      if (convRes) {
        if (convRes.type === 'conversion') {
          const isCur = convRes.category === 'Currency';
          results.utilities.push({
            type: 'conversion',
            itemType: 'utility',
            isCalculatedResult: true,
            priority: 100,
            category: convRes.category,
            title: convRes.title,
            subtitle: convRes.subtitle,
            formula: convRes.formula,
            cleanResultStr: convRes.cleanResultStr,
            reverseQuery: convRes.reverseQuery,
            icon: convRes.icon,
            badge: isCur ? 'Currency Converter' : `${convRes.category} Converter`,
            action: () => {
              if (navigator.clipboard) navigator.clipboard.writeText(convRes.cleanResultStr);
              if (window.showToast) window.showToast(`Copied: ${convRes.cleanResultStr}`, 'success');
            }
          });
        } else if (convRes.type === 'conversion_suggestion') {
          results.utilities.push({
            type: 'conversion_suggestion',
            itemType: 'utility',
            priority: 100,
            title: convRes.title,
            subtitle: convRes.subtitle,
            suggestions: convRes.suggestions,
            val: convRes.val,
            fromRes: convRes.fromRes,
            icon: convRes.icon,
            badge: 'Unit Suggestions',
            action: () => { }
          });
        } else if (convRes.type === 'conversion_ambiguous') {
          results.utilities.push({
            type: 'conversion_ambiguous',
            itemType: 'utility',
            priority: 100,
            title: convRes.title,
            subtitle: convRes.subtitle,
            options: convRes.options,
            icon: convRes.icon,
            badge: 'Clarification Needed',
            action: () => { }
          });
        }
        return results;
      }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. WORDS INTENT: Suppress unrelated tools
    // ─────────────────────────────────────────────────────────────
    if (dominantIntent === 'WORDS') {
      const amounts = this.NLP.extractAmounts(q);
      const numVal = amounts.length > 0 ? amounts[0].val : null;
      if (numVal !== null && numVal > 0) {
        const wordsStr = this.numberToWordsIndian(numVal);
        results.utilities.push({
          type: 'words',
          isCalculatedResult: true,
          priority: 100,
          title: `✍️ ${wordsStr}`,
          subtitle: `Amount: ${BillsoftUtils.formatCurrency(numVal)}`,
          icon: '✍️',
          badge: 'Amount in Words',
          action: () => {
            if (navigator.clipboard) navigator.clipboard.writeText(wordsStr);
            if (window.showToast) window.showToast('Copied words to clipboard', 'success');
          }
        });
        return results;
      }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. MATH INTENT: Suppress unrelated tools
    // ─────────────────────────────────────────────────────────────
    if (dominantIntent === 'MATH') {
      const mathResult = this.evaluateMath(raw);
      if (mathResult) {
        results.utilities.push({
          type: 'math',
          isCalculatedResult: true,
          isEquation: true,
          priority: 100,
          title: mathResult.title || `🧮 Result: ${mathResult.formatted}`,
          subtitle: mathResult.subtitle || `Equation: ${mathResult.expression} = ${mathResult.result}`,
          value: mathResult.result,
          icon: '🧮',
          badge: 'Calculator',
          action: () => {
            if (navigator.clipboard) navigator.clipboard.writeText(String(mathResult.result));
            if (window.showToast) window.showToast(`Copied result: ${mathResult.result}`, 'success');
          }
        });
        return results;
      }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. CHANGE INTENT: Suppress unrelated tools
    // ─────────────────────────────────────────────────────────────
    if (dominantIntent === 'CHANGE') {
      const cashChange = this.parseChange(raw);
      if (cashChange && !cashChange.partial) {
        results.utilities.push({
          type: 'change',
          isCalculatedResult: true,
          priority: 100,
          title: `💵 Return Change: ${BillsoftUtils.formatCurrency(cashChange.change)}`,
          subtitle: `Tendered: ${BillsoftUtils.formatCurrency(cashChange.tendered)} • Bill: ${BillsoftUtils.formatCurrency(cashChange.bill)} • Breakdown: ${cashChange.breakdownStr}`,
          icon: '💵',
          badge: 'Cashier Helper',
          action: () => {
            if (navigator.clipboard) navigator.clipboard.writeText(`Change to return: ₹${cashChange.change} (${cashChange.breakdownStr})`);
            if (window.showToast) window.showToast('Copied change breakdown', 'success');
          }
        });
        return results;
      }
    }

    // ─────────────────────────────────────────────────────────────
    // 6. SPLIT INTENT: Suppress unrelated tools
    // ─────────────────────────────────────────────────────────────
    if (dominantIntent === 'SPLIT') {
      const billSplit = this.parseBillSplit(raw);
      if (billSplit) {
        if (billSplit.partial) {
          const kwCards = this.parseKeywordTemplates(raw);
          if (kwCards && kwCards.length > 0) {
            kwCards.forEach(c => results.utilities.push(c));
          } else if (billSplit.suggestions) {
            results.utilities.push({
              type: 'split_interactive',
              isCalculatedResult: true,
              priority: 100,
              title: `👥 Split ${BillsoftUtils.formatCurrency(billSplit.amount)} evenly:`,
              subtitle: `2 people: ₹${Math.round(billSplit.amount / 2)} • 3 people: ₹${Math.round(billSplit.amount / 3)} • 4 people: ₹${Math.round(billSplit.amount / 4)} • 5 people: ₹${Math.round(billSplit.amount / 5)}`,
              icon: '👥',
              badge: 'Bill Splitter',
              amount: billSplit.amount,
              suggestions: billSplit.suggestions,
              action: () => {
                if (navigator.clipboard) navigator.clipboard.writeText(`₹${billSplit.amount} split in 4: ₹${Math.round(billSplit.amount / 4)} each`);
                if (window.showToast) window.showToast('Copied 4-way split', 'success');
              }
            });
          }
        } else {
          results.utilities.push({
            type: 'split',
            isCalculatedResult: true,
            priority: 100,
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
        return results;
      }
    }

    // ─────────────────────────────────────────────────────────────
    // 7. GST / TAX INTENT
    // ─────────────────────────────────────────────────────────────
    if (dominantIntent === 'GST') {
      const gstRes = this.parseGst(raw);
      if (gstRes) {
        if (gstRes.partial) {
          if (gstRes.suggestions) {
            results.utilities.push({
              type: 'gst_interactive',
              isCalculatedResult: true,
              priority: 100,
              title: `🏛️ GST Breakdown for ${BillsoftUtils.formatCurrency(gstRes.amount)}`,
              subtitle: `5%: ${BillsoftUtils.formatCurrency(gstRes.amount * 1.05)} • 12%: ${BillsoftUtils.formatCurrency(gstRes.amount * 1.12)} • 18%: ${BillsoftUtils.formatCurrency(gstRes.amount * 1.18)} • 28%: ${BillsoftUtils.formatCurrency(gstRes.amount * 1.28)}`,
              icon: '🏛️',
              badge: 'GST Calculator',
              amount: gstRes.amount,
              suggestions: gstRes.suggestions,
              action: () => {
                const gst18 = Math.round((gstRes.amount * 1.18) * 100) / 100;
                if (navigator.clipboard) navigator.clipboard.writeText(`Base: ₹${gstRes.amount}, 18% GST: ₹${Math.round(gstRes.amount * 0.18 * 100) / 100}, Total: ₹${gst18}`);
                if (window.showToast) window.showToast(`Copied 18% GST breakdown for ₹${gstRes.amount}`, 'success');
              }
            });
          }
        } else {
          results.utilities.push({
            type: 'gst',
            isCalculatedResult: true,
            priority: 100,
            title: `💰 Base: ${BillsoftUtils.formatCurrency(gstRes.baseAmount)} | Total: ${BillsoftUtils.formatCurrency(gstRes.totalAmount)}`,
            subtitle: `${gstRes.rate}% GST = ${BillsoftUtils.formatCurrency(gstRes.gstAmount)} (CGST ${gstRes.rate / 2}%: ${BillsoftUtils.formatCurrency(gstRes.cgst)}, SGST ${gstRes.rate / 2}%: ${BillsoftUtils.formatCurrency(gstRes.sgst)})`,
            icon: '🏛️',
            badge: gstRes.isReverse ? 'Reverse GST' : 'GST Calculator',
            action: () => {
              if (navigator.clipboard) navigator.clipboard.writeText(`Base: ${gstRes.baseAmount.toFixed(2)}, GST (${gstRes.rate}%): ${gstRes.gstAmount.toFixed(2)}, Total: ${gstRes.totalAmount.toFixed(2)}`);
              if (window.showToast) window.showToast('Copied GST breakdown to clipboard', 'success');
            }
          });
        }
      }
      const pctCards = this.parsePercentageQuery(raw);
      if (pctCards && pctCards.length > 0) {
        pctCards.forEach(c => results.utilities.push(c));
      }
      if (results.utilities.length > 0) return results;
    }

    // ─────────────────────────────────────────────────────────────
    // 8. DISCOUNT INTENT
    // ─────────────────────────────────────────────────────────────
    if (dominantIntent === 'DISCOUNT') {
      const discountRes = this.parseDiscount(raw);
      if (discountRes && !discountRes.partial) {
        results.utilities.push({
          type: 'discount',
          isCalculatedResult: true,
          priority: 100,
          title: `🏷️ Final Price: ${BillsoftUtils.formatCurrency(discountRes.finalPrice)} (Save ${BillsoftUtils.formatCurrency(discountRes.discountAmount)})`,
          subtitle: `Original: ${BillsoftUtils.formatCurrency(discountRes.original)} with ${discountRes.rate}% discount`,
          icon: '🏷️',
          badge: 'Discount Calculator',
          action: () => {
            if (navigator.clipboard) navigator.clipboard.writeText(`Final: ${discountRes.finalPrice.toFixed(2)} (Discount: ${discountRes.discountAmount.toFixed(2)})`);
            if (window.showToast) window.showToast('Copied discount price', 'success');
          }
        });
        return results;
      }
    }

    // ─────────────────────────────────────────────────────────────
    // 9. MARGIN INTENT
    // ─────────────────────────────────────────────────────────────
    if (dominantIntent === 'MARGIN') {
      const marginRes = this.parseProfitMargin(raw);
      if (marginRes) {
        results.utilities.push({
          type: 'margin',
          isCalculatedResult: true,
          priority: 100,
          title: `📈 Profit: ${BillsoftUtils.formatCurrency(marginRes.profit)} | Margin: ${marginRes.marginPct}% | Markup: ${marginRes.markupPct}%`,
          subtitle: `Cost: ${BillsoftUtils.formatCurrency(marginRes.cost)} • Selling Price: ${BillsoftUtils.formatCurrency(marginRes.price)}`,
          icon: '📈',
          badge: 'Margin Calculator',
          action: () => {
            if (navigator.clipboard) navigator.clipboard.writeText(`Profit: ${marginRes.profit.toFixed(2)}, Margin: ${marginRes.marginPct}%, Markup: ${marginRes.markupPct}%`);
            if (window.showToast) window.showToast('Copied margin breakdown', 'success');
          }
        });
        return results;
      }
      const markupRes = this.parseMarkup(raw);
      if (markupRes) {
        results.utilities.push({
          type: 'markup',
          isCalculatedResult: true,
          priority: 100,
          title: `🏷️ Selling Price: ${BillsoftUtils.formatCurrency(markupRes.sellingPrice)} (+${BillsoftUtils.formatCurrency(markupRes.markupAmount)})`,
          subtitle: `Cost: ${BillsoftUtils.formatCurrency(markupRes.cost)} with ${markupRes.rate}% markup`,
          icon: '🏷️',
          badge: 'Markup Calculator',
          action: () => {
            if (navigator.clipboard) navigator.clipboard.writeText(`Selling Price: ${markupRes.sellingPrice.toFixed(2)}`);
            if (window.showToast) window.showToast('Copied selling price', 'success');
          }
        });
        return results;
      }
    }

    // ─────────────────────────────────────────────────────────────
    // 10. FINANCE & STATS INTENT
    // ─────────────────────────────────────────────────────────────
    if (dominantIntent === 'FINANCE') {
      const emiRes = this.parseEmi(raw);
      if (emiRes) {
        if (emiRes.partial) {
          if (emiRes.suggestions) {
            results.utilities.push({
              type: 'emi_interactive',
              isCalculatedResult: true,
              priority: 100,
              title: `🏦 Loan EMI Calculator ${emiRes.isCustomPrincipal ? `for ${BillsoftUtils.formatCurrency(emiRes.principal)}` : ''}`,
              subtitle: `Select tenure & rate to calculate monthly installment:`,
              icon: '🏦',
              badge: 'EMI Calculator',
              suggestions: emiRes.suggestions,
              action: () => {
                if (emiRes.suggestions && emiRes.suggestions[0]) {
                  const s = emiRes.suggestions[0];
                  if (navigator.clipboard) navigator.clipboard.writeText(`EMI for ₹${s.principal} @ ${s.rate}% for ${s.months}m: ₹${s.emi}/mo (Total: ₹${s.total})`);
                  if (window.showToast) window.showToast(`Selected: ₹${s.emi}/mo (${s.label})`, 'success');
                }
              }
            });
          }
        } else {
          results.utilities.push({
            type: 'emi',
            isCalculatedResult: true,
            priority: 100,
            title: `🏦 Monthly EMI: ${BillsoftUtils.formatCurrency(emiRes.emi)} / month`,
            subtitle: `Loan: ${BillsoftUtils.formatCurrency(emiRes.principal)} @ ${emiRes.annualRate}% for ${emiRes.tenureYears} Years (${emiRes.months} mos) • Total Interest: ${BillsoftUtils.formatCurrency(emiRes.totalInterest)} • Total: ${BillsoftUtils.formatCurrency(emiRes.totalAmount)}`,
            icon: '🏦',
            badge: 'Loan EMI Calculator',
            action: () => {
              if (navigator.clipboard) navigator.clipboard.writeText(`EMI: ₹${emiRes.emi}/mo, Total Interest: ₹${emiRes.totalInterest}, Total Payment: ₹${emiRes.totalAmount}`);
              if (window.showToast) window.showToast('Copied EMI details', 'success');
            }
          });
        }
      }

      const intRes = this.parseInterest(raw);
      if (intRes && !intRes.partial) {
        results.utilities.push({
          type: 'interest',
          isCalculatedResult: true,
          priority: 100,
          title: `📈 ${intRes.type === 'compound' ? 'Compound' : 'Simple'} Interest: ${BillsoftUtils.formatCurrency(intRes.interest)} | Total: ${BillsoftUtils.formatCurrency(intRes.total)}`,
          subtitle: `Principal: ${BillsoftUtils.formatCurrency(intRes.principal)} @ ${intRes.rate}% for ${intRes.time} Years`,
          icon: '📈',
          badge: intRes.type === 'compound' ? 'Compound Interest' : 'Simple Interest',
          action: () => {
            if (navigator.clipboard) navigator.clipboard.writeText(`Interest: ${intRes.interest.toFixed(2)}, Total: ${intRes.total.toFixed(2)}`);
            if (window.showToast) window.showToast('Copied interest breakdown', 'success');
          }
        });
      }

      const statsRes = this.parseStatistics(raw);
      if (statsRes) {
        if (statsRes.type === 'average') {
          results.utilities.push({
            type: 'stats_avg',
            isCalculatedResult: true,
            priority: 100,
            title: `📊 Average: ${statsRes.result}`,
            subtitle: `Sum: ${statsRes.sum} across ${statsRes.count} numbers (${statsRes.nums.join(', ')})`,
            icon: '📊',
            badge: 'Average',
            action: () => {
              if (navigator.clipboard) navigator.clipboard.writeText(String(statsRes.result));
              if (window.showToast) window.showToast(`Copied average: ${statsRes.result}`, 'success');
            }
          });
        } else if (statsRes.type === 'max' || statsRes.type === 'min') {
          results.utilities.push({
            type: 'stats_minmax',
            isCalculatedResult: true,
            priority: 100,
            title: `${statsRes.type === 'max' ? '🔼 Maximum' : '🔽 Minimum'}: ${statsRes.result}`,
            subtitle: `Evaluated from: ${statsRes.nums.join(', ')}`,
            icon: statsRes.type === 'max' ? '🔼' : '🔽',
            badge: statsRes.type === 'max' ? 'Max Value' : 'Min Value',
            action: () => {
              if (navigator.clipboard) navigator.clipboard.writeText(String(statsRes.result));
              if (window.showToast) window.showToast(`Copied value: ${statsRes.result}`, 'success');
            }
          });
        }
      }

      const lakhCroreRes = this.parseLakhCrore(raw);
      if (lakhCroreRes) {
        results.utilities.push({
          type: 'scale_converter',
          isCalculatedResult: true,
          priority: 100,
          title: `🌐 ${lakhCroreRes.val} ${lakhCroreRes.fromUnit.toUpperCase()} = ${lakhCroreRes.converted.toLocaleString('en-IN', { maximumFractionDigits: 4 })} ${lakhCroreRes.toUnit.toUpperCase()}`,
          subtitle: `Absolute value: ₹${lakhCroreRes.absoluteVal.toLocaleString('en-IN')}`,
          icon: '🌐',
          badge: 'Scale Converter',
          action: () => {
            if (navigator.clipboard) navigator.clipboard.writeText(String(lakhCroreRes.converted));
            if (window.showToast) window.showToast('Copied converted value', 'success');
          }
        });
      }

      if (results.utilities.length > 0) return results;
    }

    // ─────────────────────────────────────────────────────────────
    // 11. STANDALONE NUMBER INTENT
    // ─────────────────────────────────────────────────────────────
    if (dominantIntent === 'STANDALONE_NUMBER') {
      const standaloneCards = this.parseStandaloneAmount(raw);
      if (standaloneCards && standaloneCards.length > 0) {
        standaloneCards.forEach(c => results.utilities.push(c));
        return results;
      }
    }

    // ─────────────────────────────────────────────────────────────
    // 12. GENERAL SEARCH (Smart Tasks, Fallback Utilities, Actions, Entities)
    // ─────────────────────────────────────────────────────────────

    // A. Smart Tasks
    const inlineReminder = this.parseInlineReminder(raw);
    if (inlineReminder && inlineReminder.title && !inlineReminder.partial) {
      if (inlineReminder.type === 'reminder') {
        results.smartTasks.push({
          type: 'smart_reminder',
          title: inlineReminder.isScheduled ? `⏰ Set Reminder: "${inlineReminder.title}" (${inlineReminder.timeFormatted})` : `⏰ Set Reminder: "${inlineReminder.title}"`,
          subtitle: inlineReminder.isScheduled ? `📅 Scheduled for ${inlineReminder.timeFormatted} • Click to edit & save reminder` : `Add to Reminders • Click to edit & save reminder`,
          icon: '⏰',
          badge: inlineReminder.isScheduled ? '⏰ Scheduled Reminder' : '⏰ Reminder',
          payload: inlineReminder,
          editableConfig: {
            kind: 'reminder',
            initialValues: {
              title: inlineReminder.title,
              dueDate: inlineReminder.dueDate || '',
              timeFormatted: inlineReminder.timeFormatted || '',
              type: 'reminder',
              priority: 'MEDIUM'
            }
          },
          action: async () => {
            try {
              if (API.reminders && API.reminders.create) {
                const remData = {
                  title: inlineReminder.title,
                  type: 'reminder',
                  status: 'TODO',
                  priority: 'MEDIUM'
                };
                if (inlineReminder.dueDate) {
                  let dVal = String(inlineReminder.dueDate).trim();
                  if (dVal.length === 10) dVal = dVal + 'T09:00:00';
                  remData.dueDate = dVal;
                }
                await API.reminders.create(remData);
                if (window.showToast) {
                  window.showToast(inlineReminder.isScheduled ? `✓ Reminder scheduled for ${inlineReminder.timeFormatted}!` : `✓ Reminder created successfully!`, 'success');
                }
                window.dispatchEvent(new CustomEvent('billsoft:app-refresh'));
              }
            } catch (e) {
              if (window.showToast) window.showToast('Failed to create reminder: ' + (e.message || 'Error'), 'error');
            }
          }
        });
      } else if (inlineReminder.type === 'task') {
        results.smartTasks.push({
          type: 'smart_task',
          title: `✅ Add Task: "${inlineReminder.title}"`,
          subtitle: `Add to Planner Tasks • Click to edit & save task`,
          icon: '✅',
          badge: '✅ Planner Task',
          payload: inlineReminder,
          editableConfig: {
            kind: 'task',
            initialValues: {
              title: inlineReminder.title,
              dueDate: '',
              type: 'task',
              priority: 'MEDIUM'
            }
          },
          action: async () => {
            try {
              if (API.reminders && API.reminders.create) {
                await API.reminders.create({
                  title: inlineReminder.title,
                  type: 'task',
                  status: 'TODO',
                  priority: 'MEDIUM'
                });
                if (window.showToast) window.showToast(`✓ Task "${inlineReminder.title}" added to Planner!`, 'success');
                window.dispatchEvent(new CustomEvent('billsoft:app-refresh'));
              }
            } catch (e) {
              if (window.showToast) window.showToast('Failed to create task: ' + (e.message || 'Error'), 'error');
            }
          }
        });
      }
    }

    const inlineExpense = this.parseInlineExpense(raw);
    if (inlineExpense && inlineExpense.amount > 0) {
      results.smartTasks.push({
        type: 'smart_expense',
        title: `💸 Record Expense: ${BillsoftUtils.formatCurrency(inlineExpense.amount)}`,
        subtitle: `Title: "${inlineExpense.title}" • Category: General • Click to edit & save`,
        icon: '💸',
        badge: '⚡ Quick Save',
        payload: inlineExpense,
        editableConfig: {
          kind: 'expense',
          initialValues: {
            title: inlineExpense.title,
            amount: inlineExpense.amount,
            category: 'General',
            expenseDate: new Date().toISOString().slice(0, 10),
            paymentMode: 'Cash'
          }
        },
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

    const inlineNote = this.parseInlineNote(raw);
    if (inlineNote && inlineNote.title) {
      results.smartTasks.push({
        type: 'smart_note',
        title: `📌 Save Sticky Note: "${inlineNote.title}"`,
        subtitle: 'Save to Planner Board • Click to edit & add note',
        icon: '📌',
        badge: '⚡ Quick Note',
        payload: inlineNote,
        editableConfig: {
          kind: 'note',
          initialValues: {
            title: inlineNote.title,
            color: 'yellow'
          }
        },
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

    const inlineCust = this.parseInlineCustomer(raw);
    if (inlineCust && inlineCust.name && inlineCust.phone) {
      results.smartTasks.push({
        type: 'smart_customer',
        title: `👤 Register Customer: ${inlineCust.name} (📞 ${inlineCust.phone})`,
        subtitle: `${inlineCust.city ? 'City: ' + inlineCust.city + ' • ' : ''}Click to edit & register customer`,
        icon: '👤',
        badge: '⚡ Quick Register',
        payload: inlineCust,
        editableConfig: {
          kind: 'customer',
          initialValues: {
            name: inlineCust.name,
            phone: inlineCust.phone,
            city: inlineCust.city || '',
            openingBalance: 0
          }
        },
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

    const inlineDues = this.parseInlineDuesReminder(raw, customers, ctx.firm);
    if (inlineDues && !inlineDues.partial && inlineDues.name) {
      const f = ctx.firm || {};
      const msg = `Dear ${inlineDues.name},\nThis is a gentle reminder that your outstanding balance with *${f.firmName || 'our store'}* is *${BillsoftUtils.formatCurrency(inlineDues.balance)}*.\nKindly clear the pending dues at your earliest convenience. Thank you!`;
      results.smartTasks.push({
        type: 'smart_dues_reminder',
        title: `💬 WhatsApp Dues Reminder: ${inlineDues.name}`,
        subtitle: `Pending Balance: ${BillsoftUtils.formatCurrency(inlineDues.balance)} • 📞 ${inlineDues.phone || 'No phone'}`,
        icon: '💬',
        badge: 'WhatsApp Dues',
        payload: inlineDues,
        editableConfig: {
          kind: 'dues',
          initialValues: {
            name: inlineDues.name,
            phone: inlineDues.phone || '',
            balance: inlineDues.balance || 0,
            msg: msg
          }
        },
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

    const inlineAtt = this.parseInlineAttendance(raw, staff);
    if (inlineAtt && inlineAtt.employee) {
      results.smartTasks.push({
        type: 'smart_attendance',
        title: `📅 Mark Attendance: ${inlineAtt.name}`,
        subtitle: `Employee: ${inlineAtt.name} (${inlineAtt.employee.role || 'Staff'}) • Click to edit & mark attendance`,
        icon: '📅',
        badge: 'HR Attendance',
        payload: inlineAtt,
        editableConfig: {
          kind: 'attendance',
          initialValues: {
            employeeId: inlineAtt.employee ? inlineAtt.employee.id : '',
            employeeName: inlineAtt.name,
            date: new Date().toISOString().slice(0, 10),
            status: 'PRESENT',
            remarks: ''
          }
        },
        action: () => {
          BillsoftSearchEngine.dispatchNavigate({ page: 'hr', hrTab: 'attendance' });
        }
      });
    }

    const inlineAdv = this.parseInlineAdvance(raw, staff);
    if (inlineAdv && inlineAdv.employee && inlineAdv.amount > 0) {
      results.smartTasks.push({
        type: 'smart_advance',
        title: `💰 Record ${BillsoftUtils.formatCurrency(inlineAdv.amount)} Advance: ${inlineAdv.name}`,
        subtitle: `Staff: ${inlineAdv.name} • Amount: ${BillsoftUtils.formatCurrency(inlineAdv.amount)} • Click to edit & save advance`,
        icon: '💰',
        badge: 'Salary Advance',
        payload: inlineAdv,
        editableConfig: {
          kind: 'advance',
          initialValues: {
            employeeId: inlineAdv.employee ? inlineAdv.employee.id : '',
            employeeName: inlineAdv.name,
            amount: inlineAdv.amount,
            date: new Date().toISOString().slice(0, 10),
            paymentMode: 'Cash',
            reason: 'Salary Advance'
          }
        },
        action: () => {
          BillsoftSearchEngine.dispatchNavigate({ page: 'hr', hrTab: 'advances' });
        }
      });
    }

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
            BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'paperwork', subTab: 'statements', statementMode: 'customer' });
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
            BillsoftSearchEngine.dispatchNavigate({ page: 'firm', tab: 'paperwork', subTab: 'orders' });
          }
        });
      }
    }

    // B. Fallback Utilities
    const keywordCards = this.parseKeywordTemplates(raw);
    if (keywordCards && keywordCards.length > 0) {
      keywordCards.forEach(c => results.utilities.push(c));
    }

    const dateMathRes = this.parseDateMath(raw);
    if (dateMathRes) {
      results.utilities.push({
        type: 'date_math',
        isCalculatedResult: true,
        priority: 95,
        title: dateMathRes.title,
        subtitle: dateMathRes.subtitle,
        icon: '📅',
        badge: 'Date Helper',
        action: () => {
          if (navigator.clipboard) navigator.clipboard.writeText(dateMathRes.title);
          if (window.showToast) window.showToast('Copied date info', 'success');
        }
      });
    }

    const textRes = this.parseTextUtilities(raw);
    if (textRes) {
      results.utilities.push({
        type: 'text_tool',
        isCalculatedResult: true,
        priority: 90,
        title: textRes.title,
        subtitle: textRes.subtitle,
        icon: '🔤',
        badge: 'Text Tool',
        action: () => {
          if (navigator.clipboard) navigator.clipboard.writeText(textRes.result);
          if (window.showToast) window.showToast('Copied text to clipboard', 'success');
        }
      });
    }

    const sysRes = this.parseSystemControls(raw);
    if (sysRes) {
      results.utilities.push({
        type: sysRes.type,
        title: sysRes.title,
        subtitle: sysRes.subtitle,
        icon: sysRes.icon,
        badge: 'System Control',
        action: sysRes.action
      });
    }

    if (['bank', 'bank details', 'ifsc', 'account number', 'bank account', 'firm gstin', 'company address'].some(k => q.includes(k))) {
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

    // C. Actions & Intents
    for (const act of this.ACTIONS) {
      let bestScore = 0;
      for (const kw of act.keywords) {
        const fm = this.fuzzyMatch(raw, kw);
        if (fm.match && fm.score > bestScore) {
          bestScore = fm.score;
        }
      }
      const tm = this.fuzzyMatch(raw, act.title);
      if (tm.match && tm.score > bestScore) {
        bestScore = tm.score;
      }
      if (bestScore > 0) {
        results.actions.push({ ...act, score: bestScore });
      }
    }
    results.actions.sort((a, b) => (b.score || 0) - (a.score || 0));

    if (results.smartTasks.length > 0) {
      results.actions = results.actions.filter(a => (a.score || 0) >= 90);
    } else if (results.utilities.length > 0) {
      results.actions = results.actions.filter(a => (a.score || 0) >= 88);
    }

    // D. Entity Matching
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

    for (const est of estimates) {
      if (!est) continue;
      if (matchSub(est.estimateNumber) || matchSub(est.invoiceNumber) || matchText(est.customerName) || matchSub(est.customerName)) {
        results.estimates.push(est);
      }
    }

    for (const po of purchaseOrders) {
      if (!po) continue;
      if (matchSub(po.poNumber) || matchText(po.partyName) || matchSub(po.partyName) || matchSub(po.status)) {
        results.purchaseOrders.push(po);
      }
    }

    for (const exp of expenses) {
      if (!exp) continue;
      if (matchText(exp.title) || matchSub(exp.title) || matchText(exp.category) || matchSub(exp.category)) {
        results.expenses.push(exp);
      }
    }

    for (const ret of returns) {
      if (!ret) continue;
      if (matchSub(ret.returnNumber) || matchText(ret.customerName) || matchSub(ret.customerName) || matchSub(ret.reason)) {
        results.returns.push(ret);
      }
    }

    for (const letDoc of letters) {
      if (!letDoc) continue;
      if (matchText(letDoc.title) || matchSub(letDoc.title) || matchText(letDoc.recipientName) || matchSub(letDoc.recipientName) || matchSub(letDoc.letterNumber)) {
        results.letters.push(letDoc);
      }
    }

    for (const rem of reminders) {
      if (!rem) continue;
      if (matchText(rem.title) || matchSub(rem.title) || matchSub(rem.type) || matchSub(rem.status)) {
        results.reminders.push(rem);
      }
    }

    // E. Suggestions
    if (results.smartTasks.length === 0 && results.actions.length === 0 && results.customers.length === 0 && results.invoices.length === 0 && results.utilities.length === 0 && results.staff.length === 0 && results.products.length === 0 && results.parties.length === 0) {
      results.suggestions = this.getSuggestions(raw);
    }

    return results;
  },

  processQuery(query, ctx = {}) {
    if (typeof window !== 'undefined' && window.OmnibarPipeline && window.OmnibarPipeline.processQuery) {
      return window.OmnibarPipeline.processQuery(query, ctx);
    }
    if (typeof require !== 'undefined') {
      try {
        const pipeline = require('./omnibarPipeline.js');
        return pipeline.processQuery(query, ctx);
      } catch (e) { }
    }
    return null;
  }
};

window.dispatchNotificationAction = async function (notif, choice = 'PRIMARY') {
  if (!notif) return;
  const choiceUpper = (choice || 'PRIMARY').toUpperCase();
  const notifId = notif.id || notif.messageId;
  const actionType = choiceUpper === 'PRIMARY' ? notif.primaryActionType : notif.secondaryActionType;
  const target = choiceUpper === 'PRIMARY' ? notif.primaryActionTarget : notif.secondaryActionTarget;

  try {
    if (typeof API !== 'undefined' && API.notifications && typeof API.notifications.executeAction === 'function' && notifId) {
      await API.notifications.executeAction(notifId, choiceUpper);
    } else if (typeof API !== 'undefined' && API.notifications && typeof API.notifications.action === 'function' && notifId) {
      await API.notifications.action(notifId, choiceUpper);
    }
  } catch (err) {
    console.warn('Notification action recording error:', err);
  }

  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('billsoft:notifications-refresh'));
  }

  if (!actionType || actionType === 'NAVIGATE') {
    if (target) {
      if (target.startsWith('http://') || target.startsWith('https://')) {
        if (typeof window !== 'undefined') window.open(target, '_blank');
      } else if (typeof BillsoftSearchEngine !== 'undefined' && BillsoftSearchEngine.dispatchNavigate) {
        try {
          const parsed = JSON.parse(target);
          BillsoftSearchEngine.dispatchNavigate(parsed);
        } catch {
          BillsoftSearchEngine.dispatchNavigate({ page: target });
        }
      } else if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('billsoft:navigate-subtab', { detail: { page: target } }));
      }
    }
  } else if (actionType === 'MODAL') {
    if (target) {
      try {
        const parsed = JSON.parse(target);
        if (parsed.modal === 'payment' && parsed.invoiceId) {
          if (typeof BillsoftSearchEngine !== 'undefined' && BillsoftSearchEngine.dispatchNavigate) {
            BillsoftSearchEngine.dispatchNavigate({ page: 'invoices', invoiceId: parsed.invoiceId, subTab: 'invoices', openPaymentModal: true });
          }
        } else if (typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent('billsoft:open-modal', { detail: parsed }));
        }
      } catch {
        if (typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent('billsoft:open-modal', { detail: { modal: target } }));
        }
      }
    }
  } else if (actionType === 'API_ACTION') {
    if (typeof window !== 'undefined' && window.showToast) window.showToast('Action completed successfully', 'success');
    if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent('billsoft:app-refresh'));
  }
};

if (typeof window !== 'undefined') {
  window.BillsoftUtils = typeof BillsoftUtils !== 'undefined' ? BillsoftUtils : (window.BillsoftUtils || {});
  window.BillsoftSearchEngine = BillsoftSearchEngine;
  window.BillsoftUtils.searchEngine = BillsoftSearchEngine;
  window.getInitials = BillsoftUtils.getInitials;
}
if (typeof module !== 'undefined' && module.exports) {
  module.exports = { BillsoftUtils, BillsoftSearchEngine, getInitials: BillsoftUtils.getInitials };
}

