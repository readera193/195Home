import React from 'react';
import { Link } from 'react-router-dom';

const TILES = [
  { to: '/family', icon: '👨‍👩‍👧', title: '家庭群組', desc: '建立或加入群組、查看邀請碼' },
  { to: '/members', icon: '👥', title: '成員列表', desc: '管理群組成員與權限' },
  { to: '/accounts', icon: '💳', title: '支付帳戶', desc: '管理共用的支付帳戶' },
  { to: '/expenses', icon: '🧾', title: '支出紀錄', desc: '瀏覽與篩選所有收支' },
  { to: '/expenses/new', icon: '➕', title: '新增支出', desc: '快速記一筆新的收支' },
  { to: '/statistics', icon: '📊', title: '統計', desc: '依月份檢視各帳戶淨額' },
  { to: '/line-binding', icon: '💬', title: 'LINE 綁定', desc: '綁定 LINE Bot 以便記帳' },
];

export default function HomePage() {
  return (
    <div>
      <div className="page-header">
        <h1>家庭共享支出平台</h1>
        <p className="page-desc">選擇下方功能開始使用</p>
      </div>
      <div className="tile-grid">
        {TILES.map((tile) => (
          <Link key={tile.to} to={tile.to} className="tile">
            <span className="tile-icon" aria-hidden="true">
              {tile.icon}
            </span>
            <div className="tile-title">{tile.title}</div>
            <p className="tile-desc">{tile.desc}</p>
          </Link>
        ))}
      </div>
    </div>
  );
}
