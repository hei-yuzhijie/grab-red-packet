package yuzhijie.redpacket.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class GrabRecord {

    private String id;               // 记录ID
    private String redPacketId;      // 红包ID
    private String userId;           // 用户ID
    private BigDecimal amount;       // 抢到金额
    private LocalDateTime grabTime;  // 抢到时间
    private Integer isLucky;         // 是否手气最佳：0-否，1-是
}