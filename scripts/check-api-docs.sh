#!/usr/bin/env bash
# 校验前端接口契约三件套（frontend-api.md、frontend-api-changelog.md、frontend-api-status.json）是否自洽。
# 供本地和 CI 使用；只要状态指针指向的 revision 与变更记录不一致就失败。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

python3 - "$ROOT" <<'PY'
import json
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
status_path = root / "docs" / "frontend-api-status.json"
changelog_path = root / "docs" / "frontend-api-changelog.md"


def fail(message):
    print(f"接口文档检查失败：{message}", file=sys.stderr)
    sys.exit(1)


for path in (status_path, changelog_path):
    if not path.is_file():
        fail(f"缺少文件 {path.relative_to(root)}")

status = json.loads(status_path.read_text(encoding="utf-8"))
changelog = changelog_path.read_text(encoding="utf-8")

required = [
    "schemaVersion", "contractVersion", "revision", "updatedAt", "latestChangeId",
    "latestChangeType", "breaking", "requiresFrontendAction", "changedAreas",
    "contractFile", "changelogFile",
]
missing = [key for key in required if key not in status]
if missing:
    fail("状态文件缺少字段：" + "、".join(missing))

for key in ("contractFile", "changelogFile"):
    if not (root / status[key]).is_file():
        fail(f"{key} 指向的文件不存在：{status[key]}")

if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", str(status["updatedAt"])):
    fail(f"updatedAt 不是 YYYY-MM-DD：{status['updatedAt']}")

entries = re.findall(r"^## Revision (\d+) - (\S+)\s*$", changelog, re.M)
if not entries:
    fail("变更记录中没有找到 “## Revision N - ID” 形式的条目")

revisions = [int(number) for number, _ in entries]
if len(set(revisions)) != len(revisions):
    fail("变更记录中存在重复的 revision 编号")

latest = max(revisions)
if latest != status["revision"]:
    fail(f"状态文件 revision={status['revision']}，变更记录最大 revision={latest}")

latest_id = {int(number): identifier for number, identifier in entries}[latest]
if latest_id != status["latestChangeId"]:
    fail(f"latestChangeId 不一致：状态文件 {status['latestChangeId']}，变更记录 {latest_id}")

block = re.search(rf"^## Revision {latest} - \S+(.*?)(?=^## Revision |\Z)", changelog, re.M | re.S).group(1)
if "前端动作" not in block:
    fail(f"revision {latest} 的变更记录缺少“前端动作”段落")

# 契约文档头部的版本信息必须和状态文件一致，避免只更新状态文件而漏掉契约正文。
contract = (root / status["contractFile"]).read_text(encoding="utf-8")
header_revision = re.search(r"当前同步 revision：`(\d+)`", contract)
if not header_revision:
    fail(f"{status['contractFile']} 头部缺少“当前同步 revision”行")
if int(header_revision.group(1)) != status["revision"]:
    fail(f"{status['contractFile']} 头部 revision={header_revision.group(1)}，状态文件 revision={status['revision']}")

header_version = re.search(r"当前契约版本：`([^`]+)`", contract)
if not header_version:
    fail(f"{status['contractFile']} 头部缺少“当前契约版本”行")
if header_version.group(1) != status["contractVersion"]:
    fail(f"{status['contractFile']} 头部 contractVersion={header_version.group(1)}，状态文件 {status['contractVersion']}")

print(f"接口文档一致：revision {latest} / {latest_id} / contract {status['contractVersion']}")
PY