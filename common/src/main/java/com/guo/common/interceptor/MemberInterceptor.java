package com.guo.common.interceptor;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.guo.common.context.LoginMemberContext;
import com.guo.common.resp.MemberLoginResp;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.logging.Logger;

/**
 * 读取网关 AddUserId2HeaderFilter 注入的 UserId 请求头，写入 ThreadLocal
 *
 * @Author: 派
 * @Date: 2024/12/21
 */
@Component
public class MemberInterceptor implements HandlerInterceptor {
    Logger Log = Logger.getLogger(MemberInterceptor.class.getName());

    /**
     * 与 gateway AddUserId2HeaderFilter 保持一致
     */
    private static final String USER_ID_HEADER = "UserId";

    @Override
    public boolean preHandle(javax.servlet.http.HttpServletRequest request,
                             javax.servlet.http.HttpServletResponse response,
                             Object handler) throws Exception {
        MDC.put("LOG_ID", System.currentTimeMillis() + RandomUtil.randomString(3));

        String userIdHeader = request.getHeader(USER_ID_HEADER);
        if (StrUtil.isBlank(userIdHeader)) {
            // 没有登录用户头，不阻断；下游业务若需要登录，通过 LoginMemberContext.getId() 自检
            return true;
        }

        try {
            MemberLoginResp member = new MemberLoginResp();
            member.setId(Long.valueOf(userIdHeader));
            LoginMemberContext.setMember(member);
            Log.info("已从网关 header 解析登录用户 ID: " + userIdHeader);
        } catch (NumberFormatException e) {
            Log.warning("UserId header 不是合法 Long: " + userIdHeader);
        }
        return true;
    }

    @Override
    public void afterCompletion(javax.servlet.http.HttpServletRequest request,
                                javax.servlet.http.HttpServletResponse response,
                                Object handler, Exception ex) {
        // 清理 ThreadLocal，避免线程池复用时数据残留
        LoginMemberContext.removeMember();
        MDC.remove("LOG_ID");
    }
}
