import Swal from 'sweetalert2';

const PRIMARY = '#2563eb';
const DANGER = '#dc2626';

/** 錯誤提示（取代頁面內的 role="alert" 段落）。 */
export function notifyError(message: string) {
  return Swal.fire({ icon: 'error', title: '操作失敗', text: message, confirmButtonText: '知道了', confirmButtonColor: PRIMARY });
}

/** 確認對話框（取代 window.confirm）；回傳使用者是否按下確認。 */
export async function confirmDialog(options: { title: string; text?: string; confirmText?: string; danger?: boolean }) {
  const result = await Swal.fire({
    icon: options.danger ? 'warning' : 'question',
    titleText: options.title,
    text: options.text,
    showCancelButton: true,
    confirmButtonText: options.confirmText ?? '確定',
    cancelButtonText: '取消',
    confirmButtonColor: options.danger ? DANGER : PRIMARY,
    reverseButtons: true,
    focusCancel: options.danger,
  });
  return result.isConfirmed;
}

/** 單行文字輸入對話框（取代 window.prompt）；取消時回傳 undefined。 */
export async function promptText(title: string, value: string) {
  const result = await Swal.fire({
    titleText: title,
    input: 'text',
    inputValue: value,
    showCancelButton: true,
    confirmButtonText: '確定',
    cancelButtonText: '取消',
    confirmButtonColor: PRIMARY,
    reverseButtons: true,
  });
  return result.isConfirmed ? (result.value as string) : undefined;
}

/** 下拉選單對話框；取消時回傳 undefined，否則回傳所選項目的 value。 */
export async function selectOption(options: {
  title: string;
  text?: string;
  choices: { value: number; label: string }[];
  placeholder: string;
  confirmText?: string;
  danger?: boolean;
}) {
  const result = await Swal.fire({
    icon: options.danger ? 'warning' : 'question',
    titleText: options.title,
    text: options.text,
    input: 'select',
    inputOptions: Object.fromEntries(options.choices.map((c) => [String(c.value), c.label])),
    inputPlaceholder: options.placeholder,
    inputValidator: (value) => (value ? undefined : options.placeholder),
    showCancelButton: true,
    confirmButtonText: options.confirmText ?? '確定',
    cancelButtonText: '取消',
    confirmButtonColor: options.danger ? DANGER : PRIMARY,
    reverseButtons: true,
  });
  return result.isConfirmed ? Number(result.value) : undefined;
}
