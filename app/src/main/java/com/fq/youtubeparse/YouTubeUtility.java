package com.fq.youtubeparse;

import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class YouTubeUtility {

    private static int retryTimes = 0;
    private QueryYouTubeTask queryYouTubeTask;
    /**
     * youtube视频单个视频
     */
    public static final String TYPE_YOUTUBE_VIDEO_SINGLE = "ytv://";
    /**
     * youtube视频列表
     */
//    public static final String TYPE_YOUTUBE_VIDEO_LIST = "ytpl://";
    /**
     * youtube视频列表
     */
    public static final String TYPE_YOUTUBE_VIDEO_LIVE = "ytlive://";

    private static final String YOUTUBE_VIDEO_INFORMATION_URL = "http://www.youtube.com/get_video_info?&video_id=";


    /**
     * Calculate the YouTube URL to load the video. Includes retrieving a token
     * that YouTube requires to play the video.
     *
     * @param pYouTubeFmtQuality quality of the video. 17=low, 18=high
     *                           whether to fallback to lower quality in case the supplied
     *                           quality is not available
     * @param pYouTubeVideoId    the id of the video
     * @return the url string that will retrieve the video
     * @throws IOException
     * @throws UnsupportedEncodingException
     */
    private String calculateYouTubeUrl(String pYouTubeFmtQuality, boolean pFallback, String pYouTubeVideoId)
            throws TimeoutException, IOException {
        String lUriStr = null;
        String el = "embedded";
        String youtubeUrl = YOUTUBE_VIDEO_INFORMATION_URL + pYouTubeVideoId + "&el=" + el
                + "&hl=en&ps=default";
        Log.e("youtube", "youtube*****url : " + youtubeUrl);

        String lInfoStr = getVideoInfo(youtubeUrl);
        if(TextUtils.isEmpty(lInfoStr)){
            return null;
        }

        String[] lArgs = lInfoStr.split("&");
        Map<String, String> lArgMap = new HashMap<String, String>();
        for (String lArg : lArgs) {
            String[] lArgValStrArr = lArg.split("=");
            if (lArgValStrArr.length >= 2) {
                lArgMap.put(lArgValStrArr[0], URLDecoder.decode(lArgValStrArr[1], "utf-8"));
            }
        }

        // Find out the URI string from the parameters
        if (videoType.equals(TYPE_YOUTUBE_VIDEO_SINGLE)) {
            // Populate the list of formats for the video
            if (TextUtils.isEmpty(lArgMap.get("fmt_list"))) {
                // Log.e("youtube_error", "reason: " + lArgMap.get("reason"));
                // 没有播放权限
                // if (lArgMap != null &&
                // !TextUtils.isEmpty(lArgMap.get("errorcode"))
                // && "150".equals(lArgMap.get("errorcode"))) {
                // return getYoutubeDetailPage(3, pFallback, pYouTubeVideoId);
                // }
                //
                // if (!TextUtils.isEmpty(lArgMap.get("reason"))) {
                // throw new SocketTimeoutException("没有版权，无法在站外播放此视频");
                // }
                int[] youtubeQuantity = {13, 17, 18, 22, 37};
                int oldQuantity = Integer.parseInt(pYouTubeFmtQuality);
                int level = 5;
                for (int i = 0; i < youtubeQuantity.length; i++) {
                    if (oldQuantity == youtubeQuantity[i]) {
                        level = i + 1;
                        break;
                    }
                }
                return getYoutubeDetailPage(level, pYouTubeVideoId);
                // return null;
            }
            String lFmtList = URLDecoder.decode(lArgMap.get("fmt_list"), "utf-8");
            ArrayList<Format> lFormats = new ArrayList<Format>();
            if (null != lFmtList) {
                String lFormatStrs[] = lFmtList.split(",");
                for (String lFormatStr : lFormatStrs) {
                    Format lFormat = new Format(lFormatStr);
                    lFormats.add(lFormat);
                }
            }

            // Populate the list of streams for the video
            String lStreamList = lArgMap.get("url_encoded_fmt_stream_map");
            if (null != lStreamList) {
                String lStreamStrs[] = lStreamList.split(",");
                ArrayList<VideoStream> lStreams = new ArrayList<VideoStream>();
                for (String lStreamStr : lStreamStrs) {
                    VideoStream lStream = new VideoStream(lStreamStr);
                    lStreams.add(lStream);
                }

                // Search for the given format in the list of video formats
                // if it is there, select the corresponding stream
                // otherwise if fallback is requested, check for next lower format
                int lFormatId = Integer.parseInt(pYouTubeFmtQuality);
                Format lSearchFormat = new Format(lFormatId);
                while (!lFormats.contains(lSearchFormat) && pFallback) {
                    int lOldId = lSearchFormat.getId();
                    int lNewId = getSupportedFallbackId(lOldId);

                    if (lOldId == lNewId) {
                        break;
                    }
                    lSearchFormat = new Format(lNewId);
                }
                Log.e("youtube", "当前播放youtube的清晰度：" + lSearchFormat.mId);

                int lIndex = lFormats.indexOf(lSearchFormat);
                if (lIndex >= 0) {
                    VideoStream lSearchStream = lStreams.get(lIndex);
                    lUriStr = lSearchStream.getUrl();
                }
                Log.e("youtube", "当前播放youtube的url：" + Uri.decode(lUriStr));
            } else {
                setYoutubeErrorMessage(0, "播放地址获取失败", "url_encoded_fmt_stream_map is null,无法解析到youtube的播放地址");
                throw new SocketTimeoutException("url_encoded_fmt_stream_map is null,无法解析到youtube的播放地址");
            }
        } else if(videoType.equals(TYPE_YOUTUBE_VIDEO_LIVE)){
            if (TextUtils.isEmpty(lArgMap.get("hlsvp"))) {
                setYoutubeErrorMessage(0, "直播地址获取失败", "hlsvp is null,无法解析到youtube的播放地址");
                return null;
            }
            lUriStr= lArgMap.get("hlsvp");
            Log.e("TAG", "sourceUrl: " + lUriStr);
        }


        // Return the URI string. It may be null if the format (or a fallback
        // format if enabled)
        // is not found in the list of formats for the video
        return lUriStr;
    }

    /**
     * itag: 240P 36； 360P 18； 720P 22； 1080P 37；
     */

    private String getYoutubeDetailPage(int pYouTubeFmtQuality, String pYouTubeVideoId) {
        String realUrl = null;
        String el1 = "detailpage";
        String youtubeUrl = YOUTUBE_VIDEO_INFORMATION_URL + pYouTubeVideoId + "&el=" + el1
                + "&hl=en&ps=default";

        Log.e("youtube", "youtube*****url : " + youtubeUrl);
        String lInfoStr = getVideoInfo(youtubeUrl);
        try {
            if (!TextUtils.isEmpty(lInfoStr)) {
                // Log.e("youtube", "解码前的字符串 : " + lInfoStr);
                try {
                    lInfoStr = URLDecoder.decode(lInfoStr, "UTF-8");
                    // Log.e("youtube", "解码后的字符串 : " + lInfoStr);
                    String[] lArgsUrl = lInfoStr.split("&url=http");
                    int m = 0;
                    SparseArray<Map<String, String>> urlSparseArray = new SparseArray<Map<String, String>>();
                    for (String curUrl : lArgsUrl) {
                        if (TextUtils.isEmpty(curUrl)) {
                            continue;
                        }
                        String formatUrl = "url=http" + curUrl;
                        // Log.e("youtube", "youtube没有裁剪：" + Uri.decode(formatUrl));
                        String[] urlParams = formatUrl.split("&");
                        Map<String, String> paramsMap = new HashMap<String, String>();
                        for (String urlParam : urlParams) {
                            if (TextUtils.isEmpty(urlParam) || !urlParam.contains("=")) {
                                continue;
                            }
                            String[] urlSplit = urlParam.split("=");
                            if (urlSplit.length >= 2) {
                                String params = urlSplit[1];
                                if (TextUtils.isEmpty(params)) {
                                    continue;
                                }
                                if ("url".equals(urlSplit[0])) {
                                    params = Uri.decode(params);
                                    if (!params.contains("://r")) {
                                        continue;
                                    }

                                    if (params.contains(",")) {
                                        String[] paramsFormat = params.split(",");
                                        for (String param : paramsFormat) {
                                            if (param.contains("://r")) {
                                                params = param;
                                                break;
                                            }
                                        }
                                    }
                                    // Log.e("youtube",
                                    // "==========================================");
                                    // Log.e("youtube", "youtube的真正播放地址：" + params);
                                }

                                paramsMap.put(urlSplit[0], params);

                            }
                        }
                        urlSparseArray.append(m++, paramsMap);
                    }

                    /**
                     * itag: 240P 36; 360P 18; 720P 22; 1080P 37; mp4 720P 137; 480P
                     * 136; 360P 135; 240P 134
                     */
                    // Log.e("youtube_", "要使用的清晰度是：   " + paramsMaps.get("itag"));
                    // Log.e("youtube_", "要使用的url是：   " + realUrl);
                    // realUrl = getTheHighestResolution(realUrl, urlSparseArray);

                    realUrl = getStanderResolution(pYouTubeFmtQuality, urlSparseArray);

                    if (TextUtils.isEmpty(realUrl) && retryTimes < 5) {
                        // Log.e("youtube", "没有查询到合适的播放地址，第几次尝试" + retryTimes);
                        retryTimes++;
                        realUrl = getYoutubeDetailPage(pYouTubeFmtQuality, pYouTubeVideoId);
                        if (!TextUtils.isEmpty(realUrl)) {
                            retryTimes = 0;
                            return realUrl;
                        }
                    }
                } catch (IllegalArgumentException e) {
                    return null;
                }
                // Log.e("youtube", "realUrl： " + realUrl);

            }
            return realUrl;
        } catch (IOException e) {
            e.printStackTrace();
            setYoutubeErrorMessage(2, "视频信息获取失败！", "IOException youTubeId == null -> getYoutubeDetailPage 无法继续" + e.getMessage());
        }
        return null;
    }



    private String getVideoInfo(String youtubeUrl) {
        URL url;//请求的URL地址
        HttpURLConnection conn = null;
        byte[] responseBody = null;//响应体
        try {
            //发送GET请求
            url = new URL(youtubeUrl);
            conn = (HttpURLConnection) url.openConnection();
            //HttpURLConnection默认就是用GET发送请求，所以下面的setRequestMethod可以省略
            conn.setRequestMethod("GET");
            //HttpURLConnection默认也支持从服务端读取结果流，所以下面的setDoInput也可以省略
            conn.setDoInput(true);
            //用setRequestProperty方法设置一个自定义的请求头:action，由于后端判断
//                conn.setRequestProperty("action", NETWORK_GET);
            //禁用网络缓存
            conn.setUseCaches(false);
            //调用getInputStream方法后，服务端才会收到请求，并阻塞式地接收服务端返回的数据
            InputStream is = conn.getInputStream();
            //将InputStream转换成byte数组,getBytesByInputStream会关闭输入流
            responseBody = getBytesByInputStream(is);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            //最后将conn断开连接
            if (conn != null) {
                conn.disconnect();
            }
        }


        String lInfoStr = null;

        if(responseBody == null || responseBody.length == 0){
            return null;
        }

        try {
            lInfoStr = new String(responseBody, "utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return lInfoStr;
    }

    private String videoType;

    public void executeQueryYouTubeTask(String type, String youTubeId) { // R3lSUXl_VQI
        if (TextUtils.isEmpty(youTubeId)) {
            setYoutubeErrorMessage(2, "视频信息获取失败！", "youTubeId == null -> executeQueryYouTubeTask 无法继续");
            return;
        }
        videoType = type;
        if (queryYouTubeTask != null) {
            queryYouTubeTask.cancel(true);
            queryYouTubeTask = null;
        }
        queryYouTubeTask = new QueryYouTubeTask();
        queryYouTubeTask.execute(youTubeId);
    }


    public void cancelQueryYoutubeTask() {
        if (queryYouTubeTask != null) {
            queryYouTubeTask.cancel(true);
            queryYouTubeTask = null;
        }
        retryTimes = 30;
    }


    private class QueryYouTubeTask extends AsyncTask<String, Void, Uri> {

        @Override
        protected Uri doInBackground(String... pParams) {
            Log.e("QueryYouTubeTask", "QueryYouTubeTask//////////////doInBackground");
            String lUriStr = null;
            // 清晰度
            String lYouTubeFmtQuality = "22";
            String lYouTubeVideoId;

            if (isCancelled())
                return null;
            try {

                lYouTubeVideoId = pParams[0];

                if (isCancelled())
                    return null;
                // //////////////////////////////////
                // calculate the actual URL of the video, encoded with
                // proper YouTube token
                lUriStr = calculateYouTubeUrl(lYouTubeFmtQuality, true, lYouTubeVideoId);

                if (isCancelled())
                    return null;

                if (!TextUtils.isEmpty(lUriStr) && lUriStr.startsWith("youtube_error")) {
                    // youtubeReason = "该视频由于版权问题，无法正常播放！";
                    // LogCat.e("QueryYouTubeTask//////--------------reason: "
                    // + lUriStr);
                    return null;
                }
                // publishProgress(new ProgressUpdateInfo(mMsgHiBand));

            } catch (SocketTimeoutException e) {
                e.printStackTrace();
                setYoutubeErrorMessage(2, "视频信息获取失败！", "youtube客户端解析 SocketTimeoutException nQueryYouTubeTask -> doInBackground -> et error SocketTimeoutException " + e.getMessage());
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                setYoutubeErrorMessage(2, "视频信息获取失败！", "youtube客户端解析 UnsupportedEncodingException nQueryYouTubeTask -> doInBackground -> UnsupportedEncodingException " + e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
                setYoutubeErrorMessage(2, "视频信息获取失败！", "youtube客户端解析 nIOException QueryYouTubeTask -> doInBackground -> IOException" + e.getMessage());
            } catch (TimeoutException e) {
                e.printStackTrace();
                setYoutubeErrorMessage(2, "视频信息获取失败！", "youtube客户端解析 TimeoutException nIOException QueryYouTubeTask -> doInBackground -> IOException" + e.getMessage());
            }

            if (lUriStr != null) {
                return Uri.parse(lUriStr);
            } else {
                return null;
            }
        }

        // }

        @Override
        protected void onPostExecute(Uri pResult) {
            super.onPostExecute(pResult);
            try {
                if (isCancelled()) {
                    return;
                }
                if (pResult == null) {
                    setYoutubeErrorMessage(0, "视频地址信息获取失败！", "QueryYouTubeTask onPostExecute -> 地址获取失败，url == null");
                    return;
                }

                if (onQueryYoutubeSuccessListener != null) {
                    onQueryYoutubeSuccessListener.onQueryYoutubeSuccess(pResult);
                }


            } catch (Exception e) {
                Log.e(this.getClass().getSimpleName(), "Error playing video!", e);
                setYoutubeErrorMessage(2, "视频地址信息获取失败！", "未知异常， Exception " + e.getMessage());
            }
        }
    }

    private OnQueryYoutubeErrorListener onQueryYoutubeErrorListener;

    public interface OnQueryYoutubeErrorListener {
        void onQueryYoutubeError(int errorTag, String errorMsg, String description);
    }

    public void setOnQueryYoutubeErrorListener(OnQueryYoutubeErrorListener onQueryYoutubeErrorListener) {
        this.onQueryYoutubeErrorListener = onQueryYoutubeErrorListener;
    }

    OnQueryYoutubeSuccessListener onQueryYoutubeSuccessListener;

    public interface OnQueryYoutubeSuccessListener {
        void onQueryYoutubeSuccess(Uri pResult);
    }

    public void setOnQueryYoutubeSuccessListener(OnQueryYoutubeSuccessListener onQueryYoutubeSuccessListener) {
        this.onQueryYoutubeSuccessListener = onQueryYoutubeSuccessListener;
    }


    /**
     * @param resoluteLevel  清晰度水平，数值越大，清晰度越高，最高清晰度5（1080），依次递减
     * @param urlSparseArray url集合
     * @return
     */
    private static String getStanderResolution(int resoluteLevel,
                                               SparseArray<Map<String, String>> urlSparseArray) {
        // 现获取有url的itag的集合
        int length = urlSparseArray.size();
        ArrayList<Map<String, String>> itags = new ArrayList<Map<String, String>>();
        // 13, // 3GPP (MPEG-4 encoded) Low quality
        // 17, // 3GPP (MPEG-4 encoded) Medium quality
        // 18, // MP4 (H.264 encoded) Normal quality
        // 22, // MP4 (H.264 encoded) High quality
        // 37 // MP4 (H.264 encoded) High quality
        Map<String, String> tagsHigher = new HashMap<String, String>();
        Map<String, String> tagsHigh = new HashMap<String, String>();
        Map<String, String> tagsNormal = new HashMap<String, String>();
        Map<String, String> tagsMedium = new HashMap<String, String>();
        Map<String, String> tagsLow = new HashMap<String, String>();
        // 根据清晰度来个排序
        for (int i = 0; i < length; i++) {
            // 提出没有包含url和itag的数据
            Map<String, String> paramsMaps = urlSparseArray.get(i);
            if (TextUtils.isEmpty(paramsMaps.get("url"))) {
                continue;
            }
            String itagStr = paramsMaps.get("itag");
            if (TextUtils.isEmpty(itagStr)) {
                String url = Uri.decode(paramsMaps.get("url"));
                int index = url.indexOf("&itag");
                String itag0 = url.substring(index + 6, url.length());
                int index1 = itag0.indexOf("&");
                if (index1 > 0) {
                    String itag1 = itag0.substring(0, index1);
                    int tag;
                    try {
                        tag = Integer.parseInt(itag1);
                        if (tag > 0 && tag < 200) {
                            itagStr = String.valueOf(tag);
                            // Log.e("youtube", "没有itag字段，但是处理后符合条件的情况： " +
                            // itagStr);
                        }
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                        continue;
                    }
                }
            }
            /**
             * itag: web / 3gp 240P 36; 240P 17; 360P 18; 720P 22; 1080P 37; mp4
             * 720P 137; 480P 136; 360P 135; 240P 134
             */
            // 13, // 3GPP (MPEG-4 encoded) Low quality
            // 17, // 3GPP (MPEG-4 encoded) Medium quality
            // 18, // MP4 (H.264 encoded) Normal quality
            // 22, // MP4 (H.264 encoded) High quality
            // 37 // MP4 (H.264 encoded) High quality

            if ("37".equals(itagStr)) { // 1080P
                tagsHigher.put("itag", itagStr);
                tagsHigher.put("url", paramsMaps.get("url"));
            } else if ("22".equals(itagStr)) { // 720P
                tagsHigh.put("itag", itagStr);
                tagsHigh.put("url", paramsMaps.get("url"));
            } else if ("18".equals(itagStr)) { // 360P
                tagsNormal.put("itag", itagStr);
                tagsNormal.put("url", paramsMaps.get("url"));
            } else if ("17".equals(itagStr)) { // 240P 或者其他未知
                tagsMedium.put("itag", itagStr);
                tagsMedium.put("url", paramsMaps.get("url"));
            } else if ("13".equals(itagStr)) {
                tagsLow.put("itag", itagStr);
                tagsLow.put("url", paramsMaps.get("url"));
            }
        }

        int i = 0;

        itags.add(i++, tagsLow);

        itags.add(i++, tagsMedium);

        itags.add(i++, tagsNormal);

        itags.add(i++, tagsHigh);

        itags.add(i, tagsHigher);
        String realUrl = null;
        int size = itags.size();
        if (resoluteLevel <= size) {
            Map<String, String> paramsMap = itags.get(resoluteLevel - 1);
            if (paramsMap != null && paramsMap.size() != 0) {
                for (int m = 0; m < paramsMap.size(); m++) {
                    if (!TextUtils.isEmpty(paramsMap.get("url"))) {
                        realUrl = paramsMap.get("url");
                        Log.e("youtube", "有符合条件的url， itag： " + paramsMap.get("itag") + " ,url: " + realUrl);
                        break;
                    }
                }
            } else { // 没有对应清晰度的视频，往下查找对应的清晰度
                // Log.e("youtube", "没有符合条件的的url");
                for (int j = resoluteLevel - 2; j >= 0; j--) {
                    Map<String, String> paramsMaps = itags.get(j);
                    for (int m = 0; m < paramsMaps.size(); m++) {
                        if (!TextUtils.isEmpty(paramsMaps.get("url"))) {
                            realUrl = paramsMaps.get("url");
                            // Log.e("youtube", "没有符合条件的的url,查找到更低层次的视频: " +
                            // realUrl);
                            break;
                        }
                    }
                    if (!TextUtils.isEmpty(realUrl)) {
                        break;
                    }
                }
            }
        }
        return realUrl;
    }

    private void setYoutubeErrorMessage(int errorTag, String msg, String description) {
        if (onQueryYoutubeErrorListener != null) {
            onQueryYoutubeErrorListener.onQueryYoutubeError(errorTag, msg, description);
        }

    }

//    private static boolean hasVideoBeenViewed(Context pCtxt, String pVideoId) {
//        SharedPreferences lPrefs = PreferenceManager.getDefaultSharedPreferences(pCtxt);
//
//        String lViewedVideoIds = lPrefs.getString("com.keyes.screebl.lastViewedVideoIds", null);
//
//        if (lViewedVideoIds == null) {
//            return false;
//        }
//
//        String[] lSplitIds = lViewedVideoIds.split(";");
//        if (lSplitIds.length == 0) {
//            return false;
//        }
//
//        for (int i = 0; i < lSplitIds.length; i++) {
//            if (lSplitIds[i] != null && lSplitIds[i].equals(pVideoId)) {
//                return true;
//            }
//        }
//
//        return false;
//
//    }
//
//    public static void markVideoAsViewed(Context pCtxt, String pVideoId) {
//
//        SharedPreferences lPrefs = PreferenceManager.getDefaultSharedPreferences(pCtxt);
//
//        if (pVideoId == null) {
//            return;
//        }
//
//        String lViewedVideoIds = lPrefs.getString("com.keyes.screebl.lastViewedVideoIds", null);
//
//        if (lViewedVideoIds == null) {
//            lViewedVideoIds = "";
//        }
//
//        String[] lSplitIds = lViewedVideoIds.split(";");
//
//        // make a hash table of the ids to deal with duplicates
//        Map<String, String> lMap = new HashMap<String, String>();
//        for (String lSplitId : lSplitIds) {
//            lMap.put(lSplitId, lSplitId);
//        }
//
//        // recreate the viewed list
//        String lNewIdList = "";
//        Set<String> lKeys = lMap.keySet();
//        for (String lId : lKeys) {
//            if (!lId.trim().equals("")) {
//                lNewIdList += lId + ";";
//            }
//        }
//
//        // add the new video id
//        lNewIdList += pVideoId + ";";
//
//        Editor lPrefEdit = lPrefs.edit();
//        lPrefEdit.putString("com.keyes.screebl.lastViewedVideoIds", lNewIdList);
//        lPrefEdit.apply();
//
//    }

    private static int getSupportedFallbackId(int pOldId) {
        final int lSupportedFormatIds[] = {13, // 3GPP (MPEG-4 encoded) Low
                // quality
                17, // 3GPP (MPEG-4 encoded) Medium quality
                18, // MP4 (H.264 encoded) Normal quality
                22, // MP4 (H.264 encoded) High quality
                37 // MP4 (H.264 encoded) High quality
        };
        int lFallbackId = pOldId;
        for (int i = lSupportedFormatIds.length - 1; i >= 0; i--) {
            if (pOldId == lSupportedFormatIds[i] && i > 0) {
                lFallbackId = lSupportedFormatIds[i - 1];
            }
        }
        return lFallbackId;
    }

    //从InputStream中读取数据，转换成byte数组，最后关闭InputStream
    private byte[] getBytesByInputStream(InputStream is) {
        byte[] bytes = null;
        BufferedInputStream bis = new BufferedInputStream(is);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BufferedOutputStream bos = new BufferedOutputStream(baos);
        byte[] buffer = new byte[1024 * 8];
        int length;
        try {
            while ((length = bis.read(buffer)) > 0) {
                bos.write(buffer, 0, length);
            }
            bos.flush();
            bytes = baos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                bos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                bis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return bytes;
    }

}
