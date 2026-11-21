<template>
  <view class="page">
    <view class="head">
      <text class="kind">{{ kindLabel }}</text>
      <text v-if="selected.length" class="count">已选 {{ selected.length }}</text>
      <text v-else class="count">{{ items.length }}{{ hasMore ? '+' : '' }} 项</text>
    </view>
    <scroll-view class="grid" scroll-y @scrolltolower="loadMore">
      <view class="cells">
        <view
          v-for="it in items"
          :key="it.path"
          class="cell"
          :class="{ picked: isSelected(it) }"
          @click="tap(it)"
          @longpress="onLongPress(it)"
        >
          <ThumbImage :path="it.path" />
          <text v-if="isSelected(it)" class="mark">✓</text>
        </view>
      </view>
      <view v-if="!items.length" class="empty"><text>{{ error || '没有内容' }}</text></view>
      <view v-if="loading" class="more"><text>加载中…</text></view>
    </scroll-view>
    <view v-if="selected.length" class="selbar">
      <text class="selbtn" @click="shareSelected">分享</text>
      <text class="selbtn" @click="moveToGroup">移入分组</text>
      <text class="selbtn" @click="selected = []">取消</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { Capacitor } from '@capacitor/core'
import { computed, ref } from 'vue'
import ThumbImage from '../../components/ThumbImage.vue'
import { Media, type MediaItem, type MediaKind } from '../../native/media'
import { Group } from '../../native/group'
import { Fs } from '../../native/fs'
import { Share } from '../../native/share'
import { Fav } from '../../native/fav'
import { describeError } from '../../utils/errors'

const kind = ref<MediaKind>('image')
const page = ref(0)
const items = ref<MediaItem[]>([])
const hasMore = ref(false)
const loading = ref(false)
const error = ref('')
const selected = ref<MediaItem[]>([])

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

function isSelected(it: MediaItem) {
  return selected.value.some((x) => x.path === it.path)
}

function toggle(it: MediaItem) {
  const i = selected.value.findIndex((x) => x.path === it.path)
  if (i >= 0) selected.value.splice(i, 1)
  else selected.value.push(it)
}

/** While a selection exists a tap toggles that item; otherwise a tap opens the viewer. */
/**
 * Long-press opens a small menu rather than jumping straight into selection, so the favourite
 * action is reachable with one gesture on any photo.
 */
function onLongPress(it: MediaItem) {
  if (selected.value.length) return toggle(it)
  uni.showActionSheet({
    itemList: ['收藏', '选择'],
    success: (r) => {
      if (r.tapIndex === 0) {
        Fav.add({ path: it.path, mediaId: it.mediaId })
          .then(() => uni.showToast({ title: '已收藏', icon: 'none' }))
          .catch((e) => uni.showToast({ title: describeError(e), icon: 'none' }))
      } else {
        toggle(it)
      }
    }
  })
}

function tap(it: MediaItem) {
  if (selected.value.length) return toggle(it)
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

async function shareSelected() {
  try {
    await Share.shareFiles({ paths: selected.value.map((x) => x.path) })
  } catch (e) {
    uni.showToast({ title: describeError(e), icon: 'none' })
  }
}

/** Offers the existing groups plus an inline create, so the flow never dead-ends. */
async function moveToGroup() {
  try {
    const { groups } = await Group.list()
    const names = groups.map((g) => g.name)
    uni.showActionSheet({
      itemList: names.length ? [...names, '＋ 新建分组'] : ['＋ 新建分组'],
      success: (r) => {
        if (r.tapIndex === names.length) createAndMove()
        else doMove(groups[r.tapIndex].path)
      }
    })
  } catch (e) {
    uni.showToast({ title: describeError(e), icon: 'none' })
  }
}

function createAndMove() {
  uni.showModal({
    title: '新建分组',
    editable: true,
    placeholderText: '分组名称',
    success: async (r) => {
      if (!r.confirm || !r.content) return
      try {
        const g = await Group.create({ name: r.content })
        doMove(g.path)
      } catch (e) {
        uni.showToast({ title: describeError(e), icon: 'none' })
      }
    }
  })
}

async function doMove(destDir: string) {
  const paths = selected.value.map((x) => x.path)
  uni.showLoading({ title: '正在移动' })
  try {
    const res = await Fs.move({ paths, destDir })
    uni.hideLoading()
    const ok = (res.moved || []).length
    if (res.failed.length) {
      uni.showModal({
        title: '部分照片未能移动',
        content:
          `成功 ${ok} 张，失败 ${res.failed.length} 张（${describeError({ code: res.failed[0].code })}）。失败的照片仍在原位置。`,
        showCancel: false
      })
    } else {
      uni.showToast({ title: `已移入 ${ok} 张`, icon: 'none' })
    }
    selected.value = []
    // Reload so the moved photos disappear from the camera-roll listing.
    items.value = []
    page.value = 0
    loadMore()
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: describeError(e), icon: 'none' })
  }
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
.cell.picked { outline: 4rpx solid #3b82f6; outline-offset: -4rpx; }
.mark { position: absolute; right: 12rpx; top: 12rpx; width: 40rpx; height: 40rpx; line-height: 40rpx; text-align: center; border-radius: 50%; background: #3b82f6; color: #ffffff; font-size: 24rpx; }
.empty { padding: 120rpx 40rpx; text-align: center; color: #6b7280; }
.more { padding: 32rpx; text-align: center; color: #6b7280; font-size: 24rpx; }
.selbar { display: flex; justify-content: space-around; padding: 24rpx; background: #191d22; }
.selbtn { color: #3b82f6; font-size: 28rpx; }
</style>
