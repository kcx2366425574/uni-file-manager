<template>
  <view class="row" @click="$emit('open', entry)" @longpress="$emit('select', entry)">
    <text class="icon">{{ entry.dir ? '📁' : iconOf(entry.mime) }}</text>
    <view class="meta">
      <text class="name">{{ entry.name }}</text>
      <text class="sub">{{ entry.dir ? '文件夹' : formatSize(entry.size) }} · {{ formatTime(entry.mtime) }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { formatSize, formatTime } from '../utils/format'
import type { FileEntry } from '../native/fs'

defineProps<{ entry: FileEntry }>()
defineEmits<{ (e: 'open', v: FileEntry): void; (e: 'select', v: FileEntry): void }>()

function iconOf(mime: string): string {
  if (mime.startsWith('image/')) return '🖼'
  if (mime.startsWith('video/')) return '🎬'
  if (mime.startsWith('audio/')) return '🎵'
  if (mime === 'application/pdf') return '📄'
  if (mime === 'application/zip') return '🗜'
  if (mime === 'application/vnd.android.package-archive') return '📦'
  return '📃'
}
</script>

<style>
.row { display: flex; align-items: center; padding: 24rpx 32rpx; border-bottom: 1rpx solid #1f2429; }
.icon { font-size: 40rpx; width: 72rpx; }
.meta { display: flex; flex-direction: column; flex: 1; min-width: 0; }
.name { font-size: 30rpx; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.sub { font-size: 22rpx; color: #7d848c; margin-top: 6rpx; }
</style>
