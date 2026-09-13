import { Injectable, inject } from '@angular/core';
import { GatewayApi } from '../../core/api/gateway-api';
import { PagedListRequest, PagedListResponse } from '../../core/api/paged-list';
import { SmsPricingProfile, SmsPricingProfileLookup, SmsPricingProfilePayload } from './pricing-profiles.model';

@Injectable({ providedIn: 'root' })
export class PricingProfilesApi {
  private readonly api = inject(GatewayApi);

  list(request: PagedListRequest = {}) {
    return this.api.call<PagedListResponse<SmsPricingProfile>>('SmsPricingProfiles', 'list', {
      ...request,
    } as Record<string, unknown>);
  }

  lookup() {
    return this.api.call<SmsPricingProfileLookup[]>('SmsPricingProfiles', 'lookup');
  }

  create(payload: SmsPricingProfilePayload) {
    return this.api.call<SmsPricingProfile>('SmsPricingProfiles', 'create', { ...payload });
  }

  update(id: number, payload: Partial<SmsPricingProfilePayload>) {
    return this.api.call<SmsPricingProfile>('SmsPricingProfiles', 'update', { id, ...payload });
  }
}
