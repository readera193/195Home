/** 載入中的骨架畫面：先佔好版面，資料回來時不會整頁跳動。 */
export function SkeletonLines({ count = 4 }: { count?: number }) {
  return (
    <div className="card" role="status" aria-label="載入中">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="skeleton skeleton-line" />
      ))}
    </div>
  );
}

export function SkeletonKpis() {
  return (
    <div className="kpi-row" role="status" aria-label="載入中">
      {[0, 1, 2].map((i) => (
        <div key={i} className="card kpi">
          <div className="skeleton skeleton-label" />
          <div className="skeleton skeleton-value" />
        </div>
      ))}
    </div>
  );
}
