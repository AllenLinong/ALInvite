# ALInvite 邀请系统插件完整使用指南

## 📌 插件简介

**ALInvite** 是一款功能强大的 Minecraft 服务器邀请系统插件，支持邀请码绑定、新人礼包、累计里程碑奖励、权限组奖励等功能。适用于 Spigot/Paper 1.21+ 服务器。

### 特性

- 🎁 **邀请码系统** - 自动生成唯一邀请码，支持自定义格式
- 📦 **礼包商店** - 老玩家可购买礼包，提升新人奖励
- 🏆 **里程碑奖励** - 累计邀请人数达到 milestones 时发放额外奖励
- 👑 **权限组奖励** - 新人加入权限组时，自动奖励邀请人
- 🔗 **跨服同步** - 支持多服务器集群数据同步（公告同步已修复）
- 💰 **经济集成** - 支持 Vault、PlayerPoints 等多种经济插件
- 🔌 **开放 API** - 提供完整 API，支持第三方插件联动

***

## 📥 安装说明

### 支持的服务端核心

| 核心         | 版本                      | 支持情况           |
| ---------- | ----------------------- | -------------- |
| **Paper**  | 1.20.x - 26.x (1.21.11) | ✅ 完全支持         |
| **Spigot** | 1.20.4 - 1.21.x         | ✅ 完全支持         |
| **Folia**  | 1.20.4 - 1.21.x         | ✅ 支持 (自动检测调度器) |

> **注意**: 本插件最低要求 Java 21，推荐使用 Paper 或 Folia 服务端以获得最佳性能和兼容性。

### 环境要求

- **服务端**: Spigot / Paper / Folia 1.20.4 - 1.21.x
- **Java**: JDK 21 或更高版本
- **依赖插件** (可选):
  - Vault + 经济插件 (用于金币奖励)
  - PlayerPoints (用于点券奖励)
  - PlaceholderAPI (用于变量替换)
  - LuckPerms (用于实时权限检测)

### 安装步骤

1. 下载 `ALInvite-1.0.5.jar`
2. 将 jar 文件放入服务器 `plugins` 文件夹
3. 启动服务器生成默认配置
4. 根据需求修改 `config.yml` 和 `languages` 文件
5. 使用 `/alinvite reload` 重载配置

***

## ⚙️ 配置文件说明

### config.yml 主要配置

```yaml
# ========== 邀请码设置 ==========
invite_code:
  length: 6                      # 邀请码长度 (4-8位)
  charset: "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789"  # 字符集
  prefix: ""                     # 邀请码前缀 (如 "AL-" 生成 "AL-ABC123")
  veteran_permission: "alinvite.veteran"   # 老玩家权限节点
  use_permission: "alinvite.use"          # 新玩家使用邀请码权限
  allow_veteran_to_bind: false   # 是否允许老玩家绑定老玩家

# ========== IP 限制 ==========
ip_restriction:
  enabled: false
  max_invites_per_ip: 1          # 同 IP 最多绑定次数
  prevent_self_ip: false         # 禁止同 IP 互相邀请

# ========== 里程碑奖励 ==========
milestones:
  1:
    name: "社交新星"
    rewards:
      - type: "money"
        value: 100
      - type: "points"
        value: 10
  5:
    name: "社交达人"
    rewards:
      - type: "money"
        value: 500
      - type: "points"
        value: 50
  10:
    name: "社交传奇"
    rewards:
      - type: "money"
        value: 1000
      - type: "points"
        value: 100

# ========== 权限组奖励 ==========
permission_group_rewards:
  enabled: true
  permission_prefix: "alinvite"  # 权限前缀
  check_interval: 10              # 检测间隔 (秒)
  rewards:
    "lv2":
      money: 100
      points: 0
      weight: 2
    "lv3":
      money: 200
      points: 0
      weight: 3
    "lv5":
      money: 1000
      points: 100
      weight: 5

# ========== 数据库设置 ==========
database:
  type: "sqlite"                 # sqlite 或 mysql
  server_id: "default"           # 服务器标识 (集群需不同ID)
```

***

## 📋 命令说明

### 玩家命令

| 命令               | 说明       | 权限             |
| ---------------- | -------- | -------------- |
| `/alinvite`      | 打开主菜单    | `alinvite.use` |
| `/alinvite code` | 查看自己的邀请码 | `alinvite.use` |

### 管理员命令

| 命令                               | 说明      | 权限               |
| -------------------------------- | ------- | ---------------- |
| `/alinvite admin reload`         | 重载配置文件  | `alinvite.admin` |
| `/alinvite admin stats [玩家]`     | 查看邀请统计  | `alinvite.admin` |
| `/alinvite admin clearcode <玩家>` | 清除玩家邀请码 | `alinvite.admin` |

***

## 🔑 权限说明

### 玩家权限

| 权限节点               | 说明      | 默认  |
| ------------------ | ------- | --- |
| `alinvite.use`     | 使用邀请码功能 | 所有人 |
| `alinvite.veteran` | 拥有邀请码资格 | OP  |

### 管理员权限

| 权限节点             | 说明    | 默认 |
| ---------------- | ----- | -- |
| `alinvite.admin` | 管理员命令 | OP |

### 权限组奖励权限

| 权限节点           | 触发奖励      |
| -------------- | --------- |
| `alinvite.lv2` | 触发 lv2 奖励 |
| `alinvite.lv3` | 触发 lv3 奖励 |
| `alinvite.lv4` | 触发 lv4 奖励 |
| `alinvite.lv5` | 触发 lv5 奖励 |
| `alinvite.lv6` | 触发 lv6 奖励 |

***

## 🎮 使用教程

### 1. 基础设置

1. 配置 `veteran_permission` 决定谁可以成为"老玩家"（拥有邀请码）
2. 配置 `use_permission` 决定谁可以使用邀请码绑定
3. 在权限插件中给老玩家添加 `alinvite.veteran` 权限

### 2. 邀请流程

```
老玩家 (有 alinvite.veteran)
    ↓ 打开菜单点击"邀请中心"
    ↓ 查看自己的邀请码 (如 ABC123)
    ↓ 分享给新玩家

新玩家 (有 alinvite.use)
    ↓ 打开菜单点击"填写邀请码"
    ↓ 输入老玩家的邀请码 ABC123
    ↓ 绑定成功，新玩家获得礼包奖励
```

### 3. 礼包商店

老玩家可以在礼包商店购买礼包：

| 礼包   | 价格             | 新人奖励              |
| ---- | -------------- | ----------------- |
| 基础礼包 | 免费             | 50金币 + 64熟牛肉      |
| 普通礼包 | 500金币 / 50点券   | 50金币 + 5点券 + 物品   |
| 高级礼包 | 2000金币 / 200点券 | 300金币 + 30点券 + 钻石 |

### 4. 里程碑奖励

当老玩家累计邀请达到指定人数时，自动获得里程碑奖励：

| 里程碑 | 名称   | 奖励示例           |
| --- | ---- | -------------- |
| 1人  | 社交新星 | 100金币 + 10点券   |
| 5人  | 社交达人 | 500金币 + 50点券   |
| 10人 | 社交传奇 | 1000金币 + 100点券 |

### 5. 权限组奖励

当新人加入指定权限组时，奖励邀请他的老玩家：

```
配置示例:
permission_group_rewards:
  rewards:
    "lv2":    # 新人需要 alinvite.lv2 权限
      money: 100
      points: 0
    "vip":    # 新人需要 alinvite.vip 权限
      money: 500
      points: 50
```

**触发条件**: 新人获得 `alinvite.lv2` 权限 → 老玩家获得对应金币奖励

**实时检测**: 配合 LuckPerms 可实现权限变化时实时奖励

***

## 🔌 API 文档

### Maven 依赖

```xml
<dependency>
    <groupId>com.alinvite</groupId>
    <artifactId>ALInvite</artifactId>
    <version>1.0.1</version>
    <scope>provided</scope>
</dependency>
```

### 初始化

```java
import com.alinvite.ALInvite;
import com.alinvite.api.ALInviteAPI;

ALInvite plugin = (ALInvite) getServer().getPluginManager().getPlugin("ALInvite");
if (plugin != null) {
    ALInviteAPI.init(plugin);
}
```

### API 方法

```java
// 获取邀请码
CompletableFuture<String> code = ALInviteAPI.getInviteCode(Player player);

// 获取邀请人数
CompletableFuture<Integer> total = ALInviteAPI.getTotalInvites(Player player);

// 检查绑定状态
CompletableFuture<Boolean> bound = ALInviteAPI.isPlayerBound(Player player);

// 获取邀请人名称
CompletableFuture<String> inviterName = ALInviteAPI.getInviterName(Player player);

// 获取邀请人 UUID
CompletableFuture<UUID> inviter = ALInviteAPI.getInviterUuid(Player player);

// 获取当前礼包配置
CompletableFuture<GiftConfig> gift = ALInviteAPI.getActiveGift(Player player);

// 监听邀请成功事件
ALInviteAPI.registerInviteListener((inviterUuid, inviteeUuid) -> {
    // 处理邀请成功逻辑
});
```

***

## ❓ 常见问题

### Q: 玩家没有邀请码？

检查玩家是否拥有 `alinvite.veteran` 权限，或者是否在配置中修改了权限节点。

### Q: 新人无法输入邀请码？

检查新人是否拥有 `alinvite.use` 权限。

### Q: 金币/点券没有发放？

1. 检查是否安装了经济插件 (Vault/PlayerPoints)
2. 检查 config.yml 中的经济配置是否正确
3. 查看服务器日志是否有错误信息

### Q: 权限组奖励没有触发？

1. 确认 `permission_group_rewards.enabled: true`
2. 确认新人拥有对应的 `alinvite.xxx` 权限
3. 如果不是 LuckPerms，最多可能有 10 秒延迟

### Q: 跨服公告重复？

确认每个服务器的 `database.server_id` 配置不同。

***

## 📄 许可证

本插件遵循 MIT 许可证开源。
