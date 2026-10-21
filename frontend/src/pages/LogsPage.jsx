import React, { useEffect, useState } from 'react';
import { api } from '../api';
import { Table } from '../components/UI';

const LOG_LABELS = {
  id: 'ID',
  userId: 'Người thực hiện',
  action: 'Hành động',
  createdAt: 'Thời gian',
  details: 'Chi tiết thao tác',
};

export default function LogsPage({ go }) {
  const [logs, setLogs] = useState([]);

  async function loadLogs() {
    const d = await api('/api/audit-logs?page=1&size=50');
    setLogs(d?.items || d?.content || d?.data || []);
    return d;
  }

  useEffect(() => {
    go(loadLogs);
  }, []);

  return (
    <div className="card">
      <div className="bar">
        <div>
          <h2>Nhật ký bảo mật & vận hành hệ thống</h2>
          <p className="muted">Theo dõi lịch sử truy cập và thay đổi dữ liệu (Yêu cầu quyền QUAN_LY).</p>
        </div>
        <button type="button" onClick={() => go(loadLogs)}>
          Làm mới nhật ký
        </button>
      </div>
      <Table
        rows={logs}
        cols={['id', 'userId', 'action', 'createdAt', 'details']}
        labels={LOG_LABELS}
      />
    </div>
  );
}