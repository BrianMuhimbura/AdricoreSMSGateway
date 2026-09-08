import { Injectable, inject } from '@angular/core';
import { GatewayApi } from '../../core/api/gateway-api';
import { PagedListRequest, PagedListResponse } from '../../core/api/paged-list';
import { AuditLogEntry } from './audit-logs.model';

@Injectable({ providedIn: 'root' })
export class AuditLogsApi {
  private readonly api = inject(GatewayApi);

  list(request: PagedListRequest = {}) {
    return this.api.call<PagedListResponse<AuditLogEntry>>('AuditLogs', 'list', {
      ...request,
    } as Record<string, unknown>);
  }

  get(id: string) {
    return this.api.call<AuditLogEntry>('AuditLogs', 'get', { id });
  }
}
