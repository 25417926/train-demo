package com.guo.member.service;

import cn.hutool.core.collection.CollUtil;
import com.guo.common.exception.ExceptionEnum;
import com.guo.common.exception.ExceptionConsole;
import com.guo.member.domain.Member;
import com.guo.member.domain.MemberExample;
import com.guo.member.mapper.MemberMapper;
import com.guo.member.req.MemberRegisterReq;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * @Description 
 * @Author: 派
 * @Date: 2024/12/12
 */
@Service
public class MemberService {

    @Resource
    private MemberMapper memberMapper;
    public int count(){
        return Math.toIntExact(memberMapper.countByExample(null));
    }

    public long register(MemberRegisterReq req){
        MemberExample example =new MemberExample();
        example.createCriteria().andMobileEqualTo(req.getMobile());
        List<Member> list = memberMapper.selectByExample(example);
        if(CollUtil.isNotEmpty(list)) {
            //使用hutool工具，判断list内是否为空，不为空返回list的第一个元素的id
            //这个用于手机号加验证码注册，可以判断为本人的可以使用这种方式
            //return list.get(0).getId();
            /**
             * 抛出异常
             */
            throw new ExceptionConsole(ExceptionEnum.MEMBER_MOBILE_EXIST);
        }
        Member member = new Member();
        member.setId(System.currentTimeMillis());
        member.setMobile(req.getMobile());
        memberMapper.insertSelective(member);
        return member.getId();
    }
}
