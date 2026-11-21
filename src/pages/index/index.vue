<template>
  <PermGate>
    <view class="page">
      <text class="title">文件管理器</text>
      <text class="hint">权限已就绪</text>
      <button class="entry" @click="goFiles">浏览文件</button>
      <button class="entry" @click="goGroups">照片分组</button>
      <button class="entry" @click="goPhotos">相册</button>
      <button class="entry" @click="goFavorites">我的收藏</button>
    </view>
  </PermGate>
</template>

<script setup lang="ts">
import PermGate from '../../components/PermGate.vue'

function goFavorites() {
  uni.navigateTo({ url: '/pages/favorites/favorites' })
}

function goPhotos() {
  uni.navigateTo({ url: '/pages/photos/photos?kind=image' })
}

function goGroups() {
  uni.navigateTo({ url: '/pages/groups/groups' })
}

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
