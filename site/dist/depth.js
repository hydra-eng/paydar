import { animate, onScroll } from './assets/anime.js';

const preference = matchMedia('(prefers-reduced-motion: reduce)');
let effects = [];
function setup() {
  effects.forEach(effect => effect.revert()); effects = [];
  if (preference.matches) return;
  // Native scroll remains in charge; motion follows the same reading direction.
  const panel = document.querySelector('.feature-panel');
  if (panel && innerWidth > 760) effects.push(animate(panel, {
    rotateX: [7, 0], rotateY: [-5, 0], translateY: [36, -12],
    ease: 'linear', autoplay: onScroll({ target: panel, enter: 'bottom top', leave: 'top bottom', sync: .6 })
  }));
  document.querySelectorAll('.device-depth-ring').forEach(ring => effects.push(animate(ring, {
    rotate: [-24, 24], scale: [.88, 1.12], ease: 'linear',
    autoplay: onScroll({ target: ring.parentElement, enter: 'bottom top', leave: 'top bottom', sync: .5 })
  })));
}
document.querySelectorAll('.hero-visual,.walk-device').forEach(host => {
  const ring = document.createElement('div'); ring.className = 'device-depth-ring';
  ring.setAttribute('aria-hidden', 'true'); host.append(ring);
});
setup(); preference.addEventListener('change', setup);
const wide = matchMedia('(min-width: 761px)'); wide.addEventListener('change', setup);
