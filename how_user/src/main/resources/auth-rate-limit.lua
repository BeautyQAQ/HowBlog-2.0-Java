local limit = tonumber(ARGV[1])
local windowMillis = tonumber(ARGV[2])
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
local ttl = redis.call('PTTL', KEYS[1])
if ttl == -1 then
    redis.call('PEXPIRE', KEYS[1], windowMillis)
    ttl = windowMillis
end
if current >= limit then
    return math.max(ttl, 1)
end
local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('PEXPIRE', KEYS[1], windowMillis)
end
return 0