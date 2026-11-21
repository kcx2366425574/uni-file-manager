import { registerPlugin } from '@capacitor/core'

export interface PermissionSnapshot {
  allFiles: boolean
  mediaImages: boolean
  installPackages: boolean
  sdk: number
}

export interface ShellNative {
  permissionState(): Promise<PermissionSnapshot>
  openAllFilesSettings(): Promise<void>
  openInstallSettings(): Promise<void>
}

export const Shell = registerPlugin<ShellNative>('Shell')
