/* YT Lite - early injection (runs at onPageStarted, before the page's own
 * scripts get a chance to make their first API call). Only touches
 * window.fetch - no DOM access here since the document may not exist yet.
 */
(function () {
if (window.__ytliteAdblockInstalled) return;
window.__ytliteAdblockInstalled = true;

var _origFetch = window.fetch;
window.fetch = async function (input, init) {
try {
var url = (typeof input === 'string') ? input : input.url;

if (url.includes('googleads.g.doubleclick.net') ||
    url.includes('youtube.com/youtubei/v1/player/ad_break') ||
    url.includes('youtube.com/pagead/adview') ||
    url.includes('youtube.com/api/stats/ads')) {
  return new Response('', {status: 200});
}

if (url.includes('youtube.com/youtubei/')) {
  var response = await _origFetch.apply(this, arguments);
  try {
    var clone = response.clone();
    var data = await clone.json();

    delete data?.adSlots;
    delete data?.playerAds;
    delete data?.adPlacements;
    delete data?.adBreakHeartbeatParams;
    delete data?.[0]?.playerResponse?.adSlots;
    delete data?.[0]?.playerResponse?.playerAds;
    delete data?.[0]?.playerResponse?.adPlacements;
    delete data?.[0]?.playerResponse?.adBreakHeartbeatParams;

    var newBody = JSON.stringify(data);
    var newHeaders = new Headers(response.headers);
    newHeaders.set('content-length', String(newBody.length));
    newHeaders.set('content-type', 'application/json');
    return new Response(newBody, {status: response.status, statusText: response.statusText, headers: newHeaders});
  } catch (e) {
    return response;
  }
}

return _origFetch.apply(this, arguments);
} catch (e) {
return _origFetch.apply(this, arguments);
}
};
})();
