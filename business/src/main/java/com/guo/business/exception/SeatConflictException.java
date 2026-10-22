package com.guo.business.exception;

/**
 * I09 座位落库版本冲突：整单事务回滚后由调用方重试，超过上限按失败处理。
 */
public class SeatConflictException extends RuntimeException {

    public SeatConflictException(Long seatId) {
        super("座位落库版本冲突, seatId=" + seatId);
    }
}
