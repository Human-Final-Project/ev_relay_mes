import { render, screen } from "@testing-library/react";
import { DonutChart, StackedBarChart, summarizeByProcess } from "./MesCharts";

test("생산 실적을 공정 순서대로 합산한다",()=>{
  const rows=summarizeByProcess([
    {processCode:"OP30",processName:"용접",okQty:8,ngQty:2},
    {processCode:"OP20",processName:"권선",okQty:9,ngQty:1},
    {processCode:"OP20",processName:"권선",okQty:7,ngQty:3},
  ],["OP20","OP30"]);
  expect(rows).toEqual([
    {code:"OP20",name:"권선",ok:16,ng:4},
    {code:"OP30",name:"용접",ok:8,ng:2},
  ]);
});

test("도넛과 막대 차트에 접근 가능한 설명을 제공한다",()=>{
  render(<><DonutChart ariaLabel="양품률 차트" centerValue="90%" centerLabel="양품률" segments={[{label:"OK",value:9,color:"green"},{label:"NG",value:1,color:"red"}]}/><StackedBarChart rows={[{code:"OP20",name:"권선",ok:9,ng:1}]}/></>);
  expect(screen.getByRole("img",{name:"양품률 차트"})).toBeInTheDocument();
  expect(screen.getByRole("img",{name:"공정별 OK 및 NG 수량"})).toBeInTheDocument();
  expect(screen.getByText("90%")).toBeInTheDocument();
});
