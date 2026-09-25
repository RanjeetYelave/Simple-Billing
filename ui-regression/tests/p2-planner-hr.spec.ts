import { test, expect } from '../fixtures/base-fixture';

test.describe('P2: Real Planner Tasks, Notes, HR Staff Lifecycle & Critical REST Endpoints', () => {
  const testRunId = Date.now();
  const testEmpName = `Staff_${testRunId}`;
  const testTaskTitle = `Task_Urgent_${testRunId}`;
  const testNoteTitle = `Memo_Audit_${testRunId}`;

  test('P2-HR-01: Employee Staff Creation, Validation & Directory Persistence', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('HR');

    const addStaffBtn = page.locator('button:has-text("Add Staff Member")').first();
    await expect(addStaffBtn).toBeVisible({ timeout: 10000 });
    await addStaffBtn.click();
    await page.waitForTimeout(300);

    const modal = page.locator('.modal-overlay').first();
    await expect(modal).toBeVisible({ timeout: 8000 });

    // Fill employee details
    await modal.locator('input.input').first().fill(testEmpName);

    // Monthly Base Salary is required
    const salaryInput = modal.locator('input[type="number"]').first();
    await salaryInput.fill('35000');

    // Submit
    const saveBtn = modal.locator('button:has-text("Save Staff Member")').first();
    await saveBtn.click();
    await page.waitForTimeout(1000);

    // Verify persistence in staff table
    const empRow = page.locator(`.table-container table tbody tr:has-text("${testEmpName}")`).first();
    await expect(empRow).toBeVisible({ timeout: 10000 });

    await errorGate.assertZeroErrors(page, 'HR Staff Creation & Directory Persistence');
  });

  test('P2-HR-02: Employee Advance, Salary & Bulk Processing Endpoints', async ({ request }) => {
    // 1. Get firm ID
    const firmsRes = await request.get('/api/firm');
    const firms = await firmsRes.json();
    const firmId = firms && firms.length > 0 ? firms[0].id : 1;

    // 2. Create an employee for salary/advance processing
    const empRes = await request.post('/api/employees', {
      data: {
        name: `Salary_Staff_${testRunId}`,
        monthlyBaseSalary: 45000,
        department: 'Operations',
        role: 'Supervisor',
        firmId
      },
      headers: { 'X-Firm-Id': String(firmId) }
    });
    const emp = await empRes.json();
    expect(emp.id).toBeDefined();

    // 3. Exercise POST /api/employees/{id}/advances
    const advRes = await request.post(`/api/employees/${emp.id}/advances`, {
      data: {
        amount: 5000,
        description: `Festival Advance ${testRunId}`
      },
      headers: { 'X-Firm-Id': String(firmId) }
    });
    expect(advRes.status()).toBe(200);
    const advData = await advRes.json();
    expect(advData.amount).toBe(5000);

    // 4. Exercise POST /api/employees/{id}/salaries
    const monthYear = `09-2026-${testRunId.toString().slice(-4)}`;
    const salRes = await request.post(`/api/employees/${emp.id}/salaries`, {
      data: {
        monthYear,
        baseSalaryAtTime: 45000,
        advanceDeducted: 2000,
        bonusAmount: 1000,
        leaveDeductionAmount: 0
      },
      headers: { 'X-Firm-Id': String(firmId) }
    });
    expect(salRes.status()).toBe(200);
    const salData = await salRes.json();
    expect(salData.netPaid).toBe(44000); // 45000 + 1000 - 2000

    // 5. Exercise POST /api/employees/bulk-salary
    const bulkRes = await request.post('/api/employees/bulk-salary', {
      data: {
        employeeIds: [emp.id],
        monthYear: `10-2026-${testRunId.toString().slice(-4)}`,
        daysAbsent: 0,
        bonusAmount: 0,
        advanceDeduct: 0
      },
      headers: { 'X-Firm-Id': String(firmId) }
    });
    expect([200, 201]).toContain(bulkRes.status());
  });

  test('P2-SYS-01: Data Protection & Licensing Recovery REST Endpoints', async ({ request }) => {
    // 1. Exercise POST /api/dataprotection/backup-now
    const dpRes = await request.post('/api/dataprotection/backup-now');
    expect(dpRes.status()).toBe(200);
    const dpData = await dpRes.json();
    expect(dpData).toBeDefined();

    // 2. Exercise POST /api/licensing/recover-emi
    const emiRes = await request.post('/api/licensing/recover-emi');
    expect(emiRes.status()).toBe(200);
    const emiData = await emiRes.json();
    expect(emiData.machineId).toBeDefined();

    // 3. Exercise POST /api/licensing/activate with payload
    const actRes = await request.post('/api/licensing/activate', {
      data: {
        licenseId: `LIC-TEST-${testRunId}`,
        customerName: 'Trial Enterprise',
        product: 'RupeeCRM'
      }
    });
    expect(actRes.status()).toBe(200);
    const actData = await actRes.json();
    expect(actData.result).toBeDefined();
  });

  test('P2-PLN-01: Planner Kanban Task Creation, Column State & Persistence', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Planner');

    const boardTab = page.locator('button:has-text("Tasks & Board")').first();
    await expect(boardTab).toBeVisible({ timeout: 10000 });
    await boardTab.click();
    await page.waitForTimeout(400);

    // Create new Task
    const addTaskBtn = page.locator('button:has-text("+ Task")').first();
    await expect(addTaskBtn).toBeVisible({ timeout: 10000 });
    await addTaskBtn.click();
    await page.waitForTimeout(300);

    const taskModal = page.locator('.modal-overlay').first();
    await expect(taskModal).toBeVisible({ timeout: 8000 });

    const titleInput = taskModal.locator('input.input, input[placeholder*="Title" i]').first();
    await titleInput.fill(testTaskTitle);

    const saveTaskBtn = taskModal.locator('.modal-actions button:has-text("Create"), .modal-actions button:has-text("Save")').first();
    await saveTaskBtn.click();
    await page.waitForTimeout(600);

    // Search for task
    const searchInput = page.locator('input[placeholder*="Search tasks" i]').first();
    if (await searchInput.isVisible()) {
      await searchInput.fill(testTaskTitle);
      await page.waitForTimeout(400);
    }

    // Verify task card exists
    const taskCard = page.locator(`:visible:text("${testTaskTitle}")`).first();
    await expect(taskCard).toBeVisible({ timeout: 10000 });

    await errorGate.assertZeroErrors(page, 'Planner Kanban Task Creation');
  });

  test('P2-PLN-02: Planner Notes & Memos Lifecycle and Persistence', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Planner');

    const boardTab = page.locator('button:has-text("Tasks & Board")').first();
    await boardTab.click();
    await page.waitForTimeout(400);

    const addNoteBtn = page.locator('button:has-text("+ Note")').first();
    await expect(addNoteBtn).toBeVisible({ timeout: 10000 });
    await addNoteBtn.click();
    await page.waitForTimeout(300);

    const noteModal = page.locator('.modal-overlay').first();
    await expect(noteModal).toBeVisible({ timeout: 8000 });

    const noteTitle = noteModal.locator('input.input, input[placeholder*="Title" i]').first();
    await noteTitle.fill(testNoteTitle);

    const noteBody = noteModal.locator('textarea').first();
    await noteBody.fill('Confidential quarterly audit notes for review.');

    const saveBtn = noteModal.locator('.modal-actions button:has-text("Create"), .modal-actions button:has-text("Save")').first();
    await saveBtn.click();
    await page.waitForTimeout(600);

    // Search for note
    const searchInput = page.locator('input[placeholder*="Search tasks" i]').first();
    if (await searchInput.isVisible()) {
      await searchInput.fill(testNoteTitle);
      await page.waitForTimeout(400);
    }

    // Verify note exists
    const noteCard = page.locator(`:visible:text("${testNoteTitle}")`).first();
    await expect(noteCard).toBeVisible({ timeout: 10000 });

    await errorGate.assertZeroErrors(page, 'Planner Notes Creation & Persistence');
  });
});
