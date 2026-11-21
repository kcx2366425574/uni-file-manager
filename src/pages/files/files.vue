<template>
  <view class="page">
    <view class="bar">
      <text class="crumb">{{ current }}</text>
      <text class="sortbtn" @click="cycleSort">{{ sortLabel }}</text>
    </view>
    <scroll-view class="list" scroll-y>
      <FileRow v-for="e in entries" :key="e.path" :entry="e" @open="open" @select="showActions" />
      <view v-if="!entries.length" class="empty"><text>{{ error || '空文件夹' }}</text></view>
    </scroll-view>
    <view class="fab" @click="showCreate">＋</view>
    <ActionSheet :visible="sheet" :items="sheetItems" @pick="onPick" @close="sheet = false" />
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import ActionSheet from '../../components/ActionSheet.vue'
import FileRow from '../../components/FileRow.vue'
import { Fs, type FileEntry, type SortKey } from '../../native/fs'
import { describeError } from '../../utils/errors'

const current = ref('')
const entries = ref<FileEntry[]>([])
const error = ref('')
const sort = ref<SortKey>('name')
const asc = ref(true)
const sheet = ref(false)
const target = ref<FileEntry | null>(null)

const sortLabel = computed(() => {
  const names: Record<SortKey, string> = { name: '名称', size: '大小', time: '时间', type: '类型' }
  return `${names[sort.value]}${asc.value ? '↑' : '↓'}`
})

const sheetItems = computed(() =>
  target.value
    ? [
        { key: 'rename', label: '重命名' },
        { key: 'detail', label: '详情' },
        { key: 'delete', label: '删除', danger: true }
      ]
    : []
)

async function load(path: string) {
  error.value = ''
  try {
    const r = await Fs.listDir({ path, sort: sort.value, asc: asc.value })
    current.value = r.path
    entries.value = r.entries
  } catch (e) {
    entries.value = []
    error.value = describeError(e)
  }
}

function open(e: FileEntry) {
  if (e.dir) load(e.path)
}

function showActions(e: FileEntry) {
  target.value = e
  sheet.value = true
}

/** Cycles name asc -> size -> time -> type -> name desc, so every key is reachable in one button. */
function cycleSort() {
  const order: SortKey[] = ['name', 'size', 'time', 'type']
  const i = order.indexOf(sort.value)
  if (i === order.length - 1 && !asc.value) {
    sort.value = 'name'
    asc.value = true
  } else if (i === order.length - 1) {
    asc.value = false
  } else {
    sort.value = order[i + 1]
  }
  load(current.value)
}

function showCreate() {
  uni.showModal({
    title: '新建文件夹',
    editable: true,
    placeholderText: '文件夹名称',
    success: async (r) => {
      if (!r.confirm || !r.content) return
      try {
        await Fs.mkdir({ parent: current.value, name: r.content })
        load(current.value)
      } catch (e) {
        uni.showToast({ title: describeError(e), icon: 'none' })
      }
    }
  })
}

async function onPick(key: string) {
  sheet.value = false
  const t = target.value
  if (!t) return
  if (key === 'rename') {
    uni.showModal({
      title: '重命名',
      editable: true,
      content: t.name,
      success: async (r) => {
        if (!r.confirm || !r.content || r.content === t.name) return
        try {
          await Fs.rename({ path: t.path, newName: r.content })
          load(current.value)
        } catch (e) {
          uni.showToast({ title: describeError(e), icon: 'none' })
        }
      }
    })
  } else if (key === 'detail') {
    uni.showModal({
      title: t.name,
      content: `路径：${t.path}\n大小：${t.dir ? '文件夹' : t.size + ' 字节'}\n修改：${new Date(t.mtime).toLocaleString()}`,
      showCancel: false
    })
  } else if (key === 'delete') {
    uni.showModal({
      title: '确认删除',
      content: `将永久删除「${t.name}」${t.dir ? '及其全部内容' : ''}，无法恢复。`,
      success: async (r) => {
        if (!r.confirm) return
        try {
          const res = await Fs.remove({ paths: [t.path] })
          if (res.failed.length) throw { code: res.failed[0].code }
          load(current.value)
        } catch (e) {
          uni.showToast({ title: describeError(e), icon: 'none' })
        }
      }
    })
  }
}

onLoad((q) => { load((q && (q.path as string)) || '/storage/emulated/0') })
</script>

<style>
.page { display: flex; flex-direction: column; height: 100vh; position: relative; }
.bar { display: flex; justify-content: space-between; padding: 20rpx 32rpx; background: #15191d; }
.crumb { font-size: 24rpx; color: #9aa0a6; flex: 1; overflow: hidden; white-space: nowrap; }
.sortbtn { font-size: 24rpx; color: #3b82f6; margin-left: 16rpx; }
.list { flex: 1; }
.empty { padding: 80rpx; text-align: center; color: #6b7280; }
.fab { position: fixed; right: 40rpx; bottom: 80rpx; width: 96rpx; height: 96rpx; border-radius: 50%; background: #3b82f6; color: #fff; font-size: 52rpx; display: flex; align-items: center; justify-content: center; }
</style>
