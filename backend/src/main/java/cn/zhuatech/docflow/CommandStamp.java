// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.docflow;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

/** 业务命令幂等记录，冲突载荷不能重复占用同一请求键。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "command_stamp")
public class CommandStamp {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "document_id")
  public Long documentId;

  @Column(name = "actor")
  public String actor;

  @Column(name = "request_key")
  public String requestKey;

  @Column(name = "fingerprint")
  public String fingerprint;
}
