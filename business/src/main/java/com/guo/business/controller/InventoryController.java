package com.guo.business.controller;

import com.guo.business.inventory.InventoryPreheatResult;
import com.guo.business.inventory.InventoryPreheatService;
import com.guo.common.resp.CommonResp;
import com.guo.common.util.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Date;

/**
 * 库存 key 预热入口（后台运维使用）。
 * 支持按车次单点重建，也支持按日期批量预热。
 */
@Tag(name = "库存预热(后台)", description = "把 daily_train_ticket / daily_train_seat 投影到 Redis 库存 key")
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    @Resource
    private InventoryPreheatService inventoryPreheatService;

    @Operation(summary = "按车次重建库存 key")
    @PostMapping("/preheat/{date}/{trainCode}")
    public CommonResp<InventoryPreheatResult> preheatTrain(
            @PathVariable("date") @DateTimeFormat(pattern = "yyyy-MM-dd") Date date,
            @PathVariable("trainCode") String trainCode) {
        return new CommonResp<>(true, "成功",
                inventoryPreheatService.preheatTrain(date, trainCode), HttpStatus.SUCCESS);
    }

    @Operation(summary = "按日期预热全部车次")
    @PostMapping("/preheat/{date}")
    public CommonResp<InventoryPreheatResult> preheatDate(
            @PathVariable("date") @DateTimeFormat(pattern = "yyyy-MM-dd") Date date) {
        return new CommonResp<>(true, "成功",
                inventoryPreheatService.preheatDate(date), HttpStatus.SUCCESS);
    }
}
