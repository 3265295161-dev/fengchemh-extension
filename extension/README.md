# 风车漫画 Mihon/Tachimanga 扩展

为漫画网站 [fengchemh.com](https://www.fengchemh.com) 编写的自制扩展，可在 **Tachimanga / Mihon / Tachiyomi** 中通过「扩展仓库 URL」安装使用。

- 扩展名：`风车漫画`（FengcheMh）
- 语言：`zh`
- 源 ID：`517481997322915305`
- 版本：`1.0.0`（versionCode 1）
- 包名：`eu.kanade.tachiyomi.extension.zh.fengchemh`

---

## 1. 功能

| 功能 | 说明 |
| --- | --- |
| 分类浏览 | 国产 / 日本 / 韩国 / 欧美 四类循环翻页 |
| 最新更新 | `/custom/update` 单页（约 50 条） |
| 详情页 | 标题、封面、作者、标签、简介、章节列表（旧→新排序） |
| 图片加载 | 章节页 `params` 解密 → CDN 图；`source_id=12` 的加密图运行时解密 |
| 搜索 | ⚠️ 站点 `/search` 有图形验证码，扩展固定返回空结果 |

---

## 2. 站点机制（逆向结论，已实测验证）

### 2.1 页面结构

| 页面 | URL | 说明 |
| --- | --- | --- |
| 详情 | `/comic/{短ID}` | |
| 章节 | `/chapter/{加密ID}.html` | 加密ID 为 AES 密文的 base64 |
| 分类 | `/category/list/{1..4}/page/{n}` | 1国产 2日本 3韩国 4欧美 |
| 最新 | `/custom/update` | 单页，无分页 |
| 搜索 | `/search?key=` | 图形验证码拦截，不可用 |

### 2.2 章节页 params 解密

章节页内联脚本：`params = '<base64>'`。解密流程：

1. base64 解码；
2. **前 16 字节 = IV**，其余为密文；
3. AES-128-CBC / PKCS7，**密钥 `9S8$vJnU2ANeSRoF`**；
4. 明文为 JSON：`{"host":"www.fengchemh.com","source_id":"10","comic_id":"...","chapter_id":"...","images":[...],"lazy":false}`。

`images` 是 CDN URL（`https://s2.325784.xyz/<base64>`，base64 解码后为真实图源 URL）。

### 2.3 图片加密（仅 source_id == 12）

- AES-128-CBC / PKCS7，**key = IV = `my2ecret782ecret`**；
- 解密后字节即原图（站点脚本的"位重组"即 CryptoJS WordArray→大端字节，为标准字节序，无需额外变换）；
- 扩展中通过在图片 URL 后追加 `#fengche:decrypt` 标记、并在 OkHttp 拦截器中剥离标记后解密响应体实现。
- ⚠️ 实测抽样 14 个章节全部为 `source_id=10`（明文 WebP），加密分支为防御性实现。

---

## 3. 构建 APK

### 3.1 方式一：命令行（JDK 17 + Android SDK）

前置条件：JDK 17、Android SDK（platforms;android-34 + build-tools;34.0.0），设置 `ANDROID_HOME`。

```bash
cd extension
# 生成 wrapper 后使用（或直接用系统 gradle 8.9）
./gradlew assembleRelease
```

产物：`extension/build/outputs/apk/release/fengchemh-v1.0.0.apk`（release 使用 debug 签名，可直接安装）。

### 3.2 方式二：Android Studio

1. Android Studio → **Open** → 选择本 `extension` 目录（或仓库根目录）；
2. 等待 Gradle Sync（首次会下载依赖；仓库内已固定 Gradle 8.9 / AGP 8.5.2 / Kotlin 2.3.10，JDK 建议 17）；
3. 如提示缺 SDK 组件：SDK Manager 安装 **Android SDK Platform 34** 与 **Build-Tools 34.0.0**；
4. 菜单 **Build → Build Bundle(s)/APK(s) → Build APK(s)**；
5. 产物在 `extension/build/outputs/apk/` 下，取 `fengchemh-v1.0.0.apk`。

> 国内网络建议：`~/.gradle/gradle.properties` 或环境变量中加入阿里云镜像可加速依赖下载。

---

## 4. 托管（生成"扩展仓库 URL"）

Tachimanga / Mihon 不能直接粘贴漫画网站 URL，只能通过**扩展仓库 URL** 安装。仓库 URL 指向一个 JSON 索引文件（`index.min.json`），其中声明了 APK 地址。

### 4.1 生成索引

```bash
cd extension
node build-index.js   # 生成 index.min.json，并校验源 ID
```

### 4.2 上传到 GitHub（推荐）

1. 建一个公开仓库（例如 `fengchemh-extension`）；
2. 上传：`fengchemh-v1.0.0.apk`、`index.min.json`、`README.md`；
3. **方式 A（GitHub Pages）**：仓库 Settings → Pages → Deploy from branch → main → 保存；
   - URL：`https://<你的用户名>.github.io/fengchemh-extension/index.min.json`
4. **方式 B（raw 链接）**：直接使用
   - `https://raw.githubusercontent.com/<你的用户名>/fengchemh-extension/main/index.min.json`

### 4.3 在 Tachimanga / Mihon 中安装

1. 打开 App → **浏览 → 扩展**（或 设置 → 扩展）；
2. 右上角 **三点菜单 → 扩展仓库**（Add repository）；
3. 粘贴上面的 `index.min.json` URL，保存；
4. 刷新扩展列表 → 找到「风车漫画」→ 点击安装。

---

## 5. 项目结构

```
extension/
├── build.gradle.kts              # 构建脚本（application 模块，compileOnly 扩展库；内联版本号，无 catalog）
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/               # Gradle Wrapper（固定 8.9）
├── src/main/AndroidManifest.xml
├── src/main/kotlin/eu/kanade/tachiyomi/extension/zh/fengchemh/
│   ├── FengcheMh.kt              # HttpSource 主类（含图片解密拦截器）
│   ├── FengcheMhCrypto.kt        # params / 图片 AES 解密
│   └── FengcheMhFactory.kt       # SourceFactory
├── index.template.json           # 索引模板（含源 ID）
├── build-index.js                # 生成 index.min.json
└── README.md
```

---

## 6. 验证记录

- 分类页：4 类各取 1-2 页，链接 72 → 去重 36，封面字段正确（http→https）；
- 最新更新页：约 50 条含封面；
- 详情页：标题/作者/标签/简介/章节列表解析正确；
- 章节页：`params` 解密成功，图片 URL 正确（如 `/chapter/VqVovibKxY.html` 第 1 章 28 张图）；
- 图片下载：抽测 4 张成功，均为 RIFF/WEBP 文件头；
- 源 ID：按 Mihon 算法（md5("风车漫画/zh/1") 前 8 字节 → 64 位 → 清符号位）计算 = **517481997322915305**。

## 7. 已知限制

- 站内搜索被图形验证码拦截，扩展内固定返回空（可接受）；
- `source_id=12` 加密图分支未在真实数据上触发（抽样均为 10），如遇异常可在 `FengcheMhCrypto.decryptImage` 处排查；
- 站点结构或加密算法变更会导致扩展失效，需重新逆向。
