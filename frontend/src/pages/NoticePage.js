import React, { useState } from "react";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { EmptyState, ErrorState, Field, LoadingState, PageHeader, formatDate } from "../components/MesComponents";

export default function NoticePage({ currentUser }) {
  const notices = useApiData(MesApi.getNotices, []);
  const [form, setForm] = useState({ title: "", content: "", pinned: false });
  const [editingId, setEditingId] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState("");
  const isAdmin = currentUser?.role === "ADMIN";

  const submit = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setSubmitError("");
    try {
      if (editingId === null) {
        await MesApi.createNotice(form);
      } else {
        await MesApi.updateNotice(editingId, form);
      }
      setForm({ title: "", content: "", pinned: false });
      setEditingId(null);
      await notices.reload();
    } catch (error) {
      setSubmitError(error?.response?.data?.message || "공지사항을 등록하지 못했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  const edit = (notice) => {
    setEditingId(notice.noticeId);
    setForm({ title: notice.title, content: notice.content, pinned: notice.pinned });
    setSubmitError("");
  };

  const cancelEdit = () => {
    setEditingId(null);
    setForm({ title: "", content: "", pinned: false });
    setSubmitError("");
  };

  return <div className="mes-page notice-page">
    <PageHeader
      title="공지사항"
      description="생산 운영과 안전 관련 안내를 확인합니다."
      actions={<button className="btn secondary" onClick={notices.reload}>새로고침</button>}
    />

    {isAdmin && <section className="mes-card notice-editor">
      <div className="notice-section-heading">
        <div><h2>{editingId === null ? "공지 작성" : "공지 수정"}</h2><p>등록한 공지는 모든 로그인 사용자에게 표시됩니다.</p></div>
      </div>
      <form onSubmit={submit}>
        <Field label="제목">
          <input
            value={form.title}
            maxLength={200}
            required
            onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))}
          />
        </Field>
        <Field label="내용">
          <textarea
            value={form.content}
            maxLength={5000}
            rows={5}
            required
            onChange={(event) => setForm((current) => ({ ...current, content: event.target.value }))}
          />
        </Field>
        <div className="notice-editor-footer">
          <label className="notice-pin-field">
            <input
              type="checkbox"
              checked={form.pinned}
              onChange={(event) => setForm((current) => ({ ...current, pinned: event.target.checked }))}
            />
            상단 고정
          </label>
          {submitError && <span className="notice-submit-error" role="alert">{submitError}</span>}
          {editingId !== null && <button className="btn secondary" type="button" onClick={cancelEdit}>수정 취소</button>}
          <button className="btn" type="submit" disabled={submitting}>
            {submitting ? "저장 중..." : editingId === null ? "공지 등록" : "공지 수정"}
          </button>
        </div>
      </form>
    </section>}

    <section className="mes-card notice-list-section">
      <div className="notice-section-heading">
        <div><h2>전체 공지</h2><p>고정 공지와 최신 공지 순으로 표시됩니다.</p></div>
        <strong>{(notices.data || []).length}건</strong>
      </div>
      {notices.loading && notices.data === null ? <LoadingState/> :
        notices.error && notices.data === null ? <ErrorState error={notices.error} onRetry={notices.reload}/> :
        !(notices.data || []).length ? <EmptyState message="등록된 공지사항이 없습니다."/> :
        <div className="notice-list">
          {notices.data.map((notice) => <article className={`notice-card ${notice.pinned ? "pinned" : ""}`} key={notice.noticeId}>
            <div className="notice-card-heading">
              <div>
                {notice.pinned && <span className="notice-pin-badge">고정</span>}
                <h3>{notice.title}</h3>
              </div>
              <div className="notice-card-actions">
                <time dateTime={notice.createdAt}>{formatDate(notice.createdAt)}</time>
                {isAdmin && <button className="btn secondary small" type="button" onClick={() => edit(notice)}>수정</button>}
              </div>
            </div>
            <p>{notice.content}</p>
            <span className="notice-author">{notice.authorName} ({notice.authorLoginId})</span>
          </article>)}
        </div>}
    </section>
  </div>;
}
