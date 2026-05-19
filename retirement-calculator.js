// -------------- Currency --------------
const CURRENCIES = {
  INR: { symbol: "₹", locale: "en-IN", style: "indian",  word: "Rupees"  },
  EUR: { symbol: "€", locale: "de-DE", style: "western", word: "Euros"   },
  USD: { symbol: "$", locale: "en-US", style: "western", word: "Dollars" },
};

let currentCurrency = "INR";  // overridden by prefs once they load
let moneyFmt = new Intl.NumberFormat(CURRENCIES[currentCurrency].locale, { maximumFractionDigits: 0 });

function refreshMoneyFmt() {
  moneyFmt = new Intl.NumberFormat(CURRENCIES[currentCurrency].locale, { maximumFractionDigits: 0 });
}
function currencySymbol() { return CURRENCIES[currentCurrency].symbol; }

const fmt = (n) => {
  const s = currencySymbol();
  return n < 0
    ? "−" + s + moneyFmt.format(Math.abs(Math.round(n)))
    : s + moneyFmt.format(Math.round(n));
};

const fmtAge = (n) => n + " yrs";

// Compact formatting for chart axis — Indian (Cr/L/k) or Western (B/M/k) based on currency
function fmtShort(n) {
  const s = currencySymbol();
  if (CURRENCIES[currentCurrency].style === "indian") {
    if (n >= 1e7)  return s + (n / 1e7).toFixed(n >= 1e8 ? 0 : 1)  + " Cr";
    if (n >= 1e5)  return s + (n / 1e5).toFixed(n >= 1e6 ? 0 : 1)  + " L";
    if (n >= 1000) return s + (n / 1000).toFixed(0) + "k";
    return s + Math.round(n);
  }
  if (n >= 1e9) return s + (n / 1e9).toFixed(1) + "B";
  if (n >= 1e6) return s + (n / 1e6).toFixed(1) + "M";
  if (n >= 1e3) return s + (n / 1e3).toFixed(1) + "k";
  return s + Math.round(n);
}

// -------------- Chart rendering --------------
const CHART_CONFIG = {
  corpus: {
    kind: "line",
    title: "Corpus Trajectory",
    color: "#3b82f6",      // blue-500
    gradId: "corpusGrad",
    series: (rows, ctx) => {
      const pts = [{ age: ctx.currentAge, value: ctx.corpus0 }];
      rows.forEach(r => pts.push({ age: r.age + 1, value: Math.max(r.endCorpus, 0) }));
      return pts;
    },
    pickPeak: (pts) => pts.reduce((m, p) => p.value > m.value ? p : m, pts[0]),
    withDepletion: true,
  },
  expenses: {
    kind: "line",
    title: "Annual Expenses",
    color: "#f97316",      // orange-500
    gradId: "expenseGrad",
    series: (rows) => rows.map(r => ({ age: r.age, value: r.annualExp })),
    pickPeak: (pts) => pts[pts.length - 1], // monotonically growing
    withDepletion: false,
  },
  investments: {
    kind: "pie",
    title: "Investments at Retirement",
  },
};

let activeChart = "corpus";
let lastChartCtx = null;

function setActiveChart(name) {
  if (!CHART_CONFIG[name]) return;
  activeChart = name;

  document.querySelectorAll(".chart-tab").forEach(btn => {
    const isActive = btn.dataset.chart === name;
    btn.setAttribute("aria-selected", String(isActive));
    btn.classList.toggle("text-slate-900", isActive);
    btn.classList.toggle("dark:text-slate-100", isActive);
    btn.classList.toggle("border-blue-500", isActive);
    btn.classList.toggle("border-transparent", !isActive);
  });

  if (lastChartCtx) renderChart(lastChartCtx);
}

document.querySelectorAll(".chart-tab").forEach(btn => {
  btn.addEventListener("click", () => setActiveChart(btn.dataset.chart));
});

function renderChart(ctx) {
  // ctx: { rows, currentAge, retireAge, corpus0, depletedAt, investedAtRetirement }
  lastChartCtx = ctx;
  const card = document.getElementById("chart-card");
  const host = document.getElementById("chart");
  if (!ctx.rows.length) { card.style.display = "none"; return; }

  const cfg = CHART_CONFIG[activeChart];
  if (cfg.kind === "pie") {
    host.innerHTML = renderPieSvg(ctx, cfg);
    card.style.display = "";
    return;
  }

  const COLOR = cfg.color;

  const W = 1200, H = 340;
  const padL = 88, padR = 32, padT = 44, padB = 44;
  const plotW = W - padL - padR;
  const plotH = H - padT - padB;

  const points = cfg.series(ctx.rows, ctx);

  const minAge = points[0].age;
  const maxAge = points[points.length - 1].age;
  const maxV   = Math.max(...points.map(p => p.value), 1) * 1.08;

  const xs = (a) => padL + ((a - minAge) / Math.max(1, (maxAge - minAge))) * plotW;
  const ys = (v) => padT + plotH - (v / maxV) * plotH;

  const linePts = points.map(p => `${xs(p.age).toFixed(1)},${ys(p.value).toFixed(1)}`).join(" ");
  const areaD =
    `M ${xs(points[0].age).toFixed(1)},${ys(0).toFixed(1)} ` +
    points.map(p => `L ${xs(p.age).toFixed(1)},${ys(p.value).toFixed(1)}`).join(" ") +
    ` L ${xs(points[points.length-1].age).toFixed(1)},${ys(0).toFixed(1)} Z`;

  const yTicks = [];
  for (let i = 0; i <= 4; i++) yTicks.push({ v: (maxV / 4) * i, y: ys((maxV / 4) * i) });

  const span = maxAge - minAge;
  const step = span > 50 ? 10 : span > 25 ? 5 : span > 12 ? 2 : 1;
  const xTicks = [];
  for (let a = minAge; a <= maxAge; a += step) xTicks.push(a);
  if (xTicks[xTicks.length - 1] !== maxAge) xTicks.push(maxAge);

  const retireX = xs(ctx.retireAge);
  const peak = cfg.pickPeak(points);
  const peakX = xs(peak.age), peakY = ys(peak.value);

  // Position peak label avoiding right-edge overflow
  const peakAnchor = peakX > W - padR - 60 ? "end" : peakX < padL + 60 ? "start" : "middle";
  const peakLabelX = peakAnchor === "end" ? peakX - 8 : peakAnchor === "start" ? peakX + 8 : peakX;

  const showDepletion = cfg.withDepletion && ctx.depletedAt;

  const svg = `
    <svg viewBox="0 0 ${W} ${H}" class="w-full h-auto" preserveAspectRatio="xMidYMid meet">
      <defs>
        <linearGradient id="${cfg.gradId}" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%"  stop-color="${COLOR}" stop-opacity="0.35"/>
          <stop offset="100%" stop-color="${COLOR}" stop-opacity="0.02"/>
        </linearGradient>
      </defs>

      <!-- Y grid + axis labels -->
      ${yTicks.map(t => `
        <line x1="${padL}" y1="${t.y.toFixed(1)}" x2="${W - padR}" y2="${t.y.toFixed(1)}"
              stroke="currentColor" stroke-opacity="0.1" stroke-width="1"/>
        <text x="${padL - 12}" y="${t.y.toFixed(1)}" text-anchor="end" dominant-baseline="central"
              font-size="12" fill="currentColor" fill-opacity="0.6"
              font-family="'JetBrains Mono', monospace">${fmtShort(t.v)}</text>
      `).join("")}

      <!-- X labels -->
      ${xTicks.map(a => `
        <text x="${xs(a).toFixed(1)}" y="${H - padB + 20}" text-anchor="middle"
              font-size="12" fill="currentColor" fill-opacity="0.6"
              font-family="'JetBrains Mono', monospace">${a}</text>
      `).join("")}
      <text x="${(padL + W - padR) / 2}" y="${H - 6}" text-anchor="middle"
            font-size="11" fill="currentColor" fill-opacity="0.5"
            font-family="Inter, sans-serif">Age</text>

      <!-- Retirement marker -->
      ${(() => {
        const tx = retireX < padL + 80 ? "start" : retireX > W - padR - 80 ? "end" : "middle";
        const lx = tx === "start" ? retireX + 6 : tx === "end" ? retireX - 6 : retireX;
        return `
          <line x1="${retireX.toFixed(1)}" y1="${padT - 6}" x2="${retireX.toFixed(1)}" y2="${H - padB}"
                stroke="#14b8a6" stroke-width="1.5" stroke-dasharray="5 4"/>
          <circle cx="${retireX.toFixed(1)}" cy="${padT - 6}" r="3" fill="#14b8a6"/>
          <text x="${lx.toFixed(1)}" y="${(padT - 14).toFixed(1)}" text-anchor="${tx}"
                font-size="12" font-weight="600" fill="#14b8a6"
                font-family="Inter, sans-serif">Retirement · age ${ctx.retireAge}</text>`;
      })()}

      ${showDepletion ? (() => {
        const dx = xs(ctx.depletedAt);
        const close = Math.abs(dx - retireX) < 200;
        const ty = close ? padT - 30 : padT - 14;
        const tx = dx < padL + 80 ? "start" : dx > W - padR - 80 ? "end" : "middle";
        const lx = tx === "start" ? dx + 6 : tx === "end" ? dx - 6 : dx;
        return `
          <line x1="${dx.toFixed(1)}" y1="${padT - 6}" x2="${dx.toFixed(1)}" y2="${H - padB}"
                stroke="#ef4444" stroke-width="1.5" stroke-dasharray="5 4"/>
          <circle cx="${dx.toFixed(1)}" cy="${padT - 6}" r="3" fill="#ef4444"/>
          <text x="${lx.toFixed(1)}" y="${ty.toFixed(1)}" text-anchor="${tx}"
                font-size="12" font-weight="600" fill="#ef4444"
                font-family="Inter, sans-serif">Depleted · age ${ctx.depletedAt}</text>`;
      })() : ""}

      <!-- Area + line -->
      <path d="${areaD}" fill="url(#${cfg.gradId})"/>
      <polyline points="${linePts}" fill="none" stroke="${COLOR}" stroke-width="2.5"
                ${cfg.dashed ? 'stroke-dasharray="5 3"' : ''}
                stroke-linecap="round" stroke-linejoin="round"/>

      <!-- Peak marker -->
      <circle cx="${peakX.toFixed(1)}" cy="${peakY.toFixed(1)}" r="4" fill="${COLOR}" stroke="#fff" stroke-width="2"/>
      <text x="${peakLabelX.toFixed(1)}" y="${(peakY - 10).toFixed(1)}" text-anchor="${peakAnchor}"
            font-size="11" font-weight="600" fill="currentColor"
            font-family="'JetBrains Mono', monospace">${fmtShort(peak.value)}</text>
    </svg>`;

  host.innerHTML = svg;
  card.style.display = "";
}

function describeDonutArc(cx, cy, rO, rI, a1, a2) {
  // angle 0 = top of circle, increasing clockwise
  const pt = (r, a) => [cx + r * Math.sin(a), cy - r * Math.cos(a)];
  const [x1o, y1o] = pt(rO, a1);
  const [x2o, y2o] = pt(rO, a2);
  const [x1i, y1i] = pt(rI, a1);
  const [x2i, y2i] = pt(rI, a2);
  const large = a2 - a1 > Math.PI ? 1 : 0;
  return `M ${x1o.toFixed(2)},${y1o.toFixed(2)}
          A ${rO},${rO} 0 ${large} 1 ${x2o.toFixed(2)},${y2o.toFixed(2)}
          L ${x2i.toFixed(2)},${y2i.toFixed(2)}
          A ${rI},${rI} 0 ${large} 0 ${x1i.toFixed(2)},${y1i.toFixed(2)} Z`;
}

function renderPieSvg(ctx, cfg) {
  const retireRow = ctx.rows.find(r => r.isRetireYear);
  const total = retireRow ? retireRow.startCorpus : 0;
  const invested = Math.max(0, ctx.investedAtRetirement || 0);
  const interest = Math.max(0, total - invested);

  if (total <= 0) {
    return `<div class="py-16 text-center text-slate-400 dark:text-slate-500 text-sm">
              No retirement data available yet.
            </div>`;
  }

  const INVESTED_COLOR = "#3b82f6";   // blue-500
  const INTEREST_COLOR = "#10b981";   // emerald-500

  const W = 1200, H = 340;
  const cx = 340, cy = H / 2;
  const rO = 130, rI = 78;

  const TAU = 2 * Math.PI;
  const sweep = (invested / total) * TAU;

  // If one side is 0%, draw a single full ring of the other color
  let slices = "";
  const EPS = 1e-4;
  if (sweep < EPS) {
    slices = `<circle cx="${cx}" cy="${cy}" r="${(rO + rI) / 2}" fill="none"
                       stroke="${INTEREST_COLOR}" stroke-width="${rO - rI}"/>`;
  } else if (sweep > TAU - EPS) {
    slices = `<circle cx="${cx}" cy="${cy}" r="${(rO + rI) / 2}" fill="none"
                       stroke="${INVESTED_COLOR}" stroke-width="${rO - rI}"/>`;
  } else {
    slices = `
      <path d="${describeDonutArc(cx, cy, rO, rI, 0, sweep)}" fill="${INVESTED_COLOR}"/>
      <path d="${describeDonutArc(cx, cy, rO, rI, sweep, TAU)}" fill="${INTEREST_COLOR}"/>`;
  }

  const investedPct = (invested / total) * 100;
  const interestPct = (interest / total) * 100;

  return `
    <svg viewBox="0 0 ${W} ${H}" class="w-full h-auto" preserveAspectRatio="xMidYMid meet">
      ${slices}

      <!-- Donut center: total -->
      <text x="${cx}" y="${cy - 12}" text-anchor="middle" font-size="12" fill="currentColor" fill-opacity="0.55"
            font-family="Inter, sans-serif">Total at retirement</text>
      <text x="${cx}" y="${cy + 18}" text-anchor="middle" font-size="26" font-weight="700" fill="currentColor"
            font-family="'JetBrains Mono', monospace">${fmtShort(total)}</text>

      <!-- Right panel: breakdown -->
      <g transform="translate(620, 80)" font-family="Inter, sans-serif">
        <rect x="0" y="4" width="14" height="14" rx="3" fill="${INVESTED_COLOR}"/>
        <text x="24" y="14" font-size="13" font-weight="600" fill="currentColor">Invested Amount</text>
        <text x="0" y="48" font-size="24" font-weight="700" fill="currentColor"
              font-family="'JetBrains Mono', monospace">${fmt(invested)}</text>
        <text x="0" y="68" font-size="12" fill="currentColor" fill-opacity="0.55">${investedPct.toFixed(1)}% of total · principal contributed</text>

        <rect x="0" y="108" width="14" height="14" rx="3" fill="${INTEREST_COLOR}"/>
        <text x="24" y="118" font-size="13" font-weight="600" fill="currentColor">Interest Earned</text>
        <text x="0" y="152" font-size="24" font-weight="700" fill="currentColor"
              font-family="'JetBrains Mono', monospace">${fmt(interest)}</text>
        <text x="0" y="172" font-size="12" fill="currentColor" fill-opacity="0.55">${interestPct.toFixed(1)}% of total · returns compounded</text>
      </g>
    </svg>`;
}

// Initialize active tab styling on load
setActiveChart(activeChart);


function parseMoney(el) {
  return parseFloat(el.value.replace(/[^\d]/g, "")) || 0;
}

function applyMoneyFormat(el) {
  const raw = el.value.replace(/[^\d]/g, "");
  if (!raw) { el.value = ""; updateMoneyWords(el); return; }

  const before = el.value.slice(0, el.selectionStart).replace(/[^\d]/g, "").length;
  el.value = moneyFmt.format(parseInt(raw, 10));

  let digits = 0, pos = el.value.length;
  for (let i = 0; i < el.value.length; i++) {
    if (/\d/.test(el.value[i])) digits++;
    if (digits === before) { pos = i + 1; break; }
  }
  el.setSelectionRange(pos, pos);
  updateMoneyWords(el);
}

// -------------- Number to words --------------
const _ONES = ["", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
  "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"];
const _TENS = ["", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"];

function _twoDigits(n) {
  if (n < 20) return _ONES[n];
  const t = Math.floor(n / 10), o = n % 10;
  return _TENS[t] + (o ? " " + _ONES[o] : "");
}
function _threeDigits(n) {
  const h = Math.floor(n / 100), r = n % 100;
  let out = "";
  if (h) out += _ONES[h] + " Hundred";
  if (r) out += (out ? " " : "") + _twoDigits(r);
  return out;
}

function numberToWordsIndian(n) {
  if (n === 0) return "Zero";
  function helper(n) {
    if (n < 100) return _twoDigits(n);
    if (n < 1000) return _threeDigits(n);
    if (n < 1e5) {
      const t = Math.floor(n / 1000), r = n % 1000;
      return _twoDigits(t) + " Thousand" + (r ? " " + _threeDigits(r) : "");
    }
    if (n < 1e7) {
      const l = Math.floor(n / 1e5), r = n % 1e5;
      return _twoDigits(l) + " Lakh" + (r ? " " + helper(r) : "");
    }
    const c = Math.floor(n / 1e7), r = n % 1e7;
    return helper(c) + " Crore" + (r ? " " + helper(r) : "");
  }
  return helper(n);
}

function numberToWordsWestern(n) {
  if (n === 0) return "Zero";
  const SCALES = [
    { v: 1e12, name: "Trillion" },
    { v: 1e9,  name: "Billion"  },
    { v: 1e6,  name: "Million"  },
    { v: 1e3,  name: "Thousand" },
  ];
  function helper(n) {
    if (n < 1000) return _threeDigits(n);
    for (const s of SCALES) {
      if (n >= s.v) {
        const q = Math.floor(n / s.v), r = n % s.v;
        return helper(q) + " " + s.name + (r ? " " + helper(r) : "");
      }
    }
    return _threeDigits(n);
  }
  return helper(n);
}

function amountInWords(n) {
  const c = CURRENCIES[currentCurrency];
  const words = c.style === "indian" ? numberToWordsIndian(n) : numberToWordsWestern(n);
  return words + " " + c.word;
}

function updateMoneyWords(el) {
  const target = document.querySelector(`[data-words-for="${el.id}"]`);
  if (!target) return;
  const raw = el.value.replace(/[^\d]/g, "");
  if (!raw) { target.textContent = ""; return; }
  target.textContent = amountInWords(parseInt(raw, 10));
}

function refreshAllMoneyWords() {
  document.querySelectorAll("[data-money]").forEach(el => updateMoneyWords(el));
}

// -------------- UI preferences (font size + theme + currency) --------------
const PREFS_KEY = "rc_prefs";
const FONT_MIN = 12, FONT_MAX = 22, FONT_STEP = 2, FONT_DEFAULT = 16;

let prefs = {};
try { prefs = JSON.parse(localStorage.getItem(PREFS_KEY) || "{}"); } catch {}
let fontSize = prefs.fontSize || FONT_DEFAULT;
let theme    = prefs.theme    || "light";
if (prefs.currency && CURRENCIES[prefs.currency]) currentCurrency = prefs.currency;
refreshMoneyFmt();

function applyFont() { document.documentElement.style.fontSize = fontSize + "px"; }
function applyTheme() { document.documentElement.classList.toggle("dark", theme === "dark"); }
function savePrefs() {
  try { localStorage.setItem(PREFS_KEY, JSON.stringify({ fontSize, theme, currency: currentCurrency })); } catch {}
}

applyFont();
applyTheme();

function updateCurrencySymbols() {
  const s = currencySymbol();
  document.querySelectorAll(".currency-symbol").forEach(el => el.textContent = s);
  const label = document.getElementById("currency-label");
  if (label) label.textContent = currentCurrency;
}

function applyCurrencyButtonStyling() {
  document.querySelectorAll(".currency-btn").forEach(btn => {
    const isActive = btn.dataset.currency === currentCurrency;
    btn.setAttribute("aria-pressed", String(isActive));
    btn.classList.toggle("text-slate-900", isActive);
    btn.classList.toggle("dark:text-slate-100", isActive);
    btn.classList.toggle("bg-slate-100", isActive);
    btn.classList.toggle("dark:bg-slate-700", isActive);
  });
}

async function setCurrency(code) {
  if (!CURRENCIES[code] || code === currentCurrency) {
    applyCurrencyButtonStyling();
    return;
  }
  currentCurrency = code;
  refreshMoneyFmt();
  savePrefs();
  applyCurrencyButtonStyling();
  updateCurrencySymbols();

  // Swap inputs to the new currency's persisted values, or fall back to defaults.json.
  // defaults.json is read-only — it never gets written back.
  await loadCurrentCurrencyValues();

  // Re-format money inputs with the new locale
  document.querySelectorAll("[data-money]").forEach(el => applyMoneyFormat(el));
  refreshAllMoneyWords();

  if (typeof calculate === "function") calculate();
}

document.getElementById("font-inc").addEventListener("click", () => {
  fontSize = Math.min(FONT_MAX, fontSize + FONT_STEP);
  applyFont(); savePrefs();
});
document.getElementById("font-dec").addEventListener("click", () => {
  fontSize = Math.max(FONT_MIN, fontSize - FONT_STEP);
  applyFont(); savePrefs();
});
document.getElementById("font-reset").addEventListener("click", () => {
  fontSize = FONT_DEFAULT;
  applyFont(); savePrefs();
});
document.getElementById("theme-toggle").addEventListener("click", () => {
  theme = theme === "dark" ? "light" : "dark";
  applyTheme(); savePrefs();
});
document.querySelectorAll(".currency-btn").forEach(btn => {
  btn.addEventListener("click", () => setCurrency(btn.dataset.currency));
});

// -------------- Calculator input persistence --------------
const STORE_KEY = "rc_inputs";
const INPUT_IDS = ["currentAge","retireAge","lifeExp","corpus","monthlyExp","inflation","growthPre","growthPost","monthlyInvPre","sipGrowthPre","monthlyInvPost","sipGrowthPost"];

function readStore() {
  try {
    const v = localStorage.getItem(STORE_KEY);
    if (v) return v;
  } catch {}
  return window.name && window.name.charAt(0) === "{" ? window.name : null;
}

function writeStore(serialized) {
  try { localStorage.setItem(STORE_KEY, serialized); } catch {}
  try { window.name = serialized; } catch {}
}

// Storage shape is { INR: {...}, EUR: {...}, USD: {...} } — values are stored per currency.
function readAllStored() {
  let saved;
  try { saved = JSON.parse(readStore()); } catch {}
  if (!saved || typeof saved !== "object") return {};
  // Legacy flat format (pre-currency split) — treat as INR.
  if (saved.currentAge != null) return { INR: saved };
  return saved;
}

function saveInputs() {
  const all = readAllStored();
  const vals = {};
  INPUT_IDS.forEach(id => {
    const el = document.getElementById(id);
    vals[id] = el.hasAttribute("data-money") ? el.value.replace(/[^\d]/g, "") : el.value;
  });
  all[currentCurrency] = vals;
  writeStore(JSON.stringify(all));
}

function loadInputsFromStorage() {
  const all = readAllStored();
  const saved = all[currentCurrency];
  if (!saved || Object.keys(saved).length === 0) return false;
  INPUT_IDS.forEach(id => {
    if (saved[id] == null) return;
    document.getElementById(id).value = saved[id];
  });
  return true;
}

// Cache defaults.json after the first fetch — the file is read-only and never modified.
let DEFAULTS_CACHE = null;
async function getDefaults() {
  if (DEFAULTS_CACHE) return DEFAULTS_CACHE;
  try {
    const response = await fetch("defaults.json");
    if (!response.ok) return null;
    DEFAULTS_CACHE = await response.json();
    return DEFAULTS_CACHE;
  } catch { return null; }
}

async function applyDefaultsForCurrency(currency) {
  const defaults = await getDefaults();
  if (!defaults) return false;
  const values = defaults[currency] || defaults.INR;
  if (!values) return false;
  INPUT_IDS.forEach(id => {
    if (values[id] != null) document.getElementById(id).value = values[id];
  });
  return true;
}

async function loadCurrentCurrencyValues() {
  if (loadInputsFromStorage()) return;
  await applyDefaultsForCurrency(currentCurrency);
}

async function resetToDefaults() {
  const applied = await applyDefaultsForCurrency(currentCurrency);
  if (!applied) return;
  document.querySelectorAll("[data-money]").forEach(el => applyMoneyFormat(el));
  saveInputs();
  calculate();
}

async function initInputs() {
  await loadCurrentCurrencyValues();

  document.querySelectorAll("[data-money]").forEach(el => {
    applyMoneyFormat(el);
    el.addEventListener("input", () => { applyMoneyFormat(el); saveInputs(); });
    el.addEventListener("blur",  () => applyMoneyFormat(el));
  });

  INPUT_IDS.filter(id => !document.getElementById(id).hasAttribute("data-money"))
    .forEach(id => document.getElementById(id).addEventListener("input", saveInputs));

  updateCurrencySymbols();
  applyCurrencyButtonStyling();
  calculate();
}

initInputs();

function calculate() {
  const currentAge = parseInt(document.getElementById("currentAge").value);
  const retireAge  = parseInt(document.getElementById("retireAge").value);
  const lifeExp    = parseInt(document.getElementById("lifeExp").value);
  const corpus0    = parseMoney(document.getElementById("corpus"));
  const monthlyExp = parseMoney(document.getElementById("monthlyExp"));
  const inflation      = parseFloat(document.getElementById("inflation").value) / 100;
  const growthPre      = parseFloat(document.getElementById("growthPre").value) / 100;
  const growthPost     = parseFloat(document.getElementById("growthPost").value) / 100;
  const monthlyInvPre  = parseMoney(document.getElementById("monthlyInvPre"));
  const sipGrowthPre   = parseFloat(document.getElementById("sipGrowthPre").value) / 100;
  const monthlyInvPost = parseMoney(document.getElementById("monthlyInvPost"));
  const sipGrowthPost  = parseFloat(document.getElementById("sipGrowthPost").value) / 100;

  if (currentAge >= retireAge) { alert("Retirement age must be greater than current age."); return; }
  if (retireAge >= lifeExp)    { alert("Life expectancy must be greater than retirement age."); return; }

  const rows = [];
  let mainCorpus = corpus0;
  let sipCorpus  = 0;
  let totalInvested = corpus0;   // initial principal + cumulative SIP contributions
  let investedAtRetirement = null;
  const annualExp0    = monthlyExp * 12;
  const annualInvPre  = monthlyInvPre * 12;
  const annualInvPost = monthlyInvPost * 12;
  const currentYear = new Date().getFullYear();
  let corpusDepletedAt = null;

  for (let age = currentAge; age <= lifeExp; age++) {
    const yearsFromNow = age - currentAge;
    const year         = currentYear + yearsFromNow;
    const isRetireYear = age === retireAge;
    const isPost       = age >= retireAge;
    const annualExp    = annualExp0 * Math.pow(1 + inflation, yearsFromNow);

    // At retirement, fold the accumulated pre-retirement SIP into the main corpus.
    // The post-retirement SIP rate then applies only to NEW post-retirement contributions,
    // not to wealth already accumulated.
    if (isRetireYear) {
      investedAtRetirement = totalInvested; // capture before this year's (post-retire) contribution
      mainCorpus += sipCorpus;
      sipCorpus = 0;
    }

    const mainRate   = isPost ? growthPost    : growthPre;
    const sipRate    = isPost ? sipGrowthPost : sipGrowthPre;
    const investment = isPost ? annualInvPost : annualInvPre;
    totalInvested += investment;

    const startCorpus = mainCorpus + sipCorpus;
    const mainReturns = mainCorpus * mainRate;
    const sipReturns  = sipCorpus  * sipRate;
    const returns     = mainReturns + sipReturns;

    let mainAfter = mainCorpus + mainReturns;
    let sipAfter  = sipCorpus  + sipReturns + investment;

    const withdrawal = isPost ? annualExp : 0;
    let endCorpus;

    if (withdrawal === 0) {
      endCorpus = mainAfter + sipAfter;
    } else {
      const pool = mainAfter + sipAfter;
      if (pool >= withdrawal) {
        // proportional draw — both buckets shrink in proportion to their share
        const mainShare = pool > 0 ? mainAfter / pool : 0;
        mainAfter -= withdrawal * mainShare;
        sipAfter  -= withdrawal * (1 - mainShare);
        endCorpus = mainAfter + sipAfter;
      } else {
        // shortfall — surface the actual deficit (negative) so the row shows the gap
        endCorpus = pool - withdrawal;
        mainAfter = 0;
        sipAfter  = 0;
      }
    }

    const depleted = endCorpus < 0;
    rows.push({ year, age, isRetireYear, isPost, annualExp, startCorpus, returns, investment, withdrawal, endCorpus, depleted });

    if (depleted && corpusDepletedAt === null) corpusDepletedAt = age;

    mainCorpus = Math.max(mainAfter, 0);
    sipCorpus  = Math.max(sipAfter,  0);
    if (depleted) break;
  }

  const retireRow = rows.find(r => r.isRetireYear);

  document.getElementById("s-corpus-retire").textContent = retireRow ? fmt(retireRow.startCorpus) : "—";
  document.getElementById("s-exp-retire").textContent    = retireRow ? fmt(retireRow.annualExp) : "—";

  const lastsEl = document.getElementById("s-lasts");
  if (corpusDepletedAt) {
    // corpusDepletedAt is the year of first shortfall — the previous year was the last
    // one fully covered by the corpus.
    lastsEl.textContent = fmtAge(corpusDepletedAt - 1);
    lastsEl.className = "stat-value text-xl font-bold text-red-600 dark:text-red-400";
  } else {
    lastsEl.textContent = "Beyond " + fmtAge(lifeExp) + " ✓";
    lastsEl.className = "stat-value text-xl font-bold text-green-600 dark:text-green-400";
  }

  // Final corpus: value at the "lasts until" year. When depleted, that's the row
  // before the shortfall (the last fully-covered year). Otherwise, life expectancy.
  const finalAge = corpusDepletedAt ? corpusDepletedAt - 1 : lifeExp;
  const finalRow = rows.find(r => r.age === finalAge) || rows[rows.length - 1];

  document.getElementById("s-final-suffix").textContent =
    corpusDepletedAt ? `(at age ${finalAge})` : "(at life expectancy)";

  const finalEl = document.getElementById("s-final");
  finalEl.textContent = fmt(finalRow.endCorpus);
  finalEl.className   = finalRow.endCorpus <= 0
    ? "stat-value text-xl font-bold text-red-600 dark:text-red-400"
    : finalRow.endCorpus < finalRow.annualExp * 5
      ? "stat-value text-xl font-bold text-amber-600 dark:text-amber-400"
      : "stat-value text-xl font-bold text-green-600 dark:text-green-400";

  document.getElementById("summary").style.display = "";

  renderChart({ rows, currentAge, retireAge, corpus0, depletedAt: corpusDepletedAt, investedAtRetirement });

  const tbody = document.getElementById("grid-body");
  tbody.innerHTML = "";

  rows.forEach((r, i) => {
    const tr = document.createElement("tr");
    const evenBg = i % 2 === 0
      ? "bg-white dark:bg-transparent"
      : "bg-slate-50 dark:bg-slate-900/40";

    const rowBg = r.depleted
      ? "bg-red-50 dark:bg-red-900/30"
      : r.isRetireYear
        ? "bg-teal-50 dark:bg-teal-900/30"
        : evenBg;
    tr.className = `${rowBg} border-b border-slate-100 dark:border-slate-700/60`;

    const phaseBadge = r.isPost
      ? `<span class="phase-badge inline-block text-xs font-semibold px-2 py-0.5 rounded-full bg-teal-100 dark:bg-teal-900/40 text-teal-700 dark:text-teal-300">Post</span>`
      : `<span class="phase-badge inline-block text-xs font-semibold px-2 py-0.5 rounded-full bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300">Pre</span>`;

    const corpusEndClass = r.isPost
      ? (r.endCorpus <= 0
          ? "text-red-600 dark:text-red-400 font-semibold"
          : r.endCorpus < r.annualExp * 10
            ? "text-amber-600 dark:text-amber-400 font-semibold"
            : "text-green-600 dark:text-green-400 font-semibold")
      : "";

    const firstTdStyle = r.isRetireYear ? ' style="border-left: 3px solid #14b8a6"' : '';

    const numCellCls = "px-4 py-2.5 text-right text-slate-700 dark:text-slate-300";
    const dual = (annual, showZero = false) => {
      if (!showZero && (!annual || annual <= 0)) {
        return `<span class="text-slate-400 dark:text-slate-500">—</span>`;
      }
      return `
        <div class="leading-tight whitespace-nowrap">${fmt(annual / 12)} <span class="text-xs text-slate-400 dark:text-slate-500">/mo</span></div>
        <div class="leading-tight whitespace-nowrap text-xs text-slate-400 dark:text-slate-500 mt-0.5">${fmt(annual)} /yr</div>`;
    };

    tr.innerHTML = `
      <td class="px-4 py-2.5 text-center font-semibold text-slate-700 dark:text-slate-200"${firstTdStyle}>${r.year}</td>
      <td class="px-4 py-2.5 text-center text-slate-700 dark:text-slate-300">${r.age}</td>
      <td class="px-4 py-2.5 text-left">${phaseBadge}</td>
      <td class="${numCellCls}">${dual(r.annualExp, true)}</td>
      <td class="${numCellCls}">${fmt(r.startCorpus)}</td>
      <td class="${numCellCls}">${dual(r.returns, true)}</td>
      <td class="${numCellCls}">${dual(r.investment)}</td>
      <td class="${numCellCls}">${dual(r.withdrawal)}</td>
      <td class="px-4 py-2.5 text-right ${corpusEndClass}">${fmt(r.endCorpus)}</td>`;

    tbody.appendChild(tr);
  });

  document.getElementById("legend").style.display = "";
}

