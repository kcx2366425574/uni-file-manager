<template>
  <view class="page">
    <view class="head">
      <text class="kind">{{ kindLabel }}</text>
      <text class="count">{{ items.length }}{{ hasMore ? '+' : '' }} 项</text>
    </view>
    <scroll-view class="grid" scroll-y @scrolltolower="loadMore">
      <view class="cells">
        <view v-for="it in items" :key="it.path" class="cell" @click="preview(it)">
          <ThumbImage :path="it.path" />
        </view>
      </view>
      <view v-if="!items.length" class="empty"><text>{{ error || '没有内容' }}</text></view>
      <view v-if="loading" class="more"><text>加载中…</text></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { Capacitor } from '@capacitor/core'
import { computed, ref } from 'vue'
import ThumbImage from '../../components/ThumbImage.vue'
import { Media, type MediaItem, type MediaKind } from '../../native/media'
import { Share } from '../../native/share'
import { describeError } from '../../utils/errors'

const kind = ref<MediaKind>('image')
const page = ref(0)
const items = ref<MediaItem[]>([])
const hasMore = ref(false)
const loading = ref(false)
const error = ref('')

const kindLabel = computed(
  () => ({ image: '图片', video: '视频', audio: '音频', doc: '文档' } as Record<MediaKind, string>)[kind.value]
)

async function loadMore() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    const r = await Media.query({ kind: kind.value, page: page.value, pageSize: 200 })
    items.value = items.value.concat(r.items)
    hasMore.value = r.hasMore
    page.value++
  } catch (e) {
    error.value = describeError(e)
  } finally {
    loading.value = false
  }
}

function preview(it: MediaItem) {
  if (!it.mime.startsWith('image/')) {
    Share.openFile({ path: it.path, mime: it.mime || '*/*' }).catch((e) =>
      uni.showToast({ title: describeError(e), icon: 'none' })
    )
    return
  }
  uni.previewImage({
    current: Capacitor.convertFileSrc(it.path),
    urls: items.value.filter((x) => x.mime.startsWith('image/')).map((x) => Capacitor.convertFileSrc(x.path))
  })
}

onLoad((q) => {
  const k = (q && (q.kind as string)) || 'image'
  kind.value = (['image', 'video', 'audio', 'doc'].includes(k) ? k : 'image') as MediaKind
  loadMore()
})
</script>

<style>
.page { display: flex; flex-direction: column; height: 100vh; }
.head { display: flex; justify-content: space-between; padding: 20rpx 32rpx; background: #15191d; }
.kind { font-size: 28rpx; }
.count { font-size: 24rpx; color: #7d848c; }
.grid { flex: 1; }
.cells { display: flex; flex-wrap: wrap; }
.cell { width: calc(100% / 3); height: 240rpx; display: inline-block; padding: 4rpx; box-sizing: border-box; position: relative; }
.empty { padding: 120rpx 40rpx; text-align: center; color: #6b7280; }
.more { padding: 32rpx; text-align: center; color: #6b7280; font-size: 24rpx; }
</style>
