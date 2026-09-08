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
  createdAt?: string;
  updatedAt?: string;
  credit?: PartnerCredit;
}

export interface PartnerCredit {
  id?: string;
  balance: number | string;
  reserved: number | string;
  available?: number | string;
  total?: number | string;
  currency: string;
  updatedAt?: string;
}

export interface PartnerPayload {
  name: string;
  email?: string | null;
  phone?: string | null;
  currency: string;
  parentPartnerId?: string | null;
  status?: PartnerStatus;
}
