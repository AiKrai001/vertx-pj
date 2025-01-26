package org.aikrai.vertx.utlis

import cn.hutool.core.util.StrUtil
import io.vertx.core.http.HttpServerRequest
import java.net.InetAddress
import java.net.UnknownHostException

object IpUtil {
  private const val REGX_0_255 = "(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)"

  // 匹配 ip
  private const val REGX_IP = "((" + REGX_0_255 + "\\.){3}" + REGX_0_255 + ")"
  const val REGX_IP_WILDCARD =
    "(((\\*\\.){3}\\*)|(" + REGX_0_255 + "(\\.\\*){3})|(" + REGX_0_255 + "\\." + REGX_0_255 + ")(\\.\\*){2}|((" + REGX_0_255 + "\\.){3}\\*))"

  // 匹配网段
  const val REGX_IP_SEG = "(" + REGX_IP + "\\-" + REGX_IP + ")"

  /**
   * 获取客户端IP
   *
   * @param request 请求对象
   * @return IP地址
   */
  fun getIpAddr(request: HttpServerRequest?): String {
    if (request == null) return "unknown"
    var ip: String? = request.getHeader("x-forwarded-for")
    if (ip.isNullOrEmpty() || "unknown".equals(ip, ignoreCase = true)) {
      ip = request.getHeader("Proxy-Client-IP")
    }
    if (ip.isNullOrEmpty() || "unknown".equals(ip, ignoreCase = true)) {
      ip = request.getHeader("X-Forwarded-For")
    }
    if (ip.isNullOrEmpty() || "unknown".equals(ip, ignoreCase = true)) {
      ip = request.getHeader("WL-Proxy-Client-IP")
    }
    if (ip.isNullOrEmpty() || "unknown".equals(ip, ignoreCase = true)) {
      ip = request.getHeader("X-Real-IP")
    }
    if (ip.isNullOrEmpty() || "unknown".equals(ip, ignoreCase = true)) {
      ip = request.remoteAddress().host()
    }
    return if (ip == "0:0:0:0:0:0:0:1") {
      "127.0.0.1"
    } else {
      getMultistageReverseProxyIp(ip)
    }
  }

  /**
   * 检查是否为内部IP地址
   *
   * @param ip IP地址
   * @return 结果
   */
  fun internalIp(ip: String?): Boolean {
    val addr = textToNumericFormatV4(ip)
    return internalIp(addr) || ip == "127.0.0.1"
  }

  /**
   * 检查是否为内部IP地址
   *
   * @param addr byte地址
   * @return 结果
   */
  private fun internalIp(addr: ByteArray?): Boolean {
    if (addr == null || addr.size < 2) return true

    val b0 = addr[0]
    val b1 = addr[1]
    // 10.x.x.x/8
    val section1: Byte = 0x0A
    // 172.16.x.x/12
    val section2: Byte = 0xAC.toByte()
    val section3: Byte = 0x10
    val section4: Byte = 0x1F
    // 192.168.x.x/16
    val section5: Byte = 0xC0.toByte()
    val section6: Byte = 0xA8.toByte()
    return when (b0) {
      section1 -> true
      section2 -> {
        if (b1 >= section3 && b1 <= section4) {
          true
        } else {
          false
        }
      }
      section5 -> {
        when (b1) {
          section6 -> true
          else -> false
        }
      }
      else -> false
    }
  }

  /**
   * 将IPv4地址转换成字节
   *
   * @param text IPv4地址
   * @return byte 字节
   */
  fun textToNumericFormatV4(text: String?): ByteArray? {
    if (text.isNullOrEmpty()) {
      return null
    }
    val bytes = ByteArray(4)
    val elements = text.split(".", limit = 5).toTypedArray()
    return try {
      when (elements.size) {
        1 -> {
          val l = elements[0].toLong()
          if (l < 0L || l > 4294967295L) {
            return null
          }
          bytes[0] = ((l shr 24) and 0xFF).toByte()
          bytes[1] = ((l shr 16) and 0xFF).toByte()
          bytes[2] = ((l shr 8) and 0xFF).toByte()
          bytes[3] = (l and 0xFF).toByte()
          bytes
        }
        2 -> {
          var l = elements[0].toLong()
          if (l < 0L || l > 255L) {
            return null
          }
          bytes[0] = (l and 0xFF).toByte()
          l = elements[1].toLong()
          if (l < 0L || l > 16777215L) {
            return null
          }
          bytes[1] = ((l shr 16) and 0xFF).toByte()
          bytes[2] = ((l shr 8) and 0xFF).toByte()
          bytes[3] = (l and 0xFF).toByte()
          bytes
        }
        3 -> {
          for (i in 0..1) {
            val l = elements[i].toLong()
            if (l < 0L || l > 255L) {
              return null
            }
            bytes[i] = (l and 0xFF).toByte()
          }
          val l = elements[2].toLong()
          if (l < 0L || l > 65535L) {
            return null
          }
          bytes[2] = ((l shr 8) and 0xFF).toByte()
          bytes[3] = (l and 0xFF).toByte()
          bytes
        }
        4 -> {
          for (i in 0..3) {
            val l = elements[i].toLong()
            if (l < 0L || l > 255L) {
              return null
            }
            bytes[i] = (l and 0xFF).toByte()
          }
          bytes
        }
        else -> null
      }
    } catch (e: NumberFormatException) {
      null
    }
  }

  /**
   * 获取IP地址
   *
   * @return 本地IP地址
   */
  fun getHostIp(): String {
    return try {
      InetAddress.getLocalHost().hostAddress
    } catch (e: UnknownHostException) {
      "127.0.0.1"
    }
  }

  /**
   * 获取主机名
   *
   * @return 本地主机名
   */
  fun getHostName(): String {
    return try {
      InetAddress.getLocalHost().hostName
    } catch (e: UnknownHostException) {
      "未知"
    }
  }

  /**
   * 从多级反向代理中获得第一个非unknown IP地址
   *
   * @param ip 获得的IP地址
   * @return 第一个非unknown IP地址
   */
  fun getMultistageReverseProxyIp(ip: String?): String {
    var ipAddress = ip
    // 多级反向代理检测
    if (!ipAddress.isNullOrEmpty() && ipAddress.contains(",")) {
      val ips = ipAddress.trim().split(",")
      for (subIp in ips) {
        if (!isUnknown(subIp)) {
          ipAddress = subIp
          break
        }
      }
    }
    return StrUtil.sub(ipAddress, 0, 255)
  }

  /**
   * 检测给定字符串是否为未知，多用于检测HTTP请求相关
   *
   * @param checkString 被检测的字符串
   * @return 是否未知
   */
  fun isUnknown(checkString: String?): Boolean {
    return checkString.isNullOrBlank() || "unknown".equals(checkString, ignoreCase = true)
  }

  /**
   * 是否为IP
   */
  fun isIP(ip: String?): Boolean {
    return !ip.isNullOrBlank() && Regex(REGX_IP).matches(ip)
  }

  /**
   * 是否为IP，或 *为间隔的通配符地址
   */
  fun isIpWildCard(ip: String?): Boolean {
    return !ip.isNullOrBlank() && Regex(REGX_IP_WILDCARD).matches(ip)
  }

  /**
   * 检测参数是否在ip通配符里
   */
  fun ipIsInWildCardNoCheck(ipWildCard: String, ip: String): Boolean {
    val s1 = ipWildCard.split(".")
    val s2 = ip.split(".")
    var isMatchedSeg = true
    for (i in s1.indices) {
      if (s1[i] == "*") {
        break
      }
      if (i >= s2.size || s1[i] != s2[i]) {
        isMatchedSeg = false
        break
      }
    }
    return isMatchedSeg
  }

  /**
   * 是否为特定格式如:“10.10.10.1-10.10.10.99”的ip段字符串
   */
  fun isIPSegment(ipSeg: String?): Boolean {
    return !ipSeg.isNullOrBlank() && Regex(REGX_IP_SEG).matches(ipSeg)
  }

  /**
   * 判断ip是否在指定网段中
   */
  fun ipIsInNetNoCheck(iparea: String, ip: String): Boolean {
    val idx = iparea.indexOf('-')
    if (idx < 0) return false
    val sips = iparea.substring(0, idx).split(".")
    val sipe = iparea.substring(idx + 1).split(".")
    val sipt = ip.split(".")
    var ips: Long = 0
    var ipe: Long = 0
    var ipt: Long = 0
    for (i in 0 until 4) {
      ips = (ips shl 8) or sips[i].toLong()
      ipe = (ipe shl 8) or sipe[i].toLong()
      ipt = (ipt shl 8) or sipt[i].toLong()
    }
    var lower = ips
    var upper = ipe
    if (lower > upper) {
      val t = lower
      lower = upper
      upper = t
    }
    return lower <= ipt && ipt <= upper
  }

  /**
   * 校验ip是否符合过滤串规则
   *
   * @param filter 过滤IP列表,支持后缀'*'通配,支持网段如:`10.10.10.1-10.10.10.99`
   * @param ip 校验IP地址
   * @return boolean 结果
   */
  fun isMatchedIp(filter: String?, ip: String?): Boolean {
    if (filter.isNullOrEmpty() || ip.isNullOrEmpty()) {
      return false
    }
    val ips = filter.split(";")
    for (iStr in ips) {
      when {
        isIP(iStr) && iStr == ip -> return true
        isIpWildCard(iStr) && ipIsInWildCardNoCheck(iStr, ip) -> return true
        isIPSegment(iStr) && ipIsInNetNoCheck(iStr, ip) -> return true
      }
    }
    return false
  }
}
