import type { ExpenseResponse } from '../../services/expenseApi';

interface ExpenseTableProps {
  expenses?: ExpenseResponse[];
  /** 正在編輯的紀錄：編輯期間其他列的編輯／刪除按鈕停用，該列不顯示「編輯中」。 */
  editingId?: number;
  onEdit: (expense: ExpenseResponse) => void;
  onDelete: (expense: ExpenseResponse) => void;
}

function amountClass(amount: number): string {
  if (amount > 0) return 'positive';
  if (amount < 0) return 'negative';
  return '';
}

export default function ExpenseTable({ expenses, editingId, onEdit, onDelete }: ExpenseTableProps) {
  const busy = editingId !== undefined;

  return (
    <div className="table-wrap">
      <table className="expense-table">
        <thead>
          <tr>
            <th>日期</th>
            <th>說明</th>
            <th className="num">金額</th>
            <th>支付帳戶</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          {expenses?.map((expense) => (
            <tr key={expense.expenseId}>
              <td className="col-date">
                <div>{new Date(expense.occurredAt).toLocaleDateString('zh-TW')}</div>
                <div>
                  {new Date(expense.occurredAt).toLocaleTimeString('zh-TW', {
                    hourCycle: 'h23',
                    hour: '2-digit',
                    minute: '2-digit',
                    second: '2-digit',
                  })}
                </div>
              </td>
              <td className="col-note">{expense.note}</td>
              <td className={`num col-amount ${amountClass(expense.amount)}`}>{expense.amount}</td>
              <td className="col-account">
                <span className="badge">{expense.paymentAccountName}</span>
              </td>
              <td className="col-actions">
                {expense.locked && editingId !== expense.expenseId && (
                  <span className="badge">編輯中</span>
                )}{' '}
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => onEdit(expense)}
                  disabled={busy}
                  aria-label={`編輯 ${expense.note}`}
                >
                  編輯
                </button>{' '}
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => onDelete(expense)}
                  disabled={busy}
                  aria-label={`刪除 ${expense.note}`}
                >
                  刪除
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
