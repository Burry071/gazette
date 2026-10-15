const fs = require('fs');
const path = require('path');

const model = process.argv[2] || 'gemma3:4b';
const OLLAMA = 'http://localhost:11434';

function buildPrompt(text) {
  return `You read a phone notification and decide if real money moved in or out of the user's own account.

Examples:
Notification: "Bachat alert! Sirf Rs.30 se shuru honay wali discounted offers khas apke liye. Dial *3535#"
JSON: {"transaction": false, "amount": null, "direction": null}

Notification: "Rs 6,023.37 is your available balance on JazzCash Mobile A/C: USMAN TARIQ as of 29/08/2026"
JSON: {"transaction": false, "amount": null, "direction": null}

Notification: "Your OTP code for Comedy Corner is 0000"
JSON: {"transaction": false, "amount": null, "direction": null}

Notification: "Rs 500.00 received from JazzCash A/C: 0300XXXXXXX on 29/08/2026. TID: 72XXXXXXXXXX via IBFT"
JSON: {"transaction": true, "amount": 500, "direction": "received"}

Notification: "Rs 100.00 paid to New QUETTA T TWENTY RESTUARENT, Till ID: 98XXXXXXX via QR Code."
JSON: {"transaction": true, "amount": 100, "direction": "sent"}

Rules:
- transaction=true ONLY if money actually moved in or out of the account in this message.
- Balance info, OTP codes, offers, discounts, prices, loans, data bundles = false.
- amount = the transaction amount only, no currency symbol, no thousands separators.
- "sent" = user paid or sent money. "received" = user got money.

Output ONLY the JSON, no other text.

Notification: "${text}"

JSON:`;
}

function loadCorpus() {
  const raw = fs.readFileSync(path.join(__dirname, '..', 'corpus', 'corpus_v1.txt'), 'utf8');
  const entries = [];
  for (const line of raw.split('\n')) {
    const t = line.trim();
    if (!t || t.startsWith('#')) continue;
    const parts = t.split(' | ');
    if (parts.length < 4) continue;
    const [label, amount, direction, text] = parts;
    entries.push({
      label,
      amount: amount === '-' ? null : Number(amount.replace(/,/g, '')),
      direction: direction === '-' ? null : direction,
      text: text.trim(),
      synth: text.includes('[SYNTH'),
    });
  }
  return entries;
}

async function ask(text) {
  const res = await fetch(`${OLLAMA}/api/generate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      model,
      prompt: buildPrompt(text),
      stream: false,
      options: { temperature: 0, num_predict: 48, num_gpu: 0 },
    }),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${await res.text()}`);
  const j = await res.json();
  return j;
}

function parseOut(raw) {
  const m = raw.match(/\{[\s\S]*\}/);
  if (!m) return null;
  try { return JSON.parse(m[0]); } catch { return null; }
}

function normAmount(a) {
  if (a === null || a === undefined) return null;
  if (typeof a === 'number') return Math.round(a * 100) / 100;
  const n = parseFloat(String(a).replace(/[,\s]/g, '').replace(/[^0-9.]/g, ''));
  return isNaN(n) ? null : Math.round(n * 100) / 100;
}

(async () => {
  const tags = await fetch(`${OLLAMA}/api/tags`).then(r => r.json()).catch(() => null);
  if (!tags) { console.error('Ollama not reachable on ' + OLLAMA); process.exit(1); }
  const have = tags.models.map(m => m.name);
  if (!have.some(n => n === model || n.startsWith(model + ':'))) {
    console.error(`Model ${model} not pulled. Available: ${have.join(', ')}`);
    process.exit(1);
  }

  const entries = loadCorpus();
  console.log(`Model: ${model} | entries: ${entries.length}\n`);

  const rows = [];
  let totalMs = 0, totalOutTok = 0;
  for (const e of entries) {
    const t0 = Date.now();
    let raw = '', err = null, meta = {};
    try { meta = await ask(e.text); raw = meta.response || ''; }
    catch (ex) { err = ex.message; }
    const ms = Date.now() - t0;
    totalMs += ms;
    if (meta.eval_count) totalOutTok += meta.eval_count;

    const parsed = err ? null : parseOut(raw);
    const got = {
      transaction: parsed ? parsed.transaction === true : null,
      amount: parsed ? normAmount(parsed.amount) : null,
      direction: parsed && (parsed.direction === 'sent' || parsed.direction === 'received') ? parsed.direction : null,
    };
    const want = { transaction: e.label === 'T', amount: e.amount, direction: e.direction };
    const pass = err || !parsed ? false
      : got.transaction === want.transaction
      && (!want.transaction || (got.amount === want.amount && got.direction === want.direction));
    rows.push({ e, got, want, pass, raw: raw.replace(/\s+/g, ' ').slice(0, 120), err, ms });
    console.log(`${pass ? 'PASS' : 'FAIL'} [${e.synth ? 'SYNTH' : 'REAL '}] (${ms}ms) "${e.text.slice(0, 70)}..."`);
    if (!pass) {
      console.log(`     want: ${JSON.stringify(want)}`);
      console.log(`     got : ${JSON.stringify(got)}${err ? ' ERR ' + err : ''}`);
      if (parsed) console.log(`     raw : ${rows[rows.length - 1].raw}`);
    }
    fs.writeFileSync(path.join(__dirname, '..', 'corpus', `results_${model.replace(/[^\w.-]/g, '_')}.jsonl`),
      rows.map(r => JSON.stringify({ text: r.e.text, synth: r.e.synth, pass: r.pass, want: r.want, got: r.got, ms: r.ms })).join('\n'));
  }

  const real = rows.filter(r => !r.e.synth);
  const synth = rows.filter(r => r.e.synth);
  const stats = (list) => {
    const tx = list.filter(r => r.e.label === 'T');
    const jk = list.filter(r => r.e.label === 'J');
    const junkOk = jk.filter(r => r.got.transaction === false).length;
    const txDetected = tx.filter(r => r.got.transaction === true).length;
    const amtOk = tx.filter(r => r.got.transaction === true && r.got.amount === r.e.amount).length;
    const dirOk = tx.filter(r => r.got.transaction === true && r.got.direction === r.e.direction).length;
    const exact = list.filter(r => r.pass).length;
    return { n: list.length, exact, tx: tx.length, txDetected, amtOk, dirOk, junk: jk.length, junkOk };
  };
  const print = (name, s) => {
    console.log(`\n== ${name} (n=${s.n}) ==`);
    console.log(`exact pass     : ${s.exact}/${s.n}  (${((s.exact / s.n) * 100).toFixed(1)}%)`);
    console.log(`tx detected    : ${s.txDetected}/${s.tx}`);
    console.log(`amount correct : ${s.amtOk}/${s.txDetected} detected`);
    console.log(`direction ok   : ${s.dirOk}/${s.txDetected} detected`);
    console.log(`junk rejected  : ${s.junkOk}/${s.junk}`);
  };
  print('REAL', stats(real));
  if (synth.length) print('SYNTH (excluded from headline)', stats(synth));
  console.log(`\ntotal time ${(totalMs / 1000).toFixed(1)}s | ${(totalMs / rows.length).toFixed(0)}ms/entry | ${totalOutTok} out tokens | ${(totalOutTok / (totalMs / 1000)).toFixed(1)} tok/s`);
})();
