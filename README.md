# FuckCn2b2t

> 新玩家聊天行为管制、反广告刷屏插件 —— 去你妈的 cn2b2t 广告，准备放飞刷屏狗的马！

## 概述

FuckCn2b2t 是一个 Paper/Folia 服务端插件，专门解决无政府状态服务器（anarchy server）的新玩家广告刷屏问题。插件通过**新玩家判定 → 聊天行为检查 → 违规处置** 的完整链路，在不影响老玩家体验的前提下，自动管制新玩家的发言行为。

## 功能特性

### 新玩家判定（二选一 / 可回退）

- **活跃度积分判定**（推荐）：通过统计玩家各项游戏行为（击杀、挖矿、附魔等）并加权计算积分，达到阈值即解除管制。可防挂机混时长。
- **在线时长判定**（回退模式）：简单以游玩时长判定，达到指定时长即解除管制。

判定规则可配置，支持占位符拓展统计项。

### 新玩家聊天检查

- **超长消息检测**：拦截超过字符限制的消息
- **链接检测**：通过正则匹配 URL，支持净化防绕过（在字符间插入干扰字符），同时检测 `域名:端口` 格式
- **频繁消息检测**：时间窗口内发言超过次数限制则拦截
- **过多数字检测**：消息中数字字符总量超限则拦截（防开盒发手机号/身份证）
- **InteractiveChat 兼容**：自动移除聊天组件标记，避免误判

每项检查均可独立开关。

### 违规处置

- **静默模式**（推荐）：取消消息事件，但向发言者发送一条格式化的"成功"假消息，造成消息仅自己可见的假象，使违规者难以察觉被管制
- **OP 通知**：违规行为可抄送在线 OP
- **积分制禁言**：每次违规累计积分，达到阈值自动禁言，禁言时长随次数递增
- **禁言期间其他拦截**：私聊命令、告示牌、铁砧重命名、书与笔
- **警告/踢出/封禁**：各级处罚的阈值和消息完全可配置

### 绕过权限

`fuckcn2b2t.bypass` — 给予此权限的玩家永久视为老玩家，跳过全部新玩家判定。（调试模式仍会覆盖）

### 双端兼容

- ✅ **Folia** — 使用区域化调度器
- ✅ **Paper** — 使用传统 Bukkit 调度器
- ❌ **Bukkit / Spigot** — 不兼容（使用了 Paper API 的 AsyncChatEvent）

## 命令

| 命令 | 说明 | 权限 | 平替命令 |
|---|---|---|---|
| `/fkcn2b2t mute <玩家> <分钟/off>` | 隐形禁言/解除禁言 | OP | `/shadowban` |
| `/fkcn2b2t unmute <玩家>` | 解除隐形禁言（新增） | OP | — |
| `/fkcn2b2t stat [玩家]` | 查看玩家活跃统计数据 | OP | `/newplayerstat` |
| `/fkcn2b2t reload` | 重载配置文件 | OP | —（已移除 `/shadowreload`） |

所有命令均支持 Tab 补全（玩家名、常用分钟数）。

## 权限

| 权限节点 | 默认值 | 说明 |
|---|---|---|
| `fuckcn2b2t.reload` | op | 允许重载配置 |
| `fuckcn2b2t.bypass` | op | 跳过新玩家判定，永久视为老玩家（调试模式仍会覆盖） |

## 配置文件

插件首次加载时自动生成 `plugins/FuckCn2b2t/config.yml`，包含四个主要章节：

```
一、新玩家聊天检查功能类    → new-player-chat-check
  ├── 超长消息检测
  ├── InteractiveChat 兼容
  ├── 链接检测（含域名+端口）
  ├── 频繁消息检测
  └── 过多数字检测

二、聊天违规处置措施类      → violation-penalties
  ├── 静默模式 / OP通知
  ├── 禁言阈值与时长
  ├── 警告/踢出/封禁消息
  └── 禁言期间拦截项（私聊/告示牌/铁砧/书笔）

三、新玩家判定规则类        → new-player-detection
  ├── 活跃度积分判定（统计项与权重）
  └── 在线时长判定（回退模式）

四、新玩家提醒              → new-player-reminder
  ├── 登录提醒 / 定时提醒
  └── 提醒内容
```

所有阈值、开关、消息文本均可在配置文件中修改，修改后执行 `/fkcn2b2t reload` 即时生效。

## 安装

1. 下载最新版本 JAR
2. 放入 `plugins/` 目录
3. 重启服务器或使用 `/reload` 加载
4. 如需 PlaceholderAPI 支持，请先安装 [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)

## 编译

```bash
mvn clean package
```

编译产物位于 `target/fuckcn2b2t-0.1.jar`。

依赖（均为 provided 范围，需服务端提供）：
- [Paper API](https://papermc.io/) — MIT 许可证
- [PlaceholderAPI](https://placeholderapi.com/) — GPL-3.0 许可证

## 兼容性

| 服务端 | 最低版本 | 状态 |
|---|---|---|
| Paper | 1.21.3 | ✅ 完美运行 |
| Folia | 1.21.3 | ✅ 完美运行 |
| Pufferfish / Purpur 等 Paper 衍生端 | 1.21.3 | ✅ 兼容 |
| Bukkit / Spigot | — | ❌ 不兼容 |

Java 21 或更高版本。

## 许可证

[GNU General Public License v3.0](LICENSE)

本项目依赖：
- **Paper API** — MIT 许可证（兼容 GPL-3.0）
- **PlaceholderAPI** — GPL-3.0 许可证（本项目因此采用 GPL-3.0）
