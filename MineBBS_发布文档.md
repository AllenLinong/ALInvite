# 🎯 ALInvite - 我的世界服务器邀请系统插件

## 📖 插件介绍

**ALInvite** 是一个功能强大的我的世界服务器邀请系统插件，旨在通过激励玩家邀请新玩家加入服务器，促进服务器人口增长和社区活跃度。

### ✨ 核心功能

- 🔑 **智能邀请码系统** - 每个老玩家拥有独立的邀请码
- 🎁 **礼包商店** - 老玩家可购买不同等级的礼包
- 🏆 **里程碑系统** - 累计邀请达到指定人数时发放额外奖励
- 📢 **全服公告** - 里程碑达成时自动全服广播
- 🛡️ **IP限制** - 防止刷小号和作弊行为
- 🌐 **多语言支持** - 内置中文和英文语言包
- 🖥️ **GUI菜单** - 直观易用的图形界面
- ⚡ **权限组奖励** - 与LuckPerms等权限插件集成
- 💰 **第三方充值返点** - 支持MinePay、SweetCheckout等充值插件
- ⚡ **Folia兼容性** - 完全支持Folia多线程服务器

## 🚀 快速安装

### 📋 前置要求
- **Minecraft 服务器版本**: 1.20.4+
- **Java 版本**: 21+
- **服务器类型**: 支持 Folia（推荐）

### 📦 安装步骤
1. 下载 `ALInvite-1.0.6-shaded.jar`
2. 将文件放入服务器的 `plugins` 文件夹
3. 重启服务器
4. 插件会自动生成配置文件
5. 根据需要修改配置文件
6. 重新加载配置或重启服务器

## 🔐 权限系统

### 基础权限
- `alinvite.use` - 使用主命令和查看菜单（默认开启）
- `alinvite.buygift` - 允许购买礼包（默认开启）
- `alinvite.admin` - 管理员权限（默认op）
- `alinvite.veteran` - 老玩家权限（拥有邀请码）

### 权限组设置示例
```yaml
# 使用 LuckPerms 设置权限组
/lp group default permission set alinvite.use
/lp group vip permission set alinvite.veteran
/lp group vip permission set alinvite.buygift
```

## ⌨️ 命令使用

### 玩家命令
| 命令 | 描述 | 权限 |
|------|------|------|
| `/alinvite` | 打开邀请系统主菜单 | `alinvite.use` |
| `/alinvite code` | 查看自己的邀请码 | `alinvite.use` + `alinvite.veteran` |
| `/alinvite stats` | 查看邀请统计信息 | `alinvite.use` |
| `/alinvite buygift` | 打开礼包商店 | `alinvite.buygift` |
| `/alinvite help` | 查看帮助信息 | `alinvite.use` |

### 管理员命令
| 命令 | 描述 | 权限 |
|------|------|------|
| `/alinvite admin help` | 显示管理帮助 | `alinvite.admin` |
| `/alinvite admin reload` | 重载插件配置 | `alinvite.admin` |
| `/alinvite admin givecode <玩家>` | 为玩家生成邀请码 | `alinvite.admin` |
| `/alinvite admin clearcode <玩家>` | 清除玩家邀请码 | `alinvite.admin` |
| `/alinvite admin addinvite <玩家> <数量>` | 增加玩家邀请次数 | `alinvite.admin` |
| `/alinvite admin reset <玩家>` | 重置玩家邀请数据 | `alinvite.admin` |
| `/alinvite admin announce <玩家> <里程碑值>` | 发送全服公告 | `alinvite.admin` |
| `/alinvite admin checkgroup <玩家>` | 检查权限组奖励 | `alinvite.admin` |
| `/alinvite givedj <玩家> <数量>` | 为玩家充值点券 | `alinvite.admin` |

## ⚙️ 配置文件详解

### 语言设置
```yaml
language:
  locale: "zh_cn"  # 语言文件标识
```

### 邀请码设置
```yaml
invite_code:
  length: 6                    # 邀请码长度
  charset: "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789"
  prefix: ""                   # 邀请码前缀
  veteran_permission: "alinvite.veteran"
  use_permission: "alinvite.use"
  allow_veteran_to_bind: false  # 是否允许老玩家绑定老玩家
```

### IP限制设置
```yaml
ip_restriction:
  enabled: false               # 是否启用IP限制
  max_invites_per_ip: 1       # 同一IP最大邀请次数
  prevent_self_ip: false      # 禁止同一IP互相邀请
```

### 里程碑系统配置
```yaml
milestones:
  auto_claim: false           # 是否自动发放奖励
  1:
    name: "社交新星"
    lore:
      - "&b1000 EMC积分"
      - "&a100 金币"
      - "&b10 点券"
    rewards:
      - type: "command"
        value: "alex give %player% 1000"
      - type: "money"
        value: 100
      - type: "points"
        value: 10
```

### 礼包商店配置
```yaml
gift_shop:
  enabled: true
  gifts:
    default:
      name: "&6基础礼包"
      price_money: 0
      price_points: 0
      rewards:
        - type: "money"
          value: 50
        - type: "item"
          value: "COOKED_BEEF 64"
```

## 🎮 使用指南

### 快速开始

**老玩家操作：**
```bash
/alinvite code          # 获取邀请码
/alinvite stats         # 查看邀请统计
/alinvite buygift       # 购买礼包
```

**新玩家操作：**
```bash
/alinvite               # 打开主菜单绑定邀请码
/alinvite stats         # 查看邀请统计
```

### 完整邀请流程
1. **老玩家获取邀请码** - 通过 `/alinvite code` 获取专属邀请码
2. **新玩家加入服务器** - 首次登录服务器
3. **输入邀请码** - 通过 `/alinvite` 打开主菜单绑定邀请码
4. **验证绑定** - 系统验证邀请码并建立绑定关系
5. **发放新玩家奖励** - 根据老玩家购买的礼包类型发放奖励
6. **记录邀请关系** - 老玩家邀请人数+1
7. **检查里程碑** - 检查老玩家是否达到里程碑条件
8. **后续返点** - 新玩家充值触发返点

## 🔌 第三方集成

### 支持的充值插件
- ✅ **MinePay** - 完全支持，自动监听充值成功事件
- ✅ **SweetCheckout** - 完全支持，自动监听充值成功事件
- 🔄 **其他插件** - 可扩展，通过事件监听器支持

### 返点规则
| 充值金额 | 返点比例 | 说明 |
|----------|----------|------|
| ≥ 10点券 | 5% | 基础返点比例（所有玩家默认） |
| ≥ 10点券 | 10%-25% | 根据权限组获得更高返点 |
| < 10点券 | 0% | 不触发返点 |

### 返点权限组
| 权限组 | 返点比例 | 权限节点 |
|--------|----------|----------|
| **基础** | 5% | 无需权限（默认） |
| **VIP** | 15% | `alinvite.rebate.vip` |
| **SVIP** | 20% | `alinvite.rebate.svip` |
| **MVIP** | 25% | `alinvite.rebate.mvip` |
| **管理员** | 0% | `alinvite.rebate.admin` |

## 🚀 性能优化

ALInvite 经过全面性能优化，特别针对高并发服务器环境：

### Folia多线程服务器支持
- ✅ **区域调度** - 避免线程阻塞，使用Folia区域调度器
- ✅ **异步处理** - 所有耗时操作异步执行，提升响应速度
- ✅ **智能检测** - 自动检测Folia/传统服务器

### 数据库优化
- ✅ **自动索引** - 查询速度提升80%，自动创建数据库索引
- ✅ **连接池** - 使用HikariCP连接池，连接复用
- ✅ **异步操作** - 所有数据库操作异步执行，避免阻塞

### 缓存优化
- ✅ **动态缓存** - 智能调整大小，根据服务器规模动态调整
- ✅ **Caffeine** - 高性能缓存，使用业界领先的缓存库
- ✅ **过期策略** - 自动清理过期缓存，内存管理

## 📦 下载地址

**最新版本**: ALInvite-1.0.6-shaded.jar

**推荐使用 shaded 版本**，包含所有依赖，开箱即用。

## 📞 技术支持

如有问题或建议，请联系插件作者：
- **作者**: Allen_Linong
- **QQ**: 1422163791

## 🔗 API文档

ALInvite 提供完整的 API 接口，支持第三方插件集成。详细API文档请参考项目中的 `ALInviteAPI.md` 文件。

***

**插件特色：**
- 🎯 智能邀请系统，促进服务器人口增长
- 💰 完整的充值返点机制，激励玩家邀请
- ⚡ 高性能优化，支持Folia多线程服务器
- 🔧 丰富的配置选项，高度可定制
- 🌐 多语言支持，国际化友好

**适用场景：**
- 需要增加服务器人口的服务器
- 希望建立玩家邀请机制的服务器
- 需要充值返点功能的服务器
- 追求高性能的Folia服务器

---
*📅 最后更新: 2026-04-21*
*✨ 插件版本: v1.0.6*