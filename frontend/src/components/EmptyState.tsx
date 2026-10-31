import type { ReactNode } from 'react';
import Icon from './Icon';

interface EmptyStateProps {
  title: string;
  hint?: string;
  action?: ReactNode;
}

export default function EmptyState({ title, hint, action }: EmptyStateProps) {
  return (
    <div className="card empty-state">
      <span className="empty-state-icon">
        <Icon name="inbox" size={28} />
      </span>
      <p className="empty-state-title">{title}</p>
      {hint && <p className="muted">{hint}</p>}
      {action}
    </div>
  );
}
