package yuzhijie.redpacket.strategy.impl;


import org.springframework.stereotype.Component;
import yuzhijie.redpacket.strategy.RedPacketStrategy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component("normalStrategy")
public class NormalStrategy implements RedPacketStrategy {

    // 保留 2 位小数（金额最常用）
    private static final int SCALE = 2;

    /**
     * 普通红包
     *
     * @param totalAmount 红包总金额
     * @param totalCount  红包个数
     * @return
     */
    @Override
    public List<BigDecimal> allocate(BigDecimal totalAmount, int totalCount) {
        List<BigDecimal> redPackets = new ArrayList<>();

        BigDecimal unitAmount = totalAmount.divide(new BigDecimal(totalCount), SCALE, BigDecimal.ROUND_HALF_UP);

        for (int i = 0; i < totalCount - 1; i++) {
            redPackets.add(unitAmount);
        }
        // 最后一个红包
        redPackets.add(totalAmount.subtract(unitAmount.multiply(new BigDecimal(totalCount - 1))));

        return redPackets;
    }
}
