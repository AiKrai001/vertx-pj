-- apply changes
CREATE TABLE sys_menu (
  menu_id BIGINT DEFAULT 0 NOT NULL,
  menu_name VARCHAR(255) DEFAULT '',
  parent_id BIGINT DEFAULT 0,
  order_num INTEGER DEFAULT 0,
  path VARCHAR(255) DEFAULT '',
  component VARCHAR(255) DEFAULT '',
  menu_type VARCHAR(255) DEFAULT '',
  visible VARCHAR(255) DEFAULT '',
  perms VARCHAR(255) DEFAULT '',
  parent_name VARCHAR(255) DEFAULT '',
  children JSONB DEFAULT '{}',
  CONSTRAINT pk_sys_menu PRIMARY KEY (menu_id)
);

CREATE TABLE sys_user (
  user_id BIGINT DEFAULT 0 NOT NULL,
  dept_id BIGINT DEFAULT 0,
  user_name VARCHAR(30) DEFAULT '',
  nick_name VARCHAR(30) DEFAULT '',
  user_type VARCHAR(2) DEFAULT '',
  email VARCHAR(50) DEFAULT '',
  phonenumber VARCHAR(11) DEFAULT '',
  sex INTEGER DEFAULT 2,
  avatar VARCHAR(100) DEFAULT '',
  password VARCHAR(100) DEFAULT '',
  status INTEGER DEFAULT 0,
  del_flag CHAR(1) DEFAULT 0,
  login_ip VARCHAR(255) DEFAULT '',
  login_date TIMESTAMPTZ,
  CONSTRAINT pk_sys_user PRIMARY KEY (user_id)
);

-- 添加字段注释
COMMENT ON COLUMN sys_user.user_id IS '用户ID';
COMMENT ON COLUMN sys_user.dept_id IS '部门ID';
COMMENT ON COLUMN sys_user.user_name IS '用户账号';
COMMENT ON COLUMN sys_user.nick_name IS '用户昵称';
COMMENT ON COLUMN sys_user.user_type IS '用户类型';
COMMENT ON COLUMN sys_user.email IS '用户邮箱';
COMMENT ON COLUMN sys_user.phonenumber IS '手机号码';
COMMENT ON COLUMN sys_user.sex IS '用户性别（0男 1女 2未知）';
COMMENT ON COLUMN sys_user.avatar IS '头像地址';
COMMENT ON COLUMN sys_user.password IS '密码';
COMMENT ON COLUMN sys_user.status IS '帐号状态（0正常 1停用）';
COMMENT ON COLUMN sys_user.del_flag IS '删除标志（0代表存在 2代表删除）';


CREATE TABLE sys_role (
  role_id BIGINT DEFAULT 0,
  role_name VARCHAR(255) DEFAULT '',
  role_key VARCHAR(255) DEFAULT '',
  role_sort INTEGER DEFAULT 0,
  data_scope CHAR(1),
  status CHAR(1) DEFAULT 0,
  del_flag CHAR(1) DEFAULT 0
);

