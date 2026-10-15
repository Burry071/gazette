// Layer 4: neuro-symbolic reconciliation over stored v3 votes (no model calls).
// The model proposes; this layer handles the linguistically-certain cases:
//   1. an event verb (paid/sent/received/credited/...) means money really moved
//   2. a balance state with NO event verb is not a money movement
//   3. security-code texts (OTP / one-time pin) are never ledger entries, amount or not
// Everything else stays with the model's fail-safe answer.
const fs = require('fs');
const path = require('path');

const RECV_RE = /\b(received|credited|deposited)\b/i;
const SENT_RE = /\b(paid|sent|spent|debited|withdrawn|transferred|withdrawal)\b/i;
const BALANCE_RE = /\bbalance\b/i;
const OTP_RE = /\botp\b|one[- ]time\s+(pin|password|code|passcode)/i;
const AMOUNT_CAP = /(?:\brs\.?|\bpkr)\s*([\d][\d,]*\.?\d*)/i;

function actionFrom(text) {
  const recv = RECV_RE.test(text);
  const sent = SENT_RE.test(text);
  if (recv === sent) return { hasEvent: recv && sent, dir: null };
  return { hasEvent: true, dir: recv ? 'received' : 'sent' };
}

function amountFrom(text) {
  const m = text.match(AMOUNT_CAP);
  if (!m) return null;
  const n = parseFloat(m[1].replace(/,/g, ''));
  return isNaN(n) ? null : Math.round(n * 100) / 100;
}

function majority(list, fallback) {
  const counts = new Map();
  for (const v of list) counts.set(v, (counts.get(v) || 0) + 1);
  let best = null, bestN = 0, tie = false;
  for (const [v, n] of counts) {
    if (n > bestN) { best = v; bestN = n; tie = false; }
    else if (n === bestN) tie = true;
  }
  return tie ? fallback : best;
}

const file = process.argv[2];
if (!file) { console.error('usage: node scripts/reconcile.js corpus/results_v3_<model>.jsonl'); process.exit(1); }
const rows = fs.readFileSync(file, 'utf8').trim().split('\n').map(JSON.parse);

let rescues = 0, vetoes = 0, otpVetoes = 0, dirOverrides = 0;
const out = rows.map(r => {
  const { hasEvent, dir: verbDir } = actionFrom(r.text);
  const modelTx = !r.gated && r.clsVotes.filter(v => v === 'P').length >= 2;

  let tx = modelTx;
  if (!tx && hasEvent) { tx = true; rescues++; }                    // event verb -> money moved
  if (tx && BALANCE_RE.test(r.text) && !hasEvent) { tx = false; vetoes++; } // state, not event
  if (tx && OTP_RE.test(r.text)) { tx = false; otpVetoes++; }       // privacy: never store codes

  let amount = null, direction = null;
  if (tx) {
    const amtVotes = r.amtVotes.filter(v => v !== null);
    amount = amtVotes.length ? majority(amtVotes, null) : amountFrom(r.text);
    if (verbDir) {
      direction = verbDir;
      const voteDir = majority(r.dirVotes.filter(v => v !== null), null);
      if (voteDir && voteDir !== verbDir) dirOverrides++;
    } else {
      direction = majority(r.dirVotes.filter(v => v !== null), null);
    }
  }

  const got = { transaction: tx, amount, direction };
  const pass = got.transaction === r.want.transaction
    && (!r.want.transaction || (got.amount === r.want.amount && got.direction === r.want.direction));
  return { ...r, got, pass };
});

const calc = (list) => {
  const tx = list.filter(r => r.want.transaction);
  const jk = list.filter(r => !r.want.transaction);
  return {
    n: list.length,
    exact: list.filter(r => r.pass).length,
    tx: tx.length,
    txDetected: tx.filter(r => r.got.transaction === true).length,
    amtOk: tx.filter(r => r.got.transaction === true && r.got.amount === r.want.amount).length,
    dirOk: tx.filter(r => r.got.transaction === true && r.got.direction === r.want.direction).length,
    junk: jk.length,
    junkOk: jk.filter(r => r.got.transaction === false).length,
  };
};
const print = (name, s) => {
  console.log(`\n== ${name} (n=${s.n}) ==`);
  console.log(`exact pass     : ${s.exact}/${s.n}  (${((s.exact / s.n) * 100).toFixed(1)}%)`);
  console.log(`tx detected    : ${s.txDetected}/${s.tx}`);
  console.log(`amount correct : ${s.amtOk}/${s.txDetected} detected`);
  console.log(`direction ok   : ${s.dirOk}/${s.txDetected} detected`);
  console.log(`junk rejected  : ${s.junkOk}/${s.junk}`);
};

console.log(`reconciling ${path.basename(file)} | entries: ${rows.length}`);
print('REAL', calc(out.filter(r => !r.synth)));
const synth = out.filter(r => r.synth);
if (synth.length) print('SYNTH (excluded from headline)', calc(synth));
console.log(`\nrescued by event verb (model said OTHER): ${rescues}`);
console.log(`vetoed as balance state (model said PAYMENT): ${vetoes}`);
console.log(`vetoed as security code (privacy): ${otpVetoes}`);
console.log(`direction corrected by verb over model votes: ${dirOverrides}`);

const fails = out.filter(r => !r.pass);
if (fails.length) {
  console.log(`\nremaining failures: ${fails.length}`);
  for (const r of fails) {
    console.log(`  [${r.synth ? 'SYNTH' : 'REAL'}] "${r.text.slice(0, 60)}..."`);
    console.log(`    want ${JSON.stringify(r.want)} got ${JSON.stringify(r.got)}`);
  }
}

const outFile = file.replace(/results_v3_/, 'results_v4_');
fs.writeFileSync(outFile, out.map(r => JSON.stringify(r)).join('\n'));
console.log(`\nwrote ${path.basename(outFile)}`);
