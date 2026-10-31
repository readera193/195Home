import React from 'react';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import Icon, { IconName } from './Icon';

const NAV_ITEMS: { to: string; label: string; icon: IconName }[] = [
  { to: '/expenses', label: '支出紀錄', icon: 'receipt' },
  { to: '/family', label: '家庭群組', icon: 'family' },
  { to: '/accounts', label: '支付帳戶', icon: 'wallet' },
];

export default function Layout() {
  const { logout } = useAuth();
  return (
    <>
      <header className="topbar">
        <div className="topbar-inner">
          <Link to="/" className="brand">
            <Icon name="home" size={22} />
            家庭共享支出平台
          </Link>
          <nav className="nav" aria-label="主選單">
            {/* 桌面版以品牌 logo 回首頁；手機版沒有頂部 logo 導覽，故底部多一個首頁入口 */}
            <NavLink to="/" end className="nav-home" aria-label="首頁" title="首頁">
              <Icon name="home" size={20} />
              <span className="nav-label">首頁</span>
            </NavLink>
            {NAV_ITEMS.map((item) => (
              <NavLink key={item.to} to={item.to} aria-label={item.label} title={item.label}>
                <Icon name={item.icon} size={20} />
                <span className="nav-label">{item.label}</span>
              </NavLink>
            ))}
          </nav>
          <button type="button" className="btn-secondary btn-sm topbar-logout" onClick={logout}>
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
