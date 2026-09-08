export interface SystemUser {
  id: number;
  username: string;
  firstName?: string | null;
  lastName?: string | null;
  email?: string | null;
  roleCode?: string | null;
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
  password?: string;
  isActive?: boolean;
}
