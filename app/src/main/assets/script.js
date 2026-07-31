/* YT Lite - injected script (v1 skeleton)
 * Responsibilities so far:
 *   1. Hide known banner/companion ad containers (CSS)
 *   2. Auto-click the "Skip Ad" button when it appears (handles skippable
 *      in-stream ads; non-skippable pre-roll ads are NOT removed yet -
 *      that needs deeper player-response handling, deferred for now)
 *   3. Tell Android when the video starts/stops playing, so it knows
 *      whether to offer Picture-in-Picture on exit
 */
(function () {

if (window.__ytliteInjected) return;
window.__ytliteInjected = true;

/* ---------- 1. Hide banner / companion ads ---------- */
var style = document.createElement('style');
style.textContent = `
  ytd-display-ad-renderer,
  ytd-promoted-sparkles-web-renderer,
  ytd-in-feed-ad-layout-renderer,
  ytd-banner-promo-renderer,
  ytd-statement-banner-renderer,
  ytd-ad-slot-renderer,
  masthead-ad,
  #masthead-ad,
  .ytp-ad-overlay-container,
  .ytp-ad-image-overlay {
    display: none !important;
  }
`;
document.head.appendChild(style);

/* ---------- 2. Auto-click skip-ad button ---------- */
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

/* ---------- 3. Report play state to Android for PiP ---------- */
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
