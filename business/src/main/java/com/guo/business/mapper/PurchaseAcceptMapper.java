package com.guo.business.mapper;

import com.guo.business.domain.Confirm_Order;
import com.guo.business.purchase.model.OrderOutboxRecord;
import com.guo.business.purchase.model.PurchaseQualificationRecord;
import com.guo.business.purchase.model.PurchaseRequestRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 受理链路写入（I06）：purchase_request / purchase_qualification / confirm_order(INIT) / order_outbox。
 *
 * <p>四张表必须在同一本地事务内提交，由 {@link com.guo.business.purchase.PurchaseTxWriter} 控制。</p>
 */
public interface PurchaseAcceptMapper {

    @Insert("insert into purchase_request " +
            "(id, request_id, member_id, payload_hash, `status`, order_id, qualification_id, create_time, update_time) " +
            "values (#{id}, #{requestId}, #{memberId}, #{payloadHash}, #{status}, #{orderId}, #{qualificationId}, " +
            "#{createTime}, #{updateTime})")
    int insertPurchaseRequest(PurchaseRequestRecord record);

    @Insert("insert into purchase_qualification " +
            "(id, request_id, member_id, passenger_id, `date`, train_code, `start`, `end`, seat_type, order_id, " +
            "`status`, active_key, expire_at, version, create_time, update_time) " +
            "values (#{id}, #{requestId}, #{memberId}, #{passengerId}, #{date}, #{trainCode}, #{start}, #{end}, " +
            "#{seatType}, #{orderId}, #{status}, #{activeKey}, #{expireAt}, 0, #{createTime}, #{updateTime})")
    int insertQualification(PurchaseQualificationRecord record);

    /**
     * 订单 INIT：除基础列外补齐 request_id / qualification_id / expire_at / ticket_status / version。
     */
    @Insert("insert into confirm_order " +
            "(id, member_id, `date`, train_code, `start`, `end`, daily_train_ticket_id, `status`, tickets, " +
            "create_time, update_time, request_id, qualification_id, expire_at, ticket_status, version) " +
            "values (#{id}, #{memberId}, #{date}, #{trainCode}, #{start}, #{end}, #{dailyTrainTicketId}, #{status}, " +
            "#{tickets}, #{createTime}, #{updateTime}, #{requestId}, #{qualificationId}, #{expireAt}, " +
            "#{ticketStatus}, #{version})")
    int insertInitOrder(Confirm_Order order);

    @Insert("insert into order_outbox " +
            "(id, event_id, aggregate_type, aggregate_id, event_type, payload, `status`, retry_count, " +
            "next_retry_at, create_time, update_time) " +
            "values (#{id}, #{eventId}, #{aggregateType}, #{aggregateId}, #{eventType}, #{payload}, #{status}, " +
            "#{retryCount}, #{nextRetryAt}, #{createTime}, #{updateTime})")
    int insertOutbox(OrderOutboxRecord record);

    @Select("select count(1) from purchase_request where request_id = #{requestId}")
    int countByRequestId(@Param("requestId") String requestId);

    @Select("select order_id from purchase_request where request_id = #{requestId} limit 1")
    Long selectOrderIdByRequestId(@Param("requestId") String requestId);
}
