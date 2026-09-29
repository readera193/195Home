import React from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

export default function HomePage() {
  const { logout } = useAuth();
  return (
    <div>
      <h1>家庭共享支出平台</h1>
      <nav>
        <ul>
          <li>
            <Link to="/family">家庭群組</Link>
          </li>
          <li>
            <Link to="/members">成員列表</Link>
          </li>
          <li>
            <Link to="/accounts">支付帳戶</Link>
          </li>
          <li>
            <Link to="/expenses">支出紀錄</Link>
          </li>
          <li>
            <Link to="/expenses/new">新增支出</Link>
          </li>
          <li>
            <Link to="/statistics">統計</Link>
          </li>
          <li>
            <Link to="/line-binding">LINE 綁定</Link>
          </li>
        </ul>
      </nav>
      <button onClick={logout}>登出</button>
    </div>
  );
}
