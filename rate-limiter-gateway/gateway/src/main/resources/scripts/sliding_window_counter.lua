-- Sliding Window Counter rate limiter
-- Blends the previous and current fixed window counts, weighted by how
-- far we are into the current window. O(1) memory per key, much cheaper
-- than the log variant, with only a small approximation error.
--
-- KEYS[1] = base key (window buckets derived from it)
-- ARGV[1] = limit
-- ARGV[2] = window_secs
-- ARGV[3] = now (epoch secs)

local base_key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

local current_window_id = math.floor(now / window)
local prev_window_id = current_window_id - 1

local current_key = base_key .. ':' .. current_window_id
local prev_key = base_key .. ':' .. prev_window_id

local current_count = tonumber(redis.call('GET', current_key) or '0')
local prev_count = tonumber(redis.call('GET', prev_key) or '0')

-- Position within current window, in [0, 1)
local elapsed_in_window = (now % window) / window
local weighted_count = (prev_count * (1 - elapsed_in_window)) + current_count

local allowed = 0
if weighted_count < limit then
    current_count = redis.call('INCR', current_key)
    redis.call('EXPIRE', current_key, window * 2)
    allowed = 1
    weighted_count = weighted_count + 1
end

local remaining = math.max(0, math.floor(limit - weighted_count))

local retry_after = 0
if allowed == 0 then
    retry_after = math.ceil(window * (1 - elapsed_in_window))
end

return { allowed, remaining, retry_after }
