import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  Activity,
  AlertTriangle,
  BarChart3,
  Bell,
  Boxes,
  Building2,
  CheckCircle2,
  ChevronDown,
  ClipboardList,
  Database,
  Edit3,
  FileDown,
  Gauge,
  HardDrive,
  Import,
  LayoutDashboard,
  Lock,
  Plus,
  RefreshCcw,
  Search,
  Settings,
  ShieldCheck,
  Signal,
  SlidersHorizontal,
  Trash2,
  Upload,
  UserRound,
  Wrench
} from 'lucide-react';
import './styles.css';

const api = {
  async request(path, options = {}) {
    const isFormData = options.body instanceof FormData;
    const response = await fetch(`/api${path}`, {
      headers: isFormData ? undefined : { 'Content-Type': 'application/json' },
      ...options
    });
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: '请求失败' }));
      throw new Error(error.message || '请求失败');
    }
    return response.json();
  },
  list(module, params) {
    const query = new URLSearchParams(params);
    return this.request(`/${module}?${query.toString()}`);
  },
  create(module, payload) {
    return this.request(`/${module}`, { method: 'POST', body: JSON.stringify(payload) });
  },
  update(module, id, payload) {
    return this.request(`/${module}/${id}`, { method: 'PUT', body: JSON.stringify(payload) });
  },
  remove(module, id) {
    return this.request(`/${module}/${id}`, { method: 'DELETE' });
  },
  batchDelete(module, ids) {
    return this.request(`/${module}/batch-delete`, { method: 'POST', body: JSON.stringify({ ids }) });
  },
  batchCardStatus(payload) {
    return this.request('/cards/batch-status', { method: 'POST', body: JSON.stringify(payload) });
  }
};

const nowDate = () => new Date().toISOString().slice(0, 10);

const optionMap = {
  status: ['库存', '已激活', '停机', '销户'],
  carrier: ['中国移动', '中国联通', '中国电信', '虚拟运营商'],
  customerStatus: ['正常', '停用', '欠费', '待审核'],
  customerLevel: ['重点客户', '普通客户', '试用客户'],
  deviceStatus: ['在线', '离线', '维护中', '故障'],
  alarmLevel: ['低', '中', '高', '紧急'],
  alarmStatus: ['未处理', '处理中', '已处理', '已关闭'],
  billingStatus: ['未出账', '待确认', '已出账', '已结算'],
  operationTargetType: ['物联网卡', '设备', '客户'],
  operationAction: ['停机', '复机', '刷新流量', '重启设备', '参数下发', '解绑设备'],
  operationStatus: ['待执行', '执行中', '成功', '失败'],
  userStatus: ['启用', '禁用'],
  roleStatus: ['启用', '禁用']
};

const modules = [
  { key: 'dashboard', title: '工作台', icon: LayoutDashboard },
  {
    key: 'cards',
    apiKey: 'cards',
    title: '物联网卡库存管理',
    icon: Boxes,
    entityName: '物联网卡',
    columns: [['cardNo', '卡号'], ['iccid', 'ICCID'], ['carrier', '运营商'], ['status', '状态'], ['customer', '客户'], ['deviceId', '绑定设备'], ['plan', '套餐'], ['dataUsed', '已用GB'], ['dataTotal', '总量GB'], ['expireAt', '到期日期']],
    fields: [
      { name: 'cardNo', label: '卡号', required: true, placeholder: '1440159100000' },
      { name: 'iccid', label: 'ICCID', required: true, placeholder: '89860415152680029500' },
      { name: 'carrier', label: '运营商', type: 'select', options: optionMap.carrier },
      { name: 'status', label: '状态', type: 'select', options: optionMap.status },
      { name: 'customer', label: '客户名称' },
      { name: 'deviceId', label: '绑定设备编号' },
      { name: 'plan', label: '流量套餐' },
      { name: 'dataUsed', label: '已用流量GB', type: 'number' },
      { name: 'dataTotal', label: '套餐总量GB', type: 'number' },
      { name: 'activatedAt', label: '激活日期', type: 'date' },
      { name: 'expireAt', label: '到期日期', type: 'date' },
      { name: 'remark', label: '备注', type: 'textarea' }
    ],
    defaults: { carrier: '中国移动', status: '库存', plan: '10GB/月', dataUsed: 0, dataTotal: 10, activatedAt: nowDate(), expireAt: '2027-05-13' }
  },
  { key: 'import', title: '物联网卡导入', icon: Import },
  { key: 'lifecycle', title: '生命周期批量操作', icon: RefreshCcw },
  { key: 'orders', title: '订单管理', icon: ClipboardList },
  {
    key: 'customers',
    apiKey: 'customers',
    title: '客户管理',
    icon: Building2,
    entityName: '客户',
    columns: [['name', '客户名称'], ['contact', '联系人'], ['phone', '联系电话'], ['industry', '行业'], ['level', '客户等级'], ['status', '状态'], ['createdAt', '创建日期']],
    fields: [
      { name: 'name', label: '客户名称', required: true },
      { name: 'contact', label: '联系人' },
      { name: 'phone', label: '联系电话' },
      { name: 'industry', label: '行业' },
      { name: 'level', label: '客户等级', type: 'select', options: optionMap.customerLevel },
      { name: 'status', label: '状态', type: 'select', options: optionMap.customerStatus },
      { name: 'createdAt', label: '创建日期', type: 'date' },
      { name: 'remark', label: '备注', type: 'textarea' }
    ],
    defaults: { level: '普通客户', status: '正常', createdAt: nowDate() }
  },
  {
    key: 'devices',
    apiKey: 'devices',
    title: '设备档案',
    icon: HardDrive,
    entityName: '设备',
    columns: [['deviceNo', '设备编号'], ['name', '设备名称'], ['customer', '客户'], ['cardNo', '卡号'], ['model', '型号'], ['location', '安装位置'], ['status', '状态'], ['maintainer', '负责人']],
    fields: [
      { name: 'deviceNo', label: '设备编号', required: true },
      { name: 'name', label: '设备名称', required: true },
      { name: 'customer', label: '所属客户' },
      { name: 'cardNo', label: '绑定卡号', placeholder: '1442473290000' },
      { name: 'model', label: '设备型号' },
      { name: 'location', label: '安装位置' },
      { name: 'status', label: '状态', type: 'select', options: optionMap.deviceStatus },
      { name: 'installedAt', label: '安装日期', type: 'date' },
      { name: 'maintainer', label: '负责人' }
    ],
    defaults: { status: '在线', installedAt: nowDate() }
  },
  {
    key: 'alarms',
    apiKey: 'alarms',
    title: '告警管理',
    icon: AlertTriangle,
    entityName: '告警',
    columns: [['alarmNo', '告警编号'], ['level', '级别'], ['type', '类型'], ['target', '对象'], ['customer', '客户'], ['status', '状态'], ['occurredAt', '发生日期'], ['handler', '处理人']],
    fields: [
      { name: 'alarmNo', label: '告警编号', required: true },
      { name: 'level', label: '级别', type: 'select', options: optionMap.alarmLevel },
      { name: 'type', label: '告警类型' },
      { name: 'target', label: '告警对象' },
      { name: 'customer', label: '客户' },
      { name: 'status', label: '处理状态', type: 'select', options: optionMap.alarmStatus },
      { name: 'occurredAt', label: '发生日期', type: 'date' },
      { name: 'handler', label: '处理人' },
      { name: 'remark', label: '处理说明', type: 'textarea' }
    ],
    defaults: { alarmNo: `ALM-${Date.now()}`, level: '中', status: '未处理', occurredAt: nowDate() }
  },
  {
    key: 'flows',
    apiKey: 'flows',
    title: '流量管理',
    icon: Gauge,
    entityName: '流量记录',
    columns: [['cardNo', '卡号'], ['customer', '客户'], ['month', '账期'], ['usedGb', '已用GB'], ['totalGb', '总量GB'], ['threshold', '阈值%'], ['billingStatus', '出账状态'], ['lastSync', '同步日期']],
    fields: [
      { name: 'cardNo', label: '卡号', required: true, placeholder: '1442473290000' },
      { name: 'customer', label: '客户' },
      { name: 'month', label: '账期', type: 'month' },
      { name: 'usedGb', label: '已用GB', type: 'number' },
      { name: 'totalGb', label: '总量GB', type: 'number' },
      { name: 'threshold', label: '告警阈值%', type: 'number' },
      { name: 'billingStatus', label: '出账状态', type: 'select', options: optionMap.billingStatus },
      { name: 'lastSync', label: '同步日期', type: 'date' }
    ],
    defaults: { month: '2026-05', usedGb: 0, totalGb: 10, threshold: 80, billingStatus: '未出账', lastSync: nowDate() }
  },
  {
    key: 'operations',
    apiKey: 'operations',
    title: '运维控制',
    icon: Wrench,
    entityName: '运维指令',
    columns: [['commandNo', '指令编号'], ['targetType', '对象类型'], ['target', '执行对象'], ['action', '动作'], ['status', '状态'], ['operator', '操作人'], ['createdAt', '创建日期']],
    fields: [
      { name: 'commandNo', label: '指令编号', required: true },
      { name: 'targetType', label: '对象类型', type: 'select', options: optionMap.operationTargetType },
      { name: 'target', label: '执行对象' },
      { name: 'action', label: '动作', type: 'select', options: optionMap.operationAction },
      { name: 'status', label: '状态', type: 'select', options: optionMap.operationStatus },
      { name: 'operator', label: '操作人' },
      { name: 'createdAt', label: '创建日期', type: 'date' },
      { name: 'remark', label: '备注', type: 'textarea' }
    ],
    defaults: { commandNo: `CMD-${Date.now()}`, targetType: '物联网卡', action: '刷新流量', status: '待执行', operator: '管理员', createdAt: nowDate() }
  },
  { key: 'reports', title: '统计报表', icon: BarChart3 },
  { key: 'system', title: '系统管理', icon: Settings }
];

const systemSections = [
  {
    title: '用户管理',
    icon: UserRound,
    apiKey: 'users',
    entityName: '用户',
    columns: [['username', '账号'], ['name', '姓名'], ['role', '角色'], ['phone', '联系电话'], ['status', '状态'], ['lastLogin', '最后登录']],
    fields: [
      { name: 'username', label: '账号', required: true },
      { name: 'name', label: '姓名', required: true },
      { name: 'role', label: '角色' },
      { name: 'phone', label: '联系电话' },
      { name: 'status', label: '状态', type: 'select', options: optionMap.userStatus },
      { name: 'lastLogin', label: '最后登录', type: 'date' },
      { name: 'remark', label: '备注', type: 'textarea' }
    ],
    defaults: { role: '操作员', status: '启用', lastLogin: nowDate() }
  },
  {
    title: '角色权限',
    icon: ShieldCheck,
    apiKey: 'roles',
    entityName: '角色',
    columns: [['roleName', '角色名称'], ['scope', '菜单权限'], ['dataScope', '数据范围'], ['status', '状态'], ['createdAt', '创建日期']],
    fields: [
      { name: 'roleName', label: '角色名称', required: true },
      { name: 'scope', label: '菜单权限' },
      { name: 'dataScope', label: '数据范围' },
      { name: 'status', label: '状态', type: 'select', options: optionMap.roleStatus },
      { name: 'createdAt', label: '创建日期', type: 'date' },
      { name: 'remark', label: '备注', type: 'textarea' }
    ],
    defaults: { scope: '库存,客户,设备,告警,流量', dataScope: '本部门客户', status: '启用', createdAt: nowDate() }
  }
];

function App() {
  const [active, setActive] = useState('dashboard');
  const [summary, setSummary] = useState(null);
  const [toast, setToast] = useState('');
  const [refreshKey, setRefreshKey] = useState(0);

  const module = modules.find((item) => item.key === active) ?? modules[0];

  const notify = (message) => {
    setToast(message);
    window.clearTimeout(window.__iotToastTimer);
    window.__iotToastTimer = window.setTimeout(() => setToast(''), 2200);
  };

  const refreshSummary = async () => {
    const next = await api.request('/summary');
    setSummary(next);
  };

  useEffect(() => {
    refreshSummary().catch((error) => notify(error.message));
  }, [refreshKey]);

  return (
    <div className="appShell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brandMark"><Signal size={24} /></div>
          <div>
            <h1>物联网卡管理平台</h1>
            <span>Database Console</span>
          </div>
        </div>
        <nav>
          {modules.map((item) => {
            const Icon = item.icon;
            return (
              <button key={item.key} className={active === item.key ? 'navItem active' : 'navItem'} onClick={() => setActive(item.key)} title={item.title}>
                <Icon size={18} />
                <span>{item.title}</span>
              </button>
            );
          })}
        </nav>
      </aside>

      <main className="main">
        <header className="topbar">
          <div>
            <p className="eyebrow">管理控制台</p>
            <h2>{module.title}</h2>
          </div>
          <div className="topActions">
            <button className="iconTextButton" onClick={() => setRefreshKey((value) => value + 1)} title="刷新">
              <RefreshCcw size={17} />
              <span>刷新</span>
            </button>
            <div className="account">
              <UserRound size={18} />
              <span>管理员</span>
              <ChevronDown size={16} />
            </div>
          </div>
        </header>

        {active === 'dashboard' && <Dashboard summary={summary} setActive={setActive} notify={notify} />}
        {active === 'import' && <ImportPage notify={notify} onChanged={() => setRefreshKey((value) => value + 1)} />}
        {active === 'lifecycle' && <LifecyclePage notify={notify} onChanged={() => setRefreshKey((value) => value + 1)} />}
        {active === 'orders' && <OrdersPage notify={notify} onChanged={() => setRefreshKey((value) => value + 1)} />}
        {module.apiKey && <EntityPage key={module.apiKey} config={module} notify={notify} onChanged={() => setRefreshKey((value) => value + 1)} />}
        {active === 'reports' && <Reports summary={summary} notify={notify} />}
        {active === 'system' && <SystemPage notify={notify} onChanged={() => setRefreshKey((value) => value + 1)} />}
      </main>

      {toast && (
        <div className="toast">
          <CheckCircle2 size={18} />
          <span>{toast}</span>
        </div>
      )}
    </div>
  );
}

function Dashboard({ summary, setActive, notify }) {
  const [cards, setCards] = useState([]);
  const [alarms, setAlarms] = useState([]);
  const safeSummary = summary ?? { totalCards: 0, activeCards: 0, onlineDevices: 0, urgentAlarms: 0, totalUsed: 0, totalGb: 0, usageRate: 0, stoppedCards: 0, counts: {} };

  useEffect(() => {
    Promise.all([
      api.list('cards', { page: 1, pageSize: 6, q: '' }),
      api.list('alarms', { page: 1, pageSize: 4, q: '' })
    ])
      .then(([cardResult, alarmResult]) => {
        setCards(cardResult.rows);
        setAlarms(alarmResult.rows);
      })
      .catch((error) => notify(error.message));
  }, []);

  const blocks = [
    { label: '物联网卡总数', value: safeSummary.totalCards, icon: Boxes, accent: 'blue', target: 'cards' },
    { label: '已激活卡', value: safeSummary.activeCards, icon: CheckCircle2, accent: 'green', target: 'cards' },
    { label: '在线设备', value: safeSummary.onlineDevices, icon: Activity, accent: 'cyan', target: 'devices' },
    { label: '高优先告警', value: safeSummary.urgentAlarms, icon: Bell, accent: 'red', target: 'alarms' }
  ];

  return (
    <section className="contentStack">
      <div className="metricGrid">
        {blocks.map((block) => {
          const Icon = block.icon;
          return (
            <button key={block.label} className={`metric metric-${block.accent}`} onClick={() => setActive(block.target)}>
              <span className="metricIcon"><Icon size={22} /></span>
              <span className="metricLabel">{block.label}</span>
              <strong>{block.value}</strong>
            </button>
          );
        })}
      </div>

      <div className="dashboardGrid">
        <section className="panel">
          <PanelTitle icon={Gauge} title="流量使用概览" />
          <div className="usageHero">
            <div className="ring" style={{ '--progress': `${safeSummary.usageRate}%` }}><span>{safeSummary.usageRate}%</span></div>
            <div>
              <h3>{Number(safeSummary.totalUsed).toFixed(1)} GB / {Number(safeSummary.totalGb).toFixed(1)} GB</h3>
              <p>当前账期总流量消耗</p>
              <div className="statusLine">
                <span>停机卡：{safeSummary.stoppedCards}</span>
                <span>客户数：{safeSummary.counts?.customers ?? 0}</span>
              </div>
            </div>
          </div>
        </section>

        <section className="panel">
          <PanelTitle icon={ClipboardList} title="待处理告警" />
          <div className="todoList">
            {alarms.map((alarm) => (
              <div className="todoItem" key={alarm.id}>
                <span className={`tag tag-${alarm.level}`}>{alarm.level}</span>
                <div>
                  <strong>{alarm.type}</strong>
                  <p>{alarm.target} · {alarm.status}</p>
                </div>
              </div>
            ))}
          </div>
        </section>
      </div>

      <section className="panel">
        <PanelTitle icon={Boxes} title="最近物联网卡" />
        <DataTable columns={[['cardNo', '卡号'], ['iccid', 'ICCID'], ['carrier', '运营商'], ['status', '状态'], ['customer', '客户'], ['dataUsed', '已用GB']]} rows={cards} compact />
      </section>
    </section>
  );
}

function EntityPage({ config, notify, onChanged }) {
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(config.apiKey === 'cards' ? 50 : 20);
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState(null);
  const [editingId, setEditingId] = useState('');
  const [selectedIds, setSelectedIds] = useState([]);

  const loadRows = async () => {
    setLoading(true);
    try {
      const result = await api.list(config.apiKey, { page, pageSize, q: query });
      setRows(result.rows);
      setTotal(result.total);
      setSelectedIds([]);
    } catch (error) {
      notify(error.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadRows();
  }, [config.apiKey, page, pageSize, query]);

  const openCreate = () => {
    setEditingId('');
    setForm(config.defaults ?? {});
  };

  const openEdit = (row) => {
    setEditingId(row.id);
    setForm(row);
  };

  const submit = async (event) => {
    event.preventDefault();
    try {
      if (editingId) {
        await api.update(config.apiKey, editingId, form);
        notify(`已更新${config.entityName}`);
      } else {
        await api.create(config.apiKey, form);
        notify(`已新增${config.entityName}`);
      }
      setForm(null);
      await loadRows();
      onChanged?.();
    } catch (error) {
      notify(error.message);
    }
  };

  const remove = async (id) => {
    if (!window.confirm(`确认删除该${config.entityName}？`)) return;
    try {
      await api.remove(config.apiKey, id);
      notify(`已删除${config.entityName}`);
      await loadRows();
      onChanged?.();
    } catch (error) {
      notify(error.message);
    }
  };

  const batchRemove = async () => {
    if (selectedIds.length === 0) {
      notify('请先勾选要删除的数据');
      return;
    }
    if (!window.confirm(`确认批量删除已选的 ${selectedIds.length} 条${config.entityName}？`)) return;
    try {
      const result = await api.batchDelete(config.apiKey, selectedIds);
      notify(`已批量删除 ${result.deleted} 条${config.entityName}`);
      await loadRows();
      onChanged?.();
    } catch (error) {
      notify(error.message);
    }
  };

  const exportCsv = () => {
    const params = new URLSearchParams({ q: query });
    window.location.href = `/api/${config.apiKey}/export?${params.toString()}`;
    notify('正在导出CSV');
  };

  return (
    <section className="contentStack">
      <Toolbar
        keyword={keyword}
        setKeyword={setKeyword}
        onSearch={() => {
          setPage(1);
          setQuery(keyword);
        }}
        onCreate={openCreate}
        onExport={exportCsv}
        onBatchDelete={batchRemove}
        selectedCount={selectedIds.length}
        createText={`新增${config.entityName}`}
      />
      <section className="panel">
        {loading ? (
          <div className="emptyCell">加载中...</div>
        ) : (
          <DataTable
            columns={config.columns}
            rows={rows}
            onEdit={openEdit}
            onDelete={remove}
            selectedIds={selectedIds}
            setSelectedIds={setSelectedIds}
          />
        )}
        <Pagination page={page} pageSize={pageSize} total={total} setPage={setPage} setPageSize={setPageSize} />
      </section>
      {form && (
        <EntityModal
          title={editingId ? `编辑${config.entityName}` : `新增${config.entityName}`}
          fields={config.fields}
          form={form}
          setForm={setForm}
          onClose={() => setForm(null)}
          onSubmit={submit}
        />
      )}
    </section>
  );
}

function ImportPage({ notify, onChanged }) {
  const [file, setFile] = useState(null);
  const [busy, setBusy] = useState(false);

  const downloadTemplate = () => {
    window.location.href = '/api/cards/template';
  };

  const submitImport = async () => {
    if (!file) {
      notify('请先选择本地导入文件');
      return;
    }
    setBusy(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const result = await api.request('/cards/import-file', { method: 'POST', body: formData });
      notify(`读取 ${result.requested} 行，新增 ${result.inserted} 张，重复 ${result.duplicate} 张，无效 ${result.invalid} 行，补充客户 ${result.createdCustomers || 0} 个`);
      setFile(null);
      onChanged?.();
    } catch (error) {
      notify(error.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="contentStack">
      <section className="panel">
        <PanelTitle icon={Upload} title="文件导入" />
        <div className="importSteps">
          <div className="stepBox">
            <strong>1</strong>
            <div>
              <h4>下载导入模板</h4>
              <p>模板为 CSV 文件，可用 Excel 打开。按表头填写卡号、ICCID、运营商、状态、套餐等字段。</p>
            </div>
            <button className="iconTextButton" onClick={downloadTemplate}>
              <FileDown size={17} />
              <span>下载模板</span>
            </button>
          </div>
          <div className="stepBox">
            <strong>2</strong>
            <div>
              <h4>选择本地文件</h4>
              <p>保存为 CSV 后上传。卡号和 ICCID 必填，重复数据会自动跳过。</p>
            </div>
            <label className="filePicker">
              <input
                type="file"
                accept=".csv,.txt"
                onChange={(event) => setFile(event.target.files?.[0] ?? null)}
              />
              <Upload size={17} />
              <span>{file ? file.name : '选择文件'}</span>
            </label>
          </div>
        </div>

        <div className="formActions left">
          <button className="primaryButton" disabled={busy} onClick={submitImport}>
            <Import size={18} />
            <span>{busy ? '上传导入中...' : '上传并导入'}</span>
          </button>
        </div>
      </section>

      <section className="panel">
        <PanelTitle icon={ClipboardList} title="模板字段" />
        <DataTable
          columns={[
            ['name', '字段'],
            ['required', '是否必填'],
            ['example', '示例']
          ]}
          rows={[
            { id: 'tpl-1', name: '卡号', required: '是', example: '1442473290000' },
            { id: 'tpl-2', name: 'ICCID', required: '是', example: '898608771525C0090000' },
            { id: 'tpl-3', name: '运营商', required: '否', example: '中国移动' },
            { id: 'tpl-4', name: '状态', required: '否', example: '库存' },
            { id: 'tpl-5', name: '客户名称', required: '否', example: '华东冷链科技' },
            { id: 'tpl-6', name: '绑定设备编号', required: '否', example: 'DEV-LD-1001' },
            { id: 'tpl-7', name: '流量套餐', required: '否', example: '10GB/月' },
            { id: 'tpl-8', name: '套餐总量GB', required: '否', example: '10' }
          ]}
          compact
        />
      </section>
    </section>
  );
}

function LifecyclePage({ notify, onChanged }) {
  const lifecycleStatuses = ['库存', '已激活', '停机', '销户'];
  const columns = [['cardNo', '卡号'], ['iccid', 'ICCID'], ['carrier', '运营商'], ['status', '生命周期'], ['customer', '客户'], ['deviceId', '绑定设备'], ['plan', '套餐']];
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [fromStatus, setFromStatus] = useState('库存');
  const [toStatus, setToStatus] = useState('已激活');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(50);
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [selectedIds, setSelectedIds] = useState([]);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);

  const loadRows = async () => {
    setLoading(true);
    try {
      const result = await api.list('cards', { page, pageSize, q: query, status: fromStatus });
      setRows(result.rows);
      setTotal(result.total);
      setSelectedIds([]);
    } catch (error) {
      notify(error.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadRows();
  }, [page, pageSize, query, fromStatus]);

  const runChange = async (mode) => {
    if (!toStatus) {
      notify('请选择目标生命周期');
      return;
    }
    if (fromStatus && fromStatus === toStatus) {
      notify('源生命周期和目标生命周期不能相同');
      return;
    }
    if (mode === 'selected' && selectedIds.length === 0) {
      notify('请先勾选要变更生命周期的物联网卡');
      return;
    }
    const text = mode === 'selected'
      ? `确认将已选 ${selectedIds.length} 张卡从“${fromStatus || '任意状态'}”变更为“${toStatus}”？`
      : `确认将当前筛选条件下的全部卡从“${fromStatus || '任意状态'}”变更为“${toStatus}”？`;
    if (!window.confirm(text)) return;

    setBusy(true);
    try {
      const result = await api.batchCardStatus({
        mode,
        ids: selectedIds,
        fromStatus,
        toStatus,
        q: query
      });
      notify(`已变更 ${result.updated} 张物联网卡生命周期`);
      await loadRows();
      onChanged?.();
    } catch (error) {
      notify(error.message);
    } finally {
      setBusy(false);
    }
  };

  const exportLifecycleCards = () => {
    const params = new URLSearchParams({ q: query, status: fromStatus });
    window.location.href = `/api/cards/export?${params.toString()}`;
    notify('正在导出当前筛选卡数据');
  };

  return (
    <section className="contentStack">
      <section className="panel lifecyclePanel">
        <PanelTitle icon={RefreshCcw} title="生命周期批量操作" />
        <div className="lifecycleControls">
          <label className="field">
            <span>源生命周期</span>
            <select value={fromStatus} onChange={(event) => { setPage(1); setFromStatus(event.target.value); }}>
              <option value="">全部状态</option>
              {lifecycleStatuses.map((status) => <option key={status} value={status}>{status}</option>)}
            </select>
          </label>
          <label className="field">
            <span>目标生命周期</span>
            <select value={toStatus} onChange={(event) => setToStatus(event.target.value)}>
              {lifecycleStatuses.map((status) => <option key={status} value={status}>{status}</option>)}
            </select>
          </label>
          <label className="field">
            <span>卡号/ICCID/客户搜索</span>
            <input
              value={keyword}
              placeholder="1442473290000 或 898608771525C0090000"
              onChange={(event) => setKeyword(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  setPage(1);
                  setQuery(keyword);
                }
              }}
            />
          </label>
          <div className="lifecycleActions">
            <button className="iconTextButton" onClick={() => { setPage(1); setQuery(keyword); }}>
              <Search size={17} />
              <span>查询</span>
            </button>
            <button className="primaryButton" disabled={busy || selectedIds.length === 0} onClick={() => runChange('selected')}>
              <CheckCircle2 size={18} />
              <span>变更选中</span>
            </button>
            <button className="iconTextButton" disabled={busy} onClick={() => runChange('filter')}>
              <RefreshCcw size={17} />
              <span>变更当前筛选全部</span>
            </button>
            <button className="iconTextButton" onClick={exportLifecycleCards}>
              <FileDown size={17} />
              <span>导出筛选结果</span>
            </button>
          </div>
        </div>
      </section>

      <section className="panel">
        {loading ? (
          <div className="emptyCell">加载中...</div>
        ) : (
          <DataTable
            columns={columns}
            rows={rows}
            selectedIds={selectedIds}
            setSelectedIds={setSelectedIds}
          />
        )}
        <Pagination page={page} pageSize={pageSize} total={total} setPage={setPage} setPageSize={setPageSize} />
      </section>
    </section>
  );
}

function OrdersPage({ notify, onChanged }) {
  const columns = [['orderNo', '订单号'], ['startTime', '开始时间'], ['amount', '金额'], ['cardCount', '卡号数量'], ['createdAt', '创建时间']];
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [selectedIds, setSelectedIds] = useState([]);
  const [form, setForm] = useState(null);
  const [editingId, setEditingId] = useState('');
  const [detailOrder, setDetailOrder] = useState(null);

  const normalizeOrder = (order) => {
    const cardDetails = Array.isArray(order.cardDetails) ? order.cardDetails : parseCardDetails(order.cardDetailsText || '');
    return {
      ...order,
      cardDetails,
      cardCount: cardDetails.length,
      amount: Number(order.amount || 0).toFixed(2)
    };
  };

  const loadRows = async () => {
    setLoading(true);
    try {
      const result = await api.list('orders', { page, pageSize, q: query });
      setRows(result.rows.map(normalizeOrder));
      setTotal(result.total);
      setSelectedIds([]);
    } catch (error) {
      notify(error.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadRows();
  }, [page, pageSize, query]);

  const openCreate = () => {
    setEditingId('');
    setForm({
      orderNo: `ORD-${Date.now()}`,
      startTime: nowDate(),
      amount: '',
      cardDetailsText: '',
      remark: ''
    });
  };

  const openEdit = (row) => {
    setEditingId(row.id);
    setForm({
      ...row,
      cardDetailsText: (row.cardDetails || []).join('\n')
    });
  };

  const submit = async (event) => {
    event.preventDefault();
    const cardDetails = parseCardDetails(form.cardDetailsText || '');
    const payload = {
      ...form,
      amount: Number(form.amount || 0),
      cardDetails,
      cardCount: cardDetails.length,
      createdAt: form.createdAt || nowDate()
    };
    delete payload.cardDetailsText;

    try {
      if (editingId) {
        await api.update('orders', editingId, payload);
        notify('已更新订单');
      } else {
        await api.create('orders', payload);
        notify('已新增订单');
      }
      setForm(null);
      await loadRows();
      onChanged?.();
    } catch (error) {
      notify(error.message);
    }
  };

  const remove = async (id) => {
    if (!window.confirm('确认删除该订单？')) return;
    try {
      await api.remove('orders', id);
      notify('已删除订单');
      await loadRows();
      onChanged?.();
    } catch (error) {
      notify(error.message);
    }
  };

  const batchRemove = async () => {
    if (selectedIds.length === 0) {
      notify('请先勾选要删除的订单');
      return;
    }
    if (!window.confirm(`确认批量删除已选的 ${selectedIds.length} 个订单？`)) return;
    try {
      const result = await api.batchDelete('orders', selectedIds);
      notify(`已批量删除 ${result.deleted} 个订单`);
      await loadRows();
      onChanged?.();
    } catch (error) {
      notify(error.message);
    }
  };

  const exportOrders = () => {
    const params = new URLSearchParams({ q: query });
    window.location.href = `/api/orders/export?${params.toString()}`;
    notify('正在导出订单');
  };

  return (
    <section className="contentStack">
      <Toolbar
        keyword={keyword}
        setKeyword={setKeyword}
        onSearch={() => {
          setPage(1);
          setQuery(keyword);
        }}
        onCreate={openCreate}
        onExport={exportOrders}
        onBatchDelete={batchRemove}
        selectedCount={selectedIds.length}
        createText="新增订单"
      />
      <section className="panel">
        {loading ? (
          <div className="emptyCell">加载中...</div>
        ) : (
          <DataTable
            columns={columns}
            rows={rows}
            onEdit={openEdit}
            onDelete={remove}
            onViewDetails={setDetailOrder}
            selectedIds={selectedIds}
            setSelectedIds={setSelectedIds}
          />
        )}
        <Pagination page={page} pageSize={pageSize} total={total} setPage={setPage} setPageSize={setPageSize} />
      </section>

      {form && (
        <OrderModal
          form={form}
          setForm={setForm}
          editing={Boolean(editingId)}
          onClose={() => setForm(null)}
          onSubmit={submit}
          notify={notify}
        />
      )}

      {detailOrder && <OrderDetailModal order={detailOrder} onClose={() => setDetailOrder(null)} />}
    </section>
  );
}

function OrderModal({ form, setForm, editing, onClose, onSubmit, notify }) {
  const importFile = async (file) => {
    if (!file) return;
    const text = await file.text();
    const cards = parseCardDetails(text);
    setForm({ ...form, cardDetailsText: cards.join('\n') });
    notify(`已读取 ${cards.length} 个卡号明细`);
  };

  return (
    <div className="modalBackdrop" role="dialog" aria-modal="true">
      <form className="modal orderModal" onSubmit={onSubmit}>
        <div className="modalHeader">
          <h3>{editing ? '编辑订单' : '新增订单'}</h3>
          <button type="button" className="ghostIcon" onClick={onClose}>×</button>
        </div>
        <div className="formGrid">
          <Input label="订单号" value={form.orderNo ?? ''} required onChange={(value) => setForm({ ...form, orderNo: value })} />
          <Input label="开始时间" type="date" value={form.startTime ?? ''} required onChange={(value) => setForm({ ...form, startTime: value })} />
          <Input label="金额" type="number" value={form.amount ?? ''} required onChange={(value) => setForm({ ...form, amount: value })} />
          <label className="field fullSpan">
            <span>卡号明细文件</span>
            <label className="filePicker inlinePicker">
              <input type="file" accept=".csv,.txt" onChange={(event) => importFile(event.target.files?.[0])} />
              <Upload size={17} />
              <span>选择文件导入卡号</span>
            </label>
          </label>
          <label className="field fullSpan">
            <span>卡号明细</span>
            <textarea
              value={form.cardDetailsText ?? ''}
              rows={10}
              placeholder="每行一个卡号，例如：1442473290000"
              onChange={(event) => setForm({ ...form, cardDetailsText: event.target.value })}
            />
          </label>
          <Input label="备注" type="textarea" value={form.remark ?? ''} onChange={(value) => setForm({ ...form, remark: value })} />
        </div>
        <div className="formActions">
          <button type="button" className="iconTextButton" onClick={onClose}>取消</button>
          <button type="submit" className="primaryButton">
            <CheckCircle2 size={18} />
            <span>保存订单</span>
          </button>
        </div>
      </form>
    </div>
  );
}

function OrderDetailModal({ order, onClose }) {
  const cards = order.cardDetails || [];
  return (
    <div className="modalBackdrop" role="dialog" aria-modal="true">
      <div className="modal orderDetailModal">
        <div className="modalHeader">
          <h3>卡号明细：{order.orderNo}</h3>
          <button type="button" className="ghostIcon" onClick={onClose}>×</button>
        </div>
        <div className="detailMeta">
          <span>开始时间：{order.startTime}</span>
          <span>金额：{order.amount}</span>
          <span>卡号数量：{cards.length}</span>
        </div>
        <div className="detailList">
          {cards.length === 0 ? (
            <p className="emptyDetail">暂无卡号明细</p>
          ) : cards.map((cardNo, index) => (
            <div key={`${cardNo}-${index}`}>
              <span>{index + 1}</span>
              <strong>{cardNo}</strong>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function Reports({ summary, notify }) {
  const [reports, setReports] = useState({ carrierRows: [], statusRows: [], customerRows: [] });
  const safeSummary = summary ?? { totalCards: 0, activeCards: 0, totalUsed: 0, openAlarms: 0 };

  useEffect(() => {
    api.request('/reports').then(setReports).catch((error) => notify(error.message));
  }, []);

  const exportReports = () => {
    window.location.href = '/api/reports/export';
    notify('正在导出统计报表');
  };

  return (
    <section className="contentStack">
      <div className="toolbar">
        <div className="statusLine">
          <span>统计报表</span>
        </div>
        <button className="iconTextButton" onClick={exportReports}>
          <FileDown size={17} />
          <span>导出报表</span>
        </button>
      </div>
      <div className="metricGrid reportMetrics">
        <div className="metric metric-blue"><span className="metricLabel">库存总卡数</span><strong>{safeSummary.totalCards}</strong></div>
        <div className="metric metric-green"><span className="metricLabel">激活率</span><strong>{safeSummary.totalCards ? Math.round((safeSummary.activeCards / safeSummary.totalCards) * 100) : 0}%</strong></div>
        <div className="metric metric-cyan"><span className="metricLabel">账期流量</span><strong>{Number(safeSummary.totalUsed).toFixed(1)}GB</strong></div>
        <div className="metric metric-red"><span className="metricLabel">未关闭告警</span><strong>{safeSummary.openAlarms ?? 0}</strong></div>
      </div>
      <div className="dashboardGrid">
        <section className="panel">
          <PanelTitle icon={Signal} title="运营商分布" />
          <BarList rows={reports.carrierRows} />
        </section>
        <section className="panel">
          <PanelTitle icon={SlidersHorizontal} title="卡状态分布" />
          <BarList rows={reports.statusRows} />
        </section>
      </div>
      <section className="panel">
        <PanelTitle icon={Building2} title="客户资产统计" />
        <DataTable columns={[['name', '客户名称'], ['cards', '卡数量'], ['devices', '设备数量'], ['alarms', '告警数量']]} rows={reports.customerRows} compact />
      </section>
    </section>
  );
}

function SystemPage({ notify, onChanged }) {
  const [activeSection, setActiveSection] = useState(0);
  const section = systemSections[activeSection];

  return (
    <section className="contentStack">
      <div className="tabs">
        {systemSections.map((item, index) => {
          const Icon = item.icon;
          return (
            <button key={item.title} className={activeSection === index ? 'active' : ''} onClick={() => setActiveSection(index)}>
              <Icon size={17} />
              <span>{item.title}</span>
            </button>
          );
        })}
      </div>
      <EntityPage config={section} notify={notify} onChanged={onChanged} />
      <section className="panel settingsStrip">
        <PanelTitle icon={Lock} title="系统参数" />
        <div className="settingsGrid">
          <label><input type="checkbox" defaultChecked /> 登录验证码</label>
          <label><input type="checkbox" defaultChecked /> 操作日志留存</label>
          <label><input type="checkbox" /> 告警短信通知</label>
          <label><input type="checkbox" defaultChecked /> 流量阈值自动告警</label>
        </div>
      </section>
    </section>
  );
}

function Toolbar({ keyword, setKeyword, onSearch, onCreate, onExport, onBatchDelete, selectedCount = 0, createText }) {
  return (
    <div className="toolbar">
      <label className="searchBox">
        <Search size={18} />
        <input
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          onKeyDown={(event) => event.key === 'Enter' && onSearch()}
          placeholder="搜索卡号、ICCID、客户、状态等"
        />
      </label>
      <div className="toolbarActions">
        <button className="iconTextButton" onClick={onSearch}>查询</button>
        <button className="iconTextButton" onClick={onExport} title="导出当前页CSV">
          <FileDown size={17} />
          <span>导出</span>
        </button>
        {onBatchDelete && (
          <button className="iconTextButton dangerText" disabled={selectedCount === 0} onClick={onBatchDelete} title="批量删除已选数据">
            <Trash2 size={17} />
            <span>批量删除{selectedCount ? `(${selectedCount})` : ''}</span>
          </button>
        )}
        <button className="primaryButton" onClick={onCreate}>
          <Plus size={18} />
          <span>{createText}</span>
        </button>
      </div>
    </div>
  );
}

function Pagination({ page, pageSize, total, setPage, setPageSize }) {
  const pages = Math.max(Math.ceil(total / pageSize), 1);
  return (
    <div className="pagination">
      <span>共 {total} 条，第 {page} / {pages} 页</span>
      <select value={pageSize} onChange={(event) => { setPage(1); setPageSize(Number(event.target.value)); }}>
        <option value={20}>20条/页</option>
        <option value={50}>50条/页</option>
        <option value={100}>100条/页</option>
        <option value={200}>200条/页</option>
      </select>
      <button className="iconTextButton" disabled={page <= 1} onClick={() => setPage(page - 1)}>上一页</button>
      <button className="iconTextButton" disabled={page >= pages} onClick={() => setPage(page + 1)}>下一页</button>
    </div>
  );
}

function EntityModal({ title, fields, form, setForm, onClose, onSubmit }) {
  return (
    <div className="modalBackdrop" role="dialog" aria-modal="true">
      <form className="modal" onSubmit={onSubmit}>
        <div className="modalHeader">
          <h3>{title}</h3>
          <button type="button" className="ghostIcon" onClick={onClose}>×</button>
        </div>
        <div className="formGrid">
          {fields.map((field) => field.type === 'select' ? (
            <Select key={field.name} label={field.label} value={form[field.name] ?? ''} options={field.options} required={field.required} onChange={(value) => setForm({ ...form, [field.name]: value })} />
          ) : (
            <Input key={field.name} label={field.label} type={field.type} value={form[field.name] ?? ''} required={field.required} placeholder={field.placeholder} onChange={(value) => setForm({ ...form, [field.name]: value })} />
          ))}
        </div>
        <div className="formActions">
          <button type="button" className="iconTextButton" onClick={onClose}>取消</button>
          <button type="submit" className="primaryButton">
            <CheckCircle2 size={18} />
            <span>保存</span>
          </button>
        </div>
      </form>
    </div>
  );
}

function Input({ label, value, onChange, type = 'text', required = false, placeholder = '' }) {
  if (type === 'textarea') {
    return (
      <label className="field fullSpan">
        <span>{label}{required ? ' *' : ''}</span>
        <textarea value={value} required={required} rows={4} placeholder={placeholder} onChange={(event) => onChange(event.target.value)} />
      </label>
    );
  }
  return (
    <label className="field">
      <span>{label}{required ? ' *' : ''}</span>
      <input value={value} required={required} type={type} placeholder={placeholder} onChange={(event) => onChange(event.target.value)} />
    </label>
  );
}

function Select({ label, value, options, onChange, required = false }) {
  return (
    <label className="field">
      <span>{label}{required ? ' *' : ''}</span>
      <select value={value} required={required} onChange={(event) => onChange(event.target.value)}>
        <option value="">请选择</option>
        {options.map((option) => <option key={option} value={option}>{option}</option>)}
      </select>
    </label>
  );
}

function DataTable({ columns, rows, onEdit, onDelete, onViewDetails, selectedIds, setSelectedIds, compact = false }) {
  const selectable = Array.isArray(selectedIds) && typeof setSelectedIds === 'function';
  const allSelected = selectable && rows.length > 0 && rows.every((row) => selectedIds.includes(row.id));
  const toggleAll = (checked) => {
    if (!selectable) return;
    const rowIds = rows.map((row) => row.id).filter(Boolean);
    setSelectedIds(checked ? Array.from(new Set([...selectedIds, ...rowIds])) : selectedIds.filter((id) => !rowIds.includes(id)));
  };
  const toggleOne = (id, checked) => {
    if (!selectable || !id) return;
    setSelectedIds(checked ? Array.from(new Set([...selectedIds, id])) : selectedIds.filter((value) => value !== id));
  };

  return (
    <div className="tableWrap">
      <table className={compact ? 'compactTable' : ''}>
        <thead>
          <tr>
            {selectable && (
              <th className="selectCol">
                <input type="checkbox" checked={allSelected} onChange={(event) => toggleAll(event.target.checked)} />
              </th>
            )}
            {columns.map(([key, label]) => <th key={key}>{label}</th>)}
            {(onEdit || onDelete || onViewDetails) && <th className="actionCol">操作</th>}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr><td className="emptyCell" colSpan={columns.length + (selectable ? 1 : 0) + (onEdit || onDelete || onViewDetails ? 1 : 0)}>暂无数据</td></tr>
          ) : rows.map((row, index) => (
            <tr key={row.id ?? `${row.cardNo ?? row.name}-${index}`}>
              {selectable && (
                <td className="selectCol">
                  <input type="checkbox" checked={selectedIds.includes(row.id)} onChange={(event) => toggleOne(row.id, event.target.checked)} />
                </td>
              )}
              {columns.map(([key]) => <td key={key}>{isBadgeKey(key) ? <StatusBadge value={row[key]} /> : String(row[key] ?? '')}</td>)}
              {(onEdit || onDelete || onViewDetails) && (
                <td className="rowActions">
                  {onViewDetails && <button className="iconOnly" onClick={() => onViewDetails(row)} title="查看明细"><ClipboardList size={16} /></button>}
                  {onEdit && <button className="iconOnly" onClick={() => onEdit(row)} title="编辑"><Edit3 size={16} /></button>}
                  {onDelete && <button className="iconOnly danger" onClick={() => onDelete(row.id)} title="删除"><Trash2 size={16} /></button>}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function isBadgeKey(key) {
  return ['status', 'level', 'billingStatus'].includes(key);
}

function StatusBadge({ value }) {
  const key = String(value ?? '');
  const tone = ['停机', '高', '紧急', '失败', '未处理'].includes(key)
    ? 'danger'
    : ['已激活', '在线', '成功', '已处理', '已出账', '启用', '正常'].includes(key)
      ? 'success'
      : 'neutral';
  return <span className={`badge ${tone}`}>{key}</span>;
}

function PanelTitle({ icon: Icon, title }) {
  return (
    <div className="panelTitle">
      <Icon size={18} />
      <h3>{title}</h3>
    </div>
  );
}

function BarList({ rows }) {
  const max = Math.max(1, ...rows.map((row) => Number(row.value || 0)));
  return (
    <div className="barList">
      {rows.map((row) => (
        <div className="barRow" key={row.name}>
          <span>{row.name}</span>
          <div className="barTrack"><i style={{ width: `${(Number(row.value || 0) / max) * 100}%` }} /></div>
          <strong>{row.value}</strong>
        </div>
      ))}
    </div>
  );
}

function parseCardDetails(text) {
  return Array.from(new Set(
    String(text || '')
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line) => line.split(/[,，\t\s]+/)[0]?.trim() || '')
      .filter((value) => /^[A-Za-z0-9-]{6,}$/.test(value))
  ));
}

function toCsv(columns, rows) {
  const escape = (value) => `"${String(value ?? '').replaceAll('"', '""')}"`;
  return [columns.map(([, label]) => escape(label)).join(','), ...rows.map((row) => columns.map(([key]) => escape(row[key])).join(','))].join('\n');
}

function downloadText(filename, text) {
  const blob = new Blob([`\ufeff${text}`], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

createRoot(document.getElementById('root')).render(<App />);
