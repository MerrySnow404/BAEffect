# [BAE]蓝白光标特效BAEffect
[![CurseForge Game Versions](https://img.shields.io/curseforge/game-versions/1521891?style=for-the-badge&logo=educative&logoColor=%23F16436&label=For%20MiaoCraft%20Version)](https://www.curseforge.com/minecraft/mc-mods/baeffect)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1521891?style=for-the-badge&logo=curseforge&logoColor=%23F16436&label=curseforge%20miaomiao%20ovo)](https://www.curseforge.com/minecraft/mc-mods/baeffect)

---
## 授权许可
代码美术资源除非另有说明，默认遵循[LICENSE](LICENSE)<br>
<b>
- [phi_origin.zip](common/src/main/resources/assets/baeffect/default_pack/phi_origin.zip)（Phigros原版皮肤，归属Pigeon Games）
- [ba-click-fx](https://github.com/CialloKing/ba-click-fx) Github项目<br>
保留其作者原许可，保留其作者原协议，如有侵权请联系删除。</b>
---

## 项目结构

本项目使用 `common + targets/<loader>-<minecraft-version>` 结构维护多个
Minecraft 加载器和版本：

- `common/`：所有没有确证版本差异的共享源码与资源。可以引用 Minecraft 和
  加载器类型；AUI 页面、画布特效、Phira 资源包解析与配置逻辑都在这里。
- `targets/`：每个加载器与 Minecraft 版本一个独立工程，只放入口、事件、
  Mixin、metadata 和已确证不同的 API 实现。
- `docs/version-differences/baeffect.md`：经源码/字节码确认的版本差异登记。

`common` 也能单独编译：

```bash
./gradlew -p common build
```

## Target 与构建

| Target | Gradle JVM | 构建 |
| --- | --- | --- |
| `forge-1.20.1` | 17 | `targets/forge-1.20.1/gradlew build` |
| `fabric-1.20.1` | 17 | `targets/fabric-1.20.1/gradlew build` |
| `fabric-1.21.1` | 21 | `targets/fabric-1.21.1/gradlew build` |
| `fabric-26.1` | 25 | `targets/fabric-26.1/gradlew build` |
| `neoforge-1.21.1` | 21 | `targets/neoforge-1.21.1/gradlew build` |
| `neoforge-26.1` | 25 | `targets/neoforge-26.1/gradlew build` |

在 IDEA 中直接打开任意 `targets/<loader>-<version>/` 目录即可；`common/`
会作为 composite build 一起导入。

## 依赖

所有 target 使用 [ApricityUI](https://github.com/Tower-of-Sighs/ApricityUI)
作为 HTML/Canvas 渲染层。AUI 版本按 target 记录在各自的
`gradle.properties` 中；升级时请同步更新
`docs/version-differences/baeffect.md` 的支持矩阵。

## 已知上游问题

ApricityUI 1.2.6 – 1.3.0 的 Fabric 产物在
`META-INF/services/com.sighs.apricityui.util.spi.IAnnotationScanner` 中注册了
`FabricAnnotationScanner`，但该类只有私有构造器，`ServiceLoader` 在客户端启动时
会抛出 `ServiceConfigurationError`。该问题位于 AUI 本体，BAEffect 的 Fabric
target 可以正常编译，但运行时需要等待 AUI 修复后再验证。
