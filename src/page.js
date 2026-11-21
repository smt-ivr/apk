const LOGO = "https://smti.uk/smt.png";

export function renderPage() {
  return `<!doctype html>
<html lang="he" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>SMT · יוצר אפליקציות</title>
<meta name="description" content="הופכים אתר אחד או כמה אתרים לאפליקציית אנדרואיד סגורה">
<link rel="icon" href="${LOGO}">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Heebo:wght@400;500;700;800&display=swap" rel="stylesheet">
<style>
:root{
  --bg:#f4f6fb; --card:#ffffff; --field:#f8f9fd; --text:#141a2e; --muted:#5d6785;
  --line:#e2e7f2; --accent:#2f6bff; --accent-ink:#ffffff; --ok:#14804a; --danger:#d93036;
  --shadow:0 1px 2px rgba(20,26,46,.05),0 8px 24px rgba(20,26,46,.07);
}
@media (prefers-color-scheme: dark){
  :root{
    --bg:#0b0f1c; --card:#141b30; --field:#0f1527; --text:#eef1fb; --muted:#9aa4c4;
    --line:#26304d; --accent:#5b8cff; --accent-ink:#0b0f1c; --ok:#3ccf83; --danger:#ff6369;
    --shadow:0 1px 2px rgba(0,0,0,.3),0 8px 24px rgba(0,0,0,.25);
  }
}
*{box-sizing:border-box}
html,body{margin:0}
body{font-family:"Heebo",system-ui,-apple-system,"Segoe UI",Arial,sans-serif;background:var(--bg);color:var(--text);line-height:1.5}
.wrap{max-width:680px;margin:0 auto;padding:28px 16px 64px}
header{text-align:center;margin-bottom:22px}
header h1{font-size:30px;font-weight:800;margin:0 0 6px}
header p{color:var(--muted);margin:0}
.card{background:var(--card);border:1px solid var(--line);border-radius:16px;box-shadow:var(--shadow);padding:20px;margin-bottom:16px}
.card h2{font-size:18px;margin:0 0 4px}
.hint{color:var(--muted);font-size:14px;margin:0 0 14px}
label.f{display:block;font-weight:500;margin:12px 0 6px}
input[type=text],input[type=url],input[type=password],input[type=tel],select,textarea{
  width:100%;font:inherit;color:var(--text);background:var(--field);border:1px solid var(--line);
  border-radius:10px;padding:10px 12px;outline:none}
input:focus,select:focus,textarea:focus{border-color:var(--accent);box-shadow:0 0 0 3px color-mix(in srgb,var(--accent) 22%,transparent)}
.ltr{direction:ltr;text-align:left}
textarea{min-height:84px;resize:vertical}
.site{border:1px solid var(--line);border-radius:12px;padding:12px;margin-bottom:10px;background:var(--field)}
.site .row{display:grid;grid-template-columns:1fr 1.4fr auto;gap:8px;align-items:center}
.site input{background:var(--card)}
.site .opts{display:flex;align-items:center;gap:6px;margin-top:8px;font-size:14px;color:var(--muted)}
.icon-btn{border:0;background:transparent;color:var(--muted);font-size:20px;cursor:pointer;padding:4px 8px;border-radius:8px}
.icon-btn:hover{background:color-mix(in srgb,var(--danger) 12%,transparent);color:var(--danger)}
@media (max-width:520px){.site .row{grid-template-columns:1fr auto}.site .row .url{grid-column:1 / -1;order:3}}
.btn{display:inline-flex;align-items:center;justify-content:center;gap:8px;font:inherit;font-weight:700;border-radius:12px;
  border:1px solid var(--line);background:var(--card);color:var(--text);padding:10px 16px;cursor:pointer;text-decoration:none}
.btn:hover{border-color:var(--accent)}
.btn.primary{background:var(--accent);color:var(--accent-ink);border-color:var(--accent);width:100%;padding:14px;font-size:18px}
.btn.primary:disabled{opacity:.6;cursor:default}
.icon-pick{display:flex;align-items:center;gap:14px}
.icon-prev{width:72px;height:72px;border-radius:18px;border:1px dashed var(--line);background:var(--field);
  display:flex;align-items:center;justify-content:center;color:var(--muted);font-size:12px;overflow:hidden;flex:none}
.icon-prev img{width:100%;height:100%;object-fit:cover}
.tabs{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:16px}
.tab{font:inherit;text-align:right;border:2px solid var(--line);background:var(--card);color:var(--text);border-radius:14px;padding:12px 14px;cursor:pointer}
.tab b{display:block;font-size:16px}
.tab span{display:block;font-size:13px;color:var(--muted);margin-top:2px}
.tab[aria-selected=true]{border-color:var(--accent);box-shadow:0 0 0 3px color-mix(in srgb,var(--accent) 18%,transparent)}
body[data-type=site] .only-lock,body[data-type=site] .only-mdm,body[data-type=site] .only-pin,
body[data-type=lock] .only-site,body[data-type=lock] .only-mdm,
body[data-type=mdm] .only-site,body[data-type=mdm] .only-lock{display:none!important}
.manage{background:linear-gradient(135deg,#0f1a2b,#17325c);color:#fff;border-radius:16px;padding:16px;text-align:right;margin:14px 0}
.manage a{color:#bfd7ff;word-break:break-all;direction:ltr;display:block;text-align:left;font-size:13px;margin:6px 0 10px}
.steps-list{text-align:right;margin:8px 0 0;padding-inline-start:20px}
.steps-list li{margin:6px 0}
code.cmd{display:block;direction:ltr;text-align:left;background:var(--field);border:1px solid var(--line);border-radius:10px;padding:10px;font-size:12px;word-break:break-all;margin:8px 0}
.update-box{display:flex;gap:8px}
.update-box input{flex:1}
.updating{display:flex;align-items:center;gap:10px;justify-content:space-between;background:color-mix(in srgb,var(--accent) 10%,var(--card));border:1px solid color-mix(in srgb,var(--accent) 35%,var(--line));border-radius:12px;padding:10px 12px;margin-bottom:16px}
.check{display:flex;align-items:flex-start;gap:8px;margin:10px 0 0;cursor:pointer}
.check input{margin-top:5px}
.grid2{display:grid;grid-template-columns:1fr 1fr;gap:12px}
@media (max-width:520px){.grid2{grid-template-columns:1fr}}
.err{color:var(--danger);font-weight:500;min-height:1.5em;margin:10px 0 0;text-align:center}
#status{display:none;text-align:center}
.spinner{width:44px;height:44px;border-radius:50%;border:4px solid var(--line);border-top-color:var(--accent);
  animation:spin 1s linear infinite;margin:6px auto 14px}
@keyframes spin{to{transform:rotate(360deg)}}
@media (prefers-reduced-motion: reduce){.spinner{animation-duration:3s}}
.steps{list-style:none;padding:0;margin:14px auto 0;max-width:280px;text-align:right}
.steps li{padding:4px 0;color:var(--muted)}
.steps li.on{color:var(--text);font-weight:700}
.steps li.ok{color:var(--ok)}
.done-mark{font-size:44px;color:var(--ok);line-height:1}
.small{font-size:14px;color:var(--muted)}
details summary{cursor:pointer;color:var(--accent);font-weight:500}
footer{text-align:center;color:var(--muted);font-size:13px;margin-top:20px}
</style>
</head>
<body data-type="site">
<div class="wrap">
  <header>
    <h1>יוצר אפליקציות</h1>
    <p>אפליקציות אנדרואיד מותאמות אישית, מוכנות להתקנה.</p>
  </header>

  <div class="tabs" role="tablist">
    <button type="button" class="tab" role="tab" data-type="site" aria-selected="true"><b>אפליקציה מאתר</b><span>מציגה רק את האתרים שתבחר</span></button>
    <button type="button" class="tab" role="tab" data-type="lock" aria-selected="false"><b>נעילת אפליקציות</b><span>נעילה וחסימה של אפליקציות בטלפון</span></button>
    <button type="button" class="tab" role="tab" data-type="mdm" aria-selected="false"><b>ניהול מכשירים</b><span>קיוסק ושליטה מלאה מרחוק</span></button>
  </div>

  <div class="updating" id="updating" hidden>
    <span>מעדכן את <b id="updName"></b>. הבנייה תותקן כעדכון על האפליקציה הקיימת.</span>
    <button type="button" class="btn" id="updCancel">ביטול</button>
  </div>
  <details class="card" id="updDetails" style="padding:14px 20px">
    <summary style="cursor:pointer;font-weight:700">עדכון לאפליקציה שכבר יצרתם</summary>
    <p class="hint" style="margin:10px 0 8px">הדביקו את קישור ההורדה של האפליקציה הקודמת. ההגדרות שלה ייטענו, ואפשר לשנות אותן ולבנות גרסה חדשה שתותקן כעדכון, בלי למחוק את הקיימת.</p>
    <div class="update-box">
      <input type="url" id="updLink" class="ltr" placeholder="https://smti.uk/apk/download/…">
      <button type="button" class="btn" id="updLoad">טעינה</button>
    </div>
    <p class="small" style="margin:8px 0 0">אין קישור? בנייה עם אותו שם בדיוק ואותו אתר ראשון מתעדכנת גם כן.</p>
  </details>

  <form id="form" novalidate>
    <div class="card">
      <h2>פרטי האפליקציה</h2>
      <label class="f" for="appName">שם האפליקציה</label>
      <input type="text" id="appName" maxlength="40" required placeholder="למשל: שיעורי תורה">

      <label class="f">סמל</label>
      <div class="icon-pick">
        <div class="icon-prev" id="iconPrev">אוטומטי</div>
        <div>
          <input type="file" id="iconFile" accept="image/*" hidden>
          <button type="button" class="btn" id="iconBtn">בחירת תמונה</button>
          <button type="button" class="btn" id="iconClear" hidden>הסרה</button>
          <p class="small only-site" style="margin:6px 0 0">בלי תמונה ננסה לקחת את הסמל של האתר הראשון.</p>
          <p class="small only-lock" style="margin:6px 0 0">בלי תמונה יופיע סמל של מנעול.</p>
          <p class="small only-mdm" style="margin:6px 0 0">בלי תמונה יופיע סמל של מגן.</p>
        </div>
      </div>
    </div>

    <div class="card only-pin">
      <h2>הקוד</h2>
      <p class="hint only-lock">הקוד נדרש כדי לפתוח אפליקציות נעולות, לשנות הגדרות ולהסיר את האפליקציה. <b>שמרו אותו במקום בטוח</b>: בלי הקוד אי אפשר להסיר את ההגנה.</p>
      <p class="hint only-mdm">הקוד נדרש בטלפון ליציאה מהקיוסק ולהסרה מלאה של האפליקציה וההגבלות. עובד תמיד, גם בלי אינטרנט. אפשר להחליף אותו ממרכז השליטה.</p>
      <div class="grid2">
        <div>
          <label class="f" for="lockPin">קוד</label>
          <input type="password" id="lockPin" class="ltr" inputmode="numeric" maxlength="8" placeholder="4–8 ספרות" autocomplete="new-password">
          <p class="small upd-only" hidden style="margin:4px 0 0">ריק = נשאר הקוד הקיים</p>
        </div>
        <div>
          <label class="f" for="lockPin2">שוב את הקוד</label>
          <input type="password" id="lockPin2" class="ltr" inputmode="numeric" maxlength="8" autocomplete="new-password">
        </div>
      </div>
    </div>

    <div class="card only-lock">
      <h2>מה האפליקציה עושה</h2>
      <ul class="steps-list small">
        <li>בטלפון בוחרים לכל אפליקציה: <b>פתוחה</b>, <b>נפתחת בקוד</b>, או <b>חסומה לגמרי</b>.</li>
        <li>עם הרשאת ניהול אי אפשר להסיר אותה בלי הקוד, וגם מסכי ההגדרות שמבטלים אותה מוגנים בקוד.</li>
        <li>אפשרות להגנה חזקה (חיבור למחשב פעם אחת): אי אפשר להסיר אותה בכלל, והגבלות נוספות כמו חסימת מצב בטוח והתקנת אפליקציות.</li>
        <li>האפליקציה לא אוספת ולא שולחת שום מידע.</li>
      </ul>
    </div>

    <div class="card only-mdm">
      <h2>אתר הניהול</h2>
      <p class="hint">סיסמה לכניסה למרכז השליטה (בנוסף לקישור הסודי). ריק = אותו קוד כמו בטלפון.</p>
      <input type="password" id="mPass" autocomplete="new-password" placeholder="לפחות 4 תווים">
      <label class="check"><input type="checkbox" id="mKiosk"> <span><b>להפעיל קיוסק מייד בהתקנה</b>: הטלפון ננעל לאפליקציות שתבחרו (אפשר לשנות בכל רגע ממרכז השליטה).</span></label>
      <div id="mKioskBox" hidden>
        <label class="f" for="mApps">אפליקציות בקיוסק <span class="small">(שמות חבילה, שורה לכל אחת)</span></label>
        <textarea id="mApps" class="ltr" placeholder="com.android.chrome&#10;com.whatsapp"></textarea>
        <label class="f" for="mTitle">כותרת במסך הקיוסק <span class="small">(לא חובה)</span></label>
        <input type="text" id="mTitle" maxlength="60">
      </div>
    </div>

    <div class="card only-mdm">
      <h2>מה מקבלים</h2>
      <ul class="steps-list small">
        <li><b>קיוסק</b>: נעילה לאפליקציה אחת או לכמה, מסך בית משלכם, נדלק מייד בהתקנה. יציאה תמיד אפשרית עם הקוד.</li>
        <li><b>הגבלות</b>: איפוס, מצב בטוח, התקנה והסרה של אפליקציות, חשבונות, WiFi, בלוטות׳, USB, מצלמה, צילומי מסך, שיחות, SMS ועוד כ-40.</li>
        <li><b>אפליקציות</b>: הסתרה, השבתה, הגנה מהסרה, פתיחה, התקנה שקטה מקישור, הסרה ומחיקת נתונים.</li>
        <li><b>פקודות מרחוק</b>: נעילה, צלצול לאיתור, הודעה על המסך, מיקום, הפעלה מחדש, עדכון האפליקציה, ופקודות רוט.</li>
        <li><b>הסרה מלאה</b>: מהטלפון עם הקוד, או מרחוק. כל ההגבלות משתחררות (עם רוט: בשקט לגמרי).</li>
        <li><b>עדכונים</b>: בונים גרסה חדשה עם אותו שם, והטלפונים מתעדכנים לבד.</li>
        <li>התקנה בברקוד (בטלפון אחרי איפוס), בלחיצה עם רוט, או בפקודה ממחשב.</li>
      </ul>
    </div>

    <div class="card only-site">
      <h2>אתרים</h2>
      <p class="hint">אם יש יותר מאתר אחד, באפליקציה יופיע מסך בית עם כפתור לכל אתר. תתי-האתרים של כל אתר מותרים גם הם.</p>
      <div id="sites"></div>
      <button type="button" class="btn" id="addSite">+ הוספת אתר</button>
    </div>

    <div class="card only-site">
      <h2>הגדרות נוספות</h2>

      <label class="f" for="extra">כתובות נוספות מורשות <span class="small">(לא חובה)</span></label>
      <p class="hint" style="margin-bottom:6px">לא יופיעו במסך הבית, אבל מותר יהיה לעבור אליהן, למשל אתר התחברות או תשלום. שורה לכל כתובת.</p>
      <textarea id="extra" class="ltr" placeholder="login.example.com"></textarea>

      <div class="grid2">
        <div>
          <label class="f" for="pin">קוד להגדרות <span class="small">(לא חובה)</span></label>
          <input type="password" id="pin" class="ltr" inputmode="numeric" maxlength="8" placeholder="4–8 ספרות" autocomplete="new-password">
          <p class="small upd-only" hidden style="margin:4px 0 0">ריק = נשאר הקוד הקיים</p>
        </div>
        <div>
          <label class="f" for="orientation">מצב מסך</label>
          <select id="orientation">
            <option value="auto">אוטומטי</option>
            <option value="portrait">תמיד לאורך</option>
            <option value="landscape">תמיד לרוחב</option>
          </select>
        </div>
      </div>
      <label class="check"><input type="checkbox" id="optToolbar" checked> <span><b>סרגל דפדפן</b>: אחורה, קדימה, דף הבית, רענון, העתקת קישור, שיתוף וחיפוש בדף. אפשר להסתיר אותו מתוך האפליקציה.</span></label>
      <label class="check"><input type="checkbox" id="optDesktop"> <span><b>מצב מחשב</b> כברירת מחדל (אפשר להחליף גם מתוך האפליקציה).</span></label>
      <p class="hint" style="margin-top:12px">עם קוד, בתפריט האפליקציה יהיה מסך הגדרות שבו אפשר להוסיף ולהסיר אתרים בלי לבנות אפליקציה חדשה. בלי קוד, הרשימה קבועה.</p>
    </div>

    <button type="submit" class="btn primary" id="submit">יצירת אפליקציה</button>
    <p class="err" id="err" role="alert"></p>
  </form>

  <div class="card" id="status" aria-live="polite">
    <div id="stBusy">
      <div class="spinner"></div>
      <h2 id="stTitle">מכין את האפליקציה…</h2>
      <p class="small">זה לוקח בדרך כלל 2–4 דקות. אפשר להשאיר את העמוד פתוח, או לחזור אליו אחר כך.</p>
      <ul class="steps">
        <li id="s1">שליחת הבקשה</li>
        <li id="s2">בתור לבנייה</li>
        <li id="s3">בונה את האפליקציה</li>
      </ul>
      <p class="small" id="stTime"></p>
    </div>
    <div id="stDone" hidden>
      <div class="done-mark">✓</div>
      <h2 id="doneTitle">האפליקציה מוכנה</h2>
      <p class="small" id="doneInfo"></p>
      <p><a class="btn primary" id="dl" href="#">הורדת קובץ ה-APK</a></p>
      <div id="lockSteps" hidden style="text-align:right">
        <h3 style="margin:16px 0 4px">אחרי ההתקנה</h3>
        <ol class="steps-list small">
          <li>פותחים את האפליקציה ומזינים את הקוד.</li>
          <li><b>שירות הנעילה:</b> לוחצים "פתיחת הגדרות נגישות" ומפעילים את האפליקציה. באנדרואיד 13 ומעלה, אם האפשרות אפורה: הגדרות ← אפליקציות ← האפליקציה ← תפריט ⋮ ← "התרת הגדרות מוגבלות".</li>
          <li><b>הרשאת ניהול:</b> לוחצים "הפעלת הרשאת ניהול" ומאשרים.</li>
          <li>בוחרים אילו אפליקציות לנעול או לחסום.</li>
        </ol>
        <details style="margin-top:10px">
          <summary>הגנה חזקה (לא חובה, דורש מחשב)</summary>
          <p class="small">עובד רק בטלפון שאין בו חשבון Google מחובר (בדרך כלל אחרי איפוס). בטלפון מפעילים "ניפוי באגים ב-USB" באפשרויות המפתחים, מחברים למחשב שמותקן בו ADB, ומריצים:</p>
          <code class="cmd" id="adbCmd"></code>
          <p class="small">אחרי זה אי אפשר להסיר את האפליקציה בלי הקוד, ואפליקציות חסומות מושבתות במערכת. אחרי ההפעלה אפשר להוסיף שוב חשבון Google.</p>
        </details>
      </div>
      <div id="filterSteps" hidden style="text-align:right">
        <div class="manage">
          <b>🛡️ מרכז השליטה</b>
          <a id="manageLink" href="#" target="_blank" rel="noopener"></a>
          <button type="button" class="btn" id="copyManage">העתקת הקישור</button>
          <p class="small" style="color:#d5deea;margin:10px 0 0"><b>שמרו את הקישור!</b> דרכו (יחד עם הסיסמה) מנהלים את המכשירים. אי אפשר לשחזר אותו. שם נמצא גם הברקוד להתקנה.</p>
          <p class="small" style="color:#d5deea;margin:8px 0 0">זו אפליקציה אחת שלכם. <b>כל הטלפונים שמתקינים אותה מנוהלים מאותו מרכז שליטה</b> — אין צורך ליצור אפליקציה לכל טלפון. בונים שוב רק כדי לעדכן גרסה.</p>
        </div>
        <h3 style="margin:16px 0 4px">הפעלת הניהול בטלפון (פעם אחת)</h3>
        <ol class="steps-list small">
          <li><b>ברקוד</b> (טלפון חדש או אחרי איפוס): במסך "ברוכים הבאים" נוגעים 6 פעמים, מתחברים ל-WiFi וסורקים את הברקוד שבמרכז השליטה.</li>
          <li><b>רוט</b>: מתקינים, פותחים ולוחצים "הפעלה עם רוט".</li>
          <li><b>מחשב</b>: מסירים זמנית את החשבונות, מפעילים ניפוי באגים ב-USB ומריצים:</li>
        </ol>
        <code class="cmd" id="filterAdb"></code>
        <p class="small">להסרה: באפליקציה ← "הסרה מלאה" עם הקוד, או ממרכז השליטה.</p>
      </div>
      <details>
        <summary>איך מתקינים?</summary>
        <ol style="text-align:right" class="small">
          <li>פותחים את הקישור בטלפון ומורידים את הקובץ.</li>
          <li>פותחים את הקובץ מההורדות.</li>
          <li>אם הטלפון מבקש, מאשרים "התקנה ממקורות לא ידועים" לדפדפן או לאפליקציית הקבצים.</li>
          <li>בנייה חוזרת עם אותו שם ואותו אתר ראשון תותקן כעדכון לאפליקציה הקיימת.</li>
        </ol>
      </details>
      <p class="small">הקישור זמין לשבוע.</p>
    </div>
    <div id="stErr" hidden>
      <h2 style="color:var(--danger)">משהו השתבש</h2>
      <p id="errMsg"></p>
    </div>
    <button type="button" class="btn" id="again" style="margin-top:12px" hidden>יצירת אפליקציה נוספת</button>
  </div>

  <footer>SMT · הקוד של האפליקציה פתוח ומבוסס על AndroidRestrictedWebView (GPL-3.0)</footer>
</div>

<script>
(function () {
  const $ = (id) => document.getElementById(id);
  const API = location.pathname.replace(/\\/$/, "") + "/api";
  const store = {
    get(k) { try { return localStorage.getItem(k); } catch { return null; } },
    set(k, v) { try { v == null ? localStorage.removeItem(k) : localStorage.setItem(k, v); } catch {} },
  };

  // ---------- app type ----------
  let type = "site";
  function setType(t) {
    type = t === "lock" || t === "mdm" ? t : "site";
    document.body.dataset.type = type;
    document.querySelectorAll(".tab").forEach((b) => b.setAttribute("aria-selected", String(b.dataset.type === type)));
    $("appName").placeholder = type === "lock" ? "למשל: נעילה" : type === "mdm" ? "למשל: הטלפון של הבית" : "למשל: שיעורי תורה";
    showErr("");
  }
  document.querySelectorAll(".tab").forEach((b) => (b.onclick = () => setType(b.dataset.type)));

  // ---------- update an app built before ----------
  let updateOf = null;
  function setUpdating(id, name) {
    updateOf = id;
    $("updating").hidden = !id;
    $("updName").textContent = name || "";
    $("updDetails").hidden = !!id;
    document.querySelectorAll(".upd-only").forEach((e) => (e.hidden = !id));
  }
  $("updCancel").onclick = () => setUpdating(null);
  $("updLoad").onclick = async () => {
    const m = /([a-f0-9]{24})/.exec($("updLink").value);
    if (!m) return showErr("לא זיהינו קישור הורדה של אפליקציה");
    showErr("");
    $("updLoad").disabled = true;
    try {
      const r = await fetch(API + "/jobs/" + m[1] + "/settings", { cache: "no-store" });
      const d = await r.json().catch(() => ({}));
      if (!r.ok || !d.ok) throw new Error(d.error || "הטעינה נכשלה");
      setType(d.type);
      $("appName").value = d.appName;
      if (d.type === "site") {
        $("sites").innerHTML = "";
        (d.sites.length ? d.sites : [{}]).forEach((x) => addSite(x.title || "", x.url || "", !!x.restrictPath));
        $("extra").value = (d.extraDomains || []).join("\\n");
        $("orientation").value = d.orientation || "auto";
        $("optToolbar").checked = d.toolbar !== false;
        $("optDesktop").checked = !!d.desktop;
      }
      setUpdating(m[1], d.appName);
      window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (err) {
      showErr(err.message);
    } finally {
      $("updLoad").disabled = false;
    }
  };

  // ---------- sites list ----------
  function addSite(title = "", url = "", restrict = false) {
    const box = document.createElement("div");
    box.className = "site";
    box.innerHTML =
      '<div class="row">' +
      '<input type="text" class="title" maxlength="40" placeholder="שם הכפתור">' +
      '<input type="url" class="url ltr" placeholder="example.com" inputmode="url">' +
      '<button type="button" class="icon-btn" title="הסרה" aria-label="הסרת אתר">✕</button>' +
      '</div>' +
      '<label class="opts"><input type="checkbox" class="restrict"> רק הנתיב שבכתובת, לא כל האתר</label>';
    box.querySelector(".title").value = title;
    box.querySelector(".url").value = url;
    box.querySelector(".restrict").checked = restrict;
    box.querySelector(".icon-btn").onclick = () => {
      if ($("sites").children.length > 1) box.remove();
      else { box.querySelector(".url").value = ""; box.querySelector(".title").value = ""; }
    };
    $("sites").appendChild(box);
    return box;
  }
  $("addSite").onclick = () => addSite().querySelector(".url").focus();
  addSite();

  // ---------- icon: center-crop to a 512px square PNG ----------
  let iconData = "";
  $("iconBtn").onclick = () => $("iconFile").click();
  $("iconClear").onclick = () => {
    iconData = "";
    $("iconPrev").textContent = "אוטומטי";
    $("iconClear").hidden = true;
    $("iconFile").value = "";
  };
  $("iconFile").onchange = function () {
    const file = this.files[0];
    if (!file) return;
    const img = new Image();
    img.onload = () => {
      const side = Math.min(img.naturalWidth, img.naturalHeight);
      const size = Math.min(512, side);
      const c = document.createElement("canvas");
      c.width = c.height = size;
      c.getContext("2d").drawImage(img,
        (img.naturalWidth - side) / 2, (img.naturalHeight - side) / 2, side, side, 0, 0, size, size);
      iconData = c.toDataURL("image/png");
      $("iconPrev").innerHTML = "";
      const p = new Image();
      p.src = iconData;
      p.alt = "";
      $("iconPrev").appendChild(p);
      $("iconClear").hidden = false;
      URL.revokeObjectURL(img.src);
    };
    img.onerror = () => showErr("לא הצלחנו לקרוא את התמונה");
    img.src = URL.createObjectURL(file);
  };

  // ---------- submit ----------
  function showErr(t) { $("err").textContent = t || ""; }

  function looksLikeUrl(s) {
    return /^(https?:\\/\\/)?([a-z0-9-]+\\.)+[a-z0-9-]{2,}(:\\d+)?(\\/\\S*)?$/i.test(s.trim());
  }

  $("form").onsubmit = async (e) => {
    e.preventDefault();
    showErr("");
    const appName = $("appName").value.trim();
    if (!appName) { $("appName").focus(); return showErr("נא להזין שם לאפליקציה"); }

    if (type === "lock" || type === "mdm") {
      const p1 = $("lockPin").value.trim();
      if (!(updateOf && !p1)) {
        if (!/^\\d{4,8}$/.test(p1)) { $("lockPin").focus(); return showErr("הקוד צריך להיות 4 עד 8 ספרות"); }
        if (p1 !== $("lockPin2").value.trim()) { $("lockPin2").focus(); return showErr("הקודים לא תואמים"); }
      }
      if (type === "mdm") {
        const sp = $("mPass").value;
        if (sp && sp.length < 4) { $("mPass").focus(); return showErr("סיסמת אתר הניהול: לפחות 4 תווים"); }
        const kioskApps = $("mApps").value.split(/[\\s,]+/).map((x) => x.trim()).filter(Boolean);
        if ($("mKiosk").checked && !kioskApps.length) return showErr("לקיוסק צריך לפחות אפליקציה אחת");
        return send({ type: "mdm", appName, pin: p1, icon: iconData, updateOf, sitePassword: sp, kiosk: $("mKiosk").checked, kioskApps, kioskTitle: $("mTitle").value.trim() });
      }
      return send({ type: "lock", appName, pin: p1, icon: iconData, updateOf });
    }

    const sites = [];
    for (const box of $("sites").children) {
      const url = box.querySelector(".url").value.trim();
      if (!url) continue;
      if (!looksLikeUrl(url)) { box.querySelector(".url").focus(); return showErr('הכתובת "' + url + '" לא תקינה'); }
      sites.push({ title: box.querySelector(".title").value.trim(), url, restrictPath: box.querySelector(".restrict").checked });
    }
    if (!sites.length) { $("sites").querySelector(".url").focus(); return showErr("נא להזין לפחות כתובת אתר אחת"); }

    const extraDomains = $("extra").value.split(/[\\s,]+/).map((s) => s.trim()).filter(Boolean);
    for (const d of extraDomains) if (!looksLikeUrl(d)) return showErr('הכתובת הנוספת "' + d + '" לא תקינה');

    const pin = $("pin").value.trim();
    if (pin && !/^\\d{4,8}$/.test(pin)) { $("pin").focus(); return showErr("הקוד צריך להיות 4 עד 8 ספרות"); }

    return send({ type: "site", appName, sites, extraDomains, pin, orientation: $("orientation").value,
      toolbar: $("optToolbar").checked, desktop: $("optDesktop").checked, icon: iconData, updateOf });
  };

  async function send(payload) {
    $("submit").disabled = true;
    try {
      const r = await fetch(API + "/build", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(payload),
      });
      const data = await r.json().catch(() => ({}));
      if (!r.ok || !data.ok) throw new Error(data.error || "השליחה נכשלה");
      store.set("apkJob", data.id);
      track(data.id);
    } catch (err) {
      showErr(err.message);
    } finally {
      $("submit").disabled = false;
    }
  };

  // ---------- progress ----------
  let timer = null;
  function setStep(n) {
    ["s1", "s2", "s3"].forEach((id, i) => {
      $(id).className = i + 1 < n ? "ok" : i + 1 === n ? "on" : "";
      $(id).textContent = $(id).textContent.replace(/^✓ /, "");
      if (i + 1 < n) $(id).textContent = "✓ " + $(id).textContent;
    });
  }
  function view(which) {
    $("form").style.display = which ? "none" : "";
    $("status").style.display = which ? "block" : "none";
    $("stBusy").hidden = which !== "busy";
    $("stDone").hidden = which !== "done";
    $("stErr").hidden = which !== "err";
    $("again").hidden = which === "busy" || !which;
  }
  function track(id) {
    view("busy");
    setStep(2);
    clearTimeout(timer);
    const poll = async () => {
      try {
        const r = await fetch(API + "/jobs/" + id, { cache: "no-store" });
        const d = await r.json();
        if (!d.ok) throw new Error(d.error || "שגיאה");
        if (d.status === "done") {
          view("done");
          $("doneTitle").textContent = "האפליקציה \\u201C" + d.appName + "\\u201D מוכנה";
          $("doneInfo").textContent = "גרסה " + d.version + " · " + Math.max(1, Math.round(d.size / 1024)) + " KB";
          $("dl").href = d.download;
          $("lockSteps").hidden = d.type !== "lock";
          $("filterSteps").hidden = d.type !== "mdm";
          if (d.manage) { $("manageLink").href = d.manage; $("manageLink").textContent = d.manage; $("filterAdb").textContent = d.adb || ""; }
          if (d.adb) $("adbCmd").textContent = d.adb;
          return;
        }
        if (d.status === "error") {
          view("err");
          $("errMsg").textContent = d.error || "הבנייה נכשלה";
          store.set("apkJob", null);
          return;
        }
        setStep(d.status === "building" ? 3 : 2);
        $("stTime").textContent = "עברו " + Math.floor(d.elapsed / 60) + ":" + String(d.elapsed % 60).padStart(2, "0") + " דקות";
      } catch (err) {
        if (String(err.message).includes("לא נמצאה")) { store.set("apkJob", null); view(null); return; }
      }
      timer = setTimeout(poll, 4000);
    };
    poll();
  }
  $("mKiosk").onchange = () => { $("mKioskBox").hidden = !$("mKiosk").checked; };
  $("copyManage").onclick = () => navigator.clipboard.writeText($("manageLink").href).then(() => { $("copyManage").textContent = "הועתק ✓"; });
  $("again").onclick = () => { clearTimeout(timer); store.set("apkJob", null); setUpdating(null); view(null); };

  const last = store.get("apkJob");
  if (last && /^[a-f0-9]{24}$/.test(last)) track(last);
})();
</script>
</body>
</html>`;
}
