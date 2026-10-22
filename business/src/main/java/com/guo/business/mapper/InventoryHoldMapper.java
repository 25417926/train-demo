package com.guo.business.mapper;

import com.guo.business.domain.InventoryHold;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

@Mapper
public interface InventoryHoldMapper {

    @Insert("insert into inventory_hold (id, hold_token, request_id, order_id, member_id, passenger_id, `date`, " +
            "train_code, seat_id, seat_type, start_index, end_index, status, create_time) values (" +
            "#{id}, #{holdToken}, #{requestId}, #{orderId}, #{memberId}, #{passengerId}, #{date}, " +
            "#{trainCode}, #{seatId}, #{seatType}, #{startIndex}, #{endIndex}, #{status}, #{createTime})")
    int insert(InventoryHold hold);

    @Select("select id, hold_token as holdToken, request_id as requestId, order_id as orderId, " +
            "member_id as memberId, passenger_id as passengerId, `date` as `date`, train_code as trainCode, " +
            "seat_id as seatId, seat_type as seatType, start_index as startIndex, end_index as endIndex, " +
            "status, create_time as createTime, release_time as releaseTime " +
            "from inventory_hold where order_id = #{orderId} and status = 'H' order by id asc")
    List<InventoryHold> selectHeldByOrderId(@Param("orderId") Long orderId);

    @Select("select id, hold_token as holdToken, request_id as requestId, order_id as orderId, " +
            "member_id as memberId, passenger_id as passengerId, `date` as `date`, train_code as trainCode, " +
            "seat_id as seatId, seat_type as seatType, start_index as startIndex, end_index as endIndex, " +
            "status, create_time as createTime, release_time as releaseTime " +
            "from inventory_hold where `date` = #{date} and train_code = #{trainCode} and status = 'H' " +
            "order by id asc")
    List<InventoryHold> selectHeldByTrain(@Param("date") Date date, @Param("trainCode") String trainCode);
}
