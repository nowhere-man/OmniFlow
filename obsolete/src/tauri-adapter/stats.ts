import { invoke } from "./invoke";

export interface TrendDataPoint {
  date: string;
  income: number;
  expense: number;
}

export interface CategoryBreakdown {
  category_name: string;
  amount: number;
  percent: number;
}

export interface AssetData {
  account_type: string;
  balance: number;
}

export interface ComparisonData {
  current_income: number;
  current_expense: number;
  previous_income: number;
  previous_expense: number;
  income_change_percent: number;
  expense_change_percent: number;
}

export interface TagStat {
  tag: string;
  income: number;
  expense: number;
}

export interface RankedTransaction {
  id: string;
  amount: number;
  merchant: string | null;
  notes: string | null;
  transaction_date: number;
  transaction_type: "expense" | "income";
  category_name: string | null;
}

export interface DashboardSummary {
  income: number;
  expense: number;
  net_cash_flow: number;
  net_assets: number;
  transaction_count: number;
}

export const StatsAPI = {
  getMonthlyTrend: async (ledgerId: string, startTs: number, endTs: number): Promise<TrendDataPoint[]> => {
    return invoke("get_monthly_trend", { ledgerId, startTs, endTs });
  },

  getTrend: async (ledgerId: string, startTs: number, endTs: number, granularity: "day" | "week" | "month" | "year"): Promise<TrendDataPoint[]> => {
    return invoke("get_trend", { ledgerId, startTs, endTs, granularity });
  },
  
  getCategoryBreakdown: async (ledgerId: string, startTs: number, endTs: number, txType: "expense" | "income"): Promise<CategoryBreakdown[]> => {
    return invoke("get_category_breakdown", { ledgerId, startTs, endTs, txType });
  },
  
  getAssetsOverview: async (): Promise<AssetData[]> => {
    return invoke("get_assets_overview");
  },

  getComparison: async (ledgerId: string, currentStart: number, currentEnd: number, previousStart: number, previousEnd: number): Promise<ComparisonData> => {
    return invoke("get_comparison", { ledgerId, currentStart, currentEnd, previousStart, previousEnd });
  },

  getTagStats: async (ledgerId: string, startTs: number, endTs: number): Promise<TagStat[]> => {
    return invoke("get_tag_stats", { ledgerId, startTs, endTs });
  },

  getTopTransactions: async (ledgerId: string, startTs: number, endTs: number, txType: "expense" | "income", limit: number): Promise<RankedTransaction[]> => {
    return invoke("get_top_transactions", { ledgerId, startTs, endTs, txType, limit });
  },

  getDashboardSummary: async (ledgerId: string, startTs: number, endTs: number): Promise<DashboardSummary> => {
    return invoke("get_dashboard_summary", { ledgerId, startTs, endTs });
  }
};
