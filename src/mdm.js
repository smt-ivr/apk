// mdm.js - remote device management: the phones check in every few seconds, get their policy (kiosk,
// restrictions, apps) and a queue of commands, and report back. The owner manages everything from a
// private control page (/apk/mdm/<id>#k=<key>).

const SCHEMA = [
  `CREATE TABLE IF NOT EXISTS mdm_apps (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    key_hash TEXT NOT NULL,
    pass_salt TEXT DEFAULT '',
    pass_hash TEXT DEFAULT '',
    fails INTEGER DEFAULT 0,
    lock_until INTEGER DEFAULT 0,
    policy TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
  )`,
  `CREATE TABLE IF NOT EXISTS mdm_devices (
    app_id TEXT NOT NULL,
    device_id TEXT NOT NULL,
    label TEXT DEFAULT '',
    policy TEXT DEFAULT '',
    info TEXT DEFAULT '{}',
    state TEXT DEFAULT '{}',
    apps TEXT DEFAULT '[]',
    location TEXT DEFAULT '',
    ip TEXT DEFAULT '',
    first_seen INTEGER NOT NULL,
    last_seen INTEGER NOT NULL,
    PRIMARY KEY (app_id, device_id)
  )`,
  `CREATE TABLE IF NOT EXISTS mdm_commands (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    app_id TEXT NOT NULL,
    device_id TEXT NOT NULL,
    cmd TEXT NOT NULL,
    args TEXT DEFAULT '{}',
    status TEXT NOT NULL DEFAULT 'pending',
    result TEXT DEFAULT '',
    created_at INTEGER NOT NULL,
    sent_at INTEGER DEFAULT 0,
    done_at INTEGER DEFAULT 0
  )`,
  `CREATE INDEX IF NOT EXISTS mdm_commands_dev ON mdm_commands(app_id, device_id, status)`,
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
const str = (v, max) => String(v ?? "").replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, max);
const safeJson = (s, def) => { try { return s ? JSON.parse(s) : def; } catch { return def; } };

async function sha256Hex(text) {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function randomHex(bytes) {
  const a = new Uint8Array(bytes);
  crypto.getRandomValues(a);
  return [...a].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** Slow hash for the device code (PBKDF2-SHA1, 60k rounds); the app checks it the same way. */
export async function pinHashSlow(salt, pin) {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(String(pin)), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-1", salt: new TextEncoder().encode(salt), iterations: 60000 }, key, 256);
  return [...new Uint8Array(bits)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

// Android user restrictions the owner can switch on (the exact Android names).
export const RESTRICTIONS = [
  "no_factory_reset", "no_safe_boot", "no_debugging_features", "no_install_apps", "no_uninstall_apps", "no_install_unknown_sources",
  "no_modify_accounts", "no_config_wifi", "no_config_bluetooth", "no_bluetooth", "no_usb_file_transfer", "no_outgoing_calls", "no_sms",
  "no_config_date_time", "no_adjust_volume", "no_config_vpn", "no_physical_media", "no_config_mobile_networks", "no_share_location",
  "no_control_apps", "no_config_tethering", "no_network_reset", "no_add_user", "no_data_roaming", "disallow_config_private_dns",
  "no_config_location", "no_config_screen_timeout", "no_config_brightness", "no_airplane_mode", "no_set_wallpaper", "no_config_credentials",
  "no_unmute_microphone", "no_camera_toggle", "no_fun", "no_autofill", "no_printing",
];

const pkgList = (v, max = 200) => (Array.isArray(v) ? v : []).map((p) => str(p, 150)).filter((p) => /^[a-zA-Z0-9_.]+$/.test(p)).slice(0, max);

/** Policy as the phones get it. */
export function cleanPolicy(input = {}, prev = {}) {
  const p = { ...prev };
  if (input.kiosk !== undefined) {
    const k = input.kiosk || {};
    p.kiosk = {
      enabled: !!k.enabled,
      apps: pkgList(k.apps, 60),
      title: str(k.title, 60),
      home: k.home !== false, notifications: !!k.notifications, systemInfo: k.systemInfo !== false,
      globalActions: !!k.globalActions, keyguard: !!k.keyguard, overview: !!k.overview,
      autoRelaunch: k.autoRelaunch !== false,
    };
  }
  if (input.restrictions !== undefined) p.restrictions = (Array.isArray(input.restrictions) ? input.restrictions : []).filter((r) => RESTRICTIONS.includes(r));
  if (input.hidden !== undefined) p.hidden = pkgList(input.hidden);
  if (input.suspended !== undefined) p.suspended = pkgList(input.suspended);
  if (input.blockUninstall !== undefined) p.blockUninstall = pkgList(input.blockUninstall);
  for (const k of ["autoUpdate", "statusBarDisabled", "cameraDisabled", "screenCaptureDisabled", "keyguardDisabled", "autoGrant", "exitGesture", "useRoot", "stayOn"]) {
    if (input[k] !== undefined) p[k] = !!input[k];
  }
  if (input.lockMessage !== undefined) p.lockMessage = str(input.lockMessage, 200);
  if (input.pollSeconds !== undefined) p.pollSeconds = Math.max(5, Math.min(600, Number(input.pollSeconds) || 20));
  p.kiosk = p.kiosk || { enabled: false, apps: [], home: true, systemInfo: true, autoRelaunch: true };
  p.restrictions = p.restrictions || ["no_factory_reset", "no_safe_boot"];
  p.hidden = p.hidden || [];
  p.suspended = p.suspended || [];
  p.blockUninstall = p.blockUninstall || [];
  if (p.exitGesture === undefined) p.exitGesture = true;
  if (p.autoGrant === undefined) p.autoGrant = true;
  if (p.useRoot === undefined) p.useRoot = true;
  if (p.autoUpdate === undefined) p.autoUpdate = true;
  p.pollSeconds = p.pollSeconds || 20;
  return p;
}

/** Called on the first build: creates the control page. */
export async function createMdm(env, name, policy, password) {
  await ensure(env);
  const id = randomHex(12);
  const key = randomHex(16);
  const now = Date.now();
  const p = cleanPolicy(policy);
  p.version = 1;
  const salt = randomHex(16);
  await env.DB.prepare(`INSERT INTO mdm_apps (id, name, key_hash, pass_salt, pass_hash, policy, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`)
    .bind(id, name, await sha256Hex(key), salt, await pinHashSlow(salt, password), JSON.stringify(p), now, now).run();
  return { id, key, policy: p };
}

export async function mdmPolicy(env, id) {
  await ensure(env);
  const row = await env.DB.prepare(`SELECT policy FROM mdm_apps WHERE id = ?`).bind(id).first();
  return row ? JSON.parse(row.policy) : null;
}

// ---------------------------------------------------------------- the phones

async function sync(request, env, id, hooks) {
  await ensure(env);
  const app = await env.DB.prepare(`SELECT policy FROM mdm_apps WHERE id = ?`).bind(id).first();
  if (!app) return fail("לא נמצא", 404);
  const b = await request.json().catch(() => ({}));
  const deviceId = str(b.deviceId, 40);
  if (!/^[a-z0-9]{8,40}$/.test(deviceId)) return fail("מכשיר לא תקין");
  const now = Date.now();

  // results of commands it ran
  for (const r of (Array.isArray(b.results) ? b.results : []).slice(0, 30)) {
    await env.DB.prepare(`UPDATE mdm_commands SET status = ?, result = ?, done_at = ? WHERE id = ? AND app_id = ? AND device_id IN (?, '*')`)
      .bind(r.ok ? "done" : "failed", str(r.result, 1500), now, Number(r.id) || 0, id, deviceId).run();
  }

  const info = typeof b.info === "object" && b.info ? JSON.stringify(b.info).slice(0, 4000) : null;
  const state = typeof b.state === "object" && b.state ? JSON.stringify(b.state).slice(0, 2000) : null;
  const apps = Array.isArray(b.apps) ? JSON.stringify(b.apps.slice(0, 400).map((a) => ({ p: str(a?.p, 150), n: str(a?.n, 60), s: !!a?.s, h: !!a?.h }))) : null;
  const location = b.location && typeof b.location === "object" ? JSON.stringify(b.location).slice(0, 300) : null;
  const ip = request.headers.get("cf-connecting-ip") || "";
  await env.DB.prepare(`INSERT INTO mdm_devices (app_id, device_id, info, state, apps, location, ip, first_seen, last_seen)
      VALUES (?, ?, COALESCE(?, '{}'), COALESCE(?, '{}'), COALESCE(?, '[]'), COALESCE(?, ''), ?, ?, ?)
      ON CONFLICT(app_id, device_id) DO UPDATE SET info = COALESCE(?, mdm_devices.info), state = COALESCE(?, mdm_devices.state),
        apps = COALESCE(?, mdm_devices.apps), location = COALESCE(?, mdm_devices.location), ip = excluded.ip, last_seen = excluded.last_seen`)
    .bind(id, deviceId, info, state, apps, location, ip, now, now, info, state, apps, location).run();

  const dev = await env.DB.prepare(`SELECT policy FROM mdm_devices WHERE app_id = ? AND device_id = ?`).bind(id, deviceId).first();
  const policy = dev && dev.policy ? JSON.parse(dev.policy) : JSON.parse(app.policy);

  // commands waiting for this phone (or for all phones, once each)
  const { results: cmds } = await env.DB.prepare(`SELECT id, cmd, args FROM mdm_commands WHERE app_id = ? AND device_id = ? AND status = 'pending' ORDER BY id LIMIT 10`)
    .bind(id, deviceId).all();
  if (cmds && cmds.length) {
    await env.DB.batch(cmds.map((c) => env.DB.prepare(`UPDATE mdm_commands SET status = 'sent', sent_at = ? WHERE id = ?`).bind(now, c.id)));
  }
  const latest = hooks ? await hooks.latest(id) : null;
  return json({ ok: true, policy, latest, commands: (cmds || []).map((c) => ({ id: c.id, cmd: c.cmd, args: safeJson(c.args, {}) })) });
}

// ---------------------------------------------------------------- the owner

const passOk = new Set();

/** The link key and the site password must both be right; 8 wrong passwords lock the page for 15 minutes. */
async function owner(request, env, id) {
  await ensure(env);
  const key = request.headers.get("x-mdm-key") || "";
  const pass = request.headers.get("x-mdm-pass") || "";
  const app = await env.DB.prepare(`SELECT * FROM mdm_apps WHERE id = ?`).bind(id).first();
  if (!app || !key || (await sha256Hex(key)) !== app.key_hash) return { error: "הקישור לא תקין" };
  if (app.lock_until > Date.now()) return { error: "יותר מדי ניסיונות. נסו שוב בעוד " + Math.ceil((app.lock_until - Date.now()) / 60000) + " דקות", locked: true };
  const memo = await sha256Hex(id + "|" + pass + "|" + app.pass_hash);
  if (passOk.has(memo)) return { app };
  if (!pass || (await pinHashSlow(app.pass_salt, pass)) !== app.pass_hash) {
    const fails = (app.fails || 0) + 1;
    await env.DB.prepare(`UPDATE mdm_apps SET fails = ?, lock_until = ? WHERE id = ?`)
      .bind(fails >= 8 ? 0 : fails, fails >= 8 ? Date.now() + 15 * 60000 : 0, id).run();
    return { error: pass ? "סיסמה שגויה" : "נדרשת סיסמה", needPass: true };
  }
  if (app.fails) await env.DB.prepare(`UPDATE mdm_apps SET fails = 0 WHERE id = ?`).bind(id).run();
  if (passOk.size > 500) passOk.clear();
  passOk.add(memo);
  return { app };
}

async function adminGet(request, env, id, hooks) {
  const o = await owner(request, env, id);
  if (o.error) return json({ ok: false, error: o.error, needPass: !!o.needPass, locked: !!o.locked }, 401);
  const app = o.app;
  const { results: devices } = await env.DB.prepare(`SELECT * FROM mdm_devices WHERE app_id = ? ORDER BY last_seen DESC`).bind(id).all();
  const { results: commands } = await env.DB.prepare(`SELECT * FROM mdm_commands WHERE app_id = ? ORDER BY id DESC LIMIT 150`).bind(id).all();
  return json({
    ok: true,
    name: app.name,
    policy: JSON.parse(app.policy),
    devices: (devices || []).map((d) => ({
      device_id: d.device_id, label: d.label, first_seen: d.first_seen, last_seen: d.last_seen, ip: d.ip,
      policy: d.policy ? JSON.parse(d.policy) : null, info: safeJson(d.info, {}), state: safeJson(d.state, {}),
      apps: safeJson(d.apps, []), location: safeJson(d.location, null),
    })),
    commands: (commands || []).map((c) => ({ ...c, args: safeJson(c.args, {}) })),
    restrictions: RESTRICTIONS,
    provisioning: hooks ? await hooks.provisioning(id) : null,
    latest: hooks ? await hooks.latest(id) : null,
    now: Date.now(),
  });
}

const COMMANDS = ["update", "lock", "reboot", "ring", "message", "locate", "info", "install", "uninstall", "launch", "clear_data", "kiosk_resume", "kiosk_pause", "wipe", "remove", "screen_on", "root"];

async function adminPost(request, env, id) {
  const o = await owner(request, env, id);
  if (o.error) return json({ ok: false, error: o.error, needPass: !!o.needPass }, 401);
  const app = o.app;
  const b = await request.json().catch(() => ({}));
  const action = String(b.action || "");
  const now = Date.now();
  const target = str(b.deviceId, 40);

  if (action === "policy") {
    // target: a device, "default" (new devices + those without their own) or "all"
    if (target === "default" || target === "all") {
      const p = cleanPolicy(b.policy || {}, JSON.parse(app.policy));
      p.version = (Number(p.version) || 0) + 1;
      await env.DB.prepare(`UPDATE mdm_apps SET policy = ?, updated_at = ? WHERE id = ?`).bind(JSON.stringify(p), now, id).run();
      if (target === "all") await env.DB.prepare(`UPDATE mdm_devices SET policy = '' WHERE app_id = ?`).bind(id).run();
      return json({ ok: true, policy: p });
    }
    const dev = await env.DB.prepare(`SELECT policy FROM mdm_devices WHERE app_id = ? AND device_id = ?`).bind(id, target).first();
    if (!dev) return fail("המכשיר לא נמצא", 404);
    const base = dev.policy ? JSON.parse(dev.policy) : JSON.parse(app.policy);
    const p = cleanPolicy(b.policy || {}, base);
    p.version = (Number(p.version) || 0) + 1;
    await env.DB.prepare(`UPDATE mdm_devices SET policy = ? WHERE app_id = ? AND device_id = ?`).bind(JSON.stringify(p), id, target).run();
    return json({ ok: true, policy: p });
  }
  if (action === "resetPolicy") {
    await env.DB.prepare(`UPDATE mdm_devices SET policy = '' WHERE app_id = ? AND device_id = ?`).bind(id, target).run();
    return json({ ok: true });
  }
  if (action === "command") {
    const cmd = String(b.cmd || "");
    if (!COMMANDS.includes(cmd)) return fail("פקודה לא מוכרת");
    const args = {};
    for (const [k, v] of Object.entries(b.args || {})) args[str(k, 30)] = str(v, 2000);
    if (cmd === "install" && !/^https:\/\/\S+$/i.test(args.url || "")) return fail("נדרש קישור https לקובץ APK");
    if (["uninstall", "launch", "clear_data"].includes(cmd) && !/^[a-zA-Z0-9_.]+$/.test(args.pkg || "")) return fail("שם חבילה לא תקין");
    let targets = [target];
    if (target === "all") {
      const { results } = await env.DB.prepare(`SELECT device_id FROM mdm_devices WHERE app_id = ?`).bind(id).all();
      targets = (results || []).map((r) => r.device_id);
    }
    if (!targets.length || !targets[0]) return fail("אין מכשירים");
    await env.DB.batch(targets.map((t) => env.DB.prepare(`INSERT INTO mdm_commands (app_id, device_id, cmd, args, created_at) VALUES (?, ?, ?, ?, ?)`)
      .bind(id, t, cmd, JSON.stringify(args), now)));
    return json({ ok: true, queued: targets.length });
  }
  if (action === "cancelCommand") {
    await env.DB.prepare(`UPDATE mdm_commands SET status = 'cancelled' WHERE id = ? AND app_id = ? AND status = 'pending'`).bind(Number(b.id), id).run();
    return json({ ok: true });
  }
  if (action === "pin") {
    const pin = String(b.pin || "").trim();
    if (!/^\d{4,8}$/.test(pin)) return fail("הקוד צריך להיות 4 עד 8 ספרות");
    const salt = randomHex(16);
    const hash = await pinHashSlow(salt, pin);
    const p = JSON.parse(app.policy);
    Object.assign(p, { pinSalt: salt, pinHash: hash, pinAlgo: "p1", version: (Number(p.version) || 0) + 1 });
    await env.DB.prepare(`UPDATE mdm_apps SET policy = ?, updated_at = ? WHERE id = ?`).bind(JSON.stringify(p), now, id).run();
    // devices with their own policy get the new code too
    const { results } = await env.DB.prepare(`SELECT device_id, policy FROM mdm_devices WHERE app_id = ? AND policy != ''`).bind(id).all();
    for (const d of results || []) {
      const dp = JSON.parse(d.policy);
      Object.assign(dp, { pinSalt: salt, pinHash: hash, pinAlgo: "p1", version: (Number(dp.version) || 0) + 1 });
      await env.DB.prepare(`UPDATE mdm_devices SET policy = ? WHERE app_id = ? AND device_id = ?`).bind(JSON.stringify(dp), id, d.device_id).run();
    }
    return json({ ok: true });
  }
  if (action === "password") {
    const np = String(b.newPassword || "");
    if (np.length < 4) return fail("סיסמה של 4 תווים לפחות");
    const salt = randomHex(16);
    await env.DB.prepare(`UPDATE mdm_apps SET pass_salt = ?, pass_hash = ? WHERE id = ?`).bind(salt, await pinHashSlow(salt, np), id).run();
    return json({ ok: true });
  }
  if (action === "label") {
    await env.DB.prepare(`UPDATE mdm_devices SET label = ? WHERE app_id = ? AND device_id = ?`).bind(str(b.label, 40), id, target).run();
    return json({ ok: true });
  }
  if (action === "removeDevice") {
    await env.DB.batch([
      env.DB.prepare(`DELETE FROM mdm_devices WHERE app_id = ? AND device_id = ?`).bind(id, target),
      env.DB.prepare(`DELETE FROM mdm_commands WHERE app_id = ? AND device_id = ?`).bind(id, target),
    ]);
    return json({ ok: true });
  }
  return fail("פעולה לא מוכרת");
}

export async function mdmRoute(request, env, path, hooks) {
  let m;
  if ((m = /^\/api\/mdm\/([a-f0-9]{24})\/sync$/.exec(path)) && request.method === "POST") return sync(request, env, m[1], hooks);
  if ((m = /^\/api\/mdm\/([a-f0-9]{24})\/admin$/.exec(path))) {
    if (request.method === "GET") return adminGet(request, env, m[1], hooks);
    if (request.method === "POST") return adminPost(request, env, m[1]);
  }
  return null;
}
