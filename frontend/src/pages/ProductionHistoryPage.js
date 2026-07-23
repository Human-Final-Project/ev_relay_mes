import React, { useMemo, useState } from "react";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { EmptyState, ErrorState, Field, LoadingState, PageHeader, formatDate } from "../components/MesComponents";
import { DonutChart, StackedBarChart, summarizeByProcess } from "../components/MesCharts";

const PROCESS_ORDER=["OP20","OP30","OP40_OP50","OP60","OP70","OP80"];

export default function ProductionHistoryPage({report=false}) {
  const [filters,setFilters]=useState({lotNo:"",processCode:"",machineId:""});
  const [applied,setApplied]=useState({});
  const result=useApiData(()=>MesApi.getProductionLogs(applied),[JSON.stringify(applied)]);
  const summary=useMemo(()=>{
    const rows=result.data||[]; const ok=rows.reduce((n,r)=>n+(Number(r.okQty)||0),0); const ng=rows.reduce((n,r)=>n+(Number(r.ngQty)||0),0);
    return {ok,ng,processes:summarizeByProcess(rows,PROCESS_ORDER)};
  },[result.data]);
  const total=summary.ok+summary.ng;
  return <div className="mes-page"><PageHeader title={report?"생산 리포트":"공정 이력"} description={report?"공정별 생산량과 양품률을 집계합니다.":"LOT·공정·설비별 생산 실적을 조회합니다."} actions={<button className="btn secondary" onClick={result.reload}>새로고침</button>}/>
    <div className="mes-card mes-filter"><Field label="LOT"><input value={filters.lotNo} onChange={e=>setFilters({...filters,lotNo:e.target.value})}/></Field><Field label="공정"><select value={filters.processCode} onChange={e=>setFilters({...filters,processCode:e.target.value})}><option value="">전체</option>{PROCESS_ORDER.map(p=><option key={p}>{p}</option>)}</select></Field><Field label="설비"><input value={filters.machineId} onChange={e=>setFilters({...filters,machineId:e.target.value})}/></Field><button className="btn" onClick={()=>setApplied(Object.fromEntries(Object.entries(filters).filter(([,v])=>v)))}>조회</button></div>
    {result.loading?<LoadingState/>:result.error?<ErrorState error={result.error}/>:!(result.data||[]).length?<EmptyState/>:<>
      {report&&<div className="production-analytics"><div><div className="chart-heading"><div><h3>공정별 생산량</h3><p>조회 조건 기준 OK / NG 수량</p></div></div><StackedBarChart rows={summary.processes}/></div><DonutChart ariaLabel="양품률" centerValue={`${total?(summary.ok/total*100).toFixed(1):0}%`} centerLabel="양품률" segments={[{label:"OK",value:summary.ok,color:"#0ea5a4"},{label:"NG",value:summary.ng,color:"#f43f5e"}]}/></div>}
      <div className="ops-panel"><div className="compact-table-wrap"><table className="compact-dashboard-table management-table"><thead><tr><th>LOT</th><th>공정</th><th>설비</th><th>투입</th><th>OK</th><th>NG</th><th>양품률</th><th>종료 시각</th></tr></thead><tbody>{result.data.map(r=><tr key={r.productionLogId}><td><strong className="mono lot-link">{r.lotNo}</strong></td><td>{r.processName}<small>{r.processCode}</small></td><td>{r.machineId}</td><td>{r.inputQty}</td><td className="ok-number">{r.okQty}</td><td className="ng-number">{r.ngQty}</td><td>{r.inputQty?`${(r.okQty/r.inputQty*100).toFixed(1)}%`:"-"}</td><td>{formatDate(r.endedAt)}</td></tr>)}</tbody></table></div></div>
    </>}
  </div>;
}
