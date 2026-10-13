import React from 'react';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

const NAV_ITEMS = [
  { to: '/expenses', label: '支出紀錄' },
  { to: '/family', label: '家庭群組' },
  { to: '/members', label: '成員列表' },
  { to: '/accounts', label: '支付帳戶' },
  { to: '/statistics', label: '統計' },
];

export default function Layout() {
  const { logout } = useAuth();
  return (
    <>
      <header className="topbar">
        <div className="topbar-inner">
          <Link to="/" className="brand">
            🏠 家庭共享支出平台
          </Link>
          <nav className="nav" aria-label="主選單">
            {NAV_ITEMS.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end}>
                {item.label}
              </NavLink>
            ))}
          </nav>
          <button type="button" className="btn-secondary btn-sm" onClick={logout}>
            登出
          </button>
        </div>
      </header>
      <main className="container">
        <Outlet />
      </main>
    </>
  );
}
