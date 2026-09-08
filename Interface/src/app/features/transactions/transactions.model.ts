export interface CreditTx {
  id: string;
  partnerId?: string | null;
  partnerCode?: string | null;
  partnerName?: string | null;
  type: string;
  amount: number | string;
  balanceAfter: number | string;
  reference?: string | null;
  createdAt?: string;
}
