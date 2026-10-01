// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.docflow;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

/** 人员与具体发布版本的签收任务，签收保存该版本摘要及时间。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "read_assignment")
public class ReadAssignment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "document_id")
  public Long documentId;

  @Column(name = "revision_id")
  public Long revisionId;

  @Column(name = "account_id")
  public Long accountId;

  @Column(name = "due_date")
  public LocalDate dueDate;

  @Column(name = "status")
  public String status;

  @Column(name = "acknowledged_hash")
  public String acknowledgedHash;

  @Column(name = "assigned_at")
  public Instant assignedAt;

  @Column(name = "acknowledged_at")
  public Instant acknowledgedAt;
}
