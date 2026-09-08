export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthUser {
  id?: number;
  username: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  roleCode?: string;
  isActive?: boolean;
  partnerId?: string | null;
  mustChangePassword?: boolean;
  permissions?: string[];
}

export const Permissions = {
  MANAGE_USERS: 'MANAGE_USERS',
  MANAGE_PARTNERS: 'MANAGE_PARTNERS',
  VIEW_MESSAGES: 'VIEW_MESSAGES',
  VIEW_AUDIT_LOGS: 'VIEW_AUDIT_LOGS',
  VIEW_TRANSACTIONS: 'VIEW_TRANSACTIONS',
  PARTNER_PORTAL: 'PARTNER_PORTAL',
} as const;

export interface LoginPayload {
  token: string;
  user?: AuthUser;
}

export interface SignupRequest {
  partnerName: string;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string | null;
  currency?: string;
}

export interface SignupResult {
  email: string;
  username: string;
  partnerCode: string;
  partnerName: string;
}

export interface OperationReturn<T = LoginPayload> {
  returnCode: number;
  returnMessage?: string;
  returnObject?: T;
}
