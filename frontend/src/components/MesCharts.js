import React from "react";

const CIRCUMFERENCE=2*Math.PI*48;

export function DonutChart({segments=[],centerValue,centerLabel,ariaLabel="분포 차트"}){
  const total=segments.reduce((sum,segment)=>sum+Math.max(0,Number(segment.value)||0),0);
  let offset=0;
  return <div className="donut-chart-wrap">
    <div className="donut-chart" role="img" aria-label={ariaLabel}>
      <svg viewBox="0 0 120 120" aria-hidden="true">
        <circle className="donut-track" cx="60" cy="60" r="48"/>
        {total>0&&segments.map(segment=>{
          const length=(Math.max(0,Number(segment.value)||0)/total)*CIRCUMFERENCE;
          const strokeOffset=offset;
          offset+=length;
          return <circle key={segment.label} className="donut-segment" cx="60" cy="60" r="48" stroke={segment.color} strokeDasharray={`${length} ${CIRCUMFERENCE-length}`} strokeDashoffset={-strokeOffset}/>;
        })}
      </svg>
      <div className="donut-center"><strong>{centerValue}</strong><span>{centerLabel}</span></div>
    </div>
    <div className="chart-legend">{segments.map(segment=><div key={segment.label}><span className="legend-dot" style={{background:segment.color}}/><span>{segment.label}</span><strong>{segment.value}</strong></div>)}</div>
  </div>;
}

export function StackedBarChart({rows=[],emptyMessage="표시할 생산 실적이 없습니다."}){
  const visible=rows.filter(row=>(Number(row.ok)||0)+(Number(row.ng)||0)>0);
  const max=Math.max(1,...visible.map(row=>(Number(row.ok)||0)+(Number(row.ng)||0)));
  if(!visible.length)return <div className="chart-empty">{emptyMessage}</div>;
  return <div className="stacked-chart" role="img" aria-label="공정별 OK 및 NG 수량">
    {visible.map(row=>{
      const ok=Number(row.ok)||0,ng=Number(row.ng)||0,total=ok+ng;
      return <div className="stacked-row" key={row.code||row.label}>
        <div className="stacked-label"><strong>{row.code||row.label}</strong><span>{row.name||""}</span></div>
        <div className="stacked-track" title={`OK ${ok}, NG ${ng}`}>
          <span className="stacked-ok" style={{width:`${ok/max*100}%`}}/>
          <span className="stacked-ng" style={{width:`${ng/max*100}%`}}/>
        </div>
        <div className="stacked-value"><strong>{total}</strong><span>OK {ok} · NG {ng}</span></div>
      </div>;
    })}
    <div className="stacked-scale"><span>0</span><span>최대 {max}</span></div>
  </div>;
}

export function summarizeByProcess(logs=[],processOrder=[]){
  const byCode=new Map();
  logs.forEach(log=>{
    const current=byCode.get(log.processCode)||{code:log.processCode,name:log.processName||"",ok:0,ng:0};
    current.ok+=Number(log.okQty)||0;
    current.ng+=Number(log.ngQty)||0;
    byCode.set(log.processCode,current);
  });
  return [...byCode.values()].sort((a,b)=>{
    const left=processOrder.indexOf(a.code),right=processOrder.indexOf(b.code);
    return (left<0?Number.MAX_SAFE_INTEGER:left)-(right<0?Number.MAX_SAFE_INTEGER:right);
  });
}
