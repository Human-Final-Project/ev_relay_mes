import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import Sidebar from "./Sidebar";
import MesApi from "../api/MesApi";

jest.mock("../api/MesApi", () => ({
  __esModule: true,
  default: { getSystemConnections: jest.fn() },
}));

test("shows live L2 and L1 connection badges instead of the account link", async () => {
  MesApi.getSystemConnections.mockResolvedValue({ data: {
    l2: { status: "ONLINE", collectorId: "L2-COLLECTOR-01" },
    l1: { status: "PARTIAL", connected: 4, total: 6 },
  } });

  render(<MemoryRouter><Sidebar currentUser={{ role: "VIEWER" }}/></MemoryRouter>);

  expect(screen.getByRole("link", { name: "Mini MES 홈으로 이동" })).toHaveAttribute("href", "/");
  expect(screen.getByText("L2 Collector")).toBeInTheDocument();
  expect(await screen.findByText("4/6")).toBeInTheDocument();
  expect(screen.getByText("ONLINE")).toBeInTheDocument();
  expect(screen.getByText("L1 Machines")).toBeInTheDocument();
  expect(screen.queryByRole("link", { name: /내 계정/ })).not.toBeInTheDocument();
});
