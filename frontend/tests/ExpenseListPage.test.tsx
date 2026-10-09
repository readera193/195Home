import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ExpenseListPage from '../src/pages/ExpenseListPage';
import * as familyApi from '../src/services/familyApi';
import * as expenseApi from '../src/services/expenseApi';

vi.mock('../src/services/familyApi');
vi.mock('../src/services/expenseApi');

function expense(id: number, note: string): expenseApi.ExpenseResponse {
  return {
    expenseId: id,
    amount: -100,
    note,
    occurredAt: '2026-09-20T12:00:00',
    paymentAccountId: 100,
    paymentAccountName: '現金',
    authorMemberId: 1,
    locked: false,
  };
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ExpenseListPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('ExpenseListPage 分頁', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(familyApi.getMyFamily).mockResolvedValue({
      familyGroupId: 10,
      name: '王家',
      status: 'ACTIVE',
      role: 'MEMBER',
      inviteCode: 'CODE1',
      familyMemberId: 1,
    });
    vi.mocked(familyApi.getMembers).mockResolvedValue([]);
    vi.mocked(expenseApi.listAccounts).mockResolvedValue([]);
  });

  it('只有一頁時不顯示分頁控制', async () => {
    vi.mocked(expenseApi.listExpenses).mockResolvedValue({
      items: [expense(1, '午餐')],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('午餐')).toBeInTheDocument());
    expect(screen.queryByRole('navigation', { name: '分頁' })).not.toBeInTheDocument();
  });

  it('按「下一頁」會以 page=1 重新查詢', async () => {
    vi.mocked(expenseApi.listExpenses).mockImplementation(async (params) => ({
      items: [expense(params.page === 1 ? 2 : 1, params.page === 1 ? '第二頁項目' : '第一頁項目')],
      page: params.page ?? 0,
      size: 20,
      totalElements: 25,
      totalPages: 2,
    }));

    renderPage();

    await waitFor(() => expect(screen.getByText('第一頁項目')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: '上一頁' })).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: '下一頁' }));

    await waitFor(() => expect(screen.getByText('第二頁項目')).toBeInTheDocument());
    expect(expenseApi.listExpenses).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 20 }));
    expect(screen.getByRole('button', { name: '下一頁' })).toBeDisabled();
  });
});
