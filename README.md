# KineticCore

[English](#english) | [简体中文](#简体中文)

## English

**Project role:** KineticCore is the shared base API and infrastructure layer for the Kinetic ecosystem. It provides configuration, networking, synchronization, reusable GUI and editor tools, and module registration for other projects. Actual gameplay content is delivered by separate Kinetic add-on projects.

Those add-ons cannot be safely merged into KineticCore or into one another. Each has its own feature scope, dependencies, configuration, release cycle, and user audience; keeping them separate enables optional installation and avoids forcing unrelated gameplay systems and dependencies into one package.

### Overview

**KineticCore** is the foundation of the Kinetic mod family. It provides the shared API layer, module bootstrap system, unified configuration center, networking, compressed payload utilities, reusable GUI components, and command extension framework. It also keeps a set of low-level gameplay tweaks and stability fixes that belong in the core.

### Key Features

- Unified `F6` configuration center for installed Kinetic modules.
- Server-authoritative configuration API with permission checks and server-side persistence.
- Shared networking and compressed payload utilities for large configuration data.
- Reusable GUI, selector, virtual-canvas, HUD editor, and advanced RGB palette APIs.
- `/kt` command extension framework for companion modules.
- Flight, inertia and noclip control with client/server synchronization.
- Manual crawling support.
- TPS/MSPT/FPS monitoring and editable HUDs.
- First-join rewards, equipment and command execution.
- World initialization and spawn-management systems.
- Per-attribute minimum/maximum range overrides.
- General gameplay tweaks such as food, farmland, cobweb, creative and PVP rules.
- NBT inspection and item-data copy utilities.
- Resource-pack/datapack ordering helpers.
- Compact status-effect HUD and night-vision flicker fixes.
- Log cleanup, startup information, entity fixes, world-deletion protection and other low-level stability tools.

### Configuration

Most core configuration files are stored under:

```text
config/kineticcore/
```

Server gameplay rules are persisted by the server. Pure client preferences, such as HUD placement, remain local client settings.

### Requirements

- Java 17
- Curios: optional integration
- JEI: optional integration

## Feature Reference
### Config Details
| Item | Description |
|---|---|
| **Automatically Scan Registered Attributes** | Adds new attributes, removes obsolete entries, and refreshes translated comments automatically. |
| **Attribute Range Limits** | Configure enabled state and numeric bounds for every registered ranged attribute. |
| **%s — Enabled** | Enable custom bounds for %s. |
| **infinity** | This bound is extremely large, extremely small, or infinite. Scientific notation, Infinity, and -Infinity are supported. |
| **%s — Maximum** | Maximum allowed value for %s. |
| **%s — Minimum** | Minimum allowed value for %s. |
| **Default Client Options** | Export the current Minecraft client options as KineticCore's defaults for a fresh options file. |
| **Save Current Options as Defaults** | Overwrite config/kineticcore/defaultoptions.txt with the current options.txt. |
| **FPS HUD** | Configure the client-only FPS overlay and open its visual position editor. |
| **Show FPS HUD** | Whether to display the FPS HUD. |
| **FPS HUD** | Client-side FPS HUD settings. |
| **Horizontal Offset** | Horizontal position offset. Positive values move left; negative values move right. |
| **Vertical Offset** | Vertical position offset. Positive values move up; negative values move down. |
| **Open Visual Position Editor** | Drag the HUD to position it and use the mouse wheel to adjust its scale. |
| **HUD Scale** | HUD scale; use the mouse wheel in the editor to adjust it. |
| **Clear Inventory Before Grant** | Clear the player's inventory before granting the starter kit. |
| **First-Join Commands** | Open the first-join command list. Adding or clicking an existing entry opens a dedicated editor with vanilla client command suggestions after typing /. |
| **Grant Delay (Seconds)** | Seconds before granting rewards after login. 0 grants immediately; precision is 0.05 seconds. |
| **Enable Starter Kit** | Enable first-join rewards for players who have not received them yet. |
| **Starter Equipment** | Format: amountx namespace:item{NBT}; leave blank to give nothing in this slot. |
| **Starter Items List** | Open the visual reward item editor to select items and edit NBT and counts. |
| **Log Deduplication** | Collapse consecutive duplicate ERROR/FATAL logs into one entry with a repeat count. |
| **Log Cleaner** | Only ERROR/FATAL logs are output; duplicate errors are collapsed and old logs are cleaned automatically. |
| **Clean Old Logs on Exit** | Clean old logs and crash reports in the background when the game exits. |
| **Filtered Log Keywords** | Hide matching log lines from both the console and log files. Separate multiple keywords with ASCII commas (,). |
| **Crash Reports to Keep** | Maximum crash reports to retain; the minimum is 1. |
| **Debug Logs to Keep** | Maximum archived debug logs to retain, excluding debug.log; the minimum is 1. |
| **Archived Logs to Keep** | Maximum archived regular logs to retain, excluding latest.log; the minimum is 1. |
| **Enable Gluttony Mode** | Allows players to eat even when the food bar is full. |
| **Persistent Mob Whitelist** | Open the entity selector to choose mobs that should never be removed by this optimization. |
| **Entity Attribute Fixer** | Fixes entities with NaN health (non-numeric values) caused by mod errors, preventing them from becoming unkillable 'Ghost' entities. |
| **Farmland Protection** | Jumping won't trample farmland while wearing boots with Protection. |
| **Fast Cobweb Breaking** | Allow axes and similar tools to break cobwebs quickly, like swords or shears. |
| **Death XP Loss (%)** | Percentage of XP lost on death even with KeepInventory enabled (0 to disable). |
| **Enable 'Let Me Despawn'** | Allows mobs that picked up items to despawn normally. This prevents server lag caused by mob accumulation. Mobs will drop picked items upon despawning. |
| **Remove Recipe Book** | Completely removes recipe book and advancements to optimize performance. |
| **Enhanced PVP Protection** | When enabled, players with PVP protection (and their pets/minions) cannot hurt others or be hurt by other players. It also prevents pets from targeting protected parties and automatically clears aggro. |
| **World Recycle Bin** | When enabled, deleting a world will move it to the system recycle bin instead of permanently deleting it. |
| **Void Damage Percentage** | Set the percentage of max health deducted per void damage tick.<br>Range: 0 - 100<br>Will deal a minimum of 4 damage. |
| **Vanilla Void Damage Whitelist** | Entities in this list will keep the vanilla void damage rate.<br>Supported formats:<br>@modid (Exclude entire mod)<br>#namespace:tag (Exclude specific tag)<br>namespace:entity_id (Exclude specific entity) |
| **Creative Void Immunity** | Prevents death from falling into the void or /kill in Creative mode. |
| **Compact Status Effects** | Configure the compact status-effect display on this client. |
| **Hold Tab to Expand Effects** | Require holding TAB to show expanded effects. |
| **Display Effects on the Left** | Display effects on the left side of the inventory. |
| **Use Potion Items as Compact Icons** | Use potion items instead of effect textures in compact mode. |
| **Compact Status Effects** | Client display settings for compact status effects. |
| **RangedAttributeAccessor** | Attribute Range Uncapping: Allows configured min/max limits to be applied to RangedAttribute instances. |
| **BeeMixins** | Server-side Bee Tweaks: Removes gravity effect, fixes pathfinding float drift, fixes spawn position offset (MC-206401), prevents bees from destroying turtle eggs, and scales physical hitbox and eye height to 25%. |
| **BeeRendererMixin** | Client-side Bee Renderer Tweaks: Forces flip degrees to 180.0F for bees, and reduced the volume to 25% of the original. |
| **ClientInterfaceMixins** | Client interface and workflow automation enhancements:<br># 1. Narrator Removal: Prevents native narrator library loading and disables all TTS features/hotkeys to avoid accidental triggers and potential lag.<br># 2. UI Cleanup: Removes Forge mod compatibility icons from the multiplayer screen for a cleaner visual experience.<br># 3. Smart Toast Interception: Replaces intrusive 'Unsecure Server' system toasts with a non-intrusive gold-colored chat message alert.<br># 4. Seamless World Loading: Automatically skips 'Experimental Settings' and 'Datapack' confirmation screens; forces world lifecycle to 'stable' to suppress warnings.<br># 5. UI Redirect Prevention: Blocks the automated transition to the 'Create New World' screen triggered by version mismatch, missing saves, or datapack errors, allowing players to remain on the current menu for manual adjustment. |
| **AbstractContainerScreenAccessor** | Copy-item container accessor: lets Alt+C / Alt+F accurately read the hovered slot in vanilla and modded container screens. |
| **PlayerCrawlPoseMixin** | Allows players to actively trigger crawling state. |
| **ServerLevelMixin** | Disables damage indicator particles to improve combat performance. |
| **DefaultOptionsMixins** | Default options loader: applies config/kineticcore/defaultoptions.txt when options.txt is first created or damaged, including custom default key bindings. |
| **MobDespawnMixins** | Mob Despawning Optimizations (Let Me Despawn):<br># 1. Entity Backlog Prevention: Fixes the vanilla issue where mobs (Zombies, Skeletons, etc.) become persistent after picking up items, reducing long-term server lag.<br># 2. Endermen Tweak: Allows Endermen holding blocks to despawn naturally like other mobs, preventing entity buildup.<br># 3. Item Recovery: When a mob despawns via this logic, any picked-up equipment is dropped back onto the ground, preventing gear loss.<br># 4. Smart Persistence: Only intercepts automatic persistence from item pickups; mobs named with Name Tags or spawned manually remain persistent. |
| **MiniEffectsMixins** | Status Effect HUD Overhaul (Mini Effects):<br># 1. Smart Layout: Breaks vanilla squeeze limits. Cards now use up to 90% of vertical space, distributed evenly.<br># 2. Hover-to-Top: Hovering over any card instantly brings it to the top layer, breaking through vanilla rendering occlusion.<br># 3. Compatibility: Fully dodges JEI areas and fixes out-of-bounds left-side rendering.<br># 4. Night Vision Anti-Flicker: Overrides GameRenderer night-vision brightness calculation and removes the vanilla sine flicker near effect expiration. |
| **FlightClientMixins** | Client Movement & Physics:<br># 1. Inertia Suppression: Instantly stops movement when input is released for precise building.<br># 2. Noclip Physics: Overrides local player physics for smooth client-side noclip.<br># 3. Dynamic Speed: Adjust creative flight speed (0.1x-100x) with Alt+Shift+Scroll.<br># 4. Anti-Jitter: Fixes the visual falling glitch during gamemode swaps. |
| **FlightServerMixins** | Server Flight Core:<br># 1. Flight Guardian: Prevents accidental flight loss from mod conflicts or network jitter.<br># 2. State Inheritance: Retains flight status across dimensions, respawn, or gamemode swaps.<br># 3. Check Interception: Bypasses server-side movement 'rubber-banding' during No-Clip.<br># 4. Speed Limit Removal: Disables vanilla server-side walking/elytra/vehicle speed checks.<br># 5. Dimensions Hack: Modifies hitboxes and eye-height during No-Clip. |
| **RenderTargetMixin** | GPU Memory Leak Fix:<br># 1. Intercepts the garbage collection of RenderTargets.<br># 2. Catches OpenGL texture and framebuffer IDs that were abandoned without being properly deleted.<br># 3. Queues them for safe deletion on the main thread, preventing VRAM leaks over long play sessions. |
| **NetworkLimitMixins** | Network Packet & Protocol Uncapping:<br># 1. Break Hardcoded Limits: Completely overrides vanilla Netty restrictions on NBT, Strings, Chunk data, and Payload packets.<br># 2. Heavy Modpack Support: Resolves "Payload may not be larger than..." or "VarInt too big" disconnect errors caused by network overflow.<br># 3. Dynamic Scaling: Works with the global network limit config to provide secure and highly elastic network throughput. |
| **RecipeBookClientMixins** | Recipe Book Client Removal: Prevents client recipe collection setup and removes the vanilla recipe-book button. |
| **RecipeBookServerMixins** | Recipe Book Server Removal: Stops recipe-book save/load/sync/award operations and filters recipes/ advancements. |
| **ServerMixin** | TPS/MSPT Sampling: Records server tick times for /kt tps reports and the TPS HUD. |
| **WorldManagementMixins** | World Management: Includes recycle bin and navigation logic. |
| **Chunk Packet Size** | Max byte limit for reading chunk packet data.<br>Vanilla default: 2097152 (2MB)<br>Range: 2097152 ~ 2147483647 |
| **Decoder Max Size** | Maximum processing limit for the network packet decoder (decompression).<br>Vanilla default: 8388608 (8MB)<br>Range: 8388608 ~ 2147483647 |
| **NBT Max Size** | Max allowed bytes for reading NBT data structure trees.<br>Vanilla default: 2097152 (2MB)<br>Range: 2097152 ~ 4194304 (4MB) |
| **Max Packet Size** | Max payload limit for Custom Payload/Query packets.<br>Vanilla default: 1048576 (1MB)<br>Range: 1048576 ~ 33554432 (32MB) |
| **String Max Size** | Max character length limit for strings transmitted over the network.<br>Vanilla default: 32767<br>Range: 32767 ~ 2147483647 |
| **Connection Timeout** | Network connection timeout (in seconds).<br>Vanilla default: 30<br>Range: 30 ~ 99999 |
| **VarInt Byte Limit** | Maximum byte length of a VarInt variable.<br>Vanilla default: 5<br>Range: 5 ~ 10 |
| **VarInt21 Decoder Limit** | Max length limit for the 21-bit VarInt Frame Decoder.<br>Vanilla default: 3<br>Range: 3 ~ 16 |
| **VarLong Byte Limit** | Maximum byte length of a VarLong variable.<br>Vanilla default: 10<br>Range: 10 ~ 20 |
| **Overlay X Position** | The left X coordinate of the information overlay. |
| **Overlay Y Position** | The top Y coordinate of the information overlay. |
| **Startup Information** | Configure the startup-time and account information shown on this client. |
| **Show Login Information** | Whether to show the current login account information. |
| **Show Startup Time** | Whether to show the game startup time. |
| **TPS/MSPT HUD** | Configure the client TPS/MSPT overlay and whether this connection subscribes to server samples. |
| **Show TPS/MSPT HUD** | Whether to show the TPS/MSPT HUD. Disabling it also stops server samples. |
| **TPS/MSPT HUD** | Client-side TPS/MSPT HUD settings. |
| **Init Commands List** | Each list entry or each line is executed as one independent command. A failed command will not stop later commands. Online admins will be notified. Do not add /. If added, it will be removed automatically. Lines starting with # are skipped. |
| **Enable Init Logic** | Whether to execute initialization logic on first world load. |
| **Data Pack Priority** | Priority rule: Entries higher in the list have higher priority; when content conflicts, the upper data pack wins.<br>Controls: Hold Ctrl + left mouse to pick up and drag a whole row. The row follows the pointer while the other rows make room in real time; release to drop it. ↑ / ↓ also work. |
| **Resource Pack Priority** | Override rule: Entries higher in the list have higher priority; when content conflicts, a resource pack above overrides the packs below it.<br>Controls: Hold Ctrl + left mouse to pick up and drag a whole row like an icon. Other rows make room in real time; release to drop it. ↑ / ↓ also work. |
| **Biome Search Step** | Sampling step for biome search (Suggested: 32-64).<br>Smaller values are more accurate but slower, larger values are faster. |
| **Custom Spawn** | Configure base parameters for new-world spawn searching. Use the dedicated rule editor below for dimension, biome, and structure lists instead of editing the config file manually. |
| **Enable Custom Spawn** | Enable custom world-spawn searching. This does not override personal spawn points set by beds or similar mechanics. |
| **Max Search Radius** | Maximum biome-mode search radius in blocks. Must be a non-negative integer; larger ranges increase worst-case search time. |
| **Spawn Rules** | Rule priority is dimension, then biome, then structure. Complex lists use a dedicated selector and are validated against server registries. |
| **Open Spawn Rule Editor** | Choose allowed dimensions, biomes, and structures and control each rule toggle. minecraft:overworld cannot be stored in the dimension restriction list; the server rejects invalid or missing IDs. |
| **Structure Search Radius (Chunks)** | Maximum structure search radius in chunks; 1 chunk = 16 blocks. Very large values (such as > 512) may severely stall world creation; End outer-island structures generally require more than 64. |
| **Structure Search Timeout Seconds** | When creating a new world, if spawn structure search takes longer than this value, it falls back to biome/dimension logic. If that still fails, vanilla default spawn is used. Recommended: 8-20 seconds. |
| **Attribute Editor** | Open the full attribute list to edit enabled state, minimum, and maximum values. |
| **Throwable Spawn Eggs** | Throw spawn eggs like snowballs and spawn the matching entity at a safe impact position. Hitting a spawner changes its entity type directly. |
| **Enable Spawn Egg Throwing** | Allows right-clicking to throw spawn eggs as projectiles. Use Alt+O to switch between throw mode and vanilla mode. |
| **Throw Velocity** | Controls spawn egg projectile speed. Default: 1.5. |
| **Throw Inaccuracy** | Controls projectile spread. Lower values are more accurate. Default: 0.2. |
| **Starting Equipment Editor** | Open item editor with item selector and NBT support |

### GUI and Editors
| Item | Description |
|---|---|
| **Reset** | Restore this option's default value. |
| **search** | Search... (@mod #tag) |
| **command edit** | Type / to use the client command tree. Tab, arrow keys, and mouse selection are supported. |
| **Starting Equipment Editor** | Left-click to select an item, right-click to open the NBT editor, middle-click to clear the slot |
| **Reward Item Editor** | Left-click the icon to select an item; right-click the icon to open the NBT editor. |
| **Cancel** | Discard the current color changes. |
| **Apply** | Apply the current color or palette. |
| **HEX** | Enter a 6-digit RGB hex value, for example FF00FF. |
| **rgb** | Enter an RGB value from 0 to 255. |
| **Copy HEX** | Copy the current color as #RRGGBB. |
| **Add to Palette** | Add the exact current color to the palette. |
| **RGB Picker** | Hold left click and drag to choose saturation and brightness. |
| **hue** | Hold left click and drag to choose hue. |
| **Current Color** | Current color: #%s |
| **swatch** | #%s Left-click to load; right-click for color actions. |
| **color picker** | Click to open the advanced RGB color picker. |

### Commands
| Item | Description |
|---|---|
| **pvp** | Toggle PVP Protection |
| **reload** | Reload mod config files |
| **setfirstjoin** | Save current inventory and equipment as first join rewards |
| **tps** | View server TPS and MSPT status |
| **world** | World & Structure command help |
| **list structures** | List all structure IDs |
| **structure** | Query structures at current location |
| **nbt** | NBT Editor |
| **hand** | Open the NBT editor for the item in your main hand. |
| **entity** | Open the NBT editor for the entity or block entity under your crosshair. |

### Editable Options
- Registered Attributes
- Global Attribute Settings
- Bee Fixes & Tweaks
- Client UI & Interaction
- Flight & Movement Control
- Mod Compatibility
- Network Protocol
- Optimization & Entity Tweaks
- Performance & Rendering Fixes
- Performance Monitoring
- Player & Entity Logic
- Vanilla System Tweaks
- World Management
- Stored only in this client's configuration.
- Client Settings
- Client
- Stored in this Minecraft installation; it does not modify a connected remote server.
- Local installation
- Server Settings
- Server
- Blocks
- Combat
- Food & Drinks
- Ingredients
- Mods
- Redstone
- Spawn Eggs
- Tools
- Apply
- %s
- All
- Inventory

### Config Defaults
| Key | Default |
|---|---|
| `anchor_x` | `2` |
| `anchor_y` | `2` |
| `creative.enableVoidImmunity` | `true` |
| `death.keep_inventory_drop_xp_percentage` | `50` |
| `enabled` | `true` |
| `first_join.clear_inventory` | `true` |
| `first_join.delay_ticks` | `20` |
| `first_join.enable` | `true` |
| `log_cleaner.deduplication` | `true` |
| `log_cleaner.enable` | `true` |
| `log_cleaner.filtered_keywords` | `"Tried to load a block entity for block"` |
| `log_cleaner.max_crash_reports` | `3` |
| `log_cleaner.max_debug_logs` | `3` |
| `log_cleaner.max_logs` | `3` |
| `mechanics.enableAlwaysEdible` | `true` |
| `mechanics.enableEntityAttributeFixer` | `true` |
| `mechanics.enableFarmlandProtection` | `true` |
| `mechanics.enablePvpProtection` | `true` |
| `mechanics.fastCobWebBreaking` | `true` |
| `mechanics.recycleBinWorlds` | `true` |
| `mobs.enableLetMeDespawn` | `true` |
| `offsetX` | `0` |
| `offsetY` | `0` |
| `recipe_book.removeRecipeBook` | `true` |
| `scale` | `1.0D` |
| `setspawn.biome_step` | `48` |
| `setspawn.enable` | `true` |
| `setspawn.radius` | `10000` |
| `setspawn.rule_biome.enable` | `false` |
| `setspawn.rule_dimension.enable` | `false` |
| `setspawn.rule_structure.enable` | `true` |
| `setspawn.structure_radius` | `256` |
| `setspawn.structure_timeout_seconds` | `12` |
| `show_login_info` | `true` |
| `show_startup_time` | `true` |
| `void_damage.percentage` | `10` |
| `world_init.enable` | `true` |

### Data Paths
Primary configuration/data paths:

- `config/kineticcore/attributes.toml`
- `config/kineticcore/defaultoptions.txt`
- `config/kineticcore/general.toml`
- `config/kineticcore/network.toml`
- `config/kineticcore/player.toml`
- `config/kineticcore/setspawn.toml`
- `config/kineticcore/spawnegg.toml`
- `config/kineticcore/world_init.toml`

### Dependencies
| Mod ID | Relationship |
|---|---|
| `curios` | Optional |
| `jei` | Optional |

## 简体中文

**项目定位：** KineticCore 是 Kinetic 系列共用的基础 API 与底层设施，负责向其他项目提供配置、网络同步、通用界面、编辑器和模块注册能力。实际玩法内容由各个独立的 Kinetic 附属项目提供。

这些附属不能简单合并进 KineticCore 或彼此合并。每个附属都有独立的功能范围、依赖、配置、更新周期和适用玩家；保持独立可以让玩家按需安装，也能避免无关玩法和依赖被强制捆绑。

### 模组定位

**KineticCore** 是 Kinetic 系列的核心基础模组。它负责公共 API、模块注册、统一配置中心、网络通信、压缩传输、通用 GUI 工具与命令扩展，同时保留一批适合直接放在核心中的基础机制与修复功能。

其他 Kinetic 附属模块会依赖 KineticCore 提供的基础设施，因此建议核心与附属模块使用匹配版本。

### 主要功能

- **F6 统一配置中心**：集中展示已安装的 Kinetic 模块，支持模块级配置页、专用编辑器入口与搜索。
- **服务端权威配置 API**：凡影响服务端规则的配置统一由服务端读取、校验、保存与同步；单人游戏也通过集成服务器走同一套保存路线。
- **网络与压缩工具**：为大型 JSON、列表、NBT、规则快照等提供统一的网络编解码与压缩能力，供附属模块复用。
- **公共 GUI / 选择器工具**：提供虚拟画布、配置页面、物品选择器、实体选择器、HUD 编辑器和高级 RGB 调色板 API 等通用客户端组件。
- **命令扩展框架**：其他模块可以把自己的子命令挂载到统一的 `/kt` 命令树。
- **飞行与穿墙控制**：提供飞行速度、惯性控制、穿墙状态与服务端同步等能力。
- **主动爬行**：允许玩家通过按键主动进入 1 格高爬行状态。
- **TPS / MSPT / FPS HUD**：提供服务器 TPS/MSPT 采样与客户端 HUD，以及可视化位置和缩放编辑。
- **首次加入系统**：支持首次加入奖励物品、初始装备与首次加入命令。
- **世界初始化**：可在新世界初始化阶段执行指定规则或命令。
- **SetSpawn 出生地系统**：支持出生点接管、结构相关出生规则与可视化配置。
- **属性上下限扩展**：允许针对属性单独调整最小值与最大值，兼容超出原版范围的属性系统。
- **通用机制调整**：包含暴食、农田保护、斧头快速破坏蜘蛛网、创造模式保护、PVP 保护等基础规则。
- **NBT 与物品数据工具**：提供手持物品、实体 NBT 查看以及面向整合包作者的物品数据复制工具。
- **资源包 / 数据包顺序管理**：提供相关加载顺序配置与重载辅助。
- **Mini Effects**：重构状态效果 HUD，并包含夜视闪烁修正等客户端体验优化。
- **日志清理与启动信息**：提供日志保留策略、启动耗时与登录信息显示。
- **稳定性修复**：包含异常实体属性修复、部分 GPU/渲染资源清理、世界删除保护、网络限制调整等底层修复。

### 常用入口

- `F6`：打开 Kinetic 统一配置中心。
- `/kt reload`：重载支持热重载的 Kinetic 配置与模块数据。
- `/kt tps`：查看或控制 TPS/MSPT 监控。
- `/kt nbt hand`：查看手持物品 NBT。
- `/kt nbt entity`：查看目标实体 NBT。
- `/kt setfirstjoin`：首次加入相关管理入口。
- `/kt world ...`：出生地与结构相关管理命令。

### 配置目录

核心配置主要位于：

```text
config/kineticcore/
```

常见文件包括：

- `general.toml`：通用机制。
- `attributes.toml`：属性上下限。
- `network.toml`：网络相关限制。
- `player.toml`：首次加入配置。
- `setspawn.toml`：出生地系统。
- `world_init.toml`：世界初始化。
- `tps_client.toml` / `fps_client.toml`：客户端 HUD。
- `spawnegg.toml`：可投掷刷怪蛋。
- `startup.toml`：启动与登录显示。

### 运行环境

- Java 17
- Curios：可选兼容
- JEI：可选兼容

## 完整功能参考

### 配置项详细说明

| 项目 | 说明 |
|---|---|
| **自动扫描注册属性** | 自动加入新属性、清理失效条目并刷新翻译注释。 |
| **属性范围限制** | 编辑所有已注册范围属性的启用状态、最小值和最大值。 |
| **%s — 启用** | 启用 %s 的自定义范围。 |
| **infinity** | 此边界为极大值、极小值或无限值；支持科学计数法、Infinity 与 -Infinity。 |
| **%s — 最大值** | %s 允许的最大值。 |
| **%s — 最小值** | %s 允许的最小值。 |
| **客户端默认选项** | 将当前 Minecraft 客户端选项导出为 KineticCore 在新建选项文件时使用的默认值。 |
| **将当前选项保存为默认值** | 用当前 options.txt 覆盖 config/kineticcore/defaultoptions.txt。 |
| **FPS HUD** | 配置仅作用于此客户端的 FPS HUD，并可打开可视化位置编辑器。 |
| **显示 FPS HUD** | 是否显示 FPS HUD。 |
| **FPS HUD** | FPS HUD 客户端显示设置。 |
| **水平偏移** | 横向位置偏移，正数向左移动，负数向右移动。 |
| **垂直偏移** | 纵向位置偏移，正数向上移动，负数向下移动。 |
| **打开可视化位置编辑器** | 拖动 HUD 调整位置，并将鼠标停在 HUD 上滚动滚轮调整缩放。 |
| **HUD 缩放** | HUD 缩放比例；编辑界面可用鼠标滚轮调整。 |
| **发放前清空背包** | 发放新手奖励前先清空玩家背包。 |
| **首次加入指令** | 打开首次加入指令列表。新增或点击已有条目会进入独立编辑页，并支持输入 / 后使用客户端原版命令补全。 |
| **奖励发放延迟（秒）** | 玩家进入服务器后延迟多少秒发放礼包；0 表示立即发放，最小时间精度为 0.05 秒。 |
| **启用新手奖励** | 为尚未领取过奖励的玩家启用首次加入奖励。 |
| **初始装备** | 格式：数量x namespace:item{NBT}；留空表示该槽位不发放物品。 |
| **奖励物品列表** | 打开可视化奖励物品编辑器，可直接选择物品并编辑 NBT 与数量。 |
| **日志过滤去重** | 合并连续重复的 ERROR/FATAL 错误日志，只保留一条并显示重复次数。 |
| **日志清理** | 仅输出 ERROR/FATAL 错误日志，并保留重复错误合并与旧日志自动清理。 |
| **退出时清理旧日志** | 游戏退出时在后台清理旧日志和崩溃报告。 |
| **日志过滤关键词** | 同时屏蔽控制台和日志文件中包含关键词的行；多个关键词请使用英文逗号（,）分隔。 |
| **保留崩溃报告数量** | 最多保留的崩溃报告数量，最小值为 1。 |
| **保留调试日志数量** | 最多保留的旧调试日志数量，不包括 debug.log；最小值为 1。 |
| **保留归档日志数量** | 最多保留的旧普通日志数量，不包括 latest.log；最小值为 1。 |
| **开启暴食模式** | 允许玩家在满腹度时继续进食。 |
| **永久保留生物白名单** | 打开实体选择器配置永久保留的生物；已加入名单的生物不会被该优化刷掉。 |
| **实体属性修复** | 修复因模组运算错误导致的 NaN（非数字）血量实体，防止其变成无法杀死的“幽灵”实体。 |
| **耕地保护** | 穿着带“保护”附魔的靴子时，跳跃不会踩坏耕地。 |
| **快速破网** | 允许斧头等工具像剑或剪刀一样快速破坏蜘蛛网。 |
| **死亡经验扣除(%)** | 开启死亡不掉落时，死亡仍扣除的经验百分比（0为不扣）。 |
| **生物消失优化** | 允许捡起物品的怪物正常被刷掉，防止由于地上的垃圾导致怪物堆积引起服务器卡顿。怪物消失时会掉落它们捡起的物品。 |
| **移除配方书** | 彻底移除配方书功能和相关进度，显著优化性能。 |
| **增强型 PVP 保护** | 开启后，开启 PVP 保护的玩家及其宠物/仆从无法伤害他人或被他人伤害。同时防止仆从相互锁定为攻击目标，并自动清除已存在的仇恨。 |
| **存档回收站保护** | 开启后，在主界面删除世界存档时，文件夹将被移至操作系统回收站。这为你提供了找回误删存档的机会。 |
| **虚空伤害扣血比例** | 设置实体在虚空中每次受伤扣除最大生命值的百分比。<br>范围：0 - 100<br>保底会受到 4 点伤害。 |
| **虚空伤害原版白名单** | 填入此列表的生物在虚空将保持原版的掉血速度。<br>支持格式：<br>@模组id (排除整个模组)<br>#命名空间:标签名 (排除对应标签)<br>命名空间:实体id (排除特定实体) |
| **创造虚空免疫** | 防止创造模式玩家因掉入虚空或 /kill 指令死亡。 |
| **紧凑状态效果** | 配置此客户端上的紧凑状态效果显示。 |
| **按住 Tab 展开状态效果** | 仅在按住 TAB 时展开状态效果。 |
| **在背包左侧显示状态效果** | 在物品栏左侧显示状态效果。 |
| **紧凑模式使用药水物品图标** | 紧凑模式使用药水物品图标，而不是效果纹理。 |
| **紧凑状态效果** | 紧凑状态效果的客户端显示设置。 |
| **RangedAttributeAccessor** | 属性范围解限：允许配置并修改 RangedAttribute 的最小值与最大值。 |
| **BeeMixins** | 服务端蜜蜂修复与微型化：移除重力影响、修复寻路浮点数漂移导致的偏移、修复生成位置偏移(MC-206401)、防止蜜蜂破坏海龟蛋，并将物理碰撞箱与视线高度缩小至 25%。 |
| **BeeRendererMixin** | 客户端蜜蜂渲染修复与微型化：为蜜蜂强制设定翻转角度（180度），并将体积缩小至原来的 25%。 |
| **ClientInterfaceMixins** | 客户端界面与流程自动化增强：<br># 1. 彻底禁用复述器：阻止 Narrator 底层库加载，禁用其所有功能及快捷键，避免误触和潜在的库加载卡顿。<br># 2. 多人列表清理：移除多人服务器列表中 Forge 模组版本的详细信息图标，使界面更整洁。<br># 3. 智能弹窗拦截：将进入非正版/离线服务器时的全屏系统弹窗拦截，改为非侵入式的聊天栏金色文字提醒。<br># 4. 自动化存档加载：创建世界或加载实验性存档时，自动跳过“实验性设置”和“数据包确认”二次弹窗，并强制标记生命周期为稳定，防止警告刷屏。<br># 5. 阻止 UI 强制跳转：拦截因版本不兼容、数据包损坏或存档缺失而导致的自动跳转至“创建新世界”界面的行为，允许玩家保留在当前菜单进行手动调整。 |
| **AbstractContainerScreenAccessor** | 物品复制工具容器访问器：允许 Alt+C / Alt+F 在原版及模组容器界面准确读取鼠标悬浮槽位。 |
| **PlayerCrawlPoseMixin** | 允许玩家主动进入爬行姿态。 |
| **ServerLevelMixin** | 禁用伤害指示器粒子生成，优化战斗性能。 |
| **DefaultOptionsMixins** | 默认选项加载：在 options.txt 首次创建或异常损坏时应用 config/kineticcore/defaultoptions.txt，并同步自定义默认按键。 |
| **MobDespawnMixins** | 生物消失逻辑优化（Let Me Despawn）：<br># 1. 防止实体积压：解决原版中僵尸、骷髅等生物捡起地上的垃圾物品后会永久占据实体位而不消失的问题，有效缓解服务器长期运行后的卡顿。<br># 2. 末影人优化：允许手持方块的末影人像普通生物一样自然消失，防止末地或主世界地表堆积大量末影人。<br># 3. 物品掉落：当受此逻辑影响的生物消失（Despawn）时，它捡起的物品会重新掉落在地上，不会导致玩家掉落的装备永久丢失。<br># 4. 智能识别：仅针对捡起物品产生的“强制持久化”进行拦截，玩家通过命名牌命名或手动生成的生物依然会保留。 |
| **MiniEffectsMixins** | 状态效果 HUD 重构 (Mini Effects)：<br># 1. 智能布局：突破原版挤压限制，效果卡片会利用屏幕 90% 的垂直空间自动等距排开，彻底告别重叠盲区。<br># 2. 悬停置顶：鼠标悬停任意卡片时，该卡片立刻突破图层遮挡，置于最顶层清晰显示。<br># 3. 兼容防冲突：完全避开 JEI 区域；完美接管并修复部分模组左侧渲染越界问题。<br># 4. 夜视防闪烁：接管 GameRenderer 的夜视亮度计算，移除原版夜视快结束时的正弦闪烁，避免屏幕忽明忽暗。 |
| **FlightClientMixins** | 客户端移动与物理：<br># 1. 惯性抑制：当停止按键时，强制清除飞行惯性，实现瞬间起停，大幅提升建筑操作精准度。<br># 2. 穿墙物理：接管 LocalPlayer 物理循环，实现客户端平滑穿墙。<br># 3. 动态调速：支持创造模式下通过 Alt+Shift+滚轮 动态调节飞行速度（0.1x-100x）。<br># 4. 视觉防抖：修正模式切换瞬间玩家会向下坠落一下的视觉抖动问题。 |
| **FlightServerMixins** | 服务端飞行核心：<br># 1. 飞行守门员：防止第三方模组或网络波动意外关闭玩家飞行姿态，解决掉落问题。<br># 2. 跨维度恢复：确保玩家在跨维度、死亡重生或切换游戏模式后仍能继承飞行状态。<br># 3. 拦截校验：拦截服务端对创造模式穿墙(No-Clip)的非法移动拉回。<br># 4. 速度限制移除：彻底禁用原版服务端对行走、鞘翅飞行、载具移动的 256/300 码速度限制校验。<br># 5. 实体尺寸干涉：允许在启用穿墙时动态修改玩家碰撞箱和眼高。 |
| **RenderTargetMixin** | GPU 内存泄漏修复 (VRAM Leak Fix)：<br># 1. 拦截 RenderTarget (帧缓冲区对象) 的垃圾回收过程。<br># 2. 当底层 OpenGL 纹理或缓冲区未被正常释放即被 Java 销毁时，接管其句柄。<br># 3. 将遗留的 OpenGL ID 放入主线程队列进行安全销毁，有效防止长时间游戏后的显存溢出。 |
| **NetworkLimitMixins** | 网络数据包与底层协议解限：<br># 1. 突破硬编码限制：彻底接管并解除原版 Netty 底层对 NBT、字符串、区块数据、Payload 通信包的大小限制。<br># 2. 大型模组支持：完美解决 "Payload may not be larger than..." 或 "VarInt too big" 等导致玩家断开连接的网络溢出错误。<br># 3. 动态扩展：配合 config 中的全局网络限制参数，提供安全且极具弹性的网络数据流吐吞能力。 |
| **RecipeBookClientMixins** | 配方书客户端移除：阻止客户端构建配方集合，并移除原版配方书按钮。 |
| **RecipeBookServerMixins** | 配方书服务端移除：停止保存、读取、同步和授予配方，并过滤 recipes/ 进度数据。 |
| **ServerMixin** | TPS/MSPT 采样：记录服务端 Tick 耗时，为 /kt tps 与 TPS HUD 提供统计数据。 |
| **WorldManagementMixins** | 存档管理：包含存档回收站和界面返回逻辑。 |
| **区块包上限** | 区块数据包读取时的最大字节限制。<br>原版默认: 2097152 (2MB)<br>范围: 2097152 ~ 2147483647 |
| **解码器上限** | 网络数据包解码器（解压）的最大处理限制。<br>原版默认: 8388608 (8MB)<br>范围: 8388608 ~ 2147483647 |
| **NBT最大限制** | 读取 NBT 数据结构树时允许的最大字节数。<br>原版默认值：2097152（2MB）<br>范围：2097152 ~ 4194304（4MB） |
| **单数据包上限** | 自定义 Payload/Query 等单数据包的最大承载限制。<br>原版默认: 1048576 (1MB)<br>范围: 1048576 ~ 33554432（32MB） |
| **字符串上限** | 网络通讯中可传输的最大字符串长度限制。<br>原版默认: 32767<br>范围: 32767 ~ 2147483647 |
| **连接超时时间** | 网络连接超时时间（秒）。<br>原版默认值：30<br>范围：30 ~ 99999 |
| **VarInt 字节上限** | VarInt 类型变量的最大字节数。<br>过大可能导致底层安全检查失效。<br>原版默认: 5<br>范围: 5 ~ 10 |
| **21位解码器上限** | 21 位 VarInt 帧解码器允许的最大长度。<br>原版默认值：3<br>范围：3 ~ 16 |
| **VarLong 字节上限** | VarLong 类型变量的最大字节数。<br>过大可能导致反序列化崩溃。<br>原版默认: 10<br>范围: 10 ~ 20 |
| **信息层 X 坐标** | 整体信息显示的左侧 X 坐标。 |
| **信息层 Y 坐标** | 整体信息显示的顶部 Y 坐标。 |
| **启动信息** | 配置此客户端显示的启动耗时与登录账号信息。 |
| **显示登录信息** | 是否显示当前登录账号信息。 |
| **显示启动耗时** | 是否显示游戏启动耗时。 |
| **TPS/MSPT HUD** | 配置客户端 TPS/MSPT HUD，以及当前连接是否订阅服务端采样数据。 |
| **显示 TPS/MSPT HUD** | 是否显示 TPS/MSPT HUD；关闭时服务器会停止发送采样数据。 |
| **TPS/MSPT HUD** | TPS/MSPT HUD 客户端显示设置。 |
| **初始化指令列表** | 每个列表项或每一行都会被当作一条独立指令执行。某条失败不会阻止后续指令。失败后会提醒在线管理员。不要加 /，误加会自动去掉。以 # 开头的行会被跳过。 |
| **启用初始化逻辑** | 是否在首次加载世界时执行初始化逻辑。 |
| **数据包优先级** | 优先级规则：列表越靠上优先级越高；内容冲突时，以上方数据包为准。<br>操作：按住 Ctrl + 左键抓住整行拖动，条目会跟随鼠标悬浮移动，其他条目实时让位；松开后落位。也可使用 ↑ / ↓。 |
| **资源包优先级** | 覆盖规则：列表越靠上优先级越高；内容冲突时，上面的资源包会覆盖下面的资源包。<br>操作：按住 Ctrl + 左键抓住整行拖动，条目会像图标一样跟随鼠标移动，其他条目实时让位；松开后落位。也可使用 ↑ / ↓。 |
| **群系搜索采样步长** | 群系搜索时的采样步长 (建议: 32-64)。<br>值越小搜索越精确(能找到细小群系)但越耗时，值越大搜索越快。 |
| **自定义出生点** | 设置新世界出生点搜索的基础参数。维度、群系和结构列表请使用下方专用规则编辑器，不需要手动修改配置文件。 |
| **启用自定义出生点** | 启用自定义出生点搜索。不会覆盖玩家通过床等方式设置的个人出生点。 |
| **最大搜索半径** | 群系模式的最大搜索半径，单位为方块。必须为非负整数；范围越大，最坏情况下搜索耗时越高。 |
| **出生规则** | 规则优先级依次为维度、群系、结构。复杂列表由专用选择器编辑并由服务器按实际注册表校验。 |
| **打开出生规则编辑器** | 选择允许的维度、群系和结构，并控制每类规则开关。minecraft:overworld 不允许写入维度限制；服务器会拒绝无效或已不存在的 ID。 |
| **结构搜索半径 (区块)** | 结构模式最大搜索半径，单位为区块，1 区块 = 16 方块。数值过大（如 > 512）可能造成建图严重卡顿；末地外岛结构通常需要大于 64。 |
| **结构搜索超时秒数** | 创建新世界时，如果出生点结构搜索超过这个秒数还没有找到结果，会自动回退到群系/维度逻辑。如果仍然找不到，就交回原版默认出生。建议 8-20 秒，不要设置太大。 |
| **属性编辑** | 打开完整属性列表，编辑每个属性的启用状态、最小值和最大值。 |
| **刷怪蛋投掷** | 将刷怪蛋像雪球一样投掷出去，命中后在安全位置生成对应实体。命中刷怪笼时会直接修改刷怪笼的实体类型。 |
| **启用刷怪蛋投掷** | 允许右键将刷怪蛋作为投射物扔出。可使用 Alt+O 在投掷模式与原版模式之间切换。 |
| **投掷飞行速度** | 控制刷怪蛋投射物的飞行速度。默认值：1.5。 |
| **投掷散布** | 控制刷怪蛋投射物的散布程度。数值越低越精准，默认值：0.2。 |
| **初始装备编辑器** | 打开物品格子编辑器，支持物品选择和NBT |

### 界面操作与编辑器说明

| 项目 | 说明 |
|---|---|
| **重置** | 恢复此选项的默认值。 |
| **search** | 搜索... (@模组 #标签) |
| **command edit** | 输入 / 后使用客户端指令树补全，支持 Tab、上下方向键和鼠标选择。 |
| **初始装备编辑** | 左键选择物品，右键打开 NBT 编辑器，中键清空槽位 |
| **奖励物品编辑** | 左键图标选择物品，右键图标打开 NBT 编辑器。 |
| **取消** | 放弃本次颜色修改。 |
| **应用** | 应用当前颜色或调色板。 |
| **HEX** | 输入 6 位十六进制 RGB，例如 FF00FF。 |
| **rgb** | 输入 0 到 255 的 RGB 数值。 |
| **复制 HEX** | 复制当前颜色的 #RRGGBB。 |
| **加入调色板** | 把当前精确颜色加入调色板。 |
| **RGB 色盘** | 按住左键拖动，选择饱和度与亮度。 |
| **hue** | 按住左键拖动，选择色相。 |
| **当前颜色** | 当前颜色：#%s |
| **swatch** | #%s 左键载入，右键打开颜色菜单。 |
| **color picker** | 点击打开高级 RGB 调色板。 |

### 命令功能说明

| 项目 | 说明 |
|---|---|
| **pvp** | 切换PVP保护模式 |
| **reload** | 管理员重载模组配置文件 |
| **setfirstjoin** | 将当前背包和装备保存为首次进服奖励 |
| **tps** | 查看服务器 TPS 与 MSPT 状态 |
| **world** | 世界与结构定位指令帮助 |
| **list structures** | 列出所有结构ID |
| **structure** | 查询所在位置的结构 |
| **nbt** | NBT 编辑 |
| **hand** | 打开主手物品的 NBT 编辑器。 |
| **entity** | 打开准星指向的实体或方块实体 NBT 编辑器。 |

### 可编辑字段、模式与分类索引

- 已注册属性
- 全局属性设置
- 蜜蜂修复与优化
- 客户端界面优化
- 飞行与移动控制
- 模组兼容性
- 网络与协议解限
- 性能与实体优化
- 性能与渲染修复
- 性能监控
- 玩家与实体逻辑
- 原版系统优化
- 存档管理
- 仅保存在当前客户端配置中。
- 客户端配置
- 客户端
- 保存在当前 Minecraft 安装中，不会修改所连接的远程服务器。
- 本机安装
- 配置始终由当前游戏服务器保存；单人游戏同样通过集成服务器处理，拥有 OP 2 级或更高权限时可编辑。
- 服务端配置
- 服务器
- 方块
- 战斗
- 食物与饮品
- 材料
- 模组
- 红石
- 刷怪蛋
- 工具
- 应用
- %s
- 全部
- 背包

### 配置键与默认值

| 配置键 | 默认值 |
|---|---|
| `anchor_x` | `2` |
| `anchor_y` | `2` |
| `creative.enableVoidImmunity` | `true` |
| `death.keep_inventory_drop_xp_percentage` | `50` |
| `enabled` | `true` |
| `first_join.clear_inventory` | `true` |
| `first_join.delay_ticks` | `20` |
| `first_join.enable` | `true` |
| `log_cleaner.deduplication` | `true` |
| `log_cleaner.enable` | `true` |
| `log_cleaner.filtered_keywords` | `"Tried to load a block entity for block"` |
| `log_cleaner.max_crash_reports` | `3` |
| `log_cleaner.max_debug_logs` | `3` |
| `log_cleaner.max_logs` | `3` |
| `mechanics.enableAlwaysEdible` | `true` |
| `mechanics.enableEntityAttributeFixer` | `true` |
| `mechanics.enableFarmlandProtection` | `true` |
| `mechanics.enablePvpProtection` | `true` |
| `mechanics.fastCobWebBreaking` | `true` |
| `mechanics.recycleBinWorlds` | `true` |
| `mobs.enableLetMeDespawn` | `true` |
| `offsetX` | `0` |
| `offsetY` | `0` |
| `recipe_book.removeRecipeBook` | `true` |
| `scale` | `1.0D` |
| `setspawn.biome_step` | `48` |
| `setspawn.enable` | `true` |
| `setspawn.radius` | `10000` |
| `setspawn.rule_biome.enable` | `false` |
| `setspawn.rule_dimension.enable` | `false` |
| `setspawn.rule_structure.enable` | `true` |
| `setspawn.structure_radius` | `256` |
| `setspawn.structure_timeout_seconds` | `12` |
| `show_login_info` | `true` |
| `show_startup_time` | `true` |
| `void_damage.percentage` | `10` |
| `world_init.enable` | `true` |

### 配置与数据路径

主要配置/数据路径：

- `config/kineticcore/attributes.toml`
- `config/kineticcore/defaultoptions.txt`
- `config/kineticcore/general.toml`
- `config/kineticcore/network.toml`
- `config/kineticcore/player.toml`
- `config/kineticcore/setspawn.toml`
- `config/kineticcore/spawnegg.toml`
- `config/kineticcore/world_init.toml`

### 依赖与可选兼容

| 模组 ID | 关系 |
|---|---|
| `curios` | 可选 |
| `jei` | 可选 |
