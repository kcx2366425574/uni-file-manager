import { Capacitor, registerPlugin } from '@capacitor/core'

export type MediaKind = 'image' | 'video' | 'audio' | 'doc'

export interface MediaItem {
  mediaId: number
  name: string
  path: string
  size: number
  mtime: number
  mime: string
  bucket: string
}

export interface MediaNative {
  query(o: { kind: MediaKind; page: number; pageSize: number; sinceMs?: number }): Promise<{ items: MediaItem[]; hasMore: boolean }>
  thumbnail(o: { path: string; maxPx?: number }): Promise<{ url: string }>
  trimThumbs(): Promise<void>
}

export const Media = registerPlugin<MediaNative>('Media')

const thumbMemo = new Map<string, string>()

/** Resolves a native thumbnail to a WebView-loadable URL, memoising the bridge round-trip. */
export async function thumbUrl(path: string, maxPx = 256): Promise<string> {
  const k = `${path}|${maxPx}`
  const hit = thumbMemo.get(k)
  if (hit) return hit
  const { url } = await Media.thumbnail({ path, maxPx })
  const src = Capacitor.isNativePlatform() ? Capacitor.convertFileSrc(url) : ''
  if (thumbMemo.size > 3000) thumbMemo.clear()
  thumbMemo.set(k, src)
  return src
}
