<template>
  <view class="page">
    <view class="head">
      <text class="title">照片分组</text>
      <text class="newbtn" @click="createGroup">＋ 新建</text>
    </view>
    <scroll-view class="list" scroll-y>
      <view v-for="g in groups" :key="g.path" class="card" @click="openGroup(g)" @longpress="showActions(g)">
        <image v-if="g.cover" class="cover" :src="coverOf(g.cover)" mode="aspectFill" />
        <view v-else class="cover emptycover"><text>空</text></view>
        <view class="info">
          <text class="name">{{ g.name }}</text>
          <text class="count">{{ g.count }} 张</text>
        </view>
      </view>
      <view v-if="!groups.length" class="empty"><text>{{ error || '还没有分组，点右上角新建' }}</text></view>
    </scroll-view>
    <ActionSheet :visible="sheet" :items="items" @pick="onPick" @close="sheet = false" />
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { Capacitor } from '@capacitor/core'
import { ref } from 'vue'
import ActionSheet from '../../components/ActionSheet.vue'
import { Group, type GroupInfo } from '../../native/group'
import { describeError } from '../../utils/errors'

const groups = ref<GroupInfo[]>([])
const error = ref('')
const sheet = ref(false)
const target = ref<GroupInfo | null>(null)
const items = [
  { key: 'rename', label: '重命名分组' },
  { key: 'remove', label: '删除分组', danger: true }
]

function coverOf(p: string) {
  return Capacitor.isNativePlatform() ? Capacitor.convertFileSrc(p) : ''
}

async function load() {
  error.value = ''
  try {
    groups.value = (await Group.list()).groups
  } catch (e) {
    groups.value = []
    error.value = describeError(e)
  }
}

function createGroup() {
  uni.showModal({
    title: '新建分组',
    editable: true,
    placeholderText: '分组名称',
    success: async (r) => {
      if (!r.confirm || !r.content) return
      try {
        await Group.create({ name: r.content })
        load()
      } catch (e) {
        uni.showToast({ title: describeError(e), icon: 'none' })
      }
    }
  })
}

function openGroup(g: GroupInfo) {
  uni.navigateTo({
    url: `/pages/groups/detail?path=${encodeURIComponent(g.path)}&name=${encodeURIComponent(g.name)}`
  })
}

function showActions(g: GroupInfo) {
  target.value = g
  sheet.value = true
}

async function onPick(key: string) {
  sheet.value = false
  const t = target.value
  if (!t) return
  if (key === 'rename') {
    uni.showModal({
      title: '重命名分组',
      editable: true,
      content: t.name,
      success: async (r) => {
        if (!r.confirm || !r.content || r.content === t.name) return
        try {
          await Group.rename({ path: t.path, newName: r.content })
          load()
        } catch (e) {
          uni.showToast({ title: describeError(e), icon: 'none' })
        }
      }
    })
  } else if (key === 'remove') {
    uni.showModal({
      title: '删除分组',
      content: `删除分组「${t.name}」？分组内还有文件时不允许删除。`,
      success: async (r) => {
        if (!r.confirm) return
        try {
          await Group.remove({ path: t.path })
          load()
        } catch (e) {
          uni.showToast({ title: describeError(e), icon: 'none' })
        }
      }
    })
  }
}

onShow(load)
</script>

<style>
.page { display: flex; flex-direction: column; height: 100vh; }
.head { display: flex; justify-content: space-between; align-items: center; padding: 28rpx 32rpx; }
.title { font-size: 38rpx; font-weight: 600; }
.newbtn { color: #3b82f6; font-size: 28rpx; }
.list { flex: 1; }
.card { display: flex; align-items: center; margin: 0 32rpx 20rpx; background: #191d22; border-radius: 16rpx; overflow: hidden; }
.cover { width: 160rpx; height: 160rpx; background: #1a1f24; }
.emptycover { display: flex; align-items: center; justify-content: center; color: #5a6169; }
.info { padding: 0 28rpx; display: flex; flex-direction: column; }
.name { font-size: 30rpx; }
.count { font-size: 24rpx; color: #7d848c; margin-top: 10rpx; }
.empty { padding: 120rpx 40rpx; text-align: center; color: #6b7280; }
</style>
