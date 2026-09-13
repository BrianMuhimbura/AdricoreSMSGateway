export type PartnerStatus = 'active' | 'inactive';

export interface Partner {
  id: string;
  partnerCode: string;
  name: string;
  email?: string | null;
  phone?: string | null;
  status: PartnerStatus;
  currency: string;
  organizationId?: string | null;
  organizationName?: string | null;
  parentPartnerId?: string | null;
  parentPartnerCode?: string | null;
  parentPartnerName?: string | null;
  pricingProfileId?: number | null;
  pricingProfileCode?: string | null;
  pricingProfileName?: string | null;
  pricingProfile?: PartnerPricingProfile | null;
  pricingCatalog?: PartnerPlanCatalog[];
  createdAt?: string;
  updatedAt?: string;
  credit?: PartnerCredit;
}

export interface PartnerPricingProfile {
  id: number;
  profileCode: string;
  profileName: string;
  description?: string | null;
  currencyCode: string;
  sellingPricePerSms: number | string;
  billingType: string;
  minimumSmsVolume?: number;
  maximumSmsVolume?: number | null;
  isDefault?: boolean;
}

export interface PartnerPlanCatalog {
  id: number;
  profileCode: string;
  profileName: string;
  description?: string | null;
  currencyCode: string;
  sellingPricePerSms: number | string;
  billingType: string;
  minimumSmsVolume: number;
  maximumSmsVolume?: number | null;
  isDefault?: boolean;
  minimumCredit: number | string;
  maximumCredit?: number | string | null;
}

export interface PartnerCredit {
  id?: string;
  balance: number | string;
  reserved: number | string;
  available?: number | string;
  total?: number | string;
  pendingTopup?: number | string;
  currency: string;
  updatedAt?: string;
}

export interface PartnerTopupRequest {
  amount: number;
}

export interface PartnerInvoice {
  id: string;
  amount: number | string;
  status: string;
  dueDate?: string;
  createdAt?: string;
  currency?: string;
  partnerCode?: string;
  outstanding?: number | string;
}

export interface PartnerPaymentRequest {
  id: string;
  invoiceId?: string;
  amount: number | string;
  customerPhone?: string;
  externalTransactionId?: string;
  status: string;
  mmTransactionId?: string | null;
  processingNumber?: string | null;
  returnCode?: number | null;
  returnMessage?: string | null;
  requestedAt?: string;
  processedAt?: string | null;
  creditedAt?: string | null;
}

export interface PartnerTopupResult {
  invoiceId?: string;
  amount: number | string;
  outstanding: number | string;
  partnerCode: string;
  currency: string;
  dueDate?: string;
  status?: string;
  createdAt?: string;
  instructions?: string;
  invoice?: PartnerInvoice;
  payment?: PartnerPaymentRequest | null;
}

export interface PartnerPayNowRequest {
  invoiceId: string;
  customerPhone: string;
}

export interface PartnerPayload {
  name: string;
  email?: string | null;
  phone?: string | null;
  currency: string;
  parentPartnerId?: string | null;
  pricingProfileId?: number | null;
  status?: PartnerStatus;
}
