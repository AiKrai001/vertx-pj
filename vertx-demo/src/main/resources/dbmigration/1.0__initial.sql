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
  user_name VARCHAR(255) DEFAULT '',
  user_type VARCHAR(255) DEFAULT '',
  email VARCHAR(255) DEFAULT '',
  phone VARCHAR(255) DEFAULT '',
  avatar VARCHAR(255) DEFAULT '',
  password VARCHAR(255) DEFAULT '',
  status INTEGER DEFAULT 0,
  del_flag CHAR(1) DEFAULT 0,
  login_ip VARCHAR(255) DEFAULT '',
  login_date TIMESTAMPTZ,
  CONSTRAINT pk_sys_user PRIMARY KEY (user_id)
);

-- 添加字段注释
COMMENT ON COLUMN sys_user.user_id IS '用户ID';


CREATE UNIQUE INDEX idx_phone ON sys_user (phone);


CREATE TABLE sys_role (
  role_id BIGINT DEFAULT 0,
  role_name VARCHAR(255) DEFAULT '',
  role_key VARCHAR(255) DEFAULT '',
  role_sort INTEGER DEFAULT 0,
  data_scope CHAR(1),
  status CHAR(1) DEFAULT 0,
  del_flag CHAR(1) DEFAULT 0
);

