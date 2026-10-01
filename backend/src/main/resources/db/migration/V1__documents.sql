-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2

CREATE TABLE department (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE
);

CREATE TABLE access_role (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE,
  scope varchar(20) NOT NULL
);

CREATE TABLE permission (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL
);

CREATE TABLE role_permission (role_id bigint NOT NULL, permission_code varchar(60) NOT NULL, PRIMARY KEY(role_id, permission_code), FOREIGN KEY(role_id) REFERENCES access_role(id), FOREIGN KEY(permission_code) REFERENCES permission(code));

CREATE TABLE nav_menu (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  permission_code varchar(60) NOT NULL,
  position int NOT NULL,
  enabled boolean NOT NULL
);

CREATE TABLE account (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  username varchar(60) NOT NULL UNIQUE,
  display_name varchar(120) NOT NULL,
  password_hash varchar(100) NOT NULL,
  role_id bigint NOT NULL,
  department_id bigint NOT NULL,
  enabled boolean NOT NULL,
  FOREIGN KEY (role_id) REFERENCES access_role(id),
  FOREIGN KEY (department_id) REFERENCES department(id)
);

CREATE TABLE audit_event (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  actor varchar(60) NOT NULL,
  action varchar(120) NOT NULL,
  object_id varchar(80) NOT NULL,
  department_id bigint NOT NULL,
  created_at timestamp(6) NOT NULL
);

CREATE TABLE system_setting (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  parameter_value varchar(200) NOT NULL
);

CREATE TABLE dictionary_entry (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  type varchar(60) NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  UNIQUE (type, code)
);



CREATE TABLE controlled_document (
 id bigint AUTO_INCREMENT PRIMARY KEY, version bigint NOT NULL, change_count bigint NOT NULL,
 number varchar(60) NOT NULL UNIQUE, title varchar(200) NOT NULL, category varchar(60) NOT NULL,
 department_id bigint NOT NULL, owner_id bigint NOT NULL, creator_id bigint NOT NULL,
 current_revision_id bigint NULL, next_revision int NOT NULL, status varchar(20) NOT NULL,
 created_at timestamp(6) NOT NULL, retired_at timestamp(6) NULL, retirement_reason text NOT NULL,
 FOREIGN KEY(department_id) REFERENCES department(id), FOREIGN KEY(owner_id) REFERENCES account(id),
 FOREIGN KEY(creator_id) REFERENCES account(id), CHECK(next_revision>0), CHECK(status IN ('ACTIVE','RETIRED'))
);
CREATE TABLE document_revision (
 id bigint AUTO_INCREMENT PRIMARY KEY, document_id bigint NOT NULL, revision_no int NOT NULL,
 title varchar(200) NOT NULL, content text NOT NULL, change_summary text NOT NULL,
 author_id bigint NOT NULL, reviewer_id bigint NULL, status varchar(20) NOT NULL,
 effective_date date NULL, review_date date NULL, acknowledgement_due date NULL,
 content_hash varchar(64) NOT NULL, decision_note text NOT NULL,
 created_at timestamp(6) NOT NULL, reviewed_at timestamp(6) NULL, published_at timestamp(6) NULL,
 FOREIGN KEY(document_id) REFERENCES controlled_document(id), FOREIGN KEY(author_id) REFERENCES account(id),
 FOREIGN KEY(reviewer_id) REFERENCES account(id), UNIQUE(document_id,revision_no), CHECK(revision_no>0),
 CHECK(reviewer_id IS NULL OR reviewer_id<>author_id),
 CHECK(status IN ('DRAFT','REVIEW','APPROVED','REJECTED','PUBLISHED','SUPERSEDED','WITHDRAWN'))
);
ALTER TABLE controlled_document ADD CONSTRAINT fk_document_current FOREIGN KEY(current_revision_id) REFERENCES document_revision(id);
CREATE TABLE revision_recipient (
 revision_id bigint NOT NULL, account_id bigint NOT NULL, PRIMARY KEY(revision_id,account_id),
 FOREIGN KEY(revision_id) REFERENCES document_revision(id), FOREIGN KEY(account_id) REFERENCES account(id)
);
CREATE TABLE read_assignment (
 id bigint AUTO_INCREMENT PRIMARY KEY, document_id bigint NOT NULL, revision_id bigint NOT NULL,
 account_id bigint NOT NULL, due_date date NOT NULL, status varchar(20) NOT NULL,
 acknowledged_hash varchar(64) NOT NULL, assigned_at timestamp(6) NOT NULL, acknowledged_at timestamp(6) NULL,
 FOREIGN KEY(document_id) REFERENCES controlled_document(id), FOREIGN KEY(revision_id) REFERENCES document_revision(id),
 FOREIGN KEY(account_id) REFERENCES account(id), UNIQUE(revision_id,account_id),
 CHECK(status IN ('PENDING','ACKNOWLEDGED','SUPERSEDED','WITHDRAWN'))
);
CREATE TABLE document_event (
 id bigint AUTO_INCREMENT PRIMARY KEY, document_id bigint NOT NULL, revision_no int NOT NULL,
 actor varchar(60) NOT NULL, action varchar(60) NOT NULL, note text NOT NULL, created_at timestamp(6) NOT NULL,
 FOREIGN KEY(document_id) REFERENCES controlled_document(id)
);
CREATE TABLE command_stamp (
 id bigint AUTO_INCREMENT PRIMARY KEY, document_id bigint NOT NULL, actor varchar(60) NOT NULL,
 request_key varchar(80) NOT NULL, fingerprint varchar(64) NOT NULL,
 FOREIGN KEY(document_id) REFERENCES controlled_document(id), UNIQUE(document_id,actor,request_key)
);
CREATE INDEX ix_document_scope ON controlled_document(department_id,status,created_at);
CREATE INDEX ix_revision_review ON document_revision(reviewer_id,status,document_id);
CREATE INDEX ix_assignment_inbox ON read_assignment(account_id,status,due_date);
CREATE INDEX ix_event_document ON document_event(document_id,created_at);
CREATE INDEX ix_audit_scope ON audit_event(department_id,created_at);
