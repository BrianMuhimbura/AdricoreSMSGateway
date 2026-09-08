import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { OperationReturn } from '../auth/auth.models';

@Injectable({ providedIn: 'root' })
export class GatewayApi {
  private readonly http = inject(HttpClient);

  async call<T>(service: string, action: string, body: Record<string, unknown> = {}): Promise<T> {
    try {
      const response = await firstValueFrom(
        this.http.post<OperationReturn<T>>(`${environment.apiBaseUrl}/${service}/${action}/`, body),
      );
      if (response.returnCode !== 0) {
        throw new Error(response.returnMessage || 'Request failed');
      }
      return response.returnObject as T;
    } catch (error) {
      if (error instanceof Error && !(error instanceof HttpErrorResponse)) {
        throw error;
      }
      if (error instanceof HttpErrorResponse) {
        const payload = error.error as OperationReturn | undefined;
        throw new Error(payload?.returnMessage || error.message || 'Request failed');
      }
      throw new Error('Request failed');
    }
  }
}
