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

        List<String> amountsString = amounts.stream().map(BigDecimal::toString).toList();
        redisTemplate.opsForList().rightPushAll(listKey, amountsString);
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
                    Long size = operations.opsForSet().size(listKey);
                    if (size == null || size == 0) {
                        return new ArrayList<>(Arrays.asList("红包已抢完"));
                    }
                    operations.multi();
                    operations.opsForList().leftPop(listKey);
                    operations.opsForSet().add(grabUserKey, userId);
                    return operations.exec();
                }
            });

            if (execute.get(0) instanceof String) {
                // 返回了 "该用户已抢过红包" 或 "红包已抢完"
                grabResult.setSuccess(false);
                grabResult.setMessage((String) execute.get(0));
            } else if (execute == null) {
                // 并发冲突，事务失败
                continue;
            } else {
                // 抢红包成功
                String firstResult = (String) execute.get(0);
                BigDecimal amount = new BigDecimal(firstResult);
                grabResult.setSuccess(true);
                grabResult.setAmount(amount);

                updateRedPacketDetail(redPacketId, amount);
                saveGrabRecord(redPacketId, userId, amount);
            }
        }
        return grabResult;

    }

    private void updateRedPacketDetail(String redPacketId, BigDecimal amount) {
    }

    private void saveGrabRecord(String redPacketId, String userId, BigDecimal amount) {
    }
}
