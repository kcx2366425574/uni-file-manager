import { registerPlugin } from '@capacitor/core'

export interface PermissionSnapshot {
  allFiles: boolean
  mediaImages: boolean
  installPackages: boolean
  sdk: number
}

export interface StorageStats {
  totalBytes: number
  freeBytes: number
  usedBytes: number
  volumes: { label: string; totalBytes: number; freeBytes: number }[]
}

export interface ShellNative {
  permissionState(): Promise<PermissionSnapshot>
  openAllFilesSettings(): Promise<void>
  openInstallSettings(): Promise<void>
  storageStats(): Promise<StorageStats>
}

export const Shell = registerPlugin<ShellNative>('Shell')
