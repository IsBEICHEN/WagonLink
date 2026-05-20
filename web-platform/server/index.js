import Database from 'better-sqlite3';
import cors from 'cors';
import express from 'express';
import iconv from 'iconv-lite';
import multer from 'multer';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const dataDir = path.join(__dirname, '..', 'data');
fs.mkdirSync(dataDir, { recursive: true });

const db = new Database(path.join(dataDir, 'iot-platform.db'));
db.pragma('journal_mode = WAL');
db.pragma('synchronous = NORMAL');

const app = express();
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 100 * 1024 * 1024 }
});

app.use(cors());
app.use(express.json({ limit: '20mb' }));

const uid = (prefix) => `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
const today = () => new Date().toISOString().slice(0, 10);
const recordModules = new Set(['customers', 'devices', 'alarms', 'flows', 'operations', 'orders', 'users', 'roles']);
const appendedCardStart = '1443000000000';

const seedRecords = {
  customers: [
    { id: 'customer-1', name: '华东冷链科技', contact: '陈经理', phone: '13800001234', industry: '冷链物流', level: '重点客户', status: '正常', createdAt: '2026-02-11', remark: '月度对账' },
    { id: 'customer-2', name: '北区能源运维', contact: '周工', phone: '13900004567', industry: '新能源', level: '普通客户', status: '正常', createdAt: '2026-03-04', remark: '需要远程开停机' }
  ],
  devices: [
    { id: 'device-1', deviceNo: 'DEV-LD-1001', name: '冷链网关一号', customer: '华东冷链科技', cardNo: '', model: 'GW-4G-CT20', location: '上海浦东仓', status: '在线', installedAt: '2026-04-06', maintainer: '李工' },
    { id: 'device-2', deviceNo: 'DEV-EN-2038', name: '光伏采集终端', customer: '北区能源运维', cardNo: '', model: 'DTU-500', location: '河北张家口', status: '在线', installedAt: '2026-03-22', maintainer: '王工' }
  ],
  alarms: [],
  flows: [],
  operations: [],
  orders: [],
  users: [
    { id: 'user-1', username: 'admin', name: '系统管理员', role: '超级管理员', phone: '13600008888', status: '启用', lastLogin: '2026-05-13', remark: '平台初始化账号' }
  ],
  roles: [
    { id: 'role-1', roleName: '超级管理员', scope: '全部菜单', dataScope: '全部客户', status: '启用', createdAt: '2026-05-01', remark: '拥有全部权限' }
  ]
};

const cardTemplateHeaders = [
  '卡号',
  'ICCID',
  '运营商',
  '状态',
  '客户名称',
  '绑定设备编号',
  '流量套餐',
  '已用流量GB',
  '套餐总量GB',
  '激活日期',
  '到期日期',
  '备注'
];

const headerMap = {
  cardNo: 'cardNo',
  卡号: 'cardNo',
  iccid: 'iccid',
  ICCID: 'iccid',
  carrier: 'carrier',
  运营商: 'carrier',
  status: 'status',
  状态: 'status',
  customer: 'customer',
  客户: 'customer',
  客户名称: 'customer',
  deviceId: 'deviceId',
  绑定设备: 'deviceId',
  绑定设备编号: 'deviceId',
  plan: 'plan',
  套餐: 'plan',
  流量套餐: 'plan',
  dataUsed: 'dataUsed',
  已用GB: 'dataUsed',
  已用流量GB: 'dataUsed',
  dataTotal: 'dataTotal',
  总量GB: 'dataTotal',
  套餐总量GB: 'dataTotal',
  activatedAt: 'activatedAt',
  激活日期: 'activatedAt',
  expireAt: 'expireAt',
  到期日期: 'expireAt',
  remark: 'remark',
  备注: 'remark'
};

const templateKeys = [
  'cardNo',
  'iccid',
  'carrier',
  'status',
  'customer',
  'deviceId',
  'plan',
  'dataUsed',
  'dataTotal',
  'activatedAt',
  'expireAt',
  'remark'
];

db.exec(`
  CREATE TABLE IF NOT EXISTS cards (
    id TEXT PRIMARY KEY,
    cardNo TEXT NOT NULL UNIQUE,
    iccid TEXT NOT NULL UNIQUE,
    carrier TEXT,
    status TEXT,
    customer TEXT,
    deviceId TEXT,
    plan TEXT,
    dataUsed REAL DEFAULT 0,
    dataTotal REAL DEFAULT 0,
    activatedAt TEXT,
    expireAt TEXT,
    remark TEXT,
    createdAt TEXT DEFAULT CURRENT_TIMESTAMP
  );
  CREATE INDEX IF NOT EXISTS idx_cards_cardNo ON cards(cardNo);
  CREATE INDEX IF NOT EXISTS idx_cards_iccid ON cards(iccid);
  CREATE INDEX IF NOT EXISTS idx_cards_status ON cards(status);
  CREATE INDEX IF NOT EXISTS idx_cards_customer ON cards(customer);

  CREATE TABLE IF NOT EXISTS records (
    module TEXT NOT NULL,
    id TEXT NOT NULL,
    payload TEXT NOT NULL,
    searchText TEXT NOT NULL,
    createdAt TEXT DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (module, id)
  );
  CREATE INDEX IF NOT EXISTS idx_records_module ON records(module);
  CREATE INDEX IF NOT EXISTS idx_records_search ON records(module, searchText);
`);

const insertCard = db.prepare(`
  INSERT OR IGNORE INTO cards
    (id, cardNo, iccid, carrier, status, customer, deviceId, plan, dataUsed, dataTotal, activatedAt, expireAt, remark)
  VALUES
    (@id, @cardNo, @iccid, @carrier, @status, @customer, @deviceId, @plan, @dataUsed, @dataTotal, @activatedAt, @expireAt, @remark)
`);

const upsertRecord = db.prepare(`
  INSERT INTO records (module, id, payload, searchText)
  VALUES (@module, @id, @payload, @searchText)
  ON CONFLICT(module, id) DO UPDATE SET payload = excluded.payload, searchText = excluded.searchText
`);

function seedIfEmpty() {
  if (process.env.SEED_DEMO !== '1') return;

  if (db.prepare('SELECT COUNT(*) AS count FROM records').get().count === 0) {
    const tx = db.transaction(() => {
      Object.entries(seedRecords).forEach(([module, rows]) => {
        rows.forEach((row) => saveRecord(module, row));
      });
    });
    tx();
  }
}

function payloadSearchText(payload) {
  return Object.values(payload).join(' ').toLowerCase();
}

function saveRecord(module, payload) {
  upsertRecord.run({
    module,
    id: payload.id,
    payload: JSON.stringify(payload),
    searchText: payloadSearchText(payload)
  });
}

function autoCustomerId(name) {
  return `customer-auto-${Buffer.from(name).toString('hex').slice(0, 32)}`;
}

function ensureCustomerRecords(names) {
  const cleanNames = Array.from(new Set(
    names.map((name) => String(name || '').trim()).filter(Boolean)
  ));
  if (cleanNames.length === 0) return 0;

  const existing = new Set(
    db.prepare('SELECT payload FROM records WHERE module = ?')
      .all('customers')
      .map((row) => JSON.parse(row.payload).name)
      .filter(Boolean)
  );

  let created = 0;
  const tx = db.transaction(() => {
    cleanNames.forEach((name) => {
      if (existing.has(name)) return;
      saveRecord('customers', {
        id: autoCustomerId(name),
        name,
        contact: '',
        phone: '',
        industry: '',
        level: '普通客户',
        status: '正常',
        createdAt: today(),
        remark: '卡导入自动创建'
      });
      existing.add(name);
      created += 1;
    });
  });
  tx();
  return created;
}

function syncCustomerRecordsFromCards() {
  const names = db.prepare("SELECT DISTINCT customer FROM cards WHERE customer IS NOT NULL AND TRIM(customer) <> ''").all().map((row) => row.customer);
  return ensureCustomerRecords(names);
}

function parsePagination(query) {
  const page = Math.max(Number(query.page || 1), 1);
  const pageSize = Math.min(Math.max(Number(query.pageSize || 20), 1), 200);
  return { page, pageSize, offset: (page - 1) * pageSize };
}

function buildCardWhere(q, status = '') {
  const conditions = [];
  const params = {};
  if (q) {
    conditions.push('(cardNo LIKE @q OR iccid LIKE @q OR carrier LIKE @q OR status LIKE @q OR customer LIKE @q OR deviceId LIKE @q OR plan LIKE @q)');
    params.q = `%${q}%`;
  }
  if (status) {
    conditions.push('status = @status');
    params.status = status;
  }
  return {
    where: conditions.length ? `WHERE ${conditions.join(' AND ')}` : '',
    params
  };
}

function normalizeCard(body) {
  const cardNo = String(body.cardNo || '').trim();
  return {
    id: body.id || `card-${cardNo || uid('empty')}`,
    cardNo,
    iccid: String(body.iccid || '').trim(),
    carrier: body.carrier || '中国移动',
    status: body.status || '库存',
    customer: body.customer || '',
    deviceId: body.deviceId || '',
    plan: body.plan || '10GB/月',
    dataUsed: Number(body.dataUsed || 0),
    dataTotal: Number(body.dataTotal || 0),
    activatedAt: body.activatedAt || '',
    expireAt: body.expireAt || '',
    remark: body.remark || ''
  };
}

function csvEscape(value) {
  return `"${String(value ?? '').replaceAll('"', '""')}"`;
}

function sendCsv(res, filename, columns, rows) {
  const header = columns.map(([, label]) => csvEscape(label)).join(',');
  const body = rows.map((row) => columns.map(([key]) => {
    const value = Array.isArray(row[key]) ? row[key].join(';') : row[key];
    return csvEscape(value);
  }).join(','));

  res.setHeader('Content-Type', 'text/csv; charset=utf-8');
  res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
  res.send(`\uFEFF${[header, ...body].join('\n')}`);
}

function buildReportsData() {
  const carrierRows = db.prepare('SELECT COALESCE(NULLIF(carrier, \'\'), ?) AS name, COUNT(*) AS value FROM cards GROUP BY name ORDER BY value DESC').all('未设置');
  const statusRows = db.prepare('SELECT COALESCE(NULLIF(status, \'\'), ?) AS name, COUNT(*) AS value FROM cards GROUP BY name ORDER BY value DESC').all('未设置');
  const customers = db.prepare('SELECT payload FROM records WHERE module = ?').all('customers').map((row) => JSON.parse(row.payload));
  const devices = db.prepare('SELECT payload FROM records WHERE module = ?').all('devices').map((row) => JSON.parse(row.payload));
  const alarms = db.prepare('SELECT payload FROM records WHERE module = ?').all('alarms').map((row) => JSON.parse(row.payload));
  const cardCustomerNames = db.prepare("SELECT DISTINCT customer AS name FROM cards WHERE customer IS NOT NULL AND TRIM(customer) <> ''").all().map((row) => row.name);
  const customerNames = Array.from(new Set([
    ...customers.map((customer) => customer.name),
    ...cardCustomerNames
  ].filter(Boolean)));
  const customerRows = customerNames.map((name) => ({
    name,
    cards: db.prepare('SELECT COUNT(*) AS count FROM cards WHERE customer = ?').get(name).count,
    devices: devices.filter((device) => device.customer === name).length,
    alarms: alarms.filter((alarm) => alarm.customer === name).length
  }));
  return { carrierRows, statusRows, customerRows };
}

const recordExportColumns = {
  customers: [['name', '客户名称'], ['contact', '联系人'], ['phone', '联系电话'], ['industry', '行业'], ['level', '客户等级'], ['status', '状态'], ['createdAt', '创建日期'], ['remark', '备注']],
  devices: [['deviceNo', '设备编号'], ['name', '设备名称'], ['customer', '客户'], ['cardNo', '卡号'], ['model', '型号'], ['location', '安装位置'], ['status', '状态'], ['installedAt', '安装日期'], ['maintainer', '负责人']],
  alarms: [['alarmNo', '告警编号'], ['level', '级别'], ['type', '类型'], ['target', '对象'], ['customer', '客户'], ['status', '状态'], ['occurredAt', '发生日期'], ['handler', '处理人'], ['remark', '备注']],
  flows: [['cardNo', '卡号'], ['customer', '客户'], ['month', '账期'], ['usedGb', '已用GB'], ['totalGb', '总量GB'], ['threshold', '阈值%'], ['billingStatus', '出账状态'], ['lastSync', '同步日期']],
  operations: [['commandNo', '指令编号'], ['targetType', '对象类型'], ['target', '执行对象'], ['action', '动作'], ['status', '状态'], ['operator', '操作人'], ['createdAt', '创建日期'], ['remark', '备注']],
  orders: [['orderNo', '订单号'], ['startTime', '开始时间'], ['amount', '金额'], ['cardCount', '卡号数量'], ['cardDetails', '卡号明细'], ['createdAt', '创建时间'], ['remark', '备注']],
  users: [['username', '账号'], ['name', '姓名'], ['role', '角色'], ['phone', '联系电话'], ['status', '状态'], ['lastLogin', '最后登录'], ['remark', '备注']],
  roles: [['roleName', '角色名称'], ['scope', '菜单权限'], ['dataScope', '数据范围'], ['status', '状态'], ['createdAt', '创建日期'], ['remark', '备注']]
};

function parseCsv(text) {
  const rows = [];
  let row = [];
  let cell = '';
  let inQuotes = false;

  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    const next = text[index + 1];

    if (char === '"' && inQuotes && next === '"') {
      cell += '"';
      index += 1;
    } else if (char === '"') {
      inQuotes = !inQuotes;
    } else if (char === ',' && !inQuotes) {
      row.push(cell.trim());
      cell = '';
    } else if ((char === '\n' || char === '\r') && !inQuotes) {
      if (char === '\r' && next === '\n') index += 1;
      row.push(cell.trim());
      if (row.some(Boolean)) rows.push(row);
      row = [];
      cell = '';
    } else {
      cell += char;
    }
  }

  row.push(cell.trim());
  if (row.some(Boolean)) rows.push(row);
  return rows;
}

function normalizeHeader(header) {
  return String(header || '').replace(/^\uFEFF/, '').trim();
}

function decodeUploadedText(buffer) {
  const utf8 = buffer.toString('utf8').replace(/^\uFEFF/, '');
  if (!utf8.includes('\uFFFD')) return utf8;
  return iconv.decode(buffer, 'gb18030').replace(/^\uFEFF/, '');
}

function cardsFromCsv(text) {
  const rows = parseCsv(text);
  if (rows.length < 2) return { cards: [], requested: 0, invalid: 0 };

  let headers = rows[0].map((header) => headerMap[normalizeHeader(header)] || normalizeHeader(header));
  if (!headers.includes('cardNo') && !headers.includes('iccid')) {
    headers = templateKeys;
  } else {
    if (!headers.includes('cardNo')) headers[0] = 'cardNo';
    if (!headers.includes('iccid')) headers[1] = 'iccid';
  }
  const cards = [];
  let invalid = 0;

  rows.slice(1).forEach((cells) => {
    const payload = {};
    headers.forEach((key, index) => {
      if (key) payload[key] = cells[index] ?? '';
    });
    const card = normalizeCard(payload);
    if (!card.cardNo || !card.iccid) {
      invalid += 1;
      return;
    }
    cards.push(card);
  });

  return { cards, requested: rows.length - 1, invalid };
}

seedIfEmpty();
syncCustomerRecordsFromCards();

app.get('/api/health', (_req, res) => {
  res.json({ ok: true });
});

app.get('/api/summary', (_req, res) => {
  const cardStats = db.prepare(`
    SELECT
      COUNT(*) AS totalCards,
      COALESCE(SUM(CASE WHEN status = '已激活' THEN 1 ELSE 0 END), 0) AS activeCards,
      COALESCE(SUM(CASE WHEN status = '停机' THEN 1 ELSE 0 END), 0) AS stoppedCards,
      COALESCE(SUM(dataUsed), 0) AS totalUsed,
      COALESCE(SUM(dataTotal), 0) AS totalGb
    FROM cards
  `).get();

  const counts = Object.fromEntries([...recordModules].map((module) => [
    module,
    db.prepare('SELECT COUNT(*) AS count FROM records WHERE module = ?').get(module).count
  ]));

  const alarms = db.prepare('SELECT payload FROM records WHERE module = ?').all('alarms').map((row) => JSON.parse(row.payload));
  const devices = db.prepare('SELECT payload FROM records WHERE module = ?').all('devices').map((row) => JSON.parse(row.payload));

  res.json({
    ...cardStats,
    usageRate: cardStats.totalGb ? Math.round((cardStats.totalUsed / cardStats.totalGb) * 100) : 0,
    urgentAlarms: alarms.filter((alarm) => ['高', '紧急'].includes(alarm.level) && alarm.status !== '已关闭').length,
    openAlarms: alarms.filter((alarm) => alarm.status !== '已关闭').length,
    onlineDevices: devices.filter((device) => device.status === '在线').length,
    counts
  });
});

app.get('/api/reports', (_req, res) => {
  res.json(buildReportsData());
});

app.get('/api/reports/export', (_req, res) => {
  const reports = buildReportsData();
  const sections = [
    ['运营商分布'],
    ['名称', '数量'],
    ...reports.carrierRows.map((row) => [row.name, row.value]),
    [],
    ['生命周期分布'],
    ['名称', '数量'],
    ...reports.statusRows.map((row) => [row.name, row.value]),
    [],
    ['客户资产统计'],
    ['客户名称', '卡数量', '设备数量', '告警数量'],
    ...reports.customerRows.map((row) => [row.name, row.cards, row.devices, row.alarms])
  ];
  const csv = sections.map((row) => row.map(csvEscape).join(',')).join('\n');
  res.setHeader('Content-Type', 'text/csv; charset=utf-8');
  res.setHeader('Content-Disposition', `attachment; filename="reports-${Date.now()}.csv"`);
  res.send(`\uFEFF${csv}`);
});

app.get('/api/cards/template', (_req, res) => {
  const csv = `${cardTemplateHeaders.map(csvEscape).join(',')}\n`;
  res.setHeader('Content-Type', 'text/csv; charset=utf-8');
  res.setHeader('Content-Disposition', 'attachment; filename="iot-card-import-template.csv"');
  res.send(`\uFEFF${csv}`);
});

app.get('/api/cards/export', (req, res) => {
  const q = String(req.query.q || '').trim();
  const status = String(req.query.status || '').trim();
  const { where, params } = buildCardWhere(q, status);
  const rows = db.prepare(`SELECT * FROM cards ${where} ORDER BY CASE WHEN cardNo >= @appendedCardStart THEN 1 ELSE 0 END, cardNo ASC`).all({ ...params, appendedCardStart });
  sendCsv(res, `cards-${Date.now()}.csv`, [
    ['cardNo', '卡号'],
    ['iccid', 'ICCID'],
    ['carrier', '运营商'],
    ['status', '生命周期'],
    ['customer', '客户名称'],
    ['deviceId', '绑定设备编号'],
    ['plan', '流量套餐'],
    ['dataUsed', '已用流量GB'],
    ['dataTotal', '套餐总量GB'],
    ['activatedAt', '激活日期'],
    ['expireAt', '到期日期'],
    ['remark', '备注']
  ], rows);
});

app.get('/api/cards', (req, res) => {
  const { page, pageSize, offset } = parsePagination(req.query);
  const q = String(req.query.q || '').trim();
  const status = String(req.query.status || '').trim();
  const { where, params } = buildCardWhere(q, status);
  const total = db.prepare(`SELECT COUNT(*) AS count FROM cards ${where}`).get(params).count;
  const rows = db.prepare(`SELECT * FROM cards ${where} ORDER BY CASE WHEN cardNo >= @appendedCardStart THEN 1 ELSE 0 END, cardNo ASC LIMIT @pageSize OFFSET @offset`).all({ ...params, appendedCardStart, pageSize, offset });
  res.json({ rows, page, pageSize, total });
});

app.post('/api/cards', (req, res) => {
  const card = normalizeCard(req.body);
  if (!card.cardNo || !card.iccid) {
    res.status(400).json({ message: '卡号和ICCID不能为空' });
    return;
  }
  insertCard.run(card);
  ensureCustomerRecords([card.customer]);
  res.status(201).json(card);
});

app.put('/api/cards/:id', (req, res) => {
  const card = normalizeCard({ ...req.body, id: req.params.id });
  db.prepare(`
    UPDATE cards SET
      cardNo=@cardNo, iccid=@iccid, carrier=@carrier, status=@status, customer=@customer,
      deviceId=@deviceId, plan=@plan, dataUsed=@dataUsed, dataTotal=@dataTotal,
      activatedAt=@activatedAt, expireAt=@expireAt, remark=@remark
    WHERE id=@id
  `).run(card);
  ensureCustomerRecords([card.customer]);
  res.json(card);
});

app.post('/api/cards/batch-delete', (req, res) => {
  const ids = Array.isArray(req.body.ids) ? req.body.ids.filter(Boolean) : [];
  if (ids.length === 0) {
    res.status(400).json({ message: '请先选择要删除的物联网卡' });
    return;
  }
  const tx = db.transaction((values) => {
    const stmt = db.prepare('DELETE FROM cards WHERE id = ?');
    return values.reduce((total, id) => total + stmt.run(id).changes, 0);
  });
  res.json({ ok: true, deleted: tx(ids) });
});

app.post('/api/cards/batch-status', (req, res) => {
  const ids = Array.isArray(req.body.ids) ? req.body.ids.filter(Boolean) : [];
  const mode = req.body.mode === 'filter' ? 'filter' : 'selected';
  const fromStatus = String(req.body.fromStatus || '').trim();
  const toStatus = String(req.body.toStatus || '').trim();
  const q = String(req.body.q || '').trim();

  if (!toStatus) {
    res.status(400).json({ message: '请选择目标生命周期' });
    return;
  }

  if (mode === 'selected') {
    if (ids.length === 0) {
      res.status(400).json({ message: '请先选择要变更生命周期的物联网卡' });
      return;
    }
    const tx = db.transaction((values) => {
      const stmt = db.prepare('UPDATE cards SET status = ? WHERE id = ?');
      return values.reduce((total, id) => total + stmt.run(toStatus, id).changes, 0);
    });
    res.json({ ok: true, updated: tx(ids) });
    return;
  }

  const conditions = [];
  const params = { toStatus };
  if (fromStatus) {
    conditions.push('status = @fromStatus');
    params.fromStatus = fromStatus;
  }
  if (q) {
    conditions.push('(cardNo LIKE @q OR iccid LIKE @q OR carrier LIKE @q OR status LIKE @q OR customer LIKE @q OR deviceId LIKE @q OR plan LIKE @q)');
    params.q = `%${q}%`;
  }

  const where = conditions.length ? `WHERE ${conditions.join(' AND ')}` : '';
  const result = db.prepare(`UPDATE cards SET status = @toStatus ${where}`).run(params);
  res.json({ ok: true, updated: result.changes });
});

app.delete('/api/cards/:id', (req, res) => {
  db.prepare('DELETE FROM cards WHERE id = ?').run(req.params.id);
  res.json({ ok: true });
});

app.delete('/api/cards', (_req, res) => {
  const result = db.prepare('DELETE FROM cards').run();
  res.json({ ok: true, deleted: result.changes });
});

app.post('/api/cards/import-file', upload.single('file'), (req, res) => {
  if (!req.file) {
    res.status(400).json({ message: '请先选择要上传的导入文件' });
    return;
  }

  const filename = req.file.originalname || '';
  if (!/\.(csv|txt)$/i.test(filename)) {
    res.status(400).json({ message: '当前支持上传CSV文件，请先下载模板填写后上传' });
    return;
  }

  const text = decodeUploadedText(req.file.buffer);
  const { cards, requested, invalid } = cardsFromCsv(text);
  let inserted = 0;
  let duplicate = 0;

  const tx = db.transaction(() => {
    cards.forEach((card) => {
      const result = insertCard.run(card);
      inserted += result.changes;
      duplicate += result.changes === 0 ? 1 : 0;
    });
  });
  tx();
  const createdCustomers = ensureCustomerRecords(cards.map((card) => card.customer));

  res.json({ requested, inserted, duplicate, invalid, createdCustomers });
});

app.get('/api/:module', (req, res) => {
  const module = req.params.module;
  if (!recordModules.has(module)) {
    res.status(404).json({ message: '未知模块' });
    return;
  }
  const { page, pageSize, offset } = parsePagination(req.query);
  const q = String(req.query.q || '').trim().toLowerCase();
  const where = q ? 'WHERE module = @module AND searchText LIKE @q' : 'WHERE module = @module';
  const params = q ? { module, q: `%${q}%` } : { module };
  const total = db.prepare(`SELECT COUNT(*) AS count FROM records ${where}`).get(params).count;
  const rows = db.prepare(`SELECT payload FROM records ${where} ORDER BY createdAt DESC LIMIT @pageSize OFFSET @offset`).all({ ...params, pageSize, offset }).map((row) => JSON.parse(row.payload));
  res.json({ rows, page, pageSize, total });
});

app.get('/api/:module/export', (req, res) => {
  const module = req.params.module;
  if (!recordModules.has(module)) {
    res.status(404).json({ message: '未知模块' });
    return;
  }
  const q = String(req.query.q || '').trim().toLowerCase();
  const where = q ? 'WHERE module = @module AND searchText LIKE @q' : 'WHERE module = @module';
  const params = q ? { module, q: `%${q}%` } : { module };
  const rows = db.prepare(`SELECT payload FROM records ${where} ORDER BY createdAt DESC`).all(params).map((row) => JSON.parse(row.payload));
  sendCsv(res, `${module}-${Date.now()}.csv`, recordExportColumns[module] || [['id', 'ID']], rows);
});

app.post('/api/:module/batch-delete', (req, res) => {
  const module = req.params.module;
  if (!recordModules.has(module)) {
    res.status(404).json({ message: '未知模块' });
    return;
  }
  const ids = Array.isArray(req.body.ids) ? req.body.ids.filter(Boolean) : [];
  if (ids.length === 0) {
    res.status(400).json({ message: '请先选择要删除的数据' });
    return;
  }
  const tx = db.transaction((values) => {
    const stmt = db.prepare('DELETE FROM records WHERE module = ? AND id = ?');
    return values.reduce((total, id) => total + stmt.run(module, id).changes, 0);
  });
  res.json({ ok: true, deleted: tx(ids) });
});

app.post('/api/:module', (req, res) => {
  const module = req.params.module;
  if (!recordModules.has(module)) {
    res.status(404).json({ message: '未知模块' });
    return;
  }
  const payload = { ...req.body, id: req.body.id || uid(module.slice(0, -1) || 'record') };
  saveRecord(module, payload);
  res.status(201).json(payload);
});

app.put('/api/:module/:id', (req, res) => {
  const module = req.params.module;
  if (!recordModules.has(module)) {
    res.status(404).json({ message: '未知模块' });
    return;
  }
  const payload = { ...req.body, id: req.params.id };
  saveRecord(module, payload);
  res.json(payload);
});

app.delete('/api/:module/:id', (req, res) => {
  const module = req.params.module;
  if (!recordModules.has(module)) {
    res.status(404).json({ message: '未知模块' });
    return;
  }
  db.prepare('DELETE FROM records WHERE module = ? AND id = ?').run(module, req.params.id);
  res.json({ ok: true });
});

const port = Number(process.env.PORT || 5174);
app.listen(port, () => {
  console.log(`API server listening on http://localhost:${port}`);
});
