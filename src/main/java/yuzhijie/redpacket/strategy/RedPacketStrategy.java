package yuzhijie.redpacket.strategy;

import java.math.BigDecimal;
import java.util.List;

public interface RedPacketStrategy {
    /**
     * 分配红包
     *
     * @param totalAmount 总金额
     * @param totalCount  红包个数
     * @return 红包列表
     */
    public List<BigDecimal> allocate(BigDecimal totalAmount, int totalCount);
}
