-- Token Bucket rate limiter
-- KEYS[1] = bucket key
-- ARGV[1] = capacity (max tokens / burst size)
-- ARGV[2] = refill_rate (tokens per second)
-- ARGV[3] = now (epoch seconds, float allowed)
-- ARGV[4] = requested tokens (usually 1)
--
-- State stored as a hash: { tokens = <float>, last_refill = <float> }
-- Refill is computed lazily on each call instead of a background job,
-- which is what keeps this atomic and race-free across gateway instances.

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

if tokens == nil then
    tokens = capacity
    last_refill = now
end

-- Lazily refill based on elapsed time since last touch
local elapsed = math.max(0, now - last_refill)
local refilled = math.min(capacity, tokens + (elapsed * refill_rate))

local allowed = 0
local remaining = refilled

if refilled >= requested then
    allowed = 1
    remaining = refilled - requested
else
    remaining = refilled
end

redis.call('HMSET', key, 'tokens', remaining, 'last_refill', now)
-- Expire the key well after it would naturally refill to capacity,
-- so idle tenants don't leak memory forever.
local ttl = math.ceil(capacity / refill_rate) + 60
redis.call('EXPIRE', key, ttl)

local retry_after = 0
if allowed == 0 then
    retry_after = math.ceil((requested - refilled) / refill_rate)
end

return { allowed, math.floor(remaining), retry_after }
