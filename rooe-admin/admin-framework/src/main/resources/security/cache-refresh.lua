-- ARGV 为先前观察值、新 JSON、绝对到期毫秒时间戳；比较与写入在同一脚本中完成。
local current = redis.call('GET', KEYS[1])
-- 键已被其他请求替换时放弃刷新；键仍为旧值或已不存在时允许写入。
if current and current ~= ARGV[1] then return 0 end
redis.call('SET', KEYS[1], ARGV[2], 'PXAT', ARGV[3])
return 1
