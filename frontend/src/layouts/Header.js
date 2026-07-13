import React, { useState } from "react";

// 샘플 알림 데이터
const notificationData = [
  {
    id: 1,
    type: "info",
    title: "정보 SPC2001",
    desc: "SPC 알람 (Stream-A), 파라미터: Sigma+0.5",
    time: "14:40:12",
  },
  {
    id: 2,
    type: "warn",
    title: "경고 SPC0032",
    desc: "라인 B 피드 속도 편차 감지됨",
    time: "14:38:05",
  },
  {
    id: 3,
    type: "error",
    title: "오류 ALM1020",
    desc: "레이저 용접 헤드 #4 과열 (92°C)",
    time: "14:35:59",
  },
  {
    id: 4,
    type: "info",
    title: "정보 COM01",
    desc: "교대 근무 시작: C팀 가동 중",
    time: "14:00:00",
  },
];

const Header = ({ currentTime }) => {
  const [isNotifyOpen, setIsNotifyOpen] = useState(false);

  return (
    <header className="top-header" style={{ position: "relative" }}>
      <div className="header-left">
        <h2>EV 릴레이 생산 시스템</h2>
      </div>
      <div className="header-right">
        <div className="tcp-status">
          <span className="status-dot glow-pulse"></span>
          <span>TCP 리스너 활성</span>
        </div>
        <span className="current-time">{currentTime}</span>

        <div className="user-actions">
          {/* 알림 토글 버튼 */}
          <button
            type="button"
            onClick={() => setIsNotifyOpen(!isNotifyOpen)}
            style={{ position: "relative" }}
          >
            <span className="material-symbols-outlined">notifications</span>
            <span className="notification-badge"></span>
          </button>

          {/* 알림 드롭다운 창 */}
          {isNotifyOpen && (
            <div className="noti-dropdown custom-scrollbar">
              <div className="noti-header">
                <span>시스템 알림</span>
                <span className="noti-count">{notificationData.length}건</span>
              </div>
              <div className="noti-list">
                {notificationData.map((item) => (
                  <div key={item.id} className={`noti-item ${item.type}`}>
                    <div className="noti-item-header">
                      <span className="noti-title">{item.title}</span>
                      <span className="noti-time">{item.time}</span>
                    </div>
                    <p className="noti-desc">{item.desc}</p>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className="user-avatar">
            <img
              src="https://lh3.googleusercontent.com/aida-public/AB6AXuAPVhs8r4P8Dz9lrCAtHBvcULEvTgkaDGaTHCykZYCOxFOn1Wm4WMMzCxMn6Qxze5kjVqTifxVNfdhB2nHoJdj39dsJ4-BpFEKqOQ_IQIz0BP9P5DlqdtC449cuvftcqfHQXi0MTsABK_vb3borr9oCyvrxFw-BY5pmlyU1YxIX-9yhKfat0PA0AN4X-xHN-sB9PyuTHCTWm86D72A64nhe7EXFxoBceEVAifG12H05xLVoVFUyCyNjHwdMzbFGbwZEno_bJQy3YvY"
              alt="Profile"
            />
          </div>
        </div>
      </div>
    </header>
  );
};

export default Header;
