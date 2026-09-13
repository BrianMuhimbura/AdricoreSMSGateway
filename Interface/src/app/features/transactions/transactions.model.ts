export interface LedgerAccountRef {
  id?: string;
  code?: string | null;
  name?: string | null;
  partnerCode?: string | null;
}

export interface CreditTx {
  id: string;
  partnerId?: string | null;
  partnerCode?: string | null;
  partnerName?: string | null;
  type: string;
  amount: number | string;
  balanceAfter: number | string;
  debitAccount?: LedgerAccountRef | null;
  creditAccount?: LedgerAccountRef | null;
  debitBalanceAfter?: number | string | null;
  creditBalanceAfter?: number | string | null;
  reference?: string | null;
  createdAt?: string;
}
