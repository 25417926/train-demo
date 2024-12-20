package com.guo.gateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.logging.Logger;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/20
 */
@Component
//实现拦截器和拦截器排序接口
public class Filter implements GlobalFilter, Ordered {
        Logger LOG = Logger.getLogger(Filter.class.getName());
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        LOG.info("1");
        //写入日志，写所需过滤逻辑

        //确保能运行到下一个拦截器
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
