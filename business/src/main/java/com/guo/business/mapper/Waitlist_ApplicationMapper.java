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

    /**
     * 匹配事务内加锁读取，确保同一申请只会被建单一次。
     */
    @Select("select " + COLUMNS + " from waitlist_application where id = #{id} for update")
    Waitlist_Application selectByIdForUpdate(@Param("id") Long id);

    /**
     * 匹配成功：W → D，记录补位订单与支付截止时间，active_key 置空。
     */
    @Update("update waitlist_application set `status` = 'D', matched_order_id = #{orderId}, "
            + "expire_at = #{expireAt}, active_key = null, update_time = #{updateTime} "
            + "where id = #{id} and `status` = 'W'")
    int markMatched(@Param("id") Long id,
                    @Param("orderId") Long orderId,
                    @Param("expireAt") Date expireAt,
                    @Param("updateTime") Date updateTime);

    /**
     * 资格转移：把该乘客同票档仍占用的资格绑定到补位订单并置为已消费。
     */
    @Update("update purchase_qualification set order_id = #{orderId}, `status` = 'C', active_key = null, "
            + "version = version + 1, update_time = now(3) "
            + "where passenger_id = #{passengerId} and `date` = #{date} and train_code = #{trainCode} "
            + "and `start` = #{start} and `end` = #{end} and seat_type = #{seatType} and `status` = 'H'")
    int transferQualification(@Param("orderId") Long orderId,
                              @Param("passengerId") Long passengerId,
                              @Param("date") Date date,
                              @Param("trainCode") String trainCode,
                              @Param("start") String start,
                              @Param("end") String end,
                              @Param("seatType") String seatType);

    /**
     * 补位订单绑定的已消费资格 id（写 confirm_order.qualification_id）。
     */
    @Select("select id from purchase_qualification where order_id = #{orderId} and `status` = 'C' "
            + "order by id asc limit 1")
    Long selectQualificationIdByOrderId(@Param("orderId") Long orderId);

    @Select("select " + COLUMNS + " from waitlist_application where request_id = #{requestId}")
    Waitlist_Application selectByRequestId(@Param("requestId") String requestId);

    @Select("select " + COLUMNS + " from waitlist_application where active_key = #{activeKey}")
    Waitlist_Application selectByActiveKey(@Param("activeKey") String activeKey);

    @Select("select " + COLUMNS + " from waitlist_application where member_id = #{memberId}"
            + "<if test='status != null and status != \"\"'> and `status` = #{status}</if>"
            + " order by create_time desc</script>")
    List<Waitlist_Application> selectByMember(@Param("memberId") Long memberId,
                                              @Param("status") String status);

    /**
     * I17 后台候补列表：按日期/车次/区间/席别/状态筛选，创建时间倒序。
     */
    @Select("<script>select " + COLUMNS + " from waitlist_application where 1 = 1"
            + "<if test='date != null'> and `date` = #{date}</if>"
            + "<if test='trainCode != null and trainCode != \"\"'> and train_code = #{trainCode}</if>"
            + "<if test='start != null and start != \"\"'> and `start` = #{start}</if>"
            + "<if test='end != null and end != \"\"'> and `end` = #{end}</if>"
            + "<if test='seatType != null and seatType != \"\"'> and seat_type = #{seatType}</if>"
            + "<if test='status != null and status != \"\"'> and `status` = #{status}</if>"
            + " order by create_time desc</script>")
    List<Waitlist_Application> selectByQuery(@Param("date") Date date,
                                             @Param("trainCode") String trainCode,
                                             @Param("start") String start,
                                             @Param("end") String end,
                                             @Param("seatType") String seatType,
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
