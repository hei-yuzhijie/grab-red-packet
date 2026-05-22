package yuzhijie.redpacket.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
@AllArgsConstructor
@NoArgsConstructor

public class RedPacket {
    // 红包id
    private String id;
    // 创建者id
    private String creatorId;
    // 红包总金额
    private BigDecimal totalAmount;
    // 红包个数
    private Integer totalCount;
    // 剩余金额
    private BigDecimal remainAmount;
    // 剩余个数
    private Integer remainCount;
    // 红包类型(NORMAL-普通红包, RANDOM-随机红包)
    private String type;
    // 祝福语
    private String wish;

    // 创建时间
    private LocalDateTime createTime;
    // 过期时间
    private LocalDateTime expireTime;

    // 状态(1-待领取,2-领取中,3-已领完,4-已过期)
    private Integer status;

}
