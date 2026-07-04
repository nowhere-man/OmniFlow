import { invoke } from "@tauri-apps/api/core";

export interface Ledger {
  id: string;
  name: string;
  budget: number;
  created_at: number;
  updated_at: number;
  deleted_at: number | null;
}

export interface Account {
  id: string;
  name: string;
  account_type: "cash" | "debit" | "credit" | "wallet" | "other";
  balance: number;
  credit_limit: number;
  bill_day: number | null;
  repay_day: number | null;
  created_at: number;
  updated_at: number;
  deleted_at: number | null;
}

export interface Transaction {
  id: string;
  ledger_id: string;
  account_id: string;
  category_id: string | null;
  transaction_date: number;
  amount: number;
  transaction_type: "expense" | "income";
  merchant: string | null;
  notes: string | null;
  tags: string[];
  is_excluded: boolean;
  external_source: string | null;
  external_id: string | null;
  created_at: number;
  updated_at: number;
  deleted_at: number | null;
}

export interface Rule {
  id: string;
  name: string;
  priority: number;
  match_condition: string;
  action: string;
  created_at: number;
  updated_at: number;
  deleted_at: number | null;
}

export interface PeriodicBill {
  id: string;
  name: string;
  amount: number;
  bill_type: "expense" | "income";
  category_id: string | null;
  account_id: string;
  cron_expression: string;
  next_date: number;
  created_at: number;
  updated_at: number;
  deleted_at: number | null;
}

export interface TransactionFilter {
  category_id?: string | null;
  parent_category_id?: string | null;
  account_id?: string | null;
  tag?: string | null;
  start_date?: number | null;
  end_date?: number | null;
  keyword?: string | null;
  min_amount?: number | null;
  max_amount?: number | null;
}

export interface SearchResult {
  transactions: Transaction[];
  total_income: number;
  total_expense: number;
}

export type DuplicateStatus = "new" | "fuzzy" | "absolute";

export interface ImportPreviewItem {
  preview_id: string;
  transaction_date: number;
  transaction_type: "expense" | "income";
  amount: number;
  merchant: string | null;
  notes: string | null;
  category_id: string | null;
  category_hint: string | null;
  account_id: string;
  tags: string[];
  is_excluded: boolean;
  source_platform: string;
  external_id: string | null;
  duplicate_status: DuplicateStatus;
  duplicate_transaction_id: string | null;
  selected: boolean;
}

export const TransactionAPI = {
  listLedgers: async (): Promise<Ledger[]> => {
    return invoke("list_ledgers");
  },

  createLedger: async (ledger: Ledger): Promise<void> => {
    return invoke("create_ledger", { ledger });
  },

  updateLedger: async (ledger: Ledger): Promise<void> => {
    return invoke("update_ledger", { ledger });
  },

  deleteLedger: async (id: string): Promise<void> => {
    return invoke("delete_ledger", { id });
  },
  
  listAccounts: async (): Promise<Account[]> => {
    return invoke("list_accounts");
  },

  createAccount: async (account: Account): Promise<void> => {
    return invoke("create_account", { account });
  },

  updateAccount: async (account: Account): Promise<void> => {
    return invoke("update_account", { account });
  },

  deleteAccount: async (id: string): Promise<void> => {
    return invoke("delete_account", { id });
  },
  
  listTransactions: async (ledgerId: string): Promise<Transaction[]> => {
    return invoke("list_transactions", { ledgerId });
  },
  
  createTransaction: async (transaction: Transaction): Promise<void> => {
    return invoke("create_transaction", { transaction });
  },

  updateTransaction: async (transaction: Transaction): Promise<void> => {
    return invoke("update_transaction", { transaction });
  },

  deleteTransaction: async (id: string): Promise<void> => {
    return invoke("delete_transaction", { id });
  },

  searchTransactions: async (ledgerId: string, filter: TransactionFilter): Promise<SearchResult> => {
    return invoke("search_transactions", { ledgerId, filter });
  },
  
  importBill: async (filePath: string, ledgerId: string, accountId: string): Promise<number> => {
    return invoke("import_bill", { filePath, ledgerId, accountId });
  },

  parseAndPreview: async (filePath: string, ledgerId: string, accountId: string): Promise<ImportPreviewItem[]> => {
    return invoke("parse_and_preview", { filePath, ledgerId, accountId });
  },

  confirmImport: async (ledgerId: string, accountId: string, items: ImportPreviewItem[]): Promise<number> => {
    return invoke("confirm_import", { ledgerId, accountId, items });
  },

  listRules: async (): Promise<Rule[]> => {
    return invoke("list_rules");
  },

  createRule: async (rule: Rule): Promise<void> => {
    return invoke("create_rule", { rule });
  },

  updateRule: async (rule: Rule): Promise<void> => {
    return invoke("update_rule", { rule });
  },

  deleteRule: async (id: string): Promise<void> => {
    return invoke("delete_rule", { id });
  },

  reapplyRules: async (ledgerId: string): Promise<number> => {
    return invoke("reapply_rules", { ledgerId });
  },

  listPeriodicBills: async (): Promise<PeriodicBill[]> => {
    return invoke("list_periodic_bills");
  },

  createPeriodicBill: async (bill: PeriodicBill): Promise<void> => {
    return invoke("create_periodic_bill", { bill });
  },

  updatePeriodicBill: async (bill: PeriodicBill): Promise<void> => {
    return invoke("update_periodic_bill", { bill });
  },

  deletePeriodicBill: async (id: string): Promise<void> => {
    return invoke("delete_periodic_bill", { id });
  },

  generatePendingConfirmations: async (nowTs: number): Promise<unknown[]> => {
    return invoke("generate_pending_confirmations", { nowTs });
  }
};
