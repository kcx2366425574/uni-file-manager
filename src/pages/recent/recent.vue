<template>
  <view class="page">
    <view class="head"><text>最近 7 天</text><text class="count">{{ items.length }} 项</text></view>
    <scroll-view class="list" scroll-y>
      <view v-for="it in items" :key="it.path" class="row" @click="open(it)">
        <text class="name">{{ it.name }}</text>
        <text class="meta">{{ formatSize(it.size) }} · {{ formatTime(it.mtime) }}</text>
      </view>
      <view v-if="!items.length" class="empty"><text>{{ error || '最近没有新增文件' }}</text></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { Media, type MediaItem } from '../../native/media'
import { Share } from '../../native/share'
import { describeError } from '../../utils/errors'
import { formatSize, formatTime } from '../../utils/format'

const items = ref<MediaItem[]>([])
const error = ref('')

async function load() {
  try {
    // 'doc' covers every non media type; the 7-day window comes from date_added on the native side.
    const r = await Media.query({ kind: 'doc', page: 0, pageSize: 200, sinceMs: Date.now() - 7 * 86400_000 })
    items.value = r.items
  } catch (e) {
    error.value = describeError(e)
  }
}

function open(it: MediaItem) {
  Share.openFile({ path: it.path, mime: it.mime || '*/*' }).catch((e) =>
    uni.showToast({ title: describeError(e), icon: 'none' })
  )
}

onLoad(load)
</script>

<style>
.page { display: flex; flex-direction: column; height: 100vh; }
.head { display: flex; justify-content: space-between; padding: 20rpx 32rpx; background: #15191d; font-size: 28rpx; }
.count { color: #7d848c; font-size: 24rpx; }
.list { flex: 1; }
.row { padding: 24rpx 32rpx; border-bottom: 1rpx solid #1f2429; display: flex; flex-direction: column; }
.name { font-size: 28rpx; }
.meta { font-size: 22rpx; color: #7d848c; margin-top: 6rpx; }
.empty { padding: 100rpx; text-align: center; color: #6b7280; }
</style>
