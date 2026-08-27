import React, { useMemo, useState } from "react";

// -------------------------------------------------------------
// [데이터 정의] 더미 근태 데이터 (이번 달)
// 실제 연동 시 출퇴근 태깅 시스템/HR API에서 받아오면 됩니다.
// -------------------------------------------------------------
const STATUS_META = {
  normal: { label: "정상", cls: "at-normal" },
  late: { label: "지각", cls: "at-late" },
  early: { label: "조퇴", cls: "at-early" },
  absent: { label: "결근", cls: "at-absent" },
  leave: { label: "휴가", cls: "at-leave" },
};

const records = [
  { date: "07-01 (화)", clockIn: "08:58", clockOut: "18:02", hours: "9.1h", status: "normal" },
  { date: "07-02 (수)", clockIn: "09:12", clockOut: "18:05", hours: "8.9h", status: "late" },
  { date: "07-03 (목)", clockIn: "08:55", clockOut: "18:00", hours: "9.1h", status: "normal" },
  { date: "07-04 (금)", clockIn: "08:59", clockOut: "16:30", hours: "7.5h", status: "early" },
  { date: "07-07 (월)", clockIn: "-", clockOut: "-", hours: "-", status: "leave" },
  { date: "07-08 (화)", clockIn: "08:57", clockOut: "18:01", hours: "9.1h", status: "normal" },
  { date: "07-09 (수)", clockIn: "08:56", clockOut: "18:03", hours: "9.1h", status: "normal" },
  { date: "07-10 (목)", clockIn: "-", clockOut: "-", hours: "0h", status: "absent" },
  { date: "07-11 (금)", clockIn: "09:05", clockOut: "18:00", hours: "8.9h", status: "late" },
  { date: "07-14 (월)", clockIn: "08:58", clockOut: "18:04", hours: "9.1h", status: "normal" },
  { date: "07-15 (화)", clockIn: "08:59", clockOut: "18:00", hours: "9.0h", status: "normal" },
  { date: "07-16 (수)", clockIn: "08:57", clockOut: "18:02", hours: "9.1h", status: "normal" },
  { date: "07-17 (목)", clockIn: "08:58", clockOut: "18:01", hours: "9.1h", status: "normal" },
  { date: "07-18 (금)", clockIn: "08:56", clockOut: "-", hours: "-", status: "normal" },
];

const FILTERS = [
  { key: "all", label: "전체" },
  { key: "late", label: "지각" },
  { key: "absent", label: "결근" },
  { key: "leave", label: "휴가" },
];

function MyAttendancePage() {
  const [statusFilter, setStatusFilter] = useState("all");

  const filtered = useMemo(
    () => (statusFilter === "all" ? records : records.filter((r) => r.status === statusFilter)),
    [statusFilter]
  );

  const summary = useMemo(() => {
    const worked = records.filter((r) => r.status !== "leave" && r.status !== "absent").length;
    const late = records.filter((r) => r.status === "late").length;
    const absent = records.filter((r) => r.status === "absent").length;
    const leave = records.filter((r) => r.status === "leave").length;
    const totalHours = records.reduce((sum, r) => {
      const h = parseFloat(r.hours);
      return sum + (isNaN(h) ? 0 : h);
    }, 0);
    return { worked, late, absent, leave, totalHours: totalHours.toFixed(1) };
  }, []);

  const styles = `
    .at { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; color: #334155; }

    .at-header { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
    .at-header h2 { margin: 0; font-size: 22px; font-weight: 800; color: #0f172a; }
    .at-header p { margin: 6px 0 0 0; font-size: 13px; color: #64748b; }
    .at-month-badge { font-size: 13px; font-weight: 700; color: #0566d9; background: #eff6ff; padding: 8px 14px; border-radius: 8px; }

    .at-summary-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; margin-bottom: 22px; }
    @media (max-width: 900px) { .at-summary-grid { grid-template-columns: repeat(2, 1fr); } }
    .at-summary-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 16px; }
    .at-summary-card .num { font-size: 22px; font-weight: 800; color: #0f172a; }
    .at-summary-card .lbl { font-size: 11.5px; color: #64748b; font-weight: 600; margin-top: 4px; }
    .at-summary-card.late .num { color: #a16207; }
    .at-summary-card.absent .num { color: #dc2626; }
    .at-summary-card.leave .num { color: #0566d9; }

    .at-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 22px; box-shadow: 0 1px 3px rgba(0,0,0,0.03); }
    .at-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 10px; }
    .at-card-header h3 { margin: 0; font-size: 15px; font-weight: 800; color: #0f172a; }

    .at-filter-row { display: flex; gap: 8px; }
    .at-filter-btn { border: 1.5px solid #e2e8f0; background: #fff; color: #64748b; padding: 6px 13px; border-radius: 999px; font-size: 12px; font-weight: 700; cursor: pointer; }
    .at-filter-btn.active { background: #0566d9; border-color: #0566d9; color: #fff; }

    .at-table { width: 100%; border-collapse: collapse; text-align: left; }
    .at-table th { background: #f8fafc; color: #64748b; font-weight: 700; font-size: 12px; padding: 12px 14px; border-bottom: 1px solid #e2e8f0; }
    .at-table td { padding: 13px 14px; border-bottom: 1px solid #f1f5f9; font-size: 13px; color: #1e293b; }
    .at-table tbody tr:last-child td { border-bottom: none; }
    .at-table tbody tr:hover td { background: #f8fafc; }

    .at-tag { font-size: 11px; font-weight: 700; padding: 4px 10px; border-radius: 12px; white-space: nowrap; }
    .at-normal { background: #f1f5f9; color: #475569; }
    .at-late { background: #fef9c3; color: #a16207; }
    .at-early { background: #dbeafe; color: #1d4ed8; }
    .at-absent { background: #fee2e2; color: #b91c1c; }
    .at-leave { background: #eaf2fc; color: #0566d9; }

    .at-empty { text-align: center; padding: 30px; color: #94a3b8; font-size: 13px; }
  `;

  return (
    <div className="at">
      <style>{styles}</style>

      <div className="at-header">
        <div>
          <h2>🗓 내 근태 조회</h2>
          <p>이번 달 출퇴근 기록과 근무 현황을 확인할 수 있습니다.</p>
        </div>
        <span className="at-month-badge">2026년 7월</span>
      </div>

      <div className="at-summary-grid">
        <div className="at-summary-card">
          <div className="num">{summary.worked}일</div>
          <div className="lbl">정상 출근</div>
        </div>
        <div className="at-summary-card late">
          <div className="num">{summary.late}회</div>
          <div className="lbl">지각</div>
        </div>
        <div className="at-summary-card absent">
          <div className="num">{summary.absent}회</div>
          <div className="lbl">결근</div>
        </div>
        <div className="at-summary-card leave">
          <div className="num">{summary.leave}일</div>
          <div className="lbl">휴가 사용</div>
        </div>
        <div className="at-summary-card">
          <div className="num">{summary.totalHours}h</div>
          <div className="lbl">총 근무 시간</div>
        </div>
      </div>

      <div className="at-card">
        <div className="at-card-header">
          <h3>일별 출퇴근 기록</h3>
          <div className="at-filter-row">
            {FILTERS.map((f) => (
              <button
                key={f.key}
                type="button"
                className={`at-filter-btn ${statusFilter === f.key ? "active" : ""}`}
                onClick={() => setStatusFilter(f.key)}
              >
                {f.label}
              </button>
            ))}
          </div>
        </div>

        <table className="at-table">
          <thead>
            <tr>
              <th>날짜</th>
              <th>출근</th>
              <th>퇴근</th>
              <th>근무시간</th>
              <th>상태</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((r, idx) => (
              <tr key={idx}>
                <td>{r.date}</td>
                <td>{r.clockIn}</td>
                <td>{r.clockOut}</td>
                <td>{r.hours}</td>
                <td>
                  <span className={`at-tag ${STATUS_META[r.status].cls}`}>
                    {STATUS_META[r.status].label}
                  </span>
                </td>
              </tr>
            ))}
            {filtered.length === 0 && (
              <tr>
                <td colSpan="5" className="at-empty">해당 조건의 기록이 없습니다.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default MyAttendancePage;
