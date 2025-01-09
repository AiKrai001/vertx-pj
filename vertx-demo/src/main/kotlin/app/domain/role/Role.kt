package app.domain.role

import jakarta.persistence.*
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@Entity
@Table(name = "`sys_role`", schema = "`vertx-demo`")
class Role : org.aikrai.vertx.utlis.Entity() {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "role_id", nullable = false)
  var id: Long? = null

  @Size(max = 30)
  @NotNull
  @Column(name = "role_name", nullable = false, length = 30)
  var roleName: String? = null

  @Size(max = 100)
  @NotNull
  @Column(name = "role_key", nullable = false, length = 100)
  var roleKey: String? = null

  @NotNull
  @Column(name = "role_sort", nullable = false)
  var roleSort: Int? = null

  @Column(name = "data_scope")
  var dataScope: Char? = null

  @NotNull
  @Column(name = "status", nullable = false)
  var status: Char? = null

  @Column(name = "del_flag")
  var delFlag: Char? = null

  @Size(max = 500)
  @Column(name = "remark", length = 500)
  var remark: String? = null
}
