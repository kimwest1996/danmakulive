-- wrk POST script for danmaku benchmarking
-- Usage: wrk -c 1000 -t 4 -d 30s --latency -s scripts/perf/wrk_danmaku.lua http://localhost:8080/api/v1/rooms/ROOM_ID/danmaku?bypassRateLimit=true

local counter = 0

request = function()
   counter = counter + 1
   local body = string.format('{"content":"wrk perf test %d"}', counter)
   return wrk.format("POST", nil, {
      ["Content-Type"] = "application/json",
      ["Authorization"] = "b80bbd91ad7e467798e505ef9c2e155a"
   }, body)
end

response = function(status, headers, body)
   if status ~= 200 then
      -- non-200 is unexpected
   end
end
