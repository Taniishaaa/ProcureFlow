import { Link } from 'react-router-dom';
import { Money, StatusBadge, EmptyState } from './ui.jsx';
import { formatDate } from '../utils.js';

/** Table of RequestSummary rows. `linkBase` decides where the row link goes. */
export function RequestTable({ rows, linkBase = '/requests', showRequester = true, actions }) {
  if (!rows || rows.length === 0) return <EmptyState>No requests found.</EmptyState>;
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Request</th>
            <th>Title</th>
            {showRequester && <th>Requester</th>}
            <th>Category</th>
            <th className="num">Estimate</th>
            <th>Status</th>
            <th>{rows[0].submittedAt !== undefined ? 'Submitted' : 'Created'}</th>
            {actions && <th />}
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id}>
              <td className="nowrap"><Link to={`${linkBase}/${r.id}`}>{r.requestNumber}</Link></td>
              <td>{r.title}</td>
              {showRequester && <td>{r.requester?.name}</td>}
              <td>{r.category?.name}</td>
              <td className="num"><Money value={r.totalEstimate} /></td>
              <td><StatusBadge status={r.status} /></td>
              <td className="nowrap">{formatDate(r.submittedAt || r.createdAt)}</td>
              {actions && <td className="right">{actions(r)}</td>}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function OrderTable({ rows, showRequester = true }) {
  if (!rows || rows.length === 0) return <EmptyState>No purchase orders found.</EmptyState>;
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>PO number</th>
            <th>Request</th>
            <th>Supplier</th>
            {showRequester && <th>Requester</th>}
            <th className="num">Total</th>
            <th>Status</th>
            <th>Expected</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((o) => (
            <tr key={o.id}>
              <td className="nowrap"><Link to={`/orders/${o.id}`}>{o.poNumber}</Link></td>
              <td>{o.requestNumber} · {o.requestTitle}</td>
              <td>{o.supplier?.name}</td>
              {showRequester && <td>{o.requesterName}</td>}
              <td className="num"><Money value={o.totalAmount} /></td>
              <td><StatusBadge status={o.status} /></td>
              <td className="nowrap">{formatDate(o.expectedDeliveryDate)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
