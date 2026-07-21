import axios from "axios";

const API_BASE_URL =
  process.env.REACT_APP_API_BASE_URL || "http://localhost:8111";

const httpClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
  withXSRFToken: true,
});

let activeCsrfHeaderName = null;

export async function refreshCsrfToken() {
  const response = await httpClient.get("/api/auth/csrf");
  const { token, headerName } = response.data || {};

  if (!token || !headerName) {
    throw new Error("Backend did not return a valid CSRF token.");
  }

  if (activeCsrfHeaderName && activeCsrfHeaderName !== headerName) {
    delete httpClient.defaults.headers.common[activeCsrfHeaderName];
  }

  activeCsrfHeaderName = headerName;
  httpClient.defaults.headers.common[headerName] = token;
  return response.data;
}

export function clearCsrfToken() {
  if (activeCsrfHeaderName) {
    delete httpClient.defaults.headers.common[activeCsrfHeaderName];
    activeCsrfHeaderName = null;
  }
}

export default httpClient;
