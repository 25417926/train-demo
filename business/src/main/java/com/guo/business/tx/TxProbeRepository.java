package com.guo.business.tx;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 事务消息探针表的读写。
 *
 * <p>仅用于 I01 验证「半消息 → 本地事务 → COMMIT/ROLLBACK → 回查」链路。
 * 正式表在 I03 落库，届时删除本类与 tx_probe 表。</p>
 */
@Repository
public class TxProbeRepository {

    private static final String INSERT_SQL =
            "insert into tx_probe(transaction_id, biz_id, state, remark) values(?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public TxProbeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insert(String transactionId, String bizId, String state, String remark) {
        return jdbcTemplate.update(INSERT_SQL, transactionId, bizId, state, remark);
    }

    public int updateState(String bizId, String state, String remark) {
        return jdbcTemplate.update("update tx_probe set state = ?, remark = ? where biz_id = ?",
                state, remark, bizId);
    }

    /**
     * 回查用：按 RocketMQ 事务ID 查本地事务结果。
     */
    public String findStateByTransactionId(String transactionId) {
        List<String> states = jdbcTemplate.queryForList(
                "select state from tx_probe where transaction_id = ?", String.class, transactionId);
        return states.isEmpty() ? null : states.get(0);
    }

    public List<String> findStatesByBizId(String bizId) {
        return jdbcTemplate.queryForList(
                "select concat(transaction_id, ':', state) from tx_probe where biz_id = ? order by id",
                String.class, bizId);
    }
}
