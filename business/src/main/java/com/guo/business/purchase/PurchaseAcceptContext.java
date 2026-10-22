package com.guo.business.purchase;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 受理上下文（I06）。
 *
 * <p>需要同时传给 RocketMQ 事务消息的本地事务与回查，因此统一在
 * {@link #toArgs()} / {@link #fromArgs(Map)} 中做扁平化，避免依赖对象序列化。</p>
 */
@Getter
@Setter
public class PurchaseAcceptContext {

    static final String ARG_REQUEST_ID = "requestId";
    static final String ARG_ORDER_ID = "orderId";
    static final String ARG_MEMBER_ID = "memberId";
    static final String ARG_DATE = "dateText";
    static final String ARG_TRAIN_CODE = "trainCode";
    static final String ARG_START = "start";
    static final String ARG_END = "end";
    static final String ARG_SEAT_TYPE = "seatType";
    static final String ARG_DAILY_TRAIN_TICKET_ID = "dailyTrainTicketId";
    static final String ARG_START_INDEX = "startIndex";
    static final String ARG_END_INDEX = "endIndex";
    static final String ARG_HOLD_TOKEN = "holdToken";
    static final String ARG_SEAT_IDS = "seatIds";
    static final String ARG_PASSENGER_IDS = "passengerIds";
    static final String ARG_TICKETS = "tickets";
    static final String ARG_PAYLOAD_HASH = "payloadHash";
    static final String ARG_PERSISTED = "persisted";
    static final String ARG_FAILURE = "failure";

    private String requestId;

    private Long orderId;

    private Long memberId;

    /** yyyy-MM-dd */
    private String dateText;

    private String trainCode;

    private String start;

    private String end;

    /** 席别编码 1/2/3/4 */
    private String seatType;

    private Long dailyTrainTicketId;

    private int startIndex;

    private int endIndex;

    private String holdToken;

    private List<Long> seatIds = new ArrayList<>();

    private List<Long> passengerIds = new ArrayList<>();

    /** 车票明细 JSON，原样落 confirm_order.tickets */
    private String tickets;

    private String payloadHash;

    public Map<String, Object> toArgs() {
        Map<String, Object> args = new HashMap<>();
        args.put(ARG_REQUEST_ID, requestId);
        args.put(ARG_ORDER_ID, String.valueOf(orderId));
        args.put(ARG_MEMBER_ID, String.valueOf(memberId));
        args.put(ARG_DATE, dateText);
        args.put(ARG_TRAIN_CODE, trainCode);
        args.put(ARG_START, start);
        args.put(ARG_END, end);
        args.put(ARG_SEAT_TYPE, seatType);
        args.put(ARG_DAILY_TRAIN_TICKET_ID, String.valueOf(dailyTrainTicketId));
        args.put(ARG_START_INDEX, String.valueOf(startIndex));
        args.put(ARG_END_INDEX, String.valueOf(endIndex));
        args.put(ARG_HOLD_TOKEN, holdToken);
        args.put(ARG_SEAT_IDS, join(seatIds));
        args.put(ARG_PASSENGER_IDS, join(passengerIds));
        args.put(ARG_TICKETS, tickets);
        args.put(ARG_PAYLOAD_HASH, payloadHash);
        args.put(ARG_PERSISTED, Boolean.FALSE);
        return args;
    }

    public static PurchaseAcceptContext fromArgs(Map<?, ?> args) {
        if (args == null) {
            return null;
        }
        PurchaseAcceptContext ctx = new PurchaseAcceptContext();
        ctx.setRequestId(string(args.get(ARG_REQUEST_ID)));
        ctx.setOrderId(longValue(args.get(ARG_ORDER_ID)));
        ctx.setMemberId(longValue(args.get(ARG_MEMBER_ID)));
        ctx.setDateText(string(args.get(ARG_DATE)));
        ctx.setTrainCode(string(args.get(ARG_TRAIN_CODE)));
        ctx.setStart(string(args.get(ARG_START)));
        ctx.setEnd(string(args.get(ARG_END)));
        ctx.setSeatType(string(args.get(ARG_SEAT_TYPE)));
        ctx.setDailyTrainTicketId(longValue(args.get(ARG_DAILY_TRAIN_TICKET_ID)));
        ctx.setStartIndex(intValue(args.get(ARG_START_INDEX)));
        ctx.setEndIndex(intValue(args.get(ARG_END_INDEX)));
        ctx.setHoldToken(string(args.get(ARG_HOLD_TOKEN)));
        ctx.setSeatIds(longList(string(args.get(ARG_SEAT_IDS))));
        ctx.setPassengerIds(longList(string(args.get(ARG_PASSENGER_IDS))));
        ctx.setTickets(string(args.get(ARG_TICKETS)));
        ctx.setPayloadHash(string(args.get(ARG_PAYLOAD_HASH)));
        return ctx;
    }

    /**
     * 事务消息体：消费端（I08）只关心订单、请求与 hold 凭据。
     */
    public String toMessageJson() {
        JSONObject body = new JSONObject(true);
        body.put("orderId", orderId);
        body.put("requestId", requestId);
        body.put("holdToken", holdToken);
        return JSON.toJSONString(body);
    }

    public static String requestIdOfMessage(String body) {
        try {
            JSONObject json = JSON.parseObject(body);
            return json == null ? null : json.getString("requestId");
        } catch (Exception e) {
            return null;
        }
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longValue(Object value) {
        String text = string(value);
        return StrUtil.isBlank(text) ? null : Long.valueOf(text);
    }

    private static int intValue(Object value) {
        String text = string(value);
        return StrUtil.isBlank(text) ? 0 : Integer.parseInt(text);
    }

    private static String join(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private static List<Long> longList(String csv) {
        List<Long> values = new ArrayList<>();
        if (StrUtil.isBlank(csv)) {
            return values;
        }
        for (String item : csv.split(",")) {
            if (StrUtil.isNotBlank(item)) {
                values.add(Long.valueOf(item.trim()));
            }
        }
        return values;
    }
}
