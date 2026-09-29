import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getMyFamily } from '../services/familyApi';
import { getMonthlySummary } from '../services/statisticsApi';

function currentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
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
    return <p>您尚未加入任何家庭群組</p>;
  }

  return (
    <div>
      <h1>月結統計</h1>
      <label htmlFor="month">月份</label>
      <input id="month" type="month" value={month} onChange={(e) => setMonth(e.target.value)} />

      {isLoading ? (
        <p>載入中...</p>
      ) : (
        <>
          <table>
            <thead>
              <tr>
                <th>支付帳戶</th>
                <th>淨額（收入－支出）</th>
              </tr>
            </thead>
            <tbody>
              {summary?.accounts.map((account) => (
                <tr key={account.paymentAccountId}>
                  <td>{account.paymentAccountName}</td>
                  <td>{account.netAmount}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p>
            <strong>總計：{summary?.totalNetAmount ?? 0}</strong>
          </p>
        </>
      )}
    </div>
  );
}
