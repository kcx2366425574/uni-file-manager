<template>
  <view v-if="!state.allFiles" class="gate">
    <text class="gate-title">需要「所有文件访问」权限</text>
    <text class="gate-body">文件管理器需要读写存储中的文件（浏览、移动、重命名、分组整理照片）。请点击下方按钮，在系统设置中允许本应用访问所有文件。未授权时可以浏览相册，但不能修改文件。</text>
    <button class="gate-btn" @click="go">去授权</button>
    <text class="gate-hint">授权后返回本页会自动刷新状态</text>
  </view>
  <slot v-else />
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { Shell, type PermissionSnapshot } from '../native/shell'

const emit = defineEmits<{ (e: 'state', v: PermissionSnapshot): void }>()
const state = ref<PermissionSnapshot>({ allFiles: false, mediaImages: false, installPackages: false, sdk: 0 })

async function refresh() {
  state.value = await Shell.permissionState()
  emit('state', state.value)
}

function go() {
  Shell.openAllFilesSettings()
}

onShow(refresh)
</script>

<style>
.gate { padding: 64rpx 48rpx; display: flex; flex-direction: column; }
.gate-title { font-size: 40rpx; font-weight: 600; margin-bottom: 24rpx; }
.gate-body { color: #9aa0a6; font-size: 28rpx; line-height: 1.7; }
.gate-btn { margin-top: 48rpx; background: #3b82f6; color: #ffffff; border-radius: 12rpx; }
.gate-hint { margin-top: 24rpx; color: #6b7280; font-size: 24rpx; }
</style>
