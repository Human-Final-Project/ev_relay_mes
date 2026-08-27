import React, { useState, useEffect } from "react";

function AdminProfilePage() {
  // 💡 로컬스토리지에서 로그인된 유저의 사원번호와 이름을 가져옵니다.
  const [empId, setEmpId] = useState("");

  const [profileForm, setProfileForm] = useState({
    name: "",
    email: "",
    phone: "",
  });

  const [passwordForm, setPasswordForm] = useState({
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  });

  useEffect(() => {
    // 실제 프로젝트 환경에 맞게 로컬스토리지 키 이름을 맞춰주세요.
    const savedId = localStorage.getItem("userId") || localStorage.getItem("userEmpId") || "admin";
    const savedName = localStorage.getItem("userName") || "관리자";
    const savedEmail = localStorage.getItem("userEmail") || "";
    const savedPhone = localStorage.getItem("userPhone") || "";
    setEmpId(savedId);
    setProfileForm({ name: savedName, email: savedEmail, phone: savedPhone });
  }, []);

  const handleProfileChange = (e) => {
    const { name, value } = e.target;
    setProfileForm({ ...profileForm, [name]: value });
  };

  const handleProfileSubmit = (e) => {
    e.preventDefault();

    // TODO: 백엔드 API 연동 (프로필 정보 수정 요청)
    console.log("프로필 수정 요청:", { empId, ...profileForm });

    localStorage.setItem("userEmail", profileForm.email);
    localStorage.setItem("userPhone", profileForm.phone);

    alert("프로필 정보가 수정되었습니다.");
  };

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setPasswordForm({ ...passwordForm, [name]: value });
  };

  const handlePasswordSubmit = (e) => {
    e.preventDefault();

    if (!passwordForm.currentPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) {
      alert("모든 필드를 입력해주세요.");
      return;
    }

    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      alert("새 비밀번호와 비밀번호 확인이 일치하지 않습니다.");
      return;
    }

    if (passwordForm.currentPassword === passwordForm.newPassword) {
      alert("새 비밀번호는 현재 비밀번호와 다르게 설정해야 합니다.");
      return;
    }

    // TODO: 백엔드 API 연동 (비밀번호 변경 요청)
    console.log("비밀번호 변경 요청:", { empId, ...passwordForm });

    alert("비밀번호가 안전하게 변경되었습니다.");
    setPasswordForm({ currentPassword: "", newPassword: "", confirmPassword: "" });
  };

  const styles = `
    .mypage-container { padding: 8px; }
    .mypage-title-area { margin-bottom: 24px; }
    .mypage-title-area h2 { margin: 0; font-size: 22px; color: #191c20; font-weight: 800; }
    .mypage-title-area p { margin: 6px 0 0 0; font-size: 13px; color: #45474c; }

    .mypage-content { display: grid; grid-template-columns: 1fr 1fr; align-items: start; gap: 24px; max-width: 960px; }
    @media (max-width: 760px) { .mypage-content { grid-template-columns: 1fr; } }
    .card { background: #ffffff; border: 1px solid #c5c6cd; border-radius: 12px; padding: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.02); }
    .card h3 { margin: 0 0 20px 0; font-size: 16px; color: #0566d9; font-weight: 700; padding-bottom: 8px; border-bottom: 2px solid #d8e2ff; }

    .form-group { display: flex; flex-direction: column; gap: 6px; margin-bottom: 16px; }
    .form-group label { font-size: 13px; font-weight: 700; color: #191c20; }
    .form-group input { padding: 10px; border: 1px solid #c5c6cd; border-radius: 6px; outline: none; font-size: 13px; background: #ffffff; }
    .form-group input:focus { border-color: #0566d9; }

    /* 읽기 전용 필드 스타일 (사원번호 고정) */
    .form-group input:disabled { background: #f2f3f7; color: #75777c; cursor: not-allowed; border-color: #eceef4; }

    .submit-btn { width: 100%; padding: 12px; background: #0566d9; color: white; border: none; border-radius: 6px; font-weight: 700; cursor: pointer; transition: 0.2s; font-size: 13px; margin-top: 8px; }
    .submit-btn:hover { background: #004fb0; }
  `;

  return (
    <>
      <style>{styles}</style>

      <div className="mypage-container">
        <div className="mypage-title-area">
          <h2>관리자 프로필 관리</h2>
          <p>프로필 정보를 수정하고, 보안을 위해 비밀번호를 주기적으로 변경해 주세요.</p>
        </div>

        <div className="mypage-content">
          {/* 프로필 정보 수정 */}
          <div className="card">
            <h3>프로필 정보 수정</h3>
            <form onSubmit={handleProfileSubmit}>
              {/* 사원번호 (수정 불가 고정) */}
              <div className="form-group">
                <label>사원번호 (수정 불가)</label>
                <input type="text" value={empId} disabled />
              </div>

              {/* 성명 (수정 불가 고정) */}
              <div className="form-group">
                <label>성명 (수정 불가)</label>
                <input type="text" value={profileForm.name} disabled />
              </div>

              {/* 이메일 */}
              <div className="form-group">
                <label>이메일</label>
                <input
                  type="email"
                  name="email"
                  placeholder="이메일을 입력하세요"
                  value={profileForm.email}
                  onChange={handleProfileChange}
                />
              </div>

              {/* 연락처 */}
              <div className="form-group">
                <label>연락처</label>
                <input
                  type="text"
                  name="phone"
                  placeholder="연락처를 입력하세요 (예: 010-0000-0000)"
                  value={profileForm.phone}
                  onChange={handleProfileChange}
                />
              </div>

              <button type="submit" className="submit-btn">
                프로필 저장하기
              </button>
            </form>
          </div>

          {/* 계정 정보 설정 (비밀번호 변경) */}
          <div className="card">
            <h3>계정 정보 설정</h3>
            <form onSubmit={handlePasswordSubmit}>
              {/* 현재 비밀번호 */}
              <div className="form-group">
                <label>현재 비밀번호</label>
                <input
                  type="password"
                  name="currentPassword"
                  placeholder="기존 비밀번호를 입력하세요"
                  value={passwordForm.currentPassword}
                  onChange={handleInputChange}
                />
              </div>

              {/* 새 비밀번호 */}
              <div className="form-group">
                <label>새 비밀번호</label>
                <input
                  type="password"
                  name="newPassword"
                  placeholder="새로운 비밀번호를 입력하세요"
                  value={passwordForm.newPassword}
                  onChange={handleInputChange}
                />
              </div>

              {/* 새 비밀번호 확인 */}
              <div className="form-group">
                <label>새 비밀번호 확인</label>
                <input
                  type="password"
                  name="confirmPassword"
                  placeholder="새로운 비밀번호를 한 번 더 입력하세요"
                  value={passwordForm.confirmPassword}
                  onChange={handleInputChange}
                />
              </div>

              <button type="submit" className="submit-btn">
                비밀번호 수정하기
              </button>
            </form>
          </div>
        </div>
      </div>
    </>
  );
}

export default AdminProfilePage;