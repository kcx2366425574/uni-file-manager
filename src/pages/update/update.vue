<template>
  <view class="page">
    <view class="box">
      <text class="title">{{ title }}</text>
      <text v-if="notes" class="notes">{{ notes }}</text>
      <button v-if="canDownload" class="btn primary" @click="startDownload">
        {{ busy ? '下载中…' : '下载并安装' }}
      </button>
      <button v-if="installedPath" class="btn primary" @click="installNow">安装</button>
      <button class="btn" @click="check">重新检查</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { Shell } from '../../native/shell'
import { Update, UPDATE_REPO } from '../../native/update'
import { describeError } from '../../utils/errors'

const title = ref('正在检查更新…')
const notes = ref('')
const apkUrl = ref('')
const installedPath = ref('')
const busy = ref(false)

const canDownload = computed(() => !!apkUrl.value && !installedPath.value)

async function check() {
  title.value = '正在检查更新…'
  notes.value = ''
  apkUrl.value = ''
  installedPath.value = ''
  try {
    const r = await Update.check(UPDATE_REPO)
    if (!r.available) {
      title.value = `已是最新版本（${r.versionName}）`
      return
    }
    title.value = `发现新版本 ${r.versionName}`
    notes.value = r.notes
    apkUrl.value = r.apkUrl
  } catch (e) {
    title.value = describeError(e)
  }
}

async function startDownload() {
  busy.value = true
  try {
    const perm = await Shell.permissionState()
    if (!perm.installPackages) {
      uni.showModal({
        title: '需要安装权限',
        content: '请先在设置中允许本应用安装未知来源应用，然后返回重试。',
        confirmText: '去设置',
        success: (r) => {
          if (r.confirm) Shell.openInstallSettings()
        }
      })
      busy.value = false
      return
    }
    const r = await Update.download({ apkUrl: apkUrl.value, fileName: `ufm-${Date.now()}.apk` })
    installedPath.value = r.path
    busy.value = false
    installNow()
  } catch (e) {
    busy.value = false
    uni.showToast({ title: describeError(e), icon: 'none' })
  }
}

function installNow() {
  Update.install({ path: installedPath.value }).catch((e) =>
    uni.showToast({ title: describeError(e), icon: 'none' })
  )
}

onLoad(check)
</script>

<style>
.page { padding: 48rpx 32rpx; }
.box { background: #191d22; border-radius: 16rpx; padding: 36rpx; }
.title { font-size: 34rpx; font-weight: 600; }
.notes { display: block; margin-top: 24rpx; color: #b6bcc2; font-size: 26rpx; line-height: 1.7; white-space: pre-wrap; }
.btn { margin-top: 28rpx; background: #23282e; color: #e8eaed; border-radius: 12rpx; font-size: 28rpx; }
.btn.primary { background: #3b82f6; color: #ffffff; }
</style>
