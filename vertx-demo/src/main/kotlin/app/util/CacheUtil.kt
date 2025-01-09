package app.util

import com.github.benmanes.caffeine.cache.Caffeine
import com.google.inject.Singleton
import dev.hsbrysk.caffeine.CoroutineCache
import dev.hsbrysk.caffeine.buildCoroutine
import java.util.concurrent.TimeUnit

@Singleton
class CacheUtil {
  companion object {
    @Volatile
    private var cache: CoroutineCache<String, Any>? = null

    fun getCache(): CoroutineCache<String, Any> {
      return cache ?: synchronized(this) {
        cache ?: init().also { cache = it }
      }
    }

    private fun init(): CoroutineCache<String, Any> {
      return Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .buildCoroutine()
    }
  }

  suspend fun get(str: String): Any? {
    return getCache().getIfPresent(str)
  }

  fun put(key: String, value: Any) {
    getCache().put(key, value)
  }

  fun invalidate(key: String) {
    getCache().synchronous().invalidate(key)
  }

  fun invalidateAll() {
    getCache().synchronous().invalidateAll()
  }
}
