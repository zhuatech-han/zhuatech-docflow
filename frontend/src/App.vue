<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { computed, onMounted, ref } from "vue";
import { api, resetCsrf } from "./api.js";
import {
  states,
  commands,
  labels,
  adminFields,
  scopeNames,
  errors,
  date,
  eventName,
  readingTask,
} from "./schema.js";
const language = ref(localStorage.getItem("docflow-language") || "zh"),
  me = ref(null),
  view = ref("workbench"),
  busy = ref(false),
  error = ref(""),
  notice = ref("");
const login = ref({ username: "", password: "" }),
  options = ref({
    people: [],
    departments: [],
    dictionaries: [],
    settings: [],
  }),
  directory = ref({ roles: [], departments: [], permissions: [] });
const rows = ref([]),
  total = ref(0),
  work = ref({ revisions: [], reads: [] }),
  stats = ref({}),
  detail = ref(null),
  selectedId = ref(null),
  readConfirmed = ref(false),
  showHistory = ref(false),
  modal = ref(null),
  form = ref({});
const search = ref(""),
  status = ref(""),
  category = ref(""),
  sort = ref("newest"),
  page = ref(0);
const tr = (a, b) => (language.value === "zh" ? a : b),
  pair = (v) => v?.[language.value === "zh" ? 0 : 1] || "—",
  state = (s) => pair(states[s]),
  label = (k) => pair(labels[k]) || k;
const permissions = computed(() => me.value?.permissions || []),
  admin = computed(() => permissions.value.includes("admin"));
const menus = computed(() => me.value?.menus || []),
  selected = computed(() =>
    detail.value?.revisions.find((r) => r.revision.id === selectedId.value),
  ),
  revision = computed(() => selected.value?.revision),
  task = computed(() => readingTask(detail.value, revision.value, me.value));
const zone = computed(
    () =>
      options.value.settings.find((s) => s.code === "timezone")?.value ||
      "Asia/Shanghai",
  ),
  company = computed(
    () =>
      options.value.settings.find((s) => s.code === "companyName")?.value ||
      tr("受控文档与制度签收", "Controlled documents"),
  );
const person = (id) =>
    options.value.people.find((p) => p.id === id)?.displayName || `#${id}`,
  department = (id) =>
    options.value.departments.find((d) => d.id === id)?.name || `#${id}`;
const revisionNo = (id) =>
  detail.value?.revisions.find((r) => r.revision.id === id)?.revision
    .revisionNo || "—";
const shortDate = (v) => date(v, zone.value),
  menuName = (m) => (language.value === "zh" ? m.name : m.nameEn);
function fail(e) {
  error.value =
    pair(errors[e.message]) ||
    tr("操作失败，请刷新后重试", "Operation failed. Refresh and retry.");
  if (e.message === "UNAUTHENTICATED") me.value = null;
}
async function run(fn) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await fn();
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function loadOptions() {
  options.value = await api("/options");
  if (admin.value) {
    for (const key of ["roles", "departments", "permissions"])
      directory.value[key] = await api("/admin/" + key);
  }
}
async function loadView() {
  if (view.value === "documents") {
    const query = new URLSearchParams({
      search: search.value,
      status: status.value,
      category: category.value,
      sort: sort.value,
      page: page.value,
      size: 10,
    });
    const result = await api("/documents?" + query);
    rows.value = result.items;
    total.value = result.total;
  } else if (view.value === "workbench") work.value = await api("/workbench");
  else if (view.value === "dashboard") stats.value = await api("/dashboard");
  else if (view.value === "audit") rows.value = await api("/audit");
  else if (adminFields[view.value])
    rows.value = await api("/admin/" + view.value);
}
async function signIn() {
  await run(async () => {
    me.value = await api("/auth/login", "POST", login.value);
    login.value.password = "";
    resetCsrf();
    view.value = menus.value[0]?.code || "about";
    await loadOptions();
    await loadView();
  });
}
async function signOut() {
  await run(async () => {
    await api("/auth/logout", "POST", {});
    resetCsrf();
    me.value = null;
    detail.value = null;
    login.value.password = "";
  });
}
async function navigate(code) {
  await run(async () => {
    rows.value = [];
    view.value = code;
    detail.value = null;
    page.value = 0;
    await loadView();
  });
}
async function openDocument(id, rid) {
  detail.value = await api("/documents/" + id);
  selectedId.value = rid || detail.value.revisions[0]?.revision.id;
  readConfirmed.value = false;
  showHistory.value = false;
}
function changeLanguage() {
  language.value = language.value === "zh" ? "en" : "zh";
  localStorage.setItem("docflow-language", language.value);
}
function today(offset = 0) {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() + offset);
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: zone.value,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(d);
}
function openModal(kind, row, action) {
  error.value = "";
  let fields = [],
    heading = "",
    v = {};
  if (kind === "catalog") {
    fields = ["title", "category", "departmentId", "ownerId"];
    heading = tr("新建受控文件", "New controlled document");
    v = { category: "SOP", departmentId: me.value.departmentId };
  } else if (kind === "draft") {
    fields = [
      "title",
      "content",
      "changeSummary",
      "reviewerId",
      "effectiveDate",
      "reviewDate",
      "acknowledgementDue",
      "recipients",
    ];
    heading = tr("编辑修订 R", "Edit revision R") + row.revisionNo;
    v = {
      ...row,
      version: detail.value.document.version,
      effectiveDate: row.effectiveDate || today(),
      reviewDate: row.reviewDate || today(365),
      acknowledgementDue: row.acknowledgementDue || today(7),
      recipients: [...(row.recipients || [])],
    };
  } else if (kind === "command") {
    fields =
      action === "distribute"
        ? ["recipients", "dueDate"]
        : ["submit", "publish"].includes(action)
          ? []
          : ["note"];
    heading = pair(commands[action]);
    v = {
      version: detail.value.document.version,
      requestKey: crypto.randomUUID(),
      recipients: [],
      dueDate: today(7),
      note: "",
    };
  } else if (kind === "revision") {
    heading = tr("开始下一次修订", "Start next revision");
    v = { version: detail.value.document.version };
  } else if (kind === "delete") {
    heading = tr("删除未送审目录", "Delete unreviewed document");
  } else if (kind === "admin") {
    fields = adminFields[view.value];
    heading = tr("管理资料", "Administration");
    v = {
      enabled: true,
      scope: "DEPARTMENT",
      permissions: [],
      type: "category",
      ...(row || {}),
      password: "",
    };
  } else if (kind === "deleteAdmin") {
    heading = tr("删除资料", "Delete record");
  } else if (kind === "password") {
    heading = tr("修改密码", "Change password");
    fields = ["oldPassword", "newPassword"];
  }
  modal.value = { kind, row, action, fields, heading };
  form.value = v;
}
async function saveModal() {
  await run(async () => {
    const m = modal.value;
    let result;
    if (m.kind === "catalog") {
      result = await api("/documents", "POST", form.value);
      await openDocument(result.document.id);
    } else if (m.kind === "draft") {
      result = await api(
        `/documents/${detail.value.document.id}/revisions/${m.row.id}`,
        "PUT",
        form.value,
      );
    } else if (m.kind === "command") {
      const suffix =
        m.action === "retire" ? "retire" : `revisions/${m.row.id}/${m.action}`;
      result = await api(
        `/documents/${detail.value.document.id}/${suffix}`,
        "POST",
        form.value,
      );
    } else if (m.kind === "revision") {
      result = await api(
        `/documents/${detail.value.document.id}/revisions`,
        "POST",
        form.value,
      );
      selectedId.value = result.revisions[0]?.revision.id;
    } else if (m.kind === "delete") {
      await api(
        `/documents/${detail.value.document.id}?version=${detail.value.document.version}`,
        "DELETE",
      );
      detail.value = null;
    } else if (m.kind === "admin") {
      const body = { ...form.value };
      if (!body.password) delete body.password;
      await api(
        "/admin/" + view.value + (m.row ? "/" + m.row.id : ""),
        m.row ? "PUT" : "POST",
        body,
      );
      me.value = await api("/auth/me");
      await loadOptions();
    } else if (m.kind === "deleteAdmin") {
      await api("/admin/" + view.value + "/" + m.row.id, "DELETE");
      await loadOptions();
    } else if (m.kind === "password") {
      await api("/auth/password", "POST", form.value);
      me.value = null;
      resetCsrf();
      detail.value = null;
    }
    if (result?.document) {
      detail.value = result;
      readConfirmed.value = false;
    }
    modal.value = null;
    notice.value = tr("已保存", "Saved");
    if (me.value) await loadView();
  });
}
async function acknowledge() {
  await run(async () => {
    if (!readConfirmed.value || !task.value) return;
    await api(
      `/documents/${detail.value.document.id}/assignments/${task.value.id}/acknowledge`,
      "POST",
      { contentHash: revision.value.contentHash },
    );
    await openDocument(detail.value.document.id, selectedId.value);
    await loadView();
    notice.value = tr("已签收此版本", "Revision acknowledged");
  });
}
const inputType = (k) =>
  ["effectiveDate", "reviewDate", "acknowledgementDue", "dueDate"].includes(k)
    ? "date"
    : k.toLowerCase().includes("password")
      ? "password"
      : k === "position"
        ? "number"
        : "text";
const longText = (k) => ["content", "changeSummary", "note"].includes(k);
function choices(k) {
  if (k === "category")
    return options.value.dictionaries
      .filter((d) => d.type === "category")
      .map((d) => ({
        id: d.code,
        name: language.value === "zh" ? d.name : d.nameEn,
      }));
  if (k === "departmentId") return options.value.departments;
  if (k === "roleId") return directory.value.roles;
  if (k === "scope")
    return Object.entries(scopeNames).map(([id, v]) => ({ id, name: pair(v) }));
  if (k === "permissionCode")
    return directory.value.permissions.map((p) => ({
      id: p.code,
      name: p.name,
    }));
  if (["ownerId", "reviewerId", "recipients"].includes(k)) {
    const dept = form.value.departmentId || detail.value?.document.departmentId;
    return options.value.people
      .filter(
        (p) =>
          p.departmentId === dept &&
          (k === "ownerId"
            ? p.permissions.includes("doc.write") &&
              p.permissions.includes("doc.publish")
            : k === "reviewerId"
              ? p.permissions.includes("doc.review")
              : p.permissions.includes("doc.ack") &&
                p.permissions.includes("doc.read")),
      )
      .map((p) => ({ id: p.id, name: p.displayName }));
  }
  if (k === "permissions")
    return directory.value.permissions.map((p) => ({
      id: p.code,
      name: p.name,
    }));
  return null;
}
function adminValue(row, k) {
  if (k === "password") return "—";
  if (k === "roleId")
    return directory.value.roles.find((r) => r.id === row[k])?.name || row[k];
  if (k === "departmentId") return department(row[k]);
  if (k === "enabled")
    return row[k] ? tr("启用", "Enabled") : tr("停用", "Disabled");
  if (k === "scope") return pair(scopeNames[row[k]]);
  if (k === "permissions")
    return (row[k] || [])
      .map(
        (code) =>
          directory.value.permissions.find((p) => p.code === code)?.name ||
          code,
      )
      .join("、");
  return row[k] ?? "—";
}
onMounted(async () => {
  try {
    me.value = await api("/auth/me");
    view.value = menus.value[0]?.code || "about";
    await loadOptions();
    await loadView();
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED") fail(e);
  }
});
</script>
<template>
  <div v-if="!me" class="login-page">
    <form class="login-card" @submit.prevent="signIn">
      <img src="/brand/logo.jpg" alt="知华科技" class="brand-logo" />
      <p class="eyebrow">DOCFLOW / 受控文档</p>
      <h1>{{ tr("登录文控工作台", "Sign in to DocFlow") }}</h1>
      <p>
        {{
          tr(
            "修订 · 审批 · 发布 · 签收",
            "Revise · Review · Publish · Acknowledge",
          )
        }}
      </p>
      <label for="login-user">{{ tr("账号", "Username") }}</label
      ><input
        id="login-user"
        v-model="login.username"
        autocomplete="username"
        required
      /><label for="login-pass">{{ tr("密码", "Password") }}</label
      ><input
        id="login-pass"
        v-model="login.password"
        type="password"
        autocomplete="current-password"
        required
      />
      <p v-if="error" role="alert" class="error">{{ error }}</p>
      <button class="primary" :disabled="busy">
        {{ tr("登录", "Sign in") }}
      </button>
      <div class="login-footer">
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noreferrer"
          >知华科技</a
        ><button type="button" class="link" @click="changeLanguage">
          {{ language === "zh" ? "English" : "中文" }}
        </button>
      </div>
      <small>{{
        tr(
          "公开源码学习版 · 非商业使用",
          "Source learning edition · Noncommercial",
        )
      }}</small>
    </form>
  </div>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" /><strong>DocFlow</strong
        ><small>{{ tr("受控文档与制度签收", "Controlled documents") }}</small>
      </div>
      <nav aria-label="主菜单">
        <button
          v-for="menu in menus"
          :key="menu.id"
          :disabled="busy"
          :class="{ active: view === menu.code }"
          @click="navigate(menu.code)"
        >
          {{ menuName(menu) }}
        </button>
      </nav>
      <div class="sidebar-footer">
        <small>0.1.0 · {{ tr("公开源码学习版", "Learning edition") }}</small
        ><a href="https://www.zhuatech.cn/" target="_blank" rel="noreferrer">{{
          tr("联系知华科技", "Zhuatech")
        }}</a
        ><small>微信 zhuatech / zhuatech2</small>
      </div>
    </aside>
    <div class="workspace">
      <header class="topbar">
        <span>{{ company }}</span>
        <div>
          <span>{{ me.displayName }}</span
          ><button class="link" @click="changeLanguage">
            {{ language === "zh" ? "EN" : "中文" }}</button
          ><button class="link" @click="navigate('about')">
            {{ tr("关于", "About") }}</button
          ><button class="link" @click="openModal('password')">
            {{ tr("改密", "Password") }}</button
          ><button class="link" @click="signOut">
            {{ tr("退出", "Sign out") }}
          </button>
        </div>
      </header>
      <main>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <p v-if="notice" class="notice" role="status">{{ notice }}</p>
        <template v-if="detail"
          ><button class="link back" @click="detail = null">
            ← {{ tr("返回列表", "Back") }}
          </button>
          <div class="page-heading">
            <div>
              <p class="eyebrow">
                {{ detail.document.number }} ·
                {{ department(detail.document.departmentId) }}
              </p>
              <h1>{{ detail.document.title }}</h1>
              <p>
                {{ tr("文控责任人", "Document owner") }}
                {{ person(detail.document.ownerId) }}
                <span class="badge">{{ state(detail.document.status) }}</span>
              </p>
            </div>
            <div class="actions">
              <button
                v-if="detail.canCreateRevision"
                @click="openModal('revision')"
              >
                {{ tr("开始修订", "New revision") }}</button
              ><button
                v-if="detail.canRetire"
                @click="openModal('command', null, 'retire')"
              >
                {{ tr("归档文件", "Archive") }}</button
              ><a
                v-if="permissions.includes('export')"
                class="button"
                :href="`/api/documents/${detail.document.id}/report.json`"
                download
                >{{ tr("导出快照", "Export snapshot") }}</a
              ><button
                v-if="
                  !detail.document.currentRevisionId &&
                  detail.document.creatorId === me.id &&
                  permissions.includes('doc.write') &&
                  detail.revisions.length === 1 &&
                  detail.revisions[0].revision.status === 'DRAFT' &&
                  detail.events.every((e) =>
                    ['CREATE_REVISION', 'SAVE_DRAFT'].includes(e.action),
                  )
                "
                @click="openModal('delete')"
              >
                {{ tr("删除未送审目录", "Delete draft") }}</button
              ><button
                class="link"
                @click="run(() => openDocument(detail.document.id, selectedId))"
              >
                {{ tr("刷新", "Refresh") }}
              </button>
            </div>
          </div>
          <div v-if="detail.document.status === 'RETIRED'" class="warning">
            {{ tr("文件已归档", "Archived document") }}：{{
              detail.document.retirementReason
            }}
          </div>
          <div class="revision-layout">
            <aside class="version-rail">
              <h2>{{ tr("版本", "Revisions") }}</h2>
              <button
                v-for="entry in detail.revisions"
                :key="entry.revision.id"
                :class="{ active: selectedId === entry.revision.id }"
                @click="
                  selectedId = entry.revision.id;
                  readConfirmed = false;
                "
              >
                <strong>R{{ entry.revision.revisionNo }}</strong
                ><span>{{ state(entry.revision.status) }}</span
                ><small>{{ entry.revision.effectiveDate || "—" }}</small>
              </button>
            </aside>
            <section v-if="revision" class="document-body panel">
              <div class="section-heading">
                <h2>
                  {{ revision.title }}
                  <span class="badge"
                    >R{{ revision.revisionNo }} ·
                    {{ state(revision.status) }}</span
                  >
                </h2>
                <div class="actions">
                  <button
                    v-for="action in selected.commands"
                    :key="action"
                    :class="{
                      primary: ['approve', 'publish', 'submit'].includes(
                        action,
                      ),
                    }"
                    @click="
                      action === 'edit'
                        ? openModal('draft', revision)
                        : openModal('command', revision, action)
                    "
                  >
                    {{ pair(commands[action]) }}
                  </button>
                </div>
              </div>
              <p
                v-if="['SUPERSEDED', 'WITHDRAWN'].includes(revision.status)"
                class="warning"
              >
                {{
                  tr(
                    "此版本已停止生效，仅供历史查阅。",
                    "This revision is no longer current. Historical reference only.",
                  )
                }}
              </p>
              <dl class="metadata">
                <div>
                  <dt>{{ tr("编写人", "Author") }}</dt>
                  <dd>{{ person(revision.authorId) }}</dd>
                </div>
                <div>
                  <dt>{{ tr("审批人", "Reviewer") }}</dt>
                  <dd>
                    {{
                      revision.reviewerId ? person(revision.reviewerId) : "—"
                    }}
                  </dd>
                </div>
                <div>
                  <dt>{{ label("effectiveDate") }}</dt>
                  <dd>{{ revision.effectiveDate || "—" }}</dd>
                </div>
                <div>
                  <dt>{{ label("reviewDate") }}</dt>
                  <dd>{{ revision.reviewDate || "—" }}</dd>
                </div>
              </dl>
              <div class="document-text">
                {{
                  revision.content ||
                  tr("正文尚未编写", "Content has not been written.")
                }}
              </div>
              <div class="revision-note">
                <h3>{{ label("changeSummary") }}</h3>
                <p>{{ revision.changeSummary || "—" }}</p>
                <template v-if="revision.decisionNote"
                  ><h3>{{ tr("审批意见", "Review decision") }}</h3>
                  <p>{{ revision.decisionNote }}</p></template
                >
              </div>
              <form
                v-if="task"
                class="acknowledgement"
                @submit.prevent="acknowledge"
              >
                <p>{{ tr("签收截止", "Due") }} {{ task.dueDate }}</p>
                <label
                  ><input v-model="readConfirmed" type="checkbox" />{{
                    tr("我已阅读上述版本正文", "I have read the revision above")
                  }}
                  R{{ revision.revisionNo }}</label
                ><button class="primary" :disabled="!readConfirmed || busy">
                  {{ tr("确认已阅读", "Acknowledge reading") }}
                </button>
              </form>
            </section>
            <div v-else class="panel empty">
              {{ tr("没有可查看的版本", "No accessible revisions") }}
            </div>
          </div>
          <section class="panel">
            <h2>{{ tr("版本签收记录", "Acknowledgements") }}</h2>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{{ tr("版本", "Revision") }}</th>
                    <th>{{ tr("人员", "Recipient") }}</th>
                    <th>{{ tr("截止日期", "Due") }}</th>
                    <th>{{ tr("状态", "Status") }}</th>
                    <th>{{ tr("签收时间", "Acknowledged") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="a in detail.assignments" :key="a.id">
                    <td>R{{ revisionNo(a.revisionId) }}</td>
                    <td>{{ person(a.accountId) }}</td>
                    <td>{{ a.dueDate }}</td>
                    <td>
                      <span class="badge">{{ state(a.status) }}</span>
                    </td>
                    <td>{{ shortDate(a.acknowledgedAt) }}</td>
                  </tr>
                </tbody>
              </table>
              <p v-if="!detail.assignments.length" class="empty">
                {{ tr("暂无签收记录", "No acknowledgements") }}
              </p>
            </div>
          </section>
          <section class="panel">
            <button class="link" @click="showHistory = !showHistory">
              {{ tr("操作历史", "History") }} {{ showHistory ? "−" : "+" }}
            </button>
            <ol v-if="showHistory" class="history">
              <li v-for="e in detail.events" :key="e.id">
                <strong
                  >R{{ e.revisionNo }} ·
                  {{ eventName(e.action, language) }}</strong
                ><span>{{ e.actor }} · {{ shortDate(e.createdAt) }}</span>
                <p>{{ e.note }}</p>
              </li>
            </ol>
          </section></template
        >
        <template v-else-if="view === 'workbench'"
          ><div class="page-heading">
            <div>
              <p class="eyebrow">DOCFLOW / {{ tr("今日待办", "MY WORK") }}</p>
              <h1>{{ tr("我的工作台", "My workbench") }}</h1>
              <p>
                {{
                  tr(
                    "待阅读文件与本人修订、审批事项",
                    "Reading tasks, revisions and reviews assigned to you",
                  )
                }}
              </p>
            </div>
            <button @click="run(loadView)">{{ tr("刷新", "Refresh") }}</button>
          </div>
          <section class="panel">
            <div class="section-heading">
              <h2>{{ tr("待签收文件", "Awaiting my acknowledgement") }}</h2>
              <span class="badge">{{ work.reads.length }}</span>
            </div>
            <div class="reading-grid">
              <article
                v-for="a in work.reads"
                :key="a.assignment.id"
                class="reading-card"
              >
                <p class="eyebrow">
                  {{ a.document.number }} / R{{ a.document.currentRevisionNo }}
                </p>
                <h3>{{ a.document.title }}</h3>
                <p>{{ tr("截止", "Due") }} {{ a.assignment.dueDate }}</p>
                <button
                  class="primary"
                  @click="
                    run(() =>
                      openDocument(a.document.id, a.assignment.revisionId),
                    )
                  "
                >
                  {{ tr("阅读并签收", "Read and acknowledge") }}
                </button>
              </article>
            </div>
            <p v-if="!work.reads.length" class="empty">
              {{ tr("当前没有待签收文件", "No reading tasks pending") }}
            </p>
          </section>
          <section class="panel">
            <h2>{{ tr("修订与审批", "Revisions and reviews") }}</h2>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{{ tr("文件", "Document") }}</th>
                    <th>{{ tr("版本", "Revision") }}</th>
                    <th>{{ tr("状态", "Status") }}</th>
                    <th>{{ tr("操作", "Action") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in work.revisions" :key="r.revision.id">
                    <td>
                      {{ r.document.title
                      }}<small>{{ r.document.number }}</small>
                    </td>
                    <td>R{{ r.revision.revisionNo }}</td>
                    <td>
                      <span class="badge">{{ state(r.revision.status) }}</span>
                    </td>
                    <td>
                      <button
                        @click="
                          run(() => openDocument(r.document.id, r.revision.id))
                        "
                      >
                        {{ tr("处理", "Open") }}
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
              <p v-if="!work.revisions.length" class="empty">
                {{ tr("当前没有待办修订", "No revision tasks pending") }}
              </p>
            </div>
          </section></template
        >
        <template v-else-if="view === 'documents'"
          ><div class="page-heading">
            <div>
              <p class="eyebrow">DOCFLOW / {{ tr("受控目录", "REGISTER") }}</p>
              <h1>{{ tr("受控文档", "Controlled documents") }}</h1>
              <p>
                {{
                  tr(
                    "生效版本、修订记录与签收进度",
                    "Current revisions and acknowledgement records",
                  )
                }}
              </p>
            </div>
            <button
              v-if="permissions.includes('doc.write')"
              class="primary"
              @click="openModal('catalog')"
            >
              + {{ tr("新建文件", "New document") }}
            </button>
          </div>
          <form
            class="filters panel"
            @submit.prevent="
              page = 0;
              run(loadView);
            "
          >
            <input
              v-model="search"
              :placeholder="tr('搜索编号或名称', 'Search number or title')"
              :aria-label="tr('搜索编号或名称', 'Search')"
            /><select v-model="status" :aria-label="tr('状态', 'Status')">
              <option value="">{{ tr("全部状态", "All statuses") }}</option>
              <option value="ACTIVE">{{ state("ACTIVE") }}</option>
              <option value="RETIRED">{{ state("RETIRED") }}</option></select
            ><select v-model="category" :aria-label="label('category')">
              <option value="">{{ tr("全部类别", "All categories") }}</option>
              <option
                v-for="d in options.dictionaries.filter(
                  (d) => d.type === 'category',
                )"
                :key="d.id"
                :value="d.code"
              >
                {{ language === "zh" ? d.name : d.nameEn }}
              </option></select
            ><select v-model="sort" :aria-label="tr('排序', 'Sort')">
              <option value="newest">{{ tr("最新创建", "Newest") }}</option>
              <option value="title">{{ tr("按名称", "By title") }}</option>
              <option value="number">
                {{ tr("按编号", "By number") }}
              </option></select
            ><button>{{ tr("查询", "Search") }}</button>
          </form>
          <section class="panel table-scroll">
            <table>
              <thead>
                <tr>
                  <th>{{ tr("受控文件", "Document") }}</th>
                  <th>{{ label("departmentId") }}</th>
                  <th>{{ tr("当前版本", "Current revision") }}</th>
                  <th>{{ label("reviewDate") }}</th>
                  <th>{{ tr("状态", "Status") }}</th>
                  <th>{{ tr("操作", "Action") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="d in rows" :key="d.id">
                  <td>
                    <strong>{{ d.title }}</strong
                    ><small>{{ d.number }}</small>
                  </td>
                  <td>{{ department(d.departmentId) }}</td>
                  <td>
                    {{ d.currentRevisionNo ? "R" + d.currentRevisionNo : "—" }}
                  </td>
                  <td :class="{ overdue: d.reviewOverdue }">
                    {{ d.reviewDate || "—" }}
                  </td>
                  <td>
                    <span class="badge">{{ state(d.status) }}</span>
                  </td>
                  <td>
                    <button @click="run(() => openDocument(d.id))">
                      {{ tr("查看", "Open") }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-if="!rows.length" class="empty">
              {{ tr("暂无受控文件", "No controlled documents") }}
            </p>
          </section>
          <div class="pagination">
            <span
              >{{ tr("共", "Total") }} {{ total }} {{ tr("条", "records") }} ·
              {{ page + 1 }}</span
            ><button
              :disabled="page === 0 || busy"
              @click="
                page--;
                run(loadView);
              "
            >
              {{ tr("上一页", "Previous") }}</button
            ><button
              :disabled="(page + 1) * 10 >= total || busy"
              @click="
                page++;
                run(loadView);
              "
            >
              {{ tr("下一页", "Next") }}
            </button>
          </div></template
        >
        <template v-else-if="view === 'dashboard'"
          ><div class="page-heading">
            <div>
              <p class="eyebrow">DOCFLOW / {{ tr("文控概览", "OVERVIEW") }}</p>
              <h1>{{ tr("文控看板", "Document dashboard") }}</h1>
              <p>
                {{
                  tr(
                    "当前账号数据范围内的文件与签收",
                    "Documents and assignments within your data scope",
                  )
                }}
              </p>
            </div>
            <button @click="run(loadView)">{{ tr("刷新", "Refresh") }}</button>
          </div>
          <div class="metrics">
            <article>
              <span>{{ tr("受控文件", "Documents") }}</span
              ><strong>{{ stats.documents }}</strong>
            </article>
            <article>
              <span>{{ tr("待签收", "Pending") }}</span
              ><strong>{{ stats.pending }}</strong>
            </article>
            <article>
              <span>{{ tr("已签收", "Acknowledged") }}</span
              ><strong>{{ stats.acknowledged }}</strong>
            </article>
            <article>
              <span>{{ tr("签收逾期", "Overdue reading") }}</span
              ><strong>{{ stats.overdue }}</strong>
            </article>
          </div>
          <section class="panel">
            <h2>{{ tr("文件状态", "Document status") }}</h2>
            <div v-for="(n, s) in stats.status" :key="s" class="status-line">
              <span>{{ state(s) }}</span>
              <div>
                <i
                  :style="{
                    width:
                      (stats.documents ? (n / stats.documents) * 100 : 0) + '%',
                  }"
                ></i>
              </div>
              <strong>{{ n }}</strong>
            </div>
            <p class="review-count">
              {{ tr("到期未复审", "Review overdue") }}
              <strong>{{ stats.reviewOverdue }}</strong>
            </p>
          </section></template
        >
        <template v-else-if="view === 'audit'"
          ><div class="page-heading">
            <div>
              <p class="eyebrow">DOCFLOW / AUDIT</p>
              <h1>{{ tr("操作审计", "Audit log") }}</h1>
            </div>
            <button @click="run(loadView)">{{ tr("刷新", "Refresh") }}</button>
          </div>
          <section class="panel table-scroll">
            <table>
              <thead>
                <tr>
                  <th>{{ tr("时间", "Time") }}</th>
                  <th>{{ tr("账号", "Account") }}</th>
                  <th>{{ tr("操作", "Action") }}</th>
                  <th>{{ tr("记录", "Record") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="a in rows" :key="a.id">
                  <td>{{ shortDate(a.createdAt) }}</td>
                  <td>{{ a.actor }}</td>
                  <td>{{ eventName(a.action, language) }}</td>
                  <td>{{ a.objectId || "—" }}</td>
                </tr>
              </tbody>
            </table>
            <p v-if="!rows.length" class="empty">
              {{ tr("暂无记录", "No records") }}
            </p>
          </section></template
        >
        <template v-else-if="adminFields[view]"
          ><div class="page-heading">
            <div>
              <p class="eyebrow">
                DOCFLOW / {{ tr("系统管理", "ADMINISTRATION") }}
              </p>
              <h1>
                {{
                  menuName(
                    menus.find((m) => m.code === view) || {
                      name: view,
                      nameEn: view,
                    },
                  )
                }}
              </h1>
            </div>
            <button
              v-if="
                ['users', 'roles', 'departments', 'dictionaries'].includes(view)
              "
              class="primary"
              @click="openModal('admin')"
            >
              + {{ tr("新增", "Add") }}
            </button>
          </div>
          <section class="panel table-scroll">
            <table>
              <thead>
                <tr>
                  <th
                    v-if="['permissions', 'menus', 'settings'].includes(view)"
                  >
                    {{ label("code") }}
                  </th>
                  <th
                    v-for="k in adminFields[view].filter(
                      (k) => k !== 'password',
                    )"
                    :key="k"
                  >
                    {{ label(k) }}
                  </th>
                  <th>{{ tr("操作", "Action") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in rows" :key="r.id">
                  <td
                    v-if="['permissions', 'menus', 'settings'].includes(view)"
                  >
                    {{ r.code }}
                  </td>
                  <td
                    v-for="k in adminFields[view].filter(
                      (k) => k !== 'password',
                    )"
                    :key="k"
                  >
                    {{ adminValue(r, k) }}
                  </td>
                  <td class="actions">
                    <button @click="openModal('admin', r)">
                      {{ tr("编辑", "Edit") }}</button
                    ><button
                      v-if="
                        [
                          'users',
                          'roles',
                          'departments',
                          'dictionaries',
                        ].includes(view)
                      "
                      @click="openModal('deleteAdmin', r)"
                    >
                      {{ tr("删除", "Delete") }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </section></template
        >
        <section v-else class="panel about">
          <img src="/brand/logo.jpg" alt="知华科技" />
          <h1>DocFlow</h1>
          <p>
            {{
              tr(
                "受控文档与制度签收 · 0.1.0",
                "Controlled documents and acknowledgements · 0.1.0",
              )
            }}
          </p>
          <h2>{{ tr("公开源码学习版", "Source learning edition") }}</h2>
          <p>
            {{
              tr(
                "限个人学习、研究和非商业测试。商业使用、再分发与授权范围详见 LICENSE。",
                "For personal study, research and noncommercial testing. See LICENSE for commercial use and redistribution terms.",
              )
            }}
          </p>
          <p>上海如静知华信息科技有限公司</p>
          <a href="https://www.zhuatech.cn/" target="_blank" rel="noreferrer"
            >www.zhuatech.cn</a
          >
          <p>
            {{
              tr(
                "商业授权、定制开发、部署与系统集成咨询",
                "Commercial licensing, customization, deployment and integration",
              )
            }}<br />微信 zhuatech / zhuatech2
          </p>
        </section>
      </main>
    </div>
  </div>
  <div v-if="modal" class="modal-overlay">
    <section
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-heading"
      class="modal"
    >
      <h2 id="modal-heading">{{ modal.heading }}</h2>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <form @submit.prevent="saveModal">
        <div class="form-grid">
          <template v-for="k in modal.fields" :key="k"
            ><fieldset
              v-if="['recipients', 'permissions'].includes(k)"
              class="wide"
            >
              <legend>{{ label(k) }}</legend>
              <label v-for="o in choices(k)" :key="o.id" class="checkbox-label"
                ><input v-model="form[k]" type="checkbox" :value="o.id" />{{
                  o.name
                }}</label
              >
              <p v-if="!choices(k).length">
                {{ tr("没有可选人员或权限", "No eligible options") }}
              </p>
            </fieldset>
            <div v-else :class="{ wide: longText(k) }">
              <label :for="'field-' + k">{{ label(k) }}</label
              ><input
                v-if="k === 'enabled'"
                :id="'field-' + k"
                v-model="form[k]"
                type="checkbox"
              /><textarea
                v-else-if="longText(k)"
                :id="'field-' + k"
                v-model="form[k]"
                :rows="k === 'content' ? 12 : 4"
                :maxlength="k === 'content' ? 12000 : 3000"
                required
              ></textarea
              ><select
                v-else-if="choices(k)"
                :id="'field-' + k"
                v-model="form[k]"
                required
              >
                <option :value="undefined" disabled>
                  {{ tr("请选择", "Choose") }}
                </option>
                <option v-for="o in choices(k)" :key="o.id" :value="o.id">
                  {{ o.name }}
                </option></select
              ><input
                v-else
                :id="'field-' + k"
                v-model="form[k]"
                :type="inputType(k)"
                :required="k !== 'password' || !modal.row"
                :maxlength="inputType(k) === 'password' ? 72 : 200"
                :autocomplete="
                  inputType(k) === 'password' ? 'new-password' : 'off'
                "
              /></div
          ></template>
        </div>
        <p v-if="!modal.fields.length">
          {{ tr("确认执行此操作？", "Confirm this operation?") }}
        </p>
        <p
          v-if="modal.fields.some((k) => k.toLowerCase().includes('password'))"
          class="hint"
        >
          {{
            tr(
              "新密码至少 12 位、含大写小写及数字，UTF-8 不超过 72 字节。",
              "New password: at least 12 characters with upper/lower case and digits; UTF-8 up to 72 bytes.",
            )
          }}
        </p>
        <div class="modal-actions">
          <button type="button" :disabled="busy" @click="modal = null">
            {{ tr("取消", "Cancel") }}</button
          ><button class="primary" :disabled="busy">
            {{ tr("确认保存", "Confirm") }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>
