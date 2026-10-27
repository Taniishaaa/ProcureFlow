import { useState } from 'react';
import { api } from '../api.js';
import { useFetch } from '../hooks.js';
import { Alert, BarList, Card, Loading, Money, PageHeader, StatCard, errorText } from '../components/ui.jsx';
import { formatMoney, statusLabel } from '../utils.js';

function toItems(buckets, label = (b) => b.label) {
  return (buckets || []).map((b) => ({ label: label(b), value: b.amount ?? b.count, note: `${b.count} order(s)` }));
}

export default function Reports() {
  const [range, setRange] = useState({ from: '', to: '' });
  const [applied, setApplied] = useState({ from: '', to: '' });
  const { data, loading, error } = useFetch(
    () => api.get('/reports/summary', { from: applied.from, to: applied.to }),
    [applied.from, applied.to],
  );

  return (
    <>
      <PageHeader title="Reports" subtitle="Spend counts issued, partially received and completed orders by issue date; draft and cancelled orders are excluded." />
      <Card>
        <form
          className="filters"
          onSubmit={(e) => {
            e.preventDefault();
            setApplied(range);
          }}
        >
          <label className="row">From <input type="date" value={range.from} onChange={(e) => setRange({ ...range, from: e.target.value })} /></label>
          <label className="row">To <input type="date" value={range.to} onChange={(e) => setRange({ ...range, to: e.target.value })} /></label>
          <button className="btn btn-primary">Apply</button>
          <button type="button" className="btn" onClick={() => { setRange({ from: '', to: '' }); setApplied({ from: '', to: '' }); }}>Clear</button>
        </form>
      </Card>
      <Alert>{errorText(error)}</Alert>
      {loading && !data && <Loading />}
      {data && (
        <>
          <div className="grid grid-3 mb">
            <StatCard label="Total spend" value={<Money value={data.totalSpend} />} tone="green" />
            <StatCard label="Orders" value={data.orderCount} />
            <StatCard label="Average order value" value={<Money value={data.averageOrderValue} />} tone="purple" />
          </div>
          <div className="grid grid-2 mb">
            <Card title="Spend by category"><BarList items={toItems(data.spendByCategory)} format={formatMoney} /></Card>
            <Card title="Spend by supplier"><BarList items={toItems(data.spendBySupplier)} format={formatMoney} /></Card>
            <Card title="Spend by month"><BarList items={toItems(data.spendByMonth)} format={formatMoney} /></Card>
            <Card title="Orders by status">
              <BarList items={(data.ordersByStatus || []).map((b) => ({ label: statusLabel(b.label), value: b.count }))} />
            </Card>
          </div>
          <Card title="Requests by status">
            <BarList items={(data.requestsByStatus || []).map((b) => ({ label: statusLabel(b.label), value: b.count }))} />
          </Card>
        </>
      )}
    </>
  );
}
