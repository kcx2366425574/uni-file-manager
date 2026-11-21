import { registerPlugin } from '@capacitor/core'

export type SortKey = 'name' | 'size' | 'time' | 'type'

export interface FileEntry {
  name: string
  path: string
  dir: boolean
  size: number
  mtime: number
  mime: string
}

export interface Volume {
  label: string
  path: string
  removable: boolean
  availableBytes: number
}

export interface FsNative {
  volumes(): Promise<{ volumes: Volume[] }>
  listDir(o: { path: string; sort?: SortKey; asc?: boolean }): Promise<{ path: string; entries: FileEntry[] }>
}

export const Fs = registerPlugin<FsNative>('Fs')
