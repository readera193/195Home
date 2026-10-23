import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PaymentAccountsPage from '../src/pages/PaymentAccountsPage';
import * as familyApi from '../src/services/familyApi';
import * as expenseApi from '../src/services/expenseApi';

vi.mock('../src/services/familyApi');
vi.mock('../src/services/expenseApi');
vi.mock('../src/utils/notify', () => ({
  notifyError: vi.fn(() => Promise.resolve()),
  confirmDialog: vi.fn(() => Promise.resolve(true)),
  promptText: vi.fn(() => Promise.resolve(undefined)),
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <PaymentAccountsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function mockFamily(role: 'ADMIN' | 'MEMBER') {
  vi.mocked(familyApi.getMyFamily).mockResolvedValue({
    familyGroupId: 10,
    name: '王家',
    status: 'ACTIVE',
    role,
    inviteCode: 'CODE1',
    familyMemberId: 1,
  });
}

describe('PaymentAccountsPage 啟用／停用／刪除', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(expenseApi.listAccounts).mockResolvedValue([
      { accountId: 1, familyMemberId: 1, name: '現金', status: 'ACTIVE', deletable: false },
      { accountId: 2, familyMemberId: 1, name: '舊信用卡', status: 'DISABLED', deletable: true },
      { accountId: 3, familyMemberId: 1, name: '備用錢包', status: 'ACTIVE', deletable: true },
    ]);
  });

  it('預設隱藏已停用帳戶；打開「顯示已停用帳戶」後才列出', async () => {
    mockFamily('ADMIN');

    renderPage();

    await waitFor(() => expect(screen.getByText('現金')).toBeInTheDocument());
    expect(screen.queryByText('舊信用卡')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('checkbox', { name: '顯示已停用帳戶' }));

    expect(screen.getByText('舊信用卡')).toBeInTheDocument();
  });

  it('「停用」與「啟用」互斥；「刪除」只出現在 deletable 的帳戶', async () => {
    mockFamily('ADMIN');

    renderPage();

    await waitFor(() => expect(screen.getByText('現金')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('checkbox', { name: '顯示已停用帳戶' }));

    // 兩個啟用中帳戶各一個「停用」、一個已停用帳戶一個「啟用」
    expect(screen.getAllByRole('button', { name: '停用' })).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: '啟用' })).toHaveLength(1);
    // 只有「舊信用卡」「備用錢包」可刪除
    expect(screen.getAllByRole('button', { name: '刪除' })).toHaveLength(2);
  });

  it('管理者點已停用帳戶的「啟用」會呼叫 enableAccount', async () => {
    mockFamily('ADMIN');
    vi.mocked(expenseApi.enableAccount).mockResolvedValue({
      accountId: 2,
      familyMemberId: 1,
      name: '舊信用卡',
      status: 'ACTIVE',
      deletable: true,
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('現金')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('checkbox', { name: '顯示已停用帳戶' }));
    fireEvent.click(screen.getByRole('button', { name: '啟用' }));

    await waitFor(() => expect(expenseApi.enableAccount).toHaveBeenCalledWith(2));
  });

  it('一般成員看不到任何管理按鈕', async () => {
    mockFamily('MEMBER');

    renderPage();

    await waitFor(() => expect(screen.getByText('現金')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('checkbox', { name: '顯示已停用帳戶' }));

    expect(screen.queryByRole('button', { name: '啟用' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '刪除' })).not.toBeInTheDocument();
  });
});
