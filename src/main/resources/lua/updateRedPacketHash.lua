local key = KEYS[1]
local amount = tonumber(ARGV[1])
local expireSec = tonumber(ARGV[2])

if not amount or amount <= 0 then
    return -4
end

if redis.call('EXISTS', key) == 0 then
    return -1
end

local remainAmount = tonumber(redis.call('HGET', key, 'remainAmount'))
local remainCount = tonumber(redis.call('HGET', key, 'remainCount'))

if not remainAmount or not remainCount or remainCount <= 0 then
    return -2
end

if remainAmount < amount then
    return -3
end

redis.call('HSET', key, 'remainAmount', tostring(remainAmount - amount))
redis.call('HSET', key, 'remainCount', tostring(remainCount - 1))

if remainCount - 1 == 0 then
    redis.call('HSET', key, 'status', '3')
else
    redis.call('HSET', key, 'status', '2')
end

if expireSec and expireSec > 0 then
    redis.call('EXPIRE', key, tonumber(expireSec))
end

return 0