/*
 * PayPulse Wireframe - Shared helper functions
 * Small utilities reused across the prototype pages. Nothing here talks to a real
 * backend - it all reads from window.DUMMY_DATA (see dummy-data.js).
 */

function formatMoney(amount, currency) {
  var symbol = currency === "INR" ? "\u20B9" : "$";
  return symbol + Number(amount).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatDateTime(iso) {
  if (!iso) return "-";
  var d = new Date(iso);
  return d.toLocaleString(undefined, { year: "numeric", month: "short", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

function findAccount(accountId) {
  return window.DUMMY_DATA.accounts.find(function (a) { return a.id === accountId; });
}

function accountLabel(accountId) {
  var acc = findAccount(accountId);
  return acc ? acc.label + " (" + acc.accountNumber + ")" : accountId;
}

function findPayment(id) {
  return window.DUMMY_DATA.payments.find(function (p) { return p.id === id; });
}

function badgeHtml(status) {
  return '<span class="badge ' + status + '">' + status + '</span>';
}

function getQueryParam(name) {
  var params = new URLSearchParams(window.location.search);
  return params.get(name);
}

function generateIdempotencyKey() {
  if (window.crypto && window.crypto.randomUUID) return window.crypto.randomUUID();
  return "key-" + Math.random().toString(36).slice(2) + Date.now();
}

function generatePaymentId() {
  return "a1b2c3d4-" + Math.random().toString(36).slice(2, 8);
}

/* Highlights the active nav link based on the current file name */
document.addEventListener("DOMContentLoaded", function () {
  var page = window.location.pathname.split("/").pop() || "index.html";
  document.querySelectorAll(".nav a").forEach(function (a) {
    var href = a.getAttribute("href");
    if (href === page) a.classList.add("active");
  });
});

