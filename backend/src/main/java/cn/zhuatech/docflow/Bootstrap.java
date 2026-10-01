// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.docflow;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库初始化文控权限、菜单与强密码管理员，重启保持原有数据。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String password;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${docflow.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.password = password;
  }

  /** 初始化基础配置，不植入业务记录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    var d = new Department();
    d.name = "总部";
    db.save(d);
    var names =
        Map.ofEntries(
            Map.entry("doc.read", "阅读授权文件"),
            Map.entry("doc.write", "编制文件修订"),
            Map.entry("doc.review", "独立审批修订"),
            Map.entry("doc.publish", "发布与分发文件"),
            Map.entry("doc.ack", "签收本人阅读任务"),
            Map.entry("dashboard", "文控统计"),
            Map.entry("export", "导出授权版本"),
            Map.entry("audit", "操作审计"),
            Map.entry("admin", "系统管理"));
    for (var e : new TreeMap<>(names).entrySet()) {
      var p = new Permission();
      p.code = e.getKey();
      p.name = e.getValue();
      db.save(p);
    }
    role("管理员", "ALL", names.keySet());
    role(
        "文控责任人",
        "DEPARTMENT",
        Set.of("doc.read", "doc.write", "doc.publish", "doc.ack", "dashboard", "export", "audit"));
    role("文件编写人", "ASSIGNED", Set.of("doc.read", "doc.write", "doc.ack", "dashboard", "export"));
    role(
        "独立审批人",
        "DEPARTMENT",
        Set.of("doc.read", "doc.review", "doc.ack", "dashboard", "export", "audit"));
    role("文件阅读人", "ASSIGNED", Set.of("doc.read", "doc.ack", "dashboard"));
    var a = new Account();
    a.username = "admin";
    a.displayName = "管理员";
    a.passwordHash = encoder.encode(password);
    a.roleId = db.all(AccessRole.class).getFirst().id;
    a.departmentId = d.id;
    a.enabled = true;
    db.save(a);
    String[][] menus = {
      {"workbench", "我的待办", "My work", "doc.read"},
      {"documents", "文件目录", "Documents", "doc.read"},
      {"dashboard", "文控统计", "Overview", "dashboard"},
      {"audit", "操作审计", "Audit", "audit"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles", "admin"},
      {"departments", "部门", "Departments", "admin"},
      {"menus", "导航管理", "Menus", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "文件类别", "Categories", "admin"},
      {"settings", "系统参数", "Settings", "admin"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    for (var e : Map.of("timezone", "Asia/Shanghai", "companyName", "知华受控文档与制度签收").entrySet()) {
      var s = new SystemSetting();
      s.code = e.getKey();
      s.value = e.getValue();
      db.save(s);
    }
    String[][] categories = {
      {"POLICY", "管理制度", "Policy"}, {"SOP", "操作规程", "SOP"}, {"GUIDE", "工作指引", "Guide"}
    };
    for (var c : categories) {
      var x = new DictionaryEntry();
      x.type = "category";
      x.code = c[0];
      x.name = c[1];
      x.nameEn = c[2];
      db.save(x);
    }
  }

  private void role(String name, String scope, Set<String> permissions) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions = new HashSet<>(permissions);
    db.save(r);
  }
}
