import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import MesApi from "../api/MesApi";
import NotificationBell from "../components/NotificationBell";

jest.mock("../api/MesApi", () => ({
  __esModule: true,
  default: {
    getNotifications: jest.fn(),
    getNotificationUnreadCount: jest.fn(),
    markNotificationRead: jest.fn(),
    markAllNotificationsRead: jest.fn(),
  },
}));

beforeEach(() => {
  jest.clearAllMocks();
  MesApi.getNotifications.mockResolvedValue({data:[{
    notificationId: 1,
    type: "MACHINE_ALARM",
    severity: "ERROR",
    title: "EQ-SEAL-01 설비 이상",
    message: "진공 펌프 이상",
    sourceKey: "22",
    linkPath: "/alarms",
    occurredAt: "2026-07-30T10:00:00",
    read: false,
  }]});
  MesApi.getNotificationUnreadCount.mockResolvedValue({data:{count:1}});
  MesApi.markNotificationRead.mockResolvedValue({data:{
    notificationId: 1,
    type: "MACHINE_ALARM",
    severity: "ERROR",
    title: "EQ-SEAL-01 설비 이상",
    message: "진공 펌프 이상",
    sourceKey: "22",
    linkPath: "/alarms",
    occurredAt: "2026-07-30T10:00:00",
    read: true,
    readAt: "2026-07-30T10:01:00",
  }});
});

test("shows unread dot and opens latest notification detail", async () => {
  render(<MemoryRouter><NotificationBell/></MemoryRouter>);

  expect(await screen.findByLabelText("미확인 알림 1개")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", {name:"알림"}));
  fireEvent.click(await screen.findByRole("button", {name:/EQ-SEAL-01 설비 이상/}));

  await waitFor(() => expect(MesApi.markNotificationRead).toHaveBeenCalledWith(1));
  expect(await screen.findByRole("heading", {name:"EQ-SEAL-01 설비 이상"}))
    .toBeInTheDocument();
  expect(screen.getByText("진공 펌프 이상")).toBeInTheDocument();
});
