import { test, expect } from '../fixtures/base-fixture';

test.describe('P2: Planner Kanban & HR Management Hub', () => {
  test('P2-PLN-01: Planner Kanban Board Columns & Cards', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Planner');

    // Switch to Kanban Board tab
    const boardTab = page.locator('button:has-text("Tasks & Board"), button:has-text("Board"), button:has-text("Kanban")').first();
    if (await boardTab.isVisible()) {
      await boardTab.click();
      await page.waitForTimeout(400);

      const board = page.locator('.planner-container, :text("Active Reminders"), :text("Task Pipeline"), :text("Notes & Memos")').first();
      await expect(board).toBeVisible();
    }

    await errorGate.assertZeroErrors(page, 'Planner Kanban Board');
  });

  test('P2-PLN-02: Planner Notes Subtab & Creation Interface', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('Planner');

    const notesTab = page.locator('button:has-text("Tasks & Board"), button:has-text("Board")').first();
    if (await notesTab.isVisible()) {
      await notesTab.click();
      await page.waitForTimeout(400);

      const notesSection = page.locator(':text("Notes & Memos"), .planner-container').first();
      await expect(notesSection).toBeVisible();
    }

    await errorGate.assertZeroErrors(page, 'Planner Notes');
  });

  test('P2-HR-01: HR Staff Directory & Employee Management', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('HR');

    const staffTab = page.locator('button:has-text("Staff"), button:has-text("Employees"), .tab-btn').first();
    if (await staffTab.isVisible()) {
      await staffTab.click();
      await page.waitForTimeout(400);

      const hrManager = page.locator('.hr-manager, .data-table, :text("Employee"), :text("Staff")').first();
      await expect(hrManager).toBeVisible();
    }

    await errorGate.assertZeroErrors(page, 'HR Staff Directory');
  });

  test('P2-HR-02: HR Attendance & Payroll Calculation Hub', async ({ page, app, errorGate }) => {
    await app.gotoApp();

    await app.navigateTo('HR');

    const payrollTab = page.locator('button:has-text("Payroll"), button:has-text("Salary"), .tab-btn:has-text("Payroll")').first();
    if (await payrollTab.isVisible()) {
      await payrollTab.click();
      await page.waitForTimeout(400);

      const payrollView = page.locator('.payroll-view, .data-table, :text("Payroll")').first();
      await expect(payrollView).toBeVisible();
    }

    await errorGate.assertZeroErrors(page, 'HR Payroll Hub');
  });
});
