import { registerPlugin } from '@capacitor/core'

export interface UpdateCheckResult {
  available: boolean
  versionName: string
  versionCode: number
  notes: string
  apkUrl: string
}

export interface UpdateNative {
  currentVersion(): Promise<{ versionName: string; versionCode: number }>
  check(o: { owner: string; repo: string }): Promise<UpdateCheckResult>
  download(o: { apkUrl: string; fileName: string }): Promise<{ path: string }>
  install(o: { path: string }): Promise<void>
}

export const Update = registerPlugin<UpdateNative>('Update')

/** Public repo, so Release downloads need no credentials. */
export const UPDATE_REPO = { owner: 'kcx2366425574', repo: 'uni-file-manager' }
