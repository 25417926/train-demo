package com.guo.business.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 库存原子操作封装（I05）：reserve / release / confirm 三个 Lua 脚本。
 *
 * <p>库存正确性完全由脚本的原子性保证，MySQL 只作为账本与对账依据。</p>
 */
@Service
public class InventoryService {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryService.class);

    /** 资格默认保留到支付窗口 */
    private static final long DEFAULT_QUAL_TTL_MS = 20 * 60 * 1000L;
    /** hold 凭据 5 分钟 */
    private static final long DEFAULT_HOLD_TTL_MS = 5 * 60 * 1000L;
    /** 受理幂等状态 24 小时 */
    private static final long REQUEST_TTL_MS = 24 * 60 * 60 * 1000L;
    /** 释放幂等标记 24 小时 */
    private static final long RELEASE_MARK_TTL_SECONDS = 24 * 60 * 60L;
    /** 单次最多扫描 64 个候选座位，避免 Lua 长时间阻塞 */
    private static final int POOL_SCAN_MAX = 64;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> reserveScript;
    private final DefaultRedisScript<List> releaseScript;
    private final DefaultRedisScript<List> confirmScript;
    private final DefaultRedisScript<Long> abortScript;

    public InventoryService(StringRedisTemplate redis) {
        this.redis = redis;
        this.reserveScript = load("lua/reserve.lua");
        this.releaseScript = load("lua/release.lua");
        this.confirmScript = load("lua/confirm.lua");
        this.abortScript = loadLong("lua/abort.lua");
    }

    /**
     * 预扣：同一 requestId 只成功一次；资格、余票、座位区间与 hold 凭据一次原子完成。
     */
    public ReserveResult reserve(ReserveCommand command) {
        validateReserve(command);
        String dateText = InventoryKey.formatDate(command.getDate());
        String holdToken = "HOLD-" + command.getOrderId();

        List<?> raw = redis.execute(reserveScript,
                Collections.singletonList(InventoryKey.purchaseRequest(command.getRequestId())),
                command.getOrderId(),
                command.getRequestId(),
                InventoryKey.qualificationPrefix(dateText, command.getTrainCode()),
                InventoryKey.ticket(dateText, command.getTrainCode(),
                        command.getStartIndex(), command.getEndIndex()),
                InventoryKey.seatPrefix(dateText, command.getTrainCode()),
                InventoryKey.pool(dateText, command.getTrainCode(), command.getSeatType(),
                        command.getStartIndex(), command.getEndIndex()),
                InventoryKey.ticketField(command.getSeatType()),
                String.valueOf(command.getPassengerIds().size()),
                String.valueOf(command.getStartIndex()),
                String.valueOf(command.getEndIndex()),
                joinIds(command.getPassengerIds()),
                String.valueOf(ttl(command.getQualTtlMs(), DEFAULT_QUAL_TTL_MS)),
                InventoryKey.holdPrefix(),
                holdToken,
                String.valueOf(ttl(command.getHoldTtlMs(), DEFAULT_HOLD_TTL_MS)),
                String.valueOf(REQUEST_TTL_MS),
                String.valueOf(POOL_SCAN_MAX));

        InventoryResultCode code = parseCode(raw);
        String orderId = valueAt(raw, 1);
        String resultHoldToken = valueAt(raw, 2);
        List<String> seatIds = parseCsv(valueAt(raw, 3));

        LOG.info("库存预扣 date={} train={} seatType={} range=[{}, {}) orderId={} requestId={} code={} seats={}",
                dateText, command.getTrainCode(), command.getSeatType(), command.getStartIndex(),
                command.getEndIndex(), command.getOrderId(), command.getRequestId(), code, seatIds);
        return ReserveResult.of(code, orderId, resultHoldToken, seatIds, describe(code));
    }

    /**
     * 释放：同一 releaseKey 只生效一次；座位按 orderId 比较后归还，旧订单不会释放新占位。
     */
    public ReleaseResult release(ReleaseCommand command) {
        validateRelease(command);
        String dateText = InventoryKey.formatDate(command.getDate());

        List<?> raw = redis.execute(releaseScript,
                Collections.singletonList(InventoryKey.releaseMark(command.getReleaseKey())),
                command.getOrderId(),
                InventoryKey.qualificationPrefix(dateText, command.getTrainCode()),
                joinIds(command.getPassengerIds()),
                InventoryKey.seatPrefix(dateText, command.getTrainCode()),
                String.join(",", command.getSeatIds()),
                InventoryKey.ticket(dateText, command.getTrainCode(),
                        command.getStartIndex(), command.getEndIndex()),
                InventoryKey.ticketField(command.getSeatType()),
                String.valueOf(command.getStartIndex()),
                String.valueOf(command.getEndIndex()),
                InventoryKey.pool(dateText, command.getTrainCode(), command.getSeatType(),
                        command.getStartIndex(), command.getEndIndex()),
                String.valueOf(RELEASE_MARK_TTL_SECONDS));

        InventoryResultCode code = parseCode(raw);
        int releasedSeats = parseInt(valueAt(raw, 1));

        LOG.info("库存释放 date={} train={} orderId={} releaseKey={} code={} seats={}",
                dateText, command.getTrainCode(), command.getOrderId(), command.getReleaseKey(),
                code, releasedSeats);
        return ReleaseResult.of(code, releasedSeats);
    }

    /**
     * 收尾：删除属于当前订单的 hold，并把资格续期到支付窗口。
     */
    public InventoryResultCode confirm(ConfirmCommand command) {
        if (command == null || isBlank(command.getHoldToken()) || isBlank(command.getOrderId())) {
            throw new IllegalArgumentException("收尾参数不完整");
        }
        String dateText = InventoryKey.formatDate(command.getDate());

        List<?> raw = redis.execute(confirmScript,
                Collections.singletonList(InventoryKey.hold(command.getHoldToken())),
                command.getOrderId(),
                InventoryKey.qualificationPrefix(dateText, command.getTrainCode()),
                joinIds(command.getPassengerIds()),
                String.valueOf(ttl(command.getQualTtlMs(), DEFAULT_QUAL_TTL_MS)));

        InventoryResultCode code = parseCode(raw);
        LOG.info("库存收尾 date={} train={} orderId={} holdToken={} code={}",
                dateText, command.getTrainCode(), command.getOrderId(), command.getHoldToken(), code);
        return code;
    }

    /**
     * 受理失败清理（I06）：删除仍属于当前订单的幂等状态与 hold 凭据。
     *
     * <p>只做比较后删除，重复调用或已被重试覆盖时无副作用。</p>
     */
    public long abort(String requestId, String orderId, String holdToken) {
        if (isBlank(requestId) || isBlank(orderId) || isBlank(holdToken)) {
            throw new IllegalArgumentException("清理参数不完整");
        }
        Long cleaned = redis.execute(abortScript,
                Arrays.asList(InventoryKey.purchaseRequest(requestId), InventoryKey.hold(holdToken)),
                orderId);
        LOG.info("受理失败清理 requestId={} orderId={} cleaned={}", requestId, orderId, cleaned);
        return cleaned == null ? 0L : cleaned;
    }

    private void validateReserve(ReserveCommand command) {
        if (command == null
                || isBlank(command.getRequestId())
                || isBlank(command.getOrderId())
                || command.getDate() == null
                || isBlank(command.getTrainCode())
                || isBlank(command.getSeatType())
                || command.getStartIndex() <= 0
                || command.getEndIndex() <= command.getStartIndex()
                || command.getPassengerIds() == null
                || command.getPassengerIds().isEmpty()) {
            throw new IllegalArgumentException("预扣参数不完整");
        }
    }

    private void validateRelease(ReleaseCommand command) {
        if (command == null
                || isBlank(command.getReleaseKey())
                || isBlank(command.getOrderId())
                || command.getDate() == null
                || isBlank(command.getTrainCode())
                || isBlank(command.getSeatType())
                || command.getStartIndex() <= 0
                || command.getEndIndex() <= command.getStartIndex()
                || command.getPassengerIds() == null
                || command.getPassengerIds().isEmpty()
                || command.getSeatIds() == null
                || command.getSeatIds().isEmpty()) {
            throw new IllegalArgumentException("释放参数不完整");
        }
    }

    private static DefaultRedisScript<List> load(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream in = resource.getInputStream()) {
            String body = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            return new DefaultRedisScript<>(body, List.class);
        } catch (IOException e) {
            throw new IllegalStateException("库存 Lua 脚本加载失败：" + path, e);
        }
    }

    private static DefaultRedisScript<Long> loadLong(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream in = resource.getInputStream()) {
            String body = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            return new DefaultRedisScript<>(body, Long.class);
        } catch (IOException e) {
            throw new IllegalStateException("库存 Lua 脚本加载失败：" + path, e);
        }
    }

    private static InventoryResultCode parseCode(List<?> result) {
        String status = valueAt(result, 0);
        if (status == null) {
            throw new IllegalStateException("库存脚本未返回状态");
        }
        try {
            return InventoryResultCode.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("未知库存脚本状态：" + status, e);
        }
    }

    private static String valueAt(List<?> result, int index) {
        if (result == null || result.size() <= index || result.get(index) == null) {
            return null;
        }
        return String.valueOf(result.get(index));
    }

    private static List<String> parseCsv(String csv) {
        if (csv == null || csv.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (String item : csv.split(",")) {
            if (!item.isEmpty()) {
                values.add(item);
            }
        }
        return values;
    }

    private static String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Long id : ids) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id);
        }
        return builder.toString();
    }

    private static long ttl(long value, long defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private static int parseInt(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String describe(InventoryResultCode code) {
        switch (code) {
            case OK:
                return "预扣成功";
            case DUPLICATE:
                return "重复受理或乘客已有有效资格";
            case NO_TICKET:
                return "余票不足";
            case NOT_READY:
                return "库存未预热";
            default:
                return code.name();
        }
    }
}
