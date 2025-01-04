package com.guo.common.interceptor;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.guo.common.context.LoginMemberContext;
import com.guo.common.resp.MemberLoginResp;
import com.guo.common.util.JWTUtil;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.logging.Logger;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/21
 */
@Component
public class MemberInterceptor implements HandlerInterceptor {
    Logger Log = Logger.getLogger(MemberInterceptor.class.getName());

    @Override
    public boolean preHandle(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response, Object handler) throws Exception {
        MDC.put("LOG_ID",System.currentTimeMillis()+ RandomUtil.randomString(3));
        String token = request.getHeader("authorization");
        // 验证token
        if (StrUtil.isNotBlank(token)) {
            // 解析token
            Log.info("token有效");
            JSONObject jsonObject = JWTUtil.getJSONObject(token);
            Log.info("token解析结果：{}" + jsonObject);
            // 将用户信息放入上下文
            MemberLoginResp member = JSONUtil.toBean(jsonObject, MemberLoginResp.class);
            LoginMemberContext.setMember(member);
        }else{
            Log.info("token无效");
        }
        return true;
    }
}
