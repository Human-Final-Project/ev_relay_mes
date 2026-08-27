import { fireEvent, render, screen } from "@testing-library/react";
import MesApi from "../api/MesApi";
import AdminEmployeePage from "./AdminEmployeePage";

jest.mock("../api/MesApi", () => ({
  __esModule: true,
  default: {
    getMembers: jest.fn(),
    createMember: jest.fn(),
    updateMember: jest.fn(),
  },
}));

beforeEach(() => {
  jest.clearAllMocks();
  MesApi.getMembers.mockResolvedValue({ data: [] });
});

test("사용자 등록은 ADMIN과 OPERATOR 역할 및 한글 상태만 제공한다", async () => {
  render(<AdminEmployeePage />);

  fireEvent.click(await screen.findByRole("button", { name: "사용자 등록" }));

  expect(screen.getByRole("option", { name: "ADMIN (관리자)" })).toBeInTheDocument();
  expect(screen.getByRole("option", { name: "OPERATOR (운영자)" })).toBeInTheDocument();
  expect(screen.queryByRole("option", { name: "MANAGER" })).not.toBeInTheDocument();
  expect(screen.queryByRole("option", { name: /VIEWER/ })).not.toBeInTheDocument();
  expect(screen.getByRole("option", { name: "사용 가능" })).toBeInTheDocument();
  expect(screen.getByRole("option", { name: "잠김" })).toBeInTheDocument();
  expect(screen.getByRole("option", { name: "퇴사/비활성" })).toBeInTheDocument();
  expect(screen.queryByLabelText("부서")).not.toBeInTheDocument();
  expect(screen.queryByLabelText("직급")).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "비밀번호 초기화" })).not.toBeInTheDocument();
});
