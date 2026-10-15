const fs = require('fs');
const path = require('path');

const model = process.argv[2] || 'gemma3:1b';
const OLLAMA = 'http://localhost:11434';
const VOTES = 3;
const TEMP = 0.4;

// A real payment notification must state an amount (that is the whole point
// of extracting one). No Rs/PKR amount in the text -> drop before any model.
const AMOUNT_RE = /\b(?:rs\.?|pkr)\s*[\d,]/i;

function classifyPrompt(text) {
  return `You read a phone notification. Decide whether it reports a real payment that actually happened - money actually moved into or out of the user's own account.

PAYMENT means money really moved. Examples:
"Rs 500.00 received from JazzCash A/C: 0300XXXXXXX on 29/08/2026. TID: 72XXXXXXXXXX via IBFT" -> PAYMENT
"Rs 100.00 paid to New QUETTA T TWENTY RESTUARENT, Till ID: 98XXXXXXX via QR Code." -> PAYMENT
"Dear HAMZA IQBAL, an amount of Rs. 700 has been successfully sent to AHMED RAZA of IBAN No: ****6116 via RAAST" -> PAYMENT

OTHER means no money moved. Examples:
"Rs 6,023.37 is your available balance on JazzCash Mobile A/C: USMAN TARIQ as of 29/08/2026" -> OTHER
"Apka Balance is call k liay nakafi hai. Abhi "Y" likh kr Reply karnay par Rs.15 ka Zong Advance Loan hasil Karen @Rs.3.5+tax" -> OTHER
"Bachat alert! Sirf Rs.30 se shuru honay wali discounted offers khas apke liye" -> OTHER
"Your OTP code for Comedy Corner is 0000" -> OTHER
"Hajj 2026 ZONG 5G brings WhatsApp Overload in KSA ! Use 3.5GB for PKR 2" -> OTHER
"Welcome to JazzCash App! Get Started with JazzCash Bonus of Rs Rs 100.00. Balance: Rs 100.00" -> OTHER
"157 messages from 2 chats" -> OTHER

Balance info, OTP codes, offers, discounts, prices, loans, bonuses, data bundles and chat messages are all OTHER. If you are not sure, answer OTHER.

Notification: "${text}"

Answer with one word (PAYMENT or OTHER):`;
}

function extractPrompt(text) {
  return `You extract facts from a payment notification.

Examples:
Notification: "Rs 2,500.00 received from ACME TECH SOLUTIONS (PRIVATE) LIMITED on 29/08/2026."
AMOUNT: 2500.00
DIRECTION: received

Notification: "Dear HAMZA IQBAL, an amount of Rs. 700 has been successfully sent to AHMED RAZA of IBAN No: ****6116 via RAAST"
AMOUNT: 700
DIRECTION: sent

Notification: "Rs 70.00 paid to New QUETTA T TWENTY RESTUARENT, Till ID: 98XXXXXXX via QR Code."
AMOUNT: 70.00
DIRECTION: sent

Rules:
- AMOUNT is the transaction amount only: a plain number, no currency symbol, no thousands separators.
- DIRECTION is exactly sent or received. Money leaving the account is sent. Money entering is received.

Notification: "${text}"

Answer in exactly this format:
AMOUNT: <number>
DIRECTION: sent or received`;
}

async function generate(prompt, predict) {
  const res = await fetch(`${OLLAMA}/api/generate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      model,
      prompt,
      stream: false,
      options: { temperature: TEMP, num_predict: predict, num_gpu: 0 },
    }),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${(await res.text()).slice(0, 120)}`);
  const j = await res.json();
  return (j.response || '').trim();
}

function parseClass(raw) {
  const t = raw.toUpperCase().replace(/[^A-Z ]/g, ' ');
  const p = t.indexOf('PAYMENT');
  const o = t.indexOf('OTHER');
  if (p >= 0 && (o < 0 || p < o)) return 'P';
  return 'O';
}

function parseAmount(raw) {
  const m = raw.match(/AMOUNT:\s*([0-9][0-9.,\s]*)/i);
  if (!m) return null;
  const n = parseFloat(m[1].replace(/[,\s]/g, ''));
  return isNaN(n) ? null : Math.round(n * 100) / 100;
}

function parseDir(raw) {
  const m = raw.match(/DIRECTION:\s*([A-Za-z]+)/i);
  const d = (m ? m[1] : '').toLowerCase();
  if (/^(paid|spent|sent|debit|debited|withdrawn|outgoing|out)$/.test(d)) return 'sent';
  if (/^(received|credited|credit|got|incoming|deposit|deposited|in)$/.test(d)) return 'received';
  return null;
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

(async () => {
  const tags = await fetch(`${OLLAMA}/api/tags`).then(r => r.json()).catch(() => null);
  if (!tags) { console.error('Ollama not reachable on ' + OLLAMA); process.exit(1); }
  if (!tags.models.some(m => m.name === model || m.name.startsWith(model + ':'))) {
    console.error(`Model ${model} not pulled.`);
    process.exit(1);
  }

  const entries = loadCorpus();
  console.log(`Model: ${model} | two-stage self-consistency | votes=${VOTES} temp=${TEMP} | entries: ${entries.length}\n`);
  const rows = [];
  let totalMs = 0, disagreements = 0;

  for (const e of entries) {
    const t0 = Date.now();
    let clsVotes = [], amtVotes = [], dirVotes = [], err = null, gated = false;
    try {
      if (!AMOUNT_RE.test(e.text)) {
        gated = true;
      } else {
        for (let i = 0; i < VOTES; i++) clsVotes.push(parseClass(await generate(classifyPrompt(e.text), 4)));
        if (new Set(clsVotes).size > 1) disagreements++;
        if (clsVotes.filter(v => v === 'P').length >= 2) {
          for (let i = 0; i < VOTES; i++) {
            const raw = await generate(extractPrompt(e.text), 24);
            amtVotes.push(parseAmount(raw));
            dirVotes.push(parseDir(raw));
          }
        }
      }
    } catch (ex) { err = ex.message.slice(0, 100); }
    const ms = Date.now() - t0;
    totalMs += ms;

    const isTx = !gated && clsVotes.filter(v => v === 'P').length >= 2;
    const got = {
      transaction: err ? null : isTx,
      amount: isTx ? majority(amtVotes, null) : null,
      direction: isTx ? majority(dirVotes, null) : null,
    };
    const want = { transaction: e.label === 'T', amount: e.amount, direction: e.direction };
    const pass = !err
      && got.transaction === want.transaction
      && (!want.transaction || (got.amount === want.amount && got.direction === want.direction));
    rows.push({ text: e.text, synth: e.synth, pass, want, got, gated, clsVotes, amtVotes, dirVotes, ms });
    console.log(`${pass ? 'PASS' : 'FAIL'} [${e.synth ? 'SYNTH' : 'REAL '}] (${ms}ms) cls=${gated ? 'GATE' : clsVotes.join('')} amt=${JSON.stringify(amtVotes)} dir=${JSON.stringify(dirVotes)} "${e.text.slice(0, 55)}..."`);
    if (!pass) {
      console.log(`     want: ${JSON.stringify(want)}`);
      console.log(`     got : ${JSON.stringify(got)}${err ? ' ERR ' + err : ''}`);
    }
    fs.writeFileSync(path.join(__dirname, '..', 'corpus', `results_v3_${model.replace(/[^\w.-]/g, '_')}.jsonl`),
      rows.map(r => JSON.stringify(r)).join('\n'));
  }

  const real = rows.filter(r => !r.synth);
  const synth = rows.filter(r => r.synth);
  const calc = (list) => {
    const tx = list.filter(r => r.want.transaction);
    const jk = list.filter(r => !r.want.transaction);
    const junkOk = jk.filter(r => r.got.transaction === false).length;
    const txDetected = tx.filter(r => r.got.transaction === true).length;
    const amtOk = tx.filter(r => r.got.transaction === true && r.got.amount === r.want.amount).length;
    const dirOk = tx.filter(r => r.got.transaction === true && r.got.direction === r.want.direction).length;
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
  print('REAL', calc(real));
  if (synth.length) print('SYNTH (excluded from headline)', calc(synth));
  console.log(`\ngate dropped (no amount in text): ${rows.filter(r => r.gated).length}/${rows.length}`);
  console.log(`classifier vote disagreements: ${disagreements}/${rows.filter(r => !r.gated).length} gated-in entries`);
  console.log(`total time ${(totalMs / 1000).toFixed(1)}s | ${(totalMs / rows.length).toFixed(0)}ms/entry`);
})();
