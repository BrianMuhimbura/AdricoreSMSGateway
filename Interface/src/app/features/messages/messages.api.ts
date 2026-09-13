import { Injectable, inject } from '@angular/core';
import { GatewayApi } from '../../core/api/gateway-api';
import { PagedListRequest, PagedListResponse } from '../../core/api/paged-list';
import { OutboundMessage, SmsSendResult } from './messages.model';

@Injectable({ providedIn: 'root' })
export class MessagesApi {
  private readonly api = inject(GatewayApi);

  list(request: PagedListRequest = {}) {
    return this.api.call<PagedListResponse<OutboundMessage>>('Messages', 'list', {
      ...request,
    } as Record<string, unknown>);
  }

  get(id: string) {
    return this.api.call<OutboundMessage>('Messages', 'get', { id });
  }

  testSend(payload: { to: string; text: string; from?: string }) {
    return this.api.call<SmsSendResult>('Messages', 'testSend', { ...payload });
  }

  bulkSend(payload: { to: string[]; text: string; from?: string }) {
    return this.api.call<SmsSendResult>('Messages', 'bulkSend', { ...payload });
  }
}
