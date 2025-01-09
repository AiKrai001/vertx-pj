package org.aikrai.vertx.db

object SqlHelper {

  fun retBool(result: Int?): Boolean {
    return null != result && result >= 1
  }
}
