import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ExpenseFormPage from '../src/pages/ExpenseFormPage';
import * as familyApi from '../src/services/familyApi';
import * as expenseApi from '../src/services/expenseApi';

vi.mock('../src/services/familyApi');
vi.mock('../src/services/expenseApi');

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ExpenseFormPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('ExpenseFormPage', () => {
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
    vi.mocked(expenseApi.listAccounts).mockResolvedValue([
      { accountId: 100, familyMemberId: 1, name: '現金', status: 'ACTIVE' },
    ]);
  });

  it('拒絕含小數點的金額，並且不呼叫 createExpense（FR-016）', async () => {
    renderPage();

    await waitFor(() => expect(screen.getByLabelText(/支付帳戶/)).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText(/金額/), { target: { value: '100.5' } });
    fireEvent.change(screen.getByLabelText('備註'), { target: { value: '午餐' } });
    fireEvent.change(screen.getByLabelText(/支付帳戶/), { target: { value: '100' } });
    fireEvent.click(screen.getByRole('button', { name: '新增' }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('金額必須為整數');
    });
    expect(expenseApi.createExpense).not.toHaveBeenCalled();
  });

  it('接受負整數金額並呼叫 createExpense（FR-016：負數代表流出）', async () => {
    vi.mocked(expenseApi.createExpense).mockResolvedValue({
      expenseId: 1,
      amount: -350,
      note: '午餐',
      occurredAt: '2026-09-20T12:00:00',
      paymentAccountId: 100,
      paymentAccountName: '現金',
      authorMemberId: 1,
      locked: false,
    });

    renderPage();

    await waitFor(() => expect(screen.getByLabelText(/支付帳戶/)).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText(/金額/), { target: { value: '-350' } });
    fireEvent.change(screen.getByLabelText('備註'), { target: { value: '午餐' } });
    fireEvent.change(screen.getByLabelText(/支付帳戶/), { target: { value: '100' } });
    fireEvent.click(screen.getByRole('button', { name: '新增' }));

    await waitFor(() => {
      expect(expenseApi.createExpense).toHaveBeenCalledWith(
        expect.objectContaining({ amount: -350, note: '午餐', paymentAccountId: 100 }),
      );
    });
  });
});
