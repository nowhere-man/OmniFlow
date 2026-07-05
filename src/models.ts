export type TransactionType = "expense" | "income";

export interface Category {
  id: string;
  name: string;
  category_type: TransactionType;
  parent_id?: string | null;
  icon?: string | null;
  created_at: number;
  updated_at: number;
  deleted_at?: number | null;
}
