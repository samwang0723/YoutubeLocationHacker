YoutubeLocationHacker
=====================

Hacker video source location for Youtube
(Notice: Rules may change in the future, if you find this API cannot parse any video anymore, please notify me)

	YVideoIdHacker hacker = new YVideoIdHacker();
    String[] argAry = {"My2FRPA3Gf8"}; // Bring in your videoId
    try {
        hacker.run(argAry);
        String mp4Url = hacker.getDownloadURL();
        YVideoIdHacker.downloadWithHttpClient(mp4Url, new File("location")); // Save as file
    } catch (Throwable e) {
        e.printStackTrace();
    }
