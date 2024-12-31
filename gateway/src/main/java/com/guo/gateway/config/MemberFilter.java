package com.guo.gateway.config;

import com.guo.gateway.util.JWTUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
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
public class MemberFilter implements GlobalFilter, Ordered {
        Logger LOG = Logger.getLogger(MemberFilter.class.getName());
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        LOG.info("1");
        //写入日志，写所需过滤逻辑
        String path = exchange.getRequest().getURI().getPath();
        if (path.contains("/business") || path.contains("/member/SendCode")|| path.contains("/member/LoginCode")|| path.contains("/member/register")) {
            LOG.info("放行路由："+path);
            return chain.filter(exchange);
        }else{
            LOG.info("拦截路由："+path);
        }
        String token = exchange.getRequest().getHeaders().getFirst("authorization");
        if (token==null||token.isEmpty()){
            LOG.info("token为空");
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        //token是否有效或者过期
        if (JWTUtil.validate(token)){
            LOG.info("token有效");
            return chain.filter(exchange);
        }else{
            LOG.info("token无效");
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        //确保能运行到下一个拦截器

    }

    @Override
    public int getOrder() {
        return 0;
    }
}
