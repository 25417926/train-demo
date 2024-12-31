package com.guo.member.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.common.context.LoginMemberContext;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import com.guo.member.TypeEnum.${Domain}Enum;
import com.guo.member.domain.${Domain};
import com.guo.member.domain.${Domain}Example;
import com.guo.member.mapper.${Domain}Mapper;
import com.guo.member.req.${Domain}QueryReq;
import com.guo.member.req.${Domain}SaveReq;
import com.guo.member.resp.${Domain}QueryResp;
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
public class ${Domain}Service {
    Logger Log = LoggerFactory.getLogger(${Domain}Service.class);
    @Resource
    private ${Domain}Mapper ${domain}Mapper;

    public void save(${Domain}SaveReq req) {
        DateTime date = DateTime.now();
        ${Domain} ${domain} = BeanUtil.copyProperties(req, ${Domain}.class);
        if(ObjectUtil.isNull(${domain}.getId())) {
            ${domain}.setId(SnowUtil.getSnowflakeNextId());
            ${domain}.setMemberId(LoginMemberContext.getId());
            ${domain}.setCreateTime(date);
            ${domain}.setUpdateTime(date);
            ${domain}Mapper.insert(${domain});
        }else{
            ${domain}.setType(${Domain}Enum.fromCode(null,${domain}.getType()).getCode());
            ${domain}.setUpdateTime(date);
            ${domain}Mapper.updateByPrimaryKeySelective(${domain});
        }
    }

    /**
     * 查询乘客列表
     * 根据查询请求中的条件，从数据库中查询符合条件的乘客信息，并返回查询结果
     *
     * @param req 乘客查询请求对象，包含查询条件和分页信息
     * @return 返回一个分页响应对象，包含乘客信息列表和总记录数
     */
    public PageResp<${Domain}QueryResp> QueryList(${Domain}QueryReq req) {
        // 创建乘客示例对象，用于构建查询条件
        ${Domain}Example example = new ${Domain}Example();
        // 设置排序规则，按照ID降序排序
        example.setOrderByClause("id desc");
        // 获取查询条件对象
        ${Domain}Example.Criteria criteria = example.createCriteria();

        // 如果请求对象中的成员ID不为空，则添加成员ID查询条件
        if (ObjectUtil.isNotEmpty(req.getMemberId())) {
            criteria.andMemberIdEqualTo(LoginMemberContext.getId());
        }

        // 启动分页查询
        PageHelper.startPage(req.getPage(), req.getSize());

        // 根据构建的查询条件，从数据库中查询符合条件的乘客列表
        List<${Domain}> ${domain}List = ${domain}Mapper.selectByExample(example);

        // 创建分页信息对象，用于存储查询结果的总记录数和每页记录数
        PageInfo<${Domain}> pageInfo = new PageInfo<>(${domain}List);

        // 记录日志，输出总行数和总页数
        Log.info("总行数：{}", pageInfo.getTotal());
        Log.info("总页数：{}", pageInfo.getPages());

        // 将查询结果转换为乘客查询响应对象列表
        List<${Domain}QueryResp> respList = BeanUtil.copyToList(${domain}List, ${Domain}QueryResp.class).stream().map(
            resp->{
                resp.setType(${Domain}Enum.fromCode(resp.getType(),null).getDesc());
                return resp;
            }
        ).collect(Collectors.toList());

        // 创建分页响应对象，设置总记录数和乘客查询响应对象列表
        PageResp<${Domain}QueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(respList);

        return pageResp;
    }
    public void delete(Long id) {
        ${domain}Mapper.deleteByPrimaryKey(id);
    }
}
