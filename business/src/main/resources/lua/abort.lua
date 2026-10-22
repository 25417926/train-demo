-- I06 受理失败清理：只有幂等状态与 hold 仍属于当前订单时才删除，避免误删重试后的新占位。
-- KEYS[1] purchase:req:{requestId}
-- KEYS[2] hold:{holdToken}
-- ARGV[1] orderId
-- 返回实际清理的 key 数量

local cleaned = 0

if redis.call('HGET', KEYS[1], 'orderId') == ARGV[1] then
    redis.call('DEL', KEYS[1])
    cleaned = cleaned + 1
end

if redis.call('HGET', KEYS[2], 'orderId') == ARGV[1] then
    redis.call('DEL', KEYS[2])
    cleaned = cleaned + 1
end

return cleaned
