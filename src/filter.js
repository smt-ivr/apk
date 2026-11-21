// filter.js - the website filter: the phones report here and get their rules, and the owner manages
// everything from a private control page (/apk/filter/<id>#k=<key>).

const SCHEMA = [
  `CREATE TABLE IF NOT EXISTS filter_apps (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    key_hash TEXT NOT NULL,
    rules TEXT NOT NULL,
    message TEXT DEFAULT '',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
  )`,
  `CREATE TABLE IF NOT EXISTS filter_devices (
    filter_id TEXT NOT NULL,
    device_id TEXT NOT NULL,
    label TEXT DEFAULT '',
    model TEXT DEFAULT '',
    android TEXT DEFAULT '',
    version TEXT DEFAULT '',
    running INTEGER DEFAULT 0,
    wanted INTEGER DEFAULT 0,
    paused INTEGER DEFAULT 0,
    stopped_at INTEGER DEFAULT 0,
    protections TEXT DEFAULT '{}',
    blocked_today INTEGER DEFAULT 0,
    allowed_today INTEGER DEFAULT 0,
    blocked_total INTEGER DEFAULT 0,
    recent TEXT DEFAULT '[]',
    apps TEXT DEFAULT '',
    ip TEXT DEFAULT '',
    first_seen INTEGER NOT NULL,
    last_seen INTEGER NOT NULL,
    PRIMARY KEY (filter_id, device_id)
  )`,
  `CREATE TABLE IF NOT EXISTS filter_requests (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    filter_id TEXT NOT NULL,
    device_id TEXT NOT NULL,
    model TEXT DEFAULT '',
    domain TEXT NOT NULL,
    note TEXT DEFAULT '',
    status TEXT NOT NULL DEFAULT 'pending',
    created_at INTEGER NOT NULL,
    decided_at INTEGER DEFAULT 0
  )`,
  `CREATE INDEX IF NOT EXISTS filter_requests_f ON filter_requests(filter_id, status)`,
];

let ready = false;
async function ensure(env) {
  if (ready) return;
  await env.DB.batch(SCHEMA.map((s) => env.DB.prepare(s)));
  ready = true;
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}
const fail = (message, status = 400) => json({ ok: false, error: message }, status);

async function sha256Hex(text) {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function randomHex(bytes) {
  const a = new Uint8Array(bytes);
  crypto.getRandomValues(a);
  return [...a].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** Slow hash for the filter's code (PBKDF2-SHA1, 60k rounds), so it can't be guessed quickly from the app file. */
export async function pinHashSlow(salt, pin) {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(String(pin)), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-1", salt: new TextEncoder().encode(salt), iterations: 60000 }, key, 256);
  return [...new Uint8Array(bits)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

const str = (v, max) => String(v ?? "").replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, max);

/** "https://www.Site.co.il/x" -> "site.co.il"; null if it is not a domain. */
export function cleanDomain(input) {
  let s = String(input || "").trim().toLowerCase();
  s = s.replace(/^[a-z]+:\/\//, "").replace(/[/?#].*$/, "").replace(/:\d+$/, "").replace(/^\*\./, "").replace(/^www\./, "").replace(/\.$/, "");
  return /^([a-z0-9-]+\.)+[a-z0-9-]{2,}$/.test(s) && s.length <= 200 ? s : null;
}

function cleanList(list, max = 2000) {
  const out = [];
  const seen = new Set();
  for (const x of Array.isArray(list) ? list : []) {
    const d = cleanDomain(x);
    if (d && !seen.has(d)) { seen.add(d); out.push(d); }
    if (out.length >= max) break;
  }
  return out;
}

/** Rules as the phones get them. */
export function cleanRules(input = {}, prev = {}) {
  const r = { ...prev };
  if (input.mode !== undefined) r.mode = input.mode === "block" ? "block" : "allow";
  if (input.allow !== undefined) r.allow = cleanList(input.allow);
  if (input.block !== undefined) r.block = cleanList(input.block);
  if (input.bypassApps !== undefined) r.bypassApps = (Array.isArray(input.bypassApps) ? input.bypassApps : []).map((p) => str(p, 120)).filter((p) => /^[a-zA-Z0-9_.]+$/.test(p)).slice(0, 100);
  for (const k of ["blockAll", "blockPage", "allowLocalPin"]) if (input[k] !== undefined) r[k] = !!input[k];
  if (input.blockMessage !== undefined) r.blockMessage = str(input.blockMessage, 300);
  if (input.pauseUntil !== undefined) r.pauseUntil = Math.max(0, Number(input.pauseUntil) || 0);
  if (input.hours !== undefined) {
    r.hours = (Array.isArray(input.hours) ? input.hours : []).slice(0, 14).map((h) => ({
      days: Math.max(0, Math.min(127, Number(h.days) || 0)),
      from: Math.max(0, Math.min(1440, Number(h.from) || 0)),
      to: Math.max(0, Math.min(1440, Number(h.to) || 0)),
    })).filter((h) => h.days);
  }
  r.mode = r.mode || "allow";
  r.allow = r.allow || [];
  r.block = r.block || [];
  r.bypassApps = r.bypassApps || [];
  if (r.blockPage === undefined) r.blockPage = true;
  if (r.allowLocalPin === undefined) r.allowLocalPin = true;
  return r;
}

/** Called when a filter app is built for the first time: creates its control page. */
export async function createFilter(env, name, rules) {
  await ensure(env);
  const id = randomHex(12);
  const key = randomHex(16);
  const now = Date.now();
  const r = cleanRules(rules);
  r.version = 1;
  await env.DB.prepare(`INSERT INTO filter_apps (id, name, key_hash, rules, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)`)
    .bind(id, name, await sha256Hex(key), JSON.stringify(r), now, now).run();
  return { id, key, rules: r };
}

export async function filterRules(env, id) {
  await ensure(env);
  const row = await env.DB.prepare(`SELECT rules FROM filter_apps WHERE id = ?`).bind(id).first();
  return row ? JSON.parse(row.rules) : null;
}

async function saveRules(env, id, rules) {
  rules.version = (Number(rules.version) || 0) + 1;
  await env.DB.prepare(`UPDATE filter_apps SET rules = ?, updated_at = ? WHERE id = ?`).bind(JSON.stringify(rules), Date.now(), id).run();
  return rules;
}

// ---------------------------------------------------------------- the phones

async function sync(request, env, id) {
  await ensure(env);
  const app = await env.DB.prepare(`SELECT rules, message FROM filter_apps WHERE id = ?`).bind(id).first();
  if (!app) return fail("הסינון לא נמצא", 404);
  const b = await request.json().catch(() => ({}));
  const deviceId = str(b.deviceId, 40);
  if (!/^[a-z0-9]{8,40}$/.test(deviceId)) return fail("מכשיר לא תקין");
  const now = Date.now();
  const recent = JSON.stringify((Array.isArray(b.recent) ? b.recent : []).slice(0, 40).map((x) => ({ d: str(x?.d, 200), t: Number(x?.t) || 0 })));
  const prot = JSON.stringify(typeof b.protections === "object" && b.protections ? b.protections : {}).slice(0, 1000);
  const apps = Array.isArray(b.apps) ? JSON.stringify(b.apps.slice(0, 300).map((a) => ({ p: str(a?.p, 120), n: str(a?.n, 60) }))) : null;
  await env.DB.prepare(`INSERT INTO filter_devices (filter_id, device_id, model, android, version, running, wanted, paused, stopped_at, protections,
      blocked_today, allowed_today, blocked_total, recent, apps, ip, first_seen, last_seen)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, ''), ?, ?, ?)
    ON CONFLICT(filter_id, device_id) DO UPDATE SET model = excluded.model, android = excluded.android, version = excluded.version,
      running = excluded.running, wanted = excluded.wanted, paused = excluded.paused, stopped_at = excluded.stopped_at, protections = excluded.protections,
      blocked_today = excluded.blocked_today, allowed_today = excluded.allowed_today, blocked_total = excluded.blocked_total,
      recent = excluded.recent, apps = COALESCE(?, filter_devices.apps), ip = excluded.ip, last_seen = excluded.last_seen`)
    .bind(id, deviceId, str(b.model, 80), str(b.android, 20), str(b.version, 20), b.running ? 1 : 0, b.wanted ? 1 : 0, b.paused ? 1 : 0,
      Number(b.stoppedAt) || 0, prot, Number(b.blockedToday) || 0, Number(b.allowedToday) || 0, Number(b.blockedTotal) || 0, recent, apps,
      request.headers.get("cf-connecting-ip") || "", now, now, apps).run();
  return json({ ok: true, rules: JSON.parse(app.rules), message: app.message || "" });
}

async function requestSite(request, env, id) {
  await ensure(env);
  const app = await env.DB.prepare(`SELECT id FROM filter_apps WHERE id = ?`).bind(id).first();
  if (!app) return fail("הסינון לא נמצא", 404);
  const b = await request.json().catch(() => ({}));
  const deviceId = str(b.deviceId, 40);
  const domain = cleanDomain(b.domain);
  if (!/^[a-z0-9]{8,40}$/.test(deviceId) || !domain) return fail("בקשה לא תקינה");
  const day = await env.DB.prepare(`SELECT COUNT(*) AS n FROM filter_requests WHERE filter_id = ? AND device_id = ? AND created_at > ?`)
    .bind(id, deviceId, Date.now() - 86400_000).first();
  if ((day?.n || 0) >= 40) return fail("נשלחו הרבה בקשות היום. נסו שוב מחר.", 429);
  const dup = await env.DB.prepare(`SELECT id FROM filter_requests WHERE filter_id = ? AND domain = ? AND status = 'pending'`).bind(id, domain).first();
  if (dup) return json({ ok: true, already: true });
  await env.DB.prepare(`INSERT INTO filter_requests (filter_id, device_id, model, domain, note, created_at) VALUES (?, ?, ?, ?, ?, ?)`)
    .bind(id, deviceId, str(b.model, 80), domain, str(b.note, 300), Date.now()).run();
  return json({ ok: true });
}

// ---------------------------------------------------------------- the owner's control page

async function owner(request, env, id) {
  await ensure(env);
  const key = request.headers.get("x-filter-key") || "";
  const app = await env.DB.prepare(`SELECT * FROM filter_apps WHERE id = ?`).bind(id).first();
  if (!app || !key || (await sha256Hex(key)) !== app.key_hash) return null;
  return app;
}

async function adminGet(request, env, id) {
  const app = await owner(request, env, id);
  if (!app) return fail("אין גישה. ודאו שפתחתם את הקישור המלא של מרכז השליטה.", 401);
  const { results: devices } = await env.DB.prepare(`SELECT * FROM filter_devices WHERE filter_id = ? ORDER BY last_seen DESC`).bind(id).all();
  const { results: requests } = await env.DB.prepare(`SELECT * FROM filter_requests WHERE filter_id = ? ORDER BY CASE status WHEN 'pending' THEN 0 ELSE 1 END, id DESC LIMIT 200`).bind(id).all();
  return json({
    ok: true,
    name: app.name,
    rules: JSON.parse(app.rules),
    message: app.message || "",
    devices: (devices || []).map((d) => ({
      ...d,
      protections: safeJson(d.protections, {}),
      recent: safeJson(d.recent, []),
      apps: safeJson(d.apps, []),
    })),
    requests: requests || [],
    now: Date.now(),
  });
}

function safeJson(s, def) {
  try { return s ? JSON.parse(s) : def; } catch { return def; }
}

async function adminPost(request, env, id) {
  const app = await owner(request, env, id);
  if (!app) return fail("אין גישה", 401);
  const b = await request.json().catch(() => ({}));
  let rules = JSON.parse(app.rules);
  const action = String(b.action || "");
  if (action === "save") {
    rules = await saveRules(env, id, cleanRules(b.rules || {}, rules));
    return json({ ok: true, rules });
  }
  if (action === "approve" || action === "deny") {
    const req = await env.DB.prepare(`SELECT * FROM filter_requests WHERE id = ? AND filter_id = ?`).bind(Number(b.id), id).first();
    if (!req) return fail("הבקשה לא נמצאה", 404);
    await env.DB.prepare(`UPDATE filter_requests SET status = ?, decided_at = ? WHERE filter_id = ? AND domain = ? AND status = 'pending'`)
      .bind(action === "approve" ? "approved" : "denied", Date.now(), id, req.domain).run();
    if (action === "approve") {
      const d = cleanDomain(b.domain || req.domain);
      if (rules.mode === "block") rules.block = (rules.block || []).filter((x) => x !== d);
      else if (d && !(rules.allow || []).includes(d)) rules.allow = [...(rules.allow || []), d];
      rules = await saveRules(env, id, cleanRules({}, rules));
    }
    return json({ ok: true, rules });
  }
  if (action === "allowDomain") {
    const d = cleanDomain(b.domain);
    if (!d) return fail("כתובת לא תקינה");
    if (rules.mode === "block") rules.block = (rules.block || []).filter((x) => x !== d);
    else if (!(rules.allow || []).includes(d)) rules.allow = [...(rules.allow || []), d];
    rules = await saveRules(env, id, cleanRules({}, rules));
    return json({ ok: true, rules });
  }
  if (action === "message") {
    await env.DB.prepare(`UPDATE filter_apps SET message = ?, updated_at = ? WHERE id = ?`).bind(str(b.text, 400), Date.now(), id).run();
    return json({ ok: true });
  }
  if (action === "pin") {
    const pin = String(b.pin || "").trim();
    if (!/^\d{4,8}$/.test(pin)) return fail("הקוד צריך להיות 4 עד 8 ספרות");
    rules.pinSalt = randomHex(16);
    rules.pinHash = await pinHashSlow(rules.pinSalt, pin);
    rules.pinAlgo = "p1";
    rules = await saveRules(env, id, rules);
    return json({ ok: true });
  }
  if (action === "label") {
    await env.DB.prepare(`UPDATE filter_devices SET label = ? WHERE filter_id = ? AND device_id = ?`).bind(str(b.label, 40), id, str(b.deviceId, 40)).run();
    return json({ ok: true });
  }
  if (action === "removeDevice") {
    await env.DB.prepare(`DELETE FROM filter_devices WHERE filter_id = ? AND device_id = ?`).bind(id, str(b.deviceId, 40)).run();
    return json({ ok: true });
  }
  return fail("פעולה לא מוכרת");
}

// ---------------------------------------------------------------- router (called from index.js)

export async function filterRoute(request, env, path) {
  let m;
  if ((m = /^\/api\/filter\/([a-f0-9]{24})\/sync$/.exec(path)) && request.method === "POST") return sync(request, env, m[1]);
  if ((m = /^\/api\/filter\/([a-f0-9]{24})\/request$/.exec(path)) && request.method === "POST") return requestSite(request, env, m[1]);
  if ((m = /^\/api\/filter\/([a-f0-9]{24})\/admin$/.exec(path))) {
    if (request.method === "GET") return adminGet(request, env, m[1]);
    if (request.method === "POST") return adminPost(request, env, m[1]);
  }
  return null;
}
