import { test, expect } from '@playwright/test';

test.describe('Login page - real browser flow', () => {

  test('homepage loads and shows login form', async ({ page }) => {
    await page.goto('/');

    // The page should load and have a title
    await expect(page).toHaveTitle(/.+/);

    // Should have some form of login input (email or username)
    await expect(page.locator('input[type="email"], input[name="email"], input[name="username"]')).toBeVisible({ timeout: 10000 });
  });

  test('login with invalid credentials shows error or stays on login', async ({ page }) => {
    await page.goto('/');

    // Fill in wrong credentials
    const emailInput = page.locator('input[type="email"], input[name="email"], input[name="username"]').first();
    const passwordInput = page.locator('input[type="password"]').first();

    await emailInput.fill('wrong@demo.com');
    await passwordInput.fill('wrongpassword');

    // Find and click the submit button
    const submitButton = page.locator('button[type="submit"], button:has-text("Log in"), button:has-text("Login"), button:has-text("Sign in")').first();
    await submitButton.click();

    // Wait a moment for response
    await page.waitForTimeout(2000);

    // Should NOT be on a claims/dashboard page
    const url = page.url();
    expect(url).not.toMatch(/\/claims|\/dashboard/);
  });
});