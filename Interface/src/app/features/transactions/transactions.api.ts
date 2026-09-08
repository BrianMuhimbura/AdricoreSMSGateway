import { Injectable, inject } from '@angular/core';
import { GatewayApi } from '../../core/api/gateway-api';
import { PagedListRequest, PagedListResponse } from '../../core/api/paged-list';
import { CreditTx } from './transactions.model';

@Injectable({ providedIn: 'root' })
export class TransactionsApi {
  private readonly api = inject(GatewayApi);

  list(request: PagedListRequest = {}) {
    return this.api.call<PagedListResponse<CreditTx>>('Transactions', 'list', {
      ...request,
    } as Record<string, unknown>);
  }
}
