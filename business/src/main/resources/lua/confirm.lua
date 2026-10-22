-- I05 收尾：落库成功后删除 hold 凭据，并把资格续期到支付窗口。
-- KEYS[1] hold:{holdToken}
-- ARGV[1] orderId
-- ARGV[2] qualKeyPrefix        qual:{date}:{trainCode}:
-- ARGV[3] passengerIds         逗号分隔
-- ARGV[4] qualTtlMs
-- 返回 {状态}；MISS 表示 hold 不存在或已属于其它订单

local holdKey = KEYS[1]
local orderId = ARGV[1]
local qualPrefix = ARGV[2]
local passengerIds = ARGV[3]
local qualTtlMs = tonumber(ARGV[4])

if redis.call('HGET', holdKey, 'orderId') ~= orderId then
    return {'MISS'}
end

redis.call('DEL', holdKey)

if passengerIds ~= nil and passengerIds ~= '' then
    for pid in string.gmatch(passengerIds, '([^,]+)') do
        local qualKey = qualPrefix .. pid
        if redis.call('GET', qualKey) == orderId then
            redis.call('PEXPIRE', qualKey, qualTtlMs)
        end
    end
end

return {'OK'}
