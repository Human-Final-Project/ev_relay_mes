import React, { useMemo, useState } from "react";

// -------------------------------------------------------------
// [데이터 정의] 더미 전체 사원 근태 데이터
// 실제 연동 시 HR/근태 API에서 사원별 집계 데이터를 받아오면 됩니다.
// -------------------------------------------------------------
const employees = [
  {
    id: "E-1001",
    name: "김철수",
    line: "Line A",
    shift: "오전",
    normalDays: 18,
    late: 1,
    absent: 0,
    leave: 1,
    totalHours: "162.4h",
    daily: [
      { date: "07-16 (수)", clockIn: "08:57", clockOut: "18:02", status: "정상" },
      { date: "07-17 (목)", clockIn: "08:58", clockOut: "18:01", status: "정상" },
      { date: "07-18 (금)", clockIn: "08:56", clockOut: "-", status: "정상" },
    ],
  },
  {
    id: "E-1002",
    name: "박영희",
    line: "Line B",
    shift: "오후",
    normalDays: 15,
    late: 3,
    absent: 1,
    leave: 0,
    totalHours: "148.2h",
    daily: [
      { date: "07-16 (수)", clockIn: "14:10", clockOut: "22:05", status: "지각" },
      { date: "07-17 (목)", clockIn: "13:58", clockOut: "22:00", status: "정상" },
      { date: "07-18 (금)", clockIn: "-", clockOut: "-", status: "결근" },
    ],
  },
  {
    id: "E-1003",
    name: "이민수",
    line: "Line A",
    shift: "오전",
    normalDays: 19,
    late: 0,
    absent: 0,
    leave: 0,
    totalHours: "171.0h",
    daily: [
      { date: "07-16 (수)", clockIn: "08:55", clockOut: "18:00", status: "정상" },
      { date: "07-17 (목)", clockIn: "08:56", clockOut: "18:02", status: "정상" },
      { date: "07-18 (금)", clockIn: "08:54", clockOut: "-", status: "정상" },
    ],
  },
  {
    id: "E-1004",
    name: "최수진",
    line: "Line C",
    shift: "야간",
    normalDays: 14,
    late: 2,
    absent: 2,
    leave: 1,
    totalHours: "132.6h",
    daily: [
      { date: "07-16 (수)", clockIn: "22:15", clockOut: "06:05", status: "지각" },
      { date: "07-17 (목)", clockIn: "-", clockOut: "-", status: "결근" },
      { date: "07-18 (금)", clockIn: "22:02", clockOut: "-", status: "정상" },
    ],
  },
];

const LINE_OPTIONS = ["전체", "Line A", "Line B", "Line C"];

const STATUS_TAG = {
  "정상": "aa-tag-normal",
  "지각": "aa-tag-late",
  "결근": "aa-tag-absent",
  "휴가": "aa-tag-leave",
};

function AdminAttendancePage() {
  const [searchName, setSearchName] = useState("");
  const [lineFilter, setLineFilter] = useState("전체");
  const [expandedId, setExpandedId] = useState(null);

  const filtered = useMemo(() => {
    return employees.filter((e) => {
      const matchName = e.name.toLowerCase().includes(searchName.trim().toLowerCase());
      const matchLine = lineFilter === "전체" || e.line === lineFilter;
      return matchName && matchLine;
    });
  }, [searchName, lineFilter]);

  const teamSummary = useMemo(() => {
    const totalLate = employees.reduce((s, e) => s + e.late, 0);
    const totalAbsent = employees.reduce((s, e) => s + e.absent, 0);
    const totalLeave = employees.reduce((s, e) => s + e.leave, 0);
    return { count: employees.length, totalLate, totalAbsent, totalLeave };
  }, []);

  const styles = `
    .aa { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; color: #334155; }

    .aa-header { margin-bottom: 20px; }
    .aa-header h2 { margin: 0; font-size: 22px; font-weight: 800; color: #0f172a; }
    .aa-header p { margin: 6px 0 0 0; font-size: 13px; color: #64748b; }

    .aa-summary-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 20px; }
    @media (max-width: 900px) { .aa-summary-grid { grid-template-columns: repeat(2, 1fr); } }
    .aa-summary-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 16px; }
    .aa-summary-card .num { font-size: 22px; font-weight: 800; color: #0f172a; }
    .aa-summary-card .lbl { font-size: 11.5px; color: #64748b; font-weight: 600; margin-top: 4px; }
    .aa-summary-card.late .num { color: #a16207; }
    .aa-summary-card.absent .num { color: #dc2626; }
    .aa-summary-card.leave .num { color: #0566d9; }

    .aa-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 22px; box-shadow: 0 1px 3px rgba(0,0,0,0.03); }
    .aa-toolbar { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
    .aa-search { flex: 1; min-width: 180px; box-sizing: border-box; padding: 10px 12px; border: 1px solid #cbd5e1; border-radius: 8px; font-size: 13px; outline: none; }
    .aa-search:focus { border-color: #0566d9; }
    .aa-select { padding: 10px 12px; border: 1px solid #cbd5e1; border-radius: 8px; font-size: 13px; outline: none; background: #fff; }

    .aa-table { width: 100%; border-collapse: collapse; text-align: left; }
    .aa-table th { background: #f8fafc; color: #64748b; font-weight: 700; font-size: 12px; padding: 12px 14px; border-bottom: 1px solid #e2e8f0; }
    .aa-table td { padding: 13px 14px; border-bottom: 1px solid #f1f5f9; font-size: 13px; color: #1e293b; }
    .aa-table tbody tr:hover td { background: #f8fafc; }
    .aa-name-row { cursor: pointer; }
    .aa-name { font-weight: 700; color: #0f172a; }
    .aa-sub { font-size: 11.5px; color: #94a3b8; }

    .aa-num-late { color: #a16207; font-weight: 700; }
    .aa-num-absent { color: #dc2626; font-weight: 700; }

    .aa-detail-row td { background: #f8fafc; padding: 16px 20px; }
    .aa-detail-title { font-size: 12.5px; font-weight: 700; color: #64748b; margin-bottom: 10px; }
    .aa-mini-table { width: 100%; border-collapse: collapse; }
    .aa-mini-table th { font-size: 11px; color: #94a3b8; font-weight: 700; padding: 6px 10px; text-align: left; }
    .aa-mini-table td { font-size: 12.5px; padding: 8px 10px; background: #ffffff; border-top: 1px solid #e2e8f0; }

    .aa-tag { font-size: 11px; font-weight: 700; padding: 4px 10px; border-radius: 12px; white-space: nowrap; }
    .aa-tag-normal { background: #f1f5f9; color: #475569; }
    .aa-tag-late { background: #fef9c3; color: #a16207; }
    .aa-tag-absent { background: #fee2e2; color: #b91c1c; }
    .aa-tag-leave { background: #eaf2fc; color: #0566d9; }

    .aa-empty { text-align: center; padding: 30px; color: #94a3b8; font-size: 13px; }
    .aa-chevron { font-size: 16px; color: #94a3b8; }
  `;

  return (
    <div className="aa">
      <style>{styles}</style>

      <div className="aa-header">
        <h2>👥 전체 사원 근태 조회</h2>
        <p>라인별 사원들의 이번 달 근태 현황을 한눈에 확인하세요.</p>
      </div>

      <div className="aa-summary-grid">
        <div className="aa-summary-card">
          <div className="num">{teamSummary.count}명</div>
          <div className="lbl">조회 대상 사원</div>
        </div>
        <div className="aa-summary-card late">
          <div className="num">{teamSummary.totalLate}회</div>
          <div className="lbl">전체 지각 건수</div>
        </div>
        <div className="aa-summary-card absent">
          <div className="num">{teamSummary.totalAbsent}회</div>
          <div className="lbl">전체 결근 건수</div>
        </div>
        <div className="aa-summary-card leave">
          <div className="num">{teamSummary.totalLeave}일</div>
          <div className="lbl">전체 휴가 사용</div>
        </div>
      </div>

      <div className="aa-card">
        <div className="aa-toolbar">
          <input
            type="text"
            className="aa-search"
            placeholder="사원명 검색..."
            value={searchName}
            onChange={(e) => setSearchName(e.target.value)}
          />
          <select className="aa-select" value={lineFilter} onChange={(e) => setLineFilter(e.target.value)}>
            {LINE_OPTIONS.map((l) => (
              <option key={l} value={l}>{l}</option>
            ))}
          </select>
        </div>

        <table className="aa-table">
          <thead>
            <tr>
              <th>사원</th>
              <th>라인 / 시프트</th>
              <th>정상 출근</th>
              <th>지각</th>
              <th>결근</th>
              <th>휴가</th>
              <th>총 근무시간</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((e) => (
              <React.Fragment key={e.id}>
                <tr className="aa-name-row" onClick={() => setExpandedId(expandedId === e.id ? null : e.id)}>
                  <td>
                    <span className="aa-chevron">{expandedId === e.id ? "▾" : "▸"}</span>{" "}
                    <span className="aa-name">{e.name}</span>
                    <div className="aa-sub">{e.id}</div>
                  </td>
                  <td>{e.line} · {e.shift}</td>
                  <td>{e.normalDays}일</td>
                  <td className={e.late > 0 ? "aa-num-late" : ""}>{e.late}회</td>
                  <td className={e.absent > 0 ? "aa-num-absent" : ""}>{e.absent}회</td>
                  <td>{e.leave}일</td>
                  <td>{e.totalHours}</td>
                </tr>
                {expandedId === e.id && (
                  <tr className="aa-detail-row">
                    <td colSpan="7">
                      <div className="aa-detail-title">최근 출퇴근 기록</div>
                      <table className="aa-mini-table">
                        <thead>
                          <tr>
                            <th>날짜</th>
                            <th>출근</th>
                            <th>퇴근</th>
                            <th>상태</th>
                          </tr>
                        </thead>
                        <tbody>
                          {e.daily.map((d, idx) => (
                            <tr key={idx}>
                              <td>{d.date}</td>
                              <td>{d.clockIn}</td>
                              <td>{d.clockOut}</td>
                              <td><span className={`aa-tag ${STATUS_TAG[d.status]}`}>{d.status}</span></td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </td>
                  </tr>
                )}
              </React.Fragment>
            ))}
            {filtered.length === 0 && (
              <tr>
                <td colSpan="7" className="aa-empty">검색 결과가 없습니다.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default AdminAttendancePage;
