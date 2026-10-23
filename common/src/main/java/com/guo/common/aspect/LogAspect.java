package com.guo.common.aspect;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.support.spring.PropertyPreFilters;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
public class LogAspect {
    private final static Logger LOG = LoggerFactory.getLogger(LogAspect.class);

    public LogAspect() {
        System.out.println("Common LogAspect");
    }



    /**
     * 定义一个切点
     */
    @Pointcut("execution(public * com.guo..*Controller.*(..))")
    public void controllerPointcut() {
    }

    /**
     * I21 高频受理接口不记录请求/响应日志：该接口是压测与生产的主要流量入口，
     * 每笔打印参数与返回体既放大日志量，也会在并发下争抢输出锁。
     */
    private boolean skipLog() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return true;
        }
        String uri = attributes.getRequest().getRequestURI();
        if (uri == null) {
            return true;
        }
        // 受理入口 + member 出票回写的内部接口均为高频路径
        return uri.endsWith("/purchase/apply") || uri.contains("/internal/order/ticketStatus");
    }
    //前置通知
    @Before("controllerPointcut()")
    public void doBefore(JoinPoint joinPoint) {
        // I21 高频受理接口不打印请求日志，避免日志竞争拖慢受理吞吐
        if (skipLog()) {
            return;
        }
        //拿取时间戳加三位随机的字符创码作为日志的流水号id
        MDC.put("LOG_id",System.currentTimeMillis()+ RandomUtil.randomString(3));
        // 开始打印请求日志
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        // 获取request
        HttpServletRequest request = attributes.getRequest();
        //获取当前被拦截的方法签名信息
        Signature signature = joinPoint.getSignature();
        //从方法签名内获取方法名
        String name = signature.getName();
        // 打印请求信息
        LOG.info("------------- 开始 -------------");
       //打印请求地址，拿取request中的url地址和方法
        LOG.info("请求地址: {} {}", request.getRequestURL().toString(), request.getMethod());
        LOG.info("类名方法: {}.{}", signature.getDeclaringTypeName(), name);
        LOG.info("远程地址: {}", request.getRemoteAddr());

        // 打印请求参数
        Object[] args = joinPoint.getArgs();
        // LOG.info("请求参数: {}", JSONObject.toJSONString(args));

        // 排除特殊类型的参数，如文件类型
        Object[] arguments = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof ServletRequest
                    || args[i] instanceof ServletResponse
                    || args[i] instanceof MultipartFile) {
                continue;
            }
            arguments[i] = args[i];
        }
        // 排除字段，敏感字段或太长的字段不显示：身份证、手机号、邮箱、密码等
        String[] excludeProperties = {"userID",};//排除字段，需要自己添加
        PropertyPreFilters filters = new PropertyPreFilters();
        PropertyPreFilters.MySimplePropertyPreFilter excludefilter = filters.addFilter();
        excludefilter.addExcludes(excludeProperties);
        LOG.info("请求参数: {}", JSONObject.toJSONString(arguments, excludefilter));
    }
    //环绕通知
    @Around("controllerPointcut()")
    public Object doAround(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        // I21 高频受理接口直接放行，不打印返回结果与耗时
        if (skipLog()) {
            return proceedingJoinPoint.proceed();
        }
        long startTime = System.currentTimeMillis();
        Object result = proceedingJoinPoint.proceed();
        // 排除字段，敏感字段或太长的字段不显示：身份证、手机号、邮箱、密码等
        String[] excludeProperties = {};
        PropertyPreFilters filters = new PropertyPreFilters();
        PropertyPreFilters.MySimplePropertyPreFilter excludefilter = filters.addFilter();
        excludefilter.addExcludes(excludeProperties);
        LOG.info("返回结果: {}", JSONObject.toJSONString(result, excludefilter));
        LOG.info("------------- 结束 耗时：{} ms -------------", System.currentTimeMillis() - startTime);
        return result;
    }

}
