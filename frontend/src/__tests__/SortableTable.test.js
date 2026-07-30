import { fireEvent, render, screen, within } from "@testing-library/react";
import { SortableTh, useSortableRows } from "../components/MesComponents";

const SORTERS = {
  name: (row) => row.name,
  quantity: (row) => row.quantity,
};

function SampleTable() {
  const sorted = useSortableRows([
    { id: 1, name: "부품 10", quantity: 2 },
    { id: 2, name: "부품 2", quantity: 11 },
    { id: 3, name: "부품 1", quantity: 5 },
  ], SORTERS);
  return <table>
    <thead><tr>
      <SortableTh label="품목" sortKey="name" {...sorted}/>
      <SortableTh label="수량" sortKey="quantity" {...sorted}/>
    </tr></thead>
    <tbody>{sorted.rows.map((row) =>
      <tr key={row.id}><td>{row.name}</td><td>{row.quantity}</td></tr>
    )}</tbody>
  </table>;
}

function firstCellValues() {
  return screen.getAllByRole("row").slice(1).map((row) =>
    within(row).getAllByRole("cell")[0].textContent
  );
}

test("sorts text naturally and toggles direction with accessible state", () => {
  render(<SampleTable/>);

  const header = screen.getByRole("columnheader", { name: /품목/ });
  fireEvent.click(within(header).getByRole("button"));
  expect(firstCellValues()).toEqual(["부품 1", "부품 2", "부품 10"]);
  expect(header).toHaveAttribute("aria-sort", "ascending");

  fireEvent.click(within(header).getByRole("button"));
  expect(firstCellValues()).toEqual(["부품 10", "부품 2", "부품 1"]);
  expect(header).toHaveAttribute("aria-sort", "descending");
});

test("sorts numeric values as numbers", () => {
  render(<SampleTable/>);

  fireEvent.click(screen.getByRole("button", { name: /수량/ }));
  expect(firstCellValues()).toEqual(["부품 10", "부품 1", "부품 2"]);
});
