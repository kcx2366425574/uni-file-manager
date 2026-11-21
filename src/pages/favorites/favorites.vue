<template>
  <view class="page">
    <view class="head">
      <text class="title">我的收藏</text>
      <text class="count">{{ items.length }} 张</text>
    </view>
    <scroll-view class="body" scroll-y>
      <view class="cells">
        <view v-for="f in items" :key="f.path" class="cell" @longpress="unfav(f)" @click="preview(f)">
          <ThumbImage :path="f.path" />
        </view>
      </view>
      <view v-if="!items.length" class="empty">
        <text>{{ error || '还没有收藏照片。在相册里长按照片即可收藏。' }}</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { Capacitor } from '@capacitor/core'
import { ref } from 'vue'
import ThumbImage from '../../components/ThumbImage.vue'
import { Fav, type FavItem } from '../../native/fav'
import { describeError } from '../../utils/errors'

const items = ref<FavItem[]>([])
const error = ref('')

async function load() {
  error.value = ''
  try {
    items.value = (await Fav.list()).items
  } catch (e) {
    items.value = []
    error.value = describeError(e)
  }
}

function preview(f: FavItem) {
  uni.previewImage({
    current: Capacitor.convertFileSrc(f.path),
    urls: items.value.map((x) => Capacitor.convertFileSrc(x.path))
  })
}

/** Removing a favourite never touches the photo file itself. */
function unfav(f: FavItem) {
  uni.showModal({
    title: '取消收藏',
    content: '仅从收藏中移除，不会删除照片文件。',
    success: async (r) => {
      if (!r.confirm) return
      await Fav.remove({ path: f.path })
      load()
    }
  })
}

onShow(load)
</script>

<style>
.page { display: flex; flex-direction: column; height: 100vh; }
.head { display: flex; justify-content: space-between; padding: 24rpx 32rpx; }
.title { font-size: 36rpx; font-weight: 600; }
.count { font-size: 24rpx; color: #7d848c; }
.body { flex: 1; }
.cells { display: flex; flex-wrap: wrap; }
.cell { width: calc(100% / 3); height: 240rpx; display: inline-block; padding: 4rpx; box-sizing: border-box; }
.empty { padding: 120rpx 40rpx; text-align: center; color: #6b7280; line-height: 1.7; }
</style>
