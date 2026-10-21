-- I01 事务消息探针表（仅联调使用，I03 建正式业务表后删除）
-- 用法：在 train-business 库执行；脚本可重复执行。

CREATE TABLE IF NOT EXISTS `tx_probe` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `transaction_id` VARCHAR(64)     NOT NULL COMMENT 'RocketMQ 事务ID',
  `biz_id`         VARCHAR(64)              DEFAULT NULL COMMENT '业务ID（探针请求标识）',
  `state`          VARCHAR(16)     NOT NULL COMMENT '本地事务结果：COMMIT / ROLLBACK',
  `remark`         VARCHAR(255)             DEFAULT NULL COMMENT '备注',
  `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tx_probe_transaction` (`transaction_id`),
  KEY `idx_tx_probe_biz` (`biz_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='事务消息探针表（I01 临时）';
