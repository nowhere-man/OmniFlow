import { useEffect, useState } from "react";
import { Check, Edit2, Plus, Trash2, X, BookHeart, CreditCard, Star } from "lucide-react";
import { AnimatePresence, motion } from "framer-motion";
import { Account, Ledger, PeriodicBill, TransactionAPI } from "../../../tauri-adapter/transactions";
import { useAppStore } from "../../../stores/appStore";
import { yuan } from "../../../lib/format";

const ts = () => Math.floor(Date.now() / 1000);

const COVERS = [
  "linear-gradient(135deg, #10b981, #047857)", // Emerald
  "linear-gradient(135deg, #3b82f6, #1d4ed8)", // Blue
  "linear-gradient(135deg, #8b5cf6, #5b21b6)", // Purple
  "linear-gradient(135deg, #f59e0b, #b45309)", // Orange
  "linear-gradient(135deg, #ec4899, #be185d)", // Pink
  "linear-gradient(135deg, #64748b, #334155)", // Slate
  "linear-gradient(135deg, #14b8a6, #0f766e)", // Teal
  "linear-gradient(135deg, #f43f5e, #be123c)", // Rose
];

export default function ManagementSettings() {
  const { fetchInitialData } = useAppStore();
  const [ledgers, setLedgers] = useState<Ledger[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [bills, setBills] = useState<PeriodicBill[]>([]);
  const [message, setMessage] = useState("");

  const [editingLedger, setEditingLedger] = useState<Ledger | null>(null);
  const [editingAccount, setEditingAccount] = useState<Account | null>(null);

  async function load() {
    try {
      const [nextLedgers, nextAccounts, nextBills] = await Promise.all([
        TransactionAPI.listLedgers(),
        TransactionAPI.listAccounts(),
        TransactionAPI.listPeriodicBills(),
      ]);
      setLedgers(nextLedgers);
      setAccounts(nextAccounts);
      setBills(nextBills);
    } catch (e: any) {
      setMessage(`加载失败: ${e.message || String(e)}`);
    }
  }

  useEffect(() => {
    load();
  }, []);

  function handleAddLedger() {
    setEditingLedger({
      id: crypto.randomUUID(),
      name: "新账本",
      budget: 0,
      cover: COVERS[0],
      description: "",
      is_default: ledgers.length === 0,
      created_at: ts(),
      updated_at: ts(),
      deleted_at: null,
    });
  }

  function handleAddAccount() {
    setEditingAccount({
      id: crypto.randomUUID(),
      name: "新账户",
      account_type: "cash",
      balance: 0,
      credit_limit: 0,
      cover: COVERS[1],
      description: "",
      is_default: accounts.length === 0,
      bill_day: null,
      repay_day: null,
      created_at: ts(),
      updated_at: ts(),
      deleted_at: null,
    });
  }

  async function saveLedger(ledger: Ledger) {
    try {
      const exists = ledgers.some((l) => l.id === ledger.id);
      if (exists) {
        await TransactionAPI.updateLedger({ ...ledger, updated_at: ts() });
      } else {
        await TransactionAPI.createLedger(ledger);
      }
      setEditingLedger(null);
      await load();
      await fetchInitialData();
    } catch (e: any) {
      setMessage(`保存账本失败: ${e.message || String(e)}`);
    }
  }

  async function saveAccount(account: Account) {
    try {
      const exists = accounts.some((a) => a.id === account.id);
      if (exists) {
        await TransactionAPI.updateAccount({ ...account, updated_at: ts() });
      } else {
        await TransactionAPI.createAccount(account);
      }
      setEditingAccount(null);
      await load();
      await fetchInitialData();
    } catch (e: any) {
      setMessage(`保存账户失败: ${e.message || String(e)}`);
    }
  }

  async function addPeriodicBill() {
    const account = accounts[0];
    if (!account) {
      setMessage("请先创建账户");
      return;
    }
    await TransactionAPI.createPeriodicBill({
      id: crypto.randomUUID(),
      name: "新周期账单",
      amount: 0,
      bill_type: "expense",
      category_id: null,
      account_id: account.id,
      cron_expression: "monthly",
      next_date: ts(),
      created_at: ts(),
      updated_at: ts(),
      deleted_at: null,
    });
    await load();
  }

  return (
    <div className="settings-stack wide">
      {message && <div className="panel panel-pad" style={{ background: "var(--danger)", color: "white" }}>{message}</div>}
      
      <Section title="我的账本" icon={<BookHeart size={20} />} onAdd={handleAddLedger}>
        <div className="card-grid">
          {ledgers.map((ledger) => (
            <div key={ledger.id} className="resource-card" style={{ background: ledger.cover || "var(--surface)" }}>
              <div className="resource-card-content">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <h3 style={{ color: ledger.cover ? "white" : "inherit" }}>
                    {ledger.name} {ledger.is_default && <Star size={14} fill="currentColor" style={{ marginLeft: 4 }} />}
                  </h3>
                  <div className="resource-actions">
                    <button className="icon-button light" onClick={() => setEditingLedger(ledger)}><Edit2 size={14} /></button>
                    <button className="icon-button light" onClick={async () => { await TransactionAPI.deleteLedger(ledger.id); await load(); }}><Trash2 size={14} /></button>
                  </div>
                </div>
                <div className="resource-meta" style={{ color: ledger.cover ? "rgba(255,255,255,0.8)" : "var(--muted)" }}>
                  <div>预算: {yuan(ledger.budget)}</div>
                  {ledger.description && <div>{ledger.description}</div>}
                </div>
              </div>
            </div>
          ))}
        </div>
      </Section>

      <Section title="我的账户" icon={<CreditCard size={20} />} onAdd={handleAddAccount}>
        <div className="card-grid">
          {accounts.map((account) => (
            <div key={account.id} className="resource-card" style={{ background: account.cover || "var(--surface)" }}>
              <div className="resource-card-content">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <h3 style={{ color: account.cover ? "white" : "inherit" }}>
                    {account.name} {account.is_default && <Star size={14} fill="currentColor" style={{ marginLeft: 4 }} />}
                  </h3>
                  <div className="resource-actions">
                    <button className="icon-button light" onClick={() => setEditingAccount(account)}><Edit2 size={14} /></button>
                    <button className="icon-button light" onClick={async () => { await TransactionAPI.deleteAccount(account.id); await load(); }}><Trash2 size={14} /></button>
                  </div>
                </div>
                <div className="resource-meta" style={{ color: account.cover ? "rgba(255,255,255,0.8)" : "var(--muted)" }}>
                  <div>{account.account_type} · 余额 {yuan(account.balance)}</div>
                  {account.description && <div>{account.description}</div>}
                </div>
              </div>
            </div>
          ))}
        </div>
      </Section>

      <Section title="周期账单" onAdd={addPeriodicBill}>
        <div className="line-list">
          {bills.map((bill) => (
            <Row key={bill.id} title={bill.name} meta={`${bill.bill_type} · ${bill.amount}`} onDelete={async () => { await TransactionAPI.deletePeriodicBill(bill.id); await load(); }} />
          ))}
        </div>
      </Section>

      <AnimatePresence>
        {editingLedger && (
          <LedgerModal 
            ledger={editingLedger} 
            onClose={() => setEditingLedger(null)} 
            onSave={saveLedger} 
          />
        )}
        {editingAccount && (
          <AccountModal 
            account={editingAccount} 
            onClose={() => setEditingAccount(null)} 
            onSave={saveAccount} 
          />
        )}
      </AnimatePresence>
    </div>
  );
}

function Section({ title, icon, children, onAdd }: { title: string; icon?: React.ReactNode; children: React.ReactNode; onAdd: () => void }) {
  return (
    <section className="panel panel-pad" style={{ padding: '24px' }}>
      <div className="settings-head-row" style={{ marginBottom: '20px' }}>
        <h2 className="section-title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {icon}
          {title}
        </h2>
        <div className="toolbar">
          <button className="primary-button" onClick={onAdd}><Plus size={16} />新增</button>
        </div>
      </div>
      {children}
    </section>
  );
}

function Row({ title, meta, onDelete }: { title: string; meta: string; onDelete: () => void }) {
  return (
    <div className="line-item">
      <div>
        <div className="line-title">{title}</div>
        <div className="line-meta">{meta}</div>
      </div>
      <div className="toolbar">
        <button className="icon-button" onClick={onDelete} aria-label="删除"><Trash2 size={15} /></button>
      </div>
    </div>
  );
}

// -- Modals --

function LedgerModal({ ledger, onClose, onSave }: { ledger: Ledger; onClose: () => void; onSave: (l: Ledger) => void }) {
  const [data, setData] = useState(ledger);
  
  return (
    <div className="modal-overlay" onClick={onClose}>
      <motion.div 
        className="modal-content" 
        onClick={e => e.stopPropagation()}
        initial={{ opacity: 0, y: 20, scale: 0.95 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        exit={{ opacity: 0, y: 10, scale: 0.95 }}
      >
        <div className="modal-header">
          <h3>编辑账本</h3>
          <button className="icon-button" onClick={onClose}><X size={20}/></button>
        </div>
        <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div className="form-group">
            <label>账本名称</label>
            <input type="text" className="input" value={data.name} onChange={e => setData({...data, name: e.target.value})} placeholder="例如：日常账本" />
          </div>
          <div className="form-group">
            <label>每月预算</label>
            <input type="number" className="input" value={data.budget} onChange={e => setData({...data, budget: parseFloat(e.target.value) || 0})} />
          </div>
          <div className="form-group">
            <label>备注描述</label>
            <input type="text" className="input" value={data.description || ""} onChange={e => setData({...data, description: e.target.value})} />
          </div>
          <div className="form-group">
            <label>选择封面颜色</label>
            <div className="color-picker-grid">
              {COVERS.map(cover => (
                <button 
                  key={cover}
                  className={`color-swatch ${data.cover === cover ? 'active' : ''}`}
                  style={{ background: cover, width: '32px', height: '32px', borderRadius: '50%', border: 'none', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: 'inset 0 0 0 1px rgba(0,0,0,0.1)' }}
                  onClick={() => setData({...data, cover})}
                >
                  {data.cover === cover && <Check size={16} color="white" />}
                </button>
              ))}
            </div>
          </div>
          <label className="checkbox-label" style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
            <input type="checkbox" checked={data.is_default} onChange={e => setData({...data, is_default: e.target.checked})} />
            <span>设为默认账本</span>
          </label>
        </div>
        <div className="modal-footer" style={{ marginTop: '24px', display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
          <button className="secondary-button" onClick={onClose}>取消</button>
          <button className="primary-button" onClick={() => onSave(data)}>保存</button>
        </div>
      </motion.div>
    </div>
  );
}

function AccountModal({ account, onClose, onSave }: { account: Account; onClose: () => void; onSave: (a: Account) => void }) {
  const [data, setData] = useState(account);
  
  return (
    <div className="modal-overlay" onClick={onClose}>
      <motion.div 
        className="modal-content" 
        onClick={e => e.stopPropagation()}
        initial={{ opacity: 0, y: 20, scale: 0.95 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        exit={{ opacity: 0, y: 10, scale: 0.95 }}
      >
        <div className="modal-header">
          <h3>编辑账户</h3>
          <button className="icon-button" onClick={onClose}><X size={20}/></button>
        </div>
        <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div className="form-group">
            <label>账户名称</label>
            <input type="text" className="input" value={data.name} onChange={e => setData({...data, name: e.target.value})} placeholder="例如：招商银行储蓄卡" />
          </div>
          <div className="form-group">
            <label>账户类型</label>
            <select className="input" value={data.account_type} onChange={e => setData({...data, account_type: e.target.value as any})}>
              <option value="cash">现金 / 储蓄卡</option>
              <option value="credit">信用卡</option>
              <option value="wallet">数字钱包 (支付宝/微信)</option>
              <option value="other">其他</option>
            </select>
          </div>
          <div className="form-group">
            <label>当前余额</label>
            <input type="number" className="input" value={data.balance} onChange={e => setData({...data, balance: parseFloat(e.target.value) || 0})} />
          </div>
          {data.account_type === 'credit' && (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
              <div className="form-group">
                <label>信用额度</label>
                <input type="number" className="input" value={data.credit_limit} onChange={e => setData({...data, credit_limit: parseFloat(e.target.value) || 0})} />
              </div>
              <div className="form-group">
                <label>账单日</label>
                <input type="number" className="input" value={data.bill_day || ''} onChange={e => setData({...data, bill_day: parseInt(e.target.value) || null})} placeholder="1-31" />
              </div>
            </div>
          )}
          <div className="form-group">
            <label>备注描述</label>
            <input type="text" className="input" value={data.description || ""} onChange={e => setData({...data, description: e.target.value})} />
          </div>
          <div className="form-group">
            <label>选择封面颜色</label>
            <div className="color-picker-grid">
              {COVERS.map(cover => (
                <button 
                  key={cover}
                  className={`color-swatch ${data.cover === cover ? 'active' : ''}`}
                  style={{ background: cover, width: '32px', height: '32px', borderRadius: '50%', border: 'none', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: 'inset 0 0 0 1px rgba(0,0,0,0.1)' }}
                  onClick={() => setData({...data, cover})}
                >
                  {data.cover === cover && <Check size={16} color="white" />}
                </button>
              ))}
            </div>
          </div>
          <label className="checkbox-label" style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
            <input type="checkbox" checked={data.is_default} onChange={e => setData({...data, is_default: e.target.checked})} />
            <span>设为默认账户</span>
          </label>
        </div>
        <div className="modal-footer" style={{ marginTop: '24px', display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
          <button className="secondary-button" onClick={onClose}>取消</button>
          <button className="primary-button" onClick={() => onSave(data)}>保存</button>
        </div>
      </motion.div>
    </div>
  );
}
