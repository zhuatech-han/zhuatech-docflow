// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.docflow;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 受控文档修订、独立审批、发布替换和版本签收的完整事务服务。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class DocService {
  final Store db;
  final AccessService access;
  final Clock clock;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

  public DocService(Store db, AccessService access, Clock clock) {
    this.db = db;
    this.access = access;
    this.clock = clock;
  }

  /** 文档建档输入，部门与责任人经服务端校验。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Catalog(String title, String category, Long departmentId, Long ownerId) {}

  /** 修订草稿包括正文、变更说明、审批人和接收人快照。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Draft(
      Long version,
      String title,
      String content,
      String changeSummary,
      Long reviewerId,
      LocalDate effectiveDate,
      LocalDate reviewDate,
      LocalDate acknowledgementDue,
      Set<Long> recipients) {}

  /** 命令绑定版本、幂等键、审核意见或新增分发人员。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(
      Long version, String requestKey, String note, Set<Long> recipients, LocalDate dueDate) {}

  /** 只允许签收当前版本及客户端实际阅读的正文摘要。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Acknowledgement(String contentHash) {}

  /** 返回授权部门人员及文件字典，不返回账号散列。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    access.require("doc.read");
    return Map.of(
        "people",
        db.all(Account.class).stream()
            .filter(a -> a.enabled && access.visible(a.departmentId))
            .map(
                a ->
                    Map.of(
                        "id",
                        a.id,
                        "displayName",
                        a.displayName,
                        "departmentId",
                        a.departmentId,
                        "permissions",
                        db.get(AccessRole.class, a.roleId).permissions))
            .toList(),
        "departments",
        db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList(),
        "dictionaries",
        db.all(DictionaryEntry.class),
        "settings",
        db.all(SystemSetting.class));
  }

  /** 新建目录和第一份本人修订草稿。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object create(Catalog v) {
    access.require("doc.write");
    access.department(v.departmentId);
    db.get(Department.class, v.departmentId);
    assigned(v.ownerId, v.departmentId, "doc.write");
    assigned(v.ownerId, v.departmentId, "doc.publish");
    if (db.query(
            DictionaryEntry.class,
            "from DictionaryEntry where type='category' and code=?1",
            v.category)
        .isEmpty()) throw new Problem(400, "INVALID_DICTIONARY");
    var d = new DocRecord();
    d.number = "DOC-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
    d.title = text(v.title, 200);
    d.category = v.category;
    d.departmentId = v.departmentId;
    d.ownerId = v.ownerId;
    d.creatorId = access.current().id;
    d.status = "ACTIVE";
    d.nextRevision = 1;
    d.createdAt = clock.instant();
    d.retirementReason = "";
    db.save(d);
    newRevision(d);
    db.flush();
    return detail(d.id);
  }

  /** 查询分页目录，阅读人看不到从未发布且未授权的草稿文件。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(
      String search, String status, String category, int page, int size, String sort) {
    access.require("doc.read");
    if (page < 0
        || page > 100000
        || size < 1
        || size > 100
        || search.length() > 200
        || !Set.of("newest", "title", "number").contains(sort)
        || !Set.of("", "ACTIVE", "RETIRED").contains(status)
        || category.length() > 60) throw new Problem(400, "INVALID_INPUT");
    var p = new ArrayList<Object>();
    String where = scope(p);
    if (!search.isBlank()) {
      p.add("%" + search.toLowerCase(Locale.ROOT) + "%");
      where +=
          " and (lower(d.title) like ?" + p.size() + " or lower(d.number) like ?" + p.size() + ")";
    }
    if (!status.isBlank()) {
      p.add(status);
      where += " and d.status=?" + p.size();
    }
    if (!category.isBlank()) {
      p.add(category);
      where += " and d.category=?" + p.size();
    }
    var count = db.jpql(Long.class, "select count(d) from DocRecord d where " + where);
    var rows =
        db.jpql(
            DocRecord.class,
            "from DocRecord d where "
                + where
                + " order by "
                + switch (sort) {
                  case "title" -> "d.title,d.id";
                  case "number" -> "d.number,d.id";
                  default -> "d.createdAt desc,d.id desc";
                });
    for (int i = 0; i < p.size(); i++) {
      count.setParameter(i + 1, p.get(i));
      rows.setParameter(i + 1, p.get(i));
    }
    return Map.of(
        "items",
        rows.setFirstResult(page * size).setMaxResults(size).getResultList().stream()
            .map(this::summary)
            .toList(),
        "total",
        count.getSingleResult());
  }

  /** 根据参与人和已发布版本返回详情；接收人仅见自己的签收记录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id) {
    access.require("doc.read");
    var d = db.get(DocRecord.class, id);
    visible(d);
    var revs =
        revisions(d).stream()
            .filter(r -> revisionVisible(d, r))
            .map(
                r -> {
                  var m = new LinkedHashMap<String, Object>();
                  m.put("revision", revisionView(r));
                  m.put("commands", commands(d, r));
                  return m;
                })
            .toList();
    boolean manager = manager(d);
    var receipts =
        assignments(d).stream()
            .filter(a -> manager || a.accountId.equals(access.current().id))
            .toList();
    var ev =
        events(d).stream()
            .filter(
                e ->
                    manager
                        || revisions(d).stream()
                            .anyMatch(r -> r.revisionNo == e.revisionNo && revisionVisible(d, r)))
            .toList();
    return Map.of(
        "document",
        summary(d),
        "revisions",
        revs,
        "assignments",
        receipts,
        "events",
        ev,
        "canCreateRevision",
        canCreate(d),
        "canRetire",
        access.role().permissions.contains("doc.publish")
            && d.ownerId.equals(access.current().id)
            && d.status.equals("ACTIVE")
            && d.currentRevisionId != null);
  }

  /** 编写人保存自己的未送审修订，校验独立审批人及接收范围。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object save(Long id, Long rid, Draft v) {
    access.require("doc.write");
    var d = locked(id);
    active(d);
    DocPolicy.version(d, v.version);
    var r = revision(d, rid);
    author(r);
    DocPolicy.state(r, "DRAFT", "REJECTED");
    r.title = text(v.title, 200);
    r.content = text(v.content, 12000);
    r.changeSummary = text(v.changeSummary, 3000);
    assigned(v.reviewerId, d.departmentId, "doc.review");
    r.reviewerId = v.reviewerId;
    DocPolicy.independent(d, r);
    DocPolicy.dates(v.effectiveDate, v.reviewDate, v.acknowledgementDue);
    r.effectiveDate = v.effectiveDate;
    r.reviewDate = v.reviewDate;
    r.acknowledgementDue = v.acknowledgementDue;
    r.recipients = recipients(d, v.recipients);
    r.contentHash = DocPolicy.hash(r.title, r.content);
    r.status = "DRAFT";
    r.reviewedAt = null;
    r.decisionNote = "";
    event(d, r, "SAVE_DRAFT", r.changeSummary);
    db.flush();
    return detail(id);
  }

  /** 只有目录责任人或建档人可开始下一次修订，目录始终最多一个未完成版本。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object startRevision(Long id, Long version) {
    access.require("doc.write");
    var d = locked(id);
    DocPolicy.version(d, version);
    if (!canCreate(d)) throw new Problem(409, "OPEN_REVISION_EXISTS");
    newRevision(d);
    db.flush();
    return detail(id);
  }

  /** 删除从未发布的新建目录；已送审历史和发布记录不可删除。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(Long id, Long version) {
    access.require("doc.write");
    var d = locked(id);
    DocPolicy.version(d, version);
    if (!d.creatorId.equals(access.current().id)) throw new Problem(403, "NOT_AUTHOR");
    var revs = revisions(d);
    if (d.currentRevisionId != null
        || revs.size() != 1
        || !revs.getFirst().status.equals("DRAFT")
        || events(d).stream()
            .anyMatch(e -> !Set.of("CREATE_REVISION", "SAVE_DRAFT").contains(e.action)))
      throw new Problem(409, "HISTORY_PROTECTED");
    access.audit("DELETE_DRAFT_DOCUMENT", id, d.departmentId);
    for (var e : events(d)) db.delete(e);
    for (var s : db.query(CommandStamp.class, "from CommandStamp where documentId=?1", id))
      db.delete(s);
    db.delete(revs.getFirst());
    db.delete(d);
  }

  /** 执行送审、撤回、通过、退回、发布与撤销批准；状态和责任人服务端核对。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object act(Long id, Long rid, String action, Command v) {
    String permission =
        switch (action) {
          case "submit", "recall", "withdraw" -> "doc.write";
          case "approve", "reject" -> "doc.review";
          case "publish", "distribute" -> "doc.publish";
          default -> throw new Problem(404, "NOT_FOUND");
        };
    access.require(permission);
    var d = locked(id);
    active(d);
    var r = revision(d, rid);
    if (replay(d, "revision:" + rid + ":" + action, v)) return detail(id);
    DocPolicy.version(d, v.version);
    String note = optional(v.note, 2000);
    switch (action) {
      case "submit" -> {
        author(r);
        DocPolicy.state(r, "DRAFT");
        text(r.content, 12000);
        text(r.changeSummary, 3000);
        assigned(r.reviewerId, d.departmentId, "doc.review");
        DocPolicy.independent(d, r);
        DocPolicy.dates(r.effectiveDate, r.reviewDate, r.acknowledgementDue);
        recipients(d, r.recipients);
        if (r.reviewDate.isBefore(today()) || r.acknowledgementDue.isBefore(today()))
          throw new Problem(400, "INVALID_DATES");
        r.status = "REVIEW";
      }
      case "recall" -> {
        author(r);
        DocPolicy.state(r, "REVIEW");
        note = text(v.note, 2000);
        r.status = "DRAFT";
      }
      case "approve", "reject" -> {
        reviewer(d, r);
        DocPolicy.state(r, "REVIEW");
        note = text(v.note, 2000);
        r.status = action.equals("approve") ? "APPROVED" : "REJECTED";
        r.decisionNote = note;
        r.reviewedAt = clock.instant();
      }
      case "withdraw" -> {
        author(r);
        DocPolicy.state(r, "DRAFT", "REJECTED", "APPROVED");
        note = text(v.note, 2000);
        r.status = "WITHDRAWN";
      }
      case "publish" -> {
        owner(d);
        DocPolicy.state(r, "APPROVED");
        DocPolicy.independent(d, r);
        assigned(r.reviewerId, d.departmentId, "doc.review");
        if (r.effectiveDate.isAfter(today())) throw new Problem(409, "NOT_EFFECTIVE_YET");
        if (r.reviewDate.isBefore(today()) || r.acknowledgementDue.isBefore(today()))
          throw new Problem(400, "INVALID_DATES");
        var target = recipients(d, r.recipients);
        if (d.currentRevisionId != null) {
          var old = db.get(DocRevision.class, d.currentRevisionId);
          old.status = "SUPERSEDED";
          closePending(d, old.id, "SUPERSEDED");
        }
        r.status = "PUBLISHED";
        r.publishedAt = clock.instant();
        d.currentRevisionId = r.id;
        d.title = r.title;
        for (var a : target) assign(d, r, a, r.acknowledgementDue);
        note = "R" + r.revisionNo + " · " + target.size() + " recipients";
      }
      case "distribute" -> {
        owner(d);
        DocPolicy.state(r, "PUBLISHED");
        if (!r.id.equals(d.currentRevisionId)) throw new Problem(409, "OLD_REVISION");
        if (v.dueDate == null || v.dueDate.isBefore(today()))
          throw new Problem(400, "INVALID_DATES");
        var target = recipients(d, v.recipients);
        for (var a : target) {
          assign(d, r, a, v.dueDate);
        }
        note = "Additional distribution · " + target.size() + " recipients";
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    event(d, r, action.toUpperCase(Locale.ROOT), note);
    db.flush();
    return detail(id);
  }

  /** 归档目录并取消未签收任务，历史版本与签收证据保留。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object retire(Long id, Command v) {
    access.require("doc.publish");
    var d = locked(id);
    owner(d);
    if (replay(d, "retire", v)) return detail(id);
    DocPolicy.version(d, v.version);
    active(d);
    if (d.currentRevisionId == null || open(d)) throw new Problem(409, "OPEN_REVISION_EXISTS");
    d.retirementReason = text(v.note, 2000);
    d.status = "RETIRED";
    d.retiredAt = clock.instant();
    var r = db.get(DocRevision.class, d.currentRevisionId);
    r.status = "WITHDRAWN";
    closePending(d, r.id, "WITHDRAWN");
    event(d, r, "RETIRE", d.retirementReason);
    db.flush();
    return detail(id);
  }

  /** 本人确认已阅读的当前正文摘要；版本替换与签收通过同一目录行锁互斥。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object acknowledge(Long id, Long aid, Acknowledgement v) {
    access.require("doc.ack");
    var d = locked(id);
    active(d);
    var a = db.get(ReadAssignment.class, aid);
    if (!a.documentId.equals(id) || !a.accountId.equals(access.current().id))
      throw new Problem(403, "NOT_RECIPIENT");
    var r = db.get(DocRevision.class, a.revisionId);
    if (!r.id.equals(d.currentRevisionId) || !r.status.equals("PUBLISHED"))
      throw new Problem(409, "OLD_REVISION");
    if (!Objects.equals(v.contentHash, r.contentHash)) throw new Problem(409, "CONTENT_CHANGED");
    if (a.status.equals("ACKNOWLEDGED")) return detail(id);
    if (!a.status.equals("PENDING")) throw new Problem(409, "INVALID_STATE");
    a.status = "ACKNOWLEDGED";
    a.acknowledgedHash = r.contentHash;
    a.acknowledgedAt = clock.instant();
    event(d, r, "ACKNOWLEDGE", "Confirmed reading of R" + r.revisionNo);
    db.flush();
    return detail(id);
  }

  /** 待办包括本人审批、编写任务与仍生效的阅读签收。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object workbench() {
    access.require("doc.read");
    var documents = scoped();
    var revisions = new ArrayList<Object>();
    var reads = new ArrayList<Object>();
    for (var d : documents) {
      if (!d.status.equals("ACTIVE")) continue;
      for (var r : revisions(d))
        if (!commands(d, r).isEmpty()
            && Set.of("DRAFT", "REJECTED", "REVIEW", "APPROVED").contains(r.status))
          revisions.add(
              Map.of(
                  "document", summary(d), "revision", revisionView(r), "commands", commands(d, r)));
      for (var a : assignments(d))
        if (a.status.equals("PENDING")
            && a.accountId.equals(access.current().id)
            && a.revisionId.equals(d.currentRevisionId))
          reads.add(Map.of("document", summary(d), "assignment", a));
    }
    return Map.of("revisions", revisions, "reads", reads);
  }

  /** 统计授权目录及可见签收，普通阅读人仅统计自己的阅读任务。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    var documents = scoped();
    var counts = new TreeMap<String, Long>();
    long pending = 0, acknowledged = 0, overdue = 0, reviews = 0;
    for (var d : documents) {
      counts.merge(d.status, 1L, Long::sum);
      if (d.currentRevisionId != null
          && d.status.equals("ACTIVE")
          && db.get(DocRevision.class, d.currentRevisionId).reviewDate.isBefore(today())) reviews++;
      for (var a : assignments(d))
        if (manager(d) || a.accountId.equals(access.current().id)) {
          if (a.status.equals("ACKNOWLEDGED")) acknowledged++;
          if (a.status.equals("PENDING")) {
            pending++;
            if (a.dueDate.isBefore(today())) overdue++;
          }
        }
    }
    return Map.of(
        "documents",
        documents.size(),
        "status",
        counts,
        "pending",
        pending,
        "acknowledged",
        acknowledged,
        "overdue",
        overdue,
        "reviewOverdue",
        reviews);
  }

  /** 下载授权范围内的版本和签收快照，不添加推广信息。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String export(Long id) {
    access.require("export");
    return json.writeValueAsString(
        Map.of("schemaVersion", "1.0", "kind", "CONTROLLED_DOCUMENT_REPORT", "report", detail(id)));
  }

  /** 审计遵守部门与本人范围。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    return switch (access.role().scope) {
      case "ALL" -> db.query(AuditEvent.class, "from AuditEvent order by id desc");
      case "ASSIGNED" ->
          db.query(
              AuditEvent.class,
              "from AuditEvent where actor=?1 order by id desc",
              access.current().username);
      default ->
          db.query(
              AuditEvent.class,
              "from AuditEvent where departmentId=?1 order by id desc",
              access.current().departmentId);
    };
  }

  private Map<String, Object> summary(DocRecord d) {
    var m = new LinkedHashMap<String, Object>();
    m.put("id", d.id);
    m.put("version", d.version);
    m.put("number", d.number);
    m.put("title", d.title);
    m.put("category", d.category);
    m.put("departmentId", d.departmentId);
    m.put("ownerId", d.ownerId);
    m.put("creatorId", d.creatorId);
    m.put("status", d.status);
    m.put("createdAt", d.createdAt);
    m.put("currentRevisionId", d.currentRevisionId);
    m.put("retirementReason", d.retirementReason);
    m.put("retiredAt", d.retiredAt);
    if (d.currentRevisionId != null) {
      var r = db.get(DocRevision.class, d.currentRevisionId);
      m.put("currentRevisionNo", r.revisionNo);
      m.put("effectiveDate", r.effectiveDate);
      m.put("reviewDate", r.reviewDate);
      m.put("reviewOverdue", d.status.equals("ACTIVE") && r.reviewDate.isBefore(today()));
    }
    return m;
  }

  private Object revisionView(DocRevision r) {
    var m = new LinkedHashMap<String, Object>();
    m.put("id", r.id);
    m.put("revisionNo", r.revisionNo);
    m.put("title", r.title);
    m.put("content", r.content);
    m.put("changeSummary", r.changeSummary);
    m.put("authorId", r.authorId);
    m.put("reviewerId", r.reviewerId);
    m.put("status", r.status);
    m.put("effectiveDate", r.effectiveDate);
    m.put("reviewDate", r.reviewDate);
    m.put("acknowledgementDue", r.acknowledgementDue);
    m.put("contentHash", r.contentHash);
    m.put("decisionNote", r.decisionNote);
    m.put("createdAt", r.createdAt);
    m.put("reviewedAt", r.reviewedAt);
    m.put("publishedAt", r.publishedAt);
    if (Set.of("doc.write", "doc.publish", "doc.review").stream()
        .anyMatch(access.role().permissions::contains)) m.put("recipients", r.recipients);
    return m;
  }

  private String scope(List<Object> p) {
    var me = access.current();
    String w = "1=1";
    if (!access.role().scope.equals("ALL")) {
      p.add(me.departmentId);
      w = "d.departmentId=?" + p.size();
    }
    if (access.role().scope.equals("ASSIGNED")) {
      p.add(me.id);
      int i = p.size();
      w +=
          " and (d.creatorId=?"
              + i
              + " or d.ownerId=?"
              + i
              + " or exists(select r.id from DocRevision r where r.documentId=d.id and (r.authorId=?"
              + i
              + " or r.reviewerId=?"
              + i
              + ")) or exists(select a.id from ReadAssignment a where a.documentId=d.id and a.accountId=?"
              + i
              + "))";
    }
    if (Collections.disjoint(
        access.role().permissions, Set.of("doc.write", "doc.publish", "doc.review")))
      w += " and d.currentRevisionId is not null";
    return w;
  }

  private List<DocRecord> scoped() {
    access.require("doc.read");
    var p = new ArrayList<Object>();
    return db.query(DocRecord.class, "from DocRecord d where " + scope(p), p.toArray());
  }

  private void visible(DocRecord d) {
    access.department(d.departmentId);
    var me = access.current();
    if (access.role().scope.equals("ASSIGNED")
        && !d.creatorId.equals(me.id)
        && !d.ownerId.equals(me.id)
        && revisions(d).stream()
            .noneMatch(r -> r.authorId.equals(me.id) || Objects.equals(r.reviewerId, me.id))
        && assignments(d).stream().noneMatch(a -> a.accountId.equals(me.id)))
      throw new Problem(403, "OUT_OF_SCOPE");
    if (d.currentRevisionId == null
        && Collections.disjoint(
            access.role().permissions, Set.of("doc.write", "doc.publish", "doc.review")))
      throw new Problem(403, "OUT_OF_SCOPE");
  }

  private boolean manager(DocRecord d) {
    return (d.ownerId.equals(access.current().id)
            && access.role().permissions.contains("doc.publish"))
        || access.role().permissions.contains("audit");
  }

  private boolean revisionVisible(DocRecord d, DocRevision r) {
    return r.publishedAt != null
        || d.ownerId.equals(access.current().id) && access.role().permissions.contains("doc.write")
        || r.authorId.equals(access.current().id) && access.role().permissions.contains("doc.write")
        || Objects.equals(r.reviewerId, access.current().id)
            && access.role().permissions.contains("doc.review");
  }

  private List<String> commands(DocRecord d, DocRevision r) {
    var out = new ArrayList<String>();
    if (!d.status.equals("ACTIVE")) return out;
    var perms = access.role().permissions;
    var id = access.current().id;
    if (perms.contains("doc.write") && r.authorId.equals(id)) {
      if (Set.of("DRAFT", "REJECTED").contains(r.status)) out.add("edit");
      if (r.status.equals("DRAFT")) out.add("submit");
      if (r.status.equals("REVIEW")) out.add("recall");
      if (Set.of("DRAFT", "REJECTED", "APPROVED").contains(r.status)) out.add("withdraw");
    }
    if (perms.contains("doc.review")
        && Objects.equals(r.reviewerId, id)
        && r.status.equals("REVIEW")) out.addAll(List.of("approve", "reject"));
    if (perms.contains("doc.publish") && d.ownerId.equals(id)) {
      if (r.status.equals("APPROVED")) out.add("publish");
      if (r.status.equals("PUBLISHED") && r.id.equals(d.currentRevisionId)) out.add("distribute");
    }
    return out;
  }

  private boolean canCreate(DocRecord d) {
    return d.status.equals("ACTIVE")
        && access.role().permissions.contains("doc.write")
        && (d.ownerId.equals(access.current().id) || d.creatorId.equals(access.current().id))
        && !open(d);
  }

  private boolean open(DocRecord d) {
    return revisions(d).stream()
        .anyMatch(r -> Set.of("DRAFT", "REJECTED", "REVIEW", "APPROVED").contains(r.status));
  }

  private void newRevision(DocRecord d) {
    var r = new DocRevision();
    r.documentId = d.id;
    r.revisionNo = d.nextRevision++;
    r.title = d.title;
    r.authorId = access.current().id;
    r.status = "DRAFT";
    r.content = "";
    r.changeSummary = "";
    r.contentHash = "";
    r.decisionNote = "";
    r.createdAt = clock.instant();
    if (d.currentRevisionId != null) {
      var previous = db.get(DocRevision.class, d.currentRevisionId);
      r.title = previous.title;
      r.content = previous.content;
      r.contentHash = previous.contentHash;
    }
    db.save(r);
    event(d, r, "CREATE_REVISION", "R" + r.revisionNo);
  }

  private Set<Long> recipients(DocRecord d, Set<Long> ids) {
    if (ids == null || ids.isEmpty() || ids.size() > 200)
      throw new Problem(400, "RECIPIENTS_REQUIRED");
    for (var id : ids) {
      assigned(id, d.departmentId, "doc.read");
      assigned(id, d.departmentId, "doc.ack");
    }
    return new HashSet<>(ids);
  }

  private void assign(DocRecord d, DocRevision r, Long target, LocalDate due) {
    if (!db.query(
            ReadAssignment.class,
            "from ReadAssignment where revisionId=?1 and accountId=?2",
            r.id,
            target)
        .isEmpty()) return;
    var a = new ReadAssignment();
    a.documentId = d.id;
    a.revisionId = r.id;
    a.accountId = target;
    a.dueDate = due;
    a.status = "PENDING";
    a.acknowledgedHash = "";
    a.assignedAt = clock.instant();
    db.save(a);
  }

  private void closePending(DocRecord d, Long rid, String status) {
    for (var a : assignments(d))
      if (a.revisionId.equals(rid) && a.status.equals("PENDING")) a.status = status;
  }

  private void event(DocRecord d, DocRevision r, String action, String note) {
    d.changeCount++;
    var e = new DocEvent();
    e.documentId = d.id;
    e.revisionNo = r.revisionNo;
    e.actor = access.current().username;
    e.action = action;
    e.note = note;
    e.createdAt = clock.instant();
    db.save(e);
    access.audit(action, d.id, d.departmentId);
  }

  private boolean replay(DocRecord d, String action, Command v) {
    String key = text(v.requestKey, 80);
    if (!key.matches("[A-Za-z0-9_-]{8,80}")) throw new Problem(400, "INVALID_REQUEST_KEY");
    String f = DocPolicy.hash(action, json.writeValueAsString(v));
    var prior =
        db.query(
            CommandStamp.class,
            "from CommandStamp where documentId=?1 and actor=?2 and requestKey=?3",
            d.id,
            access.current().username,
            key);
    if (!prior.isEmpty()) {
      if (!prior.getFirst().fingerprint.equals(f)) throw new Problem(409, "IDEMPOTENCY_CONFLICT");
      return true;
    }
    var s = new CommandStamp();
    s.documentId = d.id;
    s.actor = access.current().username;
    s.requestKey = key;
    s.fingerprint = f;
    db.save(s);
    return false;
  }

  private DocRecord locked(Long id) {
    var d = db.lock(DocRecord.class, id);
    visible(d);
    return d;
  }

  private DocRevision revision(DocRecord d, Long id) {
    var r = db.get(DocRevision.class, id);
    if (!r.documentId.equals(d.id)) throw new Problem(404, "NOT_FOUND");
    return r;
  }

  private List<DocRevision> revisions(DocRecord d) {
    return db.query(
        DocRevision.class, "from DocRevision where documentId=?1 order by revisionNo desc", d.id);
  }

  private List<ReadAssignment> assignments(DocRecord d) {
    return db.query(
        ReadAssignment.class, "from ReadAssignment where documentId=?1 order by id", d.id);
  }

  private List<DocEvent> events(DocRecord d) {
    return db.query(DocEvent.class, "from DocEvent where documentId=?1 order by id desc", d.id);
  }

  private void author(DocRevision r) {
    if (!r.authorId.equals(access.current().id)) throw new Problem(403, "NOT_AUTHOR");
  }

  private void owner(DocRecord d) {
    if (!d.ownerId.equals(access.current().id)) throw new Problem(403, "NOT_OWNER");
  }

  private void reviewer(DocRecord d, DocRevision r) {
    if (!Objects.equals(r.reviewerId, access.current().id)) throw new Problem(403, "NOT_REVIEWER");
    DocPolicy.independent(d, r);
  }

  private void active(DocRecord d) {
    if (!d.status.equals("ACTIVE")) throw new Problem(409, "DOCUMENT_RETIRED");
  }

  private void assigned(Long id, Long dept, String permission) {
    var a = db.get(Account.class, id);
    if (!a.enabled
        || !a.departmentId.equals(dept)
        || !db.get(AccessRole.class, a.roleId).permissions.contains(permission))
      throw new Problem(400, "INVALID_ASSIGNEE");
  }

  private LocalDate today() {
    return LocalDate.now(
        clock.withZone(
            ZoneId.of(
                db.query(SystemSetting.class, "from SystemSetting where code='timezone'")
                    .getFirst()
                    .value)));
  }

  private static String text(String v, int max) {
    return AdminService.text(v, max);
  }

  private static String optional(String v, int max) {
    if (v == null) return "";
    if (v.length() > max) throw new Problem(400, "INVALID_INPUT");
    return v.trim();
  }
}
