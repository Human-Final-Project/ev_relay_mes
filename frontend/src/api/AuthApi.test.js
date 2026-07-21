import AuthApi from "./AuthApi";
import httpClient, {
  clearCsrfToken,
  refreshCsrfToken,
} from "./httpClient";

jest.mock("./httpClient", () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
  },
  refreshCsrfToken: jest.fn(),
  clearCsrfToken: jest.fn(),
}));

beforeEach(() => {
  jest.clearAllMocks();
  refreshCsrfToken.mockResolvedValue({
    token: "csrf-token",
    headerName: "X-XSRF-TOKEN",
  });
});

test("fetches CSRF before and after a successful login", async () => {
  const response = { data: { loginId: "admin", role: "ADMIN" } };
  httpClient.post.mockResolvedValue(response);

  await expect(AuthApi.login("admin", "password")).resolves.toBe(response);

  expect(refreshCsrfToken).toHaveBeenCalledTimes(2);
  expect(httpClient.post).toHaveBeenCalledWith("/api/auth/login", {
    loginId: "admin",
    password: "password",
  });
});

test("checks the Backend session after preparing CSRF", async () => {
  const response = { data: { loginId: "admin", role: "ADMIN" } };
  httpClient.get.mockResolvedValue(response);

  await expect(AuthApi.getCurrentUser()).resolves.toBe(response);

  expect(refreshCsrfToken).toHaveBeenCalledTimes(1);
  expect(httpClient.get).toHaveBeenCalledWith("/api/auth/me");
});

test("clears the cached CSRF header after logout", async () => {
  httpClient.post.mockResolvedValue({ status: 204 });

  await AuthApi.logout();

  expect(refreshCsrfToken).toHaveBeenCalledTimes(1);
  expect(httpClient.post).toHaveBeenCalledWith("/api/auth/logout");
  expect(clearCsrfToken).toHaveBeenCalledTimes(1);
});
