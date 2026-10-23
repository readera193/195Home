import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AxiosError, AxiosHeaders } from 'axios';
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

  it('只有一頁時仍顯示分頁控制，且上一頁／下一頁皆停用', async () => {
    vi.mocked(expenseApi.listExpenses).mockResolvedValue({
      items: [expense(1, '午餐')],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('午餐')).toBeInTheDocument());
    expect(screen.getByRole('navigation', { name: '分頁' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '上一頁' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '下一頁' })).toBeDisabled();
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

describe('ExpenseListPage 新增支出表單', () => {
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
    vi.mocked(expenseApi.listAccounts).mockResolvedValue([
      { accountId: 100, familyMemberId: 1, name: '現金', status: 'ACTIVE' },
    ]);
    vi.mocked(expenseApi.listExpenses).mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
  });

  async function fillAndSubmit(amount: string) {
    fireEvent.click(await screen.findByRole('button', { name: '新增支出' }));
    await waitFor(() => expect(within(screen.getByLabelText('支付帳戶')).getByRole('option', { name: '現金' })).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText(/^金額/), { target: { value: amount } });
    fireEvent.change(screen.getByLabelText('說明', { selector: 'input' }), { target: { value: '午餐' } });
    fireEvent.change(screen.getByLabelText('支付帳戶'), { target: { value: '100' } });
    fireEvent.click(screen.getByRole('button', { name: '新增' }));
  }

  it('拒絕含小數點的金額，並且不呼叫 createExpense（FR-016）', async () => {
    renderPage();

    await fillAndSubmit('100.5');

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('金額必須為整數');
    });
    expect(expenseApi.createExpense).not.toHaveBeenCalled();
  });

  it('接受負整數金額並呼叫 createExpense（FR-016：負數代表流出）', async () => {
    vi.mocked(expenseApi.createExpense).mockResolvedValue(expense(1, '午餐'));

    renderPage();

    await fillAndSubmit('-350');

    await waitFor(() => {
      expect(expenseApi.createExpense).toHaveBeenCalledWith(
        expect.objectContaining({ amount: -350, note: '午餐', paymentAccountId: 100 }),
      );
    });
  });
});

describe('ExpenseListPage 日期篩選', () => {
  it('預設為本月第一天到最後一天，變更後以新的起訖日期重新查詢', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(2026, 1, 10) });
    try {
      await runDateFilterTest();
    } finally {
      vi.useRealTimers();
    }
  });

  async function runDateFilterTest() {
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
    vi.mocked(expenseApi.listExpenses).mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });

    renderPage();
    await waitFor(() =>
      expect(expenseApi.listExpenses).toHaveBeenLastCalledWith(
        expect.objectContaining({ startDate: '2026-02-01', endDate: '2026-02-28' }),
      ),
    );

    fireEvent.change(screen.getByLabelText('起始日期'), { target: { value: '2026-02-05' } });
    fireEvent.change(screen.getByLabelText('結束日期'), { target: { value: '2026-02-20' } });

    await waitFor(() =>
      expect(expenseApi.listExpenses).toHaveBeenLastCalledWith(
        expect.objectContaining({ startDate: '2026-02-05', endDate: '2026-02-20', page: 0 }),
      ),
    );
  }
});

describe('ExpenseListPage 編輯／刪除', () => {
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
    vi.mocked(expenseApi.listAccounts).mockResolvedValue([
      { accountId: 100, familyMemberId: 1, name: '現金', status: 'ACTIVE' },
    ]);
    vi.mocked(expenseApi.listExpenses).mockResolvedValue({
      items: [expense(1, '午餐')],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
  });

  it('編輯：先取得鎖，儲存時呼叫 updateExpense', async () => {
    vi.mocked(expenseApi.lockExpense).mockResolvedValue({ expenseId: 1 });
    vi.mocked(expenseApi.updateExpense).mockResolvedValue(expense(1, '晚餐'));

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: '編輯 午餐' }));

    await waitFor(() => expect(expenseApi.lockExpense).toHaveBeenCalledWith(1, 10));
    fireEvent.change(await screen.findByLabelText('編輯說明'), { target: { value: '晚餐' } });
    fireEvent.click(screen.getByRole('button', { name: '儲存' }));

    await waitFor(() =>
      expect(expenseApi.updateExpense).toHaveBeenCalledWith(
        1,
        10,
        expect.objectContaining({ note: '晚餐', amount: -100, paymentAccountId: 100 }),
      ),
    );
  });

  it('紀錄被他人編輯中（RECORD_LOCKED）時顯示提示且不開啟編輯表單', async () => {
    const config = { headers: new AxiosHeaders() };
    vi.mocked(expenseApi.lockExpense).mockRejectedValue(
      new AxiosError('Conflict', 'ERR_BAD_REQUEST', config, null, {
        data: { status: 409, title: 'Conflict', detail: '此紀錄目前正被編輯中，請稍後再試', code: 'RECORD_LOCKED' },
        status: 409,
        statusText: '',
        headers: {},
        config,
      }),
    );

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: '編輯 午餐' }));

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('此紀錄目前正被編輯中'));
    expect(screen.queryByRole('form', { name: '編輯支出' })).not.toBeInTheDocument();
  });

  it('取消編輯會釋放鎖', async () => {
    vi.mocked(expenseApi.lockExpense).mockResolvedValue({ expenseId: 1 });
    vi.mocked(expenseApi.unlockExpense).mockResolvedValue(undefined);

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: '編輯 午餐' }));
    fireEvent.click(await screen.findByRole('button', { name: '取消' }));

    await waitFor(() => expect(expenseApi.unlockExpense).toHaveBeenCalledWith(1));
  });

  it('刪除：確認後呼叫 deleteExpense；取消確認則釋放鎖', async () => {
    vi.mocked(expenseApi.lockExpense).mockResolvedValue({ expenseId: 1 });
    vi.mocked(expenseApi.deleteExpense).mockResolvedValue(undefined);
    vi.mocked(expenseApi.unlockExpense).mockResolvedValue(undefined);

    renderPage();
    const confirm = vi.spyOn(window, 'confirm');

    confirm.mockReturnValueOnce(false);
    fireEvent.click(await screen.findByRole('button', { name: '刪除 午餐' }));
    await waitFor(() => expect(expenseApi.unlockExpense).toHaveBeenCalledWith(1));
    expect(expenseApi.deleteExpense).not.toHaveBeenCalled();

    confirm.mockReturnValueOnce(true);
    fireEvent.click(screen.getByRole('button', { name: '刪除 午餐' }));
    await waitFor(() => expect(expenseApi.deleteExpense).toHaveBeenCalledWith(1, 10));
  });
});
