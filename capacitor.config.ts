import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'com.kuangcx.ufm',
  appName: '文件管理器',
  webDir: 'dist/build/h5',
  android: { allowMixedContent: false, webContentsDebuggingEnabled: true }
}

export default config
