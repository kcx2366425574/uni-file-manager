import { registerPlugin } from '@capacitor/core'

export interface ShareNative {
  shareFiles(o: { paths: string[] }): Promise<void>
  openFile(o: { path: string; mime: string }): Promise<void>
}

export const Share = registerPlugin<ShareNative>('Share')
