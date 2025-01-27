package com.guo.batch.feign;

import com.guo.common.resp.CommonResp;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/11
 */
@Component
public class BusinessFeignFallback implements BusinessFeign{
    @Override
    public CommonResp gen_daily(Date date) {
        //返回一个约定值
        return null;
    }
}
