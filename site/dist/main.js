import { animate, createTimeline, stagger, onScroll } from './assets/anime.js';
const $ = (selector) => document.querySelector(selector);
const icons = () => window.lucide?.createIcons();
const reduced = matchMedia('(prefers-reduced-motion: reduce)');
icons();
const menu = $('.menu-toggle');
menu.addEventListener('click', () => { const open = menu.getAttribute('aria-expanded') !== 'true'; menu.setAttribute('aria-expanded', String(open)); menu.setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation'); $('#mobile-nav').hidden = !open; });
$('#mobile-nav').addEventListener('click', e => { if (e.target.closest('a')) { menu.setAttribute('aria-expanded', 'false'); menu.setAttribute('aria-label', 'Open navigation'); $('#mobile-nav').hidden = true; } });
document.addEventListener('keydown', e => { if(e.key === 'Escape' && !$('#mobile-nav').hidden){$('#mobile-nav').hidden = true;menu.setAttribute('aria-expanded','false');menu.setAttribute('aria-label','Open navigation');menu.focus();} });

const featureData = [
  { tag:'SCAN. READ. COMPARE.', title:'Inspect the recipient before paying.', description:'Scan a UPI QR and inspect the recipient before paying. Compare it with the person or business in front of you.', limit:'QR decoding is available. Physical sticker-tamper detection is still in development.', preview:'<div class="mini-card"><header><span>UPI QR · EXAMPLE</span><i data-lucide="scan-line"></i></header><div class="mini-recipient">Corner Coffee</div><div class="mini-meta">coffee@bank · ₹180.00</div><div class="mini-status"><i data-lucide="eye"></i> Confirm the recipient in person</div></div>' },
  { tag:'LOCAL RECORDS. CLEAR LIMITATIONS.', title:'Review the available risk information.', description:'Check an ID against records stored on your phone, or open the official I4C suspect search. Review the source and coverage of every result.', limit:'No fabricated records are included in the app. An automated authoritative feed is not connected.', preview:'<div class="mini-card"><header><span>FRAUDCHECK · EXAMPLE</span><i data-lucide="shield"></i></header><div class="mini-recipient">coffee@bank</div><div class="mini-meta">No match in the local list</div><div class="mini-status"><i data-lucide="circle-help"></i> Unknown does not mean safe</div></div>' },
  { tag:'THREE QUESTIONS. EXPLAINED RESULTS.', title:'Assess a request to move money.', description:'Review requests to receive and forward money. Three questions identify common warning signals, including commissions, urgency, and secrecy.', limit:'Educational guidance based on your answers. No questionnaire can rule out every scam.', preview:'<div class="mini-card"><header><span>MULEGUARD · QUESTION 01 / 03</span><i data-lucide="triangle-alert"></i></header><h4>Has someone asked you to receive money and send it on?</h4><div class="mini-answer"><span>Yes</span><span>No</span></div></div>' },
  { tag:'PREPARE. REVIEW. CONTACT SUPPORT.', title:'Prepare a transaction support report.', description:'Prepare a report with the recipient, amount and transaction reference. Save it locally, then share it with your payment provider or bank.', limit:'PayDar does not submit disputes or reverse payments. Official providers handle complaints.', preview:'<div class="mini-card"><header><span>WRONGPAY RESCUE</span><i data-lucide="undo-2"></i></header><h4>Prepare your recovery report.</h4><div class="mini-step"><b>1</b> Add transaction details</div><div class="mini-step"><b>2</b> Review your report</div><div class="mini-step"><b>3</b> Contact official support</div></div>' },
  { tag:'RECURRING PAYMENTS. ONE VIEW.', title:'Keep recurring charges visible.', description:'Turn payment messages into a list you can review. Track recurring amounts, edit the details and set local reminders.', limit:'Paste messages or choose SMS import. Recurring charges and estimated dates need your confirmation.', preview:'<div class="mini-card"><header><span>SUBWATCHER · EXAMPLE DATA</span><i data-lucide="bell"></i></header><div class="mini-row"><span class="service-icon">S</span><span>Spotify</span><span>₹119</span></div><div class="mini-row"><span class="service-icon netflix">N</span><span>Netflix</span><span>₹499</span></div><div class="mini-status">Estimated monthly total <b>₹618</b></div></div>' }
];
const tabs = [...document.querySelectorAll('[data-feature]')];
function selectFeature(index, focus = false){
  const feature = featureData[index];
  tabs.forEach((tab,i)=>{tab.setAttribute('aria-selected',String(i===index));tab.tabIndex=i===index?0:-1;});
  $('#feature-panel').setAttribute('aria-labelledby',tabs[index].id);
  $('#panel-tag').textContent=feature.tag;$('#panel-title').textContent=feature.title;$('#panel-description').textContent=feature.description;$('#panel-limit').textContent=feature.limit;$('#feature-preview').innerHTML=feature.preview;icons();
  if(!reduced.matches)animate('.feature-preview .mini-card',{opacity:[0,1],translateY:[14,0],duration:460,ease:'outQuart'});
  if(focus)tabs[index].focus();
  if (matchMedia('(max-width:760px)').matches) { const list=tabs[index].parentElement; list.scrollTo({left:tabs[index].offsetLeft-list.offsetLeft-12,behavior:reduced.matches?'instant':'smooth'}); }
}
tabs.forEach((tab,index)=>{tab.addEventListener('click',()=>selectFeature(index));tab.addEventListener('keydown',e=>{let next=index;if((e.key==='ArrowDown'||e.key==='ArrowRight'))next=(index+1)%tabs.length;else if((e.key==='ArrowUp'||e.key==='ArrowLeft'))next=(index+tabs.length-1)%tabs.length;else if(e.key==='Home')next=0;else if(e.key==='End')next=tabs.length-1;else return;e.preventDefault();selectFeature(next,true);});});
selectFeature(0);
const compactTools=matchMedia('(max-width:760px)');
const orientTools=()=>$('.feature-tabs').setAttribute('aria-orientation',compactTools.matches?'horizontal':'vertical');
compactTools.addEventListener('change',orientTools);orientTools();

export function checkId(raw){
  const upi=raw.trim().toLowerCase();
  if(!/^[a-z0-9][a-z0-9._-]{1,127}@[a-z][a-z0-9.-]{1,63}$/.test(upi))return{tone:'warning',icon:'circle-alert',title:'That doesn’t look like a UPI ID.',message:'Use the format name@bank. This preview checks conventional UPI IDs; it does not validate numeric UPI aliases.'};
  return{tone:'warning',icon:'circle-help',title:'Unverified. Confirm before paying.',message:'This browser preview has no authoritative fraud feed. Confirm the recipient name and ID independently in your payment app.'};
}
$('#check-form').addEventListener('submit',event=>{event.preventDefault();const result=checkId($('#upi-id').value);const target=$('#check-result');target.className=`check-result ${result.tone}`;target.replaceChildren();const icon=document.createElement('i');icon.dataset.lucide=result.icon;const heading=document.createElement('h3');heading.textContent=result.title;const p=document.createElement('p');p.textContent=result.message;target.append(icon,heading,p);icons();});
document.querySelectorAll('[data-sample]').forEach(button=>button.addEventListener('click',()=>{$('#upi-id').value=button.dataset.sample;$('#check-form').requestSubmit();}));

const motion=[];
if(!reduced.matches){
  motion.push(createTimeline({defaults:{ease:'outQuart',duration:900}})
    .add('.hero-copy .eyebrow',{opacity:[0,1],translateY:[14,0]},0)
    .add('.hero h1',{opacity:[0,1],translateY:[50,0]},100)
    .add('.hero-description,.hero-detail,.hero-actions,.hero-proof',{opacity:[0,1],translateY:[22,0],delay:stagger(85)},280)
    .add('.floating-note',{opacity:[0,1],translateY:[30,0],delay:stagger(160)},650));
  document.querySelectorAll('.reveal').forEach(el=>motion.push(animate(el,{opacity:[0,1],translateY:[32,0],duration:750,ease:'outQuart',autoplay:onScroll({target:el,enter:'bottom-=30 top',repeat:false})})));
  motion.push(animate('.privacy-zero',{translateY:[-35,35],ease:'linear',autoplay:onScroll({target:'.privacy',enter:'bottom top',leave:'top bottom',sync:.6})}));
  motion.push(animate('.radar-axis',{rotate:[-15,40],ease:'linear',autoplay:onScroll({target:'.hero',enter:'top top',leave:'top bottom',sync:.5})}));
  motion.push(animate('.feature-strip span',{translateX:[30,-30],ease:'linear',autoplay:onScroll({target:'.feature-strip',enter:'bottom top',leave:'top bottom',sync:true})}));
}
reduced.addEventListener('change',()=>{if(reduced.matches)motion.forEach(m=>m.revert());});
// The attributed phone model carries the silhouette; its screen uses a local UI texture.
function mapScreen(THREE, model, texture) {
  texture.flipY = false;
  model.traverse(mesh => {
    if (!mesh.isMesh || !mesh.material?.name?.toLowerCase().includes('screen')) return;
    const geometry = mesh.geometry.clone();
    geometry.computeBoundingBox();
    const { min, max } = geometry.boundingBox;
    const positions = geometry.attributes.position;
    const uv = new Float32Array(positions.count * 2);
    for (let i = 0; i < positions.count; i++) {
      uv[i * 2] = (max.y - positions.getY(i)) / (max.y - min.y);
      uv[i * 2 + 1] = (max.z - positions.getZ(i)) / (max.z - min.z);
    }
    geometry.setAttribute('uv', new THREE.BufferAttribute(uv, 2));
    mesh.geometry = geometry;
    mesh.material = new THREE.MeshBasicMaterial({map:texture,toneMapped:false,side:THREE.DoubleSide});
  });
}
// Share the model download and parse, but keep each scene's materials independent.
let phoneAsset;
async function makeStudio(canvas, host, height) {
  const THREE=await import('three');
  const {GLTFLoader}=await import('three/addons/loaders/GLTFLoader.js');
  const renderer=new THREE.WebGLRenderer({canvas,alpha:true,antialias:true,powerPreference:'low-power'});
  renderer.setPixelRatio(Math.min(devicePixelRatio,matchMedia('(max-width:760px)').matches?1.4:1.75));
  renderer.outputColorSpace=THREE.SRGBColorSpace;
  renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=1.15;
  const scene=new THREE.Scene();
  const camera=new THREE.PerspectiveCamera(33,1,.1,100);camera.position.set(0,.03,7.5);
  scene.add(new THREE.AmbientLight(0xffffff,1.8));
  for(const [color,intensity,x,y,z] of [[0xffffff,4,-3,5,5],[0xe5ffb3,3,4,1,-2],[0xb19aff,2,-4,-1,2]]) {
    const light=new THREE.DirectionalLight(color,intensity);light.position.set(x,y,z);scene.add(light);
  }
  // Large luminous panels produce continuous reflections across the metal edges.
  const studio=new THREE.Scene();studio.background=new THREE.Color('#25262c');
  for(const [x,y,z,w,h,color] of [[-4,2,2,2,7,0xffffff],[4,0,1,1,6,0xe9edcf],[0,5,0,7,2,0xffffff],[-3,-2,-3,2,4,0x988ac2]]) {
    const panel=new THREE.Mesh(new THREE.PlaneGeometry(w,h),new THREE.MeshBasicMaterial({color,side:THREE.DoubleSide}));
    panel.position.set(x,y,z);panel.lookAt(0,0,0);studio.add(panel);
  }
  const pmrem=new THREE.PMREMGenerator(renderer);
  const environment=pmrem.fromScene(studio,.12);scene.environment=environment.texture;
  pmrem.dispose();studio.traverse(o=>{if(o.isMesh){o.geometry.dispose();o.material.dispose();}});
  phoneAsset??=new GLTFLoader().loadAsync('assets/phone-pro.glb');
  const model=(await phoneAsset).scene.clone(true);
  const box=new THREE.Box3().setFromObject(model),size=box.getSize(new THREE.Vector3()),center=box.getCenter(new THREE.Vector3());
  const scale=height/size.y;model.scale.setScalar(scale);model.position.copy(center.multiplyScalar(-scale));
  model.traverse(obj=>{
    if(!obj.isMesh)return;
    obj.material=obj.material.clone();const m=obj.material,n=m.name.toLowerCase();
    if(/screen|glass|lens|black|material.007/.test(n))return;
    m.color.set(/frame|metal/.test(n)?0x70717a:0x272830);
    m.metalness=/frame|metal/.test(n)?.95:.55;
    m.roughness=/frame|metal/.test(n)?.22:.34;
    m.envMapIntensity=1.1;
  });
  const rig=new THREE.Group();rig.add(model);scene.add(rig);
  const resize=()=>{const {width,height}=host.getBoundingClientRect();if(!width||!height)return;renderer.setSize(width,height,false);camera.aspect=width/height;camera.position.z=Math.max(8.7,3.3/camera.aspect);camera.updateProjectionMatrix();};
  return {THREE,renderer,scene,camera,rig,model,resize};
}

async function startScene(){
  const canvas=$('#phone-scene'),host=$('#hero-visual');
  const {THREE,renderer,scene,camera,rig,model,resize}=await makeStudio(canvas,host,3.8);
  await document.fonts.ready;
  const texCanvas=document.createElement('canvas');texCanvas.width=640;texCanvas.height=1320;const ctx=texCanvas.getContext('2d');
  ctx.fillStyle='#101216';ctx.fillRect(0,0,640,1320);
  const text=(s,x,y,font,color='#eff2e9')=>{ctx.font=font;ctx.fillStyle=color;ctx.fillText(s,x,y);};
  const card=(x,y,w,h,color,r=20)=>{ctx.fillStyle=color;ctx.beginPath();ctx.roundRect(x,y,w,h,r);ctx.fill();};
  text('9:41',40,53,'600 21px Manrope');text('●  ▰',526,53,'20px Manrope');
  const logo=new Image();logo.src='assets/paydar-logo.png';await logo.decode();ctx.drawImage(logo,37,106,65,65);
  text('PAYDAR',111,151,'800 32px Manrope');text('YOUR PAYMENT RADAR',40,230,'600 15px Manrope','#9da58f');
  text('CHECK BEFORE',40,302,'600 52px Oswald');text('YOU PAY.',40,363,'600 52px Oswald','#e5ff00');
  card(35,410,570,165,'#e5ff00');text('Scan a UPI QR',64,475,'800 31px Manrope','#171c0a');text('See who you’re paying.',64,521,'500 21px Manrope','#3f4b17');
  ctx.strokeStyle='#171c0a';ctx.lineWidth=5;ctx.strokeRect(515,452,45,45);
  text('YOUR TOOLKIT',40,630,'600 16px Manrope','#a7af9a');
  const rows=[['01','FraudCheck','Look up a UPI ID'],['02','MuleGuard','Spot a money-mule request'],['03','WrongPay Rescue','Prepare a recovery report'],['04','SubWatcher','Track recurring payments']];
  rows.forEach((row,i)=>{const y=670+i*122;card(35,y,570,105,'#1d2025',13);text(row[0],58,y+48,'600 21px Oswald','#bb9bff');text(row[1],112,y+44,'700 25px Manrope');text(row[2],112,y+77,'400 17px Manrope','#9a9f94');});
  text('OFFLINE BY DESIGN',40,1236,'600 16px Manrope','#a88ae6');
  const texture=new THREE.CanvasTexture(texCanvas);texture.colorSpace=THREE.SRGBColorSpace;texture.anisotropy=Math.min(8,renderer.capabilities.getMaxAnisotropy());
  mapScreen(THREE, model, texture);
  let pointerX=0,pointerY=0,visible=false,frame=0,last=0,view=0,lost=false;
  rig.rotation.set(-.09,1.12,-.1);rig.position.y=.25;
  host.addEventListener('pointermove',e=>{if(e.pointerType==='touch')return;const r=host.getBoundingClientRect();pointerX=((e.clientX-r.left)/r.width-.5)*.42;pointerY=((e.clientY-r.top)/r.height-.5)*.18;});
  host.addEventListener('pointerleave',()=>{pointerX=0;pointerY=0;});
  const controls=[...document.querySelectorAll('[data-phone-view]')];
  controls.forEach(button=>{button.disabled=false;button.addEventListener('click',()=>{
    view=Number(button.dataset.phoneView);
    controls.forEach(el=>el.setAttribute('aria-pressed',String(el===button)));
    canvas.setAttribute('aria-label',view?'Rear view of the illustrative phone model':'3D preview of the PayDar application');schedule();
  });});
  function render(now){
    frame=0;if(!visible||document.hidden||lost)return;
    const moving=!reduced.matches,dt=Math.min((now-(last||now))/1000,.05);last=now;
    const blend=moving?1-Math.exp(-7*dt):1;
    const scroll=Math.min(Math.max(scrollY/800,0),1);
    const y=1.12-view*Math.PI+(moving?pointerX+scroll*.48:0);
    rig.rotation.x+=((moving?-.09+pointerY:0)-rig.rotation.x)*blend;
    rig.rotation.y+=(y-rig.rotation.y)*blend;
    rig.rotation.z+=((moving?-.1+scroll*.09:0)-rig.rotation.z)*blend;
    rig.position.y+=((moving?.25+Math.sin(now/1800)*.035-scroll*.12:.25)-rig.position.y)*blend;
    renderer.render(scene,camera);if(moving)frame=requestAnimationFrame(render);
  }
  function schedule(){if(!frame&&visible&&!document.hidden&&!lost)frame=requestAnimationFrame(render);}
  new ResizeObserver(()=>{resize();schedule();}).observe(host);resize();
  new IntersectionObserver(entries=>{visible=entries[0].isIntersecting;if(visible){last=0;schedule();}else if(frame){cancelAnimationFrame(frame);frame=0;}},{rootMargin:'80px'}).observe(host);
  document.addEventListener('visibilitychange',()=>{last=0;schedule();});reduced.addEventListener('change',schedule);
  canvas.addEventListener('webglcontextlost',e=>{e.preventDefault();lost=true;if(frame)cancelAnimationFrame(frame);frame=0;canvas.classList.remove('ready');$('#phone-fallback').hidden=false;controls.forEach(el=>el.disabled=true);});
  canvas.addEventListener('webglcontextrestored',()=>{lost=false;canvas.classList.add('ready');$('#phone-fallback').hidden=true;controls.forEach(el=>el.disabled=false);schedule();});
  canvas.classList.add('ready');$('#phone-fallback').hidden=true;schedule();
}

// The reading flow and keyboard controls remain available without WebGL.
const walkthrough={stage:0,progress:0,update:null};
const outer=$('#walkthrough'),sticky=outer.querySelector('.walk-sticky');
function setWalkStage(next){
  walkthrough.stage=next;
  document.querySelectorAll('.walk-caption').forEach((el,i)=>{el.classList.toggle('active',i===next);el.setAttribute('aria-hidden',String(i!==next));});
  document.querySelectorAll('[data-walk]').forEach((el,i)=>{el.classList.toggle('active',i===next);el.setAttribute('aria-pressed',String(i===next));});
  $('#walk-number').textContent=String(next+1).padStart(2,'0');
  $('#walk-progress-fill').style.transform=`scaleX(${(next+1)/3})`;
  $('.walk-fallback').textContent=['Scan a UPI QR with your camera, or import an image.','Review the recipient name, UPI ID, and amount.','An unknown result is not a guarantee. Confirm independently.'][next];
  walkthrough.update?.();
}
function syncWalk(){
  if(!reduced.matches && getComputedStyle(sticky).position!=='relative'){
    const range=Math.max(1,outer.offsetHeight-sticky.offsetHeight);
    const top=outer.getBoundingClientRect().top-parseFloat(getComputedStyle(sticky).top||0);
    walkthrough.progress=Math.max(0,Math.min(1,-top/range));
    const next=Math.min(2,Math.floor(walkthrough.progress*3));
    if(next!==walkthrough.stage)setWalkStage(next);
  }
}
let scrollFrame=0;
function updateScroll(){
  scrollFrame=0;syncWalk();
  const range=document.documentElement.scrollHeight-innerHeight;
  $('#page-progress').style.transform=`scaleX(${range>0?scrollY/range:0})`;
}
addEventListener('scroll',()=>{if(!scrollFrame)scrollFrame=requestAnimationFrame(updateScroll);},{passive:true});
addEventListener('resize',updateScroll);reduced.addEventListener('change',updateScroll);
document.querySelectorAll('[data-walk]').forEach(el=>el.addEventListener('click',()=>{
  const next=Number(el.dataset.walk);setWalkStage(next);
  if(!reduced.matches && getComputedStyle(sticky).position!=='relative'){const range=Math.max(1,outer.offsetHeight-sticky.offsetHeight);scrollTo({top:scrollY+outer.getBoundingClientRect().top-parseFloat(getComputedStyle(sticky).top||0)+range*(next+.5)/3,behavior:'smooth'});}
}));
setWalkStage(0);updateScroll();

async function startWalkScene(){
  const canvas=$('#walk-scene'),host=$('#walk-device');
  const {THREE,renderer,scene,camera,rig,model,resize}=await makeStudio(canvas,host,3.65);
  await document.fonts.ready;
  const texCanvas=document.createElement('canvas');texCanvas.width=640;texCanvas.height=1320;const ctx=texCanvas.getContext('2d');const texture=new THREE.CanvasTexture(texCanvas);texture.colorSpace=THREE.SRGBColorSpace;
  const text=(s,x,y,font,color='#eff2e9')=>{ctx.font=font;ctx.fillStyle=color;ctx.fillText(s,x,y)};const card=(x,y,w,h,color,r=20)=>{ctx.fillStyle=color;ctx.beginPath();ctx.roundRect(x,y,w,h,r);ctx.fill()};
  const scenes=[
    {title:'SCAN THE RECIPIENT',sub:'Point your camera at a UPI QR.',status:'No payment is initiated.',color:'#e5ff00'},
    {title:'REVIEW THE DETAILS',sub:'Compare the ID, name, and amount.',status:'Check the available local records.',color:'#af89ff'},
    {title:'DECIDE WITH CONTEXT',sub:'An unknown result is not a guarantee.',status:'Confirm independently in your payment app.',color:'#e5ff00'}
  ];
  const drawStage = (stage) => {
    const d=scenes[stage];
    ctx.fillStyle='#101216';ctx.fillRect(0,0,640,1320);
    text('9:41',40,53,'600 21px Manrope');
    text('PAYDAR',40,148,'800 33px Manrope');
    text('PAYMENT CHECK / PREVIEW',40,218,'600 16px Manrope','#9da58f');
    text(d.title,40,302,'600 46px Oswald',d.color);
    text(d.sub,40,350,'500 19px Manrope');
    card(35,420,570,450,'#1d2025');
    if (stage === 0) {
      text('SCANSAFE',65,472,'600 16px Manrope','#a8af9e');
      ctx.strokeStyle='#e5ff00';ctx.lineWidth=5;
      for (const [x,y,dx,dy] of [[160,530,1,1],[480,530,-1,1],[160,740,1,-1],[480,740,-1,-1]]) {
        ctx.beginPath();ctx.moveTo(x,y+dy*40);ctx.lineTo(x,y);ctx.lineTo(x+dx*40,y);ctx.stroke();
      }
      ctx.strokeStyle='#e5ff0066';ctx.lineWidth=2;
      ctx.beginPath();ctx.moveTo(185,635);ctx.lineTo(455,635);ctx.stroke();
      text('ALIGN THE UPI QR',210,801,'600 17px Manrope','#c0c7b6');
    } else if (stage === 1) {
      text('RECIPIENT',65,478,'600 15px Manrope','#a8af9e');
      text('Corner Coffee',65,534,'700 32px Manrope');
      text('coffee@bank',65,578,'500 23px Manrope','#b995ff');
      ctx.fillStyle='#343a32';ctx.fillRect(65,615,510,1);
      text('AMOUNT IN QR',65,657,'600 15px Manrope','#a8af9e');
      text('₹180.00',65,710,'700 35px Manrope');
      text('Illustrative recipient and amount',65,815,'500 17px Manrope','#939c87');
    } else {
      text('LOCAL RECORD CHECK',65,478,'600 15px Manrope','#a8af9e');
      text('UNVERIFIED',65,549,'700 39px Oswald','#e5ff00');
      text('No match in local records.',65,606,'500 23px Manrope');
      ctx.fillStyle='#343a32';ctx.fillRect(65,650,510,1);
      text('A missing record is not a',65,704,'500 22px Manrope','#c0c7b6');
      text('guarantee of safety.',65,740,'500 22px Manrope','#c0c7b6');
      text('Confirm the recipient independently.',65,815,'500 18px Manrope','#e5ff00');
    }
    card(35,914,570,90,d.color,13);
    text(stage===0?'OPEN CAMERA':stage===1?'REVIEW LOCAL RECORDS':'CONFIRM IN YOUR PAYMENT APP',65,969,'800 20px Manrope','#111507');
    text(d.status,40,1075,'500 18px Manrope','#bdc6b7');
    text('PAYDAR / LOCAL PROCESSING',40,1235,'600 15px Manrope','#a88ae6');
    texture.needsUpdate=true;
  };
  drawStage(walkthrough.stage);mapScreen(THREE,model,texture);
  let frame=0,visible=false,last=0,drawn=walkthrough.stage,lost=false;
  rig.rotation.set(-.06,1.18,-.07);
  function tick(now){
    frame=0;if(!visible||document.hidden||lost)return;
    if(drawn!==walkthrough.stage){drawn=walkthrough.stage;drawStage(drawn);}
    canvas.setAttribute('aria-label',`PayDar application preview: ${scenes[drawn].title.toLowerCase()}`);
    const moving=!reduced.matches,dt=Math.min((now-(last||now))/1000,.05);last=now;
    const blend=moving?1-Math.exp(-6*dt):1,p=walkthrough.progress;
    rig.rotation.y+=((moving?1.05+p*.92:Math.PI/2)-rig.rotation.y)*blend;
    rig.rotation.z+=((moving?-.14+p*.24:0)-rig.rotation.z)*blend;
    rig.rotation.x+=((moving?Math.sin(p*Math.PI)*.12:0)-rig.rotation.x)*blend;
    rig.position.z+=((moving?Math.sin(p*Math.PI)*.45:0)-rig.position.z)*blend;
    rig.position.y+=((moving?Math.sin(p*Math.PI)*.12:0)-rig.position.y)*blend;
    renderer.render(scene,camera);if(moving)frame=requestAnimationFrame(tick);
  }
  function schedule(){if(!frame&&visible&&!document.hidden&&!lost)frame=requestAnimationFrame(tick);}
  walkthrough.update=schedule;
  new ResizeObserver(()=>{resize();schedule();}).observe(host);resize();
  new IntersectionObserver(entries=>{visible=entries[0].isIntersecting;if(visible){last=0;schedule();}else if(frame){cancelAnimationFrame(frame);frame=0;}},{rootMargin:'80px'}).observe(host);
  document.addEventListener('visibilitychange',()=>{last=0;schedule();});reduced.addEventListener('change',schedule);
  canvas.addEventListener('webglcontextlost',e=>{e.preventDefault();lost=true;if(frame)cancelAnimationFrame(frame);frame=0;canvas.classList.remove('ready');$('.walk-fallback').hidden=false;});
  canvas.addEventListener('webglcontextrestored',()=>{lost=false;canvas.classList.add('ready');$('.walk-fallback').hidden=true;schedule();});
  canvas.classList.add('ready');$('.walk-fallback').hidden=true;schedule();
}
startScene().catch(error=>console.warn('3D preview unavailable; showing the accessible fallback.',error.message));
const walkLoader=new IntersectionObserver(entries=>{if(entries.some(e=>e.isIntersecting)){walkLoader.disconnect();startWalkScene().catch(error=>console.warn('Walkthrough preview unavailable; showing the accessible fallback.',error.message));}},{rootMargin:'500px'});
walkLoader.observe($('#walkthrough'));
