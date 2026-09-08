import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { PartnersApi } from '../partners/partners.api';
import { Partner, PartnerCredit } from '../partners/partners.model';

@Component({
  selector: 'app-partner-profile',
  imports: [DatePipe],
  templateUrl: './partner-profile.html',
  styleUrl: './partner-profile.css',
})
export class PartnerProfile implements OnInit {
  private readonly api = inject(PartnersApi);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly partner = signal<Partner | null>(null);

  ngOnInit(): void {
    void this.load();
  }

  refresh(): void {
    void this.load();
  }

  formatMoney(value: number | string | null | undefined, currency?: string | null): string {
    if (value == null || value === '') {
      return '—';
    }
    const amount = typeof value === 'number' ? value : Number(value);
    if (Number.isNaN(amount)) {
      return String(value);
    }
    const code = currency || this.partner()?.currency || this.credit()?.currency || 'UGX';
    try {
      return new Intl.NumberFormat(undefined, {
        style: 'currency',
        currency: code,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(amount);
    } catch {
      return `${code} ${amount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    }
  }

  credit(): PartnerCredit | null {
    return this.partner()?.credit ?? null;
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.partner.set(await this.api.profile());
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'Unable to load partner profile.');
    } finally {
      this.loading.set(false);
    }
  }
}
