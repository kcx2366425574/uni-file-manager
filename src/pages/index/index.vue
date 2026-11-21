<template>
  <PermGate>
    <view class="page">
      <text class="title">文件管理器</text>
      <text class="hint">权限已就绪</text>
    </view>
  </PermGate>
</template>

<script setup lang="ts">
import PermGate from '../../components/PermGate.vue'
</script>

<style>
.page { padding: 48rpx; }
.title { font-size: 44rpx; font-weight: 600; }
.hint { display: block; margin-top: 16rpx; color: #8a9199; }
</style>
