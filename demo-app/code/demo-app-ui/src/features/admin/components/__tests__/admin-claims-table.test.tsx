import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { AdminClaimsTable } from '../admin-claims-table';
import type { ClaimResponse } from '@/src/lib/api/bff-client';

// Mock the toast library (used by "View Details" button)
vi.mock('react-hot-toast', () => ({
  default: vi.fn(),
}));

// Mock child components to keep tests focused
vi.mock('../claim-status-select', () => ({
  ClaimStatusSelect: () => <div data-testid="status-select" />,
}));

const mockClaims: ClaimResponse[] = [
  {
    claimId: '12345678-aaaa-bbbb-cccc-ddddeeeeffff',
    userId: '87654321-xxxx-yyyy-zzzz-aaaabbbbcccc',
    incidentDate: new Date('2026-01-15'),        // ← Date object
    claimAmount: 5000,
    status: 'SUBMITTED',
    description: 'Test claim 1',
    incidentLocation: 'Kuala Lumpur',
    createdAt: '2026-01-15T10:00:00Z',
    updatedAt: '2026-01-15T10:00:00Z',
  } as unknown as ClaimResponse,
  {
    claimId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
    userId: 'bbbbbbbb-cccc-dddd-eeee-ffffffffffff',
    incidentDate: new Date('2026-02-20'),        // ← Date object
    claimAmount: 12500,
    status: 'APPROVED',
    description: 'Test claim 2',
    incidentLocation: 'Penang',
    createdAt: '2026-02-20T10:00:00Z',
    updatedAt: '2026-02-20T10:00:00Z',
  } as unknown as ClaimResponse,
];

describe('AdminClaimsTable', () => {

  it('renders the table with all expected column headers', () => {
    render(<AdminClaimsTable claims={mockClaims} />);

    expect(screen.getByText('Claim ID')).toBeInTheDocument();
    expect(screen.getByText('User ID')).toBeInTheDocument();
    expect(screen.getByText('Incident Date')).toBeInTheDocument();
    expect(screen.getByText('Amount')).toBeInTheDocument();
    expect(screen.getByText('Status')).toBeInTheDocument();
    expect(screen.getByText('Actions')).toBeInTheDocument();
  });

  it('renders one row per claim with truncated IDs', () => {
    render(<AdminClaimsTable claims={mockClaims} />);

    // Truncated claim IDs (first 8 chars)
    expect(screen.getByText('12345678')).toBeInTheDocument();
    expect(screen.getByText('aaaaaaaa')).toBeInTheDocument();

    // Truncated user IDs
    expect(screen.getByText('87654321')).toBeInTheDocument();
    expect(screen.getByText('bbbbbbbb')).toBeInTheDocument();
  });

  it('renders empty table body when no claims provided', () => {
    render(<AdminClaimsTable claims={[]} />);

    // Headers should still render
    expect(screen.getByText('Claim ID')).toBeInTheDocument();

    // But no data rows
    const rows = screen.queryAllByRole('row');
    // 1 header row only
    expect(rows.length).toBe(1);
  });
});