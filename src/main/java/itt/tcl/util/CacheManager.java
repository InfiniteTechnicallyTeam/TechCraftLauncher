package itt.tcl.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 高性能缓存管理器
 * 提供内存缓存、文件缓存和异步加载功能
 */
public final class CacheManager {
    private static final CacheManager INSTANCE = new CacheManager();

    // 内存缓存
    private final Map<String, CacheEntry> memoryCache = new ConcurrentHashMap<>();

    // 异步加载线程池
    private final ExecutorService executor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "cache-loader");
                t.setDaemon(true);
                return t;
            }
    );

    // 缓存条目
    private static class CacheEntry {
        final Object data;
        final long timestamp;
        final long ttl;

        CacheEntry(Object data, long ttl) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
            this.ttl = ttl;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > ttl;
        }
    }

    private CacheManager() {}

    public static CacheManager getInstance() {
        return INSTANCE;
    }

    /**
     * 获取缓存的 JSON 对象
     * @param key 缓存键
     * @param file 文件路径
     * @param ttl 缓存有效期（毫秒）
     * @return JSON 对象，如果缓存未命中则返回 null
     */
    public JsonObject getCachedJson(String key, Path file, long ttl) {
        // 先检查内存缓存
        CacheEntry entry = memoryCache.get(key);
        if (entry != null && !entry.isExpired() && entry.data instanceof JsonObject) {
            return (JsonObject) entry.data;
        }

        // 检查文件缓存
        if (Files.exists(file)) {
            try {
                JsonObject json = readJsonFile(file);
                if (json != null) {
                    // 存入内存缓存
                    memoryCache.put(key, new CacheEntry(json, ttl));
                    return json;
                }
            } catch (Exception e) {
                // 文件读取失败，删除缓存
                memoryCache.remove(key);
            }
        }

        return null;
    }

    /**
     * 存入缓存
     */
    public void putCache(String key, Object data, long ttl) {
        memoryCache.put(key, new CacheEntry(data, ttl));
    }

    /**
     * 异步加载 JSON 文件
     */
    public Future<JsonObject> loadJsonAsync(Path file) {
        return executor.submit(() -> readJsonFile(file));
    }

    /**
     * 读取 JSON 文件
     */
    private JsonObject readJsonFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 清理过期缓存
     */
    public void cleanupExpired() {
        memoryCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * 清空所有缓存
     */
    public void clearAll() {
        memoryCache.clear();
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        return new CacheStats(memoryCache.size());
    }

    public record CacheStats(int cacheSize) {}

    /**
     * 关闭缓存管理器
     */
    public void shutdown() {
        executor.shutdown();
        memoryCache.clear();
    }
}