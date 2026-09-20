export function formatDate(value?: string | Date | null, locale: string = 'en-US'): string {
  if (!value) return '—';
  try {
    const date = typeof value === 'string' ? new Date(value) : value;
    if (Number.isNaN(date.getTime())) {
      return typeof value === 'string' ? value : '—';
    }
    return new Intl.DateTimeFormat(locale, {
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    }).format(date);
  } catch {
    return typeof value === 'string' ? value : '—';
  }
}

export function formatDateTime(value?: string | Date | null, locale: string = 'en-US'): string {
  if (!value) return '—';
  try {
    const date = typeof value === 'string' ? new Date(value) : value;
    if (Number.isNaN(date.getTime())) {
      return typeof value === 'string' ? value : '—';
    }
    return new Intl.DateTimeFormat(locale, {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: 'numeric',
      minute: 'numeric',
      hour12: true
    }).format(date);
  } catch {
    return typeof value === 'string' ? value : '—';
  }
}

export function toDateTimeLocal(date: Date = new Date(), includeSeconds = false): string {
  if (Number.isNaN(date.getTime())) return '';
  const pad = (n: number) => n.toString().padStart(2, '0');
  const year = date.getFullYear();
  const month = pad(date.getMonth() + 1);
  const day = pad(date.getDate());
  const hours = pad(date.getHours());
  const minutes = pad(date.getMinutes());
  if (includeSeconds) {
    const seconds = pad(date.getSeconds());
    return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
  }
  return `${year}-${month}-${day}T${hours}:${minutes}`;
}

export function toRelativeTime(value?: string | Date | null): string {
  if (!value) return '';
  try {
    const timestamp = typeof value === 'string' ? new Date(value).getTime() : value.getTime();
    if (Number.isNaN(timestamp)) return '';
    const diffMs = Date.now() - timestamp;
    const diffSec = Math.floor(diffMs / 1000);
    if (diffSec < 60) return 'just now';
    const diffMin = Math.floor(diffSec / 60);
    if (diffMin < 60) return `${diffMin}m ago`;
    const diffHours = Math.floor(diffMin / 60);
    if (diffHours < 24) return `${diffHours}h ago`;
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 30) return `${diffDays}d ago`;
    const diffMonths = Math.floor(diffDays / 30);
    if (diffMonths < 12) return `${diffMonths}mo ago`;
    const diffYears = Math.floor(diffDays / 365);
    return `${diffYears}y ago`;
  } catch {
    return '';
  }
}
