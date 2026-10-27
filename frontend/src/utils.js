const money = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function formatMoney(value) {
  return money.format(Number(value || 0));
}

function toDate(value) {
  if (!value) return null;
  // Plain dates (yyyy-mm-dd) are parsed as local dates so they never shift by a day.
  return new Date(value.length === 10 ? `${value}T00:00:00` : value);
}

export function formatDate(value) {
  const d = toDate(value);
  return d ? d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }) : '—';
}

export function formatDateTime(value) {
  const d = toDate(value);
  return d
    ? d.toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
    : '—';
}

export function todayIso() {
  const d = new Date();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${month}-${day}`;
}

export function statusLabel(status) {
  if (!status) return '';
  const text = status.replace(/_/g, ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export const ROLE_LABELS = {
  EMPLOYEE: 'Employee',
  MANAGER: 'Manager',
  PROCUREMENT_OFFICER: 'Procurement Officer',
  ADMIN: 'Administrator',
};

export const REQUEST_STATUSES = ['DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED', 'ORDERED', 'FULFILLED'];
export const ORDER_STATUSES = ['DRAFT', 'ISSUED', 'PARTIALLY_RECEIVED', 'COMPLETED', 'CANCELLED'];

export function daysUntil(dateIso) {
  const d = toDate(dateIso);
  if (!d) return null;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return Math.round((d - today) / 86400000);
}

export function daysSince(value) {
  const d = toDate(value);
  if (!d) return null;
  return Math.floor((Date.now() - d.getTime()) / 86400000);
}
