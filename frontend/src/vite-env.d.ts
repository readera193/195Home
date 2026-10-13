/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** LINE Login channel ID（公開資訊）；未設定時登入頁使用開發用假登入 */
  readonly VITE_LINE_CHANNEL_ID?: string;
}
