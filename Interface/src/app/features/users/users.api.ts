import { Injectable, inject } from '@angular/core';
import { GatewayApi } from '../../core/api/gateway-api';
import { PagedListRequest, PagedListResponse } from '../../core/api/paged-list';
import { SystemRole, SystemUser, UserPayload } from './users.model';

@Injectable({ providedIn: 'root' })
export class UsersApi {
  private readonly api = inject(GatewayApi);

  list(request: PagedListRequest = {}) {
    return this.api.call<PagedListResponse<SystemUser>>('Users', 'list', { ...request } as Record<string, unknown>);
  }

  roles() {
    return this.api.call<SystemRole[]>('Users', 'roles');
  }

  create(payload: UserPayload) {
    return this.api.call<SystemUser>('Users', 'create', { ...payload });
  }

  update(id: number, payload: Partial<UserPayload>) {
    return this.api.call<SystemUser>('Users', 'update', { id, ...payload });
  }
}
