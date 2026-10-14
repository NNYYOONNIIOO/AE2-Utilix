# AE2-Utilix CRL 迁移 TODO

目标：把 Forge/ae2uel 版的 AE2-Utilix 迁到 Cleanroom + AppliedEnergistics2-Supergiant，
仓库 `ae2-utilix-crl`，产物名形如 `ae2_utilix-1.3.0-crl.jar`。

源仓库：`C:\Users\wubid\Documents\Codex\2026-07-19\d-trae-ae2-utilix-1-12-2`
目标仓库：`C:\Users\wubid\Documents\Codex\2026-07-19\ae2-utilix-crl`
Supergiant 源码：`D:\GitHub\Applied-Energistics-2-Supergiant-main`
Supergiant 依赖：`libs\AppliedEnergistics2-Supergiant-1.0.16.jar`
模板：`D:\ModderUseful\mdk\CleanroomModTemplate-main`（已铺到目标仓库根目录）

## 已核实的关键事实（不要重复踩坑）

- Supergiant jar 内 2942 个类**全部是 `ae2.*`**，没有任何 `appeng` 兼容层。
  因此**不是** `appeng.` 批量替换成 `ae2.` 就能编译，必须逐个核对类名与包路径。
- 已确认改名的例子：`appeng.api.AEApi` 不存在了；`DualityInterface`、`PartStorageBus`、
  `ItemMagnetCard` 在 Supergiant 里都找不到同名类（需逐个查新名字）。
- `ae2.core.AppEng` 对应旧的 `appeng.core.AppEng`；`ae2.api.networking.GridFlags` 对应旧 GridFlags。
- 源仓库共 166 个 Java 文件，其中 105 个引用 `appeng.*`，合计 784 处引用需要处理。
- 模板用 Unimined + Cleanroom 工具链，与原 ForgeGradle 脚本完全不兼容，构建文件要重写。
- 通用系列（通用 ME 接口/通用输入输出总线/通用方块存储总线）在 Supergiant 里自带，**不迁移**。

## 需要迁移的功能清单（用户指定）

1. 产物返回卡（Product Return Card）
2. 移相卡（Phase Card）
3. 耦合手杖（Coupling Staff）
4. 物质分解器（Matter Decomposer）
5. ~~打包器 + 设备包裹 + 空设备包裹~~ —— **不迁移**：Supergiant 自带 ME封装胶带（`ae2:me_packing_tape`）与封装设备（`ae2:package`），可打包 ME 接口 / 样板供应器 / ME 驱动器方块及接口 / 样板供应器面板，并完整保留状态。
6. ~~相位 ME 接口~~ → **相位样板供应器 / Phased Pattern Provider**（`phased_pattern_provider`，2026-10-14 改造）
7. 晶体培育仓
8. ME 频道呼喊器 / ME 频道监听器
9. ME 接口放入磁引卡功能（不联动 AE2 Better Magnet Card 的内容）
10. ME 终端（方块）、ME 合成终端（方块）、ME 样板终端（方块）、ME 接口终端（方块）
11. ME 方块存储总线（Parts：PartBlockStorageBus 等）

---

## 阶段 0：仓库与构建骨架

- [x] 0.1 `gradle.properties`：`mod_id=ae2_utilix`、`mod_name`、`root_package=com.ae2utilix`、
      `mod_version=1.3.0`，并让产物名带 `-crl` 后缀。
- [x] 0.2 `build.gradle`：改成 Unimined/Cleanroom，依赖换成
      `libs/AppliedEnergistics2-Supergiant-1.0.16.jar`（替换原 ae2uel 依赖）。
- [x] 0.3 产物命名：确认最终 jar 为 `ae2_utilix-1.3.0-crl.jar`。
- [x] 0.4 `.gitignore`、`settings.gradle`、`mcmod.info`、`pack.mcmeta` 按新 modid 调整。
- [x] 0.5 构建骨架已验证通过：产物 `ae2_utilix-crl-1.3.0.jar`。

## 阶段 1：基础设施与 API 差异摸底

- [ ] 1.1 逐类建立映射表：`appeng.X` -> Supergiant 中的真实位置与名字，
      重点：AEApi、IGridNode/IGrid、GridFlags、IStorageGrid、IMEMonitor、
      DualityInterface、PartStorageBus、ItemMagnetCard、AEConfig、Platform。
- [ ] 1.2 把映射表落到 `docs/api-map.md`，后续迁移全部按表改。
- [ ] 1.3 主类改造：源 `com.ae2utilix.AE2Utilix` -> 新 `@Mod` 主类 + 代理（IProxy/ClientProxy/CommonProxy）。
- [ ] 1.4 配置类 `AE2UtilixConfig` 迁移（Forge Configuration -> Cleanroom 对应 API）。
- [ ] 1.5 网络包 `NetworkHandler` 及其包类迁移（SimpleNetworkWrapper 是否仍可用需确认）。
- [ ] 1.6 Mixin 基建：`AE2UtilixMixinPlugin`、`mixins.ae2_utilix*.json` 的包名与 refmap
      按模板的 mixin 配置重做。

## 阶段 2：逐功能迁移（每项独立验证）

- [ ] 2.1 ME 频道呼喊器 / ME 频道监听器
      - `block/BlockMEChannelCaller.java`
      - `block/TileMEChannelCaller.java`
      - `item/ItemMEChannelCaller.java`
      - `integration/MEChannelCallerNetwork.java`
      - `integration/MEChannelCallerSavedData.java`
      - `integration/MEChannelCallerMemoryCardHandler.java`
      - `integration/MEChannelCallerTOPIntegration.java`（TOP 依赖是否保留待定）
      - `integration/WirelessTerminalNetworkLocator.java`
      - `integration/BaublesIntegration.java`（饰品栏联动，可选）
      - 资源：`assets/ae2_utilix/models|blockstates|textures` 中 me_channel_caller/listener 相关
      - 配方：`recipes/crafting/...`、`recipes/me_channel_caller.json`
- [ ] 2.2 ME 方块存储总线（parts）
      - `parts/PartBlockStorageBus.java`、`parts/ItemPartBlockStorageBus.java`
      - `mixin/MixinPartStorageBusAccessor.java`
- [ ] 2.3 ME 终端系列（方块）
      - `block/terminal/BlockStorageTerminal.java`、`TileStorageTerminal.java`
      - `block/terminal/BlockCraftingTerminal.java`、`TileCraftingTerminal.java`
      - `block/terminal/BlockPatternTerminal.java`、`TilePatternTerminal.java`
      - `block/terminal/BlockInterfaceTerminal.java`、`TileInterfaceTerminal.java`
      - `block/terminal/BlockFullTerminal.java`、`TileFullTerminal.java`（上四者共用的宿主）
      - `gui/*`（FullTerminalGuiHandler、ContainerFull*、GuiFull*、NativePatternTerminalHostAdapter）
      - `mixin/MixinTilePatternTerminalRandomComplement.java`、`MixinContainerFullPattern*`
      - 注意：样板终端要支持 AE2FC 流体样板；AE2FC 的 CRL 版是否可用待确认
- [ ] 2.4 相位 ME 接口
      - `block/BlockPhaseInterface.java`、`TilePhaseInterface.java`、`IPhaseLinkHost.java`
      - `item/ItemPhaseCard.java`
- [x] 2.5 晶体培育仓 ← 已完成并编译通过（2026-10-14）
      - `block/BlockCrystalGrowthChamber.java`、`block/TileCrystalGrowthChamber.java`
      - `recipe/CrystalGrowthRecipe.java`、`recipe/CrystalGrowthRecipes.java`
      - `gui/GuiCrystalGrowthChamber.java`、`gui/ContainerCrystalGrowthChamber.java`
      - `client/RenderCrystalGrowthChamber.java`、`network/PacketToggleCGCButton.java`
      - 从源仓库移除：并行卡（不在用户迁移列表内），CGC 只支持 AE2 原版超频卡。
      - `block/BlockCrystalGrowthChamber.java`、`TileCrystalGrowthChamber.java`
      - `recipe/CrystalGrowthRecipe.java`、`recipe/CrystalGrowthRecipes.java`
      - `integration/crt/CrystalGrowthChamberHandler.java`（CraftTweaker，可选）
      - `gui/GuiCrystalGrowthChamber.java`、对应 Container
      - 配方资源：`recipes/fluix_crystal.json`、`recipes/meteorite_*_budding.json`
- [x] 2.6 ~~打包器 / 设备包裹 / 空设备包裹~~ —— **不迁移**（2026-10-14 确认）
      - Supergiant 自带 `ae2:me_packing_tape`（ME封装胶带）与 `ae2:package`（封装设备），
        白名单覆盖 ME 接口 / 样板供应器 / ME 驱动器方块，以及接口 / 样板供应器面板，状态完整保留。
      - 本模组原有的打包器（Packer）与设备包裹（Device Package）不再移植。
- [x] 2.7 产物返回卡 / 移相卡  ← 已完成并编译通过（2026-10-13）
      - `item/ItemProductReturnCard.java`、`item/ItemPhaseCard.java`
      - `integration/ProductReturnHandler` 等相关处理
- [ ] 2.8 耦合手杖
      - `item/ItemCouplingStaff.java`
- [x] 2.9 物质分解器  ← 已完成并编译通过
      - `item/ItemMatterDecomposer.java`
- [ ] 2.10 ME 接口放入磁引卡
      - 源参考：`integration/BMCCompat.java`、`BMCMagnetHelper.java`、`BMCInteractionHandler.java`
        （这些是 AE2 Better Magnet Card 联动，**只保留"接口内放卡"功能本体**）
      - `mixin/MixinAEBaseContainer.java`、`mixin/MixinDualityInterface.java`
      - 注意：Supergiant 里 DualityInterface 改名，需先查清新名字
- [x] 2.11 卡片类基础设施  ← 已完成（自建 IAE2UtilixUpgradeModule + ModUpgradeRegistry）
      - `item/IAE2UtilixUpgradeModule.java`、`IAE2UtilixExtendedUpgradeInventory.java`
      - `item/ItemParallelCard.java`、`item/ItemOverflowDestructionCard.java`（被上述功能依赖）
      - `mixin/MixinUpgradeInventory.java`、`mixin/MixinUpgradeInvFilter.java`

## 阶段 3：资源与本地化

- [ ] 3.1 `assets/ae2_utilix/lang/zh_cn.lang`、`en_us.lang` 只保留已迁移功能的条目。
- [ ] 3.2 模型/贴图：按功能逐个挑，不要整包拷贝。
- [ ] 3.3 `mcmod.info` 信息、logo、描述更新为 CRL 版。

## 阶段 4：验证与发布

- [ ] 4.1 `gradlew build` 通过，产物名为 `ae2_utilix-1.3.0-crl.jar`。
- [ ] 4.2 游戏内冒烟测试清单（逐项勾选）：
      方块可放置、GUI 可打开、配方可见、卡片可存读、频道呼喊器可跨维度传输。
- [ ] 4.3 初始化 git 仓库并提交本地。
- [ ] 4.4 推送到 `ae2-utilix-crl`（需要用户提供仓库地址或建仓权限）。

## 已知风险与待确认

- [ ] R1 **API 差异巨大**：Supergiant 无 appeng 兼容层，784 处引用要逐个核对，
      阶段 1.1 的映射表是全部后续工作的前提，必须先做完。
- [ ] R2 **可选联动**：AE2FC（流体样板）、Mekanism Energistics（气体）、
      ThaumicEnergistics（源质）、NAE2、RandomComplement、Baubles、
      AE2 Better Magnet Card、CraftTweaker —— 这些在原仓库都是可选联动，
      CRL 生态下是否有对应版本未知。**默认先全部关闭**，只迁移核心功能；
      需要时再逐个接回。
- [ ] R3 **样板终端方块**：源实现里有大量针对 AE2FC/RandomComplement 的 mixin，
      纯 CRL 环境下要退回到"原生 AE2 样板终端行为"。
- [ ] R4 **构建环境**：本机 Gradle 存在构建卡死问题（工程内 `.gradle` 状态缓存损坏，
      已移出备份）。CRL 侧若复现，用独立 project-cache-dir 规避。
- [ ] R5 **TOP 联动**：ME 频道呼喊器的 TOP 显示依赖 theoneprobe，
      该模组在 CRL 下是否可用待确认；不可用则该项跳过。

## 执行约定

- 每完成一个功能项：编译通过后单独提交一次，提交信息写明功能名。
- 每迁移一个功能前，先在 `docs/api-map.md` 里确认它用到的 API 已登记映射。
- 不要修改本 TODO 中"不迁移"的功能；通用系列由 Supergiant 自带。
---

## 本机构建环境（已跑通，照此执行）

构建必须用 Java 25，且需要走代理下载依赖：

```
$env:JAVA_HOME = 'D:\java25'
.\gradlew.bat build --console=plain --no-daemon --project-cache-dir <临时目录>
```

- 本机默认 java 是 1.8，模板要求 Java 25 工具链（`java.toolchain = Java 25`），
  不设 `JAVA_HOME` 会在 Unimined 阶段失败。
- 代理：`127.0.0.1:7897`。wrapper 下载 Gradle 发行包时必须走代理，
  已写在 `gradle.properties` 的 `systemProp.*.proxy*`。
- 首次构建约 3m45s（含下载 Minecraft/配置 Unimined），后续会快。
- 产物：`build/libs/ae2_utilix-crl-1.3.0.jar`（另有 `-dev` 与 `-sources`）。
- 已验证通过时间：2026-10-13 20:28。

## 用户已确认的迁移范围调整

- 样板终端（方块）**不兼容 AE2FC 流体样板**，Supergiant 自带样板功能，直接调用其 API；
  也不再有 AE2FC/RandomComplement 的 mixin。
- Mekanism Energistics（气体）、ThaumicEnergistics（源质）、NAE2、RandomComplement、
  AE2 Better Magnet Card **全部不兼容**（无对应 Supergiant 版本）。
- theoneprobe 在 CRL 下可用，TOP 集成保留。
- Baubles、CraftTweaker 依赖文件已复制到 `libs/` 并在依赖脚本中登记。
