-- 候补匹配合并锁释放（I16）：只删除自己持有的锁，避免误删后继实例的锁。
-- KEYS[1]=waitlist:match:key:{key}  ARGV[1]=token
if redis.call('GET', KEYS[1]) == ARGV[1] then
  return redis.call('DEL', KEYS[1])
end
return 0
