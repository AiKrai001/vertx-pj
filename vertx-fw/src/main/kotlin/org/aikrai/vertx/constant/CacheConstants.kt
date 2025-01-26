package org.aikrai.vertx.constant

object CacheConstants {
  /**
   * 搜索历史key
   */
  const val SEARCH_CONFIG: String = "search_config:"

  /**
   * 登录用户 redis key
   */
  const val LOGIN_TOKEN_KEY: String = "login_tokens:"

  /**
   * 验证码 redis key
   */
  const val CAPTCHA_CODE_KEY: String = "captcha_codes:"

  /**
   * 参数管理 cache key
   */
  const val SYS_CONFIG_KEY: String = "sys_config:"

  /**
   * 字典管理 cache key
   */
  const val SYS_DICT_KEY: String = "sys_dict:"

  /**
   * 防重提交 redis key
   */
  const val REPEAT_SUBMIT_KEY: String = "repeat_submit:"

  /**
   * 限流 redis key
   */
  const val RATE_LIMIT_KEY: String = "rate_limit:"

  /**
   * 登录账户密码错误次数 redis key
   */
  const val PWD_ERR_CNT_KEY: String = "pwd_err_cnt:"
}
