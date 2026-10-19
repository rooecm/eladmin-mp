-- KEYS[1] access key, KEYS[2] refresh key, KEYS[3] session-id index.
-- ARGV: accessTtl, refreshTtl, sessionId, userId, createdAt, authVersion, clientType, ip, userAgent, absoluteExpireAt,
--   accessDigest, refreshDigest.
-- Access and refresh keys hold only the session id; the index is the single source of session data, so rotation
-- never has to copy fields between credentials. All writes are single-field HSET for broad Redis compatibility.
if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
local now = tonumber(ARGV[5])
local absoluteExpireAt = tonumber(ARGV[10])
-- EXPIRE 只接受整数秒，取整避免浮点参数被拒绝。
local accessTtl = math.floor(math.min(tonumber(ARGV[1]), absoluteExpireAt - now))
local refreshTtl = math.floor(math.min(tonumber(ARGV[2]), absoluteExpireAt - now))
if accessTtl <= 0 or refreshTtl <= 0 then return 0 end
redis.call('SET', KEYS[1], ARGV[3], 'EX', accessTtl)
redis.call('SET', KEYS[2], ARGV[3], 'EX', refreshTtl)
redis.call('HSET', KEYS[3], 'userId', ARGV[4])
redis.call('HSET', KEYS[3], 'createdAt', ARGV[5])
redis.call('HSET', KEYS[3], 'authVersion', ARGV[6])
redis.call('HSET', KEYS[3], 'clientType', ARGV[7])
redis.call('HSET', KEYS[3], 'loginIp', ARGV[8])
redis.call('HSET', KEYS[3], 'userAgent', ARGV[9])
redis.call('HSET', KEYS[3], 'absoluteExpireAt', ARGV[10])
redis.call('HSET', KEYS[3], 'accessDigest', ARGV[11])
redis.call('HSET', KEYS[3], 'refreshDigest', ARGV[12])
redis.call('EXPIRE', KEYS[3], refreshTtl)
return 1