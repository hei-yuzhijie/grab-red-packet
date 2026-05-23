package yuzhijie.redpacket.strategy.impl;

import org.springframework.stereotype.Component;
import yuzhijie.redpacket.strategy.RedPacketStrategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


@Component("randomStrategy")
public class RandomStrategy implements RedPacketStrategy {

    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");
    private static final int SCALE = 2;
    private static final Random RANDOM = new Random();

    /**
     * 随机红包
     *
     * @param totalAmount 红包总金额
     * @param totalCount  红包个数
     * @return
     */
    @Override
    public List<BigDecimal> allocate(BigDecimal totalAmount, int totalCount) {
        List<BigDecimal> redPackets = new ArrayList<>();
        BigDecimal remainAmount = totalAmount;
        int remainCount = totalCount;

        for (int i = 0; i < totalCount - 1; i++) {
            // 二倍均值 = 剩余金额 / 剩余个数 * 2
            BigDecimal avg = remainAmount.divide(new BigDecimal(remainCount), SCALE, RoundingMode.DOWN);
            BigDecimal max = avg.multiply(new BigDecimal("2"));

            // 生成随机金额：0.01 ~ max 之间
            BigDecimal randomAmount = nextRandomAmount(MIN_AMOUNT, max);

            // 加入列表
            redPackets.add(randomAmount);

            // 更新剩余金额、剩余个数
            remainAmount = remainAmount.subtract(randomAmount);
            remainCount--;
        }
        redPackets.add(remainAmount);
        return redPackets;
    }
    private BigDecimal nextRandomAmount(BigDecimal min, BigDecimal max) {
        double minDouble = min.doubleValue();
        double maxDouble = max.doubleValue();

        // 生成随机小数
        double random = minDouble + (maxDouble - minDouble) * RANDOM.nextDouble();

        // 保留2位小数，四舍五入
        return new BigDecimal(random).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
