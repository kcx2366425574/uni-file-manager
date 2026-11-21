import { registerPlugin } from '@capacitor/core'

export interface FavItem {
  path: string
  mediaId: number
  addedAt: number
}

export interface FavNative {
  add(o: { path: string; mediaId: number }): Promise<void>
  remove(o: { path: string }): Promise<void>
  list(): Promise<{ items: FavItem[]; pruned: number }>
  prune(): Promise<{ removed: number }>
}

export const Fav = registerPlugin<FavNative>('Fav')
