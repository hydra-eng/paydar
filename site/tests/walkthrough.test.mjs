import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const source=readFileSync(new URL('../dist/main.js',import.meta.url),'utf8');
const controller=source.slice(source.indexOf('const walkthrough='),source.indexOf('async function startWalkScene'));
function fixture(reduce=false){
  const node=()=>({style:{},attributes:{},events:{},classList:{toggle(){}},setAttribute(k,v){this.attributes[k]=v;},addEventListener(k,v){this.events[k]=v;}});
  const buttons=Array.from({length:3},(_,i)=>({...node(),dataset:{walk:String(i)}}));
  const captions=Array.from({length:3},node);
  const sticky={offsetHeight:600};let top=900;
  const outer={offsetHeight:1800,querySelector:()=>sticky,getBoundingClientRect:()=>({top})};
  const nodes={'#walkthrough':outer,'#walk-number':node(),'#walk-progress-fill':node(),'.walk-fallback':node(),'#page-progress':node()};
  const events={},scrolls=[];
  const context={
    $:s=>nodes[s],reduced:{matches:reduce,addEventListener(){}},
    document:{querySelectorAll:s=>s==='[data-walk]'?buttons:captions,documentElement:{scrollHeight:6000}},
    getComputedStyle:()=>({top:'76px'}),scrollY:0,innerHeight:800,
    addEventListener:(k,f)=>events[k]=f,requestAnimationFrame:f=>{f();return 1;},scrollTo:v=>scrolls.push(v)
  };
  runInNewContext(controller,context);
  return {buttons,captions,nodes,scrolls,context,setTop:v=>{top=v;events.resize();}};
}
test('walkthrough controls remain functional without loading Three.js',()=>{
  const f=fixture();f.buttons[2].events.click();
  assert.equal(f.nodes['#walk-number'].textContent,'03');
  assert.equal(f.buttons[2].attributes['aria-pressed'],'true');
  assert.equal(f.captions[0].attributes['aria-hidden'],'true');
  assert.match(f.nodes['.walk-fallback'].textContent,/unknown result/);
  assert.equal(f.scrolls[0].top,1824);
});
test('reduced motion preserves selected stage without scrolling',()=>{
  const f=fixture(true);f.buttons[1].events.click();f.setTop(-2000);
  assert.equal(f.nodes['#walk-number'].textContent,'02');
  assert.equal(f.scrolls.length,0);
});
test('scroll stages account for the sticky navigation offset',()=>{
  const f=fixture();f.setTop(76);assert.equal(f.nodes['#walk-number'].textContent,'01');
  f.setTop(-525);assert.equal(f.nodes['#walk-number'].textContent,'02');
  f.setTop(-1124);assert.equal(f.nodes['#walk-number'].textContent,'03');
  f.setTop(500);assert.equal(f.nodes['#walk-number'].textContent,'01');
});
test('format preview cannot report a valid-looking ID as safe',()=>{
  const check=source.slice(source.indexOf('export function checkId'),source.indexOf("$('#check-form').addEventListener")).replace('export ','');
  const context={};runInNewContext(check,context);
  assert.match(context.checkId('coffee@bank').title,/Unverified/);
  assert.match(context.checkId('<script>').title,/doesn’t look/);
});
