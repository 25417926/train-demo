package com.guo.business.controller;

import com.guo.business.tx.TxProbeProducer;
import com.guo.business.tx.TxProbeRepository;
import com.guo.common.resp.CommonResp;
import com.guo.common.util.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 事务消息探针入口（仅 I01 联调使用，I03 之后移除）。
 */
@Tag(name = "事务消息探针", description = "验证半消息、本地事务与回查")
@RestController
@RequestMapping("tx-probe")
public class TxProbeController {

    @Resource
    private TxProbeProducer txProbeProducer;

    @Resource
    private TxProbeRepository txProbeRepository;

    @Operation(summary = "发送一条事务消息")
    @PostMapping("/send")
    public CommonResp<Map<String, Object>> send(@RequestParam String bizId,
                                                @RequestParam(defaultValue = "true") boolean commit) {
        boolean sent = txProbeProducer.send(bizId, commit);
        Map<String, Object> data = new HashMap<>(4);
        data.put("bizId", bizId);
        data.put("commit", commit);
        data.put("sent", sent);
        return new CommonResp<>(sent, sent ? "已发送" : "发送结果待确认", data, HttpStatus.SUCCESS);
    }

    @Operation(summary = "按业务ID查询探针记录")
    @GetMapping("/state/{bizId}")
    public CommonResp<List<String>> state(@PathVariable String bizId) {
        return new CommonResp<>(true, "成功", txProbeRepository.findStatesByBizId(bizId), HttpStatus.SUCCESS);
    }
}
