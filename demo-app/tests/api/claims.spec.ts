import { test, expect } from '@playwright/test';

const BFF_SERVICE_URL = 'http://localhost:8090';

test.describe('Claims API - unauthenticated access', () => {

  test('GET /api/claims without auth returns 401', async ({ request }) => {
    const response = await request.get(`${BFF_SERVICE_URL}/api/claims`);
    expect([401, 403]).toContain(response.status());
  });

  test('POST /api/claims without auth returns 401', async ({ request }) => {
    const response = await request.post(`${BFF_SERVICE_URL}/api/claims`, {
      data: {
        incidentDate: '2026-01-01',
        incidentLocation: 'Kuala Lumpur',
        description: 'Test claim description for API test',
        claimAmount: 1000,
      },
    });
    expect([401, 403]).toContain(response.status());
  });

  test('GET /api/claims/{id} without auth returns 401', async ({ request }) => {
    const response = await request.get(
      `${BFF_SERVICE_URL}/api/claims/00000000-0000-0000-0000-000000000000`
    );
    expect([401, 403]).toContain(response.status());
  });
});