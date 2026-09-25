import { test, expect } from '../fixtures/base-fixture';

test.describe('P2: Quick Actions, Expenses, Savings & Planner Goals', () => {
  const testRunId = Date.now();

  test('P2-QA-01: Expense Recording & Form Verification', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Planner');

    // Switch to Expense Outflow tab
    const expenseTab = page.locator('button:has-text("Expense Outflow")').first();
    await expect(expenseTab).toBeVisible({ timeout: 10000 });
    await expenseTab.click();
    await page.waitForTimeout(400);

    // Open Record Expense Modal
    const recordBtn = page.locator('button:has-text("+ Record Expense")').first();
    await expect(recordBtn).toBeVisible({ timeout: 5000 });
    await recordBtn.click();
    await page.waitForTimeout(400);

    const modal = page.locator('.modal-overlay').first();
    await expect(modal).toBeVisible({ timeout: 5000 });

    // Fill Title and Amount
    const titleInput = modal.locator('input[placeholder*="Office Stationery" i], input.input').first();
    await expect(titleInput).toBeVisible();
    await titleInput.fill(`Fuel Expense ${testRunId}`);

    const amountInput = modal.locator('input[type="number"]').first();
    await expect(amountInput).toBeVisible();
    await amountInput.fill('500');

    // Save Expense (Create button)
    const saveBtn = modal.locator('button:has-text("Create"), button:has-text("Save")').first();
    await expect(saveBtn).toBeVisible();
    await saveBtn.click();
    await page.waitForTimeout(600);

    // Verify expense appears in list
    const expenseRow = page.locator(`:visible:text("Fuel Expense ${testRunId}")`).first();
    await expect(expenseRow).toBeVisible({ timeout: 10000 });

    await errorGate.assertZeroErrors(page, 'Expense Recording & Pipeline');
  });

  test('P2-QA-02: Tasks, Reminders, and Notes Creation on Board', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Planner');

    // Switch to Tasks & Board tab
    const boardTab = page.locator('button:has-text("Tasks & Board")').first();
    await expect(boardTab).toBeVisible({ timeout: 10000 });
    await boardTab.click();
    await page.waitForTimeout(400);

    // Create a new task
    const taskBtn = page.locator('button:has-text("+ Task")').first();
    await expect(taskBtn).toBeVisible({ timeout: 5000 });
    await taskBtn.click();
    await page.waitForTimeout(400);

    const modal = page.locator('.modal-overlay').first();
    await expect(modal).toBeVisible({ timeout: 5000 });

    const titleInput = modal.locator('input[placeholder*="Title" i], input.input').first();
    await expect(titleInput).toBeVisible();
    const taskTitle = `Task_QA_${testRunId}`;
    await titleInput.fill(taskTitle);

    const saveBtn = modal.locator('button:has-text("Create"), button:has-text("Save")').first();
    await expect(saveBtn).toBeVisible();
    await saveBtn.click();
    await page.waitForTimeout(600);

    // Filter by search input to make task easily visible
    const searchInput = page.locator('input[placeholder*="Search tasks" i]').first();
    if (await searchInput.isVisible()) {
      await searchInput.fill(taskTitle);
      await page.waitForTimeout(400);
    }

    // Verify task is visible on board
    const taskCard = page.locator(`:visible:text("${taskTitle}")`).first();
    await expect(taskCard).toBeVisible({ timeout: 10000 });

    await errorGate.assertZeroErrors(page, 'Tasks & Board Creation');
  });

  test('P2-GOAL-01: Planner Goals & Habits Pipeline Verification', async ({ page, app, errorGate }) => {
    await app.gotoApp();
    await app.navigateTo('Planner');

    // Switch to Goals & Habits tab
    const goalsTab = page.locator('button:has-text("Goals & Habits")').first();
    await expect(goalsTab).toBeVisible({ timeout: 10000 });
    await goalsTab.click();
    await page.waitForTimeout(400);

    // Set Goal / Habit
    const setGoalBtn = page.locator('button:has-text("+ Set Goal / Habit")').first();
    await expect(setGoalBtn).toBeVisible({ timeout: 5000 });
    await setGoalBtn.click();
    await page.waitForTimeout(400);

    const modal = page.locator('.modal-overlay').first();
    await expect(modal).toBeVisible({ timeout: 5000 });

    // Step 1: Choose Goal Type (e.g. Financial Goal)
    const goalOption = modal.locator('div:has-text("Financial Goal")').last();
    await expect(goalOption).toBeVisible({ timeout: 5000 });
    await goalOption.click();
    await page.waitForTimeout(400);

    // Step 2: Fill Details
    const titleInput = modal.locator('input[placeholder*="Title" i], input.input').first();
    await expect(titleInput).toBeVisible({ timeout: 5000 });
    const goalTitle = `Goal_Revenue_${testRunId}`;
    await titleInput.fill(goalTitle);

    // Click Preview & Review button
    const reviewBtn = modal.locator('button:has-text("Preview & Review")').first();
    await expect(reviewBtn).toBeVisible({ timeout: 5000 });
    await reviewBtn.click();
    await page.waitForTimeout(400);

    // Step 3: Final Confirm / Create
    const saveBtn = modal.locator('button:has-text("Create"), button:has-text("Set Goal"), button:has-text("Save"), button:has-text("Confirm")').first();
    await expect(saveBtn).toBeVisible();
    await saveBtn.click();
    await page.waitForTimeout(600);

    // Verify goal is visible in list
    const goalCard = page.locator(`:visible:text("${goalTitle}")`).first();
    await expect(goalCard).toBeVisible({ timeout: 10000 });

    await errorGate.assertZeroErrors(page, 'Goals & Habits Pipeline');
  });
});
