package com.guo.business.mapper;

import com.guo.business.domain.Confirm_Order;
import com.guo.business.purchase.model.InventoryHoldRecord;
import com.guo.business.purchase.model.OrderOutboxRecord;
import com.guo.business.purchase.model.SeatRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;

/**
 * I09 出票落库：订单 CAS、inventory_hold、座位 version CAS、余票 delta、资格消费。
 */
@Mapper
public interface OrderConfirmPersistMapper {

    @Select("select id, member_id, date, train_code, start, `end`, daily_train_ticket_id, tickets, "
            + "status, ticket_status, expire_at, request_id, version "
            + "from confirm_order where request_id = #{requestId} limit 1")
    Confirm_Order selectOrderByRequestId(@Param("requestId") String requestId);

    @Select("select id, member_id, date, train_code, start, `end`, daily_train_ticket_id, tickets, "
            + "status, ticket_status, expire_at, request_id, version "
            + "from confirm_order where id = #{orderId}")
    Confirm_Order selectOrderById(@Param("orderId") Long orderId);

    @Update("update confirm_order set status = #{toStatus}, version = version + 1, update_time = now(3) "
            + "where id = #{id} and status = #{fromStatus} and expire_at > now(3)")
    int casStatus(@Param("id") Long id,
                  @Param("fromStatus") String fromStatus,
                  @Param("toStatus") String toStatus);

    @Update("update confirm_order set status = 'S', ticket_status = 'I', expire_at = #{expireAt}, "
            + "version = version + 1, update_time = now(3) where id = #{id} and status = 'P'")
    int markSuccess(@Param("id") Long id, @Param("expireAt") Date expireAt);

    @Update("update confirm_order set status = 'F', ticket_status = 'F', close_reason = #{reason}, "
            + "version = version + 1, update_time = now(3) where id = #{id} and status = 'P'")
    int markFailed(@Param("id") Long id, @Param("reason") String reason);

    @Insert("insert ignore into inventory_hold (id, hold_token, request_id, order_id, member_id, passenger_id, "
            + "date, train_code, seat_id, seat_type, start_index, end_index, status, create_time) values "
            + "(#{id}, #{holdToken}, #{requestId}, #{orderId}, #{memberId}, #{passengerId}, #{date}, "
            + "#{trainCode}, #{seatId}, #{seatType}, #{startIndex}, #{endIndex}, #{status}, #{createTime})")
    int insertHold(InventoryHoldRecord record);

    @Select("select count(*) from inventory_hold where order_id = #{orderId}")
    int countHoldsByOrderId(@Param("orderId") Long orderId);

    @Select("select id, sell, version from daily_train_seat where id = #{id}")
    SeatRecord selectSeat(@Param("id") Long seatId);

    @Update("update daily_train_seat set sell = #{sell}, version = version + 1, update_time = now(3) "
            + "where id = #{id} and version = #{version}")
    int casSeatSell(@Param("id") Long seatId,
                    @Param("sell") String sell,
                    @Param("version") Integer version);

    @Update("update daily_train_ticket set ${field} = greatest(${field} - #{delta}, 0), update_time = now(3) "
            + "where id = #{id}")
    int decreaseTicket(@Param("id") Long ticketId,
                       @Param("field") String field,
                       @Param("delta") int delta);

    @Update("update purchase_qualification set status = 'C', update_time = now(3) "
            + "where order_id = #{orderId} and status = 'H'")
    int consumeQualifications(@Param("orderId") Long orderId);

    /**
     * I10 出票事件与出票落库同事务写入，作为审计与人工重投依据
     */
    @Insert("insert into order_outbox "
            + "(id, event_id, aggregate_type, aggregate_id, event_type, payload, `status`, retry_count, "
            + "next_retry_at, create_time, update_time) "
            + "values (#{id}, #{eventId}, #{aggregateType}, #{aggregateId}, #{eventType}, #{payload}, #{status}, "
            + "#{retryCount}, #{nextRetryAt}, #{createTime}, #{updateTime})")
    int insertOutbox(OrderOutboxRecord record);

    @Update("update confirm_order set ticket_status = 'S', version = version + 1, update_time = now(3) "
            + "where id = #{orderId} and ticket_status = 'I'")
    int markTicketSuccess(@Param("orderId") Long orderId);

    @Update("update confirm_order set ticket_status = 'F', version = version + 1, update_time = now(3) "
            + "where id = #{orderId} and ticket_status = 'I'")
    int markTicketFailed(@Param("orderId") Long orderId);

    @Update("update order_outbox set `status` = 'S', update_time = now(3) "
            + "where event_id = #{eventId} and `status` = 'N'")
    int markOutboxSent(@Param("eventId") String eventId);
}
