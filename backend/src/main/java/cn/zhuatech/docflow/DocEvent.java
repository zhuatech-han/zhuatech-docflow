// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.docflow;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

/** 追加式版本与发布历史；保存当时的轮次、操作者和可读意见。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "document_event")
public class DocEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "document_id")
  public Long documentId;

  @Column(name = "revision_no")
  public int revisionNo;

  @Column(name = "actor")
  public String actor;

  @Column(name = "action")
  public String action;

  @Column(name = "note", columnDefinition = "text")
  public String note;

  @Column(name = "created_at")
  public Instant createdAt;
}
