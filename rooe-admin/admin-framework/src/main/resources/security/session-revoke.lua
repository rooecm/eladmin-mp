-- KEYS[1] session-id index key. ARGV: nothing.
-- Revokes every credential belonging to one session; used by logout and replay detection.
local fields = redis.call('HGETALL', KEYS[1])
if #fields == 0 then return 0 end
local data = {}
for i = 1, #fields, 2 do data[fields[i]] = fields[i + 1] end
local removed = 0
if data['accessDigest'] then removed = removed + redis.call('DEL', 'rooe:auth:session:' .. data['accessDigest']) end
if data['refreshDigest'] then removed = removed + redis.call('DEL', 'rooe:auth:refresh:' .. data['refreshDigest']) end
redis.call('DEL', KEYS[1])
return removed