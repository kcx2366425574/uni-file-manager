<template>
  <view class="page">
    <view class="tabs">
      <text :class="['tab', tab === 'dup' ? 'on' : '']" @click="switchTab('dup')">重复/相似</text>
      <text :class="['tab', tab === 'size' ? 'on' : '']" @click="switchTab('size')">占空间</text>
    </view>
    <scroll-view class="body" scroll-y>
      <view v-if="scanning" class="empty"><text>正在扫描…（首次可能需要几分钟）</text></view>

      <view v-for="(g, gi) in dupGroups" :key="'d' + gi" class="card">
        <view class="cardhead">
          <text>{{ g.kind === 'exact' ? '完全重复' : '相似照片' }} · {{ g.photos.length }} 张</text>
          <text class="keep">{{ keepMap[gi] === undefined ? '点选要保留的那张' : '保留：' + g.photos[keepMap[gi]].name }}</text>
        </view>
        <view class="cells">
          <view
            v-for="(p, pi) in g.photos"
            :key="p.path"
            class="cell"
            :class="{ marked: keepMap[gi] === pi }"
            @click="keepMap[gi] = pi"
          >
            <ThumbImage :path="p.path" />
            <text class="sz">{{ formatSize(p.size) }}</text>
          </view>
        </view>
        <button class="danger" @click="deleteExtras(gi)">删除未保留的 {{ g.photos.length - 1 }} 张</button>
      </view>

      <view v-for="(g, gi) in sizeGroups" :key="'s' + gi" class="card">
        <view class="cardhead"><text>单张 {{ formatSize(g.size) }}</text></view>
        <view class="cells">
          <view v-for="p in g.photos" :key="p.path" class="cell">
            <ThumbImage :path="p.path" />
          </view>
        </view>
      </view>

      <view v-if="!scanning && !dupGroups.length && !sizeGroups.length" class="empty">
        <text>{{ error || '没有发现可整理的内容' }}</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ThumbImage from '../../components/ThumbImage.vue'
import { Cleanup, type DupGroup } from '../../native/cleanup'
import { Fs } from '../../native/fs'
import { describeError } from '../../utils/errors'
import { formatSize } from '../../utils/format'

const tab = ref<'dup' | 'size'>('dup')
const scanning = ref(false)
const error = ref('')
const dupGroups = ref<DupGroup[]>([])
const sizeGroups = ref<{ size: number; photos: { path: string; size: number }[] }[]>([])
/** Group index -> index of the photo to keep. Undefined means "nothing chosen yet". */
const keepMap = ref<Record<number, number>>({})

async function scan() {
  scanning.value = true
  error.value = ''
  dupGroups.value = []
  sizeGroups.value = []
  keepMap.value = {}
  try {
    if (tab.value === 'dup') {
      dupGroups.value = (await Cleanup.scanDuplicates({ limit: 400 })).groups
    } else {
      sizeGroups.value = (await Cleanup.scanBySize({ minBytes: 1024 * 1024 })).groups
    }
  } catch (e) {
    error.value = describeError(e)
  } finally {
    scanning.value = false
  }
}

function switchTab(t: 'dup' | 'size') {
  tab.value = t
  scan()
}

/** Refuses to delete anything until a keeper is chosen, so a whole cluster can never vanish. */
function deleteExtras(gi: number) {
  const g = dupGroups.value[gi]
  const keep = keepMap.value[gi]
  if (keep === undefined) {
    uni.showToast({ title: '请先点选要保留的那一张', icon: 'none' })
    return
  }
  const victims = g.photos.filter((_, i) => i !== keep).map((p) => p.path)
  uni.showModal({
    title: '确认删除',
    content: `将永久删除 ${victims.length} 张照片文件，无法恢复。`,
    success: async (r) => {
      if (!r.confirm) return
      try {
        const res = await Fs.remove({ paths: victims })
        uni.showToast({ title: `已删除 ${res.removed.length} 张`, icon: 'none' })
        scan()
      } catch (e) {
        uni.showToast({ title: describeError(e), icon: 'none' })
      }
    }
  })
}

scan()
</script>

<style>
.page { display: flex; flex-direction: column; height: 100vh; }
.tabs { display: flex; background: #15191d; }
.tab { flex: 1; text-align: center; padding: 24rpx; font-size: 28rpx; color: #8a9199; }
.tab.on { color: #3b82f6; border-bottom: 4rpx solid #3b82f6; }
.body { flex: 1; }
.card { margin: 24rpx 32rpx; background: #191d22; border-radius: 16rpx; padding: 24rpx; }
.cardhead { display: flex; justify-content: space-between; font-size: 26rpx; margin-bottom: 20rpx; }
.keep { color: #3b82f6; font-size: 24rpx; }
.cells { display: flex; flex-wrap: wrap; }
.cell { width: 25%; height: 180rpx; padding: 4rpx; box-sizing: border-box; position: relative; }
.cell.marked { outline: 4rpx solid #22c55e; outline-offset: -4rpx; }
.sz { position: absolute; bottom: 8rpx; left: 8rpx; font-size: 18rpx; color: #ffffff; background: rgba(0, 0, 0, 0.55); padding: 2rpx 6rpx; border-radius: 4rpx; }
.danger { margin-top: 20rpx; background: #3a1d1f; color: #ef5350; font-size: 26rpx; border-radius: 12rpx; }
.empty { padding: 100rpx 40rpx; text-align: center; color: #6b7280; line-height: 1.7; }
</style>
