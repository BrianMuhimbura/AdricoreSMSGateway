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

export interface SmsSendResult {
  to?: string;
  messageId?: string;
  upstreamId?: string | null;
  status?: MessageStatus | string;
  cost?: number | string;
  success?: boolean;
  error?: string;
  accepted?: number;
  requested?: number;
  charged?: number | string;
  balanceAfter?: number | string;
  messages?: Array<{
    to: string;
    success?: boolean;
    status?: string;
    error?: string;
  }>;
}
