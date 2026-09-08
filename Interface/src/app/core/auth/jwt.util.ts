export interface JwtPayload {
  sub?: string;
  role?: string;
  role_code?: string;
  domain?: string;
  permissions?: string[];
  must_change_password?: boolean;
  partner_id?: string;
  exp?: number;
  iat?: number;
}

export function decodeJwt(token: string): JwtPayload | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) {
      return null;
    }

    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const json = decodeUriComponent(decodeBase64(padded));
    return JSON.parse(json) as JwtPayload;
  } catch {
    return null;
  }
}

export function isJwtExpired(token: string, skewMs = 15_000): boolean {
  const payload = decodeJwt(token);
  if (!payload?.exp) {
    return true;
  }
  return payload.exp * 1000 <= Date.now() + skewMs;
}

function decodeBase64(value: string): string {
  const globalAtob = (globalThis as { atob?: (data: string) => string }).atob;
  if (typeof globalAtob === 'function') {
    return globalAtob(value);
  }
  throw new Error('Base64 decoding is not available in this environment.');
}

function decodeUriComponent(value: string): string {
  return decodeURIComponent(
    value
      .split('')
      .map((char) => '%' + char.charCodeAt(0).toString(16).padStart(2, '0'))
      .join(''),
  );
}
