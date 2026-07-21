import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import AuthApi from "../api/AuthApi";
import LoginPage from "./LoginPage";

jest.mock("../api/AuthApi", () => ({
  __esModule: true,
  default: {
    login: jest.fn(),
  },
}));

beforeEach(() => {
  jest.clearAllMocks();
});

test("authenticates with the Backend instead of comparing hardcoded credentials", async () => {
  const user = {
    memberId: 1,
    loginId: "admin",
    memberName: "시스템 관리자",
    role: "ADMIN",
    status: "ACTIVE",
  };
  const onLoginSuccess = jest.fn();
  AuthApi.login.mockResolvedValue({ data: user });

  render(
    <MemoryRouter>
      <LoginPage onLoginSuccess={onLoginSuccess} />
    </MemoryRouter>
  );

  fireEvent.change(screen.getByLabelText("사원번호"), {
    target: { value: " admin " },
  });
  fireEvent.change(screen.getByLabelText("비밀번호"), {
    target: { value: "admin1234!" },
  });
  fireEvent.click(screen.getByRole("button", { name: "로그인 arrow_forward" }));

  await waitFor(() =>
    expect(AuthApi.login).toHaveBeenCalledWith("admin", "admin1234!")
  );
  expect(onLoginSuccess).toHaveBeenCalledWith(user);
});

test("shows the Backend authentication error message", async () => {
  AuthApi.login.mockRejectedValue({
    response: { data: { message: "로그인 정보를 확인해 주세요." } },
  });

  render(
    <MemoryRouter>
      <LoginPage onLoginSuccess={jest.fn()} />
    </MemoryRouter>
  );

  fireEvent.change(screen.getByLabelText("사원번호"), {
    target: { value: "admin" },
  });
  fireEvent.change(screen.getByLabelText("비밀번호"), {
    target: { value: "wrong" },
  });
  fireEvent.click(screen.getByRole("button", { name: "로그인 arrow_forward" }));

  expect(
    await screen.findByText("로그인 정보를 확인해 주세요.")
  ).toBeInTheDocument();
});
