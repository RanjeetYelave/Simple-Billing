import { Page, Request, Response } from '@playwright/test';

export interface ErrorReport {
  pageErrors: string[];
  consoleErrors: string[];
  failedRequests: { url: string; method: string; failure: string }[];
  httpErrors: { url: string; method: string; status: number }[];
  errorBoundariesFound: string[];
}

export class ErrorGate {
  private pageErrors: string[] = [];
  private consoleErrors: string[] = [];
  private failedRequests: { url: string; method: string; failure: string }[] = [];
  private httpErrors: { url: string; method: string; status: number }[] = [];
  private expectedHttpErrors: Set<string> = new Set();

  private static ALLOWLISTED_CONSOLE_MESSAGES = [
    'Download the React DevTools',
    'favicon.ico',
    '[HMR]',
    'react-devtools'
  ];

  attach(page: Page) {
    page.on('pageerror', (err: Error) => {
      this.pageErrors.push(`[PageError] ${err.name}: ${err.message}\n${err.stack || ''}`);
    });

    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        const text = msg.text();
        const isAllowlisted = ErrorGate.ALLOWLISTED_CONSOLE_MESSAGES.some(pattern => text.includes(pattern));
        if (!isAllowlisted) {
          this.consoleErrors.push(`[ConsoleError] ${text}`);
        }
      }
    });

    page.on('requestfailed', (req: Request) => {
      const url = req.url();
      if (!url.includes('favicon.ico')) {
        this.failedRequests.push({
          url,
          method: req.method(),
          failure: req.failure()?.errorText || 'Unknown failure'
        });
      }
    });

    page.on('response', (res: Response) => {
      const status = res.status();
      const url = res.url();
      // Detect unexpected 5xx server errors or 4xx errors on primary app APIs
      if (status >= 500 || (status >= 400 && !this.isExpectedError(url, status))) {
        if (!url.includes('favicon.ico')) {
          this.httpErrors.push({
            url,
            method: res.request().method(),
            status
          });
        }
      }
    });
  }

  expectHttpError(urlPattern: string) {
    this.expectedHttpErrors.add(urlPattern);
  }

  private isExpectedError(url: string, status: number): boolean {
    for (const pattern of this.expectedHttpErrors) {
      if (url.includes(pattern)) return true;
    }
    return false;
  }

  async checkForErrorBoundary(page: Page): Promise<string[]> {
    const errorBoundaries: string[] = [];
    try {
      const errorModal = page.locator('.modal-overlay:has-text("Something went wrong"), .error-boundary, text="An unexpected error occurred"');
      if (await errorModal.count() > 0 && await errorModal.first().isVisible()) {
        const text = await errorModal.first().textContent();
        errorBoundaries.push(`[ErrorBoundary] Detected fatal UI fallback: ${text?.trim()}`);
      }
    } catch (e) {
      // Ignore inspection errors
    }
    return errorBoundaries;
  }

  async assertZeroErrors(page: Page, stepDescription: string = '') {
    const errorBoundaries = await this.checkForErrorBoundary(page);

    const hasErrors =
      this.pageErrors.length > 0 ||
      this.consoleErrors.length > 0 ||
      this.failedRequests.length > 0 ||
      this.httpErrors.length > 0 ||
      errorBoundaries.length > 0;

    if (hasErrors) {
      const report: ErrorReport = {
        pageErrors: [...this.pageErrors],
        consoleErrors: [...this.consoleErrors],
        failedRequests: [...this.failedRequests],
        httpErrors: [...this.httpErrors],
        errorBoundariesFound: errorBoundaries
      };

      const diagnostics = [
        `\n======================================================`,
        `🚨 UI REGRESSION GATE FAILURE at step: "${stepDescription}"`,
        `URL: ${page.url()}`,
        `======================================================`,
        report.errorBoundariesFound.length ? `[ErrorBoundary Fallbacks Detected]:\n${report.errorBoundariesFound.join('\n')}\n` : '',
        report.pageErrors.length ? `[Uncaught JavaScript PageErrors]:\n${report.pageErrors.join('\n')}\n` : '',
        report.consoleErrors.length ? `[Console Errors]:\n${report.consoleErrors.join('\n')}\n` : '',
        report.failedRequests.length ? `[Failed Network Requests]:\n${JSON.stringify(report.failedRequests, null, 2)}\n` : '',
        report.httpErrors.length ? `[Unexpected HTTP Failures]:\n${JSON.stringify(report.httpErrors, null, 2)}\n` : '',
        `======================================================\n`
      ].filter(Boolean).join('\n');

      // Clear for subsequent checks in same test if needed
      this.pageErrors = [];
      this.consoleErrors = [];
      this.failedRequests = [];
      this.httpErrors = [];

      throw new Error(diagnostics);
    }
  }

  clear() {
    this.pageErrors = [];
    this.consoleErrors = [];
    this.failedRequests = [];
    this.httpErrors = [];
    this.expectedHttpErrors.clear();
  }
}
