import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import DashboardPage from "./DashboardPage";
import MesApi from "../api/MesApi";

jest.mock("../api/MesApi", () => ({
  __esModule: true,
  default: {
    getDashboardSummary: jest.fn(),
    getMachines: jest.fn(),
    getLots: jest.fn(),
    getProductionLogs: jest.fn(),
    getMachineAlarms: jest.fn(),
    clearMachineAlarm: jest.fn(),
  },
}));

test("Stitch 생산 대시보드가 Backend 집계 데이터로 표시된다", async () => {
  MesApi.getDashboardSummary.mockResolvedValue({ data: {
    production: { okQty: 9420, ngQty: 142 },
    machines: { total: 6, running: 5, idle: 0, stopped: 0, error: 1 },
    workOrders: { total: 16, completed: 10, running: 4, created: 2, released: 0 },
    alarms: { active: 1 },
    materials: { lowStockItemCount: 3 },
    generatedAt: "2026-07-22T14:32:05",
  } });
  MesApi.getMachines.mockResolvedValue({ data: [] });
  MesApi.getLots.mockResolvedValue({ data: [{ lotNo: "LOT-1" }] });
  MesApi.getProductionLogs.mockResolvedValue({ data: [] });
  MesApi.getMachineAlarms.mockResolvedValue({ data: [] });

  render(<MemoryRouter><DashboardPage currentUser={{ role: "ADMIN" }}/></MemoryRouter>);

  expect(await screen.findByRole("heading", { name: "생산 대시보드" })).toBeInTheDocument();
  expect(screen.getByText("9,420")).toBeInTheDocument();
  expect(screen.getByText("142")).toBeInTheDocument();
  expect(screen.getByText("1", { selector: ".tone-primary strong" })).toBeInTheDocument();
  expect(screen.getByText("실시간 생산 공정 현황")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "전체보기" })).toHaveAttribute("href", "/production");
});
