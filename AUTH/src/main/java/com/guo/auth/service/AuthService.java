package com.guo.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.CircleCaptcha;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.captcha.ShearCaptcha;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.nacos.shaded.com.google.common.collect.Lists;
import com.guo.auth.constant.RedisKeyConstants;
import com.guo.auth.domain.Member;
import com.guo.auth.domain.MemberExample;
import com.guo.auth.domain.UserRoleRel;
import com.guo.auth.mapper.MemberMapper;
import com.guo.auth.mapper.UserRoleRelMapper;
import com.guo.auth.mapper.UserRoleRelationMapper;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Service
public class AuthService {
    private final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private MemberMapper memberMapper;
    @Resource
    private UserRoleRelMapper userRoleRelMapper;
    @Resource
    private IEmailServiceImpl emailService;
    @Resource
    private UserRoleRelationMapper userRoleRelationMapper;

    public UserResp login(UserReq user) {
        MemberExample example = new MemberExample();
        //这里因为之前的业务是使用手机号，后续更新可能会改回手机号，也可以双切
        example.createCriteria().andEmailEqualTo(user.getEmail());
        List<Member> list = memberMapper.selectByExample(example);

        boolean useCode = StrUtil.isNotBlank(user.getCode());
        boolean usePwd  = StrUtil.isNotBlank(user.getPassword());
        boolean fromAdmin = "admin".equalsIgnoreCase(user.getSource());

        // 验证码登录：先校验 redis 里的验证码，校验通过后再删
        if (useCode) {
            Object cacheCode = redisTemplate.opsForValue().get(user.getEmail());
            if (cacheCode == null) {
                throw new ExceptionConsole(ExceptionEnum.MEMBER_CODE_EXPIRED);
            }
            if (!user.getCode().equals(String.valueOf(cacheCode))) {
                throw new ExceptionConsole(ExceptionEnum.MEMBER_CODE_ERROR);
            }
            redisTemplate.delete(user.getEmail());
        }

        Member member;
        if (CollUtil.isEmpty(list)) {
            // admin 端：禁止自动注册，账号不存在直接拒绝
            if (fromAdmin) {
                throw new ExceptionConsole(ExceptionEnum.MEMBER_NOT_EXIST);
            }
            // 会员端：仅当走验证码流程时允许自动注册
            if (!useCode) {
                throw new ExceptionConsole(ExceptionEnum.MEMBER_NOT_EXIST);
            }
            member = register(user);
        } else {
            member = list.get(0);
        }

        if (usePwd) {
            log.info("用户使用邮箱密码登录");
            if (!PwdAESCryUtil.decrypt(member.getPassword()).equals(user.getPassword())) {
                throw new ExceptionConsole(ExceptionEnum.MEMBER_PASSWORD_ERROR);
            }
        }
        if (useCode) {
            log.info("用户使用短信登录");
        }

        // 登录成功后写入"用户角色"缓存
        List<String> roles = userRoleRelationMapper.selectRoleByUserId(member.getId());

        // admin 端：要求账户具备 admin 角色，否则拒绝
        if (fromAdmin && (CollUtil.isEmpty(roles) || !roles.contains("admin"))) {
            log.warn("非 admin 账户尝试登录管理后台，userId={}, roles={}", member.getId(), roles);
            throw new ExceptionConsole(ExceptionEnum.ADMIN_ONLY);
        }

        StpUtil.login(member.getId());
        redisTemplate.opsForValue().set(RedisKeyConstants.buildUserRolesKey(member.getId()), roles);
        UserResp userResp = new UserResp();
        userResp.setId(String.valueOf(member.getId()));
        userResp.setSaToken(StpUtil.getTokenValue());
        return userResp;
    }

    public byte[] SendCode(String email) throws Exception {
        if (StrUtil.isNotBlank(email)) {
            log.info("发送验证码");
            // 生成验证码 线段干扰
//            LineCaptcha captcha = new LineCaptcha(100, 30);
            // 生成验证码 扭曲干扰
//            ShearCaptcha captcha = CaptchaUtil.createShearCaptcha(100, 30);
            // 生成验证码 圆圈干扰
            CircleCaptcha captcha = CaptchaUtil.createCircleCaptcha(100, 30);
            // 获取验证码的文本信息
            String captchaCode = captcha.getCode();
            //创建字节数组流
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try{
                // 将验证码图片写入字节数组输出流
                captcha.write(bos);
            }catch (Exception e){
                throw new ExceptionConsole(ExceptionEnum.MEMBER_CODE_EXCEOTION);
            }
            if (redisTemplate.hasKey(email)) {
                redisTemplate.delete(email);
            }
            redisTemplate.opsForValue().set(email, captchaCode, 5, TimeUnit.MINUTES);
            emailService.send("12306", "254179262@qq.com", email, "验证码", "本次的验证码为:" + captchaCode, false, null, null, null);
            return bos.toByteArray();
        }
        return null;
    }


    public Member register(UserReq user) {
            if(StrUtil.isBlank(user.getCode()) && StrUtil.isNotBlank(user.getPassword())){
                throw new ExceptionConsole(ExceptionEnum.MEMBER_NOT_EXIST);
            }
            log.info("用户使用短信注册");
            MemberExample example = new MemberExample();
            // 注册唯一性按邮箱判定（之前误写成了 mobile，导致同邮箱可重复注册并写入随机手机号）
            example.createCriteria().andEmailEqualTo(user.getEmail());
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
            redisTemplate.opsForValue().set(String.valueOf(member.getId()), JSONUtil.toJsonStr(roles));
            return member;
    }
}
