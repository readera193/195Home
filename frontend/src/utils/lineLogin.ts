const STATE_KEY = 'family195home_line_state';
const AUTHORIZE_URL = 'https://access.line.me/oauth2/v2.1/authorize';

/** 回呼網址：須與後端 LINE_LOGIN_REDIRECT_URI 及 LINE Developers Console 的 Callback URL 完全一致。 */
export function getRedirectUri(): string {
  return `${window.location.origin}/login/callback`;
}

/** 有設定 VITE_LINE_CHANNEL_ID 才顯示 LINE 登入按鈕。 */
export function getLineChannelId(): string | undefined {
  return import.meta.env.VITE_LINE_CHANNEL_ID || undefined;
}

/** 產生 state（防 CSRF）存入 sessionStorage，並回傳 LINE 授權頁網址。 */
export function buildAuthorizeUrl(channelId: string): string {
  const state = crypto.randomUUID();
  try {
    sessionStorage.setItem(STATE_KEY, state);
  } catch {
    // 無法儲存時 callback 驗證 state 會失敗，使用者需重新登入
  }
  const params = new URLSearchParams({
    response_type: 'code',
    client_id: channelId,
    redirect_uri: getRedirectUri(),
    state,
    scope: 'profile',
  });
  return `${AUTHORIZE_URL}?${params.toString()}`;
}

/** 驗證 callback 帶回的 state 與登入前存的一致；一次性，驗證後即清除。 */
export function consumeState(state: string | null): boolean {
  try {
    const expected = sessionStorage.getItem(STATE_KEY);
    sessionStorage.removeItem(STATE_KEY);
    return !!state && !!expected && state === expected;
  } catch {
    return false;
  }
}
