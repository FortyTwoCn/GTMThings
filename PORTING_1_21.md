# GTMThings 1.21.1 NeoForge / GTCEu 8.0 移植

本工作树从 GTMThings 1.6.0（`de5492e`，Minecraft 1.20.1 Forge）移植。目标依赖是 GTCEu **8.0** 的原生 1.21 分支。

## 依赖基线

| 组件 | 验证版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248 |
| Java / Gradle | 21 / 8.14.2 |
| GTCEu | `8.0.0-20261001.095212-101`，运行时 `8.0.0-SNAPSHOT+d2ef26f` |
| ModularUI | `3.3.1-20260922.143646-5` |
| AE2 / GuideME | 19.2.8 / 21.1.1 |
| Registrate / Configuration | MC1.21-1.3.0+67 / 3.1.1-neoforge |
| JEI / EMI / Jade | 19.25.1.328 / 1.1.24+1.21.1 / 15.10.0+neoforge |
| 可选 FTB Library / Teams / Architectury | 2101.1.30 / 2101.1.9 / 13.0.8 |

GTCEu 源码参考为 [FortyTwoCn/GregTech-Modern 的 1.21 分支](https://github.com/FortyTwoCn/GregTech-Modern/tree/1.21)，提交 `c72dc16b52795cbb0456b6e15e13cc4a85c0ed1b`，其中合入的上游版本为 `d2ef26f86`。GTCEu 与 ModularUI 的 Maven 时间戳版本固定，避免后续 SNAPSHOT 更新悄悄改变 API；更新时应复跑下述验证。

## 原生迁移

- 使用 NeoForge 生命周期、事件和 `RegisterCapabilitiesEvent`，Java 21、ModDevGradle 及 `neoforge.mods.toml`；GT8 使用 `BlockEntityCreationInfo`、`MachineEntry`、sync-system 持久化和同步。
- 全部 GUI 使用 GT8 的 `brachy.modularui` 机器、覆盖板和物品菜单工厂。物品设置绑定实际使用的背包槽位，支持副手。客户端事件仅在客户端注册。
- 虚拟物品、标记、流体容量和 ME 缓冲使用持久化及网络可编码的原生 Data Components；其他终端设置使用复制后写入的 `minecraft:custom_data`。
- 保留原机器注册规则，按 GTCEu 高阶内容配置分别提供 260 / 157 个机器 ID。开发环境启用高阶内容；普通整合包中的高阶内容由 GTCEu 配置控制。
- UHV 及以上无线接收覆盖板的配方和材料列表同样遵循高阶配置，避免默认配置下引用尚未注册的 GT8 高阶元件。
- 迁移创造输入仓、超大总线及双仓、催化剂槽、可编程仓和覆盖板、数字采矿机、无线能量及算力、监视器和终端、无线物品与流体覆盖板、虚拟物品及 AE2 存储元件。
- GT8 过滤器保存的是物品槽，实际过滤规则为临时对象。覆盖板和采矿机在重载后从原生过滤器 Data Components 恢复规则；恢复采矿过滤器不重置已保存的队列。FTB 所有者显示同时支持玩家 UUID 和队伍 UUID。
- ME 输出使用 AE2 19 的网格节点和 `AEKey` 存储，满网或离线时保留输出；拆机时将待输出内容保存在机器物品上。模拟处理预留重复键容量，防止 long 溢出。
- 多方块终端使用 GT8 的公开结构 API，处理重复层、朝向、翻转、线圈选择、禁用可选仓口、生存材料和放置取消后的回滚；可从背包、嵌套物品容器及已绑定的 AE2 无线终端取材。
- 固定的 GT8 快照中，XOR 谓词会把全局最小数量错误地用于尚未出现该仓口的重复层，导致装配线数据仓位验证失败。本移植对无显式层最小数量的 XOR 谓词修正层检查，保留全局数量与显式层数量验证；建造规划只选择一个互斥分支，并优先保留已放置的仓口类型。
- 模型元数据采用 `neoforge_data`。`src/generated/resources` 中的资源随源码提交；`.cache` 不进入 JAR。配方由 GT8 原生运行时数据包生成，数据生成的静态资源主要是模型、方块状态和语言文件。

## 构建与验证

在项目根目录使用完整 Java 21 JDK：

```sh
./gradlew runData
./gradlew build
./gradlew runGameTestServer
./gradlew runGameTestServer -PtestFtbIntegration
```

产物为 `build/libs/gtmthings-1.6.0.jar`；只打包 `main` 源集，GameTest 和客户端测试工具不进入发行 JAR。安装时同时提供上述 GTCEu 8.0 和 AE2 19.2.8。GTCEu 自带其 Jar-in-Jar 依赖，AE2 还需要 GuideME。FTB Teams 和 Jade 为可选集成。

开发客户端：

```sh
./gradlew runClient
```

先创建名为 `port-smoke` 的 1.21.1 测试世界，再运行自动 GUI 验证：

```sh
./gradlew runSmokeClient
```

Linux 无窗口环境需要可用的 X11 显示和 OpenGL，例如 Xvfb 配合软件渲染。测试通过实际服务端菜单、客户端布局与渲染、同步数据包打开机器及物品界面，截图写入 `run/screenshots/port_gui_*.png`。数字采矿机半径与副手终端重复层设置会发送 C2S 数据包并检查服务端实际状态。首次初始化 EMI 配方索引耗时较长，测试会等待其加载完成后退出。

GameTest 覆盖原机器 ID 和原生方块实体保存重载、超大数量与溢出、虚拟物品副手槽位、ME 缓冲与拆机数据、真实 AE2 网格满载及暂停恢复、实际采矿与队列及过滤器重载、普通容器输出容量、可编程单方块机器、催化剂、无线能量实际扣除、覆盖板白名单传输及保存、算力绑定、FTB 队伍所有者显示、多方块建造、旋转、重复和翻转、生存材料消耗及取消线圈替换的回滚。CI 构建和发布均执行原生服务端 GameTest，并复跑安装 FTB Teams 的环境。

## 已执行的验证

| 检查 | 结果 |
| --- | --- |
| Java 21 构建、Spotless、JAR 资源检查 | 通过；发行 JAR 不含测试工具或数据生成缓存 |
| 原生数据生成 | 通过；821 个生成 JSON，其中 260 个机器方块状态 |
| NeoForge 服务端 GameTest，无 FTB Teams | 23 / 23 通过 |
| NeoForge 服务端 GameTest，含 FTB Teams | 23 / 23 通过 |
| 实际客户端菜单、布局和渲染 | 21 个界面通过，含物品和流体过滤器弹窗 |
| GUI C2S 同步 | 采矿机半径和副手终端重复层数均更新服务端实际状态 |
| 正式 JAR / 默认关闭高阶内容 | 启动通过；157 个原机器 ID 实际放置、保存和重载 |
| 正式 JAR / 开启高阶内容 | 启动通过；260 个原机器 ID 实际放置、保存，重启后逐个检查全部通过；磁盘方块实体 ID 与原版本清单完全一致 |
| 正式 JAR / 原生世界数据重载 | 超大总线 `2147483647` 个物品、ME 缓冲 `1234567` 个物品及 `9876543` mB 流体完整保留 |

上述客户端验证使用 1280×800、GUI 比例 2、Xvfb 和软件 OpenGL，安装 GTCEu、AE2、JEI、EMI、Jade 和 FTB Teams。正式服务端使用独立的 NeoForge 安装目录及发行 JAR，不使用 Gradle 开发类路径。

已验证的发行产物为 `build/libs/gtmthings-1.6.0.jar`，SHA-256：

```text
4e6e3446636e338ed84b8eea30e0f691611514776d13ac9e9d9d5fa75f48c6e1
```

本次云工作区的验证日志位于 `/workspace/scratch/gtm-port/`：`verification-team-final.log`、`verification-no-ftb-final.log`、`verification-filter-final.log`、`production-final-reload.log` 和 `production-high-reload.log`。正式服务端日志中的 Mojang 公钥请求失败来自测试环境无法连接其认证服务；服务端成功加载世界并完成上述检查。缺少可选 JEI / EMI 时的混入目标警告来自 GTCEu 快照。

## 验证边界

本分支面向 Minecraft 1.21.1 NeoForge 原生世界。保存和重载验证覆盖当前版本的机器、库存、ME 缓冲、过滤器和无线绑定数据；跨版本存档转换不在本项目范围内。

自动测试不等同于大型整合包的长期运行验证。第三方整合包脚本、其他附属模组、所有 GTCEu 多方块组合和跨机器配方链仍需要在目标整合包中回归；GTCEu 8 当前基线为指定快照版本。
