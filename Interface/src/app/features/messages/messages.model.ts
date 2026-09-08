export type MessageStatus =
  | 'QUEUED'
  | 'SENDING'
  | 'SENT'
  | 'ACCEPTED'
  | 'DELIVERED'
  | 'FAILED'
  | 'REJECTED'
  | 'EXPIRED';

export interface OutboundMessage {
  id: string;
  partnerId?: string | null;
  partnerCode?: string | null;
  partnerName?: string | null;
  fromAddr?: string | null;
  toAddr: string;
  text: string;
  encoding?: string | null;
  segments?: number | null;
  cost?: number | string | null;
  status: MessageStatus;
  upstreamId?: string | null;
  callbackUrl?: string | null;
  providerResponse?: string | null;
  createdAt?: string;
  updatedAt?: string;
}
