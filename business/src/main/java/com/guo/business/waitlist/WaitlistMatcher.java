package com.guo.business.waitlist;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.guo.business.TypeEnum.ConfirmOrderStatusEnum;
import com.guo.business.domain.Confirm_Order;
import com.guo.business.domain.Daily_Train_Ticket;
import com.guo.business.domain.InventoryHold;
import com.guo.business.domain.Waitlist_Application;
import com.guo.business.inventory.InventoryKey;
import com.guo.business.inventory.InventoryResultCode;
import com.guo.business.inventory.InventoryService;
import com.guo.business.inventory.ReleaseCommand;
import com.guo.business.inventory.ReserveCommand;
import com.guo.business.inventory.ReserveResult;
import com.guo.business.mapper.InventoryHoldMapper;
import com.guo.business.mapper.PurchaseAcceptMapper;
import com.guo.business.mapper.Waitlist_ApplicationMapper;
import com.guo.business.purchase.model.OrderOutboxRecord;
import com.guo.business.purchase.model.PurchaseQualificationRecord;
import com.guo.business.req.Confirm_OrderTicketReq;
import com.guo.business.service.Daily_Train_TicketService;
import com.guo.business.service.OrderCloseQueue;
import com.guo.common.util.SnowUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * I16 候补补位匹配器。
 *
 * <p>处理顺序对齐 docs 7.7：事件幂等（{@code wl:evt}）→ 票档合并锁
 * （{@code waitlist:match:key}，2s）→ 循环 {@code ZPOPMIN} → Lua 预扣 →
 * 事务内建单/资格转移/状态 D/outbox。预扣失败按 NO_STOCK / SEAT_CONFLICT
 * 原分数回队并结束本轮；连续 3 次转 {@code waitlist:retry} 退避 30s。</p>
 */
@Service
public class WaitlistMatcher {

    private static final Logger LOG = LoggerFactory.getLogger(WaitlistMatcher.class);

    private static final String STATUS_WAITING = "W";
    private static final String STATUS_MATCHED = "D";
    private static final String QUALIFICATION_CONSUMED = "C";
    private static final String HOLD_STATUS_HELD = "H";
    private static final String OUTBOX_STATUS_NEW = "N";
    private static final String EVENT_TYPE_NOTIFY = "WAITLIST_NOTIFY";
    private static final String REQUEST_ID_PREFIX = "WL:";
    private static final String HOLD_TOKEN_PREFIX = "HOLD-";

    /** 单轮最多匹配的申请数，防止事件风暴下长时间持锁 */
    private static final int MAX_MATCH_PER_ROUND = 100;
    /** 连续失败达到该次数转退避 */
    private static final int MAX_FAIL_TIMES = 3;
    /** 退避时长 30 秒 */
    private static final long RETRY_DELAY_MS = 30_000L;
    /** 补位订单支付窗口 5 分钟 */
    private static final long ORDER_EXPIRE_MS = 5 * 60 * 1000L;

    private static final Duration EVENT_TTL = Duration.ofMinutes(10);
    private static final Duration LOCK_TTL = Duration.ofMillis(2000);
    private static final Duration FAIL_TTL = Duration.ofMinutes(10);

    private final Waitlist_ApplicationMapper waitlistMapper;
    private final InventoryHoldMapper inventoryHoldMapper;
    private final PurchaseAcceptMapper purchaseAcceptMapper;
    private final Daily_Train_TicketService dailyTrainTicketService;
    private final InventoryService inventoryService;
    private final OrderCloseQueue orderCloseQueue;
    private final StringRedisTemplate redis;
    private final TransactionTemplate transactionTemplate;
    private final DefaultRedisScript<String> popScript;
    private final DefaultRedisScript<Long> enqueueScript;
    private final DefaultRedisScript<Long> unlockScript;

    public WaitlistMatcher(Waitlist_ApplicationMapper waitlistMapper,
                           InventoryHoldMapper inventoryHoldMapper,
                           PurchaseAcceptMapper purchaseAcceptMapper,
                           Daily_Train_TicketService dailyTrainTicketService,
                           InventoryService inventoryService,
                           OrderCloseQueue orderCloseQueue,
                           StringRedisTemplate redis,
                           PlatformTransactionManager transactionManager) {
        this.waitlistMapper = waitlistMapper;
        this.inventoryHoldMapper = inventoryHoldMapper;
        this.purchaseAcceptMapper = purchaseAcceptMapper;
        this.dailyTrainTicketService = dailyTrainTicketService;
        this.inventoryService = inventoryService;
        this.orderCloseQueue = orderCloseQueue;
        this.redis = redis;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.popScript = load("lua/waitlist_pop.lua", String.class);
        this.enqueueScript = load("lua/waitlist_enqueue.lua", Long.class);
        this.unlockScript = load("lua/waitlist_unlock.lua", Long.class);
    }

    /**
     * 消费 INVENTORY_RELEASED 事件；返回本轮成功补位的申请数。
     */
    public int onRelease(String payload) {
        WaitlistMatchEvent event = parse(payload);
        if (event == null) {
            LOG.warn("候补匹配事件为空或格式非法，直接 ACK");
            return 0;
        }
        return onRelease(event);
    }

    public int onRelease(WaitlistMatchEvent event) {
        if (event == null || StrUtil.isBlank(event.getDate()) || StrUtil.isBlank(event.getTrainCode())
                || StrUtil.isBlank(event.getStart()) || StrUtil.isBlank(event.getEnd())
                || StrUtil.isBlank(event.getSeatType())) {
            LOG.warn("候补匹配事件缺少票档字段，直接 ACK event={}", JSON.toJSONString(event));
            return 0;
        }
        String eventId = StrUtil.isBlank(event.getEventId())
                ? "MANUAL:" + SnowUtil.getSnowflakeNextId() : event.getEventId();
        Boolean first = redis.opsForValue().setIfAbsent(WaitlistKey.event(eventId), "1", EVENT_TTL);
        if (!Boolean.TRUE.equals(first)) {
            LOG.info("候补匹配事件重复，已 ACK eventId={}", eventId);
            return 0;
        }

        String bucket = event.bucket();
        String lockKey = WaitlistKey.matchLock(bucket);
        String token = UUID.randomUUID().toString();
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            LOG.info("同票档已有一轮匹配在执行，事件被合并 eventId={} bucket={}", eventId, bucket);
            return 0;
        }

        int matched = 0;
        try {
            for (int i = 0; i < MAX_MATCH_PER_ROUND; i++) {
                String applicationId = pop(bucket);
                if (applicationId == null) {
                    break;
                }
                Outcome outcome = matchOne(bucket, applicationId);
                if (outcome == Outcome.MATCHED) {
                    matched++;
                    continue;
                }
                if (outcome == Outcome.SKIP) {
                    continue;
                }
                // NO_STOCK / SEAT_CONFLICT / 异常：本轮结束，等待退避或下一次释放事件
                break;
            }
        } finally {
            unlock(lockKey, token);
        }
        LOG.info("候补匹配完成 eventId={} bucket={} matched={}", eventId, bucket, matched);
        return matched;
    }

    private Outcome matchOne(String bucket, String applicationIdText) {
        Long applicationId;
        try {
            applicationId = Long.valueOf(applicationIdText);
        } catch (NumberFormatException e) {
            LOG.warn("候补队列存在非法成员，已丢弃 member={}", applicationIdText);
            return Outcome.SKIP;
        }

        Waitlist_Application application = waitlistMapper.selectById(applicationId);
        if (application == null || !STATUS_WAITING.equals(application.getStatus())) {
            LOG.info("候补申请已不在等待状态，跳过 applicationId={}", applicationId);
            return Outcome.SKIP;
        }

        Daily_Train_Ticket ticket = dailyTrainTicketService.selectByUnique(
                application.getDate(), application.getTrainCode(), application.getStart(), application.getEnd());
        if (ticket == null || ticket.getStartIndex() == null || ticket.getEndIndex() == null
                || ticket.getEndIndex() <= ticket.getStartIndex()) {
            requeue(bucket, application, "余票行缺失");
            return Outcome.STOP;
        }

        long orderId = SnowUtil.getSnowflakeNextId();
        String requestId = REQUEST_ID_PREFIX + applicationId;
        ReserveResult reserved = inventoryService.reserve(reserveCommand(application, ticket, requestId, orderId));
        if (reserved.getCode() != InventoryResultCode.OK) {
            handleReserveFailure(bucket, application, reserved.getCode());
            return Outcome.STOP;
        }

        Date expireAt = new Date(System.currentTimeMillis() + ORDER_EXPIRE_MS);
        try {
            transactionTemplate.executeWithoutResult(status ->
                    persistMatched(applicationId, application, ticket, orderId, requestId, reserved, expireAt));
        } catch (StaleApplicationException e) {
            LOG.info("候补申请匹配事务内状态已变化，释放预扣 applicationId={}", applicationId);
            rollbackReservation(application, ticket, requestId, orderId, reserved);
            return Outcome.SKIP;
        } catch (RuntimeException e) {
            LOG.error("候补补位落库失败，释放预扣并回队 applicationId={}", applicationId, e);
            rollbackReservation(application, ticket, requestId, orderId, reserved);
            requeue(bucket, application, "落库失败");
            return Outcome.STOP;
        }

        orderCloseQueue.schedule(orderId, expireAt);
        redis.delete(WaitlistKey.fail(applicationId));
        LOG.info("候补补位成功 applicationId={} orderId={} seqNo={}",
                applicationId, orderId, application.getSeqNo());
        return Outcome.MATCHED;
    }

    /**
     * 同一事务内：加锁校验 W → 建 H 单 → hold 落库 → 资格转移 → 申请置 D → outbox。
     */
    private void persistMatched(Long applicationId, Waitlist_Application application, Daily_Train_Ticket ticket,
                                long orderId, String requestId, ReserveResult reserved, Date expireAt) {
        Waitlist_Application locked = waitlistMapper.selectByIdForUpdate(applicationId);
        if (locked == null || !STATUS_WAITING.equals(locked.getStatus())) {
            throw new StaleApplicationException();
        }

        Date now = new Date();
        Date date = locked.getDate();
        String holdToken = StrUtil.isBlank(reserved.getHoldToken())
                ? HOLD_TOKEN_PREFIX + orderId : reserved.getHoldToken();

        Long qualificationId = transferQualification(locked, orderId, requestId, expireAt, now);

        Confirm_Order order = new Confirm_Order();
        order.setId(orderId);
        order.setMemberId(locked.getMemberId());
        order.setDate(date);
        order.setTrainCode(locked.getTrainCode());
        order.setStart(locked.getStart());
        order.setEnd(locked.getEnd());
        order.setDailyTrainTicketId(ticket.getId());
        order.setStatus(ConfirmOrderStatusEnum.WAITLIST_PAY.getCode());
        order.setTickets(ticketsJson(locked));
        order.setCreateTime(now);
        order.setUpdateTime(now);
        order.setRequestId(requestId);
        order.setQualificationId(qualificationId);
        order.setExpireAt(expireAt);
        order.setTicketStatus("N");
        order.setVersion(0);
        purchaseAcceptMapper.insertInitOrder(order);

        for (String seatIdText : reserved.getSeatIds()) {
            InventoryHold hold = new InventoryHold();
            hold.setId(SnowUtil.getSnowflakeNextId());
            hold.setHoldToken(holdToken);
            hold.setRequestId(requestId);
            hold.setOrderId(orderId);
            hold.setMemberId(locked.getMemberId());
            hold.setPassengerId(locked.getPassengerId());
            hold.setDate(date);
            hold.setTrainCode(locked.getTrainCode());
            hold.setSeatId(Long.valueOf(seatIdText));
            hold.setSeatType(locked.getSeatType());
            hold.setStartIndex(ticket.getStartIndex());
            hold.setEndIndex(ticket.getEndIndex());
            hold.setStatus(HOLD_STATUS_HELD);
            hold.setCreateTime(now);
            inventoryHoldMapper.insert(hold);
        }

        if (waitlistMapper.markMatched(applicationId, orderId, expireAt, now) != 1) {
            throw new StaleApplicationException();
        }
        insertNotifyOutbox(applicationId, orderId, now);
    }

    /**
     * 资格转移：优先复用该乘客同票档的 H 资格，否则补一条已消费资格，保持 DB 与
     * reserve.lua 写下的 {@code qual:{date}:{train}:{passengerId}} 一致。
     */
    private Long transferQualification(Waitlist_Application application, long orderId,
                                       String requestId, Date expireAt, Date now) {
        int transferred = waitlistMapper.transferQualification(orderId, application.getPassengerId(),
                application.getDate(), application.getTrainCode(), application.getStart(),
                application.getEnd(), application.getSeatType());
        if (transferred > 0) {
            return selectedQualificationId(orderId);
        }
        PurchaseQualificationRecord qualification = new PurchaseQualificationRecord();
        long qualificationId = SnowUtil.getSnowflakeNextId();
        qualification.setId(qualificationId);
        qualification.setRequestId(requestId);
        qualification.setMemberId(application.getMemberId());
        qualification.setPassengerId(application.getPassengerId());
        qualification.setDate(new java.sql.Date(application.getDate().getTime()));
        qualification.setTrainCode(application.getTrainCode());
        qualification.setStart(application.getStart());
        qualification.setEnd(application.getEnd());
        qualification.setSeatType(application.getSeatType());
        qualification.setOrderId(orderId);
        qualification.setStatus(QUALIFICATION_CONSUMED);
        qualification.setActiveKey(null);
        qualification.setExpireAt(expireAt);
        qualification.setCreateTime(now);
        qualification.setUpdateTime(now);
        purchaseAcceptMapper.insertQualification(qualification);
        return qualificationId;
    }

    /**
     * 转移后按订单取资格 id，供 confirm_order.qualification_id 记录。
     */
    private Long selectedQualificationId(long orderId) {
        Long qualificationId = waitlistMapper.selectQualificationIdByOrderId(orderId);
        return qualificationId == null ? 0L : qualificationId;
    }

    private void insertNotifyOutbox(Long applicationId, long orderId, Date now) {
        com.alibaba.fastjson.JSONObject payload = new com.alibaba.fastjson.JSONObject(true);
        payload.put("applicationId", applicationId);
        payload.put("orderId", orderId);

        OrderOutboxRecord outbox = new OrderOutboxRecord();
        outbox.setId(SnowUtil.getSnowflakeNextId());
        outbox.setEventId(EVENT_TYPE_NOTIFY + ":" + applicationId);
        outbox.setAggregateType("WAITLIST");
        outbox.setAggregateId(applicationId);
        outbox.setEventType(EVENT_TYPE_NOTIFY);
        outbox.setPayload(JSON.toJSONString(payload));
        outbox.setStatus(OUTBOX_STATUS_NEW);
        outbox.setRetryCount(0);
        outbox.setNextRetryAt(now);
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);
        purchaseAcceptMapper.insertOutbox(outbox);
    }

    private String ticketsJson(Waitlist_Application application) {
        Confirm_OrderTicketReq ticket = new Confirm_OrderTicketReq();
        ticket.setPassengerId(application.getPassengerId());
        ticket.setPassengerType("1");
        ticket.setPassengerName(application.getPassengerName());
        ticket.setPassengerIdCard("");
        ticket.setSeatTypeCode(application.getSeatType());
        return JSON.toJSONString(Collections.singletonList(ticket));
    }

    private ReserveCommand reserveCommand(Waitlist_Application application, Daily_Train_Ticket ticket,
                                          String requestId, long orderId) {
        ReserveCommand command = new ReserveCommand();
        command.setRequestId(requestId);
        command.setOrderId(String.valueOf(orderId));
        command.setDate(application.getDate());
        command.setTrainCode(application.getTrainCode());
        command.setSeatType(application.getSeatType());
        command.setStartIndex(ticket.getStartIndex());
        command.setEndIndex(ticket.getEndIndex());
        command.setPassengerIds(Collections.singletonList(application.getPassengerId()));
        return command;
    }

    /**
     * 预扣失败分级：NO_STOCK / SEAT_CONFLICT 连续 3 次转退避；其余原因原分数回队。
     */
    private void handleReserveFailure(String bucket, Waitlist_Application application, InventoryResultCode code) {
        boolean stockFailure = code == InventoryResultCode.NO_TICKET || code == InventoryResultCode.DUPLICATE;
        if (!stockFailure) {
            LOG.warn("候补预扣暂时不可用 applicationId={} code={}，原分数回队",
                    application.getId(), code);
            requeue(bucket, application, code.name());
            return;
        }

        String failKey = WaitlistKey.fail(application.getId());
        Long failures = redis.opsForValue().increment(failKey);
        redis.expire(failKey, FAIL_TTL);
        if (failures != null && failures >= MAX_FAIL_TIMES) {
            redis.opsForZSet().add(WaitlistKey.retry(bucket), String.valueOf(application.getId()),
                    System.currentTimeMillis() + RETRY_DELAY_MS);
            // 登记票档索引，供退避推进器直接遍历，避免每秒 KEYS 全库扫描阻塞 Redis
            redis.opsForSet().add(WaitlistKey.retryIndex(), bucket);
            redis.delete(failKey);
            LOG.warn("候补连续失败 {} 次，进入退避 30s applicationId={} code={}",
                    failures, application.getId(), code);
            return;
        }
        LOG.info("候补预扣失败 applicationId={} code={} failures={}，原分数回队",
                application.getId(), code, failures);
        requeue(bucket, application, code.name());
    }

    /**
     * 用原 seq_no 回主队列，保证 FIFO 不丢位；已存在时不重复计数。
     */
    private void requeue(String bucket, Waitlist_Application application, String reason) {
        try {
            redis.execute(enqueueScript,
                    Arrays.asList(WaitlistKey.queue(bucket), WaitlistKey.count(bucket)),
                    String.valueOf(application.getSeqNo()), String.valueOf(application.getId()));
        } catch (RuntimeException e) {
            LOG.error("候补回队失败，等待退避/人工补偿 applicationId={} reason={}",
                    application.getId(), reason, e);
        }
    }

    private void rollbackReservation(Waitlist_Application application, Daily_Train_Ticket ticket,
                                     String requestId, long orderId, ReserveResult reserved) {
        ReleaseCommand command = new ReleaseCommand();
        command.setReleaseKey("WLF:" + application.getId() + ":" + orderId);
        command.setOrderId(String.valueOf(orderId));
        command.setDate(application.getDate());
        command.setTrainCode(application.getTrainCode());
        command.setSeatType(application.getSeatType());
        command.setStartIndex(ticket.getStartIndex());
        command.setEndIndex(ticket.getEndIndex());
        command.setPassengerIds(Collections.singletonList(application.getPassengerId()));
        List<String> seatIds = new ArrayList<>(reserved.getSeatIds());
        command.setSeatIds(seatIds);
        try {
            inventoryService.release(command);
        } catch (RuntimeException e) {
            LOG.error("候补补位回滚释放失败 orderId={} applicationId={}", orderId, application.getId(), e);
        }
        try {
            inventoryService.abort(requestId, String.valueOf(orderId), reserved.getHoldToken());
        } catch (RuntimeException e) {
            LOG.error("候补补位回滚清理幂等失败 requestId={}", requestId, e);
        }
    }

    private String pop(String bucket) {
        try {
            return redis.execute(popScript,
                    Arrays.asList(WaitlistKey.queue(bucket), WaitlistKey.count(bucket)));
        } catch (RuntimeException e) {
            LOG.error("候补出队失败 bucket={}", bucket, e);
            return null;
        }
    }

    private void unlock(String lockKey, String token) {
        try {
            redis.execute(unlockScript, Collections.singletonList(lockKey), token);
        } catch (RuntimeException e) {
            LOG.warn("候补合并锁释放失败，等待 TTL 自动过期 lockKey={}", lockKey, e);
        }
    }

    private WaitlistMatchEvent parse(String payload) {
        if (StrUtil.isBlank(payload)) {
            return null;
        }
        try {
            WaitlistMatchEvent event = JSON.parseObject(payload, WaitlistMatchEvent.class);
            if (event != null && StrUtil.isBlank(event.getDate())) {
                event.setDate(DateUtil.formatDate(new Date()));
            }
            return event;
        } catch (Exception e) {
            LOG.error("候补匹配事件解析失败 payload={}", payload, e);
            return null;
        }
    }

    private static <T> DefaultRedisScript<T> load(String path, Class<T> resultType) {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream in = resource.getInputStream()) {
            String body = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            return new DefaultRedisScript<>(body, resultType);
        } catch (IOException e) {
            throw new IllegalStateException("候补 Lua 脚本加载失败：" + path, e);
        }
    }

    private enum Outcome {
        /** 本轮结束（无票/座位冲突/异常） */
        STOP,
        /** 继续下一位（申请已失效或落库冲突） */
        SKIP,
        /** 补位成功 */
        MATCHED
    }

    /** 事务内发现申请已不是 W 时回滚并释放预扣 */
    private static class StaleApplicationException extends RuntimeException {
    }
}
