package com.guo.auth.Interceptor;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.guo.auth.constant.BaseConstant;
import com.guo.common.resp.CommonResp;
import com.guo.common.util.HttpStatus;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class LoginInterceptor implements HandlerInterceptor {
    /**
     * 删除redis中login数据缓存
     */
    private void DelCache(String tokenValue,String loginId) {
        RedisTemplate redisTemplate = SpringUtil.getBean(RedisTemplate.class);
        String LastActive = BaseConstant.USER_LOGIN_STATE+"last-active"+tokenValue;
        String LoginToken = BaseConstant.USER_LOGIN_STATE+"token"+tokenValue;
        String LoginSession = BaseConstant.USER_LOGIN_STATE+"session"+loginId;
        if(redisTemplate.hasKey(LastActive)){
            redisTemplate.delete(LastActive);
        }
        if(redisTemplate.hasKey(LoginToken)){
            redisTemplate.delete(LoginToken);
        }
        if(redisTemplate.hasKey(LoginSession)){
            redisTemplate.delete(LoginSession);
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String TokenValue = StpUtil.getTokenValue();
        String LoginId =(String) StpUtil.getLoginIdByToken(TokenValue);
        //判断登录是否失效或者被多地登录
        if(!StpUtil.isLogin()){
            CommonResp commonUtil = new CommonResp(false, "请先登录", HttpStatus.UNAUTHORIZED);
            response.getWriter().write(commonUtil.toString());
            DelCache(TokenValue,LoginId);
            return false;
        }

        //判断token是否大于创建俩个小时，大于则刷新token
        //拿取创建时间和当前时间互减
        long time = System.currentTimeMillis() - StpUtil.getSession().getCreateTime();
        long hours = time / (1000 * 60 * 60);
        if (hours >2){
            StpUtil.logout(LoginId);
            StpUtil.login(LoginId);

        }
//        // 获取过期时间
//        long tokenTimeout = StpUtil.getTokenTimeout();
//        //token没过期，过期时间不是-1的时候，每次请求都刷新过期时间
//        if (tokenTimeout != -1){
//            SaTokenDao saTokenDao = SaManager.getSaTokenDao();
//            saTokenDao.updateSessionTimeout(StpUtil.getSession().getId(),3600);
//            saTokenDao.updateTimeout(BaseConstant.USER_LOGIN_STATE+TokenValue,3600);
//            saTokenDao.updateTimeout(BaseConstant.USER_LOGIN_STATE+"last-activity:"+TokenValue,3600);
//        }
        return true;
    }
}