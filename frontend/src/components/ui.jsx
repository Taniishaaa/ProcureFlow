import { useEffect } from 'react';
import { formatMoney, statusLabel } from '../utils.js';

export function Money({ value }) {
  return <span className="money">{formatMoney(value)}</span>;
}

const STATUS_TONES = {
  DRAFT: 'gray',
  SUBMITTED: 'blue',
  APPROVED: 'green',
  REJECTED: 'red',
  CANCELLED: 'gray',
  ORDERED: 'purple',
  FULFILLED: 'green',
  ISSUED: 'blue',
  PARTIALLY_RECEIVED: 'amber',
  COMPLETED: 'green',
  SELECTED: 'green',
  ACTIVE: 'green',
  INACTIVE: 'gray',
};

export function StatusBadge({ status, label }) {
  const tone = STATUS_TONES[status] || 'gray';
  return <span className={`badge badge-${tone}`}>{label || statusLabel(status)}</span>;
}

export function PageHeader({ title, subtitle, actions }) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </div>
  );
}

export function Card({ title, actions, children, className = '' }) {
  return (
    <section className={`card ${className}`}>
      {(title || actions) && (
        <div className="card-header">
          {title && <h2>{title}</h2>}
          {actions && <div className="card-actions">{actions}</div>}
        </div>
      )}
      {children}
    </section>
  );
}

export function Alert({ type = 'error', children }) {
  if (!children) return null;
  return (
    <div className={`alert alert-${type}`} role={type === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  );
}

export function Loading({ label = 'Loading…' }) {
  return (
    <div className="loading">
      <span className="spinner" />
      {label}
    </div>
  );
}

export function EmptyState({ children }) {
  return <div className="empty">{children}</div>;
}

export function Field({ label, error, hint, children, required }) {
  return (
    <label className={`field ${error ? 'field-error' : ''}`}>
      <span className="field-label">
        {label}
        {required && <span className="req"> *</span>}
      </span>
      {children}
      {hint && !error && <span className="field-hint">{hint}</span>}
      {error && <span className="field-error-text">{error}</span>}
    </label>
  );
}

export function Pagination({ page, totalPages, totalElements, onPage }) {
  if (!totalPages || totalPages <= 1) {
    return totalElements ? <div className="pagination muted">{totalElements} record(s)</div> : null;
  }
  return (
    <div className="pagination">
      <span className="muted">
        Page {page + 1} of {totalPages} · {totalElements} record(s)
      </span>
      <div className="pagination-buttons">
        <button className="btn btn-sm" disabled={page === 0} onClick={() => onPage(page - 1)}>
          Previous
        </button>
        <button className="btn btn-sm" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
          Next
        </button>
      </div>
    </div>
  );
}

export function Modal({ title, onClose, children, footer, wide = false }) {
  useEffect(() => {
    const handler = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onClose]);

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div
        className={`modal ${wide ? 'modal-wide' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <h2>{title}</h2>
          <button className="icon-btn" onClick={onClose} aria-label="Close">
            ×
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-footer">{footer}</div>}
      </div>
    </div>
  );
}

export function ConfirmModal({ title, message, confirmLabel = 'Confirm', danger = false, busy, onConfirm, onClose }) {
  return (
    <Modal
      title={title}
      onClose={onClose}
      footer={
        <>
          <button className="btn" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button className={`btn ${danger ? 'btn-danger' : 'btn-primary'}`} onClick={onConfirm} disabled={busy}>
            {busy ? 'Working…' : confirmLabel}
          </button>
        </>
      }
    >
      <p>{message}</p>
    </Modal>
  );
}

export function StatCard({ label, value, hint, tone = 'blue' }) {
  return (
    <div className={`stat stat-${tone}`}>
      <div className="stat-label">{label}</div>
      <div className="stat-value">{value}</div>
      {hint && <div className="stat-hint">{hint}</div>}
    </div>
  );
}

export function ProgressBar({ percent }) {
  const value = Math.max(0, Math.min(100, percent || 0));
  return (
    <div className="progress" title={`${value}%`}>
      <div className="progress-fill" style={{ width: `${value}%` }} />
    </div>
  );
}

/** Horizontal bar list used by the reports and admin dashboard. */
export function BarList({ items, format = (v) => v, empty = 'No data for this period.' }) {
  if (!items || items.length === 0) return <EmptyState>{empty}</EmptyState>;
  const max = Math.max(...items.map((i) => Number(i.value)), 1);
  return (
    <ul className="bars">
      {items.map((item) => (
        <li key={item.label}>
          <div className="bars-row">
            <span className="bars-label">{item.label}</span>
            <span className="bars-value">{format(item.value)}</span>
          </div>
          <div className="bars-track">
            <div className="bars-fill" style={{ width: `${(Number(item.value) / max) * 100}%` }} />
          </div>
          {item.note && <div className="bars-note muted">{item.note}</div>}
        </li>
      ))}
    </ul>
  );
}

export function errorText(error) {
  if (!error) return null;
  if (error.fieldErrors) {
    const details = Object.entries(error.fieldErrors)
      .map(([field, message]) => `${field}: ${message}`)
      .join('; ');
    return `${error.message}${details ? ` (${details})` : ''}`;
  }
  return error.message || 'Something went wrong';
}
