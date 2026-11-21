<template>
  <view class="page">
    <view class="bar">
      <text class="crumb">{{ current }}</text>
      <text class="count">{{ entries.length }} 项</text>
    </view>
    <scroll-view class="list" scroll-y>
      <FileRow v-for="e in entries" :key="e.path" :entry="e" @open="open" @select="onSelect" />
      <view v-if="!entries.length" class="empty"><text>{{ error || '空文件夹' }}</text></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import FileRow from '../../components/FileRow.vue'
import { Fs, type FileEntry } from '../../native/fs'
import { describeError } from '../../utils/errors'

const current = ref('')
const entries = ref<FileEntry[]>([])
const error = ref('')

async function load(path: string) {
  error.value = ''
  try {
    const r = await Fs.listDir({ path, sort: 'name', asc: true })
    current.value = r.path
    entries.value = r.entries
  } catch (e) {
    entries.value = []
    error.value = describeError(e)
  }
}

function open(e: FileEntry) {
  if (e.dir) load(e.path)
}

function onSelect(e: FileEntry) {
  uni.showToast({ title: e.name, icon: 'none' })
}

onLoad((q) => { load((q && (q.path as string)) || '/storage/emulated/0') })
</script>

<style>
.page { display: flex; flex-direction: column; height: 100vh; }
.bar { display: flex; justify-content: space-between; padding: 20rpx 32rpx; background: #15191d; }
.crumb { font-size: 24rpx; color: #9aa0a6; flex: 1; overflow: hidden; white-space: nowrap; }
.count { font-size: 24rpx; color: #6b7280; margin-left: 16rpx; }
.list { flex: 1; }
.empty { padding: 80rpx; text-align: center; color: #6b7280; }
</style>
