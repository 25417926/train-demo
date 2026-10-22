-- I05 释放：按 orderId 比较后归还资格与座位，rel 标记保证重复释放无副作用。
-- KEYS[1] rel:{releaseKey}
-- ARGV[1]  orderId
-- ARGV[2]  qualKeyPrefix        qual:{date}:{trainCode}:
-- ARGV[3]  passengerIds         逗号分隔
-- ARGV[4]  seatKeyPrefix        inv:seat:{date}:{trainCode}:
-- ARGV[5]  seatIds              逗号分隔
-- ARGV[6]  ticketKey            inv:t:{date}:{trainCode}:{start}:{end}
-- ARGV[7]  seatTypeField        ydz / edz / rw / yw
-- ARGV[8]  startIndex
-- ARGV[9]  endIndex
-- ARGV[10] poolKey
-- ARGV[11] releaseTtlSeconds
-- 返回 {状态, 实际归还座位数}

local relKey = KEYS[1]
local orderId = ARGV[1]
local qualPrefix = ARGV[2]
local passengerIds = ARGV[3]
local seatKeyPrefix = ARGV[4]
local seatIds = ARGV[5]
local ticketKey = ARGV[6]
local seatTypeField = ARGV[7]
local startIndex = tonumber(ARGV[8])
local endIndex = tonumber(ARGV[9])
local poolKey = ARGV[10]
local releaseTtlSeconds = tonumber(ARGV[11])

local function split(csv)
    local list = {}
    if csv == nil or csv == '' then
        return list
    end
    for item in string.gmatch(csv, '([^,]+)') do
        table.insert(list, item)
    end
    return list
end

-- 1. 释放幂等：同一 releaseKey 只生效一次
if redis.call('EXISTS', relKey) == 1 then
    return {'ALREADY', '0'}
end

-- 2. 资格：只有仍属于本订单才删除
for _, pid in ipairs(split(passengerIds)) do
    local qualKey = qualPrefix .. pid
    if redis.call('GET', qualKey) == orderId then
        redis.call('DEL', qualKey)
    end
end

-- 3. 座位：按 orderId 比较删除，旧订单不会释放新占位
local firstSeg = startIndex - 1
local lastSeg = endIndex - 2
local releasedSeats = 0
for _, seatId in ipairs(split(seatIds)) do
    local seatKey = seatKeyPrefix .. seatId
    local released = false
    for i = firstSeg, lastSeg do
        if redis.call('HGET', seatKey, 'seg:' .. i) == orderId then
            redis.call('HDEL', seatKey, 'seg:' .. i)
            released = true
        end
    end
    if released then
        releasedSeats = releasedSeats + 1
        redis.call('RPUSH', poolKey, seatId)
    end
    if redis.call('HLEN', seatKey) == 0 then
        redis.call('DEL', seatKey)
    end
end

-- 4. 余票回补：按实际归还的座位数补偿，避免旧订单多还库存
if releasedSeats > 0 and redis.call('EXISTS', ticketKey) == 1 then
    redis.call('HINCRBY', ticketKey, seatTypeField, releasedSeats)
end

-- 5. 释放幂等标记
redis.call('SET', relKey, '1', 'EX', releaseTtlSeconds)

return {'OK', tostring(releasedSeats)}
