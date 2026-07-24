import React, { useState } from "react";
// 💡 페이지 이동을 위해 useNavigate를 가져옵니다.
import { useNavigate } from "react-router-dom";
import AuthApi from "../api/AuthApi";

function LoginPage({ onLoginSuccess }) { 
  const navigate = useNavigate();

  const [employeeId, setEmployeeId] = useState("");
  const [password, setPassword] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  const handleSubmit = async (e) => {
    e.preventDefault();

    const loginId = employeeId.trim();
    if (!loginId || !password) {
      return;
    }

    setIsLoading(true);
    setErrorMessage("");

    try {
      const response = await AuthApi.login(loginId, password);
      const user = response.data;
      onLoginSuccess(user);
      navigate("/dashboard", { replace: true });
    } catch (error) {
      setErrorMessage(
        error.response?.data?.message ||
          "로그인에 실패했습니다. Backend 연결과 계정 정보를 확인해 주세요."
      );
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <>
      {/* Google Fonts */}
      <style>{`
        @import url("https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&family=JetBrains+Mono&display=swap");
        @import url("https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@24,400,0,0");

        :root{
          --surface-container-low:#f0f3ff;
          --surface-container-lowest:#ffffff;
          --primary:#006591;
          --primary-container:#0ea5e9;
          --on-primary:#ffffff;
          --on-surface:#111c2d;
          --on-surface-variant:#3e4850;
          --outline:#6e7881;
          --outline-variant:#bec8d2;
          --secondary:#505f76;
          --on-tertiary-fixed-variant:#43474b;
          --surface-variant:#d8e3fb;
        }

        *{
          box-sizing:border-box;
        }

        html,
        body,
        #root{
          width:100%;
          height:100%;
          margin:0;
          padding:0;
          font-family:"Inter",sans-serif;
          background:var(--surface-container-low);
        }

        .material-symbols-outlined{
          font-family:"Material Symbols Outlined";
          font-weight:normal;
          font-style:normal;
          font-size:24px;
          line-height:1;
          display:inline-block;
          white-space:nowrap;
          direction:ltr;
          font-feature-settings:"liga";
          -webkit-font-smoothing:antialiased;
        }

        .login-wrapper{
          width:100vw;
          height:100vh;
          display:flex;
          align-items:center;
          justify-content:center;
          position:relative;
          overflow:hidden;
          background:var(--surface-container-low);
        }

        .bg-pattern{
          position:absolute;
          inset:0;
          background-image:radial-gradient(#6e7881 0.5px,transparent 0.5px);
          background-size:24px 24px;
          opacity:.2;
          z-index:0;
        }

        .login-container{
          position:relative;
          width:100%;
          max-width:420px;
          z-index:10;
        }

        .brand-header{
          display:flex;
          flex-direction:column;
          align-items:center;
          text-align:center;
          margin-bottom:40px;
        }

        .icon-container{
          margin-bottom:16px;
        }

        .icon-large{
          width:48px;
          height:48px;
          background:var(--primary);
          color:white;
          display:flex;
          align-items:center;
          justify-content:center;
          border-radius:4px;
          font-size:32px;
        }

        .brand-header h1{
          margin:0;
          font-size:32px;
          font-weight:600;
          color:var(--on-surface);
        }

        .brand-header p{
          margin-top:4px;
          font-size:12px;
          color:var(--primary);
          font-weight:600;
          letter-spacing:.05em;
          text-transform:uppercase;
        }

        .login-card{
          background:white;
          border:1px solid var(--outline-variant);
          border-radius:8px;
          padding:40px;
          box-shadow:0 12px 32px rgba(30,41,59,.05);
        }

        .card-header{
          margin-bottom:32px;
        }

        .card-header h2{
          margin:0;
          font-size:20px;
        }

        .card-header p{
          margin-top:8px;
          color:var(--on-surface-variant);
        }

        .login-form{
          display:flex;
          flex-direction:column;
          gap:24px;
        }

        .form-group{
          display:flex;
          flex-direction:column;
          gap:8px;
        }

        .form-group label{
          font-size:12px;
          text-transform:uppercase;
          color:var(--outline);
          font-weight:600;
        }

        .input-wrapper{
          position:relative;
          display:flex;
          align-items:center;
        }

        .input-icon{
          position:absolute;
          left:12px;
          top:50%;
          transform:translateY(-50%);
          color:var(--outline);
          font-size:20px;
        }

        .input-wrapper input{
          width:100%;
          padding:12px 16px 12px 40px;
          border:1px solid var(--outline-variant);
          border-radius:4px;
          outline:none;
          font-size:16px;
          transition:.2s;
        }

        .input-wrapper input:focus{
          border-color:var(--primary-container);
          box-shadow:0 0 0 2px rgba(14,165,233,.2);
        }

        /* 💡 에러메시지 디자인 추가 */
        .error-message {
          color: #ba1a1a;
          font-size: 13px;
          margin: -12px 0 4px 0;
          font-weight: 600;
        }

        .submit-btn{
          width:100%;
          padding:16px;
          background:var(--primary-container);
          color:white;
          border:none;
          border-radius:4px;
          display:flex;
          justify-content:center;
          align-items:center;
          gap:8px;
          cursor:pointer;
          font-size:16px;
          font-weight:600;
          transition:.2s;
        }

        .submit-btn:hover:not(:disabled){
          background:var(--primary);
        }

        .submit-btn:active:not(:disabled){
          transform:scale(.98);
        }

        .loading{
          opacity:.8;
          cursor:not-allowed;
        }

        .btn-icon{
          transition:.2s;
        }

        .submit-btn:hover .btn-icon{
          transform:translateX(4px);
        }

        .animate-spin{
          animation:spin 1s linear infinite;
        }

        @keyframes spin{
          from{transform:rotate(0deg);}
          to{transform:rotate(360deg);}
        }

        .card-footer{
          margin-top:32px;
          padding-top:24px;
          border-top:1px solid var(--surface-variant);
          display:flex;
          justify-content:space-between;
          align-items:center;
        }

        .help-btn{
          border:none;
          background:none;
          display:flex;
          align-items:center;
          gap:4px;
          cursor:pointer;
          color:var(--secondary);
        }

        .help-btn:hover{
          color:var(--primary);
        }

        .status-indicator{
          display:flex;
          align-items:center;
          gap:8px;
        }

        .status-dot{
          width:8px;
          height:8px;
          border-radius:50%;
          background:#ba1a1a;
        }

        .status-dot.active{
          background:#22c55e;
        }

        .animate-pulse{
          animation:pulse 2s infinite;
        }

        @keyframes pulse{
          0%,100%{opacity:1;}
          50%{opacity:.5;}
        }

        .status-indicator span{
          font-size:12px;
          font-weight:600;
        }

        .system-info{
          display:flex;
          align-items:center;
          gap:16px;
          font-family:"JetBrains Mono",monospace;
          color:rgba(110,120,129,.6);
        }

        .separator{
          width:4px;
          height:4px;
          border-radius:50%;
          background:rgba(110,120,129,.3);
        }

        .decoration-element{
          position:fixed;
          right:48px;
          bottom:48px;
          opacity:.1;
          pointer-events:none;
        }

        .deco-content{
          display:flex;
          flex-direction:column;
          align-items:flex-end;
        }

        .deco-icon{
          font-size:120px !important;
        }

        .deco-content p{
          font-family:"JetBrains Mono",monospace;
          text-align:right;
        }

        @media(max-width:1023px){
          .decoration-element{
            display:none;
          }
        }
      `}</style>

      <div className="login-wrapper">
        <div className="bg-pattern" />

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

            <form onSubmit={handleSubmit} className="login-form" autoComplete="off">
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
                    autoComplete="username"
                    required
                  />
                </div>
              </div>

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
                    autoComplete="current-password"
                    required
                  />
                </div>
              </div>

              {/* 💡 에러 발생 시 컴포넌트 노출 */}
              {errorMessage && <div className="error-message">{errorMessage}</div>}

              <button
                type="submit"
                className={`submit-btn ${isLoading ? "loading" : ""}`}
                disabled={isLoading}
              >
                {isLoading ? (
                  <>
                    <span className="material-symbols-outlined animate-spin">
                      sync
                    </span>
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

          </div>
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
    </>
  );
}

export default LoginPage;
