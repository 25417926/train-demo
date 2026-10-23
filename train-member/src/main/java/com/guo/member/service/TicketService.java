package com.guo.member.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.common.req.MemberTicketReq;
import com.guo.common.req.TicketIssueReq;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import com.guo.member.domain.Ticket;
import com.guo.member.domain.TicketExample;
import com.guo.member.mapper.TicketMapper;
import com.guo.member.req.TicketQueryReq;
import com.guo.member.resp.TicketQueryResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class TicketService {

    private static final Logger LOG = LoggerFactory.getLogger(TicketService.class);

    @Resource
    private TicketMapper ticketMapper;

    public void save(MemberTicketReq req){
        saveQuietly(req, DateTime.now());
    }

    /**
     * I10 按订单批量出票：唯一键 (order_id, passenger_id) 保证事件重投不会重复发票。
     *
     * @return 实际新增的车票数
     */
    public int issue(TicketIssueReq req) {
        if (req == null || req.getOrderId() == null || CollUtil.isEmpty(req.getTickets())) {
            throw new IllegalArgumentException("出票请求缺少订单id或车票明细");
        }
        DateTime now = DateTime.now();
        int inserted = 0;
        for (MemberTicketReq ticketReq : req.getTickets()) {
            ticketReq.setOrderId(req.getOrderId());
            inserted += saveQuietly(ticketReq, now);
        }
        LOG.debug("订单[{}]出票完成：新增 {} 张，已存在 {} 张",
                req.getOrderId(), inserted, req.getTickets().size() - inserted);
        return inserted;
    }

    private int saveQuietly(MemberTicketReq req, DateTime now) {
        Ticket ticket = BeanUtil.copyProperties(req, Ticket.class);
        ticket.setId(SnowUtil.getSnowflakeNextId());
        ticket.setCreateTime(now);
        ticket.setUpdateTime(now);
        return ticketMapper.insertIgnore(ticket);
    }

    public PageResp<TicketQueryResp> QueryList(TicketQueryReq req) {
        TicketExample ticketExample = new TicketExample();
        ticketExample.setOrderByClause("id desc");
        TicketExample.Criteria criteria = ticketExample.createCriteria();
        if(ObjectUtil.isNotNull(req.getMemberId())){
            criteria.andMemberIdEqualTo(req.getMemberId());
        }
        LOG.info("查询页码：{}", req.getPage());
        LOG.info("每页条数：{}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<Ticket> ticketList = ticketMapper.selectByExample(ticketExample);

        PageInfo<Ticket> pageInfo = new PageInfo<>(ticketList);
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        List<TicketQueryResp> list = BeanUtil.copyToList(ticketList, TicketQueryResp.class);

        PageResp<TicketQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }

    public void delete(Long id) {
        ticketMapper.deleteByPrimaryKey(id);
    }

    /**
     * 按订单id查询车票，供退票/退改签场景精确反查与删除
     */
    public List<Ticket> selectByOrderId(Long orderId) {
        return ticketMapper.selectByOrderId(orderId);
    }

    /**
     * I10 按订单精确删除本单车票，不误删其他订单
     *
     * @return 删除的车票数
     */
    public int deleteByOrderId(Long orderId) {
        int deleted = ticketMapper.deleteByOrderId(orderId);
        LOG.info("订单[{}]删除车票 {} 张", orderId, deleted);
        return deleted;
    }
}
