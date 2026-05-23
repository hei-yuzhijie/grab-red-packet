package yuzhijie.redpacket.service;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import yuzhijie.redpacket.common.RedisUtil;
import yuzhijie.redpacket.model.GrabRecord;
import yuzhijie.redpacket.model.GrabResult;
import yuzhijie.redpacket.model.RedPacket;
import yuzhijie.redpacket.strategy.RedPacketStrategy;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class RedPacketService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

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

    public String sendRedPacket(String creatorId, BigDecimal totalAmount, int totalCount, String type, String wish) {
        String redPacketId = RedisUtil.generateId();

        List<BigDecimal> amounts;
        if(type.equals("NORMAL")){
            amounts = normalStrategy.allocate(totalAmount, totalCount);
        }else {
            amounts = randomStrategy.allocate(totalAmount, totalCount);
        }
        String listKey = AMOUNT_LIST_PREFIX + redPacketId;
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

        Long expireSeconds = redisTemplate.getExpire(listKey);
        if(expireSeconds == -2) {
            updateRedPacketStatus(redPacketId,4);
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
                        return new ArrayList<>(Arrays.asList("该用户已抢过红包"));
                    }
                    Long size = operations.opsForList().size(listKey);
                    if (size == null || size == 0) {
                        return new ArrayList<>(Arrays.asList("红包已抢完"));
                    }
                    operations.multi();
                    operations.opsForList().leftPop(listKey);
                    operations.opsForSet().add(grabUserKey, userId);
                    return operations.exec();
                }
            });

            if (execute == null) {
                continue;
            }

            if (execute.get(0) instanceof String) {
                grabResult.setSuccess(false);
                grabResult.setMessage((String) execute.get(0));
                return grabResult;
            }

            Object amountObj = execute.get(0);
            if (amountObj == null) {
                grabResult.setSuccess(false);
                grabResult.setMessage("红包已抢完");
                return grabResult;
            }

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
     * @param redPacketId
     * @param amount
     */
    private void updateRedPacketDetail(String redPacketId, BigDecimal amount) {
        String detailKey = DETAIL_PREFIX + redPacketId;
        RedPacket redPacket = (RedPacket) redisTemplate.opsForValue().get(detailKey);

        redPacket.setRemainAmount(redPacket.getRemainAmount().subtract(amount));
        redPacket.setRemainCount(redPacket.getRemainCount() - 1);
        if (redPacket.getRemainCount().equals(0)) {
            redPacket.setStatus(3);
        } else {
            redPacket.setStatus(2);
        }
    }

    /**
     * 更新红包当前状态
     * @param redPacketId
     * @param status
     */
    public void updateRedPacketStatus(String redPacketId, Integer status) {
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
        return (RedPacket) redisTemplate.opsForValue().get(detailKey);
    }
}
