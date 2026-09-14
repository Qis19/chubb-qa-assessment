import { test, expect } from '@playwright/test';

const CLAIMS_SERVICE_URL = 'http://localhost:8080';
const BFF_SERVICE_URL = 'http://localhost:8090';

test.describe('Service health checks', () => {

  test('claims service is UP', async ({ request }) => {
    const response = await request.get(`${CLAIMS_SERVICE_URL}/actuator/health`);
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.status).toBe('UP');
  });

  test('bff service is UP', async ({ request }) => {
    const response = await request.get(`${BFF_SERVICE_URL}/actuator/health`);
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.status).toBe('UP');
  });

  test('UI homepage loads', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveTitle(/.+/);
  });
});