import { render, screen, waitFor } from "@testing-library/react";
import MesApi from "../api/MesApi";
import ProductionPage, { MachineCard } from "./ProductionPage";

jest.mock("../api/MesApi", () => ({
  __esModule: true,
  default: {
    getMachines: jest.fn(),
    getPipelineLots: jest.fn(),
    getMachineAssignments: jest.fn(),
  },
}));

beforeEach(() => {
  MesApi.getMachines.mockResolvedValue({ data: [] });
  MesApi.getPipelineLots.mockResolvedValue({ data: [] });
});

test("가동 중인 공정의 LOT와 진행률을 표시한다", () => {
  render(<MachineCard machine={{
    machineId: "EQ-WIND-01",
    machineName: "권선 설비",
    processCode: "OP20",
    processName: "코일 권선",
    status: "RUNNING",
    currentLotNo: "LOT-001",
    processedQty: 4,
    targetQty: 10,
    progressPercent: 40,
  }} />);

  expect(screen.getByText("LOT-001")).toBeInTheDocument();
  expect(screen.getByText("4 / 10 (40%)")).toBeInTheDocument();
  expect(screen.getByRole("progressbar", { name: "코일 권선 진행률" }))
    .toHaveAttribute("aria-valuenow", "40");
});

test("활성 알람과 생산 실적을 생산 모니터링에서 분리한다", async () => {
  render(<ProductionPage currentUser={{ role: "OPERATOR" }} />);

  expect(await screen.findByRole("heading", { name: "실시간 생산 공정 현황" })).toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "활성 알람" })).not.toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "생산 실적" })).not.toBeInTheDocument();
  expect(screen.queryByText("최근 상태 이력")).not.toBeInTheDocument();
});
test("서로 다른 공정의 여러 LOT을 파이프라인 현황에 표시한다", async () => {
  MesApi.getPipelineLots.mockResolvedValue({ data: [
    { lotId: 1, lotNo: "LOT-001", orderNo: "WO-001", workOrderId: 1, currentProcessCode: "OP60", currentProcessName: "실링", lotType: "INITIAL", productionRound: 1, inputQty: 10, status: "RUNNING", startedAt: "2026-07-22T10:00:00" },
    { lotId: 2, lotNo: "LOT-002", orderNo: "WO-002", workOrderId: 2, currentProcessCode: "OP40_OP50", currentProcessName: "조립", lotType: "INITIAL", productionRound: 1, inputQty: 10, status: "RUNNING", startedAt: "2026-07-22T10:01:00" },
  ] });

  render(<ProductionPage currentUser={{ role: "OPERATOR" }} />);

  expect(await screen.findByRole("heading", { name: "파이프라인 LOT 현황" })).toBeInTheDocument();
  expect(await screen.findByText("LOT-001")).toBeInTheDocument();
  expect(await screen.findByText("LOT-002")).toBeInTheDocument();
  expect(screen.getAllByText("OP60").length).toBeGreaterThan(0);
  expect(screen.getAllByText("OP40_OP50").length).toBeGreaterThan(0);
});


test("OP20과 OP30 병렬 카드에도 생산 진행률을 표시한다", async () => {
  MesApi.getMachines.mockResolvedValue({ data: [
    {
      machineId: "EQ-WIND-01",
      machineName: "코일 권선기",
      processCode: "OP20",
      processName: "코일 권선",
      status: "RUNNING",
      currentLotNo: "LOT-001",
      processedQty: 4,
      targetQty: 10,
      progressPercent: 40,
    },
    {
      machineId: "EQ-WELD-01",
      machineName: "접점 용접기",
      processCode: "OP30",
      processName: "접점 가공/용접",
      status: "RUNNING",
      currentLotNo: "LOT-001",
      processedQty: 6,
      targetQty: 10,
      progressPercent: 60,
    },
  ] });

  render(<ProductionPage/>);

  expect(await screen.findByRole("progressbar", { name: "코일 권선 진행률" }))
    .toHaveAttribute("aria-valuenow", "40");
  expect(screen.getByRole("progressbar", { name: "접점 가공/용접 진행률" }))
    .toHaveAttribute("aria-valuenow", "60");
  expect(screen.getAllByText("LOT-001").length).toBeGreaterThanOrEqual(2);
});
