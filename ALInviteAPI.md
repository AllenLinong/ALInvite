# ALInvite API 文档

## 📖 概述

ALInvite 提供 API 接口，支持通过命令调用方式集成。第三方插件可以通过执行 `alinvite givedj` 命令来触发返点处理。

## 🚀 快速开始

### 📦 导入 API

```java
import com.alinvite.api.ALInviteAPI;
```

### 🔧 获取插件实例

```java
// 方式1：通过 Bukkit 获取
ALInvite plugin = (ALInvite) Bukkit.getPluginManager().getPlugin("ALInvite");

// 方式2：通过 API 类获取（推荐）
ALInvite plugin = ALInviteAPI.getPlugin();
```

## 🔌 第三方插件集成方式

### 💰 通过命令调用集成

ALInvite 支持通过命令调用方式与第三方插件集成。第三方插件可以在支付完成后执行 `alinvite givedj` 命令来触发返点处理。

#### 配置示例

在第三方插件的配置文件中添加：

```yaml
# 支付完成后执行命令
commands:
  # 使用控制台执行 alinvite givedj 命令
  - '[console]alinvite givedj %player_name% %points%'
  
  # 可选：给玩家发送成功消息
  - '[message]&a充值成功！返点已自动处理'
```

#### 变量说明
- `%player_name%` - 玩家名称
- `%points%` - 充值点券数量

#### 执行流程
1. 第三方插件执行 `[console]alinvite givedj 玩家名 金额`
2. ALInvite 处理返点逻辑
3. ALInvite 执行配置的点券发放命令

#### processPointsRecharge - 处理点券充值返点（完整版）

**方法签名：**
```java
public static CompletableFuture<Boolean> processPointsRecharge(
    String operator, 
    String targetPlayer, 
    double amount, 
    boolean skipRebate
)
```

**参数说明：**
- `operator` - 操作者名称（执行命令的玩家或控制台）
- `targetPlayer` - 目标玩家名称（接收点券的玩家）
- `amount` - 充值点券数量
- `skipRebate` - 是否跳过返点处理（用于测试或特殊情况）

**使用示例：**
```java
// 管理员命令调用示例
CompletableFuture<Boolean> result = ALInviteAPI.processPointsRecharge(
    "Console", 
    "Steve", 
    1000.0, 
    false
);
```

### 🔗 邀请关系查询 API

#### getInviterUuid - 获取邀请人UUID

**方法签名：**
```java
public static CompletableFuture<UUID> getInviterUuid(UUID playerUuid)
```

**参数说明：**
- `playerUuid` - 玩家 UUID

**返回值：**
- `CompletableFuture<UUID>` - 邀请人的 UUID，如果无邀请人返回 `null`

**使用示例：**
```java
UUID playerUuid = player.getUniqueId();
CompletableFuture<UUID> inviterFuture = ALInviteAPI.getInviterUuid(playerUuid);

inviterFuture.thenAccept(inviterUuid -> {
    if (inviterUuid != null) {
        getLogger().info("玩家的邀请人是: " + inviterUuid);
    } else {
        getLogger().info("玩家没有邀请人");
    }
});
```

#### getInviterName - 获取邀请人名称

**方法签名：**
```java
public static CompletableFuture<String> getInviterName(UUID playerUuid)
```

**参数说明：**
- `playerUuid` - 玩家 UUID

**返回值：**
- `CompletableFuture<String>` - 邀请人的名称，如果无邀请人返回 "无"

### 🔑 邀请码管理 API

#### getInviteCode - 获取邀请码

**方法签名：**
```java
public static CompletableFuture<String> getInviteCode(UUID playerUuid)
```

**参数说明：**
- `playerUuid` - 玩家 UUID

**返回值：**
- `CompletableFuture<String>` - 玩家的邀请码，如果无邀请码返回 `null`

**使用示例：**
```java
UUID playerUuid = player.getUniqueId();
CompletableFuture<String> codeFuture = ALInviteAPI.getInviteCode(playerUuid);

codeFuture.thenAccept(code -> {
    if (code != null) {
        getLogger().info("玩家的邀请码是: " + code);
    } else {
        getLogger().info("玩家没有邀请码");
    }
});
```

### 📊 邀请统计 API

#### getTotalInvites - 获取总邀请人数

**方法签名：**
```java
public static CompletableFuture<Integer> getTotalInvites(UUID playerUuid)
```

**参数说明：**
- `playerUuid` - 玩家 UUID

**返回值：**
- `CompletableFuture<Integer>` - 总邀请人数

**使用示例：**
```java
UUID playerUuid = player.getUniqueId();
CompletableFuture<Integer> totalFuture = ALInviteAPI.getTotalInvites(playerUuid);

totalFuture.thenAccept(total -> {
    getLogger().info("玩家累计邀请人数: " + total);
});
```

#### getTotalRebateAmount - 获取累计返点金额

**方法签名：**
```java
public static CompletableFuture<Double> getTotalRebateAmount(UUID playerUuid)
```

**参数说明：**
- `playerUuid` - 玩家 UUID

**返回值：**
- `CompletableFuture<Double>` - 累计返点金额

### 🎁 礼包管理 API

#### getPurchasedGift - 获取已购买礼包ID

**方法签名：**
```java
public static CompletableFuture<String> getPurchasedGift(UUID playerUuid)
```

**参数说明：**
- `playerUuid` - 玩家 UUID

**返回值：**
- `CompletableFuture<String>` - 已购买礼包的ID，如果未购买返回 `null`

#### getActiveGift - 获取当前生效礼包配置

**方法签名：**
```java
public static CompletableFuture<GiftManager.GiftConfig> getActiveGift(UUID playerUuid)
```

**参数说明：**
- `playerUuid` - 玩家 UUID

**返回值：**
- `CompletableFuture<GiftManager.GiftConfig>` - 当前生效礼包的配置信息

### 🔍 状态检查 API

#### isPlayerBound - 检查玩家是否已绑定邀请码

**方法签名：**
```java
public static CompletableFuture<Boolean> isPlayerBound(UUID playerUuid)
```

**参数说明：**
- `playerUuid` - 玩家 UUID

**返回值：**
- `CompletableFuture<Boolean>` - `true` 表示已绑定，`false` 表示未绑定

#### isSameIp - 检查两个玩家是否同一IP

**方法签名：**
```java
public static CompletableFuture<Boolean> isSameIp(Player p1, Player p2)
```

**参数说明：**
- `p1` - 玩家1
- `p2` - 玩家2

**返回值：**
- `CompletableFuture<Boolean>` - `true` 表示同一IP，`false` 表示不同IP

### 🛡️ 防重复检查 API

#### checkRebateDuplicate - 检查跨服重复交易

**方法签名：**
```java
public static CompletableFuture<Boolean> checkRebateDuplicate(UUID playerUuid, double amount)
```

**参数说明：**
- `playerUuid` - 玩家 UUID
- `amount` - 充值金额

**返回值：**
- `CompletableFuture<Boolean>` - `true` 表示重复交易，`false` 表示新交易

## 💡 第三方插件集成示例

### MinePay 集成示例

```java
import top.minepay.api.event.MinePaySuccessEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MinePayIntegration implements Listener {
    
    @EventHandler
    public void onMinePaySuccess(MinePaySuccessEvent event) {
        try {
            // 获取订单信息
            TradeInfo tradeInfo = event.getTradeInfo();
            String playerName = tradeInfo.getPlayerName();
            int priceInCents = tradeInfo.getPrice();
            double pointsAmount = priceInCents / 100.0;
            
            // 只处理点券充值订单
            if (tradeInfo.getTradeType() == TradeType.POINT) {
                // 调用 ALInvite API 处理返点
                CompletableFuture<Boolean> result = ALInviteAPI.processPointsRecharge(
                    playerName, 
                    pointsAmount
                );
                
                result.thenAccept(success -> {
                    if (success) {
                        getLogger().info("MinePay充值返点处理成功: " + playerName);
                    } else {
                        getLogger().warning("MinePay充值返点处理失败: " + playerName);
                    }
                });
            }
        } catch (Exception e) {
            getLogger().severe("处理MinePay充值事件失败: " + e.getMessage());
        }
    }
}
```

### SweetCheckout 集成示例

```java
import com.sweetcheckout.api.event.PaymentCompletedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class SweetCheckoutIntegration implements Listener {
    
    @EventHandler
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        try {
            String playerName = event.getPlayer().getName();
            double pointsAmount = event.getAmount();
            
            // 调用 ALInvite API 处理返点
            CompletableFuture<Boolean> result = ALInviteAPI.processPointsRecharge(
                playerName, 
                pointsAmount
            );
            
            result.thenAccept(success -> {
                if (success) {
                    getLogger().info("SweetCheckout充值返点处理成功: " + playerName);
                } else {
                    getLogger().warning("SweetCheckout充值返点处理失败: " + playerName);
                }
            });
        } catch (Exception e) {
            getLogger().severe("处理SweetCheckout支付事件失败: " + e.getMessage());
        }
    }
}
```

### 自定义支付插件集成示例

```java
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class CustomPaymentIntegration implements Listener {
    
    @EventHandler
    public void onCustomPayment(CustomPaymentEvent event) {
        try {
            String playerName = event.getPlayerName();
            double pointsAmount = event.getPointsAmount();
            
            // 调用 ALInvite API 处理返点
            CompletableFuture<Boolean> result = ALInviteAPI.processPointsRecharge(
                playerName, 
                pointsAmount
            );
            
            result.thenAccept(success -> {
                if (success) {
                    getLogger().info("自定义支付插件返点处理成功: " + playerName);
                } else {
                    getLogger().warning("自定义支付插件返点处理失败: " + playerName);
                }
            });
        } catch (Exception e) {
            getLogger().severe("处理自定义支付事件失败: " + e.getMessage());
        }
    }
}
```

## ⚠️ 注意事项

1. **异步处理**：所有 API 方法都是异步的，返回 `CompletableFuture`，请使用 `thenAccept()` 或 `thenApply()` 处理结果

2. **错误处理**：建议对 API 调用进行错误处理，避免插件崩溃

3. **权限检查**：调用 API 前请确保玩家有相应权限

4. **数据一致性**：多个插件同时调用 API 时，建议使用同步机制避免数据竞争

## 📞 技术支持

如有 API 使用问题或集成需求，请联系：
- **作者**：Allen_Linong
- **QQ**：1422163791

***

*📅 最后更新：2026-04-21*
*✨ 文档版本：v3.0（完整API版）*