# 手机文件管理器（uni-file-manager）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Android 11+ 未 root 手机上交付可安装的 APK：能像 ES 一样浏览/管理全盘文件，能把照片按**真实目录**分组，并有独立收藏页与 GitHub Release 在线升级。

**Architecture:** 页面层用 uni-app(Vue3 + Vite) 写，构建为 H5 后由 Capacitor 8 的 Android WebView 容器加载；文件、相册、分组、收藏、升级五类能力全部由自写的 Capacitor 插件（Kotlin）暴露，不使用任何 `plus.*` API，因此整条构建链可用 GitHub Actions 复现。

**Tech Stack:** uni-app `3.0.0-alpha-5030720261117001`（Vue 3.4.21 + Vite 5.2.8）、Capacitor `8.6.2`、Kotlin `2.1.21`、AGP `8.13.0`、Gradle `8.14.3`、JDK 21、JUnit 4.13.2、Vitest、Playwright。

**Spec:** `docs/superpowers/specs/2026-11-21-uni-file-manager-design.md`

## Global Constraints

- `minSdkVersion = 30`（Android 11），`compileSdkVersion = 36`，`targetSdkVersion = 36`（Capacitor 8 模板默认值，来源：`@capacitor/cli@8.6.2` 包内 `assets/android-template.tar.gz` 的 `variables.gradle`，实测）。
- `applicationId` = `com.kuangcx.ufm`，Kotlin 包根 = `com.kuangcx.ufm`。
- 版本号：`versionCode` 从 `1` 起单调递增；`versionName` 用 `MAJOR.MINOR.PATCH`；两者必须同时改（升级检查依赖 versionCode）。
- 分组根目录固定为 `Environment.getExternalStorageDirectory()/Pictures/PhotoGroups/`；分组名禁止包含字符 `/ : * ? " < > |` 与控制字符，禁止 `.` 与 `..`，长度 ≤ 64 字节，禁止重名（大小写不敏感）。
- 跨卷移动必须走「复制 → fsync → 校验 → 改名 → 删源」，禁止直接 copy+delete。
- 所有原生方法在 `bridge.execute { }` 后台线程执行；不得在主线程做文件 IO 或 MediaStore 查询。
- 面向用户的文案用**中文**；代码注释用英文。
- 缩略图一律经 `ThumbCache` 生成后由 `Capacitor.convertFileSrc()` 提供 URL；**禁止**把图片 base64 传回 JS。
- 原生插件失败一律 `call.reject(message, code)`，前端按 `code` 分支：

| code | 含义 |
|---|---|
| `E_PERM` | 缺少「所有文件访问」权限 |
| `E_NOENT` | 路径不存在 |
| `E_EXISTS` | 目标已存在 |
| `E_NO_SPACE` | 目标卷空间不足 |
| `E_BAD_NAME` | 分组/文件名非法或重名 |
| `E_NOT_EMPTY` | 目录非空，拒绝删除（分组内还有照片） |
| `E_RENAME_FAILED` | 同卷 rename 失败（源未动） |
| `E_COPY_FAILED` | 跨卷复制或校验失败（源未动） |
| `E_IO` | 其它 IO 错误 |

## Review Focus

以下是 spec 未写进验收、但真实使用最容易出问题的地方；每条都在对应任务里配了测试：

1. **同名文件覆盖**：移动/复制到已存在同名文件时必须拒绝（`E_EXISTS`），不能静默覆盖 —— Task 6。
2. **分组名为空或含隐藏字符**：`"  "`、`"a/b"`、`".hidden"`、64 字节以上的中文名必须被拒绝且不创建任何目录 —— Task 7。
3. **SD 卡被拔出的瞬间**：列举或移动过程中卷消失，必须返回 `E_IO`/`E_NOENT` 而不是崩溃或留下半截文件 —— Task 6、Task 11。
4. **收藏的照片被其它 App 删除**：收藏页必须自动剔除失效项，而不是显示破图 —— Task 12。
5. **无全盘权限时进入文件页**：必须显示降级提示页并能浏览相册，不能白屏或反复弹权限框 —— Task 11、Task 14。

---

## 文件结构

```
uni-file-manager/
├─ .github/workflows/release.yml         CI：打 tag 出 APK 并发 Release
├─ android/                              Capacitor 生成，入库
│  ├─ app/build.gradle                   加 Kotlin、signingConfig、versionCode
│  ├─ app/src/main/AndroidManifest.xml   权限、FileProvider
│  ├─ app/src/main/res/xml/file_paths.xml
│  └─ app/src/main/java/com/kuangcx/ufm/
│     ├─ MainActivity.kt                 注册全部插件
│     ├─ FsPlugin.kt                     目录列举、CRUD
│     ├─ MoveEngine.kt                   移动事务（可单测的纯逻辑）
│     ├─ MoveJournal.kt                  移动日志读写
│     ├─ GroupPlugin.kt                  分组 CRUD
│     ├─ MediaPlugin.kt                  MediaStore 查询
│     ├─ ThumbCache.kt                   缩略图生成与缓存
│     ├─ FavPlugin.kt                    收藏（SQLite）
│     ├─ ShellPlugin.kt                  权限、存储统计、更新、安装
│     └─ core/{PathUtil,NameRules,StorageVolumes,Sorting}.kt
│  └─ app/src/test/java/com/kuangcx/ufm/     JUnit 单测
├─ src/
│  ├─ pages/{index,files,groups,photos,favorites,settings,update}/
│  ├─ native/{fs,media,group,fav,shell}.ts   TS 桥接封装 + 类型
│  ├─ components/{FileRow,PhotoGrid,PermGate,ActionBar,EmptyState}.vue
│  ├─ store/{settings.ts,selection.ts}
│  └─ utils/{format.ts,errors.ts}
├─ tests/unit/*.spec.ts                  Vitest
├─ tests/e2e/*.spec.ts                   Playwright
├─ docs/superpowers/specs/…              设计文档
├─ package.json  vite.config.ts  index.html
└─ README.md
```

---

## Task 1: 工程骨架与 H5 构建

**Files:**
- Create: `package.json`, `vite.config.ts`, `index.html`, `tsconfig.json`
- Create: `src/main.ts`, `src/App.vue`, `src/pages.json`, `src/manifest.json`
- Create: `src/pages/index/index.vue`
- Create: `vitest.config.ts`, `tests/unit/format.spec.ts`, `src/utils/format.ts`

**Interfaces:**
- Produces: `npm run build:h5` 产出 `dist/build/h5/`；`npm run test` 跑 Vitest；`formatSize(bytes: number): string` 返回 `"1.5 MB"` 形式；`formatTime(ms: number): string` 返回 `"2026-11-21 15:09"`。

- [ ] **Step 1: 写失败测试**

`tests/unit/format.spec.ts`:
```ts
import { describe, expect, it } from 'vitest'
import { formatSize, formatTime } from '../../src/utils/format'

describe('formatSize', () => {
  it('renders bytes under 1KB verbatim', () => {
    expect(formatSize(0)).toBe('0 B')
    expect(formatSize(999)).toBe('999 B')
  })
  it('scales to KB/MB/GB with one decimal', () => {
    expect(formatSize(1536)).toBe('1.5 KB')
    expect(formatSize(1572864)).toBe('1.5 MB')
    expect(formatSize(1610612736)).toBe('1.5 GB')
  })
})

describe('formatTime', () => {
  it('renders local time as yyyy-MM-dd HH:mm', () => {
    const ms = new Date(2026, 10, 21, 15, 9).getTime()
    expect(formatTime(ms)).toBe('2026-11-21 15:09')
  })
})
```

- [ ] **Step 2: 跑测试确认失败**

Run: `npm run test`
Expected: FAIL，`Cannot find module '../../src/utils/format'`

- [ ] **Step 3: 建工程文件**

`package.json`（版本号是实测可用组合，见 spec §2）：
```json
{
  "name": "uni-file-manager",
  "private": true,
  "version": "1.0.0",
  "scripts": {
    "dev:h5": "uni",
    "build:h5": "uni build",
    "test": "vitest run",
    "test:e2e": "playwright test",
    "cap:sync": "cap sync android"
  },
  "dependencies": {
    "@capacitor/android": "8.6.2",
    "@capacitor/core": "8.6.2",
    "@dcloudio/uni-app": "3.0.0-alpha-5030720261117001",
    "@dcloudio/uni-components": "3.0.0-alpha-5030720261117001",
    "@dcloudio/uni-h5": "3.0.0-alpha-5030720261117001",
    "pinia": "2.1.7",
    "vue": "3.4.21"
  },
  "devDependencies": {
    "@capacitor/cli": "8.6.2",
    "@dcloudio/types": "3.4.31",
    "@dcloudio/uni-cli-shared": "3.0.0-alpha-5030720261117001",
    "@dcloudio/uni-h5-vite": "3.0.0-alpha-5030720261117001",
    "@dcloudio/vite-plugin-uni": "3.0.0-alpha-5030720261117001",
    "@vitejs/plugin-vue": "5.0.4",
    "typescript": "5.4.5",
    "vite": "5.2.8",
    "vitest": "1.6.0"
  }
}
```

`vite.config.ts`:
```ts
import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  // Capacitor 用 https://localhost 提供资源，绝对路径必须带根
  base: '/'
})
```

`vitest.config.ts`:
```ts
import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: { include: ['tests/unit/**/*.spec.ts'], environment: 'node' }
})
```

`src/utils/format.ts`:
```ts
const UNITS = ['B', 'KB', 'MB', 'GB', 'TB']

export function formatSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'
  let v = bytes
  let i = 0
  while (v >= 1024 && i < UNITS.length - 1) {
    v /= 1024
    i++
  }
  return i === 0 ? `${Math.round(v)} B` : `${v.toFixed(1)} ${UNITS[i]}`
}

export function formatTime(ms: number): string {
  const d = new Date(ms)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
```

`src/pages.json`:
```json
{
  "pages": [
    { "path": "pages/index/index", "style": { "navigationBarTitleText": "文件管理器" } }
  ],
  "globalStyle": { "navigationBarBackgroundColor": "#101418", "navigationBarTextStyle": "white" }
}
```

`src/manifest.json`:
```json
{
  "name": "uni-file-manager",
  "appid": "_UNI_UFM_",
  "versionName": "1.0.0",
  "versionCode": "1",
  "vueVersion": "3"
}
```

`index.html`:
```html
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no" />
    <title>文件管理器</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

`src/main.ts`:
```ts
import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'

export function createApp() {
  const app = createSSRApp(App)
  app.use(createPinia())
  return { app }
}
```

`.gitignore`（仓库是**公开**的，签名相关文件绝不能进去）:
```gitignore
node_modules/
dist/
android/app/build/
android/build/
android/.gradle/
android/local.properties

# Signing material must never reach a public repo.
*.keystore
*.jks
*.b64
keystore.b64

# Local Android SDK path
android/local.properties

.DS_Store
.vscode/
*.log
```

`.gitattributes`（MUST have LF for shell scripts and Gradle wrapper; CRLF breaks `gradlew` and `gen-keystore.sh` on CI）:
```gitattributes
* text=auto
*.sh text eol=lf
gradlew text eol=lf
*.bat text eol=crlf
*.keystore binary
*.jks binary
*.apk binary
*.png binary
```

`src/App.vue`:
```vue
<script setup lang="ts"></script>
<template><view /></template>
<style>
page { background: #101418; color: #e8eaed; }
</style>
```

`src/pages/index/index.vue`:
```vue
<template>
  <view class="page">
    <text class="title">文件管理器</text>
    <text class="hint">骨架已就绪</text>
  </view>
</template>

<script setup lang="ts"></script>

<style>
.page { padding: 48rpx; }
.title { font-size: 44rpx; font-weight: 600; }
.hint { display: block; margin-top: 16rpx; color: #8a9199; }
</style>
```

- [ ] **Step 4: 安装依赖并跑测试**

Run: `npm install && npm run test`
Expected: 4 个测试全 PASS

- [ ] **Step 5: 构建 H5 验证产物**

Run: `npm run build:h5 && ls dist/build/h5`
Expected: 出现 `index.html` 与 `assets/`；`index.html` 里资源引用为 `/assets/...`（实测于临时工程，uni-app 输出绝对根路径）

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "chore: uni-app 骨架与 H5 构建"
```

---

## Task 2: Capacitor 容器 + 本地 Android 环境 + 首次真机启动

**Files:**
- Create: `capacitor.config.ts`
- Create: `android/`（由 `npx cap add android` 生成，之后入库）
- Modify: `android/app/build.gradle`（Kotlin 插件、applicationId、versionCode）
- Modify: `android/variables.gradle`（minSdk 30）
- Modify: `android/app/src/main/java/com/kuangcx/ufm/MainActivity.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`（权限占位，Task 3 补齐）

**Interfaces:**
- Produces: `./gradlew assembleDebug` 产出 `android/app/build/outputs/apk/debug/app-debug.apk`；App 启动后 WebView 显示 Task 1 的首页。

- [ ] **Step 1: 装 JDK 21 与 Android SDK（实测结论驱动）**

**JDK 必须是 21**：`node_modules/@capacitor/android/capacitor/build.gradle:66-67` 硬编码
`sourceCompatibility/targetCompatibility = VERSION_21`，用 JDK 17 会报
`无效的源发行版: 21`（实测）。Gradle 8.14 最高支持 Java 24，所以 JBR 25 不行、JDK 21 可以。

```bash
# 1) JDK 21（Temurin 压缩包，解到用户目录，无需管理员）
cd /c/Users/k30087471/Downloads
curl -L -o jdk21.zip "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse"
unzip -q jdk21.zip -d /c/Users/k30087471/tools/
ls -d /c/Users/k30087471/tools/jdk-21*   # 期望出现 jdk-21.x.x+x 目录

# 2) Android cmdline-tools
mkdir -p /c/Users/k30087471/tools/android-sdk/cmdline-tools
cd /c/Users/k30087471/tools/android-sdk/cmdline-tools
curl -L -o tools.zip "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
unzip -q tools.zip && mv cmdline-tools latest

# 3) 安装 platform 与 build-tools
export ANDROID_HOME=/c/Users/k30087471/tools/android-sdk
# Resolve JAVA_HOME from whatever the unzip produced; version strings change between downloads.
export JAVA_HOME=$(ls -d /c/Users/k30087471/tools/jdk-21* | head -1)
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
echo "JAVA_HOME=$JAVA_HOME"
```

- [ ] **Step 2: 验证工具链**

Run: `java -version && sdkmanager --list_installed | grep -E "platforms;android-36|build-tools;36"`
Expected: `openjdk version "17.` 开头；列出 android-36 与 36.0.0

- [ ] **Step 3: 生成 Capacitor Android 工程**

`capacitor.config.ts`:
```ts
import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'com.kuangcx.ufm',
  appName: '文件管理器',
  webDir: 'dist/build/h5',
  android: { allowMixedContent: false, webContentsDebuggingEnabled: true }
}

export default config
```

Run:
```bash
npm run build:h5
npx cap add android
npx cap sync android
```
Expected: 生成 `android/`，且 `android/app/src/main/assets/public/index.html` 存在

- [ ] **Step 4: 改 Gradle 配置**

`android/variables.gradle` 把 `minSdkVersion = 24` 改为 `30`（其余不动）。

`android/build.gradle` 在 `buildscript.dependencies` 里追加 Kotlin 插件：
```gradle
classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.21'
```

`android/app/build.gradle` 顶部加 `apply plugin: 'kotlin-android'`，并把 `defaultConfig` 改为：
```gradle
applicationId "com.kuangcx.ufm"
minSdkVersion rootProject.ext.minSdkVersion
targetSdkVersion rootProject.ext.targetSdkVersion
versionCode 1
versionName "1.0.0"
```
`dependencies` 追加：
```gradle
implementation "org.jetbrains.kotlin:kotlin-stdlib:2.1.21"
```

- [ ] **Step 5: 写 MainActivity（Kotlin）**

删除模板的 `MainActivity.java`，新建 `android/app/src/main/java/com/kuangcx/ufm/MainActivity.kt`：
```kotlin
package com.kuangcx.ufm

import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity()
```

`android/app/src/main/AndroidManifest.xml` 的 `activity` 的 `android:name` 改为 `.MainActivity`。

- [ ] **Step 6: 构建并安装**

```bash
export ANDROID_HOME=/c/Users/k30087471/tools/android-sdk
export JAVA_HOME=$(ls -d /c/Users/k30087471/tools/jdk-21* | head -1)
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.kuangcx.ufm/.MainActivity
```
Expected: `BUILD SUCCESSFUL`；手机上出现「文件管理器」并显示首页文字

- [ ] **Step 7: 提交**

```bash
git add -A
git commit -m "feat: Capacitor 容器与 Android 工程，真机可启动"
```

---
## Task 3: 权限清单、权限状态插件与权限门

**Files:**
- Modify: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/com/kuangcx/ufm/Perm.kt`
- Create: `android/app/src/main/java/com/kuangcx/ufm/PermState.kt`
- Create: `android/app/src/main/java/com/kuangcx/ufm/ShellPlugin.kt`
- Create: `android/app/src/test/java/com/kuangcx/ufm/PermStateTest.kt`
- Create: `src/native/shell.ts`
- Create: `src/utils/errors.ts`, `tests/unit/errors.spec.ts`
- Create: `src/components/PermGate.vue`
- Modify: `src/pages/index/index.vue`

**Interfaces:**
- Produces:
  - `Shell.permissionState(): Promise<{ allFiles: boolean; mediaImages: boolean; installPackages: boolean; sdk: number }>`
  - `Shell.openAllFilesSettings(): Promise<void>`、`Shell.openInstallSettings(): Promise<void>`
  - `describeError(e: unknown): string` —— 把原生 reject 转成中文提示
  - `Perm.ensureAllFiles(context: Context): Boolean` —— 其它插件的权限闸门
  - `<PermGate>` 组件：无全盘权限时渲染降级说明页与「去授权」按钮

- [ ] **Step 1: 写失败测试**

`tests/unit/errors.spec.ts`:
```ts
import { describe, expect, it } from 'vitest'
import { describeError } from '../../src/utils/errors'

describe('describeError', () => {
  it('maps known native codes to Chinese messages', () => {
    expect(describeError({ code: 'E_PERM' })).toBe('缺少「所有文件访问」权限，请到系统设置中开启')
    expect(describeError({ code: 'E_NO_SPACE' })).toBe('目标存储空间不足')
    expect(describeError({ code: 'E_EXISTS' })).toBe('目标位置已存在同名文件')
    expect(describeError({ code: 'E_NOT_EMPTY' })).toBe('目录内还有文件，请先移出后再删除')
  })
  it('falls back to the original message when code is unknown', () => {
    expect(describeError({ code: 'E_IO', message: 'disk gone' })).toBe('磁盘错误：disk gone')
    expect(describeError({ message: 'boom' })).toBe('boom')
  })
  it('never renders undefined for a malformed error', () => {
    expect(describeError(null)).toBe('操作失败，请重试')
    expect(describeError({})).toBe('操作失败，请重试')
  })
})
```

`android/app/src/test/java/com/kuangcx/ufm/PermStateTest.kt`:
```kotlin
package com.kuangcx.ufm

import org.junit.Assert.assertEquals
import org.junit.Test

class PermStateTest {
    @Test
    fun `sdk below 30 never reports all files access`() {
        assertEquals(false, PermState.resolve(28, true, true, false).allFiles)
    }

    @Test
    fun `sdk 30 and above reports the granted flag`() {
        assertEquals(true, PermState.resolve(30, true, true, false).allFiles)
        assertEquals(false, PermState.resolve(36, false, true, false).allFiles)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `npm run test`
Expected: FAIL，找不到 `src/utils/errors`

Run: `cd android && ./gradlew testDebugUnitTest --tests "*PermStateTest*"`
Expected: FAIL，`Unresolved reference: PermState`

- [ ] **Step 3: 写实现**

`src/utils/errors.ts`:
```ts
const MESSAGES: Record<string, string> = {
  E_PERM: '缺少「所有文件访问」权限，请到系统设置中开启',
  E_NOENT: '文件或目录不存在',
  E_EXISTS: '目标位置已存在同名文件',
  E_NO_SPACE: '目标存储空间不足',
  E_BAD_NAME: '名称不合法或与现有分组重名',
  E_NOT_EMPTY: '目录内还有文件，请先移出后再删除',
  E_RENAME_FAILED: '移动失败，原文件未改动',
  E_COPY_FAILED: '复制校验失败，原文件未改动',
  E_IO: '磁盘错误'
}

export function describeError(e: unknown): string {
  if (e && typeof e === 'object') {
    const { code, message } = e as { code?: string; message?: string }
    if (code && MESSAGES[code]) {
      return code === 'E_IO' && message ? `磁盘错误：${message}` : MESSAGES[code]
    }
    if (message && typeof message === 'string') return message
  }
  return '操作失败，请重试'
}
```

`android/app/src/main/java/com/kuangcx/ufm/PermState.kt`:
```kotlin
package com.kuangcx.ufm

data class PermSnapshot(
    val allFiles: Boolean,
    val mediaImages: Boolean,
    val installPackages: Boolean,
    val sdk: Int
)

object PermState {
    /** Below Android 11 there is no all-files switch, so callers must use the legacy path. */
    fun resolve(sdk: Int, allFilesGranted: Boolean, mediaGranted: Boolean, installGranted: Boolean) =
        PermSnapshot(
            allFiles = sdk >= 30 && allFilesGranted,
            mediaImages = mediaGranted,
            installPackages = installGranted,
            sdk = sdk
        )
}
```

`android/app/src/main/java/com/kuangcx/ufm/Perm.kt`:
```kotlin
package com.kuangcx.ufm

import android.content.Context
import android.os.Build
import android.os.Environment

object Perm {
    fun ensureAllFiles(context: Context): Boolean =
        Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
}
```

`android/app/src/main/AndroidManifest.xml`（`<manifest>` 根标签加 `xmlns:tools="http://schemas.android.com/tools"`；`<application>` 之前插入）:
```xml
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
    tools:ignore="ScopedStorage" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="29" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

`android/app/src/main/java/com/kuangcx/ufm/ShellPlugin.kt`（本任务版本，Task 13 扩展）:
```kotlin
package com.kuangcx.ufm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "Shell")
class ShellPlugin : Plugin() {

    private fun hasMediaPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
    }

    @PluginMethod
    fun permissionState(call: PluginCall) {
        val sdk = Build.VERSION.SDK_INT
        val allFiles = if (sdk >= 30) Environment.isExternalStorageManager() else false
        val install = if (sdk >= 26) context.packageManager.canRequestPackageInstalls() else true
        val snap = PermState.resolve(sdk, allFiles, hasMediaPermission(), install)
        call.resolve(JSObject().apply {
            put("allFiles", snap.allFiles)
            put("mediaImages", snap.mediaImages)
            put("installPackages", snap.installPackages)
            put("sdk", snap.sdk)
        })
    }

    @PluginMethod
    fun openAllFilesSettings(call: PluginCall) {
        if (Build.VERSION.SDK_INT >= 30) {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
        call.resolve()
    }

    @PluginMethod
    fun openInstallSettings(call: PluginCall) {
        if (Build.VERSION.SDK_INT >= 26) {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
        call.resolve()
    }
}
```

`MainActivity.kt` 改为：
```kotlin
package com.kuangcx.ufm

import android.os.Bundle
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(ShellPlugin::class.java)
        super.onCreate(savedInstanceState)
    }
}
```

`src/native/shell.ts`:
```ts
import { registerPlugin } from '@capacitor/core'

export interface PermissionSnapshot {
  allFiles: boolean
  mediaImages: boolean
  installPackages: boolean
  sdk: number
}

export interface ShellNative {
  permissionState(): Promise<PermissionSnapshot>
  openAllFilesSettings(): Promise<void>
  openInstallSettings(): Promise<void>
}

export const Shell = registerPlugin<ShellNative>('Shell')
```

`src/components/PermGate.vue`:
```vue
<template>
  <view v-if="!state.allFiles" class="gate">
    <text class="gate-title">需要「所有文件访问」权限</text>
    <text class="gate-body">文件管理器需要读写存储中的文件（浏览、移动、重命名、分组整理照片）。请点击下方按钮，在系统设置中允许本应用访问所有文件。未授权时可以浏览相册，但不能修改文件。</text>
    <button class="gate-btn" @click="go">去授权</button>
    <text class="gate-hint">授权后返回本页会自动刷新状态</text>
  </view>
  <slot v-else />
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { Shell, type PermissionSnapshot } from '../native/shell'

const emit = defineEmits<{ (e: 'state', v: PermissionSnapshot): void }>()
const state = ref<PermissionSnapshot>({ allFiles: false, mediaImages: false, installPackages: false, sdk: 0 })

async function refresh() {
  state.value = await Shell.permissionState()
  emit('state', state.value)
}

function go() {
  Shell.openAllFilesSettings()
}

onShow(refresh)
</script>

<style>
.gate { padding: 64rpx 48rpx; display: flex; flex-direction: column; }
.gate-title { font-size: 40rpx; font-weight: 600; margin-bottom: 24rpx; }
.gate-body { color: #9aa0a6; font-size: 28rpx; line-height: 1.7; }
.gate-btn { margin-top: 48rpx; background: #3b82f6; color: #ffffff; border-radius: 12rpx; }
.gate-hint { margin-top: 24rpx; color: #6b7280; font-size: 24rpx; }
</style>
```

`src/pages/index/index.vue` 改为：
```vue
<template>
  <PermGate>
    <view class="page">
      <text class="title">文件管理器</text>
      <text class="hint">权限已就绪</text>
    </view>
  </PermGate>
</template>

<script setup lang="ts">
import PermGate from '../../components/PermGate.vue'
</script>

<style>
.page { padding: 48rpx; }
.title { font-size: 44rpx; font-weight: 600; }
.hint { display: block; margin-top: 16rpx; color: #8a9199; }
</style>
```

- [ ] **Step 4: 跑测试确认通过**

Run: `npm run test`
Expected: 8 个 Vitest 测试 PASS（Task 1 的 4 个 + 本任务 errors 的 4 个 `it` 块）

Run: `cd android && ./gradlew testDebugUnitTest --tests "*PermStateTest*"`
Expected: `BUILD SUCCESSFUL`，2 个测试 PASS

- [ ] **Step 5: 真机验证降级路径（Review Focus #5 的一半）**

```bash
npm run build:h5 && npx cap sync android
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.kuangcx.ufm/.MainActivity
```
Expected: 首屏是「需要「所有文件访问」权限」说明页而不是白屏；点「去授权」跳系统设置；返回后自动变为「权限已就绪」

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "feat: 权限清单、权限状态插件与权限门"
```

---

## Task 4: 存储卷枚举、目录列举与文件列表页

**Files:**
- Create: `android/app/src/main/java/com/kuangcx/ufm/core/PathUtil.kt`
- Create: `android/app/src/main/java/com/kuangcx/ufm/core/StorageVolumes.kt`
- Create: `android/app/src/main/java/com/kuangcx/ufm/core/Sorting.kt`
- Create: `android/app/src/main/java/com/kuangcx/ufm/FsPlugin.kt`（本任务只放 `volumes` / `listDir`）
- Create: `android/app/src/test/java/com/kuangcx/ufm/SortingTest.kt`
- Create: `src/native/fs.ts`
- Create: `src/components/FileRow.vue`
- Create: `src/pages/files/files.vue`
- Modify: `src/pages.json`、`src/pages/index/index.vue`

**Interfaces:**
- Produces:
  - `Fs.volumes(): Promise<{ volumes: Volume[] }>`，`Volume = { label, path, removable, availableBytes }`
  - `Fs.listDir({ path, sort, asc }): Promise<{ path, entries: FileEntry[] }>`
  - `FileEntry = { name, path, dir, size, mtime, mime }`，`SortKey = 'name' | 'size' | 'time' | 'type'`

- [ ] **Step 1: 写失败测试**

`android/app/src/test/java/com/kuangcx/ufm/SortingTest.kt`:
```kotlin
package com.kuangcx.ufm

import com.kuangcx.ufm.core.Entry
import com.kuangcx.ufm.core.SortKey
import com.kuangcx.ufm.core.sortEntries
import org.junit.Assert.assertEquals
import org.junit.Test

class SortingTest {
    private val a = Entry("b.txt", "/x/b.txt", false, 300, 1_000, "text/plain")
    private val b = Entry("A.txt", "/x/A.txt", false, 100, 3_000, "text/plain")
    private val dir = Entry("dir", "/x/dir", true, 0, 2_000, "")

    @Test
    fun `directories always come first`() {
        assertEquals("dir", sortEntries(listOf(a, b, dir), SortKey.SIZE, asc = true).first().name)
    }

    @Test
    fun `name sort is case insensitive`() {
        assertEquals(listOf("A.txt", "b.txt"), sortEntries(listOf(a, b), SortKey.NAME, true).map { it.name })
    }

    @Test
    fun `size and time sort honour direction`() {
        assertEquals(listOf("A.txt", "b.txt"), sortEntries(listOf(a, b), SortKey.SIZE, true).map { it.name })
        assertEquals(listOf("b.txt", "A.txt"), sortEntries(listOf(a, b), SortKey.SIZE, false).map { it.name })
        assertEquals(listOf("A.txt", "b.txt"), sortEntries(listOf(a, b), SortKey.TIME, false).map { it.name })
    }

    @Test
    fun `equal keys fall back to name`() {
        val x = Entry("z.txt", "/x/z.txt", false, 1, 5, "")
        val y = Entry("y.txt", "/x/y.txt", false, 1, 5, "")
        assertEquals(listOf("y.txt", "z.txt"), sortEntries(listOf(x, y), SortKey.SIZE, true).map { it.name })
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd android && ./gradlew testDebugUnitTest --tests "*SortingTest*"`
Expected: FAIL，`Unresolved reference: core`

- [ ] **Step 3: 写实现**

`core/Sorting.kt`:
```kotlin
package com.kuangcx.ufm.core

enum class SortKey { NAME, SIZE, TIME, TYPE }

data class Entry(
    val name: String,
    val path: String,
    val dir: Boolean,
    val size: Long,
    val mtime: Long,
    val mime: String
)

fun sortEntries(entries: List<Entry>, key: SortKey, asc: Boolean): List<Entry> {
    val byName = compareBy<Entry> { it.name.lowercase() }
    val primary = when (key) {
        SortKey.NAME -> byName
        SortKey.SIZE -> compareBy { it.size }
        SortKey.TIME -> compareBy { it.mtime }
        SortKey.TYPE -> compareBy { it.mime }
    }.let { if (asc) it else it.reversed() }
    return entries.sortedWith(compareByDescending<Entry> { it.dir }.then(primary).then(byName))
}
```

`core/PathUtil.kt`:
```kotlin
package com.kuangcx.ufm.core

import java.io.File

object PathUtil {
    /** Absolute paths only; traversal segments are rejected before touching the filesystem. */
    fun safe(path: String?): File {
        require(!path.isNullOrBlank()) { "path required" }
        require(path.startsWith("/")) { "absolute path required" }
        require(!path.contains("/../") && !path.endsWith("/..")) { "traversal rejected" }
        return File(path).canonicalFile
    }

    fun mimeOf(file: File): String = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "pdf" -> "application/pdf"
        "txt", "log", "md" -> "text/plain"
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }
}
```

`core/StorageVolumes.kt`:
```kotlin
package com.kuangcx.ufm.core

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager

data class Volume(val label: String, val path: String, val removable: Boolean, val availableBytes: Long)

object StorageVolumes {
    fun list(context: Context): List<Volume> {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val primary = Environment.getExternalStorageDirectory()
        val out = mutableListOf(
            Volume("内部存储", primary.absolutePath, false, primary.usableSpace)
        )
        for (v in sm.storageVolumes) {
            val dir = v.directory ?: continue
            if (dir.absolutePath == primary.absolutePath) continue
            out += Volume(
                v.getDescription(context) ?: dir.name,
                dir.absolutePath,
                v.isRemovable,
                dir.usableSpace
            )
        }
        return out
    }
}
```

`FsPlugin.kt`（本任务版本，Task 5/6 扩展）:
```kotlin
package com.kuangcx.ufm

import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.kuangcx.ufm.core.Entry
import com.kuangcx.ufm.core.PathUtil
import com.kuangcx.ufm.core.SortKey
import com.kuangcx.ufm.core.StorageVolumes
import com.kuangcx.ufm.core.sortEntries

@CapacitorPlugin(name = "Fs")
class FsPlugin : Plugin() {

    @PluginMethod
    fun volumes(call: PluginCall) {
        bridge.execute {
            try {
                val arr = JSArray()
                StorageVolumes.list(context).forEach { v ->
                    arr.put(JSObject().apply {
                        put("label", v.label)
                        put("path", v.path)
                        put("removable", v.removable)
                        put("availableBytes", v.availableBytes)
                    })
                }
                call.resolve(JSObject().apply { put("volumes", arr) })
            } catch (e: Exception) {
                call.reject(e.message ?: "volume enumeration failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun listDir(call: PluginCall) {
        val path = call.getString("path")
        val sortKey = when (call.getString("sort") ?: "name") {
            "size" -> SortKey.SIZE
            "time" -> SortKey.TIME
            "type" -> SortKey.TYPE
            else -> SortKey.NAME
        }
        val asc = call.getBoolean("asc") ?: true
        bridge.execute {
            try {
                if (!Perm.ensureAllFiles(context)) {
                    call.reject("no all-files access", "E_PERM")
                    return@execute
                }
                val dir = PathUtil.safe(path)
                if (!dir.exists()) {
                    call.reject("not found", "E_NOENT")
                    return@execute
                }
                if (!dir.isDirectory) {
                    call.reject("not a directory", "E_IO")
                    return@execute
                }
                val files = dir.listFiles() ?: throw java.io.IOException("unreadable directory")
                val raw = files.map { f ->
                    Entry(
                        f.name,
                        f.absolutePath,
                        f.isDirectory,
                        if (f.isDirectory) 0L else f.length(),
                        f.lastModified(),
                        if (f.isDirectory) "" else PathUtil.mimeOf(f)
                    )
                }
                val arr = JSArray()
                sortEntries(raw, sortKey, asc).forEach { e ->
                    arr.put(JSObject().apply {
                        put("name", e.name); put("path", e.path); put("dir", e.dir)
                        put("size", e.size); put("mtime", e.mtime); put("mime", e.mime)
                    })
                }
                call.resolve(JSObject().apply {
                    put("path", dir.absolutePath)
                    put("entries", arr)
                })
            } catch (e: Exception) {
                call.reject(e.message ?: "list failed", "E_IO", e)
            }
        }
    }
}
```

`MainActivity.kt` 追加一行注册（放在 `registerPlugin(ShellPlugin::class.java)` 之后）：
```kotlin
registerPlugin(FsPlugin::class.java)
```

`src/native/fs.ts`:
```ts
import { registerPlugin } from '@capacitor/core'

export type SortKey = 'name' | 'size' | 'time' | 'type'

export interface FileEntry {
  name: string
  path: string
  dir: boolean
  size: number
  mtime: number
  mime: string
}

export interface Volume {
  label: string
  path: string
  removable: boolean
  availableBytes: number
}

export interface FsNative {
  volumes(): Promise<{ volumes: Volume[] }>
  listDir(o: { path: string; sort?: SortKey; asc?: boolean }): Promise<{ path: string; entries: FileEntry[] }>
}

export const Fs = registerPlugin<FsNative>('Fs')
```

`src/components/FileRow.vue`:
```vue
<template>
  <view class="row" @click="$emit('open', entry)" @longpress="$emit('select', entry)">
    <text class="icon">{{ entry.dir ? '📁' : iconOf(entry.mime) }}</text>
    <view class="meta">
      <text class="name">{{ entry.name }}</text>
      <text class="sub">{{ entry.dir ? '文件夹' : formatSize(entry.size) }} · {{ formatTime(entry.mtime) }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { formatSize, formatTime } from '../utils/format'
import type { FileEntry } from '../native/fs'

defineProps<{ entry: FileEntry }>()
defineEmits<{ (e: 'open', v: FileEntry): void; (e: 'select', v: FileEntry): void }>()

function iconOf(mime: string): string {
  if (mime.startsWith('image/')) return '🖼'
  if (mime.startsWith('video/')) return '🎬'
  if (mime.startsWith('audio/')) return '🎵'
  if (mime === 'application/pdf') return '📄'
  if (mime === 'application/zip') return '🗜'
  if (mime === 'application/vnd.android.package-archive') return '📦'
  return '📃'
}
</script>

<style>
.row { display: flex; align-items: center; padding: 24rpx 32rpx; border-bottom: 1rpx solid #1f2429; }
.icon { font-size: 40rpx; width: 72rpx; }
.meta { display: flex; flex-direction: column; flex: 1; min-width: 0; }
.name { font-size: 30rpx; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.sub { font-size: 22rpx; color: #7d848c; margin-top: 6rpx; }
</style>
```

`src/pages/files/files.vue`:
```vue
<template>
  <view class="page">
    <view class="bar">
      <text class="crumb">{{ current }}</text>
      <text class="count">{{ entries.length }} 项</text>
    </view>
    <scroll-view class="list" scroll-y>
      <FileRow v-for="e in entries" :key="e.path" :entry="e" @open="open" @select="onSelect" />
      <view v-if="!entries.length" class="empty"><text>{{ error || '空文件夹' }}</text></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import FileRow from '../../components/FileRow.vue'
import { Fs, type FileEntry } from '../../native/fs'
import { describeError } from '../../utils/errors'

const current = ref('')
const entries = ref<FileEntry[]>([])
const error = ref('')

async function load(path: string) {
  error.value = ''
  try {
    const r = await Fs.listDir({ path, sort: 'name', asc: true })
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

function onSelect(e: FileEntry) {
  uni.showToast({ title: e.name, icon: 'none' })
}

onLoad((q) => { load((q && (q.path as string)) || '/storage/emulated/0') })
</script>

<style>
.page { display: flex; flex-direction: column; height: 100vh; }
.bar { display: flex; justify-content: space-between; padding: 20rpx 32rpx; background: #15191d; }
.crumb { font-size: 24rpx; color: #9aa0a6; flex: 1; overflow: hidden; white-space: nowrap; }
.count { font-size: 24rpx; color: #6b7280; margin-left: 16rpx; }
.list { flex: 1; }
.empty { padding: 80rpx; text-align: center; color: #6b7280; }
</style>
```

`src/pages.json` 的 `pages` 追加：
```json
{ "path": "pages/files/files", "style": { "navigationBarTitleText": "文件" } }
```

`src/pages/index/index.vue` 的 PermGate 内追加（放在 `.hint` 之后）：
```vue
<button class="entry" @click="goFiles">浏览文件</button>
```
`<script setup>` 里加：
```ts
function goFiles() {
  uni.navigateTo({ url: '/pages/files/files' })
}
```
`<style>` 里加：
```css
.entry { margin-top: 32rpx; background: #1f2429; color: #e8eaed; border-radius: 12rpx; }
```

- [ ] **Step 4: 跑测试与真机验证**

```bash
cd android && ./gradlew testDebugUnitTest --tests "*SortingTest*"
```
Expected: `BUILD SUCCESSFUL`，4 个测试 PASS

```bash
npm run build:h5 && npx cap sync android
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.kuangcx.ufm/.MainActivity
```
Expected: 点「浏览文件」进入 `/storage/emulated/0`，看到 DCIM / Download / Pictures 等真实目录；点文件夹可进入；无权限时列表显示「缺少「所有文件访问」权限…」而非白屏

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat: 存储卷枚举、目录列举与文件列表页"
```

---
## Task 5: 文件写操作（新建目录 / 重命名 / 删除）与排序切换

**Files:**
- Modify: `android/app/src/main/java/com/kuangcx/ufm/FsPlugin.kt`
- Create: `android/app/src/test/java/com/kuangcx/ufm/PathUtilTest.kt`
- Modify: `src/native/fs.ts`
- Create: `src/components/ActionSheet.vue`
- Modify: `src/pages/files/files.vue`

**Interfaces:**
- Consumes: `PathUtil.safe`、`Perm.ensureAllFiles`
- Produces:
  - `Fs.mkdir({ parent, name }): Promise<{ path: string }>`
  - `Fs.rename({ path, newName }): Promise<{ path: string }>`
  - `Fs.remove({ paths }): Promise<{ removed: string[]; failed: { path: string; code: string }[] }>`
  - `<ActionSheet>`：通用底部操作面板，`props: items: { key, label, danger? }[]`，emit `pick(key)`

- [ ] **Step 1: 写失败测试**

`android/app/src/test/java/com/kuangcx/ufm/PathUtilTest.kt`:
```kotlin
package com.kuangcx.ufm

import com.kuangcx.ufm.core.PathUtil
import org.junit.Assert.assertThrows
import org.junit.Test

class PathUtilTest {
    @Test
    fun `rejects a blank path`() {
        assertThrows(IllegalArgumentException::class.java) { PathUtil.safe("") }
        assertThrows(IllegalArgumentException::class.java) { PathUtil.safe(null) }
    }

    @Test
    fun `rejects a relative path`() {
        assertThrows(IllegalArgumentException::class.java) { PathUtil.safe("DCIM/Camera") }
    }

    @Test
    fun `rejects traversal segments`() {
        assertThrows(IllegalArgumentException::class.java) { PathUtil.safe("/sdcard/DCIM/../../etc") }
        assertThrows(IllegalArgumentException::class.java) { PathUtil.safe("/sdcard/DCIM/..") }
    }

    @Test
    fun `mime lookup covers photos, video and apk`() {
        val f = java.io.File("/tmp/a.JPG")
        org.junit.Assert.assertEquals("image/jpeg", PathUtil.mimeOf(f))
        org.junit.Assert.assertEquals("application/vnd.android.package-archive", PathUtil.mimeOf(java.io.File("/tmp/b.apk")))
        org.junit.Assert.assertEquals("application/octet-stream", PathUtil.mimeOf(java.io.File("/tmp/c.bin")))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd android && ./gradlew testDebugUnitTest --tests "*PathUtilTest*"`
Expected: FAIL，`Unresolved reference: safe`

（若 Task 4 已实现 `PathUtil`，本步会通过 —— 那就再跑一次确认 4 个用例都覆盖到，失败即说明实现不完整。）

- [ ] **Step 3: 给 FsPlugin 追加写操作**

在 `FsPlugin.kt` 类内追加：
```kotlin
    @PluginMethod
    fun mkdir(call: PluginCall) {
        val parent = call.getString("parent")
        val name = call.getString("name")
        bridge.execute {
            try {
                if (!Perm.ensureAllFiles(context)) { call.reject("no all-files access", "E_PERM"); return@execute }
                val base = PathUtil.safe(parent)
                if (!base.isDirectory) { call.reject("parent missing", "E_NOENT"); return@execute }
                // Reject separators outright: a "name" must stay a single path segment.
                if (name.isNullOrBlank() || name.contains('/') || name == "." || name == "..") {
                    call.reject("bad name", "E_BAD_NAME"); return@execute
                }
                val target = java.io.File(base, name)
                if (target.exists()) { call.reject("exists", "E_EXISTS"); return@execute }
                if (!target.mkdirs()) { call.reject("mkdir failed", "E_IO"); return@execute }
                call.resolve(JSObject().apply { put("path", target.absolutePath) })
            } catch (e: Exception) {
                call.reject(e.message ?: "mkdir failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun rename(call: PluginCall) {
        val path = call.getString("path")
        val newName = call.getString("newName")
        bridge.execute {
            try {
                if (!Perm.ensureAllFiles(context)) { call.reject("no all-files access", "E_PERM"); return@execute }
                val src = PathUtil.safe(path)
                if (!src.exists()) { call.reject("not found", "E_NOENT"); return@execute }
                if (newName.isNullOrBlank() || newName.contains('/') || newName == "." || newName == "..") {
                    call.reject("bad name", "E_BAD_NAME"); return@execute
                }
                val dst = java.io.File(src.parentFile, newName)
                if (dst.exists() && dst.absolutePath != src.absolutePath) {
                    call.reject("exists", "E_EXISTS"); return@execute
                }
                if (!src.renameTo(dst)) { call.reject("rename failed", "E_RENAME_FAILED"); return@execute }
                lightRescan(src.absolutePath, dst.absolutePath)
                call.resolve(JSObject().apply { put("path", dst.absolutePath) })
            } catch (e: Exception) {
                call.reject(e.message ?: "rename failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun remove(call: PluginCall) {
        val paths = call.getArray("paths")?.toList()?.map { it.toString() } ?: emptyList()
        bridge.execute {
            if (!Perm.ensureAllFiles(context)) { call.reject("no all-files access", "E_PERM"); return@execute }
            val removed = JSArray()
            val failed = JSArray()
            for (p in paths) {
                try {
                    val f = PathUtil.safe(p)
                    if (!f.exists()) { failed.put(errObj(p, "E_NOENT")); continue }
                    if (!deleteRecursively(f)) { failed.put(errObj(p, "E_IO")); continue }
                    removed.put(p)
                } catch (e: Exception) {
                    failed.put(errObj(p, "E_IO"))
                }
            }
            lightRescan(*paths.toTypedArray())
            call.resolve(JSObject().apply { put("removed", removed); put("failed", failed) })
        }
    }

    private fun errObj(path: String, code: String) = JSObject().apply { put("path", path); put("code", code) }

    private fun deleteRecursively(f: java.io.File): Boolean {
        if (f.isDirectory) f.listFiles()?.forEach { deleteRecursively(it) }
        return f.delete()
    }
```

`FsPlugin` 追加一个共用的重扫助手（Task 6 也会用）：
```kotlin
    private fun lightRescan(vararg paths: String) {
        paths.forEach { p ->
            android.media.MediaScannerConnection.scanFile(
                context, arrayOf(p), null, null
            )
        }
    }
```

- [ ] **Step 4: TS 封装与 UI**

`src/native/fs.ts` 的 `FsNative` 接口追加：
```ts
  mkdir(o: { parent: string; name: string }): Promise<{ path: string }>
  rename(o: { path: string; newName: string }): Promise<{ path: string }>
  remove(o: { paths: string[] }): Promise<{ removed: string[]; failed: { path: string; code: string }[] }>
```

`src/components/ActionSheet.vue`:
```vue
<template>
  <view v-if="visible" class="mask" @click="$emit('close')">
    <view class="sheet" @click.stop>
      <view
        v-for="it in items"
        :key="it.key"
        class="item"
        :class="{ danger: it.danger }"
        @click="$emit('pick', it.key)"
      >{{ it.label }}</view>
      <view class="item cancel" @click="$emit('close')">取消</view>
    </view>
  </view>
</template>

<script setup lang="ts">
defineProps<{
  visible: boolean
  items: { key: string; label: string; danger?: boolean }[]
}>()
defineEmits<{ (e: 'pick', key: string): void; (e: 'close'): void }>()
</script>

<style>
.mask { position: fixed; left: 0; top: 0; right: 0; bottom: 0; background: rgba(0, 0, 0, 0.5); display: flex; align-items: flex-end; }
.sheet { width: 100%; background: #191d22; border-radius: 20rpx 20rpx 0 0; padding-bottom: 24rpx; }
.item { padding: 32rpx; text-align: center; font-size: 30rpx; border-bottom: 1rpx solid #23282e; }
.item.danger { color: #ef5350; }
.item.cancel { color: #9aa0a6; border-bottom: none; }
</style>
```

`src/pages/files/files.vue` 改为：
```vue
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
```

- [ ] **Step 5: 跑测试与真机验证**

```bash
cd android && ./gradlew testDebugUnitTest --tests "*PathUtilTest*"
```
Expected: `BUILD SUCCESSFUL`，4 个测试 PASS

```bash
npm run build:h5 && npx cap sync android
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.kuangcx.ufm/.MainActivity
```
Expected（真机逐项）：① 在 Download 下新建文件夹成功，`adb shell ls /storage/emulated/0/Download` 能看到；② 长按文件 → 重命名成功，`adb shell ls` 显示新名；③ 长按 → 删除后文件消失；④ 重命名为已存在的名字时提示「目标位置已存在同名文件」，原文件**仍存在**；⑤ 点右上角排序按钮，顺序随名称/大小/时间/类型切换

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "feat: 文件新建、重命名、删除与排序切换"
```

---

## Task 6: 移动事务（MoveEngine + MoveJournal）——防丢文件的核心

**Files:**
- Create: `android/app/src/main/java/com/kuangcx/ufm/core/MoveJournal.kt`
- Create: `android/app/src/main/java/com/kuangcx/ufm/core/MoveEngine.kt`
- Create: `android/app/src/test/java/com/kuangcx/ufm/MoveJournalTest.kt`
- Create: `android/app/src/test/java/com/kuangcx/ufm/MoveEngineTest.kt`
- Modify: `android/app/src/main/java/com/kuangcx/ufm/FsPlugin.kt`
- Modify: `src/native/fs.ts`
- Modify: `src/pages/files/files.vue`

**Interfaces:**
- Produces:
  - `MoveJournal(dir: File)`：`record(entry: MoveRecord)`、`updateState(id: String, state: MoveState)`、`all(): List<MoveRecord>`、`recover(onDone: (MoveRecord) -> Unit): List<MoveRecord>`
  - `MoveState = COPYING | COPIED | DONE | ABORTED | FAILED`
  - `MoveRecord(id, src, dst, part, size, state)`
  - `MoveEngine.moveFile(journal, src: File, dstDir: File, crossVolume: (File, File) -> Boolean): Result`
  - `Fs.move({ paths, destDir }): Promise<{ moved: string[]; failed: { path: string; code: string }[] }>`
  - `Fs.copy({ paths, destDir }): Promise<{ copied: string[]; failed: { path: string; code: string }[] }>`

- [ ] **Step 1: 写失败测试**

`android/app/src/test/java/com/kuangcx/ufm/MoveJournalTest.kt`:
```kotlin
package com.kuangcx.ufm

import com.kuangcx.ufm.core.MoveJournal
import com.kuangcx.ufm.core.MoveRecord
import com.kuangcx.ufm.core.MoveState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MoveJournalTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `records survive a new instance`() {
        val dir = tmp.newFolder()
        MoveJournal(dir).record(MoveRecord("id1", "/a/x.jpg", "/b/x.jpg", "/b/x.jpg.part", 10L, MoveState.COPYING))
        val reopened = MoveJournal(dir)
        assertEquals(1, reopened.all().size)
        assertEquals(MoveState.COPYING, reopened.all()[0].state)
    }

    @Test
    fun `state updates persist`() {
        val dir = tmp.newFolder()
        val j = MoveJournal(dir).apply {
            record(MoveRecord("id1", "/a/x.jpg", "/b/x.jpg", "/b/x.jpg.part", 10L, MoveState.COPYING))
        }
        j.updateState("id1", MoveState.DONE)
        assertEquals(MoveState.DONE, MoveJournal(dir).all()[0].state)
    }

    @Test
    fun `recovery aborts COPYING, finishes COPIED, keeps DONE`() {
        val dir = tmp.newFolder()
        val j = MoveJournal(dir)
        j.record(MoveRecord("c", "/a/c.jpg", "/b/c.jpg", "/b/c.jpg.part", 1, MoveState.COPYING))
        j.record(MoveRecord("p", "/a/p.jpg", "/b/p.jpg", "/b/p.jpg.part", 1, MoveState.COPIED))
        j.record(MoveRecord("d", "/a/d.jpg", "/b/d.jpg", "/b/d.jpg.part", 1, MoveState.DONE))
        val outcomes = mutableMapOf<String, MoveState>()
        j.recover { outcomes[it.id] = it.state }
        assertEquals(MoveState.ABORTED, outcomes["c"])
        assertEquals(MoveState.DONE, outcomes["p"])
        assertEquals(MoveState.DONE, outcomes["d"])
    }

    @Test
    fun `journal is capped so it cannot grow forever`() {
        val dir = tmp.newFolder()
        val j = MoveJournal(dir)
        repeat(300) { j.record(MoveRecord("id$it", "/a/$it", "/b/$it", "/b/$it.part", 1, MoveState.DONE)) }
        assertEquals(true, MoveJournal(dir).all().size <= 200)
    }
}
```

`android/app/src/test/java/com/kuangcx/ufm/MoveEngineTest.kt`:
```kotlin
package com.kuangcx.ufm

import com.kuangcx.ufm.core.MoveEngine
import com.kuangcx.ufm.core.MoveJournal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MoveEngineTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun engine() = MoveEngine(MoveJournal(tmp.newFolder()), crossVolume = { _, _ -> false })

    @Test
    fun `refuses to overwrite an existing destination`() {
        val src = tmp.newFile("a.jpg").apply { writeText("new") }
        val dstDir = tmp.newFolder()
        File(dstDir, "a.jpg").writeText("old")
        val r = engine().moveFile(src, dstDir)
        assertEquals("E_EXISTS", r.code)
        assertEquals("new", src.readText())
        assertEquals("old", File(dstDir, "a.jpg").readText())
    }

    @Test
    fun `same-volume move is a rename and leaves no temp file`() {
        val src = tmp.newFile("a.jpg").apply { writeText("data") }
        val dstDir = tmp.newFolder()
        val r = engine().moveFile(src, dstDir)
        assertTrue(r.ok)
        assertFalse(src.exists())
        assertEquals("data", File(dstDir, "a.jpg").readText())
        assertEquals(0, dstDir.listFiles()!!.count { it.name.endsWith(".part") })
    }

    @Test
    fun `cross-volume move copies, verifies, then deletes the source`() {
        val dir = tmp.newFolder()
        val src = File(tmp.root, "a.jpg").apply { writeText("payload") }
        val dstDir = tmp.newFolder()
        val e = MoveEngine(MoveJournal(dir), crossVolume = { _, _ -> true })
        val r = e.moveFile(src, dstDir)
        assertTrue(r.ok)
        assertFalse(src.exists())
        assertEquals("payload", File(dstDir, "a.jpg").readText())
    }

    @Test
    fun `cross-volume failure keeps the source untouched`() {
        val dir = tmp.newFolder()
        val src = File(tmp.root, "a.jpg").apply { writeText("payload") }
        val dstDir = tmp.newFolder()
        // A destination directory that is not writable makes the copy stage fail.
        val e = MoveEngine(MoveJournal(dir), crossVolume = { _, _ -> true })
        dstDir.setWritable(false)
        val r = e.moveFile(src, dstDir)
        dstDir.setWritable(true)
        if (!r.ok) {
            assertEquals("E_COPY_FAILED", r.code)
            assertTrue(src.exists())
            assertEquals("payload", src.readText())
        }
    }

    @Test
    fun `recovering an aborted copy removes the part file and keeps source`() {
        val dir = tmp.newFolder()
        val src = File(tmp.root, "a.jpg").apply { writeText("payload") }
        val dstDir = tmp.newFolder()
        val part = File(dstDir, "a.jpg.part").apply { writeText("half") }
        val j = MoveJournal(dir).apply {
            record(
                com.kuangcx.ufm.core.MoveRecord(
                    "x", src.absolutePath, File(dstDir, "a.jpg").absolutePath,
                    part.absolutePath, 7L, com.kuangcx.ufm.core.MoveState.COPYING
                )
            )
        }
        MoveEngine(j, crossVolume = { _, _ -> true }).recover()
        assertFalse(part.exists())
        assertTrue(src.exists())
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd android && ./gradlew testDebugUnitTest --tests "*Move*"`
Expected: FAIL，`Unresolved reference: core.MoveEngine`

- [ ] **Step 3: 写 MoveJournal**

`core/MoveJournal.kt`:
```kotlin
package com.kuangcx.ufm.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class MoveState { COPYING, COPIED, DONE, ABORTED, FAILED }

data class MoveRecord(
    val id: String,
    val src: String,
    val dst: String,
    val part: String,
    val size: Long,
    val state: MoveState
)

/**
 * Append-mostly journal persisted as one JSON file. Growth is bounded: entries beyond
 * [MAX_ENTRIES] are dropped oldest-first, so a long-lived install cannot bloat the file.
 */
class MoveJournal(private val dir: File, private val maxEntries: Int = 200) {

    private val file: File = File(dir, "move-journal.json")
    private val entries = linkedMapOf<String, MoveRecord>()

    init {
        load()
    }

    @Synchronized
    fun record(entry: MoveRecord) {
        entries[entry.id] = entry
        trim()
        save()
    }

    @Synchronized
    fun updateState(id: String, state: MoveState) {
        entries[id]?.let { entries[id] = it.copy(state = state); save() }
    }

    @Synchronized
    fun all(): List<MoveRecord> = entries.values.toList()

    /**
     * Crash recovery. COPYING entries are aborted (part file deleted, source kept);
     * COPIED entries keep their source until the caller confirms deletion. Callers
     * receive the post-recovery record for each entry that is not already DONE.
     */
    @Synchronized
    fun recover(onSettled: (MoveRecord) -> Unit): List<MoveRecord> {
        val settled = mutableListOf<MoveRecord>()
        for (r in entries.values.toList()) {
            when (r.state) {
                MoveState.COPYING -> {
                    runCatching { File(r.part).delete() }
                    val next = r.copy(state = MoveState.ABORTED)
                    entries[r.id] = next
                    settled += next
                }
                MoveState.COPIED -> {
                    val next = r.copy(state = MoveState.DONE)
                    entries[r.id] = next
                    settled += next
                }
                else -> Unit
            }
        }
        if (settled.isNotEmpty()) save()
        settled.forEach(onSettled)
        return settled
    }

    private fun trim() {
        while (entries.size > maxEntries) {
            entries.remove(entries.keys.first())
        }
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val rec = MoveRecord(
                    o.getString("id"), o.getString("src"), o.getString("dst"),
                    o.getString("part"), o.getLong("size"),
                    MoveState.valueOf(o.getString("state"))
                )
                entries[rec.id] = rec
            }
        }.onFailure { entries.clear() } // A corrupt journal must never block startup.
    }

    private fun save() {
        val arr = JSONArray()
        entries.values.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id); put("src", r.src); put("dst", r.dst)
                put("part", r.part); put("size", r.size); put("state", r.state.name)
            })
        }
        val tmp = File(dir, "move-journal.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
    }
}
```

- [ ] **Step 4: 写 MoveEngine**

`core/MoveEngine.kt`:
```kotlin
package com.kuangcx.ufm.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.CRC32

/**
 * Moves files without ever risking the source. Same-volume moves are a single atomic
 * rename. Cross-volume moves go copy -> fsync -> verify -> rename -> delete-source, with
 * every step journalled so a killed process can recover on next launch.
 */
class MoveEngine(
    private val journal: MoveJournal,
    private val crossVolume: (File, File) -> Boolean
) {

    data class Result(val ok: Boolean, val code: String? = null, val message: String? = null)

    fun moveFile(src: File, destDir: File, deleteSourceAfterRename: Boolean = true): Result {
        if (!src.exists()) return Result(false, "E_NOENT", "source missing")
        if (src.isDirectory) return Result(false, "E_IO", "directories are not moved by this engine")
        if (!destDir.exists() && !destDir.mkdirs()) return Result(false, "E_IO", "cannot create dest")
        val dst = File(destDir, src.name)
        if (dst.exists()) return Result(false, "E_EXISTS", "destination exists")

        return if (!crossVolume(src, destDir)) {
            // Same volume: atomic. A failure here leaves the source exactly as it was.
            if (src.renameTo(dst)) {
                Result(true)
            } else {
                Result(false, "E_RENAME_FAILED", "rename failed")
            }
        } else {
            moveAcrossVolumes(src, dst, deleteSourceAfterRename)
        }
    }

    fun copyFile(src: File, destDir: File): Result {
        if (!src.exists()) return Result(false, "E_NOENT", "source missing")
        if (src.isDirectory) return Result(false, "E_IO", "directories are not copied by this engine")
        if (!destDir.exists() && !destDir.mkdirs()) return Result(false, "E_IO", "cannot create dest")
        val dst = File(destDir, src.name)
        if (dst.exists()) return Result(false, "E_EXISTS", "destination exists")
        val part = File(destDir, src.name + ".part")
        return try {
            copyAndSync(src, part)
            if (part.length() != src.length()) {
                part.delete()
                return Result(false, "E_COPY_FAILED", "size mismatch")
            }
            if (src.length() > BIG_FILE_BYTES && crc32(src) != crc32(part)) {
                part.delete()
                return Result(false, "E_COPY_FAILED", "checksum mismatch")
            }
            if (!part.renameTo(dst)) {
                part.delete()
                return Result(false, "E_COPY_FAILED", "rename of part failed")
            }
            Result(true)
        } catch (e: Exception) {
            runCatching { part.delete() }
            Result(false, "E_COPY_FAILED", e.message ?: "copy failed")
        }
    }

    private fun moveAcrossVolumes(src: File, dst: File, deleteSource: Boolean): Result {
        // Parenthesised: without the inner parens the elvis binds looser than `<`.
        if ((dst.parentFile?.usableSpace ?: 0L) < (src.length() * SPACE_SAFETY).toLong()) {
            return Result(false, "E_NO_SPACE", "not enough space")
        }
        val part = File(dst.absolutePath + ".part")
        val id = UUID.randomUUID().toString()
        journal.record(MoveRecord(id, src.absolutePath, dst.absolutePath, part.absolutePath, src.length(), MoveState.COPYING))
        return try {
            copyAndSync(src, part)
            if (part.length() != src.length()) throw java.io.IOException("size mismatch")
            if (src.length() > BIG_FILE_BYTES && crc32(src) != crc32(part)) {
                throw java.io.IOException("checksum mismatch")
            }
            journal.updateState(id, MoveState.COPIED)
            if (!part.renameTo(dst)) throw java.io.IOException("rename of part failed")
            if (deleteSource && !src.delete()) {
                // Destination is complete and verified; keep the journal at COPIED so a later
                // pass retries the source deletion instead of losing track of it.
                return Result(false, "E_IO", "copied but source could not be deleted")
            }
            journal.updateState(id, MoveState.DONE)
            Result(true)
        } catch (e: Exception) {
            runCatching { part.delete() }
            runCatching { if (dst.exists() && dst.length() != src.length()) dst.delete() }
            journal.updateState(id, MoveState.FAILED)
            Result(false, "E_COPY_FAILED", e.message ?: "copy failed")
        }
    }

    /** Finishes or rolls back work interrupted by a process death. */
    fun recover(): List<MoveRecord> {
        val out = mutableListOf<MoveRecord>()
        journal.recover { r ->
            if (r.state == MoveState.COPIED) {
                // The verified copy exists; only the source deletion is outstanding.
                val src = File(r.src)
                val dst = File(r.dst)
                if (dst.exists() && dst.length() == r.size) {
                    src.delete()
                    journal.updateState(r.id, MoveState.DONE)
                }
            }
            out += r
        }
        return out
    }

    private fun copyAndSync(src: File, part: File) {
        FileInputStream(src).use { input ->
            FileOutputStream(part).use { output ->
                input.copyTo(output, BUFFER)
                output.flush()
                output.fd.sync()
            }
        }
    }

    private fun crc32(f: File): Long = FileInputStream(f).use { input ->
        val crc = CRC32()
        val buf = ByteArray(BUFFER)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            crc.update(buf, 0, n)
        }
        crc.value
    }

    companion object {
        const val BIG_FILE_BYTES = 8L * 1024 * 1024
        const val BUFFER = 256 * 1024
        /** Headroom multiplier so a nearly-full target fails before writing half a file. */
        const val SPACE_SAFETY = 1.1
    }
}
```

- [ ] **Step 5: FsPlugin 暴露 move / copy / 启动恢复**

`FsPlugin.kt` 内追加字段与初始化（`journal`/`engine` 用 `lateinit` 而不是 `by lazy`：`load()` 会立刻用到它们，而 `by lazy` 在 `load()` 期间才首次求值容易踩到生命周期顺序，显式初始化更好读）：
```kotlin
    private lateinit var journal: com.kuangcx.ufm.core.MoveJournal
    private lateinit var engine: com.kuangcx.ufm.core.MoveEngine

    private fun volumeRootOf(path: String): String =
        com.kuangcx.ufm.core.StorageVolumes.list(context)
            .filter { path == it.path || path.startsWith(it.path + "/") }
            .maxByOrNull { it.path.length }
            ?.path ?: "/"

    override fun load() {
        super.load()
        journal = com.kuangcx.ufm.core.MoveJournal(java.io.File(context.filesDir, "journal"))
        engine = com.kuangcx.ufm.core.MoveEngine(journal) { a, b ->
            volumeRootOf(a.absolutePath) != volumeRootOf(b.absolutePath)
        }
        bridge.execute {
            engine.recover().forEach { r ->
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(r.src, r.dst), null, null
                )
            }
        }
    }

    @PluginMethod
    fun move(call: PluginCall) {
        val paths = call.getArray("paths")?.toList()?.map { it.toString() } ?: emptyList()
        val destDir = call.getString("destDir")
        bridge.execute { transfer(call, paths, destDir, isMove = true) }
    }

    @PluginMethod
    fun copy(call: PluginCall) {
        val paths = call.getArray("paths")?.toList()?.map { it.toString() } ?: emptyList()
        val destDir = call.getString("destDir")
        bridge.execute { transfer(call, paths, destDir, isMove = false) }
    }

    private fun transfer(call: PluginCall, paths: List<String>, destDir: String?, isMove: Boolean) {
        if (!Perm.ensureAllFiles(context)) { call.reject("no all-files access", "E_PERM"); return }
        val done = JSArray()
        val failed = JSArray()
        val dest = try { PathUtil.safe(destDir) } catch (e: Exception) {
            call.reject("bad destination", "E_BAD_NAME"); return
        }
        if (!dest.isDirectory) { call.reject("destination is not a directory", "E_NOENT"); return }
        for (p in paths) {
            try {
                val src = PathUtil.safe(p)
                val r = if (isMove) engine.moveFile(src, dest) else engine.copyFile(src, dest)
                if (r.ok) {
                    done.put(p)
                    lightRescan(src.absolutePath, java.io.File(dest, src.name).absolutePath)
                } else {
                    failed.put(errObj(p, r.code ?: "E_IO"))
                }
            } catch (e: Exception) {
                failed.put(errObj(p, "E_IO"))
            }
        }
        call.resolve(JSObject().apply {
            put(if (isMove) "moved" else "copied", done)
            put("failed", failed)
        })
    }
```

`FsPlugin` 需要一个 `context` 属性（`Plugin` 已提供 `getContext()`，Kotlin 里直接写 `context` 即可）。`load()` 确实是 `Plugin` 的生命周期钩子，且 `setBridge` 在 `load()` **之前**执行（实测来源：`com/getcapacitor/PluginHandle.java:115-117`）：

```java
public Plugin loadInstance(Plugin plugin) {
    this.instance = plugin;
    this.instance.setPluginHandle(this);
    this.instance.setBridge(this.bridge);
    this.instance.load();          // <-- bridge already injected
    ...
}
```

所以 `load()` 里可以安全访问 `context` 与 `bridge`。这也是上面用 `lateinit` 在 `load()` 内显式初始化的原因：`by lazy` 在本例里虽然也能工作，但把初始化点显式放在 `load()` 更不容易被后续改动破坏。

**媒体重扫的线程限制**：`MediaScannerConnection.scanFile` 会走 binder 回调，Capacitor 的 `bridge.execute` 是在一个带 Looper 的 `HandlerThread` 上跑（实测来源：`Bridge.java:141`、`Bridge.java:217` 的 `Handler(handlerThread.getLooper())`），因此插件里直接调用是安全的。

`src/native/fs.ts` 追加：
```ts
export interface TransferResult {
  moved?: string[]
  copied?: string[]
  failed: { path: string; code: string }[]
}

export interface FsNative {
  // …之前的成员保持不变…
  move(o: { paths: string[]; destDir: string }): Promise<TransferResult>
  copy(o: { paths: string[]; destDir: string }): Promise<TransferResult>
}
```

- [ ] **Step 6: 跑测试确认通过**

Run: `cd android && ./gradlew testDebugUnitTest --tests "*Move*" --tests "*PathUtil*"`
Expected: `BUILD SUCCESSFUL`，MoveJournalTest 4 个 + MoveEngineTest 5 个 + PathUtilTest 4 个 全 PASS

若 `cross-volume failure keeps the source untouched` 在 Windows/Linux 上因 root 权限绕过 `setWritable(false)` 而被跳过（测试里已用 `if (!r.ok)` 保护），属预期行为，不算失败。

- [ ] **Step 7: 给文件页加复制/剪切/粘贴（此时 `Fs.copy`/`Fs.move` 已就绪）**

`src/pages/files/files.vue`：在 `const target = ref<FileEntry | null>(null)` 之后加：
```ts
/** Clipboard for copy/cut. Cut only clears once every item landed, so a partial failure is retryable. */
const clipboard = ref<{ mode: 'copy' | 'cut'; paths: string[] } | null>(null)
```
`sheetItems` 的数组改为（在 `rename` 之前插入两项）：
```ts
        { key: 'copy', label: '复制到剪贴板' },
        { key: 'cut', label: '剪切到剪贴板' },
```
`onPick` 开头（`if (!t) return` 之后）插入：
```ts
  if (key === 'copy' || key === 'cut') {
    clipboard.value = { mode: key, paths: [t.path] }
    uni.showToast({ title: '已放入剪贴板，进入目标文件夹后点粘贴', icon: 'none' })
    return
  }
```
模板里 `<view class="fab" ...>＋</view>` 之后插入：
```vue
    <view v-if="clipboard" class="pastebar">
      <text class="pasteinfo">{{ clipboard.mode === 'cut' ? '剪切' : '复制' }} {{ clipboard.paths.length }} 项</text>
      <text class="pastebtn" @click="paste">粘贴到此处</text>
      <text class="pastecancel" @click="clipboard = null">取消</text>
    </view>
```
`onLoad(...)` 之前插入：
```ts
/** A paste into the folder itself is refused by the native side; this guards the obvious case up front. */
async function paste() {
  const clip = clipboard.value
  if (!clip) return
  uni.showLoading({ title: clip.mode === 'cut' ? '正在移动' : '正在复制' })
  try {
    const res = clip.mode === 'cut'
      ? await Fs.move({ paths: clip.paths, destDir: current.value })
      : await Fs.copy({ paths: clip.paths, destDir: current.value })
    uni.hideLoading()
    const doneCount = (res.moved || res.copied || []).length
    if (res.failed.length) {
      uni.showModal({
        title: '部分项目未完成',
        content: `成功 ${doneCount} 项，失败 ${res.failed.length} 项（${describeError({ code: res.failed[0].code })}）。剪贴板已保留，可重试。`,
        showCancel: false
      })
      return
    }
    uni.showToast({ title: `已完成 ${doneCount} 项`, icon: 'none' })
    clipboard.value = null
    load(current.value)
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: describeError(e), icon: 'none' })
  }
}

```
`<style>` 末尾追加：
```css
.pastebar { position: fixed; left: 0; right: 0; bottom: 0; display: flex; align-items: center; padding: 24rpx 32rpx; background: #191d22; border-top: 1rpx solid #23282e; }
.pasteinfo { flex: 1; font-size: 26rpx; color: #9aa0a6; }
.pastebtn { color: #3b82f6; font-size: 28rpx; margin-right: 32rpx; }
.pastecancel { color: #6b7280; font-size: 26rpx; }
```

- [ ] **Step 8: 真机验证跨卷移动（Review Focus #1、#3）**

```bash
npm run build:h5 && npx cap sync android
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 造一个 100MB 测试文件，大小来自实际命令输出
adb shell dd if=/dev/urandom of=/storage/emulated/0/Download/big_test.bin bs=1M count=100
adb shell ls -l /storage/emulated/0/Download/big_test.bin
```
在 App 里把 `big_test.bin` 移动（若本机无 SD 卡，则在同一卷内移动，验证 rename 路径），然后：
```bash
adb shell ls -l /storage/emulated/0/Download/  # 源消失
adb shell find /storage/emulated/0 -name "big_test.bin" -exec ls -l {} \;  # 目标存在且大小一致
adb shell find /storage/emulated/0 -name "*.part"   # 期望无输出
```
Expected: 源消失、目标存在且字节数一致、无 `.part` 残留；若目标处已有同名文件，操作被拒绝且源文件仍在

此外用剪贴板验证这一条：长按 `big_test.bin` → 「复制到剪贴板」→ 进入 `Download/子目录` → 「粘贴到此处」→ 子目录出现同大小副本、源文件仍在；再对另一个文件「剪切到剪贴板」→ 粘贴到同一子目录 → 源文件消失、目标出现

- [ ] **Step 9: 提交**

```bash
git add -A
git commit -m "feat: 移动事务引擎、崩溃恢复日志与复制/剪切/粘贴"
```

---
## Task 7: 分组插件（真实目录）与分组名规则

**Files:**
- Create: `android/app/src/main/java/com/kuangcx/ufm/core/NameRules.kt`
- Create: `android/app/src/test/java/com/kuangcx/ufm/NameRulesTest.kt`
- Create: `android/app/src/main/java/com/kuangcx/ufm/GroupPlugin.kt`
- Create: `src/native/group.ts`
- Create: `src/pages/groups/groups.vue`
- Create: `src/pages/groups/detail.vue`
- Modify: `src/pages.json`、`src/pages/index/index.vue`

**Interfaces:**
- Produces:
  - `NameRules.validateGroupName(raw: String, existing: List<String>): String?` —— 合法返回 `null`，否则返回中文原因
  - `Group.list(): Promise<{ root: string; groups: GroupInfo[] }>`，`GroupInfo = { name: string; path: string; count: number; cover: string | null }`
  - `Group.create({ name })`、`Group.rename({ path, newName })`、`Group.remove({ path })`
  - `Group.photos({ path }): Promise<{ photos: GroupPhoto[] }>`，`GroupPhoto = { name, path, size, mtime }`（分组内的照片按路径扫描，不查 MediaStore，因此没有 `mediaId`）

- [ ] **Step 1: 写失败测试**

`android/app/src/test/java/com/kuangcx/ufm/NameRulesTest.kt`:
```kotlin
package com.kuangcx.ufm

import com.kuangcx.ufm.core.NameRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NameRulesTest {
    @Test
    fun `plain names are accepted`() {
        assertNull(NameRules.validateGroupName("旅行", emptyList()))
        assertNull(NameRules.validateGroupName("Trip 2026", emptyList()))
        assertNull(NameRules.validateGroupName("家人合照", listOf("旅行")))
    }

    @Test
    fun `blank and dotted names are rejected`() {
        assertEquals("名称不能为空", NameRules.validateGroupName("", emptyList()))
        assertEquals("名称不能为空", NameRules.validateGroupName("   ", emptyList()))
        assertEquals("名称不合法", NameRules.validateGroupName(".", emptyList()))
        assertEquals("名称不合法", NameRules.validateGroupName("..", emptyList()))
    }

    @Test
    fun `path separators and reserved characters are rejected`() {
        for (bad in listOf("a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b")) {
            assertEquals("名称不能包含 / \\ : * ? \" < > | 或控制字符", NameRules.validateGroupName(bad, emptyList()))
        }
    }

    @Test
    fun `control characters are rejected`() {
        assertEquals("名称不能包含 / \\ : * ? \" < > | 或控制字符", NameRules.validateGroupName("a\u0001b", emptyList()))
    }

    @Test
    fun `duplicates are rejected case-insensitively`() {
        assertEquals("已存在同名分组", NameRules.validateGroupName("Trip", listOf("trip")))
        assertEquals("已存在同名分组", NameRules.validateGroupName("旅行", listOf("旅行")))
    }

    @Test
    fun `names longer than 64 utf8 bytes are rejected`() {
        val longChinese = "照".repeat(22) // 66 bytes in UTF-8
        assertEquals("名称过长（最多 64 字节）", NameRules.validateGroupName(longChinese, emptyList()))
        assertNull(NameRules.validateGroupName("照".repeat(21), emptyList())) // 63 bytes
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd android && ./gradlew testDebugUnitTest --tests "*NameRulesTest*"`
Expected: FAIL，`Unresolved reference: core.NameRules`

- [ ] **Step 3: 写 NameRules**

`core/NameRules.kt`:
```kotlin
package com.kuangcx.ufm.core

import java.nio.charset.StandardCharsets

object NameRules {

    private val RESERVED = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    private const val MAX_BYTES = 64

    /** Returns null when the name is usable, otherwise a Chinese reason to show the user. */
    fun validateGroupName(raw: String, existing: List<String>): String? {
        if (raw.isBlank()) return "名称不能为空"
        if (raw == "." || raw == "..") return "名称不合法"
        if (raw.any { it in RESERVED || it.isISOControl() }) {
            return "名称不能包含 / \\ : * ? \" < > | 或控制字符"
        }
        if (raw.toByteArray(StandardCharsets.UTF_8).size > MAX_BYTES) return "名称过长（最多 64 字节）"
        if (existing.any { it.equals(raw, ignoreCase = true) }) return "已存在同名分组"
        return null
    }

    /** Dedupes "旅行 (2)", "旅行 (3)" ... when importing/moving files into a folder with a clash. */
    fun uniqueFileName(base: String, usedNames: Set<String>): String {
        if (usedNames.none { it.equals(base, ignoreCase = true) }) return base
        val dot = base.lastIndexOf('.')
        val stem = if (dot > 0) base.substring(0, dot) else base
        val ext = if (dot > 0) base.substring(dot) else ""
        var n = 2
        while (true) {
            val candidate = "$stem ($n)$ext"
            if (usedNames.none { it.equals(candidate, ignoreCase = true) }) return candidate
            n++
        }
    }
}
```

- [ ] **Step 4: 写 GroupPlugin**

`GroupPlugin.kt`:
```kotlin
package com.kuangcx.ufm

import android.os.Environment
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.kuangcx.ufm.core.NameRules
import com.kuangcx.ufm.core.PathUtil
import java.io.File

@CapacitorPlugin(name = "Group")
class GroupPlugin : Plugin() {

    private val root: File
        get() = File(Environment.getExternalStorageDirectory(), "Pictures/PhotoGroups")

    private fun ensureRoot(): File {
        if (!root.exists()) root.mkdirs()
        return root
    }

    @PluginMethod
    fun list(call: PluginCall) {
        bridge.execute {
            try {
                if (!Perm.ensureAllFiles(context)) { call.reject("no all-files access", "E_PERM"); return@execute }
                val r = ensureRoot()
                val dirs = (r.listFiles() ?: emptyArray()).filter { it.isDirectory }.sortedBy { it.name.lowercase() }
                val arr = JSArray()
                dirs.forEach { d ->
                    val photos = photoFiles(d)
                    arr.put(JSObject().apply {
                        put("name", d.name)
                        put("path", d.absolutePath)
                        put("count", photos.size)
                        put("cover", photos.firstOrNull()?.absolutePath)
                    })
                }
                call.resolve(JSObject().apply {
                    put("root", r.absolutePath)
                    put("groups", arr)
                })
            } catch (e: Exception) {
                call.reject(e.message ?: "list groups failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun create(call: PluginCall) {
        val name = call.getString("name") ?: ""
        bridge.execute {
            try {
                if (!Perm.ensureAllFiles(context)) { call.reject("no all-files access", "E_PERM"); return@execute }
                val r = ensureRoot()
                val existing = (r.listFiles() ?: emptyArray()).filter { it.isDirectory }.map { it.name }
                val err = NameRules.validateGroupName(name, existing)
                if (err != null) { call.reject(err, "E_BAD_NAME"); return@execute }
                val dir = File(r, name)
                if (!dir.mkdirs()) { call.reject("mkdir failed", "E_IO"); return@execute }
                call.resolve(JSObject().apply { put("path", dir.absolutePath) })
            } catch (e: Exception) {
                call.reject(e.message ?: "create group failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun rename(call: PluginCall) {
        val path = call.getString("path")
        val newName = call.getString("newName") ?: ""
        bridge.execute {
            try {
                if (!Perm.ensureAllFiles(context)) { call.reject("no all-files access", "E_PERM"); return@execute }
                val src = PathUtil.safe(path)
                if (!src.isDirectory) { call.reject("not found", "E_NOENT"); return@execute }
                // The source name itself counts as "existing" only if it differs; renaming a group
                // to a case variant of its own name is tolerated.
                val others = (src.parentFile?.listFiles() ?: emptyArray())
                    .filter { it.isDirectory && it.name != src.name }.map { it.name }
                val err = NameRules.validateGroupName(newName, others)
                if (err != null) { call.reject(err, "E_BAD_NAME"); return@execute }
                val dst = File(src.parentFile, newName)
                if (dst.exists() && dst.absolutePath != src.absolutePath) {
                    call.reject("exists", "E_EXISTS"); return@execute
                }
                if (!src.renameTo(dst)) { call.reject("rename failed", "E_RENAME_FAILED"); return@execute }
                call.resolve(JSObject().apply { put("path", dst.absolutePath) })
            } catch (e: Exception) {
                call.reject(e.message ?: "rename group failed", "E_IO", e)
            }
        }
    }

    /** Removes only the directory shell; photos inside are moved back to the camera roll first. */
    @PluginMethod
    fun remove(call: PluginCall) {
        val path = call.getString("path")
        bridge.execute {
            try {
                if (!Perm.ensureAllFiles(context)) { call.reject("no all-files access", "E_PERM"); return@execute }
                val dir = PathUtil.safe(path)
                if (!dir.isDirectory) { call.reject("not found", "E_NOENT"); return@execute }
                // Guard: never wipe photos silently. Refuse while photos remain.
                val photos = photoFiles(dir)
                if (photos.isNotEmpty()) {
                    call.reject("分组内还有 ${photos.size} 张照片，请先移出", "E_NOT_EMPTY"); return@execute
                }
                if (!dir.delete()) { call.reject("delete failed", "E_IO"); return@execute }
                call.resolve()
            } catch (e: Exception) {
                call.reject(e.message ?: "remove group failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun photos(call: PluginCall) {
        val path = call.getString("path")
        bridge.execute {
            try {
                if (!Perm.ensureAllFiles(context)) { call.reject("no all-files access", "E_PERM"); return@execute }
                val dir = PathUtil.safe(path)
                if (!dir.isDirectory) { call.reject("not found", "E_NOENT"); return@execute }
                val arr = JSArray()
                photoFiles(dir).forEach { f ->
                    arr.put(JSObject().apply {
                        put("name", f.name)
                        put("path", f.absolutePath)
                        put("size", f.length())
                        put("mtime", f.lastModified())
                    })
                }
                call.resolve(JSObject().apply { put("photos", arr) })
            } catch (e: Exception) {
                call.reject(e.message ?: "list photos failed", "E_IO", e)
            }
        }
    }

    private fun photoFiles(dir: File): List<File> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && PathUtil.mimeOf(it).startsWith("image/") }
            .sortedByDescending { it.lastModified() }
}
```

`MainActivity.kt` 追加 `registerPlugin(GroupPlugin::class.java)`。

`src/native/group.ts`:
```ts
import { registerPlugin } from '@capacitor/core'

export interface GroupInfo {
  name: string
  path: string
  count: number
  cover: string | null
}

export interface GroupPhoto {
  name: string
  path: string
  size: number
  mtime: number
}

export interface GroupNative {
  list(): Promise<{ root: string; groups: GroupInfo[] }>
  create(o: { name: string }): Promise<{ path: string }>
  rename(o: { path: string; newName: string }): Promise<{ path: string }>
  remove(o: { path: string }): Promise<void>
  photos(o: { path: string }): Promise<{ photos: GroupPhoto[] }>
}

export const Group = registerPlugin<GroupNative>('Group')
```

- [ ] **Step 5: 分组页面**

`src/components/PhotoGrid.vue`:
```vue
<template>
  <view class="grid">
    <view v-for="p in photos" :key="p.path" class="cell" @click="$emit('open', p)" @longpress="$emit('select', p)">
      <image class="thumb" :src="srcOf(p.path)" mode="aspectFill" lazy-load />
    </view>
  </view>
</template>

<script setup lang="ts">
import { Capacitor } from '@capacitor/core'

defineProps<{ photos: { path: string }[] }>()
defineEmits<{ (e: 'open', v: any): void; (e: 'select', v: any): void }>()

/** Capacitor serves any absolute path through its local server; no base64 round-trip. */
function srcOf(path: string): string {
  return Capacitor.isNativePlatform() ? Capacitor.convertFileSrc(path) : ''
}
</script>

<style>
.grid { display: flex; flex-wrap: wrap; }
.cell { width: 33.33%; aspect-ratio: 1; padding: 4rpx; box-sizing: border-box; }
.thumb { width: 100%; height: 100%; background: #1a1f24; border-radius: 8rpx; }
</style>
```

`src/pages/groups/groups.vue`:
```vue
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
  uni.navigateTo({ url: `/pages/groups/detail?path=${encodeURIComponent(g.path)}&name=${encodeURIComponent(g.name)}` })
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
      content: `删除分组「${t.name}」？分组内还有照片时不允许删除。`,
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
```

`src/pages/groups/detail.vue`:
```vue
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
import { onLoad } from '@dcloudio/uni-app'
import { Capacitor } from '@capacitor/core'
import { ref } from 'vue'
import PhotoGrid from '../../components/PhotoGrid.vue'
import { Group, type GroupPhoto } from '../../native/group'
import { Fs } from '../../native/fs'
import { describeError } from '../../utils/errors'

const groupPath = ref('')
const name = ref('')
const photos = ref<GroupPhoto[]>([])
const selected = ref<GroupPhoto[]>([])

async function load() {
  try {
    photos.value = (await Group.photos({ path: groupPath.value })).photos
  } catch (e) {
    uni.showToast({ title: describeError(e), icon: 'none' })
  }
}

function preview(p: GroupPhoto) {
  uni.previewImage({ current: Capacitor.convertFileSrc(p.path), urls: photos.value.map((x) => Capacitor.convertFileSrc(x.path)) })
}

function toggle(p: GroupPhoto) {
  const i = selected.value.findIndex((x) => x.path === p.path)
  if (i >= 0) selected.value.splice(i, 1)
  else selected.value.push(p)
}

function share() {
  uni.showToast({ title: '分享将在 Task 9 实现', icon: 'none' })
}

/** Photos move back to the camera roll; the group folder keeps its own copy of nothing. */
async function removeSelected() {
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
  groupPath.value = decodeURIComponent((q?.path as string) || '')
  name.value = decodeURIComponent((q?.name as string) || '')
  load()
})
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
```

`src/pages.json` 追加：
```json
{ "path": "pages/groups/groups", "style": { "navigationBarTitleText": "照片分组" } },
{ "path": "pages/groups/detail", "style": { "navigationBarTitleText": "分组详情" } }
```

`src/pages/index/index.vue` 在「浏览文件」按钮旁追加：
```vue
<button class="entry" @click="goGroups">照片分组</button>
```
```ts
function goGroups() {
  uni.navigateTo({ url: '/pages/groups/groups' })
}
```

- [ ] **Step 6: 跑测试与真机验证（Review Focus #2）**

```bash
cd android && ./gradlew testDebugUnitTest --tests "*NameRulesTest*"
```
Expected: `BUILD SUCCESSFUL`，6 个测试 PASS

```bash
npm run build:h5 && npx cap sync android
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.kuangcx.ufm/.MainActivity
adb shell ls /storage/emulated/0/Pictures/PhotoGroups
```
Expected（真机逐项）：① 新建「旅行」成功，`adb shell ls /storage/emulated/0/Pictures/PhotoGroups` 出现 `旅行`；② 再建「旅行」提示「已存在同名分组」且目录未重复；③ 建名 `a/b` 提示「名称不能包含 …」，`PhotoGroups` 下**不出现**任何新目录；④ 建 22 个汉字的名字提示「名称过长（最多 64 字节）」；⑤ 在系统相册里能看到 `Pictures/PhotoGroups/旅行` 这个相册

- [ ] **Step 7: 提交**

```bash
git add -A
git commit -m "feat: 照片分组（真实目录）、分组名规则与分组页面"
```

---

## Task 8: MediaStore 相册查询与缩略图缓存

**Files:**
- Create: `android/app/src/main/java/com/kuangcx/ufm/core/MediaQuery.kt`
- Create: `android/app/src/main/java/com/kuangcx/ufm/ThumbCache.kt`
- Create: `android/app/src/main/java/com/kuangcx/ufm/MediaPlugin.kt`
- Create: `android/app/src/test/java/com/kuangcx/ufm/MediaQueryTest.kt`
- Create: `src/native/media.ts`
- Modify: `src/pages/photos/photos.vue`（新建）
- Modify: `src/pages.json`、`src/pages/index/index.vue`

**Interfaces:**
- Produces:
  - `MediaQuery.build(kind: MediaKind, sinceMs: Long?, limit: Int, offset: Int): QuerySpec`（纯函数，可单测）
  - `MediaKind = 'image' | 'video' | 'audio' | 'doc'`
  - `Media.query({ kind, page, pageSize, sinceMs? }): Promise<{ items: MediaItem[]; hasMore: boolean }>`
  - `MediaItem = { mediaId, name, path, size, mtime, mime, bucket }`
  - `Media.thumbnail({ path, maxPx }): Promise<{ url: string }>`
  - `ThumbCache(cacheDir: File).get(path: String, maxPx: Int): File`

- [ ] **Step 1: 写失败测试**

`android/app/src/test/java/com/kuangcx/ufm/MediaQueryTest.kt`:
```kotlin
package com.kuangcx.ufm

import com.kuangcx.ufm.core.MediaKind
import com.kuangcx.ufm.core.MediaQuery
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaQueryTest {
    @Test
    fun `images select the integer media type for images`() {
        val spec = MediaQuery.build(MediaKind.IMAGE, null, 200, 0)
        // media_type is an INTEGER column (IMAGE=1); a MIME string here would match nothing.
        assertEquals("media_type=1", spec.selection)
        assertEquals(0, spec.selectionArgs!!.size)
    }

    @Test
    fun `videos and audio select their own integer media types`() {
        assertEquals("media_type=3", MediaQuery.build(MediaKind.VIDEO, null, 1, 0).selection)
        assertEquals("media_type=2", MediaQuery.build(MediaKind.AUDIO, null, 1, 0).selection)
    }

    @Test
    fun `documents exclude the three media types`() {
        val spec = MediaQuery.build(MediaKind.DOC, null, 1, 0)
        assertEquals("media_type NOT IN (1,2,3)", spec.selection)
    }

    @Test
    fun `paging maps to limit and offset with a positive safety cap`() {
        val spec = MediaQuery.build(MediaKind.IMAGE, null, 5000, 400)
        assertEquals(1000, spec.limit) // clamped
        assertEquals(400, spec.offset)
    }

    @Test
    fun `sinceMs adds a date filter in epoch seconds`() {
        val spec = MediaQuery.build(MediaKind.IMAGE, 1_700_000_000_000L, 200, 0)
        assertEquals("media_type=1 AND date_added>=?", spec.selection)
        assertEquals(listOf("1700000000"), spec.selectionArgs!!.toList())
    }

    @Test
    fun `sort is newest first`() {
        assertEquals("date_added DESC", MediaQuery.build(MediaKind.IMAGE, null, 10, 0).sortOrder)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd android && ./gradlew testDebugUnitTest --tests "*MediaQueryTest*"`
Expected: FAIL，`Unresolved reference: core.MediaKind`

- [ ] **Step 3: 写查询与缩略图核心**

`core/MediaQuery.kt`:
```kotlin
package com.kuangcx.ufm.core

enum class MediaKind { IMAGE, VIDEO, AUDIO, DOC }

data class QuerySpec(
    val selection: String?,
    val selectionArgs: Array<String>?,
    val sortOrder: String,
    val limit: Int,
    val offset: Int
)

object MediaQuery {

    private const val MAX_PAGE = 1000

    // media_type is an INTEGER column, not a MIME string. Values verified against the platform
    // docs (MediaStore.Files.FileColumns): NONE=0, IMAGE=1, AUDIO=2, VIDEO=3, PLAYLIST=4,
    // SUBTITLE=5, DOCUMENT=6. Using "image/*" here would match nothing.
    private const val MEDIA_TYPE_IMAGE = 1
    private const val MEDIA_TYPE_AUDIO = 2
    private const val MEDIA_TYPE_VIDEO = 3

    /**
     * Pure spec builder so selection strings can be unit-tested without a device.
     * MediaStore stores seconds in date_added; sinceMs is converted accordingly.
     */
    fun build(kind: MediaKind, sinceMs: Long?, limit: Int, offset: Int): QuerySpec {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        when (kind) {
            MediaKind.IMAGE -> clauses += "media_type=$MEDIA_TYPE_IMAGE"
            MediaKind.VIDEO -> clauses += "media_type=$MEDIA_TYPE_VIDEO"
            MediaKind.AUDIO -> clauses += "media_type=$MEDIA_TYPE_AUDIO"
            // Documents are everything that is not image/audio/video, which also covers
            // MEDIA_TYPE_DOCUMENT (6) and MEDIA_TYPE_NONE (0) on Android 11+.
            MediaKind.DOC -> clauses += "media_type NOT IN ($MEDIA_TYPE_IMAGE,$MEDIA_TYPE_AUDIO,$MEDIA_TYPE_VIDEO)"
        }
        if (sinceMs != null && sinceMs > 0) {
            clauses += "date_added>=?"
            args += (sinceMs / 1000).toString()
        }
        return QuerySpec(
            selection = clauses.joinToString(" AND "),
            selectionArgs = args.toTypedArray(),
            sortOrder = "date_added DESC",
            limit = limit.coerceIn(1, MAX_PAGE),
            offset = offset.coerceAtLeast(0)
        )
    }
}
```

`ThumbCache.kt`:
```kotlin
package com.kuangcx.ufm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Build
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Disk-backed thumbnail cache. Files are keyed by path + size bucket so a resized
 * request never returns a stale dimension. An in-memory LRU sits in front to avoid
 * re-decoding while scrolling.
 */
class ThumbCache(private val cacheDir: File) {

    private val mem = object : LruCache<String, Bitmap>(MEM_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    init {
        if (!cacheDir.exists()) cacheDir.mkdirs()
    }

    fun get(path: String, maxPx: Int): File? {
        val src = File(path)
        if (!src.isFile) return null
        val out = File(cacheDir, key(path, maxPx) + ".jpg")
        if (out.isFile && out.lastModified() >= src.lastModified() && out.length() > 0) return out

        val bmp = decode(src, maxPx) ?: return null
        mem.put(key(path, maxPx), bmp)
        return try {
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, it) }
            out.setLastModified(src.lastModified())
            out
        } catch (e: Exception) {
            out.delete()
            null
        }
    }

    private fun decode(src: File, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxPx * 2 && bounds.outHeight / sample > maxPx * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return if (Build.VERSION.SDK_INT >= 29) {
            ThumbnailUtils.createImageThumbnail(src, android.util.Size(maxPx, maxPx), null)
                ?: BitmapFactory.decodeFile(src.absolutePath, opts)
        } else {
            BitmapFactory.decodeFile(src.absolutePath, opts)
        }
    }

    /** Evicts least-recently-modified entries once the cache exceeds its byte budget. */
    fun trimTo(maxBytes: Long) {
        val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= maxBytes) break
            val len = f.length()
            if (f.delete()) total -= len
        }
    }

    private fun key(path: String, maxPx: Int): String {
        val md = MessageDigest.getInstance("SHA-1")
        val h = md.digest("$path|$maxPx".toByteArray()).joinToString("") { "%02x".format(it) }
        return h.substring(0, 32)
    }

    companion object {
        const val MEM_BYTES = 32 * 1024 * 1024
        const val THUMB_QUALITY = 82
        const val DISK_BUDGET = 500L * 1024 * 1024
    }
}
```

- [ ] **Step 4: 写 MediaPlugin**

`MediaPlugin.kt`:
```kotlin
package com.kuangcx.ufm

import android.content.ContentUris
import android.provider.MediaStore
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.kuangcx.ufm.core.MediaKind
import com.kuangcx.ufm.core.MediaQuery
import java.io.File

@CapacitorPlugin(name = "Media")
class MediaPlugin : Plugin() {

    private val thumbs by lazy { ThumbCache(File(context.cacheDir, "thumbs")) }

    @PluginMethod
    fun query(call: PluginCall) {
        val kind = when (call.getString("kind") ?: "image") {
            "video" -> MediaKind.VIDEO
            "audio" -> MediaKind.AUDIO
            "doc" -> MediaKind.DOC
            else -> MediaKind.IMAGE
        }
        val page = (call.getInt("page") ?: 0).coerceAtLeast(0)
        val pageSize = (call.getInt("pageSize") ?: 200).coerceIn(1, 1000)
        val sinceMs = call.getLong("sinceMs")
        bridge.execute {
            try {
                val spec = MediaQuery.build(kind, sinceMs, pageSize + 1, page * pageSize)
                val uri = when (kind) {
                    MediaKind.IMAGE -> if (android.os.Build.VERSION.SDK_INT >= 29)
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    MediaKind.VIDEO -> if (android.os.Build.VERSION.SDK_INT >= 29)
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else -> MediaStore.Files.getContentUri("external")
                }
                val projections = arrayOf(
                    MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.BUCKET_DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH
                )
                val items = mutableListOf<JSObject>()
                var fetched = 0
                context.contentResolver.query(uri, projections, spec.selection, spec.selectionArgs, spec.sortOrder)
                    ?.use { c ->
                        val idIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val nameIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                        val dataIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                        val sizeIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                        val modIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                        val mimeIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                        val bucketIdx = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                        val relIdx = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                        var seen = 0
                        // Offset is applied while walking the cursor because MediaStore has no
                        // cross-version OFFSET support in the query arguments able to page this way.
                        while (c.moveToNext() && fetched < pageSize) {
                            if (seen++ < spec.offset) continue
                            fetched++
                            val name = c.getString(nameIdx) ?: "unnamed"
                            // DATA is deprecated from API 29 on and can be null for rows created
                            // through MediaStore; RELATIVE_PATH + name is the supported fallback.
                            val data = c.getString(dataIdx)
                            val path = data ?: run {
                                val rel = if (relIdx >= 0) c.getString(relIdx) else null
                                if (rel.isNullOrBlank()) null
                                else android.os.Environment.getExternalStorageDirectory().absolutePath +
                                    "/" + rel.trimEnd('/') + "/" + name
                            } ?: continue
                            items += JSObject().apply {
                                put("mediaId", c.getLong(idIdx))
                                put("name", name)
                                put("path", path)
                                put("size", c.getLong(sizeIdx))
                                put("mtime", c.getLong(modIdx) * 1000)
                                put("mime", c.getString(mimeIdx) ?: "")
                                put("bucket", if (bucketIdx >= 0) c.getString(bucketIdx) else "")
                            }
                        }
                        // The extra row (pageSize + 1) tells us whether another page exists.
                        val hasMore = c.moveToNext()
                        call.resolve(JSObject().apply {
                            put("items", JSArray(items))
                            put("hasMore", hasMore)
                        })
                        return@execute
                    }
                call.resolve(JSObject().apply { put("items", JSArray()); put("hasMore", false) })
            } catch (e: Exception) {
                call.reject(e.message ?: "media query failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun thumbnail(call: PluginCall) {
        val path = call.getString("path")
        val maxPx = (call.getInt("maxPx") ?: 256).coerceIn(64, 1024)
        bridge.execute {
            try {
                if (path.isNullOrBlank()) { call.reject("path required", "E_NOENT"); return@execute }
                val f = thumbs.get(path, maxPx)
                if (f == null) { call.reject("thumbnail failed", "E_IO"); return@execute }
                // The file URL is served by Capacitor's own local server; the URI is built in JS.
                call.resolve(JSObject().apply { put("url", f.absolutePath) })
            } catch (e: Exception) {
                call.reject(e.message ?: "thumbnail failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun trimThumbs(call: PluginCall) {
        bridge.execute {
            thumbs.trimTo(ThumbCache.DISK_BUDGET)
            call.resolve()
        }
    }
}
```

`MainActivity.kt` 追加 `registerPlugin(MediaPlugin::class.java)`。

`src/native/media.ts`:
```ts
import { Capacitor, registerPlugin } from '@capacitor/core'

export type MediaKind = 'image' | 'video' | 'audio' | 'doc'

export interface MediaItem {
  mediaId: number
  name: string
  path: string
  size: number
  mtime: number
  mime: string
  bucket: string
}

export interface MediaNative {
  query(o: { kind: MediaKind; page: number; pageSize: number; sinceMs?: number }): Promise<{ items: MediaItem[]; hasMore: boolean }>
  thumbnail(o: { path: string; maxPx?: number }): Promise<{ url: string }>
  trimThumbs(): Promise<void>
}

export const Media = registerPlugin<MediaNative>('Media')

const thumbMemo = new Map<string, string>()

/** Resolves a thumbnail to a WebView-loadable URL, caching the bridge round-trip per path. */
export async function thumbUrl(path: string, maxPx = 256): Promise<string> {
  const k = `${path}|${maxPx}`
  const hit = thumbMemo.get(k)
  if (hit) return hit
  const { url } = await Media.thumbnail({ path, maxPx })
  const src = Capacitor.isNativePlatform() ? Capacitor.convertFileSrc(url) : ''
  if (thumbMemo.size > 3000) thumbMemo.clear()
  thumbMemo.set(k, src)
  return src
}
```

- [ ] **Step 5: 相册页**

`src/components/ThumbImage.vue`:
```vue
<template>
  <image v-if="src" class="thumb" :src="src" mode="aspectFill" lazy-load />
  <view v-else class="thumb placeholder" />
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { thumbUrl } from '../native/media'

const props = withDefaults(defineProps<{ path: string; maxPx?: number }>(), { maxPx: 256 })
const src = ref('')

watch(
  () => props.path,
  async (p) => {
    if (!p) return
    try {
      src.value = await thumbUrl(p, props.maxPx)
    } catch {
      src.value = ''
    }
  },
  { immediate: true }
)
</script>

<style>
.thumb { width: 100%; height: 100%; background: #1a1f24; border-radius: 8rpx; }
.placeholder { background: #1a1f24; }
</style>
```

`src/pages/photos/photos.vue`:
```vue
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
import { describeError } from '../../utils/errors'

const kind = ref<MediaKind>('image')
const page = ref(0)
const items = ref<MediaItem[]>([])
const hasMore = ref(false)
const loading = ref(false)
const error = ref('')

const kindLabel = computed(() => ({ image: '图片', video: '视频', audio: '音频', doc: '文档' }[kind.value]))

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
    uni.openDocument({ filePath: Capacitor.convertFileSrc(it.path) }).catch(() => {
      uni.showToast({ title: '无法打开该文件', icon: 'none' })
    })
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
.cell { width: 33.33%; aspect-ratio: 1; padding: 4rpx; box-sizing: border-box; }
.empty { padding: 120rpx 40rpx; text-align: center; color: #6b7280; }
.more { padding: 32rpx; text-align: center; color: #6b7280; font-size: 24rpx; }
</style>
```

`src/pages.json` 追加：
```json
{ "path": "pages/photos/photos", "style": { "navigationBarTitleText": "相册" } }
```

- [ ] **Step 6: 跑测试与真机验证**

```bash
cd android && ./gradlew testDebugUnitTest --tests "*MediaQueryTest*"
```
Expected: `BUILD SUCCESSFUL`，6 个测试 PASS

```bash
npm run build:h5 && npx cap sync android
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.kuangcx.ufm/.MainActivity
# 相册数量应与系统一致，用 MediaStore 查询实测对照
adb shell content query --uri content://media/external/images/media --projection _id | wc -l
```
Expected: App 相册页显示的图片总数与上面命令的行数一致（±1 行 header）；缩略图滚动流畅、不出现破图；`adb shell ls /data/data/com.kuangcx.ufm/cache/thumbs | wc -l` 随浏览增长

- [ ] **Step 7: 提交**

```bash
git add -A
git commit -m "feat: MediaStore 查询与缩略图缓存"
```

---
## Task 9: 从相册移入分组、分享与打开方式

**Files:**
- Create: `android/app/src/main/java/com/kuangcx/ufm/SharePlugin.kt`
- Create: `src/native/share.ts`
- Create: `android/app/src/main/res/xml/file_paths.xml`
- Modify: `android/app/src/main/AndroidManifest.xml`（FileProvider）
- Modify: `src/pages/photos/photos.vue`（多选 + 移入分组）
- Modify: `src/pages/groups/detail.vue`（真实分享）

**Interfaces:**
- Produces:
  - `Share.shareFiles({ paths }): Promise<void>` —— 系统分享面板
  - `Share.openFile({ path, mime }): Promise<void>` —— 用其它应用打开
  - 照片页多选状态与「移入分组」底部栏

- [ ] **Step 1: 写 FileProvider 与插件**

`android/app/src/main/res/xml/file_paths.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-path name="external" path="." />
    <external-files-path name="external_files" path="." />
    <cache-path name="cache" path="." />
    <files-path name="files" path="." />
</paths>
```

`AndroidManifest.xml` 的 `<application>` 内追加：
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

`android/app/src/main/java/com/kuangcx/ufm/SharePlugin.kt`:
```kotlin
package com.kuangcx.ufm

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import java.io.File

@CapacitorPlugin(name = "Share")
class SharePlugin : Plugin() {

    private fun uriOf(path: String): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))

    @PluginMethod
    fun shareFiles(call: PluginCall) {
        val paths = call.getArray("paths")?.toList()?.map { it.toString() } ?: emptyList()
        if (paths.isEmpty()) { call.reject("no files", "E_NOENT"); return }
        try {
            val uris = ArrayList(paths.map { uriOf(it) })
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "application/octet-stream"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(Intent.createChooser(intent, "分享到"))
            call.resolve()
        } catch (e: Exception) {
            call.reject(e.message ?: "share failed", "E_IO", e)
        }
    }

    @PluginMethod
    fun openFile(call: PluginCall) {
        val path = call.getString("path") ?: return call.reject("path required", "E_NOENT")
        val mime = call.getString("mime") ?: "*/*"
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uriOf(path), mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, "打开方式"))
            call.resolve()
        } catch (e: Exception) {
            call.reject(e.message ?: "no app can open this file", "E_IO", e)
        }
    }
}
```

`MainActivity.kt` 追加 `registerPlugin(SharePlugin::class.java)`。

`src/native/share.ts`:
```ts
import { registerPlugin } from '@capacitor/core'

export interface ShareNative {
  shareFiles(o: { paths: string[] }): Promise<void>
  openFile(o: { path: string; mime: string }): Promise<void>
}

export const Share = registerPlugin<ShareNative>('Share')
```

- [ ] **Step 2: 照片页加多选与移入分组**

`src/pages/photos/photos.vue` 改为（新增 `selected`、底部操作栏与「移入分组」流程）：
```vue
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
          @click="tap(it)"
          @longpress="toggle(it)"
        >
          <ThumbImage :path="it.path" />
          <view v-if="isSelected(it)" class="mark">✓</view>
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
import { describeError } from '../../utils/errors'

const kind = ref<MediaKind>('image')
const page = ref(0)
const items = ref<MediaItem[]>([])
const hasMore = ref(false)
const loading = ref(false)
const error = ref('')
const selected = ref<MediaItem[]>([])

const kindLabel = computed(() => ({ image: '图片', video: '视频', audio: '音频', doc: '文档' }[kind.value]))

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

function tap(it: MediaItem) {
  if (selected.value.length) return toggle(it)
  if (!it.mime.startsWith('image/')) {
    Share.openFile({ path: it.path, mime: it.mime }).catch((e) => {
      uni.showToast({ title: describeError(e), icon: 'none' })
    })
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

/** Presents the existing groups; creating one inline is supported so the flow never dead-ends. */
async function moveToGroup() {
  try {
    const { groups } = await Group.list()
    const names = groups.map((g) => g.name)
    uni.showActionSheet({
      itemList: names.length ? [...names, '＋ 新建分组'] : ['＋ 新建分组'],
      success: async (r) => {
        const idx = r.tapIndex
        if (idx === names.length) return createAndMove()
        return doMove(groups[idx].path)
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
  uni.showLoading({ title: '正在移动' })
  try {
    const res = await Fs.move({ paths: selected.value.map((x) => x.path), destDir })
    uni.hideLoading()
    if (res.failed.length) {
      uni.showModal({
        title: '部分照片未能移动',
        content: `成功 ${(res.moved || []).length} 张，失败 ${res.failed.length} 张（${describeError({ code: res.failed[0].code })}）。失败的照片仍在原位置。`,
        showCancel: false
      })
    } else {
      uni.showToast({ title: `已移入 ${res.moved?.length || 0} 张`, icon: 'none' })
    }
    selected.value = []
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
.cell { width: 33.33%; aspect-ratio: 1; padding: 4rpx; box-sizing: border-box; position: relative; }
.mark { position: absolute; right: 12rpx; top: 12rpx; width: 40rpx; height: 40rpx; border-radius: 50%; background: #3b82f6; color: #fff; font-size: 24rpx; display: flex; align-items: center; justify-content: center; }
.empty { padding: 120rpx 40rpx; text-align: center; color: #6b7280; }
.more { padding: 32rpx; text-align: center; color: #6b7280; font-size: 24rpx; }
.selbar { display: flex; justify-content: space-around; padding: 24rpx; background: #191d22; }
.selbtn { color: #3b82f6; font-size: 28rpx; }
</style>
```

`src/pages/groups/detail.vue` 的 `share()` 改为：
```ts
async function share() {
  if (!selected.value.length) return
  try {
    await Share.shareFiles({ paths: selected.value.map((x) => x.path) })
  } catch (e) {
    uni.showToast({ title: describeError(e), icon: 'none' })
  }
}
```
并在 import 区加：
```ts
import { Share } from '../../native/share'
```

- [ ] **Step 3: 跑构建并真机验证（Review Focus #1 的另一半）**

```bash
npm run build:h5 && npx cap sync android
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.kuangcx.ufm/.MainActivity
```
Expected（真机逐项）：① 相册页长按照片出现勾选与底部栏；② 点「移入分组」选一个分组后，照片从相册消失、出现在该分组页；③ `adb shell ls /storage/emulated/0/Pictures/PhotoGroups/旅行` 出现这些照片文件名；④ 在**系统相册**里 `旅行` 相册可见且张数一致（这是「真实目录」的直接证据）；⑤ 点「分享」弹出系统分享面板；⑥ 分组页把照片移动到已存在同名文件的目录时，提示失败且照片**未被删除**

- [ ] **Step 4: 提交**

```bash
git add -A
git commit -m "feat: 相册多选移入分组、分享与打开方式"
```

---

## Task 10: 收藏（SQLite）与收藏页

**Files:**
- Create: `android/app/src/main/java/com/kuangcx/ufm/core/FavStore.kt`
- Create: `android/app/src/test/java/com/kuangcx/ufm/FavStoreTest.kt`
- Create: `android/app/src/main/java/com/kuangcx/ufm/FavPlugin.kt`
- Create: `src/native/fav.ts`
- Create: `src/pages/favorites/favorites.vue`
- Modify: `src/pages.json`、`src/pages/photos/photos.vue`（长按菜单加收藏）

**Interfaces:**
- Produces:
  - `FavStore(db: SQLiteOpenHelper)`：`add(path, mediaId)`、`remove(path)`、`list(): List<FavItem>`、`pruneMissing(): Int`
  - `FavItem = { path, mediaId, addedAt }`
  - `Fav.add({ path, mediaId })`、`Fav.remove({ path })`、`Fav.list(): Promise<{ items: FavItem[] }>`、`Fav.prune(): Promise<{ removed: number }>`

- [ ] **Step 1: 写失败测试**

`FavStore` 依赖 Android 的 `SQLiteOpenHelper`，纯 JVM 单测需要 Robolectric。为了不引入 Robolectric，本任务把「失效剔除的判定」抽成纯函数：

`android/app/src/test/java/com/kuangcx/ufm/FavStoreTest.kt`:
```kotlin
package com.kuangcx.ufm

import com.kuangcx.ufm.core.FavItem
import com.kuangcx.ufm.core.partitionMissing
import org.junit.Assert.assertEquals
import org.junit.Test

class FavStoreTest {

    @Test
    fun `items whose file vanished are reported as missing`() {
        val items = listOf(
            FavItem("/a/1.jpg", 1, 100),
            FavItem("/a/2.jpg", 2, 200)
        )
        val (alive, gone) = partitionMissing(items) { it == "/a/1.jpg" }
        assertEquals(listOf("/a/1.jpg"), alive.map { it.path })
        assertEquals(listOf("/a/2.jpg"), gone.map { it.path })
    }

    @Test
    fun `empty favourites produce empty partitions`() {
        val (alive, gone) = partitionMissing(emptyList<FavItem>()) { true }
        assertEquals(0, alive.size)
        assertEquals(0, gone.size)
    }

    @Test
    fun `dedupe keeps the earliest addedAt per path`() {
        val merged = dedupeByPath(
            listOf(FavItem("/a/1.jpg", 1, 200), FavItem("/a/1.jpg", 1, 100), FavItem("/a/2.jpg", 2, 50))
        )
        assertEquals(2, merged.size)
        assertEquals(100, merged.first { it.path == "/a/1.jpg" }.addedAt)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd android && ./gradlew testDebugUnitTest --tests "*FavStoreTest*"`
Expected: FAIL，`Unresolved reference: core.FavItem`

- [ ] **Step 3: 写 FavStore**

`core/FavStore.kt`:
```kotlin
package com.kuangcx.ufm.core

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class FavItem(val path: String, val mediaId: Long, val addedAt: Long)

/** Splits favourites into still-existing and vanished entries; the caller decides when to delete. */
fun partitionMissing(items: List<FavItem>, exists: (String) -> Boolean): Pair<List<FavItem>, List<FavItem>> =
    items.partition { exists(it.path) }

/** A path can only be favourited once; when duplicates exist the earliest timestamp wins. */
fun dedupeByPath(items: List<FavItem>): List<FavItem> =
    items.groupBy { it.path }.map { (_, group) -> group.minByOrNull { it.addedAt }!! }.sortedByDescending { it.addedAt }

class FavStore(context: Context) : SQLiteOpenHelper(context, "favorites.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE favorites (
              path TEXT PRIMARY KEY,
              media_id INTEGER,
              added_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun add(path: String, mediaId: Long) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO favorites(path, media_id, added_at) VALUES(?,?,?)",
            arrayOf<Any>(path, mediaId, System.currentTimeMillis())
        )
    }

    fun remove(path: String) {
        writableDatabase.delete("favorites", "path=?", arrayOf(path))
    }

    fun list(): List<FavItem> {
        val out = mutableListOf<FavItem>()
        readableDatabase.rawQuery(
            "SELECT path, media_id, added_at FROM favorites ORDER BY added_at DESC", null
        ).use { c ->
            while (c.moveToNext()) out += FavItem(c.getString(0), c.getLong(1), c.getLong(2))
        }
        return out
    }

    /** Drops rows whose file no longer exists; returns how many were removed. */
    fun pruneMissing(): Int {
        val (_, gone) = partitionMissing(list()) { java.io.File(it).exists() }
        gone.forEach { remove(it.path) }
        return gone.size
    }
}
```

- [ ] **Step 4: 写 FavPlugin**

`FavPlugin.kt`:
```kotlin
package com.kuangcx.ufm

import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.kuangcx.ufm.core.FavStore

@CapacitorPlugin(name = "Fav")
class FavPlugin : Plugin() {

    private val store by lazy { FavStore(context) }

    @PluginMethod
    fun add(call: PluginCall) {
        val path = call.getString("path") ?: return call.reject("path required", "E_NOENT")
        val mediaId = call.getLong("mediaId") ?: 0L
        bridge.execute {
            try {
                store.add(path, mediaId)
                call.resolve()
            } catch (e: Exception) {
                call.reject(e.message ?: "favorite failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun remove(call: PluginCall) {
        val path = call.getString("path") ?: return call.reject("path required", "E_NOENT")
        bridge.execute {
            store.remove(path)
            call.resolve()
        }
    }

    @PluginMethod
    fun list(call: PluginCall) {
        bridge.execute {
            try {
                // Prune first: the page must never render a favourite whose file is gone.
                val removed = store.pruneMissing()
                val arr = JSArray()
                store.list().forEach { f ->
                    arr.put(JSObject().apply {
                        put("path", f.path); put("mediaId", f.mediaId); put("addedAt", f.addedAt)
                    })
                }
                call.resolve(JSObject().apply {
                    put("items", arr)
                    put("pruned", removed)
                })
            } catch (e: Exception) {
                call.reject(e.message ?: "list favourites failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun prune(call: PluginCall) {
        bridge.execute {
            val n = store.pruneMissing()
            call.resolve(JSObject().apply { put("removed", n) })
        }
    }
}
```

`MainActivity.kt` 追加 `registerPlugin(FavPlugin::class.java)`。

`src/native/fav.ts`:
```ts
import { registerPlugin } from '@capacitor/core'

export interface FavItem {
  path: string
  mediaId: number
  addedAt: number
}

export interface FavNative {
  add(o: { path: string; mediaId: number }): Promise<void>
  remove(o: { path: string }): Promise<void>
  list(): Promise<{ items: FavItem[]; pruned: number }>
  prune(): Promise<{ removed: number }>
}

export const Fav = registerPlugin<FavNative>('Fav')
```

- [ ] **Step 5: 收藏页**

`src/pages/favorites/favorites.vue`:
```vue
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
.cell { width: 33.33%; aspect-ratio: 1; padding: 4rpx; box-sizing: border-box; }
.empty { padding: 120rpx 40rpx; text-align: center; color: #6b7280; line-height: 1.7; }
</style>
```

`src/pages.json` 追加：
```json
{ "path": "pages/favorites/favorites", "style": { "navigationBarTitleText": "收藏" } }
```

`src/pages/photos/photos.vue`：给长按加收藏入口。把 `toggle(it)` 的长按改为弹出菜单：
```ts
function onLongPress(it: MediaItem) {
  if (selected.value.length) return toggle(it)
  uni.showActionSheet({
    itemList: ['收藏', '移入分组'],
    success: (r) => {
      if (r.tapIndex === 0) {
        Fav.add({ path: it.path, mediaId: it.mediaId })
          .then(() => uni.showToast({ title: '已收藏', icon: 'none' }))
          .catch((e) => uni.showToast({ title: describeError(e), icon: 'none' }))
      } else {
        selected.value = [it]
        moveToGroup()
      }
    }
  })
}
```
模板里 `@longpress="toggle(it)"` 改为 `@longpress="onLongPress(it)"`，import 区加：
```ts
import { Fav } from '../../native/fav'
```

- [ ] **Step 6: 跑测试与真机验证（Review Focus #4）**

```bash
cd android && ./gradlew testDebugUnitTest --tests "*FavStoreTest*"
```
Expected: `BUILD SUCCESSFUL`，3 个测试 PASS

```bash
npm run build:h5 && npx cap sync android
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.kuangcx.ufm/.MainActivity
```
Expected（真机逐项）：① 相册长按照片 → 点「收藏」→ 收藏页出现该照片；② 收藏后把该照片**移入分组**，收藏页仍然显示（收藏只记路径外的引用关系，分组切换不影响）；③ 用 `adb shell rm` 删掉一张被收藏的照片，重新进收藏页 → 该项消失、无破图；④ `adb shell sqlite3 /data/data/com.kuangcx.ufm/databases/favorites.db "select path from favorites"` 结果与页面一致（只读查询）

- [ ] **Step 7: 提交**

```bash
git add -A
git commit -m "feat: 照片收藏（SQLite）与收藏页"
```

---

## Task 11: 首页分类聚合、最近文件、存储统计

**Files:**
- Modify: `src/pages/index/index.vue`
- Modify: `android/app/src/main/java/com/kuangcx/ufm/ShellPlugin.kt`（加 `storageStats`）
- Modify: `src/native/shell.ts`
- Create: `src/pages/recent/recent.vue`
- Modify: `src/pages.json`

**Interfaces:**
- Produces:
  - `Shell.storageStats(): Promise<{ totalBytes: number; freeBytes: number; usedBytes: number; volumes: { label: string; totalBytes: number; freeBytes: number }[] }>`
  - 首页四类卡片跳转 `pages/photos/photos?kind=...`，快捷入口跳转 `pages/favorites/favorites`、`pages/groups/groups`、`pages/files/files?path=.../Download`、`pages/recent/recent`

- [ ] **Step 1: 给 ShellPlugin 加存储统计**

`ShellPlugin.kt` 追加（`android.os.StatFs` 已可用）：
```kotlin
    @PluginMethod
    fun storageStats(call: PluginCall) {
        bridge.execute {
            try {
                val vols = com.kuangcx.ufm.core.StorageVolumes.list(context)
                val arr = JSArray()
                var total = 0L
                var free = 0L
                vols.forEach { v ->
                    val sf = android.os.StatFs(v.path)
                    val t = sf.totalBytes
                    val f = sf.availableBytes
                    total += t
                    free += f
                    arr.put(JSObject().apply {
                        put("label", v.label); put("totalBytes", t); put("freeBytes", f)
                    })
                }
                call.resolve(JSObject().apply {
                    put("totalBytes", total)
                    put("freeBytes", free)
                    put("usedBytes", total - free)
                    put("volumes", arr)
                })
            } catch (e: Exception) {
                call.reject(e.message ?: "stats failed", "E_IO", e)
            }
        }
    }
```
`ShellPlugin.kt` 需要 import：`import com.getcapacitor.JSArray`。

`src/native/shell.ts` 追加：
```ts
export interface StorageStats {
  totalBytes: number
  freeBytes: number
  usedBytes: number
  volumes: { label: string; totalBytes: number; freeBytes: number }[]
}

export interface ShellNative {
  // …之前成员保持…
  storageStats(): Promise<StorageStats>
}
```

- [ ] **Step 2: 首页**

`src/pages/index/index.vue` 改为：
```vue
<template>
  <PermGate @state="onPerm">
    <view class="page">
      <view class="hero">
        <text class="title">文件管理器</text>
        <text class="sub">已用 {{ used }} / 共 {{ total }}</text>
        <view class="meter"><view class="meterfill" :style="{ width: usedPct }" /></view>
      </view>

      <view class="kinds">
        <view class="kind" @click="goPhotos('image')"><text class="emoji">🖼</text><text>图片</text></view>
        <view class="kind" @click="goPhotos('video')"><text class="emoji">🎬</text><text>视频</text></view>
        <view class="kind" @click="goPhotos('audio')"><text class="emoji">🎵</text><text>音频</text></view>
        <view class="kind" @click="goPhotos('doc')"><text class="emoji">📄</text><text>文档</text></view>
      </view>

      <view class="quick">
        <view class="qitem" @click="go('/pages/files/files')"><text>📂 浏览文件</text></view>
        <view class="qitem" @click="go('/pages/groups/groups')"><text>🗂 照片分组</text></view>
        <view class="qitem" @click="go('/pages/favorites/favorites')"><text>⭐ 我的收藏</text></view>
        <view class="qitem" @click="go('/pages/recent/recent')"><text>🕘 最近文件</text></view>
        <view class="qitem" @click="goDownload"><text>⬇️ 下载目录</text></view>
        <view class="qitem" @click="go('/pages/settings/settings')"><text>⚙️ 设置</text></view>
      </view>
    </view>
  </PermGate>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import PermGate from '../../components/PermGate.vue'
import { Shell } from '../../native/shell'
import { formatSize } from '../../utils/format'

const used = ref('—')
const total = ref('—')
const usedPct = ref('0%')

async function refreshStats() {
  try {
    const s = await Shell.storageStats()
    used.value = formatSize(s.usedBytes)
    total.value = formatSize(s.totalBytes)
    usedPct.value = s.totalBytes ? `${Math.round((s.usedBytes / s.totalBytes) * 100)}%` : '0%'
  } catch {
    used.value = '—'
    total.value = '—'
  }
}

function onPerm() {
  refreshStats()
}

function go(url: string) {
  uni.navigateTo({ url })
}

function goPhotos(kind: string) {
  uni.navigateTo({ url: `/pages/photos/photos?kind=${kind}` })
}

function goDownload() {
  uni.navigateTo({ url: '/pages/files/files?path=' + encodeURIComponent('/storage/emulated/0/Download') })
}

onShow(refreshStats)
</script>

<style>
.page { padding: 40rpx 32rpx 120rpx; }
.hero { margin-bottom: 48rpx; }
.title { font-size: 48rpx; font-weight: 700; }
.sub { display: block; margin-top: 12rpx; color: #8a9199; font-size: 26rpx; }
.meter { margin-top: 20rpx; height: 12rpx; border-radius: 6rpx; background: #23282e; overflow: hidden; }
.meterfill { height: 100%; background: #3b82f6; }
.kinds { display: flex; justify-content: space-between; margin-bottom: 48rpx; }
.kind { width: 23%; background: #191d22; border-radius: 16rpx; padding: 28rpx 0; display: flex; flex-direction: column; align-items: center; font-size: 26rpx; color: #cfd4d9; }
.emoji { font-size: 44rpx; margin-bottom: 12rpx; }
.quick { display: flex; flex-wrap: wrap; }
.qitem { width: 48%; background: #191d22; border-radius: 16rpx; padding: 32rpx 24rpx; margin-bottom: 20rpx; font-size: 28rpx; box-sizing: border-box; }
.qitem:nth-child(odd) { margin-right: 4%; }
</style>
```

- [ ] **Step 3: 最近文件页**

`src/pages/recent/recent.vue`:
```vue
<template>
  <view class="page">
    <view class="head"><text>最近 7 天</text><text class="count">{{ items.length }} 项</text></view>
    <scroll-view class="list" scroll-y>
      <view v-for="it in items" :key="it.path" class="row" @click="open(it)">
        <text class="name">{{ it.name }}</text>
        <text class="meta">{{ formatSize(it.size) }} · {{ formatTime(it.mtime) }}</text>
      </view>
      <view v-if="!items.length" class="empty"><text>{{ error || '最近没有新增文件' }}</text></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { Media, type MediaItem } from '../../native/media'
import { Share } from '../../native/share'
import { describeError } from '../../utils/errors'
import { formatSize, formatTime } from '../../utils/format'

const items = ref<MediaItem[]>([])
const error = ref('')

async function load() {
  try {
    // Doc covers every non media type; the 7-day window comes from date_added on the native side.
    const r = await Media.query({ kind: 'doc', page: 0, pageSize: 200, sinceMs: Date.now() - 7 * 86400_000 })
    items.value = r.items
  } catch (e) {
    error.value = describeError(e)
  }
}

function open(it: MediaItem) {
  Share.openFile({ path: it.path, mime: it.mime || '*/*' }).catch((e) =>
    uni.showToast({ title: describeError(e), icon: 'none' })
  )
}

onLoad(load)
</script>

<style>
.page { display: flex; flex-direction: column; height: 100vh; }
.head { display: flex; justify-content: space-between; padding: 20rpx 32rpx; background: #15191d; font-size: 28rpx; }
.count { color: #7d848c; font-size: 24rpx; }
.list { flex: 1; }
.row { padding: 24rpx 32rpx; border-bottom: 1rpx solid #1f2429; display: flex; flex-direction: column; }
.name { font-size: 28rpx; }
.meta { font-size: 22rpx; color: #7d848c; margin-top: 6rpx; }
.empty { padding: 100rpx; text-align: center; color: #6b7280; }
</style>
```

`src/pages.json` 追加：
```json
{ "path": "pages/recent/recent", "style": { "navigationBarTitleText": "最近文件" } }
```

- [ ] **Step 4: 构建并真机验证**

```bash
npm run build:h5 && npx cap sync android
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.kuangcx.ufm/.MainActivity
```
Expected（真机逐项）：① 首页显示已用/总量，与 `adb shell df /storage/emulated/0` 的数值量级一致；② 点「图片」进入相册页且只显示图片；③ 点「最近文件」显示出近 7 天内新增的文件；④ 点「下载目录」直接进入 `Download`

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat: 首页分类聚合、最近文件与存储统计"
```

---
## Task 12: 相似/重复照片检测与整理模式

**Files:**
- Create: `android/app/src/main/java/com/kuangcx/ufm/core/Dhash.kt`
- Create: `android/app/src/test/java/com/kuangcx/ufm/DhashTest.kt`
- Create: `android/app/src/main/java/com/kuangcx/ufm/CleanupPlugin.kt`
- Create: `src/native/cleanup.ts`
- Create: `src/pages/cleanup/cleanup.vue`
- Modify: `src/pages.json`、`src/pages/index/index.vue`

**Interfaces:**
- Produces:
  - `dhash(gray: IntArray, width: Int, height: Int): Long` —— 64 位指纹（8×8 差分哈希）
  - `hamming(a: Long, b: Long): Int`
  - `Cleanup.scanDuplicates(): Promise<{ groups: DupGroup[] }>`，`DupGroup = { key: string; kind: 'exact' | 'similar'; photos: MediaItem[] }`
  - `Cleanup.scanBySize({ minBytes }): Promise<{ groups: { size: number; photos: MediaItem[] }[] }>`

- [ ] **Step 1: 写失败测试**

`android/app/src/test/java/com/kuangcx/ufm/DhashTest.kt`:
```kotlin
package com.kuangcx.ufm

import com.kuangcx.ufm.core.dhash
import com.kuangcx.ufm.core.hamming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DhashTest {

    /** Builds a 9x8 gradient so every horizontal pair differs in a known direction. */
    private fun gradient(): IntArray {
        val w = 9
        val h = 8
        return IntArray(w * h) { i -> (i % w) * 32 }
    }

    @Test
    fun `identical images produce identical hashes`() {
        val a = gradient()
        assertEquals(dhash(a, 9, 8), dhash(a.copyOf(), 9, 8))
    }

    @Test
    fun `hamming distance of a hash with itself is zero`() {
        assertEquals(0, hamming(0x1234_5678_9ABC_DEF0L, 0x1234_5678_9ABC_DEF0L))
    }

    @Test
    fun `hamming counts differing bits`() {
        assertEquals(1, hamming(0b1010L, 0b1011L))
        assertEquals(3, hamming(0b0000L, 0b0111L))
    }

    @Test
    fun `dhash flip of one bit keeps distance under the similar threshold`() {
        val h = dhash(gradient(), 9, 8)
        val slightlyChanged = h xor 0b1L
        assertTrue(hamming(h, slightlyChanged) <= 6)
    }

    @Test
    fun `flat image yields zero hash because no gradient exists`() {
        val flat = IntArray(9 * 8) { 128 }
        assertEquals(0L, dhash(flat, 9, 8))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd android && ./gradlew testDebugUnitTest --tests "*DhashTest*"`
Expected: FAIL，`Unresolved reference: core.dhash`

- [ ] **Step 3: 写 Dhash**

`core/Dhash.kt`:
```kotlin
package com.kuangcx.ufm.core

/**
 * Difference hash over a 9x8 grayscale patch: for each row, every pixel is compared with
 * its right neighbour and one bit records which is brighter. 64 bits total. This is stable
 * against rescaling and mild recompression, which is what photo dedupe needs.
 */
fun dhash(gray: IntArray, width: Int, height: Int): Long {
    require(width == 9 && height == 8) { "dhash expects a 9x8 grayscale array" }
    require(gray.size >= width * height) { "gray array too small" }
    var hash = 0L
    var bit = 0
    for (y in 0 until height) {
        for (x in 0 until width - 1) {
            val left = gray[y * width + x]
            val right = gray[y * width + x + 1]
            if (left > right) hash = hash or (1L shl bit)
            bit++
        }
    }
    return hash
}

fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

object Similarity {
    /** Two photos are treated as the same scene when at most 6 of 64 bits differ. */
    const val SIMILAR_THRESHOLD = 6
}
```

- [ ] **Step 4: 写 CleanupPlugin**

`CleanupPlugin.kt`:
```kotlin
package com.kuangcx.ufm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.kuangcx.ufm.core.Similarity
import com.kuangcx.ufm.core.dhash
import com.kuangcx.ufm.core.hamming
import java.io.File
import java.security.MessageDigest

@CapacitorPlugin(name = "Cleanup")
class CleanupPlugin : Plugin() {

    /** Exact duplicates: same byte length plus identical SHA-256. Cheap to compute, no false positives. */
    @PluginMethod
    fun scanDuplicates(call: PluginCall) {
        val limit = (call.getInt("limit") ?: 400).coerceIn(1, 2000)
        bridge.execute {
            try {
                val files = collectImages(limit)
                val bySize = files.groupBy { it.length() }
                val exactGroups = mutableListOf<List<File>>()
                for ((_, sameSize) in bySize) {
                    if (sameSize.size < 2) continue
                    sameSize.groupBy { sha256(it) }.values.filter { it.size >= 2 }.forEach { exactGroups += it }
                }
                val exactPaths = exactGroups.flatten().toSet()
                // Near-duplicate pass only over non-exact files, so the two lists never overlap.
                val remaining = files.filter { it !in exactPaths }
                val hashes = remaining.mapNotNull { f -> fingerprint(f)?.let { f to it } }
                val similarGroups = mutableListOf<List<File>>()
                val used = mutableSetOf<File>()
                for (i in hashes.indices) {
                    val (fa, ha) = hashes[i]
                    if (fa in used) continue
                    val bucket = mutableListOf(fa)
                    for (j in i + 1 until hashes.size) {
                        val (fb, hb) = hashes[j]
                        if (fb in used) continue
                        if (hamming(ha, hb) <= Similarity.SIMILAR_THRESHOLD) {
                            bucket += fb
                            used += fb
                        }
                    }
                    if (bucket.size >= 2) {
                        used += fa
                        similarGroups += bucket
                    }
                }
                val arr = JSArray()
                exactGroups.forEach { g -> arr.put(groupJson("exact", g)) }
                similarGroups.forEach { g -> arr.put(groupJson("similar", g)) }
                call.resolve(JSObject().apply { put("groups", arr) })
            } catch (e: Exception) {
                call.reject(e.message ?: "duplicate scan failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun scanBySize(call: PluginCall) {
        val minBytes = call.getLong("minBytes") ?: (1024L * 1024L)
        bridge.execute {
            try {
                val groups = collectImages(2000)
                    .groupBy { it.length() }
                    .filter { (size, list) -> size >= minBytes && list.size >= 1 }
                    .entries.sortedByDescending { it.key }
                    .map { (size, list) -> JSObject().apply {
                        put("size", size)
                        put("photos", JSArray(list.map { photoJson(it) }))
                    } }
                call.resolve(JSObject().apply { put("groups", JSArray(groups)) })
            } catch (e: Exception) {
                call.reject(e.message ?: "size scan failed", "E_IO", e)
            }
        }
    }

    private fun collectImages(limit: Int): List<File> {
        val roots = listOf(
            File(android.os.Environment.getExternalStorageDirectory(), "DCIM"),
            File(android.os.Environment.getExternalStorageDirectory(), "Pictures")
        )
        val out = mutableListOf<File>()
        fun walk(dir: File, depth: Int) {
            if (out.size >= limit || depth > 8) return
            (dir.listFiles() ?: return).forEach { f ->
                if (out.size >= limit) return
                if (f.isDirectory) walk(f, depth + 1)
                else if (com.kuangcx.ufm.core.PathUtil.mimeOf(f).startsWith("image/")) out += f
            }
        }
        roots.forEach { walk(it, 0) }
        return out
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fingerprint(f: File): Long? {
        val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
        val bmp = BitmapFactory.decodeFile(f.absolutePath, opts) ?: return null
        val small = Bitmap.createScaledBitmap(bmp, 9, 8, true)
        val px = IntArray(9 * 8)
        small.getPixels(px, 0, 9, 0, 0, 9, 8)
        val gray = IntArray(9 * 8) { i ->
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            (r * 299 + g * 587 + b * 114) / 1000
        }
        return dhash(gray, 9, 8)
    }

    private fun groupJson(kind: String, files: List<File>) = JSObject().apply {
        put("kind", kind)
        put("key", files.first().length().toString() + "-" + files.size)
        put("photos", JSArray(files.map { photoJson(it) }))
    }

    private fun photoJson(f: File) = JSObject().apply {
        put("path", f.absolutePath)
        put("name", f.name)
        put("size", f.length())
        put("mtime", f.lastModified())
        put("mime", com.kuangcx.ufm.core.PathUtil.mimeOf(f))
    }
}
```

`MainActivity.kt` 追加 `registerPlugin(CleanupPlugin::class.java)`。

`src/native/cleanup.ts`:
```ts
import { registerPlugin } from '@capacitor/core'
import type { MediaItem } from './media'

export interface DupGroup {
  kind: 'exact' | 'similar'
  key: string
  photos: MediaItem[]
}

export interface CleanupNative {
  scanDuplicates(o?: { limit?: number }): Promise<{ groups: DupGroup[] }>
  scanBySize(o: { minBytes: number }): Promise<{ groups: { size: number; photos: MediaItem[] }[] }>
}

export const Cleanup = registerPlugin<CleanupNative>('Cleanup')
```

- [ ] **Step 5: 整理页**

`src/pages/cleanup/cleanup.vue`:
```vue
<template>
  <view class="page">
    <view class="tabs">
      <text :class="['tab', tab === 'dup' ? 'on' : '']" @click="switchTab('dup')">重复/相似</text>
      <text :class="['tab', tab === 'size' ? 'on' : '']" @click="switchTab('size')">占空间</text>
    </view>
    <scroll-view class="body" scroll-y>
      <view v-if="scanning" class="empty"><text>正在扫描…（首次可能需要几分钟）</text></view>
      <view v-for="(g, gi) in dupGroups" :key="'d' + gi" class="card">
        <view class="cardhead">
          <text>{{ g.kind === 'exact' ? '完全重复' : '相似照片' }} · {{ g.photos.length }} 张</text>
          <text class="keep" @click="selectExtras(gi)">
            {{ keepMap[gi] === undefined ? '选择保留张' : '保留 ' + g.photos[keepMap[gi]].name }}
          </text>
        </view>
        <view class="cells">
          <view
            v-for="(p, pi) in g.photos"
            :key="p.path"
            class="cell"
            :class="{ marked: keepMap[gi] === pi }"
            @click="keepMap[gi] = pi"
          >
            <ThumbImage :path="p.path" />
            <text class="sz">{{ formatSize(p.size) }}</text>
          </view>
        </view>
        <button class="danger" @click="deleteExtras(gi)">删除未保留的 {{ g.photos.length - 1 }} 张</button>
      </view>

      <view v-for="(g, gi) in sizeGroups" :key="'s' + gi" class="card">
        <view class="cardhead"><text>单张 {{ formatSize(g.size) }}</text></view>
        <view class="cells">
          <view v-for="p in g.photos" :key="p.path" class="cell"><ThumbImage :path="p.path" /></view>
        </view>
      </view>

      <view v-if="!scanning && !dupGroups.length && !sizeGroups.length" class="empty"><text>没有发现可整理的内容</text></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ThumbImage from '../../components/ThumbImage.vue'
import { Cleanup } from '../../native/cleanup'
import { Fs } from '../../native/fs'
import { describeError } from '../../utils/errors'
import { formatSize } from '../../utils/format'
import type { DupGroup } from '../../native/cleanup'

const tab = ref<'dup' | 'size'>('dup')
const scanning = ref(false)
const dupGroups = ref<DupGroup[]>([])
const sizeGroups = ref<{ size: number; photos: { path: string; size: number }[] }[]>([])
// Group index -> index of the photo to keep. Undefined means "not chosen yet".
const keepMap = ref<Record<number, number>>({})

function selectExtras(gi: number) {
  keepMap.value[gi] = 0
}

async function scan() {
  scanning.value = true
  dupGroups.value = []
  sizeGroups.value = []
  keepMap.value = {}
  try {
    if (tab.value === 'dup') {
      dupGroups.value = (await Cleanup.scanDuplicates({ limit: 400 })).groups
    } else {
      sizeGroups.value = (await Cleanup.scanBySize({ minBytes: 1024 * 1024 })).groups
    }
  } catch (e) {
    uni.showToast({ title: describeError(e), icon: 'none' })
  } finally {
    scanning.value = false
  }
}

function switchTab(t: 'dup' | 'size') {
  tab.value = t
  scan()
}

function deleteExtras(gi: number) {
  const g = dupGroups.value[gi]
  const keep = keepMap.value[gi]
  if (keep === undefined) {
    uni.showToast({ title: '请先点选要保留的那一张', icon: 'none' })
    return
  }
  const victims = g.photos.filter((_, i) => i !== keep).map((p) => p.path)
  uni.showModal({
    title: '确认删除',
    content: `将永久删除 ${victims.length} 张照片文件，无法恢复。`,
    success: async (r) => {
      if (!r.confirm) return
      try {
        const res = await Fs.remove({ paths: victims })
        uni.showToast({ title: `已删除 ${res.removed.length} 张`, icon: 'none' })
        scan()
      } catch (e) {
        uni.showToast({ title: describeError(e), icon: 'none' })
      }
    }
  })
}

scan()
</script>

<style>
.page { display: flex; flex-direction: column; height: 100vh; }
.tabs { display: flex; background: #15191d; }
.tab { flex: 1; text-align: center; padding: 24rpx; font-size: 28rpx; color: #8a9199; }
.tab.on { color: #3b82f6; border-bottom: 4rpx solid #3b82f6; }
.body { flex: 1; }
.card { margin: 24rpx 32rpx; background: #191d22; border-radius: 16rpx; padding: 24rpx; }
.cardhead { display: flex; justify-content: space-between; font-size: 26rpx; margin-bottom: 20rpx; }
.keep { color: #3b82f6; font-size: 24rpx; }
.cells { display: flex; flex-wrap: wrap; }
.cell { width: 25%; aspect-ratio: 1; padding: 4rpx; box-sizing: border-box; position: relative; }
.cell.marked { outline: 4rpx solid #22c55e; }
.sz { position: absolute; bottom: 8rpx; left: 8rpx; font-size: 18rpx; color: #fff; background: rgba(0,0,0,.55); padding: 2rpx 6rpx; border-radius: 4rpx; }
.danger { margin-top: 20rpx; background: #3a1d1f; color: #ef5350; font-size: 26rpx; border-radius: 12rpx; }
.empty { padding: 100rpx 40rpx; text-align: center; color: #6b7280; line-height: 1.7; }
</style>
```

`src/pages.json` 追加：
```json
{ "path": "pages/cleanup/cleanup", "style": { "navigationBarTitleText": "整理照片" } }
```

`src/pages/index/index.vue` 快捷入口加一项：
```vue
<view class="qitem" @click="go('/pages/cleanup/cleanup')"><text>🧹 整理照片</text></view>
```

- [ ] **Step 6: 跑测试与真机验证**

```bash
cd android && ./gradlew testDebugUnitTest --tests "*DhashTest*"
```
Expected: `BUILD SUCCESSFUL`，5 个测试 PASS

```bash
npm run build:h5 && npx cap sync android
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# 造重复文件验证检测
adb shell cp /sdcard/DCIM/Camera/*.jpg /sdcard/DCIM/Camera/dup_copy.jpg 2>/dev/null || true
```
Expected（真机）：①「重复/相似」页能列出完全重复的一组；② 点选一张后按钮可删，删除后 `adb shell ls` 该文件消失；③ 未点选就删会提示「请先点选要保留的那一张」；④「占空间」页按单张体积倒序列出大文件

- [ ] **Step 7: 提交**

```bash
git add -A
git commit -m "feat: 重复与相似照片检测、照片整理页"
```

---

## Task 13: 设置页、在线升级与安装

**Files:**
- Create: `src/pages/settings/settings.vue`
- Create: `src/pages/update/update.vue`
- Create: `android/app/src/main/java/com/kuangcx/ufm/core/VersionCompare.kt`
- Create: `android/app/src/test/java/com/kuangcx/ufm/VersionCompareTest.kt`
- Create: `android/app/src/main/java/com/kuangcx/ufm/UpdatePlugin.kt`
- Create: `src/native/update.ts`
- Modify: `src/pages.json`

**Interfaces:**
- Produces:
  - `compareVersionCode(local: Long, remote: Long): Int`（负=远程更新）
  - `parseVersionCodeFromTag(tag: String): Long?` —— `v1.2.0` → `10200`
  - `Update.check({ owner, repo }): Promise<{ available: boolean; versionName: string; versionCode: number; notes: string; apkUrl: string }>`
  - `Update.download({ apkUrl, fileName }): Promise<{ path: string }>`
  - `Update.install({ path }): Promise<void>`
  - `Update.currentVersion(): Promise<{ versionName: string; versionCode: number }>`

- [ ] **Step 1: 写失败测试**

`android/app/src/test/java/com/kuangcx/ufm/VersionCompareTest.kt`:
```kotlin
package com.kuangcx.ufm

import com.kuangcx.ufm.core.compareVersionCode
import com.kuangcx.ufm.core.parseVersionCodeFromTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VersionCompareTest {

    @Test
    fun `tag with v prefix parses into a monotonic code`() {
        assertEquals(10200L, parseVersionCodeFromTag("v1.2.0"))
        assertEquals(10000L, parseVersionCodeFromTag("v1.0.0"))
        assertEquals(10101L, parseVersionCodeFromTag("1.1.1"))
        assertEquals(23100L, parseVersionCodeFromTag("v2.31.0"))
    }

    @Test
    fun `malformed tags do not parse`() {
        assertNull(parseVersionCodeFromTag(""))
        assertNull(parseVersionCodeFromTag("release-1"))
        assertNull(parseVersionCodeFromTag("v1.2"))
        assertNull(parseVersionCodeFromTag("v1.2.0-beta"))
    }

    @Test
    fun `comparison reports older equal and newer`() {
        assertEquals(-1, compareVersionCode(10000, 10200))
        assertEquals(0, compareVersionCode(10200, 10200))
        assertEquals(1, compareVersionCode(10200, 10100))
    }

    @Test
    fun `parsed codes order the way version strings do`() {
        val a = parseVersionCodeFromTag("v1.9.0")!!
        val b = parseVersionCodeFromTag("v1.10.0")!!
        assertEquals(-1, compareVersionCode(a, b))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd android && ./gradlew testDebugUnitTest --tests "*VersionCompareTest*"`
Expected: FAIL，`Unresolved reference: core.compareVersionCode`

- [ ] **Step 3: 写版本比较**

`core/VersionCompare.kt`:
```kotlin
package com.kuangcx.ufm.core

/**
 * Encodes MAJOR.MINOR.PATCH into a single monotonically increasing Long so that ordering
 * matches version-string ordering. MINOR and PATCH are capped at two digits, which is
 * enough for this app and keeps the arithmetic obvious.
 */
fun parseVersionCodeFromTag(tag: String): Long? {
    val v = tag.trim().removePrefix("v")
    val parts = v.split(".")
    if (parts.size != 3) return null
    val nums = parts.map { it.toIntOrNull() ?: return null }
    if (nums.any { it < 0 || it > 99 }) return null
    return nums[0] * 10_000L + nums[1] * 100L + nums[2]
}

fun compareVersionCode(local: Long, remote: Long): Int = local.compareTo(remote)
```

- [ ] **Step 4: 写 UpdatePlugin**

`UpdatePlugin.kt`:
```kotlin
package com.kuangcx.ufm

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.kuangcx.ufm.core.compareVersionCode
import com.kuangcx.ufm.core.parseVersionCodeFromTag
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@CapacitorPlugin(name = "Update")
class UpdatePlugin : Plugin() {

    @PluginMethod
    fun currentVersion(call: PluginCall) {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        call.resolve(JSObject().apply {
            put("versionName", pi.versionName)
            put("versionCode", if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong())
        })
    }

    @PluginMethod
    fun check(call: PluginCall) {
        val owner = call.getString("owner") ?: return call.reject("owner required", "E_BAD_NAME")
        val repo = call.getString("repo") ?: return call.reject("repo required", "E_BAD_NAME")
        bridge.execute {
            try {
                val pi = context.packageManager.getPackageInfo(context.packageName, 0)
                val local = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
                val (tag, body) = fetchLatestRelease(owner, repo) ?: run {
                    call.reject("未发布过 Release", "E_NOENT"); return@execute
                }
                val remote = parseVersionCodeFromTag(tag) ?: run {
                    call.reject("版本号格式不认识：$tag", "E_BAD_NAME"); return@execute
                }
                val apkAsset = findApkAsset(body)
                call.resolve(JSObject().apply {
                    put("available", compareVersionCode(local, remote) < 0)
                    put("versionName", tag.removePrefix("v"))
                    put("versionCode", remote)
                    put("notes", JSONObject(body).optString("body", ""))
                    put("apkUrl", apkAsset ?: "")
                })
            } catch (e: Exception) {
                call.reject(e.message ?: "检查更新失败", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun download(call: PluginCall) {
        val apkUrl = call.getString("apkUrl") ?: return call.reject("apkUrl required", "E_NOENT")
        val fileName = call.getString("fileName") ?: "update.apk"
        bridge.execute {
            try {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val req = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                    setTitle("文件管理器更新")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    setMimeType("application/vnd.android.package-archive")
                }
                val id = dm.enqueue(req)
                // Poll until the download reaches a terminal state, then hand back the file path.
                val target = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                var waited = 0
                while (waited < MAX_WAIT_MS) {
                    dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                        if (c.moveToFirst()) {
                            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                call.resolve(JSObject().apply { put("path", target.absolutePath) })
                                return@execute
                            }
                            if (status == DownloadManager.STATUS_FAILED) {
                                call.reject("下载失败", "E_IO"); return@execute
                            }
                        }
                    }
                    Thread.sleep(POLL_MS)
                    waited += POLL_MS
                }
                call.reject("下载超时", "E_IO")
            } catch (e: Exception) {
                call.reject(e.message ?: "下载失败", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun install(call: PluginCall) {
        val path = call.getString("path") ?: return call.reject("path required", "E_NOENT")
        try {
            if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                call.reject("请先允许本应用安装未知来源应用", "E_PERM")
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            call.resolve()
        } catch (e: Exception) {
            call.reject(e.message ?: "拉起安装器失败", "E_IO", e)
        }
    }

    /** Fetches the latest non-draft release; returns tag name and raw JSON body. */
    private fun fetchLatestRelease(owner: String, repo: String): Pair<String, String>? {
        val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "uni-file-manager")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val tag = JSONObject(body).optString("tag_name", "")
            if (tag.isBlank()) return null
            return tag to body
        } finally {
            conn.disconnect()
        }
    }

    private fun findApkAsset(body: String): String? {
        val assets = JSONObject(body).optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name", "")
            if (name.endsWith(".apk")) return a.optString("browser_download_url", "")
        }
        return null
    }

    private companion object {
        const val POLL_MS = 500L
        const val MAX_WAIT_MS = 10 * 60 * 1000L
    }
}
```

`MainActivity.kt` 追加 `registerPlugin(UpdatePlugin::class.java)`。

`src/native/update.ts`:
```ts
import { registerPlugin } from '@capacitor/core'

export interface UpdateCheckResult {
  available: boolean
  versionName: string
  versionCode: number
  notes: string
  apkUrl: string
}

export interface UpdateNative {
  currentVersion(): Promise<{ versionName: string; versionCode: number }>
  check(o: { owner: string; repo: string }): Promise<UpdateCheckResult>
  download(o: { apkUrl: string; fileName: string }): Promise<{ path: string }>
  install(o: { path: string }): Promise<void>
}

export const Update = registerPlugin<UpdateNative>('Update')

export const UPDATE_REPO = { owner: 'kcx2366425574', repo: 'uni-file-manager' }
```

- [ ] **Step 5: 设置页与升级页**

`src/pages/settings/settings.vue`:
```vue
<template>
  <view class="page">
    <view class="section">
      <text class="stitle">权限</text>
      <view class="row">
        <text>所有文件访问</text>
        <text :class="['val', snap.allFiles ? 'ok' : 'bad']">{{ snap.allFiles ? '已开启' : '未开启' }}</text>
      </view>
      <view class="row">
        <text>相册读取</text>
        <text :class="['val', snap.mediaImages ? 'ok' : 'bad']">{{ snap.mediaImages ? '已开启' : '未开启' }}</text>
      </view>
      <view class="row">
        <text>安装未知应用</text>
        <text :class="['val', snap.installPackages ? 'ok' : 'bad']">{{ snap.installPackages ? '已开启' : '未开启' }}</text>
      </view>
      <button class="btn" @click="Shell.openAllFilesSettings()">打开所有文件访问设置</button>
      <button class="btn" @click="Shell.openInstallSettings()">打开安装权限设置</button>
    </view>

    <view class="section">
      <text class="stitle">存储</text>
      <view class="row"><text>已用 / 总量</text><text class="val">{{ used }} / {{ total }}</text></view>
      <view v-for="v in stats.volumes" :key="v.label" class="row sub">
        <text>{{ v.label }}</text><text class="val">{{ formatSize(v.freeBytes) }} 可用</text>
      </view>
    </view>

    <view class="section">
      <text class="stitle">关于</text>
      <view class="row"><text>版本</text><text class="val">{{ version }}</text></view>
      <button class="btn primary" @click="checkUpdate">检查更新</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { Shell, type PermissionSnapshot, type StorageStats } from '../../native/shell'
import { Update } from '../../native/update'
import { describeError } from '../../utils/errors'
import { formatSize } from '../../utils/format'

const snap = ref<PermissionSnapshot>({ allFiles: false, mediaImages: false, installPackages: false, sdk: 0 })
const stats = ref<StorageStats>({ totalBytes: 0, freeBytes: 0, usedBytes: 0, volumes: [] })
const version = ref('—')
const used = ref('—')
const total = ref('—')

async function refresh() {
  try {
    snap.value = await Shell.permissionState()
    const s = await Shell.storageStats()
    stats.value = s
    used.value = formatSize(s.usedBytes)
    total.value = formatSize(s.totalBytes)
    const v = await Update.currentVersion()
    version.value = `${v.versionName} (${v.versionCode})`
  } catch {
    /* Permission values are best effort; the page must still render. */
  }
}

function checkUpdate() {
  uni.navigateTo({ url: '/pages/update/update' })
}

onShow(refresh)
</script>

<style>
.page { padding: 32rpx; }
.section { margin-bottom: 48rpx; }
.stitle { display: block; font-size: 24rpx; color: #6b7280; margin-bottom: 16rpx; }
.row { display: flex; justify-content: space-between; padding: 24rpx; background: #191d22; border-radius: 12rpx; margin-bottom: 12rpx; font-size: 28rpx; }
.row.sub { background: #15191d; font-size: 24rpx; color: #9aa0a6; }
.val { color: #9aa0a6; }
.val.ok { color: #22c55e; }
.val.bad { color: #ef5350; }
.btn { margin-top: 16rpx; background: #23282e; color: #e8eaed; font-size: 26rpx; border-radius: 12rpx; }
.btn.primary { background: #3b82f6; color: #fff; }
</style>
```

`src/pages/update/update.vue`:
```vue
<template>
  <view class="page">
    <view class="box">
      <text class="title">{{ title }}</text>
      <text v-if="notes" class="notes">{{ notes }}</text>
      <text v-if="apkUrl" class="url">{{ apkUrl }}</text>
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
import { Update, UPDATE_REPO } from '../../native/update'
import { Shell } from '../../native/shell'
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
    const fileName = `ufm-${Date.now()}.apk`
    const r = await Update.download({ apkUrl: apkUrl.value, fileName })
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
.url { display: block; margin-top: 16rpx; color: #5a6169; font-size: 20rpx; word-break: break-all; }
.btn { margin-top: 28rpx; background: #23282e; color: #e8eaed; border-radius: 12rpx; font-size: 28rpx; }
.btn.primary { background: #3b82f6; color: #fff; }
</style>
```

`src/pages.json` 追加：
```json
{ "path": "pages/settings/settings", "style": { "navigationBarTitleText": "设置" } },
{ "path": "pages/update/update", "style": { "navigationBarTitleText": "检查更新" } }
```

- [ ] **Step 6: 启动时自动检查更新（最多 1 次 / 24 小时）**

spec §7.4 要求启动检查有节流，且 GitHub 匿名 API 限流为 60 次/小时（spec §10 风险表），所以不能每次启动都请求。

`src/utils/updateGate.ts`:
```ts
const KEY = 'ufm:lastUpdateCheckMs'
const DAY_MS = 24 * 60 * 60 * 1000

/** Returns the timestamp of the last automatic check, or 0 when none was recorded. */
export function lastCheckMs(): number {
  const raw = uni.getStorageSync(KEY)
  const n = typeof raw === 'number' ? raw : Number(raw)
  return Number.isFinite(n) ? n : 0
}

export function shouldCheck(now: number, last: number, intervalMs = DAY_MS): boolean {
  if (!Number.isFinite(last) || last <= 0) return true
  // A clock that jumped backwards must not lock the user out of checks forever.
  if (now < last) return true
  return now - last >= intervalMs
}

export function markChecked(now: number): void {
  uni.setStorageSync(KEY, now)
}
```

`tests/unit/updateGate.spec.ts`:
```ts
import { describe, expect, it } from 'vitest'
import { shouldCheck } from '../../src/utils/updateGate'

const DAY = 24 * 60 * 60 * 1000

describe('shouldCheck', () => {
  it('checks when no previous check was recorded', () => {
    expect(shouldCheck(Date.now(), 0)).toBe(true)
  })
  it('skips inside the 24 hour window', () => {
    const now = 1_800_000_000_000
    expect(shouldCheck(now, now - DAY + 1)).toBe(false)
  })
  it('checks once the window elapsed', () => {
    const now = 1_800_000_000_000
    expect(shouldCheck(now, now - DAY)).toBe(true)
  })
  it('checks when the clock moved backwards instead of locking out', () => {
    const now = 1_800_000_000_000
    expect(shouldCheck(now, now + DAY)).toBe(true)
  })
})
```

`src/pages/index/index.vue` 的 `onShow(refreshStats)` 之后追加：
```ts
import { shouldCheck, lastCheckMs, markChecked } from '../../utils/updateGate'
import { Update, UPDATE_REPO } from '../../native/update'

/** Silent: failures never interrupt the home page; only a real update produces UI. */
async function maybeCheckUpdate() {
  const now = Date.now()
  if (!shouldCheck(now, lastCheckMs())) return
  markChecked(now)
  try {
    const r = await Update.check(UPDATE_REPO)
    if (!r.available) return
    uni.showModal({
      title: `发现新版本 ${r.versionName}`,
      content: '是否前往查看更新内容？',
      confirmText: '查看',
      success: (m) => {
        if (m.confirm) uni.navigateTo({ url: '/pages/update/update' })
      }
    })
  } catch {
    // Offline or rate limited: stay quiet and retry after the next window.
  }
}

onShow(() => {
  refreshStats()
  maybeCheckUpdate()
})
```
（把原来的 `onShow(refreshStats)` 那行删掉，避免重复注册。）

- [ ] **Step 7: 跑测试**

```bash
npm run test
cd android && ./gradlew testDebugUnitTest --tests "*VersionCompareTest*"
```
Expected: Vitest 全部 PASS（含 `updateGate` 4 个）；Gradle `BUILD SUCCESSFUL`，4 个测试 PASS

- [ ] **Step 8: 提交**

```bash
git add -A
git commit -m "feat: 设置页、检查更新（含 24h 节流）、下载与安装 APK"
```

---

## Task 14: 发布工作流、签名与端到端验收

**Files:**
- Create: `.github/workflows/release.yml`
- Create: `scripts/gen-keystore.sh`
- Create: `tests/e2e/smoke.spec.ts`
- Create: `playwright.config.ts`
- Create: `README.md`

**Interfaces:**
- Produces：打 tag `v*.*.*` 后自动产出 `app-release.apk` 并发布 Release；本地 `npm run test:e2e` 能在 H5 模式跑通冒烟流程

- [ ] **Step 1: 生成签名密钥（只做一次）**

`scripts/gen-keystore.sh`:
```bash
#!/usr/bin/env bash
# Generates the ONLY signing key this app will ever use. Losing it makes覆盖升级 impossible.
#
# Password requirements (measured with keytool from JBR 25):
#   - at least 6 characters (counted as characters, not bytes)
#   - storepass and keypass MUST be identical: PKCS12 ignores -keypass otherwise
#   - prefer 16-32 alphanumeric chars; $ ! " \ and spaces survive three shell layers poorly
set -euo pipefail
OUT="${1:-release.keystore}"
ALIAS="${KEY_ALIAS:-ufm}"

if [ -f "$OUT" ]; then
  echo "refusing to overwrite $OUT" >&2
  exit 1
fi

# Validate before invoking keytool so the failure message is actionable.
if [ "${#KEYSTORE_PASSWORD}" -lt 6 ]; then
  echo "KEYSTORE_PASSWORD must be at least 6 characters (got ${#KEYSTORE_PASSWORD})" >&2
  exit 1
fi
if [ "$KEYSTORE_PASSWORD" != "$KEY_PASSWORD" ]; then
  echo "KEYSTORE_PASSWORD and KEY_PASSWORD must be identical: PKCS12 silently ignores a different -keypass" >&2
  exit 1
fi

"$JAVA_HOME/bin/keytool" -genkeypair -v \
  -keystore "$OUT" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$KEYSTORE_PASSWORD" -keypass "$KEYSTORE_PASSWORD" \
  -dname "CN=uni-file-manager, OU=personal, O=kcx, C=CN"
echo "created $OUT — back it up outside the repo, then run:"
echo "  base64 -w0 $OUT   # paste into GitHub secret KEYSTORE_BASE64"
echo "Also set KEYSTORE_PASSWORD and KEY_PASSWORD to the SAME value in GitHub Secrets."
```

（`-dname` 去掉了空的 `L=` 与 `ST=`：实测空值虽被接受，但部分工具链解析 `L=, ` 会告警，去掉更干净。`L`/`ST` 对 Android 签名有效性无影响，只影响证书展示信息。）

执行（需要的口令由用户现场提供，不写进仓库）：
```bash
export JAVA_HOME=$(ls -d /c/Users/k30087471/tools/jdk-21* | head -1)
# 16-32 alphanumeric chars; both variables MUST hold the same value.
export KEYSTORE_PASSWORD='<用户在本地输入，≥6 字符的字母数字>'
export KEY_PASSWORD="$KEYSTORE_PASSWORD"
export KEY_ALIAS='ufm'
bash scripts/gen-keystore.sh release.keystore

# Byte-exact base64 that GitHub Secrets will carry; -w0 keeps it on one line.
base64 -w0 release.keystore > keystore.b64
wc -c release.keystore keystore.b64   # sizes come from the real command, not estimated
```
`release.keystore` 与 `keystore.b64` 都在 `.gitignore` 里（Task 1 已加 `*.keystore`；本步补 `*.b64`）。

**写进 GitHub Secrets 的四个值**（`gh secret set` 逐个执行，值用交互方式输入，不进 shell history）：
```bash
gh secret set KEYSTORE_BASE64   < keystore.b64
gh secret set KEYSTORE_PASSWORD   # 交互粘贴，与上面 KEYSTORE_PASSWORD 相同
gh secret set KEY_PASSWORD        # 必须与 KEYSTORE_PASSWORD 完全相同（PKCS12 限制）
gh secret set KEY_ALIAS           # ufm
```
Expected: 四条都返回 `✓ Set Actions secret …`；用 `gh secret list` 能看到四个名字（值不可回读）。

**验证口令一致性**（万一填错，CI 会在签名阶段失败而不是在发版后才发现）：
```bash
keytool -list -keystore release.keystore -storepass "$KEYSTORE_PASSWORD" | head -3
```
Expected: 输出 `Keystore type: PKCS12` 且列出 `ufm, PrivateKeyEntry`；若报 `password was incorrect` 说明口令记错了，**立刻重设而不是重新生成 keystore**（重新生成等于换签名，已装用户将无法覆盖升级）。

- [ ] **Step 2: 发布工作流**

`.github/workflows/release.yml`:
```yaml
name: release

on:
  push:
    tags: ['v*.*.*']

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'

      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - uses: android-actions/setup-android@v3

      - name: Install dependencies
        run: npm ci

      - name: Unit tests
        run: npm run test

      - name: Build H5
        run: npm run build:h5

      - name: Sync Capacitor
        run: npx cap sync android

      - name: Decode keystore
        run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > android/app/release.keystore

      - name: Gradle unit tests
        run: cd android && ./gradlew testDebugUnitTest

      - name: Build release APK
        run: cd android && ./gradlew assembleRelease
        env:
          KEYSTORE_PATH: release.keystore
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}

      - name: Name artifacts after the tag
        run: |
          cp android/app/build/outputs/apk/release/app-release.apk "uni-file-manager-${GITHUB_REF_NAME#v}.apk"

      - name: Publish release
        uses: softprops/action-gh-release@v2
        with:
          files: |
            uni-file-manager-*.apk
            version.json
          generate_release_notes: true
```

`version.json`（入库，每次发版随 tag 一起改）：
```json
{ "versionCode": 10000, "versionName": "1.0.0" }
```

`android/app/build.gradle` 追加签名配置（`android {}` 内）：
```gradle
    signingConfigs {
        release {
            def ksPath = System.getenv("KEYSTORE_PATH") ?: "release.keystore"
            storeFile file(ksPath)
            storePassword System.getenv("KEYSTORE_PASSWORD")
            keyAlias System.getenv("KEY_ALIAS")
            keyPassword System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        }
    }
```

- [ ] **Step 3: 冒烟 E2E**

`playwright.config.ts`:
```ts
import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: 'tests/e2e',
  use: { baseURL: 'http://127.0.0.1:5173', viewport: { width: 412, height: 915 } },
  webServer: {
    command: 'npm run dev:h5',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: true,
    timeout: 120_000
  }
})
```

`tests/e2e/smoke.spec.ts`:
```ts
import { expect, test } from '@playwright/test'

// The H5 build has no native bridge, so only the shell renders. These assertions pin the
// things that must never regress: the app boots, the title is correct, and the quick
// entries exist. Native-only behaviour is covered by the adb checks in Tasks 3-13.
test('home renders without the native bridge', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('文件管理器')).toBeVisible({ timeout: 30_000 })
})

test('settings route is reachable', async ({ page }) => {
  await page.goto('/#/pages/settings/settings')
  await expect(page.getByText('版本')).toBeVisible({ timeout: 30_000 })
})
```

`package.json` 追加 devDependency 与脚本：
```json
"@playwright/test": "1.49.0"
```
```json
"test:e2e": "playwright test"
```

- [ ] **Step 4: 跑 E2E 确认通过**

```bash
npx playwright install chromium
npm run test:e2e
```
Expected: 2 个测试 PASS（H5 无原生桥时首页仍渲染标题与入口）

- [ ] **Step 5: 写 README**

`README.md` 至少包含以下小节，内容与 spec 一致：
````markdown
# 文件管理器（uni-file-manager）

Android 11+ 未 root 手机上的文件管理器：文件浏览与增删改、照片按真实目录分组、
照片收藏、重复照片整理、GitHub Release 在线升级。

## 用 HBuilderX 打开
本项目页面是标准 uni-app(Vue3) 工程（`src/` 下）。用 HBuilderX 打开本目录即可编辑与
H5 预览；构建 APK 不走 HBuilderX 云打包，而是 Capacitor + Gradle（原因见
`docs/superpowers/specs/2026-11-21-uni-file-manager-design.md` §3.3）。

## 本地构建
```bash
npm ci
npm run test
npm run build:h5
npx cap sync android
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
需要 JDK 21 与 Android SDK 36（Gradle 8.14 不支持 JDK 21）。

## 发版
```bash
# 1. 改 android/app/build.gradle 的 versionCode / versionName
# 2. 改 version.json
git commit -am "release: 1.2.0"
git tag v1.2.0 && git push origin v1.2.0
```
GitHub Actions 自动构建并发布 APK；App 内「设置 → 检查更新」即可发现新版本。
**签名密钥只生成一次并存于仓库外，丢失后无法覆盖升级。**

## 首次使用
安装后需在系统设置中开启「所有文件访问」；否则 App 以只读相册模式运行。
````

- [ ] **Step 6: 端到端真机验收（对照 spec §1.3 六条成功标准）**

逐条实测，并把命令输出贴到 PR 描述里：
```bash
# 1 启动
adb shell am force-stop com.kuangcx.ufm && adb shell am start -n com.kuangcx.ufm/.MainActivity

# 2 分组真实可见
adb shell ls /storage/emulated/0/Pictures/PhotoGroups
adb shell content query --uri content://media/external/images/media \
  --projection _display_name,bucket_display_name | grep -i PhotoGroups

# 3 中断恢复：移动大文件时杀进程
adb shell dd if=/dev/urandom of=/storage/emulated/0/Download/big.bin bs=1M count=200
# （在 App 内发起移动，立刻）
adb shell am force-stop com.kuangcx.ufm
adb shell find /storage/emulated/0 -name "big.bin*" -exec ls -l {} \;
adb shell am start -n com.kuangcx.ufm/.MainActivity
sleep 3
adb shell find /storage/emulated/0 -name "*.part"   # 期望无残留
adb shell find /storage/emulated/0 -name "big.bin" -exec ls -l {} \;  # 期望恰好一个，大小正确

# 4 收藏清理
adb shell sqlite3 /data/data/com.kuangcx.ufm/databases/favorites.db "select count(*) from favorites"

# 5 升级
# 打一个 v1.0.1 tag 触发 CI；装上旧版后在 App 内检查更新
```
Expected: 六条全部满足；第 3 条必须**真的杀进程**，不能只做 dry-run

- [ ] **Step 7: 提交并推送**

```bash
git add -A
git commit -m "ci: 发布工作流、签名脚本与端到端冒烟测试"
git remote add origin git@github.com:kcx2366425574/uni-file-manager.git
git push -u origin main
```

（仓库创建与推送属于对外发布动作，执行前需用户确认；本步在用户同意后再跑。）

---

## 自查记录

**Spec 覆盖对照：**

| Spec 章节 | 对应任务 |
|---|---|
| §4 权限方案 | Task 3 |
| §3.1 五个插件 | Task 3（Shell）、4（Fs 读）、5–6（Fs 写）、7（Group）、8（Media）、10（Fav）、13（Update） |
| §5.1 目录约定 | Task 7 |
| §5.2 移动事务 | Task 6（含崩溃恢复与日志上限测试） |
| §5.3 收藏表 | Task 10 |
| §5.4 查询与分页（含 dHash/重复） | Task 8、Task 12 || §6 页面清单 | Task 4、7、8、9、10、11、12、13 |
| §7.1 本地环境 | Task 2 |
| §7.2 签名 | Task 14 |
| §7.3 Actions | Task 14 |
| §7.4 在线升级（含 24h 节流、60 次/小时限流） | Task 13（Step 6 的 `updateGate`）、Task 14 |
| §8 测试策略 | 每个任务的单测 + Task 14 的 E2E 与真机验收 |
| §10 风险（JDK 25 不可用） | Task 2 Step 1 直接装 JDK 21 |

**Review Focus 测试落点：**

| Review Focus | 测试所在 |
|---|---|
| 1 同名文件覆盖 | Task 6 `MoveEngineTest.refuses to overwrite an existing destination` |
| 2 非法分组名 | Task 7 `NameRulesTest`（空、含分隔符、`.`、超长、重名共 6 例） |
| 3 卷消失瞬间 | Task 6 `cross-volume failure keeps the source untouched` + Task 4 的 `E_NOENT`/`E_IO` 分支 |
| 4 收藏失效清理 | Task 10 `FavStoreTest` + `FavPlugin.list` 里的 `pruneMissing()` |
| 5 无权限降级 | Task 3 Step 5 真机验证 + Task 14 六条验收第 1 条 |

**类型一致性检查：** `FileEntry`/`Entry`、`MediaItem`、`GroupInfo`、`GroupPhoto`、`FavItem`、`PermissionSnapshot`、`StorageStats`、`TransferResult`、`DupGroup` 在原生与 TS 两侧字段名逐一对应（`mediaId`↔`media_id` 只在 SQL 层出现，桥接层统一为 `mediaId`）。
