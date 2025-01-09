package app.service.user

import app.domain.user.User
import app.service.user.impl.UserServiceImpl
import com.google.inject.ImplementedBy

@ImplementedBy(UserServiceImpl::class)
interface UserService {
  suspend fun updateUser(user: User)

  suspend fun testTransaction()
}
