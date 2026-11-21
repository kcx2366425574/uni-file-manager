<template>
  <view class="dbg">
    <img v-if="url" :src="url" class="pic" @load="onLoad" @error="onErr" />
    <text class="t">{{ status }}</text>
    <text class="u">{{ url }}</text>
  </view>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { thumbUrl } from '../native/media'

const props = withDefaults(defineProps<{ path: string; maxPx?: number }>(), { maxPx: 256 })

const url = ref('')
const status = ref('waiting')

watch(
  () => props.path,
  async (p) => {
    status.value = 'resolving'
    if (!p) {
      url.value = ''
      status.value = 'no path'
      return
    }
    try {
      url.value = await thumbUrl(p, props.maxPx)
      status.value = `got url len=${url.value.length}`
    } catch (e) {
      url.value = ''
      status.value = 'error: ' + JSON.stringify(e)
    }
  },
  { immediate: true }
)

function onLoad() { status.value = 'LOADED ' + url.value.slice(-20) }
function onErr() { status.value = 'IMG ERROR ' + url.value.slice(-20) }
</script>

<style>
.dbg { width: 100%; height: 100%; background: #1a1f24; overflow: hidden; }
.pic { width: 100%; height: 100%; object-fit: cover; display: block; }
.t { font-size: 16rpx; color: #22c55e; }
.u { font-size: 12rpx; color: #6b7280; word-break: break-all; }
</style>
