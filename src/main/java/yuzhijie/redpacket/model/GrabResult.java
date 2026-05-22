package yuzhijie.redpacket.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class GrabResult {
    // 红包id
    private String redPacketId;
    // 用户id
    private String userId;
    // 抢红包结果
    private boolean success;
    // 抢红包结果信息
    private String message;
    // 抢红包金额
    private BigDecimal amount;
    // 是否是幸运用户
    private boolean isLucky;
}