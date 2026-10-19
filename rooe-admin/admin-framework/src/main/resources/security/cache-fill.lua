-- KEYS[1] 为缓存键；ARGV 为 JSON、绝对到期毫秒时间戳。只填充空键，不延长原有缓存寿命。
return redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PXAT', ARGV[2]) and 1 or 0
