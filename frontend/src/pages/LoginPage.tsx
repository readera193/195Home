import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { loginWithLine } from '../services/userApi';
import { useAuth } from '../hooks/useAuth';
import { getErrorMessage } from '../utils/apiError';
import InfoTip from '../components/InfoTip';
import { buildAuthorizeUrl, getLineChannelId } from '../utils/lineLogin';

// 有設定 VITE_LINE_CHANNEL_ID 時導向 LINE 授權頁（callback 見 LoginCallbackPage）；
// 未設定時為開發用假登入：輸入暱稱即登入（同暱稱視為同一帳號）。
export default function LoginPage() {
  const [nickname, setNickname] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const auth = useAuth();
  const navigate = useNavigate();

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const response = await loginWithLine(nickname.trim());
      auth.login(response.token);
      navigate('/');
    } catch (err) {
      setError(getErrorMessage(err, '登入失敗，請稍後再試'));
    } finally {
      setSubmitting(false);
    }
  }

  const channelId = getLineChannelId();
  if (channelId) {
    return (
      <div className="auth-wrap">
        <div className="auth-card">
          <h1>登入</h1>
          <button type="button" className="btn-block" onClick={() => window.location.assign(buildAuthorizeUrl(channelId))}>
            使用 LINE 登入
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-wrap">
      <div className="auth-card">
        <h1>登入</h1>
        <form onSubmit={handleSubmit}>
          <div className="field">
            <div className="label-row">
              <label htmlFor="nickname">LINE 暱稱</label>
              <InfoTip text="開發用：輸入暱稱即可登入，相同暱稱視為同一帳號。" />
            </div>
            <input id="nickname" value={nickname} onChange={(e) => setNickname(e.target.value)} required />
          </div>
          {error && <p role="alert">{error}</p>}
          <button type="submit" className="btn-block" disabled={submitting || !nickname.trim()}>
            使用 LINE 登入
          </button>
        </form>
      </div>
    </div>
  );
}
