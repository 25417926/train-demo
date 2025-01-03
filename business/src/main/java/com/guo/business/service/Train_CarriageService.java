package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.TypeEnum.TrainCarriageTypeEnum;
import com.guo.business.TypeEnum.TrainSeatTypeEnum;
import com.guo.business.domain.Train_Carriage;
import com.guo.business.domain.Train_CarriageExample;
import com.guo.business.mapper.Train_CarriageMapper;
import com.guo.business.req.Train_CarriageQueryReq;
import com.guo.business.req.Train_CarriageSaveReq;
import com.guo.business.resp.Train_CarriageQueryResp;
import com.guo.common.exception.ExceptionConsole;
import com.guo.common.exception.ExceptionEnum;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 列车车厢服务类，提供与列车车厢相关的业务操作
 */
@Service
public class Train_CarriageService {

    /**
     * 日志对象，用于记录日志信息
     */
    private static final Logger LOG = LoggerFactory.getLogger(Train_CarriageService.class);

    /**
     * 列车车厢数据访问对象，用于操作数据库中的列车车厢表
     */
    @Resource
    private Train_CarriageMapper train_CarriageMapper;

    /**
     * 保存列车车厢信息，包括插入和更新操作
     *
     * @param req 列车车厢保存请求对象，包含需要保存的列车车厢信息
     */
    public void save(Train_CarriageSaveReq req) {
        DateTime now = DateTime.now();

        // 根据座位类型获取列数，并计算总座位数
        req.setColCount(TrainSeatTypeEnum.getColsByType(req.getSeatType()).size());
        req.setSeatCount(req.getRowCount() * req.getColCount());
        Train_Carriage train_Carriage = BeanUtil.copyProperties(req, Train_Carriage.class);
        if (ObjectUtil.isEmpty(train_Carriage.getId())) {
            Train_Carriage CarriageDB = selectByUnique(train_Carriage.getTrainCode(),train_Carriage.getIndex());
            if(ObjectUtil.isNotEmpty(CarriageDB)){
                throw new ExceptionConsole(ExceptionEnum.CARRIAGE_EXIST);
            }
            // 如果是新记录，进行插入操作
            train_Carriage.setId(SnowUtil.getSnowflakeNextId());
            train_Carriage.setCreateTime(now);
            train_Carriage.setUpdateTime(now);
            train_CarriageMapper.insert(train_Carriage);
        } else {
            // 如果是已有记录，进行更新操作
            train_Carriage.setUpdateTime(now);
            train_CarriageMapper.updateByPrimaryKey(train_Carriage);
        }
    }

    /**
     * 查询列车车厢列表，支持分页和条件查询
     *
     * @param req 列车车厢查询请求对象，包含查询条件和分页信息
     * @return 返回查询结果，包括总行数、总页数和数据列表
     */
    public PageResp<Train_CarriageQueryResp> QueryList(Train_CarriageQueryReq req) {
        Train_CarriageExample train_CarriageExample = new Train_CarriageExample();
        train_CarriageExample.setOrderByClause("train_code asc,`index` asc");
        Train_CarriageExample.Criteria criteria = train_CarriageExample.createCriteria();
        if (ObjectUtil.isNotEmpty(req.getTrainCode())) {
            criteria.andTrainCodeEqualTo(req.getTrainCode());
        }
        LOG.info("查询页码：{}", req.getPage());
        LOG.info("每页条数：{}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<Train_Carriage> train_CarriageList = train_CarriageMapper.selectByExample(train_CarriageExample);

        PageInfo<Train_Carriage> pageInfo = new PageInfo<>(train_CarriageList);
        LOG.info("总行数：{}", pageInfo.getTotal());
        LOG.info("总页数：{}", pageInfo.getPages());

        // 将查询结果转换为响应对象，并设置座位类型描述
        List<Train_CarriageQueryResp> list = BeanUtil.copyToList(train_CarriageList, Train_CarriageQueryResp.class).stream().map(
                resp -> {
                    String type = TrainCarriageTypeEnum.fromCode(resp.getSeatType(), null).getDesc();
                    resp.setSeatType(type);
                    return resp;
                }
        ).collect(Collectors.toList());

        PageResp<Train_CarriageQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }

    /**
     * 根据列车编码查询列车车厢列表
     *
     * @param TrainCode 列车编码，用于查询对应的列车车厢
     * @return 返回查询到的列车车厢列表
     */
    public List<Train_Carriage> SelectByTrainCode(String TrainCode) {
        Train_CarriageExample train_CarriageExample = new Train_CarriageExample();
        train_CarriageExample.setOrderByClause("train_code asc,`index` asc");
        Train_CarriageExample.Criteria criteria = train_CarriageExample.createCriteria();

        criteria.andTrainCodeEqualTo(TrainCode);
        List<Train_Carriage> trainCarriages = train_CarriageMapper.selectByExample(train_CarriageExample);
        return trainCarriages;

    }
    private Train_Carriage selectByUnique(String trainCode ,Integer index) {
        Train_CarriageExample Train_CarriageExample = new Train_CarriageExample();
        Train_CarriageExample.Criteria criteria = Train_CarriageExample.createCriteria();
        criteria.andTrainCodeEqualTo(trainCode);
        criteria.andIndexEqualTo(index);
        List<Train_Carriage> list = train_CarriageMapper.selectByExample(Train_CarriageExample);
        if (CollUtil.isEmpty(list)) {
            return null;
        }else{
            return list.get(0);
        }
    }
    /**
     * 根据ID删除列车车厢信息
     *
     * @param id 列车车厢的ID，用于标识需要删除的记录
     */
    public void delete(Long id) {
        train_CarriageMapper.deleteByPrimaryKey(id);
    }

    public List<Train_Carriage> selectByTrainCode(String trainCode ){
        Train_CarriageExample Train_CarriageExample = new Train_CarriageExample();
        Train_CarriageExample.setOrderByClause("`index` asc");
        Train_CarriageExample.Criteria criteria = Train_CarriageExample.createCriteria();
        criteria.andTrainCodeEqualTo(trainCode);

//        if (CollUtil.isEmpty(list)) {
//            return null;
//        }else{
//            return list.get(0);
//        }
        return train_CarriageMapper.selectByExample(Train_CarriageExample);
    }
}
