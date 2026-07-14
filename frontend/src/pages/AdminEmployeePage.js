import React, { useState } from "react";

function AdminEmployeePage() {
  const [employees, setEmployees] = useState([
    { id: "M0012", name: "홍길동", dept: "생산 1팀", role: "user", joinDate: "2024-01-15", status: "재직" },
    { id: "M0045", name: "김철수", dept: "품질 관리팀", role: "user", joinDate: "2024-03-22", status: "재직" },
    { id: "M0002", name: "이영희", dept: "시스템 관리팀", role: "admin", joinDate: "2023-05-10", status: "재직" },
    { id: "M0089", name: "박민수", dept: "자재 관리팀", role: "user", joinDate: "2025-11-01", status: "휴직" },
  ]);

  const [newEmployee, setNewEmployee] = useState({ id: "", name: "", dept: "", role: "user" });

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setNewEmployee({ ...newEmployee, [name]: value });
  };

  const handleAddEmployee = (e) => {
    e.preventDefault();
    if (!newEmployee.id || !newEmployee.name || !newEmployee.dept) {
      alert("모든 정보를 입력해주세요.");
      return;
    }
    const added = {
      ...newEmployee,
      joinDate: new Date().toISOString().split("T")[0],
      status: "재직",
    };
    setEmployees([...employees, added]);
    setNewEmployee({ id: "", name: "", dept: "", role: "user" });
    alert("신규 사원이 등록되었습니다.");
  };

  const handleDelete = (id) => {
    if (window.confirm(`사원번호 ${id}번을 삭제하시겠습니까?`)) {
      setEmployees(employees.filter((emp) => emp.id !== id));
    }
  };

  return (
    <>
      <style>{`
        .admin-container { padding: 8px; }
        .admin-title-area { margin-bottom: 24px; }
        .admin-title-area h2 { margin: 0; font-size: 22px; color: #191c20; font-weight: 800; }
        .admin-title-area p { margin: 6px 0 0 0; font-size: 13px; color: #45474c; }
        .admin-content { display: flex; gap: 24px; flex-wrap: wrap; }
        .card { background: #ffffff; border: 1px solid #c5c6cd; border-radius: 12px; padding: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.02); }
        .list-card { flex: 2; min-width: 600px; }
        .form-card { flex: 1; min-width: 300px; height: fit-content; }
        .card h3 { margin: 0 0 20px 0; font-size: 16px; color: #0566d9; font-weight: 700; padding-bottom: 8px; border-bottom: 2px solid #d8e2ff; }
        .emp-table { width: 100%; border-collapse: collapse; text-align: left; }
        .emp-table th, .emp-table td { padding: 14px 16px; border-bottom: 1px solid #eceef4; font-size: 13px; }
        .emp-table th { background: #f2f3f7; color: #45474c; font-weight: 700; }
        .emp-table tr:hover { background: #f8f9ff; }
        .badge-admin { background: #d8e2ff; color: #0566d9; padding: 4px 8px; border-radius: 6px; font-size: 11px; font-weight: 700; }
        .badge-user { background: #f2f3f7; color: #45474c; padding: 4px 8px; border-radius: 6px; font-size: 11px; }
        .form-group { display: flex; flex-direction: column; gap: 6px; margin-bottom: 16px; }
        .form-group label { font-size: 13px; font-weight: 700; color: #191c20; }
        .form-group input, .form-group select { padding: 10px; border: 1px solid #c5c6cd; border-radius: 6px; outline: none; font-size: 13px; background: #ffffff; }
        .form-group input:focus { border-color: #0566d9; }
        .submit-btn { width: 100%; padding: 12px; background: #0566d9; color: white; border: none; border-radius: 6px; font-weight: 700; cursor: pointer; transition: 0.2s; font-size: 13px; }
        .submit-btn:hover { background: #004fb0; }
        .delete-btn { background: none; border: 1px solid #ba1a1a; color: #ba1a1a; padding: 4px 10px; border-radius: 6px; cursor: pointer; font-size: 11px; font-weight: 700; }
        .delete-btn:hover { background: #fbaeae; }
      `}</style>

      <div className="admin-container">
        <div className="admin-title-area">
          <h2>사원 권한 및 계정 관리</h2>
          <p>Mini MES 시스템 이용 권한을 제어하고 인프라 접근 계정을 생성 및 삭제합니다.</p>
        </div>

        <div className="admin-content">
          <div className="card list-card">
            <h3>사원 명부 ({employees.length}명 등록됨)</h3>
            <table className="emp-table">
              <thead>
                <tr>
                  <th>사원번호</th>
                  <th>이름</th>
                  <th>소속 부서</th>
                  <th>시스템 권한</th>
                  <th>등록 일자</th>
                  <th>재직 상태</th>
                  <th>제어</th>
                </tr>
              </thead>
              <tbody>
                {employees.map((emp) => (
                  <tr key={emp.id}>
                    <td style={{ fontWeight: "700", color: "#191c20" }}>{emp.id}</td>
                    <td>{emp.name}</td>
                    <td>{emp.dept}</td>
                    <td>
                      <span className={emp.role === "admin" ? "badge-admin" : "badge-user"}>
                        {emp.role === "admin" ? "마스터 관리자" : "일반 오퍼레이터"}
                      </span>
                    </td>
                    <td>{emp.joinDate}</td>
                    <td>{emp.status}</td>
                    <td>
                      <button className="delete-btn" onClick={() => handleDelete(emp.id)}>
                        삭제
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="card form-card">
            <h3>신규 오퍼레이터 등록</h3>
            <form onSubmit={handleAddEmployee}>
              <div className="form-group">
                <label>사원번호</label>
                <input
                  type="text"
                  name="id"
                  placeholder="예: M0123"
                  value={newEmployee.id}
                  onChange={handleInputChange}
                />
              </div>
              <div className="form-group">
                <label>성명</label>
                <input
                  type="text"
                  name="name"
                  placeholder="실명을 입력하세요"
                  value={newEmployee.name}
                  onChange={handleInputChange}
                />
              </div>
              <div className="form-group">
                <label>배치 부서</label>
                <input
                  type="text"
                  name="dept"
                  placeholder="예: 품질 관리팀"
                  value={newEmployee.dept}
                  onChange={handleInputChange}
                />
              </div>
              <div className="form-group">
                <label>시스템 마스터 등급</label>
                <select name="role" value={newEmployee.role} onChange={handleInputChange}>
                  <option value="user">일반사원 (user)</option>
                  <option value="admin">최고관리자 (admin)</option>
                </select>
              </div>
              <button type="submit" className="submit-btn">
                새 계정 발급하기
              </button>
            </form>
          </div>
        </div>
      </div>
    </>
  );
}

export default AdminEmployeePage;