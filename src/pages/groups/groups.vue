<template>
  <view class="page">
    <view class="head">
      <text class="title">照片分组</text>
      <text class="newbtn" @click="createGroup">＋ 新建</text>
    </view>
    <image v-if="topCover" :src="topCover" style="width:160rpx;height:160rpx;display:block;" mode="aspectFill" />
    <scroll-view class="list" scroll-y>
      <view v-for="g in groups" :key="g.path" class="card" @click="openGroup(g)" @longpress="showActions(g)">
        <view class="cover">
          <image v-if="covers[g.path]" class="coverimg" :src="covers[g.path]" mode="aspectFill" />
          <text v-else class="dbgtext">...</text>
        </view>
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
import { ref } from 'vue'
import ActionSheet from '../../components/ActionSheet.vue'
import { Group, type GroupInfo } from '../../native/group'
import { describeError } from '../../utils/errors'
import { thumbUrl } from '../../native/media'

const groups = ref<GroupInfo[]>([])
/** Resolved thumbnail URL per group path, so the template never awaits mid-render. */
const covers = ref<Record<string, string>>({})
const topCover = ref('')
const error = ref('')
const sheet = ref(false)
const target = ref<GroupInfo | null>(null)
const items = [
  { key: 'rename', label: '重命名分组' },
  { key: 'remove', label: '删除分组', danger: true }
]

async function load() {
  error.value = ''
  try {
    groups.value = (await Group.list()).groups
    const map: Record<string, string> = {}
    for (const g of groups.value) {
      if (!g.cover) continue
      try { map[g.path] = await thumbUrl(g.cover, 320) } catch { /* fall through to the placeholder */ }
    }
    covers.value = map
    topCover.value = Object.values(map)[0] || ''
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
.card { display: flex; align-items: center; min-height: 168rpx; margin: 0 32rpx 20rpx; background: #191d22; border-radius: 16rpx; overflow: hidden; }
.cover { width: 160rpx; height: 160rpx; background: #1a1f24; flex-shrink: 0; position: relative; overflow: hidden; }
/* uni-app scopes each component's styles, so the child .thumb cannot size off this box
   unless the sizing is pushed down explicitly. */
.emptycover { display: flex; align-items: center; justify-content: center; color: #5a6169; width: 160rpx; height: 160rpx; }
.info { padding: 0 28rpx; display: flex; flex-direction: column; }
.name { font-size: 30rpx; }
.count { font-size: 24rpx; color: #7d848c; margin-top: 10rpx; }
.empty { padding: 120rpx 40rpx; text-align: center; color: #6b7280; }
.dbgtext { font-size: 22rpx; color: #5a6169; }
.coverimg { width: 160rpx; height: 160rpx; display: block; }
</style>
