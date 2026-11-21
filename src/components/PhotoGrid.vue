<template>
  <view class="grid">
    <view v-for="p in photos" :key="p.path" class="cell" @click="$emit('open', p)" @longpress="$emit('select', p)">
      <ThumbImage :path="p.path" />
    </view>
  </view>
</template>

<script setup lang="ts">
import ThumbImage from './ThumbImage.vue'
import type { GroupPhoto } from '../native/group'

defineProps<{ photos: GroupPhoto[] }>()
defineEmits<{ (e: 'open', v: GroupPhoto): void; (e: 'select', v: GroupPhoto): void }>()
</script>

<style>
.grid { display: flex; flex-wrap: wrap; }
.cell { width: calc(100% / 3); height: 240rpx; display: inline-block; padding: 4rpx; box-sizing: border-box; position: relative; }
</style>
