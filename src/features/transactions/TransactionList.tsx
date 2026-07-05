import { useEffect, useMemo, useState } from "react";
import { ArrowDown, ArrowUp, Edit2, Plus, Save, Search, Trash2, X } from "lucide-react";
import { invoke } from "@tauri-apps/api/core";
import { useAppStore } from "../../stores/appStore";
import { Transaction, TransactionAPI } from "../../tauri-adapter/transactions";
import { shortDate, yuan } from "../../lib/format";
import { Category } from "../../models";
import { Select } from "../../components/ui/Select";

const now = () => Math.floor(Date.now() / 1000);

export default function TransactionList() {
  const { accounts, currentLedgerId, fetchInitialData, fetchTransactionsPage, isLoading, transactions, transactionTotal } = useAppStore();
  const [query, setQuery] = useState("");
  const [editing, setEditing] = useState<Transaction | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [pageSize] = useState(80);

  useEffect(() => {
    fetchInitialData();
    invoke<Category[]>("list_categories").then(setCategories).catch(() => setCategories([]));
  }, [fetchInitialData]);

  useEffect(() => {
    if (currentLedgerId) {
      fetchTransactionsPage(0, pageSize);
    }
  }, [currentLedgerId, fetchTransactionsPage, pageSize]);

  const defaultAccountId = accounts[0]?.id || "";
  const visible = useMemo(() => {
    const key = query.trim();
    if (!key) return transactions;
    return transactions.filter((transaction) =>
      transaction.merchant?.includes(key)
      || transaction.notes?.includes(key)
      || transaction.tags.some((tag) => tag.includes(key))
      || transaction.amount.toString().includes(key)
    );
  }, [query, transactions]);
  const categoryLabelById = useMemo(() => new Map(categoryOptions(categories).map((category) => [category.id, category.label])), [categories]);

  function startNew() {
    if (!currentLedgerId || !defaultAccountId) return;
    const timestamp = now();
    setEditing({
      id: crypto.randomUUID(),
      ledger_id: currentLedgerId,
      account_id: defaultAccountId,
      category_id: null,
      transaction_date: timestamp,
      amount: 0,
      transaction_type: "expense",
      merchant: "",
      notes: "",
      tags: [],
      is_excluded: false,
      external_source: "manual",
      external_id: null,
      created_at: timestamp,
      updated_at: timestamp,
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
    await fetchTransactionsPage(0, pageSize);
  }

  async function remove(id: string) {
    await TransactionAPI.deleteTransaction(id);
    await fetchTransactionsPage(0, pageSize);
  }

  async function toggleExcluded(transaction: Transaction) {
    await TransactionAPI.updateTransaction({ ...transaction, is_excluded: !transaction.is_excluded, updated_at: now() });
    await fetchTransactionsPage(0, pageSize);
  }

  async function loadMore() {
    await fetchTransactionsPage(transactions.length, pageSize);
  }

  return (
    <div className="money-flow-page">
      <section className="flow-command">
        <div>
          <span>明细</span>
          <h1>交易明细</h1>
        </div>
        <div className="flow-command-tools">
          <label className="search-field">
            <Search size={17} />
            <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="商户、备注、标签、金额" />
          </label>
          <button className="primary-button" onClick={startNew} disabled={!currentLedgerId || !defaultAccountId}><Plus size={17} />新增</button>
        </div>
      </section>

      <section className="flow-layout">
        <div className="timeline-panel">
          {isLoading ? (
            <div className="empty-line">正在载入</div>
          ) : visible.length === 0 ? (
            <div className="empty-line">没有匹配交易</div>
          ) : (
            <>
              {visible.map((transaction) => (
                <article key={transaction.id} className="transaction-card" onClick={() => setEditing(transaction)}>
                  <div className={transaction.transaction_type === "expense" ? "tx-symbol expense" : "tx-symbol income"}>
                    {transaction.transaction_type === "expense" ? <ArrowUp size={16} /> : <ArrowDown size={16} />}
                  </div>
                  <div className="tx-body">
                    <div className="tx-mainline">
                      <strong>{transaction.merchant || transaction.notes || "未命名交易"}</strong>
                      <b className={transaction.transaction_type === "expense" ? "amount-expense" : "amount-income"}>
                        {transaction.transaction_type === "expense" ? "-" : "+"}{yuan(transaction.amount)}
                      </b>
                    </div>
                    <div className="tx-meta">
                      <span>{shortDate(transaction.transaction_date)}</span>
                      {transaction.category_id && <span>{categoryLabelById.get(transaction.category_id) || "未分类"}</span>}
                      <span>{transaction.external_source || "manual"}</span>
                      {transaction.is_excluded && <span>不计收支</span>}
                    </div>
                    {transaction.tags.length > 0 && <div className="tx-tags">{transaction.tags.map((tag) => <span key={tag}>{tag}</span>)}</div>}
                  </div>
                  <div className="tx-actions" onClick={(event) => event.stopPropagation()}>
                    <button className="icon-button" onClick={() => setEditing(transaction)} aria-label="详情"><Edit2 size={15} /></button>
                    <button className="icon-button" onClick={() => toggleExcluded(transaction)} aria-label="不计收支"><X size={15} /></button>
                    <button className="icon-button" onClick={() => remove(transaction.id)} aria-label="删除"><Trash2 size={15} /></button>
                  </div>
                </article>
              ))}
              {!query.trim() && transactions.length < transactionTotal && (
                <button className="ghost-button load-more-button" onClick={loadMore} disabled={isLoading}>
                  已显示 {transactions.length} / {transactionTotal}，加载更多
                </button>
              )}
            </>
          )}
        </div>

        <aside className={`edit-drawer ${editing ? "open" : ""}`}>
          {editing ? (
            <>
              <div className="panel-head">
                <h2>{transactions.some((transaction) => transaction.id === editing.id) ? "编辑交易" : "新增交易"}</h2>
                <button className="icon-button" onClick={() => setEditing(null)} aria-label="关闭"><X size={15} /></button>
              </div>
              <div className="drawer-fields">
                <div className="segmented">
                  <button className={editing.transaction_type === "expense" ? "active" : ""} onClick={() => setEditing({ ...editing, transaction_type: "expense" })}>支出</button>
                  <button className={editing.transaction_type === "income" ? "active" : ""} onClick={() => setEditing({ ...editing, transaction_type: "income" })}>收入</button>
                </div>
                <input className="money-input" type="number" min="0" step="0.01" value={editing.amount} onChange={(event) => setEditing({ ...editing, amount: Number(event.target.value) })} />
                <input className="field" value={editing.merchant || ""} placeholder="商户" onChange={(event) => setEditing({ ...editing, merchant: event.target.value })} />
                <input className="field" value={editing.notes || ""} placeholder="备注" onChange={(event) => setEditing({ ...editing, notes: event.target.value })} />
                <input className="field" type="datetime-local" value={toDateTimeInput(editing.transaction_date)} onChange={(event) => setEditing({ ...editing, transaction_date: Math.floor(new Date(event.target.value).getTime() / 1000) })} />
                <Select
                  value={editing.account_id}
                  onChange={(val) => setEditing({ ...editing, account_id: val })}
                  options={accounts.map((account) => ({ value: account.id, label: account.name }))}
                />
                <Select
                  value={editing.category_id || ""}
                  onChange={(val) => setEditing({ ...editing, category_id: val || null })}
                  options={[
                    { value: "", label: "未分类" },
                    ...categoryOptions(categories).map((category) => ({ value: category.id, label: category.label })),
                  ]}
                />
                <input className="field" value={editing.tags.join(",")} placeholder="标签，用逗号分隔" onChange={(event) => setEditing({ ...editing, tags: event.target.value.split(",").map((tag) => tag.trim()).filter(Boolean) })} />
                <label className="check-row">
                  <input type="checkbox" checked={editing.is_excluded} onChange={(event) => setEditing({ ...editing, is_excluded: event.target.checked })} />
                  不计入收支
                </label>
                <button className="primary-button" onClick={save}><Save size={17} />保存</button>
              </div>
            </>
          ) : (
            <div className="empty-line">选择一笔交易</div>
          )}
        </aside>
      </section>
    </div>
  );
}

function toDateTimeInput(timestamp: number) {
  const date = new Date(timestamp * 1000);
  const pad = (value: number) => value.toString().padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function categoryOptions(categories: Category[]) {
  const parents = categories.filter((category) => !category.parent_id);
  const childrenByParent = new Map<string, Category[]>();
  for (const category of categories) {
    if (!category.parent_id) continue;
    childrenByParent.set(category.parent_id, [...(childrenByParent.get(category.parent_id) || []), category]);
  }
  return parents.flatMap((parent) => [
    { id: parent.id, label: parent.name },
    ...(childrenByParent.get(parent.id) || []).map((child) => ({ id: child.id, label: `${parent.name} / ${child.name}` })),
  ]);
}
