export interface SystemUser {
  id: number;
  username: string;
  firstName?: string | null;
  lastName?: string | null;
  email?: string | null;
  roleCode?: string | null;
  partnerId?: string | null;
  isActive?: boolean | null;
  createdAt?: string;
  lastLoggedInAt?: string | null;
}

export interface SystemRole {
  roleCode: string;
  roleName: string;
}

export interface UserPayload {
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  roleCode: string;
  partnerId?: string | null;
  password?: string;
  isActive?: boolean;
}
