package com.guo.business.service;

import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guo.business.TypeEnum.TrainCarriageTypeEnum;
import com.guo.business.domain.Daily_Train_Ticket;
import com.guo.business.domain.Waitlist_Application;
import com.guo.business.feign.MemberFeign;
import com.guo.business.inventory.InventoryKey;
import com.guo.business.mapper.Waitlist_ApplicationMapper;
import com.guo.business.req.WaitlistApplyReq;
import com.guo.business.resp.WaitlistApplyResp;
import com.guo.business.resp.WaitlistResp;
import com.guo.business.waitlist.WaitlistKey;
import com.guo.common.exception.ExceptionConsole;
import com.guo.common.exception.ExceptionEnum;
import com.guo.common.req.PageReq;
import com.guo.common.resp.CommonResp;
import com.guo.common.resp.PageResp;
import com.guo.common.resp.PassengerBriefResp;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * I15 候补申请 / 取消 / 我的候补。
 *
 * <p>DB {@code waitlist_application} 是事实源，Redis 主队列只是顺序索引：
 * 严格取号由 {@code INCR waitlist:seq:{key}} 保证；重复申请由 {@code uk_request_id}
 * 与 {@code uk_active} 双重兜底；取消置 C 并清空 active_key，可重新申请排到队尾。</p>
 */
@Service
public class WaitlistService {

    private static final Logger LOG = LoggerFactory.getLogger(WaitlistService.class);

    private static final String STATUS_WAITING = "W";
    private static final String STATUS_CANCELLED = "C";
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final String REQUEST_ID_PREFIX = "WLREQ";

    private final Waitlist_ApplicationMapper waitlistMapper;
    private final Daily_Train_TicketService dailyTrainTicketService;
    private final MemberFeign memberFeign;
    private final StringRedisTemplate redis;
    private final TransactionTemplate transactionTemplate;
    private final DefaultRedisScript<Long> enqueueScript;
    private final DefaultRedisScript<Long> dequeueScript;

    public WaitlistService(Waitlist_ApplicationMapper waitlistMapper,
                           Daily_Train_TicketService dailyTrainTicketService,
                           MemberFeign memberFeign,
                           StringRedisTemplate redis,
                           PlatformTransactionManager transactionManager) {
        this.waitlistMapper = waitlistMapper;
        this.dailyTrainTicketService = dailyTrainTicketService;
        this.memberFeign = memberFeign;
        this.redis = redis;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.enqueueScript = loadLong("lua/waitlist_enqueue.lua");
        this.dequeueScript = loadLong("lua/waitlist_dequeue.lua");
    }

    /**
     * 申请候补：严格取号 → 落库 → 入队计数。重复申请返回已有记录。
     */
    public WaitlistApplyResp apply(WaitlistApplyReq req, Long memberId, String idempotencyKey) {
        if (req == null || memberId == null) {
            throw new ExceptionConsole(ExceptionEnum.PARAM_NOT_VALID);
        }
        String seatType = validateSeatType(req.getSeatType());
        Daily_Train_Ticket ticket = dailyTrainTicketService.selectByUnique(
                req.getDate(), req.getTrainCode(), req.getStart(), req.getEnd());
        if (ticket == null
                || ticket.getStartIndex() == null
                || ticket.getEndIndex() == null
                || ticket.getEndIndex() <= ticket.getStartIndex()) {
            throw new ExceptionConsole(ExceptionEnum.WAITLIST_TICKET_NOT_EXIST);
        }

        String requestId = normalizeRequestId(idempotencyKey);
        String dateText = InventoryKey.formatDate(req.getDate());
        String bucket = WaitlistKey.bucket(dateText, req.getTrainCode(), req.getStart(), req.getEnd(), seatType);
        String activeKey = activeKey(memberId, req.getPassengerId(), bucket);

        // 幂等快判：同一申请幂等键直接返回已有记录，并确保索引在位
        Waitlist_Application replay = waitlistMapper.selectByRequestId(requestId);
        if (replay != null) {
            ensureEnqueued(replay, bucket);
            return WaitlistApplyResp.of(replay, false);
        }

        String passengerName = resolvePassengerName(memberId, req.getPassengerId());
        Waitlist_Application application = buildApplication(
                requestId, memberId, req, passengerName, dateText, seatType, activeKey);

        boolean[] created = {false};
        Waitlist_Application saved = transactionTemplate.execute(status -> {
            application.setSeqNo(nextSeqNo(bucket, dateText, req, seatType));
            try {
                waitlistMapper.insert(application);
                created[0] = true;
                return application;
            } catch (DuplicateKeyException e) {
                // uk_active 冲突：同一乘客同一票档已有 waiting 申请，返回已有记录
                Waitlist_Application existing = waitlistMapper.selectByActiveKey(activeKey);
                if (existing == null) {
                    throw new ExceptionConsole(ExceptionEnum.WAITLIST_STATUS_ERROR);
                }
                return existing;
            }
        });
        if (saved == null) {
            throw new ExceptionConsole(ExceptionEnum.WAITLIST_STATUS_ERROR);
        }
        ensureEnqueued(saved, bucket);
        return WaitlistApplyResp.of(saved, created[0]);
    }

    /**
     * 取消候补：仅 W 可取消，重复取消幂等返回。
     */
    public WaitlistApplyResp cancel(Long applicationId, Long memberId) {
        if (applicationId == null || memberId == null) {
            throw new ExceptionConsole(ExceptionEnum.PARAM_NOT_VALID);
        }
        Waitlist_Application application = waitlistMapper.selectById(applicationId);
        if (application == null) {
            throw new ExceptionConsole(ExceptionEnum.WAITLIST_NOT_EXIST);
        }
        if (!application.getMemberId().equals(memberId)) {
            throw new ExceptionConsole(ExceptionEnum.WAITLIST_MEMBER_ERROR);
        }
        if (STATUS_CANCELLED.equals(application.getStatus())) {
            dequeue(application);
            return WaitlistApplyResp.of(application, false);
        }
        if (!STATUS_WAITING.equals(application.getStatus())) {
            throw new ExceptionConsole(ExceptionEnum.WAITLIST_STATUS_ERROR);
        }

        Date now = new Date();
        Integer updated = transactionTemplate.execute(status -> waitlistMapper.cancel(applicationId, memberId, now));
        if (updated == null || updated == 0) {
            Waitlist_Application latest = waitlistMapper.selectById(applicationId);
            if (latest != null && STATUS_CANCELLED.equals(latest.getStatus())) {
                dequeue(latest);
                return WaitlistApplyResp.of(latest, false);
            }
            throw new ExceptionConsole(ExceptionEnum.WAITLIST_STATUS_ERROR);
        }
        application.setStatus(STATUS_CANCELLED);
        application.setActiveKey(null);
        application.setUpdateTime(now);
        dequeue(application);
        return WaitlistApplyResp.of(application, false);
    }

    /**
     * 我的候补：按创建时间倒序分页，可按状态过滤。
     */
    public PageResp<WaitlistResp> queryMine(PageReq pageReq, Long memberId, String status) {
        if (memberId == null) {
            throw new ExceptionConsole(ExceptionEnum.PARAM_NOT_VALID);
        }
        PageHelper.startPage(pageReq.getPage(), pageReq.getSize());
        List<Waitlist_Application> list = waitlistMapper.selectByMember(
                memberId, StrUtil.isBlank(status) ? null : status);
        PageInfo<Waitlist_Application> pageInfo = new PageInfo<>(list);
        PageResp<WaitlistResp> pageResp = new PageResp<>();
        pageResp.setTotal(pageInfo.getTotal());
        pageResp.setList(list.stream().map(WaitlistResp::of).collect(Collectors.toList()));
        return pageResp;
    }

    private Waitlist_Application buildApplication(String requestId, Long memberId, WaitlistApplyReq req,
                                                  String passengerName, String dateText, String seatType,
                                                  String activeKey) {
        Date now = new Date();
        Waitlist_Application application = new Waitlist_Application();
        application.setId(SnowUtil.getSnowflakeNextId());
        application.setRequestId(requestId);
        application.setMemberId(memberId);
        application.setPassengerId(req.getPassengerId());
        application.setPassengerName(passengerName);
        application.setDate(req.getDate());
        application.setTrainCode(req.getTrainCode());
        application.setStart(req.getStart());
        application.setEnd(req.getEnd());
        application.setSeatType(seatType);
        application.setStatus(STATUS_WAITING);
        application.setActiveKey(activeKey);
        application.setCreateTime(now);
        application.setUpdateTime(now);
        return application;
    }

    private String resolvePassengerName(Long memberId, Long passengerId) {
        CommonResp<List<PassengerBriefResp>> resp = memberFeign.listPassengersByMember(memberId);
        if (resp == null || resp.getContent() == null) {
            throw new ExceptionConsole(ExceptionEnum.WAITLIST_PASSENGER_ERROR);
        }
        for (PassengerBriefResp passenger : resp.getContent()) {
            if (passenger != null && passengerId.equals(passenger.getId())) {
                return passenger.getName();
            }
        }
        throw new ExceptionConsole(ExceptionEnum.WAITLIST_PASSENGER_ERROR);
    }

    private String validateSeatType(String seatType) {
        if (StrUtil.isBlank(seatType)) {
            throw new ExceptionConsole(ExceptionEnum.PARAM_NOT_VALID);
        }
        try {
            TrainCarriageTypeEnum.fromCode(seatType, null);
        } catch (IllegalArgumentException e) {
            throw new ExceptionConsole(ExceptionEnum.PARAM_NOT_VALID);
        }
        return seatType;
    }

    private String normalizeRequestId(String idempotencyKey) {
        if (StrUtil.isBlank(idempotencyKey)) {
            return REQUEST_ID_PREFIX + SnowUtil.getSnowflakeNextId();
        }
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new ExceptionConsole(ExceptionEnum.PARAM_NOT_VALID);
        }
        return idempotencyKey;
    }

    private String activeKey(Long memberId, Long passengerId, String bucket) {
        return memberId + ":" + passengerId + ":" + bucket;
    }

    /**
     * Redis 取号器失效时用 DB 同票档 max(seq_no)+1 恢复（严格递增仍成立）。
     */
    private long nextSeqNo(String bucket, String dateText, WaitlistApplyReq req, String seatType) {
        try {
            Long seqNo = redis.opsForValue().increment(WaitlistKey.seq(bucket));
            if (seqNo != null) {
                return seqNo;
            }
        } catch (RuntimeException e) {
            LOG.warn("候补取号器不可用，回退 DB 最大序号 key={}", bucket, e);
        }
        return waitlistMapper.selectMaxSeqNo(dateText, req.getTrainCode(), req.getStart(), req.getEnd(), seatType) + 1;
    }

    /**
     * 保证 waiting 申请在 Redis 主队列中；已入队时脚本不会重复计数。
     */
    private void ensureEnqueued(Waitlist_Application application, String bucket) {
        if (!STATUS_WAITING.equals(application.getStatus())) {
            return;
        }
        try {
            redis.execute(enqueueScript,
                    Arrays.asList(WaitlistKey.queue(bucket), WaitlistKey.count(bucket)),
                    String.valueOf(application.getSeqNo()), String.valueOf(application.getId()));
        } catch (RuntimeException e) {
            LOG.error("候补入队失败，等待重试 applicationId={} bucket={}",
                    application.getId(), bucket, e);
        }
    }

    private void dequeue(Waitlist_Application application) {
        String bucket = WaitlistKey.bucket(InventoryKey.formatDate(application.getDate()),
                application.getTrainCode(), application.getStart(), application.getEnd(), application.getSeatType());
        try {
            redis.execute(dequeueScript,
                    Arrays.asList(WaitlistKey.queue(bucket), WaitlistKey.count(bucket)),
                    String.valueOf(application.getId()));
        } catch (RuntimeException e) {
            LOG.error("候补出队失败，等待对账 applicationId={} bucket={}",
                    application.getId(), bucket, e);
        }
    }

    private static DefaultRedisScript<Long> loadLong(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream in = resource.getInputStream()) {
            String body = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            return new DefaultRedisScript<>(body, Long.class);
        } catch (IOException e) {
            throw new IllegalStateException("候补 Lua 脚本加载失败：" + path, e);
        }
    }
}
