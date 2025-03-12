package app.base.domain.auth.modle

class LoginUser {
  var accountId: Long = 0L
  var token: String = ""
  var loginTime: Long = 0L
  var expireTime: Long = 0L
  var ipaddr: String = ""
  var client: String = ""
}
