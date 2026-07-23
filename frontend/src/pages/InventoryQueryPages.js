import React, { useMemo } from "react";
import MesApi from "../api/MesApi";
import useApiData from "../hooks/useApiData";
import { EmptyState, ErrorState, LoadingState, PageHeader, StatusBadge, formatDate } from "../components/MesComponents";

function Kpis({ rows }) {
  return <div className="dashboard-kpi-grid inventory-kpis">{rows.map(item=><article className={`dashboard-kpi-card ${item.tone||"blue"}`} key={item.label}><span className="kpi-label">{item.label}</span><div className="kpi-value-row"><strong>{item.value}</strong><span>{item.unit}</span></div><span className="kpi-icon"><span className="material-symbols-outlined">{item.icon}</span></span></article>)}</div>;
}

export function MaterialStockPage() {
  const result = useApiData(MesApi.getMaterialLots, []);
  const rows = useMemo(()=>{
    const map={};
    (result.data||[]).forEach(row=>{
      const value=map[row.itemCode]||(map[row.itemCode]={itemCode:row.itemCode,itemName:row.itemName,availableQty:0,holdQty:0,lotCount:0});
      value.lotCount+=1;
      if(row.status==="AVAILABLE")value.availableQty+=Number(row.currentQty)||0;
      if(row.status==="HOLD")value.holdQty+=Number(row.currentQty)||0;
    });
    return Object.values(map);
  },[result.data]);
  const total=rows.reduce((sum,row)=>sum+row.availableQty,0);
  return <div className="mes-page"><PageHeader title="자재 재고 조회" description="자재 LOT의 현재 수량을 품목별로 집계합니다." actions={<button className="btn secondary" onClick={result.reload}>새로고침</button>}/>
    <Kpis rows={[{label:"자재 품목",value:rows.length,unit:"SKUS",icon:"category"},{label:"사용 가능 재고",value:total,unit:"EA",icon:"inventory_2",tone:"green"},{label:"부족 예상 품목",value:rows.filter(r=>r.availableQty<=100).length,unit:"SKUS",icon:"warning",tone:"red"}]}/>
    {result.loading?<LoadingState/>:result.error?<ErrorState error={result.error}/>:!rows.length?<EmptyState/>:<DataTable heads={["자재 코드","자재명","가용 수량","HOLD 수량","LOT 수","재고 상태"]} rows={rows.map(r=>[r.itemCode,r.itemName,r.availableQty,r.holdQty,r.lotCount,<StatusBadge value={r.availableQty<=100?"WARNING":"AVAILABLE"}/>])}/>}
  </div>;
}

export function MaterialTransactionPage() {
  const result=useApiData(MesApi.getMaterialLots,[]);
  const rows=useMemo(()=>(result.data||[]).flatMap(lot=>{
    const issued=Math.max(0,(Number(lot.receivedQty)||0)-(Number(lot.currentQty)||0));
    const list=[{key:`in-${lot.materialLotId}`,at:lot.receivedAt,type:"IN",lot:lot.materialLotNo,code:lot.itemCode,name:lot.itemName,qty:lot.receivedQty,balance:lot.receivedQty,handler:lot.receivedBy}];
    if(issued)list.push({key:`out-${lot.materialLotId}`,at:null,type:"OUT",lot:lot.materialLotNo,code:lot.itemCode,name:lot.itemName,qty:issued,balance:lot.currentQty,handler:"생산 투입 누적"});
    return list;
  }).sort((a,b)=>String(b.at||"").localeCompare(String(a.at||""))),[result.data]);
  return <div className="mes-page"><PageHeader title="자재 입출고 이력 조회" description="자재 LOT별 입고와 생산 투입에 따른 누적 출고를 조회합니다." actions={<button className="btn secondary" onClick={result.reload}>새로고침</button>}/>
    {result.loading?<LoadingState/>:result.error?<ErrorState error={result.error}/>:!rows.length?<EmptyState/>:<DataTable heads={["처리 시각","구분","자재 LOT","자재","수량","처리 후 수량","처리 정보"]} rows={rows.map(r=>[formatDate(r.at),<StatusBadge value={r.type}/>,r.lot,<>{r.name}<small>{r.code}</small></>,r.qty,r.balance,r.handler])}/>}
  </div>;
}

function productRows(lots) {
  const map={};
  (lots||[]).filter(l=>l.status==="COMPLETED").forEach(l=>{
    const code=l.itemCode||l.orderNo||"FG";
    const value=map[code]||(map[code]={itemCode:code,itemName:l.itemName||"-",qty:0,lots:0,lastAt:null});
    value.qty+=Number(l.okQty)||0; value.lots+=1;
    if(String(l.completedAt||"")>String(value.lastAt||""))value.lastAt=l.completedAt;
  });
  return Object.values(map);
}

export function ProductStockPage() {
  const result=useApiData(MesApi.getLots,[]);
  const rows=useMemo(()=>productRows(result.data),[result.data]);
  return <div className="mes-page"><PageHeader title="제품 재고 조회" description="완료된 제품 LOT의 양품 수량을 기준으로 완제품 재고를 집계합니다." actions={<button className="btn secondary" onClick={result.reload}>새로고침</button>}/>
    <Kpis rows={[{label:"완제품 품목",value:rows.length,unit:"SKUS",icon:"package_2"},{label:"생산 완료 수량",value:rows.reduce((n,r)=>n+r.qty,0),unit:"EA",icon:"inventory",tone:"green"},{label:"완료 LOT",value:rows.reduce((n,r)=>n+r.lots,0),unit:"LOTS",icon:"deployed_code"}]}/>
    {result.loading?<LoadingState/>:result.error?<ErrorState error={result.error}/>:!rows.length?<EmptyState/>:<DataTable heads={["제품 코드","제품명","현재 수량","완료 LOT","최근 입고"]} rows={rows.map(r=>[r.itemCode,r.itemName,r.qty,r.lots,formatDate(r.lastAt)])}/>}
  </div>;
}

export function ProductTransactionPage() {
  const result=useApiData(MesApi.getLots,[]);
  const rows=(result.data||[]).filter(l=>l.status==="COMPLETED").sort((a,b)=>String(b.completedAt||"").localeCompare(String(a.completedAt||"")));
  return <div className="mes-page"><PageHeader title="제품 입출고 이력 조회" description="포장 공정을 완료한 제품 LOT의 입고 이력을 조회합니다." actions={<button className="btn secondary" onClick={result.reload}>새로고침</button>}/>
    {result.loading?<LoadingState/>:result.error?<ErrorState error={result.error}/>:!rows.length?<EmptyState/>:<DataTable heads={["입고 시각","구분","제품 LOT","제품","양품 수량","작업지시"]} rows={rows.map(r=>[formatDate(r.completedAt),<StatusBadge value="IN"/>,r.lotNo,<>{r.itemName}<small>{r.itemCode}</small></>,r.okQty,r.orderNo])}/>}
  </div>;
}

function DataTable({heads,rows}){return <div className="ops-panel"><div className="compact-table-wrap"><table className="compact-dashboard-table management-table"><thead><tr>{heads.map(h=><th key={h}>{h}</th>)}</tr></thead><tbody>{rows.map((row,i)=><tr key={i}>{row.map((value,j)=><td key={j}>{value}</td>)}</tr>)}</tbody></table></div></div>}
