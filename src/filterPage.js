// The owner's control page for a website filter. The access key stays in the address after "#",
// so it is never sent to the server in the URL or kept in its logs.

export function renderFilterPage() {
  return `<!doctype html>
<html lang="he" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>מרכז שליטה · סינון</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Rubik:wght@400;500;700&display=swap" rel="stylesheet">
<style>
:root{--bg:#f2f5fa;--card:#fff;--text:#0f1a2b;--muted:#6b778a;--line:#e3e8f0;--blue:#1f5fad;--blue2:#3b82f6;--navy:#0f1a2b;--navy2:#17325c;
--good:#16a34a;--good-s:#e8f7ee;--warn:#c2760c;--warn-s:#fff4e2;--bad:#dc2626;--bad-s:#fdecef;--field:#f6f8fb}
@media (prefers-color-scheme:dark){:root{--bg:#0b1220;--card:#131c2e;--text:#e6ebf3;--muted:#8b97ab;--line:#24304a;--field:#0f1729;--good-s:#10301f;--warn-s:#33260f;--bad-s:#3a1418}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font:15px/1.55 Rubik,system-ui,sans-serif}
.top{background:linear-gradient(160deg,var(--navy),var(--navy2));color:#fff;padding:22px 16px 64px}
.top .in,.wrap{max-width:1000px;margin:0 auto}
.top h1{margin:0;font-size:22px;display:flex;gap:10px;align-items:center}
.top p{margin:4px 0 0;opacity:.8;font-size:14px}
.wrap{padding:0 16px 40px;margin-top:-46px}
.kpis{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}
@media (max-width:720px){.kpis{grid-template-columns:repeat(2,1fr)}}
.kpi{background:var(--card);border-radius:16px;padding:14px 16px;box-shadow:0 2px 10px rgba(15,26,43,.06)}
.kpi b{display:block;font-size:24px}
.kpi span{color:var(--muted);font-size:13px}
.tabs{display:flex;gap:6px;margin:18px 0 12px;overflow-x:auto;padding-bottom:2px}
.tab{border:0;background:var(--card);color:var(--text);padding:9px 16px;border-radius:999px;font:inherit;font-weight:500;cursor:pointer;white-space:nowrap;box-shadow:0 1px 4px rgba(15,26,43,.05)}
.tab[aria-selected=true]{background:var(--blue);color:#fff}
.tab .n{background:var(--bad);color:#fff;border-radius:999px;padding:0 7px;font-size:12px;margin-inline-start:6px}
.card{background:var(--card);border-radius:18px;padding:18px;box-shadow:0 2px 10px rgba(15,26,43,.06);margin-bottom:12px}
.card h2{margin:0 0 10px;font-size:17px}
.hint{color:var(--muted);font-size:13.5px;margin:0 0 10px}
.row{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
input[type=text],input[type=password],input[type=time],textarea,select{width:100%;padding:11px 12px;border:1px solid var(--line);border-radius:12px;background:var(--field);color:var(--text);font:inherit}
textarea{min-height:90px}
.ltr{direction:ltr;text-align:left}
.btn{border:0;border-radius:12px;padding:10px 16px;font:inherit;font-weight:500;cursor:pointer;background:var(--field);color:var(--text);border:1px solid var(--line)}
.btn.p{background:linear-gradient(135deg,var(--blue2),var(--blue));color:#fff;border:0}
.btn.g{background:var(--good);color:#fff;border:0}
.btn.d{background:var(--bad-s);color:var(--bad);border:0}
.btn.sm{padding:6px 11px;font-size:13px;border-radius:10px}
.chips{display:flex;flex-wrap:wrap;gap:6px}
.chip{display:inline-flex;align-items:center;gap:6px;background:var(--field);border:1px solid var(--line);border-radius:999px;padding:4px 6px 4px 12px;font-size:13.5px;direction:ltr}
.chip button{border:0;background:transparent;color:var(--muted);cursor:pointer;font-size:14px;padding:0 4px}
.badge{display:inline-block;border-radius:999px;padding:2px 9px;font-size:12px;font-weight:500}
.badge.good{background:var(--good-s);color:var(--good)}.badge.warn{background:var(--warn-s);color:var(--warn)}.badge.bad{background:var(--bad-s);color:var(--bad)}
.list>div{display:flex;gap:10px;align-items:center;padding:10px 0;border-bottom:1px solid var(--line)}
.list>div:last-child{border-bottom:0}
.grow{flex:1;min-width:0}
.muted{color:var(--muted);font-size:13px}
.dev{border:1px solid var(--line);border-radius:16px;padding:14px;margin-bottom:10px}
.dev h3{margin:0;font-size:16px}
.prot{display:grid;grid-template-columns:repeat(auto-fill,minmax(190px,1fr));gap:6px;margin-top:10px}
.prot div{font-size:13px}
.packs{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:8px}
.pack{border:1px solid var(--line);border-radius:14px;padding:10px 12px;cursor:pointer;background:var(--field)}
.pack b{display:block}.pack span{font-size:12.5px;color:var(--muted)}
.switch{display:flex;gap:10px;align-items:flex-start;padding:10px 0;border-bottom:1px solid var(--line)}
.switch:last-child{border-bottom:0}
.switch input{width:20px;height:20px;margin-top:2px;accent-color:var(--blue)}
.toast{position:fixed;bottom:18px;left:50%;transform:translateX(-50%);background:var(--navy);color:#fff;padding:10px 18px;border-radius:12px;opacity:0;transition:.25s;pointer-events:none;z-index:9}
.toast.on{opacity:1}
.hidden{display:none!important}
.alert{border-radius:14px;padding:12px 14px;margin-bottom:10px;font-size:14px}
.alert.bad{background:var(--bad-s);color:var(--bad)}.alert.warn{background:var(--warn-s);color:var(--warn)}
.days{display:flex;gap:4px;flex-wrap:wrap}.days label{display:flex;gap:3px;align-items:center;font-size:13px;background:var(--field);border:1px solid var(--line);border-radius:8px;padding:3px 7px}
</style>
</head>
<body>
<div class="top"><div class="in">
  <h1>🛡️ <span id="name">מרכז שליטה</span></h1>
  <p>ניהול הסינון: אתרים, בקשות, מכשירים והגדרות. השינויים מגיעים לטלפונים תוך כמה דקות.</p>
</div></div>
<div class="wrap">
  <div id="noKey" class="card hidden"><h2>אין גישה</h2><p class="hint">יש לפתוח את הקישור המלא של מרכז השליטה (כולל החלק שאחרי הסימן #).</p></div>
  <div id="app" class="hidden">
    <div class="kpis" id="kpis"></div>
    <div class="tabs" role="tablist">
      <button class="tab" data-t="overview" aria-selected="true">סקירה</button>
      <button class="tab" data-t="sites">אתרים</button>
      <button class="tab" data-t="requests">בקשות<span class="n hidden" id="reqN"></span></button>
      <button class="tab" data-t="devices">מכשירים</button>
      <button class="tab" data-t="settings">הגדרות</button>
    </div>

    <section data-p="overview">
      <div id="alerts"></div>
      <div class="card"><h2>מה נחסם לאחרונה</h2><p class="hint">מכל המכשירים. לחיצה על "אישור" מוסיפה את האתר לרשימה המאושרת.</p><div class="list" id="ovBlocked"></div></div>
    </section>

    <section data-p="sites" class="hidden">
      <div class="card">
        <h2 id="listTitle">אתרים מאושרים</h2>
        <p class="hint" id="listHint">רק האתרים האלה (וכל תתי-האתרים שלהם) נפתחים במכשירים.</p>
        <div class="row"><input type="text" id="addInput" class="ltr" placeholder="example.com או כמה כתובות מופרדות בפסיק/רווח" style="flex:1"><button class="btn p" id="addBtn">הוספה</button></div>
        <div class="row" style="margin:10px 0"><input type="text" id="filterInput" placeholder="חיפוש ברשימה…" style="flex:1"></div>
        <div class="chips" id="chips"></div>
        <p class="muted" id="listCount" style="margin-top:8px"></p>
      </div>
      <div class="card only-allow">
        <h2>חבילות מוכנות</h2>
        <p class="hint">לחיצה מוסיפה את כל הכתובות שצריך כדי שהשירות יעבוד.</p>
        <div class="packs" id="packs"></div>
      </div>
      <div class="card only-allow">
        <h2>חסימה גם בתוך המאושרים <span class="muted">(לא חובה)</span></h2>
        <p class="hint">למשל לאשר google.com אבל לחסום youtube.com. שורה לכל כתובת.</p>
        <textarea id="blockExtra" class="ltr"></textarea>
        <button class="btn p" id="saveBlockExtra" style="margin-top:8px">שמירה</button>
      </div>
    </section>

    <section data-p="requests" class="hidden">
      <div class="card"><h2>בקשות לאישור אתרים</h2><p class="hint">נשלחות מדף החסימה בטלפון.</p><div class="list" id="reqList"></div></div>
    </section>

    <section data-p="devices" class="hidden"><div id="devList"></div></section>

    <section data-p="settings" class="hidden">
      <div class="card">
        <h2>סוג הסינון</h2>
        <div class="switch"><input type="radio" name="mode" value="allow" id="mAllow"><label for="mAllow"><b>רק אתרים מאושרים</b><div class="muted">הכי בטוח: כל מה שלא ברשימה חסום.</div></label></div>
        <div class="switch"><input type="radio" name="mode" value="block" id="mBlock"><label for="mBlock"><b>הכל פתוח חוץ מרשימת חסימה</b><div class="muted">גמיש: רק האתרים שברשימה חסומים.</div></label></div>
      </div>
      <div class="card">
        <h2>אפשרויות</h2>
        <div class="switch"><input type="checkbox" id="oPage"><label for="oPage"><b>דף חסימה מעל הדפדפן</b><div class="muted">כשנכנסים לאתר חסום מוצג דף שלנו עם "בקשת אישור".</div></label></div>
        <div class="switch"><input type="checkbox" id="oLocal"><label for="oLocal"><b>פתיחת אתר בטלפון עם הקוד</b><div class="muted">מי שיודע את הקוד יכול לפתוח אתר במכשיר מסוים מדף החסימה.</div></label></div>
        <div class="switch"><input type="checkbox" id="oAll"><label for="oAll"><b style="color:var(--bad)">חסימת כל הגלישה עכשיו</b><div class="muted">חירום: שום אתר לא נפתח (חוץ ממה שהטלפון צריך כדי לעבוד).</div></label></div>
        <label class="muted" for="oMsg" style="display:block;margin-top:10px">הודעה בדף החסימה</label>
        <input type="text" id="oMsg" maxlength="300" placeholder="ברירת מחדל: הגלישה מוגבלת לאתרים מאושרים בלבד…">
        <button class="btn p" id="saveOpts" style="margin-top:12px">שמירת האפשרויות</button>
      </div>
      <div class="card">
        <h2>שעות גלישה</h2>
        <p class="hint">מחוץ לשעות האלה הגלישה חסומה. בלי שעות = תמיד מותר.</p>
        <div id="hours"></div>
        <div class="row" style="margin-top:8px"><button class="btn" id="addHours">+ הוספת טווח שעות</button><button class="btn p" id="saveHours">שמירה</button></div>
      </div>
      <div class="card">
        <h2>השהיה מרחוק</h2>
        <p class="hint" id="pauseState">הסינון לא מושהה.</p>
        <div class="row"><button class="btn" data-pause="30">30 דקות</button><button class="btn" data-pause="60">שעה</button><button class="btn" data-pause="180">3 שעות</button><button class="btn d" data-pause="0">ביטול השהיה</button></div>
      </div>
      <div class="card">
        <h2>אפליקציות בלי סינון</h2>
        <p class="hint">אפליקציות שעוברות בלי סינון (למשל ניווט או בנק). הרשימה נלקחת מהמכשירים.</p>
        <div id="bypass" class="chips"></div>
        <button class="btn p" id="saveBypass" style="margin-top:10px">שמירה</button>
      </div>
      <div class="card">
        <h2>הודעה לטלפונים</h2>
        <p class="hint">מוצגת במסך הראשי של האפליקציה.</p>
        <div class="row"><input type="text" id="msg" maxlength="400" style="flex:1" placeholder="למשל: אתרים חדשים אושרו"><button class="btn p" id="saveMsg">שמירה</button></div>
      </div>
      <div class="card">
        <h2>קוד הסינון</h2>
        <p class="hint">הקוד נדרש לכיבוי, השהיה והסרה בטלפון. קוד חדש יגיע לכל המכשירים.</p>
        <div class="row"><input type="password" id="newPin" class="ltr" inputmode="numeric" maxlength="8" placeholder="4–8 ספרות" style="flex:1"><button class="btn p" id="savePin">עדכון הקוד</button></div>
      </div>
      <div class="card">
        <h2>הקישור למרכז השליטה</h2>
        <p class="hint">שמרו אותו. מי שיש לו את הקישור יכול לנהל את הסינון.</p>
        <button class="btn" id="copyLink">העתקת הקישור</button>
      </div>
    </section>
  </div>
</div>
<div class="toast" id="toast"></div>

<script>
(function () {
  const $ = (id) => document.getElementById(id);
  const id = (location.pathname.match(/([a-f0-9]{24})/) || [])[1];
  const key = new URLSearchParams(location.hash.slice(1)).get("k") || "";
  const API = location.pathname.replace(/\\/filter\\/.*$/, "") + "/api/filter/" + id + "/admin";
  let D = null;
  const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const toast = (t) => { $("toast").textContent = t; $("toast").classList.add("on"); setTimeout(() => $("toast").classList.remove("on"), 2200); };
  const ago = (t) => { if (!t) return "—"; const m = Math.floor((Date.now() - t) / 60000); if (m < 1) return "עכשיו"; if (m < 60) return "לפני " + m + " דק׳"; if (m < 1440) return "לפני " + Math.floor(m / 60) + " שע׳"; return "לפני " + Math.floor(m / 1440) + " ימים"; };
  const hhmm = (m) => String(Math.floor(m / 60)).padStart(2, "0") + ":" + String(m % 60).padStart(2, "0");

  const PACKS = [
    { n: "התראות של אפליקציות", d: "חובה כמעט תמיד: התראות מ-WhatsApp, Gmail ועוד", l: ["mtalk.google.com", "fcm.googleapis.com", "android.googleapis.com", "firebaseinstallations.googleapis.com", "fcmtoken.googleapis.com"] },
    { n: "WhatsApp", d: "הודעות, שיחות ותמונות", l: ["whatsapp.com", "whatsapp.net", "wa.me"] },
    { n: "Gmail", d: "דואר של גוגל", l: ["mail.google.com", "gmail.com", "accounts.google.com", "googleusercontent.com", "gstatic.com", "inbox.google.com", "ssl.gstatic.com"] },
    { n: "חשבון Google", d: "כניסה והתחברות", l: ["accounts.google.com", "accounts.youtube.com", "gstatic.com", "googleapis.com"] },
    { n: "Google Play", d: "התקנה ועדכון אפליקציות", l: ["play.google.com", "android.clients.google.com", "play.googleapis.com", "googleapis.com", "gvt1.com", "gvt2.com", "ggpht.com", "gstatic.com"] },
    { n: "מפות גוגל", d: "מפות וניווט", l: ["maps.google.com", "maps.googleapis.com", "maps.gstatic.com", "googleapis.com", "gstatic.com"] },
    { n: "Waze", d: "ניווט", l: ["waze.com", "waze.co.il", "googleapis.com"] },
    { n: "Moovit ותחבורה ציבורית", d: "אוטובוסים ורכבת", l: ["moovitapp.com", "moovit.com", "rail.co.il", "bus.co.il", "egged.co.il"] },
    { n: "בנקים", d: "הבנקים הגדולים", l: ["bankhapoalim.co.il", "leumi.co.il", "discountbank.co.il", "mizrahi-tefahot.co.il", "fibi.co.il", "bankjerusalem.co.il", "mercantile.co.il", "onezero.co.il"] },
    { n: "קופות חולים", d: "תורים ומרשמים", l: ["clalit.co.il", "maccabi4u.co.il", "meuhedet.co.il", "leumit.co.il"] },
    { n: "ממשלה ודואר", d: "gov.il, ביטוח לאומי, דואר", l: ["gov.il", "btl.gov.il", "israelpost.co.il", "mygov.gov.il"] },
    { n: "SMT", d: "smti.uk ועכשיו סלומון", l: ["smti.uk"] },
  ];

  async function api(method, body) {
    const r = await fetch(API, { method, headers: { "x-filter-key": key, "content-type": "application/json" }, body: body ? JSON.stringify(body) : undefined, cache: "no-store" });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) throw new Error(d.error || "שגיאה");
    return d;
  }
  async function load() {
    try { D = await api("GET"); } catch (e) { $("noKey").classList.remove("hidden"); $("noKey").querySelector("p").textContent = e.message; return; }
    $("app").classList.remove("hidden");
    render();
  }
  async function save(rules, msg) {
    try { const d = await api("POST", { action: "save", rules }); D.rules = d.rules; render(); toast(msg || "נשמר ✓"); } catch (e) { toast(e.message); }
  }
  async function act(body, msg) {
    try { const d = await api("POST", body); if (d.rules) D.rules = d.rules; await load(); toast(msg || "בוצע ✓"); } catch (e) { toast(e.message); }
  }

  // ---------- tabs ----------
  document.querySelectorAll(".tab").forEach((b) => b.onclick = () => {
    document.querySelectorAll(".tab").forEach((x) => x.setAttribute("aria-selected", String(x === b)));
    document.querySelectorAll("section[data-p]").forEach((s) => s.classList.toggle("hidden", s.dataset.p !== b.dataset.t));
  });

  function render() {
    const R = D.rules, devs = D.devices;
    $("name").textContent = D.name;
    document.title = "מרכז שליטה · " + D.name;
    const online = devs.filter((d) => Date.now() - d.last_seen < 40 * 60000);
    const off = devs.filter((d) => !d.running);
    const pending = D.requests.filter((r) => r.status === "pending");
    const blocked = devs.reduce((a, d) => a + (d.blocked_today || 0), 0);
    $("kpis").innerHTML = [
      ["מכשירים", devs.length, online.length + " מחוברים"],
      ["הסינון כבוי", off.length, off.length ? "דורש טיפול" : "הכל תקין ✓"],
      ["נחסמו היום", blocked, "בכל המכשירים"],
      ["בקשות ממתינות", pending.length, "לאישור אתרים"],
    ].map(([t, v, s]) => '<div class="kpi"><span>' + t + '</span><b>' + v + '</b><span>' + s + '</span></div>').join("");
    $("reqN").textContent = pending.length; $("reqN").classList.toggle("hidden", !pending.length);

    // alerts
    const al = [];
    devs.forEach((d) => {
      const n = esc(d.label || d.model);
      if (!d.running) al.push('<div class="alert bad">⚠️ הסינון כבוי במכשיר <b>' + n + '</b>' + (d.stopped_at ? " מאז " + ago(d.stopped_at) : "") + '</div>');
      else if (Date.now() - d.last_seen > 3 * 3600000) al.push('<div class="alert warn">המכשיר <b>' + n + '</b> לא דיווח ' + ago(d.last_seen) + ' (כבוי, בלי אינטרנט, או שהאפליקציה הוסרה)</div>');
      if (d.protections && d.protections.admin === false) al.push('<div class="alert warn">במכשיר <b>' + n + '</b> אין הגנה מהסרה</div>');
    });
    if (R.blockAll) al.unshift('<div class="alert bad">כל הגלישה חסומה כרגע (מצב חירום). לביטול: הגדרות ← אפשרויות.</div>');
    $("alerts").innerHTML = al.join("");

    // recent blocked across devices
    const agg = {};
    devs.forEach((d) => (d.recent || []).forEach((x) => { if (!agg[x.d] || agg[x.d].t < x.t) agg[x.d] = { t: x.t, dev: d.label || d.model }; }));
    const rows = Object.entries(agg).sort((a, b) => b[1].t - a[1].t).slice(0, 40);
    $("ovBlocked").innerHTML = rows.length ? rows.map(([dom, x]) => '<div><div class="grow"><b class="ltr" style="display:inline-block">' + esc(dom) + '</b><div class="muted">' + esc(x.dev) + ' · ' + ago(x.t) + '</div></div><button class="btn sm g" data-allow="' + esc(dom) + '">אישור</button></div>').join("") : '<p class="muted">עדיין לא נחסם כלום.</p>';

    // sites
    const allowMode = R.mode !== "block";
    document.querySelectorAll(".only-allow").forEach((e) => e.classList.toggle("hidden", !allowMode));
    $("listTitle").textContent = allowMode ? "אתרים מאושרים" : "אתרים חסומים";
    $("listHint").textContent = allowMode ? "רק האתרים האלה (וכל תתי-האתרים שלהם) נפתחים במכשירים." : "האתרים האלה (וכל תתי-האתרים שלהם) חסומים. כל השאר פתוח.";
    renderChips();
    $("packs").innerHTML = PACKS.map((p, i) => '<div class="pack" data-pack="' + i + '"><b>' + esc(p.n) + '</b><span>' + esc(p.d) + '</span></div>').join("");
    $("blockExtra").value = allowMode ? (R.block || []).join("\\n") : "";

    // requests
    $("reqList").innerHTML = D.requests.length ? D.requests.map((r) => '<div><div class="grow"><b class="ltr" style="display:inline-block">' + esc(r.domain) + '</b> ' +
      (r.status === "pending" ? '<span class="badge warn">ממתין</span>' : r.status === "approved" ? '<span class="badge good">אושר</span>' : '<span class="badge bad">נדחה</span>') +
      '<div class="muted">' + esc(r.model) + ' · ' + ago(r.created_at) + (r.note ? ' · "' + esc(r.note) + '"' : '') + '</div></div>' +
      (r.status === "pending" ? '<button class="btn sm g" data-approve="' + r.id + '">אישור</button><button class="btn sm d" data-deny="' + r.id + '">דחייה</button>' : '') + '</div>').join("") : '<p class="muted">אין בקשות.</p>';

    // devices
    const pr = (v, good, bad) => v ? '<span class="badge good">' + good + '</span>' : '<span class="badge warn">' + bad + '</span>';
    $("devList").innerHTML = devs.length ? devs.map((d) => {
      const p = d.protections || {};
      const ao = p.alwaysOn === "lockdown" ? '<span class="badge good">תמידי + חסימה</span>' : p.alwaysOn === "on" ? '<span class="badge warn">תמידי בלי חסימה</span>' : p.alwaysOn === "off" ? '<span class="badge warn">כבוי</span>' : '<span class="badge">לא ידוע</span>';
      return '<div class="card dev"><div class="row"><div class="grow"><h3>' + esc(d.label || d.model) + '</h3><div class="muted">' + esc(d.model) + ' · אנדרואיד ' + esc(d.android) + ' · גרסה ' + esc(d.version) + ' · דיווח ' + ago(d.last_seen) + '</div></div>' +
        (d.running ? (d.paused ? '<span class="badge warn">מושהה</span>' : '<span class="badge good">הסינון פעיל</span>') : '<span class="badge bad">הסינון כבוי</span>') + '</div>' +
        '<div class="prot"><div>נחסמו היום: <b>' + (d.blocked_today || 0) + '</b></div><div>סה״כ נחסמו: <b>' + (d.blocked_total || 0) + '</b></div><div>מאושרים היום: <b>' + (d.allowed_today || 0) + '</b></div>' +
        '<div>הגנה מהסרה: ' + pr(p.admin, "פעילה", "אין") + '</div><div>מצב חזק: ' + pr(p.owner, "פעיל", "לא") + '</div><div>VPN תמידי: ' + ao + '</div>' +
        '<div>דף חסימה: ' + pr(p.overlay, "מאושר", "לא מאושר") + '</div><div>דף חסימה חכם: ' + pr(p.usage, "פעיל", "לא") + '</div><div>פועל ברקע: ' + pr(p.battery, "כן", "חיסכון סוללה") + '</div></div>' +
        '<details style="margin-top:10px"><summary class="muted" style="cursor:pointer">נחסמו לאחרונה במכשיר (' + (d.recent || []).length + ')</summary><div class="list">' +
        (d.recent || []).slice(0, 25).map((x) => '<div><div class="grow ltr" style="text-align:left">' + esc(x.d) + ' <span class="muted">' + ago(x.t) + '</span></div><button class="btn sm g" data-allow="' + esc(x.d) + '">אישור</button></div>').join("") + '</div></details>' +
        '<div class="row" style="margin-top:10px"><button class="btn sm" data-label="' + esc(d.device_id) + '">שינוי שם</button><button class="btn sm d" data-remove="' + esc(d.device_id) + '">הסרה מהרשימה</button></div></div>';
    }).join("") : '<div class="card"><p class="muted">עדיין אין מכשירים. התקינו את האפליקציה בטלפון ופתחו אותה.</p></div>';

    // settings
    $("mAllow").checked = allowMode; $("mBlock").checked = !allowMode;
    $("oPage").checked = R.blockPage !== false; $("oLocal").checked = R.allowLocalPin !== false; $("oAll").checked = !!R.blockAll;
    $("oMsg").value = R.blockMessage || "";
    $("msg").value = D.message || "";
    $("pauseState").textContent = R.pauseUntil > Date.now() ? "מושהה עד " + new Date(R.pauseUntil).toLocaleTimeString("he-IL", { hour: "2-digit", minute: "2-digit" }) : "הסינון לא מושהה.";
    renderHours(R.hours || []);
    const apps = {};
    devs.forEach((d) => (d.apps || []).forEach((a) => apps[a.p] = a.n));
    const by = new Set(R.bypassApps || []);
    $("bypass").innerHTML = Object.keys(apps).length ? Object.entries(apps).sort((a, b) => a[1].localeCompare(b[1], "he")).map(([p, n]) =>
      '<label class="chip" style="direction:rtl;padding:4px 12px"><input type="checkbox" value="' + esc(p) + '"' + (by.has(p) ? " checked" : "") + '> ' + esc(n) + '</label>').join("") : '<p class="muted">הרשימה תופיע אחרי שמכשיר ידווח.</p>';
  }

  function renderChips() {
    const R = D.rules, list = R.mode === "block" ? (R.block || []) : (R.allow || []);
    const q = $("filterInput").value.trim().toLowerCase();
    const shown = list.filter((d) => !q || d.includes(q));
    $("chips").innerHTML = shown.map((d) => '<span class="chip">' + esc(d) + '<button title="הסרה" data-del="' + esc(d) + '">✕</button></span>').join("") || '<p class="muted">הרשימה ריקה.</p>';
    $("listCount").textContent = list.length + " כתובות";
  }
  $("filterInput").oninput = renderChips;

  const listKey = () => D.rules.mode === "block" ? "block" : "allow";
  function addDomains(items) {
    const k = listKey(), cur = new Set(D.rules[k] || []);
    let n = 0;
    items.forEach((x) => { const d = String(x).trim().toLowerCase().replace(/^[a-z]+:\\/\\//, "").replace(/[/?#].*$/, "").replace(/^www\\./, ""); if (d && !cur.has(d)) { cur.add(d); n++; } });
    if (!n) return toast("כבר ברשימה");
    save({ [k]: [...cur] }, "נוספו " + n + " כתובות ✓");
  }
  $("addBtn").onclick = () => { const v = $("addInput").value.split(/[\\s,]+/).filter(Boolean); if (!v.length) return; $("addInput").value = ""; addDomains(v); };
  $("addInput").onkeydown = (e) => { if (e.key === "Enter") $("addBtn").click(); };
  $("saveBlockExtra").onclick = () => save({ block: $("blockExtra").value.split(/[\\s,]+/).filter(Boolean) });

  document.body.addEventListener("click", (e) => {
    const t = e.target.closest("[data-del],[data-pack],[data-allow],[data-approve],[data-deny],[data-label],[data-remove],[data-pause]");
    if (!t) return;
    if (t.dataset.del) { const k = listKey(); save({ [k]: (D.rules[k] || []).filter((x) => x !== t.dataset.del) }, "הוסר ✓"); }
    else if (t.dataset.pack) addDomains(PACKS[+t.dataset.pack].l);
    else if (t.dataset.allow) act({ action: "allowDomain", domain: t.dataset.allow }, "האתר אושר ✓");
    else if (t.dataset.approve) act({ action: "approve", id: +t.dataset.approve }, "אושר ✓ האתר ייפתח בטלפון תוך כמה דקות");
    else if (t.dataset.deny) act({ action: "deny", id: +t.dataset.deny }, "נדחה");
    else if (t.dataset.label) { const l = prompt("שם למכשיר (למשל: הטלפון של יוסי)"); if (l !== null) act({ action: "label", deviceId: t.dataset.label, label: l }); }
    else if (t.dataset.remove) { if (confirm("להסיר את המכשיר מהרשימה? אם הוא עדיין מותקן, הוא יופיע שוב בדיווח הבא.")) act({ action: "removeDevice", deviceId: t.dataset.remove }); }
    else if (t.dataset.pause !== undefined) { const m = +t.dataset.pause; save({ pauseUntil: m ? Date.now() + m * 60000 : 0 }, m ? "הסינון יושהה בכל המכשירים" : "ההשהיה בוטלה"); }
  });

  document.querySelectorAll("input[name=mode]").forEach((r) => r.onchange = () => save({ mode: r.value }, r.value === "block" ? "מצב רשימת חסימה" : "מצב אתרים מאושרים בלבד"));
  $("saveOpts").onclick = () => {
    if ($("oAll").checked && !D.rules.blockAll && !confirm("לחסום את כל הגלישה בכל המכשירים?")) return;
    save({ blockPage: $("oPage").checked, allowLocalPin: $("oLocal").checked, blockAll: $("oAll").checked, blockMessage: $("oMsg").value });
  };
  $("saveMsg").onclick = () => act({ action: "message", text: $("msg").value }, "ההודעה תוצג בטלפונים");
  $("savePin").onclick = () => { const p = $("newPin").value.trim(); if (!/^\\d{4,8}$/.test(p)) return toast("4 עד 8 ספרות"); $("newPin").value = ""; act({ action: "pin", pin: p }, "הקוד יתעדכן בכל המכשירים"); };
  $("saveBypass").onclick = () => save({ bypassApps: [...$("bypass").querySelectorAll("input:checked")].map((i) => i.value) });
  $("copyLink").onclick = () => navigator.clipboard.writeText(location.href).then(() => toast("הקישור הועתק"));

  // ---------- browsing hours ----------
  const DAYS = ["א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳"];
  function renderHours(list) {
    $("hours").innerHTML = list.map((h, i) => '<div class="row hrow" style="margin-bottom:8px;border:1px solid var(--line);border-radius:12px;padding:8px">' +
      '<div class="days">' + DAYS.map((d, k) => '<label><input type="checkbox" data-day="' + k + '"' + ((h.days >> k) & 1 ? " checked" : "") + '>' + d + '</label>').join("") + '</div>' +
      '<input type="time" class="hf" value="' + hhmm(h.from) + '" style="width:auto"> עד <input type="time" class="ht" value="' + hhmm(h.to === 1440 ? 1439 : h.to) + '" style="width:auto">' +
      '<button class="btn sm d hdel">✕</button></div>').join("") || '<p class="muted">אין הגבלת שעות.</p>';
    $("hours").querySelectorAll(".hdel").forEach((b, i) => b.onclick = () => { const l = readHours(); l.splice(i, 1); renderHours(l); });
  }
  function readHours() {
    return [...$("hours").querySelectorAll(".hrow")].map((r) => {
      let days = 0; r.querySelectorAll("[data-day]").forEach((c) => { if (c.checked) days |= 1 << +c.dataset.day; });
      const tm = (v) => { const [a, b] = v.split(":").map(Number); return a * 60 + b; };
      return { days, from: tm(r.querySelector(".hf").value || "00:00"), to: tm(r.querySelector(".ht").value || "23:59") };
    });
  }
  $("addHours").onclick = () => { const l = readHours(); l.push({ days: 127, from: 8 * 60, to: 22 * 60 }); renderHours(l); };
  $("saveHours").onclick = () => save({ hours: readHours() }, "שעות הגלישה נשמרו");

  if (!id || !key) { $("noKey").classList.remove("hidden"); return; }
  load();
  setInterval(() => { if (!document.hidden) load(); }, 60000);
})();
</script>
</body>
</html>`;
}
