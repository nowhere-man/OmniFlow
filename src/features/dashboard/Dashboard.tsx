import { useEffect, useMemo, useState } from "react";
import ReactECharts from "echarts-for-react";
import { ArrowDownCircle, ArrowUpCircle, Landmark, ReceiptText, Sparkles } from "lucide-react";
import { useAppStore } from "../../stores/appStore";
import { DashboardSummary, RankedTransaction, StatsAPI, TrendDataPoint } from "../../tauri-adapter/stats";
import { monthRange, shortDate, yuan } from "../../lib/format";

export default function Dashboard() {
  const { ledgers, currentLedgerId, fetchInitialData, isLoading } = useAppStore();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [trend, setTrend] = useState<TrendDataPoint[]>([]);
  const [topExpenses, setTopExpenses] = useState<RankedTransaction[]>([]);
  const [loadingStats, setLoadingStats] = useState(false);

  useEffect(() => {
    fetchInitialData();
  }, [fetchInitialData]);

  useEffect(() => {
    async function load() {
      if (!currentLedgerId) return;
      setLoadingStats(true);
      const { startTs, endTs } = monthRange();
      try {
        const [nextSummary, nextTrend, nextTop] = await Promise.all([
          StatsAPI.getDashboardSummary(currentLedgerId, startTs, endTs),
          StatsAPI.getTrend(currentLedgerId, startTs, endTs, "day"),
          StatsAPI.getTopTransactions(currentLedgerId, startTs, endTs, "expense", 5),
        ]);
        setSummary(nextSummary);
        setTrend(nextTrend);
        setTopExpenses(nextTop);
      } finally {
        setLoadingStats(false);
      }
    }
    load();
  }, [currentLedgerId]);

  const currentLedger = ledgers.find((ledger) => ledger.id === currentLedgerId);
  const chartOption = useMemo(() => ({
    color: ["#62dca3", "#ff8a80"],
    tooltip: {
      trigger: "axis",
      backgroundColor: "rgba(8,12,20,0.95)",
      borderWidth: 0,
      textStyle: { color: "#fff" },
    },
    grid: { left: 8, right: 8, top: 24, bottom: 8, containLabel: true },
    xAxis: {
      type: "category",
      data: trend.map((item) => item.date.slice(5)),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: "rgba(248,251,255,0.58)" },
    },
    yAxis: {
      type: "value",
      axisLabel: { color: "rgba(248,251,255,0.58)" },
      splitLine: { lineStyle: { color: "rgba(255,255,255,0.1)" } },
    },
    series: [
      {
        name: "收入",
        type: "line",
        smooth: true,
        symbolSize: 6,
        areaStyle: { opacity: 0.18 },
        lineStyle: { width: 3 },
        data: trend.map((item) => item.income),
      },
      {
        name: "支出",
        type: "line",
        smooth: true,
        symbolSize: 6,
        areaStyle: { opacity: 0.14 },
        lineStyle: { width: 3 },
        data: trend.map((item) => item.expense),
      },
    ],
  }), [trend]);

  if (isLoading || loadingStats) {
    return <div className="page-stack"><div className="panel panel-pad">正在同步账本状态...</div></div>;
  }

  return (
    <div className="page-stack">
      <section className="dashboard-hero">
        <div className="hero-copy">
          <div>
            <div className="hero-kicker"><Sparkles size={14} /> {currentLedger?.name || "默认账本"}</div>
            <h1 className="hero-title">本月钱流，一眼明白</h1>
          </div>

          <div className="hero-balance">
            <span>账户余额概览</span>
            <strong>{yuan(summary?.net_assets ?? 0)}</strong>
          </div>

          <div className="hero-stat-grid">
            <div className="hero-stat">
              <span>收入</span>
              <strong>{yuan(summary?.income ?? 0)}</strong>
            </div>
            <div className="hero-stat">
              <span>支出</span>
              <strong>{yuan(summary?.expense ?? 0)}</strong>
            </div>
            <div className="hero-stat">
              <span>净现金流</span>
              <strong>{yuan(summary?.net_cash_flow ?? 0)}</strong>
            </div>
          </div>
        </div>

        <div className="hero-chart">
          <ReactECharts option={chartOption} style={{ height: "100%", minHeight: 320 }} />
        </div>
      </section>

      <section className="insight-row">
        <div className="insight-card">
          <div className="metric-label" style={{ color: "var(--asset)" }}><Landmark size={18} /> 资产口径</div>
          <p>这里展示账户余额合计，适合快速确认今天的资金状态。</p>
        </div>
        <div className="insight-card">
          <div className="metric-label" style={{ color: "var(--income)" }}><ArrowDownCircle size={18} /> 收入节奏</div>
          <p>收入曲线和支出曲线分开呈现，月底回看不会被一张默认柱状图糊住。</p>
        </div>
        <div className="insight-card">
          <div className="metric-label" style={{ color: "var(--expense)" }}><ArrowUpCircle size={18} /> 支出提醒</div>
          <p>本月高额支出会自动浮到下面，方便继续钻到明细。</p>
        </div>
      </section>

      <section className="metric-grid">
        <Metric icon={<Landmark size={18} />} label="净资产" value={yuan(summary?.net_assets ?? 0)} tone="asset" />
        <Metric icon={<ArrowDownCircle size={18} />} label="本月收入" value={yuan(summary?.income ?? 0)} tone="income" />
        <Metric icon={<ArrowUpCircle size={18} />} label="本月支出" value={yuan(summary?.expense ?? 0)} tone="expense" />
        <Metric icon={<ReceiptText size={18} />} label="净现金流" value={yuan(summary?.net_cash_flow ?? 0)} tone={(summary?.net_cash_flow ?? 0) >= 0 ? "income" : "expense"} />
      </section>

      <section className="grid grid-cols-1">
        <div className="panel panel-pad">
          <h2 className="section-title mb-4">值得注意的支出</h2>
          <div className="spend-list">
            {topExpenses.length === 0 ? (
              <div className="text-sm text-[var(--muted)] py-10 text-center">本月还没有可分析的支出</div>
            ) : topExpenses.map((item) => (
              <div key={item.id} className="spend-item">
                <div className="flex items-center gap-3 min-w-0">
                  <span className="spend-rank">{topExpenses.indexOf(item) + 1}</span>
                  <div className="min-w-0">
                    <div className="font-semibold truncate">{item.merchant || item.notes || "未命名支出"}</div>
                    <div className="text-xs text-[var(--muted)]">{shortDate(item.transaction_date)} · {item.category_name || "未分类"}</div>
                  </div>
                </div>
                <strong className="text-[var(--expense)]">{yuan(item.amount)}</strong>
              </div>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}

function Metric({ icon, label, value, tone }: { icon: React.ReactNode; label: string; value: string; tone: "income" | "expense" | "asset" }) {
  return (
    <div className="metric-tile">
      <div className="metric-label" style={{ color: `var(--${tone})` }}>{icon}{label}</div>
      <strong>{value}</strong>
    </div>
  );
}
