import { useEffect, useRef, useState } from 'react';
import Icon from './Icon';

/** 複製文字到剪貼簿，成功後短暫顯示「已複製」。 */
export default function CopyButton({ text }: { text: string }) {
  const [state, setState] = useState<'idle' | 'copied' | 'failed'>('idle');
  const timer = useRef<number>();

  useEffect(() => () => window.clearTimeout(timer.current), []);

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(text);
      setState('copied');
    } catch {
      setState('failed');
    }
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => setState('idle'), 1500);
  }

  return (
    <button type="button" className="btn-secondary btn-sm copy-btn" onClick={handleCopy}>
      <Icon name={state === 'copied' ? 'check' : 'copy'} size={14} />
      {state === 'copied' ? '已複製' : state === 'failed' ? '複製失敗' : '複製'}
    </button>
  );
}
