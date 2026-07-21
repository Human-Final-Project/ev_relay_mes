import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import App from "./App";
import AuthApi from "./api/AuthApi";

jest.mock("./api/AuthApi", () => ({
  __esModule: true,
  default: {
    getCurrentUser: jest.fn(),
    logout: jest.fn(),
  },
}));

jest.mock("./modal/LowStockAlertModal", () => () => null);

const adminUser = {
  memberId: 1,
  loginId: "admin",
  memberName: "시스템 관리자",
  role: "ADMIN",
  status: "ACTIVE",
};

beforeEach(() => {
  jest.clearAllMocks();
  localStorage.clear();
  window.history.pushState({}, "", "/");
});

test("ignores stale local storage when the Backend session is missing", async () => {
  localStorage.setItem("isLoggedIn", "true");
  localStorage.setItem("userRole", "admin");
  AuthApi.getCurrentUser.mockRejectedValue({ response: { status: 401 } });

  render(<App />);

  expect(await screen.findByText("시스템 로그인")).toBeInTheDocument();
  expect(localStorage.getItem("isLoggedIn")).toBeNull();
  expect(localStorage.getItem("userRole")).toBeNull();
});

test("restores an authenticated session and logs out through the Backend", async () => {
  AuthApi.getCurrentUser.mockResolvedValue({ data: adminUser });
  AuthApi.logout.mockResolvedValue({ status: 204 });

  render(<App />);

  expect(await screen.findByText("👤 사원 관리 (Employee)")).toBeInTheDocument();
  expect(localStorage.getItem("userRole")).toBe("admin");
  expect(localStorage.getItem("isLoggedIn")).toBeNull();

  fireEvent.click(screen.getByTitle("로그아웃"));

  await waitFor(() => expect(AuthApi.logout).toHaveBeenCalledTimes(1));
  expect(await screen.findByText("시스템 로그인")).toBeInTheDocument();
  expect(localStorage.getItem("userRole")).toBeNull();
});
