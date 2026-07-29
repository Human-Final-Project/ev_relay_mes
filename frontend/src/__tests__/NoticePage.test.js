import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MesApi from "../api/MesApi";
import NoticePage from "../pages/NoticePage";

jest.mock("../api/MesApi", () => ({
  __esModule: true,
  default: {
    getNotices: jest.fn(),
    createNotice: jest.fn(),
    updateNotice: jest.fn(),
  },
}));

const rows = [{
  noticeId: 1,
  title: "라인 점검 안내",
  content: "오후 3시에 설비를 점검합니다.",
  pinned: true,
  authorName: "관리자",
  authorLoginId: "admin",
  createdAt: "2026-07-28T09:00:00",
}];

beforeEach(() => {
  jest.clearAllMocks();
  MesApi.getNotices.mockResolvedValue({ data: rows });
  MesApi.createNotice.mockResolvedValue({ status: 201 });
  MesApi.updateNotice.mockResolvedValue({ status: 200 });
});

test("모든 사용자가 공지사항을 조회한다", async () => {
  render(<NoticePage currentUser={{ role: "OPERATOR" }}/>);
  expect(await screen.findByText("라인 점검 안내")).toBeInTheDocument();
  expect(screen.getByText("오후 3시에 설비를 점검합니다.")).toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "공지 작성" })).not.toBeInTheDocument();
});

test("관리자는 공지사항을 작성한다", async () => {
  render(<NoticePage currentUser={{ role: "ADMIN" }}/>);
  await screen.findByText("라인 점검 안내");
  fireEvent.change(screen.getByLabelText("제목"), { target: { value: "신규 공지" } });
  fireEvent.change(screen.getByLabelText("내용"), { target: { value: "신규 공지 내용" } });
  fireEvent.click(screen.getByLabelText("상단 고정"));
  fireEvent.click(screen.getByRole("button", { name: "공지 등록" }));
  await waitFor(() => expect(MesApi.createNotice).toHaveBeenCalledWith({
    title: "신규 공지",
    content: "신규 공지 내용",
    pinned: true,
  }));
  expect(MesApi.getNotices).toHaveBeenCalledTimes(2);
});

test("관리자는 기존 공지사항을 수정한다", async () => {
  render(<NoticePage currentUser={{ role: "ADMIN" }}/>);
  await screen.findByText("라인 점검 안내");
  fireEvent.click(screen.getByRole("button", { name: "수정" }));
  expect(screen.getByRole("heading", { name: "공지 수정" })).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("제목"), { target: { value: "수정된 안내" } });
  fireEvent.click(screen.getByRole("button", { name: "공지 수정" }));
  await waitFor(() => expect(MesApi.updateNotice).toHaveBeenCalledWith(1, {
    title: "수정된 안내",
    content: "오후 3시에 설비를 점검합니다.",
    pinned: true,
  }));
  expect(MesApi.getNotices).toHaveBeenCalledTimes(2);
});
