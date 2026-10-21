package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.TypeEnum.TrainTypeEnum;
import com.guo.business.domain.Daily_Train;
import com.guo.business.domain.Daily_TrainExample;
import com.guo.business.domain.train;
import com.guo.business.mapper.Daily_TrainMapper;
import com.guo.business.req.Daily_TrainQueryReq;
import com.guo.business.req.Daily_TrainSaveReq;
import com.guo.business.resp.Daily_TrainQueryResp;
import com.guo.common.resp.PageResp;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class Daily_TrainService {

    private static final Logger LOG = LoggerFactory.getLogger(Daily_TrainService.class);

    @Resource
    private Daily_TrainMapper daily_TrainMapper;
    @Resource
    private trainService trainService;
    @Resource
    private train_seatService train_seatService;
    @Resource
    private Daily_Train_StationService dailyTrainStationService;
    @Resource
    private Daily_Train_CarriageService dailyTrainCarriageService;
    @Resource
    private Daily_Train_SeatService dailyTrainSeatService;
    @Resource
    private Daily_Train_TicketService dailyTrainTicketService;
    @Resource
    private Sk_TokenService skTokenService;

    @Transactional
    public void save(Daily_TrainSaveReq req) {
        DateTime now = DateTime.now();
        Daily_Train dailyTrain = BeanUtil.copyProperties(req, Daily_Train.class);
        String type = TrainTypeEnum.fromCode(null, req.getType()).getCode();

        if (ObjectUtil.isNull(dailyTrain.getId())) {
            dailyTrain.setId(SnowUtil.getSnowflakeNextId());
            dailyTrain.setType(type);
            dailyTrain.setCreateTime(now);
            dailyTrain.setUpdateTime(now);
            daily_TrainMapper.insert(dailyTrain);
        } else {
            dailyTrain.setType(type);
            dailyTrain.setUpdateTime(now);
            daily_TrainMapper.updateByPrimaryKey(dailyTrain);
        }

        generateDailyAssociatedData(dailyTrain);
    }

    public PageResp<Daily_TrainQueryResp> QueryList(Daily_TrainQueryReq req) {
        Daily_TrainExample dailyTrainExample = new Daily_TrainExample();
        dailyTrainExample.setOrderByClause("date desc ,code asc");
        Daily_TrainExample.Criteria criteria = dailyTrainExample.createCriteria();
        if (ObjectUtil.isNotNull(req.getDate())) {
            criteria.andDateEqualTo(req.getDate());
        }
        if (ObjectUtil.isNotEmpty(req.getCode())) {
            criteria.andCodeEqualTo(req.getCode());
        }
        LOG.info("query page: {}", req.getPage());
        LOG.info("query size: {}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<Daily_Train> dailyTrainList = daily_TrainMapper.selectByExample(dailyTrainExample);

        PageInfo<Daily_Train> pageInfo = new PageInfo<>(dailyTrainList);
        LOG.info("query total: {}", pageInfo.getTotal());
        LOG.info("query pages: {}", pageInfo.getPages());

        List<Daily_TrainQueryResp> list = BeanUtil.copyToList(dailyTrainList, Daily_TrainQueryResp.class)
                .stream()
                .peek(resp -> resp.setType(TrainTypeEnum.fromCode(resp.getType(), null).getDesc()))
                .collect(Collectors.toList());

        PageResp<Daily_TrainQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }

    public void delete(Long id) {
        daily_TrainMapper.deleteByPrimaryKey(id);
    }

    public void gen_daily(Date date) {
        List<train> trainList = trainService.selectAll();
        if (CollUtil.isEmpty(trainList)) {
            LOG.error("No base train data, skip daily generation");
            return;
        }
        for (train train : trainList) {
            gen_dailyTrain(date, train);
        }
    }

    @Transactional
    public void gen_dailyTrain(Date date, train train) {
        DateTime now = DateTime.now();
        Daily_TrainExample dailyTrainExample = new Daily_TrainExample();
        dailyTrainExample.createCriteria()
                .andDateEqualTo(date)
                .andCodeEqualTo(train.getCode());

        daily_TrainMapper.deleteByExample(dailyTrainExample);

        Daily_Train dailyTrain = BeanUtil.copyProperties(train, Daily_Train.class);
        dailyTrain.setId(SnowUtil.getSnowflakeNextId());
        dailyTrain.setCreateTime(now);
        dailyTrain.setUpdateTime(now);
        dailyTrain.setDate(date);
        daily_TrainMapper.insert(dailyTrain);

        generateDailyAssociatedData(dailyTrain);
    }

    private void generateDailyAssociatedData(Daily_Train dailyTrain) {
        Date date = dailyTrain.getDate();
        String code = dailyTrain.getCode();

        // Refresh base seats first so daily data always matches latest carriage setup.
        train_seatService.genSeat(code);
        dailyTrainStationService.genDaily(date, code);
        dailyTrainCarriageService.genDaily(date, code);
        dailyTrainSeatService.genDaily(date, code);
        dailyTrainTicketService.genDaily(dailyTrain, date, code);
        skTokenService.genDaily(date, code);
    }
}
