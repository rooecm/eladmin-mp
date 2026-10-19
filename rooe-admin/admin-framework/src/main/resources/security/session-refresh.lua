-- KEYS[1] old refresh key, KEYS[2] new access key, KEYS[3] new refresh key, KEYS[4] used-marker key,
--   KEYS[5] new session-id index.
-- ARGV: newAccessDigest, newRefreshDigest, newSessionId, now, accessTtl, refreshTtl, usedMarkerTtl.
-- Rotates both credentials atomically under a fresh session identity. The old session index is derived from the
-- refresh value, so callers only need the digest they were handed. A missing refresh key is either expiry or
-- replay; the used marker tells them apart so a replayed credential is reported instead of silently ignored.
-- Credentials are plain strings holding the session id; the index hash is the single copy of session data.
local oldSessionId = redis.call('GET', KEYS[1])
if not oldSessionId then
  if redis.call('EXISTS', KEYS[4]) == 1 then return {'REPLAY'} end
  return {'NOT_FOUND'}
end
local oldIndexKey = 'rooe:auth:sid:' .. oldSessionId
if redis.call('EXISTS', oldIndexKey) == 0 then
  redis.call('DEL', KEYS[1])
  return {'NOT_FOUND'}
end
local userId = redis.call('HGET', oldIndexKey, 'userId')
local createdAt = redis.call('HGET', oldIndexKey, 'createdAt')
local authVersion = redis.call('HGET', oldIndexKey, 'authVersion')
local clientType = redis.call('HGET', oldIndexKey, 'clientType')
local loginIp = redis.call('HGET', oldIndexKey, 'loginIp')
local userAgent = redis.call('HGET', oldIndexKey, 'userAgent')
local expiry = tonumber(redis.call('HGET', oldIndexKey, 'absoluteExpireAt'))
local oldAccessDigest = redis.call('HGET', oldIndexKey, 'accessDigest')
local now = tonumber(ARGV[4])
local oldAccessKey = oldAccessDigest and ('rooe:auth:session:' .. oldAccessDigest) or nil
if not expiry or now >= expiry then
  if oldAccessKey then redis.call('DEL', oldAccessKey) end
  redis.call('DEL', KEYS[1], oldIndexKey)
  return {'EXPIRED'}
end
-- EXPIRE 只接受整数秒，取整避免浮点参数被拒绝。
local accessTtl = math.floor(math.min(tonumber(ARGV[5]), expiry - now))
local refreshTtl = math.floor(math.min(tonumber(ARGV[6]), expiry - now))
if accessTtl <= 0 or refreshTtl <= 0 then
  if oldAccessKey then redis.call('DEL', oldAccessKey) end
  redis.call('DEL', KEYS[1], oldIndexKey)
  return {'EXPIRED'}
end
-- Retire the previous access credential and move the session onto a new identity with fresh digests.
if oldAccessKey then redis.call('DEL', oldAccessKey) end
redis.call('SET', KEYS[2], ARGV[3], 'EX', accessTtl)
redis.call('SET', KEYS[3], ARGV[3], 'EX', refreshTtl)
redis.call('DEL', KEYS[1], oldIndexKey)
redis.call('SET', KEYS[4], '1', 'EX', math.floor(tonumber(ARGV[7])))
redis.call('HSET', KEYS[5], 'userId', userId)
redis.call('HSET', KEYS[5], 'createdAt', createdAt)
redis.call('HSET', KEYS[5], 'authVersion', authVersion)
redis.call('HSET', KEYS[5], 'clientType', clientType or '')
redis.call('HSET', KEYS[5], 'loginIp', loginIp or '')
redis.call('HSET', KEYS[5], 'userAgent', userAgent or '')
redis.call('HSET', KEYS[5], 'absoluteExpireAt', tostring(expiry))
redis.call('HSET', KEYS[5], 'accessDigest', ARGV[1])
redis.call('HSET', KEYS[5], 'refreshDigest', ARGV[2])
redis.call('EXPIRE', KEYS[5], refreshTtl)
return {'OK', ARGV[3], userId, createdAt, tostring(expiry), authVersion, tostring(accessTtl)}