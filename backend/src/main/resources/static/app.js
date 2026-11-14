// CampusDesk web UI - plain JS single-page app with hash routing (#/tickets, #/ticket/12 ...).
"use strict";

const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

const LABEL = {
  NEW: "New", ASSIGNED: "Assigned", IN_PROGRESS: "In progress", WAITING_ON_USER: "Waiting on student",
  RESOLVED: "Resolved", CLOSED: "Closed",
  URGENT: "Urgent", HIGH: "High", MEDIUM: "Medium", LOW: "Low",
  STUDENT: "Student", TECHNICIAN: "Technician", ADMIN: "IT Admin",
  LAPTOP: "Laptop", DESKTOP: "Desktop / lab PC", PHONE: "Phone", TABLET: "Tablet", PRINTER: "Printer",
  CLASSROOM_AV: "Classroom AV", NETWORK: "Network / Wi-Fi", OTHER: "Other",
  ON_TRACK: "On track", AT_RISK: "Due soon", OVERDUE: "Overdue", MET: "SLA met", MISSED: "SLA missed",
};
const OPEN_STATUSES = ["NEW", "ASSIGNED", "IN_PROGRESS", "WAITING_ON_USER"];

const state = { me: null, meta: null, counts: null, signupAllowed: true, staff: [], countsTimer: null };

// ------------------------------------------------------------------ helpers

class ApiError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

async function api(path, opts = {}) {
  const res = await fetch(path, { credentials: "same-origin", ...opts });
  if (res.status === 401 && !path.startsWith("/api/auth/")) {
    state.me = null;
    renderAuth("login", "Your session has ended - please sign in again.");
    throw new ApiError(401, "Please sign in");
  }
  if (!res.ok) {
    let msg = `${res.status} ${res.statusText}`;
    try { const j = await res.json(); msg = j.detail || msg; } catch { /* not JSON */ }
    throw new ApiError(res.status, msg);
  }
  return res.status === 204 ? null : res.json();
}
const send = (method, body) => ({ method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });

function toast(msg, isError = false) {
  const t = $("#toast");
  t.textContent = msg;
  t.className = "toast" + (isError ? " error" : "");
  t.hidden = false;
  clearTimeout(toast._t);
  toast._t = setTimeout(() => (t.hidden = true), 3500);
}
const fail = (e) => { if (e.status !== 401) toast(e.message || String(e), true); };

const pill = (kind, v) => `<span class="pill ${kind}-${v}">${esc(LABEL[v] || v)}</span>`;
const initials = (name) => (name || "?").split(/\s+/).map((p) => p[0]).slice(0, 2).join("").toUpperCase();

function when(iso) {
  if (!iso) return "";
  const d = new Date(iso), s = (Date.now() - d.getTime()) / 1000;
  if (Math.abs(s) < 60) return "just now";
  if (s > 0 && s < 3600) return `${Math.floor(s / 60)} min ago`;
  if (s > 0 && s < 86400) return `${Math.floor(s / 3600)} h ago`;
  return d.toLocaleString([], { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
}
/** "in 3 h" / "2 h ago" relative to now, for deadlines. */
function relative(iso) {
  const ms = new Date(iso).getTime() - Date.now(), past = ms < 0, m = Math.abs(ms) / 60000;
  const txt = m < 60 ? `${Math.round(m)} min` : m < 2880 ? `${Math.round(m / 60)} h` : `${Math.round(m / 1440)} days`;
  return past ? `${txt} ago` : `in ${txt}`;
}
const fullDate = (iso) => (iso ? new Date(iso).toLocaleString([], { dateStyle: "medium", timeStyle: "short" }) : "-");

// ------------------------------------------------------------------ dialog

function openDialog({ title, body, okLabel = "Save", danger = false, onOk }) {
  $("#dlgTitle").textContent = title;
  $("#dlgBody").innerHTML = body;
  const ok = $("#dlgOk");
  ok.textContent = okLabel;
  ok.className = "btn " + (danger ? "danger" : "primary");
  $("#dialog").hidden = false;
  setTimeout(() => $("#dlgBody input, #dlgBody textarea, #dlgBody select")?.focus(), 20);
  ok.onclick = async () => {
    ok.disabled = true;
    try { if ((await onOk()) !== false) closeDialog(); } catch (e) { fail(e); } finally { ok.disabled = false; }
  };
}
function closeDialog() { $("#dialog").hidden = true; }
$("#dlgCancel").onclick = closeDialog;
$("#dialog").addEventListener("mousedown", (e) => { if (e.target.id === "dialog") closeDialog(); });
document.addEventListener("keydown", (e) => { if (e.key === "Escape" && !$("#dialog").hidden) closeDialog(); });

// ================================================================== auth screens

function renderAuth(mode, notice = "") {
  clearInterval(state.countsTimer);
  const screens = {
    setup: {
      title: "Welcome to CampusDesk",
      lead: "Create the first IT administrator account. You can add technicians afterwards.",
      fields: `
        <div class="field"><label>Full name</label><input class="input" name="fullName" autocomplete="name"></div>
        <div class="field"><label>Work email</label><input class="input" name="email" type="email" autocomplete="email"></div>
        <div class="field"><label>Password</label><input class="input" name="password" type="password" autocomplete="new-password">
          <span class="hint">At least 8 characters.</span></div>`,
      button: "Create admin account", path: "/api/auth/setup",
    },
    login: {
      title: "Sign in",
      lead: "Campus IT help desk - report a problem or follow up on a ticket.",
      fields: `
        <div class="field"><label>Email</label><input class="input" name="email" type="email" autocomplete="username"></div>
        <div class="field"><label>Password</label><input class="input" name="password" type="password" autocomplete="current-password"></div>`,
      button: "Sign in", path: "/api/auth/login",
      switchText: state.signupAllowed ? `New student? <button type="button" data-go="register">Create an account</button>` : "",
    },
    register: {
      title: "Create a student account",
      lead: "Use your campus email so IT can reach you about your tickets.",
      fields: `
        <div class="field"><label>Full name</label><input class="input" name="fullName" autocomplete="name"></div>
        <div class="two">
          <div class="field"><label>Campus email</label><input class="input" name="email" type="email" autocomplete="email"></div>
          <div class="field"><label>Student number</label><input class="input" name="studentNumber"></div>
        </div>
        <div class="field"><label>Password</label><input class="input" name="password" type="password" autocomplete="new-password">
          <span class="hint">At least 8 characters.</span></div>`,
      button: "Create account", path: "/api/auth/register",
      switchText: `Already have an account? <button type="button" data-go="login">Sign in</button>`,
    },
  };
  const s = screens[mode];
  $("#root").innerHTML = `
    <div class="auth"><form class="auth-card" id="authForm" novalidate>
      <div class="auth-brand"><img src="icon-64.png" alt=""> CampusDesk</div>
      <h1>${s.title}</h1>
      <p class="lead">${s.lead}</p>
      <div class="error-box" id="authError" ${notice ? "" : "hidden"}>${esc(notice)}</div>
      ${s.fields}
      <button class="btn primary block" type="submit">${s.button}</button>
      ${s.switchText ? `<div class="auth-switch">${s.switchText}</div>` : ""}
    </form></div>`;
  $$("[data-go]").forEach((b) => b.onclick = () => renderAuth(b.dataset.go));
  $("#authForm input")?.focus();
  $("#authForm").onsubmit = async (e) => {
    e.preventDefault();
    const btn = $("#authForm button[type=submit]");
    btn.disabled = true;
    const body = Object.fromEntries(new FormData(e.target));
    try {
      state.me = await api(s.path, send("POST", body));
      await startApp();
    } catch (err) {
      $("#authError").textContent = err.message;
      $("#authError").hidden = false;
      $("#authError").style.background = "";
    } finally { btn.disabled = false; }
  };
  if (notice && mode === "login") $("#authError").style.background = "var(--accent-soft)", $("#authError").style.color = "var(--accent)";
}

async function logout() {
  await api("/api/auth/logout", { method: "POST" }).catch(() => {});
  $("#toast").hidden = true;
  state.me = null;
  location.hash = "";
  renderAuth("login");
}

function changePassword() {
  openDialog({
    title: "Change password",
    body: `<div class="field"><label>Current password</label><input class="input" id="pwOld" type="password"></div>
           <div class="field"><label>New password</label><input class="input" id="pwNew" type="password"><span class="hint">At least 8 characters.</span></div>`,
    onOk: async () => {
      await api("/api/auth/password", send("POST", { currentPassword: $("#pwOld").value, newPassword: $("#pwNew").value }));
      toast("Password changed");
    },
  });
}

// ================================================================== shell + routing

const isStaff = () => state.me && state.me.role !== "STUDENT";
const isAdmin = () => state.me && state.me.role === "ADMIN";

async function startApp() {
  state.meta = await api("/api/meta");
  if (isStaff()) {
    state.staff = await api("/api/users/assignable");
    await refreshCounts();
    clearInterval(state.countsTimer);
    state.countsTimer = setInterval(() => refreshCounts().then(renderNav).catch(() => {}), 30000);
  }
  renderShell();
  if (!location.hash || location.hash === "#/") location.hash = isStaff() ? "#/tickets?view=open" : "#/tickets";
  else route();
}

async function refreshCounts() {
  if (isStaff()) state.counts = await api("/api/tickets/counts");
}

function renderShell() {
  $("#root").innerHTML = `
    <div class="shell">
      <nav class="nav" id="nav"></nav>
      <main class="main"><div class="page" id="page"></div></main>
    </div>`;
  renderNav();
}

function renderNav() {
  const nav = $("#nav");
  if (!nav) return;
  const c = state.counts || {};
  const badge = (n, alert) => (n ? `<span class="badge ${alert ? "alert" : ""}">${n}</span>` : "");
  const link = (href, ico, text, extra = "") => `<a href="${href}" data-href="${href}"><span class="ico">${ico}</span>${text}${extra}</a>`;
  const items = isStaff()
    ? `${link("#/dashboard", "▦", "Dashboard")}
       <div class="nav-label">Tickets</div>
       ${link("#/tickets?view=open", "☰", "All open", badge(c.open))}
       ${link("#/tickets?view=mine", "●", "Assigned to me", badge(c.mine))}
       ${link("#/tickets?view=unassigned", "○", "Unassigned", badge(c.unassigned))}
       ${link("#/tickets?view=overdue", "!", "Overdue", badge(c.overdue, true))}
       ${link("#/tickets?view=waiting", "…", "Waiting on student", badge(c.waiting))}
       ${link("#/tickets?view=all", "≡", "All tickets")}
       ${link("#/new", "+", "New ticket")}
       ${isAdmin() ? `<div class="nav-label">Admin</div>${link("#/users", "☺", "Users")}${link("#/settings", "⚙", "Settings")}` : ""}`
    : `${link("#/tickets", "☰", "My tickets")}${link("#/new", "+", "Report a problem")}`;
  nav.innerHTML = `
    <div class="nav-brand"><img src="icon-64.png" alt="">CampusDesk</div>
    ${items}
    <div class="nav-user">
      <div class="who">${esc(state.me.fullName)}</div>
      <div class="role">${esc(LABEL[state.me.role])} · ${esc(state.me.email)}</div>
      <div class="links"><button id="pwBtn">Change password</button><button id="logoutBtn">Sign out</button></div>
    </div>`;
  $("#logoutBtn").onclick = logout;
  $("#pwBtn").onclick = changePassword;
  markActiveNav();
}

function markActiveNav() {
  const h = location.hash || "";
  $$("#nav a").forEach((a) => {
    const href = a.dataset.href;
    const active = h === href || (href === "#/tickets" && h.startsWith("#/tickets")) ;
    a.classList.toggle("active", active);
  });
}

function parseHash() {
  const raw = (location.hash || "#/").slice(1);
  const [path, query = ""] = raw.split("?");
  return { parts: path.split("/").filter(Boolean), params: new URLSearchParams(query) };
}

async function route() {
  if (!state.me) return;
  markActiveNav();
  const { parts, params } = parseHash();
  const page = $("#page");
  page.innerHTML = `<div class="muted">Loading…</div>`;
  try {
    switch (parts[0]) {
      case "dashboard": return isStaff() ? await viewDashboard(page) : (location.hash = "#/tickets");
      case "new": return viewNewTicket(page);
      case "ticket": return await viewTicket(page, Number(parts[1]));
      case "users": return isAdmin() ? await viewUsers(page) : (location.hash = "#/tickets");
      case "settings": return isAdmin() ? await viewSettings(page) : (location.hash = "#/tickets");
      default: return isStaff() ? await viewStaffList(page, params) : await viewMyTickets(page);
    }
  } catch (e) {
    if (e.status === 404) page.innerHTML = `<div class="empty">That page or ticket doesn't exist.</div>`;
    else fail(e);
  }
}
window.addEventListener("hashchange", route);

// ================================================================== student: my tickets

async function viewMyTickets(page) {
  const res = await api("/api/tickets?sort=updated&size=200");
  const open = res.items.filter((t) => OPEN_STATUSES.includes(t.status));
  const resolved = res.items.filter((t) => t.status === "RESOLVED");
  const closed = res.items.filter((t) => t.status === "CLOSED");
  const card = (t) => `
    <div class="card tcard" data-id="${t.id}">
      <div class="main-col">
        <div class="t-title">${esc(t.title)}</div>
        <div class="t-sub">${t.number} · ${esc(t.categoryName)} · ${esc(t.building)}${t.room ? " " + esc(t.room) : ""} · updated ${when(t.updatedAt)}</div>
      </div>
      ${t.status === "RESOLVED" ? `<span class="small" style="color:var(--ok);font-weight:600">Please confirm the fix</span>` : ""}
      ${pill("st", t.status)}
    </div>`;
  const section = (title, list) => list.length ? `<h3 style="margin:22px 0 10px;font-size:14px">${title}</h3><div class="ticket-cards">${list.map(card).join("")}</div>` : "";
  page.innerHTML = `
    <div class="page-head">
      <div><h1>My tickets</h1><div class="sub">Hi ${esc(state.me.fullName.split(" ")[0])} - here's everything you've reported to IT.</div></div>
      <div class="spacer"></div>
      <a class="btn primary" href="#/new">+ Report a problem</a>
    </div>
    ${res.items.length ? section("Needs your attention", resolved) + section("Open", open) + section("Closed", closed)
      : `<div class="card empty">You haven't reported any problems yet.<br><br><a class="btn primary" href="#/new">Report a problem</a></div>`}`;
  $$(".tcard").forEach((c) => c.onclick = () => (location.hash = `#/ticket/${c.dataset.id}`));
}

// ================================================================== staff: ticket list

const VIEWS = {
  open: { title: "All open tickets", query: { status: OPEN_STATUSES.join(",") }, sort: "priority" },
  mine: { title: "Assigned to me", query: { status: OPEN_STATUSES.join(","), assignee: "me" }, sort: "due" },
  unassigned: { title: "Unassigned", query: { status: OPEN_STATUSES.join(","), assignee: "unassigned" }, sort: "oldest" },
  overdue: { title: "Overdue", query: { overdue: "true" }, sort: "due" },
  waiting: { title: "Waiting on student", query: { status: "WAITING_ON_USER" }, sort: "updated" },
  all: { title: "All tickets", query: {}, sort: "newest" },
};

async function viewStaffList(page, params) {
  const viewKey = VIEWS[params.get("view")] ? params.get("view") : "open";
  const view = VIEWS[viewKey];
  const f = {
    q: params.get("q") || "",
    priority: params.get("priority") || "",
    category: params.get("category") || "",
    assignee: params.get("assignee") ?? (view.query.assignee || ""),
    status: params.get("status") ?? (view.query.status || ""),
    sort: params.get("sort") || view.sort,
    page: Number(params.get("page") || 0),
  };
  const qs = new URLSearchParams({ sort: f.sort, page: f.page, size: 50 });
  if (f.q) qs.set("q", f.q);
  if (f.priority) qs.set("priority", f.priority);
  if (f.category) qs.set("category", f.category);
  if (f.assignee) qs.set("assignee", f.assignee);
  if (f.status) qs.set("status", f.status);
  if (view.query.overdue) qs.set("overdue", "true");
  const res = await api(`/api/tickets?${qs}`);

  const opt = (v, label, cur) => `<option value="${esc(v)}" ${String(cur) === String(v) ? "selected" : ""}>${esc(label)}</option>`;
  const statusOptions = [["", "Any status"], [OPEN_STATUSES.join(","), "Any open"], ...Object.keys(LABEL).slice(0, 6).map((s) => [s, LABEL[s]])];
  page.innerHTML = `
    <div class="page-head">
      <div><h1>${view.title}</h1><div class="sub">${res.total} ticket${res.total === 1 ? "" : "s"}</div></div>
      <div class="spacer"></div>
      <a class="btn primary" href="#/new">+ New ticket</a>
    </div>
    <div class="filters">
      <input class="input search" id="fq" placeholder="Search title, description, messages, student…" value="${esc(f.q)}">
      <select class="input" id="fstatus">${statusOptions.map(([v, l]) => opt(v, l, f.status)).join("")}</select>
      <select class="input" id="fpriority">${opt("", "Any priority", f.priority)}${state.meta.priorities.map((p) => opt(p, LABEL[p], f.priority)).join("")}</select>
      <select class="input" id="fcategory">${opt("", "Any category", f.category)}${state.meta.categories.map((c) => opt(c.id, c.name, f.category)).join("")}</select>
      <select class="input" id="fassignee">${opt("", "Anyone", f.assignee)}${opt("me", "Me", f.assignee)}${opt("unassigned", "Unassigned", f.assignee)}${state.staff.map((u) => opt(u.id, u.fullName, f.assignee)).join("")}</select>
      <select class="input" id="fsort">${[["priority", "Priority"], ["due", "Due soonest"], ["newest", "Newest"], ["oldest", "Oldest"], ["updated", "Recently updated"]].map(([v, l]) => opt(v, "Sort: " + l, f.sort)).join("")}</select>
    </div>
    <div class="card">
      ${res.items.length ? `
      <table class="list">
        <thead><tr><th style="width:92px">Ticket</th><th>Issue</th><th>Status</th><th>Priority</th><th>Assignee</th><th>Due</th></tr></thead>
        <tbody>${res.items.map((t) => `
          <tr data-id="${t.id}" class="${t.sla === "OVERDUE" ? "overdue" : ""}">
            <td class="t-num">${t.number}</td>
            <td><div class="t-title">${esc(t.title)}</div>
                <div class="t-sub">${esc(t.requesterName)} · ${esc(t.categoryName)} · ${esc(t.building)}${t.room ? " " + esc(t.room) : ""} · ${when(t.createdAt)}</div></td>
            <td>${pill("st", t.status)}</td>
            <td>${pill("pr", t.priority)}</td>
            <td>${t.assigneeName ? esc(t.assigneeName) : `<span class="faint">Unassigned</span>`}</td>
            <td>${OPEN_STATUSES.includes(t.status)
              ? `<span class="sla sla-${t.sla}" title="${esc(fullDate(t.resolutionDueAt))}">${t.sla === "OVERDUE" ? "Overdue " + relative(t.resolutionDueAt).replace(" ago", "") : relative(t.resolutionDueAt)}</span>`
              : `<span class="sla sla-${t.sla}">${LABEL[t.sla]}</span>`}</td>
          </tr>`).join("")}
        </tbody>
      </table>
      ${res.totalPages > 1 ? `<div class="pager"><span class="muted small">Page ${res.page + 1} of ${res.totalPages}</span>
        <button class="btn small" id="prev" ${res.page === 0 ? "disabled" : ""}>‹ Prev</button>
        <button class="btn small" id="next" ${res.page + 1 >= res.totalPages ? "disabled" : ""}>Next ›</button></div>` : ""}`
      : `<div class="empty">No tickets match.${viewKey === "overdue" ? " Nothing is overdue - nice." : ""}</div>`}
    </div>`;

  const go = (changes) => {
    const p = new URLSearchParams({ view: viewKey, q: f.q, priority: f.priority, category: f.category,
      assignee: f.assignee, status: f.status, sort: f.sort, page: 0, ...changes });
    for (const [k, v] of [...p]) if (v === "" && !["assignee", "status"].includes(k)) p.delete(k);
    location.hash = `#/tickets?${p}`;
  };
  let t;
  $("#fq").oninput = (e) => { clearTimeout(t); t = setTimeout(() => go({ q: e.target.value }), 350); };
  $("#fstatus").onchange = (e) => go({ status: e.target.value });
  $("#fpriority").onchange = (e) => go({ priority: e.target.value });
  $("#fcategory").onchange = (e) => go({ category: e.target.value });
  $("#fassignee").onchange = (e) => go({ assignee: e.target.value });
  $("#fsort").onchange = (e) => go({ sort: e.target.value });
  $("#prev") && ($("#prev").onclick = () => go({ page: f.page - 1 }));
  $("#next") && ($("#next").onclick = () => go({ page: f.page + 1 }));
  $$("tbody tr").forEach((r) => r.onclick = () => (location.hash = `#/ticket/${r.dataset.id}`));
  if (f.q) { const el = $("#fq"); el.focus(); el.setSelectionRange(el.value.length, el.value.length); }
}

// ================================================================== new ticket

function viewNewTicket(page) {
  let device = "";
  page.innerHTML = `
    <div class="page-head"><div><h1>${isStaff() ? "New ticket" : "Report a problem"}</h1>
      <div class="sub">Tell IT what's wrong and where - the more detail, the faster the fix.</div></div></div>
    <form class="card card-pad form-card" id="newForm" novalidate>
      <div class="field"><label>What's the problem?</label>
        <input class="input" name="title" maxlength="150" placeholder="e.g. Can't connect to campus Wi-Fi in the library"></div>
      <div class="field"><label>Category</label>
        <select class="input" name="categoryId"><option value="">Choose…</option>
          ${state.meta.categories.filter((c) => c.active).map((c) => `<option value="${c.id}">${esc(c.name)}</option>`).join("")}</select></div>
      <div class="field"><label>Details</label>
        <textarea class="input" name="description" rows="6" placeholder="What happened? Any error message? When did it start? What have you tried?"></textarea></div>
      <div class="field"><label>Device</label>
        <div class="chips device-chips">${state.meta.deviceTypes.map((d) => `<button type="button" class="chip" data-device="${d}">${esc(LABEL[d])}</button>`).join("")}</div></div>
      <div class="field"><label>Device details <span class="faint">(optional)</span></label>
        <input class="input" name="deviceDetails" placeholder="e.g. Dell XPS 13, Windows 11 - or the lab PC asset tag"></div>
      <div class="two">
        <div class="field"><label>Building</label>
          <input class="input" name="building" list="buildings" placeholder="e.g. Library">
          <datalist id="buildings">${state.meta.buildings.map((b) => `<option value="${esc(b)}">`).join("")}</datalist></div>
        <div class="field"><label>Room <span class="faint">(optional)</span></label><input class="input" name="room" placeholder="e.g. B204"></div>
      </div>
      <div class="error-box" id="newError" hidden></div>
      <div class="row"><button class="btn primary" type="submit">Submit ticket</button><a class="btn" href="#/tickets">Cancel</a></div>
    </form>`;
  $$("[data-device]").forEach((b) => b.onclick = () => {
    device = b.dataset.device;
    $$("[data-device]").forEach((x) => x.classList.toggle("on", x === b));
  });
  $("#newForm").oninput = () => ($("#newError").hidden = true);
  $("#newForm").onsubmit = async (e) => {
    e.preventDefault();
    const body = Object.fromEntries(new FormData(e.target));
    body.categoryId = body.categoryId ? Number(body.categoryId) : null;
    body.deviceType = device || null;
    try {
      const d = await api("/api/tickets", send("POST", body));
      toast(`Ticket ${d.ticket.number} submitted`);
      state.meta = await api("/api/meta");   // new building may now be suggested
      if (isStaff()) refreshCounts().then(renderNav).catch(() => {});
      location.hash = `#/ticket/${d.ticket.id}`;
    } catch (err) {
      $("#newError").textContent = err.message;
      $("#newError").hidden = false;
    }
  };
}

// ================================================================== ticket detail

/** Button wording for each possible move, from this user's point of view. */
function transitionLabel(from, to) {
  const student = !isStaff();
  if (to === "IN_PROGRESS") {
    if (from === "NEW") return "Start working";
    if (from === "WAITING_ON_USER") return "Resume";
    if (from === "RESOLVED" || from === "CLOSED") return student ? "Still not fixed - reopen" : "Reopen";
    return "Mark in progress";
  }
  if (to === "WAITING_ON_USER") return "Wait on student";
  if (to === "RESOLVED") return "Resolve…";
  if (to === "CLOSED") {
    if (from === "RESOLVED") return student ? "Confirm it's fixed" : "Close ticket";
    return student ? "Withdraw ticket…" : "Close without fixing…";
  }
  return LABEL[to];
}
const needsReason = (from, to) => to === "RESOLVED" || (to === "CLOSED" && from !== "RESOLVED");

async function viewTicket(page, id) {
  const d = await api(`/api/tickets/${id}`);
  const t = d.ticket;
  let internal = false;

  const timeline = d.timeline.map((c) => {
    const intern = c.visibility === "INTERNAL";
    if (c.kind === "EVENT") {
      return `<div class="event ${intern ? "internal" : ""}"><span class="dot"></span>${esc(c.body)}
                <span class="faint">· ${when(c.createdAt)}</span>${intern ? ` <span class="tag-internal">IT only</span>` : ""}</div>`;
    }
    return `<div class="msg ${intern ? "internal" : ""}">
      <div class="avatar av-${c.authorRole}">${esc(initials(c.authorName))}</div>
      <div class="msg-body">
        <div class="msg-head"><b>${esc(c.authorName)}</b><span>${esc(LABEL[c.authorRole])}</span><span>· ${when(c.createdAt)}</span>
          ${intern ? `<span class="tag-internal">Internal note</span>` : ""}</div>
        <div class="msg-text">${esc(c.body)}</div>
      </div></div>`;
  }).join("");

  const statusButtons = [...d.allowedStatuses].map((to) =>
    `<button class="btn small ${to === "RESOLVED" || (to === "CLOSED" && t.status === "RESOLVED") ? "primary" : ""}" data-to="${to}">${esc(transitionLabel(t.status, to))}</button>`).join("");
  const assigneeControl = d.canAssign
    ? `<select class="input" id="assignSel"><option value="">Unassigned</option>${state.staff.map((u) =>
        `<option value="${u.id}" ${u.id === t.assigneeId ? "selected" : ""}>${esc(u.fullName)}</option>`).join("")}</select>`
    : `<div>${t.assigneeName ? esc(t.assigneeName) : `<span class="faint">Unassigned</span>`}</div>
       ${d.canTake ? `<button class="btn small primary" id="takeBtn" style="margin-top:8px">Assign to me</button>` : ""}`;
  const slaLine = (label, due, done) => `<div class="line"><span>${label}</span><span>${done
      ? `<span style="color:${new Date(done) <= new Date(due) ? "var(--ok)" : "var(--danger)"}">${fullDate(done)}</span>`
      : `<span title="${esc(fullDate(due))}">${new Date(due) < new Date() ? `<span style="color:var(--danger)">overdue (${relative(due)})</span>` : relative(due)}</span>`}</span></div>`;

  page.innerHTML = `
    <div class="detail-head">
      <div class="crumbs small"><a href="${isStaff() ? "#/tickets?view=open" : "#/tickets"}">← Tickets</a> · ${t.number}</div>
      <h1>${esc(t.title)}</h1>
      <div class="meta">${pill("st", t.status)} ${isStaff() ? pill("pr", t.priority) : ""}
        <span class="sla sla-${t.sla}">${esc(LABEL[t.sla])}</span>
        <span class="muted small">Opened ${when(t.createdAt)} by ${esc(t.requesterName)}</span></div>
    </div>
    <div class="detail">
      <div>
        <div class="card card-pad">
          <div class="desc">${esc(t.description)}</div>
          <div class="facts">
            <div><span class="lbl">Category</span>${esc(t.categoryName)}</div>
            <div><span class="lbl">Device</span>${esc(LABEL[t.deviceType])}${t.deviceDetails ? `<div class="small muted">${esc(t.deviceDetails)}</div>` : ""}</div>
            <div><span class="lbl">Location</span>${esc(t.building)}${t.room ? ", room " + esc(t.room) : ""}</div>
          </div>
          ${t.resolution ? `<div class="resolution"><span class="lbl">${t.status === "CLOSED" && !t.resolvedAt ? "Closed because" : "Resolution"}</span><div class="desc">${esc(t.resolution)}</div></div>` : ""}
        </div>
        <div class="timeline">${timeline}</div>
        ${t.status !== "CLOSED" || isStaff() ? `
        <div class="composer card card-pad">
          <textarea class="input" id="msg" rows="3" placeholder="${isStaff() ? "Reply to the student, or add troubleshooting notes…" : "Add more details or reply to IT…"}"></textarea>
          <div class="row">
            ${d.canWriteInternal ? `<div class="toggle"><button class="on" id="pubBtn">Reply to student</button><button id="intBtn">Internal note</button></div>` : ""}
            <span class="spacer"></span>
            <button class="btn primary" id="sendBtn">Send</button>
          </div>
        </div>` : ""}
      </div>

      <aside class="side">
        <div class="card">
          ${statusButtons ? `<div class="side-row"><span class="lbl">Actions</span><div class="actions">${statusButtons}</div></div>` : ""}
          ${isStaff() ? `
          <div class="side-row"><span class="lbl">Assignee</span>${assigneeControl}</div>
          <div class="side-row"><span class="lbl">Priority</span>${d.canChangePriority
            ? `<select class="input" id="prioSel">${state.meta.priorities.map((p) => `<option value="${p}" ${p === t.priority ? "selected" : ""}>${LABEL[p]}</option>`).join("")}</select>`
            : pill("pr", t.priority)}</div>
          <div class="side-row"><span class="lbl">Category</span>${d.canChangeCategory
            ? `<select class="input" id="catSel">${state.meta.categories.filter((c) => c.active || c.id === t.categoryId).map((c) => `<option value="${c.id}" ${c.id === t.categoryId ? "selected" : ""}>${esc(c.name)}</option>`).join("")}</select>`
            : esc(t.categoryName)}</div>
          <div class="side-row"><span class="lbl">Requester</span>
            <div>${esc(t.requesterName)}</div>
            <div class="small muted">${esc(t.requesterEmail)}${t.requesterStudentNumber ? " · #" + esc(t.requesterStudentNumber) : ""}</div></div>`
          : `<div class="side-row"><span class="lbl">Assigned to</span>${t.assigneeName ? esc(t.assigneeName) : `<span class="faint">Not yet assigned</span>`}</div>`}
        </div>
        <div class="card sla-box"><div class="side-row">
          <span class="lbl">Service level (${esc(LABEL[t.priority])})</span>
          ${slaLine("First response", t.responseDueAt, t.firstResponseAt)}
          ${slaLine("Resolution", t.resolutionDueAt, t.resolvedAt)}
          <div class="line"><span>Last update</span><span>${when(t.updatedAt)}</span></div>
        </div></div>
      </aside>
    </div>`;

  // ---- wire up
  const refresh = async () => { await viewTicket(page, id); if (isStaff()) refreshCounts().then(renderNav).catch(() => {}); };
  const doStatus = async (to, resolution) => {
    await api(`/api/tickets/${id}/status`, send("PATCH", { status: to, resolution }));
    toast(`${LABEL[to]}`);
    await refresh();
  };
  $$("[data-to]").forEach((b) => b.onclick = () => {
    const to = b.dataset.to;
    if (!needsReason(t.status, to)) return doStatus(to).catch(fail);
    const resolving = to === "RESOLVED";
    openDialog({
      title: resolving ? "Resolve ticket" : isStaff() ? "Close without fixing" : "Withdraw ticket",
      okLabel: resolving ? "Mark resolved" : isStaff() ? "Close ticket" : "Withdraw",
      body: `<p class="muted" style="margin-top:0">${resolving
          ? "Explain what fixed it. The student will see this and can confirm or reopen."
          : isStaff() ? "Say why (duplicate, not an IT issue, …). The student will see this." : "Let IT know why you're withdrawing it (e.g. fixed itself)."}</p>
        <textarea class="input" id="reason" rows="5"></textarea>`,
      onOk: async () => { await doStatus(to, $("#reason").value); },
    });
  });
  $("#assignSel") && ($("#assignSel").onchange = async (e) => {
    try {
      await api(`/api/tickets/${id}/assignee`, send("PATCH", { assigneeId: e.target.value ? Number(e.target.value) : null }));
      toast("Assignment updated");
      await refresh();
    } catch (err) { fail(err); await refresh(); }
  });
  $("#takeBtn") && ($("#takeBtn").onclick = async () => {
    try { await api(`/api/tickets/${id}/assignee`, send("PATCH", { assigneeId: state.me.id })); toast("Ticket is yours"); await refresh(); }
    catch (err) { fail(err); }
  });
  $("#prioSel") && ($("#prioSel").onchange = async (e) => {
    try { await api(`/api/tickets/${id}/priority`, send("PATCH", { priority: e.target.value })); toast("Priority updated - SLA deadlines recalculated"); await refresh(); }
    catch (err) { fail(err); await refresh(); }
  });
  $("#catSel") && ($("#catSel").onchange = async (e) => {
    try { await api(`/api/tickets/${id}/category`, send("PATCH", { categoryId: Number(e.target.value) })); toast("Category updated"); await refresh(); }
    catch (err) { fail(err); await refresh(); }
  });
  if ($("#pubBtn")) {
    $("#pubBtn").onclick = () => { internal = false; $("#pubBtn").classList.add("on"); $("#intBtn").classList.remove("on", "internal"); $("#msg").placeholder = "Reply to the student…"; };
    $("#intBtn").onclick = () => { internal = true; $("#intBtn").classList.add("on", "internal"); $("#pubBtn").classList.remove("on"); $("#msg").placeholder = "Troubleshooting notes - only IT staff will see this"; };
  }
  $("#sendBtn") && ($("#sendBtn").onclick = async () => {
    const body = $("#msg").value.trim();
    if (!body) return;
    try {
      await api(`/api/tickets/${id}/comments`, send("POST", { body, internal }));
      toast(internal ? "Internal note added" : "Message sent");
      await refresh();
      window.scrollTo(0, document.body.scrollHeight);
    } catch (err) { fail(err); }
  });
}

// ================================================================== dashboard

async function viewDashboard(page) {
  const d = await api("/api/dashboard");
  const s = d.summary;
  const kpi = (label, value, suffix = "", alert = false) =>
    `<div class="card kpi ${alert ? "alert" : ""}"><div class="lbl">${label}</div><div class="v">${value ?? "-"}${value != null && suffix ? `<small> ${suffix}</small>` : ""}</div></div>`;
  const bars = (rows, colorFor) => {
    const max = Math.max(1, ...rows.map((r) => r.count));
    return rows.length ? rows.map((r) => `
      <div class="hbar"><span>${esc(LABEL[r.label] || r.label)}</span>
        <div class="track"><div class="fill" style="width:${(100 * r.count) / max}%;${colorFor ? `background:${colorFor(r.label)}` : ""}"></div></div>
        <span class="n">${r.count}</span></div>`).join("") : `<div class="muted small">No open tickets.</div>`;
  };
  const prColor = { URGENT: "var(--danger)", HIGH: "var(--warn)", MEDIUM: "#7c8aa0", LOW: "#b6bfcc" };

  page.innerHTML = `
    <div class="page-head"><div><h1>Dashboard</h1><div class="sub">Live help-desk numbers · last 30 days where noted</div></div></div>
    <div class="kpis">
      ${kpi("Open tickets", s.open)}
      ${kpi("Unassigned", s.unassigned, "", s.unassigned > 0)}
      ${kpi("Overdue", s.overdue, "", s.overdue > 0)}
      ${kpi("SLA met (30d)", s.sla_met_percent, "%")}
      ${kpi("Avg resolution (30d)", s.avg_resolution_hours, "h")}
      ${kpi("Avg first response (30d)", s.avg_response_hours, "h")}
    </div>
    <div class="grid-2">
      <div class="card card-pad"><div class="row"><h3 style="margin:0">Created vs resolved - last 14 days</h3><span class="spacer"></span>
        <div class="legend"><span><i style="background:var(--accent)"></i>Created</span><span><i style="background:#9ad1b5"></i>Resolved</span></div></div>
        ${dayChart(d.perDay)}</div>
      <div class="card card-pad"><h3>Open by priority</h3>${bars(d.openByPriority, (l) => prColor[l])}
        <h3 style="margin-top:18px">Open by status</h3>${bars(d.openByStatus)}</div>
    </div>
    <div class="grid-2">
      <div class="card"><div class="card-pad" style="padding-bottom:6px"><h3 style="margin:0">Technician workload</h3></div>
        <table class="list"><thead><tr><th title="Ranked by tickets resolved in the last 30 days">Rank</th><th>Name</th><th>Open</th><th>Overdue</th><th>Resolved (30d)</th></tr></thead>
        <tbody>${d.technicians.map((u) => `<tr data-id="${u.id}"><td class="t-num">${u.rank}</td><td>${esc(u.name)} <span class="faint small">${esc(LABEL[u.role])}</span></td>
          <td>${u.open}</td><td>${u.overdue ? `<span style="color:var(--danger);font-weight:600">${u.overdue}</span>` : 0}</td><td>${u.resolved30}</td></tr>`).join("")}</tbody></table></div>
      <div class="card card-pad"><h3>Tickets by category (30 days)</h3>${bars(d.byCategory)}</div>
    </div>`;
  $$("tbody tr[data-id]").forEach((r) => r.onclick = () => (location.hash = `#/tickets?view=open&assignee=${r.dataset.id}`));
}

/** Small SVG grouped bar chart (no chart library needed). */
function dayChart(rows) {
  const W = 640, H = 200, pad = 26, n = rows.length || 1;
  const max = Math.max(1, ...rows.map((r) => Math.max(r.created, r.resolved)));
  const slot = (W - pad) / n, bw = Math.max(4, slot * 0.32);
  const y = (v) => H - pad - ((H - pad * 1.6) * v) / max;
  const bars = rows.map((r, i) => {
    const x = pad + i * slot + slot * 0.15;
    const day = new Date(r.label + "T12:00:00");
    const lbl = i % 2 === 0 ? `<text x="${x + bw}" y="${H - 8}" text-anchor="middle">${day.toLocaleDateString([], { month: "short", day: "numeric" })}</text>` : "";
    return `<rect x="${x}" y="${y(r.created)}" width="${bw}" height="${H - pad - y(r.created)}" rx="2" fill="var(--accent)"><title>${r.label}: ${r.created} created</title></rect>
            <rect x="${x + bw + 2}" y="${y(r.resolved)}" width="${bw}" height="${H - pad - y(r.resolved)}" rx="2" fill="#9ad1b5"><title>${r.label}: ${r.resolved} resolved</title></rect>${lbl}`;
  }).join("");
  const grid = [0, 0.5, 1].map((f) => { const v = Math.round(max * f), yy = y(v);
    return `<line x1="${pad}" x2="${W}" y1="${yy}" y2="${yy}" stroke="#eef1f5"/><text x="${pad - 6}" y="${yy + 3}" text-anchor="end">${v}</text>`; }).join("");
  return `<svg class="chart" viewBox="0 0 ${W} ${H}" width="100%" preserveAspectRatio="none" style="height:210px;margin-top:10px">${grid}${bars}</svg>`;
}

// ================================================================== users (admin)

async function viewUsers(page) {
  const params = parseHash().params;
  const role = params.get("role") || "";
  const users = await api(`/api/users${role ? "?role=" + role : ""}`);
  page.innerHTML = `
    <div class="page-head"><div><h1>Users</h1><div class="sub">${users.length} account${users.length === 1 ? "" : "s"}</div></div>
      <div class="spacer"></div><button class="btn primary" id="addUser">+ Add user</button></div>
    <div class="filters"><div class="chips">${[["", "Everyone"], ["ADMIN", "IT Admins"], ["TECHNICIAN", "Technicians"], ["STUDENT", "Students"]].map(([v, l]) =>
      `<button class="chip ${role === v ? "on" : ""}" data-role="${v}">${l}</button>`).join("")}</div></div>
    <div class="card"><table class="list">
      <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Student #</th><th>Status</th><th>Last sign-in</th><th></th></tr></thead>
      <tbody>${users.map((u) => `<tr data-id="${u.id}" style="cursor:default">
        <td class="t-title">${esc(u.fullName)}${u.id === state.me.id ? ` <span class="faint small">(you)</span>` : ""}</td>
        <td>${esc(u.email)}</td><td>${esc(LABEL[u.role])}</td><td>${esc(u.studentNumber || "")}</td>
        <td>${u.active ? `<span class="pill st-RESOLVED">Active</span>` : `<span class="pill st-CLOSED">Deactivated</span>`}</td>
        <td class="muted small">${u.lastLoginAt ? when(u.lastLoginAt) : "Never"}</td>
        <td style="text-align:right;white-space:nowrap"><button class="btn small" data-edit="${u.id}">Edit</button>
          <button class="btn small" data-pw="${u.id}">Reset password</button></td></tr>`).join("")}</tbody>
    </table></div>`;
  $$("[data-role]").forEach((b) => b.onclick = () => (location.hash = `#/users${b.dataset.role ? "?role=" + b.dataset.role : ""}`));
  const roleOptions = (cur) => ["STUDENT", "TECHNICIAN", "ADMIN"].map((r) => `<option value="${r}" ${r === cur ? "selected" : ""}>${LABEL[r]}</option>`).join("");
  const reload = async () => { state.staff = await api("/api/users/assignable"); viewUsers(page); };

  $("#addUser").onclick = () => openDialog({
    title: "Add user", okLabel: "Create",
    body: `<div class="field"><label>Full name</label><input class="input" id="uName"></div>
      <div class="field"><label>Email</label><input class="input" id="uEmail" type="email"></div>
      <div class="two"><div class="field"><label>Role</label><select class="input" id="uRole">${roleOptions("TECHNICIAN")}</select></div>
        <div class="field" id="uSnWrap" hidden><label>Student number</label><input class="input" id="uSn"></div></div>
      <div class="field"><label>Temporary password</label><input class="input" id="uPw" type="text"><span class="hint">Share it with them; they can change it after signing in.</span></div>`,
    onOk: async () => {
      await api("/api/users", send("POST", { fullName: $("#uName").value, email: $("#uEmail").value, role: $("#uRole").value,
        studentNumber: $("#uSn").value, password: $("#uPw").value }));
      toast("User created"); await reload();
    },
  });

  $$("[data-edit]").forEach((b) => b.onclick = () => {
    const u = users.find((x) => x.id === Number(b.dataset.edit));
    openDialog({
      title: `Edit ${u.fullName}`,
      body: `<div class="field"><label>Full name</label><input class="input" id="eName" value="${esc(u.fullName)}"></div>
        <div class="two"><div class="field"><label>Role</label><select class="input" id="eRole">${roleOptions(u.role)}</select></div>
          <div class="field" id="eSnWrap" ${u.role === "STUDENT" ? "" : "hidden"}><label>Student number</label><input class="input" id="eSn" value="${esc(u.studentNumber || "")}"></div></div>
        <label class="row" style="gap:8px;margin:4px 0 8px"><input type="checkbox" id="eActive" ${u.active ? "checked" : ""}> Account is active (can sign in)</label>`,
      onOk: async () => {
        await api(`/api/users/${u.id}`, send("PATCH", { fullName: $("#eName").value, role: $("#eRole").value,
          studentNumber: $("#eSn").value || null, active: $("#eActive").checked }));
        toast("User updated"); await reload();
      },
    });
  });
  $$("[data-pw]").forEach((b) => b.onclick = () => {
    const u = users.find((x) => x.id === Number(b.dataset.pw));
    openDialog({
      title: `Reset password for ${u.fullName}`, okLabel: "Set password",
      body: `<div class="field"><label>New temporary password</label><input class="input" id="rPw" type="text"><span class="hint">At least 8 characters.</span></div>`,
      onOk: async () => { await api(`/api/users/${u.id}/password`, send("POST", { password: $("#rPw").value })); toast("Password reset"); },
    });
  });
}

// In the add/edit user dialogs, show the student-number field only for students.
// (One listener for the whole page, registered once.)
document.addEventListener("change", (e) => {
  if (e.target.id === "uRole" || e.target.id === "eRole") {
    const wrap = $(e.target.id === "uRole" ? "#uSnWrap" : "#eSnWrap");
    if (wrap) wrap.hidden = e.target.value !== "STUDENT";
  }
});

// ================================================================== settings (admin)

async function viewSettings(page) {
  const meta = await api("/api/meta");
  state.meta = meta;
  const hours = (m) => +(m / 60).toFixed(2);
  page.innerHTML = `
    <div class="page-head"><div><h1>Settings</h1><div class="sub">Service levels and ticket categories</div></div></div>
    <div class="grid-2">
      <div class="card"><div class="card-pad" style="padding-bottom:4px"><h3 style="margin:0">SLA targets</h3>
        <p class="muted small">How quickly IT should respond to and resolve tickets at each priority. Changes apply to new tickets and to tickets whose priority is changed.</p></div>
        <table class="list"><thead><tr><th>Priority</th><th>First response (hours)</th><th>Resolution (hours)</th><th></th></tr></thead>
        <tbody>${meta.sla.map((p) => `<tr style="cursor:default"><td>${pill("pr", p.priority)}</td>
          <td><input class="input" style="width:110px" type="number" min="0.25" step="0.25" id="r-${p.priority}" value="${hours(p.responseMinutes)}"></td>
          <td><input class="input" style="width:110px" type="number" min="0.25" step="0.25" id="s-${p.priority}" value="${hours(p.resolutionMinutes)}"></td>
          <td><button class="btn small" data-sla="${p.priority}">Save</button></td></tr>`).join("")}</tbody></table></div>
      <div class="card"><div class="card-pad" style="padding-bottom:4px"><h3 style="margin:0">Categories</h3>
        <p class="muted small">Hidden categories stay on old tickets but can't be picked for new ones.</p></div>
        <table class="list"><tbody>${meta.categories.map((c) => `<tr style="cursor:default">
          <td>${esc(c.name)} ${c.active ? "" : `<span class="pill st-CLOSED">Hidden</span>`}</td>
          <td style="text-align:right"><button class="btn small" data-rename="${c.id}">Rename</button>
            <button class="btn small" data-toggle="${c.id}">${c.active ? "Hide" : "Show"}</button></td></tr>`).join("")}</tbody></table>
        <div class="card-pad row"><input class="input" id="newCat" placeholder="New category name"><button class="btn primary" id="addCat">Add</button></div></div>
    </div>`;
  $$("[data-sla]").forEach((b) => b.onclick = async () => {
    const p = b.dataset.sla;
    try {
      await api(`/api/sla/${p}`, send("PUT", { responseMinutes: Math.round(Number($(`#r-${p}`).value) * 60),
        resolutionMinutes: Math.round(Number($(`#s-${p}`).value) * 60) }));
      toast(`${LABEL[p]} SLA saved`);
    } catch (e) { fail(e); }
  });
  $$("[data-toggle]").forEach((b) => b.onclick = async () => {
    const c = meta.categories.find((x) => x.id === Number(b.dataset.toggle));
    try { await api(`/api/categories/${c.id}`, send("PATCH", { name: c.name, active: !c.active })); viewSettings(page); } catch (e) { fail(e); }
  });
  $$("[data-rename]").forEach((b) => b.onclick = () => {
    const c = meta.categories.find((x) => x.id === Number(b.dataset.rename));
    openDialog({
      title: "Rename category",
      body: `<div class="field"><label>Name</label><input class="input" id="catName" value="${esc(c.name)}"></div>`,
      onOk: async () => { await api(`/api/categories/${c.id}`, send("PATCH", { name: $("#catName").value, active: c.active })); viewSettings(page); },
    });
  });
  $("#addCat").onclick = async () => {
    const name = $("#newCat").value.trim();
    if (!name) return;
    try { await api("/api/categories", send("POST", { name })); toast("Category added"); viewSettings(page); } catch (e) { fail(e); }
  };
}

// ================================================================== boot

(async () => {
  try {
    const s = await api("/api/auth/state");
    state.signupAllowed = s.signupAllowed;
    if (s.setupRequired) return renderAuth("setup");
    if (!s.user) return renderAuth("login");
    state.me = s.user;
    await startApp();
  } catch (e) {
    $("#root").innerHTML = `<div class="auth"><div class="auth-card"><h1>Can't reach CampusDesk</h1><p class="lead">${esc(e.message)}</p></div></div>`;
  }
})();
