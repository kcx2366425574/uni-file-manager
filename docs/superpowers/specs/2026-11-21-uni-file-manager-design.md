# 手机文件管理器（uni-app + Capacitor）设计文档

- 文档日期：2026-11-21
- 状态：待用户评审
- 仓库：`uni-file-manager`（GitHub 公开仓库）
- 目标：Android 11+ 未 root 手机上，替代 ES 文件管理器的自用工具，核心能力是**照片按真实目录分组**，并支持**在线升级**

---

## 1. 需求与边界

### 1.1 已确认的用户意图

| 项目 | 结论 | 来源 |
|---|---|---|
| 用途 | 本人日常使用，替代 ES 文件管理器 | 用户答复 |
| 分组语义 | **真实目录分组**（移动文件） | 用户答复 |
| 收藏 | 与分组独立，有专门页面展示 | 用户答复 |
| 目标设备 | Android 11+，未 root | 用户答复 |
| 机型 | 通用适配，不针对特定厂商 | 用户答复 |
| 构建路线 | **A：uni-app(Vue3) + Capacitor**，GitHub Actions 自动出 APK 并发 Release | 用户答复 |
| 升级方式 | 提示新版本 → 用户确认 → 下载 → 拉起系统安装器 | 用户答复 |
| 仓库 | GitHub 公开仓库（Release 下载无需鉴权） | 用户答复 |
| 本机环境 | 需装 Android 构建环境并本机构建验证一次 | 用户答复 |

### 1.2 v1 功能范围

**包含：**

1. **文件浏览与基本增删改**：双栏浏览（内部存储 / SD 卡）、新建目录、删除、重命名、复制、剪切、粘贴、排序（名称/大小/时间/类型）、详情、分享、用其它应用打开。
2. **照片分组（核心）**：以 `Pictures/PhotoGroups/<分组名>/` 为分组根；新建/重命名/删除分组；照片批量移入移出；按日期/大小/相似度整理；重复照片检测；缩略图墙。
3. **分类聚合与快捷入口**：图片/视频/音频/文档四类聚合入口；最近文件；下载目录；收藏入口。
4. **照片收藏**：独立页面，从任意位置收藏照片，与分组互不影响；取消收藏；收藏项文件失效时自动清理。
5. **在线升级**：检查 GitHub Releases 最新版 → 下载 → 系统安装器安装。
6. **设置页**：所有文件访问权限状态与跳转、存储容量统计、检查更新、关于。

**不做（二期）：**

- FTP / SMB / WebDAV / 网盘访问
- root 根目录访问
- 压缩包创建与解压（仅保留打开/预览）
- 回收站、文件加密、应用备份

### 1.3 成功标准

1. 装入 Android 11+ 真机可正常启动，无全盘权限时降级为只读相册模式而非闪退。
2. 照片移入分组后，文件真实位于 `Pictures/PhotoGroups/<分组>/`，且系统相册立即可见。
3. 跨卷移动过程中被杀进程/断电后，重启可续做或回滚，**不丢文件**。
4. 收藏页展示正确，文件删除后失效收藏自动清理。
5. GitHub Actions 打 tag 后自动产出可安装 APK 并发布 Release。
6. App 内检查更新能发现新版本，下载后可拉起系统安装器完成覆盖升级（签名一致）。

---

## 2. 环境现状（实测）

命令与输出见下表，均在本机 Windows 11 执行：

| 检查项 | 命令 | 实测结果 |
|---|---|---|
| Node | `node -v` | `v24.9.0` |
| npm | `npm -v` | `11.6.0` |
| npm 源 | `npm config get registry` | `https://registry.npmmirror.com` |
| uni-app Vue3 包可用 | `npm view @dcloudio/uni-app dist-tags` | `vue3: '3.0.0-alpha-5030720261117001'` |
| Capacitor 可用 | `npm view @capacitor/core version` | `8.6.2` |
| JDK | `java -version` | 未安装（PATH 中无 java） |
| DevEco 自带 JBR | `"/c/Program Files/Huawei/DevEco Studio/jbr/bin/java.exe" -version` | `openjdk 25.0.2` |
| Android SDK | 查找 `AppData/Local/Android/Sdk` 等路径 | **未安装** |
| Gradle | `which gradle` | 未安装（由 Capacitor 工程的 wrapper 提供） |
| 磁盘 | `df -h /c` | 可用 1.6T |
| GitHub CLI | `gh auth status` | 已登录 `kcx2366425574`，scope 含 `repo`、`workflow` |
| HBuilderX | 查找安装目录 | 仅有 `AppData/Local/HBuilder X/cache` 与 `Roaming` 残留，**主程序未安装** |

结论：**本机缺 Android SDK / JDK**，需要安装后才能本地构建 APK。已有 DevEco 的 JBR 25 可作备选，但 Capacitor 8 的 Android Gradle Plugin 对 JDK 有明确区间要求，若 JBR 25 不被接受则安装 JDK 17（见 §7.1）。

---

## 3. 总体架构

### 3.1 分层

```
┌──────────────────────────────────────────────────────┐
│  uni-app 页面层（Vue3 + Vite SFC，HBuilderX 可直接编辑）  │
│  pages/index, files, groups, photos, favorites, ...   │
└──────────────────────┬───────────────────────────────┘
                       │ 自写 TS 封装层（src/native/*.ts）
                       │ 统一 Promise API + 错误码
┌──────────────────────▼───────────────────────────────┐
│  Capacitor JS Bridge（@capacitor/core）                │
└──────────────────────┬───────────────────────────────┘
                       │ registerPlugin('Fs' | 'Media' | 'Group' | 'Fav' | 'Shell')
┌──────────────────────▼───────────────────────────────┐
│  Android 容器（android/app/src/main/java/...）         │
│  ├─ FsPlugin      文件 CRUD、目录列举、移动事务         │
│  ├─ MediaPlugin   MediaStore 查询、缩略图、重扫         │
│  ├─ GroupPlugin   分组 CRUD（真实目录）                 │
│  ├─ FavPlugin     收藏（SQLite）                       │
│  └─ ShellPlugin   权限状态、存储统计、更新检查、装 APK   │
└──────────────────────────────────────────────────────┘
```

### 3.2 构建链路

```
npm run build:h5      →  dist/build/h5
npx cap sync android  →  同步到 android/app/src/main/assets/public
./gradlew assembleRelease  →  app-release.apk
```

- GitHub Actions 完整执行上述三步，用 Secrets 中的 keystore 签名。
- 本机可用 HBuilderX 编辑与预览（H5 模式），也可用 `npm run dev:h5` 走浏览器。
- 不依赖 DCloud 云打包，不依赖 `plus.*` API。

### 3.3 关键设计决策与理由

| 决策 | 理由 |
|---|---|
| 不用 `plus.*`，全部自定义 Capacitor 插件 | `plus` 只在 HBuilderX 打包的 runtime 里存在，CI 无法构建；自定义插件让构建链完全公开可复现 |
| 缩略图走 `convertFileSrc()` 而非 base64 | 相册上万张时 base64 会撑爆内存与 IPC；本地文件服务直接给 `<img>` 用 |
| 照片索引实时查 MediaStore，不落库 | 避免自建索引与系统索引不一致导致「照片在不同页面数量不同」 |
| 分组用真实目录而非虚拟引用 | 用户选择；好处是系统相册与其它 App 立即可见 |
| 跨卷移动用「复制→校验→改名→删源」 | `renameTo` 跨卷必然失败；直接复制后删源在中断时会丢文件 |

---

## 4. 权限方案

### 4.1 清单（`AndroidManifest.xml`）

```xml
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
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

### 4.2 运行时获取流程

1. 启动 → `Environment.isExternalStorageManager()` 判定全盘权限。
2. 无权限 → 显示说明页（为什么需要、会读写什么、如何在系统设置里开启）→ 按钮跳 `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`（`package:` 指向自身）。
3. 返回后 `onResume` 复查；仍未授予则进入**只读相册模式**：可浏览 MediaStore 图片/视频、收藏、查看，但不能写文件、不能新建分组。
4. 相册查询权限：Android 13+ 请求 `READ_MEDIA_*`；≤12 请求 `READ_EXTERNAL_STORAGE`。
5. 升级安装：首次下载前检查 `canRequestPackageInstalls()`，否则跳 `ACTION_MANAGE_UNKNOWN_APP_SOURCES`。

### 4.3 存储卷枚举

`StorageManager.getStorageVolumes()` 得到内部存储与可移除卷；内部存储用 `Environment.getExternalStorageDirectory()`，可移除卷用 `StorageVolume.getDirectory()`（API 30+）。对 SD 卡的读写同样由 `MANAGE_EXTERNAL_STORAGE` 覆盖。

---

## 5. 数据模型

### 5.1 目录约定

| 用途 | 路径 |
|---|---|
| 分组根目录 | `内部存储/Pictures/PhotoGroups/` |
| 单个分组 | `.../PhotoGroups/<分组名>/`（限制字符集，禁止 `/ \ : * ? " < > \|`，禁止重名） |
| 缩略图缓存 | App 私有 `cache/thumbs/<sha1(路径)>.jpg`（`getExternalCacheDir()` 下） |
| 移动日志 | App 私有 `files/move-journal.json` |
| 收藏数据库 | App 私有 `databases/favorites.db` |

### 5.2 移动事务（防丢文件的核心）

**同卷移动**（源与目标同一 `StorageVolume`）：

```
1. 目标已存在同名文件 → 拒绝，返回 E_EXISTS
2. File.renameTo(target)          // 原子
3. 成功 → 结束；失败 → 返回 E_RENAME_FAILED，源文件不变
4. MediaScannerConnection.scanFile(源目录、目标目录)
```

**跨卷移动**（内部存储 ↔ SD 卡）：

```
1. 预检：StatFs 可用字节 ≥ 文件大小 × 1.1，否则 E_NO_SPACE
2. 写日志条目 {src, dst, dstPart, state:"COPYING", size}
3. 复制到 dst.part → FileOutputStream.flush + FileDescriptor.sync()
4. 校验：长度一致；size > 8MB 时追加 CRC32 比对
5. rename dst.part → dst
6. 日志置 state:"COPIED"
7. 删源文件
8. 日志置 state:"DONE"，落盘
9. scanFile(源目录、目标目录)
```

**崩溃恢复**：App 启动时读日志。

- `COPYING`：删除 `dst.part`，保留源，日志置 `ABORTED`（保证不出现半截文件）。
- `COPIED`：源若仍存在则重试删源；日志置 `DONE`。
- 日志超过 30 天自动清理。

**批量移动**：按文件逐个执行上述流程；单个失败不影响后续，最终汇总「成功 N / 失败 M / 原因清单」。

### 5.3 收藏表

```sql
CREATE TABLE favorites (
  path      TEXT PRIMARY KEY,   -- 绝对路径
  media_id  INTEGER,            -- MediaStore _ID，用于快速取缩略图
  added_at  INTEGER NOT NULL    -- epoch millis
);
```

- 只存引用，不复制文件。
- 收藏页启动时对每条记录做 `File.exists()` 校验；不存在则删除该行。
- 已移入分组的照片仍可被收藏，两者互不联动（符合用户要求）。

### 5.4 查询与分页

- 相册/分类列表查询 MediaStore：`Images.Media.EXTERNAL_CONTENT_URI`、`Video`、`Audio`、`Files`（文档按 MIME 过滤）。
- 排序默认 `DATE_TAKEN DESC`；分页 `LIMIT/OFFSET`，每页 200 条。
- 相似照片检测：取 8×8 灰度图算 dHash，汉明距离 ≤ 6 视为相似；重复检测用「大小相同 + SHA-256 相同」。

---

## 6. 页面与交互

| 页面 | 路径 | 关键内容 |
|---|---|---|
| 首页 | `pages/index/index` | 四大分类卡片（图片/视频/音频/文档）、快捷入口（最近、下载、收藏、分组）、存储占用条 |
| 文件浏览 | `pages/files/files` | 面包屑 + 双栏（左目录、右文件列表）；长按多选；底部操作条 |
| 分组列表 | `pages/groups/groups` | 分组卡片（封面取组内第一张 + 张数）；新建/重命名/删除 |
| 分组详情 | `pages/groups/detail` | 照片墙；多选移出、分享、批量删除；「移入更多照片」入口 |
| 相册 | `pages/photos/photos` | 时间轴网格；切到「整理模式」可按日期/大小/相似度勾选建议 |
| 收藏 | `pages/favorites/favorites` | 独立页面，网格展示；长按取消收藏 |
| 设置 | `pages/settings/settings` | 权限状态、存储统计、检查更新、关于 |
| 升级 | `pages/update/update` | 版本对比、更新说明、下载进度、安装按钮 |

**性能约束：**

- 图片网格每格 `<img>` 用缩略图 URL；列表虚拟滚动（只渲染可视区 ± 1 屏）。
- 一次只加载 200 条；滚动到底再取下一页。
- 缩略图 LRU 上限 500MB，超出按最久未用清理。

**交互约定：**

- 长按 = 进入多选；多选状态下单击 = 勾选。
- 下拉刷新 = 重扫当前目录。
- 深色模式跟随系统；使用 `rpx` 自适应各种屏宽。

---

## 7. 构建与发布

### 7.1 本地环境安装（待执行，属实施阶段）

顺序：

1. 安装 JDK 17（Temurin），或先试 DevEco 的 JBR 25；以 `gradlew` 是否报版本错误为准，不做臆测。
2. 安装 Android `cmdline-tools` → `sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"`。
3. 设置 `ANDROID_HOME`、`JAVA_HOME`，接受 licenses。
4. 生成本地 `local.properties`（不入库）。
5. 首次 `./gradlew assembleDebug` 验证工具链。

### 7.2 签名

- 用 `keytool` 生成 `release.keystore`（RSA 2048，有效期 10000 天）。
- **只生成一次**，备份在仓库外；`keystore` 的 base64 + 口令存 GitHub Secrets：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。
- 签名不一致会导致覆盖安装失败，因此 keystore 一旦丢失即无法升级。

### 7.3 GitHub Actions（`.github/workflows/release.yml`）

触发：推送形如 `v*.*.*` 的 tag。

步骤：

1. checkout
2. `actions/setup-node@v4`（node 20）
3. `actions/setup-java@v4`（temurin 17）
4. `npm ci`
5. `npm run build:h5`
6. `npx cap sync android`
7. 解码 keystore → `gradlew assembleRelease`
8. `softprops/action-gh-release` 上传 APK，并把 `version.json` 一并作为 release asset

`version.json` 内容：

```json
{ "versionCode": 3, "versionName": "1.2.0", "notes": "新增相似照片整理", "apk": "uni-file-manager-1.2.0.apk" }
```

### 7.4 在线升级

- 检查：请求 `https://api.github.com/repos/<owner>/uni-file-manager/releases/latest`，读 `tag_name` 与 assets 中的 `version.json`，与本地 `versionCode` 比较。
- 节流：启动时检查最多 1 次/24 小时；设置页可手动触发。
- 下载：`DownloadManager` 下载 APK 到公共 Downloads；进度用 `query()` 轮询。
- 安装：`FileProvider` 暴露 APK → `Intent.ACTION_VIEW` + `application/vnd.android.package-archive` → 用户手动确认。

---

## 8. 测试策略

| 层级 | 内容 | 执行环境 |
|---|---|---|
| 单元测试（Kotlin JUnit） | 移动事务状态机（COPYING/COPIED/DONE/ABORTED 各状态恢复）、路径名校验、versionCode 比较、dHash 相似判定 | 本机 `./gradlew test` |
| 前端单元测试（Vitest） | 格式化工具（字节数、时间）、排序比较器、多选状态机 | 本机 `npm run test` |
| 页面流程测试 | H5 模式下用 Playwright 走：打开文件列表 → 多选 → 批量操作确认弹窗；新建分组 → 列表出现 | 本机 + CI |
| 真机验收 | ① 跨卷移动中途断进程后恢复不丢文件；② 移动后系统相册可见；③ 无全盘权限时降级不闪退；④ 安装升级覆盖成功 | 本机 adb + 真机 |

**验收断言**（必须实测，不能凭推断）：

- 移动测试：用大文件（>100MB）移动中 `adb shell am force-stop`，重启后 `adb shell ls` 校验源/目标文件数与大小。
- 相册可见：移动后 `adb shell content query --uri content://media/external/images/media --where "_display_name='xxx'"` 有结果。

---

## 9. 目录结构

```
uni-file-manager/
├─ .github/workflows/release.yml
├─ android/                       # Capacitor 生成的 Android 工程
│  └─ app/src/main/java/com/example/ufm/
│     ├─ FsPlugin.kt
│     ├─ MediaPlugin.kt
│     ├─ GroupPlugin.kt
│     ├─ FavPlugin.kt
│     ├─ ShellPlugin.kt
│     ├─ core/MoveJournal.kt
│     ├─ core/ThumbCache.kt
│     └─ core/MediaQuery.kt
├─ src/
│  ├─ pages/…                     # 见 §6
│  ├─ native/                     # bridge TypeScript 封装
│  │  ├─ fs.ts media.ts group.ts fav.ts shell.ts
│  ├─ components/
│  └─ store/                      # Pinia
├─ docs/superpowers/specs/2026-11-21-uni-file-manager-design.md
├─ index.html
├─ package.json
├─ vite.config.ts
└─ README.md
```

---

## 10. 风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| Capacitor 8 要求 JDK 版本与 JBR 25 不符 | 本地无法构建 | 实测 `gradlew` 报错再决定是否装 JDK 17，不预先臆断 |
| uni-app Vue3 包为 alpha 标签 | 可能有构建不稳 | 锁定具体版本（`3.0.0-alpha-5030720261117001`），锁 `package-lock.json`；若构建失败，改用稳定版 CLI 模板 |
| Android 14+ 对 `MANAGE_EXTERNAL_STORAGE` 收紧 | 部分机型设置页不显示 | 设置页给出逐厂商的说明；无权限时降级模式必须有完整可用路径 |
| SD 卡 `getDirectory()` 在部分机型返回 null | 无法枚举 SD 卡 | 回退到 `/storage/<UUID>` 扫描 `/proc/mounts` |
| GitHub Releases API 有速率限制（未认证 60 次/小时） | 检查更新失败 | 24 小时节流；失败静默，不打扰用户 |

---

## 11. 里程碑

| 阶段 | 内容 | 完成判据 |
|---|---|---|
| M1 骨架 | uni-app 工程 + Capacitor 容器 + 5 个插件空壳 + CI 出 APK | 装到真机能启动，显示首页 |
| M2 文件浏览 | FsPlugin + files 页面 | 能浏览内部存储/SD 卡，增删改可用 |
| M3 照片分组 | GroupPlugin + MoveJournal + groups 页面 | 跨卷移动中断可恢复，系统相册可见 |
| M4 相册与收藏 | MediaPlugin + FavPlugin + photos/favorites 页面 | 缩略图流畅，收藏增删正确 |
| M5 升级 | Release 工作流 + 更新检查 + 安装 | 新版能覆盖安装成功 |
