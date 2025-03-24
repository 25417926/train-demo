package com.guo.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.nacos.shaded.com.google.common.collect.Lists;
import com.guo.auth.domain.Member;
import com.guo.auth.domain.MemberExample;
import com.guo.auth.domain.UserRoleRel;
import com.guo.auth.mapper.MemberMapper;
import com.guo.auth.mapper.UserRoleRelMapper;
import com.guo.auth.req.UserReq;
import com.guo.auth.resp.UserResp;
import com.guo.auth.utils.PwdAESCryUtil;
import com.guo.common.exception.ExceptionConsole;
import com.guo.common.exception.ExceptionEnum;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Service
public class AuthService {
    private final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Resource
    private RedisTemplate<Object, Object> redisTemplate;
    @Resource
    private MemberMapper memberMapper;
    @Resource
    private UserRoleRelMapper userRoleRelMapper;
    @Resource
    private IEmailServiceImpl emailService;

    public UserResp login(UserReq user) {
        MemberExample example = new MemberExample();
        //这里因为之前的业务是使用手机号，后续更新可能会改回手机号，也可以双切
        example.createCriteria().andEmailEqualTo(user.getEmail());
        List<Member> list = memberMapper.selectByExample(example);

        if (ObjectUtil.isNotNull(user.getCode()) && !redisTemplate.opsForValue().get(user.getEmail()).equals(user.getCode())) {
            throw new ExceptionConsole(ExceptionEnum.MEMBER_CODE_ERROR);
        }
        redisTemplate.delete(user.getEmail());
        if (CollUtil.isEmpty(list)) {
            Member member = register(user);
            UserResp userResp = new UserResp();
            userResp.setId(member.getId());
            userResp.setSaTokenInfo(StpUtil.getTokenInfo());
            return userResp;
        }
        Member member = list.get(0);
        if (StrUtil.isNotBlank(user.getPassword())) {
            log.info("用户使用邮箱密码登录");
            //这里使用redis存储邮箱
            if (!PwdAESCryUtil.decrypt(member.getPassword()).equals(user.getPassword())) {
                throw new ExceptionConsole(ExceptionEnum.MEMBER_PASSWORD_ERROR);
            }

            UserResp userResp = new UserResp();
            userResp.setId(member.getId());
            userResp.setSaTokenInfo(StpUtil.getTokenInfo());
            return userResp;
        }
        if (StrUtil.isNotBlank(user.getCode())) {
            log.info("用户使用短信登录");
            //这里使用redis存储邮箱号，后续可以修改为jwt
            StpUtil.login(member.getId());
            UserResp userResp = new UserResp();
            userResp.setId(member.getId());
            userResp.setSaTokenInfo(StpUtil.getTokenInfo());
            return userResp;
        }


        log.info("用户账号或密码错误");
        return null;
    }

    public String SendCode(String email) throws Exception {
        if (StrUtil.isNotBlank(email)) {
            log.info("发送验证码");
            String code = RandomUtil.randomString(4);
            if (redisTemplate.hasKey(email)) {
                redisTemplate.delete(email);
            }
            redisTemplate.opsForValue().set(email, code, 60, TimeUnit.SECONDS);
            emailService.send("12306", "254179262@qq.com", email, "验证码", "本次的验证码为:" + code, false, null, null, null);
            return code;
        }
        return "请求失败";
    }

    @Transactional(rollbackFor = Exception.class)
    public Member register(UserReq user) {
        if (StrUtil.isNotBlank(user.getCode())) {
            log.info("用户使用短信注册");
            MemberExample example = new MemberExample();
            //这里因为之前的业务是使用手机号，后续更新可能会改回手机号，也可以双切
            example.createCriteria().andMobileEqualTo(user.getEmail());
            List<Member> list = memberMapper.selectByExample(example);
            if (CollUtil.isNotEmpty(list)) {
                throw new ExceptionConsole(ExceptionEnum.MEMBER_EXIST);
            }
            //添加进用户数据库
            Member member = new Member();
            BeanUtil.copyProperties(user, member);
            member.setId(SnowUtil.getSnowflakeNextId());
            member.setMobile(RandomUtil.randomNumbers(11));
            member.setPassword(PwdAESCryUtil.encrypt("123456"));

            memberMapper.insert(member);

            //创建权限关联，给予用户权限
            Date now = new Date();
            UserRoleRel userRoleRel = new UserRoleRel();
            userRoleRel.setRoleId(2L);
            userRoleRel.setUserId(member.getId());
            userRoleRel.setCreateTime(now);
            userRoleRel.setUpdateTime(now);
            userRoleRel.setIsDeleted(false);
            userRoleRelMapper.insert(userRoleRel);
            List<Long> roles = Lists.newArrayList();
            roles.add(userRoleRel.getRoleId());
            //将用户的权限id存到redis中，方便后续使用
            redisTemplate.opsForValue().set(member.getId(), JSONUtil.toJsonStr(roles));
            return member;
        }
        return null;
    }
}
