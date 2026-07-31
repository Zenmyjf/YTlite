/* YT Lite - injected script (runs at onPageFinished, once the DOM exists)
 * Responsibilities:
 *   1. Auto-click the "Skip Ad" button when it appears (handles skippable
 *      in-stream ads that slip past the network-level block - e.g. the
 *      very first ad shown before our fetch override could install)
 *   2. Tell Android when the video starts/stops playing, so it knows
 *      whether to offer Picture-in-Picture on exit
 *
 * Network-level ad blocking lives in adblock_early.js, injected separately
 * at onPageStarted so it's in place before YouTube's own first data call.
 */
(function () {

if (window.__ytliteInjected) return;
window.__ytliteInjected = true;

/* ---------- 1. Auto-click skip-ad button ---------- */
function clickSkipButtonIfPresent() {
  var selectors = [
    '.ytp-ad-skip-button',
    '.ytp-ad-skip-button-modern',
    '.videoAdUiSkipButton',
    '.ytp-skip-ad-button'
  ];
  for (var i = 0; i < selectors.length; i++) {
    var btn = document.querySelector(selectors[i]);
    if (btn) {
      btn.click();
      return;
    }
  }
}
setInterval(clickSkipButtonIfPresent, 800);

/* ---------- 2. Report play state to Android for PiP ---------- */
function attachVideoListeners() {
  var video = document.querySelector('video.video-stream, video');
  if (!video || video.__ytliteBound) return;
  video.__ytliteBound = true;

  var report = function () {
    try {
      if (window.Android && window.Android.setPlaying) {
        window.Android.setPlaying(!video.paused && !video.ended);
      }
    } catch (e) {}
  };

  video.addEventListener('play', report);
  video.addEventListener('pause', report);
  video.addEventListener('ended', report);
  report();
}

// The video element may not exist yet on first injection (page still
// loading), and mobile YouTube is a single-page app that swaps the video
// element between navigations - so we watch for it periodically rather
// than relying on a single DOMContentLoaded-style hook.
setInterval(attachVideoListeners, 1000);

})();
