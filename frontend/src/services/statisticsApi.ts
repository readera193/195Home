import apiClient from './apiClient';

export interface AccountSummaryView {
  paymentAccountId: number;
  paymentAccountName: string | null;
  netAmount: number;
}

export interface MonthlySummaryResponse {
  familyGroupId: number;
  month: string;
  accounts: AccountSummaryView[];
  totalNetAmount: number;
}

export function getMonthlySummary(familyGroupId: number, month: string): Promise<MonthlySummaryResponse> {
  return apiClient.get('/statistics/monthly', { params: { familyGroupId, month } }).then((res) => res.data);
}
