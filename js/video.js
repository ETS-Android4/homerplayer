(function() {

const VIDEO_WIDTH = 853;
const VIDEO_HEIGHT = 480;

$(document).ready(function() {
  $('.video-poster a').click(function(event) {
    event.preventDefault();

    const lightview_options = {
      width: VIDEO_WIDTH,
      height: VIDEO_HEIGHT,
      initialDimensions: { width: VIDEO_WIDTH, height: VIDEO_HEIGHT }
    };
    const url = event.currentTarget.href;

    Lightview.show({
        url: url, type: 'iframe', options: lightview_options });
  });
});

})();
