export interface LazyPageEvent {
  first?: number | null;
  rows?: number | null;
  sortField?: string | string[] | null;
  sortOrder?: number | null;
}

export interface PagedListRequest {
  first?: number;
  rows?: number;
  sortField?: string | null;
  sortOrder?: number | null;
  query?: string;
  status?: string;
  isActive?: boolean;
}

export interface PagedListResponse<T> {
  items: T[];
  totalRecords: number;
  first: number;
  rows: number;
  totalCount?: number;
  activeCount?: number;
  inactiveCount?: number;
  deliveredCount?: number;
  failedCount?: number;
  inFlightCount?: number;
  todayCount?: number;
  topupCount?: number;
  consumptionCount?: number;
}

export function sortFieldOf(value: string | string[] | null | undefined): string | undefined {
  if (Array.isArray(value)) {
    return value[0];
  }
  return value ?? undefined;
}

export function fromLazyEvent(
  event: LazyPageEvent,
  defaults: { rows: number; sortField: string; sortOrder: number },
): Pick<PagedListRequest, 'first' | 'rows' | 'sortField' | 'sortOrder'> {
  return {
    first: event.first ?? 0,
    rows: event.rows ?? defaults.rows,
    sortField: sortFieldOf(event.sortField) || defaults.sortField,
    sortOrder: event.sortOrder ?? defaults.sortOrder,
  };
}
