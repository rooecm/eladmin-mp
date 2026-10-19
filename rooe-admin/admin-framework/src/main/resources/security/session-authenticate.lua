-- ARGV 为当前秒数、空闲超时秒数、是否续期（1/0）；一次调用完成验证和可选续期。
if redis.call('EXISTS', KEYS[1]) == 0 then return {'NOT_FOUND'} end
local fields = redis.call('HMGET', KEYS[1], 'sessionId', 'userId', 'createdAt', 'absoluteExpireAt', 'authVersion')
local expiry = tonumber(fields[4])
if not expiry or not fields[1] or not fields[2] or not fields[3] or not fields[5] then
  redis.call('DEL', KEYS[1])
  return {'EXPIRED'}
end
local now = tonumber(ARGV[1])
if now >= expiry then
  redis.call('DEL', KEYS[1])
  return {'EXPIRED'}
end
local ttl = redis.call('TTL', KEYS[1])
local renewed = 0
if ARGV[3] == '1' then
  -- 续期始终受绝对到期时间限制，用户持续活动也不能突破最长生命周期。
  ttl = math.min(tonumber(ARGV[2]), expiry - now)
  redis.call('EXPIRE', KEYS[1], ttl)
  renewed = 1
end
return {'OK', fields[1], fields[2], fields[3], fields[4], fields[5], tostring(renewed), tostring(ttl)}
