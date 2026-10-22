# =========================================================================
# 1. 建库
# =========================================================================
CREATE DATABASE IF NOT EXISTS `train`          DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `train-business` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `train-batch`    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# =========================================================================
# 2. 建账号（与各模块 application.yaml 中 spring.datasource.username 对齐）
#    密码统一 123456，仅用于本地演示环境
# =========================================================================

# train-member 账号：AUTH 模块、train-member 模块都连 train 库
CREATE USER IF NOT EXISTS 'train-member'@'%' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON `train`.* TO 'train-member'@'%';

# train-business 账号：business 模块连 train-business 库
CREATE USER IF NOT EXISTS 'train-business'@'%' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON `train-business`.* TO 'train-business'@'%';

# train-batch 账号：batch 模块连 train-batch 库
CREATE USER IF NOT EXISTS 'train-batch'@'%' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON `train-batch`.* TO 'train-batch'@'%';

# 刷新权限缓存
FLUSH PRIVILEGES;

# 验证授权情况
SHOW GRANTS FOR 'train-member'@'%';
SHOW GRANTS FOR 'train-business'@'%';
SHOW GRANTS FOR 'train-batch'@'%';
