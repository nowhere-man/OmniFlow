import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import ReactECharts from "echarts-for-react";
import {
  ArrowDown,
  ArrowUp,
  FileDown,
  ListTree,
  Plus,
  Search,
  WalletCards,
} from "lucide-react";
import { useAppStore } from "../../stores/appStore";
import { DashboardSummary, RankedTransaction, StatsAPI, TrendDataPoint } from "../../tauri-adapter/stats";
import { Transaction, TransactionAPI } from "../../tauri-adapter/transactions";
import { monthRange, shortDate, yuan } from "../../lib/format";

const now = () => Math.floor(Date.now() / 1000);

export default function Dashboard() {
  const { accounts, currentLedgerId, fetchInitialData, fetchTransactionsPage, isLoading, transactions, transactionTotal } = useAppStore();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [trend, setTrend] = useState<TrendDataPoint[]>([]);
  const [topExpenses, setTopExpenses] = useState<RankedTransaction[]>([]);
  const [draft, setDraft] = useState({ amount: "", merchant: "", notes: "", type: "expense" as "expense" | "income" });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    fetchInitialData();
  }, [fetchInitialData]);

  useEffect(() => {
    if (currentLedgerId) {
      fetchTransactionsPage(0, 12);
    }
  }, [currentLedgerId, fetchTransactionsPage]);

  useEffect(() => {
    async function loadStats() {
      if (!currentLedgerId) return;
      const { startTs, endTs } = monthRange();
      const [nextSummary, nextTrend, nextTop] = await Promise.all([
        StatsAPI.getDashboardSummary(currentLedgerId, startTs, endTs),
        StatsAPI.getTrend(currentLedgerId, startTs, endTs, "day"),
        StatsAPI.getTopTransactions(currentLedgerId, startTs, endTs, "expense", 4),
      ]);
      setSummary(nextSummary);
      setTrend(nextTrend);
      setTopExpenses(nextTop);
    }
    loadStats();
  }, [currentLedgerId, transactionTotal]);

  const defaultAccount = accounts.find((account) => account.account_type === "cash") || accounts[0];
  const recentTransactions = useMemo(() => transactions.slice(0, 7), [transactions]);
  const netFlow = summary?.net_cash_flow ?? 0;

  const flowOption = useMemo(() => ({
    color: ["#10b981", "#ef4444"],
    tooltip: { trigger: "axis", backgroundColor: "rgba(15,23,42,0.94)", borderWidth: 0, borderRadius: 8, textStyle: { color: "#fff", fontSize: 13 } },
    grid: { left: 0, right: 0, top: 8, bottom: 0, containLabel: false },
    xAxis: { type: "category", show: false, data: trend.map((item) => item.date.slice(5)) },
    yAxis: { type: "value", show: false },
    series: [
      { name: "收入", type: "line", smooth: true, showSymbol: false, lineStyle: { width: 3 }, areaStyle: { opacity: 0.16 }, data: trend.map((item) => item.income) },
      { name: "支出", type: "line", smooth: true, showSymbol: false, lineStyle: { width: 3 }, areaStyle: { opacity: 0.12 }, data: trend.map((item) => item.expense) },
    ],
  }), [trend]);

  async function quickAdd() {
    if (!currentLedgerId || !defaultAccount || Number(draft.amount) <= 0) return;
    setSaving(true);
    const timestamp = now();
    const transaction: Transaction = {
      id: crypto.randomUUID(),
      ledger_id: currentLedgerId,
      account_id: defaultAccount.id,
      category_id: null,
      transaction_date: timestamp,
      amount: Number(draft.amount),
      transaction_type: draft.type,
      merchant: draft.merchant || null,
      notes: draft.notes || null,
      tags: [],
      is_excluded: false,
      external_source: "manual",
      external_id: null,
      created_at: timestamp,
      updated_at: timestamp,
      deleted_at: null,
    };
    await TransactionAPI.createTransaction(transaction);
    setDraft({ amount: "", merchant: "", notes: "", type: draft.type });
    await fetchTransactionsPage(0, 12);
    setSaving(false);
  }

  if (isLoading) {
    return <div className="screen-state">正在载入</div>;
  }

  return (
    <div className="home-workbench">
      <section className="today-strip">
        <div className="balance-stage">
          <div className="stage-kicker"><WalletCards size={16} /> 总览</div>
          <div className="balance-row">
            <div>
              <span>账户余额</span>
              <strong>{yuan(summary?.net_assets ?? 0)}</strong>
            </div>
            <div className={netFlow >= 0 ? "flow-positive" : "flow-negative"}>
              <span>本月净流动</span>
              <strong>{yuan(netFlow)}</strong>
            </div>
          </div>
          <div className="stage-chart">
            <ReactECharts option={flowOption} style={{ height: 112 }} />
          </div>
        </div>

        <div className="quick-entry-panel">
          <div className="panel-head">
            <h2>快速补记</h2>
            <div className="segmented">
              <button className={draft.type === "expense" ? "active" : ""} onClick={() => setDraft({ ...draft, type: "expense" })}><ArrowUp size={15} />支出</button>
              <button className={draft.type === "income" ? "active" : ""} onClick={() => setDraft({ ...draft, type: "income" })}><ArrowDown size={15} />收入</button>
            </div>
          </div>
          <div className="quick-entry-grid">
            <input className="money-input" inputMode="decimal" placeholder="0.00" value={draft.amount} onChange={(event) => setDraft({ ...draft, amount: event.target.value })} />
            <input className="field" placeholder="商户" value={draft.merchant} onChange={(event) => setDraft({ ...draft, merchant: event.target.value })} />
            <input className="field" placeholder="备注" value={draft.notes} onChange={(event) => setDraft({ ...draft, notes: event.target.value })} />
            <button className="primary-button" onClick={quickAdd} disabled={saving || !draft.amount}><Plus size={17} />记一笔</button>
          </div>
        </div>
      </section>

      <section className="action-lane">
        <ActionCard to="/import" icon={<FileDown size={20} />} title="导入账单" value="预览后入账" />
        <ActionCard to="/transactions" icon={<ListTree size={20} />} title="钱流明细" value={`${transactionTotal} 笔`} />
        <ActionCard to="/search" icon={<Search size={20} />} title="找一笔账" value="组合筛选" />
      </section>

      <section className="home-grid">
        <div className="flow-panel">
          <div className="panel-head">
            <h2>最近钱流</h2>
            <Link to="/transactions">全部</Link>
          </div>
          <div className="flow-list">
            {recentTransactions.length === 0 ? (
              <div className="empty-line">暂无交易</div>
            ) : recentTransactions.map((transaction) => (
              <TransactionRow key={transaction.id} transaction={transaction} />
            ))}
          </div>
        </div>

        <div className="insight-panel">
          <div className="panel-head">
            <h2>本月重点</h2>
            <Link to="/charts">分析</Link>
          </div>
          <div className="focus-list">
            {topExpenses.length === 0 ? (
              <div className="empty-line">暂无支出排行</div>
            ) : topExpenses.map((item, index) => (
              <div className="focus-item" key={item.id}>
                <span>{index + 1}</span>
                <div>
                  <strong>{item.merchant || item.notes || "未命名支出"}</strong>
                  <small>{shortDate(item.transaction_date)} · {item.category_name || "未分类"}</small>
                </div>
                <b>{yuan(item.amount)}</b>
              </div>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}

function ActionCard({ to, icon, title, value }: { to: string; icon: React.ReactNode; title: string; value: string }) {
  return (
    <Link to={to} className="action-card">
      <span>{icon}</span>
      <strong>{title}</strong>
      <small>{value}</small>
    </Link>
  );
}

function TransactionRow({ transaction }: { transaction: Transaction }) {
  const isExpense = transaction.transaction_type === "expense";
  return (
    <div className="flow-row">
      <span className={isExpense ? "flow-dot expense" : "flow-dot income"}>{isExpense ? <ArrowUp size={14} /> : <ArrowDown size={14} />}</span>
      <div className="flow-main">
        <strong>{transaction.merchant || transaction.notes || "未命名交易"}</strong>
        <small>{shortDate(transaction.transaction_date)}{transaction.is_excluded ? " · 不计收支" : ""}</small>
      </div>
      <b className={isExpense ? "amount-expense" : "amount-income"}>{isExpense ? "-" : "+"}{yuan(transaction.amount)}</b>
    </div>
  );
}
