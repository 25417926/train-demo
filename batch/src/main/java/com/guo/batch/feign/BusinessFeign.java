package com.guo.batch.feign;

import com.guo.common.resp.CommonResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Date;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/3
 */
@FeignClient(name = "business" , url = "localhost:80/business")
public interface BusinessFeign {
    @GetMapping("/daily-train/gen-daily/{date}")
    CommonResp gen_daily(@PathVariable("date") @DateTimeFormat(pattern = "yyyy-MM-dd") Date date);

}
