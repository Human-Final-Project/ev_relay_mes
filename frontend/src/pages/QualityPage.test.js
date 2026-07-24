import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MesApi from "../api/MesApi";
import QualityPage from "./QualityPage";

jest.mock("../api/MesApi", () => ({
  __esModule: true,
  default: {
    getInspections: jest.fn(),
    getDefects: jest.fn(),
    getInspectionStandards: jest.fn(),
    getWorkOrders: jest.fn(),
    getLots: jest.fn(),
    getMachines: jest.fn(),
    getProcesses: jest.fn(),
    getDefectCodes: jest.fn(),
    updateInspectionLimits: jest.fn(),
  },
}));

beforeEach(() => {
  MesApi.getInspections.mockResolvedValue({ data: [] });
  MesApi.getDefects.mockResolvedValue({ data: [] });
  MesApi.getInspectionStandards.mockResolvedValue({ data: [] });
  MesApi.getWorkOrders.mockResolvedValue({ data: [
    { workOrderId: 7, orderNo: "WO-007", itemName: "EV Relay" },
  ] });
  MesApi.getLots.mockResolvedValue({ data: [
    { lotId: 71, lotNo: "LOT-007", lotType: "INITIAL", productionRound: 1 },
  ] });
  MesApi.getMachines.mockResolvedValue({ data: [] });
  MesApi.getProcesses.mockResolvedValue({ data: [] });
  MesApi.getDefectCodes.mockResolvedValue({ data: [] });
});

test("품질관리에서도 작업지시 선택 후 해당 LOT를 고른다", async () => {
  render(<QualityPage currentUser={{ role: "VIEWER" }} />);

  const workOrderSelect = await screen.findByLabelText("작업지시");
  const lotSelect = screen.getByLabelText("LOT");
  expect(lotSelect).toBeDisabled();

  fireEvent.change(workOrderSelect, { target: { value: "7" } });

  await waitFor(() => expect(MesApi.getLots).toHaveBeenCalledWith({ workOrderId: "7" }));
  expect(await screen.findByRole("option", { name: "LOT-007 · 최초" })).toBeInTheDocument();
});
