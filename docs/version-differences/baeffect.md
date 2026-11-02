# BAEffect 版本差异登记

本文件只记录**经源码/字节码确认**的差异。未在此登记的内容全部保留在
`common/`；每个 target 只在 `prepareCommonSources` 中排除确实需要替换的
common 文件。

## 支持矩阵

| Target | JDK | AUI |
| --- | --- | --- |
| `forge-1.20.1` | 17 | `ApricityUI-forge-1.20.1:1.2.9` |
| `fabric-1.20.1` | 17 | `ApricityUI-fabric-1.20.1:1.2.9` |
| `fabric-1.21.1` | 21 | `ApricityUI-fabric-1.21.1:1.2.9` |
| `fabric-26.1` | 25 | `ApricityUI-fabric-26.1:1.2.9` |
| `neoforge-1.21.1` | 21 | `ApricityUI-neoforge-1.21.1:1.2.9` |
| `neoforge-26.1` | 25 | `ApricityUI-neoforge-26.1:1.2.9` |

ApricityUI 1.2.9 在以上 6 个 target 中的公开 API（`Document`、`Element`、
`Canvas`、`Client`、`HTML`、`Window`、`ColorPicker`、`ApricityScreen`）经
`javap` 比对一致，因此所有 AUI 页面、画布绘制与设置页逻辑都放在 `common/`。

各 target 的 `repositories` 首先查找 `mavenLocal()`，随后是 Sighs Maven；
1.2.9 已存在于 Gradle 缓存或 Sighs Maven，本地构建也可先用 `mavenLocal` 覆盖。
发布元数据把 AUI 依赖限制在 `[1.2.7,1.2.9]`（Fabric 写作
`>=1.2.7 <=1.2.9`）。

## 加载器差异

| 内容 | 位置 | 差异 |
| --- | --- | --- |
| 入口 | `targets/*/...` | Forge/NeoForge 使用 `@Mod`；Fabric 使用 `ClientModInitializer`。 |
| 客户端 tick | target 事件类 | Forge `TickEvent.ClientTickEvent`；NeoForge `ClientTickEvent.Pre/Post`；Fabric `ClientTickEvents`。 |
| 屏幕渲染 | target 事件类 / Mixin | Forge/NeoForge 使用 `ScreenEvent.Render.Post`；Fabric 无跨版本屏幕渲染事件，使用 target Mixin。 |
| 鼠标按下 | target 事件类 / Mixin | Forge/NeoForge 使用输入事件；Fabric 无跨版本全局鼠标事件，使用 `MouseHandler` Mixin。 |
| Tooltip | target 事件类 | Forge/NeoForge `RenderTooltipEvent.Pre`；Fabric `ItemTooltipCallback`。 |
| 元数据 | target 资源 | `mods.toml` / `neoforge.mods.toml` / `fabric.mod.json`。 |
| 配置/游戏目录 | `BaeffectPlatform` 实现 | Forge/NeoForge `FMLPaths`；Fabric `FabricLoader`。 |

## Minecraft API 差异

### 屏幕绘制

| 版本 | API |
| --- | --- |
| 1.20.1 / 1.21.1 | `Screen#render(GuiGraphics, int, int, float)`；`GuiGraphics#fill/blit/renderTooltip` |
| 26.1 | `Screen#extractRenderState(GuiGraphicsExtractor, int, int, float)`；`GuiGraphicsExtractor#fill/blit/setTooltipForNextFrame` |

因此设置页右上角图标的绘制分别实现在各 target 的
`BAEffectSettingsShortcutRenderer` 中；`common/BAEffectSettingsShortcut` 只保留
几何、命中测试与点击行为。

### 鼠标设置页

| 版本 | 类 |
| --- | --- |
| 1.20.1 | `net.minecraft.client.gui.screens.MouseSettingsScreen` |
| 1.21.1 / 26.1 | `net.minecraft.client.gui.screens.options.MouseSettingsScreen` |

该判断通过 `BaeffectPlatform#isSettingsScreen(Screen)` 下放到 target。

### 窗口句柄

| 版本 | 方法 |
| --- | --- |
| 1.20.1 – 1.21.1 | `Window#getWindow()` |
| 26.1 | `Window#handle()` |

通过 `BaeffectPlatform#windowHandle()` 下放。

### OGG/Vorbis 解码

| 版本 | 解码器 |
| --- | --- |
| 1.20.1 | `com.mojang.blaze3d.audio.OggAudioStream` |
| 1.21.1 / 26.1 | `net.minecraft.client.sounds.JOrbisAudioStream`（float 采样） |

`common/OggDecoder.java` 实现旧版解码；`fabric-1.21.1`、`fabric-26.1`、
`neoforge-1.21.1`、`neoforge-26.1` 在 `prepareCommonSources` 中
`exclude 'com/sighs/baeffect/OggDecoder.java'`，并提供自己的同名实现。

### 资源标识符

| 版本 | 类型 |
| --- | --- |
| 1.20.1 – 1.21.1 | `net.minecraft.resources.ResourceLocation` |
| 26.1 | `net.minecraft.resources.Identifier` |

只影响 target 中的图标 `ResourceLocation`/`Identifier` 构造。

## Fabric Mixin 差异

| 版本 | 屏幕渲染注入点 | 鼠标注入点 |
| --- | --- | --- |
| 1.20.1 / 1.21.1 | `Screen#render` HEAD/TAIL | `MouseHandler#onPress(long,int,int,int)` |
| 26.1 | `Screen#extractRenderState` HEAD/TAIL | `MouseHandler#onButton(long,MouseButtonInfo,int)` |

Fabric 1.20.1/1.21.1 生成 `baeffect.refmap.json`；26.1 为命名运行时，不生成
refmap，构建时从 `baeffect.mixins.json` 中移除 `refmap` 字段。

## 已知上游问题

ApricityUI 1.2.6 – 1.3.0 的 Fabric 产物注册了
`com.sighs.apricityui.network.fabricutil.FabricAnnotationScanner` 作为
`IAnnotationScanner` 的 ServiceLoader 实现，但该类的构造器是 private，
`ServiceLoader` 在客户端启动时抛出：

```text
java.util.ServiceConfigurationError: com.sighs.apricityui.util.spi.IAnnotationScanner:
com.sighs.apricityui.network.fabricutil.FabricAnnotationScanner Unable to get public no-arg constructor
```

这是 AUI 本体的问题，不属于本项目 target 适配范围。Fabric target 可以编译、
产出与 Mixin refmap 均正常；运行验证需等待 AUI 修复该 ServiceLoader 注册。

## 构建验证

```text
common:                 gradlew -p common build
forge-1.20.1:           targets/forge-1.20.1/gradlew build
fabric-1.20.1:          targets/fabric-1.20.1/gradlew build
fabric-1.21.1:          targets/fabric-1.21.1/gradlew build   (Gradle JVM 21)
fabric-26.1:            targets/fabric-26.1/gradlew build     (Gradle JVM 25)
neoforge-1.21.1:        targets/neoforge-1.21.1/gradlew build
neoforge-26.1:          targets/neoforge-26.1/gradlew build   (Gradle JVM 25)
```
