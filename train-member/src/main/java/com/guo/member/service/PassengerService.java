package com.guo.member.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.common.context.LoginMemberContext;
import com.guo.common.resp.PageResp;
import com.guo.common.resp.PassengerBriefResp;
import com.guo.common.util.SnowUtil;
import com.guo.member.TypeEnum.PassengerEnum;
import com.guo.member.domain.Passenger;
import com.guo.member.domain.PassengerExample;
import com.guo.member.mapper.PassengerMapper;
import com.guo.member.req.PassengerQueryReq;
import com.guo.member.req.PassengerSaveReq;
import com.guo.member.resp.PassengerQueryResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/21
 */
@Service
public class PassengerService {
    Logger Log = LoggerFactory.getLogger(PassengerService.class);
    @Resource
    private PassengerMapper passengerMapper;

    public void save(PassengerSaveReq req) {
        DateTime date = DateTime.now();
        Passenger passenger = BeanUtil.copyProperties(req, Passenger.class);
        if(ObjectUtil.isNull(passenger.getId())) {
            passenger.setId(SnowUtil.getSnowflakeNextId());
            passenger.setMemberId(LoginMemberContext.getId());
            passenger.setCreateTime(date);
            passenger.setUpdateTime(date);
            passengerMapper.insert(passenger);
        }else{
            passenger.setType(PassengerEnum.fromCode(null,passenger.getType()).getCode());
            passenger.setUpdateTime(date);
            passengerMapper.updateByPrimaryKeySelective(passenger);
        }
    }

    /**
     * 查询乘客列表
     * 根据查询请求中的条件，从数据库中查询符合条件的乘客信息，并返回查询结果
     *
     * @param req 乘客查询请求对象，包含查询条件和分页信息
     * @return 返回一个分页响应对象，包含乘客信息列表和总记录数
     */
    public PageResp<PassengerQueryResp> QueryList(PassengerQueryReq req) {
        // 创建乘客示例对象，用于构建查询条件
        PassengerExample example = new PassengerExample();
        // 设置排序规则，按照ID降序排序
        example.setOrderByClause("id desc");
        // 获取查询条件对象
        PassengerExample.Criteria criteria = example.createCriteria();

        // 如果请求对象中的成员ID不为空，则添加成员ID查询条件
        if (ObjectUtil.isNotEmpty(req.getMemberId())) {
            criteria.andMemberIdEqualTo(LoginMemberContext.getId());
        }

        // 启动分页查询
        PageHelper.startPage(req.getPage(), req.getSize());

        // 根据构建的查询条件，从数据库中查询符合条件的乘客列表
        List<Passenger> passengerList = passengerMapper.selectByExample(example);

        // 创建分页信息对象，用于存储查询结果的总记录数和每页记录数
        PageInfo<Passenger> pageInfo = new PageInfo<>(passengerList);

        // 记录日志，输出总行数和总页数
        Log.info("总行数：{}", pageInfo.getTotal());
        Log.info("总页数：{}", pageInfo.getPages());

        // 将查询结果转换为乘客查询响应对象列表
        List<PassengerQueryResp> respList = BeanUtil.copyToList(passengerList, PassengerQueryResp.class).stream().map(
            resp->{
                resp.setType(PassengerEnum.fromCode(resp.getType(),null).getDesc());
                return resp;
            }
        ).collect(Collectors.toList());

        // 创建分页响应对象，设置总记录数和乘客查询响应对象列表
        PageResp<PassengerQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(respList);

        return pageResp;
    }
    public void delete(Long id) {
        passengerMapper.deleteByPrimaryKey(id);
    }

    /**
     * 内部 Feign：按会员查询乘客简要信息，供 business 校验候补申请归属。
     */
    public List<PassengerBriefResp> queryByMember(Long memberId) {
        PassengerExample example = new PassengerExample();
        example.createCriteria().andMemberIdEqualTo(memberId);
        return BeanUtil.copyToList(passengerMapper.selectByExample(example), PassengerBriefResp.class);
    }

    public List<PassengerQueryResp> queryMine() {
        PassengerExample example = new PassengerExample();
        example.setOrderByClause("name asc");
        example.createCriteria().andMemberIdEqualTo(LoginMemberContext.getId());
        List<Passenger> passengerList = passengerMapper.selectByExample(example);
        return BeanUtil.copyToList(passengerList, PassengerQueryResp.class).stream().map(
                resp->{
                    resp.setType(PassengerEnum.fromCode(resp.getType(),null).getDesc());
                    return resp;
                }
        ).collect(Collectors.toList());
    }
}
