import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getMyFamily } from '../services/familyApi';
import { getMonthlySummary } from '../services/statisticsApi';

function currentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

function amountClass(amount: number): string {
  return amount > 0 ? 'positive' : amount < 0 ? 'negative' : '';
}

export default function StatisticsPage() {
  const { data: myFamily } = useQuery({ queryKey: ['myFamily'], queryFn: getMyFamily });
  const familyGroupId = myFamily?.familyGroupId ?? undefined;

  const [month, setMonth] = useState(currentYearMonth());

  const { data: summary, isLoading } = useQuery({
    queryKey: ['statistics', familyGroupId, month],
    queryFn: () => getMonthlySummary(familyGroupId!, month),
    enabled: !!familyGroupId,
  });

  if (!familyGroupId) {
    return (
      <div className="card empty">
        <p>您尚未加入任何家庭群組</p>
        <Link to="/family">前往建立或加入群組</Link>
      </div>
    );
  }

  const total = summary?.totalNetAmount ?? 0;

  return (
    <div className="container-narrow">
      <div className="page-header">
        <h1>月結統計</h1>
      </div>

      <div className="card">
        <div className="field" style={{ marginBottom: 0, maxWidth: 240 }}>
          <label htmlFor="month">月份</label>
          <input id="month" type="month" value={month} onChange={(e) => setMonth(e.target.value)} />
        </div>
      </div>

      {isLoading ? (
        <p className="empty">載入中...</p>
      ) : (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>支付帳戶</th>
                  <th className="num">淨額（收入－支出）</th>
                </tr>
              </thead>
              <tbody>
                {summary?.accounts.map((account) => (
                  <tr key={account.paymentAccountId}>
                    <td>{account.paymentAccountName}</td>
                    <td className={`num ${amountClass(account.netAmount)}`}>{account.netAmount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="stat-total">
            <span>本月</span>
            <strong className={amountClass(total)}>總計：{total}</strong>
          </div>
        </>
      )}
    </div>
  );
}
