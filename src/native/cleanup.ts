import { registerPlugin } from '@capacitor/core'

export interface DupPhoto {
  path: string
  name: string
  size: number
  mtime: number
  mime: string
}

export interface DupGroup {
  kind: 'exact' | 'similar'
  key: string
  photos: DupPhoto[]
}

export interface CleanupNative {
  scanDuplicates(o?: { limit?: number }): Promise<{ groups: DupGroup[] }>
  scanBySize(o: { minBytes: number }): Promise<{ groups: { size: number; photos: DupPhoto[] }[] }>
}

export const Cleanup = registerPlugin<CleanupNative>('Cleanup')
