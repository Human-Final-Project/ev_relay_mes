import React, { useEffect, useState } from "react";
import MesApi from "../api/MesApi";
import { getEmployees, saveEmployees, subscribeEmployees } from "../utils/employeeStore";

// -------------------------------------------------------------
// [데이터 정의]
// -------------------------------------------------------------
const DEPT_OPTIONS = ["경영지원", "생산관리", "생산1팀", "생산2팀", "품질관리", "자재관리"];
const LINE_OPTIONS = ["사무실 (생산 외)", "Line A", "Line B", "Line C"];

const STATUS_CLASS = { "재직중": "status-active", "휴직": "status-leave", "퇴직": "status-retired" };

const emptyForm = { id: "", name: "", email: "", dept: DEPT_OPTIONS[0], phone: "", status: "재직중", line: LINE_OPTIONS[0], role: "user" };

// 임시 비밀번호 생성 (Mock)
// 실제로는 서버가 생성해서 응답으로 내려줘야 합니다 (클라이언트에서 만든 값을 신뢰하면 안 됨).
function generateTempPassword() {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
  let result = "";
  for (let i = 0; i < 10; i += 1) {
    result += chars[Math.floor(Math.random() * chars.length)];
  }
  return result;
}

// -------------------------------------------------------------
// [CSS 스타일 정의] - 프로젝트 공통 톤(#0566d9 포인트 컬러)에 맞춤
// -------------------------------------------------------------
const styles = `
  .admin-container { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; color: #334155; padding: 8px; }

  .admin-header-row { display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: 16px; margin-bottom: 20px; }
  .admin-title-area h2 { margin: 0; font-size: 22px; color: #0f172a; font-weight: 800; display: flex; align-items: center; gap: 8px; }
  .admin-title-area p { margin: 6px 0 0 0; font-size: 13px; color: #64748b; font-weight: 600; }

  .admin-toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
  .search-box { border: 1px solid #cbd5e1; padding: 9px 14px; border-radius: 8px; font-size: 13px; width: 240px; outline: none; transition: border-color 0.2s; }
  .search-box:focus { border-color: #0566d9; }
  .btn-primary { background: #0566d9; color: #ffffff; border: none; padding: 10px 18px; border-radius: 8px; font-size: 13px; font-weight: 700; cursor: pointer; transition: 0.2s; white-space: nowrap; }
  .btn-primary:hover { opacity: 0.85; }
  .btn-bulk-delete { background: #fee2e2; color: #dc2626; border: none; padding: 9px 14px; border-radius: 8px; font-size: 13px; font-weight: 700; cursor: pointer; white-space: nowrap; }
  .btn-bulk-delete:hover { background: #fca5a5; }

  .table-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.02); overflow-x: auto; }
  .emp-table { width: 100%; border-collapse: collapse; text-align: left; min-width: 860px; }
  .emp-table th { background: #f1f5f9; color: #475569; font-weight: 700; font-size: 12px; padding: 14px 16px; border-bottom: 1px solid #e2e8f0; white-space: nowrap; }
  .emp-table td { padding: 14px 16px; border-bottom: 1px solid #e2e8f0; font-size: 13px; vertical-align: middle; }
  .emp-table tbody tr:last-child td { border-bottom: none; }
  .emp-table tbody tr:hover td { background: #f8fafc; }
  .emp-id { font-weight: 700; color: #0f172a; }

  .profile-cell { display: flex; align-items: center; gap: 12px; }
  .avatar-circle { width: 36px; height: 36px; border-radius: 50%; background: #e2e8f0; color: #0f172a; display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 14px; flex-shrink: 0; }
  .emp-name { font-weight: 700; color: #0f172a; display: flex; align-items: center; gap: 6px; }
  .emp-email { font-size: 12px; color: #94a3b8; margin-top: 2px; }

  .badge-admin { background: #dbeafe; color: #0566d9; padding: 2px 7px; border-radius: 6px; font-size: 10px; font-weight: 700; }

  .status-badge { font-size: 11px; font-weight: 700; padding: 4px 10px; border-radius: 12px; display: inline-block; }
  .status-active { background: #dcfce7; color: #16803d; }
  .status-leave { background: #fef9c3; color: #a16207; }
  .status-retired { background: #f1f5f9; color: #64748b; }

  .row-actions { display: flex; align-items: center; justify-content: center; gap: 6px; }
  .btn-icon-action { background: none; border: 1px solid #e2e8f0; font-size: 13px; cursor: pointer; padding: 6px 8px; border-radius: 6px; line-height: 1; color: #475569; }
  .btn-icon-action:hover { background: #f1f5f9; }
  .btn-icon-action.danger { color: #dc2626; border-color: #fecaca; }
  .btn-icon-action.danger:hover { background: #fee2e2; }
  .btn-icon-action.key { color: #a16207; border-color: #fde68a; }
  .btn-icon-action.key:hover { background: #fef9c3; }

  .empty-row td { text-align: center; padding: 40px; color: #94a3b8; font-size: 13px; }

  .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(15, 23, 42, 0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; }
  .modal-content { background: #ffffff; width: 420px; max-width: 90vw; border-radius: 12px; padding: 24px; box-shadow: 0 10px 25px rgba(0,0,0,0.1); max-height: 88vh; overflow-y: auto; }
  .modal-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
  .modal-header h3 { margin: 0; font-size: 17px; color: #0f172a; }
  .btn-close { background: none; border: none; font-size: 18px; cursor: pointer; color: #64748b; }

  .form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  .form-group { display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
  .form-group label { font-size: 12px; font-weight: 700; color: #334155; }
  .form-group input, .form-group select { padding: 10px; border: 1px solid #cbd5e1; border-radius: 8px; outline: none; font-size: 13px; background: #ffffff; }
  .form-group input:focus, .form-group select:focus { border-color: #0566d9; }

  .submit-btn { width: 100%; padding: 12px; background: #0566d9; color: white; border: none; border-radius: 8px; font-weight: 700; cursor: pointer; transition: 0.2s; font-size: 13px; margin-top: 6px; }
  .submit-btn:hover { opacity: 0.85; }

  /* 임시 비밀번호 발급 모달 */
  .temp-pw-target { font-size: 13px; color: #64748b; margin-bottom: 16px; }
  .temp-pw-target b { color: #0f172a; }
  .temp-pw-box { display: flex; align-items: center; gap: 8px; background: #f8fafc; border: 1.5px dashed #cbd5e1; border-radius: 10px; padding: 14px 16px; margin-bottom: 14px; }
  .temp-pw-value { flex: 1; font-family: "JetBrains Mono", monospace; font-size: 18px; font-weight: 800; color: #0f172a; letter-spacing: 0.03em; }
  .btn-copy { background: #0566d9; color: #fff; border: none; padding: 8px 14px; border-radius: 8px; font-size: 12px; font-weight: 700; cursor: pointer; white-space: nowrap; }
  .btn-copy:hover { opacity: 0.85; }
  .btn-copy.copied { background: #16803d; }

  .temp-pw-warning { display: flex; gap: 8px; background: #fff7ed; border: 1px solid #fed7aa; border-radius: 8px; padding: 12px 14px; font-size: 12px; color: #9a3412; line-height: 1.5; margin-bottom: 6px; }
  .temp-pw-warning .icon { flex-shrink: 0; }
`;

function AdminEmployeePage() {
  const [employees, setEmployees] = useState([]);

  useEffect(() => {
    setEmployees(getEmployees());
    const unsubscribe = subscribeEmployees(setEmployees);
    return unsubscribe;
  }, []);
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedIds, setSelectedIds] = useState([]);
  const [showModal, setShowModal] = useState(false);
  const [modalMode, setModalMode] = useState("add");
  const [form, setForm] = useState(emptyForm);

  // ---- 임시 비밀번호 발급 모달 상태 ----
  // 발급된 비밀번호는 컴포넌트 상태에만 잠깐 담아두고, 모달을 닫으면 화면 어디에도 남지 않습니다.
  const [tempPasswordInfo, setTempPasswordInfo] = useState(null); // { empId, name, password }
  const [isCopied, setIsCopied] = useState(false);

  const filteredEmployees = employees.filter((emp) => {
    const term = searchTerm.trim().toLowerCase();
    if (!term) return true;
    return (
      emp.name.toLowerCase().includes(term) ||
      emp.dept.toLowerCase().includes(term) ||
      emp.id.toLowerCase().includes(term)
    );
  });

  const allVisibleSelected =
    filteredEmployees.length > 0 && filteredEmployees.every((emp) => selectedIds.includes(emp.id));

  const toggleSelectAll = () => {
    if (allVisibleSelected) {
      setSelectedIds(selectedIds.filter((id) => !filteredEmployees.some((emp) => emp.id === id)));
    } else {
      const visibleIds = filteredEmployees.map((emp) => emp.id);
      setSelectedIds([...new Set([...selectedIds, ...visibleIds])]);
    }
  };

  const toggleSelectOne = (id) => {
    setSelectedIds((prev) => (prev.includes(id) ? prev.filter((v) => v !== id) : [...prev, id]));
  };

  const openAddModal = () => {
    setModalMode("add");
    setForm(emptyForm);
    setShowModal(true);
  };

  const openEditModal = (emp) => {
    setModalMode("edit");
    setForm({ ...emp });
    setShowModal(true);
  };

  const closeModal = () => setShowModal(false);

  const handleFormChange = (e) => {
    const { name, value } = e.target;
    setForm({ ...form, [name]: value });
  };

  const generateNextId = () => {
    const nums = employees
      .map((emp) => {
        const match = emp.id.match(/^EMP(\d+)$/);
        return match ? parseInt(match[1], 10) : null;
      })
      .filter((n) => n !== null);
    const next = nums.length > 0 ? Math.max(...nums) + 1 : 26001;
    return `EMP${next}`;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!form.name || !form.dept) {
      alert("이름과 부서는 필수 입력 항목입니다.");
      return;
    }

    if (modalMode === "add") {
      const newEmp = { ...form, id: generateNextId() };
      const next = [...employees, newEmp];
      setEmployees(next);
      saveEmployees(next);
      alert("신규 사원이 등록되었습니다.");
    } else {
      const next = employees.map((emp) => (emp.id === form.id ? { ...form } : emp));
      setEmployees(next);
      saveEmployees(next);
      alert("사원 정보가 수정되었습니다.");
    }
    setShowModal(false);
  };

  const handleDelete = (id) => {
    if (window.confirm(`사번 ${id}을(를) 삭제하시겠습니까?`)) {
      const next = employees.filter((emp) => emp.id !== id);
      setEmployees(next);
      saveEmployees(next);
      setSelectedIds(selectedIds.filter((v) => v !== id));
    }
  };

  const handleBulkDelete = () => {
    if (selectedIds.length === 0) return;
    if (window.confirm(`선택한 ${selectedIds.length}명의 사원을 삭제하시겠습니까?`)) {
      const next = employees.filter((emp) => !selectedIds.includes(emp.id));
      setEmployees(next);
      saveEmployees(next);
      setSelectedIds([]);
    }
  };

  // ---- 임시 비밀번호 발급 ----
  const handleIssueTempPassword = async (emp) => {
    if (!window.confirm(`${emp.name}(${emp.id}) 사원에게 임시 비밀번호를 발급하시겠습니까?\n발급 시 해당 사원의 기존 로그인 세션은 모두 종료됩니다.`)) {
      return;
    }

    try {
      // TODO: 백엔드 연동 시 아래 mock 대신 이 호출을 그대로 사용하세요.
      // const res = await MesApi.issueTempPassword(emp.id);
      // const tempPassword = res.data.tempPassword;
      const tempPassword = generateTempPassword();

      // 서버는 임시 비밀번호 발급 시 해당 사원의 세션 버전을 올려
      // 기존에 로그인되어 있던 모든 기기를 로그아웃 처리해야 합니다.
      setTempPasswordInfo({ empId: emp.id, name: emp.name, password: tempPassword });
      setIsCopied(false);
    } catch (err) {
      alert("임시 비밀번호 발급에 실패했습니다.");
    }
  };

  const handleCopyTempPassword = async () => {
    if (!tempPasswordInfo) return;
    try {
      await navigator.clipboard.writeText(tempPasswordInfo.password);
      setIsCopied(true);
      setTimeout(() => setIsCopied(false), 1500);
    } catch {
      alert("클립보드 복사에 실패했습니다. 직접 선택해서 복사해주세요.");
    }
  };

  const closeTempPasswordModal = () => {
    // 모달을 닫으면 발급된 비밀번호 값은 상태에서 완전히 제거됩니다 (다시 볼 수 없음).
    setTempPasswordInfo(null);
    setIsCopied(false);
  };

  return (
    <div className="admin-container">
      <style>{styles}</style>

      <div className="admin-header-row">
        <div className="admin-title-area">
          <h2>👤 사원 관리 (Employee)</h2>
          <p>총 인원: {employees.length} 명</p>
        </div>

        <div className="admin-toolbar">
          {selectedIds.length > 0 && (
            <button className="btn-bulk-delete" onClick={handleBulkDelete}>
              🗑 선택 삭제 ({selectedIds.length})
            </button>
          )}
          <input
            type="text"
            className="search-box"
            placeholder="이름, 부서, 사번 검색..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
          <button className="btn-primary" onClick={openAddModal}>+ 신규 등록</button>
        </div>
      </div>

      <div className="table-card">
        <table className="emp-table">
          <thead>
            <tr>
              <th style={{ width: "40px" }}>
                <input type="checkbox" checked={allVisibleSelected} onChange={toggleSelectAll} />
              </th>
              <th>사번</th>
              <th>프로필 (이름/이메일)</th>
              <th>부서/직책</th>
              <th>연락처</th>
              <th>상태</th>
              <th>담당 라인</th>
              <th style={{ textAlign: "center" }}>관리</th>
            </tr>
          </thead>
          <tbody>
            {filteredEmployees.map((emp) => (
              <tr key={emp.id}>
                <td>
                  <input
                    type="checkbox"
                    checked={selectedIds.includes(emp.id)}
                    onChange={() => toggleSelectOne(emp.id)}
                  />
                </td>
                <td className="emp-id">#{emp.id}</td>
                <td>
                  <div className="profile-cell">
                    <div className="avatar-circle">{emp.name.charAt(0)}</div>
                    <div>
                      <div className="emp-name">
                        {emp.name}
                        {emp.role === "admin" && <span className="badge-admin">관리자</span>}
                      </div>
                      <div className="emp-email">{emp.email}</div>
                    </div>
                  </div>
                </td>
                <td>{emp.dept}</td>
                <td>📞 {emp.phone}</td>
                <td>
                  <span className={`status-badge ${STATUS_CLASS[emp.status]}`}>{emp.status}</span>
                </td>
                <td>{emp.line}</td>
                <td style={{ textAlign: "center" }}>
                  <div className="row-actions">
                    <button
                      className="btn-icon-action key"
                      title="임시 비밀번호 발급"
                      onClick={() => handleIssueTempPassword(emp)}
                    >
                      🔑
                    </button>
                    <button
                      className="btn-icon-action"
                      title="수정"
                      onClick={() => openEditModal(emp)}
                    >
                      ✏️
                    </button>
                    <button
                      className="btn-icon-action danger"
                      title="삭제"
                      onClick={() => handleDelete(emp.id)}
                    >
                      🗑
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {filteredEmployees.length === 0 && (
              <tr className="empty-row">
                <td colSpan="8">검색 결과가 없습니다.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {showModal && (
        <div className="modal-overlay" onClick={closeModal}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>{modalMode === "add" ? "신규 사원 등록" : "사원 정보 수정"}</h3>
              <button className="btn-close" onClick={closeModal}>✕</button>
            </div>
            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label>성명</label>
                <input
                  type="text"
                  name="name"
                  placeholder="실명을 입력하세요"
                  value={form.name}
                  onChange={handleFormChange}
                />
              </div>
              <div className="form-group">
                <label>이메일</label>
                <input
                  type="email"
                  name="email"
                  placeholder="example@medisone.com"
                  value={form.email}
                  onChange={handleFormChange}
                />
              </div>
              <div className="form-group">
                <label>연락처</label>
                <input
                  type="text"
                  name="phone"
                  placeholder="010-0000-0000"
                  value={form.phone}
                  onChange={handleFormChange}
                />
              </div>
              <div className="form-group">
                <label>소속 부서</label>
                <select name="dept" value={form.dept} onChange={handleFormChange}>
                  {DEPT_OPTIONS.map((d) => (
                    <option key={d} value={d}>{d}</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>담당 라인</label>
                <select name="line" value={form.line} onChange={handleFormChange}>
                  {LINE_OPTIONS.map((l) => (
                    <option key={l} value={l}>{l}</option>
                  ))}
                </select>
              </div>
              <div className="form-row">
                <div className="form-group">
                  <label>재직 상태</label>
                  <select name="status" value={form.status} onChange={handleFormChange}>
                    <option value="재직중">재직중</option>
                    <option value="휴직">휴직</option>
                    <option value="퇴직">퇴직</option>
                  </select>
                </div>
                <div className="form-group">
                  <label>시스템 권한</label>
                  <select name="role" value={form.role} onChange={handleFormChange}>
                    <option value="user">일반 오퍼레이터</option>
                    <option value="admin">마스터 관리자</option>
                  </select>
                </div>
              </div>
              <button type="submit" className="submit-btn">
                {modalMode === "add" ? "새 계정 발급하기" : "변경사항 저장"}
              </button>
            </form>
          </div>
        </div>
      )}

      {/* 임시 비밀번호 발급 결과 모달 (1회성 표시) */}
      {tempPasswordInfo && (
        <div className="modal-overlay" onClick={closeTempPasswordModal}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>🔑 임시 비밀번호 발급 완료</h3>
              <button className="btn-close" onClick={closeTempPasswordModal}>✕</button>
            </div>

            <div className="temp-pw-target">
              <b>{tempPasswordInfo.name}</b> (#{tempPasswordInfo.empId}) 사원의 임시 비밀번호가
              발급되었습니다.
            </div>

            <div className="temp-pw-box">
              <span className="temp-pw-value">{tempPasswordInfo.password}</span>
              <button
                type="button"
                className={`btn-copy ${isCopied ? "copied" : ""}`}
                onClick={handleCopyTempPassword}
              >
                {isCopied ? "복사됨 ✓" : "복사"}
              </button>
            </div>

            <div className="temp-pw-warning">
              <span className="icon">⚠️</span>
              <span>
                이 비밀번호는 <b>지금 한 번만</b> 표시되며 다시 확인할 수 없습니다. 사원에게 직접
                전달해주세요. 사원은 로그인 후 원할 때 <b>마이페이지에서 자유롭게 변경</b>할 수
                있으며, 반드시 즉시 변경해야 하는 것은 아닙니다. 발급과 동시에 해당 사원의 기존
                로그인 세션은 모두 종료됩니다.
              </span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default AdminEmployeePage;