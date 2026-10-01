// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.docflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** HTTP 全流程、权限、历史冻结、并发重试与 Flyway 迁移验收。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(DocIntegrationTest.TimeConfig.class)
class DocIntegrationTest {
  static final String password = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("docflow.admin-password", () -> password);
  }

  @org.springframework.boot.test.context.TestConfiguration
  static class TimeConfig {
    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Primary
    TestClock testClock() {
      return new TestClock();
    }
  }

  /** 仅测试使用的可推进时钟。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  static class TestClock extends Clock {
    volatile Instant now = Instant.now();

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return Clock.fixed(now, zone);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired TestClock clock;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  MockHttpSession admin, writer, owner, reviewer, reader, otherReader, stranger;
  long dept,
      ownerId,
      reviewerId,
      readerId,
      otherReaderId,
      writerId,
      id,
      rid,
      writerRole,
      ownerRole,
      reviewRole,
      readRole;
  JsonNode d;
  String writerName;

  LocalDate today() {
    return LocalDate.ofInstant(clock.now, ZoneId.of("Asia/Shanghai"));
  }

  @BeforeEach
  void setup() throws Exception {
    clock.now = Instant.now();
    admin = login("admin", password);
    String s = UUID.randomUUID().toString().substring(0, 8);
    dept = ok(admin, "POST", "/admin/departments", Map.of("name", "文控验收-" + s)).path("id").asLong();
    for (var r : ok(admin, "GET", "/admin/roles", null))
      switch (r.path("name").asString()) {
        case "文件编写人" -> writerRole = r.path("id").asLong();
        case "文控责任人" -> ownerRole = r.path("id").asLong();
        case "独立审批人" -> reviewRole = r.path("id").asLong();
        case "文件阅读人" -> readRole = r.path("id").asLong();
        default -> {}
      }
    writerName = "writer-" + s;
    writerId = user(writerName, writerRole);
    writer = login(writerName, password);
    ownerId = user("owner-" + s, ownerRole);
    owner = login("owner-" + s, password);
    reviewerId = user("review-" + s, reviewRole);
    reviewer = login("review-" + s, password);
    readerId = user("read-" + s, readRole);
    reader = login("read-" + s, password);
    otherReaderId = user("other-" + s, readRole);
    otherReader = login("other-" + s, password);
    user("stranger-" + s, readRole);
    stranger = login("stranger-" + s, password);
    d =
        ok(
            writer,
            "POST",
            "/documents",
            Map.of(
                "title",
                "验收规程（虚构测试资料）",
                "category",
                "SOP",
                "departmentId",
                dept,
                "ownerId",
                ownerId));
    id = d.path("document").path("id").asLong();
    rid = d.path("revisions").get(0).path("revision").path("id").asLong();
  }

  long user(String name, long role) throws Exception {
    return ok(
            admin,
            "POST",
            "/admin/users",
            Map.of(
                "username",
                name,
                "displayName",
                name,
                "password",
                password,
                "roleId",
                role,
                "departmentId",
                dept,
                "enabled",
                true))
        .path("id")
        .asLong();
  }

  Map<String, Object> draft() {
    var m = new LinkedHashMap<String, Object>();
    m.put("version", version());
    m.put("title", "验收规程（虚构测试资料）");
    m.put("content", "1. 作业前复核当前版本。\n2. 按流程核对并记录。\n3. 发现异常暂停并上报。");
    m.put("changeSummary", "初次建立验收规程");
    m.put("reviewerId", reviewerId);
    m.put("effectiveDate", today().toString());
    m.put("reviewDate", today().plusDays(365).toString());
    m.put("acknowledgementDue", today().plusDays(7).toString());
    m.put("recipients", List.of(readerId, otherReaderId));
    return m;
  }

  long version() {
    return d.path("document").path("version").asLong();
  }

  String path() {
    return "/documents/" + id + "/revisions/" + rid;
  }

  Map<String, Object> cmd() {
    return new LinkedHashMap<>(
        Map.of(
            "version",
            version(),
            "requestKey",
            UUID.randomUUID().toString(),
            "note",
            "验收：确认正文完整，职责和步骤明确"));
  }

  void save() throws Exception {
    d = ok(writer, "PUT", path(), draft());
  }

  void act(MockHttpSession s, String a) throws Exception {
    d = ok(s, "POST", path() + "/" + a, cmd());
  }

  void publish() throws Exception {
    save();
    act(writer, "submit");
    act(reviewer, "approve");
    act(owner, "publish");
  }

  JsonNode revision() {
    for (var e : d.path("revisions"))
      if (e.path("revision").path("id").asLong() == rid) return e.path("revision");
    throw new AssertionError("Revision not visible");
  }

  JsonNode assignment(long account) {
    for (var a : d.path("assignments"))
      if (a.path("accountId").asLong() == account && a.path("revisionId").asLong() == rid) return a;
    throw new AssertionError("No assignment");
  }

  String ackPath(long aid) {
    return "/documents/" + id + "/assignments/" + aid + "/acknowledge";
  }

  MockHttpSession login(String name, String p) throws Exception {
    var r = request(null, "POST", "/auth/login", Map.of("username", name, "password", p), true);
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return (MockHttpSession) r.getRequest().getSession();
  }

  MvcResult request(MockHttpSession s, String method, String p, Object body, boolean token)
      throws Exception {
    var b =
        switch (method) {
          case "POST" -> post("/api" + p);
          case "PUT" -> put("/api" + p);
          case "DELETE" -> delete("/api" + p);
          default -> get("/api" + p);
        };
    if (s != null) b.session(s);
    if (token) b.with(csrf());
    if (body != null) b.contentType("application/json").content(json.writeValueAsString(body));
    return mvc.perform(b).andReturn();
  }

  JsonNode ok(MockHttpSession s, String method, String p, Object b) throws Exception {
    var r = request(s, method, p, b, true);
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  void denied(MockHttpSession s, String method, String p, Object b, int code) throws Exception {
    var r = request(s, method, p, b, true);
    assertEquals(code, r.getResponse().getStatus(), r.getResponse().getContentAsString());
  }

  @Test
  void fullFlowAndSnapshot() throws Exception {
    publish();
    assertEquals("PUBLISHED", revision().path("status").asString());
    var a = assignment(readerId);
    var v = Map.of("contentHash", revision().path("contentHash").asString());
    ok(reader, "POST", ackPath(a.path("id").asLong()), v);
    d = ok(owner, "GET", "/documents/" + id, null);
    assertEquals("ACKNOWLEDGED", assignment(readerId).path("status").asString());
    var report = ok(owner, "GET", "/documents/" + id + "/report.json", null);
    assertEquals("CONTROLLED_DOCUMENT_REPORT", report.path("kind").asString());
    assertFalse(report.toString().contains("zhuatech2"));
  }

  @Test
  void readersCannotAccessUnpublishedOrOthers() throws Exception {
    save();
    denied(reader, "GET", "/documents/" + id, null, 403);
    assertEquals(0, ok(reader, "GET", "/documents", null).path("total").asInt());
    publish();
    denied(stranger, "GET", "/documents/" + id, null, 403);
    var v = ok(reader, "GET", "/documents/" + id, null);
    assertEquals(1, v.path("assignments").size());
    assertFalse(v.path("revisions").get(0).path("revision").has("recipients"));
  }

  @Test
  void crossDepartmentDenied() throws Exception {
    denied(admin, "POST", path() + "/publish", cmd(), 403);
    long old = dept;
    dept =
        ok(admin, "POST", "/admin/departments", Map.of("name", "另部门-" + UUID.randomUUID()))
            .path("id")
            .asLong();
    String n = "remote-" + UUID.randomUUID();
    user(n, ownerRole);
    var remote = login(n, password);
    denied(remote, "GET", "/documents/" + id, null, 403);
    dept = old;
  }

  @Test
  void ownerAndAdminCannotEditAnotherAuthorsDraft() throws Exception {
    denied(owner, "PUT", path(), draft(), 403);
    denied(admin, "PUT", path(), draft(), 403);
  }

  @Test
  void reviewerMustBeIndependent() throws Exception {
    var v = draft();
    v.put("reviewerId", ownerId);
    denied(writer, "PUT", path(), v, 400);
  }

  @Test
  void submitRequiresCompleteDraft() throws Exception {
    denied(writer, "POST", path() + "/submit", cmd(), 400);
    var v = draft();
    v.put("recipients", List.of());
    denied(writer, "PUT", path(), v, 400);
  }

  @Test
  void dateOrderAndAudienceDepartmentValidated() throws Exception {
    var v = draft();
    v.put("reviewDate", today().toString());
    denied(writer, "PUT", path(), v, 400);
    v = draft();
    v.put("recipients", List.of(1));
    denied(writer, "PUT", path(), v, 400);
  }

  @Test
  void staleDraftWriteCannotOverride() throws Exception {
    var old = draft();
    save();
    denied(writer, "PUT", path(), old, 409);
  }

  @Test
  void reviewedContentCannotBeEdited() throws Exception {
    save();
    act(writer, "submit");
    denied(writer, "PUT", path(), draft(), 409);
    denied(admin, "POST", path() + "/approve", cmd(), 403);
    act(reviewer, "approve");
    denied(writer, "PUT", path(), draft(), 409);
  }

  @Test
  void approvalReturnRecallAndWithdraw() throws Exception {
    save();
    act(writer, "submit");
    act(reviewer, "reject");
    assertEquals("REJECTED", revision().path("status").asString());
    save();
    act(writer, "submit");
    act(writer, "recall");
    assertEquals("DRAFT", revision().path("status").asString());
    act(writer, "withdraw");
    assertEquals("WITHDRAWN", revision().path("status").asString());
  }

  @Test
  void futureEffectiveDateGatesPublish() throws Exception {
    var v = draft();
    v.put("effectiveDate", today().plusDays(1).toString());
    d = ok(writer, "PUT", path(), v);
    act(writer, "submit");
    act(reviewer, "approve");
    denied(owner, "POST", path() + "/publish", cmd(), 409);
    clock.now = clock.now.plusSeconds(86400);
    act(owner, "publish");
    assertEquals("PUBLISHED", revision().path("status").asString());
  }

  @Test
  void acknowledgementRequiresRecipientAndExactContent() throws Exception {
    publish();
    long aid = assignment(readerId).path("id").asLong();
    denied(reader, "POST", ackPath(aid), Map.of("contentHash", "wrong"), 409);
    denied(
        otherReader,
        "POST",
        ackPath(aid),
        Map.of("contentHash", revision().path("contentHash").asString()),
        403);
  }

  @Test
  void duplicateAcknowledgementIsSafe() throws Exception {
    publish();
    long aid = assignment(readerId).path("id").asLong();
    var v = Map.of("contentHash", revision().path("contentHash").asString());
    ok(reader, "POST", ackPath(aid), v);
    d = ok(owner, "GET", "/documents/" + id, null);
    int n = d.path("events").size();
    ok(reader, "POST", ackPath(aid), v);
    assertEquals(n, ok(owner, "GET", "/documents/" + id, null).path("events").size());
  }

  @Test
  void replacementPreservesAcknowledgedEvidenceAndClosesOldTasks() throws Exception {
    publish();
    long oldRid = rid, oldAid = assignment(otherReaderId).path("id").asLong();
    String oldHash = revision().path("contentHash").asString();
    ok(
        reader,
        "POST",
        ackPath(assignment(readerId).path("id").asLong()),
        Map.of("contentHash", oldHash));
    d = ok(owner, "GET", "/documents/" + id, null);
    d = ok(writer, "POST", "/documents/" + id + "/revisions", Map.of("version", version()));
    rid = d.path("revisions").get(0).path("revision").path("id").asLong();
    assertNotEquals(oldRid, rid);
    var v = draft();
    v.put("content", "新版验收正文");
    v.put("changeSummary", "更新作业要求");
    d = ok(writer, "PUT", path(), v);
    act(writer, "submit");
    act(reviewer, "approve");
    act(owner, "publish");
    denied(otherReader, "POST", ackPath(oldAid), Map.of("contentHash", oldHash), 409);
    for (var a : d.path("assignments"))
      if (a.path("revisionId").asLong() == oldRid) {
        assertEquals(
            a.path("accountId").asLong() == readerId ? "ACKNOWLEDGED" : "SUPERSEDED",
            a.path("status").asString());
        if (a.path("accountId").asLong() == readerId)
          assertEquals(oldHash, a.path("acknowledgedHash").asString());
      }
    assertEquals(2, d.path("revisions").size());
  }

  @Test
  void onlyOneOpenRevision() throws Exception {
    denied(writer, "POST", "/documents/" + id + "/revisions", Map.of("version", version()), 409);
  }

  @Test
  void publishedHistoryCannotBeDeleted() throws Exception {
    publish();
    denied(writer, "DELETE", "/documents/" + id + "?version=" + version(), null, 409);
  }

  @Test
  void neverReviewedDraftCanBeDeleted() throws Exception {
    save();
    ok(writer, "DELETE", "/documents/" + id + "?version=" + version(), null);
    denied(writer, "GET", "/documents/" + id, null, 404);
  }

  @Test
  void commandRetriesAndChangedPayload() throws Exception {
    save();
    var c = cmd();
    d = ok(writer, "POST", path() + "/submit", c);
    int n = d.path("events").size();
    d = ok(writer, "POST", path() + "/submit", c);
    assertEquals(n, d.path("events").size());
    c.put("note", "changed");
    denied(writer, "POST", path() + "/submit", c, 409);
  }

  @Test
  void concurrentSubmitHasSingleHistoryEntry() throws Exception {
    save();
    var c = cmd();
    int before = d.path("events").size();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var tasks =
          List.<Callable<Integer>>of(
              () -> request(writer, "POST", path() + "/submit", c, true).getResponse().getStatus(),
              () -> request(writer, "POST", path() + "/submit", c, true).getResponse().getStatus());
      for (var r : pool.invokeAll(tasks)) assertEquals(200, r.get());
    }
    assertEquals(before + 1, ok(writer, "GET", "/documents/" + id, null).path("events").size());
  }

  @Test
  void distributionDeduplicatesRecipients() throws Exception {
    publish();
    var c = cmd();
    c.put("recipients", List.of(readerId));
    c.put("dueDate", today().plusDays(9).toString());
    d = ok(owner, "POST", path() + "/distribute", c);
    assertEquals(2, d.path("assignments").size());
  }

  @Test
  void retireRetainsHistoryAndClosesPendingReading() throws Exception {
    publish();
    d = ok(owner, "POST", "/documents/" + id + "/retire", cmd());
    assertEquals("RETIRED", d.path("document").path("status").asString());
    assertEquals("WITHDRAWN", assignment(readerId).path("status").asString());
    denied(
        reader,
        "POST",
        ackPath(assignment(readerId).path("id").asLong()),
        Map.of("contentHash", revision().path("contentHash").asString()),
        409);
  }

  @Test
  void overdueMetricsUseBusinessClock() throws Exception {
    publish();
    clock.now = clock.now.plusSeconds(86400L * 366);
    var v = ok(owner, "GET", "/dashboard", null);
    assertEquals(1, v.path("reviewOverdue").asInt());
    assertEquals(2, v.path("overdue").asInt());
  }

  @Test
  void searchPaginationAndSort() throws Exception {
    assertEquals(
        1, ok(writer, "GET", "/documents?search=虚构&size=1&sort=title", null).path("total").asInt());
    assertEquals(0, ok(writer, "GET", "/documents?page=1&size=1", null).path("items").size());
    denied(writer, "GET", "/documents?sort=bad", null, 400);
  }

  @Test
  void longUnicodeBodyPersists() throws Exception {
    var v = draft();
    String body = "中文测试正文".repeat(1800);
    v.put("content", body);
    d = ok(writer, "PUT", path(), v);
    assertEquals(body, revision().path("content").asString());
  }

  @Test
  void csrfAndAdminProtection() throws Exception {
    assertEquals(
        403, request(writer, "POST", path() + "/submit", cmd(), false).getResponse().getStatus());
    denied(reader, "GET", "/admin/users", null, 403);
    for (var a : ok(admin, "GET", "/admin/users", null)) {
      assertFalse(a.has("passwordHash"));
      if (a.path("username").asString().equals("admin"))
        denied(admin, "DELETE", "/admin/users/" + a.path("id").asLong(), null, 409);
    }
  }

  @Test
  void passwordChangeRevokesSession() throws Exception {
    ok(
        writer,
        "POST",
        "/auth/password",
        Map.of("oldPassword", password, "newPassword", "Bb8" + UUID.randomUUID()));
    assertTrue(writer.isInvalid());
  }
}
