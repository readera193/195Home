import { describe, expect, it } from 'vitest';
import { AxiosError, AxiosHeaders } from 'axios';
import { getErrorCode, getErrorMessage } from '../src/utils/apiError';

function problemError(data: unknown, status: number): AxiosError {
  const config = { headers: new AxiosHeaders() };
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', config, null, {
    data,
    status,
    statusText: '',
    headers: {},
    config,
  });
}

describe('apiError', () => {
  it('從 Problem Details 取出 detail 與 code', () => {
    const err = problemError(
      { status: 409, title: 'Conflict', detail: '您已屬於一個家庭群組', code: 'ALREADY_IN_A_GROUP' },
      409,
    );

    expect(getErrorMessage(err, 'fallback')).toBe('您已屬於一個家庭群組');
    expect(getErrorCode(err)).toBe('ALREADY_IN_A_GROUP');
  });

  it('沒有 detail（例如網路中斷）時使用 fallback', () => {
    const err = new AxiosError('Network Error');

    expect(getErrorMessage(err, '登入失敗')).toBe('登入失敗');
    expect(getErrorCode(err)).toBeUndefined();
  });

  it('非 Axios 錯誤時使用 fallback', () => {
    expect(getErrorMessage(new Error('boom'), '失敗')).toBe('失敗');
  });
});
