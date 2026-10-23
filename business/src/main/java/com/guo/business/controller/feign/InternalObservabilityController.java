package com.guo.business.controller.feign;

import com.guo.business.alert.AlertEvent;
import com.guo.business.alert.AlertService;
import com.guo.business.metrics.ObservabilitySnapshotService;
import com.guo.common.resp.CommonResp;
import com.guo.common.util.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * I18 可观测内部接口：指标面板、告警查询与告警自测。
 *
 * <p>只读指标来自 Micrometer 注册表；告警自测用于验证告警日志与查询通道可用，
 * 不依赖外部监控平台。</p>
 */
@Tag(name = "可观测(内部)", description = "指标快照、告警查询与人工告警自测")
@RestController
@RequestMapping("/internal/observability")
public class InternalObservabilityController {

    @Resource
    private ObservabilitySnapshotService snapshotService;
    @Resource
    private AlertService alertService;

    @Operation(summary = "关键指标快照")
    @GetMapping("/metrics")
    public CommonResp<Map<String, Object>> metrics() {
        return new CommonResp<>(true, "成功", snapshotService.snapshot(), HttpStatus.SUCCESS);
    }

    @Operation(summary = "最近告警")
    @GetMapping("/alerts")
    public CommonResp<List<AlertEvent>> alerts(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        return new CommonResp<>(true, "成功", alertService.recentAlerts(limit), HttpStatus.SUCCESS);
    }

    @Operation(summary = "人工触发一条告警")
    @PostMapping("/alerts/test")
    public CommonResp<AlertEvent> testAlert(
            @RequestParam(value = "rule", required = false) String rule) {
        return new CommonResp<>(true, "已触发", alertService.triggerTest(rule), HttpStatus.SUCCESS);
    }
}
