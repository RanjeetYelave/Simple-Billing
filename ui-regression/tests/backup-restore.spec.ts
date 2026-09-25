import { test, expect } from '../fixtures/base-fixture';

test.describe('Backup, Vault & Data Protection Suite', () => {
  test('BAK-01: System Backup & Vault UI Navigation and Controls Verification', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Settings');

    // Click Backup & Vault subtab
    const backupTab = page.locator('button:has-text("Backup & Vault")').first();
    await expect(backupTab).toBeVisible({ timeout: 10000 });
    await backupTab.click();
    await page.waitForTimeout(400);

    // Verify presence of Backup cards
    const exportCard = page.locator('text=Export Manual Backup').first();
    await expect(exportCard).toBeVisible({ timeout: 8000 });

    const exportBtn = page.locator('button:has-text("Export All Firms")').first();
    await expect(exportBtn).toBeVisible({ timeout: 8000 });

    // Verify Cloud Vault backup button exists
    const cloudBackupBtn = page.locator('button:has-text("Backup Now to Cloud Vault")').first();
    await expect(cloudBackupBtn).toBeVisible({ timeout: 8000 });

    await errorGate.assertZeroErrors(page, 'Safety Backup UI & Controls');
  });

  test('BAK-02: End-to-End Backup Export, Entity Modification & Live Database Restore', async ({ request, page, app, errorGate }) => {
    const testRunId = Date.now();
    const backupTestCustName = `BAK_Cust_${testRunId}`;

    // 1. Create a known test record (Customer) under active firm
    const firmsRes = await request.get('/api/firm');
    const firms = await firmsRes.json();
    const activeFirmId = Array.isArray(firms) && firms.length > 0 ? firms[0].id : 1;

    const createCustRes = await request.post('/api/customers', {
      data: {
        name: backupTestCustName,
        phone: '9876543210',
        city: 'Pune',
        firmId: activeFirmId
      },
      headers: { 'X-Firm-Id': String(activeFirmId) }
    });
    expect(createCustRes.status()).toBe(200);
    const createdCust = await createCustRes.json();
    expect(createdCust.id).toBeDefined();

    // 2. Trigger real backup/export workflow
    const exportRes = await request.get('/api/backup/export/all');
    expect(exportRes.status()).toBe(200);
    const backupBuffer = await exportRes.body();
    const backupJson = JSON.parse(backupBuffer.toString('utf-8'));

    // Assert that the backup artifact contains our known customer
    const customersInBackup = backupJson.customers || [];
    const customerFound = customersInBackup.some((c: any) => c.name === backupTestCustName);
    expect(customerFound).toBeTruthy();

    // 3. Delete the known customer to simulate data loss / modification
    if (createdCust.id) {
      const deleteRes = await request.delete(`/api/customers/${createdCust.id}`);
      expect([200, 204]).toContain(deleteRes.status());
    }

    // Verify customer is gone from active queries
    const verifyDeleteRes = await request.get('/api/customers', {
      headers: { 'X-Firm-Id': String(activeFirmId) }
    });
    const remainingCustomers = await verifyDeleteRes.json();
    const isStillPresent = remainingCustomers.some((c: any) => c.name === backupTestCustName);
    expect(isStillPresent).toBeFalsy();

    // 4. Invoke ACTUAL restore workflow via POST /api/backup/import with multipart file
    const restoreRes = await request.post('/api/backup/import', {
      multipart: {
        file: {
          name: `billsoft-restore-${testRunId}.json`,
          mimeType: 'application/json',
          buffer: backupBuffer
        },
        mode: 'merge',
        firmId: String(activeFirmId)
      }
    });
    expect(restoreRes.status()).toBe(200);
    const restoreResult = await restoreRes.json();
    expect(restoreResult.status).toBe('success');
    expect(restoreResult.message).toContain('restored');

    // 5. Verify the known record survives and is fully restored in database
    const verifyRestoredRes = await request.get('/api/customers', {
      headers: { 'X-Firm-Id': String(activeFirmId) }
    });
    const restoredCustomers = await verifyRestoredRes.json();
    const isRestored = restoredCustomers.some((c: any) => c.name === backupTestCustName);
    expect(isRestored).toBeTruthy();

    // 6. Verify UI loads cleanly and displays the restored customer
    await app.gotoApp();
    await app.navigateTo('Firm');
    await page.locator('button:has-text("Customers")').first().click();
    await page.waitForTimeout(400);

    const restoredRow = page.locator(`.table-container table tbody tr:has-text("${backupTestCustName}")`).first();
    await expect(restoredRow).toBeVisible({ timeout: 10000 });

    await errorGate.assertZeroErrors(page, 'Database Restore Verification');
  });
});
