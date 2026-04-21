# 🎯 ALInvite - 我的世界服务器邀请系统插件

## 📖 概述

**ALInvite** 是一个功能强大的我的世界服务器邀请系统插件，旨在通过激励玩家邀请新玩家加入服务器，促进服务器人口增长和社区活跃度。插件支持多语言、自定义菜单、礼包商店、里程碑奖励等丰富功能。

### ✨ 主要特性

| 功能模块           | 描述                 |
| -------------- | ------------------ |
| 🔑 **智能邀请码系统** | 每个老玩家拥有独立的邀请码      |
| 🎁 **礼包商店**    | 老玩家可购买不同等级的礼包      |
| 🏆 **里程碑系统**   | 累计邀请达到指定人数时发放额外奖励  |
| 📢 **全服公告**    | 里程碑达成时自动全服广播       |
| 🛡️ **IP限制**   | 防止刷小号和作弊行为         |
| 🔒 **功能权限控制** | 同IP绑定功能权限限制         |
| 🌐 **多语言支持**   | 内置中文和英文语言包         |
| 🖥️ **GUI菜单**  | 直观易用的图形界面          |
| ⚡ **权限组奖励**    | 与LuckPerms等权限插件集成  |
| 💰 **点券充值返点**  | 支持点券充值返点功能         |
| 💵 **现金返点系统**  | 支持现金返点记录和兑换功能      |
| 🔌 **第三方集成**   | 支持通过命令调用方式与第三方插件集成 |
| ⚡ **Folia兼容性** | 完全支持Folia多线程服务器    |
| 🚀 **性能优化**    | 数据库索引、缓存、异步处理优化    |

## 🚀 安装指南

### 📋 前置要求

| 项目                  | 要求           |
| ------------------- | ------------ |
| **Minecraft 服务器版本** | 1.20.4+      |
| **Java 版本**         | **21+**      |
| **服务器类型**           | 支持 Folia（推荐） |

### 📦 安装步骤

1. 📥 将 `ALInvite-1.0.7-shaded.jar` 文件放入服务器的 `plugins` 文件夹
2. 🔄 重启服务器
3. ⚙️ 插件会自动生成配置文件
4. ✏️ 根据需要修改配置文件
5. 🔄 重新加载配置或重启服务器

### ⚡ 服务器类型支持

| 服务器类型        | 支持状态 | 性能表现    |
| ------------ | ---- | ------- |
| ✅ **Folia**  | 完全支持 | 🚀 最佳性能 |
| ✅ **Paper**  | 完全支持 | ⚡ 优秀性能  |
| ✅ **Spigot** | 完全支持 | 👍 良好性能 |
| ✅ **Bukkit** | 完全支持 | 👍 良好性能 |

## 🔐 权限系统

### 📊 基础权限表

| 权限节点                  | 描述           | 默认值    |
| --------------------- | ------------ | ------ |
| ✅ `alinvite.use`      | 使用主命令和查看菜单   | `true` |
| 🎁 `alinvite.buygift` | 允许购买礼包       | `true` |
| 👑 `alinvite.admin`   | 管理员权限        | `op`   |
| 🔑 `alinvite.veteran` | 老玩家权限（拥有邀请码） | 无      |

### ⚙️ 权限组设置示例

```yaml
# 使用 LuckPerms 设置权限组
/lp group default permission set alinvite.use
/lp group vip permission set alinvite.veteran
/lp group vip permission set alinvite.buygift
```

## ⌨️ 命令使用

### 👤 玩家命令表

| 命令                     | 描述        | 权限                                  |
| ---------------------- | --------- | ----------------------------------- |
| 🖥️ `/alinvite`        | 打开邀请系统主菜单 | `alinvite.use`                      |
| 🔑 `/alinvite code`    | 查看自己的邀请码  | `alinvite.use` + `alinvite.veteran` |
| 📊 `/alinvite stats`   | 查看邀请统计信息  | `alinvite.use`                      |
| 🎁 `/alinvite buygift` | 打开礼包商店    | `alinvite.buygift`                  |
| ❓ `/alinvite help`     | 查看帮助信息    | `alinvite.use`                      |

### 👑 管理员命令表

| 命令                                        | 描述       | 权限               |
| ----------------------------------------- | -------- | ---------------- |
| ❓ `/alinvite admin help`                  | 显示管理帮助   | `alinvite.admin` |
| 🔄 `/alinvite admin reload`               | 重载插件配置   | `alinvite.admin` |
| 🔑 `/alinvite admin givecode <玩家>`        | 为玩家生成邀请码 | `alinvite.admin` |
| 🗑️ `/alinvite admin clearcode <玩家>`      | 清除玩家邀请码  | `alinvite.admin` |
| ➕ `/alinvite admin addinvite <玩家> <数量>`   | 增加玩家邀请次数 | `alinvite.admin` |
| 🔄 `/alinvite admin reset <玩家>`           | 重置玩家邀请数据 | `alinvite.admin` |
| 📢 `/alinvite admin announce <玩家> <里程碑值>` | 发送全服公告   | `alinvite.admin` |
| 👥 `/alinvite admin checkgroup <玩家>`      | 检查权限组奖励  | `alinvite.admin` |
| 💰 `/alinvite givedj <玩家> <数量>`           | 为玩家充值点券  | `alinvite.admin` |
| 💵 `/alinvite admin cashrebate <玩家> [金额]` | 现金返点管理   | `alinvite.admin` |
| 🔄 `/alinvite admin exchange <玩家> <金额>`   | 现金返点兑换   | `alinvite.admin` |

## ⚙️ 配置文件详解

### 🔧 基础配置

```yaml
# 邀请码配置
invite_code:
  enabled: true                    # 是否启用邀请码系统
  veteran_permission: "alinvite.veteran"  # 老玩家权限节点
  code_length: 8                   # 邀请码长度
  
# 新玩家奖励
new_player_reward:
  enabled: true                    # 是否启用新玩家奖励
  require_gift: false              # 是否需要礼包才能发放奖励
  
# 里程碑系统
milestone:
  enabled: true                    # 是否启用里程碑系统
  announce_enabled: true           # 是否启用全服公告
```

### 💰 点券充值返点系统

```yaml
# 点券充值返点配置
points_rebate:
  enabled: true                    # 是否启用点券充值返点功能
  
  # 点券发放命令
  points_command: "points give {player} {amount}"
  
  # 权限组返点比例
  permission_prefix: "alinvite.rebate"
  
  # 返点限制
  limits:
    min_amount: 10.0               # 最低充值金额
    max_rebate_per_day: 1000.0     # 每日返点上限
```

### 💵 现金返点系统

```yaml
# 现金返点配置
cash_rebate:
  enabled: true                    # 是否启用现金返点系统
  
  # 权限组现金返点模式
  permission_groups:
    vip:
      rebate_rate: 0.15            # 返点比例
      cash_mode: false             # 是否为现金返点模式
```

## 🔒 功能权限控制系统

ALInvite 提供强大的功能权限控制系统，允许管理员灵活控制同IP绑定的功能权限，既防止作弊又兼顾用户体验。

### ⚙️ 配置说明

在 `config.yml` 中配置功能权限控制：

```yaml
# ==================== IP 限制 ====================
ip_restriction:
  enabled: false                 # 是否启用同IP绑定限制
  # 如果 enabled=true：完全禁止同IP绑定（下面的配置无效）
  # 如果 enabled=false：允许同IP绑定，但根据 flexible_mode 限制功能
  flexible_mode:
    milestone: false             # 里程碑功能（false = 禁用，同IP绑定不增加里程碑计数）
    rebate: true                 # 返点功能（true = 启用，同IP绑定可以获得返点）
    gift: false                  # 礼包功能（false = 禁用，同IP绑定没有新玩家礼包）
```

### 🔄 执行逻辑

#### 场景1：严格模式（enabled: true）
- **完全禁止**同IP绑定
- 同一个IP只能有一个玩家绑定邀请码
- **完全防作弊**，适合严格管理的服务器

#### 场景2：灵活模式（enabled: false）
- **允许**同IP绑定，但所有同IP绑定都根据配置限制功能
- **所有同IP绑定**都受同样的功能权限限制
- 没有"第一个绑定不受限制"的特殊处理

### 💡 实际应用

#### 家庭用户场景
- 玩家A（家庭IP）绑定 → ✅ 获得返点功能，❌ 无法获得里程碑和礼包
- 玩家B（同家庭IP）绑定 → ✅ 获得返点功能，❌ 无法获得里程碑和礼包
- **优势**：家庭多个玩家都能参与，但防止刷奖励

#### 网吧/学校场景
- 玩家C（网吧IP）绑定 → ✅ 获得返点功能，❌ 无法获得里程碑和礼包
- 玩家D（同网吧IP）绑定 → ✅ 获得返点功能，❌ 无法获得里程碑和礼包
- **优势**：公共环境也能使用，但限制作弊行为

### 🎯 功能权限控制效果

| 功能模块 | 同IP绑定效果 | 说明 |
|----------|--------------|------|
| **里程碑功能** | ❌ 禁用 | 同IP绑定不增加里程碑计数 |
| **返点功能** | ✅ 启用 | 同IP绑定可以获得返点 |
| **礼包功能** | ❌ 禁用 | 同IP绑定没有新玩家礼包 |

### 🔧 技术特点

1. **永久绑定**：功能权限状态永久记录在数据库中
2. **智能检测**：自动检测同IP绑定顺序
3. **详细提示**：玩家绑定时会收到明确的功能状态说明
4. **灵活配置**：管理员可以自由配置功能限制规则

## 🔌 第三方插件集成

ALInvite 提供了多种集成方式，包括命令调用和API集成，为第三方插件提供灵活的集成方案。

### 💰 充值插件集成

#### 命令调用方式
在第三方充值插件的配置文件中添加以下命令：

```yaml
# 支付完成后执行命令
commands:
  # 使用控制台执行 alinvite givedj 命令
  - '[console]alinvite givedj %player_name% %points%'
  
  # 可选：给玩家发送成功消息
  - '[message]&a充值成功！返点已自动处理'
  - '[actionbar]&a返点处理完成'
```

### 📡 ALInviteAPI 接口

ALInvite 提供了完整的 API 接口，方便第三方插件集成。

#### 核心 API 方法

| 方法名称 | 描述 | 参数 | 返回值 |
|---------|------|------|-------|
| `getInviteCode()` | 获取玩家邀请码 | Player/UUID | CompletableFuture<String> |
| `getTotalInvites()` | 获取玩家邀请总数 | Player/UUID | CompletableFuture<Integer> |
| `getPurchasedGift()` | 获取玩家购买的礼包 | Player/UUID | CompletableFuture<String> |
| `getActiveGift()` | 获取玩家当前活跃礼包 | Player/UUID | CompletableFuture<GiftConfig> |
| `isSameIp()` | 检查两个玩家是否同IP | Player, Player | CompletableFuture<Boolean> |
| `getInvitees()` | 获取被邀请玩家列表 | UUID | CompletableFuture<List<UUID>> |
| `isPlayerBound()` | 检查玩家是否已绑定 | Player/UUID | CompletableFuture<Boolean> |
| `getBindStatus()` | 获取玩家绑定状态 | Player/UUID | CompletableFuture<String> |
| `getInviterName()` | 获取邀请人名称 | Player/UUID | CompletableFuture<String> |
| `getInviterUuid()` | 获取邀请人UUID | Player/UUID | CompletableFuture<UUID> |
| `resolvePlaceholders()` | 解析占位符 | String, Player | CompletableFuture<String> |
| `registerInviteListener()` | 注册邀请成功监听器 | InviteSuccessListener | void |

#### 点券充值返点 API

| 方法名称 | 描述 | 参数 | 返回值 |
|---------|------|------|-------|
| `processPointsRecharge()` | 处理点券充值返点 | String, double | CompletableFuture<Boolean> |
| `processPointsRecharge()` | 处理点券充值返点（带操作者信息） | String, String, double, boolean | CompletableFuture<Boolean> |
| `getTotalRebateAmount()` | 获取玩家累计返点总额 | Player/UUID | CompletableFuture<Double> |
| `checkRebateDuplicate()` | 检查跨服重复交易 | UUID, double | CompletableFuture<Boolean> |

#### API 使用示例

```java
// 获取玩家邀请码
ALInviteAPI.getInviteCode(player).thenAccept(code -> {
    player.sendMessage("您的邀请码: " + code);
});

// 处理点券充值返点
ALInviteAPI.processPointsRecharge("ThirdParty", "PlayerName", 100.0, false)
    .thenAccept(success -> {
        if (success) {
            System.out.println("返点处理成功");
        } else {
            System.out.println("返点处理失败");
        }
    });

// 注册邀请成功监听器
ALInviteAPI.registerInviteListener((inviterUuid, inviteeUuid) -> {
    Player inviter = Bukkit.getPlayer(inviterUuid);
    Player invitee = Bukkit.getPlayer(inviteeUuid);
    if (inviter != null && invitee != null) {
        inviter.sendMessage("您成功邀请了 " + invitee.getName());
    }
});
```

#### 变量说明

- `%player_name%` - 玩家名称
- `%points%` - 充值点券数量

#### 执行流程

1. 第三方插件执行 `[console]alinvite givedj 玩家名 金额`
2. ALInvite 处理返点逻辑
3. ALInvite 执行配置的点券发放命令

### ✅ 集成优势

- **简单稳定**：通过标准命令调用，避免复杂的API依赖
- **兼容性好**：支持所有支持命令调用的第三方插件
- **可控性强**：管理员可以明确控制集成方式
- **无版本依赖**：无需担心插件版本兼容性问题

## 🖥️ 菜单系统

### 🎯 菜单功能

- **主菜单**：填写邀请码、查看邀请中心
- **邀请中心**：查看邀请统计、领取里程碑奖励
- **礼包商店**：购买不同等级的礼包
- **返回导航**：支持返回主菜单的便捷导航

### ⚙️ 菜单配置

菜单布局和按钮行为在 `menus.yml` 中配置，支持动态内容和状态显示。

## 🚀 性能优化

### ⚡ Folia多线程支持

- 完全支持Folia多线程服务器
- 异步数据库操作，避免线程阻塞
- 智能调度系统，提升响应速度

### 💾 数据库优化

- 使用HikariCP连接池
- 自动创建数据库索引
- 异步数据操作

### 🔄 缓存系统

- 高性能Caffeine缓存
- 智能缓存过期策略
- 动态缓存大小调整

## 🔧 故障排除

### ❓ 常见问题

| 问题             | 解决方案                           |
| -------------- | ------------------------------ |
| 🛠️ **菜单无法打开** | 检查权限设置，确认玩家有 `alinvite.use` 权限 |
| 🔍 **邀请码无效**   | 检查邀请码格式，检查IP限制设置               |
| 💰 **奖励未发放**   | 检查经济插件是否正常，确认命令权限设置            |

### 📝 日志调试

启用调试模式查看详细日志：

```yaml
# 在 config.yml 中添加
debug: true
```

## 📞 技术支持

如有问题或建议，请联系插件作者：

- **作者**：Allen\_Linong
- **QQ**：1422163791

***

**插件特色：**

- 🎯 智能邀请系统，促进服务器人口增长
- 💰 完整的充值返点机制，激励玩家邀请
- ⚡ Folia多线程兼容，高性能运行
- 🔧 简单稳定的第三方集成方案
