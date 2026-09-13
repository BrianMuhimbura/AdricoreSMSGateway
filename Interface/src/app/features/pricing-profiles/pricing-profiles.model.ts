export type BillingType = 'PER_SMS' | 'PER_SEGMENT';

export interface SmsPricingProfile {
  id: number;
  profileCode: string;
  profileName: string;
  description?: string | null;
  currencyCode: string;
  providerCostPerSms?: number | string;
  topupFeePerSms?: number | string;
  sellingPricePerSms: number | string;
  effectiveCostPerSms?: number | string;
  grossProfitPerSms?: number | string;
  grossMarginPercent?: number | string;
  minimumSmsVolume: number;
  maximumSmsVolume?: number | null;
  billingType: BillingType;
  isDefault: boolean;
  isActive: boolean;
  effectiveFrom?: string;
  effectiveTo?: string | null;
  createdAt?: string;
  updatedAt?: string | null;
}

export type SmsPricingProfileLookup = Pick<
  SmsPricingProfile,
  'id' | 'profileCode' | 'profileName' | 'currencyCode' | 'sellingPricePerSms' | 'isDefault'
>;

export interface SmsPricingProfilePayload {
  profileCode: string;
  profileName: string;
  description?: string | null;
  currencyCode: string;
  providerCostPerSms?: number | null;
  topupFeePerSms?: number | null;
  minimumSmsVolume: number;
  maximumSmsVolume?: number | null;
  billingType: BillingType;
  isDefault: boolean;
  isActive: boolean;
}
