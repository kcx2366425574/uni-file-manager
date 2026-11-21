<template>
  <!--
    uni-app's <image> component rewrites a https://localhost/_capacitor_file_/... src into a
    root-relative path (/cache/thumbs/...), which no longer reaches Capacitor's local server.
    Verified over CDP: the DOM img ended up with src="/cache/thumbs/x.jpg" while the original
    URL loaded fine (naturalWidth>0). So the src attribute is assigned through a plain DOM
    lookup after mount instead of through a template binding.
  -->
  <view class="box">
    <img :id="domId" class="pic" alt="" />
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { thumbUrl } from '../native/media'

const props = withDefaults(defineProps<{ path: string; maxPx?: number }>(), { maxPx: 256 })

const domId = `thumb-${Math.random().toString(36).slice(2, 10)}`

async function paint(p: string) {
  const el = document.getElementById(domId) as HTMLImageElement | null
  if (!el) return
  if (!p) {
    el.removeAttribute('src')
    return
  }
  try {
    el.src = await thumbUrl(p, props.maxPx)
  } catch {
    el.removeAttribute('src')
  }
}

onMounted(() => paint(props.path))
watch(() => props.path, (p) => paint(p))
</script>

<style>
.box { width: 100%; height: 100%; background: #1a1f24; border-radius: 4px; overflow: hidden; }
.pic { width: 100%; height: 100%; object-fit: cover; display: block; }
</style>
