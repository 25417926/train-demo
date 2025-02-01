package com.guo.business.service;


import cn.hutool.core.date.DateTime;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.fastjson.JSON;
import com.guo.business.TypeEnum.ConfirmOrderStatusEnum;
import com.guo.business.TypeEnum.RocketMqEnum;
import com.guo.business.domain.Confirm_Order;
import com.guo.business.mapper.Confirm_OrderMapper;
import com.guo.business.req.Confirm_OrderDoReq;
import com.guo.business.req.Confirm_OrderDto;
import com.guo.business.req.Confirm_OrderTicketReq;
import com.guo.common.context.LoginMemberContext;
import com.guo.common.exception.ExceptionConsole;
import com.guo.common.exception.ExceptionEnum;
import com.guo.common.util.SnowUtil;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * 确认订单服务类
 */
@Service
public class Before_Confirm_OrderService {

    private static final Logger LOG = LoggerFactory.getLogger(Before_Confirm_OrderService.class);

    @Resource
    private Sk_TokenService sk_tokenService;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private Confirm_OrderMapper confirm_OrderMapper;
    @SentinelResource(value = "BeforeDoConfirm", blockHandler = "BeforeDoConfirmBlock")
    public Long BeforeDoConfirm(Confirm_OrderDoReq req) {
        // 校验令牌
        req.setMemberId(LoginMemberContext.getId());
        boolean validSkToken = sk_tokenService.validToken(req.getDate(), req.getTrainCode(), req.getMemberId());
        if (validSkToken) {
            LOG.info("令牌校验通过");
        } else {
            LOG.info("令牌校验失败");
            throw new ExceptionConsole(ExceptionEnum.CONFIRM_ORDER_SK_TOKEN_FAIL);
        }
        //保存确认订单表
        DateTime now = DateTime.now();
        Confirm_Order confirm_Order = new Confirm_Order();
        List<Confirm_OrderTicketReq> tickets = req.getTickets();

        confirm_Order.setId(SnowUtil.getSnowflakeNextId());
        confirm_Order.setMemberId(req.getMemberId());
        Date date = req.getDate();
        String trainCode = req.getTrainCode();
        String start = req.getStart();
        String end = req.getEnd();
        confirm_Order.setDate(date);
        confirm_Order.setTrainCode(trainCode);
        confirm_Order.setStart(start);
        confirm_Order.setEnd(end);
        confirm_Order.setDailyTrainTicketId(req.getDailyTrainTicketId());
        confirm_Order.setStatus(ConfirmOrderStatusEnum.INIT.getCode());
        confirm_Order.setTickets(JSON.toJSONString(tickets));
        confirm_Order.setCreateTime(now);
        confirm_Order.setUpdateTime(now);
        confirm_OrderMapper.insert(confirm_Order);
        //拿取日志流水号
        String LogId=MDC.get("LOG_ID");
        Confirm_OrderDto dto =new Confirm_OrderDto();
        dto.setDate(date);
        dto.setTrainCode(trainCode);
        dto.setLogId(LogId);
        //TODO: 发送mq，等待购票
        String JsonString = JSON.toJSONString(dto);
        LOG.info("发送mq开始");
        LOG.info("发送mq内容:{}", JsonString);
        rocketMQTemplate.convertAndSend(RocketMqEnum.ORDER_CONFIRM.getCode(), JsonString);
        LOG.info("发送mq完成,等待出票");

        return confirm_Order.getId();
    }

    public void BeforeDoConfirmBlock(Confirm_OrderDoReq req, BlockException ex) {
        LOG.info("BeforeDoConfirmBlock");
        throw new ExceptionConsole(ExceptionEnum.CONFIRM_ORDER_LOCK_FAIL);
    }

}