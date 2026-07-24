import React, { useState, useMemo } from "react";
import { pushNotification } from "../utils/notificationBus";

const styles = `
.ane-page {
  min-height: 100vh;
  background: #F8FAFC;
  font-family: -apple-system, BlinkMacSystemFont, "Pretendard", sans-serif;
  color: #1E293B;
}

.ane-shell {
  max-width: 1180px;
  margin: 0 auto;
  padding: 36px 28px 80px;
}

/* 페이지 헤더 */
.ane-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 6px;
}
.ane-badge {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: #FCE7F0;
  color: #9D174D;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.ane-head h1 {
  font-size: 21px;
  font-weight: 800;
  margin: 0;
  letter-spacing: -0.02em;
}
.ane-sub {
  margin: 0 0 30px 52px;
  font-size: 13.5px;
  color: #64748B;
}

/* 레이아웃 */
.ane-grid {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 24px;
  align-items: start;
}
@media (max-width: 940px) {
  .ane-grid {
    grid-template-columns: 1fr;
  }
}

.ane-panel {
  background: #fff;
  border: 1px solid #E5EAF1;
  border-radius: 16px;
  padding: 24px;
}

/* 폼 필드 */
.ane-field {
  margin-bottom: 20px;
}
.ane-field:last-child {
  margin-bottom: 0;
}
.ane-field label {
  display: block;
  font-size: 12.5px;
  font-weight: 700;
  color: #64748B;
  margin-bottom: 8px;
}
.ane-hint {
  font-size: 11.5px;
  color: #94A3B8;
  font-weight: 500;
  margin-top: 6px;
}

.ane-input,
.ane-textarea,
.ane-datetime {
  width: 100%;
  font-family: inherit;
  font-size: 14px;
  color: #1E293B;
  border: 1px solid #E5EAF1;
  background: #FBFCFE;
  border-radius: 10px;
  padding: 11px 13px;
  outline: none;
  transition: border-color 0.15s, background 0.15s;
  box-sizing: border-box;
}
.ane-input:focus,
.ane-textarea:focus,
.ane-datetime:focus {
  border-color: #2952CC;
  background: #fff;
}
.ane-input.error {
  border-color: #C2410C;
}
.ane-textarea {
  resize: vertical;
  min-height: 96px;
  line-height: 1.55;
  font-family: inherit;
}

.ane-row2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

/* 유형 선택 카드 */
.ane-type-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.ane-type-opt {
  border: 1.5px solid #E5EAF1;
  border-radius: 12px;
  padding: 12px 10px;
  background: #fff;
  cursor: pointer;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.1s;
  font-family: inherit;
}
.ane-type-opt:hover {
  transform: translateY(-1px);
}
.ane-type-opt .ic {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
}
.ane-type-opt .name {
  font-size: 12.5px;
  font-weight: 700;
  color: #334155;
}
.ane-type-opt.warn .ic { background: #FDECEA; color: #C2410C; }
.ane-type-opt.info .ic { background: #E9F1FE; color: #1D4ED8; }
.ane-type-opt.plain .ic { background: #F1F5F4; color: #3F6B57; }
.ane-type-opt.warn.active { border-color: #C2410C; box-shadow: 0 0 0 3px rgba(194,65,12,.10); }
.ane-type-opt.info.active { border-color: #1D4ED8; box-shadow: 0 0 0 3px rgba(29,78,216,.10); }
.ane-type-opt.plain.active { border-color: #3F6B57; box-shadow: 0 0 0 3px rgba(63,107,87,.10); }

/* 옵션 스위치 */
.ane-switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-top: 1px solid #E5EAF1;
}
.ane-switch-row:first-of-type {
  border-top: none;
  padding-top: 0;
}
.ane-switch-row .txt strong {
  display: block;
  font-size: 13.5px;
  font-weight: 700;
}
.ane-switch-row .txt span {
  font-size: 12px;
  color: #64748B;
}
.ane-switch {
  position: relative;
  width: 42px;
  height: 24px;
  flex-shrink: 0;
  border-radius: 99px;
  background: #D8DEE7;
  cursor: pointer;
  border: none;
  transition: background 0.15s;
  padding: 0;
}
.ane-switch.on {
  background: #2952CC;
}
.ane-switch .dot {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0,0,0,.25);
  transition: left 0.15s;
}
.ane-switch.on .dot {
  left: 20px;
}

.ane-submit-bar {
  display: flex;
  gap: 10px;
  margin-top: 24px;
}
.ane-btn {
  padding: 13px;
  border-radius: 10px;
  border: none;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  font-family: inherit;
}
.ane-btn-primary {
  flex: 1;
  background: #2952CC;
  color: #fff;
}
.ane-btn-primary:hover {
  background: #1F3FA6;
}
.ane-btn-ghost {
  background: #F1F4F9;
  color: #1E293B;
  padding: 13px 18px;
}
.ane-btn-ghost:hover {
  background: #E7ECF3;
}

/* 미리보기 */
.ane-preview-label {
  font-size: 12px;
  font-weight: 700;
  color: #64748B;
  margin: 0 0 10px 2px;
}
.ane-emp-widget {
  background: #fff;
  border: 1px solid #E5EAF1;
  border-radius: 16px;
  padding: 20px;
}
.ane-emp-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.ane-emp-badge {
  width: 30px;
  height: 30px;
  border-radius: 10px;
  background: #FCE7F0;
  color: #9D174D;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ane-emp-head h2 {
  font-size: 15.5px;
  font-weight: 800;
  margin: 0;
}

.ane-notice-card {
  border-radius: 12px;
  padding: 14px 15px;
  border: 1px solid transparent;
}
.ane-notice-card.warn { background: #FDECEA; border-color: #F6C6BE; }
.ane-notice-card.info { background: #E9F1FE; border-color: #C7DBFB; }
.ane-notice-card.plain { background: #F1F5F4; border-color: #D4DEDA; }

.ane-notice-card .top {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 6px;
}
.ane-notice-card .top .ic {
  font-size: 14px;
  line-height: 1;
}
.ane-notice-card .title {
  font-size: 13.5px;
  font-weight: 800;
}
.ane-notice-card.warn .title { color: #9A3412; }
.ane-notice-card.info .title { color: #1E3A8A; }
.ane-notice-card.plain .title { color: #2B4A3D; }

.ane-notice-card .body {
  font-size: 12.5px;
  line-height: 1.6;
  color: #3F4B5C;
  white-space: pre-wrap;
  margin-bottom: 8px;
}
.ane-notice-card .meta {
  font-size: 11px;
  color: #8592A3;
  font-weight: 600;
}
.ane-notice-card .pin {
  margin-left: auto;
  font-size: 10px;
  font-weight: 800;
  color: #94A3B8;
}

/* 발행 리스트 */
.ane-list-panel {
  margin-top: 20px;
}
.ane-list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.ane-list-head h3 {
  font-size: 14.5px;
  font-weight: 800;
  margin: 0;
}
.ane-list-head span {
  font-size: 12px;
  color: #64748B;
}

.ane-empty-hint {
  font-size: 12.5px;
  color: #94A3B8;
  text-align: center;
  padding: 28px 10px;
}

.ane-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 4px;
  border-top: 1px solid #E5EAF1;
}
.ane-item:first-child {
  border-top: none;
}
.ane-item .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 6px;
  flex-shrink: 0;
}
.ane-item.warn .dot { background: #C2410C; }
.ane-item.info .dot { background: #1D4ED8; }
.ane-item.plain .dot { background: #3F6B57; }
.ane-item .content {
  flex: 1;
  min-width: 0;
}
.ane-item .title {
  font-size: 13.5px;
  font-weight: 700;
  margin-bottom: 2px;
}
.ane-item .meta {
  font-size: 11.5px;
  color: #64748B;
}
.ane-item .del-btn {
  border: none;
  background: #F1F4F9;
  color: #64748B;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 12px;
  flex-shrink: 0;
}
.ane-item .del-btn:hover {
  background: #FDECEA;
  color: #C2410C;
}
`;

const TYPES = {
  warn: { label: "필독 · 경고", icon: "⚠️" },
  info: { label: "안전 안내", icon: "🦺" },
  plain: { label: "일반 공지", icon: "📋" },
};

function Toggle({ on, onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`ane-switch ${on ? "on" : ""}`}
    >
      <span className="dot" />
    </button>
  );
}

function NoticeCard({ type, title, body, meta, pinned }) {
  const t = TYPES[type];
  return (
    <div className={`ane-notice-card ${type}`}>
      <div className="top">
        <span className="ic">{t.icon}</span>
        <span className="title">{title || "제목을 입력하세요"}</span>
        {pinned && <span className="pin">📌 고정</span>}
      </div>
      <div className="body">
        {body || "내용을 입력하면 여기에 표시됩니다."}
      </div>
      <div className="meta">{meta}</div>
    </div>
  );
}

const MegaphoneIcon = ({ size = 18 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
    <path
      d="M3 11v2a2 2 0 0 0 2 2h1l2 5h2l-1.5-5H11l7 4V6l-7 4H6a2 2 0 0 0-2 2z"
      fill="currentColor"
    />
    <path
      d="M19 8.5a3.5 3.5 0 0 1 0 7"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
    />
  </svg>
);

export default function AdminNoticeEditor() {
  const [type, setType] = useState("warn");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [team, setTeam] = useState("");
  const [writer, setWriter] = useState("");
  const [pinned, setPinned] = useState(false);
  const [postNow, setPostNow] = useState(true);
  const [schedule, setSchedule] = useState("");
  const [titleError, setTitleError] = useState(false);
  const [publishedFlash, setPublishedFlash] = useState(false);

  const [posts, setPosts] = useState([
    {
      type: "warn",
      title: "필독: 라인 클리닝 일정 변경",
      body: "오후 3시 예정되었던 정기 클리닝이 설비 점검으로 인해 4시로 연기되었습니다.",
      meta: "최관리 팀장 | 14:10",
      pinned: false,
    },
    {
      type: "info",
      title: "신규 안전 보호구 착용 안내",
      body: "내일부터 지급되는 신형 정전기 방지 장갑을 필히 착용해주시기 바랍니다.",
      meta: "품질관리팀 | 09:20",
      pinned: false,
    },
  ]);

  const meta = useMemo(() => {
    const who = [team, writer].filter(Boolean).join(" ");
    const when = postNow ? "방금" : schedule ? "예약됨" : "방금";
    return who ? `${who} | ${when}` : when;
  }, [team, writer, postNow, schedule]);

  const sortedPosts = useMemo(
    () => [...posts].sort((a, b) => (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0)),
    [posts]
  );

  function resetForm() {
    setTitle("");
    setBody("");
    setTeam("");
    setWriter("");
    setPinned(false);
    setType("warn");
  }

  function handlePublish() {
    if (!title.trim()) {
      setTitleError(true);
      setTimeout(() => setTitleError(false), 900);
      return;
    }
    const now = new Date();
    const time = postNow
      ? `${String(now.getHours()).padStart(2, "0")}:${String(
          now.getMinutes()
        ).padStart(2, "0")}`
      : schedule
      ? new Date(schedule).toLocaleString("ko-KR", {
          month: "numeric",
          day: "numeric",
          hour: "2-digit",
          minute: "2-digit",
        })
      : "방금";

    const who = [team, writer].filter(Boolean).join(" ");
    setPosts((prev) => [
      { type, title, body, meta: who ? `${who} | ${time}` : time, pinned },
      ...prev,
    ]);

    setPublishedFlash(true);
    setTimeout(() => setPublishedFlash(false), 1400);

    // 필독·경고 공지는 사원 전체에게 실시간 알림으로도 전달합니다.
    if (type === "warn") {
      pushNotification({
        targetRole: "employee",
        type: "warn",
        title: `필독 ${title}`,
        desc: body || "새로운 필독 공지가 등록되었습니다.",
      });
    }
  }

  function deletePost(idx) {
    setPosts((prev) => prev.filter((_, i) => i !== idx));
  }

  return (
    <div className="ane-page">
      <style>{styles}</style>
      <div className="ane-shell">
        {/* 페이지 헤더 */}
        <div className="ane-head">
          <div className="ane-badge">
            <MegaphoneIcon />
          </div>
          <h1>관리자 공지 작성</h1>
        </div>
        <p className="ane-sub">
          사원 홈 화면의 '관리자 공지' 카드에 표시될 내용을 작성하고 바로
          확인하세요.
        </p>

        <div className="ane-grid">
          {/* 작성 폼 */}
          <div className="ane-panel">
            {/* 유형 선택 */}
            <div className="ane-field">
              <label>공지 유형</label>
              <div className="ane-type-grid">
                {Object.entries(TYPES).map(([key, t]) => (
                  <button
                    key={key}
                    type="button"
                    onClick={() => setType(key)}
                    className={`ane-type-opt ${key} ${
                      type === key ? "active" : ""
                    }`}
                  >
                    <div className="ic">{t.icon}</div>
                    <div className="name">{t.label}</div>
                  </button>
                ))}
              </div>
            </div>

            {/* 제목 */}
            <div className="ane-field">
              <label>제목</label>
              <input
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                maxLength={40}
                placeholder="예: 필독: 라인 클리닝 일정 변경"
                className={`ane-input ${titleError ? "error" : ""}`}
              />
            </div>

            {/* 내용 */}
            <div className="ane-field">
              <label>내용</label>
              <textarea
                value={body}
                onChange={(e) => setBody(e.target.value)}
                placeholder="사원이 확인해야 할 내용을 간결하게 작성하세요."
                className="ane-textarea"
              />
              <div className="ane-hint">
                줄바꿈은 그대로 적용됩니다. 2~3문장 이내를 권장합니다.
              </div>
            </div>

            {/* 부서 / 작성자 */}
            <div className="ane-field ane-row2">
              <div>
                <label>작성 부서/팀</label>
                <input
                  type="text"
                  value={team}
                  onChange={(e) => setTeam(e.target.value)}
                  maxLength={20}
                  placeholder="예: 품질관리팀"
                  className="ane-input"
                />
              </div>
              <div>
                <label>작성자</label>
                <input
                  type="text"
                  value={writer}
                  onChange={(e) => setWriter(e.target.value)}
                  maxLength={20}
                  placeholder="예: 최관리 팀장"
                  className="ane-input"
                />
              </div>
            </div>

            {/* 옵션 */}
            <div className="ane-field">
              <div className="ane-switch-row">
                <div className="txt">
                  <strong>상단 고정</strong>
                  <span>목록 맨 위에 항상 노출합니다</span>
                </div>
                <Toggle on={pinned} onClick={() => setPinned((v) => !v)} />
              </div>
              <div className="ane-switch-row">
                <div className="txt">
                  <strong>지금 바로 게시</strong>
                  <span>끄면 게시 시각을 예약할 수 있습니다</span>
                </div>
                <Toggle on={postNow} onClick={() => setPostNow((v) => !v)} />
              </div>
              {!postNow && (
                <div className="ane-field" style={{ paddingTop: 4 }}>
                  <label>게시 예약 시각</label>
                  <input
                    type="datetime-local"
                    value={schedule}
                    onChange={(e) => setSchedule(e.target.value)}
                    className="ane-datetime"
                  />
                </div>
              )}
            </div>

            {/* 버튼 */}
            <div className="ane-submit-bar">
              <button
                type="button"
                onClick={resetForm}
                className="ane-btn ane-btn-ghost"
              >
                초기화
              </button>
              <button
                type="button"
                onClick={handlePublish}
                className="ane-btn ane-btn-primary"
              >
                {publishedFlash ? "게시되었습니다 ✓" : "공지 게시하기"}
              </button>
            </div>
          </div>

          {/* 미리보기 + 목록 */}
          <div>
            <p className="ane-preview-label">사원 화면 미리보기</p>
            <div className="ane-emp-widget">
              <div className="ane-emp-head">
                <div className="ane-emp-badge">
                  <MegaphoneIcon size={15} />
                </div>
                <h2>관리자 공지</h2>
              </div>
              <NoticeCard
                type={type}
                title={title}
                body={body}
                meta={meta}
                pinned={pinned}
              />
            </div>

            <div className="ane-panel ane-list-panel">
              <div className="ane-list-head">
                <h3>게시된 공지</h3>
                <span>{posts.length}건</span>
              </div>
              {sortedPosts.length === 0 ? (
                <div className="ane-empty-hint">
                  아직 게시된 공지가 없습니다.
                </div>
              ) : (
                sortedPosts.map((p) => {
                  const originalIdx = posts.indexOf(p);
                  return (
                    <div key={originalIdx} className={`ane-item ${p.type}`}>
                      <div className="dot" />
                      <div className="content">
                        <div className="title">
                          {p.pinned ? "📌 " : ""}
                          {p.title}
                        </div>
                        <div className="meta">
                          {TYPES[p.type].label} · {p.meta}
                        </div>
                      </div>
                      <button
                        type="button"
                        onClick={() => deletePost(originalIdx)}
                        className="del-btn"
                        title="삭제"
                      >
                        ✕
                      </button>
                    </div>
                  );
                })
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}