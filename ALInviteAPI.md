# ALInvite API 文档

## 简介

ALInvite 是一款 Minecraft 服务器邀请系统插件，支持邀请码绑定、礼包奖励、里程碑奖励、权限组奖励等功能。本文档面向希望与 ALInvite 联动的第三方插件开发者。

## 依赖引入

### Maven

```xml
<dependency>
    <groupId>com.alinvite</groupId>
    <artifactId>ALInvite</artifactId>
    <version>1.0.5</version>
    <scope>provided</scope>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
compileOnly("com.alinvite:ALInvite:1.0.5")
```

## 前置要求

你的插件需要在 `plugin.yml` 中声明对 ALInvite 的软依赖：

```yaml
name: YourPlugin
version: 1.0.0
main: com.example.YourPlugin
softdepend:
  - ALInvite
```

## 初始化

在你的插件启动时获取 ALInvite API 实例：

```java
import com.alinvite.ALInvite;
import com.alinvite.api.ALInviteAPI;
import org.bukkit.plugin.java.JavaPlugin;

public class YourPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // 等待 ALInvite 完全加载
        getServer().getScheduler().runTaskLater(this, () -> {
            ALInvite alInvite = (ALInvite) getServer().getPluginManager().getPlugin("ALInvite");
            if (alInvite != null) {
                ALInviteAPI.init(alInvite);
                getLogger().info("ALInvite API 初始化成功");
            }
        }, 1L);
    }
}
```

***

## API 参考

所有方法都是**异步**的，返回 `CompletableFuture`，请勿在主线程调用。

***

### 1. 邀请码相关

#### 获取玩家邀请码

```java
// 通过 Player 对象获取
CompletableFuture<String> code = ALInviteAPI.getInviteCode(Player player);

// 通过 UUID 获取
CompletableFuture<String> code = ALInviteAPI.getInviteCode(UUID uuid);
```

**返回值说明：**

- 如果玩家有邀请码，返回格式如 `"ABC123"`（不含前缀）或 `"AL-ABC123"`（含前缀）
- 如果玩家没有邀请码或不具备老玩家权限，返回 `null`

**权限要求：**

- 玩家需要拥有 `alinvite.veteran` 权限（或配置中的自定义权限）才能获得邀请码

**示例：**

```java
ALInviteAPI.getInviteCode(player).thenAccept(code -> {
    if (code != null) {
        player.sendMessage("你的邀请码是: " + code);
    } else {
        player.sendMessage("你还没有邀请码");
    }
});
```

***

#### 获取玩家邀请人数

```java
// 通过 Player 对象获取
CompletableFuture<Integer> total = ALInviteAPI.getTotalInvites(Player player);

// 通过 UUID 获取
CompletableFuture<Integer> total = ALInviteAPI.getTotalInvites(UUID uuid);
```

**返回值说明：**

- 返回该玩家成功邀请的玩家数量（已绑定且完成注册）

***

### 2. 绑定状态相关

#### 检查玩家是否已绑定邀请人

```java
CompletableFuture<Boolean> bound = ALInviteAPI.isPlayerBound(Player player);
CompletableFuture<Boolean> bound = ALInviteAPI.isPlayerBound(UUID uuid);
```

**返回值说明：**

- `true` - 玩家已绑定邀请人
- `false` - 玩家未绑定（是新玩家）

***

#### 获取绑定状态文字

```java
CompletableFuture<String> status = ALInviteAPI.getBindStatus(Player player);
CompletableFuture<String> status = ALInviteAPI.getBindStatus(UUID uuid);
```

**返回值说明：**

- `"已绑定"` - 玩家已绑定邀请人
- `"未绑定"` - 玩家未绑定

***

#### 获取邀请人名称

```java
CompletableFuture<String> name = ALInviteAPI.getInviterName(Player player);
CompletableFuture<String> name = ALInviteAPI.getInviterName(UUID uuid);
```

**返回值说明：**

- 如果有邀请人且在线，返回邀请人当前游戏名
- 如果有邀请人但离线，返回数据库中保存的邀请码
- 如果没有邀请人，返回 `"无"`

***

#### 获取邀请人 UUID

```java
CompletableFuture<UUID> inviter = ALInviteAPI.getInviterUuid(Player player);
CompletableFuture<UUID> inviter = ALInviteAPI.getInviterUuid(UUID uuid);
```

**返回值说明：**

- 如果有邀请人，返回邀请人 UUID
- 如果没有邀请人，返回 `null`

***

#### 获取被邀请的玩家列表

```java
CompletableFuture<List<UUID>> invitees = ALInviteAPI.getInvitees(UUID inviterUuid);
```

**返回值说明：**

- 返回指定邀请人邀请过的所有玩家 UUID 列表

***

### 3. 礼包相关

#### 获取玩家已购买的礼包 ID

```java
CompletableFuture<String> giftId = ALInviteAPI.getPurchasedGift(Player player);
CompletableFuture<String> giftId = ALInviteAPI.getPurchasedGift(UUID uuid);
```

**返回值说明：**

- 返回礼包配置中的 ID，如 `"basic_gift"`、`"premium_gift"`
- 如果玩家未购买任何礼包，返回 `null`

***

#### 获取玩家当前使用的礼包配置

```java
CompletableFuture<GiftConfig> gift = ALInviteAPI.getActiveGift(Player player);
CompletableFuture<GiftConfig> gift = ALInviteAPI.getActiveGift(UUID uuid);
```

**返回值说明：**

- 返回玩家当前使用的礼包完整配置
- 如果玩家未购买且 `require_gift: false`，返回默认礼包
- 如果玩家未购买且 `require_gift: true`，返回 `null`

**GiftConfig 结构：**

```java
public class GiftConfig {
    public String name;                        // 礼包显示名称
    public String material;                    // 物品材质
    public int customModelData;                // 自定义模型数据
    public double priceMoney;                  // 金币价格
    public int pricePoints;                    // 点券价格
    public List<RewardItem> rewards;           // 奖励列表
}

public class RewardItem {
    public String type;   // 奖励类型: "command", "money", "points", "item"
    public String value;  // 奖励值（命令/数量/物品ID）
}
```

***

### 4. 工具方法

#### 检查两玩家是否同 IP

```java
CompletableFuture<Boolean> sameIp = ALInviteAPI.isSameIp(Player p1, Player p2);
```

**返回值说明：**

- `true` - 两玩家 IP 地址相同
- `false` - IP 不同或任一玩家为 null

***

#### 解析 PlaceholderAPI 变量

```java
CompletableFuture<String> resolved = ALInviteAPI.resolvePlaceholders(String text, Player player);
```

**支持的变量：**

- `{player}` - 玩家名称
- `{invite_code}` - 玩家邀请码
- `{total_invites}` - 邀请人数
- 其他 PlaceholderAPI 变量

**示例：**

```java
ALInviteAPI.resolvePlaceholders("恭喜 {player} 获得邀请码: {invite_code}", player)
    .thenAccept(message -> {
        Bukkit.broadcastMessage(message);
    });
```

***

### 5. 事件监听

#### 监听邀请成功事件

```java
ALInviteAPI.registerInviteListener((inviterUuid, inviteeUuid) -> {
    // 邀请人 UUID
    // 被邀请人 UUID
    getLogger().info("玩家 " + inviterUuid + " 成功邀请了 " + inviteeUuid);
});
```

**触发时机：**

- 当新玩家成功绑定邀请码并完成注册时触发

***

## 完整示例

### 检查玩家邀请码并广播

```java
public void announceInviteCode(Player player) {
    ALInviteAPI.getInviteCode(player).thenAccept(code -> {
        if (code != null) {
            Bukkit.broadcastMessage(player.getName() + " 的邀请码是 " + code + "，快去使用吧！");
        } else {
            player.sendMessage("你还没有邀请码");
        }
    });
}
```

### 给予达到邀请里程碑的玩家特殊称号

```java
public void checkAndGrantTitle(Player player) {
    ALInviteAPI.getTotalInvites(player).thenAccept(total -> {
        if (total >= 10) {
            // 给邀请了10人的玩家添加称号
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + player.getName() + " permission set special.inviter true");
        }
    });
}
```

### 第三方插件检测老玩家

```java
public boolean isVeteranPlayer(Player player) {
    return ALInviteAPI.getInviteCode(player).thenApply(code -> code != null).join();
}
```

***

## 常见问题

### Q: API 调用是同步还是异步？

所有 API 方法都是**异步**的，返回 `CompletableFuture`。建议在异步线程中处理业务逻辑，如果需要同步等待，可以使用 `.join()` 方法：

```java
// 异步获取
ALInviteAPI.getInviteCode(player).thenAccept(code -> {
    // 在异步线程中处理
});

// 同步等待（谨慎使用，可能阻塞主线程）
String code = ALInviteAPI.getInviteCode(player).join();
```

### Q: 如何检查 ALInvite 是否安装？

```java
ALInvite plugin = (ALInvite) getServer().getPluginManager().getPlugin("ALInvite");
if (plugin != null) {
    // ALInvite 已安装
    ALInviteAPI.init(plugin);
}
```

### Q: 获取的邀请码为 null 是什么原因？

可能原因：

1. 玩家没有 `alinvite.veteran` 权限
2. 玩家确实没有生成过邀请码
3. 数据库中该玩家没有邀请码记录

### Q: 如何联系开发者？

如有问题或建议，请通过以下方式联系：

- GitHub Issues: [AllenLinong/ALInvite： 邀请插件](https://github.com/AllenLinong/ALInvite)
- QQ:1422163791

***

##
