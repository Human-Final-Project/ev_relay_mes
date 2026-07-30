import { fireEvent, render, screen } from "@testing-library/react";
import MesApi from "../api/MesApi";
import AdminEmployeePage from "../pages/AdminEmployeePage";

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

test("사용자 등록은 책임자 연동용 부서와 직급을 함께 입력한다", async () => {
  render(<AdminEmployeePage />);

  fireEvent.click(await screen.findByRole("button", { name: "사용자 등록" }));

  expect(screen.getByRole("option", { name: "ADMIN (관리자)" })).toBeInTheDocument();
  expect(screen.getByRole("option", { name: "OPERATOR (운영자)" })).toBeInTheDocument();
  expect(screen.queryByRole("option", { name: "MANAGER" })).not.toBeInTheDocument();
  expect(screen.queryByRole("option", { name: /VIEWER/ })).not.toBeInTheDocument();
  expect(screen.getByRole("option", { name: "사용 가능" })).toBeInTheDocument();
  expect(screen.getByRole("option", { name: "잠김" })).toBeInTheDocument();
  expect(screen.getByRole("option", { name: "퇴사/비활성" })).toBeInTheDocument();
  expect(screen.getByLabelText("부서")).toBeInTheDocument();
  expect(screen.getByLabelText("직급")).toBeInTheDocument();
  expect(screen.getByLabelText("사번 / 로그인 ID"))
    .toHaveAttribute("placeholder", "EVR00000001");
  expect(screen.getByText("사번은 EVR + 숫자 8자리로 입력합니다."))
    .toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "비밀번호 초기화" })).not.toBeInTheDocument();
});
