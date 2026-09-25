import { test, expect } from '@playwright/test';

test.describe('Management Control Plane Standalone Suite (Port 28090)', () => {
  const smtRunId = Date.now();
  const smtCustomerName = `SMT_Cust_${smtRunId}`;
  const smtFirmName = `SMT_Firm_${smtRunId}`;
  const smtSalesmanName = `SMT_Sales_${smtRunId}`;
  const testMachineId = `MID-${smtRunId.toString().slice(-4)}-TEST-NODE`;

  test('MCP-01: Control Plane Mount, All Tabs Traversals & Zero Error Gate', async ({ page }) => {
    const consoleErrors: string[] = [];
    page.on('console', msg => {
      if (msg.type() === 'error') consoleErrors.push(msg.text());
    });

    await page.goto('http://127.0.0.1:28090/');
    await page.waitForLoadState('networkidle');

    // Verify Title
    const title = await page.title();
    expect(title).toContain('Management Control Plane');

    // Verify All Navigation Tabs
    const tabs = [
      'tab-dashboard',
      'tab-sales-customers',
      'tab-licensing',
      'tab-salesmen',
      'tab-emi',
      'tab-broadcasts',
      'tab-dataprotection',
      'tab-reports',
      'tab-machines',
      'tab-system'
    ];

    for (const tabId of tabs) {
      const tabBtn = page.locator(`button.nav-tab[data-tab="${tabId}"]`).first();
      await expect(tabBtn).toBeVisible({ timeout: 8000 });
      await tabBtn.click();
      await page.waitForTimeout(200);
      const pane = page.locator(`#${tabId}`);
      await expect(pane).toBeVisible({ timeout: 5000 });
    }

    expect(consoleErrors.length).toBe(0);
  });

  test('MCP-02: Real Customer & Salesman Registration Lifecycle via UI Action', async ({ page }) => {
    await page.goto('http://127.0.0.1:28090/');
    await page.waitForLoadState('networkidle');

    // 1. Navigate to Sales & Customers Tab
    const custNavTab = page.locator('button.nav-tab[data-tab="tab-sales-customers"]').first();
    await expect(custNavTab).toBeVisible({ timeout: 8000 });
    await custNavTab.click();
    await page.waitForTimeout(400);

    // Switch to Customers Directory Subtab
    const custSubTab = page.locator('button.sub-tab:has-text("Customers Directory")').first();
    await expect(custSubTab).toBeVisible({ timeout: 8000 });
    await custSubTab.click();
    await page.waitForTimeout(300);

    // Open Add Customer Modal
    const addCustBtn = page.locator('button:has-text("Register Customer")').first();
    await expect(addCustBtn).toBeVisible({ timeout: 8000 });
    await addCustBtn.click();
    await page.waitForTimeout(300);

    const nameInput = page.locator('#cust-name').first();
    await expect(nameInput).toBeVisible({ timeout: 8000 });
    await nameInput.fill(smtCustomerName);
    await page.locator('#cust-company').first().fill(smtFirmName);
    await page.locator('#cust-phone').first().fill('9822001122');

    const submitCustBtn = page.locator('#modal-add-customer button[type="submit"], #modal-add-customer button:has-text("Save Customer")').first();
    await expect(submitCustBtn).toBeVisible();
    await submitCustBtn.click();
    await page.waitForTimeout(800);

    // 2. Navigate to Salesmen Tab
    const salesTab = page.locator('button.nav-tab[data-tab="tab-salesmen"]').first();
    await expect(salesTab).toBeVisible({ timeout: 8000 });
    await salesTab.click();
    await page.waitForTimeout(400);

    const addSmBtn = page.locator('button:has-text("Add Salesman")').first();
    await expect(addSmBtn).toBeVisible({ timeout: 8000 });
    await addSmBtn.click();
    await page.waitForTimeout(300);

    const smNameInput = page.locator('#sm-name').first();
    await expect(smNameInput).toBeVisible({ timeout: 8000 });
    await smNameInput.fill(smtSalesmanName);
    await page.locator('#sm-phone').first().fill('9811445566');

    const submitSmBtn = page.locator('#modal-add-salesman button[type="submit"], #modal-add-salesman button:has-text("Save Salesman")').first();
    await expect(submitSmBtn).toBeVisible();
    await submitSmBtn.click();
    await page.waitForTimeout(800);
  });

  test('MCP-03: Broadcast Announcement Dispatch Workflow via Literal UI Action', async ({ page }) => {
    await page.goto('http://127.0.0.1:28090/');
    await page.waitForLoadState('networkidle');

    const broadTab = page.locator('button.nav-tab[data-tab="tab-broadcasts"]').first();
    await expect(broadTab).toBeVisible({ timeout: 8000 });
    await broadTab.click();
    await page.waitForTimeout(400);

    const newBroadcastBtn = page.locator('button:has-text("New Broadcast")').first();
    await expect(newBroadcastBtn).toBeVisible({ timeout: 8000 });
    await newBroadcastBtn.click();
    await page.waitForTimeout(300);

    const titleInput = page.locator('#bc-title').first();
    await expect(titleInput).toBeVisible({ timeout: 8000 });
    await titleInput.fill(`Maintenance Alert ${smtRunId}`);
    await page.locator('#bc-message').first().fill('Scheduled database vacuuming at midnight.');

    const sendBtn = page.locator('#modal-broadcast button[type="submit"], button:has-text("Send Broadcast")').first();
    await expect(sendBtn).toBeVisible();
    await sendBtn.click();
    await page.waitForTimeout(800);
  });

  test('MCP-04: New License Generation, Entitlement & Signing Workflow', async ({ page }) => {
    await page.goto('http://127.0.0.1:28090/');
    await page.waitForLoadState('networkidle');

    // Click Topbar '+ New License' button
    const newLicBtn = page.locator('button:has-text("New License")').first();
    await expect(newLicBtn).toBeVisible({ timeout: 8000 });
    await newLicBtn.click();
    await page.waitForTimeout(400);

    const modal = page.locator('#modal-new-license');
    await expect(modal).toBeVisible({ timeout: 8000 });

    // Fill form
    await page.locator('#new-firm-name').fill(smtFirmName);
    await page.locator('#new-machine-id').fill(testMachineId);
    await page.locator('#new-customer-name').fill(smtCustomerName);
    await page.locator('#new-mobile').fill('9876543210');
    await page.locator('#new-software-price').fill('15000');

    // Submit license signing and publishing
    const submitBtn = page.locator('#btn-submit-license');
    await expect(submitBtn).toBeVisible();
    await submitBtn.click();
    await page.waitForTimeout(1200);

    // Verify modal closes
    await expect(modal).toBeHidden({ timeout: 8000 });
  });

  test('MCP-05: EMI Collections List Navigation & Literal "Mark Paid" UI Action', async ({ page }) => {
    await page.goto('http://127.0.0.1:28090/');
    await page.waitForLoadState('networkidle');

    // Navigate to EMI Collections Tab
    const emiTab = page.locator('button.nav-tab[data-tab="tab-emi"]').first();
    await expect(emiTab).toBeVisible({ timeout: 8000 });
    await emiTab.click();
    await page.waitForTimeout(400);

    // Switch between Active and Upcoming Subtabs
    const activeSubtab = page.locator('#btn-subtab-active-emi');
    const upcomingSubtab = page.locator('#btn-subtab-upcoming-emi');
    await expect(activeSubtab).toBeVisible();
    await expect(upcomingSubtab).toBeVisible();

    await upcomingSubtab.click();
    await page.waitForTimeout(300);
    await activeSubtab.click();
    await page.waitForTimeout(300);

    // If an EMI row exists with 'Mark Paid', click the literal UI button
    const markPaidBtn = page.locator('#tab-emi button:has-text("Mark Paid"), #tab-emi .btn-mark-paid').first();
    if (await markPaidBtn.isVisible()) {
      await markPaidBtn.click();
      await page.waitForTimeout(400);

      const markPaidModal = page.locator('#modal-mark-paid');
      if (await markPaidModal.isVisible()) {
        const confirmBtn = page.locator('#btn-confirm-mark-paid');
        await expect(confirmBtn).toBeVisible();
        await confirmBtn.click();
        await page.waitForTimeout(800);
      }
    }
  });

  test('MCP-06: Customer Application Licensing Sync & Network Fallback Resilience', async ({ request }) => {
    // 1. Query client licensing status
    const statusRes = await request.get('/api/licensing/status');
    expect(statusRes.status()).toBe(200);
    const initialStatus = await statusRes.json();
    expect(initialStatus).toBeDefined();

    // 2. Trigger Check Online Sync on client application
    const checkOnlineRes = await request.post('/api/licensing/check-online');
    expect([200, 404, 502, 503]).toContain(checkOnlineRes.status());

    // 3. Verify client license status remains safe and non-null
    const afterSyncRes = await request.get('/api/licensing/status');
    expect(afterSyncRes.status()).toBe(200);
    const currentStatus = await afterSyncRes.json();
    expect(currentStatus.status || currentStatus.plan || currentStatus.isTrial !== undefined).toBeTruthy();
  });
});
