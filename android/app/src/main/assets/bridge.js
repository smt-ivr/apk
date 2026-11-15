// Injected by the app. A WebView has no Notification API, so this provides one
// backed by real phone notifications. Only used if the site asks for notifications.
(function () {
  if (window.__smtBridge || !window.SmtApp) return;
  window.__smtBridge = true;
  var N = window.SmtApp, waiting = {}, seq = 0, byTag = {};
  function AppNotification(title, opts) {
    opts = opts || {};
    this.title = String(title || '');
    this.body = String(opts.body || '');
    this.tag = String(opts.tag || ('n' + Date.now()));
    this.onclick = null;
    byTag[this.tag] = this;
    N.notify(this.title, this.body, this.tag);
  }
  AppNotification.prototype.close = function () { N.cancel(this.tag); };
  AppNotification.prototype.addEventListener = function (t, f) { if (t === 'click') this.onclick = f; };
  Object.defineProperty(AppNotification, 'permission', { get: function () { return N.permission(); } });
  AppNotification.requestPermission = function (cb) {
    return new Promise(function (resolve) {
      var id = ++seq;
      waiting[id] = function (p) { resolve(p); if (typeof cb === 'function') cb(p); };
      N.requestPermission(id);
    });
  };
  window.Notification = AppNotification;
  window.__smtPermission = function (id, p) { var f = waiting[id]; delete waiting[id]; if (f) f(p); };
  window.__smtNotificationClick = function (tag) {
    var n = byTag[tag];
    if (n && typeof n.onclick === 'function') { try { n.onclick({ target: n }); } catch (e) {} }
  };
})();
