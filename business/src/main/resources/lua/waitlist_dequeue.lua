-- 候补出队（I15）：真正移除才减少等待计数，且计数不为负。
-- KEYS[1]=waitlist:{key}  KEYS[2]=waitlist:count:{key}
-- ARGV[1]=applicationId
if redis.call('ZREM', KEYS[1], ARGV[1]) == 1 then
  local count = redis.call('DECR', KEYS[2])
  if count < 0 then
    redis.call('SET', KEYS[2], '0')
    return 0
  end
  return 1
end
return 0
