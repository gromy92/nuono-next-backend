function g(r){const n=r.map(l=>Number(l.value||0));return{grid:{bottom:24,containLabel:!0,left:8,right:14,top:32},tooltip:{axisPointer:{lineStyle:{color:"#94a3b8",type:"dashed"},type:"line"},backgroundColor:"rgba(255, 255, 255, 0.96)",borderColor:"rgba(15, 23, 42, 0.08)",borderRadius:8,borderWidth:1,extraCssText:"box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);",formatter:l=>{const i=Array.isArray(l)?l[0]:l,o=typeof i=="object"&&i&&"dataIndex"in i?Number(i.dataIndex):0,e=r[o],c=n[o];return`
          <div style="font-weight:600;color:#111827;margin-bottom:6px;">${(e==null?void 0:e.fullDate)||(e==null?void 0:e.date)||""}</div>
          <div style="display:flex;align-items:center;gap:8px;color:#475569;">
            <span style="display:inline-block;width:8px;height:8px;border-radius:999px;background:#5e3cde;"></span>
            <span>净销量</span>
            <span style="font-weight:700;color:#111827;">${y(c)}</span>
            <span>件</span>
          </div>
        `},padding:[10,12],trigger:"axis"},xAxis:{axisLabel:{color:"#64748b",hideOverlap:!0},axisLine:{lineStyle:{color:"#cbd5e1"}},axisTick:{show:!1},boundaryGap:!1,data:r.map(l=>l.date),type:"category"},yAxis:{axisLabel:{color:"#64748b"},axisTick:{show:!1},minInterval:1,name:"净销量",nameLocation:"end",nameTextStyle:{color:"#64748b",fontWeight:600,padding:[0,0,0,34]},splitLine:{lineStyle:{color:"#e5e7eb",type:"dashed"}},type:"value"},series:[{areaStyle:{color:{colorStops:[{color:"rgba(94, 60, 222, 0.24)",offset:.1},{color:"rgba(94, 60, 222, 0.02)",offset:1}],type:"linear",x:0,x2:0,y:0,y2:1}},data:n,emphasis:{focus:"series"},itemStyle:{borderColor:"#5e3cde",borderWidth:2,color:"#fff"},lineStyle:{color:"#5e3cde",width:2.5},name:"净销量",showSymbol:!0,smooth:!0,symbol:"circle",symbolSize:7,type:"line"}]}}function x(r,n){var m;const l=new Map(r.map(t=>[t.fullDate||t.date,t])),i=new Map(n.map(t=>[t.fullDate||t.date,t])),o=Array.from(new Set([...l.keys(),...i.keys()])).sort().map(t=>{var s,p;return{fullDate:t,date:((s=l.get(t))==null?void 0:s.date)||((p=i.get(t))==null?void 0:p.date)||t}}),e=o.map(t=>{var s;return((s=l.get(t.fullDate))==null?void 0:s.value)??null}),c=o.map(t=>{var s;return((s=i.get(t.fullDate))==null?void 0:s.avgOfferPrice)??null}),b=((m=n.find(t=>t.currencyCode))==null?void 0:m.currencyCode)||"";return{grid:{bottom:24,containLabel:!0,left:8,right:42,top:56},legend:{itemGap:18,right:8,top:6,textStyle:{color:"#64748b"}},tooltip:{axisPointer:{lineStyle:{color:"#94a3b8",type:"dashed"},type:"line"},backgroundColor:"rgba(255, 255, 255, 0.96)",borderColor:"rgba(15, 23, 42, 0.08)",borderRadius:8,borderWidth:1,extraCssText:"box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);",formatter:t=>{const s=Array.isArray(t)?t[0]:t,p=typeof s=="object"&&s&&"dataIndex"in s?Number(s.dataIndex):0,d=o[p],a=d?i.get(d.fullDate):void 0;return`
          <div style="font-weight:600;color:#111827;margin-bottom:6px;">${(d==null?void 0:d.fullDate)||(d==null?void 0:d.date)||""}</div>
          <div style="display:flex;align-items:center;gap:8px;color:#475569;margin-bottom:4px;">
            <span style="display:inline-block;width:8px;height:8px;border-radius:999px;background:#5e3cde;"></span>
            <span>净销量</span>
            <span style="font-weight:700;color:#111827;">${f(e[p])}</span>
            <span>件</span>
          </div>
          <div style="display:flex;align-items:center;gap:8px;color:#475569;margin-bottom:4px;">
            <span style="display:inline-block;width:8px;height:8px;border-radius:999px;background:#0f766e;"></span>
            <span>平均出单价</span>
            <span style="font-weight:700;color:#111827;">${u(a==null?void 0:a.avgOfferPrice)}</span>
            <span>${(a==null?void 0:a.currencyCode)||b}</span>
          </div>
          <div style="color:#64748b;">最低/最高 ${u(a==null?void 0:a.minOfferPrice)} / ${u(a==null?void 0:a.maxOfferPrice)}</div>
          <div style="color:#64748b;">订单行数 ${f(a==null?void 0:a.orderLineCount)} · 币种 ${(a==null?void 0:a.currencyCode)||b||"-"}</div>
        `},padding:[10,12],trigger:"axis"},xAxis:{axisLabel:{color:"#64748b",hideOverlap:!0},axisLine:{lineStyle:{color:"#cbd5e1"}},axisTick:{show:!1},boundaryGap:!1,data:o.map(t=>t.date),type:"category"},yAxis:[{axisLabel:{color:"#64748b"},axisTick:{show:!1},minInterval:1,splitLine:{lineStyle:{color:"#e5e7eb",type:"dashed"}},type:"value"},{axisLabel:{color:"#64748b"},axisTick:{show:!1},splitLine:{show:!1},type:"value"}],series:[{data:e,emphasis:{focus:"series"},itemStyle:{borderColor:"#5e3cde",borderWidth:2,color:"#fff"},lineStyle:{color:"#5e3cde",width:2.5},name:"净销量",showSymbol:!0,smooth:!0,symbol:"circle",symbolSize:7,type:"line",yAxisIndex:0},{connectNulls:!0,data:c,emphasis:{focus:"series"},itemStyle:{borderColor:"#0f766e",borderWidth:2,color:"#fff"},lineStyle:{color:"#0f766e",width:2.5},name:"出单价",showSymbol:!0,smooth:!0,symbol:"circle",symbolSize:7,type:"line",yAxisIndex:1}]}}function h(r,{seriesName:n="分布",unit:l=""}={}){const i=r.map(o=>({name:o.label,value:Number(o.value||0)}));return{legend:{bottom:0,itemGap:12,textStyle:{color:"#64748b"},type:"scroll"},series:[{avoidLabelOverlap:!0,data:i,emphasis:{label:{fontSize:14,fontWeight:700,show:!0}},label:{color:"#334155",formatter:"{b}: {c}",overflow:"break",width:110},name:n,radius:["42%","68%"],type:"pie"}],tooltip:{backgroundColor:"rgba(255, 255, 255, 0.96)",borderColor:"rgba(15, 23, 42, 0.08)",borderRadius:8,borderWidth:1,formatter:o=>{const e=o;return`
          <div style="font-weight:600;color:#111827;margin-bottom:6px;">${e.name||n}</div>
          <div style="color:#475569;">
            <span>${n}</span>
            <span style="font-weight:700;color:#111827;margin-left:8px;">${y(e.value)}</span>
            <span>${l}</span>
            <span style="margin-left:8px;color:#64748b;">${Number(e.percent||0).toFixed(1)}%</span>
          </div>
        `},padding:[10,12],trigger:"item"}}}function v(r,{seriesName:n="数量",unit:l=""}={}){const i=[...r].sort((o,e)=>Number(e.value||0)-Number(o.value||0));return{grid:{bottom:8,containLabel:!0,left:8,right:18,top:18},series:[{barMaxWidth:18,data:i.map(o=>Number(o.value||0)),itemStyle:{borderRadius:[0,8,8,0],color:"#1677ff"},label:{color:"#334155",formatter:`{c}${l}`,position:"right",show:!0},name:n,type:"bar"}],tooltip:{axisPointer:{type:"shadow"},backgroundColor:"rgba(255, 255, 255, 0.96)",borderColor:"rgba(15, 23, 42, 0.08)",borderRadius:8,borderWidth:1,formatter:o=>{const e=Array.isArray(o)?o[0]:o,c=typeof e=="object"&&e&&"name"in e?String(e.name||""):n,b=typeof e=="object"&&e&&"value"in e?Number(e.value||0):0;return`
          <div style="font-weight:600;color:#111827;margin-bottom:6px;">${c}</div>
          <div style="color:#475569;">
            <span>${n}</span>
            <span style="font-weight:700;color:#111827;margin-left:8px;">${y(b)}</span>
            <span>${l}</span>
          </div>
        `},padding:[10,12],trigger:"axis"},xAxis:{axisLabel:{color:"#64748b"},axisTick:{show:!1},minInterval:1,splitLine:{lineStyle:{color:"#e5e7eb",type:"dashed"}},type:"value"},yAxis:{axisLabel:{color:"#334155",interval:0,overflow:"truncate",width:120},axisTick:{show:!1},data:i.map(o=>o.label),inverse:!0,type:"category"}}}function y(r){return Number(r||0).toLocaleString()}function f(r){return typeof r=="number"&&Number.isFinite(r)?r.toLocaleString():"-"}function u(r){return typeof r=="number"&&Number.isFinite(r)?r.toLocaleString(void 0,{minimumFractionDigits:2,maximumFractionDigits:2}):"-"}export{v as a,h as b,x as c,g as d};
