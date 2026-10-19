-- KEYS[1] access key. ARGV[1] current epoch seconds.
-- Read-only session validation: access tokens never renew, so a missing credential is terminal.
-- Session data lives in the index keyed by session id, keeping one authoritative copy.
local sessionId = redis.call('GET', KEYS[1])
if not sessionId then return {'NOT_FOUND'} end
local indexKey = 'rooe:auth:sid:' .. sessionId
if redis.call('EXISTS', indexKey) == 0 then
  redis.call('DEL', KEYS[1])
  return {'NOT_FOUND'}
end
local userId = redis.call('HGET', indexKey, 'userId')
local createdAt = redis.call('HGET', indexKey, 'createdAt')
local authVersion = redis.call('HGET', indexKey, 'authVersion')
local expiry = tonumber(redis.call('HGET', indexKey, 'absoluteExpireAt'))
local now = tonumber(ARGV[1])
if not expiry or not userId or not createdAt or not authVersion or now >= expiry then
  redis.call('DEL', KEYS[1])
  return {'EXPIRED'}
end
local ttl = redis.call('TTL', KEYS[1])
return {'OK', sessionId, userId, createdAt, tostring(expiry), authVersion, tostring(ttl)}