/** FR-016：前端整數驗證（後端仍會再次驗證）。通過回傳 null，否則回傳要顯示的錯誤訊息。 */
export function validateAmountAndNote(amount: string, note: string): string | null {
  if (!/^-?\d+$/.test(amount)) {
    return '金額必須為整數，不支援小數點';
  }
  if (!note.trim()) {
    return '說明為必填欄位';
  }
  return null;
}

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

/** 本月第一天（offset=0）或最後一天（offset=1），格式 YYYY-MM-DD（本地時區）。 */
export function monthBoundary(offset: 0 | 1): string {
  const now = new Date();
  const d =
    offset === 0
      ? new Date(now.getFullYear(), now.getMonth(), 1)
      : new Date(now.getFullYear(), now.getMonth() + 1, 0);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
