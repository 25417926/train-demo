package com.guo.business.mapper;

import com.guo.business.purchase.model.OrderOutboxRecord;
import com.guo.business.purchase.model.SeatRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

/**
 * I12 关单落账：订单 CAS、资格/hold 释放、释放流水、到期扫描与账本回滚。
 */
@Mapper
public interface OrderCloseMapper {

    /**
     * 关单 CAS：只有仍在 I/S/H 的订单才会被关闭，重复关闭返回 0 行。
     *
     * <p>已出票/出票中的订单同步把 ticket_status 复位为 N，阻止延迟到达的
     * TICKET_CREATE 重投再次发票。</p>
     */
    @Update("update confirm_order set status = #{toStatus}, close_reason = #{reason}, "
            + "ticket_status = case when ticket_status in ('I', 'S') then 'N' else ticket_status end, "
            + "version = version + 1, update_time = now(3) "
            + "where id = #{orderId} and status in ('I', 'S', 'H')")
    int closeOrder(@Param("orderId") Long orderId,
                   @Param("toStatus") String toStatus,
                   @Param("reason") String reason);

    /**
     * 释放资格：active_key 置空后同一乘客可重新下单。
     */
    @Update("update purchase_qualification set status = 'R', active_key = null, "
            + "version = version + 1, update_time = now(3) "
            + "where order_id = #{orderId} and status in ('H', 'C')")
    int releaseQualifications(@Param("orderId") Long orderId);

    @Update("update inventory_hold set status = 'R', release_time = #{releaseTime} "
            + "where order_id = #{orderId} and status = 'H'")
    int releaseHolds(@Param("orderId") Long orderId, @Param("releaseTime") Date releaseTime);

    /**
     * 释放流水：唯一键保证同一订单同一原因只落一条。
     */
    @Insert("insert ignore into inventory_release_record "
            + "(id, idempotent_key, qualification_id, order_id, release_type, seat_count, `status`, create_time) "
            + "values (#{id}, #{idempotentKey}, #{qualificationId}, #{orderId}, #{releaseType}, #{seatCount}, "
            + "#{status}, #{createTime})")
    int insertReleaseRecord(@Param("id") Long id,
                            @Param("idempotentKey") String idempotentKey,
                            @Param("qualificationId") Long qualificationId,
                            @Param("orderId") Long orderId,
                            @Param("releaseType") String releaseType,
                            @Param("seatCount") Integer seatCount,
                            @Param("status") String status,
                            @Param("createTime") Date createTime);

    @Update("update inventory_release_record set `status` = #{status} where idempotent_key = #{idempotentKey}")
    int updateReleaseRecordStatus(@Param("idempotentKey") String idempotentKey,
                                  @Param("status") String status);

    /**
     * 兜底扫描：ZSet 丢数据时由定时任务按过期时间关单。
     */
    @Select("select id from confirm_order where status in ('I', 'S', 'H') "
            + "and expire_at is not null and expire_at < now(3) order by expire_at asc limit #{limit}")
    List<Long> selectExpiredOrderIds(@Param("limit") int limit);

    @Select("select id, sell, version from daily_train_seat where id = #{id}")
    SeatRecord selectSeat(@Param("id") Long seatId);

    @Update("update daily_train_seat set sell = #{sell}, version = version + 1, update_time = now(3) "
            + "where id = #{id} and version = #{version}")
    int casSeatSell(@Param("id") Long seatId,
                    @Param("sell") String sell,
                    @Param("version") Integer version);

    /**
     * 关单回补余票：与 I09 的扣减方向相反，字段名由白名单转换后传入。
     */
    @Update("update daily_train_ticket set ${field} = ${field} + #{delta}, update_time = now(3) "
            + "where id = #{id}")
    int increaseTicket(@Param("id") Long ticketId,
                       @Param("field") String field,
                       @Param("delta") int delta);

    @Insert("insert into order_outbox "
            + "(id, event_id, aggregate_type, aggregate_id, event_type, payload, `status`, retry_count, "
            + "next_retry_at, create_time, update_time) "
            + "values (#{id}, #{eventId}, #{aggregateType}, #{aggregateId}, #{eventType}, #{payload}, #{status}, "
            + "#{retryCount}, #{nextRetryAt}, #{createTime}, #{updateTime})")
    int insertOutbox(OrderOutboxRecord record);

    @Update("update order_outbox set `status` = 'S', update_time = now(3) "
            + "where event_id = #{eventId} and `status` = 'N'")
    int markOutboxSent(@Param("eventId") String eventId);
}
