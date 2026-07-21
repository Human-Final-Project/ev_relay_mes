import httpClient, {
  clearCsrfToken,
  refreshCsrfToken,
} from "./httpClient";

const AuthApi = {
  async getCurrentUser() {
    await refreshCsrfToken();
    return httpClient.get("/api/auth/me");
  },

  async login(loginId, password) {
    await refreshCsrfToken();
    const response = await httpClient.post("/api/auth/login", {
      loginId,
      password,
    });

    // 로그인 과정에서 세션과 CSRF 토큰이 교체될 수 있으므로 다시 받는다.
    await refreshCsrfToken();
    return response;
  },

  async logout() {
    await refreshCsrfToken();
    try {
      return await httpClient.post("/api/auth/logout");
    } finally {
      clearCsrfToken();
    }
  },
};

export default AuthApi;
