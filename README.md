# KineticCore

[简体中文](#简体中文) | [English](#english)

## 简体中文

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

- Minecraft 1.20.1
- Minecraft Forge 47.x
- Java 17
- Curios：可选兼容
- JEI：可选兼容

## English

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

- Minecraft 1.20.1
- Minecraft Forge 47.x
- Java 17
- Curios: optional integration
- JEI: optional integration


## 开源协议与版权 (License)

Copyright (C) 2024-2026 XYAT.

本项目基于 **GNU Lesser General Public License v3.0 (LGPLv3)** 协议开源。

This project is open-sourced under the **GNU Lesser General Public License v3.0 (LGPLv3)**.
