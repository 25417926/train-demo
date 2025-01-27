package com.guo.member.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.guo.common.exception.ExceptionConsole;
import com.guo.common.exception.ExceptionEnum;
import com.guo.common.req.UserDetailsReq;
import com.guo.common.util.JWTUtil;
import com.guo.common.util.SnowUtil;
import com.guo.member.domain.Member;
import com.guo.member.domain.MemberExample;
import com.guo.member.mapper.MemberMapper;
import com.guo.member.req.MemberLoginReq;
import com.guo.member.req.MemberRegisterReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    Logger Log = LoggerFactory.getLogger(MemberService.class);
    @Resource
    private MemberMapper memberMapper;
    @Resource
    private ValidCodeService validCodeService;
    public String count() {
    return "1";
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
        Member member = BeanUtil.copyProperties(req, Member.class);
        member.setId(SnowUtil.getSnowflakeNextId());
        //为了防止并发出现问题，将前面的时间戳改为雪花算法来当做id
        //getSnowflake(1,1)第一个参数为机器id，第二个参数为数据中心id
        memberMapper.insertSelective(member);
        return member.getId();
    }

    public String Login(MemberLoginReq req){
        MemberExample example =new MemberExample();
        example.createCriteria().andMobileEqualTo(req.getMobile());
        List<Member> list = memberMapper.selectByExample(example);
        if(CollUtil.isEmpty(list)) {
            //抛出异常
            throw new ExceptionConsole(ExceptionEnum.MEMBER_MOBILE_NOT_EXIST);
        }

        boolean expired = validCodeService.isCodeExpired(req.getTokenCode());
        if(!expired){
            //抛出验证码过期异常
            throw new ExceptionConsole(ExceptionEnum.MEMBER_CODE_EXPIRED);
        }
        boolean valid = validCodeService.ValidForCode(req.getTokenCode(), req.getCode());
        //判断验证码是否正确
        if(valid){

           UserDetailsReq reqs= BeanUtil.copyProperties(req,UserDetailsReq.class);

           reqs.setId(list.get(0).getId());
            //将map集合传入jwt转换工具类，然后返回jwt生成的字符串给前端
            String jwt =JWTUtil.createToken(reqs);
            return jwt;
        }else{
            //抛出验证码异常
            throw new ExceptionConsole(ExceptionEnum.MEMBER_CODE_ERROR);
        }
    }
}
