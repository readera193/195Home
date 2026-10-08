import axios from 'axios';

/**
 * 後端錯誤一律為 RFC 9457 Problem Details（application/problem+json）：
 * `detail` 為給使用者看的訊息，`code` 為穩定的機器可讀錯誤代碼（見 contracts/app-service.md）。
 */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  errors?: { field: string; message: string }[];
}

export function getProblemDetail(err: unknown): ProblemDetail | undefined {
  if (axios.isAxiosError(err)) {
    return err.response?.data as ProblemDetail | undefined;
  }
  return undefined;
}

/** 取出錯誤訊息供畫面顯示；後端沒有提供 detail（例如網路中斷）時使用 fallback。 */
export function getErrorMessage(err: unknown, fallback: string): string {
  return getProblemDetail(err)?.detail ?? fallback;
}

export function getErrorCode(err: unknown): string | undefined {
  return getProblemDetail(err)?.code;
}
