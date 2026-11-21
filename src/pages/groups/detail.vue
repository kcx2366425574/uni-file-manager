<template>
  <view class="page">
    <view class="bar">
      <text class="name">{{ name }}</text>
      <text class="count">{{ photos.length }} 张</text>
    </view>
    <scroll-view class="body" scroll-y>
      <PhotoGrid :photos="photos" @open="preview" @select="toggle" />
      <view v-if="!photos.length" class="empty"><text>这个分组还没有照片。在相册里选择照片并「移入分组」。</text></view>
    </scroll-view>
    <view v-if="selected.length" class="selbar">
      <text class="selcount">已选 {{ selected.length }}</text>
      <text class="selbtn" @click="share">分享</text>
      <text class="selbtn danger" @click="removeSelected">移出分组</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad, onShow } from '@dcloudio/uni-app'
import { Capacitor } from '@capacitor/core'
import { ref } from 'vue'
import PhotoGrid from '../../components/PhotoGrid.vue'
import { Group, type GroupPhoto } from '../../native/group'
import { Fs } from '../../native/fs'
import { Share } from '../../native/share'
import { describeError } from '../../utils/errors'

const groupPath = ref('')
const name = ref('')
const photos = ref<GroupPhoto[]>([])
const selected = ref<GroupPhoto[]>([])

async function load() {
  if (!groupPath.value) return
  try {
    photos.value = (await Group.photos({ path: groupPath.value })).photos
  } catch (e) {
    uni.showToast({ title: describeError(e), icon: 'none' })
  }
}

function preview(p: GroupPhoto) {
  if (selected.value.length) return toggle(p)
  uni.previewImage({
    current: Capacitor.convertFileSrc(p.path),
    urls: photos.value.map((x) => Capacitor.convertFileSrc(x.path))
  })
}

function toggle(p: GroupPhoto) {
  const i = selected.value.findIndex((x) => x.path === p.path)
  if (i >= 0) selected.value.splice(i, 1)
  else selected.value.push(p)
}

async function share() {
  if (!selected.value.length) return
  try {
    await Share.shareFiles({ paths: selected.value.map((x) => x.path) })
  } catch (e) {
    uni.showToast({ title: describeError(e), icon: 'none' })
  }
}

/** Photos move back to the camera roll; the group folder then holds nothing. */
function removeSelected() {
  uni.showModal({
    title: '移出分组',
    content: `把选中的 ${selected.value.length} 张照片移回 DCIM/Camera？`,
    success: async (r) => {
      if (!r.confirm) return
      try {
        const res = await Fs.move({
          paths: selected.value.map((x) => x.path),
          destDir: '/storage/emulated/0/DCIM/Camera'
        })
        if (res.failed.length) throw { code: res.failed[0].code }
        selected.value = []
        load()
      } catch (e) {
        uni.showToast({ title: describeError(e), icon: 'none' })
      }
    }
  })
}

onLoad((q) => {
  groupPath.value = decodeURIComponent((q && (q.path as string)) || '')
  name.value = decodeURIComponent((q && (q.name as string)) || '')
})

onShow(load)
</script>

<style>
.page { display: flex; flex-direction: column; height: 100vh; }
.bar { display: flex; justify-content: space-between; padding: 20rpx 32rpx; background: #15191d; }
.name { font-size: 28rpx; }
.count { font-size: 24rpx; color: #7d848c; }
.body { flex: 1; }
.empty { padding: 120rpx 40rpx; text-align: center; color: #6b7280; line-height: 1.7; }
.selbar { display: flex; align-items: center; padding: 24rpx 32rpx; background: #191d22; }
.selcount { flex: 1; font-size: 26rpx; }
.selbtn { margin-left: 28rpx; color: #3b82f6; font-size: 26rpx; }
.selbtn.danger { color: #ef5350; }
</style>
