package app.controller

import org.aikrai.vertx.auth.AllowAnonymous
import org.aikrai.vertx.context.Controller
import org.aikrai.vertx.context.D

@AllowAnonymous
@D("Hello")
@Controller("/")
class HelloController {
  suspend fun hello(): String {
    return "Hello"
  }
}
