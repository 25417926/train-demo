-- 候补出队（I16）：取最小 seq_no 的申请并同步等待计数。
-- Redis 3.x 无 ZPOPMIN，用 ZRANGE + ZREM 在脚本内保持原子。
-- KEYS[1]=waitlist:{key}  KEYS[2]=waitlist:count:{key}
-- 返回 applicationId；队列为空返回 nil
local head = redis.call('ZRANGE', KEYS[1], 0, 0)
if #head == 0 then
  return nil
end
local member = head[1]
redis.call('ZREM', KEYS[1], member)
local count = redis.call('DECR', KEYS[2])
if count < 0 then
  redis.call('SET', KEYS[2], '0')
end
return member
