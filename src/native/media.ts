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
  thumbnailData(o: { path: string; maxPx?: number }): Promise<{ dataUrl: string }>
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

/**
 * Thumbnail as a data URL. uni-app's image pipeline rewrites https://localhost/_capacitor_file_
 * URLs into root-relative paths (proven over CDP: DOM img got src="/cache/thumbs/x.jpg" while
 * the real URL loaded), so file-server URLs are not usable in this app. A data URL bypasses
 * that path entirely. Kept for grid/cover use where a few dozen images are on screen.
 */
const dataMemo = new Map<string, string>()

export async function thumbDataUrl(path: string, maxPx = 256): Promise<string> {
  const k = path + '|' + maxPx
  const hit = dataMemo.get(k)
  if (hit) return hit
  const { dataUrl } = await Media.thumbnailData({ path, maxPx })
  if (dataMemo.size > 500) dataMemo.clear()
  dataMemo.set(k, dataUrl)
  return dataUrl
}
