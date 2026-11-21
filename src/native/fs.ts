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

export interface TransferResult {
  moved?: string[]
  copied?: string[]
  failed: { path: string; code: string }[]
}

export interface FsNative {
  volumes(): Promise<{ volumes: Volume[] }>
  listDir(o: { path: string; sort?: SortKey; asc?: boolean }): Promise<{ path: string; entries: FileEntry[] }>
  mkdir(o: { parent: string; name: string }): Promise<{ path: string }>
  rename(o: { path: string; newName: string }): Promise<{ path: string }>
  remove(o: { paths: string[] }): Promise<{ removed: string[]; failed: { path: string; code: string }[] }>
  move(o: { paths: string[]; destDir: string }): Promise<TransferResult>
  copy(o: { paths: string[]; destDir: string }): Promise<TransferResult>
}

export const Fs = registerPlugin<FsNative>('Fs')
