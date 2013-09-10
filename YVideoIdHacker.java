import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

public class YVideoIdHacker {
    
    private static final String scheme = "http";
    private static final String host = "www.youtube.com";
    private static final String YOUTUBE_WATCH_URL_PREFIX = scheme + "://" + host + "/watch?v=";
    private static final String ERROR_MISSING_VIDEO_ID = "Missing video id. Extract from " + YOUTUBE_WATCH_URL_PREFIX + "VIDEO_ID";
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_8_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/29.0.1547.65 Safari/537.36";
    private static final String DEFAULT_ENCODING = "UTF-8";
    private static final String newline = System.getProperty("line.separator");
    private static final Pattern commaPattern = Pattern.compile(",");
    private static final char[] ILLEGAL_FILENAME_CHARACTERS = { '/', '\n', '\r', '\t', '\0', '\f', '`', '?', '*', '\\', '<', '>', '|', '\"', ':' };
    private static final int BUFFER_SIZE = 2048;
    private static final DecimalFormat commaFormatNoPrecision = new DecimalFormat("###,###");
    private static final double ONE_HUNDRED = 100;
    private static final double KB = 1024;
    private static String downloadUrl;

    private void usage(String error) {
        if (error != null) {
            System.err.println("Error: " + error);
        }
        System.err.println("usage: JavaYoutubeDownload VIDEO_ID");
        System.err.println();
        System.err.println("Options:");
        System.err.println("\t[-dir DESTINATION_DIR] - Specify output directory.");
        System.err.println("\t[-format FORMAT] - Format number" + newline + "\t\tSee http://en.wikipedia.org/wiki/YouTube#Quality_and_codecs");
        System.err.println("\t[-ua USER_AGENT] - Emulate a browser user agent.");
        System.err.println("\t[-enc ENCODING] - Default character encoding.");
        System.err.println("\t[-verbose] - Verbose logging for downloader component.");
        System.err.println("\t[-verboseall] - Verbose logging for all components (e.g. HttpClient).");
        System.exit(-1);
    }

    public boolean run(String[] args) throws Throwable {
        String videoId = null;
        String outdir = ".";
        int format = 18;
        String encoding = DEFAULT_ENCODING;
        String userAgent = DEFAULT_USER_AGENT;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            // Options start with either -, --
            // Do not accept Windows-style args that start with / because of abs
            // paths on linux for file names.
            if (arg.charAt(0) == '-') {

                // For easier processing, convert any double dashes to
                // single dashes
                if (arg.length() > 1 && arg.charAt(1) == '-') {
                    arg = arg.substring(1);
                }

                String larg = arg.toLowerCase();

                // Process the option
                if (larg.equals("-help") || larg.equals("-?") || larg.equals("-usage") || larg.equals("-h")) {
                    usage(null);
                } else if (larg.equals("-dir")) {
                    outdir = args[++i];
                } else if (larg.equals("-format")) {
                    format = Integer.parseInt(args[++i]);
                } else if (larg.equals("-ua")) {
                    userAgent = args[++i];
                } else if (larg.equals("-enc")) {
                    encoding = args[++i];
                } else {
                    usage("Unknown command line option " + args[i]);
                }
            } else {
                // Non-option (i.e. does not start with -, --

                videoId = arg;

                // Break if only the first non-option should be used.
                break;
            }
        }

        if (videoId == null) {
            usage(ERROR_MISSING_VIDEO_ID);
        }

        System.out.println("Starting");

        if (videoId.startsWith(YOUTUBE_WATCH_URL_PREFIX)) {
            videoId = videoId.substring(YOUTUBE_WATCH_URL_PREFIX.length());
        }
        int a = videoId.indexOf('&');
        if (a != -1) {
            videoId = videoId.substring(0, a);
        }

        File outputDir = new File(outdir);
        String extension = getExtension(format);

        System.out.println("Finished");
        return play(videoId, format, encoding, userAgent, outputDir, extension);
    }

    private static String getExtension(int format) {
        switch (format) {
        case 18:
            return "mp4";
        default:
            throw new Error("Unsupported format " + format);
        }
    }

    private static boolean play(String videoId, int format, String encoding, String userAgent, File outputdir, String extension) throws Throwable {
        downloadUrl = null;
        System.out.println("Retrieving " + videoId);
        boolean found = false;

        CookieStore cookieStore = new BasicCookieStore();
        HttpContext localContext = new BasicHttpContext();
        localContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);

        HttpClient httpclient = new DefaultHttpClient();
        HttpGet httpget = new HttpGet("http://www.youtube.com/get_video_info?eurl=http%3A%2F%2Fkej.tw%2F&sts=1586&video_id=" + videoId);
        if (userAgent != null && userAgent.length() > 0) {
            httpget.setHeader("User-Agent", userAgent);
        }

        System.out.println("Executing " + httpget.getURI());
        HttpResponse response = httpclient.execute(httpget, localContext);
        HttpEntity entity = response.getEntity();

        if (entity != null && response.getStatusLine().getStatusCode() == 200) {
            InputStream instream = entity.getContent();
            String videoInfo = getStringFromInputStream(encoding, instream);
            System.out.println("videoInfo= " + videoInfo);
            if (videoInfo != null && videoInfo.length() > 0) {
                List<NameValuePair> infoMap = new ArrayList<NameValuePair>();
                URLEncodedUtils.parse(infoMap, new Scanner(videoInfo), encoding);
                String filename = videoId;

                for (NameValuePair pair : infoMap) {
                    String key = pair.getName();
                    String val = pair.getValue();
                    // System.out.println(key + "=" + val);
                    if (key.equals("title")) {
                        filename = val;
                    } else if (key.equals("url_encoded_fmt_stream_map")) {
                        String[] formats = commaPattern.split(val);

                        int[] itagList = new int[10];
                        String[] urlList = new String[10];
                        String[] signatureList = new String[10];

                        for (int k = 0; k < formats.length; k++) {
                            String[] params = formats[k].split("&");
                            for (String para : params) {
                                para = URLDecoder.decode(para, "UTF-8");
                                if (para.indexOf("itag=") == 0) {
                                    System.out.println("" + para.substring(5));
                                    itagList[k] = (Integer.parseInt(para.substring(5), 10));
                                } else if (para.indexOf("url=") == 0) {
                                    System.out.println("" + para);
                                    urlList[k] = (para.substring(4));
                                } else if (para.indexOf("sig=") == 0) {
                                    System.out.println("" + para);
                                    signatureList[k] = (para.substring(4));
                                } else if (para.indexOf("s=") == 0) {
                                    System.out.println("" + para);
                                    signatureList[k] = (sigHandlerAlternative(para.substring(2)));
                                } else if (para.indexOf("type=") == 0) {
                                    System.out.println("" + para.substring(5));
                                }
                            }
                        }

                        for (int i = 0; i < urlList.length; i++) {
                            if (urlList[i] != null && itagList[i] == format) {
                                downloadUrl = URLDecoder.decode(urlList[i], "UTF-8") + "&signature=" + signatureList[i];
                                System.out.println("downloadUrl=" + downloadUrl);
                                found = true;
                            }
                        }

                        if (!found) {
                            System.out.println("Could not find video matching specified format, however some formats of the video do exist (use -verbose).");
                        }
                    }
                }

                // download videos here
                filename = cleanFilename(filename);
                if (filename.length() == 0) {
                    filename = videoId;
                } else {
                    filename += "_" + videoId;
                }
                filename += "." + extension;
                File outputfile = new File(outputdir, filename);

                if (downloadUrl != null) {
                    downloadWithHttpClient(downloadUrl, outputfile);
                } else {
                    System.out.println("Could not find video");
                }
            } else {
                System.out.println("Did not receive content from youtube");
            }
        } else {
            System.out.println("Could not contact youtube: " + response.getStatusLine());
        }

        return found;
    }

    public static String sigHandlerAlternative(String s) {
        System.out.println("sigHandlerAlternative: " + s);
        String[] sArray = s.split("");
        String tmpA, tmpB;

        tmpA = sArray[1];
        tmpB = sArray[53];

        sArray[1] = tmpB;
        sArray[53] = tmpA;

        tmpA = sArray[84];
        tmpB = sArray[63];

        sArray[84] = tmpB;
        sArray[63] = tmpA;

        sArray = Arrays.copyOfRange(sArray, 4, sArray.length); // sArray.slice(3);
        List<String> list = Arrays.asList(sArray);
        Collections.reverse(list); // sArray.reverse();
        sArray = (String[]) list.toArray();
        sArray = Arrays.copyOfRange(sArray, 3, sArray.length);

        return combine(sArray, "");
    }

    private static String combine(String[] s, String glue) {
        int k = s.length;
        if (k == 0)
            return null;
        StringBuilder out = new StringBuilder();
        out.append(s[0]);
        for (int x = 1; x < k; ++x)
            out.append(glue).append(s[x]);
        return out.toString();
    }

    public String getDownloadURL() {
        return downloadUrl;
    }

    public static void downloadWithHttpClient(String downloadUrl, File outputfile) throws Throwable {
        HttpGet httpget2 = new HttpGet(downloadUrl);
        httpget2.setHeader("User-Agent", DEFAULT_USER_AGENT);

        System.out.println("Executing " + httpget2.getURI());
        HttpClient httpclient2 = new DefaultHttpClient();
        HttpResponse response2 = httpclient2.execute(httpget2);
        HttpEntity entity2 = response2.getEntity();
        System.out.println("status code=" + response2.getStatusLine().getStatusCode());

        if (entity2 != null && response2.getStatusLine().getStatusCode() == 200) {
            double length = entity2.getContentLength();
            if (length <= 0) {
                // Unexpected, but do not divide by zero
                length = 1;
            }
            InputStream instream2 = entity2.getContent();
            System.out.println("Writing " + commaFormatNoPrecision.format(length) + " bytes to " + outputfile);
            if (outputfile.exists()) {
                outputfile.delete();
            }
            FileOutputStream outstream = new FileOutputStream(outputfile);
            try {
                byte[] buffer = new byte[BUFFER_SIZE];
                double total = 0;
                int count = -1;
                int progress = 10;
                long start = System.currentTimeMillis();
                while ((count = instream2.read(buffer)) != -1) {
                    total += count;
                    int p = (int) ((total / length) * ONE_HUNDRED);
                    if (p >= progress) {
                        long now = System.currentTimeMillis();
                        double s = (now - start) / 1000;
                        int kbpers = (int) ((total / KB) / s);
                        System.out.println(progress + "% (" + kbpers + "KB/s)");
                        progress += 10;
                    }
                    outstream.write(buffer, 0, count);
                }
                outstream.flush();
            } finally {
                outstream.close();
            }
            System.out.println("Done");
        }
    }

    private static String cleanFilename(String filename) {
        for (char c : ILLEGAL_FILENAME_CHARACTERS) {
            filename = filename.replace(c, '_');
        }
        return filename;
    }

    private static String getStringFromInputStream(String encoding, InputStream instream) throws UnsupportedEncodingException, IOException {
        Writer writer = new StringWriter();

        char[] buffer = new char[1024];
        try {
            Reader reader = new BufferedReader(new InputStreamReader(instream, encoding));
            int n;
            while ((n = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, n);
            }
        } finally {
            instream.close();
        }
        String result = writer.toString();
        return result;
    }
}
