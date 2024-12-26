package com.guo.common.context;

import com.guo.common.resp.MemberLoginResp;

import java.util.logging.Logger;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/21
 */
public class LoginMemberContext {
    private static Logger Log = Logger.getLogger(LoginMemberContext.class.getName());
    private static final ThreadLocal<MemberLoginResp> member = new ThreadLocal<>();

    public static MemberLoginResp getMember() {
        return member.get();
    }

    public static void setMember(MemberLoginResp member) {
        LoginMemberContext.member.set(member);
    }
    public static void removeMember() {
        member.remove();
    }
    public static Long getId() {
        try {
            return member.get().getId();
        }catch (Exception e){
            Log.info("获取异常"+e);
            throw e;
        }
    }
}
