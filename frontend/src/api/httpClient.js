import axios from "axios";

const API_BASE_URL =
  process.env.REACT_APP_API_BASE_URL || "http://localhost:8111";

const httpClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  // Backend CookieCsrfTokenRepository와 Springdoc Swagger UI가 같은 쿠키/헤더 규칙을 사용한다.
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
  withXSRFToken: true,
});

export async function refreshCsrfToken() {
  const response = await httpClient.get("/api/auth/csrf");
  const { token, headerName } = response.data || {};

  if (!token || !headerName) {
    throw new Error("Backend did not return a valid CSRF token.");
  }

  // 실제 요청 헤더 첨부는 Axios가 XSRF-TOKEN 쿠키에서 자동 처리한다.
  return response.data;
}

export function clearCsrfToken() {
  // 로그아웃 API가 쿠키를 제거하지만, 화면에서도 남은 토큰 쿠키를 방어적으로 정리한다.
  if (typeof document !== "undefined") {
    document.cookie = "XSRF-TOKEN=; Max-Age=0; Path=/; SameSite=Lax";
  }
}

export default httpClient;
