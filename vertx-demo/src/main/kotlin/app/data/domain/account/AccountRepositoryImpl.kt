package app.data.domain.account

import app.data.domain.account.modle.AccountRoleAccessDTO
import app.data.domain.account.modle.AccountRoleDTO
import com.google.inject.Inject
import io.vertx.sqlclient.SqlClient
import org.aikrai.vertx.db.RepositoryImpl

class AccountRepositoryImpl @Inject constructor(
  sqlClient: SqlClient
) : RepositoryImpl<Long, Account>(sqlClient), AccountRepository {

  override suspend fun getUserList(
    userName: String?,
    phone: String?
  ): List<Account> {
    return queryBuilder()
      .eq(!userName.isNullOrBlank(), Account::userName, userName)
      .eq(!phone.isNullOrBlank(), Account::phone, phone)
      .getList()
  }

  override suspend fun getByName(name: String): Account? {
    val account = queryBuilder()
      .eq(Account::userName, name)
      .getOne()
    return account
  }

  override suspend fun getInfo(id: Long): AccountRoleAccessDTO? {
    val sql = """
        SELECT
            JSONB_BUILD_OBJECT(
                'user_id', a.user_id, 'user_name', a.user_name, 'phone', a.phone, 'status', a.status, 'avatar', a.avatar,
                'password', a.password
            ) AS account,
            COALESCE(
                JSONB_AGG(
                    DISTINCT JSONB_BUILD_OBJECT(
                        'role_id', r.role_id, 'role_name', r.role_name, 'role_key', r.role_key, 'remark', r.remark
                    )
                ) FILTER (WHERE r.role_id IS NOT NULL),
                '[]'::jsonb
            ) AS roles,
            COALESCE(
                JSONB_AGG(
                    DISTINCT JSONB_BUILD_OBJECT(
                        'menu_id', m.menu_id, 'menu_name', m.menu_name, 'parent_id', m.parent_id, 'order_num', m.order_num,
                        'menu_type', m.menu_type, 'visible', m.visible, 'path', m.path, 'perms', m.perms,
                        'component', m.component
                    )
                ) FILTER (WHERE m.menu_id IS NOT NULL),
                '[]'::jsonb
            ) AS access
        FROM
            sys_user a
            LEFT JOIN sys_user_role ar ON a.user_id = ar.user_id
            LEFT JOIN sys_role r ON ar.role_id = r.role_id
            LEFT JOIN sys_role_menu rm ON r.role_id = rm.role_id
            LEFT JOIN sys_menu m ON rm.menu_id = m.menu_id
        where a.user_id = #{id}
        GROUP BY a.user_id, a.user_name, a.phone, a.status, a.avatar, a.password;
    """.trimIndent()
    return get(sql, mapOf("id" to id), AccountRoleAccessDTO::class.java)
  }

  override suspend fun getAccountRole(id: Long): AccountRoleDTO? {
    val sql = """
        SELECT a.user_id, a.user_name, a.phone, a.status, a.avatar, a.password,
            COALESCE(
                JSONB_AGG(
                    DISTINCT JSONB_BUILD_OBJECT(
                        'role_id', r.role_id, 'role_name', r.role_name, 'role_key', r.role_key, 'remark', r.remark
                    )
                ) FILTER (WHERE r.role_id IS NOT NULL),
                '[]'::jsonb
            ) AS roles
        FROM
            account a
            LEFT JOIN account_role ar ON a.id = ar.account_id
            LEFT JOIN role r ON ar.role_id = r.id
        WHERE a.id = #{id}
        GROUP BY a.user_id, a.user_name, a.phone, a.status, a.avatar, a.password;
    """.trimIndent()
    return get(sql, mapOf("id" to id), AccountRoleDTO::class.java)
  }

  override suspend fun bindRoles(id: Long, roles: List<Long>) {
    if (roles.isEmpty()) return
    val sql = StringBuilder("INSERT INTO account_role (account_id, role_id) VALUES ")
    roles.forEachIndexed { index, roleId ->
      sql.append("($id, $roleId)")
      if (index < roles.size - 1) {
        sql.append(", ")
      }
    }
    execute(sql.toString())
  }

  override suspend fun removeAllRole(id: Long): Int {
    val sql = "DELETE FROM account_role WHERE account_id = #{$id}"
    return execute(sql)
  }
}
