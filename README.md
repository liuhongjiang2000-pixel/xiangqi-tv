# 单机象棋（Xiangqi TV）

开源的**单机中国象棋** Android 应用，专为电视大屏与遥控器操作设计，内置 AI 引擎，无需联网、无账号、无广告。

## 特性

- 人机对战：内置 alpha-beta 剪枝 + 吃子静态搜索引擎，四档难度（入门 / 初级 / 中级 / 高级）
- 人人对战：同一棋盘轮流走子
- 遥控器操作：方向键移动光标，确认键选子 / 落子；合法落点实时提示
- 也支持触屏直接点按
- 悔棋、AI 执红先行、将死 / 困毙判定、将军提示
- 适配 Android TV（Leanback launcher、banner、非触屏设备可装）
- 纯 Java + 平台 API，零第三方依赖，安装包小巧

## 操作方式

| 按键 | 作用 |
| --- | --- |
| 方向键 | 移动光标 |
| 确认键（OK） | 选中棋子 / 落子 / 换选己方棋子 |
| 触屏 | 直接点按棋子与落点 |

底部按钮：新对局、悔棋、难度切换、AI 执红先行开关。

## 构建

GitHub Actions 已在 `main` 分支推送时自动构建并发布到 [Releases](https://github.com/liuhongjiang2000-pixel/xiangqi-tv/releases)。

本地构建：

```bash
gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

要求：JDK 17 + Gradle 8.9 + Android SDK 34。

## 引擎说明

- 棋盘：`int[90]` 一维数组，10 行 × 9 列
- 着法：完整实现帅/将、仕/士、相/象（塞象眼）、马（蹩马腿）、车、炮（翻山）、兵/卒（过河）规则及白脸将判负
- 搜索：Negamax + alpha-beta 剪枝 + 迭代加深 + MVV-LVA 吃子排序 + 有限吃子静态搜索，限时 2.5 秒
- 评估：子力价值 + 程序化位置分（兵过河 / 中路 / 活跃位）

## 免责声明

本项目为原创的洁净室实现（clean-room implementation），与任何商业象棋应用无关，未使用其代码或素材；棋类规则本身不受版权保护。仅供学习交流。

## License

MIT
