import { Component, HostListener, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { PLATFORM_ID } from '@angular/core';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-api-docs',
  templateUrl: './api-docs.html',
  styleUrl: './api-docs.css',
})
export class ApiDocs {
  private readonly platformId = inject(PLATFORM_ID);
  readonly copied = signal<string | null>(null);
  private copyTimer: ReturnType<typeof setTimeout> | null = null;

  baseUrl(): string {
    if (isPlatformBrowser(this.platformId) && environment.apiBaseUrl.startsWith('/')) {
      return `${window.location.origin}${environment.apiBaseUrl}`;
    }
    return environment.apiBaseUrl.replace(/\/+$/, '');
  }

  loginExample(): string {
    return `curl -sS ${this.baseUrl()}/Auth/login/ \\
  -H "Content-Type: application/json" \\
  -d '{
    "username": "api.user",
    "password": "your-password"
  }'`;
  }

  tokenExample(): string {
    return `{
  "returnCode": 0,
  "returnMessage": "Login successful",
  "returnObject": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9…",
    "user": {
      "username": "api.user",
      "roleCode": "API_USER"
    }
  }
}`;
  }

  sendExample(): string {
    return `curl -sS ${this.baseUrl()}/SMS/sendSMS/ \\
  -H "Content-Type: application/json" \\
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \\
  -d '{
    "to": "256700000000",
    "text": "Your verification code is 4821"
  }'`;
  }

  batchExample(): string {
    return `curl -sS ${this.baseUrl()}/SMS/sendSMS/ \\
  -H "Content-Type: application/json" \\
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \\
  -d '{
    "to": ["256700000000", "256750000000"],
    "from": "Adricore",
    "text": "Maintenance window starts at 22:00"
  }'`;
  }

  envelopeExample(): string {
    return `{
  "returnCode": 0,
  "returnMessage": "SMS sent successfully",
  "returnObject": {
    "to": "256700000000",
    "messageId": "8f2c…",
    "status": "SENT",
    "segments": 1,
    "cost": 30.00,
    "transactionId": "c91a…",
    "balanceAfter": 9970.00,
    "pricingProfile": {
      "code": "STARTER",
      "name": "Starter Profile",
      "billingType": "PER_SMS",
      "sellingPricePerSms": 30.00
    }
  }
}`;
  }

  async copy(id: string, value: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(value);
      this.copied.set(id);
      if (this.copyTimer) {
        clearTimeout(this.copyTimer);
      }
      this.copyTimer = setTimeout(() => this.copied.set(null), 1800);
    } catch {
      this.copied.set(null);
    }
  }

  downloadPdf(): void {
    const previous = document.title;
    document.title = 'Adricore SMS Gateway API';
    document.body.classList.add('print-api-docs');
    const restore = () => {
      document.title = previous;
      document.body.classList.remove('print-api-docs');
      window.removeEventListener('afterprint', restore);
    };
    window.addEventListener('afterprint', restore);
    window.print();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.copied.set(null);
  }
}
