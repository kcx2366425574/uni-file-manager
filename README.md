# 文件管理器（uni-file-manager）

Android 11+ 未 root 手机上的文件管理器：文件浏览与增删改、照片按真实目录分组、
照片收藏、重复照片整理，以及 GitHub Release 在线升级。

## 用 HBuilderX 打开

页面是标准 uni-app(Vue3) 工程，都在 `src/` 下。用 HBuilderX 打开本目录即可编辑与
H5 预览。构建 APK **不走** HBuilderX 云打包，而是 Capacitor + Gradle，原因见
`docs/superpowers/specs/2026-11-21-uni-file-manager-design.md` §3.3：只有公开工具链
才能让 GitHub Actions 自动出包。

## 本地构建

```bash
npm ci
npm run test
npm run build:h5
npx cap sync android
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

需要 **JDK 21**（Capacitor 8 的 `build.gradle` 硬编码 `JavaVersion.VERSION_21`）与
Android SDK 36。Gradle 8.14 最高只支持 Java 24，所以 DevEco 自带的 JBR 25 不能用。

Windows 下 `android/local.properties` 的路径要写成 `C\:/Users/...` 或转义反斜杠，
写成裸 `C:\Users\...` 会让 Gradle 报“文件名、目录名或卷标语法不正确”。

## 发版

```bash
# 1. 改 android/app/build.gradle 的 versionCode / versionName
# 2. 改 version.json
git commit -am "release: 1.2.0"
git tag v1.2.0 && git push origin v1.2.0
```

GitHub Actions 自动构建并发布 APK。App 内「设置 → 检查更新」即可发现新版本。

**签名密钥只生成一次并存于仓库外**（`scripts/gen-keystore.sh`），丢失后无法覆盖升级。
`storepass` 与 `keypass` 必须相同（PKCS12 限制），建议 16–32 位纯字母数字。

## 首次使用

安装后需在系统设置中开启「所有文件访问」；否则 App 以只读相册模式运行。

## 隐私

本应用不收集任何数据。唯一的网络请求是检查 GitHub Releases 的更新。
