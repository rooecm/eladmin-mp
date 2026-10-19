-- KEYS[1] 含 Token 摘要；已有会话不覆盖。写入 Hash 与设置 TTL 原子完成。
-- ARGV 依次为会话 ID、用户 ID、创建秒数、绝对到期秒数、认证版本、客户端、IP、UA、TTL 秒数。
if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
redis.call('HSET', KEYS[1], 'sessionId', ARGV[1], 'userId', ARGV[2],
  'createdAt', ARGV[3], 'absoluteExpireAt', ARGV[4], 'authVersion', ARGV[5],
  'clientType', ARGV[6], 'loginIp', ARGV[7], 'userAgent', ARGV[8])
redis.call('EXPIRE', KEYS[1], ARGV[9])
return 1
