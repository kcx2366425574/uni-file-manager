<template>
  <PermGate>
    <view class="page">
      <text class="title">文件管理器</text>
      <text class="hint">权限已就绪</text>
      <button class="entry" @click="goFiles">浏览文件</button>
    </view>
  </PermGate>
</template>

<script setup lang="ts">
import PermGate from '../../components/PermGate.vue'

function goFiles() {
  uni.navigateTo({ url: '/pages/files/files' })
}
</script>

<style>
.page { padding: 48rpx; }
.title { font-size: 44rpx; font-weight: 600; }
.hint { display: block; margin-top: 16rpx; color: #8a9199; }
.entry { margin-top: 32rpx; background: #1f2429; color: #e8eaed; border-radius: 12rpx; }
</style>
