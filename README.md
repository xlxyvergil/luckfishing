Luck Fishing — 1.21.1 / NeoForge
=================

把 1.20.1 Forge 版的 Luck Fishing（`../luckfishing1.20.1`）移植到 **Minecraft 1.21.1 + NeoForge**
的成果工程。钓上鱼时按配置的实体池抽怪，怪生成在钓鱼者身边；鱼竿可用铁砧加配置的强化道具升级，
强化等级决定可用的实体池与出怪概率，幸运值会改变池内权重分布。

功能与 1.20.1 版一一对应：强化配置读写、幸运权重、铁砧强化、tooltip、JEI 钓鱼池页面、
`/luckf reload` 客户端指令、服务端配置同步、车万女仆钓钩兼容。

版本矩阵
=========

| 项 | 值 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248（`gradle.properties` 的 `neo_version`） |
| ModDevGradle | 2.0.144（NeoForm 反编译管线） |
| Gradle wrapper | 9.2.1 |
| JDK | 21（toolchain 自动探测，foojoy 兜底下载） |
| 映射 | 官方映射 + Parchment 2024.11.17 |

可选依赖（都不打进 jar，缺了模组照常工作）：

| 模组 | 版本 | 作用 | 配置 |
|---|---|---|---|
| JEI | 19.21.0.247 | 钓鱼池/未强化/铁砧配方页面 | `compileOnly` + `localRuntime`（开发客户端可见） |
| 车万女仆 (Touhou Little Maid) | 1.5.3-neoforge+mc1.21.1 | 女仆钓钩也触发抽怪 | `compileOnly`，运行期用 `ModList.isLoaded` 守卫 |

女仆兼容（已对 `ck1.21.1/TouhouLittleMaid-1.21` 的 1.21.1 源码逐条核对）：

* `MaidFishedEvent extends Event implements ICancellableEvent`，包名 `...api.event` 未变，
  `getMaid()` 仍返回 `EntityMaid`（即 `TamableAnimal`，主手就是鱼竿）。
* 事件由 `NeoForge.EVENT_BUS.post(event)` 发出（不是 mod bus），所以监听注册在 `NeoForge.EVENT_BUS`。
* 抛钩收回时顺序为 `new MaidFishedEvent(...)` → `post` → 若 `isCanceled()` 则 `discard()` 并直接返回，
  **不执行 `spawnLoot`**；女仆的战利品用的是事件之前那份列表，`getDrops().clear()` 拦不住，
  必须取消事件——与 1.20.1 版的处理方式一致。

构建与运行
==========

```bash
./gradlew build          # 产物：build/libs/luckfishing-1.0.4.jar
./gradlew runServer      # 开发服务端（首次需改 run/eula.txt）
./gradlew runClient      # 开发客户端
./gradlew runClient -PquickPlayWorld=<存档名>   # 直入 run/saves 下的存档，跳过菜单
```

Windows 下用 `gradlew.bat`。首次构建要跑 NeoForm 反编译，可能十几分钟。

配置与指令
==========

* 配置文件：`config/luckfishing.json`。首次启动扫描全部 `MobCategory.MONSTER` 实体写入默认池
  （默认强化道具 `minecraft:iron_ingot`，等级 0，概率 0.2），之后每次启动只补新实体、保留手改内容。
* `rodTag` 默认 `c:tools/fishing_rod`（NeoForge 1.21 的通用标签约定，等价于 1.20.1 的
  `forge:fishing_rods`；沿用旧 id 会匹配不到任何鱼竿）。
* 池条目可带 `nbt` 字段，写法同 `/summon` 的 SNBT，生成时在实体入世界前加载（保留原 UUID）。
* 客户端 `/luckf reload`：重读配置、补测 JEI 实体模型尺寸、重载资源让 JEI 重建配方。
* 服务端登录时把配置同步给装了本模组的客户端，tooltip 以服务端配置为准；
  `displayTest="IGNORE_SERVER_VERSION"`，原版客户端也能进服，只是看不到 tooltip 行。

1.20.1 → 1.21.1 移植对照（本次实际改动）
======================================

| 1.20.1 Forge | 1.21.1 NeoForge | 说明 |
|---|---|---|
| `net.minecraftforge.fml.common.Mod` | `net.neoforged.fml.common.Mod` | 主类构造器改为接收 `IEventBus`，`RegisterPayloadHandlersEvent` / `FMLLoadCompleteEvent` 都挂 mod bus |
| `@Mod.EventBusSubscriber(bus = MOD/FORGE)` | `@EventBusSubscriber(bus = Bus.GAME/MOD, value = Dist.CLIENT)` | 游戏总线类全部改注解；`Dist` 换成 `net.neoforged.api.distmarker.Dist` |
| `ForgeRegistries.ENTITY_TYPES/ITEMS` | `BuiltInRegistries.ENTITY_TYPE/ITEM` + `Registries.ITEM` | **`ENTITY_TYPE`/`ITEM` 是带默认值的注册表**（猪/空气），`get()` 对未知 id 不返回 null，改用 `getOptional(...).orElse(null)` |
| `ItemStack.getTag()/getOrCreateTag()` | `DataComponents.CUSTOM_DATA`（`CustomData.update(...)` / `stack.get(...)`） | 1.21 物品 NBT 改数据组件；读写封装在 `Constants.applyUpgrade/getAppliedUpgrade` |
| `SimpleChannel` + `registerMessage` | `CustomPacketPayload` + `StreamCodec` + `PayloadRegistrar.playToClient` | 传输仍用字节数组避开 `writeUtf` 上限；`PacketDistributor.sendToPlayer` 发送 |
| `CHANNEL.isRemotePresent(player.connection.connection)` | `player.connection.hasChannel(SyncConfigPayload.TYPE)` | 判断对端是否装本模组，缺省时不下发 |
| `EntityType.spawn(level, tag, callback, pos, type, b, b)` | `EntityType.spawn(level, consumer, pos, type, b, b)` | 1.21.1 去掉 NBT 形参；回调在 `finalizeSpawn` 之后、加入世界之前执行，配置 NBT 与坐标覆盖都放回调里 |
| `ForgeRegistries.X.tags().getTag(key)` | `BuiltInRegistries.ITEM.getTagOrEmpty(key)` | JEI 鱼竿催化剂按标签枚举 |
| `RenderSystem.getModelViewStack()` + `renderEntityInInventoryFollowsMouse(gg, x, y, scale, ...)` | `InventoryScreen.renderEntityInInventory(gg, x, y, scale, Vector3f, Quaternionf, null, entity)` | 1.21.1 的实体预览改为在 GUI 姿势上绘制，可直接嵌进 JEI 平移过的姿势；不再动模型视图栈 |
| `VertexConsumer.vertex/color/uv/.../endVertex` | `addVertex/setColor/setUv/setUv1/setUv2/setNormal` | `EntitySizeCache` 里只记顶点包围盒的 consumer 按新接口重写 |
| `javax.annotation.Nullable` | `org.jetbrains.annotations.Nullable` | NeoForge 1.21 的注解来源 |
| 鱼竿判定：`instanceof FishingRodItem` + `forge:fishing_rods` + 手写 id 列表 | 再加 `stack.canPerformAction(ItemAbilities.FISHING_ROD_CAST)` | 1.21 起 NeoForge 用物品能力表达"能抛竿"，模组鱼竿可能既没继承 `FishingRodItem` 也没进标签；车万女仆 1.21.1 的钓鱼任务同样按这个能力判定 |
| `MinecraftForge.EVENT_BUS` | `NeoForge.EVENT_BUS` | 女仆钓钩事件就发在这条总线上（已对 `ck1.21.1/TouhouLittleMaid-1.21` 源码核对） |
| `mods.toml`（`modLoader`/`dependencies` 老形态） | `src/main/templates/META-INF/neoforge.mods.toml` + `generateModMetadata` | 1.21.1 仍需显式 `modLoader="javafml"` + `loaderVersion`，否则客户端报 `Missing ModLoader` |

运行验证记录
============

* `gradlew build` 通过，产物 `luckfishing-1.0.4.jar` 只含本模组类（JEI/女仆为 `compileOnly`）。
* `gradlew runServer`：模组以 `Luck Fishing 1.0.4 (luckfishing)` 加载，无异常；
  首次启动生成 `run/config/luckfishing.json`，实体池由当前注册表扫描得到。
* `gradlew runClient`（含 JEI 19.21.0.247）：JEI 走完全部插件阶段
  （categories / recipe catalysts / recipes），注册了 `luckfishing:fishing_loot`、
  `luckfishing:unupgraded` 两个配方类型并注册了铁砧配方，无异常。
* 未做端到端实钓验证（需要真实玩家抛竿）与 JEI 页面视觉确认（需要人工打开 JEI 界面）：
  相关代码只是 API 形态替换，逻辑与原版一致；实际使用中若模型大小/位置有偏差，
  调 `FishingLootCategory.renderMob` 的 `MOB_SIZE`、平移量与 `EntitySizeCache` 即可。

原始模板说明（NeoForge MDK）
==========================

* Mapping names: 官方 Mojang 映射，相关许可见 <https://github.com/NeoForged/NeoForm/blob/main/Mojang.md>
* 文档：<https://docs.neoforged.net/> ，Discord：<https://discord.neoforged.net/>
* 模板许可见 `TEMPLATE_LICENSE.txt`
