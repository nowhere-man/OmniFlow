import { useEffect, useState } from "react";
import { Play, Plus, Trash2 } from "lucide-react";
import { Account, Ledger, PeriodicBill, Rule, TransactionAPI } from "../../../tauri-adapter/transactions";
import { useAppStore } from "../../../stores/appStore";

const ts = () => Math.floor(Date.now() / 1000);

export default function ManagementSettings() {
  const { currentLedgerId, fetchInitialData } = useAppStore();
  const [ledgers, setLedgers] = useState<Ledger[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [rules, setRules] = useState<Rule[]>([]);
  const [bills, setBills] = useState<PeriodicBill[]>([]);
  const [message, setMessage] = useState("");
  const [sampleMerchant, setSampleMerchant] = useState("咖啡");
  const [sampleAmount, setSampleAmount] = useState(18);

  async function load() {
    const [nextLedgers, nextAccounts, nextRules, nextBills] = await Promise.all([
      TransactionAPI.listLedgers(),
      TransactionAPI.listAccounts(),
      TransactionAPI.listRules(),
      TransactionAPI.listPeriodicBills(),
    ]);
    setLedgers(nextLedgers);
    setAccounts(nextAccounts);
    setRules(nextRules);
    setBills(nextBills);
  }

  useEffect(() => {
    load();
  }, []);

  async function addLedger() {
    await TransactionAPI.createLedger({ id: crypto.randomUUID(), name: "新账本", budget: 0, created_at: ts(), updated_at: ts(), deleted_at: null });
    await load();
    await fetchInitialData();
  }

  async function addAccount() {
    await TransactionAPI.createAccount({
      id: crypto.randomUUID(),
      name: "新账户",
      account_type: "cash",
      balance: 0,
      credit_limit: 0,
      bill_day: null,
      repay_day: null,
      created_at: ts(),
      updated_at: ts(),
      deleted_at: null,
    });
    await load();
    await fetchInitialData();
  }

  async function addRule() {
    await TransactionAPI.createRule({
      id: crypto.randomUUID(),
      name: "咖啡规则",
      priority: 10,
      match_condition: JSON.stringify([{ match_type: "merchant_keyword", value: "咖啡" }]),
      action: JSON.stringify([{ action_type: "add_tag", value: "coffee" }]),
      created_at: ts(),
      updated_at: ts(),
      deleted_at: null,
    });
    await load();
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

  async function reapplyRules() {
    if (!currentLedgerId) return;
    const count = await TransactionAPI.reapplyRules(currentLedgerId);
    setMessage(`已重新应用规则，更新 ${count} 笔交易`);
  }

  async function generatePending() {
    const created = await TransactionAPI.generatePendingConfirmations(ts());
    setMessage(`已生成 ${created.length} 张待确认卡片`);
  }

  function testRule(rule: Rule) {
    try {
      const conditions = JSON.parse(rule.match_condition) as Array<{ match_type: string; value: string }>;
      const hit = conditions.every((condition) => {
        if (condition.match_type === "merchant_keyword") return sampleMerchant.includes(condition.value);
        if (condition.match_type === "amount_range") {
          const [min, max] = condition.value.split(",").map(Number);
          return sampleAmount >= min && sampleAmount <= max;
        }
        return false;
      });
      setMessage(hit ? `「${rule.name}」会命中样例` : `「${rule.name}」不会命中样例`);
    } catch {
      setMessage("规则 JSON 无法解析");
    }
  }

  return (
    <div className="grid gap-6">
      {message && <div className="panel panel-pad">{message}</div>}
      <Section title="账本" onAdd={addLedger}>
        {ledgers.map((ledger) => (
          <Row key={ledger.id} title={ledger.name} meta={`预算 ${ledger.budget}`} onDelete={async () => { await TransactionAPI.deleteLedger(ledger.id); await load(); }} />
        ))}
      </Section>

      <Section title="账户" onAdd={addAccount}>
        {accounts.map((account) => (
          <Row key={account.id} title={account.name} meta={`${account.account_type} · 余额 ${account.balance}`} onDelete={async () => { await TransactionAPI.deleteAccount(account.id); await load(); }} />
        ))}
      </Section>

      <Section title="规则" onAdd={addRule} action={<button className="ghost-button" onClick={reapplyRules}><Play size={16} />重新执行</button>}>
        <div className="toolbar">
          <input className="field" value={sampleMerchant} onChange={(event) => setSampleMerchant(event.target.value)} />
          <input className="field w-24" type="number" value={sampleAmount} onChange={(event) => setSampleAmount(Number(event.target.value))} />
        </div>
        {rules.map((rule) => (
          <Row key={rule.id} title={rule.name} meta={`优先级 ${rule.priority}`} onRun={() => testRule(rule)} onDelete={async () => { await TransactionAPI.deleteRule(rule.id); await load(); }} />
        ))}
      </Section>

      <Section title="周期账单" onAdd={addPeriodicBill} action={<button className="ghost-button" onClick={generatePending}><Play size={16} />检测到期</button>}>
        {bills.map((bill) => (
          <Row key={bill.id} title={bill.name} meta={`${bill.bill_type} · ${bill.amount}`} onDelete={async () => { await TransactionAPI.deletePeriodicBill(bill.id); await load(); }} />
        ))}
      </Section>
    </div>
  );
}

function Section({ title, children, onAdd, action }: { title: string; children: React.ReactNode; onAdd: () => void; action?: React.ReactNode }) {
  return (
    <section className="panel panel-pad">
      <div className="flex items-center justify-between gap-3 mb-4">
        <h2 className="section-title">{title}</h2>
        <div className="toolbar">
          {action}
          <button className="primary-button" onClick={onAdd}><Plus size={16} />新增</button>
        </div>
      </div>
      <div className="grid gap-2">{children}</div>
    </section>
  );
}

function Row({ title, meta, onDelete, onRun }: { title: string; meta: string; onDelete: () => void; onRun?: () => void }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-[var(--border)] py-2 last:border-0">
      <div>
        <div className="font-semibold">{title}</div>
        <div className="text-xs text-[var(--muted)]">{meta}</div>
      </div>
      <div className="toolbar">
        {onRun && <button className="icon-button" onClick={onRun} aria-label="测试"><Play size={15} /></button>}
        <button className="icon-button" onClick={onDelete} aria-label="删除"><Trash2 size={15} /></button>
      </div>
    </div>
  );
}
