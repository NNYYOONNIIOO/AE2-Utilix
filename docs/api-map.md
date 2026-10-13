# appeng (ae2uel) -> ae2 (Supergiant) API 映射表

原则：Supergiant 把根包从 `appeng` 改成 `ae2`，但**并非所有类都只是改包名**。
已核实的差异分三类，迁移时按表处理。

## A. 只是换包名（直接 `appeng.` -> `ae2.` 即可）

| 旧 (ae2uel) | 新 (Supergiant) |
|---|---|
| appeng.api.parts.IPartHost | ae2.api.parts.IPartHost |
| appeng.api.parts.SelectedPart | ae2.api.parts.SelectedPart |
| appeng.api.parts.IPart | ae2.api.parts.IPart |
| appeng.api.config.Actionable | ae2.api.config.Actionable |
| appeng.api.util.AEPartLocation | ae2.api.util.AEPartLocation |
| appeng.api.util.AEColor | ae2.api.util.AEColor |
| appeng.api.util.AECableType | ae2.api.util.AECableType |
| appeng.api.util.IConfigManager | ae2.api.util.IConfigManager |
| appeng.api.networking.IGridNode | ae2.api.networking.IGridNode |
| appeng.api.networking.IGrid | ae2.api.networking.IGrid |
| appeng.api.networking.IGridConnection | ae2.api.networking.IGridConnection |
| appeng.api.networking.GridFlags | ae2.api.networking.GridFlags |
| appeng.api.networking.security.IActionHost | ae2.api.networking.security.IActionHost |
| appeng.api.implementations.items.IMemoryCard | ae2.api.implementations.items.IMemoryCard |
| appeng.api.implementations.items.MemoryCardMessages | ae2.api.implementations.items.MemoryCardMessages |
| appeng.api.upgrades.Upgrades | ae2.api.upgrades.Upgrades |
| appeng.items.AEBaseItem | ae2.items.AEBaseItem |
| appeng.block.AEBaseBlock | ae2.block.AEBaseBlock |
| appeng.tile.AEBaseTile | ae2.tile.AEBaseTile |
| appeng.items.tools.powered.powersink.AEBasePoweredItem | ae2.items.tools.powered.powersink.AEBasePoweredItem |
| appeng.core.AEConfig | ae2.core.AEConfig |
| appeng.me.GridConnection | ae2.me.GridConnection |
| appeng.me.GridNode | ae2.me.GridNode |
| appeng.me.Grid | ae2.me.Grid |

## B. 已确认不存在同名类（需要找新名字或改写）

| 旧 (ae2uel) | 状态 |
|---|---|
| appeng.api.AEApi | **不存在**。Supergiant 改用 `ae2.api.AECapabilities` 与各 API 定义类 |
| appeng.api.networking.storage.IStorageGrid | **不存在**，改为 `ae2.api.storage.MEStorage` 体系 |
| appeng.api.storage.IMEMonitor | **不存在**，改为 `MEStorageMonitor` |
| appeng.api.storage.data.IAEItemStack | **不存在**，改为 `AEKey` 体系 |
| appeng.api.storage.IStorageChannel | **不存在** |
| appeng.api.storage.channels.IItemStorageChannel | **不存在** |
| appeng.me.helpers.AENetworkProxy | **不存在** |
| appeng.tile.grid.AENetworkTile | **不存在** |
| appeng.tile.grid.AENetworkInvTile | **不存在** |
| appeng.tile.grid.AENetworkPowerTile | **不存在** |
| appeng.api.util.DimensionalCoord | **不存在**，改为 `ae2.api.util.DimensionalBlockPos` |
| appeng.me.GridAccessException | **不存在** |
| appeng.util.Platform | 改为 `ae2.util.Platform`（需逐方法核对） |
| appeng.helpers.DualityInterface | **不存在** |
| appeng.parts.automation.PartStorageBus | **不存在** |
| appeng.items.materials.ItemMagnetCard | **不存在** |
| appeng.parts.automation.PartBasicState | **不存在** |
| appeng.api.networking.IGridHost | 需确认新位置 |
| appeng.core.AppEng | 改为 `ae2.core.AppEng` |
| appeng.hooks.TickHandler | 需确认 |
| appeng.items.tools.powered.powersink.IAEItemPowerStorage | 需确认 |

## C. 项目内部自建类（需要一并迁移）

- `com.ae2utilix.AE2Utilix`（主类）-> 新包 `com.ae2utilix.ae2_utilix`
- `com.ae2utilix.AE2UtilixConfig`（配置）
- `com.ae2utilix.AE2UtilixUpgrades`（升级注册）
- `com.ae2utilix.ClientEventHandler` / `ClientUtil`
- `com.ae2utilix.network.NetworkHandler` + 各 Packet 类
- `com.ae2utilix.integration.crt.*`（CraftTweaker，依赖已复制）

## 待办：继续补全映射

- [ ] 逐个核对上面 B 类中"需确认"的项，落实新名字。
- [ ] 核对 Supergiant 的物品/方块注册方式（`ae2.init` 包），用于迁移注册代码。
- [ ] 核对网络包基类（`AppEngPacket` 不存在）的新写法。
- [ ] 核对 GUI 基类（`AEBaseGui`/`AEBaseContainer` 存在，但 `GuiMEMonitorable`、
      `ContainerMEMonitorable` 不存在）的新体系。
## 迁移实测补充（移相卡 / 产物返回卡）

首次真实迁移验证到的差异，后续所有功能共用：

| 旧写法 | Supergiant 写法 |
|---|---|
| `extends Item` + `this.setUnlocalizedName(x)` | `this.setTranslationKey(x)`（Cleanroom 映射） |
| `appeng.api.implementations.IUpgradeableHost` | `ae2.api.upgrades.IUpgradeableObject` |
| `host.getInventoryByName("upgrades")` | `host.getUpgrades()`，返回 `IUpgradeInventory` |
| `net.minecraftforge.items.IItemHandler`（升级库存） | `ae2.api.upgrades.IUpgradeInventory`（继承 `ae2.api.inventories.InternalInventory`） |
| `inv.getSlots()` | `inv.size()` |
| `ipHost.selectPart(Vec3d)` | `ipHost.selectPartWorld(Vec3d)` |
| `NBTTagCompound.hasNoTags()` | `NBTTagCompound.isEmpty()` |
| `appeng.api.config.Upgrades`（枚举）+ `Upgrades.X.registerItem(...)` | `ae2.api.upgrades.Upgrades.add(cardItem, machineItem, max)`（静态工具类，无枚举常量） |
| `appeng.api.implementations.items.IUpgradeModule` | 不存在；本模组改用自建 `IAE2UtilixUpgradeModule` |

**依赖接入方式（重要，已验证可用）**：Unimined 只接受能通过仓库解析的 mod 依赖，
本地 `files(...)` 声明不会进入 compileClasspath。Supergiant 的接入办法是：

1. 把 jar 安装进本地 Maven 仓库：`%USERPROFILE%\.m2\repository\ae2\supergiant\1.0.16\`
   （`supergiant-1.0.16.jar` + 同名 `.pom`）。
2. `dependencies.gradle` 里写 `modCompileOnly 'ae2:supergiant:1.0.16'` 与
   `modRuntimeOnly 'ae2:supergiant:1.0.16'`。
3. 构建命令加 `--offline` 可避免代理不可用时去校验远程仓库。

**构建命令（当前可用）**：

```
$env:JAVA_HOME = 'D:\java25'
.\gradlew.bat build --console=plain --no-daemon --offline --project-cache-dir <临时目录>
```
