import { useEffect, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { loginWithLine } from '../services/userApi';
import { useAuth } from '../hooks/useAuth';
import { getErrorMessage } from '../utils/apiError';
import { consumeState } from '../utils/lineLogin';
import { notifyError } from '../utils/notify';

// LINE 授權完成後的回呼頁：驗證 state，以授權碼呼叫 POST /api/auth/line 換取 JWT。
export default function LoginCallbackPage() {
  const [params] = useSearchParams();
  const auth = useAuth();
  const navigate = useNavigate();
  // 失敗時以對話框告知，確認後回登入頁
  const fail = (message: string) =>
    notifyError(message).then(() => navigate('/login', { replace: true }));
  // 授權碼僅能使用一次，避免 StrictMode 重複執行 effect
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;

    const code = params.get('code');
    if (params.get('error') || !code) {
      fail('已取消 LINE 登入或授權失敗');
      return;
    }
    if (!consumeState(params.get('state'))) {
      fail('登入驗證失敗（state 不符），請重新登入');
      return;
    }
    loginWithLine(code)
      .then((response) => {
        auth.login(response.token);
        navigate('/', { replace: true });
      })
      .catch((err) => fail(getErrorMessage(err, '登入失敗，請稍後再試')));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="auth-wrap">
      <div className="auth-card">
        <p>登入中...</p>
      </div>
    </div>
  );
}
