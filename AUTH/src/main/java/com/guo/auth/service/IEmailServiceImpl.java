package com.guo.auth.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.mail.internet.InternetAddress;
import java.io.File;
import java.util.Date;
import java.util.List;

/**
 * 电子邮件服务实现类
 */
@Service
public class IEmailServiceImpl{

    @Resource
    private JavaMailSender javaMailSender;

    /**
     * 发送电子邮件
     *
     * @param name    发件人名称
     * @param form    发件人邮箱
     * @param to      收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     * @param isHtml  内容是否为HTML
     * @param cc      抄送人邮箱
     * @param bcc     密送人邮箱
     * @param files   附件文件列表
     * @throws Exception 当邮件发送过程中出现异常时抛出
     */
    public void send(String name, String form, String to, String subject,
                     String content, Boolean isHtml, String cc, String bcc,
                     List<File> files) throws Exception {

        // 增强空值校验（修复空指针风险）
        if (StrUtil.hasBlank(form, to, subject, content)) {
            throw new Exception("必填参数存在空值");
        }

        try {
            // 显式指定UTF-8编码（解决字符编码异常）
            MimeMessageHelper messageHelper = new MimeMessageHelper(
                    javaMailSender.createMimeMessage(), true, "UTF-8");

            // 发件人设置
            messageHelper.setFrom(new InternetAddress(name + "<" + form + ">"));

            // 收件人处理（支持多地址）
            messageHelper.setTo(to.split(","));
            messageHelper.setSubject(subject);
            messageHelper.setText(content, isHtml);

            // 修正密送设置（原错误使用setCc）
            if (!StrUtil.isEmpty(cc)) {
                messageHelper.setCc(cc.split(","));
            }
            if (!StrUtil.isEmpty(bcc)) {
                messageHelper.setBcc(bcc.split(",")); // 修正点
            }

            // 附件处理增强（添加存在性检查）
            if (CollUtil.isNotEmpty(files)) {
                for (File file : files) {
                    if (!file.exists() || !file.canRead()) {
                        throw new Exception("附件不可访问: " + file.getPath());
                    }
                    messageHelper.addAttachment(file.getName(), file);
                }
            }

            messageHelper.setSentDate(new Date());
            javaMailSender.send(messageHelper.getMimeMessage());

        } catch (MailAuthenticationException ex) {
            throw new Exception("SMTP认证失败", ex); // 细化异常类型
        } catch (MailSendException ex) {
            throw new Exception("邮件传输错误", ex); // 细化异常类型
        } catch (Exception e) {
            throw new Exception("邮件发送失败", e);
        }
    }
}