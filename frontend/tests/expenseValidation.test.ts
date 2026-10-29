import { describe, expect, it } from 'vitest';
import { monthBoundary, validateAmountAndNote } from '../src/utils/expenseValidation';

describe('validateAmountAndNote', () => {
  it('接受正數、負數與 0 的整數金額', () => {
    expect(validateAmountAndNote('100', '收入')).toBeNull();
    expect(validateAmountAndNote('-350', '午餐')).toBeNull();
    expect(validateAmountAndNote('0', '僅記錄')).toBeNull();
  });

  it('拒絕小數與非數字金額', () => {
    expect(validateAmountAndNote('100.5', '午餐')).toBe('金額必須為整數，不支援小數點');
    expect(validateAmountAndNote('abc', '午餐')).toBe('金額必須為整數，不支援小數點');
    expect(validateAmountAndNote('', '午餐')).toBe('金額必須為整數，不支援小數點');
  });

  it('拒絕空白說明', () => {
    expect(validateAmountAndNote('100', '   ')).toBe('說明為必填欄位');
  });
});

describe('monthBoundary', () => {
  it('回傳本月第一天與最後一天（YYYY-MM-DD）', () => {
    const now = new Date();
    const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
    const prefix = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;

    expect(monthBoundary(0)).toBe(`${prefix}-01`);
    expect(monthBoundary(1)).toBe(`${prefix}-${String(lastDay).padStart(2, '0')}`);
  });
});
