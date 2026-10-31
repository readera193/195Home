import apiClient from './apiClient';

export interface AccountSummaryView {
  paymentAccountId: number;
  paymentAccountName: string | null;
  /** 收入合計與支出合計皆為正數，netAmount = incomeAmount − expenseAmount。 */
  incomeAmount: number;
  expenseAmount: number;
  netAmount: number;
}

export interface MonthlySummaryResponse {
  familyGroupId: number;
  month: string;
  accounts: AccountSummaryView[];
  totalIncomeAmount: number;
  totalExpenseAmount: number;
  totalNetAmount: number;
}

export function getMonthlySummary(
  familyGroupId: number,
  month: string,
): Promise<MonthlySummaryResponse> {
  return apiClient
    .get('/statistics/monthly', { params: { familyGroupId, month } })
    .then((res) => res.data);
}
