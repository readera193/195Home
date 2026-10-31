import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { getMyFamily } from '../services/familyApi';
import { getMonthlySummary } from '../services/statisticsApi';
import { amountClass, formatNumber } from '../utils/formatNumber';
import Icon from '../components/Icon';
import { SkeletonKpis, SkeletonLines } from '../components/Skeleton';

const DELETED_ACCOUNT = '（已刪除帳戶）';

interface Slice {
  id: number;
  name: string;
  amount: number;
  /** 圖表色序：依帳戶在列表中的位置固定，使同一帳戶在各張圖上顏色一致。 */
  colorIndex: number;
}

function currentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

function formatPct(ratio: number): string {
  return `${(ratio * 100).toFixed(ratio > 0 && ratio < 0.1 ? 1 : 0)}%`;
}

function ShareCard({ title, empty, slices }: { title: string; empty: string; slices: Slice[] }) {
  const items = slices.filter((s) => s.amount > 0).sort((a, b) => b.amount - a.amount);
  const total = items.reduce((sum, s) => sum + s.amount, 0);
  const max = Math.max(...items.map((s) => s.amount), 1);
  return (
    <section className="card share-card" aria-label={title}>
      <h2 className="share-title">{title}</h2>
      {total === 0 ? (
        <p className="empty">{empty}</p>
      ) : (
        <ul className="bars">
          {items.map((s) => (
            <li key={s.id}>
              <span className="bars-head">
                <span>{s.name}</span>
                <span className="bars-value">
                  {formatNumber(s.amount)}{' '}
                  <span className="muted">({formatPct(s.amount / total)})</span>
                </span>
              </span>
              <span className="bars-track" aria-hidden="true">
                <span
                  className="bars-fill"
                  style={{
                    width: `${(s.amount / max) * 100}%`,
                    background: `var(--chart-${(s.colorIndex % 6) + 1})`,
                  }}
                />
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

export default function HomePage() {
  const familyQuery = useQuery({ queryKey: ['myFamily'], queryFn: getMyFamily });
  const familyGroupId = familyQuery.data?.familyGroupId ?? undefined;

  const [month, setMonth] = useState(currentYearMonth());

  const {
    data: summary,
    isLoading,
    isError,
    refetch,
    isFetching,
  } = useQuery({
    queryKey: ['statistics', familyGroupId, month],
    queryFn: () => getMonthlySummary(familyGroupId!, month),
    enabled: !!familyGroupId && !!month,
    placeholderData: keepPreviousData,
  });

  if (familyQuery.isLoading)
    return (
      <div className="home-page page-wide">
        <SkeletonKpis />
        <SkeletonLines count={4} />
      </div>
    );
  if (familyQuery.isError)
    return (
      <div className="card" role="alert">
        <p>無法載入家庭資料，請稍後再試。</p>
        <button type="button" onClick={() => void familyQuery.refetch()}>
          重新載入
        </button>
      </div>
    );

  if (!familyGroupId) {
    return (
      <div className="card empty">
        <p>您尚未加入任何家庭群組</p>
        <Link to="/family">前往建立或加入群組</Link>
      </div>
    );
  }

  const accounts = summary?.accounts ?? [];
  const toSlices = (pick: (a: (typeof accounts)[number]) => number): Slice[] =>
    accounts.map((a, i) => ({
      id: a.paymentAccountId,
      name: a.paymentAccountName ?? DELETED_ACCOUNT,
      amount: pick(a),
      colorIndex: i,
    }));

  return (
    <div className="home-page page-wide">
      <div className="page-header page-header--row">
        <h1>月結統計</h1>
        <input
          aria-label="月份"
          type="month"
          value={month}
          onChange={(e) => setMonth(e.target.value)}
        />
      </div>

      {isLoading ? (
        <>
          <SkeletonKpis />
          <SkeletonLines count={4} />
        </>
      ) : isError || !summary ? (
        <div className="card" role="alert">
          <p>無法載入月結統計，請重新載入。</p>
          <button type="button" disabled={isFetching} onClick={() => void refetch()}>
            重新載入
          </button>
        </div>
      ) : (
        <>
          <div className="kpi-row">
            <div className="card kpi">
              <span className="kpi-label">
                <span className="kpi-icon kpi-icon--income">
                  <Icon name="arrowUp" size={14} />
                </span>
                收入
              </span>
              <strong className="positive">{formatNumber(summary.totalIncomeAmount)}</strong>
            </div>
            <div className="card kpi">
              <span className="kpi-label">
                <span className="kpi-icon kpi-icon--expense">
                  <Icon name="arrowDown" size={14} />
                </span>
                支出
              </span>
              <strong className="negative">{formatNumber(summary.totalExpenseAmount)}</strong>
            </div>
            <div className="card kpi">
              <span className="kpi-label">
                <span className="kpi-icon">
                  <Icon name="scale" size={14} />
                </span>
                結餘
              </span>
              <strong className={amountClass(summary.totalNetAmount)}>
                {formatNumber(summary.totalNetAmount)}
              </strong>
            </div>
          </div>

          <div className="share-grid">
            <ShareCard
              title="支出占比"
              empty="本月無支出"
              slices={toSlices((a) => a.expenseAmount)}
            />
            <ShareCard
              title="收入占比"
              empty="本月無收入"
              slices={toSlices((a) => a.incomeAmount)}
            />
            {accounts.length > 0 && (
              <section className="card share-card" aria-label="各帳戶結餘">
                <h2 className="share-title">各帳戶結餘</h2>
                <ul className="balance-list">
                  {[...accounts]
                    .sort((a, b) => b.netAmount - a.netAmount)
                    .map((a) => (
                      <li key={a.paymentAccountId}>
                        <span>{a.paymentAccountName ?? DELETED_ACCOUNT}</span>
                        <strong className={amountClass(a.netAmount)}>
                          {formatNumber(a.netAmount)}
                        </strong>
                      </li>
                    ))}
                </ul>
              </section>
            )}
          </div>
        </>
      )}
    </div>
  );
}
