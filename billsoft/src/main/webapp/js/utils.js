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
    if (filename.toLowerCase().endsWith('.pdf') && blob.type !== 'application/pdf') {
      blob = new Blob([blob], { type: 'application/pdf' });
    }
    const reader = new FileReader();
    reader.onload = () => {
      const a = document.createElement('a');
      a.href = reader.result;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    };
    reader.readAsDataURL(blob);
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
        @media print {
          body > *:not(.print-modal-overlay) { display: none !important; }
          .print-modal-overlay { 
            position: absolute !important; top: 0 !important; left: 0 !important; 
            width: 100% !important; height: auto !important; 
            background: transparent !important; display: block !important; 
          }
          .print-modal-overlay > div:first-child { display: none !important; }
          #pdf-render-container { 
            padding: 0 !important; overflow: visible !important; 
            display: block !important; height: auto !important;
          }
          #pdf-render-container img { 
            max-width: 100% !important; width: 100% !important;
            page-break-after: always; margin: 0 !important;
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
