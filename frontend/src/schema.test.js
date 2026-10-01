// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { test } from "node:test";
import assert from "node:assert/strict";
import { readingTask } from "./schema.js";
const me = { id: 4, permissions: ["doc.ack"] },
  rev = { id: 9, status: "PUBLISHED" },
  task = { id: 1, revisionId: 9, accountId: 4, status: "PENDING" },
  detail = {
    document: { status: "ACTIVE", currentRevisionId: 9 },
    assignments: [task],
  };
test("only a personal pending assignment to current version can be acknowledged", () => {
  assert.equal(readingTask(detail, rev, me), task);
  assert.equal(readingTask(detail, rev, { ...me, id: 5 }), null);
  assert.equal(readingTask(detail, rev, { ...me, permissions: [] }), null);
});
test("a replaced, archived or withdrawn version never offers acknowledgement", () => {
  assert.equal(
    readingTask(
      { ...detail, document: { ...detail.document, currentRevisionId: 10 } },
      rev,
      me,
    ),
    null,
  );
  assert.equal(
    readingTask(
      { ...detail, document: { ...detail.document, status: "RETIRED" } },
      rev,
      me,
    ),
    null,
  );
  assert.equal(readingTask(detail, { ...rev, status: "SUPERSEDED" }, me), null);
});
test("an acknowledged task cannot be offered again", () => {
  assert.equal(
    readingTask(
      { ...detail, assignments: [{ ...task, status: "ACKNOWLEDGED" }] },
      rev,
      me,
    ),
    null,
  );
});
