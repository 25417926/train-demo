package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.domain.station;
import com.guo.business.domain.stationExample;
import com.guo.business.mapper.stationMapper;
import com.guo.business.req.stationQueryReq;
import com.guo.business.req.stationSaveReq;
import com.guo.business.resp.stationQueryResp;
import com.guo.common.exception.ExceptionConsole;
import com.guo.common.exception.ExceptionEnum;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 提供车站信息的业务服务
 */
@Service
public class stationService {

    // 日志对象，用于记录日志信息
    private static final Logger LOG = LoggerFactory.getLogger(stationService.class);

    // stationMapper对象，用于执行车站信息相关的数据库操作
    @Resource
    private stationMapper stationMapper;

    /**
     * 保存车站信息
     *
     * @param req 车站保存请求对象，包含要保存的车站信息
     */
    public void save(stationSaveReq req) {
        // 获取当前时间
        DateTime now = DateTime.now();
        // 将请求对象转换为车站对象
        station station = BeanUtil.copyProperties(req, station.class);
        // 如果车站ID为空，则为新车站，执行插入操作
        if (ObjectUtil.isEmpty(station.getId())) {
            station stationDB = selectByUnique(station.getName());
            if(ObjectUtil.isNotEmpty(stationDB)){
                throw new ExceptionConsole(ExceptionEnum.STATION_EXIST);
            }
            // 生成新车站的ID
            station.setId(SnowUtil.getSnowflakeNextId());
            // 设置创建时间和更新时间为当前时间
            station.setCreateTime(now);
            station.setUpdateTime(now);
            // 执行插入操作
            stationMapper.insert(station);

        } else {
            // 如果车站ID不为空，则为更新车站，只设置更新时间为当前时间
            station.setUpdateTime(now);
            // 执行更新操作
            stationMapper.updateByPrimaryKey(station);
        }
    }

    private station selectByUnique(String name) {
        stationExample stationExample = new stationExample();
        stationExample.Criteria criteria = stationExample.createCriteria();
        criteria.andNameEqualTo(name);
        List<station> list = stationMapper.selectByExample(stationExample);
        if (CollUtil.isEmpty(list)) {
            return null;
        }else{
            return list.get(0);
        }
    }

    /**
     * 查询车站列表
     *
     * @param req 车站查询请求对象，包含查询条件、页码和每页条数
     * @return 返回包含车站查询响应对象的页面响应
     */
    public PageResp<stationQueryResp> queryList(stationQueryReq req) {
        // 创建车站查询条件对象，并设置排序条件
        stationExample stationExample = new stationExample();
        stationExample.setOrderByClause("id desc");
        stationExample.Criteria criteria = stationExample.createCriteria();

        // 记录查询页码和每页条数
        LOG.info("查询页码：{}", req.getPage());
        LOG.info("每页条数：{}", req.getSize());
        // 启用分页查询
        PageHelper.startPage(req.getPage(), req.getSize());
        // 执行查询操作
        List<station> stationList = stationMapper.selectByExample(stationExample);

        // 创建页面信息对象，用于获取总行数和总页数
        PageInfo<station> pageInfo = new PageInfo<>(stationList);
        // 记录总行数和总页数
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        // 将查询结果转换为车站查询响应对象列表
        List<stationQueryResp> list = BeanUtil.copyToList(stationList, stationQueryResp.class);

        // 创建页面响应对象，设置总行数和查询结果列表
        PageResp<stationQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        // 返回页面响应对象
        return pageResp;
    }

    /**
     * 查询车站列表
     *
     * @return 返回包含车站查询响应对象的页面响应
     */
    public List<stationQueryResp> queryAllList() {
        // 创建车站查询条件对象，并设置排序条件
        stationExample stationExample = new stationExample();
        stationExample.setOrderByClause("name_pinyin desc");
        // 执行查询操作
        List<station> stationList = stationMapper.selectByExample(stationExample);

        List<stationQueryResp> list = BeanUtil.copyToList(stationList, stationQueryResp.class);

        // 返回页面响应对象
        return list;
    }

    /**
     * 删除车站信息
     *
     * @param id 要删除的车站的ID
     */
    public void delete(Long id) {
        // 执行删除操作
        stationMapper.deleteByPrimaryKey(id);
    }
}
