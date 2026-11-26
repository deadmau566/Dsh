#!/usr/bin/env python3
"""让 LLM 根据 CI 失败日志生成补丁（只改 app/ 下的文件）。

用法: SILICONFLOW_API_KEY=... python3 scripts/auto_fix.py /tmp/logs.zip
成功退出码 0 且工作区有改动；无法修复时退出码 2（不视为 workflow 失败）。
"""
import json, os, re, subprocess, sys, urllib.request, zipfile, pathlib

KEY = os.environ.get("SILICONFLOW_API_KEY")
if not KEY:
    print("no key, skip"); sys.exit(2)
API = "https://api.siliconflow.cn/v1/chat/completions"
MODEL = os.environ.get("AUTOFIX_MODEL", "deepseek-ai/DeepSeek-V4-Flash")


def error_span_lines(lines, keep=5, cap=400):
    """所有 error/failed 行 + 各自上下文，去重后按出现顺序，最多 cap 行。"""
    picked = {}
    for i, l in enumerate(lines):
        if "error" in l.lower() or "failed" in l.lower():
            for j in range(max(0, i - keep), min(len(lines), i + keep + 1)):
                picked.setdefault(j, lines[j])
    keys = sorted(picked)[-cap:]
    return "\n".join(picked[k] for k in keys)


def failed_log(zpath):
    if not zpath.endswith(".zip"):
        txt = pathlib.Path(zpath).read_text(encoding="utf-8", errors="replace")
        return error_span_lines(txt.splitlines())
    try:
        z = zipfile.ZipFile(zpath)
    except zipfile.BadZipFile:
        import os as _os
        try: size = _os.path.getsize(zpath)
        except OSError: size = -1
        head = ""
        try:
            with open(zpath, "rb") as f: head = f.read(200).decode("utf-8", "replace")
        except OSError: pass
        print(f"BAD_LOG_ARCHIVE: {zpath} 不是 zip（{size} 字节），内容开头：{head!r}", file=sys.stderr)
        sys.exit(2)
    best = ""
    for n in z.namelist():
        if not n.endswith(".txt"):
            continue
        t = z.read(n).decode(errors="replace")
        if ("Build APK" in n) or ("error" in t.lower() and "flutter" in t.lower()):
            if len(t) > len(best):
                best = t
    return error_span_lines(best.splitlines())


DART_ERR = re.compile(r"(Error:|error: |Compilation failed|Target kernel_snapshot|Target dart2js|lib/[^ ]+\.dart:\d+)", re.I)


def apply_patch(patch_text):
    """应用 LLM 给的 unified diff（大文件用这个模式，避免整文件装不下）。"""
    for line in patch_text.splitlines():
        if line.startswith("+++ b/"):
            pth = line[6:].split("\t")[0].strip()
            if not pth.startswith("app/") or ".." in pth:
                print("reject patch path", pth)
                return False
    p = pathlib.Path("/tmp/autofix.patch")
    p.write_text(patch_text if patch_text.endswith("\n") else patch_text + "\n", encoding="utf-8")
    r = subprocess.run(["git", "apply", "--3way", "--recount", "--whitespace=fix", str(p)],
                       capture_output=True, text=True)
    if r.returncode != 0:
        print("patch failed:", (r.stderr or r.stdout).strip()[:300])
        subprocess.run(["git", "checkout", "--", "."], capture_output=True, text=True)
        subprocess.run(["git", "clean", "-fd"], capture_output=True, text=True)
        return False
    p.unlink(missing_ok=True)
    return True


DART_FILE = re.compile(r"package:dsh_app/([\w/]+\.dart)")


def upstream_refs(log):
    """从错误日志里认出我们保留的旧文件，附上上游同路径版本，供模型把新 API 合并进来。"""
    out, seen = [], set()
    for m in DART_FILE.finditer(log):
        rel = "app/lib/" + m.group(1)
        if rel in seen:
            continue
        seen.add(rel)
        try:
            r = subprocess.run(["git", "show", f"upstream/main:{rel}"],
                               capture_output=True, text=True, timeout=60)
        except Exception:
            continue
        if r.returncode != 0 or not r.stdout.strip():
            continue
        body = r.stdout
        if len(body) > 24000:
            body = body[:24000] + "\n/* ... 已截断 ... */\n"
        out.append(f"### 上游（ccch1mneyyy/dsh-go）同路径文件 {rel}\n```dart\n{body}\n```")
    return "\n\n".join(out)


def local_refs(log):
    """错误涉及的、本 fork 里的当前文件（diff 必须基于它，不能只照抄上游）。"""
    out, seen = [], set()
    for m in DART_FILE.finditer(log):
        rel = "app/lib/" + m.group(1)
        if rel in seen:
            continue
        seen.add(rel)
        f = pathlib.Path(rel)
        if not f.exists():
            continue
        body = f.read_text(encoding="utf-8", errors="replace")
        if len(body) > 24000:
            body = body[:24000] + "\n/* ... 已截断 ... */\n"
        out.append(f"### 本 fork 当前文件 {rel}（你的 diff 必须基于这个文件的实际内容）\n```dart\n{body}\n```")
    return "\n\n".join(out)


def collect_context(log=""):
    ctx = []
    for p in ("app/pubspec.yaml",):
        f = pathlib.Path(p)
        if f.exists():
            ctx.append(f"### {p}\n```\n{f.read_text(encoding='utf-8', errors='replace')[:4000]}\n```")
    local = local_refs(log)
    if local:
        ctx.append(local)
    up = upstream_refs(log)
    if up:
        ctx.append(up)
    return "\n\n".join(ctx)


def main():
    log = failed_log(sys.argv[1])
    if not log.strip():
        print("no log"); sys.exit(2)
    if not DART_ERR.search(log):
        print("log does not look like a Dart/Kotlin compile error (workflow/dependency failure?) - skip")
        sys.exit(2)
    prompt = f"""你在修复一个 Flutter Android 项目的 CI 构建失败。
下面是失败日志（尾部），以及项目文件片段。请判断根因，给出最小修复。

规则：
- 只能修改 app/ 目录下的文件（Dart、Kotlin、yaml、gradle 都可以）。
- 输出严格 JSON，不要 markdown 代码块：
{{"reason":"一句话根因","confidence":"high|medium|low","changes":[{{"path":"app/...","content":"完整的新文件内容"}}]}}
- 首选 patch（unified diff）：只写改动的那几行，不要整份文件。格式：
  {{"path":"app/...","patch":"--- a/<path>\n+++ b/<path>\n@@ -行,列 +行,列 @@\n 上下文\n-旧行\n+新行"}}
  行号和上下文必须和文件实际内容对得上。
- 只有文件小于 8000 字符时，才允许用 content 给整份文件。
- 一次只改 1 个文件、优先改最关键的；最多改 3 个文件；宁可 confidence 低也不要瞎改。
- patch 总计不超过 40 行（上下文行也算）；如果改动更大，只写最关键的那一处，不要整段重写。
- 如果错误显示我们的文件缺少上游新增的 API/字段/常量（下面附了上游同路径文件），把上游新增的部分合并进我们的文件；
  但必须保留本 fork 的功能：setShellMode / PhoneShellStatus.prepareSkillReplay / discardSkillReplay / ShellGuard / 插件翻译 / updateRepo=ghjicb/dsh-Go。
  不要整份抄上游、不要把 OURS 名单里的文件回退成上游原样。

失败日志：
{log[-12000:]}

{collect_context(log)}
"""
    body = json.dumps({"model": MODEL, "temperature": 0.1, "max_tokens": 16000,
                       "messages": [{"role": "user", "content": prompt}]}).encode()
    req = urllib.request.Request(API, data=body, headers={
        "Authorization": "Bearer " + KEY, "Content-Type": "application/json"})
    try:
        resp = json.load(urllib.request.urlopen(req, timeout=180))
    except Exception as e:
        print("api error:", e); sys.exit(2)
    text = resp["choices"][0]["message"]["content"].strip()
    if text.startswith("```"):
        text = text.split("\n", 1)[1].rsplit("```", 1)[0]
    try:
        data = json.loads(text)
    except Exception:
        print("bad json", text[:400]); sys.exit(2)
    print("reason:", data.get("reason"), "| confidence:", data.get("confidence"))
    changes = data.get("changes") or []
    if not changes or data.get("confidence") == "low":
        print("no confident fix"); sys.exit(2)
    n = 0
    for ch in changes[:3]:
        p = pathlib.Path(ch["path"])
        rp = p.as_posix()
        if not rp.startswith("app/") or ".." in rp:
            print("reject path", rp); continue
        patch = (ch.get("patch") or "").strip()
        if patch:
            if apply_patch(patch):
                print("patched (diff)", rp); n += 1
            continue
        content = ch.get("content") or ""
        if len(content) > 300000 or not content.strip():
            continue
        old = p.read_text(encoding="utf-8", errors="replace") if p.exists() else ""
        if old == content:
            continue
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content, encoding="utf-8")
        print("patched", rp)
        n += 1
    sys.exit(0 if n else 2)


if __name__ == "__main__":
    main()
