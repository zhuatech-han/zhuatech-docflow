// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
export const states = {
  ACTIVE: ["在用", "Active"],
  RETIRED: ["已归档", "Archived"],
  DRAFT: ["修订草稿", "Draft"],
  REVIEW: ["待审批", "Review"],
  APPROVED: ["待发布", "Approved"],
  REJECTED: ["已退回", "Returned"],
  PUBLISHED: ["当前生效", "Current"],
  SUPERSEDED: ["已替代", "Superseded"],
  WITHDRAWN: ["已撤销", "Withdrawn"],
  PENDING: ["待签收", "Pending"],
  ACKNOWLEDGED: ["已签收", "Acknowledged"],
};
export const commands = {
  edit: ["编辑草稿", "Edit draft"],
  submit: ["提交审批", "Submit"],
  recall: ["撤回审批", "Recall"],
  approve: ["通过修订", "Approve"],
  reject: ["退回修改", "Return"],
  publish: ["发布生效", "Publish"],
  distribute: ["补充分发", "Distribute"],
  withdraw: ["撤销修订", "Withdraw"],
  retire: ["归档文件", "Archive"],
};
export const labels = {
  title: ["文件名称", "Title"],
  category: ["文件类别", "Category"],
  departmentId: ["部门", "Department"],
  ownerId: ["文控责任人", "Document owner"],
  content: ["正文", "Content"],
  changeSummary: ["变更说明", "Change summary"],
  reviewerId: ["独立审批人", "Reviewer"],
  effectiveDate: ["生效日期", "Effective date"],
  reviewDate: ["下次复审日期", "Next review"],
  acknowledgementDue: ["签收截止日期", "Acknowledgement due"],
  recipients: ["签收人员", "Recipients"],
  dueDate: ["签收截止日期", "Due date"],
  note: ["意见或原因", "Decision or reason"],
  name: ["名称", "Name"],
  nameEn: ["英文名称", "English name"],
  username: ["登录账号", "Username"],
  displayName: ["显示名称", "Display name"],
  password: ["初始或重置密码", "Initial or reset password"],
  roleId: ["角色", "Role"],
  enabled: ["启用", "Enabled"],
  scope: ["数据范围", "Data scope"],
  permissions: ["权限", "Permissions"],
  permissionCode: ["菜单所需权限", "Required permission"],
  position: ["菜单顺序", "Position"],
  type: ["字典类型", "Dictionary type"],
  code: ["代码", "Code"],
  value: ["参数值", "Value"],
  oldPassword: ["当前密码", "Current password"],
  newPassword: ["新密码", "New password"],
};
export const scopeNames = {
  ALL: ["全部数据", "All data"],
  DEPARTMENT: ["本部门", "Department"],
  ASSIGNED: ["本人关联", "Assigned"],
};
export const adminFields = {
  users: [
    "username",
    "displayName",
    "password",
    "roleId",
    "departmentId",
    "enabled",
  ],
  roles: ["name", "scope", "permissions"],
  departments: ["name"],
  menus: ["name", "nameEn", "permissionCode", "position", "enabled"],
  permissions: ["name"],
  dictionaries: ["type", "code", "name", "nameEn"],
  settings: ["value"],
};
export const errors = {
  NETWORK_ERROR: ["连接失败，请稍后重试", "Connection failed. Retry."],
  UNAUTHENTICATED: ["登录已失效，请重新登录", "Session expired. Sign in."],
  LOGIN_FAILED: [
    "账号、密码错误或账号停用",
    "Invalid credentials or disabled account.",
  ],
  LOGIN_THROTTLED: ["请五分钟后重试登录", "Retry login in five minutes."],
  FORBIDDEN: ["没有操作权限", "Permission denied."],
  OUT_OF_SCOPE: ["没有该记录的数据权限", "Outside your data scope."],
  INVALID_INPUT: [
    "请检查必填项、长度和日期",
    "Check required fields, lengths and dates.",
  ],
  INVALID_STATE: ["状态已改变，请刷新详情", "State changed. Refresh detail."],
  STALE_VERSION: [
    "记录已更新，请刷新后操作",
    "Record changed. Refresh detail.",
  ],
  INDEPENDENT_REVIEW_REQUIRED: [
    "复核人必须独立于报告与执行人员",
    "Reviewer must be independent of reporting and execution.",
  ],
  INVALID_ASSIGNEE: [
    "责任人须启用、属于本部门且具有相应权限",
    "Assignee must be active, in the same department and authorized.",
  ],
  NOT_REVIEWER: [
    "仅指定复核人可操作",
    "Only the designated reviewer may do this.",
  ],
  INVALID_DICTIONARY: ["来源或类别不存在", "Unknown source or category."],
  IDEMPOTENCY_CONFLICT: [
    "请求内容已改变，请重新打开操作",
    "Request payload changed. Reopen operation.",
  ],
  CONFLICT: ["数据重复或仍被其他记录引用", "Duplicate or referenced data."],
  LAST_ADMIN: [
    "须保留一个启用的全范围管理员",
    "Keep one active all-data administrator.",
  ],
  WEAK_PASSWORD: [
    "密码至少 12 位、含大写小写及数字，UTF-8 不超过 72 字节",
    "Use at least 12 characters with upper case, lower case and digits; UTF-8 up to 72 bytes.",
  ],
  OLD_PASSWORD_INVALID: ["当前密码不正确", "Current password is incorrect."],
  BUILTIN_RESOURCE: [
    "内建资源不能删除",
    "Built-in resource cannot be deleted.",
  ],
  NOT_FOUND: ["记录不存在", "Record not found."],
  INVALID_USERNAME: [
    "账号须为 3–60 位字母、数字、点、横线或下划线",
    "Invalid username.",
  ],
  INVALID_SCOPE: ["数据范围不正确", "Invalid data scope."],
  INVALID_PERMISSION: ["权限代码不存在", "Unknown permission code."],
  REGISTERED_PERMISSIONS_ONLY: [
    "只能编辑已登记权限的名称",
    "Only registered permission names may be edited.",
  ],
  REGISTERED_MENUS_ONLY: [
    "只能编辑已登记菜单",
    "Only registered menus may be edited.",
  ],
  REGISTERED_SETTINGS_ONLY: [
    "只能编辑已登记参数",
    "Only registered settings may be edited.",
  ],
  INVALID_SETTING: ["参数不正确", "Invalid setting."],
  INVALID_REQUEST_KEY: ["请重新打开操作", "Reopen the operation."],
};

Object.assign(errors, {
  INDEPENDENT_REVIEW_REQUIRED: [
    "审批人不得是编写人、建档人或文控责任人",
    "Reviewer must be independent of author, creator and owner.",
  ],
  NOT_AUTHOR: ["仅该版本编写人可操作", "Only the revision author may do this."],
  NOT_OWNER: ["仅文控责任人可操作", "Only the document owner may do this."],
  NOT_REVIEWER: [
    "仅指定审批人可操作",
    "Only the designated reviewer may do this.",
  ],
  NOT_RECIPIENT: ["仅本人可签收", "Only the recipient may acknowledge."],
  RECIPIENTS_REQUIRED: ["请选择 1–200 位签收人员", "Choose 1–200 recipients."],
  INVALID_DATES: [
    "复审日期须晚于生效日期，签收截止日期不得早于生效日期",
    "Review must follow effective date; acknowledgement due must not precede it.",
  ],
  NOT_EFFECTIVE_YET: ["尚未到生效日期", "Effective date has not arrived."],
  OPEN_REVISION_EXISTS: ["仍有未完成的修订", "An unfinished revision exists."],
  DOCUMENT_RETIRED: ["文件已归档", "Document has been archived."],
  OLD_REVISION: [
    "该版本已停止生效，请阅读当前版本",
    "Read the current revision.",
  ],
  CONTENT_CHANGED: [
    "正文已改变，请重新打开详情",
    "Content changed. Reopen detail.",
  ],
  HISTORY_PROTECTED: [
    "已送审或发布的历史不能删除",
    "Reviewed or published history cannot be deleted.",
  ],
  INVALID_DICTIONARY: ["文件类别不存在", "Unknown category."],
});
/** 依据当前生效版本及本人任务显示签收入口；服务端独立校验。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function readingTask(detail, revision, me) {
  if (
    !detail ||
    !revision ||
    !me ||
    detail.document.status !== "ACTIVE" ||
    revision.status !== "PUBLISHED" ||
    detail.document.currentRevisionId !== revision.id ||
    !me.permissions.includes("doc.ack")
  )
    return null;
  return (
    detail.assignments.find(
      (a) =>
        a.revisionId === revision.id &&
        a.accountId === me.id &&
        a.status === "PENDING",
    ) || null
  );
}
/** 时间显示遵循系统时区。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function date(value, zone = "Asia/Shanghai") {
  return value
    ? new Intl.DateTimeFormat("zh-CN", {
        dateStyle: "short",
        timeStyle: "short",
        timeZone: zone,
      }).format(new Date(value))
    : "—";
}
/** 将审计代码映射为操作名称。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function eventName(code, lang = "zh") {
  const fixed = {
    CREATE_REVISION: ["开始修订", "Start revision"],
    SAVE_DRAFT: ["保存草稿", "Save draft"],
    ACKNOWLEDGE: ["签收版本", "Acknowledge"],
    DELETE_DRAFT_DOCUMENT: ["删除未送审目录", "Delete draft document"],
    LOGIN: ["登录", "Sign in"],
    PASSWORD_CHANGE: ["修改密码", "Change password"],
  };
  const pair = fixed[code] || commands[code.toLowerCase()];
  return pair
    ? pair[lang === "zh" ? 0 : 1]
    : code.startsWith("ADMIN_")
      ? lang === "zh"
        ? "管理资料变更"
        : "Administration change"
      : code;
}
