import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api.js';
import { useDebounced, useFetch } from '../../hooks.js';
import { Alert, Card, EmptyState, Loading, Money, PageHeader, Pagination, StatusBadge, errorText } from '../../components/ui.jsx';
import { formatDateTime } from '../../utils.js';

export default function ApprovalHistory() {
  const [decision, setDecision] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const debounced = useDebounced(search);
  const { data, loading, error } = useFetch(
    () => api.get('/approvals', { decision, search: debounced, page, size: 10 }),
    [decision, debounced, page],
  );

  return (
    <>
      <PageHeader title="Approval history" subtitle="Decisions you have made." />
      <Card>
        <div className="filters">
          <input className="search" placeholder="Search by number, title or requester…" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} />
          <select value={decision} onChange={(e) => { setDecision(e.target.value); setPage(0); }}>
            <option value="">All decisions</option>
            <option value="APPROVED">Approved</option>
            <option value="REJECTED">Rejected</option>
          </select>
        </div>
        <Alert>{errorText(error)}</Alert>
        {loading && !data ? <Loading /> : data?.content.length === 0 ? (
          <EmptyState>No decisions found.</EmptyState>
        ) : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Request</th><th>Requester</th><th className="num">Estimate</th><th>Decision</th><th>Comments</th><th>Decided</th></tr></thead>
              <tbody>
                {data?.content.map((a) => (
                  <tr key={a.id}>
                    <td><Link to={`/requests/${a.requestId}`}>{a.requestNumber}</Link> · {a.requestTitle}</td>
                    <td>{a.requesterName}</td>
                    <td className="num"><Money value={a.totalEstimate} /></td>
                    <td><StatusBadge status={a.decision} /></td>
                    <td>{a.comments || <span className="muted">—</span>}</td>
                    <td className="nowrap">{formatDateTime(a.decidedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {data && <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />}
      </Card>
    </>
  );
}
