// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.docflow;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

/** 受控文件目录与当前生效版本指针，发布行锁保护并行修订。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "controlled_document")
public class DocRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version
  @Column(name = "version")
  public Long version;

  @Column(name = "change_count")
  public long changeCount;

  @Column(name = "number")
  public String number;

  @Column(name = "title")
  public String title;

  @Column(name = "category")
  public String category;

  @Column(name = "department_id")
  public Long departmentId;

  @Column(name = "owner_id")
  public Long ownerId;

  @Column(name = "creator_id")
  public Long creatorId;

  @Column(name = "current_revision_id")
  public Long currentRevisionId;

  @Column(name = "next_revision")
  public int nextRevision;

  @Column(name = "status")
  public String status;

  @Column(name = "created_at")
  public Instant createdAt;

  @Column(name = "retired_at")
  public Instant retiredAt;

  @Column(name = "retirement_reason", columnDefinition = "text")
  public String retirementReason;
}
