package yuzhijie.redpacket.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import yuzhijie.redpacket.common.ApiResponse;
import yuzhijie.redpacket.model.GrabResult;
import yuzhijie.redpacket.model.RedPacket;
import yuzhijie.redpacket.service.RedPacketService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
public class RedPacketController {

    @Autowired
    private RedPacketService redPacketService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    /**
     * 发红包接口
     *
     * @param creatorId   创建者ID
     * @param totalAmount 总金额（元）
     * @param totalCount  红包数量
     * @param type        红包类型：NORMAL（普通）/ RANDOM（随机）
     * @param wish        祝福语（可选）
     * @return 红包ID
     */
    @PostMapping("/send")
    @ResponseBody
    public ApiResponse<Map<String, String>> sendRedPacket(
            @RequestParam String creatorId,
            @RequestParam BigDecimal totalAmount,
            @RequestParam int totalCount,
            @RequestParam(defaultValue = "NORMAL") String type,
            @RequestParam(defaultValue = "恭喜发财") String wish) {

        // 参数校验
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ApiResponse.error(400, "红包金额必须大于0");
        }
        if (totalCount <= 0) {
            return ApiResponse.error(400, "红包数量必须大于0");
        }
        if (!"NORMAL".equals(type) && !"RANDOM".equals(type)) {
            return ApiResponse.error(400, "红包类型只能是 NORMAL 或 RANDOM");
        }

        try {
            String redPacketId = redPacketService.sendRedPacket(
                    creatorId, totalAmount, totalCount, type, wish);

            Map<String, String> result = new HashMap<>();
            result.put("redPacketId", redPacketId);
            result.put("message", "红包发送成功");
            log.info("{}","红包ID："+redPacketId);
            return ApiResponse.success("红包发送成功", result);

        } catch (Exception e) {
            return ApiResponse.error(500, "红包发送失败：" + e.getMessage());
        }
    }

    /**
     * 抢红包接口
     *
     * @param redPacketId 红包ID
     * @param userId      用户ID
     * @return 抢红包结果
     */
    @PostMapping("/grab")
    @ResponseBody
    public ApiResponse<GrabResult> grabRedPacket(
            @RequestParam String redPacketId,
            @RequestParam String userId) {

        // 参数校验
        if (redPacketId == null || redPacketId.isEmpty()) {
            return ApiResponse.error(400, "红包ID不能为空");
        }
        if (userId == null || userId.isEmpty()) {
            return ApiResponse.error(400, "用户ID不能为空");
        }

        try {
            GrabResult grabResult = redPacketService.grabRedPacket(redPacketId, userId);

            if (grabResult.isSuccess()) {
                return ApiResponse.success("抢红包成功", grabResult);
            } else {
                return ApiResponse.error(400, grabResult.getMessage());
            }
        } catch (Exception e) {
            log.info("{}",e);
            return ApiResponse.error(500, "抢红包失败：" + e.getMessage());
        }
    }

    /**
     * 红包详情接口
     *
     * @param redPacketId 红包ID
     * @return 红包详情
     */
    @GetMapping("/detail/{redPacketId}")
    @ResponseBody
    public ApiResponse<RedPacket> getRedPacketDetail(@PathVariable String redPacketId) {
        RedPacket redPacketDetail = redPacketService.getRedPacketDetail(redPacketId);
        if (redPacketDetail == null) {
            return ApiResponse.error(404, "红包不存在或已过期");
        }
        return ApiResponse.success(redPacketDetail);
    }
}