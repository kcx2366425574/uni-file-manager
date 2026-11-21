<template>
  <view v-if="visible" class="mask" @click="$emit('close')">
    <view class="sheet" @click.stop>
      <view
        v-for="it in items"
        :key="it.key"
        class="item"
        :class="{ danger: it.danger }"
        @click="$emit('pick', it.key)"
      >{{ it.label }}</view>
      <view class="item cancel" @click="$emit('close')">取消</view>
    </view>
  </view>
</template>

<script setup lang="ts">
defineProps<{
  visible: boolean
  items: { key: string; label: string; danger?: boolean }[]
}>()
defineEmits<{ (e: 'pick', key: string): void; (e: 'close'): void }>()
</script>

<style>
.mask { position: fixed; left: 0; top: 0; right: 0; bottom: 0; background: rgba(0, 0, 0, 0.5); display: flex; align-items: flex-end; z-index: 10; }
.sheet { width: 100%; background: #191d22; border-radius: 20rpx 20rpx 0 0; padding-bottom: 24rpx; }
.item { padding: 32rpx; text-align: center; font-size: 30rpx; border-bottom: 1rpx solid #23282e; }
.item.danger { color: #ef5350; }
.item.cancel { color: #9aa0a6; border-bottom: none; }
</style>
