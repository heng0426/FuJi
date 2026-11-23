# 小福日常（FuJi）

单宠（橘猫「小福」）日常健康与生活记录 Android App。核心体验是「打开即可记、一眼看懂」：喝水、主粮/加餐、体重、驱虫、疫苗、异常六类记录，配合同步汇总、日历回看、到期提醒与趋势图。

- **当前版本**：`0.9.0`（versionCode 9）
- **交付形态**：`dist/小福日常-v0.9.0.apk`（自测签名，覆盖安装保留数据）
- **状态**：核心功能完成，已按 7 轮真机反馈迭代；剩余事项见 [`docs/任务清单-小福日常.md`](docs/任务清单-小福日常.md)
- **技术栈**：Kotlin · Jetpack Compose · Material 3 · Room · WorkManager
- **平台**：Android 9+（minSdk 28），compileSdk / targetSdk 37

## 功能概览

### 今日
- 今日概览：喝水总量、主粮/加餐（加餐仅当日有记录时显示，带展开动画）、最近体重。
- 快捷记录：喝水、喂食、加餐、更多；全部走底部弹窗，预填上次值。
- 喂食：份数为整数（1–20），每份克重与份数均记忆上次；内置「早餐 3 份 / 晚饭 4 份」预设，支持自定义预设（长按删除）；使用预设不改变默认份数。
- 时间线：按日分组倒序，日期含年份；长按进入多选，可批量删除；多选顶栏吸顶。
- 详情：六类记录统一详情弹窗，支持编辑/删除；异常展示分类、程度、完整备注与照片。

### 日历
- 跟手横向翻月（`HorizontalPager`），支持左右箭头与手势。
- 日期标记：有记录灰点、异常红点；驱虫紫色圆环、疫苗青色圆环（不再额外画到期点）。
- 记录小点随月份即时加载，无需先选日期。
- 选中到期日时，记录列表首条展示「驱虫到期 / 疫苗到期」信息卡。
- 补记入口为右上角「+」；「今天」按钮位于日历卡右下角图例最右侧。
- 日期跳转采用手动输入（`yyyy-MM-dd`），校验后联动分页。

### 记录
- 类型筛选 chips；长按标签可拖动排序，自动避让，松手生效并持久化。
- 筛选驱虫/疫苗时，顶部展示对应的到期倒计时卡。
- 长按记录进入多选，吸顶操作栏支持批量删除（二次确认）。

### 我的
- 宠物档案（头像、名字、生日、品种、性别）。
- 驱虫周期、提醒时间、外观（跟随系统 / 浅色 / 深色）。
- 体重趋势图、版本信息、首次使用引导。

### 基础能力
- 本地 Room 持久化；删除单条支持 Snackbar 撤销，批量删除二次确认。
- WorkManager 每小时检查、到达设定提醒小时后每日一次本地通知；Android 13+ 通知权限申请。
- 深色模式全量适配，深色下涟漪与图标色板单独调校。

## 构建与安装

```powershell
$env:JAVA_HOME='D:\Develop\Android\jdk-21'
$env:ANDROID_HOME='D:\Develop\Android\Sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$env:GRADLE_USER_HOME='D:\Develop\Android\gradle-home'
$env:ANDROID_USER_HOME='D:\Develop\Android\android-home'
$env:Path="$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

.\gradlew.bat assembleDebug      # 调试包
.\gradlew.bat assembleRelease    # 交付包
adb install -r dist/小福日常-v0.9.0.apk
```

签名：当前为自测签名，不可上架应用商店；覆盖安装（`adb install -r`）不会清空数据。

## 目录

```
app/                 Android 源码（Compose + Room）
design/              HTML 视觉稿
docs/                需求、UI、数据模型、任务清单
dist/                Release APK
build/shots/         真机验收截图（本地，不随仓库分发）
```

## 文档索引

| 文档 | 内容 |
|------|------|
| [`docs/小福日常-需求确认与实施规划.md`](docs/小福日常-需求确认与实施规划.md) | 需求决策、功能规格、阶段计划与迭代记录 |
| [`docs/UI设计规范-小福日常.md`](docs/UI设计规范-小福日常.md) | 设计令牌、组件规格、页面蓝图 |
| [`docs/数据模型设计-阶段1.md`](docs/数据模型设计-阶段1.md) | 表结构、枚举、DAO、设置键与迁移 |
| [`docs/任务清单-小福日常.md`](docs/任务清单-小福日常.md) | **当前进度：已完成 / 待进行 / 验收记录** |
| [`docs/验收报告-0.9.0.md`](docs/验收报告-0.9.0.md) | 深色编辑表单、升级/异常路径 P0 验收结果 |
| [`design/mockup.html`](design/mockup.html) | 可交互视觉稿 |

## 最近提交

```
d5e1033 fix:修复深色下弹窗导致状态栏图标不可读
5121172 chore:版本号升至 0.9.0（versionCode 9），关于页读取实际版本
a38a6d2 fix:日历小点随页显示并移除到期点
2ea6a2b feat:日历跟手翻月、到期色区分与格内信息回退
b31f41d feat:日历切月动画与到期信息增强
1122d4c feat:第五轮反馈 日历滑动/手输跳转/到期文字与记录页倒计时
ecfc54f feat:第四轮反馈 标题精简、记录多选、拖拽排序、吸顶顶栏
ea23e5c feat:第三轮反馈 圆角涟漪、删除能力、多选与类型排序
c7bd96c feat:按第二轮反馈调整深色、喂食预设、日期与日历交互
4e32041 feat:按反馈调整外观、喂食、首页概览与列表交互
```
