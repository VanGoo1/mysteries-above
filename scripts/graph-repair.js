#!/usr/bin/env node
/**
 * graph-repair — лагодить дві вади graphify у `.graphify/graph.json`. Нуль токенів LLM:
 * усе робить AST-екстрактор.
 *
 * 1. ПРИВИДИ. `graphify update` (і git-хук, що кличе те саме) прибирає ноди лише тих файлів,
 *    які зникли ЦІЛКОМ. Метод, видалений із живого файлу, лишається в графі назавжди зі
 *    старим номером рядка. Тут кожен цільовий файл ЗАМІНЮЄТЬСЯ: старі ноди викидаються,
 *    свіжі вставляються.
 *
 * 2. ФІЛЬТР «SECRET». graphify мовчки викидає будь-який шлях із підрядком `secret` (евристика
 *    для паролів/ключів). У цьому репозиторії під неї потрапляє `SecretOrderService.java` —
 *    хаб підсистеми таємних орденів, єдиний із ~490 java-файлів, відсутній у графі. Вимкнути
 *    фільтр не можна (`--exclude` лише ДОДАЄ патерни), тож такі файли стейджаться під
 *    псевдонімом без «secret», а `source_file`, id та label переписуються назад.
 *    Повна перезбірка це НЕ лікує — вона викине їх знову.
 *
 * Використання:
 *   node scripts/graph-repair.js                    # файли останнього коміту + усі secret-файли
 *   node scripts/graph-repair.js --since HEAD~5     # усе, що змінилось від ревізії
 *   node scripts/graph-repair.js --files a.java,b.java
 *   node scripts/graph-repair.js --all              # усі .java репозиторію (повільно, але без LLM)
 *   node scripts/graph-repair.js --dry-run          # лише показати план
 *   node scripts/graph-repair.js --no-cluster       # не перекластеризовувати
 *
 * Після ремонту кличе `graphify cluster-only` — він розкидає нові ноди по спільнотах і
 * НЕ стирає наявних назв спільнот. Попередній граф лишається в `.graphify/graph.json.bak`.
 */
'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync, execSync } = require('child_process');

/**
 * graphify — це `.cmd`-шим на Windows, який Node 24 відмовляється запускати через execFile
 * без shell (EINVAL, захист від CVE-2024-27980). А передача масиву аргументів РАЗОМ із
 * shell:true дає DeprecationWarning DEP0190. Тому команда збирається одним рядком —
 * усі аргументи тут константні, підстановки вводу немає.
 */
const graphify = (args, cwd) =>
  execSync('graphify ' + args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });

const REPO = path.resolve(__dirname, '..');
const GRAPH = path.join(REPO, '.graphify', 'graph.json');
const SENSITIVE = /secret/i;

// ── аргументи ────────────────────────────────────────────────────────────────

function parseArgs(argv) {
  const opts = { since: null, files: null, all: false, dryRun: false, cluster: true };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--since') opts.since = argv[++i];
    else if (a === '--files') opts.files = argv[++i].split(',').map((s) => s.trim()).filter(Boolean);
    else if (a === '--all') opts.all = true;
    else if (a === '--dry-run') opts.dryRun = true;
    else if (a === '--no-cluster') opts.cluster = false;
    else if (a === '--help' || a === '-h') { printHelp(); process.exit(0); }
    else { console.error('Невідомий аргумент: ' + a); process.exit(2); }
  }
  return opts;
}

function printHelp() {
  console.log(fs.readFileSync(__filename, 'utf8').split('*/')[0].split('\n')
    .filter((l) => l.includes('node scripts/graph-repair.js'))
    .map((l) => l.replace(/^\s*\*\s?/, '')).join('\n'));
}

// ── вибір цільових файлів ────────────────────────────────────────────────────

const git = (args) => execFileSync('git', args, { cwd: REPO, encoding: 'utf8' });
const isJava = (f) => f.endsWith('.java');

function walkJava(dir, acc = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.name === 'target' || e.name === '.git' || e.name === '.graphify') continue;
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walkJava(p, acc);
    else if (isJava(e.name)) acc.push(path.relative(REPO, p).split(path.sep).join('/'));
  }
  return acc;
}

function selectFiles(opts) {
  if (opts.files) return opts.files.filter(isJava);
  if (opts.all) return walkJava(path.join(REPO, 'src'));

  const range = opts.since ? opts.since + '..HEAD' : 'HEAD~1..HEAD';
  let changed = [];
  try {
    changed = git(['diff', '--name-only', range]).split('\n').map((s) => s.trim()).filter(isJava);
  } catch {
    console.warn('git diff ' + range + ' не вдався — беру лише secret-файли');
  }
  // secret-файли додаємо ЗАВЖДИ: вони випадають з графа при кожній перезбірці,
  // тож «незмінений» такий файл усе одно потребує ремонту.
  const secrets = walkJava(path.join(REPO, 'src')).filter((f) => SENSITIVE.test(f));
  return [...new Set([...changed, ...secrets])].filter((f) => fs.existsSync(path.join(REPO, f)));
}

// ── стейджинг із псевдонімами ────────────────────────────────────────────────

/** id ноди = `<батьківська тека>_<ім'я файлу без розширення>` у нижньому регістрі. */
const idPrefix = (rel) => (path.basename(path.dirname(rel)) + '_' + path.basename(rel, '.java')).toLowerCase();

/** Псевдонім без «secret» у ТІЙ САМІЙ теці — інакше зміниться префікс id. */
function aliasFor(rel, taken) {
  const dir = path.dirname(rel);
  const stem = path.basename(rel, '.java');
  let candidate = stem.replace(/secret/gi, (m) => (m[0] === m[0].toUpperCase() ? 'Plain' : 'plain'));
  if (SENSITIVE.test(candidate)) candidate = 'Graphsafe' + candidate;
  let n = 0, out = candidate;
  while (taken.has(dir + '/' + out + '.java')) out = candidate + ++n;
  taken.add(dir + '/' + out + '.java');
  return dir + '/' + out + '.java';
}

function stage(files, root) {
  const taken = new Set(files);
  const stagedToReal = {};   // шлях у темпі → справжній repo-relative
  const prefixFix = [];      // [псевдо-префікс, справжній префікс]
  const labelFix = {};       // псевдо-basename → справжній basename

  for (const rel of files) {
    const staged = SENSITIVE.test(rel) ? aliasFor(rel, taken) : rel;
    const dest = path.join(root, staged);
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.copyFileSync(path.join(REPO, rel), dest);
    stagedToReal[staged] = rel;
    if (staged !== rel) {
      prefixFix.push([idPrefix(staged), idPrefix(rel)]);
      labelFix[path.basename(staged)] = path.basename(rel);
    }
  }
  return { stagedToReal, prefixFix, labelFix };
}

// ── мердж ────────────────────────────────────────────────────────────────────

const norm = (s) => (s || '').replace(/\\/g, '/');

function merge(graph, fresh, ctx) {
  const { stagedToReal, prefixFix, labelFix } = ctx;
  const targets = new Set(Object.values(stagedToReal));

  const fixId = (id) => {
    if (!id) return id;
    for (const [from, to] of prefixFix) if (id.startsWith(from)) return to + id.slice(from.length);
    return id;
  };

  // спільноти вже відомих нод переносимо, щоб cluster-only менше перетрушував граф
  const prevCommunity = new Map(graph.nodes.map((n) => [n.id, [n.community, n.community_name]]));

  const before = graph.nodes.length;
  const nodes = graph.nodes.filter((n) => !targets.has(norm(n.source_file)));
  const dropped = before - nodes.length;

  let added = 0;
  const perFile = {};
  for (const n of fresh.nodes) {
    const real = stagedToReal[norm(n.source_file)];
    if (!real) continue;               // зовнішні типи без source_file уже є в графі
    const id = fixId(n.id);
    const node = { ...n, id, source_file: real };
    if (labelFix[node.label]) node.label = labelFix[node.label];
    const prev = prevCommunity.get(id);
    if (prev && prev[0] !== undefined) { node.community = prev[0]; node.community_name = prev[1]; }
    nodes.push(node);
    added++;
    perFile[real] = (perFile[real] || 0) + 1;
  }

  const ids = new Set(nodes.map((n) => n.id));
  const linksBefore = graph.links.length;
  const links = graph.links.filter(
    (l) => !targets.has(norm(l.source_file)) && ids.has(l.source) && ids.has(l.target));
  const droppedLinks = linksBefore - links.length;

  // Граф — `multigraph: false, directed: false`, тобто пара вузлів тримає РІВНО одне ребро,
  // а напрям у ключі не рахується. Дедуп із `relation` у ключі створював би паралельні ребра,
  // які `cluster-only` потім згортає — і лічильник стрибав би між прогонами.
  const pairKey = (a, b) => (a < b ? a + '>' + b : b + '>' + a);

  const seen = new Set(links.map((l) => pairKey(l.source, l.target)));
  let addedLinks = 0;
  for (const e of fresh.edges) {
    const real = stagedToReal[norm(e.source_file)];
    if (!real) continue;
    const s = fixId(e.source), t = fixId(e.target);
    if (!ids.has(s) || !ids.has(t)) continue;
    const key = pairKey(s, t);
    if (seen.has(key)) continue;
    seen.add(key);
    links.push({ ...e, source: s, target: t, _src: s, _tgt: t, source_file: real });
    addedLinks++;
  }

  graph.nodes = nodes;
  graph.links = links;
  return { before, dropped, added, addedLinks, droppedLinks, perFile, targets };
}

// ── головне ──────────────────────────────────────────────────────────────────

function main() {
  const opts = parseArgs(process.argv.slice(2));

  if (!fs.existsSync(GRAPH)) {
    console.error('Немає ' + GRAPH + ' — спершу побудуйте граф (/graphify).');
    process.exit(1);
  }

  const files = selectFiles(opts);
  if (!files.length) {
    console.log('Нема що лагодити.');
    return;
  }

  console.log('Цільових файлів: ' + files.length);
  for (const f of files) console.log('  ' + f + (SENSITIVE.test(f) ? '   [псевдонім: фільтр secret]' : ''));
  if (opts.dryRun) { console.log('\n--dry-run: граф не змінено.'); return; }

  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'graph-repair-'));
  try {
    const ctx = stage(files, tmp);

    graphify('extract . --no-cluster --out .', tmp);

    const extractPath = path.join(tmp, '.graphify', '.graphify_extract.json');
    if (!fs.existsSync(extractPath)) throw new Error('graphify extract не створив ' + extractPath);
    const fresh = JSON.parse(fs.readFileSync(extractPath, 'utf8'));

    const graph = JSON.parse(fs.readFileSync(GRAPH, 'utf8'));
    const stats = merge(graph, fresh, ctx);

    const missed = files.filter((f) => !stats.perFile[f]);
    if (missed.length) {
      console.warn('\nУВАГА: без жодної ноди лишились ' + missed.length + ' файл(ів):');
      for (const m of missed) console.warn('  ' + m);
      console.warn('Причина зазвичай — новий фільтр graphify або збій парсингу.');
    }

    fs.copyFileSync(GRAPH, GRAPH + '.bak');
    fs.writeFileSync(GRAPH, JSON.stringify(graph));

    console.log('\nноди: було ' + stats.before + ', викинуто ' + stats.dropped
      + ', додано ' + stats.added + ', стало ' + graph.nodes.length);
    console.log('лінки: ' + graph.links.length + ' (викинуто ' + stats.droppedLinks
      + ', додано ' + stats.addedLinks + ')');
    // AST бачить лише застейджені файли, тож ребро з цільового файлу до файлу ПОЗА набором
    // не відтворюється. Різниця майже завжди означає саме це, а не втрату даних.
    if (stats.droppedLinks > stats.addedLinks) {
      console.log('  ' + (stats.droppedLinks - stats.addedLinks) + ' крос-файлових ребер не '
        + 'відтворено — цілі поза набором. Повний прогін (без --files) їх поверне.');
    }
    console.log('бекап: .graphify/graph.json.bak');

    if (opts.cluster) {
      console.log('\nКластеризація...');
      const out = graphify('cluster-only .', REPO);
      console.log(out.trim().split('\n').slice(-1)[0]);
    }
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
}

main();
