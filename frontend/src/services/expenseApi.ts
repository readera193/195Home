import apiClient from './apiClient';

export interface PaymentAccountResponse {
  accountId: number;
  familyMemberId: number;
  name: string;
  status: 'ACTIVE' | 'DISABLED';
}

export interface ExpenseResponse {
  expenseId: number;
  amount: number;
  note: string;
  occurredAt: string;
  paymentAccountId: number;
  paymentAccountName: string | null;
  authorMemberId: number;
  locked: boolean;
}

export function createAccount(familyGroupId: number, name: string): Promise<PaymentAccountResponse> {
  return apiClient.post('/accounts', { familyGroupId, name }).then((res) => res.data);
}

export function listAccounts(
  familyGroupId: number,
  status: 'ACTIVE' | 'DISABLED' | 'ALL' = 'ALL',
): Promise<PaymentAccountResponse[]> {
  return apiClient.get('/accounts', { params: { familyGroupId, status } }).then((res) => res.data);
}

export function disableAccount(accountId: number): Promise<PaymentAccountResponse> {
  return apiClient.post(`/accounts/${accountId}/disable`).then((res) => res.data);
}

export function renameAccount(accountId: number, name: string): Promise<PaymentAccountResponse> {
  return apiClient.put(`/accounts/${accountId}`, { name }).then((res) => res.data);
}

export function deleteAccount(accountId: number): Promise<void> {
  return apiClient.delete(`/accounts/${accountId}`).then(() => undefined);
}

export interface CreateExpenseInput {
  familyGroupId: number;
  paymentAccountId: number;
  amount: number;
  note: string;
  occurredAt?: string;
}

export function createExpense(input: CreateExpenseInput): Promise<ExpenseResponse> {
  return apiClient.post('/expenses', input).then((res) => res.data);
}

export interface ListExpensesParams {
  familyGroupId: number;
  paymentAccountId?: number;
  authorMemberId?: number;
  month?: string;
}

export function listExpenses(params: ListExpensesParams): Promise<ExpenseResponse[]> {
  return apiClient.get('/expenses', { params }).then((res) => res.data);
}

export function lockExpense(expenseId: number, familyGroupId: number): Promise<{ expenseId: number }> {
  return apiClient.post(`/expenses/${expenseId}/lock`, null, { params: { familyGroupId } }).then((res) => res.data);
}

export function unlockExpense(expenseId: number): Promise<void> {
  return apiClient.post(`/expenses/${expenseId}/unlock`).then(() => undefined);
}

export function updateExpense(
  expenseId: number,
  familyGroupId: number,
  input: { paymentAccountId: number; amount: number; note: string; occurredAt?: string },
): Promise<ExpenseResponse> {
  return apiClient
    .put(`/expenses/${expenseId}`, input, { params: { familyGroupId } })
    .then((res) => res.data);
}

export function deleteExpense(expenseId: number, familyGroupId: number): Promise<void> {
  return apiClient.delete(`/expenses/${expenseId}`, { params: { familyGroupId } }).then(() => undefined);
}
