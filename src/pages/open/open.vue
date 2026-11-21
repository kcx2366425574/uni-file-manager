<template>
  <view class="page">
    <view class="card">
      <text class="title">{{ title }}</text>
      <text class="name">{{ name }}</text>
      <text class="meta">{{ sizeText }}</text>
      <text v-if="error" class="err">{{ error }}</text>
      <button v-if="isApk" class="btn primary" @click="install">安装</button>
      <button v-if="isImage" class="btn primary" @click="preview">预览</button>
      <button v-if="!isApk && !isImage" class="btn primary" @click="openWith">用其它应用打开</button>
      <button class="btn" @click="back">返回文件管理器</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { Capacitor } from '@capacitor/core'
import { onLoad } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { Open } from '../../native/open'
import { Share } from '../../native/share'
import { describeError } from '../../utils/errors'
import { formatSize } from '../../utils/format'

const name = ref('')
const path = ref('')
const mime = ref('')
const size = ref(0)
const error = ref('')
const title = ref('正在读取文件…')

const isApk = computed(() => mime.value === 'application/vnd.android.package-archive' || name.value.toLowerCase().endsWith('.apk'))
const isImage = computed(() => mime.value.startsWith('image/'))
const sizeText = computed(() => (size.value ? formatSize(size.value) : ''))

async function load() {
  try {
    const f = await Open.takePending()
    if (!f.path) {
      title.value = '没有待处理的文件'
      return
    }
    name.value = f.name || ''
    path.value = f.path
    mime.value = f.mime || ''
    size.value = f.size || 0
    title.value = '收到一个文件'
  } catch (e) {
    title.value = '读取失败'
    error.value = describeError(e)
  }
}

function install() {
  Open.install({ path: path.value }).catch((e) =>
    uni.showToast({ title: describeError(e), icon: 'none' })
  )
}

function preview() {
  uni.previewImage({ current: Capacitor.convertFileSrc(path.value), urls: [Capacitor.convertFileSrc(path.value)] })
}

function openWith() {
  Share.openFile({ path: path.value, mime: mime.value || '*/*' }).catch((e) =>
    uni.showToast({ title: describeError(e), icon: 'none' })
  )
}

function back() {
  uni.reLaunch({ url: '/pages/index/index' })
}

onLoad(load)
</script>

<style>
.page { padding: 48rpx 32rpx; }
.card { background: #191d22; border-radius: 16rpx; padding: 36rpx; display: flex; flex-direction: column; }
.title { font-size: 34rpx; font-weight: 600; }
.name { margin-top: 24rpx; font-size: 30rpx; word-break: break-all; }
.meta { margin-top: 12rpx; color: #7d848c; font-size: 24rpx; }
.err { margin-top: 16rpx; color: #ef5350; font-size: 26rpx; }
.btn { margin-top: 28rpx; background: #23282e; color: #e8eaed; border-radius: 12rpx; font-size: 28rpx; }
.btn.primary { background: #3b82f6; color: #ffffff; }
</style>
