package com.guo.business.mapper;

import com.guo.business.domain.Waitlist_Application;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

/**
 * I15 候补申请数据访问，全部使用注解 SQL（表名与 date/start/end 需反引号）。
 */
public interface Waitlist_ApplicationMapper {

    String COLUMNS = "id, request_id, member_id, passenger_id, passenger_name, `date`, train_code, "
            + "`start`, `end`, seat_type, seq_no, `status`, active_key, matched_order_id, expire_at, "
            + "create_time, update_time";

    @Insert("insert into waitlist_application (" + COLUMNS + ") values ("
            + "#{id}, #{requestId}, #{memberId}, #{passengerId}, #{passengerName}, #{date}, #{trainCode}, "
            + "#{start}, #{end}, #{seatType}, #{seqNo}, #{status}, #{activeKey}, #{matchedOrderId}, "
            + "#{expireAt}, #{createTime}, #{updateTime})")
    int insert(Waitlist_Application record);

    @Select("select " + COLUMNS + " from waitlist_application where id = #{id}")
    Waitlist_Application selectById(@Param("id") Long id);

    @Select("select " + COLUMNS + " from waitlist_application where request_id = #{requestId}")
    Waitlist_Application selectByRequestId(@Param("requestId") String requestId);

    @Select("select " + COLUMNS + " from waitlist_application where active_key = #{activeKey}")
    Waitlist_Application selectByActiveKey(@Param("activeKey") String activeKey);

    @Select("<script>select " + COLUMNS + " from waitlist_application where member_id = #{memberId}"
            + "<if test='status != null and status != \"\"'> and `status` = #{status}</if>"
            + " order by create_time desc</script>")
    List<Waitlist_Application> selectByMember(@Param("memberId") Long memberId,
                                              @Param("status") String status);

    /**
     * 取消：仅 W 可取消，同时释放 active_key 允许重新排队。
     */
    @Update("update waitlist_application set `status` = 'C', active_key = null, update_time = #{updateTime} "
            + "where id = #{id} and member_id = #{memberId} and `status` = 'W'")
    int cancel(@Param("id") Long id,
               @Param("memberId") Long memberId,
               @Param("updateTime") Date updateTime);

    /**
     * Redis 取号器丢失时的兜底：DB 同票档最大序号 + 1。
     */
    @Select("select coalesce(max(seq_no), 0) from waitlist_application "
            + "where `date` = #{date} and train_code = #{trainCode} and `start` = #{start} "
            + "and `end` = #{end} and seat_type = #{seatType}")
    long selectMaxSeqNo(@Param("date") String date,
                        @Param("trainCode") String trainCode,
                        @Param("start") String start,
                        @Param("end") String end,
                        @Param("seatType") String seatType);
}
