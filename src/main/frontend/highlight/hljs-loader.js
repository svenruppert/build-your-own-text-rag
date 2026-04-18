// highlight.min.js is a classic browser script whose top-level
// `var hljs = ...` only attaches to `window` when it runs in global
// scope. Importing it as an ES module would keep `hljs` module-local.
//
// Workaround: ask Vite for the URL of the bundled asset (the `?url`
// suffix), then inject a real <script> tag. The browser treats
// dynamically created <script> elements as classic scripts by default,
// so `var hljs` becomes `window.hljs` exactly as on the CDN demo page.
//
// The GitHub theme is injected the same way so it ends up in the
// document head (not scoped to a Vaadin theme shadow root) and therefore
// styles the <pre><code> rendered by the Html component.
import hljsUrl from './highlight.min.js?url';
import themeUrl from './github.min.css?url';

if (!window.__hljsInjected) {
  window.__hljsInjected = true;

  const link = document.createElement('link');
  link.rel = 'stylesheet';
  link.href = themeUrl;
  document.head.appendChild(link);

  const script = document.createElement('script');
  script.src = hljsUrl;
  // Keep insertion order and guarantee execution before our highlighter
  // retry loop triggers.
  script.async = false;
  script.defer = false;
  document.head.appendChild(script);
}
