-- 候补入队（I15）：首次加入 ZSet 才增加等待计数，重放不重复计数。
-- KEYS[1]=waitlist:{key}  KEYS[2]=waitlist:count:{key}
-- ARGV[1]=seq_no  ARGV[2]=applicationId
if redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2]) == 1 then
  redis.call('INCR', KEYS[2])
  return 1
end
return 0
