const formatter = new Intl.NumberFormat('en-US');

/** 數字以千分位逗號顯示（例如 1234567 → 1,234,567）。 */
export function formatNumber(value: number): string {
  return formatter.format(value);
}

/** 金額正負對應的文字色 class：正數綠、負數紅、0 維持預設色。 */
export function amountClass(amount: number): string {
  if (amount > 0) return 'positive';
  if (amount < 0) return 'negative';
  return '';
}
