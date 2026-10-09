import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { generateLineBindingCode, getMyFamily } from '../services/familyApi';
import { getErrorMessage } from '../utils/apiError';

export default function LineBindingPage() {
  const { data: myFamily } = useQuery({ queryKey: ['myFamily'], queryFn: getMyFamily });
  const [code, setCode] = useState<string | null>(null);
  const [expiresAt, setExpiresAt] = useState<string | null>(null);
  const [remainingSeconds, setRemainingSeconds] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!expiresAt) return;
    const timer = setInterval(() => {
      const diff = Math.max(0, Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000));
      setRemainingSeconds(diff);
      if (diff <= 0) clearInterval(timer);
    }, 1000);
    return () => clearInterval(timer);
  }, [expiresAt]);

  async function handleGenerate() {
    if (!myFamily?.familyMemberId) return;
    setError(null);
    try {
      const response = await generateLineBindingCode(myFamily.familyMemberId);
      setCode(response.code);
      setExpiresAt(response.expiresAt);
    } catch (err) {
      setError(getErrorMessage(err, '產生綁定碼失敗'));
    }
  }

  if (!myFamily?.familyGroupId) {
    return (
      <div className="card empty">
        <p>您尚未加入任何家庭群組</p>
        <Link to="/family">前往建立或加入群組</Link>
      </div>
    );
  }

  return (
    <div className="container-narrow">
      <div className="page-header">
        <h1>LINE 綁定</h1>
        <p className="page-desc">產生綁定碼後，於 10 分鐘內在 LINE Bot 對話中輸入該碼即可完成綁定。</p>
      </div>
      {error && <p role="alert">{error}</p>}
      <div className="card">
        <button onClick={handleGenerate}>產生綁定碼</button>
        {code && (
          <div style={{ marginTop: 20 }}>
            <p>
              綁定碼：<strong className="code-box code-box-lg">{code}</strong>
            </p>
            {remainingSeconds !== null && (
              <p className={remainingSeconds > 0 ? 'muted' : 'negative'}>
                {remainingSeconds > 0 ? `剩餘 ${remainingSeconds} 秒有效` : '此綁定碼已過期，請重新產生'}
              </p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
