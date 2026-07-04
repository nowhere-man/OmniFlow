import { useEffect, useState } from "react";
import { Edit2, Plus, Save, Trash2, X } from "lucide-react";
import { useAppStore } from "../../stores/appStore";
import { Transaction, TransactionAPI } from "../../tauri-adapter/transactions";
import { shortDate, yuan } from "../../lib/format";

const now = () => Math.floor(Date.now() / 1000);

export default function TransactionList() {
  const { transactions, accounts, currentLedgerId, fetchInitialData, fetchTransactions, isLoading } = useAppStore();
  const [editing, setEditing] = useState<Transaction | null>(null);

  useEffect(() => {
    fetchInitialData();
  }, [fetchInitialData]);

  const defaultAccountId = accounts[0]?.id || "";

  function startNew() {
    if (!currentLedgerId || !defaultAccountId) return;
    setEditing({
      id: window.crypto.randomUUID(),
      ledger_id: currentLedgerId,
      account_id: defaultAccountId,
      category_id: null,
      transaction_date: now(),
      amount: 0,
      transaction_type: "expense",
      merchant: "",
      notes: "",
      tags: [],
      is_excluded: false,
      external_source: "manual",
      external_id: null,
      created_at: now(),
      updated_at: now(),
      deleted_at: null,
    });
  }

  async function save() {
    if (!editing) return;
    const payload = { ...editing, updated_at: now() };
    const exists = transactions.some((transaction) => transaction.id === payload.id);
    if (exists) {
      await TransactionAPI.updateTransaction(payload);
    } else {
      await TransactionAPI.createTransaction(payload);
    }
    setEditing(null);
    await fetchTransactions();
  }

  async function remove(id: string) {
    await TransactionAPI.deleteTransaction(id);
    await fetchTransactions();
  }

  async function toggleExcluded(transaction: Transaction) {
    await TransactionAPI.updateTransaction({ ...transaction, is_excluded: !transaction.is_excluded, updated_at: now() });
    await fetchTransactions();
  }

  return (
    <div className="page-stack">
      <section className="page-heading">
        <div>
          <div className="eyebrow">transactions</div>
          <h1 className="page-title">明细要轻，不要像表格后台</h1>
        </div>
        <button className="primary-button" onClick={startNew} disabled={!currentLedgerId || !defaultAccountId}><Plus size={17} />新增</button>
      </section>

      {editing && (
        <section className="panel panel-pad">
          <div className="toolbar">
            <select className="select-field" value={editing.transaction_type} onChange={(event) => setEditing({ ...editing, transaction_type: event.target.value as "expense" | "income" })}>
              <option value="expense">支出</option>
              <option value="income">收入</option>
            </select>
            <input className="field" type="number" min="0" step="0.01" value={editing.amount} onChange={(event) => setEditing({ ...editing, amount: Number(event.target.value) })} />
            <input className="field" value={editing.merchant || ""} placeholder="商户" onChange={(event) => setEditing({ ...editing, merchant: event.target.value })} />
            <input className="field" value={editing.notes || ""} placeholder="备注" onChange={(event) => setEditing({ ...editing, notes: event.target.value })} />
            <select className="select-field" value={editing.account_id} onChange={(event) => setEditing({ ...editing, account_id: event.target.value })}>
              {accounts.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
            </select>
            <label className="inline-flex items-center gap-2 text-sm font-semibold">
              <input type="checkbox" checked={editing.is_excluded} onChange={(event) => setEditing({ ...editing, is_excluded: event.target.checked })} />
              不计收支
            </label>
            <button className="primary-button" onClick={save}><Save size={17} />保存</button>
            <button className="ghost-button" onClick={() => setEditing(null)}><X size={17} />取消</button>
          </div>
        </section>
      )}

      <section className="panel overflow-x-auto">
        {isLoading ? (
          <div className="panel-pad text-center text-[var(--muted)]">正在载入交易...</div>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>日期</th>
                <th>商户</th>
                <th>金额</th>
                <th>标签</th>
                <th>来源</th>
                <th>不计收支</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {transactions.map((transaction) => (
                <tr key={transaction.id}>
                  <td>{shortDate(transaction.transaction_date)}</td>
                  <td>
                    <div className="font-semibold">{transaction.merchant || "未命名交易"}</div>
                    <div className="text-xs text-[var(--muted)]">{transaction.notes || "-"}</div>
                  </td>
                  <td className={transaction.transaction_type === "expense" ? "text-[var(--expense)]" : "text-[var(--income)]"}>{yuan(transaction.amount)}</td>
                  <td>{transaction.tags.length > 0 ? transaction.tags.join(" / ") : "-"}</td>
                  <td>{transaction.external_source || "manual"}</td>
                  <td><input type="checkbox" checked={transaction.is_excluded} onChange={() => toggleExcluded(transaction)} /></td>
                  <td>
                    <div className="toolbar">
                      <button className="icon-button" onClick={() => setEditing(transaction)} aria-label="编辑"><Edit2 size={16} /></button>
                      <button className="icon-button" onClick={() => remove(transaction.id)} aria-label="删除"><Trash2 size={16} /></button>
                    </div>
                  </td>
                </tr>
              ))}
              {transactions.length === 0 && (
                <tr><td colSpan={7} className="text-center text-[var(--muted)] py-12">还没有交易，先导入账单或手动新增一笔。</td></tr>
              )}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
