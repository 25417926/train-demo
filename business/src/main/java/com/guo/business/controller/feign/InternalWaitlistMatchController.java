package com.guo.business.controller.feign;

import com.guo.business.waitlist.WaitlistMatchEvent;
import com.guo.business.waitlist.WaitlistMatcher;
import com.guo.common.resp.CommonResp;
import com.guo.common.util.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * I16 内部触发入口：MQ 不可用或需要人工补跑时，按票档直接驱动一轮补位匹配。
 *
 * <p>eventId 为空时由匹配器生成 {@code MANUAL:{snowflake}}，因此重复调用会各执行一轮。</p>
 */
@Tag(name = "候补补位(内部)", description = "手动触发同票档 FIFO 补位匹配")
@RestController
@RequestMapping("/internal/waitlist")
public class InternalWaitlistMatchController {

    @Resource
    private WaitlistMatcher waitlistMatcher;

    @Operation(summary = "手动触发补位匹配")
    @PostMapping("/match")
    public CommonResp<Integer> match(@RequestBody WaitlistMatchEvent event) {
        if (event == null) {
            return new CommonResp<>(false, "缺少事件参数", HttpStatus.ERROR);
        }
        int matched = waitlistMatcher.onRelease(event);
        return new CommonResp<>(true, "成功", matched, HttpStatus.SUCCESS);
    }
}
