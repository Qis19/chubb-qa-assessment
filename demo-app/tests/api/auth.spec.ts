import { test, expect } from '@playwright/test';

const BFF_SERVICE_URL = 'http://localhost:8090';

test.describe('Authentication API', () => {

  test('valid credentials return user info', async ({ request }) => {
  const response = await request.post(`${BFF_SERVICE_URL}/api/auth/login`, {
    data: {
      email: 'claimant@demo.com',
      password: 'Claimant123!',
    },
  });

  expect(response.status()).toBe(200);

  const body = await response.json();
  expect(body.email).toBe('claimant@demo.com');
  expect(body.role).toBe('CLAIMANT');
  expect(body.userId).toBeTruthy();
});

  test('invalid credentials are rejected', async ({ request }) => {
    const response = await request.post(`${BFF_SERVICE_URL}/api/auth/login`, {
      data: {
        email: 'wrong@demo.com',
        password: 'wrongpassword',
      },
    });

    expect([400, 401, 403]).toContain(response.status());
  });

  test('unauthenticated request to protected endpoint is rejected', async ({ request }) => {
    const response = await request.get(`${BFF_SERVICE_URL}/api/claims`);

    expect([401, 403]).toContain(response.status());
  });
});