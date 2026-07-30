import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import MesApi from "../api/MesApi";
import NotificationPage from "../pages/NotificationPage";

jest.mock("../api/MesApi", () => ({
  __esModule: true,
  default: {
    getNotifications: jest.fn(),
    markNotificationRead: jest.fn(),
    markAllNotificationsRead: jest.fn(),
  },
}));

beforeEach(() => {
  jest.clearAllMocks();
  MesApi.getNotifications.mockResolvedValue({data:[{
    notificationId: 3,
    type: "LOW_STOCK",
    severity: "WARN",
    title: "구리선 재고 부족",
    message: "가용 재고가 80개입니다.",
    sourceKey: "RM-CU-001",
    linkPath: "/materials",
    occurredAt: "2026-07-30T10:00:00",
    read: false,
  }]});
  MesApi.markNotificationRead.mockResolvedValue({data:{
    notificationId: 3,
    type: "LOW_STOCK",
    severity: "WARN",
    title: "구리선 재고 부족",
    message: "가용 재고가 80개입니다.",
    sourceKey: "RM-CU-001",
    linkPath: "/materials",
    occurredAt: "2026-07-30T10:00:00",
    read: true,
    readAt: "2026-07-30T10:01:00",
  }});
});

test("keeps notification history and supports severity sorting", async () => {
  render(<MemoryRouter><NotificationPage/></MemoryRouter>);

  expect(await screen.findByText("구리선 재고 부족")).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("정렬"), {target:{value:"SEVERITY"}});
  await waitFor(() => expect(MesApi.getNotifications).toHaveBeenLastCalledWith({
    limit: 200,
    sort: "SEVERITY",
    type: "",
    read: undefined,
  }));

  fireEvent.click(await screen.findByRole("button", {name:/구리선 재고 부족/}));
  await waitFor(() => expect(MesApi.markNotificationRead).toHaveBeenCalledWith(3));
  expect(await screen.findByRole("heading", {name:"구리선 재고 부족"}))
    .toBeInTheDocument();
});
