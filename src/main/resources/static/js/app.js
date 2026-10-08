/*
 * RecoverPay dashboard: a small single-page app on top of the REST API.
 * No framework and no build step on purpose: the browser loads this file as-is from Spring Boot's /static folder.
 *
 * Layout of this file:
 *   1. API client         fetch wrapper that turns the API's error body into a JS Error
 *   2. Formatting         money (INR), dates, labels and badges for the domain enums
 *   3. UI helpers         toasts, drawer, modal forms, charts
 *   4. Router             hash-based (#/overview, #/customers, ...)
 *   5. Pages              one render function per page
 *   6. Detail drawers     invoice, subscription and customer side panels
 */
'use strict';

/* ======================================================================
 * 1. API client
 * ==================================================================== */

class ApiError extends Error {
  constructor(status, body) {
    super((body && body.message) || `Request failed (${status})`);
    this.status = status;
    this.fieldErrors = (body && body.fieldErrors) || {};
  }
}

async function request(method, url, body) {
  const res = await fetch(url, {
    method,
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new ApiError(res.status, data);
  return data;
}

const api = {
  get: (url) => request('GET', url),
  post: (url, body) => request('POST', url, body),
  put: (url, body) => request('PUT', url, body),
  patch: (url, body) => request('PATCH', url, body),
};

/* ======================================================================
 * 2. Formatting
 * ==================================================================== */

const inrFmt = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const inrFmt2 = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 });
const numFmt = new Intl.NumberFormat('en-IN');

const inr = (v) => inrFmt.format(Number(v || 0));
const inrExact = (v) => inrFmt2.format(Number(v || 0));
const num = (v) => numFmt.format(Number(v || 0));
const pct = (v) => `${Number(v || 0).toFixed(1)}%`;
/** plural(1, 'invoice') -> "1 invoice", plural(3, 'invoice') -> "3 invoices". */
const plural = (n, word) => `${num(n)} ${word}${Number(n) === 1 ? '' : 's'}`;

function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

/** The API sends dates as "2026-10-07" and date-times as "2026-10-07T09:00:00" (IST, no offset). */
function parseDate(value) {
  if (!value) return null;
  return value.length === 10 ? new Date(`${value}T00:00:00`) : new Date(value);
}

function fmtDate(value) {
  const d = parseDate(value);
  return d ? d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '—';
}

function fmtDateTime(value) {
  const d = parseDate(value);
  return d ? d.toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }) : '—';
}

/** "in 3 days", "2 hours ago": easier to scan than timestamps in a queue. */
function relative(value) {
  const d = parseDate(value);
  if (!d) return '—';
  const diff = d.getTime() - Date.now();
  const abs = Math.abs(diff);
  const units = [['day', 86400000], ['hour', 3600000], ['minute', 60000]];
  const rtf = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
  for (const [unit, ms] of units) {
    if (abs >= ms || unit === 'minute') return rtf.format(Math.round(diff / ms), unit);
  }
  return '';
}

const REASONS = {
  INSUFFICIENT_BALANCE: { label: 'Insufficient balance', hard: false },
  MANDATE_REVOKED: { label: 'Mandate revoked', hard: true },
  LIMIT_EXCEEDED: { label: 'Limit exceeded', hard: false },
  BANK_DOWNTIME: { label: 'Bank downtime', hard: false },
  CARD_EXPIRED: { label: 'Card expired', hard: true },
};
const reasonLabel = (r) => (REASONS[r] ? REASONS[r].label : r || '—');

function reasonBadge(reason) {
  if (!reason) return '<span class="subtle">—</span>';
  const r = REASONS[reason] || { label: reason, hard: false };
  return `<span class="badge plain ${r.hard ? 'b-red' : 'b-amber'}" title="${r.hard ? 'Hard decline: the customer must act' : 'Soft decline: worth retrying'}">${esc(r.label)}</span>`;
}

const SUB_STATUS = {
  ACTIVE: ['Active', 'b-green'],
  PAST_DUE: ['Past due', 'b-amber'],
  PAUSED: ['Paused', 'b-grey'],
  CANCELLED: ['Cancelled', 'b-red'],
};
const INVOICE_STATUS = {
  PENDING: ['Pending', 'b-grey'],
  PAID: ['Paid', 'b-green'],
  FAILED: ['In recovery', 'b-amber'],
  RECOVERED: ['Recovered', 'b-brand'],
  WRITTEN_OFF: ['Written off', 'b-red'],
};
const RISK_BAND = { LOW: ['Low risk', 'b-green'], MEDIUM: ['Medium risk', 'b-amber'], HIGH: ['High risk', 'b-red'] };

function badge(map, key) {
  const [label, cls] = map[key] || [key, 'b-grey'];
  return `<span class="badge ${cls}">${esc(label)}</span>`;
}

const METHOD = { UPI_AUTOPAY: 'UPI AutoPay', CARD: 'Card' };
const CYCLE = { MONTHLY: 'Monthly', QUARTERLY: 'Quarterly', YEARLY: 'Yearly' };

const AVATAR_COLORS = ['#6366f1', '#0ea5e9', '#14b8a6', '#f59e0b', '#ec4899', '#8b5cf6', '#22c55e', '#ef4444'];
function avatar(name) {
  const initials = String(name || '?').split(/\s+/).map((p) => p[0]).slice(0, 2).join('').toUpperCase();
  let h = 0;
  for (const ch of String(name)) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return `<span class="avatar" style="background:${AVATAR_COLORS[h % AVATAR_COLORS.length]}">${esc(initials)}</span>`;
}

function who(name, sub) {
  return `<div class="who">${avatar(name)}<div><div class="cell-main">${esc(name)}</div>${sub ? `<div class="cell-sub">${esc(sub)}</div>` : ''}</div></div>`;
}

/* ======================================================================
 * 3. UI helpers
 * ==================================================================== */

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

const ICONS = {
  ok: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>',
  err: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><path d="M12 8v4M12 16h.01"/></svg>',
  x: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"><path d="M18 6 6 18M6 6l12 12"/></svg>',
  rupee: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 3h12M6 8h12M6 13l8.5 8M6 13h3a5 5 0 0 0 0-10"/></svg>',
  alert: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/><path d="M12 9v4M12 17h.01"/></svg>',
  up: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m22 7-8.5 8.5-5-5L2 17"/><path d="M16 7h6v6"/></svg>',
  target: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="6"/><circle cx="12" cy="12" r="2"/></svg>',
  plus: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>',
  db: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M3 5v14c0 1.7 4 3 9 3s9-1.3 9-3V5"/><path d="M3 12c0 1.7 4 3 9 3s9-1.3 9-3"/></svg>',
  inbox: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 12h-6l-2 3h-4l-2-3H2"/><path d="M5.5 5.1 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.5-6.9A2 2 0 0 0 16.8 4H7.2a2 2 0 0 0-1.7 1.1z"/></svg>',
  flask: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 3h6"/><path d="M10 3v6L4.5 18.5A1.7 1.7 0 0 0 6 21h12a1.7 1.7 0 0 0 1.5-2.5L14 9V3"/></svg>',
};

function toast(message, detail, type = 'ok') {
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.innerHTML = `${type === 'ok' ? ICONS.ok : ICONS.err}<div>${esc(message)}${detail ? `<small>${esc(detail)}</small>` : ''}</div>`;
  $('#toasts').appendChild(el);
  setTimeout(() => el.remove(), type === 'ok' ? 4000 : 7000);
}

function showError(err) {
  toast(err.message || 'Something went wrong', err.status ? `HTTP ${err.status}` : 'Is the service running?', 'err');
}

/** Run an async action with the button showing a spinner, so double clicks can't fire it twice. */
async function busy(button, fn) {
  const original = button.innerHTML;
  button.disabled = true;
  button.innerHTML = `<span class="spinner"></span>${button.textContent.trim() ? ` ${esc(button.textContent.trim())}` : ''}`;
  try {
    return await fn();
  } finally {
    button.disabled = false;
    button.innerHTML = original;
  }
}

const drawer = {
  open(kicker, title, html) {
    $('#drawer-kicker').textContent = kicker;
    $('#drawer-title').innerHTML = title;
    $('#drawer-body').innerHTML = html;
    $('#drawer').classList.add('open');
    $('#drawer').setAttribute('aria-hidden', 'false');
    $('#drawer-overlay').classList.add('open');
  },
  loading(kicker) {
    this.open(kicker, '<div class="skeleton" style="width:180px;height:20px"></div>',
      '<div class="skeleton" style="margin-bottom:12px"></div><div class="skeleton" style="width:70%"></div>');
  },
  close() {
    $('#drawer').classList.remove('open');
    $('#drawer').setAttribute('aria-hidden', 'true');
    $('#drawer-overlay').classList.remove('open');
  },
  body: () => $('#drawer-body'),
};

/**
 * Modal form. `fields` is a list of {name, label, type, value, options, hint, min, step}; on submit the values are
 * collected into an object and passed to onSubmit. Validation errors from the API are shown under each field.
 */
const modal = {
  onSubmit: null,
  open({ title, sub = '', fields = [], html = '', submitLabel = 'Save', danger = false, onSubmit }) {
    $('#modal-title').textContent = title;
    $('#modal-sub').textContent = sub;
    $('#modal-body').innerHTML = html + fields.map(renderField).join('');
    $('#modal-body').classList.toggle('hidden', !html && fields.length === 0);
    const submit = $('#modal-submit');
    submit.textContent = submitLabel;
    submit.className = `btn ${danger ? 'btn-danger' : 'btn-primary'}`;
    this.onSubmit = onSubmit;
    $('#modal').classList.add('open');
    $('#modal-overlay').classList.add('open');
    const first = $('#modal-body input, #modal-body select');
    if (first) setTimeout(() => first.focus(), 50);
  },
  close() {
    $('#modal').classList.remove('open');
    $('#modal-overlay').classList.remove('open');
  },
};

function renderField(f) {
  const id = `f-${f.name}`;
  let control;
  if (f.type === 'select') {
    control = `<select id="${id}" name="${f.name}">${f.options.map((o) =>
      `<option value="${esc(o.value)}" ${String(o.value) === String(f.value) ? 'selected' : ''}>${esc(o.label)}</option>`).join('')}</select>`;
  } else {
    control = `<input id="${id}" name="${f.name}" type="${f.type || 'text'}" value="${esc(f.value ?? '')}"
      ${f.min !== undefined ? `min="${f.min}"` : ''} ${f.max !== undefined ? `max="${f.max}"` : ''}
      ${f.step ? `step="${f.step}"` : ''} placeholder="${esc(f.placeholder || '')}">`;
  }
  return `<div class="field" data-field="${f.name}"><label for="${id}">${esc(f.label)}</label>${control}
    ${f.hint ? `<div class="hint">${esc(f.hint)}</div>` : ''}<div class="err"></div></div>`;
}

async function submitModal(event) {
  event.preventDefault();
  if (!modal.onSubmit) return;
  $$('#modal .field').forEach((f) => { f.classList.remove('invalid'); $('.err', f).textContent = ''; });
  const values = Object.fromEntries(new FormData($('#modal')).entries());
  try {
    await busy($('#modal-submit'), () => modal.onSubmit(values));
    modal.close();
  } catch (err) {
    let shown = false;
    for (const [name, msg] of Object.entries(err.fieldErrors || {})) {
      const field = $(`#modal .field[data-field="${name}"]`);
      if (field) { field.classList.add('invalid'); $('.err', field).textContent = msg; shown = true; }
    }
    if (!shown) showError(err);
  }
}

/* Charts: Chart.js instances are tracked so they can be destroyed when the page re-renders. */
const charts = [];
function destroyCharts() { while (charts.length) charts.pop().destroy(); }

function cssVar(name) { return getComputedStyle(document.documentElement).getPropertyValue(name).trim(); }

function makeChart(canvas, config) {
  if (!window.Chart) return;
  Chart.defaults.font.family = cssVar('--font');
  Chart.defaults.color = cssVar('--text-2');
  Chart.defaults.borderColor = cssVar('--border');
  charts.push(new Chart(canvas, config));
}

/* ======================================================================
 * 4. Router
 * ==================================================================== */

const ROUTES = {
  overview: { title: 'Overview', crumb: 'Dashboard', render: renderOverview },
  recovery: { title: 'Recovery queue', crumb: 'Dashboard', render: renderRecovery },
  subscriptions: { title: 'Subscriptions', crumb: 'Manage', render: renderSubscriptions },
  customers: { title: 'Customers', crumb: 'Manage', render: renderCustomers },
  plans: { title: 'Plans', crumb: 'Manage', render: renderPlans },
  simulation: { title: 'Simulation lab', crumb: 'Analyse', render: renderSimulation },
};

/** Per-page UI state (filters, page numbers) kept while the app is open. */
const state = {
  recovery: { status: 'FAILED', page: 0 },
  subscriptions: { status: '', page: 0 },
  customers: { page: 0 },
  simulation: { result: null, form: { customers: 1000, months: 6, seed: 42 } },
};

function currentRoute() {
  const name = location.hash.replace(/^#\//, '').split('?')[0];
  return ROUTES[name] ? name : 'overview';
}

async function render() {
  const name = currentRoute();
  const route = ROUTES[name];
  $$('#nav a').forEach((a) => a.classList.toggle('active', a.dataset.route === name));
  $('#page-title').textContent = route.title;
  $('#crumb').textContent = route.crumb;
  $('#sidebar').classList.remove('open');
  document.title = `${route.title} · RecoverPay`;
  destroyCharts();
  const view = $('#view');
  view.innerHTML = '<div class="card card-body"><div class="skeleton" style="width:40%;margin-bottom:12px"></div><div class="skeleton"></div></div>';
  try {
    await route.render(view);
  } catch (err) {
    view.innerHTML = `<div class="card"><div class="empty"><div class="empty-icon">${ICONS.alert}</div>
      <h3>Could not load this page</h3><p>${esc(err.message)}</p>
      <button class="btn" onclick="render()">Try again</button></div></div>`;
  }
  refreshQueueCount();
}

async function refreshQueueCount() {
  try {
    const o = await api.get('/api/analytics/overview');
    const el = $('#queue-count');
    el.textContent = o.invoicesInRecovery;
    el.classList.toggle('hidden', !o.invoicesInRecovery);
  } catch (e) { /* the page itself shows the error */ }
}

async function checkHealth() {
  try {
    const h = await api.get('/actuator/health');
    const up = h.status === 'UP';
    $('#health-dot').classList.toggle('off', !up);
    $('#health-text').textContent = up ? 'Service healthy' : `Service ${h.status}`;
  } catch (e) {
    $('#health-dot').classList.add('off');
    $('#health-text').textContent = 'Service unreachable';
  }
}

/* ======================================================================
 * 5. Pages
 * ==================================================================== */

function kpi(label, value, foot, tone, icon) {
  return `<div class="card kpi"><div class="kpi-label"><span class="kpi-icon tone-${tone}">${icon}</span>${esc(label)}</div>
    <div class="kpi-value num">${value}</div><div class="kpi-foot">${foot}</div></div>`;
}

function emptyState(icon, title, text, actionHtml = '') {
  return `<div class="empty"><div class="empty-icon">${icon}</div><h3>${esc(title)}</h3><p>${text}</p>${actionHtml}</div>`;
}

function pager(page, onChange) {
  if (!page || page.totalPages <= 1) {
    return `<div class="pager"><span>${num(page ? page.totalElements : 0)} total</span></div>`;
  }
  const from = page.page * page.size + 1;
  const to = Math.min(page.totalElements, from + page.content.length - 1);
  setTimeout(() => {
    $('#pg-prev').onclick = () => onChange(page.page - 1);
    $('#pg-next').onclick = () => onChange(page.page + 1);
  });
  return `<div class="pager"><span>${num(from)}–${num(to)} of ${num(page.totalElements)}</span><span class="spacer"></span>
    <button class="btn btn-sm" id="pg-prev" ${page.page === 0 ? 'disabled' : ''}>Previous</button>
    <button class="btn btn-sm" id="pg-next" ${page.page >= page.totalPages - 1 ? 'disabled' : ''}>Next</button></div>`;
}

/* ---------- Overview ---------- */

async function renderOverview(view) {
  const [o, analytics, queue] = await Promise.all([
    api.get('/api/analytics/overview'),
    api.get('/api/analytics/recovery'),
    api.get('/api/invoices?status=FAILED&size=6&sort=nextRetryAt,asc'),
  ]);

  if (o.customers === 0) {
    view.innerHTML = `<div class="card">${emptyState(ICONS.db, 'Welcome to RecoverPay',
      'The database is empty. Load a demo business with 5 plans and 48 customers on UPI AutoPay and cards. Today\'s billing runs straight away, so some payments fail and you can watch the recovery engine work on them.',
      '<button class="btn btn-primary" id="load-demo">Load demo data</button> <a class="btn" href="#/customers">Add data by hand</a>')
      .replace('class="empty"', 'class="empty hero-empty"')}</div>`;
    $('#load-demo').onclick = (e) => busy(e.currentTarget, async () => {
      try {
        const r = await api.post('/api/admin/jobs/demo-data');
        toast(`Demo data loaded: ${r.customersCreated} customers, ${r.plansCreated} plans`,
          `Billing charged ${r.billing.processed} invoices: ${r.billing.succeeded} paid, ${r.billing.failed} failed`);
        render();
      } catch (err) { showError(err); }
    });
    return;
  }

  const s = o.subscriptionsByStatus;
  const billed = (s.ACTIVE || 0) + (s.PAST_DUE || 0);
  view.innerHTML = `
    <div class="grid kpis">
      ${kpi('Monthly recurring revenue', inr(o.monthlyRecurringRevenue), `${plural(billed, 'billed subscription')}`, 'brand', ICONS.rupee)}
      ${kpi('Revenue at risk', inr(o.revenueAtRisk), `${plural(o.invoicesInRecovery, 'invoice')} in recovery`, 'amber', ICONS.alert)}
      ${kpi('Revenue recovered', inr(o.recoveredRevenueAllTime), `${plural(o.invoicesRecovered, 'invoice')} saved, ${num(o.invoicesWrittenOff)} written off`, 'green', ICONS.up)}
      ${kpi('Recovery rate', pct(analytics.recoveryRatePercent), `of ${inr(analytics.totalFailedRevenue)} failed in the last 6 months`, 'blue', ICONS.target)}
    </div>
    <div class="grid two-col">
      <div class="card">
        <div class="card-head"><div><h3>Failed vs recovered revenue</h3><div class="sub">By first failure reason, last 6 months</div></div></div>
        <div class="card-body">${analytics.byFailureReason.length
          ? '<div class="chart-box"><canvas id="reason-chart"></canvas></div>'
          : emptyState(ICONS.ok, 'No failed payments yet', 'When a charge fails it shows up here, grouped by why it failed.')}</div>
      </div>
      <div class="card">
        <div class="card-head"><div><h3>Subscriptions</h3><div class="sub">${num(Object.values(s).reduce((a, b) => a + b, 0))} total</div></div></div>
        <div class="card-body"><div class="chart-box"><canvas id="status-chart"></canvas></div></div>
      </div>
    </div>
    <div class="grid two-col">
      <div class="card">
        <div class="card-head"><div><h3>Next up in recovery</h3><div class="sub">Failed invoices, soonest retry first</div></div>
          <span class="spacer"></span><a class="btn btn-sm" href="#/recovery">View all</a></div>
        <div class="card-body flush">${queue.content.length ? invoiceTable(queue.content, { compact: true })
          : emptyState(ICONS.ok, 'Nothing to recover', 'Every invoice is paid. Run billing on a later date or load more customers to see failures.')}</div>
      </div>
      <div class="card">
        <div class="card-head"><h3>How smart recovery decides</h3></div>
        <div class="card-body">
          <ul class="factors">
            <li><b>Insufficient balance:</b> retry just after the customer's likely salary day, not blindly every few days.</li>
            <li><b>Bank downtime:</b> retry within hours, once the bank is back.</li>
            <li><b>Limit exceeded:</b> retry the next morning, after daily limits reset.</li>
            <li><b>Mandate revoked / card expired:</b> never retry blindly. Ask the customer to update their payment method.</li>
            <li>High churn-risk customers get fewer debit attempts and earlier reminders.</li>
          </ul>
          <div style="margin-top:14px"><a href="#/simulation">Compare smart vs fixed retries in the simulation lab →</a></div>
        </div>
      </div>
    </div>`;

  bindInvoiceRows(view);

  if (analytics.byFailureReason.length) {
    makeChart($('#reason-chart'), {
      type: 'bar',
      data: {
        labels: analytics.byFailureReason.map((r) => reasonLabel(r.reason)),
        datasets: [
          { label: 'Failed', data: analytics.byFailureReason.map((r) => r.failedRevenue), backgroundColor: cssVar('--amber'), borderRadius: 6, maxBarThickness: 34 },
          { label: 'Recovered', data: analytics.byFailureReason.map((r) => r.recoveredRevenue), backgroundColor: cssVar('--brand-2'), borderRadius: 6, maxBarThickness: 34 },
        ],
      },
      options: {
        maintainAspectRatio: false,
        plugins: { legend: { position: 'bottom', labels: { usePointStyle: true, boxWidth: 8 } },
          tooltip: { callbacks: { label: (c) => `${c.dataset.label}: ${inr(c.raw)}` } } },
        scales: { y: { ticks: { callback: (v) => inr(v) }, grid: { color: cssVar('--border') } }, x: { grid: { display: false } } },
      },
    });
  }
  const statusKeys = ['ACTIVE', 'PAST_DUE', 'PAUSED', 'CANCELLED'];
  makeChart($('#status-chart'), {
    type: 'doughnut',
    data: {
      labels: statusKeys.map((k) => SUB_STATUS[k][0]),
      datasets: [{ data: statusKeys.map((k) => s[k] || 0), borderWidth: 0,
        backgroundColor: [cssVar('--green'), cssVar('--amber'), cssVar('--text-3'), cssVar('--red')] }],
    },
    options: { maintainAspectRatio: false, cutout: '68%', plugins: { legend: { position: 'bottom', labels: { usePointStyle: true, boxWidth: 8 } } } },
  });
}

/* ---------- Recovery queue / invoices ---------- */

function invoiceTable(rows, { compact = false } = {}) {
  return `<div class="table-wrap"><table class="${compact ? 'compact' : ''}"><thead><tr>
    <th>Invoice</th><th>Customer</th><th class="right">Amount</th>${compact ? '' : '<th>Status</th>'}<th>Failure reason</th>
    ${compact ? '' : '<th class="right">Attempts</th>'}<th>${compact ? 'Next retry' : 'Next retry / outcome'}</th>
    ${compact ? '' : '<th>Recovery deadline</th>'}</tr></thead><tbody>
    ${rows.map((i) => `<tr class="clickable" data-invoice="${i.id}">
      <td><div class="cell-main mono">INV-${String(i.id).padStart(5, '0')}</div><div class="cell-sub">Due ${fmtDate(i.dueDate)}</div></td>
      <td>${who(i.customerName, i.planName)}</td>
      <td class="right num cell-main">${inr(i.amount)}</td>
      ${compact ? '' : `<td>${badge(INVOICE_STATUS, i.status)}</td>`}
      <td>${reasonBadge(i.lastFailureReason || i.initialFailureReason)}</td>
      ${compact ? '' : `<td class="right num">${i.attemptCount}</td>`}
      <td>${nextStep(i)}</td>
      ${compact ? '' : `<td>${i.status === 'FAILED' ? `<span title="${fmtDateTime(i.recoveryDeadline)}">${fmtDate(i.recoveryDeadline)}</span>` : '<span class="subtle">—</span>'}</td>`}
    </tr>`).join('')}</tbody></table></div>`;
}

function nextStep(i) {
  if (i.status === 'FAILED') {
    return i.nextRetryAt
      ? `<div class="cell-main">${relative(i.nextRetryAt)}</div><div class="cell-sub">${fmtDateTime(i.nextRetryAt)}</div>`
      : '<span class="badge plain b-red">Waiting for customer</span>';
  }
  if (i.paidAt) return `<div class="cell-main">Paid</div><div class="cell-sub">${fmtDateTime(i.paidAt)}</div>`;
  return '<span class="subtle">—</span>';
}

function bindInvoiceRows(root) {
  $$('[data-invoice]', root).forEach((row) => { row.onclick = () => openInvoice(Number(row.dataset.invoice)); });
}

async function renderRecovery(view) {
  const st = state.recovery;
  const o = await api.get('/api/analytics/overview');
  const query = st.status ? `status=${st.status}&` : '';
  const sort = st.status === 'FAILED' ? 'nextRetryAt,asc' : 'id,desc';
  const page = await api.get(`/api/invoices?${query}page=${st.page}&size=15&sort=${sort}`);
  const tabs = [
    ['FAILED', 'In recovery', o.invoicesInRecovery],
    ['RECOVERED', 'Recovered', o.invoicesRecovered],
    ['WRITTEN_OFF', 'Written off', o.invoicesWrittenOff],
    ['PAID', 'Paid first time', o.invoicesPaid],
    ['', 'All', null],
  ];
  view.innerHTML = `
    <div class="toolbar">
      <div class="tabs">${tabs.map(([k, label, n]) =>
        `<button class="tab ${st.status === k ? 'active' : ''}" data-status="${k}">${label}${n !== null ? `<span class="n">${num(n)}</span>` : ''}</button>`).join('')}</div>
      <span class="spacer"></span>
      ${st.status === 'FAILED' && o.invoicesInRecovery ? `<span class="muted">${inr(o.revenueAtRisk)} at risk</span>` : ''}
    </div>
    <div class="card">${page.content.length ? invoiceTable(page.content) + pager(page, (p) => { st.page = p; render(); })
      : emptyState(ICONS.inbox, 'No invoices here', st.status === 'FAILED'
        ? 'No failed payments are waiting for recovery right now.' : 'Invoices appear here once billing has run.')}</div>`;
  $$('.tab', view).forEach((t) => { t.onclick = () => { st.status = t.dataset.status; st.page = 0; render(); }; });
  bindInvoiceRows(view);
}

/* ---------- Subscriptions ---------- */

async function renderSubscriptions(view) {
  const st = state.subscriptions;
  const o = await api.get('/api/analytics/overview');
  const page = await api.get(`/api/subscriptions?${st.status ? `status=${st.status}&` : ''}page=${st.page}&size=15&sort=id,desc`);
  const s = o.subscriptionsByStatus;
  const total = Object.values(s).reduce((a, b) => a + b, 0);
  const tabs = [['', 'All', total], ['ACTIVE', 'Active', s.ACTIVE], ['PAST_DUE', 'Past due', s.PAST_DUE],
    ['PAUSED', 'Paused', s.PAUSED], ['CANCELLED', 'Cancelled', s.CANCELLED]];
  view.innerHTML = `
    <div class="toolbar">
      <div class="tabs">${tabs.map(([k, label, n]) =>
        `<button class="tab ${st.status === k ? 'active' : ''}" data-status="${k}">${label}<span class="n">${num(n)}</span></button>`).join('')}</div>
      <span class="spacer"></span>
      <button class="btn btn-primary" id="new-sub">${ICONS.plus} New subscription</button>
    </div>
    <div class="card">${page.content.length ? `<div class="table-wrap"><table><thead><tr>
        <th>Subscription</th><th>Customer</th><th>Plan</th><th>Status</th><th>Payment method</th><th>Next billing</th><th>Started</th>
      </tr></thead><tbody>${page.content.map((x) => `<tr class="clickable" data-sub="${x.id}">
        <td class="mono cell-main">SUB-${String(x.id).padStart(5, '0')}</td>
        <td>${who(x.customerName)}</td>
        <td>${esc(x.planName)}</td>
        <td>${badge(SUB_STATUS, x.status)}</td>
        <td>${METHOD[x.paymentMethod] || x.paymentMethod}</td>
        <td>${x.status === 'CANCELLED' ? '<span class="subtle">—</span>' : fmtDate(x.nextBillingDate)}</td>
        <td>${fmtDate(x.startDate)}</td></tr>`).join('')}</tbody></table></div>${pager(page, (p) => { st.page = p; render(); })}`
      : emptyState(ICONS.inbox, 'No subscriptions', 'Subscribe a customer to a plan to start billing them.')}</div>`;
  $$('.tab', view).forEach((t) => { t.onclick = () => { st.status = t.dataset.status; st.page = 0; render(); }; });
  $$('[data-sub]', view).forEach((r) => { r.onclick = () => openSubscription(Number(r.dataset.sub)); });
  $('#new-sub').onclick = () => newSubscription();
}

async function newSubscription(customerId) {
  try {
    const [customers, plans] = await Promise.all([api.get('/api/customers?size=500&sort=name'), api.get('/api/plans')]);
    const activePlans = plans.filter((p) => p.active);
    if (!customers.content.length || !activePlans.length) {
      toast('Add a customer and an active plan first', null, 'err');
      return;
    }
    modal.open({
      title: 'New subscription',
      sub: 'The first invoice is charged on the start date by the billing job.',
      submitLabel: 'Create subscription',
      fields: [
        { name: 'customerId', label: 'Customer', type: 'select', value: customerId,
          options: customers.content.map((c) => ({ value: c.id, label: `${c.name} (${c.email})` })) },
        { name: 'planId', label: 'Plan', type: 'select',
          options: activePlans.map((p) => ({ value: p.id, label: `${p.name}: ${inr(p.pricePerCycle)} / ${CYCLE[p.billingCycle].toLowerCase()}` })) },
        { name: 'paymentMethod', label: 'Payment method', type: 'select',
          options: [{ value: 'UPI_AUTOPAY', label: 'UPI AutoPay' }, { value: 'CARD', label: 'Card' }] },
        { name: 'startDate', label: 'Start date', type: 'date', value: new Date().toLocaleDateString('en-CA') },
      ],
      onSubmit: async (v) => {
        const sub = await api.post('/api/subscriptions', {
          customerId: Number(v.customerId), planId: Number(v.planId), paymentMethod: v.paymentMethod, startDate: v.startDate || null,
        });
        toast(`Subscribed ${sub.customerName} to ${sub.planName}`);
        drawer.close();
        location.hash = '#/subscriptions';
        render();
      },
    });
  } catch (err) { showError(err); }
}

/* ---------- Customers ---------- */

async function renderCustomers(view) {
  const st = state.customers;
  const page = await api.get(`/api/customers?page=${st.page}&size=15&sort=id,desc`);
  view.innerHTML = `
    <div class="toolbar"><span class="muted">${plural(page.totalElements, 'customer')}</span><span class="spacer"></span>
      <button class="btn btn-primary" id="new-customer">${ICONS.plus} Add customer</button></div>
    <div class="card">${page.content.length ? `<div class="table-wrap"><table><thead><tr>
        <th>Customer</th><th>Signed up</th><th class="right">Tenure</th><th class="right">Past failures</th><th></th>
      </tr></thead><tbody>${page.content.map((c) => `<tr class="clickable" data-customer="${c.id}">
        <td>${who(c.name, c.email)}</td>
        <td>${fmtDate(c.signupDate)}</td>
        <td class="right num">${c.tenureMonths} mo</td>
        <td class="right num">${c.pastFailureCount ? `<span class="badge plain ${c.pastFailureCount > 2 ? 'b-red' : 'b-amber'}">${c.pastFailureCount}</span>` : '<span class="subtle">0</span>'}</td>
        <td class="right"><span class="subtle">View risk →</span></td></tr>`).join('')}</tbody></table></div>${pager(page, (p) => { st.page = p; render(); })}`
      : emptyState(ICONS.inbox, 'No customers yet', 'Add your first customer, or load the demo data from the overview page.',
        '<a class="btn" href="#/overview">Go to overview</a>')}</div>`;
  $$('[data-customer]', view).forEach((r) => { r.onclick = () => openCustomer(Number(r.dataset.customer)); });
  $('#new-customer').onclick = () => modal.open({
    title: 'Add customer',
    submitLabel: 'Add customer',
    fields: [
      { name: 'name', label: 'Full name', placeholder: 'Priya Sharma' },
      { name: 'email', label: 'Email', type: 'email', placeholder: 'priya@example.in' },
      { name: 'signupDate', label: 'Signup date', type: 'date', value: new Date().toLocaleDateString('en-CA') },
      { name: 'pastFailureCount', label: 'Past payment failures', type: 'number', value: 0, min: 0,
        hint: 'Failures before onboarding, e.g. migrated from another system. Raises the churn-risk score.' },
    ],
    onSubmit: async (v) => {
      const c = await api.post('/api/customers', { ...v, pastFailureCount: Number(v.pastFailureCount || 0) });
      toast(`Added ${c.name}`);
      st.page = 0;
      render();
    },
  });
}

/* ---------- Plans ---------- */

async function renderPlans(view) {
  const plans = await api.get('/api/plans');
  view.innerHTML = `
    <div class="toolbar"><span class="muted">Price changes apply from the next invoice.</span><span class="spacer"></span>
      <button class="btn btn-primary" id="new-plan">${ICONS.plus} New plan</button></div>
    ${plans.length ? `<div class="grid plans">${plans.map((p) => `<div class="card plan-card">
        <div class="row"><h3>${esc(p.name)}</h3><span class="spacer" style="flex:1"></span>
          ${p.active ? '<span class="badge b-green">Active</span>' : '<span class="badge b-grey">Closed</span>'}</div>
        <div class="price num">${inr(p.monthlyPrice)}<small> / month</small></div>
        <div class="row muted"><span class="badge plain b-brand">${CYCLE[p.billingCycle]}</span>
          ${p.billingCycle !== 'MONTHLY' ? `Billed ${inr(p.pricePerCycle)} per cycle` : 'Billed every month'}</div>
        <div><button class="btn btn-sm" data-edit="${p.id}">Edit plan</button></div></div>`).join('')}</div>`
      : `<div class="card">${emptyState(ICONS.inbox, 'No plans yet', 'Create a plan that customers can subscribe to.')}</div>`}`;
  $('#new-plan').onclick = () => planModal();
  $$('[data-edit]', view).forEach((b) => { b.onclick = () => planModal(plans.find((p) => p.id === Number(b.dataset.edit))); });
}

function planModal(plan) {
  modal.open({
    title: plan ? `Edit ${plan.name}` : 'New plan',
    submitLabel: plan ? 'Save changes' : 'Create plan',
    fields: [
      { name: 'name', label: 'Plan name', value: plan ? plan.name : '', placeholder: 'Premium' },
      { name: 'monthlyPrice', label: 'Monthly price (₹)', type: 'number', step: '0.01', min: 1, value: plan ? plan.monthlyPrice : '', placeholder: '499' },
      { name: 'billingCycle', label: 'Billing cycle', type: 'select', value: plan ? plan.billingCycle : 'MONTHLY',
        options: Object.entries(CYCLE).map(([value, label]) => ({ value, label })),
        hint: 'Quarterly and yearly plans are charged monthly price × 3 or × 12.' },
      { name: 'active', label: 'Open for new subscriptions', type: 'select', value: plan ? String(plan.active) : 'true',
        options: [{ value: 'true', label: 'Yes' }, { value: 'false', label: 'No' }] },
    ],
    onSubmit: async (v) => {
      const body = { name: v.name, monthlyPrice: v.monthlyPrice ? Number(v.monthlyPrice) : null, billingCycle: v.billingCycle, active: v.active === 'true' };
      const saved = plan ? await api.put(`/api/plans/${plan.id}`, body) : await api.post('/api/plans', body);
      toast(plan ? `Updated ${saved.name}` : `Created ${saved.name}`);
      render();
    },
  });
}

/* ---------- Simulation lab ---------- */

async function renderSimulation(view) {
  const st = state.simulation;
  view.innerHTML = `
    <div class="card" style="margin-bottom:18px">
      <div class="card-head"><div><h3>Smart vs fixed retries</h3>
        <div class="sub">Generates synthetic Indian subscribers and replays months of billing under both recovery policies, with the same customers and the same random seed.</div></div></div>
      <div class="card-body">
        <form class="sim-form" id="sim-form">
          ${renderField({ name: 'customers', label: 'Customers', type: 'number', min: 10, max: 20000, value: st.form.customers })}
          ${renderField({ name: 'months', label: 'Months', type: 'number', min: 1, max: 24, value: st.form.months })}
          ${renderField({ name: 'seed', label: 'Random seed', type: 'number', value: st.form.seed })}
          <button class="btn btn-primary" type="submit" id="sim-run" style="height:38px">${ICONS.flask} Run simulation</button>
        </form>
      </div>
    </div>
    <div id="sim-result"></div>`;

  $('#sim-form').onsubmit = async (e) => {
    e.preventDefault();
    const v = Object.fromEntries(new FormData(e.target).entries());
    st.form = { customers: Number(v.customers), months: Number(v.months), seed: Number(v.seed) };
    await busy($('#sim-run'), runSimulation);
  };
  if (st.result) showSimulation(st.result);
  else await busy($('#sim-run'), runSimulation);
}

async function runSimulation() {
  const st = state.simulation;
  $$('#sim-form .field').forEach((f) => { f.classList.remove('invalid'); $('.err', f).textContent = ''; });
  try {
    st.result = await api.post('/api/simulations', st.form);
    showSimulation(st.result);
  } catch (err) {
    let shown = false;
    for (const [name, msg] of Object.entries(err.fieldErrors || {})) {
      const field = $(`#sim-form .field[data-field="${name}"]`);
      if (field) { field.classList.add('invalid'); $('.err', field).textContent = msg; shown = true; }
    }
    if (!shown) showError(err);
  }
}

function showSimulation(r) {
  destroyCharts();
  const lift = Number(r.recoveryRateLiftPoints);
  const rows = [
    ['Invoices issued', (p) => num(p.invoicesIssued)],
    ['Failed invoices', (p) => num(p.failedInvoices)],
    ['Recovered invoices', (p) => num(p.recoveredInvoices)],
    ['Written off', (p) => num(p.writtenOffInvoices)],
    ['Failed revenue', (p) => inr(p.failedRevenue)],
    ['Recovered revenue', (p) => inr(p.recoveredRevenue)],
    ['Invoice recovery rate', (p) => pct(p.invoiceRecoveryRatePercent)],
    ['Debit retries by policy', (p) => num(p.policyRetries)],
    ['Reminders sent', (p) => num(p.remindersSent)],
    ['Payments made by customers after a reminder', (p) => num(p.customerInitiatedPayments)],
    ['Average days to recover', (p) => Number(p.avgDaysToRecover).toFixed(1)],
    ['Cancellations from retry fatigue', (p) => num(p.retryFatigueCancellations)],
    ['Active subscriptions at the end', (p) => num(p.activeSubscriptionsAtEnd)],
    ['Total revenue collected', (p) => inr(p.totalCollectedRevenue)],
  ];
  const reasons = r.smart.byFailureReason.map((x) => x.reason);
  const fixedBy = Object.fromEntries(r.fixed.byFailureReason.map((x) => [x.reason, x]));

  $('#sim-result').innerHTML = `
    <div class="grid vs">
      ${kpi('Smart recovery rate', pct(r.smart.recoveryRatePercent), `${inr(r.smart.recoveredRevenue)} of ${inr(r.smart.failedRevenue)} recovered`, 'brand', ICONS.target)}
      ${kpi('Fixed-interval recovery rate', pct(r.fixed.recoveryRatePercent), `${inr(r.fixed.recoveredRevenue)} of ${inr(r.fixed.failedRevenue)} recovered`, 'amber', ICONS.target)}
      ${kpi('Lift from smart retries', `<span class="${lift >= 0 ? 'lift' : ''}">${lift >= 0 ? '+' : ''}${lift.toFixed(1)}<small> pts</small></span>`,
        `${inr(r.extraRevenueRecovered)} extra revenue recovered`, 'green', ICONS.up)}
    </div>
    <div class="grid two-col">
      <div class="card">
        <div class="card-head"><div><h3>Recovery rate by failure reason</h3><div class="sub">${num(r.customers)} customers × ${r.months} months, seed ${r.seed}</div></div></div>
        <div class="card-body"><div class="chart-box"><canvas id="sim-chart"></canvas></div></div>
      </div>
      <div class="card">
        <div class="card-head"><h3>Model assumptions</h3></div>
        <div class="card-body"><ul class="assumptions">${r.modelAssumptions.map((a) => `<li>${esc(a)}</li>`).join('')}</ul></div>
      </div>
    </div>
    <div class="card">
      <div class="card-head"><h3>Side by side</h3></div>
      <div class="card-body flush"><div class="table-wrap"><table><thead><tr><th>Metric</th><th class="right">Smart</th><th class="right">Fixed interval</th></tr></thead>
        <tbody>${rows.map(([label, f]) => `<tr><td>${label}</td><td class="right num cell-main">${f(r.smart)}</td><td class="right num">${f(r.fixed)}</td></tr>`).join('')}</tbody></table></div></div>
    </div>`;

  makeChart($('#sim-chart'), {
    type: 'bar',
    data: {
      labels: reasons.map(reasonLabel),
      datasets: [
        { label: 'Smart', data: r.smart.byFailureReason.map((x) => x.recoveryRatePercent), backgroundColor: cssVar('--brand-2'), borderRadius: 6, maxBarThickness: 30 },
        { label: 'Fixed interval', data: reasons.map((k) => (fixedBy[k] ? fixedBy[k].recoveryRatePercent : 0)), backgroundColor: cssVar('--amber'), borderRadius: 6, maxBarThickness: 30 },
      ],
    },
    options: {
      maintainAspectRatio: false,
      plugins: { legend: { position: 'bottom', labels: { usePointStyle: true, boxWidth: 8 } },
        tooltip: { callbacks: { label: (c) => `${c.dataset.label}: ${pct(c.raw)}` } } },
      scales: { y: { min: 0, max: 100, ticks: { callback: (v) => `${v}%` }, grid: { color: cssVar('--border') } }, x: { grid: { display: false } } },
    },
  });
}

/* ======================================================================
 * 6. Detail drawers
 * ==================================================================== */

function attemptTimeline(attempts) {
  if (!attempts || !attempts.length) return '<p class="muted">No charge attempts yet.</p>';
  return `<ul class="timeline">${attempts.map((a) => {
    const ok = a.result === 'SUCCESS';
    return `<li><span class="tl-dot ${ok ? 'tone-green' : 'tone-red'}">${ok ? ICONS.ok : ICONS.x}</span>
      <div class="tl-title">${a.attemptNumber === 1 ? 'First charge' : `Retry ${a.attemptNumber - 1}`}
        ${ok ? '<span class="badge b-green">Success</span>' : reasonBadge(a.failureReason)}</div>
      <div class="tl-time">${fmtDateTime(a.attemptedAt)} · <span class="mono">${esc(a.gatewayReference || '')}</span></div>
      ${a.decisionNote ? `<div class="tl-note">${esc(a.decisionNote)}</div>` : ''}</li>`;
  }).join('')}</ul>`;
}

async function openInvoice(id) {
  drawer.loading('Invoice');
  try {
    const inv = await api.get(`/api/invoices/${id}`);
    const sub = await api.get(`/api/subscriptions/${inv.subscriptionId}`);
    const canFix = inv.status === 'FAILED' && sub.status !== 'CANCELLED';
    drawer.open(`Invoice · ${fmtDate(inv.dueDate)}`,
      `INV-${String(inv.id).padStart(5, '0')} ${badge(INVOICE_STATUS, inv.status)}`, `
      <div class="drawer-section">
        <div class="kpi-value num" style="margin:0 0 4px">${inrExact(inv.amount)}</div>
        <div class="muted">${esc(sub.customerName)} · ${esc(sub.planName)}</div>
      </div>
      ${canFix ? `<div class="drawer-section"><h4>Actions</h4><div class="actions-row">
        <button class="btn btn-primary" id="inv-fix">Customer updated payment method</button>
        <button class="btn" id="inv-sub">Open subscription</button></div>
        <p class="subtle" style="margin:8px 0 0">Simulates the customer re-authorising their UPI mandate or adding a new card. The invoice is retried immediately, which is how hard declines get recovered.</p></div>`
        : '<div class="drawer-section"><button class="btn" id="inv-sub">Open subscription</button></div>'}
      <div class="drawer-section"><h4>Details</h4><dl class="dl">
        <dt>Billing period from</dt><dd>${fmtDate(inv.periodStart)}</dd>
        <dt>Due date</dt><dd>${fmtDate(inv.dueDate)}</dd>
        <dt>First failure</dt><dd>${inv.initialFailureReason ? reasonLabel(inv.initialFailureReason) : '—'}</dd>
        <dt>Latest failure</dt><dd>${inv.lastFailureReason ? reasonLabel(inv.lastFailureReason) : '—'}</dd>
        <dt>Attempts</dt><dd>${inv.attemptCount}</dd>
        ${inv.status === 'FAILED' ? `<dt>Next retry</dt><dd>${inv.nextRetryAt ? `${fmtDateTime(inv.nextRetryAt)} (${relative(inv.nextRetryAt)})` : 'Waiting for the customer'}</dd>
        <dt>Gives up on</dt><dd>${fmtDateTime(inv.recoveryDeadline)}</dd>` : ''}
        ${inv.paidAt ? `<dt>Paid at</dt><dd>${fmtDateTime(inv.paidAt)}</dd>` : ''}
      </dl></div>
      <div class="drawer-section"><h4>Payment attempts and retry decisions</h4>${attemptTimeline(inv.attempts)}</div>`);
    $('#inv-sub').onclick = () => openSubscription(sub.id);
    if (canFix) $('#inv-fix').onclick = () => paymentMethodModal(sub, true, () => openInvoice(id));
  } catch (err) { drawer.close(); showError(err); }
}

/**
 * Update the payment method. When `retryNow` is set, the recovery job runs straight after, so the retry the
 * service scheduled for "now" happens immediately instead of on the next 15-minute tick.
 */
function paymentMethodModal(sub, retryNow, after) {
  modal.open({
    title: 'Update payment method',
    sub: `${sub.customerName} · currently ${METHOD[sub.paymentMethod]}. Failed invoices on this subscription are retried right away.`,
    submitLabel: 'Update and retry',
    fields: [{ name: 'paymentMethod', label: 'New payment method', type: 'select', value: sub.paymentMethod,
      options: [{ value: 'UPI_AUTOPAY', label: 'UPI AutoPay (new mandate)' }, { value: 'CARD', label: 'Card (new card)' }] }],
    onSubmit: async (v) => {
      await api.put(`/api/subscriptions/${sub.id}/payment-method`, { paymentMethod: v.paymentMethod });
      if (retryNow) {
        const job = await api.post('/api/admin/jobs/recovery');
        toast('Payment method updated', `Recovery ran: ${job.succeeded} recovered, ${job.failed} still failing`);
      } else {
        toast('Payment method updated');
      }
      render();
      after();
    },
  });
}

async function openSubscription(id) {
  drawer.loading('Subscription');
  try {
    const [sub, invoices] = await Promise.all([api.get(`/api/subscriptions/${id}`), api.get(`/api/subscriptions/${id}/invoices`)]);
    const actions = [];
    if (sub.status === 'ACTIVE') actions.push('<button class="btn" data-to="PAUSED">Pause</button>');
    if (sub.status === 'PAUSED') actions.push('<button class="btn btn-primary" data-to="ACTIVE">Resume</button>');
    if (sub.status !== 'CANCELLED') {
      actions.push('<button class="btn" id="sub-method">Update payment method</button>');
      actions.push('<button class="btn btn-danger" data-to="CANCELLED">Cancel subscription</button>');
    }
    drawer.open('Subscription', `SUB-${String(sub.id).padStart(5, '0')} ${badge(SUB_STATUS, sub.status)}`, `
      <div class="drawer-section">${who(sub.customerName, sub.planName)}</div>
      ${actions.length ? `<div class="drawer-section"><h4>Actions</h4><div class="actions-row">${actions.join('')}</div>
        ${sub.status === 'PAST_DUE' ? '<p class="subtle" style="margin:8px 0 0">A past-due subscription becomes active again only when its failed invoice is paid.</p>' : ''}</div>` : ''}
      <div class="drawer-section"><h4>Details</h4><dl class="dl">
        <dt>Customer</dt><dd><a href="#" id="sub-customer">${esc(sub.customerName)}</a></dd>
        <dt>Plan</dt><dd>${esc(sub.planName)}</dd>
        <dt>Payment method</dt><dd>${METHOD[sub.paymentMethod]}</dd>
        <dt>Started</dt><dd>${fmtDate(sub.startDate)}</dd>
        <dt>Next billing</dt><dd>${sub.status === 'CANCELLED' ? '—' : fmtDate(sub.nextBillingDate)}</dd>
      </dl></div>
      <div class="drawer-section"><h4>Invoices</h4>${invoices.length ? `<div class="card"><table><tbody>${invoices.map((i) => `
        <tr class="clickable" data-invoice="${i.id}"><td class="mono">INV-${String(i.id).padStart(5, '0')}</td><td>${fmtDate(i.dueDate)}</td>
        <td class="right num">${inr(i.amount)}</td><td>${badge(INVOICE_STATUS, i.status)}</td></tr>`).join('')}</tbody></table></div>`
        : '<p class="muted">No invoices yet. The first one is created on the next billing date.</p>'}</div>`);

    bindInvoiceRows(drawer.body());
    $('#sub-customer').onclick = (e) => { e.preventDefault(); openCustomer(sub.customerId); };
    if ($('#sub-method')) $('#sub-method').onclick = () => paymentMethodModal(sub, invoices.some((i) => i.status === 'FAILED'), () => openSubscription(id));
    $$('[data-to]', drawer.body()).forEach((b) => {
      b.onclick = () => {
        const to = b.dataset.to;
        const verb = { PAUSED: 'Pause', ACTIVE: 'Resume', CANCELLED: 'Cancel' }[to];
        modal.open({
          title: `${verb} subscription?`,
          sub: to === 'CANCELLED' ? 'Cancelling is final: a cancelled subscription cannot be resumed.'
            : to === 'PAUSED' ? 'A paused subscription is not billed until it is resumed.' : 'Billing continues from the next billing date.',
          submitLabel: `${verb} subscription`,
          danger: to === 'CANCELLED',
          onSubmit: async () => {
            await api.patch(`/api/subscriptions/${id}/status`, { status: to });
            toast(`Subscription ${verb === 'Cancel' ? 'cancelled' : verb === 'Pause' ? 'paused' : 'resumed'}`);
            render();
            openSubscription(id);
          },
        });
      };
    });
  } catch (err) { drawer.close(); showError(err); }
}

async function openCustomer(id) {
  drawer.loading('Customer');
  try {
    const [c, risk] = await Promise.all([api.get(`/api/customers/${id}`), api.get(`/api/customers/${id}/risk`)]);
    const color = { LOW: cssVar('--green'), MEDIUM: cssVar('--amber'), HIGH: cssVar('--red') }[risk.band];
    drawer.open('Customer', esc(c.name), `
      <div class="drawer-section">${who(c.name, c.email)}</div>
      <div class="drawer-section"><h4>Churn risk</h4>
        <div class="risk">
          <div class="gauge" style="background:conic-gradient(${color} ${risk.score * 3.6}deg, ${cssVar('--grey-soft')} 0)">
            <div class="gauge-inner"><div><strong class="num">${risk.score}</strong><br><span>of 100</span></div></div></div>
          <div>${badge(RISK_BAND, risk.band)}
            <ul class="factors" style="margin-top:8px">${risk.factors.map((f) => `<li>${esc(f)}</li>`).join('') || '<li>No risk factors</li>'}</ul></div>
        </div>
        <p class="subtle" style="margin:10px 0 0">The retry engine uses this band: high-risk customers get fewer debit attempts and earlier reminders.</p>
      </div>
      <div class="drawer-section"><h4>Details</h4><dl class="dl">
        <dt>Email</dt><dd>${esc(c.email)}</dd>
        <dt>Signed up</dt><dd>${fmtDate(c.signupDate)}</dd>
        <dt>Tenure</dt><dd>${c.tenureMonths} months</dd>
        <dt>Past failures</dt><dd>${c.pastFailureCount}</dd>
      </dl></div>
      <div class="drawer-section"><div class="actions-row">
        <button class="btn btn-primary" id="cust-sub">${ICONS.plus} New subscription</button>
        <button class="btn" id="cust-edit">Edit details</button></div></div>`);
    $('#cust-sub').onclick = () => newSubscription(c.id);
    $('#cust-edit').onclick = () => modal.open({
      title: 'Edit customer',
      fields: [{ name: 'name', label: 'Full name', value: c.name }, { name: 'email', label: 'Email', type: 'email', value: c.email }],
      onSubmit: async (v) => {
        await api.put(`/api/customers/${c.id}`, v);
        toast('Customer updated');
        render();
        openCustomer(c.id);
      },
    });
  } catch (err) { drawer.close(); showError(err); }
}

/* ======================================================================
 * Wiring
 * ==================================================================== */

function runJob(button, path, describe) {
  busy(button, async () => {
    try {
      const r = await api.post(path);
      toast(describe(r), `${r.processed} processed`);
      render();
    } catch (err) { showError(err); }
  });
}

document.addEventListener('DOMContentLoaded', () => {
  $('#run-billing').onclick = (e) => runJob(e.currentTarget, '/api/admin/jobs/billing',
    (r) => (r.processed ? `Billing: ${r.succeeded} paid, ${r.failed} failed` : 'Billing: nothing due today'));
  $('#run-recovery').onclick = (e) => runJob(e.currentTarget, '/api/admin/jobs/recovery',
    (r) => (r.processed ? `Recovery: ${r.succeeded} recovered, ${r.failed} still failing` : 'Recovery: no retries due right now'));
  $('#theme-btn').onclick = () => {
    const dark = document.documentElement.dataset.theme !== 'dark';
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
    try { localStorage.setItem('theme', dark ? 'dark' : 'light'); } catch (e) { /* private mode */ }
    render();
  };
  $('#menu-btn').onclick = () => $('#sidebar').classList.toggle('open');
  $('#drawer-close').onclick = () => drawer.close();
  $('#drawer-overlay').onclick = () => drawer.close();
  $('#modal-cancel').onclick = () => modal.close();
  $('#modal-overlay').onclick = () => modal.close();
  $('#modal').addEventListener('submit', submitModal);
  document.addEventListener('keydown', (e) => {
    if (e.key !== 'Escape') return;
    if ($('#modal').classList.contains('open')) modal.close(); else drawer.close();
  });
  window.addEventListener('hashchange', () => { drawer.close(); modal.close(); render(); });
  checkHealth();
  setInterval(checkHealth, 30000);
  render();
});
