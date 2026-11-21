<template>
  <view class="page">
    <view class="section">
      <text class="stitle">权限</text>
      <view class="row">
        <text>所有文件访问</text>
        <text :class="['val', snap.allFiles ? 'ok' : 'bad']">{{ snap.allFiles ? '已开启' : '未开启' }}</text>
      </view>
      <view class="row">
        <text>相册读取</text>
        <text :class="['val', snap.mediaImages ? 'ok' : 'bad']">{{ snap.mediaImages ? '已开启' : '未开启' }}</text>
      </view>
      <view class="row">
        <text>安装未知应用</text>
        <text :class="['val', snap.installPackages ? 'ok' : 'bad']">{{ snap.installPackages ? '已开启' : '未开启' }}</text>
      </view>
      <button class="btn" @click="openAllFiles">打开所有文件访问设置</button>
      <button class="btn" @click="openInstall">打开安装权限设置</button>
    </view>

    <view class="section">
      <text class="stitle">存储</text>
      <view class="row"><text>已用 / 总量</text><text class="val">{{ used }} / {{ total }}</text></view>
      <view v-for="v in stats.volumes" :key="v.label" class="row sub">
        <text>{{ v.label }}</text><text class="val">{{ formatSize(v.freeBytes) }} 可用</text>
      </view>
    </view>

    <view class="section">
      <text class="stitle">关于</text>
      <view class="row"><text>版本</text><text class="val">{{ version }}</text></view>
      <button class="btn primary" @click="checkUpdate">检查更新</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { Shell, type PermissionSnapshot, type StorageStats } from '../../native/shell'
import { Update } from '../../native/update'
import { formatSize } from '../../utils/format'

const snap = ref<PermissionSnapshot>({ allFiles: false, mediaImages: false, installPackages: false, sdk: 0 })
const stats = ref<StorageStats>({ totalBytes: 0, freeBytes: 0, usedBytes: 0, volumes: [] })
const version = ref('—')
const used = ref('—')
const total = ref('—')

async function refresh() {
  try {
    snap.value = await Shell.permissionState()
    const s = await Shell.storageStats()
    stats.value = s
    used.value = formatSize(s.usedBytes)
    total.value = formatSize(s.totalBytes)
    const v = await Update.currentVersion()
    version.value = v.versionName + ' (' + v.versionCode + ')'
  } catch {
    // Permission and storage values are best effort; the page must still render.
  }
}

function openAllFiles() {
  Shell.openAllFilesSettings()
}

function openInstall() {
  Shell.openInstallSettings()
}

function checkUpdate() {
  uni.navigateTo({ url: '/pages/update/update' })
}

onShow(refresh)
</script>

<style>
.page { padding: 32rpx; }
.section { margin-bottom: 48rpx; }
.stitle { display: block; font-size: 24rpx; color: #6b7280; margin-bottom: 16rpx; }
.row { display: flex; justify-content: space-between; padding: 24rpx; background: #191d22; border-radius: 12rpx; margin-bottom: 12rpx; font-size: 28rpx; }
.row.sub { background: #15191d; font-size: 24rpx; color: #9aa0a6; }
.val { color: #9aa0a6; }
.val.ok { color: #22c55e; }
.val.bad { color: #ef5350; }
.btn { margin-top: 16rpx; background: #23282e; color: #e8eaed; font-size: 26rpx; border-radius: 12rpx; }
.btn.primary { background: #3b82f6; color: #ffffff; }
</style>
