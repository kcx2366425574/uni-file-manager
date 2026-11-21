<template>
  <PermGate>
    <view class="page">
      <view class="hero">
        <text class="title">文件管理器</text>
        <text class="sub">已用 {{ used }} / 共 {{ total }}</text>
        <view class="meter"><view class="meterfill" :style="{ width: usedPct }" /></view>
      </view>

      <view class="kinds">
        <view class="kind" @click="goPhotos('image')"><text class="emoji">🖼</text><text>图片</text></view>
        <view class="kind" @click="goPhotos('video')"><text class="emoji">🎬</text><text>视频</text></view>
        <view class="kind" @click="goPhotos('audio')"><text class="emoji">🎵</text><text>音频</text></view>
        <view class="kind" @click="goPhotos('doc')"><text class="emoji">📄</text><text>文档</text></view>
      </view>

      <view class="quick">
        <view class="qitem" @click="go('/pages/files/files')"><text>📂 浏览文件</text></view>
        <view class="qitem" @click="go('/pages/groups/groups')"><text>🗂 照片分组</text></view>
        <view class="qitem" @click="go('/pages/favorites/favorites')"><text>⭐ 我的收藏</text></view>
        <view class="qitem" @click="go('/pages/recent/recent')"><text>🕘 最近文件</text></view>
        <view class="qitem" @click="goDownload"><text>⬇️ 下载目录</text></view>
        <view class="qitem" @click="go('/pages/cleanup/cleanup')"><text>🧹 整理照片</text></view>
        <view class="qitem" @click="go('/pages/settings/settings')"><text>⚙️ 设置</text></view>
      </view>
    </view>
  </PermGate>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { onMounted, ref } from 'vue'
import PermGate from '../../components/PermGate.vue'
import { Shell } from '../../native/shell'
import { formatSize } from '../../utils/format'

const used = ref('—')
const total = ref('—')
const usedPct = ref('0%')

async function refreshStats() {
  try {
    const s = await Shell.storageStats()
    used.value = formatSize(s.usedBytes)
    total.value = formatSize(s.totalBytes)
    usedPct.value = s.totalBytes ? `${Math.round((s.usedBytes / s.totalBytes) * 100)}%` : '0%'
  } catch {
    // Storage stats are decoration; the page must still render without them.
    used.value = '—'
    total.value = '—'
  }
}

function go(url: string) {
  uni.navigateTo({ url })
}

function goPhotos(kind: string) {
  uni.navigateTo({ url: `/pages/photos/photos?kind=${kind}` })
}

function goDownload() {
  uni.navigateTo({ url: '/pages/files/files?path=' + encodeURIComponent('/storage/emulated/0/Download') })
}

onMounted(refreshStats)
onShow(refreshStats)
</script>

<style>
.page { padding: 40rpx 32rpx 120rpx; }
.hero { margin-bottom: 48rpx; }
.title { font-size: 48rpx; font-weight: 700; }
.sub { display: block; margin-top: 12rpx; color: #8a9199; font-size: 26rpx; }
.meter { margin-top: 20rpx; height: 12rpx; border-radius: 6rpx; background: #23282e; overflow: hidden; }
.meterfill { height: 100%; background: #3b82f6; }
.kinds { display: flex; justify-content: space-between; margin-bottom: 48rpx; }
.kind { width: 23%; background: #191d22; border-radius: 16rpx; padding: 28rpx 0; display: flex; flex-direction: column; align-items: center; font-size: 26rpx; color: #cfd4d9; }
.emoji { font-size: 44rpx; margin-bottom: 12rpx; }
.quick { display: flex; flex-wrap: wrap; }
.qitem { width: 48%; background: #191d22; border-radius: 16rpx; padding: 32rpx 24rpx; margin-bottom: 20rpx; font-size: 28rpx; box-sizing: border-box; }
.qitem:nth-child(odd) { margin-right: 4%; }
</style>
