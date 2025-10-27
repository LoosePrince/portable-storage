# Portable Storage

<p align="center">
  <img src="docs/img/icon.png" />
  <br>
  <span style="font-size: 24px; font-weight: bold;">Portable Storage</span>
</p>

<div align="center">

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/fgNKEUno?label=Modrinth&color=00AF5C&logo=modrinth)](https://modrinth.com/mod/portable-storage/)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1365553?label=CurseForge&color=orange&logo=curseforge)](https://www.curseforge.com/minecraft/mc-mods/portable-storage/)
[![GitHub Downloads](https://img.shields.io/github/downloads/LoosePrince/portable-storage/total?label=GitHub&color=blue&logo=github)](https://github.com/LoosePrince/portable-storage/releases)
[![Mod百科](https://img.shields.io/badge/Mod百科-查看-blue?logo=bookstack&style=flat)](https://www.mcmod.cn/class/22574.html)

</div>

<div align="center">

![Minecraft](https://img.shields.io/badge/Minecraft-1.21-00AA00?logo=minecraft)
![Fabric Loader](https://img.shields.io/badge/Fabric-Loader-7a7a7a?logo=fabric)
![Fabric API](https://img.shields.io/badge/Fabric-API-7a7a7a?logo=fabric)
![Java](https://img.shields.io/badge/Java-21-7a7a7a?logo=java)
![GitHub Release](https://img.shields.io/github/v/release/LoosePrince/portable-storage?label=Latest%20Release&logo=github)

</div>

## 项目简介

**Portable Storage** 是一个基于 [Fabric](https://fabricmc.net/) 和 [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api) 的 Minecraft 模组，为玩家提供随身仓库功能。仓库目前采用无限堆叠设计，按物品变体（含组件和NBT数据）进行智能存储，支持多玩家共享和自动化设备交互。

无限并非真无限，单种物品最大为Long.MAX_VALUE

### 相关链接

- MOD百科：https://www.mcmod.cn/class/22574.html
- modrinth：https://modrinth.com/mod/portable-storage/
- curseforge：https://www.curseforge.com/minecraft/mc-mods/portable-storage

## 主要特性

这个模组为玩家背包UI上方添加一个仓库界面，界面顶部有搜索栏（支持搜索物品名称、模组ID、物品描述），下方显示仓库中存放的物品，可以自由拿取和放入物品。

仓库界面左侧有5个升级槽位，可以放入特定物品以解锁仓库的额外功能，右侧是仓库设置面板。

你需要使用一个下届之星才能启用仓库（可配置）

## 模组UI

![](docs/img/02-v1.3.8.png)


### 升级（放入升级槽位的物品，右键升级槽位可停用）：

- **工作台**：在工作台界面底部显示仓库界面，支持合成补充功能（自动从仓库补充物品到合成格）
- **漏斗**：自动拾取周围5格范围内的掉落物到仓库中
  - 中键点击槽位打开筛选界面，可以配置过滤和销毁
  - 过滤：可以配置过滤物品，过滤的物品不会被自动拾取
  - 销毁：可以配置销毁物品，销毁的物品会被自动拾取并销毁，不会进入你的仓库
- **箱子**：增加扩展升级槽5个
- **木桶**：创建绑定容器，支持多玩家仓库共享和自动化设备访问：
  - 放入槽位后升级为带有绑定的特殊容器
  - 取出槽位后作为方块放置，右键打开可存入物品到仓库
  - 自动化设备可以从绑定木桶中提取/存入玩家仓库物品
  - 放入其它玩家的绑定木桶可与其共享仓库（合并显示）
  - 支持多玩家共享同一仓库，但有防循环机制
  - shift+右键打开筛选界面，添加为黑名单的物品会被直接销毁
- **龙蛋（裂隙升级）**：右键进入 `裂隙` **维度**
  - 私人空间，16x16大小（一个区块），可以配置大小
  - 裂隙区块加载跟随玩家，无论是否在裂隙中
  - 裂隙永昼且无需照明
  - 玩家离开裂隙会生成“复制体”代替玩家接受效果（你可以在裂隙中放信标来获得信标效果）
  - 在裂隙中死亡时仓库钥匙会掉落在进入裂隙前的位置
  - 掉入裂隙虚空的话会退出裂隙且不会生成复制体

### 扩展升级槽

- **光灵箭**：射出的普通箭矢将使目标获得10s的发光效果
- **床**：右键启用的床升级原地睡觉（不记录重生点）
- **附魔之瓶**：在仓库显示“瓶装经验”
  - 右键点击槽位切换存取等级（`1、5、10、100`）
  - 中键点击槽位切换“等级维持”功能的启用状态
  - 等级维持：将维持玩家等级为当前等级，变化时会将多的存入少的取出
  - 空手右键瓶装经验存入相应等级的经验，左键取出
  - 持有空玻璃瓶右键瓶装经验以11点经验换1个附魔之瓶，经验不足时多出的玻璃瓶会被放入仓库
- **活塞**：自动补充主副手物品，手持活塞左键可旋转方块旋转

### 特殊槽位

- **流体**：拿流体桶右键此槽位可存入流体，位于升级槽位顶部
  - 可存入 岩浆、水、牛奶
  - 流入流体后会在仓库中显示流体
  - 拿着空桶右键流体以取出流体桶
  - 开启 **自动传入** 时 `shift+点击` 存入流体桶会分为空桶和流体存入
  - 开启 **自动传入** 时背包有空桶 `shift+点击` 仓库的流体快捷取出流体桶
- **垃圾桶**：有箱子升级即可用，可以放入任何物品，位于扩展升级槽位顶部
  - 物品会在退出存档或离线时销毁
  - 使用不同的物品覆盖以销毁
  - 在销毁前可以取出物品

### 客户端设置

以下设置保存在客户端，重启游戏和切换界面不会丢失：

- **折叠仓库**：将仓库界面折叠为一个选项卡形式显示
- **排序方案**：支持四种排序方式：
  - 数量（按物品数量排列）
  - 物品名称（按字母顺序）
  - 模组ID（按模组名称）
  - 更新时间（最近操作的物品优先）
- **排序顺序**：可切换正序（升序）或倒序（降序）
- **合成补充**：在合成台界面使用时，自动从仓库补充物品到合成格，支持连续合成
- **自动传入**：使用 `Shift+左键` 点击物品时，优先将物品存入仓库而非背包或快捷栏
- **智能折叠**：折叠因nbt不同导致无法折叠的物品，搜索时展开（右键折叠自动搜索）
- **搜索位置**：调整搜索栏的位置：
  - 底部：显示在仓库UI下方
  - 顶部：在玩家背包UI上方（靠近）
  - 顶部2：在玩家背包UI上方（更靠上）
  - 中间：插入在背包UI和仓库UI之间
- **返回原版**：点击打开原版工作台界面，需要通过右键工作台打开合成界面的情况下才能返回（有配方书）

### 其它机制

- 弓和弩的箭矢支持从仓库扣除（无限则不扣）
- 在仓库中对物品中键可以将其设为收藏和取消收藏，收藏物品会排在其它物品前面
- 拼音搜索：可以首字母
- 隐藏背包界面的配方书按钮（需要工作台升级）
- 快速存入（自动传入补充）：光标持有物品时，按住shift+双击相同物品，会将背包中全部相同物品存入仓库
- 无限流体：流体在仓库中数量大于阈值时数量显示∞（无限），存取都不会使其数量变化
  - 岩浆阈值默认10000，水阈值默认2
- 物品大小限制：仓库会检查单个物品的大小，如果超过限制则无法存入（会在存入时丢失）

### 机制说明

[机制说明](docs/机制说明.md)

**包含：**

- 绑定木桶的详细说明
- 增量同步详细机制说明
- 拼音搜索详细机制说明

### 配置

1. 客户端配置

`config\portable-storage-client.json`

2. 服务端配置

`config\portable-storage-server.toml`

具体配置说明查 [配置说明](docs/配置.md)

## 系统要求

- **Minecraft 版本**：
  - 1.21
  - 1.21.1
- **Fabric Loader**：0.17.2 或更高版本
- **Fabric API**：兼容版本（推荐最新稳定版）

## 适配

- **EMI**：1.1.10+1.21（仅在工作台升级时在工作台界面有效）

更多适配逐渐扩展中...

## 安装方法

1. 安装 [Fabric Loader](https://fabricmc.net/use/) 和 [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)。
2. 下载本模组的 jar 文件，放入 `mods` 文件夹。
3. 启动游戏，模组会自动加载。

## 开发与贡献

欢迎提交 Issue 或 PR 参与开发！

[开发说明](docs/开发说明.md)

## 致谢与协议

- 本项目由 LoosePrince 开发，遵循 MIT 协议。
- 拼音搜索使用 [TinyPinyin](https://github.com/promeG/TinyPinyin)
- 感谢 Fabric 社区及所有开源贡献者。

### 创意和意见提供者

- 感谢 [雪开](https://github.com/XueK66) 提出的光灵箭升级和床升级想法
- 感谢群友 [小泽](https://github.com/Dreamwxz) 提出的裂隙升级和装填想法
- 感谢群友 PMCK 提出的经验瓶升级想法和床升级实现思路
- 感谢群友 酸甜＆牛奶 提出的流体储存想法
- 感谢B站用户 [恋雪绊人心](https://space.bilibili.com/6317479) 发现的大量BUG和性能问题，和协助测试

---

如有建议或问题，欢迎在 Issue 区或Q群726741344留言反馈！
