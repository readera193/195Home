import React from 'react';
import { Link } from 'react-router-dom';
import Icon, { IconName } from '../components/Icon';

const TILES: { to: string; icon: IconName; title: string; desc: string }[] = [
  { to: '/expenses', icon: 'receipt', title: '支出紀錄', desc: '新增、瀏覽與篩選所有收支' },
  { to: '/family', icon: 'family', title: '家庭群組', desc: '建立或加入群組、邀請碼與成員管理' },
  { to: '/accounts', icon: 'wallet', title: '支付帳戶', desc: '管理共用的支付帳戶' },
  { to: '/statistics', icon: 'chart', title: '統計', desc: '依月份檢視各帳戶淨額' },
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
            <span className="tile-icon">
              <Icon name={tile.icon} />
            </span>
            <div className="tile-title">{tile.title}</div>
            <p className="tile-desc">{tile.desc}</p>
          </Link>
        ))}
      </div>
    </div>
  );
}
