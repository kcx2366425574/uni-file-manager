import { registerPlugin } from '@capacitor/core'

export interface GroupInfo {
  name: string
  path: string
  count: number
  cover: string | null
}

export interface GroupPhoto {
  name: string
  path: string
  size: number
  mtime: number
  mime: string
}

export interface GroupNative {
  list(): Promise<{ root: string; groups: GroupInfo[] }>
  create(o: { name: string }): Promise<{ path: string }>
  rename(o: { path: string; newName: string }): Promise<{ path: string }>
  remove(o: { path: string }): Promise<void>
  photos(o: { path: string }): Promise<{ photos: GroupPhoto[] }>
}

export const Group = registerPlugin<GroupNative>('Group')
