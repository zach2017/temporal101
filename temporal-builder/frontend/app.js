// ══════════════════════════════════════════════════════════════
//  STATE
// ══════════════════════════════════════════════════════════════
const state = { types:[], activities:[], workflows:[], pipelines:[], workers:[], clients:[], projects:[] };
let idCounter = 0;
const uid = () => `item_${++idCounter}`;
let validationErrors = [];

const PRIMITIVES = [
  { label:'string', color:'accent' }, { label:'int', color:'agreen' },
  { label:'float', color:'aamber' }, { label:'bool', color:'apurple' },
  { label:'datetime', color:'ared' }, { label:'duration', color:'dim' },
];
const COMPOSITES = [
  { label:'list<T>', template:'list<string>', color:'accent', icon:'[ ]' },
  { label:'map<K,V>', template:'map<string,string>', color:'agreen', icon:'{ }' },
  { label:'optional<T>', template:'optional<string>', color:'aamber', icon:'?' },
];
const COMPONENTS = [
  { label:'Activity', kind:'activity', icon:'⚡' }, { label:'Workflow', kind:'workflow', icon:'🔄' },
  { label:'Worker', kind:'worker', icon:'⚙' }, { label:'Client', kind:'client', icon:'🔌' },
  { label:'Pipeline', kind:'pipeline', icon:'🔗' },
];

// ══════════════════════════════════════════════════════════════
//  SAMPLE PROJECT
// ══════════════════════════════════════════════════════════════
function getSampleProject() {
  return {
    meta: { name:'OrderProcessingSystem', namespace:'orders-prod', taskqueue:'order-processing', language:'python' },
    types: [
      { id:uid(), name:'Money', kind:'struct', alias_of:'', values:[], fields:[
        { id:uid(), name:'amount', type:'int', required:true },
        { id:uid(), name:'currency', type:'string', required:true },
      ]},
      { id:uid(), name:'OrderItem', kind:'struct', alias_of:'', values:[], fields:[
        { id:uid(), name:'sku', type:'string', required:true },
        { id:uid(), name:'quantity', type:'int', required:true },
        { id:uid(), name:'unit_price', type:'Money', required:true },
      ]},
      { id:uid(), name:'OrderInput', kind:'struct', alias_of:'', values:[], fields:[
        { id:uid(), name:'order_id', type:'string', required:true },
        { id:uid(), name:'customer_id', type:'string', required:true },
        { id:uid(), name:'items', type:'list<string>', required:true },
        { id:uid(), name:'total', type:'Money', required:true },
      ]},
      { id:uid(), name:'OrderStatus', kind:'enum', alias_of:'', fields:[], values:[
        { id:uid(), name:'PENDING', value:'pending' },
        { id:uid(), name:'PAYMENT_CAPTURED', value:'payment_captured' },
        { id:uid(), name:'SHIPPED', value:'shipped' },
        { id:uid(), name:'COMPLETED', value:'completed' },
        { id:uid(), name:'CANCELLED', value:'cancelled' },
      ]},
      { id:uid(), name:'PaymentResult', kind:'struct', alias_of:'', values:[], fields:[
        { id:uid(), name:'transaction_id', type:'string', required:true },
        { id:uid(), name:'captured', type:'bool', required:true },
      ]},
      { id:uid(), name:'ShipmentResult', kind:'struct', alias_of:'', values:[], fields:[
        { id:uid(), name:'tracking_number', type:'string', required:true },
        { id:uid(), name:'carrier', type:'string', required:true },
      ]},
    ],
    activities: [
      { id:uid(), name:'validate_order', description:'Validates order data, checks SKU catalog, verifies pricing.', mode:'sync', input_type:'OrderInput', output_type:'OrderInput', start_to_close_timeout:'10s', heartbeat_timeout:'10s', retry_max_attempts:5, task_queue:'' },
      { id:uid(), name:'reserve_inventory', description:'Reserves stock for each line item. Idempotent by order_id.', mode:'sync', input_type:'OrderInput', output_type:'bool', start_to_close_timeout:'15s', heartbeat_timeout:'10s', retry_max_attempts:3, task_queue:'' },
      { id:uid(), name:'capture_payment', description:'Captures payment via gateway. Heartbeats while waiting for processor callback.', mode:'async', input_type:'OrderInput', output_type:'PaymentResult', start_to_close_timeout:'3m', heartbeat_timeout:'30s', retry_max_attempts:3, task_queue:'' },
      { id:uid(), name:'fulfill_order', description:'Hands order to warehouse. Heartbeats as pick/pack/ship progresses.', mode:'async', input_type:'OrderInput', output_type:'ShipmentResult', start_to_close_timeout:'23h', heartbeat_timeout:'5m', retry_max_attempts:5, task_queue:'fulfillment' },
      { id:uid(), name:'send_notification', description:'Sends order confirmation or shipment notification email.', mode:'sync', input_type:'string', output_type:'bool', start_to_close_timeout:'30s', heartbeat_timeout:'10s', retry_max_attempts:10, task_queue:'notifications' },
    ],
    workflows: [
      { id:uid(), name:'ProcessOrder', description:'End-to-end order processing with saga-style compensation.', mode:'async', input_type:'OrderInput', output_type:'string', execution_timeout:'48h', cron_schedule:'', steps:[
        { id:uid(), step_id:'validate', kind:'activity', activity:'validate_order', output_var:'validated_order' },
        { id:uid(), step_id:'reserve', kind:'activity', activity:'reserve_inventory', output_var:'reserved' },
        { id:uid(), step_id:'payment', kind:'activity', activity:'capture_payment', output_var:'payment_result' },
        { id:uid(), step_id:'fulfill', kind:'activity', activity:'fulfill_order', output_var:'shipment' },
        { id:uid(), step_id:'notify', kind:'activity', activity:'send_notification', output_var:'notified' },
      ]},
      { id:uid(), name:'CleanupStaleOrders', description:'Runs daily to cancel stuck orders older than 24h.', mode:'cron', input_type:'null', output_type:'int', execution_timeout:'1h', cron_schedule:'0 3 * * *', steps:[] },
    ],
    pipelines: [],
    workers: [
      { id:uid(), name:'order-worker', task_queue:'order-processing', description:'', activities:['validate_order','reserve_inventory'], workflows:['ProcessOrder','CleanupStaleOrders'], max_concurrent_activities:100, max_concurrent_workflow_tasks:200, replicas:3, cpu:'1', memory:'1Gi' },
      { id:uid(), name:'payment-worker', task_queue:'order-processing', description:'', activities:['capture_payment'], workflows:[], max_concurrent_activities:50, max_concurrent_workflow_tasks:200, replicas:2, cpu:'500m', memory:'512Mi' },
      { id:uid(), name:'fulfillment-worker', task_queue:'fulfillment', description:'', activities:['fulfill_order'], workflows:[], max_concurrent_activities:25, max_concurrent_workflow_tasks:200, replicas:2, cpu:'500m', memory:'512Mi' },
    ],
    clients: [
      { id:uid(), name:'order-api-client', description:'', target:'temporal.internal:7233', tls_enabled:true, default_mode:'async', allowed_workflows:['ProcessOrder'], allowed_signals:[], allowed_queries:[] },
      { id:uid(), name:'admin-client', description:'', target:'temporal.internal:7233', tls_enabled:true, default_mode:'sync', allowed_workflows:['ProcessOrder','CleanupStaleOrders'], allowed_signals:[], allowed_queries:[] },
    ],
  };
}

function loadSampleProject() {
  const s = getSampleProject();
  document.getElementById('meta_name').value = s.meta.name;
  document.getElementById('meta_namespace').value = s.meta.namespace;
  document.getElementById('meta_taskqueue').value = s.meta.taskqueue;
  document.getElementById('meta_language').value = s.meta.language;
  state.types=s.types; state.activities=s.activities; state.workflows=s.workflows;
  state.pipelines=s.pipelines; state.workers=s.workers; state.clients=s.clients;
  renderAll(); clearValidationUI();
  document.getElementById('sampleBanner').classList.remove('hidden');
  showToast('Sample "Order Processing System" loaded — explore each tab!','apurple');
}

function clearAll() {
  const total = state.types.length+state.activities.length+state.workflows.length+state.workers.length+state.clients.length+state.pipelines.length;
  if(total===0) return;
  if(!confirm('Clear all items? This cannot be undone.')) return;
  ['meta_name','meta_namespace','meta_taskqueue'].forEach(id => document.getElementById(id).value='');
  state.types=[]; state.activities=[]; state.workflows=[]; state.pipelines=[]; state.workers=[]; state.clients=[];
  renderAll(); clearValidationUI();
  document.getElementById('sampleBanner').classList.add('hidden');
  showToast('All items cleared','dim');
}

// ══════════════════════════════════════════════════════════════
//  VALIDATION
// ══════════════════════════════════════════════════════════════
const DURATION_RE = /^\d+(ms|s|m|h|d)$/;
const NAME_RE = /^[a-z][a-z0-9_]*$/;
const PASCAL_RE = /^[A-Z][A-Za-z0-9]*$/;
const KEBAB_RE = /^[a-z][a-z0-9-]*$/;

function validate() {
  const errors = [];
  const tabErr = {types:false,activities:false,workflows:false,pipelines:false,workers:false,clients:false};
  const mn = val('meta_name'), mns = val('meta_namespace'), mtq = val('meta_taskqueue');

  if(!mn) errors.push({tab:'metadata',field:'meta_name',msg:'Project name is required.'});
  if(!mns) errors.push({tab:'metadata',field:'meta_namespace',msg:'Namespace is required.'});
  if(!mtq) errors.push({tab:'metadata',field:'meta_taskqueue',msg:'Default task queue is required.'});

  // Types
  state.types.forEach((t,i) => {
    if(!t.name) { err('types',t.id,`Type #${i+1}: Name is required.`); tabErr.types=true; }
    else if(!PASCAL_RE.test(t.name)) { err('types',t.id,`Type "${t.name}": Must be PascalCase (e.g. OrderInput).`); tabErr.types=true; }
    if(t.kind==='struct' && t.fields.length===0) { err('types',t.id,`Type "${t.name||'#'+(i+1)}": Struct needs at least one field. Drag a type from the sidebar.`); tabErr.types=true; }
    if(t.kind==='struct') {
      t.fields.forEach((f,fi) => {
        if(!f.name) { err('types',t.id,`Type "${t.name||'#'+(i+1)}": Field #${fi+1} needs a name.`); tabErr.types=true; }
        else if(!NAME_RE.test(f.name)) { err('types',t.id,`Type "${t.name}": Field "${f.name}" must be snake_case.`); tabErr.types=true; }
      });
      const fnames = t.fields.map(f=>f.name).filter(Boolean);
      const fd = fnames.filter((n,i)=>fnames.indexOf(n)!==i);
      if(fd.length) { err('types',t.id,`Type "${t.name}": Duplicate field(s): ${[...new Set(fd)].join(', ')}`); tabErr.types=true; }
    }
    if(t.kind==='enum' && t.values.length===0) { err('types',t.id,`Type "${t.name||'#'+(i+1)}": Enum needs at least one value.`); tabErr.types=true; }
    if(t.kind==='enum') t.values.forEach((v,vi) => { if(!v.name) { err('types',t.id,`Type "${t.name||'#'+(i+1)}": Enum value #${vi+1} needs a name.`); tabErr.types=true; } });
    if(t.kind==='alias' && !t.alias_of) { err('types',t.id,`Type "${t.name||'#'+(i+1)}": Alias needs a target type (e.g. list<string>).`); tabErr.types=true; }
  });
  checkDupes(state.types,'name','types','Type',errors,tabErr);

  // Activities
  state.activities.forEach((a,i) => {
    if(!a.name) { err('activities',a.id,`Activity #${i+1}: Name is required.`); tabErr.activities=true; }
    else if(!NAME_RE.test(a.name)) { err('activities',a.id,`Activity "${a.name}": Must be snake_case (e.g. validate_order).`); tabErr.activities=true; }
    if(a.start_to_close_timeout && !DURATION_RE.test(a.start_to_close_timeout)) { err('activities',a.id,`Activity "${a.name||'#'+(i+1)}": Invalid timeout "${a.start_to_close_timeout}". Use 30s, 5m, 2h etc.`); tabErr.activities=true; }
    if(a.mode==='async' && a.heartbeat_timeout && !DURATION_RE.test(a.heartbeat_timeout)) { err('activities',a.id,`Activity "${a.name||'#'+(i+1)}": Invalid heartbeat timeout.`); tabErr.activities=true; }
  });
  checkDupes(state.activities,'name','activities','Activity',errors,tabErr);

  // Workflows
  const actNames = state.activities.map(a=>a.name).filter(Boolean);
  state.workflows.forEach((w,i) => {
    if(!w.name) { err('workflows',w.id,`Workflow #${i+1}: Name is required.`); tabErr.workflows=true; }
    else if(!PASCAL_RE.test(w.name)) { err('workflows',w.id,`Workflow "${w.name}": Must be PascalCase (e.g. ProcessOrder).`); tabErr.workflows=true; }
    if(w.execution_timeout && !DURATION_RE.test(w.execution_timeout)) { err('workflows',w.id,`Workflow "${w.name||'#'+(i+1)}": Invalid timeout "${w.execution_timeout}".`); tabErr.workflows=true; }
    if(w.mode==='cron' && !w.cron_schedule) { err('workflows',w.id,`Workflow "${w.name||'#'+(i+1)}": Cron mode requires a schedule (e.g. "0 3 * * *").`); tabErr.workflows=true; }
    w.steps.forEach((s,si) => {
      if(!s.step_id) { err('workflows',w.id,`Workflow "${w.name||'#'+(i+1)}": Step #${si+1} needs an ID.`); tabErr.workflows=true; }
      if(s.kind==='activity' && !s.activity) { err('workflows',w.id,`Workflow "${w.name||'#'+(i+1)}": Step "${s.step_id||'#'+(si+1)}" needs an activity.`); tabErr.workflows=true; }
      if(s.kind==='activity' && s.activity && !actNames.includes(s.activity)) { err('workflows',w.id,`Workflow "${w.name||'#'+(i+1)}": Step "${s.step_id}" references undefined activity "${s.activity}".`); tabErr.workflows=true; }
    });
    const sids = w.steps.map(s=>s.step_id).filter(Boolean);
    const sd = sids.filter((n,i)=>sids.indexOf(n)!==i);
    if(sd.length) { err('workflows',w.id,`Workflow "${w.name}": Duplicate step IDs: ${[...new Set(sd)].join(', ')}`); tabErr.workflows=true; }
  });

  // Workers
  if(state.workers.length===0 && (state.activities.length>0||state.workflows.length>0)) { errors.push({tab:'workers',msg:'You have activities/workflows but no workers — add at least one to host them.'}); tabErr.workers=true; }
  state.workers.forEach((w,i) => {
    if(!w.name) { err('workers',w.id,`Worker #${i+1}: Name is required.`); tabErr.workers=true; }
    else if(!KEBAB_RE.test(w.name)) { err('workers',w.id,`Worker "${w.name}": Should be kebab-case (e.g. order-worker).`); tabErr.workers=true; }
    if(!w.task_queue) { err('workers',w.id,`Worker "${w.name||'#'+(i+1)}": Task queue is required.`); tabErr.workers=true; }
    if(w.activities.length===0 && w.workflows.length===0) { err('workers',w.id,`Worker "${w.name||'#'+(i+1)}": Must host at least one activity or workflow.`); tabErr.workers=true; }
  });

  // Clients
  if(state.clients.length===0 && state.workflows.length>0) { errors.push({tab:'clients',msg:'You have workflows but no clients — add at least one to invoke them.'}); tabErr.clients=true; }
  state.clients.forEach((c,i) => {
    if(!c.name) { err('clients',c.id,`Client #${i+1}: Name is required.`); tabErr.clients=true; }
    else if(!KEBAB_RE.test(c.name)) { err('clients',c.id,`Client "${c.name}": Should be kebab-case (e.g. api-client).`); tabErr.clients=true; }
    if(!c.target) { err('clients',c.id,`Client "${c.name||'#'+(i+1)}": Target host:port is required.`); tabErr.clients=true; }
    if(c.allowed_workflows.length===0) { err('clients',c.id,`Client "${c.name||'#'+(i+1)}": Select at least one allowed workflow.`); tabErr.clients=true; }
  });

  // Warnings: unassigned items
  const waSet = new Set(state.workers.flatMap(w=>w.activities));
  state.activities.filter(a=>a.name).forEach(a => { if(!waSet.has(a.name)) errors.push({tab:'workers',msg:`Activity "${a.name}" is not assigned to any worker.`,severity:'warning'}); });
  const wwSet = new Set(state.workers.flatMap(w=>w.workflows));
  state.workflows.filter(w=>w.name).forEach(w => { if(!wwSet.has(w.name)) errors.push({tab:'workers',msg:`Workflow "${w.name}" is not assigned to any worker.`,severity:'warning'}); });

  Object.keys(tabErr).forEach(tab => { const dot = document.querySelector(`[data-tab-dot="${tab}"]`); if(dot) dot.classList.toggle('visible', tabErr[tab]); });
  validationErrors = errors;
  return errors;

  function err(tab,id,msg) { errors.push({tab,id,msg}); }
  function val(id) { return document.getElementById(id).value.trim(); }
}

function checkDupes(arr,key,tab,label,errors,tabErr) {
  const names = arr.map(x=>x[key]).filter(Boolean);
  const d = names.filter((n,i)=>names.indexOf(n)!==i);
  if(d.length) { errors.push({tab,msg:`Duplicate ${label.toLowerCase()} name(s): ${[...new Set(d)].join(', ')}`}); tabErr[tab]=true; }
}

function showValidationPanel(errors) {
  const panel = document.getElementById('validationPanel');
  const container = document.getElementById('validationErrors');
  const title = document.getElementById('validationTitle');
  const real = errors.filter(e=>e.severity!=='warning');
  const warns = errors.filter(e=>e.severity==='warning');
  title.textContent = `${real.length} error${real.length!==1?'s':''}${warns.length?` and ${warns.length} warning${warns.length!==1?'s':''}`:''} found`;
  container.innerHTML = errors.map(e => {
    const isW = e.severity==='warning'; const c = isW?'aamber':'ared'; const ic = isW?'⚠':'✕';
    const tl = e.tab.charAt(0).toUpperCase()+e.tab.slice(1);
    return `<div class="flex items-start gap-2 text-xs py-1.5 cursor-pointer hover:bg-${c}/5 px-2 rounded transition-colors" onclick="jumpToError('${e.tab}','${e.id||''}','${e.field||''}')">
      <span class="text-${c} shrink-0 mt-px">${ic}</span>
      <span class="text-${c}/80 shrink-0 mono text-[10px] w-16 mt-px">${tl}</span>
      <span class="text-dim leading-snug">${e.msg}</span>
    </div>`;
  }).join('');
  panel.classList.remove('hidden');
}

function hideValidation() { document.getElementById('validationPanel').classList.add('hidden'); }

function clearValidationUI() {
  hideValidation(); validationErrors = [];
  document.querySelectorAll('.tab-error-dot').forEach(d=>d.classList.remove('visible'));
  document.querySelectorAll('.input-error').forEach(el=>el.classList.remove('input-error'));
  document.querySelectorAll('.error-msg').forEach(el=>{ el.classList.add('hidden'); el.textContent=''; });
  document.querySelectorAll('.builder-card.has-error').forEach(el=>el.classList.remove('has-error'));
}

function jumpToError(tab, id, field) {
  if(tab==='metadata') { if(field) { const el=document.getElementById(field); if(el){el.classList.add('input-error','shake');el.focus();setTimeout(()=>el.classList.remove('shake'),400);} } return; }
  switchTab(tab);
  if(id) setTimeout(()=>{ const card=document.querySelector(`[data-item-id="${id}"]`); if(card){card.scrollIntoView({behavior:'smooth',block:'center'}); card.classList.add('has-error','shake'); setTimeout(()=>card.classList.remove('shake'),400); } },100);
}

// ══════════════════════════════════════════════════════════════
//  PALETTE
// ══════════════════════════════════════════════════════════════
function initPalette() {
  const pe = document.getElementById('primitiveTypes');
  PRIMITIVES.forEach(p => { pe.innerHTML += `<div class="drag-item px-3 py-2 rounded-lg bg-raised border border-bdr text-xs mono font-medium text-${p.color} hover:border-${p.color}/40 flex items-center gap-1.5" draggable="true" data-type="${p.label}"><span class="w-2 h-2 rounded-full bg-${p.color}"></span>${p.label}</div>`; });
  const ce = document.getElementById('compositeTypes');
  COMPOSITES.forEach(c => { ce.innerHTML += `<div class="drag-item px-3 py-2 rounded-lg bg-raised border border-bdr text-xs mono font-medium text-${c.color} hover:border-${c.color}/40 flex items-center gap-2" draggable="true" data-type="${c.template}"><span class="text-base opacity-60">${c.icon}</span>${c.label}</div>`; });
  const cm = document.getElementById('componentPalette');
  COMPONENTS.forEach(c => { cm.innerHTML += `<div class="drag-item px-3 py-2 rounded-lg bg-raised border border-bdr text-xs font-medium text-dim hover:border-accent/40 flex items-center gap-2 cursor-pointer" draggable="true" data-component="${c.kind}" onclick="addComponent('${c.kind}')"><span class="text-base">${c.icon}</span>${c.label}</div>`; });
  document.querySelectorAll('.drag-item[data-type]').forEach(el => { el.addEventListener('dragstart', e => { e.dataTransfer.setData('text/plain', el.dataset.type); e.dataTransfer.effectAllowed='copy'; }); });
  document.querySelectorAll('.drag-item[data-component]').forEach(el => { el.addEventListener('dragstart', e => { e.dataTransfer.setData('application/component', el.dataset.component); e.dataTransfer.effectAllowed='copy'; }); });
}
function addComponent(kind) { ({activity:()=>{addActivity();switchTab('activities')},workflow:()=>{addWorkflow();switchTab('workflows')},worker:()=>{addWorker();switchTab('workers')},client:()=>{addClient();switchTab('clients')},pipeline:()=>{addPipeline();switchTab('pipelines')}})[kind](); }

// ══════════════════════════════════════════════════════════════
//  TABS & RENDER
// ══════════════════════════════════════════════════════════════
function switchTab(tab) {
  document.querySelectorAll('.tab-btn').forEach(b=>b.classList.remove('active'));
  document.querySelector(`.tab-btn[data-tab="${tab}"]`).classList.add('active');
  renderTabContent(tab);
}

let currentTab = 'types';
function renderTabContent(tab) {
  currentTab = tab || currentTab;
  const el = document.getElementById('tabContent');
  const renderers = { types:renderTypesTab, activities:renderActivitiesTab, workflows:renderWorkflowsTab, pipelines:renderPipelinesTab, workers:renderWorkersTab, clients:renderClientsTab, results:renderResultsTab };
  el.innerHTML = (renderers[currentTab]||renderers.types)();
  initDropZones();
}
function renderAll() { renderTabContent(currentTab); }

function emptyState(section, msg, submsg, addFn, btnLabel) {
  if(state[section].length>0) return '';
  return `<div class="text-center py-12 text-dim text-sm"><p class="mb-2">${msg}</p><p class="text-xs text-muted mb-4">${submsg}</p>${addFn?`<button onclick="${addFn}()" class="px-3 py-1.5 rounded-lg text-xs font-medium bg-accent/10 text-accent border border-accent/20 hover:bg-accent/20 transition-all">${btnLabel}</button>`:''}</div>`;
}

function getTypeOptions() {
  const p = PRIMITIVES.map(p=>p.label);
  const c = state.types.filter(t=>t.name).map(t=>t.name);
  return [...p,'any','null',...c];
}
function esc(s){return(s||'').replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}
function hasItemErr(id) { return validationErrors.some(e=>e.id===id); }

// ══════════════════════════════════════════════════════════════
//  TYPES TAB
// ══════════════════════════════════════════════════════════════
function addType() { state.types.push({id:uid(),name:'',kind:'struct',fields:[],values:[],alias_of:''}); renderAll(); }
function removeType(id) { state.types=state.types.filter(t=>t.id!==id); renderAll(); }
function addField(tid) { const t=state.types.find(x=>x.id===tid); if(t){t.fields.push({id:uid(),name:'',type:'string',required:true}); renderAll();} }
function removeField(tid,fid) { const t=state.types.find(x=>x.id===tid); if(t){t.fields=t.fields.filter(f=>f.id!==fid); renderAll();} }
function addEnumValue(tid) { const t=state.types.find(x=>x.id===tid); if(t){t.values.push({id:uid(),name:'',value:''}); renderAll();} }
function removeEnumValue(tid,vid) { const t=state.types.find(x=>x.id===tid); if(t){t.values=t.values.filter(v=>v.id!==vid); renderAll();} }
function updateType(id,key,val) { const t=state.types.find(x=>x.id===id); if(t)t[key]=val; renderAll(); }
function updateField(tid,fid,key,val) { const t=state.types.find(x=>x.id===tid); if(!t)return; const f=t.fields.find(x=>x.id===fid); if(f)f[key]=val; }
function updateEnumVal(tid,vid,key,val) { const t=state.types.find(x=>x.id===tid); if(!t)return; const v=t.values.find(x=>x.id===vid); if(v)v[key]=val; }

function renderTypesTab() {
  const empty = emptyState('types','No types defined yet.','Types let you define structured data (structs, enums, aliases) used as activity/workflow I/O.','addType','+ Add Your First Type');
  if(empty) return `<div class="flex items-center justify-between mb-4"><p class="text-sm text-dim">Define reusable types — drag primitives from the sidebar into struct fields.</p><button onclick="addType()" class="px-3 py-1.5 rounded-lg text-xs font-medium bg-accent/10 text-accent border border-accent/20 hover:bg-accent/20 transition-all">+ Add Type</button></div>${empty}`;

  return `<div class="flex items-center justify-between mb-4"><p class="text-sm text-dim">Define reusable types — drag primitives from the sidebar into struct fields.</p><button onclick="addType()" class="px-3 py-1.5 rounded-lg text-xs font-medium bg-accent/10 text-accent border border-accent/20 hover:bg-accent/20 transition-all">+ Add Type</button></div><div class="space-y-3">${state.types.map(t => {
    const he=hasItemErr(t.id);
    let body='';
    if(t.kind==='struct') {
      body=`<div class="space-y-2 mt-3">${t.fields.map(f=>`<div class="entry-row flex items-center gap-2 p-2 rounded-lg"><input value="${esc(f.name)}" onchange="updateField('${t.id}','${f.id}','name',this.value)" placeholder="field_name (snake_case)" class="flex-1 bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"/><select onchange="updateField('${t.id}','${f.id}','type',this.value)" class="bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent">${getTypeOptions().map(o=>`<option value="${o}"${f.type===o?' selected':''}>${o}</option>`).join('')}<option value="list<string>"${f.type.startsWith('list')?' selected':''}>list&lt;T&gt;</option><option value="map<string,string>"${f.type.startsWith('map')?' selected':''}>map&lt;K,V&gt;</option></select><label class="flex items-center gap-1 text-[10px] text-dim"><input type="checkbox"${f.required?' checked':''} onchange="updateField('${t.id}','${f.id}','required',this.checked)" class="accent-accent w-3 h-3"/>req</label><button onclick="removeField('${t.id}','${f.id}')" class="text-muted hover:text-ared text-xs">✕</button></div>`).join('')}<div class="drop-zone rounded-lg p-2" data-target-type="${t.id}"></div><button onclick="addField('${t.id}')" class="text-xs text-accent hover:text-accent/80">+ Add Field</button></div>`;
    } else if(t.kind==='enum') {
      body=`<div class="space-y-2 mt-3">${t.values.map(v=>`<div class="entry-row flex items-center gap-2 p-2 rounded-lg"><input value="${esc(v.name)}" onchange="updateEnumVal('${t.id}','${v.id}','name',this.value)" placeholder="VARIANT_NAME" class="flex-1 bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"/><input value="${esc(v.value)}" onchange="updateEnumVal('${t.id}','${v.id}','value',this.value)" placeholder="wire_value" class="flex-1 bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"/><button onclick="removeEnumValue('${t.id}','${v.id}')" class="text-muted hover:text-ared text-xs">✕</button></div>`).join('')}<button onclick="addEnumValue('${t.id}')" class="text-xs text-accent hover:text-accent/80">+ Add Value</button></div>`;
    } else {
      body=`<div class="mt-3"><label class="text-[10px] text-muted mb-1 block">Alias target type expression</label><input value="${esc(t.alias_of)}" onchange="updateType('${t.id}','alias_of',this.value)" placeholder="e.g. list<string>" class="w-full bg-raised border border-bdr rounded-lg px-3 py-2 text-xs mono focus:outline-none focus:border-accent"/></div>`;
    }
    return `<div class="builder-card p-4 ${he?'has-error':''}" data-item-id="${t.id}"><div class="flex items-center gap-3"><input value="${esc(t.name)}" onchange="updateType('${t.id}','name',this.value)" placeholder="TypeName (PascalCase)" class="flex-1 bg-raised border border-bdr rounded-lg px-3 py-2 text-sm mono font-semibold focus:outline-none focus:border-accent"/><select onchange="updateType('${t.id}','kind',this.value)" class="bg-raised border border-bdr rounded-lg px-2 py-1 text-xs mono focus:outline-none focus:border-accent"><option value="struct"${t.kind==='struct'?' selected':''}>struct</option><option value="enum"${t.kind==='enum'?' selected':''}>enum</option><option value="alias"${t.kind==='alias'?' selected':''}>alias</option></select><button onclick="removeType('${t.id}')" class="text-muted hover:text-ared"><svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/></svg></button></div>${body}</div>`;
  }).join('')}</div>`;
}

// ══════════════════════════════════════════════════════════════
//  ACTIVITIES TAB
// ══════════════════════════════════════════════════════════════
function addActivity() { state.activities.push({id:uid(),name:'',description:'',mode:'sync',input_type:'string',output_type:'string',start_to_close_timeout:'30s',heartbeat_timeout:'10s',retry_max_attempts:3,task_queue:''}); renderAll(); }
function removeActivity(id) { state.activities=state.activities.filter(a=>a.id!==id); renderAll(); }
function updateActivity(id,key,val) { const a=state.activities.find(x=>x.id===id); if(a)a[key]=val; if(key==='mode') renderAll(); }

function renderActivitiesTab() {
  const empty = emptyState('activities','No activities defined yet.','Activities are the building blocks — each does one task like "validate_order" or "send_email".','addActivity','+ Add Your First Activity');
  const header = `<div class="flex items-center justify-between mb-4"><p class="text-sm text-dim flex items-center gap-1">Define activities — units of work your workflows execute. <span class="hint">?<span class="hint-text"><b>sync</b> = blocking call.<br/><b>async</b> = long-running with heartbeats.</span></span></p><button onclick="addActivity()" class="px-3 py-1.5 rounded-lg text-xs font-medium bg-accent/10 text-accent border border-accent/20 hover:bg-accent/20 transition-all">+ Add Activity</button></div>`;
  if(state.activities.length===0) return header+empty;
  const to = getTypeOptions();
  return header+`<div class="space-y-3">${state.activities.map(a => {
    const he=hasItemErr(a.id);
    return `<div class="builder-card p-4 space-y-3 ${he?'has-error':''}" data-item-id="${a.id}"><div class="flex items-center gap-3"><div class="flex items-center gap-1.5"><span class="w-2 h-2 rounded-full ${a.mode==='async'?'bg-aamber':'bg-agreen'}"></span><select onchange="updateActivity('${a.id}','mode',this.value)" class="bg-raised border border-bdr rounded-lg px-2 py-1 text-xs mono font-semibold focus:outline-none focus:border-accent ${a.mode==='async'?'text-aamber':'text-agreen'}"><option value="sync"${a.mode==='sync'?' selected':''}>sync</option><option value="async"${a.mode==='async'?' selected':''}>async</option></select></div><input value="${esc(a.name)}" onchange="updateActivity('${a.id}','name',this.value)" placeholder="activity_name (snake_case)" class="flex-1 bg-raised border border-bdr rounded-lg px-3 py-2 text-sm mono font-semibold focus:outline-none focus:border-accent"/><button onclick="removeActivity('${a.id}')" class="text-muted hover:text-ared"><svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/></svg></button></div><input value="${esc(a.description)}" onchange="updateActivity('${a.id}','description',this.value)" placeholder="What does this activity do? (optional but helpful)" class="w-full bg-raised border border-bdr rounded-lg px-3 py-1.5 text-xs focus:outline-none focus:border-accent"/><div class="grid grid-cols-2 md:grid-cols-4 gap-3"><div><label class="block text-[10px] text-muted mb-1">Input Type</label><select onchange="updateActivity('${a.id}','input_type',this.value)" class="w-full bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent">${to.map(o=>`<option value="${o}"${a.input_type===o?' selected':''}>${o}</option>`).join('')}</select></div><div><label class="block text-[10px] text-muted mb-1">Output Type</label><select onchange="updateActivity('${a.id}','output_type',this.value)" class="w-full bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent">${to.map(o=>`<option value="${o}"${a.output_type===o?' selected':''}>${o}</option>`).join('')}</select></div><div><label class="block text-[10px] text-muted mb-1 flex items-center">Timeout <span class="hint">?<span class="hint-text">Max time from start to completion. e.g. 30s, 5m, 2h</span></span></label><input value="${esc(a.start_to_close_timeout)}" onchange="updateActivity('${a.id}','start_to_close_timeout',this.value)" placeholder="30s" class="w-full bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"/></div><div><label class="block text-[10px] text-muted mb-1 flex items-center">Max Retries <span class="hint">?<span class="hint-text">Retry count on failure with exponential backoff. 0=no retries.</span></span></label><input type="number" value="${a.retry_max_attempts}" onchange="updateActivity('${a.id}','retry_max_attempts',+this.value)" min="0" max="100" class="w-full bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"/></div></div>${a.mode==='async'?`<div class="grid grid-cols-2 gap-3 pt-2 border-t border-bdr/50"><div><label class="block text-[10px] text-aamber/70 mb-1 flex items-center">Heartbeat Timeout <span class="hint">?<span class="hint-text">How often the activity must heartbeat to prove it's alive.</span></span></label><input value="${esc(a.heartbeat_timeout)}" onchange="updateActivity('${a.id}','heartbeat_timeout',this.value)" placeholder="30s" class="w-full bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"/></div><div><label class="block text-[10px] text-aamber/70 mb-1 flex items-center">Task Queue Override <span class="hint">?<span class="hint-text">Route to a different queue. Leave empty for default.</span></span></label><input value="${esc(a.task_queue)}" onchange="updateActivity('${a.id}','task_queue',this.value)" placeholder="(uses default)" class="w-full bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"/></div></div>`:''}</div>`;
  }).join('')}</div>`;
}

// ══════════════════════════════════════════════════════════════
//  WORKFLOWS TAB
// ══════════════════════════════════════════════════════════════
function addWorkflow() { state.workflows.push({id:uid(),name:'',description:'',mode:'async',input_type:'string',output_type:'string',execution_timeout:'1h',cron_schedule:'',steps:[]}); renderAll(); }
function removeWorkflow(id) { state.workflows=state.workflows.filter(w=>w.id!==id); renderAll(); }
function addStep(wid) { const w=state.workflows.find(x=>x.id===wid); if(w){w.steps.push({id:uid(),step_id:'',kind:'activity',activity:'',output_var:''}); renderAll();} }
function removeStep(wid,sid) { const w=state.workflows.find(x=>x.id===wid); if(w){w.steps=w.steps.filter(s=>s.id!==sid); renderAll();} }
function updateWorkflow(id,key,val) { const w=state.workflows.find(x=>x.id===id); if(w)w[key]=val; if(key==='mode') renderAll(); }
function updateStep(wid,sid,key,val) { const w=state.workflows.find(x=>x.id===wid); if(!w)return; const s=w.steps.find(x=>x.id===sid); if(s)s[key]=val; }

function renderWorkflowsTab() {
  const empty = emptyState('workflows','No workflows defined yet.','Workflows orchestrate activities into reliable processes. Define activities first.','addWorkflow','+ Add Your First Workflow');
  const header = `<div class="flex items-center justify-between mb-4"><p class="text-sm text-dim flex items-center gap-1">Workflows orchestrate activities in order. <span class="hint">?<span class="hint-text"><b>sync</b>=blocks until done. <b>async</b>=fire-and-forget. <b>cron</b>=scheduled.</span></span></p><button onclick="addWorkflow()" class="px-3 py-1.5 rounded-lg text-xs font-medium bg-accent/10 text-accent border border-accent/20 hover:bg-accent/20 transition-all">+ Add Workflow</button></div>`;
  if(state.workflows.length===0) return header+empty;
  const to=getTypeOptions(), an=state.activities.filter(a=>a.name).map(a=>a.name), wn=state.workflows.filter(w=>w.name).map(w=>w.name);
  return header+`<div class="space-y-3">${state.workflows.map(w=>{
    const he=hasItemErr(w.id);
    return `<div class="builder-card p-4 space-y-3 ${he?'has-error':''}" data-item-id="${w.id}"><div class="flex items-center gap-3"><select onchange="updateWorkflow('${w.id}','mode',this.value)" class="bg-raised border border-bdr rounded-lg px-2 py-1 text-xs mono font-semibold focus:outline-none focus:border-accent ${w.mode==='cron'?'text-apurple':w.mode==='sync'?'text-agreen':'text-accent'}"><option value="sync"${w.mode==='sync'?' selected':''}>sync</option><option value="async"${w.mode==='async'?' selected':''}>async</option><option value="cron"${w.mode==='cron'?' selected':''}>cron</option></select><input value="${esc(w.name)}" onchange="updateWorkflow('${w.id}','name',this.value)" placeholder="WorkflowName (PascalCase)" class="flex-1 bg-raised border border-bdr rounded-lg px-3 py-2 text-sm mono font-semibold focus:outline-none focus:border-accent"/><button onclick="removeWorkflow('${w.id}')" class="text-muted hover:text-ared"><svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/></svg></button></div><input value="${esc(w.description)}" onchange="updateWorkflow('${w.id}','description',this.value)" placeholder="What does this workflow orchestrate? (optional)" class="w-full bg-raised border border-bdr rounded-lg px-3 py-1.5 text-xs focus:outline-none focus:border-accent"/><div class="grid grid-cols-2 md:grid-cols-4 gap-3"><div><label class="block text-[10px] text-muted mb-1">Input Type</label><select onchange="updateWorkflow('${w.id}','input_type',this.value)" class="w-full bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent">${to.map(o=>`<option value="${o}"${w.input_type===o?' selected':''}>${o}</option>`).join('')}</select></div><div><label class="block text-[10px] text-muted mb-1">Output Type</label><select onchange="updateWorkflow('${w.id}','output_type',this.value)" class="w-full bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent">${to.map(o=>`<option value="${o}"${w.output_type===o?' selected':''}>${o}</option>`).join('')}</select></div><div><label class="block text-[10px] text-muted mb-1">Exec Timeout</label><input value="${esc(w.execution_timeout)}" onchange="updateWorkflow('${w.id}','execution_timeout',this.value)" placeholder="1h" class="w-full bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"/></div>${w.mode==='cron'?`<div><label class="block text-[10px] text-apurple/70 mb-1">Cron Schedule</label><input value="${esc(w.cron_schedule)}" onchange="updateWorkflow('${w.id}','cron_schedule',this.value)" placeholder="0 3 * * *" class="w-full bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"/></div>`:'<div></div>'}</div><div><h4 class="text-[11px] font-semibold text-dim uppercase tracking-wide mb-2 flex items-center gap-1">Steps${an.length===0?'<span class="text-[10px] text-aamber font-normal normal-case tracking-normal ml-2">⚠ Define activities first</span>':''}</h4><div class="space-y-2">${w.steps.map((s,i)=>`<div class="entry-row flex items-center gap-2 p-2 rounded-lg"><span class="text-[10px] text-muted w-5 text-center">${i+1}</span><input value="${esc(s.step_id)}" onchange="updateStep('${w.id}','${s.id}','step_id',this.value)" placeholder="step_id" class="w-24 bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"/><select onchange="updateStep('${w.id}','${s.id}','kind',this.value);renderAll()" class="bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent">${['activity','child_workflow','timer','local_activity','parallel','condition'].map(k=>`<option value="${k}"${s.kind===k?' selected':''}>${k}</option>`).join('')}</select>${s.kind==='activity'?`<select onchange="updateStep('${w.id}','${s.id}','activity',this.value)" class="flex-1 bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"><option value="">-- select activity --</option>${an.map(n=>`<option value="${n}"${s.activity===n?' selected':''}>${n}</option>`).join('')}</select>`:s.kind==='child_workflow'?`<select onchange="updateStep('${w.id}','${s.id}','activity',this.value)" class="flex-1 bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"><option value="">-- select workflow --</option>${wn.map(n=>`<option value="${n}"${s.activity===n?' selected':''}>${n}</option>`).join('')}</select>`:s.kind==='timer'?`<input value="${esc(s.activity||'1m')}" onchange="updateStep('${w.id}','${s.id}','activity',this.value)" placeholder="5m" class="flex-1 bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"/>`:`<span class="flex-1 text-xs text-muted italic">Configure in YAML</span>`}<input value="${esc(s.output_var)}" onchange="updateStep('${w.id}','${s.id}','output_var',this.value)" placeholder="result_var" class="w-24 bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"/><button onclick="removeStep('${w.id}','${s.id}')" class="text-muted hover:text-ared text-xs">✕</button></div>`).join('')}</div><button onclick="addStep('${w.id}')" class="text-xs text-accent hover:text-accent/80 mt-2">+ Add Step</button></div></div>`;
  }).join('')}</div>`;
}

// ══════════════════════════════════════════════════════════════
//  PIPELINES, WORKERS, CLIENTS, RESULTS
// ══════════════════════════════════════════════════════════════
function addPipeline(){state.pipelines.push({id:uid(),name:'',description:'',trigger:'manual',timeout:'12h',stages:[]});renderAll();}
function removePipeline(id){state.pipelines=state.pipelines.filter(p=>p.id!==id);renderAll();}
function addStage(pid){const p=state.pipelines.find(x=>x.id===pid);if(p){p.stages.push({id:uid(),stage_id:'',execution:'sequential',workflow:'',max_concurrency:10});renderAll();}}
function removeStage(pid,sid){const p=state.pipelines.find(x=>x.id===pid);if(p){p.stages=p.stages.filter(s=>s.id!==sid);renderAll();}}
function updatePipeline(id,key,val){const p=state.pipelines.find(x=>x.id===id);if(p)p[key]=val;}
function updateStage(pid,sid,key,val){const p=state.pipelines.find(x=>x.id===pid);if(!p)return;const s=p.stages.find(x=>x.id===sid);if(s)s[key]=val;}

function renderPipelinesTab(){
  const empty=emptyState('pipelines','No pipelines defined yet. (Optional)','Pipelines coordinate multiple workflows — use for batch processing or staged rollouts.',null,'');
  const header=`<div class="flex items-center justify-between mb-4"><p class="text-sm text-dim">Multi-workflow orchestration with stages and fan-out.</p><button onclick="addPipeline()" class="px-3 py-1.5 rounded-lg text-xs font-medium bg-accent/10 text-accent border border-accent/20 hover:bg-accent/20 transition-all">+ Add Pipeline</button></div>`;
  if(state.pipelines.length===0) return header+empty;
  const wn=state.workflows.filter(w=>w.name).map(w=>w.name);
  return header+`<div class="space-y-3">${state.pipelines.map(p=>`<div class="builder-card p-4 space-y-3" data-item-id="${p.id}"><div class="flex items-center gap-3"><input value="${esc(p.name)}" onchange="updatePipeline('${p.id}','name',this.value)" placeholder="PipelineName" class="flex-1 bg-raised border border-bdr rounded-lg px-3 py-2 text-sm mono font-semibold focus:outline-none focus:border-accent"/><select onchange="updatePipeline('${p.id}','trigger',this.value)" class="bg-raised border border-bdr rounded-lg px-2 py-1 text-xs mono focus:outline-none focus:border-accent">${['manual','cron','signal','webhook','event'].map(t=>`<option value="${t}"${p.trigger===t?' selected':''}>${t}</option>`).join('')}</select><input value="${esc(p.timeout)}" onchange="updatePipeline('${p.id}','timeout',this.value)" placeholder="12h" class="w-20 bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"/><button onclick="removePipeline('${p.id}')" class="text-muted hover:text-ared"><svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/></svg></button></div><div><h4 class="text-[11px] font-semibold text-dim uppercase tracking-wide mb-2">Stages</h4><div class="space-y-2">${p.stages.map((s,i)=>`<div class="entry-row flex items-center gap-2 p-2 rounded-lg"><span class="text-[10px] text-muted w-5">${i+1}</span><input value="${esc(s.stage_id)}" onchange="updateStage('${p.id}','${s.id}','stage_id',this.value)" placeholder="stage_id" class="w-24 bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"/><select onchange="updateStage('${p.id}','${s.id}','execution',this.value)" class="bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent">${['sequential','parallel','fan_out_fan_in'].map(e=>`<option value="${e}"${s.execution===e?' selected':''}>${e}</option>`).join('')}</select><select onchange="updateStage('${p.id}','${s.id}','workflow',this.value)" class="flex-1 bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"><option value="">-- workflow --</option>${wn.map(n=>`<option value="${n}"${s.workflow===n?' selected':''}>${n}</option>`).join('')}</select><button onclick="removeStage('${p.id}','${s.id}')" class="text-muted hover:text-ared text-xs">✕</button></div>`).join('')}</div><button onclick="addStage('${p.id}')" class="text-xs text-accent hover:text-accent/80 mt-2">+ Add Stage</button></div></div>`).join('')}</div>`;
}

function addWorker(){state.workers.push({id:uid(),name:'',task_queue:'',description:'',activities:[],workflows:[],max_concurrent_activities:200,max_concurrent_workflow_tasks:200,replicas:1,cpu:'500m',memory:'512Mi'});renderAll();}
function removeWorker(id){state.workers=state.workers.filter(w=>w.id!==id);renderAll();}
function updateWorker(id,key,val){const w=state.workers.find(x=>x.id===id);if(w)w[key]=val;}
function toggleWorkerList(wid,lk,name,checked){const w=state.workers.find(x=>x.id===wid);if(!w)return;if(checked&&!w[lk].includes(name))w[lk].push(name);if(!checked)w[lk]=w[lk].filter(n=>n!==name);renderAll();}

function renderWorkersTab(){
  const empty=emptyState('workers','No workers defined yet.','You need at least one worker to execute activities and workflows.','addWorker','+ Add Your First Worker');
  const header=`<div class="flex items-center justify-between mb-4"><p class="text-sm text-dim">Worker processes — assign activities/workflows, tune concurrency.</p><button onclick="addWorker()" class="px-3 py-1.5 rounded-lg text-xs font-medium bg-accent/10 text-accent border border-accent/20 hover:bg-accent/20 transition-all">+ Add Worker</button></div>`;
  if(state.workers.length===0)return header+empty;
  const an=state.activities.filter(a=>a.name).map(a=>a.name),wn=state.workflows.filter(w=>w.name).map(w=>w.name);
  return header+`<div class="space-y-3">${state.workers.map(w=>{const he=hasItemErr(w.id);return`<div class="builder-card p-4 space-y-3 ${he?'has-error':''}" data-item-id="${w.id}"><div class="flex items-center gap-3"><span class="text-base">⚙</span><input value="${esc(w.name)}" onchange="updateWorker('${w.id}','name',this.value)" placeholder="worker-name (kebab-case)" class="flex-1 bg-raised border border-bdr rounded-lg px-3 py-2 text-sm mono font-semibold focus:outline-none focus:border-accent"/><label class="text-[10px] text-muted">Queue:</label><input value="${esc(w.task_queue)}" onchange="updateWorker('${w.id}','task_queue',this.value)" placeholder="task-queue" class="w-36 bg-raised border border-bdr rounded-lg px-3 py-2 text-xs mono focus:outline-none focus:border-accent"/><button onclick="removeWorker('${w.id}')" class="text-muted hover:text-ared"><svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/></svg></button></div><div class="grid grid-cols-2 gap-3"><div><label class="block text-[10px] text-muted mb-1">Activities to host</label><div class="space-y-1 max-h-32 overflow-y-auto">${an.map(n=>`<label class="flex items-center gap-1.5 text-xs mono"><input type="checkbox"${w.activities.includes(n)?' checked':''} onchange="toggleWorkerList('${w.id}','activities','${n}',this.checked)" class="accent-accent w-3 h-3"/>${n}</label>`).join('')}${an.length===0?'<span class="text-[10px] text-aamber italic">⚠ Define activities first</span>':''}</div></div><div><label class="block text-[10px] text-muted mb-1">Workflows to host</label><div class="space-y-1 max-h-32 overflow-y-auto">${wn.map(n=>`<label class="flex items-center gap-1.5 text-xs mono"><input type="checkbox"${w.workflows.includes(n)?' checked':''} onchange="toggleWorkerList('${w.id}','workflows','${n}',this.checked)" class="accent-accent w-3 h-3"/>${n}</label>`).join('')}${wn.length===0?'<span class="text-[10px] text-aamber italic">⚠ Define workflows first</span>':''}</div></div></div><div class="grid grid-cols-2 md:grid-cols-5 gap-3"><div><label class="block text-[10px] text-muted mb-1">Max Activities</label><input type="number" value="${w.max_concurrent_activities}" onchange="updateWorker('${w.id}','max_concurrent_activities',+this.value)" class="w-full bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"/></div><div><label class="block text-[10px] text-muted mb-1">Max WF Tasks</label><input type="number" value="${w.max_concurrent_workflow_tasks}" onchange="updateWorker('${w.id}','max_concurrent_workflow_tasks',+this.value)" class="w-full bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"/></div><div><label class="block text-[10px] text-muted mb-1">Replicas</label><input type="number" value="${w.replicas}" onchange="updateWorker('${w.id}','replicas',+this.value)" min="1" class="w-full bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"/></div><div><label class="block text-[10px] text-muted mb-1">CPU</label><input value="${esc(w.cpu)}" onchange="updateWorker('${w.id}','cpu',this.value)" class="w-full bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"/></div><div><label class="block text-[10px] text-muted mb-1">Memory</label><input value="${esc(w.memory)}" onchange="updateWorker('${w.id}','memory',this.value)" class="w-full bg-raised border border-bdr rounded px-2 py-1 text-xs mono focus:outline-none focus:border-accent"/></div></div></div>`;}).join('')}</div>`;
}

function addClient(){state.clients.push({id:uid(),name:'',description:'',target:'localhost:7233',tls_enabled:false,default_mode:'async',allowed_workflows:[],allowed_signals:[],allowed_queries:[]});renderAll();}
function removeClient(id){state.clients=state.clients.filter(c=>c.id!==id);renderAll();}
function updateClient(id,key,val){const c=state.clients.find(x=>x.id===id);if(c)c[key]=val;}
function toggleClientList(cid,lk,name,checked){const c=state.clients.find(x=>x.id===cid);if(!c)return;if(checked&&!c[lk].includes(name))c[lk].push(name);if(!checked)c[lk]=c[lk].filter(n=>n!==name);renderAll();}

function renderClientsTab(){
  const empty=emptyState('clients','No clients defined yet.','You need at least one client to start and interact with workflows.','addClient','+ Add Your First Client');
  const header=`<div class="flex items-center justify-between mb-4"><p class="text-sm text-dim">Typed clients — set connection and allowed workflows.</p><button onclick="addClient()" class="px-3 py-1.5 rounded-lg text-xs font-medium bg-accent/10 text-accent border border-accent/20 hover:bg-accent/20 transition-all">+ Add Client</button></div>`;
  if(state.clients.length===0)return header+empty;
  const wn=state.workflows.filter(w=>w.name).map(w=>w.name);
  return header+`<div class="space-y-3">${state.clients.map(c=>{const he=hasItemErr(c.id);return`<div class="builder-card p-4 space-y-3 ${he?'has-error':''}" data-item-id="${c.id}"><div class="flex items-center gap-3"><span class="text-base">🔌</span><input value="${esc(c.name)}" onchange="updateClient('${c.id}','name',this.value)" placeholder="client-name (kebab-case)" class="flex-1 bg-raised border border-bdr rounded-lg px-3 py-2 text-sm mono font-semibold focus:outline-none focus:border-accent"/><label class="text-[10px] text-muted">Target:</label><input value="${esc(c.target)}" onchange="updateClient('${c.id}','target',this.value)" placeholder="host:port" class="w-40 bg-raised border border-bdr rounded-lg px-3 py-2 text-xs mono focus:outline-none focus:border-accent"/><select onchange="updateClient('${c.id}','default_mode',this.value)" class="bg-raised border border-bdr rounded-lg px-2 py-1 text-xs mono focus:outline-none focus:border-accent"><option value="async"${c.default_mode==='async'?' selected':''}>async</option><option value="sync"${c.default_mode==='sync'?' selected':''}>sync</option></select><label class="flex items-center gap-1 text-[10px] text-dim"><input type="checkbox"${c.tls_enabled?' checked':''} onchange="updateClient('${c.id}','tls_enabled',this.checked)" class="accent-accent w-3 h-3"/>TLS</label><button onclick="removeClient('${c.id}')" class="text-muted hover:text-ared"><svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/></svg></button></div><div><label class="block text-[10px] text-muted mb-1">Allowed Workflows</label><div class="flex flex-wrap gap-2">${wn.map(n=>`<label class="flex items-center gap-1.5 text-xs mono"><input type="checkbox"${c.allowed_workflows.includes(n)?' checked':''} onchange="toggleClientList('${c.id}','allowed_workflows','${n}',this.checked)" class="accent-accent w-3 h-3"/>${n}</label>`).join('')}${wn.length===0?'<span class="text-[10px] text-aamber italic">⚠ Define workflows first</span>':''}</div></div></div>`;}).join('')}</div>`;
}

function renderResultsTab(){
  if(state.projects.length===0) return `<div class="text-center py-16 text-dim"><svg class="w-12 h-12 mx-auto mb-3 text-muted" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M3.75 9.776c.112-.017.227-.026.344-.026h15.812c.117 0 .232.009.344.026m-16.5 0a2.25 2.25 0 00-1.883 2.542l.857 6a2.25 2.25 0 002.227 1.932H19.05a2.25 2.25 0 002.227-1.932l.857-6a2.25 2.25 0 00-1.883-2.542m-16.5 0V6A2.25 2.25 0 016 3.75h3.879a1.5 1.5 0 011.06.44l2.122 2.12a1.5 1.5 0 001.06.44H18A2.25 2.25 0 0120.25 9v.776"/></svg>No generated projects yet. Configure your interface and click <strong class="text-accent">Generate</strong>.</div>`;
  const ki={worker:'⚙',client:'🔌',activities:'⚡',workflows:'🔄',types:'📦',config:'📄'};
  const badge=document.getElementById('resultsBadge');badge.textContent=state.projects.length;badge.classList.remove('hidden');
  return `<div class="space-y-4">${state.projects.map(p=>`<div class="builder-card p-5"><div class="flex items-center justify-between mb-4"><div><h3 class="font-semibold text-sm">${esc(p.project_name||p.project_id)}</h3><p class="text-[11px] text-muted mono mt-0.5">${p.project_id}</p></div><a href="${p.download_all_url}" class="px-3 py-1.5 rounded-lg text-xs font-medium bg-accent/10 text-accent border border-accent/20 hover:bg-accent/20 transition-all flex items-center gap-1.5"><svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/></svg>Download ZIP</a></div><div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2">${(p.files||[]).map(f=>{const k=f.kind||(f.name.includes('worker')?'worker':f.name.includes('client')?'client':f.name.includes('activities')?'activities':f.name.includes('workflows')?'workflows':f.name.includes('types')?'types':'config');return`<a href="${f.download_url}" class="flex items-center gap-2 p-2.5 rounded-lg bg-raised border border-bdr hover:border-accent/40 transition-all group"><span class="text-sm">${ki[k]||'📄'}</span><span class="text-xs mono text-dim group-hover:text-accent transition-colors truncate">${esc(f.name)}</span></a>`;}).join('')}</div></div>`).join('')}</div>`;
}

// ══════════════════════════════════════════════════════════════
//  DROP ZONES
// ══════════════════════════════════════════════════════════════
function initDropZones(){
  document.querySelectorAll('.drop-zone[data-target-type]').forEach(z=>{
    z.addEventListener('dragover',e=>{e.preventDefault();z.classList.add('drag-over');});
    z.addEventListener('dragleave',()=>z.classList.remove('drag-over'));
    z.addEventListener('drop',e=>{e.preventDefault();z.classList.remove('drag-over');const tv=e.dataTransfer.getData('text/plain');if(!tv)return;const tid=z.dataset.targetType;const t=state.types.find(x=>x.id===tid);if(t&&t.kind==='struct'){t.fields.push({id:uid(),name:'',type:tv,required:true});renderAll();}});
  });
}

// ══════════════════════════════════════════════════════════════
//  YAML
// ══════════════════════════════════════════════════════════════
function buildYaml(){const c={schema_version:'1.0.0',metadata:{name:document.getElementById('meta_name').value.trim(),namespace:document.getElementById('meta_namespace').value.trim(),default_task_queue:document.getElementById('meta_taskqueue').value.trim(),language:[document.getElementById('meta_language').value]}};if(state.types.length>0)c.types=state.types.filter(t=>t.name).map(t=>{const e={name:t.name,kind:t.kind};if(t.kind==='struct')e.fields=t.fields.filter(f=>f.name).map(f=>({name:f.name,type:f.type,required:f.required}));else if(t.kind==='enum')e.values=t.values.filter(v=>v.name).map(v=>({name:v.name,value:v.value||v.name}));else e.alias_of=t.alias_of;return e;});if(state.activities.length>0){c.retry_policies=state.activities.filter(a=>a.name&&a.retry_max_attempts).map(a=>({name:`auto-${a.name}`,initial_interval:'1s',backoff_coefficient:2.0,max_interval:'30s',max_attempts:a.retry_max_attempts}));c.activities=state.activities.filter(a=>a.name).map(a=>{const e={name:a.name,description:a.description,mode:a.mode,input:{type:a.input_type},output:{type:a.output_type},start_to_close_timeout:a.start_to_close_timeout,retry_policy:`auto-${a.name}`};if(a.mode==='async'){e.heartbeat_timeout=a.heartbeat_timeout;if(a.task_queue)e.task_queue=a.task_queue;}return e;});}if(state.workflows.length>0)c.workflows=state.workflows.filter(w=>w.name).map(w=>{const e={name:w.name,description:w.description,mode:w.mode,input:{type:w.input_type},output:{type:w.output_type},execution_timeout:w.execution_timeout};if(w.mode==='cron')e.cron_schedule=w.cron_schedule;if(w.steps.length>0)e.steps=w.steps.filter(s=>s.step_id).map(s=>{const st={id:s.step_id,kind:s.kind};if(s.kind==='activity')st.activity=s.activity;else if(s.kind==='child_workflow')st.workflow=s.activity;else if(s.kind==='timer')st.duration=s.activity||'1m';if(s.output_var)st.output_var=s.output_var;return st;});return e;});if(state.pipelines.length>0)c.pipelines=state.pipelines.filter(p=>p.name).map(p=>{const e={name:p.name,trigger:{kind:p.trigger},timeout:p.timeout};if(p.stages.length>0)e.stages=p.stages.filter(s=>s.stage_id).map(s=>({id:s.stage_id,execution:s.execution,workflows:[{workflow:s.workflow,max_concurrency:s.max_concurrency}]}));return e;});if(state.workers.length>0)c.workers=state.workers.filter(w=>w.name).map(w=>({name:w.name,task_queue:w.task_queue||c.metadata.default_task_queue,activities:w.activities,workflows:w.workflows,max_concurrent_activities:w.max_concurrent_activities,max_concurrent_workflow_tasks:w.max_concurrent_workflow_tasks,runtime:{replicas:w.replicas,cpu:w.cpu,memory:w.memory}}));if(state.clients.length>0)c.clients=state.clients.filter(c2=>c2.name).map(c2=>{const e={name:c2.name,target:c2.target,default_mode:c2.default_mode,allowed_workflows:c2.allowed_workflows};if(c2.tls_enabled)e.tls={enabled:true};return e;});return c;}

function toYamlString(obj,indent=0){const pad='  '.repeat(indent);let out='';if(Array.isArray(obj)){if(obj.length===0)return'[]';for(const item of obj){if(typeof item==='object'&&item!==null){out+=`${pad}-\n`;out+=toYamlString(item,indent+1);}else out+=`${pad}- ${JSON.stringify(item)}\n`;}}else if(typeof obj==='object'&&obj!==null){for(const[k,v]of Object.entries(obj)){if(v===undefined||v===null||v==='')continue;if(typeof v==='object'){out+=`${pad}${k}:\n`;out+=toYamlString(v,indent+1);}else if(typeof v==='string')out+=`${pad}${k}: "${v}"\n`;else out+=`${pad}${k}: ${v}\n`;}}return out;}

let lastYaml='';
function previewYaml(){const c=buildYaml();lastYaml=toYamlString(c);document.getElementById('yamlPreviewContent').textContent=lastYaml;document.getElementById('yamlModal').classList.remove('hidden');document.getElementById('yamlModal').classList.add('flex');}
function closeYamlModal(){document.getElementById('yamlModal').classList.add('hidden');document.getElementById('yamlModal').classList.remove('flex');}
function copyYaml(){navigator.clipboard.writeText(lastYaml);showToast('YAML copied','agreen');}

function validateAndGenerate(){
  clearValidationUI();
  const errors=validate();
  const real=errors.filter(e=>e.severity!=='warning');
  errors.filter(e=>e.field).forEach(e=>{const i=document.getElementById(e.field);if(i)i.classList.add('input-error');const d=document.getElementById(`err_${e.field}`);if(d){d.textContent=e.msg;d.classList.remove('hidden');}});
  renderAll();
  if(real.length>0){showValidationPanel(errors);showToast(`${real.length} error${real.length!==1?'s':''} — fix to generate.`,'ared');const b=document.getElementById('generateBtn');b.classList.add('shake');setTimeout(()=>b.classList.remove('shake'),400);return;}
  if(errors.length>0)showValidationPanel(errors);
  generateProject();
}

async function generateProject(){
  const btn=document.getElementById('generateBtn');btn.disabled=true;
  btn.innerHTML='<svg class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path></svg>Generating...';
  try{const res=await fetch('/api/generate',{method:'POST',headers:{'Content-Type':'text/yaml'},body:toYamlString(buildYaml())});if(!res.ok){const e=await res.json();throw new Error(e.detail||'Failed');}const data=await res.json();state.projects.unshift(data);switchTab('results');showToast(`"${data.project_name}" generated!`,'agreen');}catch(e){showToast(`Error: ${e.message}`,'ared');}finally{btn.disabled=false;btn.innerHTML='<svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M5 3l14 9-14 9V3z"/></svg>Generate';}
}

async function loadProjects(){try{const r=await fetch('/api/projects');const d=await r.json();if(d.projects)state.projects=d.projects.map(p=>({project_id:p.project_id,project_name:p.project_name,files:p.files.map(f=>({name:f,download_url:`/api/projects/${p.project_id}/files/${f}`})),download_all_url:p.download_url}));}catch(e){}}

function showToast(msg,color='accent'){const c=document.getElementById('toasts');const el=document.createElement('div');el.className=`toast px-4 py-3 rounded-lg bg-surface border border-bdr shadow-xl flex items-center gap-2 text-sm max-w-sm`;el.innerHTML=`<span class="w-2 h-2 rounded-full bg-${color} shrink-0"></span><span>${msg}</span>`;c.appendChild(el);setTimeout(()=>{el.style.opacity='0';el.style.transition='opacity .3s';setTimeout(()=>el.remove(),300);},5000);}

// ══════════════════════════════════════════════════════════════
//  INIT
// ══════════════════════════════════════════════════════════════
document.addEventListener('DOMContentLoaded',()=>{
  initPalette(); loadProjects();
  const visited=sessionStorage.getItem('temporal_builder_visited');
  if(!visited){sessionStorage.setItem('temporal_builder_visited','1');loadSampleProject();}
  renderAll();
});
