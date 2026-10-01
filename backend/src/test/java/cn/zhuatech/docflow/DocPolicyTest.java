// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.docflow;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;

/** 独立审批、日期边界、陈旧写入和摘要规则验收。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class DocPolicyTest {
  @Test
  void staleVersion() {
    var d = new DocRecord();
    d.version = 2L;
    assertThrows(Problem.class, () -> DocPolicy.version(d, 1L));
    assertThrows(Problem.class, () -> DocPolicy.version(d, null));
    DocPolicy.version(d, 2L);
  }

  @Test
  void independentFromThreeResponsiblePeople() {
    var d = new DocRecord();
    d.ownerId = 1L;
    d.creatorId = 2L;
    var r = new DocRevision();
    r.authorId = 3L;
    for (long i = 1; i <= 3; i++) {
      r.reviewerId = i;
      assertThrows(Problem.class, () -> DocPolicy.independent(d, r));
    }
    r.reviewerId = 4L;
    DocPolicy.independent(d, r);
  }

  @Test
  void reviewMustFollowEffect() {
    var e = LocalDate.of(2026, 10, 1);
    assertThrows(Problem.class, () -> DocPolicy.dates(e, e, e));
    assertThrows(Problem.class, () -> DocPolicy.dates(e, e.plusDays(1), e.minusDays(1)));
    DocPolicy.dates(e, e.plusDays(1), e);
  }

  @Test
  void missingDatesRejected() {
    var e = LocalDate.now();
    assertThrows(Problem.class, () -> DocPolicy.dates(null, e, e));
    assertThrows(Problem.class, () -> DocPolicy.dates(e, null, e));
    assertThrows(Problem.class, () -> DocPolicy.dates(e, e.plusDays(1), null));
  }

  @Test
  void hashesBindTitleAndUnicodeBody() {
    assertEquals(64, DocPolicy.hash("规程", "正文😊").length());
    assertNotEquals(DocPolicy.hash("规程", "正文"), DocPolicy.hash("规程", "正文2"));
    assertNotEquals(DocPolicy.hash("规程", "正文"), DocPolicy.hash("规程2", "正文"));
    assertEquals(DocPolicy.hash("规程", "正文"), DocPolicy.hash("规程", "正文"));
  }

  @Test
  void invalidStateCannotBeApproved() {
    var r = new DocRevision();
    r.status = "PUBLISHED";
    assertThrows(Problem.class, () -> DocPolicy.state(r, "REVIEW"));
  }

  @Test
  void bcryptByteLimit() {
    assertThrows(Problem.class, () -> AdminService.validatePassword("Aa9" + "测".repeat(30)));
    assertThrows(Problem.class, () -> AdminService.validatePassword("weak"));
    AdminService.validatePassword("Aa9" + "测".repeat(15));
  }
}
