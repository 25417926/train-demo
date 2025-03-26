package com.guo.auth.filter;

import cn.hutool.core.util.StrUtil;
import com.guo.common.constant.GlobalConstants;
import com.guo.common.context.LoginMemberContext;
import com.guo.common.resp.MemberLoginResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class HeaderUserId2ContextFilter extends OncePerRequestFilter {
    Logger log = LoggerFactory.getLogger(HeaderUserId2ContextFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // 从请求头中获取用户 ID
        String userId = request.getHeader(GlobalConstants.USER_ID);
        // 判断用户 ID 是否为空
        if (StrUtil.isBlank(userId)) {
            // 直接放行
            chain.doFilter(request, response);
            return;
        }
        log.info("## HeaderUserId2ContextFilter, 用户 ID: {}", userId);

        MemberLoginResp memberLoginResp = new MemberLoginResp();
        memberLoginResp.setId(Long.valueOf(userId));
        LoginMemberContext.setMember(memberLoginResp);
        try {
            chain.doFilter(request, response);
        } finally {
            // 一定要删除 ThreadLocal ，防止内存泄露
            LoginMemberContext.removeMember();
            log.info("===== 删除 ThreadLocal， userId: {}", userId);
        }

    }
}