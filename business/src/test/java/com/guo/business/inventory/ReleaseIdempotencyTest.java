package com.guo.business.inventory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I14 释放幂等专项：乱序/重复释放不得多还库存，也不得释放新订单占用的名额。
 *
 * <p>依赖本地 Redis（localhost:6379，db 0），测试前清理 2099-02-02 / T14 的库存 key。</p>
 */
class ReleaseIdempotencyTest {

    private static final String DATE_TEXT = "2099-02-02";
    private static final String TRAIN_CODE = "T14";
    private static final String SEAT_TYPE = "1";
    private static final int START_INDEX = 1;
    private static final int END_INDEX = 2;
    private static final String SEAT_ID = "201";

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        inventoryService = new InventoryService(redis);

        cleanUp();
        Map<String, String> ticket = new HashMap<>();
        ticket.put("ydz", "1");
        ticket.put("edz", "-1");
        ticket.put("rw", "-1");
        ticket.put("yw", "-1");
        redis.opsForHash().putAll(ticketKey(), ticket);
        redis.opsForList().rightPush(poolKey(), SEAT_ID);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
        factory.destroy();
    }

    /**
     * 旧订单的迟到释放（换了 releaseKey 让脚本真正执行）不得动新订单的座位与资格。
     */
    @Test
    void outOfOrderReleaseKeepsSeatAndQualificationOfNewOrder() {
        assertTrue(inventoryService.reserve(command("req-old", "order-old", 5001)).isSuccess());
        ReleaseResult first = inventoryService.release(releaseCommand("REL-order-old:T", "order-old", 5001));
        assertEquals(InventoryResultCode.OK, first.getCode());
        assertEquals(1, first.getReleasedSeats());

        assertTrue(inventoryService.reserve(command("req-new", "order-new", 5001)).isSuccess());
        assertEquals("order-new", redis.opsForHash().get(seatKey(), "seg:0"));
        assertEquals("order-new", redis.opsForValue().get(qualKey(5001)));
        assertEquals("0", redis.opsForHash().get(ticketKey(), "ydz"));
        assertEquals(0L, redis.opsForList().size(poolKey()));

        ReleaseResult replay = inventoryService.release(releaseCommand("REL-order-old:R", "order-old", 5001));
        assertEquals(InventoryResultCode.OK, replay.getCode(), "新 releaseKey 让脚本真正执行");
        assertEquals(0, replay.getReleasedSeats(), "旧订单不应释放任何座位");
        assertEquals("order-new", redis.opsForHash().get(seatKey(), "seg:0"), "座位仍属于新订单");
        assertEquals("order-new", redis.opsForValue().get(qualKey(5001)), "资格仍属于新订单");
        assertEquals("0", redis.opsForHash().get(ticketKey(), "ydz"), "余票不得增加");
        assertEquals(0L, redis.opsForList().size(poolKey()), "候选池不得回填");
    }

    /**
     * 同一 releaseKey 并发重放：只有一次真正归还，库存与候选池只增加一次。
     */
    @Test
    void concurrentDuplicateReleaseAddsInventoryOnce() throws Exception {
        assertTrue(inventoryService.reserve(command("req-dup", "order-dup", 5002)).isSuccess());

        int attempts = 20;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ReleaseResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return inventoryService.release(releaseCommand("REL-order-dup:T", "order-dup", 5002));
                }));
            }
            start.countDown();

            int releasedOnce = 0;
            int already = 0;
            for (Future<ReleaseResult> future : futures) {
                ReleaseResult result = future.get(10, TimeUnit.SECONDS);
                if (result.getCode() == InventoryResultCode.OK && result.getReleasedSeats() == 1) {
                    releasedOnce++;
                } else if (result.getCode() == InventoryResultCode.ALREADY) {
                    already++;
                }
            }

            assertEquals(1, releasedOnce, "只允许一次真正归还");
            assertEquals(attempts - 1, already, "其余请求应返回 ALREADY");
            assertEquals("1", redis.opsForHash().get(ticketKey(), "ydz"), "余票只恢复一次");
            assertEquals(1L, redis.opsForList().size(poolKey()), "候选池只回填一次");
            assertNull(redis.opsForValue().get(qualKey(5002)), "资格应删除");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 同一订单不同释放原因（超时与退款）重放：第二次执行但不再归还库存。
     */
    @Test
    void differentReleaseKeysForSameOrderOnlyFirstReturnsSeat() {
        assertTrue(inventoryService.reserve(command("req-two", "order-two", 5003)).isSuccess());

        ReleaseResult timeout = inventoryService.release(releaseCommand("REL-order-two:T", "order-two", 5003));
        assertEquals(InventoryResultCode.OK, timeout.getCode());
        assertEquals(1, timeout.getReleasedSeats());

        ReleaseResult refund = inventoryService.release(releaseCommand("REL-order-two:R", "order-two", 5003));
        assertEquals(InventoryResultCode.OK, refund.getCode(), "不同幂等键会真正执行脚本");
        assertEquals(0, refund.getReleasedSeats(), "第二次释放不得归还座位");
        assertEquals("1", redis.opsForHash().get(ticketKey(), "ydz"), "余票只恢复一次");
        assertEquals(1L, redis.opsForList().size(poolKey()), "候选池只回填一次");
        assertNull(redis.opsForHash().get(seatKey(), "seg:0"));
    }

    private ReserveCommand command(String requestId, String orderId, long passengerId) {
        ReserveCommand command = new ReserveCommand();
        command.setRequestId(requestId);
        command.setOrderId(orderId);
        command.setDate(date());
        command.setTrainCode(TRAIN_CODE);
        command.setSeatType(SEAT_TYPE);
        command.setStartIndex(START_INDEX);
        command.setEndIndex(END_INDEX);
        command.setPassengerIds(java.util.Collections.singletonList(passengerId));
        return command;
    }

    private ReleaseCommand releaseCommand(String releaseKey, String orderId, long passengerId) {
        ReleaseCommand command = new ReleaseCommand();
        command.setReleaseKey(releaseKey);
        command.setOrderId(orderId);
        command.setDate(date());
        command.setTrainCode(TRAIN_CODE);
        command.setSeatType(SEAT_TYPE);
        command.setStartIndex(START_INDEX);
        command.setEndIndex(END_INDEX);
        command.setPassengerIds(java.util.Collections.singletonList(passengerId));
        command.setSeatIds(java.util.Collections.singletonList(SEAT_ID));
        return command;
    }

    private Date date() {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(DATE_TEXT);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    private String ticketKey() {
        return InventoryKey.ticket(DATE_TEXT, TRAIN_CODE, START_INDEX, END_INDEX);
    }

    private String seatKey() {
        return InventoryKey.seat(DATE_TEXT, TRAIN_CODE, Long.valueOf(SEAT_ID));
    }

    private String poolKey() {
        return InventoryKey.pool(DATE_TEXT, TRAIN_CODE, SEAT_TYPE, START_INDEX, END_INDEX);
    }

    private String qualKey(long passengerId) {
        return InventoryKey.qualification(DATE_TEXT, TRAIN_CODE, passengerId);
    }

    private void cleanUp() {
        Set<String> keys = new HashSet<>(redis.keys("inv:*:" + DATE_TEXT + ":" + TRAIN_CODE + ":*"));
        keys.addAll(redis.keys("qual:" + DATE_TEXT + ":" + TRAIN_CODE + ":*"));
        keys.addAll(redis.keys("purchase:req:req-old"));
        keys.addAll(redis.keys("purchase:req:req-new"));
        keys.addAll(redis.keys("purchase:req:req-dup"));
        keys.addAll(redis.keys("purchase:req:req-two"));
        keys.addAll(redis.keys("hold:HOLD-order-*"));
        keys.addAll(redis.keys("rel:REL-order-*"));
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
