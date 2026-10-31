import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import ExpenseListPage from '../src/pages/ExpenseListPage';
import StatisticsPage from '../src/pages/StatisticsPage';
import * as familyApi from '../src/services/familyApi';
import * as expenseApi from '../src/services/expenseApi';
import * as statisticsApi from '../src/services/statisticsApi';

vi.mock('../src/services/familyApi');
vi.mock('../src/services/expenseApi');
vi.mock('../src/services/statisticsApi');

function show(page: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>{page}</MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('讀取失敗與重試', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(familyApi.getMyFamily).mockResolvedValue({
      familyGroupId: 10,
      familyMemberId: 1,
      name: '測試家庭',
      role: 'MEMBER',
      status: 'ACTIVE',
      inviteCode: 'TEST',
    });
    vi.mocked(familyApi.getMembers).mockResolvedValue([]);
    vi.mocked(expenseApi.listAccounts).mockResolvedValue([]);
  });

  it('家庭資料失敗不會顯示尚未加入家庭', async () => {
    vi.mocked(familyApi.getMyFamily).mockRejectedValue(new Error('offline'));
    show(<ExpenseListPage />);
    expect(await screen.findByRole('alert')).toHaveTextContent('無法載入家庭資料');
    expect(screen.queryByText('您尚未加入任何家庭群組')).not.toBeInTheDocument();
  });

  it('支出讀取失敗後可以重試並恢復分頁清單', async () => {
    vi.mocked(expenseApi.listExpenses)
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValue({
        items: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      });
    show(<ExpenseListPage />);
    expect(await screen.findByRole('alert')).toHaveTextContent('無法載入支出紀錄');
    fireEvent.click(screen.getByRole('button', { name: '重新載入' }));
    expect(await screen.findByRole('navigation', { name: '分頁' })).toBeInTheDocument();
  });

  it('統計讀取失敗不會顯示誤導性的總計零', async () => {
    vi.mocked(statisticsApi.getMonthlySummary).mockRejectedValue(new Error('offline'));
    show(<StatisticsPage />);
    expect(await screen.findByRole('alert')).toHaveTextContent('無法載入月結統計');
    expect(screen.queryByText('總計：0')).not.toBeInTheDocument();
  });
});
