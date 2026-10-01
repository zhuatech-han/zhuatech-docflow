// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.docflow;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 文档与管理接口；实际授权、版本和状态由事务服务核验。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final DocService service;
  final AdminService admin;

  public ApiController(DocService service, AdminService admin) {
    this.service = service;
    this.admin = admin;
  }

  /** 授权人员与类别。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return service.options();
  }

  /** 目录搜索、状态类别筛选与分页排序。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/documents")
  public Object list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "") String category,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return service.list(search, status, category, page, size, sort);
  }

  /** 建档并创建本人第一版草稿。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/documents")
  public Object create(@RequestBody DocService.Catalog v) {
    return service.create(v);
  }

  /** 版本及签收详情。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/documents/{id}")
  public Object detail(@PathVariable Long id) {
    return service.detail(id);
  }

  /** 删除未送审的新建目录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/documents/{id}")
  public Object delete(@PathVariable Long id, @RequestParam Long version) {
    service.delete(id, version);
    return Map.of("ok", true);
  }

  /** 开始下一次修订。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/documents/{id}/revisions")
  public Object revision(@PathVariable Long id, @RequestBody Map<String, Long> v) {
    return service.startRevision(id, v.get("version"));
  }

  /** 保存本人草稿。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/documents/{id}/revisions/{rid}")
  public Object save(
      @PathVariable Long id, @PathVariable Long rid, @RequestBody DocService.Draft v) {
    return service.save(id, rid, v);
  }

  /** 有限审批和发布命令。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/documents/{id}/revisions/{rid}/{action}")
  public Object act(
      @PathVariable Long id,
      @PathVariable Long rid,
      @PathVariable String action,
      @RequestBody DocService.Command v) {
    return service.act(id, rid, action, v);
  }

  /** 归档目录，保留版本及已签收证据。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/documents/{id}/retire")
  public Object retire(@PathVariable Long id, @RequestBody DocService.Command v) {
    return service.retire(id, v);
  }

  /** 本人签收当前版本。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/documents/{id}/assignments/{aid}/acknowledge")
  public Object acknowledge(
      @PathVariable Long id, @PathVariable Long aid, @RequestBody DocService.Acknowledgement v) {
    return service.acknowledge(id, aid, v);
  }

  /** 本人阅读及审批待办。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/workbench")
  public Object work() {
    return service.workbench();
  }

  /** 数据范围内的文控指标。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return service.dashboard();
  }

  /** 操作审计。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  public Object audit() {
    return service.audit();
  }

  /** JSON受控文件快照，文件名只用服务端数值标识。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/documents/{id}/report.json")
  public ResponseEntity<String> export(@PathVariable Long id) {
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=document-report-" + id + ".json")
        .contentType(MediaType.APPLICATION_JSON)
        .body(service.export(id));
  }

  /** 管理目录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object listAdmin(@PathVariable String type) {
    return admin.list(type);
  }

  /** 创建管理资源。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object createAdmin(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 修改管理资源。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object updateAdmin(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 删除无引用的管理资源。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object deleteAdmin(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}
