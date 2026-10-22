-- I05 预扣：资格占位 + 余票扣减 + 座位区间占用 + hold 凭据，单次 RTT 原子完成。
-- KEYS[1] purchase:req:{requestId}
-- ARGV[1]  orderId
-- ARGV[2]  requestId
-- ARGV[3]  qualKeyPrefix        qual:{date}:{trainCode}:
-- ARGV[4]  ticketKey            inv:t:{date}:{trainCode}:{start}:{end}
-- ARGV[5]  seatKeyPrefix        inv:seat:{date}:{trainCode}:
-- ARGV[6]  poolKey              inv:pool:{date}:{trainCode}:{seatType}:{start}:{end}
-- ARGV[7]  seatTypeField        ydz / edz / rw / yw
-- ARGV[8]  seatCount
-- ARGV[9]  startIndex
-- ARGV[10] endIndex
-- ARGV[11] passengerIds         逗号分隔
-- ARGV[12] qualTtlMs
-- ARGV[13] holdKeyPrefix        hold:
-- ARGV[14] holdToken
-- ARGV[15] holdTtlMs
-- ARGV[16] requestTtlMs
-- ARGV[17] poolScanMax
-- 返回 {状态, orderId, holdToken, 座位ID逗号分隔}

local reqKey = KEYS[1]
local orderId = ARGV[1]
local requestId = ARGV[2]
local qualPrefix = ARGV[3]
local ticketKey = ARGV[4]
local seatKeyPrefix = ARGV[5]
local poolKey = ARGV[6]
local seatTypeField = ARGV[7]
local seatCount = tonumber(ARGV[8])
local startIndex = tonumber(ARGV[9])
local endIndex = tonumber(ARGV[10])
local passengerIds = ARGV[11]
local qualTtlMs = tonumber(ARGV[12])
local holdKeyPrefix = ARGV[13]
local holdToken = ARGV[14]
local holdTtlMs = tonumber(ARGV[15])
local requestTtlMs = tonumber(ARGV[16])
local poolScanMax = tonumber(ARGV[17])

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

-- 1. 受理幂等：同一 requestId 只允许成功一次
if redis.call('EXISTS', reqKey) == 1 then
    return {'DUPLICATE',
            redis.call('HGET', reqKey, 'orderId') or '',
            redis.call('HGET', reqKey, 'holdToken') or '',
            ''}
end

-- 2. 资格占位：任一乘客已有资格则整体失败
local passengers = split(passengerIds)
local qualified = {}
for _, pid in ipairs(passengers) do
    local qualKey = qualPrefix .. pid
    local ok = redis.call('SET', qualKey, orderId, 'NX', 'PX', qualTtlMs)
    if not ok then
        for _, done in ipairs(qualified) do
            redis.call('DEL', qualPrefix .. done)
        end
        return {'DUPLICATE', '', '', ''}
    end
    table.insert(qualified, pid)
end

-- 3. 余票判断
if redis.call('EXISTS', ticketKey) == 0 then
    for _, pid in ipairs(qualified) do
        redis.call('DEL', qualPrefix .. pid)
    end
    return {'NOT_READY', '', '', ''}
end
local available = tonumber(redis.call('HGET', ticketKey, seatTypeField) or '-1')
if available == nil or available < seatCount then
    for _, pid in ipairs(qualified) do
        redis.call('DEL', qualPrefix .. pid)
    end
    return {'NO_TICKET', '', '', ''}
end

-- 4. 从候选池选座：逐段校验 [startIndex-1, endIndex-1)
local firstSeg = startIndex - 1
local lastSeg = endIndex - 2
local claimed = {}
local scanned = 0
while #claimed < seatCount and scanned < poolScanMax do
    local seatId = redis.call('LPOP', poolKey)
    if not seatId then
        break
    end
    scanned = scanned + 1
    local seatKey = seatKeyPrefix .. seatId
    local conflict = false
    for i = firstSeg, lastSeg do
        if redis.call('HEXISTS', seatKey, 'seg:' .. i) == 1 then
            conflict = true
            break
        end
    end
    if not conflict then
        for i = firstSeg, lastSeg do
            redis.call('HSET', seatKey, 'seg:' .. i, orderId)
        end
        table.insert(claimed, seatId)
    end
end

if #claimed < seatCount then
    -- 回滚已占座位、资格，并把可用座位放回池中
    for _, seatId in ipairs(claimed) do
        local seatKey = seatKeyPrefix .. seatId
        for i = firstSeg, lastSeg do
            if redis.call('HGET', seatKey, 'seg:' .. i) == orderId then
                redis.call('HDEL', seatKey, 'seg:' .. i)
            end
        end
        redis.call('RPUSH', poolKey, seatId)
    end
    for _, pid in ipairs(qualified) do
        if redis.call('GET', qualPrefix .. pid) == orderId then
            redis.call('DEL', qualPrefix .. pid)
        end
    end
    return {'NO_TICKET', '', '', ''}
end

-- 5. 余票扣减
redis.call('HINCRBY', ticketKey, seatTypeField, -seatCount)

-- 6. hold 凭据（5 分钟）
local holdKey = holdKeyPrefix .. holdToken
redis.call('HMSET', holdKey,
        'orderId', orderId,
        'requestId', requestId,
        'seatType', seatTypeField,
        'startIndex', startIndex,
        'endIndex', endIndex,
        'seatIds', table.concat(claimed, ','),
        'passengerIds', passengerIds)
redis.call('PEXPIRE', holdKey, holdTtlMs)

-- 7. 受理幂等状态
redis.call('HMSET', reqKey, 'state', 'RESERVED', 'orderId', orderId, 'holdToken', holdToken)
redis.call('PEXPIRE', reqKey, requestTtlMs)

return {'OK', orderId, holdToken, table.concat(claimed, ',')}
