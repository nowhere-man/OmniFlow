import { useEffect, useMemo, useState } from "react";
import ReactECharts from "echarts-for-react";
import { endOfMonth, format, startOfMonth, subMonths } from "date-fns";
import { Activity, BarChart3, CalendarRange, Tags } from "lucide-react";
import { useAppStore } from "../../stores/appStore";
import { CategoryBreakdown, ComparisonData, RankedTransaction, StatsAPI, TagStat, TrendDataPoint } from "../../tauri-adapter/stats";
import { yuan } from "../../lib/format";
import { DatePicker } from "../../components/ui/DatePicker";

type Granularity = "day" | "week" | "month" | "year";
type PeriodMode = "week" | "month" | "year" | "range";

export default function ChartsView() {
  const { currentLedgerId } = useAppStore();
  const [periodMode, setPeriodMode] = useState<PeriodMode>("month");
  const [rangeStart, setRangeStart] = useState(format(startOfMonth(new Date()), "yyyy-MM-dd"));
  const [rangeEnd, setRangeEnd] = useState(format(new Date(), "yyyy-MM-dd"));
  const [trend, setTrend] = useState<TrendDataPoint[]>([]);
  const [categories, setCategories] = useState<CategoryBreakdown[]>([]);
  const [tags, setTags] = useState<TagStat[]>([]);
  const [top, setTop] = useState<RankedTransaction[]>([]);
  const [comparison, setComparison] = useState<ComparisonData | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    async function load() {
      if (!currentLedgerId) return;
      setLoading(true);
      const { start, end, previousStart, previousEnd, granularity } = resolvePeriod(periodMode, rangeStart, rangeEnd);
      const startTs = Math.floor(start.getTime() / 1000);
      const endTs = Math.floor(end.getTime() / 1000);
      try {
        const [nextTrend, nextCategories, nextTags, nextTop, nextComparison] = await Promise.all([
          StatsAPI.getTrend(currentLedgerId, startTs, endTs, granularity),
          StatsAPI.getCategoryBreakdown(currentLedgerId, startTs, endTs, "expense"),
          StatsAPI.getTagStats(currentLedgerId, startTs, endTs),
          StatsAPI.getTopTransactions(currentLedgerId, startTs, endTs, "expense", 8),
          StatsAPI.getComparison(
            currentLedgerId,
            Math.floor(startOfMonth(new Date()).getTime() / 1000),
            Math.floor(end.getTime() / 1000),
            Math.floor(previousStart.getTime() / 1000),
            Math.floor(previousEnd.getTime() / 1000),
          ),
        ]);
        setTrend(nextTrend);
        setCategories(nextCategories);
        setTags(nextTags);
        setTop(nextTop);
        setComparison(nextComparison);
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [currentLedgerId, periodMode, rangeStart, rangeEnd]);

  const trendOption = useMemo(() => ({
    color: ["#7fb8ad", "#ee6f6a", "#f2c76e"],
    tooltip: { trigger: "axis", backgroundColor: "rgba(12,12,13,0.94)", borderWidth: 0, borderRadius: 8, textStyle: { color: "#fff", fontSize: 13 }, valueFormatter: (value: number) => yuan(value) },
    legend: { top: 0, right: 0, icon: "roundRect", textStyle: { color: "var(--muted)", fontWeight: 650 } },
    grid: { left: 8, right: 10, top: 46, bottom: 8, containLabel: true },
    xAxis: { type: "category", data: trend.map((item) => item.date), axisTick: { show: false }, axisLine: { lineStyle: { color: "rgba(128,128,128,0.2)" } }, axisLabel: { color: "var(--muted)" } },
    yAxis: { type: "value", splitLine: { lineStyle: { color: "rgba(128,128,128,0.14)" } }, axisLabel: { color: "var(--muted)", formatter: (value: number) => `¥${value}` } },
    series: [
      { name: "收入", type: "bar", data: trend.map((item) => item.income), barMaxWidth: 18, itemStyle: { borderRadius: [7, 7, 2, 2] } },
      { name: "支出", type: "bar", data: trend.map((item) => item.expense), barMaxWidth: 18, itemStyle: { borderRadius: [7, 7, 2, 2] } },
      { name: "净额", type: "line", smooth: true, showSymbol: false, lineStyle: { width: 3 }, data: trend.map((item) => item.income - item.expense) },
    ],
  }), [trend]);

  const categoryOption = useMemo(() => ({
    color: ["#b42318", "#b45309", "#0f766e", "#2563eb", "#7c3aed", "#be185d", "#4d7c0f"],
    tooltip: { trigger: "item", formatter: "{b}<br/>{c} ({d}%)" },
    series: [{
      type: "pie",
      radius: ["48%", "72%"],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: "var(--surface)", borderWidth: 3, borderRadius: 6 },
      data: categories.map((item) => ({ name: item.category_name, value: item.amount })),
    }],
  }), [categories]);

  return (
    <div className="page-stack">
      <section className="page-heading">
        <div>
          <div className="eyebrow">分析</div>
          <h1 className="page-title">财务分析</h1>
        </div>
        <div className="toolbar chart-toolbar">
          {(["week", "month", "year", "range"] as PeriodMode[]).map((value) => (
            <button key={value} className={periodMode === value ? "primary-button" : "ghost-button"} onClick={() => setPeriodMode(value)}>
              {value === "week" ? "周" : value === "month" ? "月" : value === "year" ? "年" : "范围"}
            </button>
          ))}
        </div>
      </section>

      {periodMode === "range" && (
        <section className="panel panel-pad range-picker">
          <label className="range-field">
            <CalendarRange size={16} />开始 
            <div className="range-date-control">
              <DatePicker 
                value={new Date(`${rangeStart}T00:00:00`).getTime() / 1000} 
                onChange={(val) => setRangeStart(val ? format(new Date(val * 1000), "yyyy-MM-dd") : format(new Date(), "yyyy-MM-dd"))} 
              />
            </div>
          </label>
          <label className="range-field">
            <CalendarRange size={16} />结束 
            <div className="range-date-control">
              <DatePicker 
                value={new Date(`${rangeEnd}T00:00:00`).getTime() / 1000} 
                onChange={(val) => setRangeEnd(val ? format(new Date(val * 1000), "yyyy-MM-dd") : format(new Date(), "yyyy-MM-dd"))} 
              />
            </div>
          </label>
        </section>
      )}

      <section className="metric-grid">
        <Insight icon={<Activity size={17} />} label="收入环比" value={`${comparison?.income_change_percent ?? 0}%`} />
        <Insight icon={<Activity size={17} />} label="支出环比" value={`${comparison?.expense_change_percent ?? 0}%`} />
        <Insight icon={<Tags size={17} />} label="活跃标签" value={`${tags.length}`} />
        <Insight icon={<BarChart3 size={17} />} label="支出分类" value={`${categories.length}`} />
      </section>

      <section className="chart-grid">
        <div className="panel panel-pad">
          <h2 className="section-title section-title-spaced">收支趋势</h2>
          {loading ? <div className="chart-state">正在计算图表...</div> : <ReactECharts option={trendOption} style={{ height: 360 }} />}
        </div>
        <div className="panel panel-pad">
          <h2 className="section-title section-title-spaced">支出结构</h2>
          {categories.length === 0 ? <div className="chart-state">暂无分类数据</div> : <ReactECharts option={categoryOption} style={{ height: 360 }} />}
        </div>
      </section>

      <section className="split-grid">
        <div className="panel panel-pad">
          <h2 className="section-title section-title-spaced">标签场景</h2>
          <div className="line-list">
            {tags.slice(0, 8).map((tag) => (
              <div key={tag.tag} className="line-item">
                <span className="line-title">#{tag.tag}</span>
                <span className="line-meta">收 {yuan(tag.income)} / 支 {yuan(tag.expense)}</span>
              </div>
            ))}
            {tags.length === 0 && <div className="compact-empty">暂无标签数据</div>}
          </div>
        </div>
        <div className="panel panel-pad">
          <h2 className="section-title section-title-spaced">支出排行</h2>
          <div className="line-list">
            {top.map((item) => (
              <div key={item.id} className="line-item">
                <span className="line-title truncate">{item.merchant || item.notes || "未命名支出"}</span>
                <strong className="amount-expense">{yuan(item.amount)}</strong>
              </div>
            ))}
            {top.length === 0 && <div className="compact-empty">暂无排行数据</div>}
          </div>
        </div>
      </section>
    </div>
  );
}

function resolvePeriod(periodMode: PeriodMode, rangeStart: string, rangeEnd: string): { start: Date; end: Date; previousStart: Date; previousEnd: Date; granularity: Granularity } {
  const now = new Date();
  if (periodMode === "week") {
    const start = new Date(now);
    start.setDate(now.getDate() - 6);
    start.setHours(0, 0, 0, 0);
    const previousStart = new Date(start);
    previousStart.setDate(start.getDate() - 7);
    const previousEnd = new Date(start);
    previousEnd.setSeconds(-1);
    return { start, end: now, previousStart, previousEnd, granularity: "day" };
  }
  if (periodMode === "year") {
    const start = startOfMonth(subMonths(now, 11));
    const previousStart = startOfMonth(subMonths(start, 12));
    const previousEnd = new Date(start);
    previousEnd.setSeconds(-1);
    return { start, end: endOfMonth(now), previousStart, previousEnd, granularity: "month" };
  }
  if (periodMode === "range") {
    const start = new Date(`${rangeStart}T00:00:00`);
    const end = new Date(`${rangeEnd}T23:59:59`);
    const days = Math.max(1, Math.ceil((end.getTime() - start.getTime()) / 86400000));
    const previousStart = new Date(start);
    previousStart.setDate(start.getDate() - days);
    const previousEnd = new Date(start);
    previousEnd.setSeconds(-1);
    return { start, end, previousStart, previousEnd, granularity: days > 120 ? "month" : days > 21 ? "week" : "day" };
  }
  const end = endOfMonth(now);
  const start = startOfMonth(now);
  const previousStart = startOfMonth(subMonths(now, 1));
  const previousEnd = endOfMonth(subMonths(now, 1));
  return { start, end, previousStart, previousEnd, granularity: "day" };
}

function Insight({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="metric-tile">
      <div className="metric-label">{icon}{label}</div>
      <strong>{value}</strong>
    </div>
  );
}
