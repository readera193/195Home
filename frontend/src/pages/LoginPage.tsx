import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getAuthConfig, loginAsDemo } from '../services/userApi';
import { useAuth } from '../hooks/useAuth';
import { getErrorMessage } from '../utils/apiError';
import { notifyError } from '../utils/notify';
import { buildAuthorizeUrl, getLineChannelId } from '../utils/lineLogin';

// 兩種登入並行：LINE 登入（有設定 VITE_LINE_CHANNEL_ID 才顯示，callback 見 LoginCallbackPage）、
// Demo 體驗（後端 DEMO_LOGIN_ENABLED 開啟才顯示，一鍵建立隨機帳號）。
export default function LoginPage() {
  const [demoEnabled, setDemoEnabled] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const auth = useAuth();
  const navigate = useNavigate();
  const channelId = getLineChannelId();

  useEffect(() => {
    getAuthConfig()
      .then((config) => setDemoEnabled(config.demoLoginEnabled))
      .catch(() => setDemoEnabled(false));
  }, []);

  async function handleDemo() {
    setSubmitting(true);
    try {
      const response = await loginAsDemo();
      auth.login(response.token);
      navigate('/');
    } catch (err) {
      notifyError(getErrorMessage(err, '登入失敗，請稍後再試'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-wrap">
      <div className="auth-card">
        <h1>登入</h1>
        <div className="auth-actions">
          {channelId && (
            <button
              type="button"
              className="btn-block btn-line"
              onClick={() => window.location.assign(buildAuthorizeUrl(channelId))}
            >
              使用 LINE 登入
            </button>
          )}
          {demoEnabled && (
            <button type="button" className="btn-block" disabled={submitting} onClick={handleDemo}>
              Demo 體驗（免登入）
            </button>
          )}
        </div>
        {!channelId && !demoEnabled && <p>目前沒有可用的登入方式</p>}
      </div>
    </div>
  );
}
