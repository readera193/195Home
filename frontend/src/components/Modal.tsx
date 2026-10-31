import React, { useEffect, useRef } from 'react';

interface ModalProps {
  /** 對話框的無障礙名稱。 */
  label: string;
  onClose: () => void;
  onSubmit: (e: React.FormEvent) => void;
  /** 送出中：禁止以 Esc／點背景關閉。 */
  busy?: boolean;
  children: React.ReactNode;
}

/** 表單式對話框：Esc 關閉；點背景關閉須「按下與放開都在背景上」，避免選取文字拖曳時誤關。 */
export default function Modal({ label, onClose, onSubmit, busy = false, children }: ModalProps) {
  const pressedOnBackdrop = useRef(false);

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      // SweetAlert2 提示開啟時，Esc 留給它關閉
      if (e.key !== 'Escape' || busy || document.querySelector('.swal2-container')) return;
      onClose();
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [busy, onClose]);

  return (
    <div
      className="modal-backdrop"
      onMouseDown={(e) => {
        pressedOnBackdrop.current = e.target === e.currentTarget;
      }}
      onClick={(e) => {
        if (pressedOnBackdrop.current && e.target === e.currentTarget && !busy) onClose();
        pressedOnBackdrop.current = false;
      }}
    >
      <form
        className="card modal"
        role="dialog"
        aria-modal="true"
        aria-label={label}
        onSubmit={onSubmit}
      >
        {children}
      </form>
    </div>
  );
}
