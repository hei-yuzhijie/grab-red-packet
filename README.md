# 红包系统 (Grab Red Packet)

基于 Spring Boot + Redis 的高性能红包系统，支持普通红包和拼手气红包两种模式。

## 项目结构

```
grab-red-packet/
├── src/main/java/yuzhijie/redpacket/
│   ├── GrabRedPacketApplication.java          # Spring Boot 启动类
│   ├── controller/
│   │   ├── PageController.java                # 页面路由控制器
│   │   └── RedPacketController.java           # REST API 控制器
│   ├── service/
│   │   └── RedPacketService.java              # 红包核心业务逻辑
│   ├── strategy/
│   │   ├── RedPacketStrategy.java             # 红包分配策略接口
│   │   └── impl/
│   │       ├── NormalStrategy.java           # 普通红包策略（平均分配）
│   │       └── RandomStrategy.java           # 拼手气红包策略（二倍均值）
│   ├── model/
│   │   ├── RedPacket.java                    # 红包实体
│   │   ├── GrabResult.java                   # 抢红包结果
│   │   └── GrabRecord.java                   # 抢红包记录
│   ├── common/
│   │   ├── ApiResponse.java                  # 统一响应格式
│   │   └── RedisUtil.java                    # Redis 工具类
│   ├── config/
│   │   └── RedisConfig.java                  # Redis 配置
│   └── mind/
│       └── RedPacketService-代码审查报告.md   # 代码审查报告
├── src/main/resources/
│   ├── application.properties                # 应用配置
│   ├── lua/
│   │   └── updateRedPacketHash.lua          # Lua 脚本（原子更新）
│   └── templates/
│       └── index.html                       # Thymeleaf 前端页面
└── pom.xml                                   # Maven 依赖配置
```

## 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot 4.0.6 | 核心框架 |
| Spring Data Redis | Redis 数据访问 |
| StringRedisTemplate | 字符串类型 Redis 操作 |
| Thymeleaf | 模板引擎（前端页面） |
| Lombok | 简化代码 |
| Jackson | JSON 序列化 |

## 功能特性

### 1. 红包类型

| 类型 | 说明 | 算法 |
|------|------|------|
| NORMAL | 普通红包 | 平均分配，保留2位小数 |
| RANDOM | 拼手气红包 | 二倍均值算法，金额随机 |

### 2. 红包状态

| 状态码 | 说明 |
|--------|------|
| 1 | 待领取 |
| 2 | 领取中 |
| 3 | 已领完 |
| 4 | 已过期 |

### 3. 核心机制

- **原子性保证**：使用 Lua 脚本确保红包扣减操作的原子性
- **并发控制**：Redis 乐观锁（watch/multi/exec）防止并发冲突
- **防重复**：记录已抢用户，防止重复领取
- **过期处理**：24小时自动过期

## API 接口

### 页面

| 接口 | 方法 | 说明 |
|------|------|------|
| `/` | GET | 首页（Thymeleaf 页面） |

### 红包 API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/redpacket/send` | POST | 发送红包 |
| `/api/redpacket/grab` | POST | 抢红包（单个） |
| `/api/redpacket/grab/batch` | POST | 抢红包（批量） |
| `/api/redpacket/detail/{id}` | GET | 查询红包详情 |

### 请求示例

**发送红包**
```bash
POST /api/redpacket/send
Content-Type: application/x-www-form-urlencoded

creatorId=user001&totalAmount=100&totalCount=10&type=NORMAL&wish=恭喜发财
```

**抢红包**
```bash
POST /api/redpacket/grab
Content-Type: application/x-www-form-urlencoded

redPacketId=xxx&userId=user002
```

**批量抢红包**
```bash
POST /api/redpacket/grab/batch
Content-Type: application/x-www-form-urlencoded

redPacketId=xxx&userIds=user002&userIds=user003&userIds=user004
```

### 响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "redPacketId": "RP202605230001",
    "success": true,
    "amount": 8.88
  }
}
```

## Redis 数据结构

| Key | 类型 | 说明 | 过期时间 |
|-----|------|------|----------|
| `redpacket:amounts:{id}` | List | 红包金额列表 | 24h |
| `redpacket:detail:{id}` | Hash | 红包完整详情（对象） | 24h |
| `redpacket:partDetail:{id}` | Hash | 红包部分详情（原子更新用） | 24h |
| `redpacket:grabbers:{id}` | Set | 已抢用户集合 | 24h |
| `redpacket:record:{id}` | List | 抢红包记录列表 | 24h |

## 配置说明

```properties
# Redis 配置
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.database=0

# Thymeleaf 配置
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.cache=false
```

## 快速开始

### 1. 环境要求

- JDK 25+
- Redis 6.0+
- Maven 3.8+

### 2. 启动步骤

```bash
# 1. 克隆项目
git clone <repository-url>
cd grab-red-packet

# 2. 启动 Redis
redis-server

# 3. 编译运行
mvn spring-boot:run
```

### 3. 访问页面

打开浏览器访问：http://localhost:8080/

## 核心算法

### 普通红包分配

```java
BigDecimal unitAmount = totalAmount / totalCount;  // 每人平均
```

### 拼手气红包分配（二倍均值）

```java
// 公式：每次随机金额 = 0.01 ~ (剩余金额/剩余个数) * 2
BigDecimal avg = remainAmount / remainCount;
BigDecimal max = avg * 2;
BigDecimal randomAmount = nextRandom(0.01, max);
```

这种算法确保：
- 每人至少拿到 0.01 元
- 不会出现金额分配不均（如一人拿完）的情况
- 所有人抢到的金额期望值相等

## 并发安全

### 问题场景

高并发下可能出现以下问题：
1. 同一用户多次抢红包
2. 红包金额被超额领取
3. 红包数量超发

### 解决方案

1. **用户去重**：使用 Set 的 `SISMEMBER` 检查用户是否已抢过
2. **乐观锁**：`WATCH` 监控 + `MULTI/EXEC` 事务
3. **Lua 脚本**：原子性扣减金额和数量

```lua
-- Lua 脚本保证原子性
local remain = tonumber(redis.call('HGET', key, 'remainCount'))
if remain <= 0 then return -2 end
redis.call('HSET', key, 'remainAmount', newAmount)
redis.call('HSET', key, 'remainCount', newCount)
```

## 项目截图

系统提供可视化界面，包含三个主要功能：
1. **发送红包**：设置金额、数量、类型
2. **抢红包**：支持多用户并发抢
3. **查询详情**：查看红包当前状态

## License

MIT License