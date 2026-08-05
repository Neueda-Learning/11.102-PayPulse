/**
 * PayPulse — API Client
 * All backend calls go through this file.
 * Base URL uses a relative path (/api/v1) so nginx proxies to the backend container
 * transparently — no hardcoded host/port in the browser JS.
 * See frontend/nginx/nginx.conf for the proxy_pass rule.
 */

const API_BASE = '/api/v1';

// ── Shared fetch wrapper ──────────────────────────────────────────────────────

async function apiFetch(path, options = {}) {
  const url = API_BASE + path;
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };

  let res;
  try {
    res = await fetch(url, { ...options, headers });
  } catch (networkErr) {
    throw { errorCode: 'NETWORK_ERROR', message: 'Could not reach the server. Check your connection.' };
  }

  // Rate limit — show toast
  if (res.status === 429) {
    const retryAfter = res.headers.get('Retry-After') || '60';
    const body = await res.json().catch(() => ({}));
    window.dispatchEvent(new CustomEvent('paypulse:ratelimit', { detail: { retryAfter, ...body } }));
    throw { errorCode: 'RATE_LIMIT_EXCEEDED', message: `Too many requests. Retry after ${retryAfter}s.` };
  }

  // All other non-2xx responses — parse ApiError body
  if (!res.ok) {
    const err = await res.json().catch(() => ({ errorCode: 'UNKNOWN', message: res.statusText }));
    throw err;
  }

  // 204 No Content
  if (res.status === 204) return null;

  return res.json();
}

// ── Accounts ─────────────────────────────────────────────────────────────────

const Accounts = {
  list: () => apiFetch('/accounts'),
  getById: (id) => apiFetch(`/accounts/${id}`)
};

// ── Payments ─────────────────────────────────────────────────────────────────

const Payments = {
  create: (body, idempotencyKey) => apiFetch('/payments', {
    method: 'POST',
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {},
    body: JSON.stringify(body)
  }),

  getById: (id) => apiFetch(`/payments/${id}`),

  list: (params = {}) => {
    const qs = new URLSearchParams();
    if (params.status)          qs.set('status', params.status);
    if (params.search)          qs.set('search', params.search);
    if (params.sourceAccountId) qs.set('sourceAccountId', params.sourceAccountId);
    if (params.page != null)    qs.set('page', params.page);
    if (params.size != null)    qs.set('size', params.size);
    qs.set('sort', params.sort || 'createdAt,desc');
    return apiFetch(`/payments?${qs.toString()}`);
  },

  getHistory: (id) => apiFetch(`/payments/${id}/history`),

  validate: (id) => apiFetch(`/payments/${id}/validate`, { method: 'POST' }),
  send:     (id) => apiFetch(`/payments/${id}/send`,     { method: 'POST' }),
  complete: (id) => apiFetch(`/payments/${id}/complete`, { method: 'POST' })
};

// ── Analytics ────────────────────────────────────────────────────────────────

const Analytics = {
  summary: (params = {}) => {
    const qs = new URLSearchParams();
    if (params.from) qs.set('from', params.from);
    if (params.to)   qs.set('to',   params.to);
    return apiFetch(`/analytics/summary?${qs.toString()}`);
  },

  trend: (hours = 24) => apiFetch(`/analytics/trend?hours=${hours}`)
};

// ── Utility helpers ───────────────────────────────────────────────────────────

function formatMoney(amount, currency) {
  const symbol = currency === 'INR' ? '₹' : '$';
  return symbol + Number(amount).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatDateTime(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString(undefined, {
    year: 'numeric', month: 'short', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  });
}

function badgeHtml(status) {
  return `<span class="badge ${status}">${status}</span>`;
}

function getQueryParam(name) {
  return new URLSearchParams(window.location.search).get(name);
}

function generateIdempotencyKey() {
  return (window.crypto && window.crypto.randomUUID)
    ? window.crypto.randomUUID()
    : 'key-' + Math.random().toString(36).slice(2) + Date.now();
}

// ── Rate-limit toast (global, mounted once) ───────────────────────────────────

(function initRateLimitToast() {
  const toast = document.createElement('div');
  toast.id = 'rate-limit-toast';
  toast.style.cssText = [
    'display:none', 'position:fixed', 'top:16px', 'right:16px',
    'background:#fef2f2', 'border:1px solid #fecaca', 'color:#b91c1c',
    'padding:12px 18px', 'border-radius:8px', 'font-size:13px',
    'font-weight:600', 'z-index:9999', 'max-width:320px',
    'box-shadow:0 4px 12px rgba(0,0,0,.1)'
  ].join(';');
  document.addEventListener('DOMContentLoaded', () => document.body.appendChild(toast));

  window.addEventListener('paypulse:ratelimit', (e) => {
    toast.textContent = `⚠️ Rate limit reached. Please wait ${e.detail.retryAfter || 60}s before retrying.`;
    toast.style.display = 'block';
    setTimeout(() => { toast.style.display = 'none'; }, 8000);
  });
})();

// ── Active nav link ───────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
  const page = window.location.pathname.split('/').pop() || 'index.html';
  document.querySelectorAll('.nav a').forEach(a => {
    if (a.getAttribute('href') === page) a.classList.add('active');
  });
});

