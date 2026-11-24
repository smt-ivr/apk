import { renderPage } from "./page.js";
import { renderMdmPage } from "./mdmPage.js";
import { mdmRoute, createMdm, mdmPolicy, pinHashSlow } from "./mdm.js";

const BASE = "/apk";
const CHUNK = 60_000; // characters of base64 per D1 row (D1 statements are limited to ~100KB)
const MAX_SITES = 30;
const MAX_EXTRA = 30;
const MAX_ICON_B64 = 400_000;
const PER_IP_PER_HOUR = 6;
const PER_DAY = 60;
const KEEP_DAYS = 7; // APK files
const KEEP_SETTINGS_DAYS = 400; // build settings, so an app can be updated later
const BUILD_TIMEOUT_MS = 20 * 60 * 1000;

const SCHEMA = [
  `CREATE TABLE IF NOT EXISTS apk_jobs (
    id TEXT PRIMARY KEY,
    status TEXT NOT NULL,
    error TEXT DEFAULT '',
    app_name TEXT NOT NULL,
    app_id TEXT NOT NULL,
    version_code INTEGER NOT NULL DEFAULT 1,
    config TEXT NOT NULL,
    icon TEXT DEFAULT '',
    ip TEXT DEFAULT '',
    apk_size INTEGER DEFAULT 0,
    chunks INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
  )`,
  `CREATE INDEX IF NOT EXISTS apk_jobs_created ON apk_jobs(created_at)`,
  `CREATE TABLE IF NOT EXISTS apk_chunks (
    job_id TEXT NOT NULL,
    idx INTEGER NOT NULL,
    data TEXT NOT NULL,
    PRIMARY KEY (job_id, idx)
  )`,
  `CREATE TABLE IF NOT EXISTS apk_meta (k TEXT PRIMARY KEY, v TEXT NOT NULL)`,
];

let schemaReady = false;
async function ensureSchema(env) {
  if (schemaReady) return;
  await env.DB.batch(SCHEMA.map((s) => env.DB.prepare(s)));
  // per-app "block download" flag
  const cols = new Set(((await env.DB.prepare("PRAGMA table_info(apk_jobs)").all()).results || []).map((c) => c.name));
  if (!cols.has("dl_blocked")) await env.DB.prepare("ALTER TABLE apk_jobs ADD COLUMN dl_blocked INTEGER DEFAULT 0").run().catch(() => {});
  schemaReady = true;
}

// ---------------------------------------------------------------- build-area gate (code, never shown)

const gateOk = new Set();
const gateFails = new Map();

/** Checks the build code from the x-build-code header. Returns null when allowed, or an error Response. */
async function requireGate(request, env) {
  await ensureSchema(env);
  const ip = request.headers.get("cf-connecting-ip") || "";
  const until = gateFails.get("lock:" + ip) || 0;
  if (until > Date.now()) return fail("יותר מדי ניסיונות. נסו שוב בעוד דקות אחדות.", 401);
  const row = await env.DB.prepare("SELECT v FROM apk_meta WHERE k = 'build_gate'").first();
  // Not claimed yet: whoever opens the build area first chooses the code.
  if (!row) return json({ ok: false, error: "האזור עדיין לא מוגן. בחרו קוד כניסה.", setup: true }, 401);
  const code = request.headers.get("x-build-code") || "";
  if (!code) return fail("נדרש קוד כניסה", 401);
  let g;
  try { g = JSON.parse(row.v); } catch { return fail("שגיאת שרת", 500); }
  const memo = g.hash + "|" + code;
  if (gateOk.has(memo)) return null;
  if ((await sha256Hex(g.salt + code)) === g.hash) {
    if (gateOk.size > 500) gateOk.clear();
    gateOk.add(memo);
    gateFails.delete(ip);
    return null;
  }
  const n = (gateFails.get(ip) || 0) + 1;
  gateFails.set(ip, n);
  if (n >= 8) { gateFails.set("lock:" + ip, Date.now() + 15 * 60_000); gateFails.set(ip, 0); }
  return fail("קוד שגוי", 401);
}

/** First-time setup: claim the build area with a code, but only if none is set yet. */
async function setupGate(request, env) {
  await ensureSchema(env);
  const row = await env.DB.prepare("SELECT v FROM apk_meta WHERE k = 'build_gate'").first();
  if (row) return fail("האזור כבר מוגן בקוד. היכנסו עם הקוד הקיים.", 403);
  const b = await request.json().catch(() => ({}));
  const code = String(b.code || "").trim();
  if (!/^\d{4,12}$/.test(code)) return fail("הקוד צריך להיות 4 עד 12 ספרות");
  const salt = randomHex(16);
  await env.DB.prepare("INSERT OR IGNORE INTO apk_meta (k, v) VALUES ('build_gate', ?)")
    .bind(JSON.stringify({ salt, hash: await sha256Hex(salt + code) })).run();
  return json({ ok: true });
}

/** Change the build code (only from inside the gated area). */
async function setGate(request, env) {
  const denied = await requireGate(request, env);
  if (denied) return denied;
  const b = await request.json().catch(() => ({}));
  const code = String(b.newCode || "").trim();
  if (!/^\d{4,12}$/.test(code)) return fail("קוד חדש צריך להיות 4 עד 12 ספרות");
  const salt = randomHex(16);
  await env.DB.prepare("INSERT INTO apk_meta (k, v) VALUES ('build_gate', ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v")
    .bind(JSON.stringify({ salt, hash: await sha256Hex(salt + code) })).run();
  gateOk.clear();
  return json({ ok: true });
}

/** Recent builds, for the gated "my builds" list (download link + block toggle). */
async function myBuilds(request, env, origin) {
  const denied = await requireGate(request, env);
  if (denied) return denied;
  await ensureSchema(env);
  const { results } = await env.DB.prepare(
    `SELECT id, app_name, config, status, apk_size, chunks, dl_blocked, created_at FROM apk_jobs ORDER BY created_at DESC LIMIT 100`
  ).all();
  const now = Date.now();
  const builds = (results || []).map((j) => ({
    id: j.id, name: j.app_name, type: jobType(j), version: versionNameOf(j), status: j.status,
    size: j.apk_size, blocked: !!j.dl_blocked,
    download: j.status === "done" && j.chunks && !j.dl_blocked && now - j.created_at < KEEP_DAYS * 86400_000 ? `${origin}${BASE}/download/${j.id}` : null,
    expired: !(j.chunks && now - j.created_at < KEEP_DAYS * 86400_000),
    createdAt: j.created_at,
  }));
  return json({ ok: true, builds });
}

async function blockDownload(request, env, id) {
  const denied = await requireGate(request, env);
  if (denied) return denied;
  const b = await request.json().catch(() => ({}));
  await env.DB.prepare("UPDATE apk_jobs SET dl_blocked = ? WHERE id = ?").bind(b.blocked ? 1 : 0, id).run();
  return json({ ok: true });
}

// ---------------------------------------------------------------- helpers

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}
const fail = (message, status = 400) => json({ ok: false, error: message }, status);

function safeEqual(a, b) {
  a = String(a ?? "");
  b = String(b ?? "");
  let diff = a.length ^ b.length;
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    diff |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  }
  return diff === 0;
}

function isBuilder(request, env) {
  const auth = request.headers.get("authorization") || "";
  return !!env.BUILD_SECRET && safeEqual(auth, "Bearer " + env.BUILD_SECRET);
}

async function sha256Hex(text) {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function randomHex(bytes) {
  const a = new Uint8Array(bytes);
  crypto.getRandomValues(a);
  return [...a].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** "example.com", "www.example.com/x" or a full URL -> normalized https URL, or null. */
function normalizeUrl(input) {
  let s = String(input || "").trim();
  if (!s) return null;
  if (!/^https?:\/\//i.test(s)) s = "https://" + s;
  try {
    const u = new URL(s);
    if (!/^https?:$/.test(u.protocol)) return null;
    if (!/^([a-z0-9-]+\.)+[a-z0-9-]{2,}$/i.test(u.hostname)) return null;
    if (u.username || u.password) return null;
    return u.toString();
  } catch {
    return null;
  }
}

function cleanText(s, max) {
  return String(s ?? "")
    .replace(/[\u0000-\u001f\u007f]/g, " ")
    .trim()
    .slice(0, max);
}

function base64ToBytes(b64) {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function bytesToBase64(bytes) {
  let bin = "";
  for (let i = 0; i < bytes.length; i += 0x8000) {
    bin += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
  }
  return btoa(bin);
}

// ---------------------------------------------------------------- API: create a build

async function createBuild(request, env) {
  const denied = await requireGate(request, env);
  if (denied) return denied;
  if (!env.GH_TOKEN || !env.BUILD_SECRET) {
    return fail("השרת עדיין לא הוגדר (חסרים GH_TOKEN או BUILD_SECRET).", 500);
  }
  let body;
  try {
    body = await request.json();
  } catch {
    return fail("בקשה לא תקינה");
  }

  const appName = cleanText(body.appName, 40);
  if (!appName) return fail("נא להזין שם לאפליקציה");
  // "site": an app that shows websites. "lock": locks other apps. "mdm": remote device management (kiosk etc.).
  const type = body.type === "lock" ? "lock" : body.type === "mdm" ? "mdm" : "site";

  const sites = [];
  for (const s of Array.isArray(body.sites) ? body.sites.slice(0, MAX_SITES) : []) {
    const raw = cleanText(s?.url, 500);
    if (!raw) continue;
    const url = normalizeUrl(raw);
    if (!url) return fail(`הכתובת "${raw}" לא תקינה`);
    const title = cleanText(s?.title, 40) || new URL(url).hostname;
    sites.push({ title, url, restrictPath: !!s?.restrictPath });
  }
  const restoreTool = type === "site" && !!body.restoreTool;
  if (type === "site" && !sites.length && !restoreTool) return fail("נא להזין לפחות כתובת אתר אחת");

  const extraDomains = [];
  for (const d of Array.isArray(body.extraDomains) ? body.extraDomains.slice(0, MAX_EXTRA) : []) {
    const raw = cleanText(d, 300);
    if (!raw) continue;
    if (!normalizeUrl(raw)) return fail(`הכתובת הנוספת "${raw}" לא תקינה`);
    extraDomains.push(raw.replace(/^https?:\/\//i, ""));
  }

  // Updating an app built before: keep its package (so it installs as an update) and, if no new PIN, its PIN.
  await ensureSchema(env);
  let prevJob = null;
  if (body.updateOf) {
    const uid = String(body.updateOf);
    if (!/^[a-f0-9]{24}$/.test(uid)) return fail("קישור העדכון לא תקין");
    prevJob = await env.DB.prepare(`SELECT app_id, config FROM apk_jobs WHERE id = ?`).bind(uid).first();
    if (!prevJob) return fail("האפליקציה הקודמת לא נמצאה");
    if (jobType(prevJob) !== type) return fail("אפשר לעדכן רק אפליקציה מאותו סוג");
  }
  const prevConfig = prevJob ? JSON.parse(prevJob.config) : {};

  const pin = String(body.pin ?? "").trim();
  if ((type === "lock" || type === "mdm") && !pin && !prevConfig.pinHash) return fail("נא לבחור קוד");
  const sitePass = String(body.sitePassword ?? "");
  if (type === "mdm" && !prevConfig.appKey && sitePass && sitePass.length < 4) return fail("סיסמת אתר הניהול: 4 תווים לפחות");
  if (pin && !/^\d{4,8}$/.test(pin)) return fail("הקוד צריך להיות 4 עד 8 ספרות");
  let pinSalt = pin ? randomHex(16) : "";
  let pinHash = pin ? await sha256Hex(pinSalt + pin) : "";
  if (!pin && prevJob && body.keepPin !== false) {
    pinSalt = prevConfig.pinSalt || "";
    pinHash = prevConfig.pinHash || "";
  }

  const orientation = ["auto", "portrait", "landscape"].includes(body.orientation) ? body.orientation : "auto";
  const toolbar = body.toolbar !== false;
  const desktop = body.desktop === true;

  let icon = "";
  if (body.icon) {
    const m = /^data:image\/png;base64,([A-Za-z0-9+/=]+)$/.exec(String(body.icon));
    if (!m) return fail("הסמל צריך להיות תמונת PNG");
    if (m[1].length > MAX_ICON_B64) return fail("הסמל גדול מדי");
    icon = m[1];
  }

  // Same name (+ first site) -> same package, so a new build installs as an update.
  let appId, config;
  if (type === "lock") {
    appId = "uk.smti.lock.a" + (await sha256Hex("lock|" + appName)).slice(0, 12);
    config = { type, pinSalt, pinHash };
  } else if (type === "mdm") {
    appId = "uk.smti.mdm.a" + (await sha256Hex("mdm|" + appName)).slice(0, 12);
    const server = new URL(request.url).origin.replace(/^http:/, "https:") + BASE;
    if (pin) pinHash = await pinHashSlow(pinSalt, pin);
    if (prevConfig.appKey) {
      // an update keeps the same control page, devices and policy
      config = { type, appKey: prevConfig.appKey, manageKey: prevConfig.manageKey, server, pinSalt, pinHash, pinAlgo: pin ? "p1" : prevConfig.pinAlgo };
    } else {
      const kioskApps = (Array.isArray(body.kioskApps) ? body.kioskApps : []).map((x) => cleanText(x, 150)).filter((x) => /^[a-zA-Z0-9_.]+$/.test(x)).slice(0, 30);
      const m = await createMdm(env, appName, {
        kiosk: { enabled: !!body.kiosk && kioskApps.length > 0, apps: kioskApps, title: cleanText(body.kioskTitle, 60) },
        pinSalt, pinHash, pinAlgo: "p1",
      }, sitePass || pin);
      config = { type, appKey: m.id, manageKey: m.key, server, pinSalt, pinHash, pinAlgo: "p1" };
    }
  } else {
    // clean default package name: com.smt.<slug from the app name>, no country/hash.
    const slug = appName.toLowerCase().replace(/[^a-z0-9]+/g, "").slice(0, 24) || "browser";
    appId = "com.smt." + slug;
    config = { sites, extraDomains, orientation, toolbar, desktop, pinSalt, pinHash };
    if (restoreTool) config.restoreTool = true;
  }
  if (prevJob) appId = prevJob.app_id;
  // Manager override (gated area only): pin an exact package name. Used to update an app already installed
  // on a device in place — same signature + same package = Android installs it as an update and keeps all
  // the app's data (bookmarks, cookies, history, settings). Pin one name to always upgrade in place.
  const wantId = cleanText(body.appId, 120).toLowerCase();
  if (wantId) {
    if (!/^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$/.test(wantId)) return fail(`שם החבילה "${body.appId}" לא תקין (למשל uk.smti.app.xxxxx)`);
    appId = wantId;
  }

  const now = Date.now();
  const ip = request.headers.get("cf-connecting-ip") || "";

  // Housekeeping and limits.
  const old = now - KEEP_DAYS * 86400_000;
  const veryOld = now - KEEP_SETTINGS_DAYS * 86400_000;
  // The newest build of each device-management app is kept: managed phones update from it and the QR code uses it.
  const keepLatest = `AND id NOT IN (SELECT j.id FROM apk_jobs j WHERE j.status = 'done' AND j.config LIKE '%"type":"mdm"%'
      AND j.created_at = (SELECT MAX(k.created_at) FROM apk_jobs k WHERE k.app_id = j.app_id AND k.status = 'done'))`;
  await env.DB.batch([
    env.DB.prepare(`DELETE FROM apk_chunks WHERE job_id IN (SELECT id FROM apk_jobs WHERE created_at < ? AND chunks > 0 ${keepLatest})`).bind(old),
    env.DB.prepare(`UPDATE apk_jobs SET chunks = 0, icon = '' WHERE created_at < ? AND chunks > 0 ${keepLatest}`).bind(old),
    env.DB.prepare(`DELETE FROM apk_jobs WHERE created_at < ?`).bind(veryOld),
  ]);
  const counts = await env.DB.prepare(
    `SELECT
       SUM(CASE WHEN ip = ? AND created_at > ? THEN 1 ELSE 0 END) AS mine,
       SUM(CASE WHEN created_at > ? THEN 1 ELSE 0 END) AS day
     FROM apk_jobs`
  ).bind(ip, now - 3600_000, now - 86400_000).first();
  if ((counts?.mine || 0) >= PER_IP_PER_HOUR) return fail("יותר מדי בקשות. נסה שוב בעוד שעה.", 429);
  if ((counts?.day || 0) >= PER_DAY) return fail("הגענו למכסה היומית. נסה שוב מחר.", 429);

  // Version code: minutes since 2025, so it always grows and every new build installs over the old one.
  const versionCode = Math.floor((now - Date.UTC(2025, 0, 1)) / 60000);
  const builds = await env.DB.prepare(`SELECT COUNT(*) AS n FROM apk_jobs WHERE app_id = ? AND status = 'done'`).bind(appId).first();
  config.versionName = "1." + ((builds?.n || 0) + 1);

  const id = randomHex(12);
  await env.DB.prepare(
    `INSERT INTO apk_jobs (id, status, app_name, app_id, version_code, config, icon, ip, created_at, updated_at)
     VALUES (?, 'queued', ?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(id, appName, appId, versionCode, JSON.stringify(config), icon, ip, now, now).run();

  const gh = await fetch(
    `https://api.github.com/repos/${env.GH_REPO}/actions/workflows/${env.GH_WORKFLOW || "build-apk.yml"}/dispatches`,
    {
      method: "POST",
      headers: {
        authorization: "Bearer " + env.GH_TOKEN,
        accept: "application/vnd.github+json",
        "x-github-api-version": "2022-11-28",
        "user-agent": "apk-builder-worker",
        "content-type": "application/json",
      },
      body: JSON.stringify({ ref: env.GH_REF || "main", inputs: { job_id: id } }),
    }
  );
  if (!gh.ok) {
    const detail = (await gh.text()).slice(0, 300);
    console.log("GitHub dispatch failed", gh.status, detail);
    await setStatus(env, id, "error", "הפעלת הבנייה נכשלה (GitHub " + gh.status + ")");
    return fail("הפעלת הבנייה נכשלה. נסה שוב מאוחר יותר.", 502);
  }

  return json({ ok: true, id });
}

function versionNameOf(job) {
  try {
    return JSON.parse(job.config).versionName || "1.0";
  } catch {
    return "1.0";
  }
}

function jobType(job) {
  try {
    const t = JSON.parse(job.config).type;
    return t === "lock" || t === "mdm" ? t : "site";
  } catch {
    return "site";
  }
}

function manageUrl(origin, job) {
  try {
    const c = JSON.parse(job.config);
    return c.type === "mdm" && c.appKey ? `${origin.replace(/^http:/, "https:")}${BASE}/mdm/${c.appKey}#k=${c.manageKey}` : null;
  } catch {
    return null;
  }
}

async function setStatus(env, id, status, error = "") {
  await env.DB.prepare(`UPDATE apk_jobs SET status = ?, error = ?, updated_at = ? WHERE id = ?`)
    .bind(status, error, Date.now(), id).run();
}

// ---------------------------------------------------------------- API: public status

async function jobStatus(env, id, origin) {
  await ensureSchema(env);
  const job = await env.DB.prepare(
    `SELECT id, status, error, app_name, app_id, version_code, config, apk_size, created_at, updated_at FROM apk_jobs WHERE id = ?`
  ).bind(id).first();
  if (!job) return fail("הבקשה לא נמצאה", 404);

  if ((job.status === "queued" || job.status === "building") && Date.now() - job.created_at > BUILD_TIMEOUT_MS) {
    job.status = "error";
    job.error = "הבנייה לקחה יותר מדי זמן. נסה שוב.";
    await setStatus(env, id, job.status, job.error);
  }

  return json({
    ok: true,
    status: job.status,
    error: job.error,
    appName: job.app_name,
    version: versionNameOf(job),
    appId: job.app_id,
    size: job.apk_size,
    elapsed: Math.round((Date.now() - job.created_at) / 1000),
    download: job.status === "done" && Date.now() - job.created_at < KEEP_DAYS * 86400_000 ? `${origin}${BASE}/download/${job.id}` : null,
    type: jobType(job),
    // For the lock app: the one-time computer command that switches on strong mode.
    adb: jobType(job) === "lock" ? `adb shell dpm set-device-owner ${job.app_id}/uk.smti.lock.AdminReceiver`
      : jobType(job) === "mdm" ? `adb shell dpm set-device-owner ${job.app_id}/uk.smti.mdm.AdminReceiver` : null,
    // For device management: the private control page (only whoever built the app has this job id).
    manage: manageUrl(origin, job),
  });
}

async function download(env, id) {
  await ensureSchema(env);
  const job = await env.DB.prepare(`SELECT app_name, status, chunks, dl_blocked FROM apk_jobs WHERE id = ?`).bind(id).first();
  if (job && job.dl_blocked) {
    return new Response("ההורדה של אפליקציה זו הושבתה על ידי המנהל.", {
      status: 403, headers: { "content-type": "text/plain; charset=utf-8" },
    });
  }
  if (!job || job.status !== "done" || !job.chunks) {
    return new Response("הקובץ לא נמצא או שפג תוקפו. אפשר לבנות אותו מחדש באתר (עדכון לאפליקציה קיימת).", {
      status: 404, headers: { "content-type": "text/plain; charset=utf-8" },
    });
  }
  const { results } = await env.DB.prepare(`SELECT data FROM apk_chunks WHERE job_id = ? ORDER BY idx`).bind(id).all();
  const bytes = base64ToBytes(results.map((r) => r.data).join(""));
  const ascii = job.app_name.replace(/[^A-Za-z0-9._-]+/g, "_").replace(/^_+|_+$/g, "") || "app";
  return new Response(bytes, {
    headers: {
      "content-type": "application/vnd.android.package-archive",
      "content-disposition": `attachment; filename="${ascii}.apk"; filename*=UTF-8''${encodeURIComponent(job.app_name)}.apk`,
      "content-length": String(bytes.length),
      "cache-control": "private, max-age=3600",
    },
  });
}

/** Settings of a previous build, to load them into the form and build an update. */
async function jobSettings(env, id) {
  await ensureSchema(env);
  const job = await env.DB.prepare(`SELECT app_name, app_id, config, status FROM apk_jobs WHERE id = ?`).bind(id).first();
  if (!job) return fail("האפליקציה לא נמצאה. ייתכן שהקישור ישן מדי.", 404);
  const c = JSON.parse(job.config);
  return json({
    ok: true,
    type: c.type === "lock" || c.type === "mdm" ? c.type : "site",
    appName: job.app_name,
    appId: job.app_id,
    sites: c.sites || [],
    extraDomains: c.extraDomains || [],
    orientation: c.orientation || "auto",
    toolbar: c.toolbar !== false,
    desktop: c.desktop === true,
    hasPin: !!c.pinHash,
  });
}

// ---------------------------------------------------------------- API: for the GitHub workflow

async function builderConfig(env, id) {
  await ensureSchema(env);
  const job = await env.DB.prepare(`SELECT * FROM apk_jobs WHERE id = ?`).bind(id).first();
  if (!job) return fail("not found", 404);
  if (job.status === "queued") await setStatus(env, id, "building");
  const config = JSON.parse(job.config);
  if (config.type === "mdm") {
    // the phone gets its first policy inside the app (kiosk can start at once); the owner key never goes into the app
    config.policy = (await mdmPolicy(env, config.appKey)) || {};
    delete config.manageKey;
  }
  return json({
    appName: job.app_name,
    appId: job.app_id,
    versionCode: job.version_code,
    versionName: versionNameOf(job),
    type: config.type === "lock" || config.type === "mdm" ? config.type : "site",
    firstHost: config.sites && config.sites.length ? new URL(config.sites[0].url).hostname : "",
    firstUrl: config.sites && config.sites.length ? config.sites[0].url : "",
    icon: job.icon || "",
    config,
  });
}

async function builderStatus(request, env, id) {
  const body = await request.json().catch(() => ({}));
  const status = ["building", "error"].includes(body.status) ? body.status : null;
  if (!status) return fail("bad status");
  await setStatus(env, id, status, cleanText(body.error, 300));
  return json({ ok: true });
}

async function builderUpload(request, env, id) {
  await ensureSchema(env);
  const job = await env.DB.prepare(`SELECT id FROM apk_jobs WHERE id = ?`).bind(id).first();
  if (!job) return fail("not found", 404);
  const bytes = new Uint8Array(await request.arrayBuffer());
  if (bytes.length < 1000 || bytes.length > 20_000_000 || bytes[0] !== 0x50 || bytes[1] !== 0x4b) {
    return fail("not an apk");
  }
  const b64 = bytesToBase64(bytes);
  const stmts = [env.DB.prepare(`DELETE FROM apk_chunks WHERE job_id = ?`).bind(id)];
  let n = 0;
  for (let i = 0; i < b64.length; i += CHUNK, n++) {
    stmts.push(env.DB.prepare(`INSERT INTO apk_chunks (job_id, idx, data) VALUES (?, ?, ?)`).bind(id, n, b64.slice(i, i + CHUNK)));
  }
  stmts.push(
    env.DB.prepare(`UPDATE apk_jobs SET status = 'done', error = '', apk_size = ?, chunks = ?, icon = '', updated_at = ? WHERE id = ?`)
      .bind(bytes.length, n, Date.now(), id)
  );
  await env.DB.batch(stmts);
  return json({ ok: true, size: bytes.length });
}

/** One signing key for every build, so a new version installs over the old one. */
async function keystoreGet(env) {
  await ensureSchema(env);
  const row = await env.DB.prepare(`SELECT v FROM apk_meta WHERE k = 'keystore'`).first();
  return row ? json(JSON.parse(row.v)) : json({ none: true });
}

async function keystorePut(request, env) {
  await ensureSchema(env);
  const body = await request.json().catch(() => null);
  if (!body?.keystore || !body?.password || body.keystore.length > 20_000) return fail("bad keystore");
  // Never overwrite an existing key: that would break updates of every app already installed.
  await env.DB.prepare(`INSERT OR IGNORE INTO apk_meta (k, v) VALUES ('keystore', ?)`)
    .bind(JSON.stringify({ keystore: body.keystore, password: body.password })).run();
  return keystoreGet(env);
}

// ---------------------------------------------------------------- device management: latest build and QR setup

/** The newest finished build of a device-management app (for remote updates and the QR code). */
async function latestMdmBuild(env, mdmId) {
  await ensureSchema(env);
  return env.DB.prepare(
    `SELECT id, app_id, version_code, config, created_at FROM apk_jobs WHERE status = 'done' AND chunks > 0 AND config LIKE ? ORDER BY created_at DESC LIMIT 1`
  ).bind('%"appKey":"' + mdmId + '"%').first();
}

function mdmHooks(env, origin) {
  const base = origin.replace(/^http:/, "https:") + BASE;
  return {
    latest: async (mdmId) => {
      const j = await latestMdmBuild(env, mdmId);
      return j ? { versionCode: j.version_code, version: versionNameOf(j), url: `${base}/download/${j.id}`, builtAt: j.created_at } : null;
    },
    provisioning: async (mdmId) => {
      const j = await latestMdmBuild(env, mdmId);
      const cert = await env.DB.prepare(`SELECT v FROM apk_meta WHERE k = 'cert_sha256'`).first();
      if (!j || !cert) return null;
      return {
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": `${j.app_id}/uk.smti.mdm.AdminReceiver`,
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": `${base}/download/${j.id}`,
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": cert.v,
        "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": true,
        "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": true,
        expiresAt: j.created_at + KEEP_DAYS * 86400_000,
      };
    },
  };
}

/** The signing certificate's SHA-256 (the same key signs every app), needed by the QR setup. */
async function certPut(request, env) {
  await ensureSchema(env);
  const b = await request.json().catch(() => ({}));
  const v = String(b.sha256 || "");
  if (!/^[A-Za-z0-9_-]{40,50}$/.test(v)) return fail("bad checksum");
  await env.DB.prepare(`INSERT INTO apk_meta (k, v) VALUES ('cert_sha256', ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v`).bind(v).run();
  return json({ ok: true });
}

// ---------------------------------------------------------------- router

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    let path = url.pathname;
    if (path === BASE) return Response.redirect(url.origin + BASE + "/", 301);
    if (path.startsWith(BASE + "/")) path = path.slice(BASE.length);
    const method = request.method;

    try {
      if (path === "/" && method === "GET") {
        return new Response(renderPage(), {
          headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-cache" },
        });
      }
      if (path === "/api/build" && method === "POST") return await createBuild(request, env);
      if (path === "/api/gate" && method === "POST") { const d = await requireGate(request, env); return d || json({ ok: true }); }
      if (path === "/api/gate/setup" && method === "POST") return await setupGate(request, env);
      if (path === "/api/gate/change" && method === "POST") return await setGate(request, env);
      if (path === "/api/mybuilds" && method === "GET") return await myBuilds(request, env, url.origin);
      if (/^\/mdm\/[a-f0-9]{24}$/.test(path) && method === "GET") {
        return new Response(renderMdmPage(), {
          headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-cache", "referrer-policy": "no-referrer", "x-robots-tag": "noindex" },
        });
      }
      if (path.startsWith("/api/mdm/")) {
        const r = await mdmRoute(request, env, path, mdmHooks(env, url.origin));
        if (r) return r;
      }

      let m;
      if ((m = /^\/api\/jobs\/([a-f0-9]{24})$/.exec(path)) && method === "GET") {
        const d = await requireGate(request, env); if (d) return d;
        return await jobStatus(env, m[1], url.origin);
      }
      if ((m = /^\/api\/jobs\/([a-f0-9]{24})\/settings$/.exec(path)) && method === "GET") {
        const d = await requireGate(request, env); if (d) return d;
        return await jobSettings(env, m[1]);
      }
      if ((m = /^\/api\/jobs\/([a-f0-9]{24})\/block$/.exec(path)) && method === "POST") {
        return await blockDownload(request, env, m[1]);
      }
      if ((m = /^\/download\/([a-f0-9]{24})$/.exec(path)) && method === "GET") {
        return await download(env, m[1]);
      }

      // Everything below is only for the GitHub workflow.
      if (path.startsWith("/builder/")) {
        if (!isBuilder(request, env)) return fail("unauthorized", 401);
        if ((m = /^\/builder\/jobs\/([a-f0-9]{24})$/.exec(path)) && method === "GET") return await builderConfig(env, m[1]);
        if ((m = /^\/builder\/jobs\/([a-f0-9]{24})\/status$/.exec(path)) && method === "POST") return await builderStatus(request, env, m[1]);
        if ((m = /^\/builder\/jobs\/([a-f0-9]{24})\/apk$/.exec(path)) && method === "PUT") return await builderUpload(request, env, m[1]);
        if (path === "/builder/keystore" && method === "GET") return await keystoreGet(env);
        if (path === "/builder/keystore" && method === "PUT") return await keystorePut(request, env);
        if (path === "/builder/cert" && method === "PUT") return await certPut(request, env);
      }

      return new Response("Not found", { status: 404 });
    } catch (e) {
      console.log("error", e?.stack || e);
      return fail("שגיאה בשרת", 500);
    }
  },
};
