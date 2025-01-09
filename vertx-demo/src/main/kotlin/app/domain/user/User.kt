package app.domain.user

import jakarta.persistence.*
import java.sql.Timestamp

@Entity
@Table(name = "`sys_user`", schema = "`vertx-demo`")
class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "user_id", nullable = false)
  var id: Long? = 0L

  @Column(name = "dept_id")
  var deptId: Long? = null

  @Column(name = "login_name", nullable = false, length = 30)
  var loginName: String? = ""

  @Column(name = "user_name", length = 30)
  var userName: String? = ""

  @Column(name = "user_type", length = 2)
  var userType: String? = ""

  @Column(name = "email", length = 50)
  var email: String? = ""

  @Column(name = "phonenumber", length = 11)
  var phonenumber: String? = ""

//  @Column(name = "sex")
//  var sex: Char? = null

  @Column(name = "avatar", length = 100)
  var avatar: String? = null

  @Column(name = "password", length = 50)
  var password: String? = null

  @Column(name = "salt", length = 20)
  var salt: String? = null

//  @Column(name = "status")
//  var status: Char? = null

//  @Column(name = "del_flag")
//  var delFlag: Char? = null

  @Column(name = "login_ip", length = 128)
  var loginIp: String? = null

  @Column(name = "login_date")
  var loginDate: Timestamp? = null

  @Column(name = "pwd_update_date")
  var pwdUpdateDate: Timestamp? = null

  @Column(name = "create_by", length = 64)
  var createBy: String? = null

  @Column(name = "create_time")
  var createTime: Timestamp? = null

  @Column(name = "update_by", length = 64)
  var updateBy: String? = null

  @Column(name = "update_time")
  var updateTime: Timestamp? = null

  @Column(name = "remark", length = 500)
  var remark: String? = null
}
