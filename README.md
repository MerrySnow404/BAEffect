# [BAE]蓝白光标特效BAEffect
[![CurseForge Game Versions](https://img.shields.io/curseforge/game-versions/1521891?style=for-the-badge&logo=educative&logoColor=%23F16436&label=For%20MiaoCraft%20Version)](https://www.curseforge.com/minecraft/mc-mods/baeffect)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1521891?style=for-the-badge&logo=curseforge&logoColor=%23F16436&label=curseforge%20miaomiao%20ovo)](https://www.curseforge.com/minecraft/mc-mods/baeffect)

---
## 授权许可
代码美术资源除非另有说明，默认遵循MIT<br>
<b>
- [phi_origin.zip](src/main/resources/assets/baeffect/default_pack/phi_origin.zip)（Phigros原版皮肤，归属Pigeon Games）
- [ba-click-fx](https://github.com/CialloKing/ba-click-fx) Github项目<br>
保留其作者原许可，保留其作者原协议，如有侵权请联系删除。</b>
---

面向模组开发者的源码安装信息
-------------------------------------------
本代码遵循 Minecraft Forge 的安装方法。它会对原版 MCP 源代码应用
一些小型补丁，使你和他人都能访问
构建一个成功模组所需的部分数据和函数。

另请注意，这些补丁是针对“未重命名”的 MCP 源代码（即
SRG 名称）构建的——这意味着你将无法直接对照
普通代码来阅读它们。

安装流程
==============================

第 1 步：打开命令行并浏览到你解压 zip 文件的文件夹。

第 2 步：你需要做一个选择。
如果你更倾向于使用 Eclipse：
1. 运行以下命令：`./gradlew genEclipseRuns`
2. 打开 Eclipse，Import > Existing Gradle Project > 选择文件夹
   或运行 `gradlew eclipse` 来生成项目。

如果你更倾向于使用 IntelliJ：
1. 打开 IDEA，并导入项目。
2. 选择你的 build.gradle 文件并让其导入。
3. 运行以下命令：`./gradlew genIntellijRuns`
4. 如有需要，在 IDEA 中刷新 Gradle 项目。

如果你在任何时候发现 IDE 中缺少库，或者遇到了问题，你可以
运行 `gradlew --refresh-dependencies` 来刷新本地缓存。运行 `gradlew clean` 来重置一切
（这不会影响你的代码），然后重新开始该流程。

映射名称
=============================
默认情况下，MDK 配置为使用 Mojang 官方映射名称来表示 Minecraft 代码库中的
方法和字段。这些名称受特定许可证保护。所有模组开发者都应了解此
许可证，如果你不同意它，你可以在
build.gradle 中将映射名称更改为其他众包名称。有关最新许可证文本，请参阅映射文件本身，或此处的参考副本：
https://github.com/MinecraftForge/MCPConfig/blob/master/Mojang.md

附加资源：
=========================
社区文档：https://docs.minecraftforge.net/en/1.20.1/gettingstarted/
LexManos 的安装视频：https://youtu.be/8VEdtQLuLO0
Forge 论坛：https://forums.minecraftforge.net/
Forge Discord：https://discord.minecraftforge.net/