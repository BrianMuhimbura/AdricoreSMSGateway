export interface AuditLogEntry {
  id: string;
  action: string;
  actorType?: string | null;
  actorId?: string | null;
  actorUsername?: string | null;
  resourceType?: string | null;
  resourceId?: string | null;
  success?: boolean | null;
  summary?: string | null;
  details?: Record<string, unknown> | null;
  createdAt?: string;
}
