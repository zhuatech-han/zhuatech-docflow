// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.docflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** 版本、日期、独立审批和内容摘要的纯业务规则。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class DocPolicy {
  private DocPolicy() {}

  /** 拒绝过期页面覆盖。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void version(DocRecord d, Long v) {
    if (v == null || !v.equals(d.version)) throw new Problem(409, "STALE_VERSION");
  }

  /** 严格校验修订状态。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void state(DocRevision r, String... expected) {
    if (!Set.of(expected).contains(r.status)) throw new Problem(409, "INVALID_STATE");
  }

  /** 审核人与作者、目录责任人及建档人独立。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void independent(DocRecord d, DocRevision r) {
    if (r.reviewerId == null
        || r.reviewerId.equals(r.authorId)
        || r.reviewerId.equals(d.ownerId)
        || r.reviewerId.equals(d.creatorId)) throw new Problem(400, "INDEPENDENT_REVIEW_REQUIRED");
  }

  /** 生效、复审和签收日期具有明确顺序。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void dates(LocalDate effective, LocalDate review, LocalDate acknowledgement) {
    if (effective == null
        || review == null
        || acknowledgement == null
        || !review.isAfter(effective)
        || acknowledgement.isBefore(effective)) throw new Problem(400, "INVALID_DATES");
  }

  /** 对标题及正文计算UTF8 SHA256；不是电子签名。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String hash(String title, String content) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest((title + "\n" + content).getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
