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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I05 库存脚本验收：50 并发抢 1 座只成功 1；重复释放无副作用；旧订单不释放新占位。
 *
 * <p>依赖本地 Redis（localhost:6379，db 0），测试前会清理 2099-01-01 / T05 的库存 key。</p>
 */
class InventoryServiceTest {

    private static final String DATE_TEXT = "2099-01-01";
    private static final String TRAIN_CODE = "T05";
    private static final String SEAT_TYPE = "1";
    private static final int START_INDEX = 1;
    private static final int END_INDEX = 2;
    private static final String SEAT_ID = "101";

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

    @Test
    void reserveOnlyOneWinnerUnderConcurrency() throws Exception {
        int attempts = 50;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ReserveResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                final int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    return inventoryService.reserve(command("req-" + index, "order-" + index, 1000 + index));
                }));
            }
            start.countDown();

            int success = 0;
            int noTicket = 0;
            String winnerOrderId = null;
            String winnerSeat = null;
            for (Future<ReserveResult> future : futures) {
                ReserveResult result = future.get(10, TimeUnit.SECONDS);
                if (result.getCode() == InventoryResultCode.OK) {
                    success++;
                    winnerOrderId = result.getOrderId();
                    winnerSeat = result.getSeatIds().get(0);
                } else if (result.getCode() == InventoryResultCode.NO_TICKET) {
                    noTicket++;
                }
            }

            assertEquals(1, success, "只允许一个请求抢到座位");
            assertEquals(attempts - 1, noTicket, "其余请求应返回 NO_TICKET");
            assertEquals(SEAT_ID, winnerSeat);
            assertEquals("0", redis.opsForHash().get(ticketKey(), "ydz"), "余票应为 0");
            assertEquals(winnerOrderId, redis.opsForHash().get(seatKey(), "seg:0"), "座位应归属赢家订单");
            assertEquals(0L, redis.opsForList().size(poolKey()), "候选池应被取空");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void releaseIsIdempotent() {
        ReserveResult reserved = inventoryService.reserve(command("req-rel", "order-rel", 2001));
        assertTrue(reserved.isSuccess(), "预扣应成功");

        ReleaseResult first = inventoryService.release(releaseCommand("rel-1", "order-rel", 2001));
        assertEquals(InventoryResultCode.OK, first.getCode());
        assertEquals(1, first.getReleasedSeats());
        assertEquals("1", redis.opsForHash().get(ticketKey(), "ydz"), "释放后余票应恢复");
        assertNull(redis.opsForHash().get(seatKey(), "seg:0"), "座位占用应删除");
        assertEquals(1L, redis.opsForList().size(poolKey()), "座位应回填候选池");
        assertNull(redis.opsForValue().get(qualKey(2001)), "资格应删除");

        ReleaseResult second = inventoryService.release(releaseCommand("rel-1", "order-rel", 2001));
        assertEquals(InventoryResultCode.ALREADY, second.getCode());
        assertEquals(0, second.getReleasedSeats());
        assertEquals("1", redis.opsForHash().get(ticketKey(), "ydz"), "重复释放不得多还库存");
        assertEquals(1L, redis.opsForList().size(poolKey()), "重复释放不得重复回填");
    }

    @Test
    void oldOrderReleaseDoesNotFreeNewHold() {
        assertTrue(inventoryService.reserve(command("req-a", "order-a", 3001)).isSuccess());
        ReleaseResult released = inventoryService.release(releaseCommand("rel-a-1", "order-a", 3001));
        assertEquals(1, released.getReleasedSeats());

        assertTrue(inventoryService.reserve(command("req-b", "order-b", 3002)).isSuccess());
        assertEquals("order-b", redis.opsForHash().get(seatKey(), "seg:0"));

        ReleaseResult replay = inventoryService.release(releaseCommand("rel-a-2", "order-a", 3001));
        assertEquals(InventoryResultCode.OK, replay.getCode(), "新 releaseKey 让脚本真正执行");
        assertEquals(0, replay.getReleasedSeats(), "旧订单不应释放新占位");
        assertEquals("order-b", redis.opsForHash().get(seatKey(), "seg:0"), "座位仍属于新订单");
        assertEquals("0", redis.opsForHash().get(ticketKey(), "ydz"), "余票不应被旧订单归还");
    }

    @Test
    void confirmRemovesHoldAndSecondConfirmMisses() {
        assertTrue(inventoryService.reserve(command("req-c", "order-c", 4001)).isSuccess());
        String holdKey = InventoryKey.hold("HOLD-order-c");
        assertNotNull(redis.opsForHash().get(holdKey, "orderId"));

        ConfirmCommand confirm = new ConfirmCommand();
        confirm.setHoldToken("HOLD-order-c");
        confirm.setOrderId("order-c");
        confirm.setDate(date());
        confirm.setTrainCode(TRAIN_CODE);
        confirm.setPassengerIds(java.util.Collections.singletonList(4001L));

        assertEquals(InventoryResultCode.OK, inventoryService.confirm(confirm));
        assertFalse(redis.hasKey(holdKey), "hold 应删除");
        assertEquals(InventoryResultCode.MISS, inventoryService.confirm(confirm), "重复收尾返回 MISS");
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
        Set<String> keys = new java.util.HashSet<>(redis.keys("inv:*:" + DATE_TEXT + ":" + TRAIN_CODE + ":*"));
        keys.addAll(redis.keys("qual:" + DATE_TEXT + ":" + TRAIN_CODE + ":*"));
        keys.addAll(redis.keys("purchase:req:req-*"));
        keys.addAll(redis.keys("hold:HOLD-order-*"));
        keys.addAll(redis.keys("rel:rel-*"));
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
