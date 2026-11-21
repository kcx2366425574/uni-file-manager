<template>
  <image v-if="src" class="thumb" :src="src" mode="aspectFill" lazy-load />
  <view v-else class="thumb placeholder" />
</template>

<script setup lang="ts">
import { Capacitor } from '@capacitor/core'
import { ref, watch } from 'vue'

const props = withDefaults(defineProps<{ path: string; maxPx?: number }>(), { maxPx: 256 })
const src = ref('')

// Task 8 replaces this with a native-generated thumbnail; until then the WebView serves the
// original file through Capacitor's local server, which is correct but heavier.
watch(
  () => props.path,
  (p) => {
    src.value = p && Capacitor.isNativePlatform() ? Capacitor.convertFileSrc(p) : ''
  },
  { immediate: true }
)
</script>

<style>
.thumb { width: 100%; height: 100%; background: #1a1f24; border-radius: 8rpx; }
.placeholder { background: #1a1f24; }
</style>
