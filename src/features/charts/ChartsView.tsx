import { useEffect, useMemo, useState } from "react";
import ReactECharts from "echarts-for-react";
import { endOfMonth, startOfMonth, subMonths } from "date-fns";
import { Activity, BarChart3, Tags } from "lucide-react";
import { useAppStore } from "../../stores/appStore";
import { CategoryBreakdown, ComparisonData, RankedTransaction, StatsAPI, TagStat, TrendDataPoint } from "../../tauri-adapter/stats";
import { yuan } from "../../lib/format";

type Granularity = "day" | "week" | "month" | "year";

export default function ChartsView() {
  const { currentLedgerId } = useAppStore();
  const [granularity, setGranularity] = useState<Granularity>("month");
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
      const end = endOfMonth(new Date());
      const start = granularity === "month" ? startOfMonth(subMonths(end, 11)) : startOfMonth(new Date());
      const previousStart = startOfMonth(subMonths(new Date(), 1));
      const previousEnd = endOfMonth(subMonths(new Date(), 1));
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
  }, [currentLedgerId, granularity]);

  const trendOption = useMemo(() => ({
    color: ["#0f8a5f", "#b42318"],
    tooltip: { trigger: "axis", backgroundColor: "rgba(23,23,23,0.92)", borderWidth: 0, textStyle: { color: "#fff" } },
    legend: { top: 0, right: 0, icon: "roundRect" },
    grid: { left: 8, right: 8, top: 42, bottom: 8, containLabel: true },
    xAxis: { type: "category", data: trend.map((item) => item.date), axisTick: { show: false } },
    yAxis: { type: "value", splitLine: { lineStyle: { color: "rgba(120,113,108,0.18)" } } },
    series: [
      { name: "收入", type: "bar", stack: "flow", data: trend.map((item) => item.income), barMaxWidth: 18, itemStyle: { borderRadius: [6, 6, 0, 0] } },
      { name: "支出", type: "bar", stack: "flow", data: trend.map((item) => item.expense), barMaxWidth: 18, itemStyle: { borderRadius: [6, 6, 0, 0] } },
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
          <div className="eyebrow">analysis</div>
          <h1 className="page-title">图表有叙事感，才值得打开</h1>
        </div>
        <div className="toolbar">
          {(["day", "week", "month", "year"] as Granularity[]).map((value) => (
            <button key={value} className={granularity === value ? "primary-button" : "ghost-button"} onClick={() => setGranularity(value)}>
              {value === "day" ? "日" : value === "week" ? "周" : value === "month" ? "月" : "年"}
            </button>
          ))}
        </div>
      </section>

      <section className="metric-grid">
        <Insight icon={<Activity size={17} />} label="收入环比" value={`${comparison?.income_change_percent ?? 0}%`} />
        <Insight icon={<Activity size={17} />} label="支出环比" value={`${comparison?.expense_change_percent ?? 0}%`} />
        <Insight icon={<Tags size={17} />} label="活跃标签" value={`${tags.length}`} />
        <Insight icon={<BarChart3 size={17} />} label="支出分类" value={`${categories.length}`} />
      </section>

      <section className="grid grid-cols-1 xl:grid-cols-[minmax(0,1.4fr)_minmax(320px,0.9fr)] gap-4">
        <div className="panel panel-pad">
          <h2 className="section-title mb-4">收支趋势</h2>
          {loading ? <div className="py-24 text-center text-[var(--muted)]">正在计算图表...</div> : <ReactECharts option={trendOption} style={{ height: 360 }} />}
        </div>
        <div className="panel panel-pad">
          <h2 className="section-title mb-4">支出结构</h2>
          {categories.length === 0 ? <div className="py-24 text-center text-[var(--muted)]">暂无分类数据</div> : <ReactECharts option={categoryOption} style={{ height: 360 }} />}
        </div>
      </section>

      <section className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div className="panel panel-pad">
          <h2 className="section-title mb-4">标签场景</h2>
          <div className="grid gap-3">
            {tags.slice(0, 8).map((tag) => (
              <div key={tag.tag} className="flex items-center justify-between border-b border-[var(--border)] pb-3 last:border-0">
                <span className="font-semibold">#{tag.tag}</span>
                <span className="text-sm text-[var(--muted)]">收 {yuan(tag.income)} / 支 {yuan(tag.expense)}</span>
              </div>
            ))}
            {tags.length === 0 && <div className="py-10 text-center text-[var(--muted)]">暂无标签数据</div>}
          </div>
        </div>
        <div className="panel panel-pad">
          <h2 className="section-title mb-4">支出排行</h2>
          <div className="grid gap-3">
            {top.map((item) => (
              <div key={item.id} className="flex items-center justify-between border-b border-[var(--border)] pb-3 last:border-0">
                <span className="font-semibold truncate">{item.merchant || item.notes || "未命名支出"}</span>
                <strong className="text-[var(--expense)]">{yuan(item.amount)}</strong>
              </div>
            ))}
            {top.length === 0 && <div className="py-10 text-center text-[var(--muted)]">暂无排行数据</div>}
          </div>
        </div>
      </section>
    </div>
  );
}

function Insight({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="metric-tile">
      <div className="metric-label">{icon}{label}</div>
      <strong>{value}</strong>
    </div>
  );
}
