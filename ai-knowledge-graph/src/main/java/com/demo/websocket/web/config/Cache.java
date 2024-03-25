package com.demo.websocket.web.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 缓存
 * */
public class Cache {
    // 负载因子设置为0.75，表示当哈希表的容量达到75%时，会自动进行扩容
    public static LinkedHashMap<String, String> graphs = new LinkedHashMap<String, String>((int)(1000/ 0.75f) + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            // 大小超过 1000 时，返回true，表示需要删除最老的条目
            return size() > 1000;
        }
    };
}
