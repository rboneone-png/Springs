
/* ===== 計算核心（SI：mm、N、MPa、N·mm） ===== */
const PI=Math.PI;
const MATS=[
 {n:"琴鋼線 (Music wire)",G:79300,E:206000,A:2211,m:.145,steel:true},
 {n:"油回火鋼線",G:77200,E:203400,A:1855,m:.187,steel:true},
 {n:"不鏽鋼 302/304",G:69000,E:193000,A:1867,m:.146,steel:true},
 {n:"磷青銅",G:41400,E:103400,A:932,m:.064,steel:false},
 {n:"鉻矽鋼線",G:77200,E:203400,A:1974,m:.108,steel:true}];
const sut=(m,d)=>m.A/Math.pow(d,m.m);
const ENDS={cg:{n:"閉端磨平",nt:a=>a+2,ls:(a,d)=>(a+2)*d},c:{n:"閉端不磨",nt:a=>a+2,ls:(a,d)=>(a+3)*d},
 o:{n:"開端不磨",nt:a=>a,ls:(a,d)=>(a+1)*d},og:{n:"開端磨平",nt:a=>a+1,ls:(a,d)=>(a+1)*d}};
const wahl=c=>(4*c-1)/(4*c-4)+.615/c;
const shear=(f,D,d)=>wahl(D/d)*8*f*D/(PI*d**3);
const rate=(G,d,D,na)=>G*d**4/(8*D**3*na);
const initialTension=(d,D)=>{const c=D/d,tau=33.5*6.894757/Math.exp(.105*c),tol=6.894757*(4-(c-3)/6.5),f=t=>Math.max(PI*d**3*t/(8*D),0);return {fi:f(tau),lo:f(tau-tol),hi:f(tau+tol)}};
const allowableLoad=(d,D,k,l0,fi,S,r1,r2)=>{const fb=.45*S/shear(1,D,d),fhb=.65*S/hookBending(1,d,D,r1),fht=.45*S/hookTorsion(1,d,D,r2);
 const L=[["本體",fb],["鉤環彎曲",fhb],["鉤側彎扭轉",fht]].sort((a,b)=>a[1]-b[1]);return {f:L[0][1],gov:L[0][0],lMax:l0+Math.max((L[0][1]-fi)/k,0)}};
const hookBending=(f,d,D,r1)=>{const c1=Math.max(2*r1/d,1.05);const ka=(4*c1*c1-c1-1)/(4*c1*(c1-1));return f*(ka*16*D/(PI*d**3)+4/(PI*d*d))};
const hookTorsion=(f,d,D,r2)=>{const c2=Math.max(2*r2/d,1.05);return (4*c2-1)/(4*c2-4)*8*f*D/(PI*d**3)};
const strengthShear=(t,s)=>{const r=t/s;return {r,v:r<=.45?0:r<=.65?1:2}};
const strengthBending=(t,s)=>{const r=t/s;return {r,v:r<=.65?0:r<=.78?1:2}};
const VL=["✔ 合格","⚠ 需預壓除應力","✘ 超限"];

function compression(d,od,na,l0,lw,m,end){
 const D=od-d,c=D/d,k=rate(m.G,d,D,na),e=ENDS[end],ls=e.ls(na,d);
 const F=k*(l0-lw),Fs=k*(l0-ls);const w=[];
 if(c<4)w.push("旋繞比 C<4，難以成形");if(c>12)w.push("旋繞比 C>12，易纏繞/不穩定");
 if(l0/D>4)w.push("自由長/中徑 >4，注意挫曲，建議加導桿或導套");
 if(lw<ls)w.push("工作長度小於密合高度");if(lw>l0)w.push("工作長度大於自由長度");
 return {D,c,k,nt:e.nt(na),ls,F,tau:shear(F,D,d),Fs,taus:shear(Fs,D,d),w};
}
function fatigue(tmax,tmin,d,m,peen){
 const S=sut(m,d),ssu=.67*S,ssa=peen?398:241,ssm=peen?534:379;
 const sse=Math.min(ssa/Math.max(1-(ssm/ssu)**2,.05),.5*ssu);
 const tm=(tmax+tmin)/2,ta=(tmax-tmin)/2,w=[];
 if(!m.steel)w.push("疲勞係數以鋼線試驗為基礎，此材質結果僅供參考");
 if(tmax>.45*S)w.push("最大應力超過靜態降伏約 0.45Sut，可能永久變形");
 if(ta<=0)w.push("循環應力幅為 0，無疲勞");
 const ssf=tmax>0?.45*S/tmax:Infinity;
 if(ta<=0||tm>=ssu)return {sut:S,sse,ssf,nf:Infinity,N:ta<=0?Infinity:1e3,below:ta>0,w};
 const nf=.5*(ssu/tm)**2*(ta/sse)*(-1+Math.sqrt(1+(2*tm*sse/(ssu*ta))**2));
 const eq=ta/(1-(tm/ssu)**2),s1=.9*ssu;let N,below=false;
 if(eq>=s1){N=1e3;below=true}else if(eq<=sse)N=Infinity;else N=Math.pow(10,3+4*Math.log(s1/eq)/Math.log(s1/sse));
 return {sut:S,sse,ssf,nf,N,below,w};
}
function fatigueBending(smax,smin,d,m,peen){
 const S=sut(m,d),sr=(peen?.67:.45)*S,se=(sr/2)/(1-(sr/2/S)**2);
 const sm=(smax+smin)/2,sa=(smax-smin)/2,w=[];
 if(!m.steel)w.push("疲勞係數以鋼線試驗為基礎，此材質結果僅供參考");
 if(smax>.78*S)w.push("最大彎曲應力超過建議上限 0.78Sut，可能永久變形");
 if(sa<=0)w.push("循環應力幅為 0，無疲勞");
 const ssf=smax>0?.78*S/smax:Infinity;
 if(sa<=0||sm>=S)return {sut:S,sse:se,ssf,nf:Infinity,N:sa<=0?Infinity:1e3,below:sa>0,w};
 const nf=.5*(S/sm)**2*(sa/se)*(-1+Math.sqrt(1+(2*sm*se/(S*sa))**2));
 const eq=sa/(1-(sm/S)**2),s1=.9*S;let N,below=false;
 if(eq>=s1){N=1e3;below=true}else if(eq<=se)N=Infinity;else N=Math.pow(10,3+3*Math.log(s1/eq)/Math.log(s1/se));
 return {sut:S,sse:se,ssf,nf,N,below,w};
}
function torsion(d,od,nb,l1,l2,ang,arm,m){
 const D=od-d,c=D/d,na=nb+(l1+l2)/(3*PI*D),kd=m.E*d**4/(3667*D*na),M=kd*ang;
 const ki=(4*c*c-c-1)/(4*c*(c-1)),sig=ki*32*M/(PI*d**3);
 const dc=D*nb/(nb+ang/360)+d,w=[];
 if(c<4)w.push("旋繞比 C<4，難以成形");if(c>14)w.push("旋繞比 C>14，不穩定");if(ang/360>nb)w.push("扭轉角過大");
 return {D,c,na,kd,M,F:arm>0?M/arm:0,sig,dc,w};
}
function extension(d,od,na,l0,lw,fi,fmax,m,r1,r2){
 const D=od-d,c=D/d,k=rate(m.G,d,D,na),F=fi+k*(lw-l0),w=[];
 if(c<4)w.push("旋繞比 C<4，難以成形");if(c>12)w.push("旋繞比 C>12，初張力難控制");
 if(lw<l0)w.push("工作長度小於自由長度，無拉力");if(F<fi)w.push("負荷小於初張力，彈簧尚未拉開");
 if(2*r1/d<=1||2*r2/d<=1)w.push("鉤環半徑過小（C1、C2 必須大於 1）");
 const fw=Math.max(F,0);
 return {D,c,k,body:(na+1)*d,F,tau:shear(fw,D,d),taui:shear(fi,D,d),taumax:shear(fmax,D,d),
  hb:hookBending(fw,d,D,r1),ht:hookTorsion(fw,d,D,r2),hbm:hookBending(fmax,d,D,r1),htm:hookTorsion(fmax,d,D,r2),w};
}
function extensionFatigue(d,D,k,l0,lw,lmin,fi,r1,r2,m,peen){
 const fo=l=>fi+k*Math.max(l-l0,0),fM=fo(lw),fm=fo(lmin);
 const body=fatigue(shear(fM,D,d),shear(fm,D,d),d,m,peen);
 const hb=fatigueBending(hookBending(fM,d,D,r1),hookBending(fm,d,D,r1),d,m,peen);
 const ht=fatigue(hookTorsion(fM,d,D,r2),hookTorsion(fm,d,D,r2),d,m,peen);
 const all=[["本體",body],["鉤環彎曲",hb],["鉤側彎扭轉",ht]];
 all.sort((a,b)=>a[1].N-b[1].N||a[1].nf-b[1].nf);
 const w=[...new Set([...body.w,...hb.w,...ht.w])];if(lmin>=lw)w.push("循環最小長度需小於工作長度");
 return {body,hb,ht,N:all[0][1].N,below:all[0][1].below,gov:all[0][0],nf:Math.min(body.nf,hb.nf,ht.nf),w};
}

/* ===== 規格設計（依空間、型式、操作位置與力量，搜尋建議的彈簧規格） ===== */
const STD_D=[.2,.25,.3,.35,.4,.45,.5,.55,.6,.7,.8,.9,1,1.1,1.2,1.4,1.5,1.6,1.8,2,2.2,2.5,2.8,3,3.2,3.5,4,4.5,5,5.5,6,6.5,7,8,9,10,11,12,13,14,15,16];
const roundTo=(x,s)=>Math.round(x/s)*s;
const VR={compression:{cLo:4,cHi:12},extension:{cLo:4,cHi:12},torsion:{cLo:4,cHi:14}};
const cmpD=(x,y)=>x===y?0:(x>y?-1:1); // 由大到小（含 Infinity）
function pickDesign(out){
 const pass=out.filter(c=>c.fails.length===0);
 // 排序：預估壽命由最佳到最差（再比疲勞安全係數、最後線材用量少者優先）
 const byLife=(a,b)=>cmpD(a.v.N,b.v.N)||cmpD(a.v.nf,b.v.nf)||a.weight-b.weight;
 const src=pass.length?pass.sort(byLife):out.slice().sort((a,b)=>a.fails.length-b.fails.length||byLife(a,b));
 const seen=new Set(),list=[];
 for(const c of src){if(seen.has(c.d))continue;seen.add(c.d);list.push(c);if(list.length>=6)break}
 list.sort(pass.length?byLife:(a,b)=>a.fails.length-b.fails.length||byLife(a,b));
 return {cands:list,pass:pass.length,total:out.length,ok:pass.length>0};
}
function designCompression(sp){
 const m=sp.mat,E=ENDS[sp.end],kreq=(sp.f2-sp.f1)/(sp.l1-sp.l2),out=[];
 for(const d of STD_D){const S=sut(m,d);
  const Dlo=Math.max((sp.idMin||0)+d,VR.compression.cLo*d),Dhi=Math.min(sp.odMax-d,VR.compression.cHi*d);
  for(let D=Math.ceil(Dlo*4-1e-9)/4;D<=Dhi+1e-9;D+=.25){
   const na=roundTo(m.G*d**4/(8*D**3*kreq),.25);if(na<2)continue;
   const k=rate(m.G,d,D,na),L0=sp.l1+sp.f1/k,F2=sp.f1+k*(sp.l1-sp.l2),ls=E.ls(na,d),Fs=k*(L0-ls);
   const t2=shear(F2,D,d),t1=shear(sp.f1,D,d),st=strengthShear(t2,S),ft=fatigue(t2,t1,d,m,sp.peen),err=Math.abs(F2/sp.f2-1);
   const fails=[],warns=[];
   if(err>sp.tol)fails.push(`力量誤差 ${(err*100).toFixed(1)}% 超過容許 ${(sp.tol*100).toFixed(0)}%`);
   if(sp.l0Max&&L0>sp.l0Max)fails.push("自由長超過空間上限");
   if(Fs<1.15*F2)fails.push("壓並力不足（需 ≥ 1.15×工作力）");
   if(st.v===2)fails.push("剪應力超過 0.65Sut");else if(st.v===1)warns.push("剪應力 >0.45Sut，需預壓除應力");
   if(sp.nTarget&&ft.N<sp.nTarget)fails.push("預估壽命低於目標");
   if(L0/D>4)warns.push("自由長/中徑 >4，建議加導桿");
   if(!m.steel)warns.push("疲勞係數以鋼線為基礎，僅供參考");
   out.push({d,D,na,fails,warns,level:st.v,weight:PI*d*d/4*PI*D*E.nt(na),
    v:{OD:D+d,ID:D-d,nt:E.nt(na),L0,ls,k,F1:sp.f1,F2,Fs,tau2:t2,ratio:st.r,sut:S,nf:ft.nf,N:ft.N,below:ft.below,C:D/d}});
  }}
 return Object.assign({kreq},pickDesign(out));
}
function designExtension(sp){
 const m=sp.mat,kreq=(sp.f2-sp.f1)/(sp.l2-sp.l1),out=[];
 for(const d of STD_D){const S=sut(m,d);
  const Dlo=Math.max((sp.idMin||0)+d,VR.extension.cLo*d),Dhi=Math.min(sp.odMax-d,VR.extension.cHi*d);
  for(let D=Math.ceil(Dlo*4-1e-9)/4;D<=Dhi+1e-9;D+=.25){
   const na=roundTo(m.G*d**4/(8*D**3*kreq),.25);if(na<3)continue;
   const k=rate(m.G,d,D,na),L0=(na+1)*d+2*sp.hookF*D,Fi=sp.f1-k*(sp.l1-L0),F2=sp.f1+k*(sp.l2-sp.l1),it=initialTension(d,D);
   const r1=D/2,r2=2*d,tb=shear(F2,D,d),hb=hookBending(F2,d,D,r1),ht=hookTorsion(F2,d,D,r2);
   const sB=strengthShear(tb,S),sH=strengthBending(hb,S),sT=strengthShear(ht,S),lv=Math.max(sB.v,sH.v,sT.v);
   const ef=Fi>0&&L0<=sp.l1?extensionFatigue(d,D,k,L0,sp.l2,sp.l1,Fi,r1,r2,m,sp.peen):{N:0,below:true,gov:"-"};
   const err=Math.abs(F2/sp.f2-1),fails=[],warns=[];
   if(L0>sp.l1)fails.push("自由長大於安裝長度（無法裝入）");
   if(Fi<=0)fails.push("所需初張力為負值（F1 過小）");
   else if(Fi<it.lo*.85||Fi>it.hi*1.15)fails.push("初張力偏離建議範圍過多");
   else if(Fi<it.lo||Fi>it.hi)warns.push("初張力略超出建議範圍");
   if(err>sp.tol)fails.push(`力量誤差 ${(err*100).toFixed(1)}% 超過容許 ${(sp.tol*100).toFixed(0)}%`);
   if(lv===2)fails.push("應力超過材質安全範圍");else if(lv===1)warns.push("部分應力需預壓除應力");
   if(sp.nTarget&&ef.N<sp.nTarget)fails.push("預估壽命低於目標");
   if(!m.steel)warns.push("疲勞係數以鋼線為基礎，僅供參考");
   out.push({d,D,na,fails,warns,level:lv,weight:PI*d*d/4*PI*D*(na+3),
    v:{OD:D+d,Lb:(na+1)*d,L0,k,Fi,FiLo:it.lo,FiHi:it.hi,F1:sp.f1,F2,tau:tb,hb,ht,rB:sB.r,rH:sH.r,rT:sT.r,N:ef.N,below:ef.below,gov:ef.gov,nf:ef.nf===undefined?0:ef.nf,C:D/d,sut:S}});
  }}
 return Object.assign({kreq},pickDesign(out));
}
function designTorsion(sp){
 const m=sp.mat,M1=sp.f1*sp.armR,M2=sp.f2*sp.armR,kreq=(M2-M1)/(sp.th2-sp.th1),out=[],arms=sp.l1+sp.l2;
 for(const d of STD_D){const S=sut(m,d);
  const Dlo=Math.max((sp.shaftD||0)+d,VR.torsion.cLo*d),Dhi=Math.min(sp.odMax-d,VR.torsion.cHi*d);
  for(let D=Math.ceil(Dlo*4-1e-9)/4;D<=Dhi+1e-9;D+=.25){
   const nb=roundTo(m.E*d**4/(3667*D*kreq)-arms/(3*PI*D),.25);if(nb<2)continue;
   const na=nb+arms/(3*PI*D),k=m.E*d**4/(3667*D*na),M2a=M1+k*(sp.th2-sp.th1),th0=sp.th1-M1/k,thTot=sp.th2-th0;
   const c=D/d,ki=(4*c*c-c-1)/(4*c*(c-1)),sg=M=>ki*32*M/(PI*d**3),s2=sg(M2a),s1=sg(M1);
   const st=strengthBending(s2,S),ft=fatigueBending(s2,s1,d,m,sp.peen),idW=D*nb/(nb+thTot/360)-d,body=(nb+1)*d,err=Math.abs(M2a/M2-1);
   const fails=[],warns=[];
   if(err>sp.tol)fails.push(`扭矩誤差 ${(err*100).toFixed(1)}% 超過容許 ${(sp.tol*100).toFixed(0)}%`);
   if(sp.lenMax&&body>sp.lenMax)fails.push("本體長度超過軸向空間上限");
   if(sp.shaftD&&idW<sp.shaftD*1.02)fails.push("扭緊後內徑小於軸徑（會抱死）");
   if(st.v===2)fails.push("彎曲應力超過 0.78Sut");else if(st.v===1)warns.push("彎曲應力 >0.65Sut，需除應力");
   if(sp.nTarget&&ft.N<sp.nTarget)fails.push("預估壽命低於目標");
   if(!m.steel)warns.push("疲勞係數以鋼線為基礎，僅供參考");
   out.push({d,D,na:nb,fails,warns,level:st.v,weight:PI*d*d/4*PI*D*nb,
    v:{OD:D+d,idW,nb,naEq:na,body,k,M1,M2:M2a,F2:M2a/sp.armR,sigma:s2,ratio:st.r,thTot,N:ft.N,below:ft.below,nf:ft.nf,C:c,sut:S}});
  }}
 return Object.assign({kreq},pickDesign(out));
}
if(typeof module!=="undefined")module.exports={designCompression,designExtension,designTorsion,allowableLoad,initialTension,MATS,compression,fatigue,fatigueBending,torsion,extension,extensionFatigue,strengthShear,strengthBending,sut};


/* ===== 工程圖產生器（A4 橫向；左：規格、右：示意圖）===== */
const DWG=(()=>{
const FONT="'Noto Sans TC','Microsoft JhengHei','PingFang TC','Heiti TC',sans-serif";
const STD=[.05,.1,.2,.25,.4,.5,1,1.5,2,2.5,4,5,10,20,50];
const esc=s=>String(s).replace(/&/g,"&amp;").replace(/</g,"&lt;");
const n2=x=>(+(+x).toFixed(2));
const fx=(v,n=2)=>Number.isFinite(v)?v.toFixed(n):"∞";
function conv(imp){return {imp,L:v=>imp?v/25.4:v,F:v=>imp?v/4.4482216:v,S:v=>imp?v*145.0377:v,R:v=>imp?v*25.4/4.4482216:v,T:v=>imp?v/(4.4482216*25.4):v,
 uL:imp?"in":"mm",uF:imp?"lbf":"N",uS:imp?"psi":"MPa",uR:imp?"lbf/in":"N/mm",uT:imp?"lbf·in":"N·mm",uTR:imp?"lbf·in/°":"N·mm/°"}}
const SUP=e=>String(e).replace(/\d/g,x=>"⁰¹²³⁴⁵⁶⁷⁸⁹"[x]);
function cyc(N,below,inf){return !Number.isFinite(N)?inf:below?"< 10³ 次":"約 "+(()=>{const e=Math.floor(Math.log10(N)),m=N/10**e;return m.toFixed(1)+"×10"+SUP(e)})()+" 次"}

/* ---------- 規格資料 ---------- */
function specComp(inp){
 const m=MATS[inp.mat||0],c=conv(inp.imp),E=ENDS[inp.end||"cg"],d=inp.d,od=inp.od,D=od-d,S=sut(m,d),k=rate(m.G,d,D,inp.na);
 const L=(v,g=2)=>c.L(v).toFixed(g)+" "+c.uL,rows=[{sec:"尺寸"},["線徑 d",L(d,3)],["外徑 OD",L(od)],["內徑 ID",L(od-2*d)],["中徑 D",L(D)],["旋繞比 C",fx(D/d)],
  ["有效圈數 Na",fx(inp.na)],["總圈數 Nt",fx(E.nt(inp.na))],["端部型式",E.n],["自由長 L0",L(inp.l0)],["壓並高度 Ls",L(E.ls(inp.na,d))],{sec:"性能"},["彈簧常數 k",fx(c.R(k),3)+" "+c.uR]];
 const lws=(inp.lw||[]).filter(x=>x>0).slice(0,3);let first=null;
 lws.forEach((lw,i)=>{const r=compression(d,od,inp.na,inp.l0,lw,m,inp.end||"cg"),st=strengthShear(r.tau,S);if(!first)first={r,lw};
  rows.push([`工作長度 L${i+1}`,L(lw)],["　負荷 F",fx(c.F(r.F))+" "+c.uF],["　剪應力 τ",fx(c.S(r.tau),1)+" "+c.uS],["　τ/Sut 判定",fx(st.r)+" "+VL[st.v]])});
 if(first){const lmax=inp.lmax>0?inp.lmax:inp.l0,tmin=lmax<inp.l0?shear(first.r.k*(inp.l0-lmax),D,d):0,ft=fatigue(first.r.tau,tmin,d,m,!!inp.peen);rows.push(["預估壽命（L1）",cyc(ft.N,ft.below,"無限壽命 (>10⁷ 次)")])}
 rows.push({sec:"材料與處理"},["材質",m.n],["抗拉強度 Sut",fx(c.S(S),0)+" "+c.uS],["珠擊處理",inp.peen?"有":"無"]);
 return {name:"壓縮彈簧",code:"C",rows,sum:`Ø${c.L(d).toFixed(2)}×Ø${c.L(od).toFixed(1)}×${c.L(inp.l0).toFixed(1)}`,mat:m.n,dims:{d,od,l0:inp.l0,nt:E.nt(inp.na),na:inp.na},end:inp.end||"cg"}
}
function specTor(inp){
 const m=MATS[inp.mat||0],c=conv(inp.imp),d=inp.d,od=inp.od,D=od-d,S=sut(m,d),nb=inp.nb,arms=inp.l1+inp.l2;
 const L=(v,g=2)=>c.L(v).toFixed(g)+" "+c.uL,b=torsion(d,od,nb,inp.l1,inp.l2,0,inp.armR,m);
 const rows=[{sec:"尺寸"},["線徑 d",L(d,3)],["外徑 OD",L(od)],["內徑 ID",L(od-2*d)],["中徑 D",L(D)],["旋繞比 C",fx(D/d)],["本體圈數 N",fx(nb)],["等效圈數 Na",fx(b.na)],
  ["臂長 L1",L(inp.l1)],["臂長 L2",L(inp.l2)],["受力點半徑 R",L(inp.armR)],["本體長度 (N+1)d",L((nb+1)*d)],{sec:"性能"},["扭力常數 k",fx(c.T(b.kd),3)+" "+c.uTR]];
 const angs=(inp.ang||[]).filter(x=>x>0).slice(0,3);let first=null;
 angs.forEach((a,i)=>{const r=torsion(d,od,nb,inp.l1,inp.l2,a,inp.armR,m),st=strengthBending(r.sig,S);if(!first)first={r,a};
  rows.push([`扭轉角 θ${i+1}`,fx(a,1)+" 度"],["　扭矩 M",fx(c.T(r.M),2)+" "+c.uT],["　受力點負荷 F",fx(c.F(r.F))+" "+c.uF],["　彎曲應力 σ",fx(c.S(r.sig),1)+" "+c.uS],["　σ/Sut 判定",fx(st.r)+" "+VL[st.v]])});
 if(first){const am=inp.amin||0,smin=first.a>0?first.r.sig*am/first.a:0,ft=fatigueBending(first.r.sig,smin,d,m,!!inp.peen);rows.push(["預估壽命（θ1）",cyc(ft.N,ft.below,"無限壽命 (>10⁶ 次)")])}
 rows.push({sec:"材料與處理"},["材質",m.n],["抗拉強度 Sut",fx(c.S(S),0)+" "+c.uS],["珠擊處理",inp.peen?"有":"無"]);
 return {name:"扭力彈簧",code:"T",rows,sum:`Ø${c.L(d).toFixed(2)}×Ø${c.L(od).toFixed(1)}×N${fx(nb)}`,mat:m.n,dims:{d,od,nb,l1:inp.l1,l2:inp.l2,th:angs[0]||90}}
}
function specExt(inp){
 const m=MATS[inp.mat||0],c=conv(inp.imp),d=inp.d,od=inp.od,D=od-d,S=sut(m,d),k=rate(m.G,d,D,inp.na);
 const L=(v,g=2)=>c.L(v).toFixed(g)+" "+c.uL,fm=inp.fm>0?inp.fm:inp.fi;
 const rows=[{sec:"尺寸"},["線徑 d",L(d,3)],["外徑 OD",L(od)],["內徑 ID",L(od-2*d)],["中徑 D",L(D)],["旋繞比 C",fx(D/d)],["有效圈數 Na",fx(inp.na)],["本體長度 Lb",L((inp.na+1)*d)],
  ["自由長 L0（鉤內）",L(inp.l0)],["鉤環中心半徑 r1",L(inp.r1)],["鉤側彎半徑 r2",L(inp.r2)],{sec:"性能"},["彈簧常數 k",fx(c.R(k),3)+" "+c.uR],["初張力 Fi",fx(c.F(inp.fi))+" "+c.uF]];
 const lws=(inp.lw||[]).filter(x=>x>0).slice(0,3);let first=null;
 lws.forEach((lw,i)=>{const r=extension(d,od,inp.na,inp.l0,lw,inp.fi,fm,m,inp.r1,inp.r2),a=strengthShear(r.tau,S),b=strengthBending(r.hb,S);if(!first)first={lw};
  rows.push([`工作長度 L${i+1}`,L(lw)],["　負荷 F",fx(c.F(r.F))+" "+c.uF],["　本體 τ",fx(c.S(r.tau),1)+" "+c.uS],["　τ/Sut 判定",fx(a.r)+" "+VL[a.v]],["　鉤環彎曲 σ",fx(c.S(r.hb),1)+" "+c.uS],["　σ/Sut 判定",fx(b.r)+" "+VL[b.v]])});
 if(first){const lmin=inp.lmin>0?inp.lmin:inp.l0,ef=extensionFatigue(d,D,k,inp.l0,first.lw,lmin,inp.fi,inp.r1,inp.r2,m,!!inp.peen);rows.push([`預估壽命（取最短：${ef.gov}）`,cyc(ef.N,ef.below,"無限壽命")])}
 rows.push({sec:"材料與處理"},["材質",m.n],["抗拉強度 Sut",fx(c.S(S),0)+" "+c.uS],["珠擊處理",inp.peen?"有":"無"]);
 return {name:"拉伸彈簧",code:"E",rows,sum:`Ø${c.L(d).toFixed(2)}×Ø${c.L(od).toFixed(1)}×${c.L(inp.l0).toFixed(1)}`,mat:m.n,dims:{d,od,na:inp.na,l0:inp.l0,r1:inp.r1}}
}
function spec(inp){return inp.type===1?specTor(inp):inp.type===2?specExt(inp):specComp(inp)}

/* ---------- 繪圖基本元件（單位：mm，圖紙座標） ---------- */
const ln=(x1,y1,x2,y2,w=.25,ex="")=>`<line x1="${n2(x1)}" y1="${n2(y1)}" x2="${n2(x2)}" y2="${n2(y2)}" stroke="#000" stroke-width="${w}" ${ex}/>`;
const tx=(x,y,t,sz=3,a="start",ex="")=>{const fl=(ex.match(/fill="([^"]+)"/)||[])[1]||"#000";return `<text x="${n2(x)}" y="${n2(y)}" font-family="${FONT}" style="font-size:${sz}px;text-anchor:${a};dominant-baseline:auto;fill:${fl}" ${ex.replace(/fill="[^"]*"/,"")}>${esc(t)}</text>`};
const rect=(x,y,w,h,sw=.25,fill="none")=>`<rect x="${n2(x)}" y="${n2(y)}" width="${n2(w)}" height="${n2(h)}" fill="${fill}" stroke="#000" stroke-width="${sw}"/>`;
const arrow=(x,y,ang)=>{const a=ang*Math.PI/180,l=2.4,w=.75,bx=x-Math.cos(a)*l,by=y-Math.sin(a)*l,nx=-Math.sin(a)*w,ny=Math.cos(a)*w;return `<polygon points="${n2(x)},${n2(y)} ${n2(bx+nx)},${n2(by+ny)} ${n2(bx-nx)},${n2(by-ny)}" fill="#000"/>`};
function dimH(x1,x2,y,t,e1,e2){let s="";if(e1!==undefined)s+=ln(x1,e1,x1,y+(y>e1?1.5:-1.5),.18);if(e2!==undefined)s+=ln(x2,e2,x2,y+(y>e2?1.5:-1.5),.18);
 return s+ln(x1,y,x2,y,.2)+arrow(x1,y,180)+arrow(x2,y,0)+tx((x1+x2)/2,y-1,t,3,"middle")}
function dimV(x,y1,y2,t,e1,e2){let s="";if(e1!==undefined)s+=ln(e1,y1,x+(x>e1?1.5:-1.5),y1,.18);if(e2!==undefined)s+=ln(e2,y2,x+(x>e2?1.5:-1.5),y2,.18);
 return s+ln(x,y1,x,y2,.2)+arrow(x,y1,-90)+arrow(x,y2,90)+tx(x-1.2,(y1+y2)/2,t,3,"middle",`transform="rotate(-90 ${n2(x-1.2)} ${n2((y1+y2)/2)})"`)}
const wireSeg=(x1,y1,x2,y2,w)=>{const o=Math.max(w,.4);return `<line x1="${n2(x1)}" y1="${n2(y1)}" x2="${n2(x2)}" y2="${n2(y2)}" stroke="#000" stroke-width="${n2(o)}" stroke-linecap="round"/>`+(o>1.1?`<line x1="${n2(x1)}" y1="${n2(y1)}" x2="${n2(x2)}" y2="${n2(y2)}" stroke="#fff" stroke-width="${n2(o-.6)}" stroke-linecap="round"/>`:"")};
const pickScale=(w,h,maxW,maxH)=>{const lim=Math.min(maxW/Math.max(w,1e-6),maxH/Math.max(h,1e-6));let s=STD[0];for(const v of STD)if(v<=lim)s=v;return s};
const scaleTxt=s=>s>=1?(Number.isInteger(s)?s+":1":s+":1"):("1:"+n2(1/s));
const center=(x1,y1,x2,y2)=>ln(x1,y1,x2,y2,.15,'stroke-dasharray="4 1 .6 1"');

/* ---------- 壓縮彈簧圖 ---------- */
function drawComp(sp,A,c){const {d,od,l0,nt}=sp.dims,avW=A.w-56,avH=A.h-24,s=pickScale(od,l0,avW,avH);
 const x0=A.x+28+avW/2,top=A.y+10+(avH-l0*s)/2,bot=top+l0*s,R=od/2*s,w=d*s,n=Math.max(2,Math.round(nt));let o="";
 o+=center(x0,top-5,x0,bot+5);
 const closed=(sp.end||"cg")!=="o"&&(sp.end||"cg")!=="og",p=(l0*s-w)/(n-1);let lead=null;
 for(let i=0;i<n;i++){const y=top+w/2+p*i,flat=closed&&(i===0||i===n-1);
  if(flat)o+=wireSeg(x0-R+w/2,y,x0+R-w/2,y,w);else{o+=wireSeg(x0-R+w/2,y+p/3,x0+R-w/2,y-p/3,w);if(i===1)lead=[x0-R+w/2,y+p/3]}}
 o+=dimV(x0+R+14,top,bot,c.L(l0).toFixed(2),x0+R+1,x0+R+1);
 o+=dimH(x0-R,x0+R,bot+11,"Ø"+c.L(od).toFixed(2),bot+1,bot+1);
 if(lead)o+=ln(lead[0],lead[1],lead[0]-12,lead[1]-8,.2)+tx(lead[0]-12.5,lead[1]-8.5,"Ø"+c.L(d).toFixed(3),3,"end");
 return {svg:o,scale:s}}
/* ---------- 拉伸彈簧圖 ---------- */
function drawExt(sp,A,c){const {d,od,na,l0,r1}=sp.dims,avW=A.w-56,avH=A.h-24,s=pickScale(od,l0,avW,avH);
 const x0=A.x+28+avW/2,top=A.y+10+(avH-l0*s)/2,bot=top+l0*s,R=od/2*s,w=d*s,rc=Math.max(r1*s,w),n=Math.max(3,Math.round(na+1));let o="";
 o+=center(x0,top-5,x0,bot+5);
 const by0=top+2*rc,by1=bot-2*rc,p=(by1-by0-w)/(n-1);let lead=null;
 for(let i=0;i<n;i++){const y=by0+w/2+p*i;o+=wireSeg(x0-R+w/2,y+p/3,x0+R-w/2,y-p/3,w);if(i===1)lead=[x0-R+w/2,y+p/3]}
 for(const cy of [top+rc-w/2,bot-rc+w/2]){const ow=Math.max(w,.4);o+=`<circle cx="${n2(x0)}" cy="${n2(cy)}" r="${n2(rc)}" fill="none" stroke="#000" stroke-width="${n2(ow)}"/>`+(ow>1.1?`<circle cx="${n2(x0)}" cy="${n2(cy)}" r="${n2(rc)}" fill="none" stroke="#fff" stroke-width="${n2(ow-.6)}"/>`:"")}
 o+=dimV(x0+R+14,top,bot,c.L(l0).toFixed(2),x0+rc+1,x0+rc+1);
 o+=dimH(x0-R,x0+R,bot+11,"Ø"+c.L(od).toFixed(2),by1+1,by1+1);
 if(lead)o+=ln(lead[0],lead[1],lead[0]-12,lead[1]-8,.2)+tx(lead[0]-12.5,lead[1]-8.5,"Ø"+c.L(d).toFixed(3),3,"end");
 return {svg:o,scale:s}}
/* ---------- 扭力彈簧圖（端視圖 + 側視圖） ---------- */
function drawTor(sp,A,c){const {d,od,nb,l1,l2,th}=sp.dims,bodyH=(nb+1)*d,up=Math.max(l1,od/2),dn=Math.max(l2,od/2),Hh=up+dn;
 const totW=od+od+22,s=pickScale(totW,Math.max(Hh,bodyH),A.w-44,A.h-26);
 const ex=A.x+22+od/2*s,cy=A.y+12+up*s,rm=(od-d)/2*s,w=d*s;let o="";
 o+=center(ex-od/2*s-5,cy,ex+od/2*s+5,cy)+center(ex,cy-up*s-5,ex,cy+dn*s+5);
 o+=`<circle cx="${n2(ex)}" cy="${n2(cy)}" r="${n2(rm)}" fill="none" stroke="#000" stroke-width="${n2(Math.max(w,.4))}"/>`+(w>1.1?`<circle cx="${n2(ex)}" cy="${n2(cy)}" r="${n2(rm)}" fill="none" stroke="#fff" stroke-width="${n2(w-.6)}"/>`:"");
 o+=wireSeg(ex+rm,cy,ex+rm,cy-l1*s,w)+wireSeg(ex-rm,cy,ex-rm,cy+l2*s,w);
 const a=th*Math.PI/180,P=al=>[ex+rm*Math.cos(al),cy-rm*Math.sin(al)],Dd=al=>[-Math.sin(al),-Math.cos(al)],p2=P(Math.PI+a),d2=Dd(Math.PI+a);
 o+=`<line x1="${n2(p2[0])}" y1="${n2(p2[1])}" x2="${n2(p2[0]+d2[0]*l2*s)}" y2="${n2(p2[1]+d2[1]*l2*s)}" stroke="#000" stroke-width=".3" stroke-dasharray="3 1.5"/>`;
 const ra=Math.max(od/2*s+6,10),A1=P(Math.PI),aa=[ex+ra*Math.cos(Math.PI),cy-ra*Math.sin(Math.PI)],ab=[ex+ra*Math.cos(Math.PI+a),cy-ra*Math.sin(Math.PI+a)];
 o+=`<path d="M${n2(aa[0])},${n2(aa[1])} A${n2(ra)},${n2(ra)} 0 ${th>180?1:0} 0 ${n2(ab[0])},${n2(ab[1])}" fill="none" stroke="#000" stroke-width=".2"/>`+tx(ex-ra-2,cy+(ra*Math.sin(a)/2)+2,"θ="+th.toFixed(0)+"°",3,"end");
 o+=dimV(ex+od/2*s+13,cy-l1*s,cy,c.L(l1).toFixed(2),ex+rm+w/2+1,ex+od/2*s+1);
 o+=dimV(ex-od/2*s-13,cy,cy+l2*s,c.L(l2).toFixed(2),ex-rm-w/2-1,ex-od/2*s-1);
 o+=dimH(ex-od/2*s,ex+od/2*s,cy+dn*s+10,"Ø"+c.L(od).toFixed(2),cy+od/2*s+1,cy+od/2*s+1);
 // 側視圖
 const fx0=A.x+A.w-22-od/2*s,fy=cy-up*s+((up+dn)*s-bodyH*s)/2,fh=bodyH*s,fw=od*s,n=Math.max(2,Math.round(nb));
 o+=center(fx0,fy-5,fx0,fy+fh+5);
 for(let i=0;i<n;i++){const y=fy+w/2+(fh-w)*i/(n-1),p=(fh-w)/(n-1);o+=wireSeg(fx0-fw/2+w/2,y+p/3,fx0+fw/2-w/2,y-p/3,w)}
 o+=dimV(fx0+fw/2+13,fy,fy+fh,c.L(bodyH).toFixed(2),fx0+fw/2+1,fx0+fw/2+1);
 o+=dimH(fx0-fw/2,fx0+fw/2,fy+fh+11,"Ø"+c.L(od).toFixed(2),fy+fh+1,fy+fh+1);
 return {svg:o,scale:s}}

/* ---------- 圖紙 ---------- */
function wrap(t,n){const o=[];let cur="",w=0;for(const ch of t){const cw=ch.charCodeAt(0)>255?2:1;if(w+cw>n){o.push(cur);cur="";w=0}cur+=ch;w+=cw}if(cur)o.push(cur);return o}
function render(inp){
 const sp=spec(inp),c=conv(!!inp.imp),A={x:124,y:12,w:161,h:156};
 const dr=sp.code==="C"?drawComp(sp,A,c):sp.code==="E"?drawExt(sp,A,c):drawTor(sp,A,c);
 const dt=new Date(),pad=n=>String(n).padStart(2,"0"),date=`${dt.getFullYear()}-${pad(dt.getMonth()+1)}-${pad(dt.getDate())}`,no=`SP-${sp.code}-${date.replace(/-/g,"")}-${pad(dt.getHours())}${pad(dt.getMinutes())}`;
 let o=`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 297 210" width="297mm" height="210mm" style="background:#fff">`;
 o+=`<rect x="0" y="0" width="297" height="210" fill="#fff"/>`+rect(5,5,287,200,.35)+rect(10,10,277,190,.7);
 // 左：規格
 o+=rect(10,10,112,8,.4,"#e6e6e6")+tx(66,15.7,"規格 SPECIFICATION",3.6,"middle","font-weight=\"bold\"");
 let y=18;const rh=5.3;
 for(const r of sp.rows){if(y+rh>166)break;
  if(r.sec){o+=rect(10,y,112,rh,.2,"#f1f1f1")+tx(12,y+3.8,r.sec,3,"start","font-weight=\"bold\"")}
  else{o+=ln(10,y+rh,122,y+rh,.12)+ln(62,y,62,y+rh,.12)+tx(12,y+3.8,r[0],2.9)+tx(64,y+3.8,r[1],2.9)}
  y+=rh}
 o+=ln(10,y,122,y,.4);
 const notes=["未註明公差者，依雙方約定；本圖尺寸為計算值，未含公差。","本圖由「彈簧計算器」自動產生，疲勞與強度為教科書估算值，製造前請覆核。","珠擊處理："+(inp.peen?"有。":"無。")+" 材質："+sp.mat+"。"];
 o+=rect(10,168,112,32,.4)+tx(12,172.2,"技術要求 NOTES",3,"start","font-weight=\"bold\"");let ny=176;
 notes.forEach((t,i)=>{const ls=wrap(`${i+1}. ${t}`,50);ls.forEach((l,j)=>{o+=tx(12,ny,l,2.5);ny+=3.3})});
 // 右：示意圖
 o+=rect(122,10,165,158,.4)+tx(124.5,15,"示意圖 DRAWING",3,"start","font-weight=\"bold\"")+`<g>${dr.svg}</g>`;
 // 標題欄
 const T=(x,yy,w,h,k,v,big)=>rect(x,yy,w,h,.3)+tx(x+1.2,yy+3,k,2.2,"start",'fill="#555"')+tx(x+w/2,yy+h-2.6,v,big?4.6:3.2,"middle",big?'font-weight="bold"':"");
 o+=T(122,168,95,11,"品名 TITLE",sp.name+"　"+sp.sum,true)+T(217,168,70,11,"圖號 DWG NO.",no)+
    T(122,179,60,10.5,"材質 MATERIAL",sp.mat)+T(182,179,35,10.5,"比例 SCALE",scaleTxt(dr.scale))+T(217,179,35,10.5,"單位 UNIT",c.uL)+T(252,179,35,10.5,"日期 DATE",date)+
    T(122,189.5,95,10.5,"備註 REMARK","非正式製造圖，僅供設計參考")+T(217,189.5,70,10.5,"繪製 BY","彈簧計算器");
 return o+"</svg>";
}
/* 由計算頁輸入組出 inp（SI：mm、N、MPa）；imp 只影響顯示單位 */
return {render,spec};
})();


