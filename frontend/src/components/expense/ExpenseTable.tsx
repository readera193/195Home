import type { ExpenseResponse } from '../../services/expenseApi';
import { amountClass, formatNumber } from '../../utils/formatNumber';

interface ExpenseTableProps {
  expenses?: ExpenseResponse[];
  /** 正在編輯的紀錄：編輯期間其他列的編輯／刪除按鈕停用，該列不顯示「編輯中」。 */
  editingId?: number;
  onEdit: (expense: ExpenseResponse) => void;
  onDelete: (expense: ExpenseResponse) => void;
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
              <td className="col-note">
                {expense.note}
                {expense.locked && editingId !== expense.expenseId && (
                  <span className="badge badge-warning">編輯中</span>
                )}
              </td>
              <td className={`num col-amount ${amountClass(expense.amount)}`}>
                {formatNumber(expense.amount)}
              </td>
              <td className="col-account">
                <span className="badge">{expense.paymentAccountName}</span>
              </td>
              <td className="col-actions">
                <div className="actions">
                  <button
                    type="button"
                    className="btn-secondary btn-sm"
                    onClick={() => onEdit(expense)}
                    disabled={busy}
                    aria-label={`編輯 ${expense.note}`}
                  >
                    編輯
                  </button>
                  <button
                    type="button"
                    className="btn-danger btn-sm"
                    onClick={() => onDelete(expense)}
                    disabled={busy}
                    aria-label={`刪除 ${expense.note}`}
                  >
                    刪除
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
