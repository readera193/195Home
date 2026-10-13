import React, { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { loginWithLine } from '../services/userApi';
import { useAuth } from '../hooks/useAuth';
import { getErrorMessage } from '../utils/apiError';
import { consumeState } from '../utils/lineLogin';

// LINE 授權完成後的回呼頁：驗證 state，以授權碼呼叫 POST /api/auth/line 換取 JWT。
export default function LoginCallbackPage() {
  const [params] = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const auth = useAuth();
  const navigate = useNavigate();
  // 授權碼僅能使用一次，避免 StrictMode 重複執行 effect
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;

    const code = params.get('code');
    if (params.get('error') || !code) {
      setError('已取消 LINE 登入或授權失敗');
      return;
    }
    if (!consumeState(params.get('state'))) {
      setError('登入驗證失敗（state 不符），請重新登入');
      return;
    }
    loginWithLine(code)
      .then((response) => {
        auth.login(response.token);
        navigate('/', { replace: true });
      })
      .catch((err) => setError(getErrorMessage(err, '登入失敗，請稍後再試')));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="auth-wrap">
      <div className="auth-card">
        {error ? (
          <>
            <p role="alert">{error}</p>
            <Link to="/login">返回登入頁</Link>
          </>
        ) : (
          <p>登入中...</p>
        )}
      </div>
    </div>
  );
}
