#创建一个用户
CREATE USER 'train-member'@'%' IDENTIFIED BY '123456';
#为用户添加一个框架的权限，这里是给予全部权限
GRANT ALL PRIVILEGES ON train.* TO 'train-member'@'%' ;
#刷新数据库的权限缓存
FLUSH PRIVILEGES;
#查看权限
SHOW GRANTS FOR 'train-member'@'%';
