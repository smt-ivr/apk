// The owner's control page for managed phones. The link key stays after "#" (never sent in the URL),
// and a password is needed as well.

export function renderMdmPage() {
  return `<!doctype html>
<html lang="he" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>ניהול מכשירים</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Rubik:wght@400;500;700&display=swap" rel="stylesheet">
<script src="https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js"></script>
<style>
:root{--bg:#f2f5fa;--card:#fff;--text:#0f1a2b;--muted:#6b778a;--line:#e3e8f0;--blue:#1f5fad;--blue2:#3b82f6;--navy:#0f1a2b;--navy2:#17325c;
--good:#16a34a;--good-s:#e8f7ee;--warn:#c2760c;--warn-s:#fff4e2;--bad:#dc2626;--bad-s:#fdecef;--field:#f6f8fb}
@media (prefers-color-scheme:dark){:root{--bg:#0b1220;--card:#131c2e;--text:#e6ebf3;--muted:#8b97ab;--line:#24304a;--field:#0f1729;--good-s:#10301f;--warn-s:#33260f;--bad-s:#3a1418}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font:15px/1.55 Rubik,system-ui,sans-serif}
.top{background:linear-gradient(160deg,var(--navy),var(--navy2));color:#fff;padding:22px 16px 64px}
.in,.wrap{max-width:1080px;margin:0 auto}
.top h1{margin:0;font-size:22px}
.top p{margin:4px 0 0;opacity:.8;font-size:14px}
.wrap{padding:0 16px 40px;margin-top:-46px}
.kpis{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}
@media (max-width:720px){.kpis{grid-template-columns:repeat(2,1fr)}}
.kpi{background:var(--card);border-radius:16px;padding:14px 16px;box-shadow:0 2px 10px rgba(15,26,43,.06)}
.kpi b{display:block;font-size:24px}.kpi span{color:var(--muted);font-size:13px}
.tabs{display:flex;gap:6px;margin:18px 0 12px;overflow-x:auto;padding-bottom:2px}
.tab{border:0;background:var(--card);color:var(--text);padding:9px 16px;border-radius:999px;font:inherit;font-weight:500;cursor:pointer;white-space:nowrap;box-shadow:0 1px 4px rgba(15,26,43,.05)}
.tab[aria-selected=true]{background:var(--blue);color:#fff}
.card{background:var(--card);border-radius:18px;padding:18px;box-shadow:0 2px 10px rgba(15,26,43,.06);margin-bottom:12px}
.card h2{margin:0 0 10px;font-size:17px}.card h3{margin:14px 0 8px;font-size:15px}
.hint{color:var(--muted);font-size:13.5px;margin:0 0 10px}
.row{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
.grow{flex:1;min-width:0}
input[type=text],input[type=password],input[type=number],textarea,select{width:100%;padding:10px 12px;border:1px solid var(--line);border-radius:12px;background:var(--field);color:var(--text);font:inherit}
.ltr{direction:ltr;text-align:left}
.btn{border:1px solid var(--line);border-radius:12px;padding:9px 14px;font:inherit;font-weight:500;cursor:pointer;background:var(--field);color:var(--text);white-space:nowrap}
.btn.p{background:linear-gradient(135deg,var(--blue2),var(--blue));color:#fff;border:0}
.btn.g{background:var(--good);color:#fff;border:0}
.btn.d{background:var(--bad-s);color:var(--bad);border:0}
.btn.sm{padding:6px 10px;font-size:13px;border-radius:10px}
.badge{display:inline-block;border-radius:999px;padding:2px 9px;font-size:12px;font-weight:500;background:var(--field);border:1px solid var(--line)}
.badge.good{background:var(--good-s);color:var(--good);border:0}.badge.warn{background:var(--warn-s);color:var(--warn);border:0}.badge.bad{background:var(--bad-s);color:var(--bad);border:0}
.muted{color:var(--muted);font-size:13px}
.dev{border:1px solid var(--line);border-radius:16px;padding:14px;margin-bottom:10px;background:var(--card)}
.dev h3{margin:0;font-size:16px}
.facts{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:4px 10px;margin:10px 0;font-size:13px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(250px,1fr));gap:2px 12px}
.chk{display:flex;gap:8px;align-items:flex-start;padding:7px 0;font-size:14px;cursor:pointer}
.chk input{width:18px;height:18px;margin-top:2px;accent-color:var(--blue);flex:none}
.list>div{display:flex;gap:8px;align-items:center;padding:8px 0;border-bottom:1px solid var(--line)}
.list>div:last-child{border-bottom:0}
.drawer{position:fixed;inset:0;background:rgba(10,16,30,.5);display:flex;justify-content:flex-start;z-index:5}
.drawer>div{background:var(--bg);width:min(760px,100%);height:100%;overflow:auto;padding:18px 16px 40px}
.toast{position:fixed;bottom:18px;left:50%;transform:translateX(-50%);background:var(--navy);color:#fff;padding:10px 18px;border-radius:12px;opacity:0;transition:.25s;pointer-events:none;z-index:9}
.toast.on{opacity:1}
.hidden{display:none!important}
.login{max-width:380px;margin:0 auto}
#qr{background:#fff;padding:14px;border-radius:14px;display:inline-block}
pre{white-space:pre-wrap;direction:ltr;text-align:left;font-size:12px;background:var(--field);border-radius:10px;padding:10px;max-height:300px;overflow:auto}
</style>
</head>
<body>
<div class="top"><div class="in"><h1>🛡️ <span id="name">ניהול מכשירים</span></h1><p>שליטה מלאה מרחוק: קיוסק, הגבלות, אפליקציות, פקודות ועדכונים.</p></div></div>
<div class="wrap">
  <div id="loginBox" class="card login hidden">
    <h2>כניסה</h2>
    <p class="hint" id="loginMsg">הזינו את סיסמת אתר הניהול.</p>
    <input type="password" id="pass" autocomplete="current-password">
    <button class="btn p" id="loginBtn" style="margin-top:10px;width:100%">כניסה</button>
  </div>
  <div id="app" class="hidden">
    <div class="kpis" id="kpis"></div>
    <div class="tabs">
      <button class="tab" data-t="devices" aria-selected="true">מכשירים</button>
      <button class="tab" data-t="policy">מדיניות כללית</button>
      <button class="tab" data-t="setup">התקנה</button>
      <button class="tab" data-t="log">יומן פקודות</button>
      <button class="tab" data-t="settings">הגדרות</button>
    </div>
    <section data-p="devices"><div class="card"><div class="row"><b class="grow">פקודה לכל המכשירים</b>
      <button class="btn sm" data-all="lock">נעילה</button><button class="btn sm" data-all="message">הודעה</button><button class="btn sm" data-all="update">עדכון</button><button class="btn sm" data-all="reboot">הפעלה מחדש</button></div></div>
      <div id="devList"></div></section>
    <section data-p="policy" class="hidden"><div class="card"><h2>מדיניות כללית</h2><p class="hint">חלה על כל מכשיר חדש ועל מכשירים בלי מדיניות משלהם. "שמירה לכולם" מבטלת גם מדיניות אישית של מכשירים.</p><div id="polDefault"></div></div></section>
    <section data-p="setup" class="hidden">
      <div class="card"><h2>התקנה בברקוד (מומלץ)</h2>
        <p class="hint">בטלפון חדש או אחרי איפוס: במסך "ברוכים הבאים" נוגעים 6 פעמים באותו מקום, מתחברים ל-WiFi וסורקים. האפליקציה מותקנת לבד כמנהלת המכשיר, ואם הקיוסק מוגדר הוא נדלק מייד.</p>
        <div id="qrBox"></div></div>
      <div class="card"><h2>טלפון קיים</h2>
        <p class="hint"><b>עם רוט:</b> מתקינים את האפליקציה ולוחצים "הפעלה עם רוט". <b>עם מחשב:</b> מסירים זמנית את החשבונות, מפעילים ניפוי באגים ב-USB ומריצים:</p>
        <pre id="adb"></pre><p class="muted" id="dlLine"></p></div>
    </section>
    <section data-p="log" class="hidden"><div class="card"><h2>יומן פקודות</h2><div class="list" id="logList"></div></div></section>
    <section data-p="settings" class="hidden">
      <div class="card"><h2>קוד המכשירים</h2><p class="hint">נדרש בטלפון ליציאה מהקיוסק ולהסרה מלאה. קוד חדש מגיע לכל המכשירים.</p>
        <div class="row"><input type="password" id="newPin" class="ltr grow" inputmode="numeric" maxlength="8" placeholder="4–8 ספרות"><button class="btn p" id="savePin">עדכון</button></div></div>
      <div class="card"><h2>סיסמת אתר הניהול</h2>
        <div class="row"><input type="password" id="newPass" class="grow" placeholder="לפחות 4 תווים" autocomplete="new-password"><button class="btn p" id="savePass">עדכון</button></div></div>
      <div class="card"><h2>הקישור</h2><p class="hint">שמרו אותו. בלי הקישור והסיסמה אי אפשר להיכנס.</p><button class="btn" id="copyLink">העתקת הקישור</button> <button class="btn" id="logout">יציאה</button></div>
    </section>
  </div>
</div>
<div class="drawer hidden" id="drawer"><div id="drawerBody"></div></div>
<div class="toast" id="toast"></div>

<script>
(function () {
  const $ = (id) => document.getElementById(id);
  const id = (location.pathname.match(/([a-f0-9]{24})/) || [])[1];
  const key = new URLSearchParams(location.hash.slice(1)).get("k") || "";
  const API = location.pathname.replace(/\\/mdm\\/.*$/, "") + "/api/mdm/" + id + "/admin";
  let pass = ""; try { pass = sessionStorage.getItem("mdmPass:" + id) || ""; } catch (e) {}
  let D = null, openDev = null;
  const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const toast = (t) => { $("toast").textContent = t; $("toast").classList.add("on"); setTimeout(() => $("toast").classList.remove("on"), 2400); };
  const ago = (t) => { if (!t) return "—"; const m = Math.floor((Date.now() - t) / 60000); if (m < 1) return "עכשיו"; if (m < 60) return "לפני " + m + " דק׳"; if (m < 1440) return "לפני " + Math.floor(m / 60) + " שע׳"; return "לפני " + Math.floor(m / 1440) + " ימים"; };

  const RL = {
    no_factory_reset: "איפוס להגדרות יצרן", no_safe_boot: "מצב בטוח", no_debugging_features: "ניפוי באגים (חיבור למחשב)", no_install_apps: "התקנת אפליקציות",
    no_uninstall_apps: "הסרת אפליקציות", no_install_unknown_sources: "התקנה מקבצי APK", no_modify_accounts: "הוספה והסרה של חשבונות", no_config_wifi: "שינוי WiFi",
    no_config_bluetooth: "הגדרות בלוטות׳", no_bluetooth: "בלוטות׳ בכלל", no_usb_file_transfer: "העברת קבצים ב-USB", no_outgoing_calls: "שיחות יוצאות", no_sms: "הודעות SMS",
    no_config_date_time: "שינוי תאריך ושעה", no_adjust_volume: "שינוי עוצמת קול", no_config_vpn: "הגדרות VPN", no_physical_media: "כרטיס זיכרון ו-USB",
    no_config_mobile_networks: "הגדרות רשת סלולרית", no_share_location: "שיתוף מיקום", no_control_apps: "עצירה וניקוי של אפליקציות", no_config_tethering: "נקודה חמה",
    no_network_reset: "איפוס הגדרות רשת", no_add_user: "הוספת משתמשים", no_data_roaming: "נדידה", disallow_config_private_dns: "DNS פרטי", no_config_location: "הגדרות מיקום",
    no_config_screen_timeout: "זמן כיבוי מסך", no_config_brightness: "בהירות", no_airplane_mode: "מצב טיסה", no_set_wallpaper: "רקע", no_config_credentials: "אישורי אבטחה",
    no_unmute_microphone: "ביטול השתקת מיקרופון", no_camera_toggle: "מתג מצלמה", no_fun: "משחקים נסתרים של המערכת", no_autofill: "מילוי אוטומטי", no_printing: "הדפסה",
  };
  const CMDL = { update: "עדכון", lock: "נעילה", reboot: "הפעלה מחדש", ring: "צלצול", message: "הודעה", locate: "איתור", info: "רענון פרטים", install: "התקנת אפליקציה",
    uninstall: "הסרת אפליקציה", launch: "פתיחת אפליקציה", clear_data: "מחיקת נתוני אפליקציה", kiosk_resume: "חזרה לקיוסק", kiosk_pause: "השהיית קיוסק", wipe: "איפוס המכשיר",
    remove: "הסרה מלאה", screen_on: "הדלקת מסך", root: "פקודת רוט" };

  async function api(method, body) {
    const r = await fetch(API, { method, cache: "no-store", headers: { "x-mdm-key": key, "x-mdm-pass": pass, "content-type": "application/json" }, body: body ? JSON.stringify(body) : undefined });
    const d = await r.json().catch(() => ({}));
    if (r.status === 401) { showLogin(d.error); throw new Error(d.error || "אין גישה"); }
    if (!r.ok || !d.ok) throw new Error(d.error || "שגיאה");
    return d;
  }
  function showLogin(msg) { $("app").classList.add("hidden"); $("loginBox").classList.remove("hidden"); if (msg) $("loginMsg").textContent = msg; $("pass").focus(); }
  $("loginBtn").onclick = () => { pass = $("pass").value; try { sessionStorage.setItem("mdmPass:" + id, pass); } catch (e) {} load(); };
  $("pass").onkeydown = (e) => { if (e.key === "Enter") $("loginBtn").click(); };
  async function load() {
    if (!pass) return showLogin();
    try { D = await api("GET"); } catch (e) { return; }
    $("loginBox").classList.add("hidden"); $("app").classList.remove("hidden");
    render();
    if (openDev) renderDrawer();
  }
  async function cmd(deviceId, c, args, msg) {
    try { const d = await api("POST", { action: "command", deviceId, cmd: c, args: args || {} }); toast((msg || CMDL[c] + ": נשלח") + (d.queued > 1 ? " (" + d.queued + " מכשירים)" : "")); setTimeout(load, 1500); } catch (e) { toast(e.message); }
  }
  async function act(body, msg) { try { await api("POST", body); toast(msg || "נשמר ✓"); await load(); } catch (e) { toast(e.message); } }

  document.querySelectorAll(".tab").forEach((b) => b.onclick = () => {
    document.querySelectorAll(".tab").forEach((x) => x.setAttribute("aria-selected", String(x === b)));
    document.querySelectorAll("section[data-p]").forEach((s) => s.classList.toggle("hidden", s.dataset.p !== b.dataset.t));
  });

  const online = (d) => Date.now() - d.last_seen < 3 * 60000;
  function devName(d) { return d.label || (d.info && d.info.model) || d.device_id.slice(0, 6); }

  function render() {
    $("name").textContent = D.name; document.title = "ניהול · " + D.name;
    const devs = D.devices;
    const managed = devs.filter((d) => d.state && d.state.owner).length;
    const kiosk = devs.filter((d) => d.state && d.state.inLockTask).length;
    const pend = D.commands.filter((c) => c.status === "pending" || c.status === "sent").length;
    $("kpis").innerHTML = [["מכשירים", devs.length, devs.filter(online).length + " מחוברים עכשיו"], ["מנוהלים", managed, "שליטה מלאה"], ["בקיוסק", kiosk, "נעולים לאפליקציות"], ["פקודות ממתינות", pend, "יבוצעו בחיבור הבא"]]
      .map(([t, v, s]) => '<div class="kpi"><span>' + t + '</span><b>' + v + '</b><span>' + s + '</span></div>').join("");

    $("devList").innerHTML = devs.length ? devs.map((d) => {
      const i = d.info || {}, s = d.state || {};
      return '<div class="dev"><div class="row"><div class="grow"><h3>' + esc(devName(d)) + '</h3><div class="muted">' + esc(i.model || "") + ' · אנדרואיד ' + esc(i.android || "?") + ' · גרסה ' + esc(i.version || "?") + '</div></div>' +
        (online(d) ? '<span class="badge good">מחובר</span>' : '<span class="badge">' + ago(d.last_seen) + '</span>') + '</div>' +
        '<div class="row" style="margin-top:8px">' + (s.owner ? '<span class="badge good">מנוהל</span>' : '<span class="badge warn">לא מנוהל</span>') +
        (s.root ? '<span class="badge good">רוט</span>' : s.rootPresent ? '<span class="badge">רוט לא אושר</span>' : '') +
        (s.inLockTask ? '<span class="badge good">קיוסק פעיל</span>' : s.kioskWanted ? '<span class="badge warn">' + (s.kioskPaused ? "קיוסק מושהה" : "קיוסק לא פעיל") + '</span>' : '') +
        (d.policy ? '<span class="badge">מדיניות אישית</span>' : '') + '</div>' +
        '<div class="facts"><div>🔋 ' + (i.battery ?? "?") + '%' + (i.charging ? " ⚡" : "") + '</div><div>📶 ' + ({ wifi: "WiFi", cellular: "סלולרי", none: "אין רשת" }[i.network] || "—") + '</div><div>💾 ' + (i.storageFreeMb ? (i.storageFreeMb / 1024).toFixed(1) + "GB פנוי" : "—") + '</div><div>' + (i.screenOn ? "מסך דלוק" : "מסך כבוי") + '</div></div>' +
        '<div class="row"><button class="btn sm p" data-open="' + d.device_id + '">ניהול המכשיר</button>' +
        ['lock', 'ring', 'message', 'locate', 'update'].map((c) => '<button class="btn sm" data-cmd="' + c + '" data-dev="' + d.device_id + '">' + CMDL[c] + '</button>').join("") + '</div></div>';
    }).join("") : '<div class="card"><p class="muted">עדיין אין מכשירים. התקינו את האפליקציה (לשונית "התקנה").</p></div>';

    policyEditor($("polDefault"), D.policy, "default", allApps());

    // setup
    const pv = D.provisioning;
    if (pv && window.QRCode) {
      const data = Object.assign({}, pv); delete data.expiresAt;
      $("qrBox").innerHTML = '<div id="qr"></div><p class="muted">הברקוד תקף לגרסה האחרונה שנבנתה. ' + (D.latest ? "גרסה " + esc(D.latest.version) : "") + '</p>';
      new QRCode($("qr"), { text: JSON.stringify(data), width: 300, height: 300, correctLevel: QRCode.CorrectLevel.M });
    } else {
      $("qrBox").innerHTML = '<p class="muted">הברקוד יופיע אחרי שהבנייה הראשונה תסתיים (רעננו את העמוד).</p>';
    }
    const pkgLine = pv ? pv["android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME"] : "";
    $("adb").textContent = pkgLine ? "adb shell dpm set-device-owner " + pkgLine : "יופיע אחרי הבנייה הראשונה";
    $("dlLine").innerHTML = D.latest ? 'קישור להורדת האפליקציה (גרסה ' + esc(D.latest.version) + '): <a class="ltr" href="' + esc(D.latest.url) + '">' + esc(D.latest.url) + '</a>' : "";

    // log
    const names = Object.fromEntries(devs.map((d) => [d.device_id, devName(d)]));
    $("logList").innerHTML = D.commands.length ? D.commands.map((c) => '<div><div class="grow"><b>' + esc(CMDL[c.cmd] || c.cmd) + '</b> · ' + esc(names[c.device_id] || c.device_id) +
      ' <span class="muted">' + ago(c.created_at) + '</span>' + (c.result ? '<div class="muted">' + esc(c.result) + '</div>' : '') + '</div>' +
      ({ pending: '<span class="badge warn">ממתין</span><button class="btn sm" data-cancel="' + c.id + '">ביטול</button>', sent: '<span class="badge warn">נשלח</span>', done: '<span class="badge good">בוצע</span>', failed: '<span class="badge bad">נכשל</span>', cancelled: '<span class="badge">בוטל</span>' }[c.status] || "") + '</div>').join("") : '<p class="muted">עדיין אין פקודות.</p>';
  }

  function allApps() {
    const m = {};
    D.devices.forEach((d) => (d.apps || []).forEach((a) => { if (!m[a.p]) m[a.p] = a; }));
    return Object.values(m).sort((a, b) => a.n.localeCompare(b.n, "he"));
  }

  // ---------- policy editor (default or one device) ----------
  function policyEditor(el, P, target, apps) {
    P = JSON.parse(JSON.stringify(P || {}));
    const k = P.kiosk || {}, R = new Set(P.restrictions || []);
    const kApps = new Set(k.apps || []);
    const sw = (key, label, on, sub) => '<label class="chk"><input type="checkbox" data-k="' + key + '"' + (on ? " checked" : "") + '><span>' + label + (sub ? '<div class="muted">' + sub + '</div>' : '') + '</span></label>';
    el.innerHTML =
      '<h3>מצב קיוסק</h3>' + sw("kiosk.enabled", "<b>קיוסק פעיל</b>", k.enabled, "הטלפון נעול לאפליקציות שנבחרו בלבד. יציאה: 7 נגיעות בפינה העליונה + הקוד.") +
      '<div class="muted" style="margin:6px 0">אפליקציות בקיוסק (אחת = נפתחת אוטומטית):</div>' +
      '<input type="text" class="appSearch" placeholder="חיפוש אפליקציה…" style="margin-bottom:6px">' +
      '<div class="grid kioskApps" style="max-height:220px;overflow:auto;border:1px solid var(--line);border-radius:12px;padding:4px 10px">' +
      (apps.length ? apps.map((a) => '<label class="chk" data-name="' + esc(a.n + " " + a.p) + '"><input type="checkbox" data-app="' + esc(a.p) + '"' + (kApps.has(a.p) ? " checked" : "") + '><span>' + esc(a.n) + '<div class="muted ltr">' + esc(a.p) + '</div></span></label>').join("") : '<p class="muted">הרשימה תופיע אחרי שמכשיר ידווח.</p>') + '</div>' +
      '<div class="row" style="margin-top:6px"><input type="text" class="ltr grow extraApps" placeholder="או שמות חבילות ידנית: com.waze, com.whatsapp" value="' + esc([...kApps].filter((p) => !apps.some((a) => a.p === p)).join(", ")) + '"></div>' +
      '<div class="row" style="margin-top:6px"><input type="text" class="grow kTitle" placeholder="כותרת במסך הקיוסק (לא חובה)" value="' + esc(k.title || "") + '"></div>' +
      '<div class="grid">' + sw("kiosk.notifications", "התראות בקיוסק", k.notifications) + sw("kiosk.systemInfo", "שעה וסוללה בשורת המצב", k.systemInfo !== false) +
      sw("kiosk.globalActions", "תפריט כיבוי", k.globalActions) + sw("kiosk.keyguard", "מסך נעילה רגיל", k.keyguard) + sw("kiosk.overview", "מעבר בין אפליקציות", k.overview) +
      sw("kiosk.autoRelaunch", "פתיחה חוזרת אוטומטית (אפליקציה אחת)", k.autoRelaunch !== false) + '</div>' +
      '<h3>הגבלות</h3><p class="hint">מה שמסומן <b>חסום</b> בטלפון.</p><div class="grid">' +
      D.restrictions.map((r) => '<label class="chk"><input type="checkbox" data-r="' + r + '"' + (R.has(r) ? " checked" : "") + '><span>' + esc(RL[r] || r) + '</span></label>').join("") + '</div>' +
      '<h3>מכשיר</h3><div class="grid">' + sw("cameraDisabled", "חסימת מצלמה", P.cameraDisabled) + sw("screenCaptureDisabled", "חסימת צילומי מסך", P.screenCaptureDisabled) +
      sw("statusBarDisabled", "נעילת שורת ההתראות", P.statusBarDisabled) + sw("keyguardDisabled", "בלי מסך נעילה", P.keyguardDisabled) + sw("stayOn", "מסך דולק בזמן טעינה", P.stayOn) +
      sw("autoGrant", "אישור הרשאות אוטומטי לאפליקציות", P.autoGrant !== false) + sw("autoUpdate", "עדכון אוטומטי של האפליקציה", P.autoUpdate !== false) + sw("useRoot", "שימוש ברוט (אם יש)", P.useRoot !== false) + '</div>' +
      '<div class="row" style="margin-top:8px"><div class="grow"><div class="muted">הודעה במסך הנעילה</div><input type="text" class="lockMsg" value="' + esc(P.lockMessage || "") + '" placeholder="למשל: אם נמצא, נא להתקשר…"></div>' +
      '<div style="width:150px"><div class="muted">בדיקה כל (שניות)</div><input type="number" class="poll" min="5" max="600" value="' + (P.pollSeconds || 20) + '"></div></div>' +
      '<div class="row" style="margin-top:14px">' + (target === "default"
        ? '<button class="btn p save" data-t="default">שמירה</button><button class="btn save" data-t="all">שמירה לכל המכשירים</button>'
        : '<button class="btn p save" data-t="' + target + '">שמירה למכשיר</button><button class="btn reset">חזרה למדיניות הכללית</button>') + '</div>';
    el.querySelector(".appSearch").oninput = (e) => { const q = e.target.value.trim().toLowerCase(); el.querySelectorAll(".kioskApps label").forEach((l) => l.classList.toggle("hidden", !!q && !l.dataset.name.toLowerCase().includes(q))); };
    el.querySelectorAll(".save").forEach((b) => b.onclick = () => {
      const g = (s) => { const x = el.querySelector('[data-k="' + s + '"]'); return x ? x.checked : false; };
      const chosen = [...el.querySelectorAll("[data-app]:checked")].map((x) => x.dataset.app).concat(el.querySelector(".extraApps").value.split(/[\\s,]+/).filter(Boolean));
      const pol = {
        kiosk: { enabled: g("kiosk.enabled"), apps: [...new Set(chosen)], title: el.querySelector(".kTitle").value, notifications: g("kiosk.notifications"), systemInfo: g("kiosk.systemInfo"),
          globalActions: g("kiosk.globalActions"), keyguard: g("kiosk.keyguard"), overview: g("kiosk.overview"), autoRelaunch: g("kiosk.autoRelaunch") },
        restrictions: [...el.querySelectorAll("[data-r]:checked")].map((x) => x.dataset.r),
        cameraDisabled: g("cameraDisabled"), screenCaptureDisabled: g("screenCaptureDisabled"), statusBarDisabled: g("statusBarDisabled"), keyguardDisabled: g("keyguardDisabled"),
        stayOn: g("stayOn"), autoGrant: g("autoGrant"), autoUpdate: g("autoUpdate"), useRoot: g("useRoot"),
        lockMessage: el.querySelector(".lockMsg").value, pollSeconds: +el.querySelector(".poll").value || 20,
      };
      if (pol.kiosk.enabled && !pol.kiosk.apps.length) return toast("בחרו לפחות אפליקציה אחת לקיוסק");
      if (b.dataset.t === "all" && !confirm("להחיל על כל המכשירים (גם על מכשירים עם מדיניות אישית)?")) return;
      act({ action: "policy", deviceId: b.dataset.t, policy: pol }, "נשמר ✓ יגיע לטלפון תוך כמה שניות");
    });
    const rs = el.querySelector(".reset");
    if (rs) rs.onclick = () => act({ action: "resetPolicy", deviceId: target }, "המכשיר חזר למדיניות הכללית");
  }

  // ---------- one device ----------
  function renderDrawer() {
    const d = D.devices.find((x) => x.device_id === openDev);
    if (!d) { closeDrawer(); return; }
    const i = d.info || {}, s = d.state || {};
    const loc = d.location;
    const b = $("drawerBody");
    b.innerHTML = '<div class="row"><h2 class="grow" style="margin:0">' + esc(devName(d)) + '</h2><button class="btn" id="dClose">סגירה</button></div>' +
      '<p class="muted">' + esc(i.model || "") + ' · אנדרואיד ' + esc(i.android || "?") + ' · גרסת אפליקציה ' + esc(i.version || "?") + ' · ' + (online(d) ? "מחובר עכשיו" : "נראה " + ago(d.last_seen)) + '</p>' +
      '<div class="card"><h2>פעולות</h2><div class="row">' +
      ['lock', 'ring', 'screen_on', 'message', 'locate', 'info', 'update', 'reboot', 'kiosk_pause', 'kiosk_resume'].map((c) => '<button class="btn sm" data-cmd="' + c + '" data-dev="' + d.device_id + '">' + CMDL[c] + '</button>').join("") + '</div>' +
      '<h3>התקנת אפליקציה מקישור</h3><div class="row"><input type="text" class="ltr grow" id="instUrl" placeholder="https://…/app.apk"><button class="btn" id="instBtn">התקנה</button></div>' +
      (s.root ? '<h3>פקודת רוט</h3><div class="row"><input type="text" class="ltr grow" id="rootCmd" placeholder="למשל: settings put global ..."><button class="btn" id="rootBtn">הרצה</button></div>' : '') +
      '<h3 style="color:var(--bad)">פעולות מסוכנות</h3><div class="row"><button class="btn d" id="removeBtn">הסרה מלאה מרחוק</button><button class="btn d" id="wipeBtn">איפוס המכשיר</button></div>' +
      '<p class="muted">הסרה מלאה: כל ההגבלות והקיוסק משתחררים והאפליקציה מוסרת (עם רוט: בשקט; בלי רוט: בטלפון יופיע אישור).</p></div>' +
      '<div class="card"><h2>מצב</h2><div class="facts">' +
      [["מנוהל", s.owner ? "כן" : "לא"], ["רוט", s.root ? "כן" : s.rootPresent ? "יש, לא אושר" : "אין"], ["קיוסק", s.inLockTask ? "פעיל" : s.kioskWanted ? (s.kioskPaused ? "מושהה" : "לא פעיל") : "כבוי"],
       ["הגבלות פעילות", s.restrictions ?? "—"], ["סוללה", (i.battery ?? "?") + "%" + (i.charging ? " בטעינה" : "")], ["רשת", i.network || "—"], ["אחסון פנוי", i.storageFreeMb ? (i.storageFreeMb / 1024).toFixed(1) + "GB" : "—"],
       ["זיכרון", i.ramMb ? (i.ramMb / 1024).toFixed(1) + "GB" : "—"], ["מספר סידורי", i.serial || "—"], ["IP", d.ip || "—"], ["נרשם", new Date(d.first_seen).toLocaleDateString("he-IL")]]
        .map(([a, v]) => '<div><span class="muted">' + a + ':</span> ' + esc(v) + '</div>').join("") + '</div>' +
      (loc && loc.lat ? '<p>📍 <a target="_blank" rel="noopener" href="https://maps.google.com/?q=' + loc.lat + ',' + loc.lng + '">מיקום אחרון במפה</a> <span class="muted">(±' + (loc.acc || "?") + 'מ׳, ' + ago(loc.t) + ')</span></p>' : '<p class="muted">אין מיקום. לחצו "איתור".</p>') +
      '<div class="row"><button class="btn sm" id="labelBtn">שינוי שם</button><button class="btn sm d" id="forgetBtn">מחיקה מהרשימה</button></div></div>' +
      '<div class="card"><h2>מדיניות ' + (d.policy ? "אישית" : "(כללית)") + '</h2><div id="polDev"></div></div>' +
      '<div class="card"><h2>אפליקציות במכשיר (' + (d.apps || []).length + ')</h2><input type="text" id="appQ" placeholder="חיפוש…" style="margin-bottom:8px"><div class="list" id="appList"></div></div>';
    $("dClose").onclick = closeDrawer;
    policyEditor($("polDev"), d.policy || D.policy, d.device_id, (d.apps || []).slice().sort((a, b) => a.n.localeCompare(b.n, "he")));
    const P = d.policy || D.policy;
    const hidden = new Set(P.hidden || []), susp = new Set(P.suspended || []), blk = new Set(P.blockUninstall || []);
    const drawApps = () => {
      const q = $("appQ").value.trim().toLowerCase();
      $("appList").innerHTML = (d.apps || []).filter((a) => !q || (a.n + a.p).toLowerCase().includes(q)).sort((a, b) => a.n.localeCompare(b.n, "he")).slice(0, 200).map((a) =>
        '<div><div class="grow">' + esc(a.n) + (a.s ? ' <span class="badge">מערכת</span>' : '') + '<div class="muted ltr">' + esc(a.p) + '</div></div>' +
        '<label class="chk" title="מוסתרת"><input type="checkbox" data-hide="' + esc(a.p) + '"' + (hidden.has(a.p) ? " checked" : "") + '>הסתרה</label>' +
        '<label class="chk" title="מושבתת"><input type="checkbox" data-susp="' + esc(a.p) + '"' + (susp.has(a.p) ? " checked" : "") + '>השבתה</label>' +
        '<label class="chk" title="אי אפשר להסיר"><input type="checkbox" data-blk="' + esc(a.p) + '"' + (blk.has(a.p) ? " checked" : "") + '>נגד הסרה</label>' +
        '<button class="btn sm" data-cmd="launch" data-pkg="' + esc(a.p) + '" data-dev="' + d.device_id + '">פתיחה</button>' +
        (a.s ? '' : '<button class="btn sm d" data-cmd="uninstall" data-pkg="' + esc(a.p) + '" data-dev="' + d.device_id + '">הסרה</button>') + '</div>').join("") +
        '<div><button class="btn p" id="saveApps">שמירת הסתרה / השבתה / הגנה</button></div>';
      $("saveApps").onclick = () => {
        const pick = (attr) => [...$("appList").querySelectorAll("[data-" + attr + "]:checked")].map((x) => x.dataset[attr]);
        act({ action: "policy", deviceId: d.device_id, policy: { hidden: pick("hide"), suspended: pick("susp"), blockUninstall: pick("blk") } }, "נשמר ✓");
      };
    };
    $("appQ").oninput = drawApps; drawApps();
    $("instBtn").onclick = () => { const u = $("instUrl").value.trim(); if (u) cmd(d.device_id, "install", { url: u }); };
    if ($("rootBtn")) $("rootBtn").onclick = () => { const c = $("rootCmd").value.trim(); if (c) cmd(d.device_id, "root", { cmd: c }); };
    $("removeBtn").onclick = () => { if (confirm("להסיר את הניהול ואת האפליקציה מהמכשיר הזה? כל ההגבלות ישתחררו.")) cmd(d.device_id, "remove"); };
    $("wipeBtn").onclick = () => { if (prompt('איפוס מוחק את כל המידע בטלפון! כדי לאשר כתבו: איפוס') === "איפוס") cmd(d.device_id, "wipe"); };
    $("labelBtn").onclick = () => { const l = prompt("שם למכשיר", d.label || ""); if (l !== null) act({ action: "label", deviceId: d.device_id, label: l }); };
    $("forgetBtn").onclick = () => { if (confirm("למחוק את המכשיר מהרשימה? (אם האפליקציה עדיין מותקנת הוא יופיע שוב)")) { act({ action: "removeDevice", deviceId: d.device_id }); closeDrawer(); } };
  }
  function closeDrawer() { openDev = null; $("drawer").classList.add("hidden"); }
  $("drawer").onclick = (e) => { if (e.target === $("drawer")) closeDrawer(); };

  document.body.addEventListener("click", (e) => {
    const t = e.target.closest("[data-open],[data-cmd],[data-all],[data-cancel]");
    if (!t) return;
    if (t.dataset.open) { openDev = t.dataset.open; $("drawer").classList.remove("hidden"); renderDrawer(); }
    else if (t.dataset.cmd) {
      const c = t.dataset.cmd, dev = t.dataset.dev;
      if (c === "message") { const txt = prompt("תוכן ההודעה"); if (txt) cmd(dev, "message", { title: "הודעה", text: txt }); }
      else if (c === "uninstall") { if (confirm("להסיר את " + t.dataset.pkg + "?")) cmd(dev, "uninstall", { pkg: t.dataset.pkg }); }
      else if (c === "reboot") { if (confirm("להפעיל מחדש את המכשיר?")) cmd(dev, c); }
      else cmd(dev, c, t.dataset.pkg ? { pkg: t.dataset.pkg } : {});
    } else if (t.dataset.all) {
      const c = t.dataset.all;
      if (c === "message") { const txt = prompt("הודעה לכל המכשירים"); if (txt) cmd("all", "message", { title: "הודעה", text: txt }); }
      else if (confirm(CMDL[c] + " לכל המכשירים?")) cmd("all", c);
    } else if (t.dataset.cancel) act({ action: "cancelCommand", id: +t.dataset.cancel }, "בוטל");
  });

  $("savePin").onclick = () => { const p = $("newPin").value.trim(); if (!/^\\d{4,8}$/.test(p)) return toast("4 עד 8 ספרות"); $("newPin").value = ""; act({ action: "pin", pin: p }, "הקוד יתעדכן בכל המכשירים"); };
  $("savePass").onclick = async () => {
    const np = $("newPass").value; if (np.length < 4) return toast("לפחות 4 תווים");
    try { await api("POST", { action: "password", newPassword: np }); pass = np; try { sessionStorage.setItem("mdmPass:" + id, np); } catch (e) {} $("newPass").value = ""; toast("הסיסמה עודכנה ✓"); } catch (e) { toast(e.message); }
  };
  $("copyLink").onclick = () => navigator.clipboard.writeText(location.href).then(() => toast("הקישור הועתק"));
  $("logout").onclick = () => { try { sessionStorage.removeItem("mdmPass:" + id); } catch (e) {} pass = ""; showLogin("יצאת. הזינו סיסמה כדי להיכנס שוב."); };

  if (!id || !key) { showLogin("יש לפתוח את הקישור המלא של מרכז השליטה (כולל החלק שאחרי #)."); return; }
  load();
  // refresh the status every 15s, but never while something is being edited
  setInterval(() => {
    const editing = openDev || !document.querySelector('section[data-p="policy"]').classList.contains("hidden") || document.activeElement.matches("input,textarea");
    if (!document.hidden && pass && !$("app").classList.contains("hidden") && !editing) load();
  }, 15000);
})();
</script>
</body>
</html>`;
}
