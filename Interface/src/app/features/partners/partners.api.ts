import { Injectable, inject } from '@angular/core';
import { GatewayApi } from '../../core/api/gateway-api';
import { PagedListRequest, PagedListResponse } from '../../core/api/paged-list';
import { Partner, PartnerPayload, PartnerPayNowRequest, PartnerTopupRequest, PartnerTopupResult } from './partners.model';

export type PartnerLookup = Pick<Partner, 'id' | 'partnerCode' | 'name' | 'status'>;

@Injectable({ providedIn: 'root' })
export class PartnersApi {
  private readonly api = inject(GatewayApi);

  list(request: PagedListRequest = {}) {
    return this.api.call<PagedListResponse<Partner>>('Partners', 'list', { ...request } as Record<string, unknown>);
  }

  lookup() {
    return this.api.call<PartnerLookup[]>('Partners', 'lookup');
  }

  create(payload: PartnerPayload) {
    return this.api.call<Partner>('Partners', 'create', { ...payload });
  }

  update(id: string, payload: Partial<PartnerPayload>) {
    return this.api.call<Partner>('Partners', 'update', { id, ...payload });
  }

  deactivate(id: string) {
    return this.api.call<Partner>('Partners', 'deactivate', { id });
  }

  activate(id: string) {
    return this.api.call<Partner>('Partners', 'activate', { id });
  }

  profile() {
    return this.api.call<Partner>('Partners', 'profile');
  }

  requestTopup(payload: PartnerTopupRequest) {
    return this.api.call<PartnerTopupResult>('Partners', 'requestTopup', { ...payload });
  }

  payNow(payload: PartnerPayNowRequest) {
    return this.api.call<PartnerTopupResult>('Partners', 'payNow', { ...payload });
  }

  paymentStatus(paymentRequestId: string) {
    return this.api.call<PartnerTopupResult>('Partners', 'paymentStatus', { paymentRequestId });
  }
}
