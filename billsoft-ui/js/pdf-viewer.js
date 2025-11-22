// js/pdf-viewer.js
import { $ } from "./utils.js";

export const pdfViewer = {

  currentUrl: null,

  open(blob, filename = "invoice.pdf", title = "Invoice PDF") {
    const url = URL.createObjectURL(blob);
    this.currentUrl = url;

    const modal = $("pdfModal");
    const embed = $("pdfEmbed");
    const link = $("pdfDownloadLink");
    const titleEl = $("pdfTitle");
    const closeBtn = $("pdfCloseBtn");

    embed.src = url;
    link.href = url;
    link.download = filename;
    titleEl.textContent = title;

    closeBtn.onclick = () => this.close();

    modal.classList.remove("hidden");
  },

  close() {
    const modal = $("pdfModal");
    modal.classList.add("hidden");

    $("pdfEmbed").src = "";
    $("pdfDownloadLink").href = "#";

    if (this.currentUrl) {
      URL.revokeObjectURL(this.currentUrl);
      this.currentUrl = null;
    }
  }
};
