// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.docflow;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

/** 每次修订的正文、独立审核、接收范围和生效日期；发布后内容不可改写。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "document_revision")
public class DocRevision {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "document_id")
  public Long documentId;

  @Column(name = "revision_no")
  public int revisionNo;

  @Column(name = "title")
  public String title;

  @Column(name = "content", columnDefinition = "text")
  public String content;

  @Column(name = "change_summary", columnDefinition = "text")
  public String changeSummary;

  @Column(name = "author_id")
  public Long authorId;

  @Column(name = "reviewer_id")
  public Long reviewerId;

  @Column(name = "status")
  public String status;

  @Column(name = "effective_date")
  public LocalDate effectiveDate;

  @Column(name = "review_date")
  public LocalDate reviewDate;

  @Column(name = "acknowledgement_due")
  public LocalDate acknowledgementDue;

  @Column(name = "content_hash")
  public String contentHash;

  @Column(name = "decision_note", columnDefinition = "text")
  public String decisionNote;

  @Column(name = "created_at")
  public Instant createdAt;

  @Column(name = "reviewed_at")
  public Instant reviewedAt;

  @Column(name = "published_at")
  public Instant publishedAt;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "revision_recipient", joinColumns = @JoinColumn(name = "revision_id"))
  @Column(name = "account_id")
  public Set<Long> recipients = new HashSet<>();
}
