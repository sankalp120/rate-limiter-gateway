-- Sliding Window Log rate limiter
-- Stores a timestamped entry per request in a sorted set and counts
-- entries within the trailing window. Exact (no boundary burst issue
-- like fixed windows) but O(window size) memory per key.
--
-- KEYS[1] = sorted set key
-- ARGV[1] = limit
-- ARGV[2] = window_secs
-- ARGV[3] = now (epoch millis)

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2]) * 1000
local now = tonumber(ARGV[3])
local window_start = now - window_ms

-- Drop entries that have aged out of the window
redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)

local current_count = redis.call('ZCARD', key)

local allowed = 0
if current_count < limit then
    -- Member must be unique even for same-millisecond requests
    local member = now .. '-' .. redis.call('INCR', key .. ':seq')
    redis.call('ZADD', key, now, member)
    allowed = 1
    current_count = current_count + 1
end

redis.call('PEXPIRE', key, window_ms)
redis.call('EXPIRE', key .. ':seq', math.ceil(window_ms / 1000) + 1)

local remaining = math.max(0, limit - current_count)

local retry_after = 0
if allowed == 0 then
    -- Oldest entry tells us when a slot frees up
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    if oldest[2] ~= nil then
        retry_after = math.ceil((tonumber(oldest[2]) + window_ms - now) / 1000)
    else
        retry_after = math.ceil(window_ms / 1000)
    end
end

return { allowed, remaining, retry_after }
