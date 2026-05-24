package yuzhijie.redpacket.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import yuzhijie.redpacket.common.RedisUtil;
import yuzhijie.redpacket.model.GrabRecord;
import yuzhijie.redpacket.model.GrabResult;
import yuzhijie.redpacket.model.RedPacket;
import yuzhijie.redpacket.strategy.RedPacketStrategy;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RedPacketService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    @Qualifier("randomStrategy")
    private RedPacketStrategy randomStrategy;

    @Autowired
    @Qualifier("normalStrategy")
    private RedPacketStrategy normalStrategy;

    @Resource
    private RedisUtil redisUtil;

    private static final String KEY_PREFIX = "redpacket:";
    private static final String AMOUNT_LIST_PREFIX = "redpacket:amounts:";
    private static final String GRAB_USER_PREFIX = "redpacket:grabbers:";
    private static final String DETAIL_PREFIX = "redpacket:detail:";
    private static final String RECORD_PREFIX = "redpacket:record:";
    private static final String PARTDETAIL_PREFIX = "redpacket:partDetail:";
    private static final DefaultRedisScript<Long> UPDATE_SCRIPT;

    static {
        UPDATE_SCRIPT = new DefaultRedisScript<>();
        UPDATE_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/updateRedPacketHash.lua")));
        UPDATE_SCRIPT.setResultType(Long.class);
    }

    public String sendRedPacket(String creatorId, BigDecimal totalAmount, int totalCount, String type, String wish) {
        String redPacketId = RedisUtil.generateId();

        List<BigDecimal> amounts;
        if(type.equals("NORMAL")){
            amounts = normalStrategy.allocate(totalAmount, totalCount);
        }else {
            amounts = randomStrategy.allocate(totalAmount, totalCount);
        }
        String listKey = AMOUNT_LIST_PREFIX + redPacketId;
        String grabUserKey = GRAB_USER_PREFIX + redPacketId;
        //错误：直接传入 amountsString（一个 List<String> 对象）时，它被当作 单个元素 处理
        List<String> amountsString = amounts.stream().map(BigDecimal::toString).toList();
        redisTemplate.opsForList().rightPushAll(listKey, amountsString.toArray(new String[0]));

        redisTemplate.expire(listKey, 24 , TimeUnit.HOURS);

        RedPacket redPacket = new RedPacket();
        redPacket.setId(redPacketId);
        redPacket.setCreatorId(creatorId);
        redPacket.setTotalAmount(totalAmount);
        redPacket.setTotalCount(totalCount);
        redPacket.setRemainAmount(totalAmount);
        redPacket.setRemainCount(totalCount);
        redPacket.setType(type);
        redPacket.setWish(wish);
        redPacket.setCreateTime(LocalDateTime.now());
        redPacket.setExpireTime(LocalDateTime.now().plusHours(24));
        redPacket.setStatus(1);
        String detailKey = DETAIL_PREFIX + redPacketId;
        redisTemplate.opsForValue().set(detailKey, redPacket);
        redisTemplate.expire(detailKey, 24 , TimeUnit.HOURS);

        redisTemplate.opsForSet().add(grabUserKey, "init");
        redisTemplate.expire(grabUserKey, 24, TimeUnit.HOURS);

        //创建部分详情
        String detailPartKey = PARTDETAIL_PREFIX + redPacketId;
        redisTemplate.opsForHash().put(detailPartKey, "remainAmount", redPacket.getRemainAmount().toPlainString());
        redisTemplate.opsForHash().put(detailPartKey, "remainCount", String.valueOf(redPacket.getTotalCount()));
        redisTemplate.opsForHash().put(detailPartKey, "status", String.valueOf(redPacket.getStatus()));
        redisTemplate.expire(detailPartKey, 24, TimeUnit.HOURS);


        return redPacketId;
    }
    public GrabResult grabRedPacket(String redPacketId, String userId) {

        GrabResult grabResult = new GrabResult();
        grabResult.setRedPacketId(redPacketId);
        grabResult.setUserId(userId);

        String detailKey = DETAIL_PREFIX + redPacketId;
        String grabUserKey = GRAB_USER_PREFIX + redPacketId;
        String listKey = AMOUNT_LIST_PREFIX + redPacketId;
        //红包过期情况

        RedPacket redPacket = (RedPacket) redisTemplate.opsForValue().get(detailKey);
        if (redPacket == null) {
            grabResult.setSuccess(false);
            grabResult.setMessage("红包不存在");
            return grabResult;
        }

        // 检查过期时间
        if (redPacket.getExpireTime().isBefore(LocalDateTime.now())) {
            // 更新状态为过期（幂等）
            if (redPacket.getStatus() != 4) {
                redPacket.setStatus(4);
                redisTemplate.opsForValue().set(detailKey, redPacket);
            }
            grabResult.setSuccess(false);
            grabResult.setMessage("红包已过期");
            return grabResult;
        }


        // 设置最大重试次数，用于处理乐观锁竞争导致的失败情况
        int maxRetries = 5;
        for (int i = 0; i < maxRetries; i++) {
            List<Object> execute = redisTemplate.execute(new SessionCallback<List<Object>>() {

                @Override
                public List<Object> execute(RedisOperations operations) throws DataAccessException {
                    operations.watch(Arrays.asList(grabUserKey, listKey));
                    boolean isMember = operations.opsForSet().isMember(grabUserKey, userId);
                    if (isMember) {
                        return Collections.singletonList("DUPLICATE");
                    }
                    Long size = operations.opsForList().size(listKey);
                    if (size == null || size == 0) {
                        return Collections.singletonList("EMPTY");
                    }
                    operations.multi();
                    operations.opsForSet().add(grabUserKey, userId);
                    operations.opsForList().leftPop(listKey);
                    return operations.exec();
                }
            });

            if (execute == null || execute.isEmpty()) {
                continue;
            }

            if (execute.get(0).equals("DUPLICATE")) {
                grabResult.setSuccess(false);
                grabResult.setMessage("该用户已抢过红包");
                return grabResult;
            }
            if (execute.get(0).equals("EMPTY")) {
                grabResult.setSuccess(false);
                grabResult.setMessage("红包已抢完");
                return grabResult;
            }

            Object amountObj = execute.get(1);
            BigDecimal amount = new BigDecimal(amountObj.toString());
            grabResult.setSuccess(true);
            grabResult.setAmount(amount);
            grabResult.setMessage("抢红包成功");

            // 更新红包详情 + 记录
            updateRedPacketDetail(redPacketId, amount);
            saveGrabRecord(redPacketId, userId, amount);

            // 成功直接返回，不再重试
            return grabResult;
        }
        grabResult.setSuccess(false);
        grabResult.setMessage("系统繁忙，请稍后重试");
        return grabResult;

    }

    /**
     * 更新红包详情(专门用于抢红包成功后的结果录入)
     * 虽然是异步执行，但是调用时必然成功抢到红包
     * 改进方向（继续封装为原子性）
     * @param redPacketId
     * @param amount
     */
    private void updateRedPacketDetail(String redPacketId, BigDecimal amount) {
        String detailPartKey = PARTDETAIL_PREFIX + redPacketId;
        Long ttl = redisTemplate.getExpire(detailPartKey, TimeUnit.SECONDS);
        long expireSec = (ttl != null && ttl > 0) ? ttl : 0L;

        System.out.println("amount str: " + amount.toPlainString());
        System.out.println("expireSec str: " + expireSec);
        Long result = stringRedisTemplate.execute(UPDATE_SCRIPT,
                Collections.singletonList(detailPartKey),
                amount.toPlainString(),
                String.valueOf(expireSec));

        System.out.println("result: " + result);
        if (result == -1) {
            throw new RuntimeException("红包详情不存在");
        }
    }

    /**
     * 更新红包当前状态
     * @param redPacketId
     * @param status
     */
    private void updateRedPacketStatus(String redPacketId, Integer status) {
        String detailKey = DETAIL_PREFIX + redPacketId;
        RedPacket redPacket = (RedPacket)redisTemplate.opsForValue().get(detailKey);
        redPacket.setStatus(status);
        redisTemplate.opsForValue().set(detailKey, redPacket);
    }

    private void saveGrabRecord(String redPacketId, String userId, BigDecimal amount) {
        String recordKey = RECORD_PREFIX + redPacketId;
        GrabRecord record = new GrabRecord();
        record.setId(RedisUtil.generateId());
        record.setRedPacketId(redPacketId);
        record.setUserId(userId);
        record.setAmount(amount);
        record.setGrabTime(LocalDateTime.now());
        //暂不计算手气最佳
        record.setIsLucky(0);
//        log.info("保存抢红包记录：{}",record);
        redisTemplate.opsForList().rightPush(recordKey, record);
        redisTemplate.expire(recordKey, 24, TimeUnit.HOURS);

    }

    /**
     * 获取红包详情
     *
     * @param redPacketId 红包ID
     * @return 红包实体信息
     */
    public RedPacket getRedPacketDetail(String redPacketId) {
        String detailKey = DETAIL_PREFIX + redPacketId;
        String partDetailKey = PARTDETAIL_PREFIX + redPacketId;
        RedPacket redPacket = (RedPacket)redisTemplate.opsForValue().get(detailKey);
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(partDetailKey);
        redPacket.setRemainAmount(new BigDecimal(entries.get("remainAmount").toString()));
        redPacket.setRemainCount(Integer.parseInt(entries.get("remainCount").toString()));
        redPacket.setStatus(Integer.parseInt(entries.get("status").toString()));
        return redPacket;
    }
}
