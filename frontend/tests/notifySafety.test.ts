import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Swal from 'sweetalert2';
import { confirmDialog, promptText } from '../src/utils/notify';

beforeEach(() => {
  vi.stubGlobal('matchMedia', () => ({
    matches: false,
    addEventListener() {},
    removeEventListener() {},
  }));
});
afterEach(() => {
  Swal.close();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('使用者文字不會被對話框解析成 HTML', () => {
  it('確認視窗將名稱與說明當作純文字', async () => {
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
    const pending = confirmDialog({ title: '刪除「<b>使用者輸入</b>」？' });
    expect(Swal.getTitle()?.innerText).toBe('刪除「<b>使用者輸入</b>」？');
    expect(Swal.getTitle()?.querySelector('b')).toBeNull();
    Swal.clickCancel();
    expect(await pending).toBe(false);
  });

  it('輸入視窗的標題也不解析 HTML', async () => {
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
    const pending = promptText('<b>名稱</b>', '原名稱');
    expect(Swal.getTitle()?.querySelector('b')).toBeNull();
    Swal.clickCancel();
    expect(await pending).toBeUndefined();
  });
});
