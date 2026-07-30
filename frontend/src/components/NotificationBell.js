import React, { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import MesApi from "../api/MesApi";
import { Modal, StatusBadge, formatDate } from "./MesComponents";

const REFRESH_MS = 10000;

export default function NotificationBell() {
  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [selected, setSelected] = useState(null);
  const wrapperRef = useRef(null);
  const navigate = useNavigate();

  const load = useCallback(async () => {
    try {
      const [listResponse, countResponse] = await Promise.all([
        MesApi.getNotifications({ limit: 5, sort: "LATEST" }),
        MesApi.getNotificationUnreadCount(),
      ]);
      setNotifications(listResponse.data || []);
      setUnreadCount(Number(countResponse.data?.count || 0));
    } catch {
      // 헤더 알림 조회 실패가 다른 화면 동작을 막지 않도록 조용히 재시도한다.
    }
  }, []);

  useEffect(() => {
    load();
    const timer = setInterval(load, REFRESH_MS);
    return () => clearInterval(timer);
  }, [load]);

  useEffect(() => {
    const closeOnOutside = (event) => {
      if (wrapperRef.current && !wrapperRef.current.contains(event.target)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", closeOnOutside);
    return () => document.removeEventListener("mousedown", closeOnOutside);
  }, []);

  const openDetail = async (notification) => {
    let detail = notification;
    if (!notification.read) {
      try {
        const response = await MesApi.markNotificationRead(notification.notificationId);
        detail = response.data;
        setUnreadCount((count) => Math.max(0, count - 1));
        setNotifications((items) => items.map((item) =>
          item.notificationId === detail.notificationId ? detail : item
        ));
      } catch {
        // 상세 내용은 읽음 처리 실패와 관계없이 확인할 수 있다.
      }
    }
    setSelected(detail);
    setOpen(false);
  };

  const markAllRead = async () => {
    await MesApi.markAllNotificationsRead();
    setUnreadCount(0);
    setNotifications((items) => items.map((item) => ({ ...item, read: true })));
  };

  return <div className="notification-center" ref={wrapperRef}>
    <button
      type="button"
      className="icon-button notification-button"
      aria-label="알림"
      aria-expanded={open}
      onClick={() => setOpen((value) => !value)}
    >
      <span className="material-symbols-outlined">notifications</span>
      {unreadCount > 0 && <span className="notification-dot" aria-label={`미확인 알림 ${unreadCount}개`}/>}
    </button>

    {open && <section className="noti-dropdown" aria-label="최근 알림">
      <header className="noti-header">
        <div><strong>최근 알림</strong><span className="noti-count">미확인 {unreadCount}</span></div>
        {unreadCount > 0 && <button type="button" onClick={markAllRead}>모두 확인</button>}
      </header>
      <div className="noti-list">
        {notifications.length === 0
          ? <div className="noti-empty">새 알림이 없습니다.</div>
          : notifications.map((notification) => <button
              type="button"
              key={notification.notificationId}
              className={`noti-item ${notification.severity.toLowerCase()} ${notification.read ? "read" : "unread"}`}
              onClick={() => openDetail(notification)}
            >
              <span className="noti-item-header">
                <span className="noti-title">{notification.title}</span>
                {!notification.read && <i className="noti-unread-dot"/>}
              </span>
              <span className="noti-desc">{notification.message}</span>
              <time className="noti-time">{formatDate(notification.occurredAt)}</time>
            </button>)}
      </div>
      <Link className="noti-all-link" to="/notifications" onClick={() => setOpen(false)}>
        전체 알림 이력 보기
      </Link>
    </section>}

    {selected && <Modal
      title={selected.title}
      onClose={() => setSelected(null)}
      footer={<>
        <button className="btn secondary" onClick={() => setSelected(null)}>닫기</button>
        <button className="btn" onClick={() => {
          setSelected(null);
          navigate(selected.linkPath);
        }}>관련 페이지 보기</button>
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
        </dl>
      </div>
    </Modal>}
  </div>;
}

export function notificationTypeLabel(type) {
  return ({
    MACHINE_ALARM: "설비 이상",
    LOW_STOCK: "재고 부족",
  })[type] || type;
}
