import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api.js';
import { useDebounced, useFetch } from '../../hooks.js';
import { Alert, Card, Loading, PageHeader, Pagination, errorText } from '../../components/ui.jsx';
import { RequestTable } from '../../components/tables.jsx';
import { daysSince } from '../../utils.js';

export default function ApprovalQueue() {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const debounced = useDebounced(search);
  const { data, loading, error } = useFetch(
    () => api.get('/requests/pending-approval', { search: debounced, page, size: 10 }),
    [debounced, page],
  );

  return (
    <>
      <PageHeader title="Approval queue" subtitle="Submitted requests waiting for a decision (oldest first)." />
      <Card>
        <div className="filters">
          <input className="search" placeholder="Search by number, title or requester…" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} />
        </div>
        <Alert>{errorText(error)}</Alert>
        {loading && !data ? (
          <Loading />
        ) : (
          <RequestTable
            rows={data?.content}
            linkBase="/approvals"
            actions={(r) => (
              <>
                {daysSince(r.submittedAt) >= 3 && <span className="badge badge-amber" style={{ marginRight: 8 }}>{daysSince(r.submittedAt)}d waiting</span>}
                <Link className="btn btn-sm btn-primary" to={`/approvals/${r.id}`}>Review</Link>
              </>
            )}
          />
        )}
        {data && <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />}
      </Card>
    </>
  );
}
