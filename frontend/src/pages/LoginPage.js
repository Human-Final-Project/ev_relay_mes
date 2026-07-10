import React, { useState } from "react";
import "./LoginPage.css";

function LoginPage() {
  // [수정 7월 10일 14:17] 상태 변수명을 사원번호(employeeId)와 비밀번호(password)로 변경
  const [employeeId, setEmployeeId] = useState("");
  const [password, setPassword] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  // [수정 7월 10일 13:52] TCP 연결 상태를 제어하기 위한 state 추가 (기본값 true)
  const [isTcpActive, setIsTcpActive] = useState(true);

  const handleSubmit = (e) => {
    e.preventDefault();
    // [수정 7월 10일 14:17] 변경된 상태 변수명 적용
    if (employeeId && password) {
      setIsLoading(true);
      setTimeout(() => {
        setIsLoading(false);
        console.log("Login attempt with:", { employeeId, password });
      }, 1500);
    }
  };

  return (
    <div className="login-wrapper">
      <div className="bg-pattern"></div>

      <main className="login-container">
        <div className="brand-header">
          <div className="icon-container">
            <span className="material-symbols-outlined icon-large">
              precision_manufacturing
            </span>
          </div>
          <h1>Mini MES</h1>
          <p>EV Relay Production Line</p>
        </div>

        <div className="login-card">
          <div className="card-header">
            <h2>시스템 로그인</h2>
            <p>Relay 라인 통합 관리를 위해 인증을 진행해 주세요.</p>
          </div>

          <form onSubmit={handleSubmit} className="login-form">
            {/* // [수정 7월 10일 14:17] 첫 번째 입력 필드를 사원번호로 변경 및 아이콘(badge) 수정 */}
            <div className="form-group">
              <label htmlFor="employee_id">사원번호</label>
              <div className="input-wrapper">
                <span className="material-symbols-outlined input-icon">
                  badge
                </span>
                <input
                  id="employee_id"
                  type="text"
                  placeholder="사원번호를 입력하세요"
                  value={employeeId}
                  onChange={(e) => setEmployeeId(e.target.value)}
                  required
                />
              </div>
            </div>

            {/* // [수정 7월 10일 14:17] 두 번째 입력 필드를 비밀번호로 변경 및 아이콘(lock) 수정 */}
            <div className="form-group">
              <label htmlFor="password">비밀번호</label>
              <div className="input-wrapper">
                <span className="material-symbols-outlined input-icon">
                  lock
                </span>
                <input
                  id="password"
                  type="password"
                  placeholder="비밀번호를 입력하세요"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </div>
            </div>

            <button
              type="submit"
              className={`submit-btn ${isLoading ? "loading" : ""}`}
              disabled={isLoading}
            >
              {isLoading ? (
                <>
                  <span className="material-symbols-outlined animate-spin">
                    sync
                  </span>{" "}
                  처리 중...
                </>
              ) : (
                <>
                  로그인
                  <span className="material-symbols-outlined btn-icon">
                    arrow_forward
                  </span>
                </>
              )}
            </button>
          </form>

          <div className="card-footer">
            <button type="button" className="help-btn">
              <span className="material-symbols-outlined">help_outline</span>
              문의하기
            </button>
            <div className="status-indicator">
              {/* // [수정 7월 10일 13:52] TCP 상태(isTcpActive)에 따라 클래스 동적 바인딩 및 텍스트 변경 */}
              {/* // [수정 7월 10일 14:02] 백틱(`)을 사용한 템플릿 리터럴 문법이 정확히 적용되었는지 확인 및 수정 */}
              {/* // [수정 7월 10일 14:04] 기본 상태를 비활성화로 두고, 활성화 상태일 때 active 클래스 부여 */}
              <div
                className={`status-dot animate-pulse ${isTcpActive ? "active" : ""}`}
              ></div>
              <span>
                {isTcpActive ? "TCP Listener Active" : "TCP Listener Inactive"}
              </span>
            </div>
          </div>
        </div>

        <footer className="page-footer">
          <p>본 시스템은 사내 전용 생산 관리 시스템입니다.</p>
          <div className="system-info">
            <span>v2.4.0-STABLE</span>
            <span className="separator"></span>
            <span>IP: 192.168.0.42</span>
          </div>
        </footer>
      </main>

      <div className="decoration-element">
        <div className="deco-content">
          <span className="material-symbols-outlined deco-icon">
            ev_station
          </span>
          <p>
            PRECISION MANUFACTURING
            <br />
            INTEGRATED CONTROL UNIT
          </p>
        </div>
      </div>
    </div>
  );
}

export default LoginPage;
