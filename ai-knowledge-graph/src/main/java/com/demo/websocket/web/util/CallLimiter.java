package com.demo.websocket.web.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Date;
import java.text.SimpleDateFormat;

public class CallLimiter {
    private static final ConcurrentHashMap<String, AtomicInteger> callCounterMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> ipLastAccessMap = new ConcurrentHashMap<>();
    private static final int MAX_CALLS_PER_DAY = 3; // 每天允许的最大调用次数

    public static boolean allowCall(String ipAddress) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        String currentDay = dateFormat.format(new Date());

        // 如果是新的一天，重置计数
        if (!ipLastAccessMap.containsKey(ipAddress) || !dateFormat.format(ipLastAccessMap.get(ipAddress)).equals(currentDay)) {
            ipLastAccessMap.put(ipAddress, System.currentTimeMillis());
            callCounterMap.put(ipAddress, new AtomicInteger(0));
        }

        AtomicInteger callCounter = callCounterMap.get(ipAddress);

        // 检查调用次数是否超过每天限制
        if (callCounter.get() < MAX_CALLS_PER_DAY) {
            callCounter.incrementAndGet(); // 增加调用次数
            return true;
        } else {
            return false;
        }
    }
}
