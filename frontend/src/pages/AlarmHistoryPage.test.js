import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MesApi from "../api/MesApi";
import AlarmHistoryPage from "./AlarmHistoryPage";

jest.mock("../api/MesApi", () => ({
  __esModule: true,
  default: {
    getMachineAlarms: jest.fn(),
    getMachines: jest.fn(),
    getProcesses: jest.fn(),
    getAlarmCodes: jest.fn(),
    clearMachineAlarm: jest.fn(),
  },
}));

beforeEach(() => {
  jest.clearAllMocks();
  MesApi.getMachines.mockResolvedValue({ data: [] });
  MesApi.getProcesses.mockResolvedValue({ data: [] });
  MesApi.getAlarmCodes.mockResolvedValue({ data: [] });
  MesApi.getMachineAlarms.mockResolvedValue({ data: [{
    machineAlarmHistoryId: 1,
    machineId: "EQ-WELD-01",
    machineName: "접점 용접기",
    processCode: "OP30",
    processName: "접점 가공/용접",
    lotNo: "LOT-001",
    alarmCode: "WELD_POWER_ERROR",
    alarmName: "용접 전원 오류",
    alarmLevel: "ERROR",
    occurredAt: "2026-07-22T10:00:00",
    cleared: false,
    message: "weld_power_error",
  }] });
  MesApi.clearMachineAlarm.mockResolvedValue({ data: {} });
});

test("알람의 설비, 공정, LOT을 표시하고 해제자를 표시하지 않는다", async () => {
  render(<AlarmHistoryPage currentUser={{ role: "OPERATOR" }}/>);

  expect(await screen.findByText("용접 전원 오류")).toBeInTheDocument();
  expect(screen.getByText("LOT-001")).toBeInTheDocument();
  expect(screen.queryByText("해제자")).not.toBeInTheDocument();
});

test("활성 알람 해제 요청을 보낸다", async () => {
  render(<AlarmHistoryPage currentUser={{ role: "OPERATOR" }}/>);

  fireEvent.click(await screen.findByRole("button", { name: "해제" }));
  await waitFor(() => expect(MesApi.clearMachineAlarm).toHaveBeenCalledWith(1));
});
