import type { PaymentAccountResponse } from '../../services/expenseApi';
import type { MemberView } from '../../services/familyApi';

export interface ExpenseFilterValues {
  paymentAccountId: string;
  authorMemberId: string;
  startDate: string;
  endDate: string;
}

interface ExpenseFiltersProps {
  values: ExpenseFilterValues;
  accounts?: PaymentAccountResponse[];
  members?: MemberView[];
  /** 任一篩選條件改變時呼叫；呼叫端負責回到第一頁。 */
  onChange: (patch: Partial<ExpenseFilterValues>) => void;
}

export default function ExpenseFilters({ values, accounts, members, onChange }: ExpenseFiltersProps) {
  return (
    <div className="card filters">
      <div className="field">
        <label htmlFor="filterAccount">支付帳戶篩選</label>
        <select
          id="filterAccount"
          value={values.paymentAccountId}
          onChange={(e) => onChange({ paymentAccountId: e.target.value })}
        >
          <option value="">全部</option>
          {accounts?.map((account) => (
            <option key={account.accountId} value={account.accountId}>
              {account.name}
            </option>
          ))}
        </select>
      </div>

      <div className="field">
        <label htmlFor="filterMember">成員篩選</label>
        <select
          id="filterMember"
          value={values.authorMemberId}
          onChange={(e) => onChange({ authorMemberId: e.target.value })}
        >
          <option value="">全部</option>
          {members?.map((member) => (
            <option key={member.familyMemberId} value={member.familyMemberId}>
              {member.displayName}
              {member.status !== 'ACTIVE' ? '（已離開）' : ''}
            </option>
          ))}
        </select>
      </div>

      <div className="field">
        <label htmlFor="filterStartDate">起始日期</label>
        <input
          id="filterStartDate"
          type="date"
          value={values.startDate}
          max={values.endDate || undefined}
          onChange={(e) => onChange({ startDate: e.target.value })}
        />
      </div>

      <div className="field">
        <label htmlFor="filterEndDate">結束日期</label>
        <input
          id="filterEndDate"
          type="date"
          value={values.endDate}
          min={values.startDate || undefined}
          onChange={(e) => onChange({ endDate: e.target.value })}
        />
      </div>
    </div>
  );
}
