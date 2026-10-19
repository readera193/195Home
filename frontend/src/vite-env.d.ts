/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** LINE Login channel ID（公開資訊）；未設定時登入頁不顯示 LINE 登入按鈕 */
  readonly VITE_LINE_CHANNEL_ID?: string;
}
