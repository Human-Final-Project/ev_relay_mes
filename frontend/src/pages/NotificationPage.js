import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import {
  EmptyState,
  ErrorState,
  Field,
  LoadingState,
  Modal,
  PageHeader,
  StatusBadge,
  formatDate,
} from "../components/MesComponents";
import { notificationTypeLabel } from "../components/NotificationBell";

export default function NotificationPage() {
  const [sort, setSort] = useState("LATEST");
  const [type, setType] = useState("");
  const [read, setRead] = useState("");
  const [selected, setSelected] = useState(null);
  const navigate = useNavigate();
  const result = useApiData(
    () => MesApi.getNotifications({
      limit: 200,
      sort,
      type,
      read: read === "" ? undefined : read === "true",
    }),
    [sort, type, read]
  );

  const openDetail = async (notification) => {
    let detail = notification;
    if (!notification.read) {
      try {
        const response = await MesApi.markNotificationRead(notification.notificationId);
        detail = response.data;
        await result.reload();
      } catch {
        // 읽음 저장 실패가 상세 확인까지 막지는 않는다.
      }
    }
    setSelected(detail);
  };

  const markAllRead = async () => {
    await MesApi.markAllNotificationsRead();
    await result.reload();
  };

  return <div className="mes-page notification-page">
    <PageHeader
      title="알림 이력"
      description="설비 이상과 재고 부족 알림을 확인합니다. 알림 확인은 설비 알람 해제와 별개입니다."
      actions={<button className="btn secondary" onClick={markAllRead}>모두 확인 처리</button>}
    />

    <div className="mes-card mes-filter">
      <Field label="정렬">
        <select value={sort} onChange={(event) => setSort(event.target.value)}>
          <option value="LATEST">최신순</option>
          <option value="SEVERITY">중요도순</option>
        </select>
      </Field>
      <Field label="유형">
        <select value={type} onChange={(event) => setType(event.target.value)}>
          <option value="">전체</option>
          <option value="MACHINE_ALARM">설비 이상</option>
          <option value="LOW_STOCK">재고 부족</option>
        </select>
      </Field>
      <Field label="확인 상태">
        <select value={read} onChange={(event) => setRead(event.target.value)}>
          <option value="">전체</option>
          <option value="false">미확인</option>
          <option value="true">확인</option>
        </select>
      </Field>
      <button className="btn secondary" onClick={result.reload}>새로고침</button>
    </div>

    {result.loading ? <LoadingState/> : result.error ?
      <ErrorState error={result.error} onRetry={result.reload}/> :
      !(result.data || []).length ? <EmptyState message="조건에 맞는 알림이 없습니다."/> :
        <div className="notification-history-list">
          {result.data.map((notification) => <button
            type="button"
            key={notification.notificationId}
            className={`notification-history-card ${notification.read ? "read" : "unread"}`}
            onClick={() => openDetail(notification)}
          >
            <span className={`notification-type-icon severity-${notification.severity.toLowerCase()}`}>
              <span className="material-symbols-outlined">
                {notification.type === "MACHINE_ALARM" ? "warning" : "inventory_2"}
              </span>
            </span>
            <span className="notification-history-copy">
              <span><strong>{notification.title}</strong>{!notification.read && <i/>}</span>
              <small>{notificationTypeLabel(notification.type)} · {notification.message}</small>
            </span>
            <span className="notification-history-meta">
              <StatusBadge value={notification.severity}/>
              {notification.resolvedAt && <StatusBadge value="RESOLVED"/>}
              <time>{formatDate(notification.occurredAt)}</time>
            </span>
          </button>)}
        </div>}

    {selected && <Modal
      title={selected.title}
      onClose={() => setSelected(null)}
      footer={<>
        <button className="btn secondary" onClick={() => setSelected(null)}>닫기</button>
        <button className="btn" onClick={() => navigate(selected.linkPath)}>관련 페이지 보기</button>
      </>}
    >
      <div className="notification-detail">
        <div className="notification-detail-meta">
          <StatusBadge value={selected.severity}/>
          <span>{formatDate(selected.occurredAt)}</span>
          {selected.resolvedAt && <StatusBadge value="RESOLVED"/>}
        </div>
        <p>{selected.message}</p>
        <dl>
          <dt>유형</dt><dd>{notificationTypeLabel(selected.type)}</dd>
          <dt>참조</dt><dd className="mono">{selected.sourceKey}</dd>
          <dt>확인 시각</dt><dd>{formatDate(selected.readAt)}</dd>
        </dl>
      </div>
    </Modal>}
  </div>;
}
