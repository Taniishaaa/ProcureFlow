import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { useDebounced, useFetch } from '../../hooks.js';
import { Alert, Card, Loading, PageHeader, Pagination, errorText } from '../../components/ui.jsx';
import { RequestTable } from '../../components/tables.jsx';

export default function ApprovedRequests() {
  const { user } = useAuth();
  const [search, setSearch] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [page, setPage] = useState(0);
  const debounced = useDebounced(search);
  const categories = useFetch(() => api.get('/categories'), []);
  const { data, loading, error } = useFetch(
    () => api.get('/requests/approved', { search: debounced, categoryId, page, size: 10 }),
    [debounced, categoryId, page],
  );

  return (
    <>
      <PageHeader title="Approved requests" subtitle="Approved requests that still need sourcing and a purchase order." />
      <Card>
        <div className="filters">
          <input className="search" placeholder="Search…" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} />
          <select value={categoryId} onChange={(e) => { setCategoryId(e.target.value); setPage(0); }}>
            <option value="">All categories</option>
            {(categories.data || []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </div>
        <Alert>{errorText(error)}</Alert>
        {loading && !data ? (
          <Loading />
        ) : (
          <RequestTable
            rows={data?.content}
            actions={(r) => (
              <div className="row" style={{ justifyContent: 'flex-end', flexWrap: 'nowrap' }}>
                <span className="muted nowrap">{r.quotationCount ?? 0} quote(s)</span>
                {user.role === 'PROCUREMENT_OFFICER' && <Link className="btn btn-sm" to={`/quotations/new?requestId=${r.id}`}>Add quote</Link>}
                <Link className="btn btn-sm btn-primary" to={`/requests/${r.id}/compare`}>Compare</Link>
              </div>
            )}
          />
        )}
        {data && <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />}
      </Card>
    </>
  );
}
